package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.vault.BackupSummary;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Requests the password that encrypted a selected backup before recovering a damaged or missing vault. */
final class VaultBackupRecoveryScreen extends Screen {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final BackupSummary backup;
    private final Screen parent;
    private EditBox password;
    private boolean busy;
    private String failureKey;

    VaultBackupRecoveryScreen(BackupSummary backup, Screen parent) {
        super(Component.translatable("blockvault.backup.recovery.title"));
        this.backup = backup;
        this.parent = parent;
    }

    @Override protected void init() {
        int center = width / 2;
        password = new EditBox(font, center - 120, height / 2 - 6, 240, 20,
                Component.translatable("blockvault.backup.recovery.password"));
        password.setHint(Component.translatable("blockvault.backup.recovery.password"));
        password.setMaxLength(256);
        password.addFormatter((value, cursor) -> FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(password);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.backup.recovery.restore"), button -> restore())
                .bounds(center - 120, height / 2 + 24, 116, 20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button ->
                Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(center + 4, height / 2 + 24, 116, 20).build());
        setInitialFocus(password);
    }

    private void restore() {
        if (busy || password.getValue().isEmpty()) return;
        char[] secret = password.getValue().toCharArray();
        password.setValue("");
        busy = true;
        failureKey = null;
        rebuildWidgets();
        VaultRuntime.recoverDamagedVault(backup, secret, failed -> {
            busy = false;
            failureKey = failed;
            if (failed != null && Minecraft.getInstance().gui.screen() == this) rebuildWidgets();
        });
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, Math.max(16, height / 2 - 76), 0xffffffff);
        String kind = Component.translatable(backup.manual() ? "blockvault.backup.manual" : "blockvault.backup.automatic").getString();
        graphics.centeredText(font, Component.translatable("blockvault.backup.recovery.selected", kind, backup.slot(),
                DATE.format(Instant.ofEpochMilli(backup.createdAt()))), width / 2, height / 2 - 50, 0xffc5c0cf);
        graphics.centeredText(font, Component.translatable("blockvault.backup.recovery.explanation"), width / 2,
                height / 2 - 30, 0xffc5c0cf);
        if (busy || failureKey != null) graphics.centeredText(font,
                Component.translatable(busy ? "blockvault.backup.working" : failureKey), width / 2,
                Math.min(height - 16, height / 2 + 58), 0xffff7777);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (password != null) password.setValue(""); super.onClose(); }
    @Override public void removed() { if (password != null) password.setValue(""); super.removed(); }
}
