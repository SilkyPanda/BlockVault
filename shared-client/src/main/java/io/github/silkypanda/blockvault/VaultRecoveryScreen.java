package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.vault.BackupSummary;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Lists encrypted automatic and manual recovery points. */
final class VaultRecoveryScreen extends Screen {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final Screen parent;
    private List<BackupSummary> backups = List.of();
    private int page;
    private int pageSize;
    private int firstRowY;
    private boolean busy;
    private String statusKey;
    private boolean loadingStarted;

    VaultRecoveryScreen(Screen parent) {
        super(Component.translatable("blockvault.backup.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        pageSize = Math.max(1, Math.min(6, (height - 150) / 25));
        page = Math.min(page, totalPages() - 1);
        int center = width / 2;
        firstRowY = Math.max(60, height / 2 - pageSize * 13);
        int buttonWidth = Math.max(150, Math.min(390, width - 40));
        List<BackupSummary> visible = pageBackups();
        for (int i = 0; i < visible.size(); i++) {
            BackupSummary summary = visible.get(i);
            Button button = Button.builder(Component.literal(backupLabel(summary)), ignored -> inspect(summary))
                    .bounds(center - buttonWidth / 2, firstRowY + i * 25, buttonWidth, 20).build();
            addRenderableWidget(button);
        }
        int controlsY = Math.min(height - 48, firstRowY + pageSize * 25 + 3);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.backup.previous"), button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(center - 150, controlsY, 92, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.translatable("blockvault.backup.next"), button -> {
            page = Math.min(totalPages() - 1, page + 1);
            rebuildWidgets();
        }).bounds(center + 58, controlsY, 92, 20).build()).active = page < totalPages() - 1;
        addRenderableWidget(Button.builder(Component.translatable("blockvault.backup.manual.create"), button -> createManual())
                .bounds(center - 150, height - 30, 148, 20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button ->
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(center + 2, height - 30, 148, 20).build());
        if (!loadingStarted) {
            loadingStarted = true;
            VaultRuntime.listBackups(loaded -> {
                if (net.minecraft.client.Minecraft.getInstance().gui.screen() != this) return;
                backups = loaded;
                page = Math.min(page, totalPages() - 1);
                rebuildWidgets();
            });
        }
    }

    private void inspect(BackupSummary summary) {
        if (busy) return;
        busy = true;
        VaultRuntime.inspectBackup(summary, contents -> {
            busy = false;
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() != this) {
                wipe(contents);
                return;
            }
            if (contents == null) {
                statusKey = "blockvault.backup.failed";
                rebuildWidgets();
            } else {
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                        new VaultBackupDetailScreen(summary, contents, this));
            }
        });
    }

    private void createManual() {
        if (busy) return;
        busy = true;
        statusKey = null;
        rebuildWidgets();
        VaultRuntime.createManualBackup(success -> {
            busy = false;
            statusKey = success ? "blockvault.backup.manual.created" : "blockvault.backup.failed";
            if (success) VaultRuntime.listBackups(loaded -> {
                if (net.minecraft.client.Minecraft.getInstance().gui.screen() != this) return;
                backups = loaded;
                page = 0;
                rebuildWidgets();
            });
            rebuildWidgets();
        });
    }

    private List<BackupSummary> pageBackups() {
        int from = Math.min(page * pageSize, backups.size());
        return backups.subList(from, Math.min(backups.size(), from + pageSize));
    }

    private int totalPages() { return Math.max(1, (backups.size() + Math.max(1, pageSize) - 1) / Math.max(1, pageSize)); }

    private String backupLabel(BackupSummary summary) {
        String kind = Component.translatable(summary.manual() ? "blockvault.backup.manual" : "blockvault.backup.automatic").getString();
        return Component.translatable("blockvault.backup.entry", kind, summary.slot(),
                DATE.format(Instant.ofEpochMilli(summary.createdAt())), summary.sizeBytes()).getString();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 14, 0xffffffff);
        graphics.centeredText(font, Component.translatable("blockvault.backup.help"), width / 2, 34, 0xffc5c0cf);
        graphics.centeredText(font, Component.translatable("blockvault.backup.page", Math.min(page + 1, totalPages()), totalPages()),
                width / 2, Math.min(height - 56, firstRowY + pageSize * 25 + 27), 0xffaaaaaa);
        if (busy || statusKey != null) graphics.centeredText(font, Component.translatable(busy
                ? "blockvault.backup.working" : statusKey), width / 2, height - 42,
                busy ? 0xffc5c0cf : "blockvault.backup.manual.created".equals(statusKey) ? 0xff77dd88 : 0xffff7777);
        if (backups.isEmpty() && !busy) graphics.centeredText(font, Component.translatable("blockvault.backup.empty"),
                width / 2, height / 2, 0xffc5c0cf);
    }

    private static void wipe(io.github.silkypanda.blockvault.core.vault.Vault value) {
        if (value != null) value.servers().forEach(server -> server.accounts().forEach(account -> account.clearPassword()));
    }

    @Override public boolean isPauseScreen() { return false; }
}
