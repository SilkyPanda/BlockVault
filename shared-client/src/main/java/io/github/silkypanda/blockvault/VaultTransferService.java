package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.vault.BackupReencryptionException;
import io.github.silkypanda.blockvault.core.vault.BackupSummary;
import io.github.silkypanda.blockvault.core.vault.DataDirs;
import io.github.silkypanda.blockvault.core.vault.ImportPreview;
import io.github.silkypanda.blockvault.core.vault.UnsupportedVaultVersionException;
import io.github.silkypanda.blockvault.core.vault.Vault;
import io.github.silkypanda.blockvault.core.vault.VaultCorruptException;
import io.github.silkypanda.blockvault.core.vault.WrongMasterPasswordException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Owns backup listing, recovery previews, import/export, and master-password rotation workflows.
 * Entry points may be invoked by game-thread screens; encrypted file work runs on the IO executor and
 * result callbacks return to the game thread.
 */
final class VaultTransferService {
    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);
    private final VaultUnlockCoordinator unlock;

    VaultTransferService(VaultUnlockCoordinator unlock) { this.unlock = unlock; }

    void listRecoveryBackups(Consumer<List<BackupSummary>> result) {
        BackgroundExecutor.submit(() -> {
            List<BackupSummary> available;
            try { available = io.github.silkypanda.blockvault.core.vault.VaultStore.availableBackups(DataDirs.resolve()); }
            catch (IOException failure) { available = List.of(); }
            List<BackupSummary> completed = available;
            BackgroundExecutor.onGameThread(() -> result.accept(completed));
            return null;
        });
    }

    void listBackups(Consumer<List<BackupSummary>> result) {
        unlock.withVault(() -> {
            List<BackupSummary> backups;
            try { backups = unlock.store().backups(); }
            catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault backup listing failed ({})", failure.getClass().getSimpleName());
                backups = List.of();
            }
            List<BackupSummary> available = backups;
            BackgroundExecutor.onGameThread(() -> result.accept(available));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(List.of())));
    }

    void createManualBackup(Consumer<String> result) {
        unlock.withVault(() -> {
            String outcome;
            try { outcome = unlock.store().createManualBackup() ? null : "blockvault.backup.empty.not_created"; }
            catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault manual backup failed ({})", failure.getClass().getSimpleName());
                outcome = "blockvault.backup.failed";
            }
            String completed = outcome;
            BackgroundExecutor.onGameThread(() -> result.accept(completed));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept("blockvault.backup.failed")));
    }

    void deleteBackup(BackupSummary backup, Consumer<Boolean> result) {
        unlock.withVault(() -> {
            boolean deleted;
            try { deleted = unlock.store().deleteBackup(backup); }
            catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault backup deletion failed ({})", failure.getClass().getSimpleName());
                deleted = false;
            }
            boolean completed = deleted;
            BackgroundExecutor.onGameThread(() -> result.accept(completed));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(false)));
    }

    void inspectBackup(BackupSummary backup, Consumer<Vault> result) {
        unlock.withVault(() -> {
            Vault contents;
            try { contents = unlock.store().inspectBackup(backup); }
            catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault backup inspection failed ({})", failure.getClass().getSimpleName());
                contents = null;
            }
            Vault inspected = contents;
            BackgroundExecutor.onGameThread(() -> result.accept(inspected));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(null)));
    }

    void inspectBackup(BackupSummary backup, char[] backupPassword, Consumer<Vault> result) {
        char[] secret = backupPassword.clone();
        Arrays.fill(backupPassword, '\0');
        unlock.withVault(() -> {
            Vault contents;
            try { contents = unlock.store().inspectBackup(backup, secret); }
            catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault could not inspect the backup with the entered password ({})",
                        failure.getClass().getSimpleName());
                contents = null;
            } finally { Arrays.fill(secret, '\0'); }
            Vault inspected = contents;
            BackgroundExecutor.onGameThread(() -> result.accept(inspected));
        }, () -> {
            Arrays.fill(secret, '\0');
            BackgroundExecutor.onGameThread(() -> result.accept(null));
        });
    }

    void restoreBackup(BackupSummary backup, Consumer<Boolean> result) {
        unlock.withVault(() -> {
            boolean success;
            try { unlock.store().restoreBackup(backup); success = true; }
            catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault backup restore failed ({})", failure.getClass().getSimpleName());
                success = false;
            }
            boolean completed = success;
            BackgroundExecutor.onGameThread(() -> result.accept(completed));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(false)));
    }

    void restoreBackup(BackupSummary backup, char[] backupPassword, Consumer<Boolean> result) {
        char[] secret = backupPassword.clone();
        Arrays.fill(backupPassword, '\0');
        unlock.withVault(() -> {
            boolean success;
            try { unlock.store().restoreBackup(backup, secret); success = true; }
            catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault backup restore failed ({})", failure.getClass().getSimpleName());
                success = false;
            } finally { Arrays.fill(secret, '\0'); }
            boolean completed = success;
            BackgroundExecutor.onGameThread(() -> result.accept(completed));
        }, () -> {
            Arrays.fill(secret, '\0');
            BackgroundExecutor.onGameThread(() -> result.accept(false));
        });
    }

    void changeMasterPassword(char[] oldPassword, char[] newPassword, Consumer<String> result) {
        unlock.withVault(() -> {
            String failureKey = null;
            boolean lockAfterChange = false;
            try {
                unlock.store().changeMasterPassword(oldPassword, newPassword);
                lockAfterChange = true;
            } catch (Exception failure) {
                lockAfterChange = failure instanceof BackupReencryptionException
                        || failure instanceof io.github.silkypanda.blockvault.core.vault.TrustedDeviceUpdateException;
                failureKey = masterPasswordFailureKey(failure);
                VaultRuntime.logger().warn("BlockVault master password change failed ({})",
                        failure.getClass().getSimpleName());
            } finally {
                Arrays.fill(oldPassword, '\0');
                Arrays.fill(newPassword, '\0');
            }
            if (lockAfterChange) {
                unlock.store().lock();
                unlock.markTrustedUnlockAttempted();
            }
            String outcome = failureKey;
            boolean wasLocked = lockAfterChange;
            BackgroundExecutor.onGameThread(() -> {
                if (wasLocked) VaultRuntime.toast("blockvault.master.change.locked");
                result.accept(outcome);
            });
        }, () -> {
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
            BackgroundExecutor.onGameThread(() -> result.accept("blockvault.vault.error"));
        });
    }

    private static String masterPasswordFailureKey(Exception failure) {
        return failure instanceof WrongMasterPasswordException ? "blockvault.master.current.wrong"
                : failure instanceof io.github.silkypanda.blockvault.core.vault.TrustedDeviceUpdateException
                        ? "blockvault.master.change.trust_failed"
                : failure instanceof BackupReencryptionException ? "blockvault.master.change.backups_failed"
                : failure instanceof IllegalArgumentException ? "blockvault.master.new.invalid"
                : failure instanceof IOException ? "blockvault.master.change.io" : "blockvault.master.failed";
    }

    void exportVault(Path folder, Consumer<Path> result) {
        unlock.withVault(() -> {
            Path exported = null;
            try {
                Path directory = folder == null ? DataDirs.resolve().resolve("exports") : folder.toAbsolutePath().normalize();
                java.nio.file.Files.createDirectories(directory);
                String timestamp = EXPORT_TIMESTAMP.format(Instant.now());
                Path destination = directory.resolve("blockvault-export-" + timestamp + ".bv");
                for (int suffix = 2; java.nio.file.Files.exists(destination); suffix++) {
                    destination = directory.resolve("blockvault-export-" + timestamp + "-" + suffix + ".bv");
                }
                unlock.store().exportEncrypted(destination);
                exported = destination;
            } catch (IOException failure) {
                VaultRuntime.logger().warn("BlockVault could not export the encrypted vault");
            }
            Path completed = exported;
            BackgroundExecutor.onGameThread(() -> result.accept(completed));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(null)));
    }

    void previewImport(Path source, char[] masterPassword, BiConsumer<ImportPreview, String> result) {
        char[] password = masterPassword.clone();
        Arrays.fill(masterPassword, '\0');
        unlock.withVault(() -> {
            ImportPreview preview = null;
            String failureKey = null;
            try { preview = unlock.store().previewImport(source, password); }
            catch (WrongMasterPasswordException wrong) { failureKey = "blockvault.transfer.wrong_password"; }
            catch (UnsupportedVaultVersionException | VaultCorruptException unreadable) {
                failureKey = "blockvault.transfer.corrupt";
            } catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault could not read the import file ({})",
                        failure.getClass().getSimpleName());
                failureKey = "blockvault.transfer.failed";
            } finally { Arrays.fill(password, '\0'); }
            ImportPreview shown = preview;
            String key = failureKey;
            BackgroundExecutor.onGameThread(() -> result.accept(shown, key));
        }, () -> {
            Arrays.fill(password, '\0');
            BackgroundExecutor.onGameThread(() -> result.accept(null, "blockvault.transfer.failed"));
        });
    }

    void commitImport(Consumer<Boolean> result) {
        unlock.withVault(() -> {
            boolean success = false;
            try { unlock.store().commitImport(); success = true; }
            catch (Exception failure) {
                VaultRuntime.logger().warn("BlockVault could not apply the import ({})",
                        failure.getClass().getSimpleName());
            }
            boolean applied = success;
            BackgroundExecutor.onGameThread(() -> result.accept(applied));
        }, () -> BackgroundExecutor.onGameThread(() -> result.accept(false)));
    }

    void discardImport() {
        BackgroundExecutor.submit(() -> {
            if (unlock.store() != null) unlock.store().discardImport();
            return null;
        });
    }
}
