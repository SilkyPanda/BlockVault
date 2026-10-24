package io.github.silkypanda.blockvault;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Explicit warning and master-password confirmation before erasing all BlockVault data. */
final class VaultEraseScreen extends Screen {
    private final Screen parent;
    private EditBox password;
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
        int top = Math.max(24, height / 2 - 92);
        password = new EditBox(font, center - 120, top + 82, 240, 20,
                Component.translatable("blockvault.vault.password"));
        password.setHint(Component.translatable("blockvault.vault.password"));
        password.setMaxLength(256);
        password.setResponder(ignored -> VaultRuntime.activity());
        password.addFormatter((value, cursor) -> FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(password);
        eraseButton = Button.builder(Component.translatable("blockvault.data.erase.confirm"), button -> erase())
                .bounds(center - 120, top + 112, 140, 20).build();
        eraseButton.active = !busy;
        addRenderableWidget(eraseButton);
        cancelButton = Button.builder(Component.translatable("blockvault.vault.back"),
                        button -> Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(center + 28, top + 112, 92, 20).build();
        addRenderableWidget(cancelButton);
        setInitialFocus(password);
    }

    private void erase() {
        if (busy || password.getValue().isEmpty()) return;
        char[] secret = password.getValue().toCharArray();
        password.setValue("");
        busy = true;
        failureKey = null;
        eraseButton.active = false;
        cancelButton.active = false;
        VaultRuntime.eraseAllData(secret, failure -> {
            busy = false;
            if (failure == null) {
                if (Minecraft.getInstance().gui.screen() == this) {
                    Minecraft.getInstance().setScreenAndShow(parent);
                    VaultRuntime.showToast("blockvault.data.erase.success");
                }
            } else {
                failureKey = failure;
                if (password != null) password.setValue("");
                if (Minecraft.getInstance().gui.screen() == this) rebuildWidgets();
            }
            Arrays.fill(secret, '\0');
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        int top = Math.max(24, height / 2 - 92);
        graphics.centeredText(font, title, center, top, 0xffff7777);
        graphics.centeredText(font, Component.translatable("blockvault.data.erase.warning1"), center, top + 24, 0xffffffff);
        graphics.centeredText(font, Component.translatable("blockvault.data.erase.warning2"), center, top + 40, 0xffffffff);
        graphics.centeredText(font, Component.translatable("blockvault.data.erase.warning3"), center, top + 56, 0xffffcc66);
        if (busy || failureKey != null) graphics.centeredText(font,
                Component.translatable(busy ? "blockvault.data.erase.working" : failureKey), center, top + 142,
                busy ? 0xffcccccc : 0xffff7777);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() {
        if (password != null) password.setValue("");
        super.removed();
    }

    @Override public void onClose() {
        if (password != null) password.setValue("");
        super.onClose();
    }
}
