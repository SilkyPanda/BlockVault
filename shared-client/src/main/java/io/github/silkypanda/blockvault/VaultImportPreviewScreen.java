package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.vault.ImportPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shows what an import file would add or change, and asks for confirmation before anything is merged. */
final class VaultImportPreviewScreen extends Screen {
    private static final int MAX_LINES = 9;
    private final Screen parent;
    private final ImportPreview preview;
    private boolean busy;
    private boolean failed;
    private boolean finished;
    private int page;

    VaultImportPreviewScreen(Screen parent, ImportPreview preview) {
        super(Component.translatable("blockvault.import.title"));
        this.parent = parent;
        this.preview = preview;
    }

    @Override
    protected void init() {
        int center = width / 2;
        Button confirm = Button.builder(Component.translatable("blockvault.import.confirm"), button -> confirm())
                .bounds(center - 154, height - 34, 150, 20).build();
        confirm.active = !preview.isEmpty();
        addRenderableWidget(confirm);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.import.cancel"), button -> onClose())
                .bounds(center + 4, height - 34, 150, 20).build());
        if (totalPages() > 1) {
            addRenderableWidget(Button.builder(Component.translatable("blockvault.backup.previous"), button -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(center - 154, height - 58, 100, 20).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.translatable("blockvault.backup.next"), button -> {
                page = Math.min(totalPages() - 1, page + 1);
                rebuildWidgets();
            }).bounds(center + 54, height - 58, 100, 20).build()).active = page < totalPages() - 1;
        }
    }

    private void confirm() {
        if (busy) return;
        busy = true;
        failed = false;
        VaultRuntime.commitImport(success -> {
            busy = false;
            if (success) {
                finished = true;
                VaultRuntime.openVaultScreen();
            } else {
                failed = true;
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xffffffff);
        boolean streamer = BlockVaultConfig.current().streamerMode();
        int y = 32;
        if (preview.isEmpty()) {
            graphics.centeredText(font, Component.translatable("blockvault.import.nothing"), width / 2, y, 0xffcccccc);
        } else {
            graphics.centeredText(font, Component.translatable("blockvault.import.summary",
                    preview.count(ImportPreview.Kind.NEW_SERVER), preview.count(ImportPreview.Kind.NEW_ACCOUNT),
                    preview.count(ImportPreview.Kind.UPDATED_ACCOUNT)), width / 2, y, 0xffcccccc);
            y += 18;
            int from = page * MAX_LINES;
            int to = Math.min(preview.changes().size(), from + MAX_LINES);
            for (int index = from; index < to; index++) {
                ImportPreview.Change change = preview.changes().get(index);
                String server = streamer ? Component.translatable("blockvault.settings.streamer.hidden").getString()
                        : change.server();
                String user = change.username() == null ? "" : change.username();
                Component line = Component.translatable("blockvault.import." + change.kind().name().toLowerCase(
                        java.util.Locale.ROOT), user, server);
                graphics.centeredText(font, line, width / 2, y, color(change.kind()));
                y += 14;
            }
            if (totalPages() > 1) graphics.centeredText(font, Component.translatable("blockvault.backup.page",
                    page + 1, totalPages()), width / 2, height - 62, 0xffaaaaaa);
        }
        if (busy) graphics.centeredText(font, Component.translatable("blockvault.transfer.working"), width / 2,
                height - 50, 0xffaaaaaa);
        else if (failed) graphics.centeredText(font, Component.translatable("blockvault.transfer.failed"), width / 2,
                height - 50, 0xffff7777);
    }

    private int totalPages() { return Math.max(1, (preview.changes().size() + MAX_LINES - 1) / MAX_LINES); }

    private static int color(ImportPreview.Kind kind) {
        return switch (kind) {
            case KEPT_LOCAL -> 0xffaaaaaa;
            case UPDATED_ACCOUNT -> 0xffffcc55;
            default -> 0xffffffff;
        };
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (!finished) VaultRuntime.discardImport();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void removed() {
        if (!finished) VaultRuntime.discardImport();
        super.removed();
    }
}
