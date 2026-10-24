package io.github.silkypanda.blockvault.mixin;

import io.github.silkypanda.blockvault.Keybinds;
import net.minecraft.client.Minecraft;
import io.github.silkypanda.blockvault.VaultRuntime;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dispatches registered key mappings directly from keyboard events without tick polling. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    private static final int GLFW_PRESS_ACTION = 1;

    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void blockvault$keyPressed(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (action == GLFW_PRESS_ACTION) {
            net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().gui.screen();
            if (screen == null) Keybinds.onKeyPress(event);
            else VaultRuntime.activity();
        }
    }
}
