package io.github.silkypanda.blockvault.mixin;

import io.github.silkypanda.blockvault.Hooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Command tree and outgoing command interception using verified 26.3 methods. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleCommands", at = @At("RETURN"), require = 0)
    private void blockvault$commandsApplied(ClientboundCommandsPacket packet, CallbackInfo callback) {
        Hooks.onCommandTree((ClientPacketListener) (Object) this);
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), require = 0)
    private void blockvault$commandSent(String command, CallbackInfo callback) {
        Hooks.onOutgoingCommand(command);
    }
}
