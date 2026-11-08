package io.github.silkypanda.blockvault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The one place BlockVault changes the current screen. Call on the game thread.
 *
 * <p>Do not use {@code Minecraft.setScreenAndShow(...)} here: since Minecraft 26.2 it renders an extra frame
 * immediately after switching, which shows up as a one-frame black flash every time a screen opens or closes
 * (cursor release and capture happen inside that extra frame). {@code Gui.setScreen(...)} does the same work
 * (screen removal, {@code init}, cursor handling) and lets the normal render loop draw the next frame.
 */
final class ScreenSwitcher {
    private ScreenSwitcher() { }

    /** Shows {@code screen}; {@code null} returns to the game or the title screen. */
    static void show(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }
}
