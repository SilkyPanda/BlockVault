package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.CredentialPassword;
import io.github.silkypanda.blockvault.core.validation.AsciiInput;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Changes the master password without exposing the current password to the UI thread after submission. */
final class VaultMasterPasswordScreen extends Screen {
    private final Screen parent;
    private EditBox current, next, confirm;
    private boolean busy, failed, mismatch, tooShort, samePassword, invalidCharacters;
    private final java.util.List<CapsLockHint> capsHints = new java.util.ArrayList<>();

    VaultMasterPasswordScreen(Screen parent) {
        super(Component.translatable("blockvault.master.change.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        capsHints.clear();
        int x = width / 2 - 120;
        int y = Math.max(34, height / 2 - 55);
        current = field(x, y, "blockvault.master.current");
        next = field(x, y + 26, "blockvault.master.new");
        confirm = field(x, y + 52, "blockvault.master.confirm");
        addRenderableWidget(current); addRenderableWidget(next); addRenderableWidget(confirm);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.master.change"), button -> submit())
                .bounds(x, y + 82, 116, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"),
                button -> Minecraft.getInstance().setScreenAndShow(parent)).bounds(x + 124, y + 82, 116, 20).build());
        setInitialFocus(current);
    }

    private EditBox field(int x, int y, String key) {
        EditBox box = new EditBox(font, x, y, 240, 20, Component.translatable(key));
        box.setHint(Component.translatable(key));
        box.setMaxLength(256);
        box.setFilter(AsciiInput::isPasswordText);
        CapsLockHint hint = new CapsLockHint();
        capsHints.add(hint);
        box.setResponder(hint::onValueChanged);
        box.addFormatter((value, cursor) -> FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
        return box;
    }

    private void submit() {
        if (busy) return;
        mismatch = next.getValue().isEmpty() || !next.getValue().equals(confirm.getValue());
        tooShort = next.getValue().length() < 8;
        samePassword = current.getValue().equals(next.getValue());
        invalidCharacters = !CredentialPassword.isValid(current.getValue().toCharArray())
                || !CredentialPassword.isValid(next.getValue().toCharArray());
        if (mismatch || tooShort || samePassword || current.getValue().isEmpty() || invalidCharacters) {
            statusKey = null;
            failed = true;
            return;
        }
        char[] oldPassword = current.getValue().toCharArray();
        char[] newPassword = next.getValue().toCharArray();
        clearFields();
        busy = true; failed = false; statusKey = null;
        VaultRuntime.changeMasterPassword(oldPassword, newPassword, failure -> {
            Arrays.fill(oldPassword, '\0'); Arrays.fill(newPassword, '\0');
            busy = false; failed = failure != null;
            if (failure == null) Minecraft.getInstance().setScreenAndShow(parent);
            else if ("blockvault.master.change.backups_failed".equals(failure)) {
                Minecraft.getInstance().setScreenAndShow(parent);
                VaultRuntime.showToast(failure);
            }
            else statusKey = failure;
        });
    }

    private void clearFields() {
        current.setValue(""); next.setValue(""); confirm.setValue("");
        capsHints.forEach(CapsLockHint::reset);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (capsHints.stream().anyMatch(CapsLockHint::likelyOn)) {
            graphics.centeredText(font, Component.translatable("blockvault.access.caps_lock"), width / 2,
                    Math.max(22, height / 2 - 70), 0xffffcc55);
        }
        if (failed || busy) graphics.centeredText(font, Component.translatable(busy ? "blockvault.master.working"
                : failureKey()), width / 2,
                Math.min(height - 20, height / 2 + 67), 0xffff7777);
    }

    private String statusKey;

    private String failureKey() {
        if (statusKey != null) return statusKey;
        if (invalidCharacters) return "blockvault.password.invalid";
        if (mismatch) return "blockvault.master.mismatch";
        if (tooShort) return "blockvault.master.new.too_short";
        if (samePassword) return "blockvault.master.new.same";
        return "blockvault.master.failed";
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { clearFields(); super.onClose(); }
    @Override public void removed() { clearFields(); super.removed(); }
}
