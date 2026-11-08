package io.github.silkypanda.blockvault.core.vault;

/**
 * One deleted account in the vault's trash, with the server needed to restore it.
 *
 * <p>{@code server} is a snapshot of the server entry as it was when the deletion happened and holds one account.
 * Passwords stay encrypted inside the vault file like any other entry.
 */
public record TrashEntry(String id, long deletedAt, ServerEntry server) {
    public TrashEntry {
        if (id == null || id.isBlank() || server == null) {
            throw new IllegalArgumentException("Trash entry fields must not be null");
        }
    }
}
