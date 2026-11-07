package io.github.silkypanda.blockvault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Native settings and vault-management fallback used when YACL is not installed. */
final class VaultNativeSettingsScreen extends Screen implements BlockVaultManagedScreen {
    private final Screen parent;
    private BlockVaultConfig.Settings settings = BlockVaultConfig.Settings.defaults();
    private boolean loaded;
    private int hintY = -1;

    VaultNativeSettingsScreen(Screen parent) {
        super(Component.translatable("blockvault.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!loaded) {
            BlockVaultConfig.load(value -> {
                if (Minecraft.getInstance().gui.screen() == this) {
                    settings = value;
                    loaded = true;
                    clearWidgets();
                    init();
                }
            });
            return;
        }
        int contentWidth = Math.max(1, Math.min(420, width - 24));
        int left = (width - contentWidth) / 2;
        int rowCount = 13;
        int rowHeight = Math.max(14, Math.min(20, (height - 36 - (rowCount - 1) * 2) / rowCount));
        int rowStep = rowHeight + 2;
        int top = Math.max(4, (height - (rowCount * rowHeight + (rowCount - 1) * 2)) / 2);
        addToggle(left, top, contentWidth, rowHeight, "blockvault.config.option.autofill", "blockvault.settings.description.autofill", settings.autofill(), value ->
                update(current -> current.withAutofill(value)));
        addToggle(left, top + rowStep, contentWidth, rowHeight, "blockvault.config.option.passwordless", "blockvault.settings.description.passwordless", settings.passwordlessLogin(), value ->
                update(current -> current.withPasswordlessLogin(value)));
        addToggle(left, top + rowStep * 2, contentWidth, rowHeight, "blockvault.config.option.streamer", "blockvault.settings.description.streamer", settings.streamerMode(), value ->
                update(current -> current.withStreamerMode(value)));
        addToggle(left, top + rowStep * 3, contentWidth, rowHeight, "blockvault.config.option.scrub_history", "blockvault.settings.description.scrub_history", settings.scrubHistory(), value ->
                update(current -> current.withScrubHistory(value)));
        addToggle(left, top + rowStep * 4, contentWidth, rowHeight, "blockvault.config.option.unlock_policy",
                "blockvault.settings.description.unlock_policy", settings.trustDevice(), value -> {
                    update(current -> current.withTrustDevice(value), () -> VaultRuntime.setTrustedDevice(value));
                });
        addDelaySlider(left, top + rowStep * 5, contentWidth, rowHeight);
        addValue(left, top + rowStep * 6, contentWidth, rowHeight, "blockvault.config.option.position", "blockvault.settings.description.position",
                () -> Component.translatable("blockvault.config.position." + settings.cardPosition()),
                () -> {
                    java.util.List<String> positions = java.util.List.of("top_left", "top_right", "bottom_left", "bottom_right");
                    String next = positions.get((positions.indexOf(settings.cardPosition()) + 1) % positions.size());
                    update(current -> current.withCardPosition(next));
                });
        addValue(left, top + rowStep * 7, contentWidth, rowHeight, "blockvault.config.option.generator", "blockvault.settings.description.generator",
                () -> Component.translatable("blockvault.settings.generator.preset." + settings.generator()),
                () -> {
                    java.util.List<String> presets = java.util.List.of("basic", "memorable");
                    String next = presets.get((presets.indexOf(settings.generator()) + 1) % presets.size());
                    update(current -> current.withGenerator(next));
                });
        addToggle(left, top + rowStep * 8, contentWidth, rowHeight, "blockvault.config.option.animations",
                "blockvault.settings.description.animations", settings.cardAnimations(), value ->
                        update(current -> current.withCardAnimations(value)));
        int actionY = top + rowStep * 9;
        int gap = 8;
        int buttonWidth = (contentWidth - gap) / 2;
        addAction(left, actionY, buttonWidth, rowHeight, "blockvault.config.action.vault", "blockvault.config.action.vault.description", () -> VaultRuntime.openVaultScreen(this));
        addAction(left + buttonWidth + gap, actionY, buttonWidth, rowHeight, "blockvault.config.action.backups", "blockvault.config.action.backups.description", () -> VaultRuntime.openBackupScreen(this));
        addAction(left, actionY + rowStep, buttonWidth, rowHeight, "blockvault.config.action.master", "blockvault.config.action.master.description", () -> VaultRuntime.openMasterPasswordScreen(this));
        addAction(left + buttonWidth + gap, actionY + rowStep, buttonWidth, rowHeight, "blockvault.transfer.import.short", "blockvault.transfer.import.help",
                () -> VaultRuntime.openImportScreen(this));
        addAction(left, actionY + rowStep * 2, buttonWidth, rowHeight, "blockvault.transfer.export.short", "blockvault.transfer.export.help",
                () -> Minecraft.getInstance().setScreenAndShow(new VaultTransferScreen(this, false)));
        addAction(left + buttonWidth + gap, actionY + rowStep * 2, buttonWidth, rowHeight, "blockvault.config.action.erase_data", "blockvault.settings.description.data.erase",
                () -> Minecraft.getInstance().setScreenAndShow(new VaultEraseScreen(this)));
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button ->
                Minecraft.getInstance().setScreenAndShow(parent)).bounds(left, actionY + rowStep * 3, contentWidth, rowHeight).build());
        int backBottom = actionY + rowStep * 3 + rowHeight;
        hintY = backBottom <= height - 20 ? height - 10 : -1;
    }

    private void addToggle(int x, int y, int controlWidth, int controlHeight, String key, String tooltipKey, boolean value,
                           java.util.function.Consumer<Boolean> change) {
        String label = Component.translatable(value ? "options.on" : "options.off").getString();
        Component message = Component.translatable(key).append(Component.literal(": ")).append(Component.literal(label));
        addRenderableWidget(Button.builder(message, button -> change.accept(!value)).bounds(x, y, controlWidth, controlHeight)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey))).build());
    }

    private void addAction(int x, int y, int buttonWidth, int buttonHeight, String key, String tooltipKey, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable(key), button -> action.run())
                .bounds(x, y, buttonWidth, buttonHeight).tooltip(Tooltip.create(Component.translatable(tooltipKey))).build());
    }

    private void addDelaySlider(int x, int y, int controlWidth, int controlHeight) {
        int steps = (5000 - 200) / 100;
        double normalized = (settings.autofillDelayMillis() - 200) / 4800.0;
        AbstractSliderButton slider = new AbstractSliderButton(x, y, controlWidth, controlHeight,
                delayMessage(settings.autofillDelayMillis()), normalized) {
            private int selectedMillis = settings.autofillDelayMillis();

            @Override protected void updateMessage() {
                int millis = 200 + (int) Math.round(value * steps) * 100;
                setMessage(delayMessage(millis));
            }

            @Override protected void applyValue() {
                int millis = 200 + (int) Math.round(value * steps) * 100;
                value = (millis - 200) / 4800.0;
                selectedMillis = millis;
                updateMessage();
            }

            @Override public void onRelease(MouseButtonEvent event) {
                super.onRelease(event);
                saveSelection();
            }

            @Override public boolean keyPressed(KeyEvent event) {
                boolean handled = super.keyPressed(event);
                if (handled) saveSelection();
                return handled;
            }

            private void saveSelection() {
                if (selectedMillis != settings.autofillDelayMillis()) {
                    VaultNativeSettingsScreen.this.update(current -> current.withAutofillDelayMillis(selectedMillis));
                }
            }
        };
        slider.setTooltip(Tooltip.create(Component.translatable("blockvault.settings.description.delay")));
        addRenderableWidget(slider);
    }

    private static Component delayMessage(int millis) {
        return Component.translatable("blockvault.config.option.delay")
                .append(Component.literal(": "))
                .append(Component.translatable("blockvault.config.value.millis", millis));
    }

    private Button addValue(int x, int y, int controlWidth, int controlHeight, String key, String tooltipKey,
                            java.util.function.Supplier<Component> value, Runnable next) {
        Component label = Component.translatable(key).append(Component.literal(": ")).append(value.get());
        Button button = Button.builder(label, pressed -> next.run()).bounds(x, y, controlWidth, controlHeight)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey))).build();
        addRenderableWidget(button);
        return button;
    }

    private void update(java.util.function.UnaryOperator<BlockVaultConfig.Settings> change) {
        update(change, () -> { });
    }

    private void update(java.util.function.UnaryOperator<BlockVaultConfig.Settings> change, Runnable afterSave) {
        BlockVaultConfig.update(change, saved -> {
            if (!saved) {
                VaultRuntime.showToast("blockvault.vault.error");
                return;
            }
            settings = change.apply(settings);
            Hooks.applySettings(settings);
            afterSave.run();
            if (Minecraft.getInstance().gui.screen() == this) {
                clearWidgets();
                init();
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (loaded && hintY >= 0) graphics.centeredText(font,
                Component.translatable("blockvault.settings.native.hint"), width / 2, hintY, 0xffaaa5b5);
        if (!loaded) graphics.centeredText(font, Component.translatable("blockvault.settings.loading"), width / 2,
                height / 2, 0xffaaaaaa);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
}
