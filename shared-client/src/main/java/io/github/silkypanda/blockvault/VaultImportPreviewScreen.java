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
            int shown = 0;
            for (ImportPreview.Change change : preview.changes()) {
                if (shown++ >= MAX_LINES) {
                    graphics.centeredText(font, Component.translatable("blockvault.import.more",
                            preview.changes().size() - MAX_LINES), width / 2, y, 0xffaaaaaa);
                    break;
                }
                String server = streamer ? Component.translatable("blockvault.settings.streamer.hidden").getString()
                        : change.server();
                String user = change.username() == null ? "" : change.username();
                Component line = Component.translatable("blockvault.import." + change.kind().name().toLowerCase(
                        java.util.Locale.ROOT), user, server);
                graphics.centeredText(font, line, width / 2, y, color(change.kind()));
                y += 14;
            }
        }
        if (busy) graphics.centeredText(font, Component.translatable("blockvault.transfer.working"), width / 2,
                height - 50, 0xffaaaaaa);
        else if (failed) graphics.centeredText(font, Component.translatable("blockvault.transfer.failed"), width / 2,
                height - 50, 0xffff7777);
    }

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
