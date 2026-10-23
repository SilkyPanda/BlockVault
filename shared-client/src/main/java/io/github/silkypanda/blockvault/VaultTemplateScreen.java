package io.github.silkypanda.blockvault;

import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Selects a validated built-in login command template for one server. */
final class VaultTemplateScreen extends Screen {
    private static final Map<String, String> LABELS = Map.of(
            "authme", "blockvault.template.authme",
            "authme-noconfirm", "blockvault.template.authme_noconfirm",
            "nlogin", "blockvault.template.nlogin",
            "loginsecurity", "blockvault.template.loginsecurity");
    private final VaultRow row;
    private final Screen parent;

    VaultTemplateScreen(VaultRow row, Screen parent) {
        super(Component.translatable("blockvault.template.title"));
        this.row = row;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 140;
        int y = height / 2 - 48;
        for (Map.Entry<String, String> template : LABELS.entrySet()) {
            addRenderableWidget(Button.builder(Component.translatable(template.getValue()), button -> {
                VaultRuntime.activity();
                VaultRuntime.updateServerTemplate(row, template.getKey());
            }).bounds(x, y, 280, 20).build());
            y += 25;
        }
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button ->
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(x, y, 280, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, Component.translatable("blockvault.template.server", row.serverName()),
                width / 2, height / 2 - 76, 0xffffffff);
    }

    @Override public boolean isPauseScreen() { return false; }
}
