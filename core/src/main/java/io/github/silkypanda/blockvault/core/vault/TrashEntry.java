package io.github.silkypanda.blockvault.core.vault;

/**
 * One item in the vault's trash: either a single deleted account or a whole deleted server.
 *
 * <p>{@code server} is a snapshot of the server entry as it was when the deletion happened. For a single
 * deleted account it holds exactly that account; for a deleted server it holds every account the server had.
 * Passwords stay encrypted inside the vault file like any other entry.
 */
public record TrashEntry(String id, long deletedAt, boolean wholeServer, ServerEntry server) {
    public TrashEntry {
        if (id == null || id.isBlank() || server == null) {
            throw new IllegalArgumentException("Trash entry fields must not be null");
        }
    }
}
