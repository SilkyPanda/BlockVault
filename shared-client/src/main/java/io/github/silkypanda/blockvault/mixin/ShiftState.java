package io.github.silkypanda.blockvault.mixin;

import net.minecraft.client.input.KeyEvent;

/** Tracks keyboard modifiers for the password-entry Caps Lock hint. */
public final class ShiftState {
    private static volatile int modifiers;

    private ShiftState() { }

    static void track(KeyEvent event) { modifiers = event.modifiers(); }

    public static boolean shiftDown() { return (modifiers & 1) != 0; }
}
