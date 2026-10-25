package io.github.silkypanda.blockvault;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Minimal masked screen for creating or unlocking the encrypted local vault. */
final class VaultAccessScreen extends Screen {
    private final boolean create;
    private EditBox password;
    private EditBox confirmation;
    private boolean failed;
    private boolean busy;
    private String failureKey;

    VaultAccessScreen(boolean create) {
        super(Component.translatable(create ? "blockvault.vault.create.title" : "blockvault.vault.unlock.title"));
        this.create = create;
    }

    @Override
    protected void init() {
        int center = width / 2;
        password = new EditBox(font, center - 100, height / 2 - 28, 200, 20,
                Component.translatable("blockvault.vault.password"));
        password.setHint(Component.translatable("blockvault.vault.password"));
        password.setMaxLength(256);
        password.addFormatter(VaultAccessScreen::mask);
        addRenderableWidget(password);
        if (create) {
            confirmation = new EditBox(font, center - 100, height / 2, 200, 20,
                    Component.translatable("blockvault.vault.confirm"));
            confirmation.setHint(Component.translatable("blockvault.vault.confirm"));
            confirmation.setMaxLength(256);
            confirmation.addFormatter(VaultAccessScreen::mask);
            addRenderableWidget(confirmation);
        }
        addRenderableWidget(Button.builder(Component.translatable(create
                        ? "blockvault.vault.create" : "blockvault.vault.unlock"), button -> submit())
                .bounds(center - 100, height / 2 + (create ? 34 : 8), 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.backup.recovery.open"), button ->
                        VaultRuntime.openRecoveryScreen(this))
                .bounds(center - 100, height / 2 + (create ? 58 : 34), 200, 20).build());
        setInitialFocus(password);
    }

    private void submit() {
        if (busy) return;
        failed = false;
        failureKey = null;
        String entered = password.getValue();
        String repeated = create ? confirmation.getValue() : entered;
        if (entered.isEmpty() || !entered.equals(repeated)) {
            failed = true;
            password.setValue("");
            if (confirmation != null) confirmation.setValue("");
            return;
        }
        if (create && entered.length() < 8) {
            failed = true;
            failureKey = "blockvault.vault.password.too_short";
            password.setValue("");
            confirmation.setValue("");
            return;
        }
        char[] secret = entered.toCharArray();
        password.setValue("");
        if (confirmation != null) confirmation.setValue("");
        busy = true;
        VaultRuntime.openWithMasterPassword(secret, create);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (failed) {
            graphics.centeredText(font, Component.translatable(failureKey != null ? failureKey : create
                    ? "blockvault.vault.password.mismatch" : "blockvault.vault.unlock.failed"), width / 2,
                    height / 2 + (create ? 59 : 32), 0xffff5555);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        clearFields();
        VaultRuntime.accessScreenClosed();
        super.onClose();
    }

    /** Called when another screen replaces this one without the owner cancelling (kick screen, credential card). */
    @Override
    public void removed() {
        clearFields();
        VaultRuntime.accessScreenReplaced();
        super.removed();
    }

    void showFailure(String translationKey) {
        busy = false;
        failed = true;
        failureKey = translationKey;
        if (password != null) password.setValue("");
        if (confirmation != null) confirmation.setValue("");
    }

    private void clearFields() {
        if (password != null) password.setValue("");
        if (confirmation != null) confirmation.setValue("");
    }

    private static FormattedCharSequence mask(String value, int cursor) {
        return FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY);
    }
}
