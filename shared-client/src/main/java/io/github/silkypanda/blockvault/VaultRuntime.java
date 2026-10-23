package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.CommandTemplate;
import io.github.silkypanda.blockvault.core.session.SavedCredential;
import io.github.silkypanda.blockvault.core.session.Scheduler;
import io.github.silkypanda.blockvault.core.session.VaultAccess;
import io.github.silkypanda.blockvault.core.vault.Account;
import io.github.silkypanda.blockvault.core.vault.DataDirs;
import io.github.silkypanda.blockvault.core.vault.KeyMode;
import io.github.silkypanda.blockvault.core.vault.ServerEntry;
import io.github.silkypanda.blockvault.core.vault.UnsupportedVaultVersionException;
import io.github.silkypanda.blockvault.core.vault.Vault;
import io.github.silkypanda.blockvault.core.vault.VaultStore;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Background-thread vault facade used by the login session. */
public final class VaultRuntime implements VaultAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockVault");
    private static final ArrayDeque<Runnable> WAITING = new ArrayDeque<>();
    private static VaultStore store;
    private static volatile boolean accessScreenOpen;
    private static volatile boolean trustedUnlockAttempted;
    private static Scheduler.Handle autoLockTimer;

    /** Returns the shared vault facade. */
    public static VaultRuntime access() { return Holder.INSTANCE; }

    private VaultRuntime() { }

    @Override
    public void find(String host, String username, Consumer<SavedCredential> result) {
        withVault(() -> {
            Vault vault = store.vault();
            ServerEntry server = findServer(vault, host);
            Account account = server == null ? null : server.accounts().stream()
                    .filter(value -> value.username().equals(username) && !isDeleted(value)).findFirst().orElse(null);
            CommandTemplate template = server == null ? CommandTemplate.builtins().get("authme")
                    : CommandTemplate.builtins().getOrDefault(server.templateId(), CommandTemplate.builtins().get("authme"));
            SavedCredential saved = server == null ? null
                    : new SavedCredential(account, server.autofill(), server.neverSave(), template);
            BackgroundExecutor.onGameThread(() -> {
                try { result.accept(saved); }
                finally { if (account != null) account.clearPassword(); }
            });
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(null)));
    }

    @Override
    public void savePending(String host, String username, char[] password, Consumer<Boolean> saved) {
        char[] copy = password.clone();
        withVault(() -> {
            try {
                changeAccount(host, username, old -> new Account(username, copy, Account.Status.PENDING,
                        old == null ? System.currentTimeMillis() : old.createdAt(), System.currentTimeMillis(),
                        old == null ? 0 : old.lastUsedAt(), old == null ? null : old.totpSecret(),
                        old == null ? null : withoutDeletedFlag(old)));
                BackgroundExecutor.onGameThread(() -> saved.accept(true));
            } catch (Exception failure) {
                BackgroundExecutor.onGameThread(() -> saved.accept(false));
            } finally { Arrays.fill(copy, '\0'); }
        }, () -> BackgroundExecutor.onGameThread(() -> saved.accept(false)));
    }

    @Override
    public void markVerified(String host, String username) {
        withVault(() -> changeAccount(host, username, old -> {
            if (old == null) return null;
            char[] secret = old.password();
            try {
                return new Account(username, secret, Account.Status.VERIFIED, old.createdAt(),
                        System.currentTimeMillis(), System.currentTimeMillis(), old.totpSecret(), withoutDeletedFlag(old));
            } finally { Arrays.fill(secret, '\0'); }
        }));
    }

    @Override
    public void delete(String host, String username) {
        withVault(() -> changeAccount(host, username, old -> {
            if (old == null) return null;
            com.google.gson.JsonObject fields = old.unknownFields();
            fields.addProperty("deleted", true);
            return new Account(username, new char[0], old.status(), old.createdAt(), System.currentTimeMillis(),
                    old.lastUsedAt(), null, fields);
        }));
    }

    @Override
    public void saveVerified(String host, String username, char[] password) {
        char[] copy = password.clone();
        withVault(() -> {
            try {
                changeAccount(host, username, old -> new Account(username, copy, Account.Status.VERIFIED,
                        old == null ? System.currentTimeMillis() : old.createdAt(), System.currentTimeMillis(),
                        System.currentTimeMillis(), old == null ? null : old.totpSecret(),
                        old == null ? null : withoutDeletedFlag(old)));
            } finally { Arrays.fill(copy, '\0'); }
        });
    }

    /** Creates or unlocks a master-password vault, then resumes operations waiting on it. */
    public static void openWithMasterPassword(char[] password, boolean create) {
        BlockVaultConfig.load(settings -> BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                if (create) store.create(password);
                else {
                    if (store.keyMode() != KeyMode.MASTER_PASSWORD) throw new IllegalStateException("Unsupported vault key mode");
                    store.unlock(password);
                }
                trustedUnlockAttempted = true;
                if (settings.trustDevice()) {
                    try { store.trustThisDevice(); }
                    catch (IOException failure) {
                        LOGGER.warn("BlockVault could not save the trusted-device key");
                        BackgroundExecutor.onGameThread(() -> toast("blockvault.toast.trust_failed"));
                    }
                } else {
                    try { store.forgetTrustedDevice(); }
                    catch (IOException failure) { LOGGER.warn("BlockVault could not remove its trusted-device key"); }
                }
                accessScreenOpen = false;
                BackgroundExecutor.onGameThread(() -> {
                    Minecraft.getInstance().setScreenAndShow(null);
                    toast("blockvault.toast.unlocked");
                    activity();
                    BackgroundExecutor.submit(() -> {
                        while (!WAITING.isEmpty()) WAITING.removeFirst().run();
                        return null;
                    });
                });
            } catch (Exception failure) {
                BackgroundExecutor.onGameThread(() -> {
                    if (Minecraft.getInstance().gui.screen() instanceof VaultAccessScreen screen) screen.showFailure();
                });
            } finally { Arrays.fill(password, '\0'); }
            return null;
        }));
    }

    /** Allows a dismissed access screen to be requested again on the next vault operation. */
    public static void accessScreenClosed() { accessScreenOpen = false; }

    /** Opens the vault view if unlocked, otherwise asks for its master password. */
    public static void openVaultScreen() {
        BlockVaultConfig.load(settings -> BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                if (!store.exists()) {
                    requestAccessScreen(true);
                } else {
                    if (!isUnlocked() && !tryTrustedUnlock(settings)) {
                        requestAccessScreen(false);
                    } else {
                        showVaultRows();
                    }
                }
            } catch (Exception failure) {
                LOGGER.warn("BlockVault could not open its local vault");
            }
            return null;
        }));
    }

    /** Locks and wipes the in-memory vault key. */
    public static void lock() {
        cancelAutoLock();
        trustedUnlockAttempted = true;
        BackgroundExecutor.submit(() -> {
            if (store != null) store.lock();
            BackgroundExecutor.onGameThread(() -> {
                Minecraft.getInstance().setScreenAndShow(null);
                toast("blockvault.toast.locked");
            });
            return null;
        });
    }

    /** Resets the inactivity lock timer after user interaction with vault UI. */
    public static void activity() {
        cancelAutoLock();
        int minutes = BlockVaultConfig.current().autoLockMinutes();
        if (minutes > 0) {
            autoLockTimer = BackgroundExecutor.scheduler().schedule(minutes * 60_000L, () -> {
                autoLockTimer = null;
                BackgroundExecutor.submit(() -> {
                    if (store != null) store.lock();
                    BackgroundExecutor.onGameThread(() -> {
                        if (Minecraft.getInstance().gui.screen() instanceof VaultListScreen
                                || Minecraft.getInstance().gui.screen() instanceof VaultCredentialScreen
                                || Minecraft.getInstance().gui.screen() instanceof VaultTemplateScreen
                                || Minecraft.getInstance().gui.screen() instanceof VaultDeleteScreen
                                || Minecraft.getInstance().gui.screen() instanceof VaultTransferScreen) {
                            Minecraft.getInstance().setScreenAndShow(null);
                        }
                        toast("blockvault.toast.locked");
                    });
                    return null;
                });
            });
        }
    }

    private static void cancelAutoLock() {
        if (autoLockTimer != null) autoLockTimer.cancel();
        autoLockTimer = null;
    }

    /** Opens settings while retaining the calling screen as its back destination. */
    public static void openSettingsScreen(net.minecraft.client.gui.screens.Screen parent) {
        Minecraft.getInstance().setScreenAndShow(new VaultSettingsScreen(parent));
    }

    /** Creates the settings screen for loader-provided config-screen entrypoints. */
    public static net.minecraft.client.gui.screens.Screen createSettingsScreen(
            net.minecraft.client.gui.screens.Screen parent) {
        return new VaultSettingsScreen(parent);
    }

    /** Reveals a selected credential only for the duration of the callback. */
    static void revealPassword(VaultRow row, Consumer<char[]> result) {
        withVault(() -> {
            Account account = findAccount(row.host(), row.username());
            char[] password = account == null ? new char[0] : account.password();
            BackgroundExecutor.onGameThread(() -> {
                try { result.accept(password); }
                finally { Arrays.fill(password, '\0'); }
            });
        });
    }

    /** Returns the saved Base32 TOTP secret on the game thread; no secret is logged. */
    static void revealTotpSecret(VaultRow row, Consumer<String> result) {
        withVault(() -> {
            Account account = findAccount(row.host(), row.username());
            String secret = account == null ? null : account.totpSecret();
            BackgroundExecutor.onGameThread(() -> result.accept(secret));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(null)));
    }

    static void requiresMasterPassword(Consumer<Boolean> result) {
        BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                boolean required = store.keyMode() == KeyMode.MASTER_PASSWORD;
                BackgroundExecutor.onGameThread(() -> result.accept(required));
            } catch (Exception failure) {
                BackgroundExecutor.onGameThread(() -> {
                    toast("blockvault.vault.error");
                    result.accept(true);
                });
            }
            return null;
        });
    }

    static void reauthenticateForReveal(char[] password, Consumer<Boolean> result) {
        char[] copy = password.clone();
        BackgroundExecutor.submit(() -> {
            boolean unlocked = false;
            try {
                ensureStore();
                store.lock();
                store.unlock(copy);
                unlocked = true;
            } catch (Exception failure) {
                LOGGER.warn("BlockVault could not re-confirm the master password");
            } finally { Arrays.fill(copy, '\0'); }
            boolean success = unlocked;
            BackgroundExecutor.onGameThread(() -> result.accept(success));
            return null;
        });
    }

    /** Copies one credential to the system clipboard. */
    static void copyPassword(VaultRow row) {
        withVault(() -> {
            Account account = findAccount(row.host(), row.username());
            if (account == null) return;
            char[] password = account.password();
            String clipboardValue = new String(password);
            Arrays.fill(password, '\0');
            BackgroundExecutor.onGameThread(() -> {
                copyToClipboard(clipboardValue);
            });
        });
    }

    /** Copies a caller-provided password to the system clipboard. */
    static void copyToClipboard(String clipboardValue) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(clipboardValue);
        toast("blockvault.toast.copied");
    }

    static void exportVault(java.nio.file.Path destination, Consumer<Boolean> result) {
        withVault(() -> {
            boolean success = false;
            try { store.exportEncrypted(destination); success = true; }
            catch (IOException failure) { LOGGER.warn("BlockVault could not export the encrypted vault"); }
            boolean exported = success;
            BackgroundExecutor.onGameThread(() -> result.accept(exported));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(false)));
    }

    static void importVault(java.nio.file.Path source, char[] masterPassword, Consumer<Boolean> result) {
        char[] password = masterPassword.clone();
        Arrays.fill(masterPassword, '\0');
        withVault(() -> {
            boolean success = false;
            try { store.mergeEncrypted(source, password); success = true; }
            catch (Exception failure) { LOGGER.warn("BlockVault could not import the encrypted vault"); }
            finally { Arrays.fill(password, '\0'); }
            boolean imported = success;
            BackgroundExecutor.onGameThread(() -> result.accept(imported));
        }, () -> {
            Arrays.fill(password, '\0');
            BackgroundExecutor.onGameThread(() -> result.accept(false));
        });
    }

    /** Saves an edited credential as verified. */
    static void updatePassword(VaultRow row, char[] password, String totpSecret) {
        char[] copy = password.clone();
        withVault(() -> {
            try {
                changeAccount(row.host(), row.username(), old -> new Account(row.username(), copy,
                        Account.Status.VERIFIED, old == null ? System.currentTimeMillis() : old.createdAt(),
                        System.currentTimeMillis(), old == null ? 0 : old.lastUsedAt(),
                        totpSecret == null || totpSecret.isBlank() ? null : totpSecret.trim(),
                        old == null ? null : withoutDeletedFlag(old)));
                BackgroundExecutor.onGameThread(() -> toast("blockvault.toast.saved"));
            } finally { Arrays.fill(copy, '\0'); }
        });
    }

    /** Changes per-server autofill and save policy. */
    static void updateServerPolicy(VaultRow row, boolean autofill, boolean neverSave) {
        withVault(() -> {
            Vault current = store.vault();
            List<ServerEntry> servers = new ArrayList<>(current.servers());
            for (int index = 0; index < servers.size(); index++) {
                ServerEntry server = servers.get(index);
                if (server.hosts().contains(row.host())) {
                    servers.set(index, new ServerEntry(server.id(), server.name(), server.hosts(), neverSave,
                            autofill, server.templateId(), server.accounts(), server.unknownFields()));
                    break;
                }
            }
            try { store.save(new Vault(current.v(), servers, current.unknownFields())); }
            catch (IOException failure) { throw new IllegalStateException("Could not save the local vault", failure); }
            BackgroundExecutor.onGameThread(VaultRuntime::openVaultScreen);
        });
    }

    static void updateServerTemplate(VaultRow row, String templateId) {
        if (!io.github.silkypanda.blockvault.core.session.CommandTemplate.builtins().containsKey(templateId)) return;
        withVault(() -> {
            Vault current = store.vault();
            List<ServerEntry> servers = new ArrayList<>(current.servers());
            for (int index = 0; index < servers.size(); index++) {
                ServerEntry server = servers.get(index);
                if (server.hosts().contains(row.host())) {
                    servers.set(index, new ServerEntry(server.id(), server.name(), server.hosts(), server.neverSave(),
                            server.autofill(), templateId, server.accounts(), server.unknownFields()));
                    break;
                }
            }
            try { store.save(new Vault(current.v(), servers, current.unknownFields())); }
            catch (IOException failure) { throw new IllegalStateException("Could not save the local vault", failure); }
            BackgroundExecutor.onGameThread(VaultRuntime::openVaultScreen);
        });
    }

    static void setNeverSave(String host, boolean neverSave) {
        withVault(() -> {
            Vault current = store.vault();
            List<ServerEntry> servers = new ArrayList<>(current.servers());
            boolean found = false;
            for (int index = 0; index < servers.size(); index++) {
                ServerEntry server = servers.get(index);
                if (server.hosts().contains(host)) {
                    servers.set(index, new ServerEntry(server.id(), server.name(), server.hosts(), neverSave,
                            server.autofill(), server.templateId(), server.accounts(), server.unknownFields()));
                    found = true;
                    break;
                }
            }
            if (!found) servers.add(new ServerEntry(UUID.randomUUID().toString(), host, List.of(host), neverSave,
                    true, "authme", List.of(), null));
            try { store.save(new Vault(current.v(), servers, current.unknownFields())); }
            catch (IOException failure) { throw new IllegalStateException("Could not save the local vault", failure); }
        });
    }

    /** Deletes an account after the caller has confirmed the action. */
    static void deleteAccount(VaultRow row) { access().delete(row.host(), row.username()); }

    static void showToast(String key) { toast(key); }

    /** Applies the explicit local-device trust preference without retaining the master password. */
    static void setTrustedDevice(boolean trusted) {
        trustedUnlockAttempted = true;
        BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                if (trusted) {
                    try { store.vault(); store.trustThisDevice(); }
                    catch (IllegalStateException locked) { /* The next password unlock will write the key. */ }
                } else {
                    store.forgetTrustedDevice();
                }
            } catch (Exception failure) {
                LOGGER.warn(trusted ? "BlockVault could not trust this device" : "BlockVault could not remove its trusted-device key");
                BackgroundExecutor.onGameThread(() -> toast("blockvault.toast.trust_failed"));
            }
            return null;
        });
    }

    private static void withVault(Runnable action) {
        withVault(action, () -> BackgroundExecutor.onGameThread(() -> toast("blockvault.vault.error")));
    }

    private static void withVault(Runnable action, Runnable failed) {
        BlockVaultConfig.load(settings -> BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                if (!store.exists() || store.keyMode() == null) {
                    WAITING.addLast(action);
                    requestAccessScreen(true);
                } else {
                    boolean unlocked = isUnlocked() || tryTrustedUnlock(settings);
                    if (unlocked) action.run();
                    else {
                        WAITING.addLast(action);
                        requestAccessScreen(false);
                    }
                }
            } catch (Exception failure) {
                LOGGER.warn("BlockVault could not open its local vault");
                failed.run();
            }
            return null;
        }));
    }

    private static boolean isUnlocked() {
        try { store.vault(); return true; }
        catch (IllegalStateException locked) { return false; }
    }

    private static boolean tryTrustedUnlock(BlockVaultConfig.Settings settings) {
        if (!settings.trustDevice() || trustedUnlockAttempted) return false;
        trustedUnlockAttempted = true;
        try {
            store.unlockTrustedDevice();
            return true;
        } catch (Exception invalidOrMissingTrustFile) {
            return false;
        }
    }

    private static void showVaultRows() {
        Vault vault = store.vault();
        List<VaultRow> rows = vault.servers().stream().flatMap(server ->
                server.accounts().stream().filter(account -> !isDeleted(account))
                        .map(account -> new VaultRow(server.name(), server.hosts().stream().findFirst().orElse(server.name()),
                                account.username(), account.status(), server.autofill(),
                                server.neverSave(), server.templateId()))).toList();
        BackgroundExecutor.onGameThread(() -> {
            Minecraft.getInstance().setScreenAndShow(new VaultListScreen(rows));
            activity();
        });
    }

    private static void requestAccessScreen(boolean create) {
        if (accessScreenOpen) return;
        accessScreenOpen = true;
        BackgroundExecutor.onGameThread(() -> Minecraft.getInstance().setScreenAndShow(new VaultAccessScreen(create)));
    }

    private static void ensureStore() throws IOException, UnsupportedVaultVersionException {
        if (store == null) store = VaultStore.open(DataDirs.resolve());
    }

    private static void changeAccount(String host, String username, UnaryOperator<Account> update) {
        Vault current = store.vault();
        List<ServerEntry> servers = new ArrayList<>(current.servers());
        int serverIndex = indexOfServer(servers, host);
        ServerEntry server = serverIndex < 0
                ? new ServerEntry(UUID.randomUUID().toString(), host, List.of(host), false, true, "authme", List.of(), null)
                : servers.get(serverIndex);
        List<Account> accounts = new ArrayList<>(server.accounts());
        int accountIndex = -1;
        Account existing = null;
        for (int index = 0; index < accounts.size(); index++) {
            if (accounts.get(index).username().equals(username)) { accountIndex = index; existing = accounts.get(index); break; }
        }
        Account replacement = update.apply(existing);
        if (replacement == null && accountIndex < 0 && serverIndex < 0) return;
        if (replacement == null && accountIndex >= 0) accounts.remove(accountIndex);
        else if (accountIndex >= 0) accounts.set(accountIndex, replacement);
        else if (replacement != null) accounts.add(replacement);
        ServerEntry updated = new ServerEntry(server.id(), server.name(), server.hosts(), server.neverSave(),
                server.autofill(), server.templateId(), accounts, server.unknownFields());
        if (serverIndex < 0) servers.add(updated); else servers.set(serverIndex, updated);
        try { store.save(new Vault(current.v(), servers, current.unknownFields())); }
        catch (IOException failure) { throw new IllegalStateException("Could not save the local vault", failure); }
    }

    private static ServerEntry findServer(Vault vault, String host) {
        return vault.servers().stream().filter(server -> server.hosts().contains(host)).findFirst().orElse(null);
    }

    private static Account findAccount(String host, String username) {
        ServerEntry server = findServer(store.vault(), host);
        return server == null ? null : server.accounts().stream()
                .filter(account -> account.username().equals(username) && !isDeleted(account))
                .findFirst().orElse(null);
    }

    private static int indexOfServer(List<ServerEntry> servers, String host) {
        for (int i = 0; i < servers.size(); i++) if (servers.get(i).hosts().contains(host)) return i;
        return -1;
    }

    private static boolean isDeleted(Account account) {
        return account.unknownFields().has("deleted") && account.unknownFields().get("deleted").getAsBoolean();
    }

    private static com.google.gson.JsonObject withoutDeletedFlag(Account account) {
        com.google.gson.JsonObject fields = account.unknownFields();
        fields.remove("deleted");
        return fields;
    }

    private static void toast(String key) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.components.toasts.SystemToast.add(mc.gui.toastManager(),
                ToastIds.BLOCKVAULT, Component.translatable("blockvault.toast.title"), Component.translatable(key));
    }

    private static final class Holder { private static final VaultRuntime INSTANCE = new VaultRuntime(); }
    private static final class ToastIds {
        private static final net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId BLOCKVAULT =
                new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId();
    }
}
