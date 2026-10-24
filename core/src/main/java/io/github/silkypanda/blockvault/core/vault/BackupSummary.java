package io.github.silkypanda.blockvault.core.vault;

/** Metadata for one encrypted automatic or manual backup. */
public record BackupSummary(boolean manual, int slot, long createdAt, long sizeBytes) {
    public BackupSummary(boolean manual, int slot, long createdAt) { this(manual, slot, createdAt, 0); }
}
