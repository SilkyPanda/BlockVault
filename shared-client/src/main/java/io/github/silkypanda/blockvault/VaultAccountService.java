package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.host.HostKey;
import io.github.silkypanda.blockvault.core.session.CommandTemplate;
import io.github.silkypanda.blockvault.core.session.CredentialPassword;
import io.github.silkypanda.blockvault.core.session.SavedCredential;
import io.github.silkypanda.blockvault.core.session.VaultAccess;
import io.github.silkypanda.blockvault.core.vault.Account;
import io.github.silkypanda.blockvault.core.vault.ServerEntry;
import io.github.silkypanda.blockvault.core.vault.TrashEntry;
import io.github.silkypanda.blockvault.core.vault.Usernames;
import io.github.silkypanda.blockvault.core.vault.Vault;
import io.github.silkypanda.blockvault.core.vault.VaultTrash;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Owns account lookup, credential updates, and trash operations.
 * Entry points may be called on the game thread; vault work runs through the IO-owned unlock coordinator,
 * and UI/session callbacks are dispatched to the game thread.
 */
final class VaultAccountService implements VaultAccess {
    private final VaultUnlockCoordinator unlock;
    private final VaultScreenNavigation navigation;

    VaultAccountService(VaultUnlockCoordinator unlock, VaultScreenNavigation navigation) {
        this.unlock = unlock;
        this.navigation = navigation;
    }

    @Override public void find(String host, String username, Consumer<SavedCredential> result) {
        find(host, username, result, null);
    }

    @Override public void find(String host, String username, Consumer<SavedCredential> result, Runnable cancelled) {
        unlock.withVault(() -> {
            Vault vault = unlock.store().vault();
            ServerEntry server = findServer(vault, host);
            Account account = server == null ? null : server.accounts().stream()
                    .filter(value -> value.isValidForUse() && Usernames.same(value.username(), username))
                    .findFirst().orElse(null);
            Account callbackAccount = account == null ? null : copyAccount(account);
            String templateId = account == null ? Account.AUTO_TEMPLATE : account.templateId();
            boolean templateExplicit = !Account.AUTO_TEMPLATE.equals(templateId);
            CommandTemplate template = CommandTemplate.builtins().getOrDefault(templateId,
                    CommandTemplate.builtins().get("authme"));
            SavedCredential saved = server == null ? null
                    : new SavedCredential(callbackAccount, account != null && account.autofill(), template,
                            templateExplicit);
            BackgroundExecutor.onGameThread(() -> {
                try { result.accept(saved); }
                finally { if (callbackAccount != null) callbackAccount.clearPassword(); }
            });
        }, () -> {
            if (cancelled != null) BackgroundExecutor.onGameThread(cancelled);
            else BackgroundExecutor.onGameThread(() -> result.accept(null));
        }, cancelled == null ? null : () -> BackgroundExecutor.onGameThread(cancelled));
    }

    @Override public void savePending(String host, String username, char[] password, Consumer<Boolean> saved) {
        char[] copy = password.clone();
        unlock.withVault(() -> {
            try {
                changeAccount(host, username, old -> {
                    long now = System.currentTimeMillis();
                    return old == null ? new Account(username, copy, Account.Status.PENDING, now, now, 0)
                            : old.withCredential(copy, Account.Status.PENDING, now, old.lastUsedAt());
                });
                BackgroundExecutor.onGameThread(() -> saved.accept(true));
            } catch (Exception failure) {
                BackgroundExecutor.onGameThread(() -> saved.accept(false));
            } finally { Arrays.fill(copy, '\0'); }
        }, () -> { Arrays.fill(copy, '\0'); BackgroundExecutor.onGameThread(() -> saved.accept(false)); });
    }

    @Override public void markVerified(String host, String username) {
        unlock.withVault(() -> changeAccount(host, username,
                old -> old == null ? null : old.verifiedAt(System.currentTimeMillis())));
    }

    @Override public void delete(String host, String username) {
        unlock.withVault(() -> {
            Vault current = unlock.store().vault();
            ServerEntry server = findServer(current, host);
            if (server == null) return;
            Vault next = VaultTrash.trashAccount(current, server.id(), username, System.currentTimeMillis());
            if (next != current) saveVault(next);
        });
    }

