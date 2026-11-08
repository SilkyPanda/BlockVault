package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.vault.BackupSummary;
import io.github.silkypanda.blockvault.core.vault.DataDirs;
import io.github.silkypanda.blockvault.core.vault.UnsupportedVaultVersionException;
import io.github.silkypanda.blockvault.core.vault.VaultCorruptException;
import io.github.silkypanda.blockvault.core.vault.VaultStore;
import io.github.silkypanda.blockvault.core.vault.WrongMasterPasswordException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Owns the background-confined store, unlock/trust state, and operations queued until unlock.
 * Public entry points may be called from the game thread; storage work stays on the IO executor and
 * screen/session callbacks are returned to the game thread. The pre-existing damaged-vault recovery
 * completion path refreshes vault rows inline on the game thread; this structural move preserves it.
 */
final class VaultUnlockCoordinator {
    private static final int MAX_WAITING = 32;

    private record Waiting(Runnable action, Runnable cancelled) { }

    private static final class UnlockState {
        private volatile boolean trustedUnlockAttempted;
        private volatile boolean pendingTrustNotice;
        private volatile boolean vaultUnreadable;
    }

    private final VaultScreenNavigation navigation;
    private final ConcurrentLinkedDeque<Waiting> waiting = new ConcurrentLinkedDeque<>();
    private final UnlockState state = new UnlockState();
    private VaultStore store;
    private Consumer<Screen> showVaultRows;

    VaultUnlockCoordinator(VaultScreenNavigation navigation) {
        this.navigation = navigation;
    }

    VaultStore store() { return store; }

    void setStore(VaultStore next) { store = next; }

    void markTrustedUnlockAttempted() { state.trustedUnlockAttempted = true; }

    void bindVaultRowsOperation(Consumer<Screen> operation) { showVaultRows = operation; }

    void ensureStore() throws IOException, UnsupportedVaultVersionException, VaultCorruptException {
        if (store == null) store = VaultStore.open(DataDirs.resolve());
    }

