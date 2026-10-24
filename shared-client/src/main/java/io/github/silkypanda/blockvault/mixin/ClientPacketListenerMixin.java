package io.github.silkypanda.blockvault.mixin;

import io.github.silkypanda.blockvault.Hooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercepts outgoing commands for explicit manual credential capture. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), require = 0)
    private void blockvault$commandSent(String command, CallbackInfo callback) {
        Hooks.onOutgoingCommand(command);
    }
}
