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

    private Keybinds() { }

    public static void onKeyPress(KeyEvent event) {
        if (OPEN_VAULT.matches(event)) VaultRuntime.openVaultScreen();
        if (LOCK_VAULT.matches(event)) VaultRuntime.lock();
    }
}
