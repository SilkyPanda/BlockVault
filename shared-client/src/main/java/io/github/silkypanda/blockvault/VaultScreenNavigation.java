package io.github.silkypanda.blockvault;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Owns deferred vault-screen destinations and performs Minecraft screen changes.
 * Call screen methods on the game thread; access-screen request flags are coordinated with IO callbacks.
 */
final class VaultScreenNavigation {
    private volatile boolean accessScreenOpen;
    private volatile boolean openVaultAfterAccess;
    private volatile boolean openImportAfterAccess;
    private volatile Screen vaultReturnScreen;

    void rememberReturnScreen(Screen parent) { vaultReturnScreen = parent; }
    Screen returnScreen() { return vaultReturnScreen; }
    void clearReturnScreen() { vaultReturnScreen = null; }
    void clearReturnScreenIf(Screen expected) { if (vaultReturnScreen == expected) vaultReturnScreen = null; }

    boolean isAccessScreenOpen() { return accessScreenOpen; }
    void markAccessScreenOpen() { accessScreenOpen = true; }
    void markAccessScreenClosed() { accessScreenOpen = false; }
    boolean openVaultAfterAccess() { return openVaultAfterAccess; }
    boolean openImportAfterAccess() { return openImportAfterAccess; }
    void requestVaultAfterAccess() { openVaultAfterAccess = true; }
    void requestImportAfterAccess() { openImportAfterAccess = true; }
    void clearVaultRequest() { openVaultAfterAccess = false; }
    void clearDeferredDestinations() {
        openVaultAfterAccess = false;
        openImportAfterAccess = false;
    }

    void requestAccessScreen(boolean create) {
        if (accessScreenOpen) return;
        accessScreenOpen = true;
        BackgroundExecutor.onGameThread(() -> ScreenSwitcher.show(
                new VaultAccessScreen(create, vaultReturnScreen)));
    }

    void openBackupScreen(Screen parent) {
        BackgroundExecutor.onGameThread(() -> ScreenSwitcher.show(new VaultRecoveryScreen(parent)));
    }

    void openRecoveryScreen(Screen parent) {
        BackgroundExecutor.onGameThread(() -> ScreenSwitcher.show(new VaultRecoveryScreen(parent, true)));
    }

    void openMasterPasswordScreen(Screen parent) {
        BackgroundExecutor.onGameThread(() -> ScreenSwitcher.show(new VaultMasterPasswordScreen(parent)));
    }

    void openImportScreen(Screen parent) {
        ScreenSwitcher.show(new VaultTransferScreen(parent, true));
    }

    void openTrashScreen(Screen parent, List<TrashRow> rows) {
        ScreenSwitcher.show(new VaultTrashScreen(parent, rows));
    }

    void openRestoreConflict(Screen parent, Runnable overwrite) {
        ScreenSwitcher.show(new VaultConfirmScreen(parent,
                "blockvault.trash.restore.conflict.title", "blockvault.trash.restore.conflict.message",
                "blockvault.trash.restore.overwrite", overwrite));
    }

    void showVaultRows(List<VaultRow> rows, Screen parent) {
        ScreenSwitcher.show(new VaultListScreen(rows, parent));
        clearReturnScreenIf(parent);
    }

    void updateVaultRows(List<VaultRow> rows) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof VaultListScreen list) list.updateRows(rows);
    }

    void closeAccessScreen() {
        Screen current = Minecraft.getInstance().gui.screen();
        if (current instanceof VaultAccessScreen) ScreenSwitcher.show(null);
    }

    /** Leaves a BlockVault screen after locking by going back to the screen that opened it. */
    void closeManagedScreen() {
        Screen current = Minecraft.getInstance().gui.screen();
        if (current instanceof BlockVaultScreen own) ScreenSwitcher.show(own.parentScreen());
        else if (BlockVaultScreen.isBlockVaultScreen(current)) ScreenSwitcher.show(null);
    }
}
