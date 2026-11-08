package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.vault.BackupSummary;
import io.github.silkypanda.blockvault.core.vault.Vault;
import io.github.silkypanda.blockvault.core.validation.InputRules;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Requests the older master password when a recovery point uses an earlier key. */
final class VaultBackupPasswordScreen extends BlockVaultScreen implements BlockVaultManagedScreen {
    private final BackupSummary backup;
    private EditBox password;
    private boolean busy;
    private String statusKey;

    VaultBackupPasswordScreen(BackupSummary backup, Screen parent) {
        super(Component.translatable("blockvault.backup.older_password.title"), parent);
        this.backup = backup;
    }

    @Override protected void init() {
        int x = width / 2 - 120;
        password = new EditBox(font, x, height / 2 - 22, 240, 20,
                Component.translatable("blockvault.backup.recovery.password"));
        password.setHint(Component.translatable("blockvault.backup.recovery.password"));
        password.setMaxLength(InputRules.MASTER_PASSWORD_MAX * 2);
        InputFilter.bind(password, InputRules::isMasterPasswordInput, null);
        password.addFormatter((value, cursor) -> FormattedCharSequence.forward("•".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(password);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.backup.older_password.inspect"), button -> inspect())
                .bounds(x, height / 2 + 12, 150, 20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button ->
                returnToParent()).bounds(x + 158, height / 2 + 12, 82, 20).build());
        setInitialFocus(password);
    }

    private void inspect() {
        if (busy || password.getValue().isEmpty()) return;
        busy = true;
        statusKey = null;
        char[] secret = password.getValue().toCharArray();
        char[] forRestore = secret.clone();
        password.setValue("");
        VaultRuntime.inspectBackup(backup, secret, contents -> {
            busy = false;
            if (Minecraft.getInstance().gui.screen() != this) {
                wipe(contents);
                Arrays.fill(forRestore, '\0');
                return;
            }
            if (contents == null) {
                Arrays.fill(forRestore, '\0');
                statusKey = "blockvault.backup.older_password.failed";
                return;
            }
            ScreenSwitcher.show(new VaultBackupDetailScreen(backup, contents, parent, forRestore));
            Arrays.fill(forRestore, '\0');
        });
    }

    private static void wipe(Vault vault) {
        if (vault == null) return;
        vault.servers().forEach(server -> server.accounts().forEach(io.github.silkypanda.blockvault.core.vault.Account::clearPassword));
        vault.trash().forEach(entry -> entry.server().accounts().forEach(io.github.silkypanda.blockvault.core.vault.Account::clearPassword));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, Component.translatable("blockvault.backup.older_password.description"), width / 2,
                height / 2 - 54, 0xffc5c0cf);
        if (statusKey != null) graphics.centeredText(font, Component.translatable(statusKey), width / 2,
                height / 2 + 42, 0xffff7777);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        if (password != null) password.setValue("");
        returnToParent();
    }

    @Override public void removed() {
        if (password != null) password.setValue("");
        super.removed();
    }
}
