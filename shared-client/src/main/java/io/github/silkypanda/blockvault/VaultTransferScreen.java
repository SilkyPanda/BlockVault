package io.github.silkypanda.blockvault;

import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Imports or exports an encrypted vault using an explicit local file path. */
final class VaultTransferScreen extends Screen {
    private final Screen parent;
    private final boolean importing;
    private EditBox pathBox;
    private EditBox passwordBox;
    private boolean failed;
    private boolean busy;

    VaultTransferScreen(Screen parent, boolean importing) {
        super(Component.translatable(importing ? "blockvault.transfer.import.title" : "blockvault.transfer.export.title"));
        this.parent = parent;
        this.importing = importing;
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        pathBox = new EditBox(font, left, height / 2 - 22, 300, 20,
                Component.translatable("blockvault.transfer.path"));
        pathBox.setHint(Component.translatable("blockvault.transfer.path"));
        pathBox.setMaxLength(1024);
        addRenderableWidget(pathBox);
        if (importing) {
            passwordBox = new EditBox(font, left, height / 2 + 4, 300, 20,
                    Component.translatable("blockvault.vault.password"));
            passwordBox.setHint(Component.translatable("blockvault.vault.password"));
            passwordBox.setMaxLength(1024);
            passwordBox.addFormatter((value, cursor) -> FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
            addRenderableWidget(passwordBox);
        }
        addRenderableWidget(Button.builder(Component.translatable(importing
                        ? "blockvault.transfer.import" : "blockvault.transfer.export"), button -> transfer())
                .bounds(left, height / 2 + 36, 144, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"),
                button -> net.minecraft.client.Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(left + 156, height / 2 + 36, 144, 20).build());
    }

    private void transfer() {
        if (busy || pathBox.getValue().isBlank()) return;
        busy = true;
        failed = false;
        failureKey = null;
        Path path;
        try { path = Path.of(pathBox.getValue().trim()); }
        catch (RuntimeException invalidPath) { busy = false; failed = true; return; }
        if (!importing) {
            VaultRuntime.exportVault(path, success -> finish(success));
            return;
        }
        char[] password = passwordBox.getValue().toCharArray();
        passwordBox.setValue("");
        VaultRuntime.previewImport(path, password, (preview, failureKey) -> {
            Arrays.fill(password, '\0');
            busy = false;
            if (preview == null) {
                failed = true;
                this.failureKey = failureKey;
                return;
            }
            net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new VaultImportPreviewScreen(parent, preview));
        });
    }

    private String failureKey;

    private void finish(boolean success) {
        busy = false;
        failed = !success;
        failureKey = null;
        if (success) VaultRuntime.openVaultScreen();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 60, 0xffffffff);
        graphics.centeredText(font, Component.translatable(importing
                ? "blockvault.transfer.import.help" : "blockvault.transfer.export.help"), width / 2,
                height / 2 - 43, 0xffc5c0cf);
        if (busy) graphics.centeredText(font, Component.translatable("blockvault.transfer.working"),
                width / 2, height / 2 + 65, 0xffaaaaaa);
        else if (failed) graphics.centeredText(font, Component.translatable(failureKey != null ? failureKey
                : "blockvault.transfer.failed"),
                width / 2, height / 2 + 65, 0xffff7777);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        if (passwordBox != null) passwordBox.setValue("");
        super.onClose();
    }

    @Override public void removed() {
        if (passwordBox != null) passwordBox.setValue("");
        super.removed();
    }
}
