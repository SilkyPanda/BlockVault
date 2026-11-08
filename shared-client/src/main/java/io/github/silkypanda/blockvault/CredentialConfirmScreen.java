package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.CardVariant;
import io.github.silkypanda.blockvault.core.session.CardResult;
import io.github.silkypanda.blockvault.core.session.CredentialPassword;
import io.github.silkypanda.blockvault.core.validation.InputRules;
import io.github.silkypanda.blockvault.core.session.LoginSession;
import io.github.silkypanda.blockvault.core.session.PromptModel;
import io.github.silkypanda.blockvault.core.session.SessionState;
import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.multiplayer.ServerData;

/** Vanilla credential card shared by save, update, registration, and rejection prompts. */
final class CredentialConfirmScreen extends Screen {
    private static final int BASE_WIDTH = 400;
    private static final int BASE_HEIGHT = 150;
    private static final int EMAIL_HEIGHT = 174;
    private static final Identifier PANEL_SPRITE = Identifier.fromNamespaceAndPath(
            "blockvault", "credential_card");
    private static final Identifier SERVER_ICON_TEXTURE = Identifier.fromNamespaceAndPath(
            "blockvault", "active_server_icon");
    private static byte[] cachedServerIcon;
    private final PromptModel model;
    private final LoginSession session;
    private final Runnable closed;
    private char[] password;
    private EditBox passwordBox;
    private EditBox emailBox;
    private boolean closing;
    private boolean closeAnimating;
    private long closeStartedAtNanos;
    private boolean registration;
    private boolean emailRequired;
    private boolean reveal;
    private boolean resultMode;
    private CardResult cardResult;
    private long resultStartedAtNanos;
    private final long openedAtNanos = System.nanoTime();
    private int cardWidth;
    private int cardHeight;
    private float scale;
    private int cardX;
    private int cardY;
    private String resizedPassword;
    private String resizedEmail;

    CredentialConfirmScreen(PromptModel model, LoginSession session, Runnable closed) {
        super(title(model.variant()));
        this.model = model;
        this.session = session;
        this.closed = closed;
        this.password = model.password();
    }

    private static Component title(CardVariant variant) {
        return Component.translatable(switch (variant) {
            case REGISTER -> "blockvault.card.register.title";
            case UPDATE -> "blockvault.card.update.title";
            case REJECTED -> "blockvault.card.rejected.title";
            case SAVE -> "blockvault.card.save.title";
            case PENDING_LOGIN -> "blockvault.card.pending_login.title";
        });
    }

    private Component promptTitle() {
        return model.variant() == CardVariant.UPDATE && registration
                ? Component.translatable("blockvault.card.register.title") : title(model.variant());
    }

