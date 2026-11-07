package io.github.silkypanda.blockvault;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** YACL settings screen for BlockVault's non-secret client preferences. */
final class VaultYaclSettingsScreen extends Screen implements BlockVaultManagedScreen {
    private static final BlockVaultConfig.Settings DEFAULTS = BlockVaultConfig.Settings.defaults();
    private final Screen parent;
    private boolean loadRequested;

    VaultYaclSettingsScreen(Screen parent) {
        super(Component.translatable("blockvault.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (loadRequested) return;
        loadRequested = true;
        BlockVaultConfig.load(settings -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() == this) minecraft.setScreenAndShow(createYacl(parent, settings));
        });
    }

    private static Screen createYacl(Screen parent, BlockVaultConfig.Settings settings) {
        Draft draft = new Draft(settings);
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("blockvault.settings.title"))
                .category(loginCategory(draft))
                .category(privacyCategory(draft))
                .category(dataCategory())
                .save(() -> save(draft))
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory loginCategory(Draft draft) {
        OptionGroup credentials = OptionGroup.createBuilder()
                .name(Component.translatable("blockvault.config.group.login"))
                .description(description("blockvault.config.group.login.description"))
                .option(boolOption("blockvault.config.option.autofill", "autofill", DEFAULTS.autofill(), () -> draft.autofill,
                        value -> draft.autofill = value))
                .option(boolOption("blockvault.config.option.passwordless", "passwordless", DEFAULTS.passwordlessLogin(), () -> draft.passwordless,
                        value -> draft.passwordless = value))
                .option(Option.<Integer>createBuilder()
                        .name(Component.translatable("blockvault.config.option.delay"))
                        .description(description("blockvault.settings.description.delay"))
                        .binding(DEFAULTS.autofillDelayMillis(), () -> draft.delay, value -> draft.delay = value)
                        .controller(option -> IntegerSliderControllerBuilder.create(option)
                                .range(200, 5000).step(100)
                                .valueFormatter(value -> Component.translatable("blockvault.config.value.millis", value)))
                        .build())
                .option(Option.<GeneratorPreset>createBuilder()
                        .name(Component.translatable("blockvault.config.option.generator"))
                        .description(description("blockvault.settings.description.generator"))
                        .binding(GeneratorPreset.from(DEFAULTS.generator()), () -> draft.generator,
                                value -> draft.generator = value)
                        .controller(option -> EnumControllerBuilder.create(option)
                                .enumClass(GeneratorPreset.class)
                                .valueFormatter(value -> Component.translatable(
                                        "blockvault.settings.generator.preset." + value.id)))
                        .build())
                .build();
        OptionGroup prompts = OptionGroup.createBuilder()
                .name(Component.translatable("blockvault.config.group.cards"))
                .description(description("blockvault.config.group.cards.description"))
                .option(Option.<CardPosition>createBuilder()
                        .name(Component.translatable("blockvault.config.option.position"))
                        .description(description("blockvault.settings.description.position"))
                        .binding(CardPosition.from(DEFAULTS.cardPosition()), () -> draft.position,
                                value -> draft.position = value)
                        .controller(option -> EnumControllerBuilder.create(option)
                                .enumClass(CardPosition.class)
                                .valueFormatter(value -> Component.translatable("blockvault.config.position." + value.id)))
                        .build())
                .option(boolOption("blockvault.config.option.animations", "animations", DEFAULTS.cardAnimations(),
                        () -> draft.animations, value -> draft.animations = value))
                .build();
        return ConfigCategory.createBuilder()
                .name(Component.translatable("blockvault.config.category.login"))
                .tooltip(Component.translatable("blockvault.config.category.login.description"))
                .group(credentials)
                .group(prompts)
                .build();
    }

    private static ConfigCategory privacyCategory(Draft draft) {
        OptionGroup privacy = OptionGroup.createBuilder()
                .name(Component.translatable("blockvault.config.group.privacy"))
                .description(description("blockvault.config.group.privacy.description"))
                .option(boolOption("blockvault.config.option.streamer", "streamer", DEFAULTS.streamerMode(), () -> draft.streamer,
                        value -> draft.streamer = value))
                .option(boolOption("blockvault.config.option.scrub_history", "scrub_history", DEFAULTS.scrubHistory(), () -> draft.scrubHistory,
                        value -> draft.scrubHistory = value))
                .option(Option.<UnlockPolicy>createBuilder()
                        .name(Component.translatable("blockvault.config.option.unlock_policy"))
                        .description(description("blockvault.settings.description.unlock_policy"))
                        .binding(DEFAULTS.trustDevice() ? UnlockPolicy.TRUST : UnlockPolicy.ASK,
                                () -> draft.unlockPolicy,
                                value -> draft.unlockPolicy = value)
                        .controller(option -> EnumControllerBuilder.create(option)
                                .enumClass(UnlockPolicy.class)
                                .valueFormatter(value -> Component.translatable(value == UnlockPolicy.TRUST
                                        ? "blockvault.settings.unlock_policy.trust"
                                        : "blockvault.settings.unlock_policy.ask")))
                        .build())
                .build();
        return ConfigCategory.createBuilder()
                .name(Component.translatable("blockvault.config.category.privacy"))
                .tooltip(Component.translatable("blockvault.config.category.privacy.description"))
                .group(privacy)
                .build();
    }