    @Override public void delete(String host, String username, Consumer<Boolean> result) {
        unlock.withVault(() -> {
            Vault current = unlock.store().vault();
            ServerEntry server = findServer(current, host);
            if (server != null) {
                Vault next = VaultTrash.trashAccount(current, server.id(), username, System.currentTimeMillis());
                if (next != current) saveVault(next);
            }
            BackgroundExecutor.onGameThread(() -> result.accept(true));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(false)),
                () -> BackgroundExecutor.onGameThread(() -> result.accept(false)));
    }

    @Override public void saveVerified(String host, String username, char[] password) {
        char[] copy = password.clone();
        unlock.withVault(() -> {
            try {
                changeAccount(host, username, old -> verifiedWith(old, username, copy));
            } finally { Arrays.fill(copy, '\0'); }
        });
    }

    @Override public void saveVerified(String host, String username, char[] password, Consumer<Boolean> result) {
        char[] copy = password.clone();
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean();
        Consumer<Boolean> finish = saved -> {
            if (!completed.compareAndSet(false, true)) return;
            Arrays.fill(copy, '\0');
            BackgroundExecutor.onGameThread(() -> result.accept(saved));
        };
        unlock.withVault(() -> {
            changeAccount(host, username, old -> verifiedWith(old, username, copy));
            finish.accept(true);
        }, () -> finish.accept(false), () -> finish.accept(false));
    }

    void revealPassword(VaultRow row, Consumer<char[]> result) {
        unlock.withVault(() -> {
            Account account = findAccount(row.host(), row.username());
            char[] password = account == null ? new char[0] : account.password();
            BackgroundExecutor.onGameThread(() -> {
                try { result.accept(password); }
                finally { Arrays.fill(password, '\0'); }
            });
        });
    }

    void copyPassword(VaultRow row) {
        unlock.withVault(() -> {
            Account account = findAccount(row.host(), row.username());
            if (account == null) return;
            char[] password = account.password();
            String clipboardValue = new String(password);
            Arrays.fill(password, '\0');
            BackgroundExecutor.onGameThread(() -> VaultRuntime.copyToClipboard(clipboardValue));
        });
    }

    void addAccount(String hostText, String username, char[] password, Consumer<String> result) {
        String host = HostKey.normalize(hostText.trim()).orElse(null);
        String name = username.trim();
        String validationFailure = validateNewAccount(host, name, password);
        if (validationFailure != null) {
            Arrays.fill(password, '\0');
            result.accept(validationFailure);
            return;
        }
        char[] copy = password.clone();
        Arrays.fill(password, '\0');
        unlock.withVault(() -> {
            String failure;
            try { failure = saveNewAccount(host, name, copy); }
            finally { Arrays.fill(copy, '\0'); }
            String key = failure;
            BackgroundExecutor.onGameThread(() -> result.accept(key));
        }, () -> {
            Arrays.fill(copy, '\0');
            BackgroundExecutor.onGameThread(() -> result.accept("blockvault.vault.error"));
        });
    }

    private static String validateNewAccount(String host, String username, char[] password) {
        if (host == null) return "blockvault.add.bad_host";
        if (!Usernames.isValid(username)) return "blockvault.add.bad_username";
        if (!CredentialPassword.isValid(password)) return "blockvault.password.invalid";
        return null;
    }

    private String saveNewAccount(String host, String username, char[] password) {
        try {
            ServerEntry server = findServer(unlock.store().vault(), host);
            boolean exists = server != null && server.accounts().stream()
                    .anyMatch(account -> Usernames.same(account.username(), username));
            if (exists) return "blockvault.add.exists";
            long now = System.currentTimeMillis();
            changeAccount(host, username, old -> new Account(username, password, Account.Status.VERIFIED, now, now, 0));
            return null;
        } catch (RuntimeException error) {
            return "blockvault.vault.error";
        }
    }

    void updatePassword(VaultRow row, char[] password) {
        if (!CredentialPassword.isValid(password)) {
            Arrays.fill(password, '\0');
            VaultRuntime.toast("blockvault.password.invalid");
            return;
        }
        char[] copy = password.clone();
        unlock.withVault(() -> {
            try {
                changeAccount(row.host(), row.username(), old -> {
                    long now = System.currentTimeMillis();
                    return old == null ? new Account(row.username(), copy, Account.Status.VERIFIED, now, now, 0)
                            : old.withCredential(copy, Account.Status.VERIFIED, now, old.lastUsedAt());
                });
                BackgroundExecutor.onGameThread(() -> VaultRuntime.toast("blockvault.toast.saved"));
            } finally { Arrays.fill(copy, '\0'); }
        });
    }

