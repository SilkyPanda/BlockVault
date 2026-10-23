package io.github.silkypanda.blockvault.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.silkypanda.blockvault.VaultRuntime;
import net.minecraft.client.gui.screens.Screen;

/** Mod Menu config-screen entrypoint. */
public final class BlockVaultModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> VaultRuntime.createSettingsScreen(parent);
    }
}
