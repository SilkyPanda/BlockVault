package io.github.silkypanda.blockvault.neoforge;

import io.github.silkypanda.blockvault.BlockVault;
import io.github.silkypanda.blockvault.Platform;
import io.github.silkypanda.blockvault.VaultRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** NeoForge client entrypoint. */
@Mod(value = "blockvault", dist = Dist.CLIENT)
public final class BlockVaultNeoForge {
    public BlockVaultNeoForge(IEventBus modBus, ModContainer container) {
        BlockVault.init(new NeoForgePlatform());
        NeoForgeClientHooks.register(modBus);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (modContainer, parent) -> VaultRuntime.createSettingsScreen(parent));
    }

    private static final class NeoForgePlatform implements Platform {
        @Override
        public String loaderName() {
            return "neoforge";
        }
    }
}
