package io.github.silkypanda.blockvault;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import io.github.silkypanda.blockvault.core.crypto.PasswordStrength;

/** Minimal masked screen for creating or unlocking the encrypted local vault. */
final class VaultAccessScreen extends Screen {
    private final boolean create;
    private EditBox password;
    private EditBox confirmation;
    private boolean failed;
    private boolean busy;
    private boolean trustThisDevice = true;
    private String failureKey;
    private final CapsLockHint capsLock = new CapsLockHint();

    VaultAccessScreen(boolean create) {
        super(Component.translatable(create ? "blockvault.vault.create.title" : "blockvault.vault.unlock.title"));
        this.create = create;
    }

    @Override
    protected void init() {
        int center = width / 2;
        password = new EditBox(font, center - 100, height / 2 + (create ? -48 : -28), 200, 20,
                Component.translatable("blockvault.vault.password"));
        password.setHint(Component.translatable("blockvault.vault.password"));
        password.setMaxLength(256);
        password.addFormatter(VaultAccessScreen::mask);
        password.setResponder(capsLock::onValueChanged);
        addRenderableWidget(password);
        if (create) {
            confirmation = new EditBox(font, center - 100, height / 2 - 20, 200, 20,
                    Component.translatable("blockvault.vault.confirm"));
            confirmation.setHint(Component.translatable("blockvault.vault.confirm"));
            confirmation.setMaxLength(256);
            confirmation.addFormatter(VaultAccessScreen::mask);
            confirmation.setResponder(capsLock::onValueChanged);
            addRenderableWidget(confirmation);
            trustThisDevice = BlockVaultConfig.current().trustDevice();
            addRenderableWidget(Button.builder(trustMessage(), button -> {
                        trustThisDevice = !trustThisDevice;
                        button.setMessage(trustMessage());
                    }).bounds(center - 100, height / 2 + 8, 200, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable(create
                        ? "blockvault.vault.create" : "blockvault.vault.unlock"), button -> submit())
                .bounds(center - 100, height / 2 + (create ? 36 : 8), 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.backup.recovery.open"), button ->
                        VaultRuntime.openRecoveryScreen(this))
                .bounds(center - 100, height / 2 + (create ? 60 : 34), 200, 20).build());
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
        VaultRuntime.openWithMasterPassword(secret, create, trustThisDevice);
    }

    private Component trustMessage() {
        return Component.translatable("blockvault.vault.trust_setup", Component.translatable(
                trustThisDevice ? "options.on" : "options.off"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (create && password != null) {
            char[] value = password.getValue().toCharArray();
            int strength;
            try { strength = PasswordStrength.rating(value); }
            finally { java.util.Arrays.fill(value, '\0'); }
            String[] labels = {"blockvault.vault.password.strength.weak", "blockvault.vault.password.strength.fair",
                    "blockvault.vault.password.strength.good", "blockvault.vault.password.strength.strong"};
            int[] colors = {0xffff6666, 0xffffbb55, 0xffeedd55, 0xff77dd88};
            graphics.centeredText(font, Component.translatable("blockvault.vault.password.strength",
                    Component.translatable(labels[strength])), width / 2, height / 2 - 80, colors[strength]);
        }
        if (capsLock.likelyOn()) {
            graphics.centeredText(font, Component.translatable("blockvault.access.caps_lock"), width / 2,
                    height / 2 - 64, 0xffffcc55);
        }
        if (busy) {
            graphics.centeredText(font, Component.translatable(create ? "blockvault.access.creating"
                    : "blockvault.access.unlocking"), width / 2, height / 2 + (create ? 59 : 32), 0xffcccccc);
        } else if (failed) {
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
        capsLock.reset();
    }

    private static FormattedCharSequence mask(String value, int cursor) {
        return FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY);
    }
}
