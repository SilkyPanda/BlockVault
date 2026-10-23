package io.github.silkypanda.blockvault;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit second step before a credential is tombstoned. */
final class VaultDeleteScreen extends Screen {
    private final VaultRow row;

    VaultDeleteScreen(VaultRow row) {
        super(Component.translatable("blockvault.vault.delete.title"));
        this.row = row;
    }

    @Override
    protected void init() {
        int x = width / 2 - 110;
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.delete.confirm"), button -> {
            VaultRuntime.deleteAccount(row);
            VaultRuntime.openVaultScreen();
        }).bounds(x, height / 2 + 18, 104, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.card.cancel"), button -> VaultRuntime.openVaultScreen())
                .bounds(x + 116, height / 2 + 18, 104, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, Component.translatable("blockvault.vault.delete.prompt",
                Component.literal(row.username()), Component.literal(row.serverName())), width / 2,
                height / 2 - 8, 0xffffffff);
    }

    @Override public boolean isPauseScreen() { return false; }
}