    void openWithMasterPassword(char[] password, boolean create, boolean trustOnCreate) {
        BlockVaultConfig.load(settings -> BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                boolean trustDevice = create ? trustOnCreate : settings.trustDevice();
                if (create) BlockVaultConfig.saveTrustDeviceChoice(trustDevice);
                if (create) store.create(password);
                else {
                    store.unlock(password);
                    state.vaultUnreadable = false;
                }
                state.trustedUnlockAttempted = true;
                boolean[] trustReady = {false};
                if (trustDevice) writeTrustedDeviceKey(trustReady);
                navigation.markAccessScreenClosed();
                List<Waiting> resumed = new ArrayList<>(waiting);
                waiting.clear();
                boolean showVaultAfterUnlock = navigation.openVaultAfterAccess() && resumed.isEmpty();
                boolean showImportAfterAccess = navigation.openImportAfterAccess() && resumed.isEmpty();
                navigation.clearDeferredDestinations();
                BackgroundExecutor.onGameThread(() -> finishUnlock(create, trustReady[0],
                        showImportAfterAccess, showVaultAfterUnlock, resumed));
            } catch (Exception failure) {
                handleUnlockFailure(failure, create);
            } finally {
                Arrays.fill(password, '\0');
            }
            return null;
        }));
    }

    private void writeTrustedDeviceKey(boolean[] trustReady) {
        try {
            store.trustThisDevice();
            trustReady[0] = true;
        } catch (IOException failure) {
            VaultRuntime.logger().warn("BlockVault could not save the trusted-device key");
            BackgroundExecutor.onGameThread(() -> VaultRuntime.toast("blockvault.toast.trust_failed"));
        }
    }

    private void finishUnlock(boolean create, boolean trustReady, boolean showImport,
                              boolean showVault, List<Waiting> resumed) {
        VaultRuntime.toast("blockvault.toast.unlocked");
        if (create && trustReady) showTrustedDeviceNotice();
        // When another BlockVault screen follows, it replaces the unlock screen directly. Closing the unlock
        // screen first would flash the game (or a rebuilt title screen) for a few frames in between.
        if (showImport) {
            net.minecraft.client.gui.screens.Screen parent = navigation.returnScreen();
            navigation.clearReturnScreen();
            navigation.openImportScreen(parent);
        } else if (showVault) {
            BackgroundExecutor.submit(() -> {
                try { showVaultRows.accept(navigation.returnScreen()); }
                catch (RuntimeException failure) {
                    BackgroundExecutor.onGameThread(navigation::closeAccessScreen);
                }
                return null;
            });
        } else {
            navigation.closeAccessScreen();
        }
        BackgroundExecutor.submit(() -> {
            while (!resumed.isEmpty()) {
                try { resumed.remove(0).action().run(); }
                catch (RuntimeException failure) {
                    VaultRuntime.logger().warn("BlockVault queued operation failed ({})",
                            failure.getClass().getSimpleName());
                }
            }
            return null;
        });
    }

    private void handleUnlockFailure(Exception failure, boolean create) {
        state.vaultUnreadable = !create && (failure instanceof VaultCorruptException
                || failure instanceof UnsupportedVaultVersionException);
        String key = failure instanceof WrongMasterPasswordException ? "blockvault.vault.unlock.wrong_password"
                : failure instanceof UnsupportedVaultVersionException ? "blockvault.vault.unlock.unsupported"
                : failure instanceof VaultCorruptException ? "blockvault.vault.unlock.corrupt"
                : create ? "blockvault.vault.create.failed" : "blockvault.vault.unlock.failed";
        BackgroundExecutor.onGameThread(() -> {
            if (Minecraft.getInstance().gui.screen() instanceof VaultAccessScreen screen) screen.showFailure(key);
        });
    }

    void showTrustedDeviceNotice() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() == null || minecraft.gui == null || minecraft.gui.hud == null) {
            state.pendingTrustNotice = true;
            return;
        }
        state.pendingTrustNotice = false;
        minecraft.gui.hud.getChat().addClientSystemMessage(
                Component.literal("[BlockVault] ").withStyle(Style.EMPTY.withColor(0xc58cff))
                        .append(Component.translatable("blockvault.chat.trusted_device_notice")
                                .withStyle(Style.EMPTY.withColor(0xffffff))));
    }

    void deliverPendingTrustNotice() {
        if (!state.pendingTrustNotice) return;
        BackgroundExecutor.scheduler().schedule(1500, () -> {
            if (!state.pendingTrustNotice || Minecraft.getInstance().getCurrentServer() == null) return;
            showTrustedDeviceNotice();
        });
    }

    void accessScreenClosed() {
        navigation.markAccessScreenClosed();
        navigation.clearReturnScreen();
        navigation.clearDeferredDestinations();
        cancelWaiting();
    }

    void accessScreenReplaced() { navigation.markAccessScreenClosed(); }

    void clearWaiting() {
        cancelWaiting();
        navigation.markAccessScreenClosed();
    }

    private void cancelWaiting() {
        List<Waiting> dropped = new ArrayList<>(waiting);
        waiting.clear();
        for (Waiting operation : dropped) {
            if (operation.cancelled() == null) continue;
            try { operation.cancelled().run(); }
            catch (RuntimeException failure) {
                VaultRuntime.logger().warn("BlockVault cancel callback failed ({})",
                        failure.getClass().getSimpleName());
            }
        }
    }

    void openVaultScreen(net.minecraft.client.gui.screens.Screen parent) {
        navigation.rememberReturnScreen(parent);
        BlockVaultConfig.load(settings -> BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                if (!store.exists()) {
                    navigation.requestVaultAfterAccess();
                    navigation.requestAccessScreen(true);
                } else if (!isUnlocked() && !tryTrustedUnlock(settings)) {
                    navigation.requestVaultAfterAccess();
                    navigation.requestAccessScreen(false);
                } else {
                    navigation.clearVaultRequest();
                    showVaultRows.accept(navigation.returnScreen());
                }
            } catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault could not open its local vault ({})",
                        failure.getClass().getSimpleName());
                String key = failure instanceof UnsupportedVaultVersionException ? "blockvault.vault.unlock.unsupported"
                        : failure instanceof VaultCorruptException ? "blockvault.vault.unlock.corrupt"
                        : "blockvault.vault.error";
                BackgroundExecutor.onGameThread(() -> VaultRuntime.toast(key));
            }
            return null;
        }));
    }

    void recoverDamagedVault(BackupSummary backup, char[] password, Consumer<String> result) {
        BlockVaultConfig.load(settings -> BackgroundExecutor.submit(() -> {
            String failureKey = null;
            try {
                VaultStore.restoreFromBackup(DataDirs.resolve(), backup, password);
                store = VaultStore.open(DataDirs.resolve());
                store.unlock(password);
                state.trustedUnlockAttempted = true;
                if (settings.trustDevice()) writeRecoveredTrust();
            } catch (Exception failure) {
                failureKey = recoveryFailureKey(failure);
                VaultRuntime.logger().warn("BlockVault backup recovery failed ({})",
                        failure.getClass().getSimpleName());
            } finally {
                Arrays.fill(password, '\0');
            }
            String completed = failureKey;
            BackgroundExecutor.onGameThread(() -> {
                result.accept(completed);
                if (completed == null) showVaultRows.accept(navigation.returnScreen());
            });
            return null;
        }));
    }

    private void writeRecoveredTrust() {
        try { store.trustThisDevice(); }
        catch (IOException failure) {
            VaultRuntime.logger().warn("BlockVault recovered the vault but could not save the trusted-device key");
            BackgroundExecutor.onGameThread(() -> VaultRuntime.toast("blockvault.toast.trust_failed"));
        }
    }

    private static String recoveryFailureKey(Exception failure) {
        return failure instanceof WrongMasterPasswordException ? "blockvault.vault.unlock.wrong_password"
                : failure instanceof UnsupportedVaultVersionException ? "blockvault.vault.unlock.unsupported"
                : failure instanceof VaultCorruptException ? "blockvault.vault.unlock.corrupt"
                : "blockvault.backup.recovery.failed";
    }

    void eraseAllDataWithoutPassword(Consumer<String> result) {
        BackgroundExecutor.submit(() -> {
            String failureKey = null;
            try {
                java.nio.file.Path directory = DataDirs.resolve();
                if (store != null) {
                    store.lock();
                    store = null;
                }
                DataDirs.delete(directory);
                state.trustedUnlockAttempted = false;
            } catch (Exception failure) {
                failureKey = "blockvault.data.erase.failed";
                VaultRuntime.logger().warn("BlockVault data erasure failed ({})",
                        failure.getClass().getSimpleName());
            }
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

    boolean hasVaultFile() {
        try { return java.nio.file.Files.isRegularFile(DataDirs.resolve().resolve("vault.bv")); }
        catch (RuntimeException inaccessible) { return false; }
    }

    void openImportScreen(net.minecraft.client.gui.screens.Screen parent) {
        if (!hasVaultFile()) {
            navigation.requestImportAfterAccess();
            openVaultScreen(parent);
            return;
        }
        navigation.openImportScreen(parent);
    }

    void lock() {
        state.trustedUnlockAttempted = true;
        BackgroundExecutor.submit(() -> {
            if (store != null) store.lock();
            BackgroundExecutor.onGameThread(() -> {
                navigation.closeManagedScreen();
                VaultRuntime.toast("blockvault.toast.locked");
            });
            return null;
        });
    }

    void setTrustedDevice(boolean trusted) {
        if (!trusted) state.trustedUnlockAttempted = true;
        BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                if (trusted) {
                    try { store.vault(); store.trustThisDevice(); }
                    catch (IllegalStateException locked) { /* The next password unlock will write the key. */ }
                } else store.forgetTrustedDevice();
            } catch (Exception failure) {
                VaultRuntime.logger().warn(trusted ? "BlockVault could not trust this device"
                        : "BlockVault could not remove its trusted-device key");
                BackgroundExecutor.onGameThread(() -> VaultRuntime.toast("blockvault.toast.trust_failed"));
            }
            return null;
        });
    }

    void withVault(Runnable action) {
        withVault(action, () -> BackgroundExecutor.onGameThread(() -> VaultRuntime.toast("blockvault.vault.error")), null);
    }

    void withVault(Runnable action, Runnable failed) { withVault(action, failed, failed); }

    /** Runs the action on the IO executor once unlocked; all failure/cancellation callbacks keep their current thread contract. */
    void withVault(Runnable action, Runnable failed, Runnable cancelled) {
        BlockVaultConfig.load(settings -> BackgroundExecutor.submit(() -> {
            try {
                ensureStore();
                if (!store.exists()) {
                    enqueue(action, cancelled);
                    navigation.requestAccessScreen(true);
                } else if (isUnlocked() || tryTrustedUnlock(settings)) {
                    store.reload();
                    purgeTrash();
                    action.run();
                } else {
                    enqueue(action, cancelled);
                    navigation.requestAccessScreen(false);
                }
            } catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault could not open its local vault ({})",
                        failure.getClass().getSimpleName());
                failed.run();
            }
            return null;
        }));
    }

    private void purgeTrash() {
        try { store.purgeExpiredTrash(); }
        catch (IOException | RuntimeException failure) { VaultRuntime.logger().warn("BlockVault could not purge the trash"); }
    }

    private boolean isUnlocked() {
        try { store.vault(); return true; }
        catch (IllegalStateException locked) { return false; }
    }

    private boolean tryTrustedUnlock(BlockVaultConfig.Settings settings) {
        if (!settings.trustDevice() || state.trustedUnlockAttempted) return false;
        state.trustedUnlockAttempted = true;
        try {
            store.unlockTrustedDevice();
            return true;
        } catch (Exception invalidOrMissingTrustFile) {
            return false;
        }
    }

    private void enqueue(Runnable action, Runnable cancelled) {
        if (waiting.size() >= MAX_WAITING) {
            Waiting evicted = waiting.pollFirst();
            if (evicted != null && evicted.cancelled() != null) evicted.cancelled().run();
        }
        waiting.addLast(new Waiting(action, cancelled));
    }
}
