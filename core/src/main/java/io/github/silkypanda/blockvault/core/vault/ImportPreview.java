package io.github.silkypanda.blockvault.core.vault;

import java.util.List;

/** What importing a vault file would change. Contains no passwords. */
public record ImportPreview(List<Change> changes) {
    /** Kinds of change, in the order they are shown. */
    public enum Kind { NEW_SERVER, NEW_ACCOUNT, UPDATED_ACCOUNT, KEPT_LOCAL, NEW_TRASH }

    /** One line of the preview; {@code username} is null for whole-server and trash lines. */
    public record Change(Kind kind, String server, String username) { }

    public ImportPreview {
        changes = List.copyOf(changes);
    }

    /** Returns how many changes of the given kind there are. */
    public long count(Kind kind) { return changes.stream().filter(change -> change.kind() == kind).count(); }

    /** True when importing would not change anything that is visible. */
    public boolean isEmpty() { return changes.isEmpty(); }
}
