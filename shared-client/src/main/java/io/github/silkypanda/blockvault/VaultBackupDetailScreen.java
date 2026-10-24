package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.vault.Account;
import io.github.silkypanda.blockvault.core.vault.BackupSummary;
import io.github.silkypanda.blockvault.core.vault.ServerEntry;
import io.github.silkypanda.blockvault.core.vault.Vault;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shows backup metadata and lets the player reveal credentials in an unlocked backup. */
final class VaultBackupDetailScreen extends Screen {
    private static final int PAGE_SIZE = 5;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final BackupSummary backup;
    private final Vault contents;
    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private final List<char[]> revealed = new ArrayList<>();
    private int page;
    private boolean confirmRestore;
    private boolean busy;

    VaultBackupDetailScreen(BackupSummary backup, Vault contents, Screen parent) {
        super(Component.translatable("blockvault.backup.details.title"));
        this.backup = backup;
        this.contents = contents;
        this.parent = parent;
        for (ServerEntry server : contents.servers()) {
            for (Account account : server.accounts()) {
                if (account.metadata().has("deleted") && account.metadata().get("deleted").getAsBoolean()) continue;
                entries.add(new Entry(server, account));
                revealed.add(null);
            }
        }
    }

    @Override protected void init() {
        int center = width / 2;
        int totalPages = totalPages();
        page = Math.min(page, totalPages - 1);
        int pageCount = Math.min(PAGE_SIZE, Math.max(0, entries.size() - page * PAGE_SIZE));
        int top = Math.max(54, height / 2 - (pageCount * 27) / 2);
        for (int local = 0; local < pageCount; local++) {
            int index = page * PAGE_SIZE + local;
            int y = top + local * 27;
            Button revealButton = Button.builder(Component.translatable(revealed.get(index) == null
                            ? "blockvault.backup.password.reveal" : "blockvault.backup.password.hide"),
                    button -> togglePassword(index, button))
                    .bounds(width - 102, y, 92, 20).build();
            revealButton.active = !busy;
            addRenderableWidget(revealButton);
        }
        int controlsY = height - 31;
        Button previous = Button.builder(Component.translatable("blockvault.backup.previous"), button -> {
            VaultRuntime.activity();
            clearRevealed(); page--; rebuildWidgets();
        }).bounds(center - 205, controlsY, 92, 20).build();
        previous.active = page > 0 && !busy;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.translatable("blockvault.backup.next"), button -> {
            VaultRuntime.activity();
            clearRevealed(); page++; rebuildWidgets();
        }).bounds(center - 105, controlsY, 92, 20).build();
        next.active = page < totalPages - 1 && !busy;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.translatable(confirmRestore
                        ? "blockvault.backup.confirm.restore" : "blockvault.backup.restore.selected"), button -> {
                    VaultRuntime.activity(); restore();
                })
                .bounds(center + 1, controlsY, 125, 20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button -> {
                VaultRuntime.activity();
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(parent);
            })
                .bounds(center + 132, controlsY, 92, 20).build());
    }

    private int totalPages() { return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

    private void togglePassword(int index, Button button) {
        VaultRuntime.activity();
        if (revealed.get(index) == null) {
            char[] password = entries.get(index).account().password();
            try { revealed.set(index, password.clone()); }
            finally { java.util.Arrays.fill(password, '\0'); }
            button.setMessage(Component.translatable("blockvault.backup.password.hide"));
        } else {
            java.util.Arrays.fill(revealed.get(index), '\0');
            revealed.set(index, null);
            button.setMessage(Component.translatable("blockvault.backup.password.reveal"));
        }
    }

    private void restore() {
        if (busy) return;
        if (!confirmRestore) { confirmRestore = true; rebuildWidgets(); return; }
        busy = true;
        clearRevealed();
        rebuildWidgets();
        VaultRuntime.restoreBackup(backup, success -> {
            busy = false;
            if (success) {
                clearContents();
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(parent);
                if (parent instanceof VaultRecoveryScreen recovery) recovery.refreshBackups();
            } else {
                confirmRestore = false;
                rebuildWidgets();
            }
        });
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String kind = Component.translatable(backup.manual() ? "blockvault.backup.manual" : "blockvault.backup.automatic").getString();
        graphics.centeredText(font, title, width / 2, 12, 0xffffffff);
        graphics.centeredText(font, Component.translatable("blockvault.backup.details.created", kind, backup.slot(),
                DATE.format(Instant.ofEpochMilli(backup.createdAt())), backup.sizeBytes()), width / 2, 31, 0xffc5c0cf);
        graphics.centeredText(font, Component.translatable("blockvault.backup.details.count", entries.size(), contents.servers().size()),
                width / 2, 44, 0xffc5c0cf);
        int pageCount = Math.min(PAGE_SIZE, Math.max(0, entries.size() - page * PAGE_SIZE));
        int top = Math.max(54, height / 2 - (pageCount * 27) / 2);
        for (int local = 0; local < pageCount; local++) {
            int index = page * PAGE_SIZE + local;
            Entry entry = entries.get(index);
            int y = top + local * 27;
            String server = BlockVaultConfig.current().streamerMode()
                    ? Component.translatable("blockvault.settings.streamer.hidden").getString() : entry.server().name();
            String username = BlockVaultConfig.current().streamerMode()
                    ? Component.translatable("blockvault.settings.streamer.hidden").getString() : entry.account().username();
            graphics.text(font, Component.literal(clip(server + " · " + username, width / 2 - 20)),
                    10, y + 5, 0xffffffff);
            char[] password = revealed.get(index);
            if (password != null) graphics.text(font, Component.literal(clip(new String(password), Math.max(1, width / 2 - 125))), width / 2 + 5,
                    y + 5, 0xffd9d3e5);
        }
        graphics.centeredText(font, Component.translatable("blockvault.backup.page", page + 1, totalPages()),
                width / 2, height - 45, 0xffaaaaaa);
        if (busy) graphics.centeredText(font, Component.translatable("blockvault.backup.working"),
                width / 2, height - 60, 0xffff7777);
    }

    private static String clip(String value, int maxPixels) {
        int maxChars = Math.max(1, maxPixels / 6);
        return value.length() <= maxChars ? value : maxChars < 2 ? "…" : value.substring(0, maxChars - 1) + "…";
    }

    private void clearRevealed() {
        for (int i = 0; i < revealed.size(); i++) {
            char[] value = revealed.get(i);
            if (value != null) java.util.Arrays.fill(value, '\0');
            revealed.set(i, null);
        }
    }

    private void clearContents() { contents.servers().forEach(server -> server.accounts().forEach(Account::clearPassword)); }

    @Override public void removed() { clearRevealed(); clearContents(); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }

    private record Entry(ServerEntry server, Account account) { }
}
