package io.github.silkypanda.blockvault;

import java.util.Arrays;
import io.github.silkypanda.blockvault.core.session.CredentialPassword;
import io.github.silkypanda.blockvault.core.validation.AsciiInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Per-account reveal, edit, copy, and delete controls. */
final class VaultCredentialScreen extends Screen {
    private final VaultRow row;
    private boolean showPassword;
    private EditBox passwordBox;
    private boolean invalidPassword;

    VaultCredentialScreen(VaultRow row) {
        super(Component.translatable("blockvault.vault.account.title"));
        this.row = row;
    }

    @Override
    protected void init() {
        revealPassword();
        int x = width / 2 - 150;
        int top = Math.max(42, height / 2 - 54);
        passwordBox = new EditBox(font, x, top, 300, 20,
                Component.translatable("blockvault.card.password"));
        passwordBox.setMaxLength(256);
        AsciiInputFilter.bind(passwordBox, AsciiInput::isPasswordText, null);
        passwordBox.addFormatter((value, cursor) -> showPassword
                ? FormattedCharSequence.forward(value, Style.EMPTY)
                : FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(passwordBox);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.save"), button -> { save(); })
                .bounds(x, top + 52, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.card.reveal"), button -> { showPassword = !showPassword; })
                .bounds(x + 103, top + 52, 197, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.copy"), button -> { VaultRuntime.copyPassword(row); })
                .bounds(x, top + 78, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.delete"), button ->
                        Minecraft.getInstance().setScreenAndShow(new VaultDeleteScreen(row, this)))
                .bounds(x + 103, top + 78, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button -> { VaultRuntime.openVaultScreen(); })
                .bounds(x + 206, top + 78, 94, 20).build());
        setInitialFocus(passwordBox);
    }

    private void revealPassword() {
        VaultRuntime.revealPassword(row, password -> {
            try {
                if (Minecraft.getInstance().gui.screen() == this && passwordBox != null) passwordBox.setValue(new String(password));
            } finally { Arrays.fill(password, '\0'); }
        });
    }

    private void save() {
        char[] password = passwordBox.getValue().toCharArray();
        if (!CredentialPassword.isValid(password)) {
            Arrays.fill(password, '\0');
            invalidPassword = true;
            VaultRuntime.showToast("blockvault.password.invalid");
            return;
        }
        invalidPassword = false;
        VaultRuntime.updatePassword(row, password);
        Arrays.fill(password, '\0');
        VaultRuntime.openVaultScreen();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String serverName = BlockVaultConfig.current().streamerMode()
                ? Component.translatable("blockvault.settings.streamer.hidden").getString() : row.serverName();
        graphics.centeredText(font, Component.literal(serverName + " · " + row.username()), width / 2,
                height / 2 - 92, 0xffffffff);
        if (!BlockVaultConfig.current().streamerMode()) {
            graphics.centeredText(font, Component.literal(row.host()), width / 2, height / 2 - 76, 0xffb9b4c5);
        }
        if (invalidPassword) graphics.centeredText(font, Component.translatable("blockvault.password.invalid"),
                width / 2, height / 2 + 20, 0xffff7777);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (passwordBox != null) passwordBox.setValue("");
        super.onClose();
    }

    @Override
    public void removed() {
        if (passwordBox != null) passwordBox.setValue("");
        super.removed();
    }
}
