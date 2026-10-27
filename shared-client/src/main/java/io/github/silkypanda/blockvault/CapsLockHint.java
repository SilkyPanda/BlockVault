package io.github.silkypanda.blockvault;

/**
 * Guesses whether Caps Lock is on from what the player types into a password box. Minecraft does not expose the
 * Caps Lock state, so each typed letter is compared with the Shift state: an upper-case letter typed without Shift,
 * or a lower-case letter typed with Shift, means Caps Lock is on. Non-letters and pasted text change nothing.
 */
final class CapsLockHint {
    private boolean likelyOn;
    private String previous = "";

    /** Call with the new box contents every time they change. */
    void onValueChanged(String value) {
        if (value.length() == previous.length() + 1 && value.startsWith(previous)) {
            char typed = value.charAt(value.length() - 1);
            if (Character.isLetter(typed) && (Character.isUpperCase(typed) || Character.isLowerCase(typed))) {
                boolean shift = Keybinds.shiftDown();
                likelyOn = Character.isUpperCase(typed) != shift;
            }
        }
        previous = value;
    }

    /** True when the last typed letter suggests Caps Lock is on. */
    boolean likelyOn() { return likelyOn; }

    /** Forgets everything, for example when the fields are cleared. */
    void reset() {
        likelyOn = false;
        previous = "";
    }
}
