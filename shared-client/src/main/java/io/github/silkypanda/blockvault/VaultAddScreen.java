package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.validation.InputRules;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Lets the owner save a login they already have (server address, username and password). */
final class VaultAddScreen extends BlockVaultScreen {
    private EditBox hostBox;
    private EditBox userBox;
    private EditBox passwordBox;
    private Button showButton;
    private boolean showPassword;
    private boolean busy;
    private String errorKey;

    VaultAddScreen(Screen parent) {
        super(Component.translatable("blockvault.add.title"), parent);
    }

    @Override
    protected void init() {
        int left = width / 2 - 120;
        int top = Math.max(40, centeredVerticalTop(112));
        hostBox = box(left, top, "blockvault.add.host", 255);
        userBox = box(left, top + 26, "blockvault.add.username", 64);
        passwordBox = box(left, top + 52, "blockvault.add.password", InputRules.SERVER_PASSWORD_MAX * 2);
        InputFilter.bind(hostBox, InputRules::isHostText, null);
        InputFilter.bind(userBox, InputRules::isUsernameText, null);
        InputFilter.bind(passwordBox, InputRules::isServerPasswordInput, null);
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
        char[] generated = PasswordGenerator.generate(io.github.silkypanda.blockvault.core.session.SessionPreferences.generatorPolicy(BlockVaultConfig.current().generator()));
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
                VaultRuntime.openVaultScreen(parent instanceof VaultListScreen list ? list.parentScreen() : parent);
            }
        });
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (ScreenText.isEnter(event)) { save(); return true; }
        return super.keyPressed(event);
    }

    private void clearFields() {
        if (passwordBox != null) passwordBox.setValue("");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int top = Math.max(40, centeredVerticalTop(112));
        graphics.centeredText(font, title, width / 2, top - 22, 0xffffffff);
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
        returnToParent();
    }

    @Override
    public void removed() {
        clearFields();
        super.removed();
    }
}
