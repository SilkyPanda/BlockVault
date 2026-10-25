package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.CommandTemplate;
import io.github.silkypanda.blockvault.core.session.SavedCredential;
import io.github.silkypanda.blockvault.core.session.VaultAccess;
import io.github.silkypanda.blockvault.core.vault.Account;
import io.github.silkypanda.blockvault.core.vault.BackupSummary;
import io.github.silkypanda.blockvault.core.vault.BackupReencryptionException;
import io.github.silkypanda.blockvault.core.vault.DataDirs;
import io.github.silkypanda.blockvault.core.vault.ServerEntry;
import io.github.silkypanda.blockvault.core.vault.UnsupportedVaultVersionException;
import io.github.silkypanda.blockvault.core.vault.TrashEntry;
import io.github.silkypanda.blockvault.core.vault.Vault;
import io.github.silkypanda.blockvault.core.vault.VaultTrash;
import io.github.silkypanda.blockvault.core.vault.VaultStore;
import io.github.silkypanda.blockvault.core.vault.WrongMasterPasswordException;
import io.github.silkypanda.blockvault.core.vault.VaultCorruptException;
import java.io.IOException;
import java.awt.Desktop;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
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
    /** An operation waiting for the vault to be unlocked, plus what to do if the owner cancels the unlock. */
    private record Waiting(Runnable action, Runnable cancelled) { }
    private static final ConcurrentLinkedDeque<Waiting> WAITING = new ConcurrentLinkedDeque<>();
    private static final int MAX_WAITING = 32;
    private static VaultStore store;
    private static volatile boolean accessScreenOpen;
    private static volatile boolean openVaultAfterAccess;
    private static volatile boolean trustedUnlockAttempted;
    private static volatile boolean pendingTrustNotice;

    /** Returns the shared vault facade. */
    public static VaultRuntime access() { return Holder.INSTANCE; }

    private VaultRuntime() { }

    @Override
    public void find(String host, String username, Consumer<SavedCredential> result) {
        find(host, username, result, null);
    }

    @Override
    public void find(String host, String username, Consumer<SavedCredential> result, Runnable cancelled) {
        withVault(() -> {
            Vault vault = store.vault();
            ServerEntry server = findServer(vault, host);
            Account account = server == null ? null : server.accounts().stream()
                    .filter(value -> value.username().equals(username)).findFirst().orElse(null);
            Account callbackAccount = account == null ? null : copyAccount(account);
            // "auto" means "detect from the server's prompt"; any other stored id is an explicit owner choice.
            boolean templateExplicit = server != null && !"auto".equals(server.templateId());
            String templateId = server == null ? "auto" : server.templateId();
            CommandTemplate template = CommandTemplate.builtins().getOrDefault(templateId, CommandTemplate.builtins().get("authme"));
            SavedCredential saved = server == null ? null
                    : new SavedCredential(callbackAccount, server.autofill(), server.neverSave(), template, templateExplicit);
            BackgroundExecutor.onGameThread(() -> {
                try { result.accept(saved); }
                finally { if (callbackAccount != null) callbackAccount.clearPassword(); }
            });
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(null)),
                cancelled == null ? null : () -> BackgroundExecutor.onGameThread(cancelled));
    }

    @Override
    public void savePending(String host, String username, char[] password, Consumer<Boolean> saved) {
        char[] copy = password.clone();
        withVault(() -> {
            try {
                changeAccount(host, username, old -> new Account(username, copy, Account.Status.PENDING,
                        old == null ? System.currentTimeMillis() : old.createdAt(), System.currentTimeMillis(),
                        old == null ? 0 : old.lastUsedAt(), pendingFields(old)));
                BackgroundExecutor.onGameThread(() -> saved.accept(true));
            } catch (Exception failure) {
                BackgroundExecutor.onGameThread(() -> saved.accept(false));
            } finally { Arrays.fill(copy, '\0'); }
        }, () -> { Arrays.fill(copy, '\0'); BackgroundExecutor.onGameThread(() -> saved.accept(false)); });
    }

    @Override
    public void markVerified(String host, String username) {
        withVault(() -> changeAccount(host, username, old -> {
            if (old == null) return null;
            char[] secret = old.password();
            try {
                return new Account(username, secret, Account.Status.VERIFIED, old.createdAt(),
                        System.currentTimeMillis(), System.currentTimeMillis(), withoutPrevious(old));
            } finally { Arrays.fill(secret, '\0'); }
        }));
    }

    @Override
    public void delete(String host, String username) {
        withVault(() -> {
            Vault current = store.vault();
            ServerEntry server = findServer(current, host);
            if (server == null) return;
            Vault next = VaultTrash.trashAccount(current, server.id(), username, System.currentTimeMillis());
            if (next != current) saveVault(next);
        });
    }

    @Override
    public void saveVerified(String host, String username, char[] password) {
        char[] copy = password.clone();
        withVault(() -> {
            try {
                changeAccount(host, username, old -> new Account(username, copy, Account.Status.VERIFIED,
                        old == null ? System.currentTimeMillis() : old.createdAt(), System.currentTimeMillis(),
                        System.currentTimeMillis(), old == null ? null : withoutPrevious(old)));
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
                    store.unlock(password);
                }
                trustedUnlockAttempted = true;
                boolean[] trustReady = {false};
                if (settings.trustDevice()) {
                    try { store.trustThisDevice(); trustReady[0] = true; }
                    catch (IOException failure) {
                        LOGGER.warn("BlockVault could not save the trusted-device key");
                        BackgroundExecutor.onGameThread(() -> toast("blockvault.toast.trust_failed"));
                    }
                }
                accessScreenOpen = false;
                List<Waiting> resumed = new ArrayList<>(WAITING);
                WAITING.clear();
                boolean showVaultAfterUnlock = openVaultAfterAccess && resumed.isEmpty();
                openVaultAfterAccess = false;
                BackgroundExecutor.onGameThread(() -> {
                    closeAccessScreen();
                    toast("blockvault.toast.unlocked");
                    if (create && trustReady[0]) showTrustedDeviceNotice();
                    if (showVaultAfterUnlock) BackgroundExecutor.submit(() -> {
                        showVaultRows();
                        return null;
                    });
                    BackgroundExecutor.submit(() -> {
                        while (!resumed.isEmpty()) {
                            try { resumed.remove(0).action().run(); }
                            catch (RuntimeException failure) { LOGGER.warn("BlockVault queued operation failed ({})", failure.getClass().getSimpleName()); }
                        }
                        return null;
                    });
                });
            } catch (Exception failure) {
                String key = failure instanceof WrongMasterPasswordException ? "blockvault.vault.unlock.wrong_password"
                        : failure instanceof UnsupportedVaultVersionException ? "blockvault.vault.unlock.unsupported"
                        : failure instanceof VaultCorruptException || failure instanceof IllegalArgumentException
                        ? "blockvault.vault.unlock.corrupt" : create ? "blockvault.vault.create.failed"
                        : "blockvault.vault.unlock.failed";
                BackgroundExecutor.onGameThread(() -> {
                    if (Minecraft.getInstance().gui.screen() instanceof VaultAccessScreen screen) screen.showFailure(key);
                });
            } finally { Arrays.fill(password, '\0'); }
            return null;
        }));
    }

    private static void showTrustedDeviceNotice() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() == null || minecraft.gui == null || minecraft.gui.hud == null) {
            pendingTrustNotice = true;
            return;
        }
        pendingTrustNotice = false;
        minecraft.gui.hud.getChat().addClientSystemMessage(
                Component.literal("[BlockVault] ").withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xc58cff))
                        .append(Component.translatable("blockvault.chat.trusted_device_notice")
                                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xffffff))));
    }

    /** Delivers the trust notice after a main-menu-created vault joins a server. */
    static void deliverPendingTrustNotice() {
        if (!pendingTrustNotice) return;
        BackgroundExecutor.scheduler().schedule(1500, () -> {
            if (!pendingTrustNotice || Minecraft.getInstance().getCurrentServer() == null) return;
            showTrustedDeviceNotice();
        });
    }

    /** Allows a dismissed access screen to be requested again on the next vault operation. */
    public static void accessScreenClosed() {
        accessScreenOpen = false;
        openVaultAfterAccess = false;
        List<Waiting> dropped = new ArrayList<>(WAITING);
        WAITING.clear();
        // Tell every operation that was waiting that the owner declined, so nothing is left hanging.
        for (Waiting waiting : dropped) {
            if (waiting.cancelled() != null) {
                try { waiting.cancelled().run(); }
                catch (RuntimeException failure) { LOGGER.warn("BlockVault cancel callback failed ({})", failure.getClass().getSimpleName()); }
            }
        }
    }

    /**
     * The access screen was replaced by another screen without being cancelled (a kick screen, a credential card).
     * Minecraft calls {@code removed()} but not {@code onClose()} then, so the flag must be reset here or no new
     * unlock screen could ever be shown. Waiting operations are kept for the next time the screen is shown.
     */
    public static void accessScreenReplaced() { accessScreenOpen = false; }

    public static void clearWaiting() {
        WAITING.clear();
        accessScreenOpen = false;
    }

    /** Opens the vault view if unlocked, otherwise asks for its master password. */
    public static void openVaultScreen() {
        BlockVaultConfig.load(settings -> BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                if (!store.exists()) {
                    openVaultAfterAccess = true;
                    requestAccessScreen(true);
                } else {
                    if (!isUnlocked() && !tryTrustedUnlock(settings)) {
                        openVaultAfterAccess = true;
                        requestAccessScreen(false);
                    } else {
                        openVaultAfterAccess = false;
                        showVaultRows();
                    }
                }
            } catch (Exception failure) {
                LOGGER.warn("BlockVault could not open its local vault ({})", failure.getClass().getSimpleName());
                String key = failure instanceof UnsupportedVaultVersionException ? "blockvault.vault.unlock.unsupported"
                        : failure instanceof VaultCorruptException ? "blockvault.vault.unlock.corrupt"
                        : "blockvault.vault.error";
                BackgroundExecutor.onGameThread(() -> toast(key));
            }
            return null;
        }));
    }

    static void openBackupScreen(net.minecraft.client.gui.screens.Screen parent) {
        BackgroundExecutor.onGameThread(() -> Minecraft.getInstance().setScreenAndShow(new VaultRecoveryScreen(parent)));
    }

    static void openRecoveryScreen(net.minecraft.client.gui.screens.Screen parent) {
        BackgroundExecutor.onGameThread(() -> Minecraft.getInstance().setScreenAndShow(new VaultRecoveryScreen(parent, true)));
    }

    static void listRecoveryBackups(Consumer<List<BackupSummary>> result) {
        BackgroundExecutor.submit(() -> {
            List<BackupSummary> available;
            try { available = VaultStore.availableBackups(DataDirs.resolve()); }
            catch (IOException failure) { available = List.of(); }
            List<BackupSummary> completed = available;
            BackgroundExecutor.onGameThread(() -> result.accept(completed));
            return null;
        });
    }

    static void recoverDamagedVault(BackupSummary backup, char[] password, Consumer<String> result) {
        BlockVaultConfig.load(settings -> BackgroundExecutor.submit(() -> {
            String failureKey = null;
            try {
                VaultStore.restoreFromBackup(DataDirs.resolve(), backup, password);
                store = VaultStore.open(DataDirs.resolve());
                store.unlock(password);
                trustedUnlockAttempted = true;
                if (settings.trustDevice()) store.trustThisDevice();
            } catch (Exception failure) {
                failureKey = failure instanceof WrongMasterPasswordException ? "blockvault.vault.unlock.wrong_password"
                        : failure instanceof UnsupportedVaultVersionException ? "blockvault.vault.unlock.unsupported"
                        : failure instanceof VaultCorruptException ? "blockvault.vault.unlock.corrupt"
                        : "blockvault.backup.recovery.failed";
                LOGGER.warn("BlockVault backup recovery failed ({})", failure.getClass().getSimpleName());
            } finally { Arrays.fill(password, '\0'); }
            String completed = failureKey;
            BackgroundExecutor.onGameThread(() -> {
                result.accept(completed);
                if (completed == null) showVaultRows();
            });
            return null;
        }));
    }

    static void openMasterPasswordScreen(net.minecraft.client.gui.screens.Screen parent) {
        BackgroundExecutor.onGameThread(() -> Minecraft.getInstance().setScreenAndShow(new VaultMasterPasswordScreen(parent)));
    }

    static void openDataDirectory() {
        BackgroundExecutor.submit(() -> {
            boolean opened = false;
            java.nio.file.Path directory = null;
            try {
                directory = DataDirs.createDirectory();
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();
                    try {
                        if (!desktop.isSupported(Desktop.Action.OPEN)) throw new UnsupportedOperationException();
                        desktop.open(directory.toFile());
                        opened = true;
                    } catch (Exception openFailure) {
                        if (desktop.isSupported(Desktop.Action.BROWSE)) {
                            desktop.browse(directory.toUri());
                            opened = true;
                        }
                    }
                }
            } catch (Exception failure) {
                LOGGER.warn("BlockVault could not open its data directory ({})", failure.getClass().getSimpleName());
            }
            boolean result = opened;
            String path = directory == null ? null : directory.toString();
            BackgroundExecutor.onGameThread(() -> {
                if (result) toast("blockvault.data.opened");
                else if (path != null) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(path);
                    toast("blockvault.data.open.copied");
                } else toast("blockvault.data.open.failed");
            });
            return null;
        });
    }

    static void eraseAllData(char[] masterPassword, Consumer<String> result) {
        char[] password = masterPassword.clone();
        Arrays.fill(masterPassword, '\0');
        BackgroundExecutor.submit(() -> {
            String failureKey = null;
            try {
                java.nio.file.Path directory = DataDirs.resolve();
                VaultStore verifier = VaultStore.open(directory);
                if (!verifier.exists()) throw new IOException("No encrypted vault is available to verify the password");
                try { verifier.unlock(password); }
                finally { verifier.lock(); }
                if (store != null) {
                    store.lock();
                    store = null;
                }
                DataDirs.delete(directory);
                trustedUnlockAttempted = false;
                accessScreenOpen = false;
                WAITING.clear();
            } catch (Exception failure) {
                failureKey = failure instanceof WrongMasterPasswordException
                        ? "blockvault.data.erase.wrong_password" : "blockvault.data.erase.failed";
                LOGGER.warn("BlockVault data erasure failed ({})", failure.getClass().getSimpleName());
            } finally { Arrays.fill(password, '\0'); }
            String completed = failureKey;
            BackgroundExecutor.onGameThread(() -> {
                if (completed == null) {
                    BlockVaultConfig.resetAfterDataErasure();
                    Hooks.onDisconnect();
                }
                result.accept(completed);
            });
            return null;
        });
    }

    static void listBackups(Consumer<List<BackupSummary>> result) {
        withVault(() -> {
            List<BackupSummary> backups;
            try {
                backups = store.backups();
            } catch (Exception failure) {
                LOGGER.warn("BlockVault backup listing failed ({})", failure.getClass().getSimpleName());
                backups = List.of();
            }
            List<BackupSummary> available = backups;
            BackgroundExecutor.onGameThread(() -> result.accept(available));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(List.of())));
    }

    static void createManualBackup(Consumer<Boolean> result) {
        withVault(() -> {
            boolean success;
            try {
                store.createManualBackup();
                success = true;
            } catch (Exception failure) {
                LOGGER.warn("BlockVault manual backup failed ({})", failure.getClass().getSimpleName());
                success = false;
            }
            boolean completed = success;
            BackgroundExecutor.onGameThread(() -> result.accept(completed));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(false)));
    }

    static void inspectBackup(BackupSummary backup, Consumer<io.github.silkypanda.blockvault.core.vault.Vault> result) {
        withVault(() -> {
            io.github.silkypanda.blockvault.core.vault.Vault contents;
            try {
                contents = store.inspectBackup(backup);
            } catch (Exception failure) {
                LOGGER.warn("BlockVault backup inspection failed ({})", failure.getClass().getSimpleName());
                contents = null;
            }
            io.github.silkypanda.blockvault.core.vault.Vault inspected = contents;
            BackgroundExecutor.onGameThread(() -> result.accept(inspected));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(null)));
    }

    static void restoreBackup(BackupSummary backup, Consumer<Boolean> result) {
        withVault(() -> {
            boolean success;
            try {
                store.restoreBackup(backup);
                success = true;
            } catch (Exception failure) {
                LOGGER.warn("BlockVault backup restore failed ({})", failure.getClass().getSimpleName());
                success = false;
            }
            boolean completed = success;
            BackgroundExecutor.onGameThread(() -> result.accept(completed));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(false)));
    }

    static void changeMasterPassword(char[] oldPassword, char[] newPassword, Consumer<String> result) {
        withVault(() -> {
            String failureKey = null;
            try {
                store.changeMasterPassword(oldPassword, newPassword);
            } catch (Exception failure) {
                failureKey = failure instanceof WrongMasterPasswordException ? "blockvault.master.current.wrong"
                        : failure instanceof BackupReencryptionException ? "blockvault.master.change.backups_failed"
                        : failure instanceof IllegalArgumentException ? "blockvault.master.new.invalid"
                        : failure instanceof IOException ? "blockvault.master.change.io" : "blockvault.master.failed";
                LOGGER.warn("BlockVault master password change failed ({})", failure.getClass().getSimpleName());
            } finally {
                Arrays.fill(oldPassword, '\0');
                Arrays.fill(newPassword, '\0');
            }
            String outcome = failureKey;
            BackgroundExecutor.onGameThread(() -> result.accept(outcome));
        }, () -> {
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
            BackgroundExecutor.onGameThread(() -> result.accept("blockvault.vault.error"));
        });
    }

    /** Locks and wipes the in-memory vault key. */
    public static void lock() {
        trustedUnlockAttempted = true;
        BackgroundExecutor.submit(() -> {
            if (store != null) store.lock();
            BackgroundExecutor.onGameThread(() -> {
                closeBlockVaultScreen();
                toast("blockvault.toast.locked");
            });
            return null;
        });
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
    static void updatePassword(VaultRow row, char[] password) {
        char[] copy = password.clone();
        withVault(() -> {
            try {
                changeAccount(row.host(), row.username(), old -> new Account(row.username(), copy,
                        Account.Status.VERIFIED, old == null ? System.currentTimeMillis() : old.createdAt(),
                        System.currentTimeMillis(), old == null ? 0 : old.lastUsedAt(),
                        old == null ? null : withoutPrevious(old)));
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
                            autofill, server.templateId(), server.accounts()));
                    break;
                }
            }
            saveVault(current.withServers(servers));
            BackgroundExecutor.onGameThread(VaultRuntime::refreshVaultRows);
        });
    }

    static void updateServerTemplate(VaultRow row, String templateId) {
        if (!"auto".equals(templateId) && !io.github.silkypanda.blockvault.core.session.CommandTemplate.builtins().containsKey(templateId)) return;
        String storedTemplate = templateId;
        withVault(() -> {
            Vault current = store.vault();
            List<ServerEntry> servers = new ArrayList<>(current.servers());
            for (int index = 0; index < servers.size(); index++) {
                ServerEntry server = servers.get(index);
                if (server.hosts().contains(row.host())) {
                    servers.set(index, new ServerEntry(server.id(), server.name(), server.hosts(), server.neverSave(),
                            server.autofill(), storedTemplate, server.accounts()));
                    break;
                }
            }
            saveVault(current.withServers(servers));
            BackgroundExecutor.onGameThread(VaultRuntime::refreshVaultRows);
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
                            server.autofill(), server.templateId(), server.accounts()));
                    found = true;
                    break;
                }
            }
            if (!found) servers.add(new ServerEntry(UUID.randomUUID().toString(), host, List.of(host), neverSave,
                    true, "auto", List.of()));
            saveVault(current.withServers(servers));
        });
    }

    /** Moves an account (or an empty server entry) to the trash after the caller has confirmed the action. */
    static void deleteAccount(VaultRow row) {
        withVault(() -> {
            Vault current = store.vault();
            ServerEntry server = findServer(current, row.host());
            if (server != null) {
                long now = System.currentTimeMillis();
                Vault next = row.serverOnly() ? VaultTrash.trashServer(current, server.id(), now)
                        : VaultTrash.trashAccount(current, server.id(), row.username(), now);
                if (next != current) saveVault(next);
            }
            BackgroundExecutor.onGameThread(VaultRuntime::refreshVaultRows);
        });
    }

    /** Moves a whole server, with every account on it, to the trash. */
    static void deleteServer(VaultRow row) {
        withVault(() -> {
            Vault current = store.vault();
            ServerEntry server = findServer(current, row.host());
            if (server != null) {
                Vault next = VaultTrash.trashServer(current, server.id(), System.currentTimeMillis());
                if (next != current) saveVault(next);
            }
            BackgroundExecutor.onGameThread(VaultRuntime::refreshVaultRows);
        });
    }

    /** Opens the trash screen, returning to {@code parent} when it closes. */
    static void openTrashScreen(net.minecraft.client.gui.screens.Screen parent) {
        withVault(() -> {
            List<TrashRow> rows = trashRows();
            BackgroundExecutor.onGameThread(() -> Minecraft.getInstance().setScreenAndShow(new VaultTrashScreen(parent, rows)));
        });
    }

    /** Puts a trash entry back into the vault (newer copy wins if the same server and user already exist). */
    static void restoreFromTrash(String trashId, Consumer<List<TrashRow>> result) {
        changeTrash(result, vault -> VaultTrash.restore(vault, trashId, System.currentTimeMillis()));
    }

    /** Permanently deletes one trash entry. */
    static void deleteFromTrash(String trashId, Consumer<List<TrashRow>> result) {
        changeTrash(result, vault -> VaultTrash.deleteForever(vault, trashId));
    }

    /** Permanently deletes everything in the trash. */
    static void emptyTrash(Consumer<List<TrashRow>> result) {
        changeTrash(result, VaultTrash::emptyTrash);
    }

    private static void changeTrash(Consumer<List<TrashRow>> result, UnaryOperator<Vault> change) {
        withVault(() -> {
            Vault current = store.vault();
            Vault next = change.apply(current);
            if (next != current) saveVault(next);
            List<TrashRow> rows = trashRows();
            BackgroundExecutor.onGameThread(() -> result.accept(rows));
        }, () -> BackgroundExecutor.onGameThread(() -> toast("blockvault.vault.error")));
    }

    private static List<TrashRow> trashRows() {
        List<TrashRow> rows = new ArrayList<>();
        for (TrashEntry entry : store.vault().trash()) {
            ServerEntry server = entry.server();
            String host = server.hosts().stream().findFirst().orElse(server.name());
            List<String> users = server.accounts().stream().map(Account::username).toList();
            rows.add(new TrashRow(entry.id(), server.name(), host, users, entry.wholeServer(), entry.deletedAt()));
        }
        rows.sort(java.util.Comparator.comparingLong(TrashRow::deletedAt).reversed());
        return List.copyOf(rows);
    }

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
        withVault(action, () -> BackgroundExecutor.onGameThread(() -> toast("blockvault.vault.error")), null);
    }

    private static void withVault(Runnable action, Runnable failed) {
        withVault(action, failed, failed);
    }

    /**
     * Runs {@code action} once the vault is unlocked. {@code failed} runs on an I/O or authentication failure;
     * {@code cancelled} (may be null) runs if the owner dismisses the master-password prompt instead.
     */
    private static void withVault(Runnable action, Runnable failed, Runnable cancelled) {
        BlockVaultConfig.load(settings -> BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                if (!store.exists()) {
                    enqueue(action, cancelled);
                    requestAccessScreen(true);
                } else {
                    boolean unlocked = isUnlocked() || tryTrustedUnlock(settings);
                    if (unlocked) {
                        store.reload();
                        purgeTrash();
                        action.run();
                    }
                    else {
                        enqueue(action, cancelled);
                        requestAccessScreen(false);
                    }
                }
            } catch (Exception failure) {
                LOGGER.warn("BlockVault could not open its local vault ({})", failure.getClass().getSimpleName());
                failed.run();
            }
            return null;
        }));
    }

    /** Removes trash entries older than 60 days; failures are harmless and retried on the next operation. */
    private static void purgeTrash() {
        try { store.purgeExpiredTrash(); }
        catch (IOException | RuntimeException failure) { LOGGER.warn("BlockVault could not purge the trash"); }
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
        purgeTrash();
        List<VaultRow> rows = vaultRows();
        BackgroundExecutor.onGameThread(() -> {
            Minecraft.getInstance().setScreenAndShow(new VaultListScreen(rows));
        });
    }

    private static List<VaultRow> vaultRows() {
        Vault vault = store.vault();
        List<VaultRow> result = new ArrayList<>();
        for (ServerEntry server : vault.servers()) {
            String host = server.hosts().stream().findFirst().orElse(server.name());
            List<Account> active = server.accounts();
            if (active.isEmpty()) {
                result.add(new VaultRow(server.name(), host, "", null, server.autofill(), server.neverSave(),
                        server.templateId(), true));
            } else {
                for (Account account : active) result.add(new VaultRow(server.name(), host, account.username(),
                        account.status(), server.autofill(), server.neverSave(), server.templateId()));
            }
        }
        return List.copyOf(result);
    }

    private static void refreshVaultRows() {
        BackgroundExecutor.submit(() -> {
            if (store == null || !isUnlocked()) return null;
            List<VaultRow> rows = vaultRows();
            BackgroundExecutor.onGameThread(() -> {
                net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().gui.screen();
                if (screen instanceof VaultListScreen list) list.updateRows(rows);
                else Minecraft.getInstance().setScreenAndShow(new VaultListScreen(rows));
            });
            return null;
        });
    }

    private static void requestAccessScreen(boolean create) {
        if (accessScreenOpen) return;
        accessScreenOpen = true;
        BackgroundExecutor.onGameThread(() -> Minecraft.getInstance().setScreenAndShow(new VaultAccessScreen(create)));
    }

    private static void enqueue(Runnable action, Runnable cancelled) {
        if (WAITING.size() >= MAX_WAITING) {
            Waiting evicted = WAITING.pollFirst();
            if (evicted != null && evicted.cancelled() != null) evicted.cancelled().run();
        }
        WAITING.addLast(new Waiting(action, cancelled));
    }

    private static void closeAccessScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof VaultAccessScreen) minecraft.setScreenAndShow(null);
    }

    private static void closeBlockVaultScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen screen = minecraft.gui.screen();
        if (screen instanceof VaultListScreen || screen instanceof VaultCredentialScreen
                || screen instanceof VaultTemplateScreen || screen instanceof VaultDeleteScreen
                || screen instanceof VaultTransferScreen || screen instanceof VaultSettingsScreen
                || screen instanceof VaultRecoveryScreen || screen instanceof VaultBackupDetailScreen
                || screen instanceof VaultBackupRecoveryScreen || screen instanceof VaultMasterPasswordScreen
                || screen instanceof VaultEraseScreen
                || screen instanceof VaultAccessScreen) {
            minecraft.setScreenAndShow(null);
        }
    }

    private static void ensureStore() throws IOException, UnsupportedVaultVersionException, VaultCorruptException {
        if (store == null) store = VaultStore.open(DataDirs.resolve());
    }

    private static void changeAccount(String host, String username, UnaryOperator<Account> update) {
        Vault current = store.vault();
        List<ServerEntry> servers = new ArrayList<>(current.servers());
        int serverIndex = indexOfServer(servers, host);
        ServerEntry server = serverIndex < 0
                ? new ServerEntry(UUID.randomUUID().toString(), host, List.of(host), false, true, "auto", List.of())
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
                server.autofill(), server.templateId(), accounts);
        if (serverIndex < 0) servers.add(updated); else servers.set(serverIndex, updated);
        saveVault(current.withServers(servers));
    }

    private static void saveVault(Vault next) {
        try { store.saveExplicit(next); }
        catch (IOException failure) { throw new IllegalStateException("Could not save the local vault", failure); }
    }

    private static ServerEntry findServer(Vault vault, String host) {
        return vault.servers().stream().filter(server -> server.hosts().contains(host)).findFirst().orElse(null);
    }

    private static Account findAccount(String host, String username) {
        ServerEntry server = findServer(store.vault(), host);
        return server == null ? null : server.accounts().stream()
                .filter(account -> account.username().equals(username))
                .findFirst().orElse(null);
    }

    private static int indexOfServer(List<ServerEntry> servers, String host) {
        for (int i = 0; i < servers.size(); i++) if (servers.get(i).hosts().contains(host)) return i;
        return -1;
    }

    private static Account copyAccount(Account source) {
        char[] password = source.password();
        try {
            return new Account(source.username(), password, source.status(), source.createdAt(), source.modifiedAt(),
                    source.lastUsedAt(), source.metadata());
        } finally { Arrays.fill(password, '\0'); }
    }

    private static com.google.gson.JsonObject withoutPrevious(Account account) {
        com.google.gson.JsonObject fields = account.metadata();
        fields.remove("blockvaultPrevious");
        return fields;
    }

    private static com.google.gson.JsonObject pendingFields(Account previousAccount) {
        if (previousAccount == null) return null;
        // A pending account keeps whatever verified password it was already protecting.
        if (previousAccount.status() != Account.Status.VERIFIED) return previousAccount.metadata();
        char[] password = previousAccount.password();
        try {
            com.google.gson.JsonObject previous = new com.google.gson.JsonObject();
            previous.addProperty("pass", new String(password));
            previous.addProperty("status", previousAccount.status().name());
            previous.addProperty("created", previousAccount.createdAt());
            previous.addProperty("modified", previousAccount.modifiedAt());
            previous.addProperty("used", previousAccount.lastUsedAt());
            com.google.gson.JsonObject fields = withoutPrevious(previousAccount);
            fields.add("blockvaultPrevious", previous);
            return fields;
        } finally { Arrays.fill(password, '\0'); }
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
