package io.github.silkypanda.blockvault.mixin;

import io.github.silkypanda.blockvault.Hooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercepts outgoing commands for manual credential capture and bare login. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void blockvault$commandSent(String command, CallbackInfo callback) {
        if (Hooks.onOutgoingCommand(command)) callback.cancel();
    }
}
