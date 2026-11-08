package io.github.silkypanda.blockvault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Common parent and layout helpers for BlockVault screens. Screen methods run on the game thread. */
abstract class BlockVaultScreen extends Screen {
    protected final Screen parent;

    protected BlockVaultScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected final void returnToParent() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override public void onClose() { super.onClose(); }

    /** Returns the first Y coordinate for a vertically centered stack with the given total height. */
    protected final int centeredVerticalTop(int totalHeight) {
        return (height - totalHeight) / 2;
    }

    protected final int drawCenteredWrapped(GuiGraphicsExtractor graphics, Component text,
                                             int centerX, int topY, int maxWidth, int color, int lineHeight) {
        return ScreenText.drawCenteredWrapped(graphics, font, text, centerX, topY, maxWidth, color, lineHeight);
    }

    static boolean isBlockVaultScreen(Screen screen) {
        return screen instanceof BlockVaultManagedScreen || VaultSettingsScreenFactory.isOptionalYaclScreen(screen);
    }
}
