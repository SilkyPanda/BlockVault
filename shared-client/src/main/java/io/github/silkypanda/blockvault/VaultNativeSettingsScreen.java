package io.github.silkypanda.blockvault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native settings and vault-management fallback used when YACL is not installed. */
final class VaultNativeSettingsScreen extends Screen {
    private final Screen parent;
    private BlockVaultConfig.Settings settings = BlockVaultConfig.Settings.defaults();
    private boolean loaded;

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
        int row = 20;
        addToggle(left, 24, contentWidth, "blockvault.config.option.autofill", "blockvault.settings.description.autofill", settings.autofill(), value ->
                update(current -> current.withAutofill(value)));
        addToggle(left, 24 + row, contentWidth, "blockvault.config.option.passwordless", "blockvault.settings.description.passwordless", settings.passwordlessLogin(), value ->
                update(current -> current.withPasswordlessLogin(value)));
        addToggle(left, 24 + row * 2, contentWidth, "blockvault.config.option.streamer", "blockvault.settings.description.streamer", settings.streamerMode(), value ->
                update(current -> current.withStreamerMode(value)));
        addToggle(left, 24 + row * 3, contentWidth, "blockvault.config.option.scrub_history", "blockvault.settings.description.scrub_history", settings.scrubHistory(), value ->
                update(current -> current.withScrubHistory(value)));
        addToggle(left, 24 + row * 4, contentWidth, "blockvault.config.option.unlock_policy",
                "blockvault.settings.description.unlock_policy", settings.trustDevice(), value -> {
                    update(current -> current.withTrustDevice(value), () -> VaultRuntime.setTrustedDevice(value));
                });
        addDelaySlider(left, 24 + row * 5, contentWidth);
        addValue(left, 24 + row * 6, contentWidth, "blockvault.config.option.position", "blockvault.settings.description.position",
                () -> Component.translatable("blockvault.config.position." + settings.cardPosition()),
                () -> {
                    java.util.List<String> positions = java.util.List.of("top_left", "top_right", "bottom_left", "bottom_right");
                    String next = positions.get((positions.indexOf(settings.cardPosition()) + 1) % positions.size());
                    update(current -> current.withCardPosition(next));
                });
        addValue(left, 24 + row * 7, contentWidth, "blockvault.config.option.generator", "blockvault.settings.description.generator",
                () -> Component.translatable("blockvault.settings.generator.preset." + settings.generator()),
                () -> {
                    java.util.List<String> presets = java.util.List.of("basic", "memorable");
                    String next = presets.get((presets.indexOf(settings.generator()) + 1) % presets.size());
                    update(current -> current.withGenerator(next));
                });
        int actionY = 198;
        int gap = 8;
        int buttonWidth = (contentWidth - gap) / 2;
        addAction(left, actionY, buttonWidth, "blockvault.config.action.vault", "blockvault.config.action.vault.description", () -> VaultRuntime.openVaultScreen(this));
        addAction(left + buttonWidth + gap, actionY, buttonWidth, "blockvault.config.action.backups", "blockvault.config.action.backups.description", () -> VaultRuntime.openBackupScreen(this));
        addAction(left, actionY + 24, buttonWidth, "blockvault.config.action.master", "blockvault.config.action.master.description", () -> VaultRuntime.openMasterPasswordScreen(this));
        addAction(left + buttonWidth + gap, actionY + 24, buttonWidth, "blockvault.transfer.import.short", "blockvault.transfer.import.help",
                () -> VaultRuntime.openImportScreen(this));
        addAction(left, actionY + 48, buttonWidth, "blockvault.transfer.export.short", "blockvault.transfer.export.help",
                () -> Minecraft.getInstance().setScreenAndShow(new VaultTransferScreen(this, false)));
        addAction(left + buttonWidth + gap, actionY + 48, buttonWidth, "blockvault.config.action.erase_data", "blockvault.settings.description.data.erase",
                () -> Minecraft.getInstance().setScreenAndShow(new VaultEraseScreen(this)));
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button ->
                Minecraft.getInstance().setScreenAndShow(parent)).bounds(left, actionY + 72, contentWidth, 20).build());
    }

    private void addToggle(int x, int y, int controlWidth, String key, String tooltipKey, boolean value,
                           java.util.function.Consumer<Boolean> change) {
        String label = Component.translatable(value ? "options.on" : "options.off").getString();
        Component message = Component.translatable(key).append(Component.literal(": ")).append(Component.literal(label));
        addRenderableWidget(Button.builder(message, button -> change.accept(!value)).bounds(x, y, controlWidth, 20)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey))).build());
    }

    private void addAction(int x, int y, int buttonWidth, String key, String tooltipKey, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable(key), button -> action.run())
                .bounds(x, y, buttonWidth, 20).tooltip(Tooltip.create(Component.translatable(tooltipKey))).build());
    }

    private void addDelaySlider(int x, int y, int controlWidth) {
        int steps = (5000 - 200) / 100;
        double normalized = (settings.autofillDelayMillis() - 200) / 4800.0;
        AbstractSliderButton slider = new AbstractSliderButton(x, y, controlWidth, 20,
                delayMessage(settings.autofillDelayMillis()), normalized) {
            @Override protected void updateMessage() {
                int millis = 200 + (int) Math.round(value * steps) * 100;
                setMessage(delayMessage(millis));
            }

            @Override protected void applyValue() {
                int millis = 200 + (int) Math.round(value * steps) * 100;
                value = (millis - 200) / 4800.0;
                updateMessage();
                VaultNativeSettingsScreen.this.update(current -> current.withAutofillDelayMillis(millis));
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

    private Button addValue(int x, int y, int controlWidth, String key, String tooltipKey,
                            java.util.function.Supplier<Component> value, Runnable next) {
        Component label = Component.translatable(key).append(Component.literal(": ")).append(value.get());
        Button button = Button.builder(label, pressed -> next.run()).bounds(x, y, controlWidth, 20)
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
        if (loaded) graphics.centeredText(font, Component.translatable("blockvault.settings.native.hint"),
                width / 2, 294, 0xffaaa5b5);
        if (!loaded) graphics.centeredText(font, Component.translatable("blockvault.settings.loading"), width / 2,
                height / 2, 0xffaaaaaa);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
}
