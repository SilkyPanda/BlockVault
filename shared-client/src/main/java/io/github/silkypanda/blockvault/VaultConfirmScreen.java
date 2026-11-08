package io.github.silkypanda.blockvault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Full-screen confirmation step for destructive or overwriting actions. */
final class VaultConfirmScreen extends BlockVaultScreen {
    private final String messageKey;
    private final String confirmKey;
    private final Runnable confirmed;
    private final Runnable cancelled;

    VaultConfirmScreen(Screen parent, String titleKey, String messageKey, String confirmKey, Runnable confirmed) {
        this(parent, titleKey, messageKey, confirmKey, confirmed, () -> { });
    }

    VaultConfirmScreen(Screen parent, String titleKey, String messageKey, String confirmKey,
                       Runnable confirmed, Runnable cancelled) {
        super(Component.translatable(titleKey), parent);
        this.messageKey = messageKey;
        this.confirmKey = confirmKey;
        this.confirmed = confirmed;
        this.cancelled = cancelled;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.max(1, Math.min(170, (width - 36) / 2));
        int left = (width - buttonWidth * 2 - 8) / 2;
        int y = height / 2 + 30;
        addRenderableWidget(Button.builder(Component.translatable(confirmKey), button -> {
            returnToParent();
            confirmed.run();
        }).bounds(left, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.card.cancel"), button -> onClose())
                .bounds(left + buttonWidth + 8, y, buttonWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 42, 0xffffffff);
        drawCenteredWrapped(graphics, Component.translatable(messageKey), width / 2,
                height / 2 - 22, width - 40, 0xffffcc66, 10);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        returnToParent();
        cancelled.run();
    }
}
