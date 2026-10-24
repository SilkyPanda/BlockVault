package io.github.silkypanda.blockvault;

import java.util.Arrays;
import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.totp.Totp;
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
    private boolean checkStarted;
    private boolean checked;
    private boolean masterRequired;
    private boolean revealed;
    private boolean revealFailed;
    private boolean showPassword;
    private boolean invalidTotp;
    private boolean totpLoaded;
    private EditBox passwordBox;
    private EditBox totpBox;
    private Button saveButton;

    VaultCredentialScreen(VaultRow row) {
        super(Component.translatable("blockvault.vault.account.title"));
        this.row = row;
    }

    @Override
    protected void init() {
        if (!checkStarted) {
            checkStarted = true;
            VaultRuntime.requiresMasterPassword(required -> {
                masterRequired = required;
                checked = true;
                if (!required) revealPassword();
                if (Minecraft.getInstance().gui.screen() == this) rebuildWidgets();
            });
            return;
        }
        int x = width / 2 - 150;
        int top = Math.max(42, height / 2 - 54);
        passwordBox = new EditBox(font, x, top, 300, 20,
                Component.translatable(masterRequired && !revealed
                        ? "blockvault.vault.reconfirm" : "blockvault.card.password"));
        passwordBox.setMaxLength(256);
        passwordBox.addFormatter((value, cursor) -> showPassword
                ? FormattedCharSequence.forward(value, Style.EMPTY)
                : FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(passwordBox);
        if (!masterRequired || revealed) {
            totpBox = new EditBox(font, x, top + 26, 300, 20, Component.translatable("blockvault.vault.totp_secret"));
            totpBox.setHint(Component.translatable("blockvault.vault.totp_secret"));
            totpBox.setMaxLength(128);
            totpBox.addFormatter((value, cursor) -> showPassword
                    ? FormattedCharSequence.forward(value, Style.EMPTY)
                    : FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
            VaultRuntime.revealTotpSecret(row, secret -> {
                if (Minecraft.getInstance().gui.screen() == this && totpBox != null) {
                    totpBox.setValue(secret == null ? "" : secret);
                    totpLoaded = true;
                    if (saveButton != null) saveButton.active = true;
                }
            });
            addRenderableWidget(totpBox);
        }
        if (masterRequired && !revealed) {
            addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.reconfirm"), button -> { VaultRuntime.activity(); reauthenticate(); })
                    .bounds(x, top + 52, 144, 20).build());
        } else {
            saveButton = Button.builder(Component.translatable("blockvault.vault.save"), button -> { VaultRuntime.activity(); save(); })
                    .bounds(x, top + 52, 94, 20).build();
            saveButton.active = totpBox == null || totpLoaded;
            addRenderableWidget(saveButton);
            addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.generate"), button -> { VaultRuntime.activity(); generate(); })
                    .bounds(x + 103, top + 52, 94, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("blockvault.card.reveal"), button -> { VaultRuntime.activity(); showPassword = !showPassword; })
                    .bounds(x + 206, top + 52, 94, 20).build());
        }
        if (!masterRequired || revealed) {
            addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.copy"), button -> { VaultRuntime.activity(); VaultRuntime.copyPassword(row); })
                    .bounds(x, top + 78, 94, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.delete"), button ->
                        Minecraft.getInstance().setScreenAndShow(new VaultDeleteScreen(row, this)))
                .bounds(x + 103, top + 78, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button -> { VaultRuntime.activity(); VaultRuntime.openVaultScreen(); })
                .bounds(x + 206, top + 78, 94, 20).build());
        setInitialFocus(passwordBox);
    }

    private void reauthenticate() {
        char[] password = passwordBox.getValue().toCharArray();
        passwordBox.setValue("");
        VaultRuntime.reauthenticateForReveal(password, success -> {
            Arrays.fill(password, '\0');
            if (success) {
                revealed = true;
                revealPassword();
                rebuildWidgets();
            } else {
                revealFailed = true;
                if (passwordBox != null) passwordBox.setValue("");
            }
        });
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
        if (password.length == 0) return;
        String secret = totpBox == null ? null : totpBox.getValue().trim();
        if (secret != null && !secret.isEmpty()) {
            try {
                byte[] decoded = Totp.decodeBase32(secret);
                Arrays.fill(decoded, (byte) 0);
            } catch (IllegalArgumentException invalid) {
                invalidTotp = true;
                Arrays.fill(password, '\0');
                return;
            }
        }
        invalidTotp = false;
        VaultRuntime.updatePassword(row, password, secret);
        Arrays.fill(password, '\0');
        VaultRuntime.openVaultScreen();
    }

    private void generate() {
        char[] generated = PasswordGenerator.generate(policy(BlockVaultConfig.current().generator()));
        try { passwordBox.setValue(new String(generated)); }
        finally { Arrays.fill(generated, '\0'); }
    }

    private static PasswordGenerator.Policy policy(String preset) {
        return switch (preset) {
            case "pickyServer" -> PasswordGenerator.Policy.pickyServer();
            case "max" -> PasswordGenerator.Policy.max();
            case "memorable" -> PasswordGenerator.Policy.memorable();
            default -> PasswordGenerator.Policy.defaults();
        };
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
        if (!checked) graphics.centeredText(font, Component.translatable("blockvault.settings.loading"), width / 2,
                height / 2 + 64, 0xffaaaaaa);
        if (revealFailed) graphics.centeredText(font, Component.translatable("blockvault.vault.unlock.failed"),
                width / 2, height / 2 + 64, 0xffff7777);
        if (invalidTotp) graphics.centeredText(font, Component.translatable("blockvault.vault.totp.invalid"),
                width / 2, height / 2 + 64, 0xffff7777);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (passwordBox != null) passwordBox.setValue("");
        if (totpBox != null) totpBox.setValue("");
        super.onClose();
    }
}
