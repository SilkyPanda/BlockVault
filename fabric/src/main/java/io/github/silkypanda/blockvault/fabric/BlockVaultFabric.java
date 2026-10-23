package io.github.silkypanda.blockvault.fabric;

import io.github.silkypanda.blockvault.BlockVault;
import io.github.silkypanda.blockvault.Platform;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric client entrypoint. */
public final class BlockVaultFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockVault.init(new FabricPlatform());
    }

    private static final class FabricPlatform implements Platform {
        @Override
        public String loaderName() {
            return "fabric";
        }

        @Override
        public Path gameDir() {
            return FabricLoader.getInstance().getGameDir();
        }

        @Override
        public boolean isDevEnvironment() {
            return FabricLoader.getInstance().isDevelopmentEnvironment();
        }
    }
}
