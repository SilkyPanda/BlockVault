package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.validation.InputRules;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
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
    private boolean confirmUnverifiedErase;
    private boolean eraseEligibilityChecked;
    private boolean passwordlessEraseAllowed;
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
        password.setMaxLength(InputRules.MASTER_PASSWORD_MAX * 2);
        InputFilter.bind(password, InputRules::isMasterPasswordInput, null);
        password.addFormatter((value, cursor) -> FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(password);
        eraseButton = Button.builder(Component.translatable(confirmUnverifiedErase
                        ? "blockvault.data.erase.confirm_without_password" : "blockvault.data.erase.confirm"),
                        button -> {
                            if (confirmUnverifiedErase) eraseWithoutPassword();
                            else erase();
                        })
                .bounds(center - 120, top + 112, 140, 20).build();
        eraseButton.active = !busy;
        addRenderableWidget(eraseButton);
        cancelButton = Button.builder(Component.translatable("blockvault.vault.back"),
                        button -> Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(center + 28, top + 112, 92, 20).build();
        addRenderableWidget(cancelButton);
        if (passwordlessEraseAllowed) {
            addRenderableWidget(Button.builder(Component.translatable("blockvault.data.erase.without_password"), button -> {
                        if (!busy) {
                            confirmUnverifiedErase = true;
                            failureKey = "blockvault.data.erase.without_password.warning";
                            rebuildWidgets();
                        }
                    }).bounds(center - 120, top + 138, 240, 20).build());
        }
        setInitialFocus(password);
        if (!eraseEligibilityChecked) {
            VaultRuntime.checkPasswordlessEraseAllowed(allowed -> {
                if (Minecraft.getInstance().gui.screen() != this) return;
                eraseEligibilityChecked = true;
                passwordlessEraseAllowed = allowed;
                if (allowed) rebuildWidgets();
            });
        }
    }

    private void eraseWithoutPassword() {
        if (busy || !confirmUnverifiedErase || !passwordlessEraseAllowed) return;
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

    @Override public boolean keyPressed(KeyEvent event) {
        if (ScreenText.isEnter(event)) {
            if (confirmUnverifiedErase) eraseWithoutPassword(); else erase();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        int top = Math.max(24, height / 2 - 92);
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
        if (confirmUnverifiedErase) ScreenText.drawCenteredWrapped(graphics, font,
                Component.translatable("blockvault.data.erase.without_password.warning"), center, warningY,
                width - 32, 0xffff7777, 10);
        if (busy || failureKey != null) graphics.centeredText(font,
                Component.translatable(busy ? "blockvault.data.erase.working" : failureKey), center, top + 166,
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