    void updateAutofill(VaultRow row, boolean autofill) {
        updatePreference(row, account -> account.withAutofill(autofill));
    }

    void updateTemplate(VaultRow row, String templateId) {
        if (!Account.AUTO_TEMPLATE.equals(templateId) && !CommandTemplate.builtins().containsKey(templateId)) return;
        updatePreference(row, account -> account.withTemplate(templateId));
    }

    private void updatePreference(VaultRow row, UnaryOperator<Account> change) {
        unlock.withVault(() -> {
            Vault current = unlock.store().vault();
            List<ServerEntry> servers = new ArrayList<>(current.servers());
            for (int index = 0; index < servers.size(); index++) {
                ServerEntry server = servers.get(index);
                if (!server.hosts().contains(row.host())) continue;
                List<Account> accounts = new ArrayList<>(server.accounts());
                for (int accountIndex = 0; accountIndex < accounts.size(); accountIndex++) {
                    Account account = accounts.get(accountIndex);
                    if (Usernames.same(account.username(), row.username())) {
                        accounts.set(accountIndex, change.apply(account));
                        break;
                    }
                }
                servers.set(index, server.withAccounts(accounts));
                break;
            }
            saveVault(current.withServers(servers));
            BackgroundExecutor.onGameThread(this::refreshVaultRows);
        });
    }

    void deleteAccount(VaultRow row) {
        unlock.withVault(() -> {
            Vault current = unlock.store().vault();
            ServerEntry server = findServer(current, row.host());
            if (server != null) {
                Vault next = VaultTrash.trashAccount(current, server.id(), row.username(), System.currentTimeMillis());
                if (next != current) saveVault(next);
            }
            BackgroundExecutor.onGameThread(this::refreshVaultRows);
        });
    }

    void openTrashScreen(Screen parent) {
        unlock.withVault(() -> {
            List<TrashRow> rows = trashRows();
            BackgroundExecutor.onGameThread(() -> navigation.openTrashScreen(parent, rows));
        });
    }

    void restoreFromTrash(String trashId, Screen parent, Consumer<List<TrashRow>> result) {
        unlock.withVault(() -> {
            Vault vault = unlock.store().vault();
            TrashEntry removed = vault.trash().stream().filter(entry -> entry.id().equals(trashId))
                    .findFirst().orElse(null);
            boolean conflict = false;
            if (removed != null) {
                for (ServerEntry live : vault.servers()) {
                    boolean sameServer = live.id().equals(removed.server().id())
                            || live.hosts().stream().anyMatch(removed.server().hosts()::contains);
                    if (!sameServer) continue;
                    conflict = removed.server().accounts().stream().anyMatch(deleted -> live.accounts().stream()
                            .anyMatch(account -> Usernames.same(account.username(), deleted.username())));
                    if (conflict) break;
                }
            }
            if (!conflict) {
                restoreFromTrashNow(trashId, result);
                return;
            }
            BackgroundExecutor.onGameThread(() -> navigation.openRestoreConflict(parent,
                    () -> restoreFromTrashNow(trashId, result)));
        }, () -> BackgroundExecutor.onGameThread(() -> VaultRuntime.toast("blockvault.vault.error")));
    }

    private void restoreFromTrashNow(String trashId, Consumer<List<TrashRow>> result) {
        changeTrash(result, vault -> VaultTrash.restore(vault, trashId, System.currentTimeMillis()));
    }

    void deleteFromTrash(String trashId, Consumer<List<TrashRow>> result) {
        changeTrash(result, vault -> VaultTrash.deleteForever(vault, trashId));
    }

    void emptyTrash(Consumer<List<TrashRow>> result) { changeTrash(result, VaultTrash::emptyTrash); }

    private void changeTrash(Consumer<List<TrashRow>> result, UnaryOperator<Vault> change) {
        unlock.withVault(() -> {
            Vault current = unlock.store().vault();
            Vault next = change.apply(current);
            if (next != current) saveVault(next);
            List<TrashRow> rows = trashRows();
            BackgroundExecutor.onGameThread(() -> result.accept(rows));
        }, () -> BackgroundExecutor.onGameThread(() -> VaultRuntime.toast("blockvault.vault.error")));
    }

