package io.github.silkypanda.blockvault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
        int left = width / 2 - 102;
        int row = 20;
        addToggle(left, 24, "blockvault.config.option.autofill", settings.autofill(), value ->
                update(current -> current.withAutofill(value)));
        addToggle(left, 24 + row, "blockvault.config.option.passwordless", settings.passwordlessLogin(), value ->
                update(current -> current.withPasswordlessLogin(value)));
        addToggle(left, 24 + row * 2, "blockvault.config.option.streamer", settings.streamerMode(), value ->
                update(current -> current.withStreamerMode(value)));
        addToggle(left, 24 + row * 3, "blockvault.config.option.scrub_history", settings.scrubHistory(), value ->
                update(current -> current.withScrubHistory(value)));
        addToggle(left, 24 + row * 4, "blockvault.config.option.unlock_policy",
                settings.trustDevice(), value -> {
                    update(current -> current.withTrustDevice(value), () -> VaultRuntime.setTrustedDevice(value));
                });
        addValue(left, 24 + row * 5, "blockvault.config.option.delay",
                () -> Component.translatable("blockvault.config.value.millis", settings.autofillDelayMillis()),
                () -> update(current -> current.withAutofillDelayMillis(
                        current.autofillDelayMillis() >= 5000 ? 200 : current.autofillDelayMillis() + 100)));
        addValue(left, 24 + row * 6, "blockvault.config.option.duration",
                () -> Component.translatable("blockvault.config.value.seconds", settings.cardDurationSeconds()),
                () -> update(current -> current.withCardDurationSeconds(
                        current.cardDurationSeconds() >= 60 ? 1 : current.cardDurationSeconds() + 1)));
        addValue(left, 24 + row * 7, "blockvault.config.option.position",
                () -> Component.translatable("blockvault.config.position." + settings.cardPosition()),
                () -> {
                    java.util.List<String> positions = java.util.List.of("top_left", "top_right", "bottom_left", "bottom_right");
                    String next = positions.get((positions.indexOf(settings.cardPosition()) + 1) % positions.size());
                    update(current -> current.withCardPosition(next));
                });
        addValue(left, 24 + row * 8, "blockvault.config.option.generator",
                () -> Component.translatable("blockvault.settings.generator.preset." + settings.generator()),
                () -> {
                    java.util.List<String> presets = java.util.List.of("pickyServer", "defaults", "max", "memorable");
                    String next = presets.get((presets.indexOf(settings.generator()) + 1) % presets.size());
                    update(current -> current.withGenerator(next));
                });
        int actionY = 218;
        int gap = 4;
        int buttonWidth = 100;
        addAction(left, actionY, buttonWidth, "blockvault.config.action.vault", VaultRuntime::openVaultScreen);
        addAction(left + buttonWidth + gap, actionY, buttonWidth, "blockvault.config.action.backups", () -> VaultRuntime.openBackupScreen(this));
        addAction(left, actionY + 24, buttonWidth, "blockvault.config.action.master", () -> VaultRuntime.openMasterPasswordScreen(this));
        addAction(left + buttonWidth + gap, actionY + 24, buttonWidth, "blockvault.transfer.import.short",
                () -> Minecraft.getInstance().setScreenAndShow(new VaultTransferScreen(this, true)));
        addAction(left, actionY + 48, buttonWidth, "blockvault.transfer.export.short",
                () -> Minecraft.getInstance().setScreenAndShow(new VaultTransferScreen(this, false)));
        addAction(left + buttonWidth + gap, actionY + 48, buttonWidth, "blockvault.config.action.erase_data",
                () -> Minecraft.getInstance().setScreenAndShow(new VaultEraseScreen(this)));
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"), button ->
                Minecraft.getInstance().setScreenAndShow(parent)).bounds(left, actionY + 72, 204, 20).build());
    }

    private void addToggle(int x, int y, String key, boolean value,
                           java.util.function.Consumer<Boolean> change) {
        String label = Component.translatable(value ? "options.on" : "options.off").getString();
        Component message = Component.translatable(key).append(Component.literal(": ")).append(Component.literal(label));
        addRenderableWidget(Button.builder(message, button ->
                change.accept(!value)).bounds(x, y, 204, 20).build());
    }

    private void addAction(int x, int y, int buttonWidth, String key, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable(key), button -> action.run())
                .bounds(x, y, buttonWidth, 20).build());
    }

    private void addValue(int x, int y, String key, java.util.function.Supplier<Component> value, Runnable next) {
        Component label = Component.translatable(key).append(Component.literal(": ")).append(value.get());
        addRenderableWidget(Button.builder(label, button -> next.run()).bounds(x, y, 204, 20).build());
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
        if (loaded) graphics.centeredText(font, Component.translatable("blockvault.settings.description.unlock_policy"),
                width / 2, 207, 0xffaaa5b5);
        if (!loaded) graphics.centeredText(font, Component.translatable("blockvault.settings.loading"), width / 2,
                height / 2, 0xffaaaaaa);
    }

    @Override public boolean isPauseScreen() { return false; }
}
