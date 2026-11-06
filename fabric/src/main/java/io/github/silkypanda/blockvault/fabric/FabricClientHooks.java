package io.github.silkypanda.blockvault.fabric;

import io.github.silkypanda.blockvault.Hooks;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/** Fabric event registration, isolated from shared Minecraft-facing code. */
final class FabricClientHooks {
    private FabricClientHooks() { }

    static void register() {
        // Action-bar (overlay) messages are deliberately handled like chat messages, matching NeoForge, because some
        // servers show their login prompt there.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> Hooks.onSystemMessage(message));
        ClientPlayConnectionEvents.JOIN.register((listener, sender, minecraft) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.isSameThread()) Hooks.onJoin();
            else client.execute(Hooks::onJoin);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> Hooks.onDisconnect());
    }
}