    private List<TrashRow> trashRows() {
        List<TrashRow> rows = new ArrayList<>();
        for (TrashEntry entry : unlock.store().vault().trash()) {
            ServerEntry server = entry.server();
            String host = server.hosts().stream().findFirst().orElse(server.name());
            List<String> users = server.accounts().stream().map(Account::username).toList();
            rows.add(new TrashRow(entry.id(), server.name(), host, users, entry.deletedAt()));
        }
        rows.sort(Comparator.comparingLong(TrashRow::deletedAt).reversed());
        return List.copyOf(rows);
    }

    /** Builds rows on the caller thread and dispatches only the screen change; recovery currently calls inline on game. */
    void showVaultRows(Screen parent) {
        purgeTrash();
        List<VaultRow> rows = vaultRows();
        BackgroundExecutor.onGameThread(() -> navigation.showVaultRows(rows, parent));
    }

    private void refreshVaultRows() {
        BackgroundExecutor.submit(() -> {
            if (unlock.store() == null || !isUnlocked()) return null;
            List<VaultRow> rows = vaultRows();
            BackgroundExecutor.onGameThread(() -> navigation.updateVaultRows(rows));
            return null;
        });
    }

    private List<VaultRow> vaultRows() {
        Vault vault = unlock.store().vault();
        List<VaultRow> result = new ArrayList<>();
        for (ServerEntry server : vault.servers()) {
            String host = server.hosts().stream().findFirst().orElse(server.name());
            for (Account account : server.accounts()) result.add(new VaultRow(server.name(), host, account.username(),
                    account.status(), account.autofill(), account.templateId(), !account.isValidForUse()));
        }
        return List.copyOf(result);
    }

    private void purgeTrash() {
        try { unlock.store().purgeExpiredTrash(); }
        catch (java.io.IOException | RuntimeException failure) {
            VaultRuntime.logger().warn("BlockVault could not purge the trash");
        }
    }

    private boolean isUnlocked() {
        try { unlock.store().vault(); return true; }
        catch (IllegalStateException locked) { return false; }
    }

    private void changeAccount(String host, String username, UnaryOperator<Account> update) {
        Vault current = unlock.store().vault();
        List<ServerEntry> servers = new ArrayList<>(current.servers());
        int serverIndex = indexOfServer(servers, host);
        ServerEntry server = serverIndex < 0
                ? new ServerEntry(UUID.randomUUID().toString(), host, List.of(host), List.of())
                : servers.get(serverIndex);
        List<Account> accounts = new ArrayList<>(server.accounts());
        int accountIndex = -1;
        Account existing = null;
        for (int index = 0; index < accounts.size(); index++) {
            if (Usernames.same(accounts.get(index).username(), username)) {
                accountIndex = index;
                existing = accounts.get(index);
                break;
            }
        }
        Account replacement = update.apply(existing);
        if (replacement != null && existing != null && !replacement.username().equals(existing.username())) {
            replacement = replacement.withUsername(existing.username());
        }
        if (replacement == null && accountIndex < 0 && serverIndex < 0) return;
        if (replacement == null && accountIndex >= 0) accounts.remove(accountIndex);
        else if (accountIndex >= 0) accounts.set(accountIndex, replacement);
        else if (replacement != null) accounts.add(replacement);
        ServerEntry updated = server.withAccounts(accounts);
        if (serverIndex < 0) servers.add(updated); else servers.set(serverIndex, updated);
        saveVault(current.withServers(servers));
    }

    private void saveVault(Vault next) {
        try { unlock.store().saveExplicit(next); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Could not save the local vault", failure); }
    }

    private static ServerEntry findServer(Vault vault, String host) {
        return vault.servers().stream().filter(server -> server.hosts().contains(host)).findFirst().orElse(null);
    }

    private Account findAccount(String host, String username) {
        ServerEntry server = findServer(unlock.store().vault(), host);
        return server == null ? null : server.accounts().stream()
                .filter(account -> Usernames.same(account.username(), username)).findFirst().orElse(null);
    }

    private static int indexOfServer(List<ServerEntry> servers, String host) {
        for (int index = 0; index < servers.size(); index++) {
            if (servers.get(index).hosts().contains(host)) return index;
        }
        return -1;
    }

    private static Account copyAccount(Account source) { return source.copy(); }

    /** A verified, just-used account holding the given password; an existing account keeps its settings. */
    private static Account verifiedWith(Account old, String username, char[] password) {
        long now = System.currentTimeMillis();
        return old == null ? new Account(username, password, Account.Status.VERIFIED, now, now, now)
                : old.withCredential(password, Account.Status.VERIFIED, now, now);
    }
}
