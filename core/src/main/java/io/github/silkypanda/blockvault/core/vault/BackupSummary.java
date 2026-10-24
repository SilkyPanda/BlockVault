package io.github.silkypanda.blockvault.core.vault;

/** Metadata for one encrypted automatic or manual backup. */
public record BackupSummary(boolean manual, int slot, long createdAt, long sizeBytes) {
}