    private static ConfigCategory dataCategory() {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("blockvault.config.category.data"))
                .tooltip(Component.translatable("blockvault.config.category.data.description"))
                .option(screenAction("blockvault.config.action.vault", "blockvault.config.action.vault.description",
                        screen -> VaultRuntime.openVaultScreen(screen)))
                .option(screenAction("blockvault.config.action.backups", "blockvault.config.action.backups.description",
                        VaultRuntime::openBackupScreen))
                .option(screenAction("blockvault.config.action.master", "blockvault.config.action.master.description",
                        VaultRuntime::openMasterPasswordScreen))
                .option(transferAction("blockvault.transfer.import.short", "blockvault.transfer.import.help", true))
                .option(transferAction("blockvault.transfer.export.short", "blockvault.transfer.export.help", false))
                .option(screenAction("blockvault.config.action.erase_data", "blockvault.settings.description.data.erase",
                        screen -> Minecraft.getInstance().setScreenAndShow(new VaultEraseScreen(screen))))
                .build();
    }

    private static Option<Boolean> boolOption(String key, String descriptionKey, boolean defaultValue,
                                               BooleanSupplier getter,
                                               Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable(key))
                .description(description("blockvault.settings.description." + descriptionKey))
                .binding(defaultValue, getter::getAsBoolean, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static ButtonOption screenAction(String name, String description, Consumer<Screen> action) {
        return ButtonOption.createBuilder()
                .name(Component.translatable(name))
                .description(description(description))
                .action((screen, option) -> action.accept(screen))
                .build();
    }

    private static ButtonOption transferAction(String name, String description, boolean importing) {
        return ButtonOption.createBuilder()
                .name(Component.translatable(name))
                .description(description(description))
                .action((screen, option) -> {
                    if (importing) VaultRuntime.openImportScreen(screen);
                    else Minecraft.getInstance().setScreenAndShow(new VaultTransferScreen(screen, false));
                })
                .build();
    }

    private static OptionDescription description(String key) {
        return OptionDescription.of(Component.translatable(key));
    }

    private static void save(Draft draft) {
        BlockVaultConfig.Settings next = draft.toSettings();
        BlockVaultConfig.update(current -> next, saved -> {
            if (!saved) {
                VaultRuntime.showToast("blockvault.vault.error");
                return;
            }
            Hooks.applySettings(next);
            VaultRuntime.setTrustedDevice(next.trustDevice());
        });
    }

    private static final class Draft {
        private boolean autofill;
        private boolean passwordless;
        private int delay;
        private CardPosition position;
        private boolean streamer;
        private GeneratorPreset generator;
        private boolean scrubHistory;
        private boolean animations;
        private UnlockPolicy unlockPolicy;

        private Draft(BlockVaultConfig.Settings settings) {
            autofill = settings.autofill();
            passwordless = settings.passwordlessLogin();
            delay = settings.autofillDelayMillis();
            position = CardPosition.from(settings.cardPosition());
            streamer = settings.streamerMode();
            generator = GeneratorPreset.from(settings.generator());
            scrubHistory = settings.scrubHistory();
            animations = settings.cardAnimations();
            unlockPolicy = settings.trustDevice() ? UnlockPolicy.TRUST : UnlockPolicy.ASK;
        }

        private BlockVaultConfig.Settings toSettings() {
            return new BlockVaultConfig.Settings(autofill, passwordless, delay, position.id, streamer,
                    generator.id, scrubHistory, unlockPolicy == UnlockPolicy.TRUST, animations);
        }
    }

    private enum CardPosition {
        TOP_LEFT("top_left"), TOP_RIGHT("top_right"), BOTTOM_LEFT("bottom_left"), BOTTOM_RIGHT("bottom_right");
        private final String id;
        CardPosition(String id) { this.id = id; }
        private static CardPosition from(String id) {
            for (CardPosition value : values()) if (value.id.equals(id)) return value;
            return TOP_RIGHT;
        }
    }

    private enum GeneratorPreset {
        BASIC("basic"), MEMORABLE("memorable");
        private final String id;
        GeneratorPreset(String id) { this.id = id; }
        private static GeneratorPreset from(String id) {
            for (GeneratorPreset value : values()) if (value.id.equals(id)) return value;
            return BASIC;
        }
    }

    private enum UnlockPolicy { TRUST, ASK }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (Minecraft.getInstance().gui.screen() == this) {
            graphics.centeredText(font, Component.translatable("blockvault.settings.loading"), width / 2,
                    height / 2 + 12, 0xffaaaaaa);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
