package io.github.silkypanda.blockvault.core.vault;

/** Identifies how the random vault key is protected. */
public enum KeyMode {
    DEVICE(1),
    MASTER_PASSWORD(2);

    private final int id;

    KeyMode(int id) {
        this.id = id;
    }

    /** Returns the stable on-disk identifier. */
    public int id() {
        return id;
    }

    /** Parses a stable on-disk key mode identifier. */
    public static KeyMode fromId(int id) {
        for (KeyMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported key mode");
    }
}
