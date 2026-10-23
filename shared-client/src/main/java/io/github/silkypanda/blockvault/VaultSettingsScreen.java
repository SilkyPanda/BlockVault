package io.github.silkypanda.blockvault;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** Vanilla settings screen for non-secret client preferences. */
final class VaultSettingsScreen extends OptionsSubScreen {
    private boolean settingsLoaded;
    private boolean loadRequested;
    private BlockVaultConfig.Settings settings = BlockVaultConfig.Settings.defaults();
    private boolean saving;

    VaultSettingsScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("blockvault.settings.title"));
    }

    @Override
    protected void init() {
        if (!loadRequested) {
            loadRequested = true;
            BlockVaultConfig.load(value -> {
                settings = value;
                settingsLoaded = true;
                rebuildWidgets();
            });
        }
        if (!settingsLoaded) {
            addRenderableWidget(Button.builder(Component.translatable("blockvault.settings.back"), button -> onClose())
                    .bounds(width / 2 - 100, height - 28, 200, 20).build());
            return;
        }
        // OptionsSubScreen.init builds and stores its layout; run it only once,
        // after the asynchronous settings load, to avoid duplicate option lists.
        super.init();
    }

    @Override
    protected void addOptions() {
        if (!settingsLoaded) return;
        addSettingButton("blockvault.settings.autofill", settings.autofill(), current -> current.withAutofill(!current.autofill()));
        addSettingButton("blockvault.settings.disable_auto_login", settings.disableAutoLogin(),
                current -> current.withDisableAutoLogin(!current.disableAutoLogin()));
        addSettingButton("blockvault.settings.delay", settings.autofillDelayMillis(),
                current -> current.withDelay(next(current.autofillDelayMillis(), List.of(0, 400, 800, 1500, 3000))));
        addSettingButton("blockvault.settings.auto_lock", settings.autoLockMinutes(),
                current -> current.withAutoLock(next(current.autoLockMinutes(), List.of(0, 5, 15, 30, 60))));
        addSettingButton("blockvault.settings.duration", settings.cardDurationSeconds(),
                current -> current.withDuration(next(current.cardDurationSeconds(), List.of(5, 10, 15, 30))));
        addSettingButton("blockvault.settings.position", settings.cardPosition(),
                current -> current.withPosition(next(current.cardPosition(), List.of("top_right", "bottom_right", "bottom_left", "top_left"))));
        addSettingButton("blockvault.settings.streamer", settings.streamerMode(),
                current -> current.withStreamer(!current.streamerMode()));
        addSettingButton("blockvault.settings.generator", generatorLabel(settings.generator()),
                current -> current.withGenerator(next(current.generator(), List.of("defaults", "pickyServer", "max", "memorable"))));
        addSettingButton("blockvault.settings.scrub_history", settings.scrubHistory(),
                current -> current.withScrubHistory(!current.scrubHistory()));
        addSettingButton("blockvault.settings.unlock_policy", unlockPolicyLabel(settings.trustDevice()),
                current -> current.withTrustDevice(!current.trustDevice()));
        list.addBig(Button.builder(Component.translatable("blockvault.settings.vault"),
                button -> VaultRuntime.openVaultScreen()).width(300).build());
    }

    @Override
    protected void addFooter() {
        layout.addToFooter(Button.builder(Component.translatable("blockvault.settings.back"), button -> onClose())
                .width(200).build());
    }

    private void addSettingButton(String key, Object value,
                                  java.util.function.UnaryOperator<BlockVaultConfig.Settings> change) {
        Button button = Button.builder(label(key, value), pressed -> {
            if (saving) return;
            saving = true;
            pressed.active = false;
            BlockVaultConfig.update(change, saved -> {
                saving = false;
                pressed.active = true;
                if (saved) {
                    settings = BlockVaultConfig.current();
                    Hooks.applySettings(settings);
                    if (key.equals("blockvault.settings.unlock_policy")) {
                        VaultRuntime.setTrustedDevice(settings.trustDevice());
                    }
                    VaultRuntime.activity();
                    pressed.setMessage(label(key, settingValue(key)));
                } else VaultRuntime.showToast("blockvault.vault.error");
            });
        }).tooltip(Tooltip.create(Component.translatable(
                "blockvault.settings.description." + key.substring("blockvault.settings.".length())))
        ).width(300).build();
        list.addBig(button);
    }

    private Object settingValue(String key) {
        return switch (key) {
            case "blockvault.settings.autofill" -> settings.autofill();
            case "blockvault.settings.disable_auto_login" -> settings.disableAutoLogin();
            case "blockvault.settings.delay" -> settings.autofillDelayMillis();
            case "blockvault.settings.auto_lock" -> settings.autoLockMinutes();
            case "blockvault.settings.duration" -> settings.cardDurationSeconds();
            case "blockvault.settings.position" -> settings.cardPosition();
            case "blockvault.settings.streamer" -> settings.streamerMode();
            case "blockvault.settings.generator" -> generatorLabel(settings.generator());
            case "blockvault.settings.scrub_history" -> settings.scrubHistory();
            case "blockvault.settings.unlock_policy" -> unlockPolicyLabel(settings.trustDevice());
            default -> "";
        };
    }

    private static Component unlockPolicyLabel(boolean trusted) {
        return Component.translatable(trusted ? "blockvault.settings.unlock_policy.trust"
                : "blockvault.settings.unlock_policy.ask");
    }

    private static Component generatorLabel(String preset) {
        return Component.translatable("blockvault.settings.generator.preset." + preset);
    }

    private static Component label(String key, Object value) { return Component.translatable(key, value); }

    private static <T> T next(T current, List<T> choices) {
        int at = choices.indexOf(current);
        return choices.get((at + 1 + choices.size()) % choices.size());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!settingsLoaded || saving) graphics.centeredText(font,
                Component.translatable(saving ? "blockvault.settings.saving" : "blockvault.settings.loading"),
                width / 2, height - 46, 0xffaaaaaa);
    }

    @Override public boolean isPauseScreen() { return false; }

}
