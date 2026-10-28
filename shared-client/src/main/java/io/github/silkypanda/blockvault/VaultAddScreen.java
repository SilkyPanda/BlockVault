package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.validation.AsciiInput;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Lets the owner save a login they already have (server address, username and password). */
final class VaultAddScreen extends Screen {
    private final Screen parent;
    private EditBox hostBox;
    private EditBox userBox;
    private EditBox passwordBox;
    private Button showButton;
    private boolean showPassword;
    private boolean busy;
    private String errorKey;
    private final CapsLockHint capsLock = new CapsLockHint();

    VaultAddScreen(Screen parent) {
        super(Component.translatable("blockvault.add.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 120;
        int top = Math.max(40, height / 2 - 56);
        hostBox = box(left, top, "blockvault.add.host", 255);
        userBox = box(left, top + 26, "blockvault.add.username", 64);
        passwordBox = box(left, top + 52, "blockvault.add.password", 1024);
        hostBox.setFilter(AsciiInput::isPrintable);
        userBox.setFilter(AsciiInput::isUsernameText);
        passwordBox.setFilter(AsciiInput::isPasswordText);
        passwordBox.setResponder(capsLock::onValueChanged);
        passwordBox.addFormatter((value, cursor) -> FormattedCharSequence.forward(
                showPassword ? value : "\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(hostBox);
        addRenderableWidget(userBox);
        addRenderableWidget(passwordBox);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.add.generate"), button -> generate())
                .bounds(left, top + 78, 116, 20).build());
        showButton = Button.builder(Component.translatable("blockvault.add.show"), button -> {
            showPassword = !showPassword;
            button.setMessage(Component.translatable(showPassword ? "blockvault.add.hide" : "blockvault.add.show"));
        }).bounds(left + 124, top + 78, 116, 20).build();
        addRenderableWidget(showButton);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.add.save"), button -> save())
                .bounds(left, top + 104, 116, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button -> onClose())
                .bounds(left + 124, top + 104, 116, 20).build());
        setInitialFocus(hostBox);
    }

    private EditBox box(int x, int y, String key, int maxLength) {
        EditBox box = new EditBox(font, x, y, 240, 20, Component.translatable(key));
        box.setHint(Component.translatable(key));
        box.setMaxLength(maxLength);
        return box;
    }

    private void generate() {
        char[] generated = PasswordGenerator.generate(BlockVaultConfig.generatorPolicy(BlockVaultConfig.current().generator()));
        try { passwordBox.setValue(new String(generated)); }
        finally { Arrays.fill(generated, '\0'); }
    }

    private void save() {
        if (busy) return;
        busy = true;
        errorKey = null;
        char[] password = passwordBox.getValue().toCharArray();
        VaultRuntime.addAccount(hostBox.getValue(), userBox.getValue(), password, failure -> {
            busy = false;
            errorKey = failure;
            if (failure == null) {
                clearFields();
                VaultRuntime.openVaultScreen();
            }
        });
    }

    private void clearFields() {
        if (passwordBox != null) passwordBox.setValue("");
        capsLock.reset();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int top = Math.max(40, height / 2 - 56);
        graphics.centeredText(font, title, width / 2, top - 22, 0xffffffff);
        if (capsLock.likelyOn()) {
            graphics.centeredText(font, Component.translatable("blockvault.access.caps_lock"), width / 2, top + 130,
                    0xffffcc55);
        }
        if (busy) {
            graphics.centeredText(font, Component.translatable("blockvault.transfer.working"), width / 2, top + 144,
                    0xffaaaaaa);
        } else if (errorKey != null) {
            graphics.centeredText(font, Component.translatable(errorKey), width / 2, top + 144, 0xffff7777);
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        clearFields();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void removed() {
        clearFields();
        super.removed();
    }
}
