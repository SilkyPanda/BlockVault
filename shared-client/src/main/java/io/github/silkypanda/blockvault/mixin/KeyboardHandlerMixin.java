package io.github.silkypanda.blockvault.mixin;

import io.github.silkypanda.blockvault.Keybinds;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dispatches registered key mappings directly from keyboard events without tick polling. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void blockvault$keyPressed(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (action == GLFW.GLFW_PRESS) Keybinds.onKeyPress(event);
    }
}
