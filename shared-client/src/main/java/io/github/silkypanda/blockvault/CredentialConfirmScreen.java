package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.CardVariant;
import io.github.silkypanda.blockvault.core.session.CredentialPassword;
import io.github.silkypanda.blockvault.core.validation.AsciiInput;
import io.github.silkypanda.blockvault.core.session.LoginSession;
import io.github.silkypanda.blockvault.core.session.PromptModel;
import io.github.silkypanda.blockvault.core.session.Scheduler;
import io.github.silkypanda.blockvault.core.session.SessionState;
import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Vanilla credential card shared by save, update, registration, and rejection prompts. */
final class CredentialConfirmScreen extends Screen {
    private static final int BASE_WIDTH = 400;
    private static final int BASE_HEIGHT = 150;
    private static final int EMAIL_HEIGHT = 174;
    private final PromptModel model;
    private final LoginSession session;
    private final Runnable closed;
    private char[] password;
    private EditBox passwordBox;
    private EditBox emailBox;
    private boolean closing;
    private boolean registration;
    private boolean emailRequired;
    private boolean reveal;
    private int cardWidth;
    private int cardHeight;
    private float scale;
    private int cardX;
    private int cardY;
    private int mouseX;
    private int mouseY;
    private long remainingNanos;
    private long lastTimerNanos;
    private Scheduler.Handle timer;
    private String resizedPassword;
    private String resizedEmail;
    private boolean initialized;
    private long focusAfterNanos;

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
        if (timer != null) timer.cancel();
        timer = null;
        BlockVaultConfig.Settings config = BlockVaultConfig.current();
        registration = model.variant() == CardVariant.REGISTER
                || model.variant() == CardVariant.UPDATE && session.state() == SessionState.PROMPTED;
        emailRequired = registration && model.emailRequired();
        int baseHeight = emailRequired ? EMAIL_HEIGHT : BASE_HEIGHT;
        if (!initialized) remainingNanos = (long) config.cardDurationSeconds() * 1_000_000_000L;
        initialized = true;
        lastTimerNanos = System.nanoTime();
        focusAfterNanos = lastTimerNanos + 400_000_000L;
        scale = Math.min(1.0f, (float) (0.98 * Math.sqrt((width * (double) height * 0.30)
                / (BASE_WIDTH * (double) baseHeight))));
        scale = Math.min(scale, Math.min((width - 16.0f) / BASE_WIDTH, (height - 16.0f) / baseHeight));
        if (scale <= 0) scale = 0.01f;
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

