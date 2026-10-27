package io.github.silkypanda.blockvault.neoforge;

import io.github.silkypanda.blockvault.Hooks;
import io.github.silkypanda.blockvault.Keybinds;
import io.github.silkypanda.blockvault.VaultRuntime;
import net.minecraft.commands.Commands;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge event registration, isolated from shared Minecraft-facing code. */
final class NeoForgeClientHooks {
    private NeoForgeClientHooks() { }

    static void register(IEventBus modBus) {
        // System messages include action-bar (overlay) text, matching Fabric, because some servers show their
        // login prompt there.
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.class, event -> {
            if (event.isSystem()) Hooks.onSystemMessage(event.getMessage());
        });
        // /blockvault opens the vault without a keybind; /blockvault lock wipes the key.
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(
                Commands.literal("blockvault")
                        .executes(context -> { VaultRuntime.openVaultScreen(); return 1; })
                        .then(Commands.literal("lock").executes(context -> { VaultRuntime.lock(); return 1; }))));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class,
                event -> Minecraft.getInstance().execute(Hooks::onJoin));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> Hooks.onDisconnect());
        modBus.addListener(NeoForgeClientHooks::registerKeyMappings);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(Keybinds.OPEN_VAULT);
        event.register(Keybinds.LOCK_VAULT);
    }
}