    @Override
    protected void init() {
        if (passwordBox != null) resizedPassword = passwordBox.getValue();
        if (emailBox != null) resizedEmail = emailBox.getValue();
        BlockVaultConfig.Settings config = BlockVaultConfig.current();
        registration = model.variant() == CardVariant.REGISTER
                || model.variant() == CardVariant.UPDATE && session.state() == SessionState.PROMPTED;
        emailRequired = registration && model.emailRequired();
        int baseHeight = emailRequired ? EMAIL_HEIGHT : BASE_HEIGHT;
        scale = Math.min(1.0f, (float) (0.98 * Math.sqrt((width * (double) height * 0.30)
                / (BASE_WIDTH * (double) baseHeight))));
        scale = Math.min(scale, Math.min((width - 16.0f) / BASE_WIDTH, (height - 16.0f) / baseHeight));
        if (scale <= 0) scale = 0.01f;
        if (scale < 0.9f) {
            baseHeight = emailRequired ? 252 : 218;
            scale = Math.min(1.0f, (float) Math.min((width - 16.0f) / BASE_WIDTH,
                    (height - 16.0f) / baseHeight));
            if (scale <= 0) scale = 0.01f;
        }
        cardWidth = Math.round(BASE_WIDTH * scale);
        cardHeight = Math.round(baseHeight * scale);
        cardX = switch (config.cardPosition()) {
            case "top_left", "bottom_left" -> 18;
            default -> width - cardWidth - 18;
        };
        cardY = switch (config.cardPosition()) {
            case "bottom_left", "bottom_right" -> height - cardHeight - 18;
            default -> 18;
        };
        cardX = Math.max(4, Math.min(cardX, width - cardWidth - 4));
        cardY = Math.max(4, Math.min(cardY, height - cardHeight - 4));

        if (resultMode) {
            int resultPad = scaled(12);
            addButton(cardX + resultPad, cardY + cardHeight - scaled(34), cardWidth - resultPad * 2,
                    "blockvault.card.close", button -> onClose());
            return;
        }

        int pad = scaled(12);
        int fieldY = cardY + scaled(52);
        passwordBox = new EditBox(font, cardX + pad, fieldY, cardWidth - pad * 2, scaled(20),
                Component.translatable("blockvault.card.password"));
        passwordBox.setMaxLength(InputRules.SERVER_PASSWORD_MAX * 2);
        InputFilter.bind(passwordBox, InputRules::isServerPasswordInput, null);
        passwordBox.setHint(Component.translatable("blockvault.card.password"));
        passwordBox.setValue(resizedPassword != null ? resizedPassword
                : model.variant() == CardVariant.REJECTED ? "" : new String(password));
        resizedPassword = null;
        passwordBox.addFormatter((value, cursor) -> FormattedCharSequence.forward(
                reveal ? value : "\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(passwordBox);

        if (emailRequired) {
            emailBox = new EditBox(font, cardX + pad, cardY + scaled(80), cardWidth - pad * 2, scaled(20),
                    Component.translatable("blockvault.card.email"));
            emailBox.setHint(Component.translatable("blockvault.card.email"));
            emailBox.setMaxLength(254);
            InputFilter.bind(emailBox, InputRules::isEmailText, null);
            if (resizedEmail != null) emailBox.setValue(resizedEmail);
            resizedEmail = null;
            addRenderableWidget(emailBox);
        }

        if (model.variant() == CardVariant.REJECTED) initRejectedButtons();
        else if (model.variant() == CardVariant.PENDING_LOGIN) initPendingLoginButtons();
        else initSaveButtons(registration);
        setInitialFocus(passwordBox);
    }

    @Override
    public void tick() {
        super.tick();
        if (closeAnimating && System.nanoTime() - closeStartedAtNanos >= 120_000_000L) {
            closeAnimating = false;
            finishClose();
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    private void initSaveButtons(boolean registration) {
        String actionKey = registration ? "blockvault.card.register"
                : model.variant() == CardVariant.UPDATE ? "blockvault.card.update" : "blockvault.card.save";
        int pad = scaled(12);
        int gap = scaled(6);
        boolean stacked = scale < 0.9f;
        int buttonY = cardY + scaled(emailRequired ? 108 : 84);
        int buttonWidth = (cardWidth - pad * 2 - gap) / 2;
        addButton(cardX + pad, buttonY, buttonWidth, actionKey, button -> accept(registration));
        addButton(cardX + pad + buttonWidth + gap, buttonY, buttonWidth, "blockvault.card.cancel", button -> onClose());
        int utilityY = buttonY + scaled(stacked ? 48 : 24);
        int utilityCount = registration ? 3 : 2;
        int utilityWidth = stacked ? (cardWidth - pad * 2 - gap) / 2
                : (cardWidth - pad * 2 - gap * (utilityCount - 1)) / utilityCount;
        addButton(cardX + pad, utilityY, utilityWidth, "blockvault.card.reveal",
                button -> { reveal = !reveal; });
        addButton(cardX + pad + utilityWidth + gap, utilityY, utilityWidth, "blockvault.card.copy",
                button -> copyFromField());
        if (registration) {
            int regenerateY = stacked ? utilityY + scaled(24) : utilityY;
            int regenerateX = stacked ? cardX + pad : cardX + pad + (utilityWidth + gap) * 2;
            int regenerateWidth = stacked ? cardWidth - pad * 2 : utilityWidth;
            addButton(regenerateX, regenerateY, regenerateWidth,
                    "blockvault.card.regenerate", button -> regenerate());
        }
    }

    private void regenerate() {
        char[] next = PasswordGenerator.generate(model.policy() != null
                ? model.policy() : io.github.silkypanda.blockvault.core.session.SessionPreferences.generatorPolicy(BlockVaultConfig.current().generator()));
        wipePassword();
        password = next;
        passwordBox.setValue(new String(password));
    }

    void setCardResult(CardResult result) {
        if (closing || closeAnimating) return;
        // The successful register response is enough feedback; close the credential form
        // with its normal exit motion instead of showing a second confirmation message.
        if (registration && result == CardResult.SUCCESS) {
            onClose();
            return;
        }
        cardResult = result;
        resultMode = true;
        resultStartedAtNanos = System.nanoTime();
        wipePassword();
        if (passwordBox != null) passwordBox.setValue("");
        if (emailBox != null) emailBox.setValue("");
        model.clearPassword();
        clearWidgets();
        int pad = scaled(12);
        addButton(cardX + pad, cardY + cardHeight - scaled(34), cardWidth - pad * 2,
                "blockvault.card.close", button -> onClose());
    }


    private void initRejectedButtons() {
        int pad = scaled(12);
        int gap = scaled(4);
        int buttonY = cardY + scaled(84);
        boolean stacked = scale < 0.9f;
        int buttonWidth = stacked ? cardWidth - pad * 2 : (cardWidth - pad * 2 - gap * 2) / 3;
        int secondY = stacked ? buttonY + scaled(24) : buttonY;
        int thirdY = stacked ? buttonY + scaled(48) : buttonY;
        addButton(cardX + pad, buttonY, buttonWidth, "blockvault.card.manual", button -> {
            session.dismissRejectedPrompt();
            onClose();
        });
        addButton(stacked ? cardX + pad : cardX + pad + buttonWidth + gap, secondY, buttonWidth, "blockvault.card.update", button -> {
            if (!CredentialPassword.isValid(passwordBox.getValue().toCharArray())) {
                VaultRuntime.showToast("blockvault.password.invalid");
                return;
            }
            wipePassword();
            password = passwordBox.getValue().toCharArray();
            session.updateRejectedPassword(password);
            setCardResult(CardResult.SAVING);
        });
        addButton(stacked ? cardX + pad : cardX + pad + (buttonWidth + gap) * 2, thirdY, buttonWidth, "blockvault.card.forget", button -> {
            setCardResult(CardResult.SAVING);
            session.forgetRejectedCredential(deleted -> setCardResult(
                    deleted ? CardResult.SUCCESS : CardResult.FAILURE));
        });
        int utilityWidth = stacked ? cardWidth - pad * 2 : (cardWidth - pad * 2 - gap) / 2;
        int utilityY = stacked ? buttonY + scaled(72) : buttonY + scaled(24);
        addButton(cardX + pad, utilityY, utilityWidth, "blockvault.card.reveal",
                button -> { reveal = !reveal; });
        addButton(stacked ? cardX + pad : cardX + pad + utilityWidth + gap,
                stacked ? utilityY + scaled(24) : utilityY, utilityWidth,
                "blockvault.card.cancel", button -> onClose());
    }

    private void initPendingLoginButtons() {
        int pad = scaled(12);
        int gap = scaled(6);
        int y = cardY + scaled(scale < 0.9f ? 108 : 84);
        int buttonWidth = (cardWidth - pad * 2 - gap) / 2;
        addButton(cardX + pad, y, buttonWidth, "blockvault.card.pending_login", button -> tryPendingLogin());
        addButton(cardX + pad + buttonWidth + gap, y, buttonWidth, "blockvault.card.cancel", button -> onClose());
        addButton(cardX + pad, y + scaled(24), buttonWidth, "blockvault.card.reveal", button -> reveal = !reveal);
        addButton(cardX + pad + buttonWidth + gap, y + scaled(24), buttonWidth, "blockvault.card.copy", button -> copyFromField());
    }

    private void tryPendingLogin() {
            if (!CredentialPassword.isValid(passwordBox.getValue().toCharArray())) {
                VaultRuntime.showToast("blockvault.password.invalid");
                return;
            }
            wipePassword();
            password = passwordBox.getValue().toCharArray();
            session.confirmPendingLogin(password);
            setCardResult(CardResult.SAVING);
    }

    private void addButton(int x, int y, int buttonWidth, String key, Button.OnPress action) {
        addRenderableWidget(Button.builder(Component.translatable(key), pressed -> {
            action.onPress(pressed);
        }).bounds(x, y, buttonWidth, scaled(20)).build());
    }

    private void copyFromField() {
        char[] copied = passwordBox.getValue().toCharArray();
        String value = new String(copied);
        Arrays.fill(copied, '\0');
        VaultRuntime.copyToClipboard(value);
    }

    private void accept(boolean registration) {
        if (!CredentialPassword.isValid(passwordBox.getValue().toCharArray())) {
            VaultRuntime.showToast("blockvault.password.invalid");
            return;
        }
        if (registration && emailBox != null
                && !InputRules.isEmail(emailBox.getValue())) {
            VaultRuntime.showToast("blockvault.toast.email_invalid");
            return;
        }
        wipePassword();
        password = passwordBox.getValue().toCharArray();
        if (registration) session.confirmRegistration(password, emailBox == null ? null : emailBox.getValue());
        else session.confirmCapturedSave(password);
        setCardResult(CardResult.SAVING);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        // Result cards require the explicit Close button. In particular, do not let Enter
        // fall through to a stale text-field/button focus and submit the old credential.
        if (closing || resultMode) return true;
        if (inputGraceActive() || closeAnimating || !openingFinished()) return true;
        if (ScreenText.isEnter(event)) {
            if (model.variant() == CardVariant.PENDING_LOGIN) { tryPendingLogin(); return true; }
            if (model.variant() == CardVariant.REJECTED) {
                if (!CredentialPassword.isValid(passwordBox.getValue().toCharArray())) {
                    VaultRuntime.showToast("blockvault.toast.password_invalid");
                    return true;
                }
                wipePassword();
                password = passwordBox.getValue().toCharArray();
                session.updateRejectedPassword(password);
                setCardResult(CardResult.SAVING);
            } else accept(registration);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float progress = closeAnimating ? accentProgress() : animationProgress();
        int panelY = cardY;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL_SPRITE, cardX, panelY, cardWidth, cardHeight, progress);
        int lineInset = Math.min(scaled(10), Math.max(1, (cardWidth - scaled(2)) / 2));
        int lineWidth = Math.max(1, cardWidth - lineInset * 2);
        int accentWidth = Math.max(1, Math.round(lineWidth * progress));
        graphics.fill(cardX + lineInset, panelY + scaled(2),
                cardX + lineInset + accentWidth, panelY + scaled(4),
                CredentialCardTheme.accent(model.variant()));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (inputGraceActive() || closeAnimating || !openingFinished()) return true;
        if (event.x() < cardX || event.x() >= cardX + cardWidth
                || event.y() < cardY || event.y() >= cardY + cardHeight) {
            // Keep the only copy of manually entered credentials on screen until the owner chooses an action.
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean inputGraceActive() { return System.nanoTime() - openedAtNanos < 250_000_000L; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int pad = scaled(12);
        Component status = Component.translatable(cardResult == null
                ? CredentialCardTheme.statusKey(model.variant()) : cardResultTranslation(cardResult, true));
        if (cardResult == CardResult.SAVING || cardResult == CardResult.SENDING) {
            status = Component.literal(status.getString()
                    + ".".repeat((int) (System.nanoTime() / 300_000_000L % 4)));
        }
        int chipWidth = font.width(status) + scaled(12);
        int chipX = cardX + cardWidth - scaled(12) - chipWidth;
        Component heading = Component.literal(ScreenText.clip(font, promptTitle().getString(),
                Math.max(1, chipX - cardX - pad - scaled(8))));
        graphics.text(font, heading, cardX + pad, cardY + scaled(8), 0xffffffff);
        int accent = cardResult == null ? CredentialCardTheme.accent(model.variant()) : resultAccent(cardResult);
        graphics.fill(chipX, cardY + scaled(7), chipX + chipWidth, cardY + scaled(21), accent);
        graphics.centeredText(font, status, chipX + chipWidth / 2, cardY + scaled(10), 0xff17131d);
        String identity = BlockVaultConfig.current().streamerMode()
                ? model.username() : model.username() + " · " + model.host();
        int identityX = cardX + pad;
        if (!BlockVaultConfig.current().streamerMode()) {
            int logoSize = scaled(20);
            drawServerLogo(graphics, identityX, cardY + scaled(25), logoSize);
            identityX += logoSize + scaled(5);
        }
        identity = ScreenText.clip(font, identity, cardX + cardWidth - pad - identityX);
        graphics.text(font, Component.literal(identity), identityX, cardY + scaled(26), CredentialCardTheme.MUTED);
        if (resultMode && cardResult != null) {
            int color = resultAccent(cardResult);
            int shake = cardResult == CardResult.FAILURE
                    ? (int) Math.round((float) Math.sin((System.nanoTime() - resultStartedAtNanos) / 16_000_000.0)
                    * 3 * Math.exp(-(System.nanoTime() - resultStartedAtNanos) / 250_000_000.0)) : 0;
            ScreenText.drawCenteredWrapped(graphics, font,
                    Component.translatable(cardResultTranslation(cardResult, false)), cardX + cardWidth / 2 + shake,
                    cardY + scaled(58), cardWidth - pad * 2, color, scaled(12));
        }
    }

    private int scaled(int value) { return Math.max(1, Math.round(value * scale)); }

    private boolean animationsEnabled() { return BlockVaultConfig.current().cardAnimations(); }
    private boolean openingFinished() { return !animationsEnabled() || System.nanoTime() - openedAtNanos >= 200_000_000L; }
    private float animationProgress() {
        if (!animationsEnabled()) return 1;
        float elapsed = Math.min(1, (System.nanoTime() - openedAtNanos) / 200_000_000f);
        float remaining = 1 - elapsed;
        return 1 - remaining * remaining * remaining;
    }
    private float accentProgress() {
        if (!animationsEnabled()) return 1;
        if (closeAnimating) return Math.max(0, 1 - (System.nanoTime() - closeStartedAtNanos) / 120_000_000f);
        return animationProgress();
    }

    private void drawServerLogo(GuiGraphicsExtractor graphics, int x, int y, int size) {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        byte[] icon = server == null ? null : server.getIconBytes();
        if (icon != null && icon.length > 0) {
            try {
                if (!Arrays.equals(cachedServerIcon, icon)) {
                    NativeImage image = NativeImage.read(new ByteArrayInputStream(icon));
                    Minecraft.getInstance().getTextureManager().register(SERVER_ICON_TEXTURE,
                            new DynamicTexture(() -> "BlockVault server icon", image));
                    cachedServerIcon = icon.clone();
                }
                graphics.blit(RenderPipelines.GUI_TEXTURED, SERVER_ICON_TEXTURE, x, y, 0, 0,
                        size, size, 64, 64, 64, 64);
                return;
            } catch (IOException | RuntimeException ignored) {
                cachedServerIcon = null;
            }
        }
        graphics.fill(x, y, x + size, y + size, CredentialCardTheme.accent(model.variant()));
        String mark = model.host().isBlank() ? "?" : model.host().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        graphics.centeredText(font, mark, x + size / 2, y + (size - font.lineHeight) / 2, 0xffffffff);
    }

    private static String cardResultTranslation(CardResult result, boolean chip) {
        String state = switch (result) {
            case SAVING -> "saving";
            case SENDING -> "sending";
            case SUCCESS -> "success";
            case FAILURE -> "failure";
            case TIMEOUT -> "timeout";
            case ALREADY_LOGGED_IN -> "already_logged_in";
        };
        return "blockvault.card.result." + state + (chip ? ".chip" : ".message");
    }

    private static int resultAccent(CardResult result) {
        return switch (result) {
            case SAVING, SENDING -> CredentialCardTheme.PENDING;
            case SUCCESS, ALREADY_LOGGED_IN -> CredentialCardTheme.REGISTER;
            case FAILURE -> CredentialCardTheme.REJECTED;
            case TIMEOUT -> CredentialCardTheme.UPDATE;
        };
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (closing) return;
        if (animationsEnabled() && openingFinished()) {
            closeAnimating = true;
            closeStartedAtNanos = System.nanoTime();
            wipePassword();
            if (passwordBox != null) passwordBox.setValue("");
            if (emailBox != null) emailBox.setValue("");
            model.clearPassword();
            return;
        }
        finishClose();
        super.onClose();
    }

    /** Wipes this card when the network screen replaces it during a disconnect. */
    void disconnect() { finishClose(); }

    @Override
    public void removed() {
        finishClose();
        super.removed();
    }

    private void finishClose() {
        if (closing) return;
        closing = true;
        if (model.variant() == CardVariant.REJECTED) session.dismissRejectedPrompt();
        else if (session.state() == SessionState.PROMPTED) session.dismissPrompt();
        wipePassword();
        if (passwordBox != null) passwordBox.setValue("");
        if (emailBox != null) emailBox.setValue("");
        model.clearPassword();
        closed.run();
    }

    private void wipePassword() { if (password != null) Arrays.fill(password, '\0'); }
}
