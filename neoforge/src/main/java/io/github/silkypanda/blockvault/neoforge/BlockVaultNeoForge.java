package io.github.silkypanda.blockvault.neoforge;

import io.github.silkypanda.blockvault.BlockVault;
import io.github.silkypanda.blockvault.Platform;
import java.nio.file.Path;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

/** NeoForge client entrypoint. */
@Mod(value = "blockvault", dist = Dist.CLIENT)
public final class BlockVaultNeoForge {
    public BlockVaultNeoForge(IEventBus modBus, ModContainer container) {
        BlockVault.init(new NeoForgePlatform());
    }

    private static final class NeoForgePlatform implements Platform {
        @Override
        public String loaderName() {
            return "neoforge";
        }

        @Override
        public Path gameDir() {
            return FMLPaths.GAMEDIR.get();
        }

        @Override
        public boolean isDevEnvironment() {
            return !FMLPaths.GAMEDIR.get().resolve(".fabric").toFile().exists();
        }
    }
}
