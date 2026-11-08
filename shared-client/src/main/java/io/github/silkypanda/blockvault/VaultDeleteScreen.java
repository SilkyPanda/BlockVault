package io.github.silkypanda.blockvault;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit second step before a credential is moved to the trash. */
final class VaultDeleteScreen extends BlockVaultScreen implements BlockVaultManagedScreen {
    private final VaultRow row;

    VaultDeleteScreen(VaultRow row, Screen parent) {
        super(Component.translatable("blockvault.vault.delete.title"), parent);
        this.row = row;
    }

    @Override
    protected void init() {
        int x = width / 2 - 110;
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.delete.confirm"), button -> {
            VaultRuntime.deleteAccount(row);
            returnToParent();
        }).bounds(x, height / 2 + 18, 104, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.card.cancel"), button ->
                        returnToParent())
                .bounds(x + 116, height / 2 + 18, 104, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String serverName = BlockVaultConfig.current().streamerMode()
                ? Component.translatable("blockvault.settings.streamer.hidden").getString() : row.serverName();
        Component prompt = Component.translatable("blockvault.vault.delete.prompt",
                Component.literal(row.username()), Component.literal(serverName));
        graphics.centeredText(font, prompt, width / 2,
                height / 2 - 8, 0xffffffff);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        returnToParent();
    }
}
