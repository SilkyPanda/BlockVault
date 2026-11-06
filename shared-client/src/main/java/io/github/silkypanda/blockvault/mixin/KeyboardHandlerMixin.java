package io.github.silkypanda.blockvault.mixin;

import io.github.silkypanda.blockvault.ShiftState;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tracks Shift state so password fields can show the Caps Lock hint. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void blockvault$trackModifiers(long window, int action, KeyEvent event, CallbackInfo callback) {
        ShiftState.track(event);
    }
}
