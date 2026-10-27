package io.github.silkypanda.blockvault;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;

/** Shared key mappings and event-based dispatch, with no tick polling. */
public final class Keybinds {
    public static final KeyMapping OPEN_VAULT = new KeyMapping(
            "key.blockvault.open_vault", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MULTIPLAYER);
    public static final KeyMapping LOCK_VAULT = new KeyMapping(
            "key.blockvault.lock_vault", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MULTIPLAYER);

    private static volatile int modifiers;

    private Keybinds() { }

    /** Remembers the modifier keys held during the latest key event (used by the Caps Lock hint). */
    public static void trackModifiers(KeyEvent event) { modifiers = event.modifiers(); }

    /** True while a Shift key was held at the latest key event. */
    static boolean shiftDown() { return (modifiers & 1) != 0; }

    public static void onKeyPress(KeyEvent event) {
        if (OPEN_VAULT.matches(event)) VaultRuntime.openVaultScreen();
        if (LOCK_VAULT.matches(event)) VaultRuntime.lock();
    }
}
