package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.SavedCredential;
import io.github.silkypanda.blockvault.core.session.VaultAccess;
import io.github.silkypanda.blockvault.core.vault.BackupSummary;
import io.github.silkypanda.blockvault.core.vault.ImportPreview;
import io.github.silkypanda.blockvault.core.vault.Vault;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thin compatibility facade used by screens and Hooks; implementation state lives in instance collaborators. */
public final class VaultRuntime implements VaultAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockVault");
    private final VaultScreenNavigation navigation = new VaultScreenNavigation();
    private final VaultUnlockCoordinator unlock = new VaultUnlockCoordinator(navigation);
    private final VaultAccountService accounts = new VaultAccountService(unlock, navigation);
    private final VaultTransferService transfers = new VaultTransferService(unlock);

    private VaultRuntime() { unlock.bindVaultRowsOperation(accounts::showVaultRows); }

    private static VaultRuntime runtime() { return Holder.INSTANCE; }

    /** Returns the shared vault access facade. */
    public static VaultRuntime access() { return runtime(); }

    static Logger logger() { return LOGGER; }

    @Override public void find(String host, String username, Consumer<SavedCredential> result) {
        accounts.find(host, username, result);
    }

    @Override public void find(String host, String username, Consumer<SavedCredential> result, Runnable cancelled) {
        accounts.find(host, username, result, cancelled);
    }

    @Override public void savePending(String host, String username, char[] password, Consumer<Boolean> saved) {
        accounts.savePending(host, username, password, saved);
    }

    @Override public void markVerified(String host, String username) { accounts.markVerified(host, username); }

    @Override public void delete(String host, String username) { accounts.delete(host, username); }

    @Override public void delete(String host, String username, Consumer<Boolean> result) {
        accounts.delete(host, username, result);
    }

    @Override public void saveVerified(String host, String username, char[] password) {
        accounts.saveVerified(host, username, password);
    }

    @Override public void saveVerified(String host, String username, char[] password, Consumer<Boolean> result) {
        accounts.saveVerified(host, username, password, result);
    }

    /** Creates or unlocks a master-password vault, then resumes operations waiting on it. */
    public static void openWithMasterPassword(char[] password, boolean create, boolean trustOnCreate) {
        runtime().unlock.openWithMasterPassword(password, create, trustOnCreate);
    }

    static void deliverPendingTrustNotice() { runtime().unlock.deliverPendingTrustNotice(); }

    /** Allows a dismissed access screen to be requested again on the next vault operation. */
    public static void accessScreenClosed() { runtime().unlock.accessScreenClosed(); }

    /** Called when another screen replaces the vault access screen. */
    public static void accessScreenReplaced() { runtime().unlock.accessScreenReplaced(); }

    public static void clearWaiting() { runtime().unlock.clearWaiting(); }

    /** Opens the vault view if unlocked, otherwise asks for its master password. */
    public static void openVaultScreen() { openVaultScreen(null); }

    /** Opens the vault and returns to the caller's settings screen when the vault view is closed. */
    public static void openVaultScreen(Screen parent) { runtime().unlock.openVaultScreen(parent); }

    static void openBackupScreen(Screen parent) { runtime().navigation.openBackupScreen(parent); }

    static void openRecoveryScreen(Screen parent) { runtime().navigation.openRecoveryScreen(parent); }

    static void listRecoveryBackups(Consumer<List<BackupSummary>> result) {
        runtime().transfers.listRecoveryBackups(result);
    }

    static void recoverDamagedVault(BackupSummary backup, char[] password, Consumer<String> result) {
        runtime().unlock.recoverDamagedVault(backup, password, result);
    }

    static void openMasterPasswordScreen(Screen parent) { runtime().navigation.openMasterPasswordScreen(parent); }

    /** Erases data after the explicit warning/confirmation flow, including when the master password is forgotten. */
    static void eraseAllDataWithoutPassword(Consumer<String> result) {
        runtime().unlock.eraseAllDataWithoutPassword(result);
    }

    static boolean hasVaultFile() { return runtime().unlock.hasVaultFile(); }

    static void openImportScreen(Screen parent) { runtime().unlock.openImportScreen(parent); }

    static void listBackups(Consumer<List<BackupSummary>> result) { runtime().transfers.listBackups(result); }

    static void createManualBackup(Consumer<String> result) { runtime().transfers.createManualBackup(result); }

    static void deleteBackup(BackupSummary backup, Consumer<Boolean> result) {
        runtime().transfers.deleteBackup(backup, result);
    }

    static void inspectBackup(BackupSummary backup, Consumer<Vault> result) {
        runtime().transfers.inspectBackup(backup, result);
    }

    static void inspectBackup(BackupSummary backup, char[] backupPassword, Consumer<Vault> result) {
        runtime().transfers.inspectBackup(backup, backupPassword, result);
    }

    static void restoreBackup(BackupSummary backup, Consumer<Boolean> result) {
        runtime().transfers.restoreBackup(backup, result);
    }

    static void restoreBackup(BackupSummary backup, char[] backupPassword, Consumer<Boolean> result) {
        runtime().transfers.restoreBackup(backup, backupPassword, result);
    }

    static void changeMasterPassword(char[] oldPassword, char[] newPassword, Consumer<String> result) {
        runtime().transfers.changeMasterPassword(oldPassword, newPassword, result);
    }

    /** Locks and wipes the in-memory vault key. */
    public static void lock() { runtime().unlock.lock(); }

    /** Creates the settings screen for loader-provided config-screen entrypoints. */
    public static Screen createSettingsScreen(Screen parent) {
        return VaultSettingsScreenFactory.create(parent);
    }

    static void revealPassword(VaultRow row, Consumer<char[]> result) { runtime().accounts.revealPassword(row, result); }

    static void copyPassword(VaultRow row) { runtime().accounts.copyPassword(row); }

    /** Copies a caller-provided password to the system clipboard. */
    static void copyToClipboard(String clipboardValue) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(clipboardValue);
        toast("blockvault.toast.copied");
    }

    static void exportVault(Path folder, Consumer<Path> result) { runtime().transfers.exportVault(folder, result); }

    static void previewImport(Path source, char[] masterPassword,
                              BiConsumer<ImportPreview, String> result) {
        runtime().transfers.previewImport(source, masterPassword, result);
    }

    static void commitImport(Consumer<Boolean> result) { runtime().transfers.commitImport(result); }

    static void discardImport() { runtime().transfers.discardImport(); }

    static void addAccount(String hostText, String username, char[] password, Consumer<String> result) {
        runtime().accounts.addAccount(hostText, username, password, result);
    }

    static void updatePassword(VaultRow row, char[] password) { runtime().accounts.updatePassword(row, password); }

    static void updateServerAutofill(VaultRow row, boolean autofill) {
        runtime().accounts.updateAutofill(row, autofill);
    }

    static void updateServerTemplate(VaultRow row, String templateId) {
        runtime().accounts.updateTemplate(row, templateId);
    }

    static void deleteAccount(VaultRow row) { runtime().accounts.deleteAccount(row); }

    static void openTrashScreen(Screen parent) { runtime().accounts.openTrashScreen(parent); }

    static void restoreFromTrash(String trashId, Screen parent, Consumer<List<TrashRow>> result) {
        runtime().accounts.restoreFromTrash(trashId, parent, result);
    }

    static void deleteFromTrash(String trashId, Consumer<List<TrashRow>> result) {
        runtime().accounts.deleteFromTrash(trashId, result);
    }

    static void emptyTrash(Consumer<List<TrashRow>> result) { runtime().accounts.emptyTrash(result); }

    static void showToast(String key) { toast(key); }

    /** Applies the explicit local-device trust preference without retaining the master password. */
    static void setTrustedDevice(boolean trusted) { runtime().unlock.setTrustedDevice(trusted); }

    static void toast(String key) {
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