        int pad = scaled(12);
        int fieldY = cardY + scaled(52);
        passwordBox = new EditBox(font, cardX + pad, fieldY, cardWidth - pad * 2, scaled(20),
                Component.translatable("blockvault.card.password"));
        passwordBox.setMaxLength(256);
        AsciiInputFilter.bind(passwordBox, AsciiInput::isPasswordText, null);
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
            AsciiInputFilter.bind(emailBox, AsciiInput::isEmailText, null);
            if (resizedEmail != null) emailBox.setValue(resizedEmail);
            resizedEmail = null;
            addRenderableWidget(emailBox);
        }

        if (model.variant() == CardVariant.REJECTED) initRejectedButtons();
        else initSaveButtons(registration);
        if (!registration && model.variant() != CardVariant.REJECTED) scheduleTimer();
    }

    private void initSaveButtons(boolean registration) {
        String actionKey = registration ? "blockvault.card.register"
                : model.variant() == CardVariant.UPDATE ? "blockvault.card.update" : "blockvault.card.save";
        int pad = scaled(12);
        int gap = scaled(6);
        int buttonY = cardY + scaled(emailRequired ? 108 : 84);
        int buttonWidth = (cardWidth - pad * 2 - gap) / 2;
        addButton(cardX + pad, buttonY, buttonWidth, actionKey, button -> accept(registration));
        addButton(cardX + pad + buttonWidth + gap, buttonY, buttonWidth, "blockvault.card.cancel", button -> onClose());
        int utilityY = buttonY + scaled(24);
        int utilityCount = registration ? 3 : 2;
        int utilityWidth = (cardWidth - pad * 2 - gap * (utilityCount - 1)) / utilityCount;
        addButton(cardX + pad, utilityY, utilityWidth, "blockvault.card.reveal",
                button -> { reveal = !reveal; });
        addButton(cardX + pad + utilityWidth + gap, utilityY, utilityWidth, "blockvault.card.copy",
                button -> copyFromField());
        if (registration) {
            addButton(cardX + pad + (utilityWidth + gap) * 2, utilityY, utilityWidth,
                    "blockvault.card.regenerate", button -> regenerate());
        }
    }

    private void regenerate() {
        char[] next = PasswordGenerator.generate(model.policy() != null
                ? model.policy() : BlockVaultConfig.generatorPolicy(BlockVaultConfig.current().generator()));
        wipePassword();
        password = next;
        passwordBox.setValue(new String(password));
    }


    private void initRejectedButtons() {
        int pad = scaled(12);
        int gap = scaled(4);
        int buttonY = cardY + scaled(84);
        int buttonWidth = (cardWidth - pad * 2 - gap * 2) / 3;
        addButton(cardX + pad, buttonY, buttonWidth, "blockvault.card.manual", button -> {
            session.dismissRejectedPrompt();
            onClose();
        });
        addButton(cardX + pad + buttonWidth + gap, buttonY, buttonWidth, "blockvault.card.update", button -> {
            if (!CredentialPassword.isValid(passwordBox.getValue().toCharArray())) {
                VaultRuntime.showToast("blockvault.password.invalid");
                return;
            }
            wipePassword();
            password = passwordBox.getValue().toCharArray();
            session.updateRejectedPassword(password);
            onClose();
        });
        addButton(cardX + pad + (buttonWidth + gap) * 2, buttonY, buttonWidth, "blockvault.card.forget", button -> {
            session.forgetRejectedCredential();
            onClose();
        });
        int utilityWidth = (cardWidth - pad * 2 - gap) / 2;
        addButton(cardX + pad, buttonY + scaled(24), utilityWidth, "blockvault.card.reveal",
                button -> { reveal = !reveal; });
        addButton(cardX + pad + utilityWidth + gap, buttonY + scaled(24), utilityWidth,
                "blockvault.card.cancel", button -> onClose());
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
                && !AsciiInput.isEmail(emailBox.getValue())) {
            VaultRuntime.showToast("blockvault.toast.email_invalid");
            return;
        }
        wipePassword();
        password = passwordBox.getValue().toCharArray();
        if (registration) session.confirmRegistration(password, emailBox == null ? null : emailBox.getValue());
        else session.confirmCapturedSave(password);
        onClose();
    }

    private void scheduleTimer() {
        timer = BackgroundExecutor.scheduler().schedule(250, () -> {
            if (Minecraft.getInstance().gui.screen() != this) return;
            long now = System.nanoTime();
            long elapsed = now - lastTimerNanos;
            lastTimerNanos = now;
            boolean hovering = mouseX >= cardX && mouseX < cardX + cardWidth
                    && mouseY >= cardY && mouseY < cardY + cardHeight;
            if (now >= focusAfterNanos && passwordBox != null && !passwordBox.isFocused()) passwordBox.setFocused(true);
            boolean focused = now < focusAfterNanos || passwordBox != null && passwordBox.isFocused()
                    || emailBox != null && emailBox.isFocused();
            if (!hovering && !focused) remainingNanos -= elapsed;
            if (remainingNanos <= 0) onClose();
            else scheduleTimer();
        });
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        graphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xf20d0c12);
        graphics.fill(cardX, cardY, cardX + cardWidth, cardY + scaled(2), 0xff9b72ff);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() < cardX || event.x() >= cardX + cardWidth
                || event.y() < cardY || event.y() >= cardY + cardHeight) {
            // Keep the only copy of manually entered credentials on screen until the owner chooses an action.
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int pad = scaled(12);
        graphics.text(font, promptTitle(), cardX + pad, cardY + scaled(8), 0xffffffff);
        String identity = BlockVaultConfig.current().streamerMode()
                ? model.username() : model.username() + " · " + model.host();
        int maxChars = Math.max(8, (cardWidth - pad * 2) / 6);
        if (identity.length() > maxChars) identity = identity.substring(0, maxChars - 1) + "…";
        graphics.text(font, Component.literal(identity), cardX + pad, cardY + scaled(26), 0xffb2adbe);
    }

    private int scaled(int value) { return Math.max(1, Math.round(value * scale)); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (closing) return;
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
        if (timer != null) timer.cancel();
        timer = null;
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
