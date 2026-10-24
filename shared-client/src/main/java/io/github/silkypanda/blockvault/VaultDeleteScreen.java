package io.github.silkypanda.blockvault;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit second step before a credential is tombstoned. */
final class VaultDeleteScreen extends Screen {
    private final VaultRow row;
    private final Screen parent;

    VaultDeleteScreen(VaultRow row, Screen parent) {
        super(Component.translatable(row.serverOnly() ? "blockvault.vault.server.remove.title" : "blockvault.vault.delete.title"));
        this.row = row;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 110;
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.delete.confirm"), button -> {
            VaultRuntime.deleteAccount(row);
            net.minecraft.client.Minecraft.getInstance().setScreenAndShow(parent);
        }).bounds(x, height / 2 + 18, 104, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.card.cancel"), button ->
                        net.minecraft.client.Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(x + 116, height / 2 + 18, 104, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String serverName = BlockVaultConfig.current().streamerMode()
                ? Component.translatable("blockvault.settings.streamer.hidden").getString() : row.serverName();
        Component prompt = row.serverOnly()
                ? Component.translatable("blockvault.vault.server.remove.prompt", Component.literal(serverName))
                : Component.translatable("blockvault.vault.delete.prompt", Component.literal(row.username()), Component.literal(serverName));
        graphics.centeredText(font, prompt, width / 2,
                height / 2 - 8, 0xffffffff);
    }

    @Override public boolean isPauseScreen() { return false; }
}
