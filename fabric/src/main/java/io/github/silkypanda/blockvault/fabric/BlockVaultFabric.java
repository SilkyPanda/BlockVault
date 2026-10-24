package io.github.silkypanda.blockvault.fabric;

import io.github.silkypanda.blockvault.BlockVault;
import io.github.silkypanda.blockvault.Platform;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint. */
public final class BlockVaultFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockVault.init(new FabricPlatform());
        FabricClientHooks.register();
    }

    private static final class FabricPlatform implements Platform {
        @Override
        public String loaderName() {
            return "fabric";
        }

    }
}
