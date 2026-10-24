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
    private boolean showPassword;
    private EditBox passwordBox;

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
        passwordBox.addFormatter((value, cursor) -> showPassword
                ? FormattedCharSequence.forward(value, Style.EMPTY)
                : FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(passwordBox);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.save"), button -> { VaultRuntime.activity(); save(); })
                .bounds(x, top + 52, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.generate"), button -> { VaultRuntime.activity(); generate(); })
                .bounds(x + 103, top + 52, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.card.reveal"), button -> { VaultRuntime.activity(); showPassword = !showPassword; })
                .bounds(x + 206, top + 52, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.copy"), button -> { VaultRuntime.activity(); VaultRuntime.copyPassword(row); })
                .bounds(x, top + 78, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.delete"), button ->
                        Minecraft.getInstance().setScreenAndShow(new VaultDeleteScreen(row, this)))
                .bounds(x + 103, top + 78, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button -> { VaultRuntime.activity(); VaultRuntime.openVaultScreen(); })
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
        String serverName = BlockVaultConfig.current().streamerMode()
                ? Component.translatable("blockvault.settings.streamer.hidden").getString() : row.serverName();
        graphics.centeredText(font, Component.literal(serverName + " · " + row.username()), width / 2,
                height / 2 - 92, 0xffffffff);
        if (!BlockVaultConfig.current().streamerMode()) {
            graphics.centeredText(font, Component.literal(row.host()), width / 2, height / 2 - 76, 0xffb9b4c5);
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (passwordBox != null) passwordBox.setValue("");
        super.onClose();
    }
}
