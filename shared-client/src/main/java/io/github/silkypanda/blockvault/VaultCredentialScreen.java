package io.github.silkypanda.blockvault;

import java.util.Arrays;
import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
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
    private boolean confirmDelete;
    private boolean showPassword;
    private EditBox passwordBox;

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
        passwordBox = new EditBox(font, x, height / 2 - 18, 300, 20,
                Component.translatable(masterRequired && !revealed
                        ? "blockvault.vault.reconfirm" : "blockvault.card.password"));
        passwordBox.setMaxLength(256);
        passwordBox.addFormatter((value, cursor) -> showPassword
                ? FormattedCharSequence.forward(value, Style.EMPTY)
                : FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(passwordBox);
        if (masterRequired && !revealed) {
            addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.reconfirm"), button -> { VaultRuntime.activity(); reauthenticate(); })
                    .bounds(x, height / 2 + 12, 144, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.save"), button -> { VaultRuntime.activity(); save(); })
                    .bounds(x, height / 2 + 12, 94, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.generate"), button -> { VaultRuntime.activity(); generate(); })
                    .bounds(x + 103, height / 2 + 12, 94, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("blockvault.card.reveal"), button -> { VaultRuntime.activity(); showPassword = !showPassword; })
                    .bounds(x + 206, height / 2 + 12, 94, 20).build());
        }
        if (!masterRequired || revealed) {
            addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.copy"), button -> { VaultRuntime.activity(); VaultRuntime.copyPassword(row); })
                    .bounds(x, height / 2 + 38, 94, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable(confirmDelete
                        ? "blockvault.vault.delete.confirm" : "blockvault.vault.delete"), button -> {
                    if (confirmDelete) {
                        VaultRuntime.deleteAccount(row);
                        VaultRuntime.openVaultScreen();
                    } else {
                        confirmDelete = true;
                        button.setMessage(Component.translatable("blockvault.vault.delete.confirm"));
                    }
                }).bounds(x + 103, height / 2 + 38, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button -> { VaultRuntime.activity(); VaultRuntime.openVaultScreen(); })
                .bounds(x + 206, height / 2 + 38, 94, 20).build());
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
        VaultRuntime.updatePassword(row, password);
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
        graphics.centeredText(font, Component.literal(row.serverName() + " · " + row.username()), width / 2,
                height / 2 - 52, 0xffffffff);
        graphics.centeredText(font, Component.literal(row.host()), width / 2, height / 2 - 36, 0xffb9b4c5);
        if (!checked) graphics.centeredText(font, Component.translatable("blockvault.settings.loading"), width / 2,
                height / 2 + 64, 0xffaaaaaa);
        if (revealFailed) graphics.centeredText(font, Component.translatable("blockvault.vault.unlock.failed"),
                width / 2, height / 2 + 64, 0xffff7777);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (passwordBox != null) passwordBox.setValue("");
        super.onClose();
    }
}
