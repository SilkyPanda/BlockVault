package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.CardVariant;
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
            case UNLOCK -> "blockvault.card.unlock.title";
            case SAVE -> "blockvault.card.save.title";
        });
    }

    private Component promptTitle() {
        return model.variant() == CardVariant.UPDATE && registration
                ? Component.translatable("blockvault.card.register.title") : title(model.variant());
    }

    @Override
    protected void init() {
        BlockVaultConfig.Settings config = BlockVaultConfig.current();
        registration = model.variant() == CardVariant.REGISTER
                || model.variant() == CardVariant.UPDATE && session.state() == SessionState.PROMPTED;
        emailRequired = registration && model.emailRequired();
        int baseHeight = emailRequired ? EMAIL_HEIGHT : BASE_HEIGHT;
        remainingNanos = (long) BlockVaultConfig.current().cardDurationSeconds() * 1_000_000_000L;
        lastTimerNanos = System.nanoTime();
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
        passwordBox.setMaxLength(64);
        passwordBox.setHint(Component.translatable("blockvault.card.password"));
        passwordBox.setValue(model.variant() == CardVariant.REJECTED ? "" : new String(password));
        passwordBox.addFormatter((value, cursor) -> FormattedCharSequence.forward(
                reveal ? value : "\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(passwordBox);

        if (emailRequired) {
            emailBox = new EditBox(font, cardX + pad, cardY + scaled(80), cardWidth - pad * 2, scaled(20),
                    Component.translatable("blockvault.card.email"));
            emailBox.setHint(Component.translatable("blockvault.card.email"));
            emailBox.setMaxLength(254);
            addRenderableWidget(emailBox);
        }

        if (model.variant() == CardVariant.REJECTED) initRejectedButtons();
        else initSaveButtons(registration);
        scheduleTimer();
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
        int utilityWidth = (cardWidth - pad * 2 - gap * 2) / 3;
        addButton(cardX + pad, utilityY, utilityWidth, "blockvault.card.reveal",
                button -> { reveal = !reveal; });
        addButton(cardX + pad + utilityWidth + gap, utilityY, utilityWidth, "blockvault.card.copy",
                button -> copyFromField());
        if (registration) {
            addButton(cardX + pad + (utilityWidth + gap) * 2, utilityY, utilityWidth,
                    "blockvault.card.regenerate", button -> regenerate());
        } else {
            addButton(cardX + pad + (utilityWidth + gap) * 2, utilityY, utilityWidth,
                    "blockvault.card.never_server", button -> {
                        VaultRuntime.setNeverSave(model.host(), true);
                        onClose();
                    });
        }
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
            if (passwordBox.getValue().isBlank()) return;
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
            VaultRuntime.activity();
            action.onPress(pressed);
        }).bounds(x, y, buttonWidth, scaled(20)).build());
    }

    private void regenerate() {
        char[] next = PasswordGenerator.generate(policy(BlockVaultConfig.current().generator()));
        wipePassword();
        password = next;
        passwordBox.setValue(new String(password));
    }

    private void copyFromField() {
        char[] copied = passwordBox.getValue().toCharArray();
        String value = new String(copied);
        Arrays.fill(copied, '\0');
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(value);
        VaultRuntime.showToast("blockvault.toast.copied");
        BackgroundExecutor.scheduler().schedule(30_000, () -> BackgroundExecutor.onGameThread(() -> {
            if (value.equals(minecraft.keyboardHandler.getClipboard())) minecraft.keyboardHandler.setClipboard("");
        }));
    }

    private static PasswordGenerator.Policy policy(String preset) {
        return switch (preset) {
            case "pickyServer" -> PasswordGenerator.Policy.pickyServer();
            case "max" -> PasswordGenerator.Policy.max();
            case "memorable" -> PasswordGenerator.Policy.memorable();
            default -> PasswordGenerator.Policy.defaults();
        };
    }

    private void accept(boolean registration) {
        wipePassword();
        password = passwordBox.getValue().toCharArray();
        if (registration) session.confirmRegistration(password, emailBox == null ? null : emailBox.getValue());
        else session.confirmCapturedSave(password);
        onClose();
    }

    private void scheduleTimer() {
        timer = BackgroundExecutor.scheduler().schedule(250, () -> {
            BackgroundExecutor.onGameThread(() -> {
                if (Minecraft.getInstance().gui.screen() != this) return;
                long now = System.nanoTime();
                long elapsed = now - lastTimerNanos;
                lastTimerNanos = now;
                boolean hovering = mouseX >= cardX && mouseX < cardX + cardWidth
                        && mouseY >= cardY && mouseY < cardY + cardHeight;
                boolean focused = passwordBox != null && passwordBox.isFocused()
                        || emailBox != null && emailBox.isFocused();
                if (!hovering && !focused) remainingNanos -= elapsed;
                if (remainingNanos <= 0) onClose();
                else scheduleTimer();
            });
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
            onClose();
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
        closing = true;
        if (timer != null) timer.cancel();
        timer = null;
        if (model.variant() == CardVariant.REJECTED) session.dismissRejectedPrompt();
        else if (session.state() == SessionState.PROMPTED) session.dismissPrompt();
        wipePassword();
        if (passwordBox != null) passwordBox.setValue("");
        if (emailBox != null) emailBox.setValue("");
        model.clearPassword();
        super.onClose();
        closed.run();
    }

    private void wipePassword() { if (password != null) Arrays.fill(password, '\0'); }
}
