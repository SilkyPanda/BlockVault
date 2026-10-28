package io.github.silkypanda.blockvault;

import java.util.List;

/** One trash entry as shown on the trash screen; contains no passwords. */
record TrashRow(String id, String serverName, String host, List<String> usernames, long deletedAt) {
    TrashRow {
        usernames = List.copyOf(usernames);
    }

    /** Whole days left before this entry is removed automatically (never negative). */
    long daysLeft(long nowMillis) {
        long retention = io.github.silkypanda.blockvault.core.vault.Vault.TRASH_RETENTION_MILLIS;
        long remaining = retention - (nowMillis - deletedAt);
        return Math.max(0, (remaining + 86_399_999L) / 86_400_000L);
    }
}
