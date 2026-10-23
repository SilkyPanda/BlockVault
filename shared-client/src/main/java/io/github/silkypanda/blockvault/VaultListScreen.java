package io.github.silkypanda.blockvault;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minimal M3 listing of account names; M4 adds editing, reveal and settings actions. */
final class VaultListScreen extends Screen {
    private final List<String> rows;

    VaultListScreen(List<String> rows) {
        super(Component.translatable("blockvault.vault.title"));
        this.rows = List.copyOf(rows);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.lock"),
                        button -> VaultRuntime.lock())
                .bounds(width / 2 - 100, height - 36, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int y = 36;
        if (rows.isEmpty()) {
            graphics.centeredText(font, Component.translatable("blockvault.vault.empty"), width / 2, y, 0xffcccccc);
        } else {
            for (String row : rows) {
                if (y > height - 48) break;
                graphics.centeredText(font, row, width / 2, y, 0xffffffff);
                y += 14;
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
