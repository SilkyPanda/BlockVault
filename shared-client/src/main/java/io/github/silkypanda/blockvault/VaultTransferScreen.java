package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.validation.InputRules;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Imports an encrypted file or exports an encrypted copy to a chosen folder. */
final class VaultTransferScreen extends Screen {
    private final Screen parent;
    private final boolean importing;
    private EditBox pathBox;
    private EditBox passwordBox;
    private boolean failed;
    private boolean busy;
    private String exportedPath;

    VaultTransferScreen(Screen parent, boolean importing) {
        super(Component.translatable(importing ? "blockvault.transfer.import.title" : "blockvault.transfer.export.title"));
        this.parent = parent;
        this.importing = importing;
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        pathBox = new EditBox(font, left, height / 2 - 22, 300, 20,
                Component.translatable(importing ? "blockvault.transfer.import.path" : "blockvault.transfer.export.folder"));
        pathBox.setHint(Component.translatable(importing
                ? "blockvault.transfer.import.path" : "blockvault.transfer.export.folder"));
        pathBox.setMaxLength(1024);
        InputFilter.bind(pathBox, InputRules::isText, null);
        addRenderableWidget(pathBox);
        if (importing) {
            passwordBox = new EditBox(font, left, height / 2 + 4, 300, 20,
                    Component.translatable("blockvault.vault.password"));
            passwordBox.setHint(Component.translatable("blockvault.vault.password"));
            passwordBox.setMaxLength(InputRules.MASTER_PASSWORD_MAX * 2);
            InputFilter.bind(passwordBox, InputRules::isMasterPasswordInput, null);
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
        if (busy || (importing && pathBox.getValue().isBlank())) return;
        if (!InputRules.isText(pathBox.getValue())) { failed = true; return; }
        busy = true;
        failed = false;
        failureKey = null;
        Path selectedPath = null;
        if (!pathBox.getValue().isBlank()) {
            try { selectedPath = Path.of(pathBox.getValue().trim()); }
            catch (RuntimeException invalidPath) { busy = false; failed = true; return; }
        }
        final Path path = selectedPath;
        if (!importing) {
            VaultRuntime.exportVault(path, exported -> {
                busy = false;
                failed = exported == null;
                exportedPath = exported == null ? null : exported.getFileName().toString();
            });
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

    @Override public boolean keyPressed(KeyEvent event) {
        if (ScreenText.isEnter(event)) { transfer(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 60, 0xffffffff);
        ScreenText.drawCenteredWrapped(graphics, font, Component.translatable(importing
                ? "blockvault.transfer.import.help" : "blockvault.transfer.export.help"), width / 2,
                height / 2 - 58, width - 32, 0xffc5c0cf, 11);
        if (exportedPath != null) graphics.centeredText(font,
                Component.translatable("blockvault.transfer.exported", exportedPath),
                width / 2, height / 2 + 65, 0xff77dd88);
        else if (busy) graphics.centeredText(font, Component.translatable("blockvault.transfer.working"),
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
