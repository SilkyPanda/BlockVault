package io.github.silkypanda.blockvault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit warning and confirmation before erasing all BlockVault data. */
final class VaultEraseScreen extends Screen {
    private final Screen parent;
    private Button eraseButton;
    private Button cancelButton;
    private boolean busy;
    private String failureKey;

    VaultEraseScreen(Screen parent) {
        super(Component.translatable("blockvault.data.erase.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int top = Math.max(18, height / 2 - 120);
        eraseButton = Button.builder(Component.translatable("blockvault.data.erase.confirm"),
                        button -> requestConfirmation())
                .bounds(center - 120, top + 108, 140, 20).build();
        eraseButton.active = !busy;
        addRenderableWidget(eraseButton);
        cancelButton = Button.builder(Component.translatable("blockvault.vault.back"),
                        button -> Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(center + 28, top + 108, 92, 20).build();
        addRenderableWidget(cancelButton);
    }

    private void requestConfirmation() {
        if (busy) return;
        Minecraft.getInstance().setScreenAndShow(new VaultConfirmScreen(this,
                "blockvault.data.confirm.title",
                "blockvault.data.erase.confirm.warning",
                "blockvault.data.erase.confirm",
                () -> {
                    erase();
                }, () -> {}));
    }

    private void erase() {
        if (busy) return;
        busy = true;
        eraseButton.active = false;
        cancelButton.active = false;
        VaultRuntime.eraseAllDataWithoutPassword(failure -> {
            busy = false;
            if (failure == null) {
                if (Minecraft.getInstance().gui.screen() == this) {
                    Minecraft.getInstance().setScreenAndShow(parent);
                    VaultRuntime.showToast("blockvault.data.erase.success");
                }
            } else {
                failureKey = failure;
                if (Minecraft.getInstance().gui.screen() == this) rebuildWidgets();
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        int top = Math.max(18, height / 2 - 120);
        graphics.centeredText(font, title, center, top, 0xffff7777);
        int warningY = top + 24;
        warningY = ScreenText.drawCenteredWrapped(graphics, font,
                Component.translatable("blockvault.data.erase.warning1"), center, warningY,
                width - 32, 0xffffffff, 10);
        warningY = ScreenText.drawCenteredWrapped(graphics, font,
                Component.translatable("blockvault.data.erase.warning2"), center, warningY,
                width - 32, 0xffffffff, 10);
        warningY = ScreenText.drawCenteredWrapped(graphics, font,
                Component.translatable("blockvault.data.erase.warning3"), center, warningY,
                width - 32, 0xffffcc66, 10);
        if (busy || failureKey != null) graphics.centeredText(font,
                Component.translatable(busy ? "blockvault.data.erase.working" : failureKey), center, top + 192,
                busy ? 0xffcccccc : 0xffff7777);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
