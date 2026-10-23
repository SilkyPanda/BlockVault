package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.session.CardVariant;
import io.github.silkypanda.blockvault.core.session.LoginSession;
import io.github.silkypanda.blockvault.core.session.PromptModel;
import io.github.silkypanda.blockvault.core.session.Scheduler;
import io.github.silkypanda.blockvault.core.session.SessionState;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** Vanilla credential card shared by save, update, registration, and rejection prompts. */
final class CredentialConfirmScreen extends Screen {
    private static final int CARD_WIDTH = 360;
    private static final int CARD_HEIGHT = 226;
    private static final Identifier CARD_TEXTURE = Identifier.fromNamespaceAndPath("blockvault", "textures/gui/sprites/card.png");
    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath("blockvault", "textures/gui/sprites/icon.png");
    private final PromptModel model;
    private final LoginSession session;
    private final Runnable closed;
    private char[] password;
    private EditBox passwordBox;
    private EditBox emailBox;
    private boolean reveal;
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
        this.remainingNanos = (long) BlockVaultConfig.current().cardDurationSeconds() * 1_000_000_000L;
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

    @Override
    protected void init() {
        BlockVaultConfig.Settings config = BlockVaultConfig.current();
        cardX = switch (config.cardPosition()) {
            case "top_left", "bottom_left" -> 18;
            default -> width - CARD_WIDTH - 18;
        };
        cardY = switch (config.cardPosition()) {
            case "bottom_left", "bottom_right" -> height - CARD_HEIGHT - 34;
            default -> 18;
        };
        cardX = Math.max(4, Math.min(cardX, width - CARD_WIDTH - 4));
        cardY = Math.max(4, Math.min(cardY, height - CARD_HEIGHT - 4));

        passwordBox = new EditBox(font, cardX + 18, cardY + 90, CARD_WIDTH - 36, 20,
                Component.translatable("blockvault.card.password"));
        passwordBox.setMaxLength(64);
        passwordBox.setValue(new String(password));
        passwordBox.addFormatter((value, cursor) -> reveal
                ? FormattedCharSequence.forward(value, Style.EMPTY)
                : FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(passwordBox);

        boolean registration = model.variant() == CardVariant.REGISTER
                || model.variant() == CardVariant.UPDATE && session.state() == SessionState.PROMPTED;
        if (registration && model.emailRequired()) {
            emailBox = new EditBox(font, cardX + 18, cardY + 116, CARD_WIDTH - 36, 20,
                    Component.translatable("blockvault.card.email"));
            emailBox.setHint(Component.translatable("blockvault.card.email"));
            emailBox.setMaxLength(254);
            addRenderableWidget(emailBox);
        }

        if (model.variant() == CardVariant.REJECTED) initRejectedButtons();
        else initSaveButtons(registration);
        lastTimerNanos = System.nanoTime();
        scheduleTimer();
    }

    private void initSaveButtons(boolean registration) {
        int actionY = cardY + 148;
        String actionKey = registration ? "blockvault.card.register"
                : model.variant() == CardVariant.UPDATE ? "blockvault.card.update" : "blockvault.card.save";
        addButton(cardX + 18, actionY, 156, "blockvault.card.accept", actionKey, button -> accept(registration));
        addButton(cardX + 186, actionY, 156, "blockvault.card.cancel", null, button -> onClose());
        addButton(cardX + 18, actionY + 26, 100, "blockvault.card.reveal", null,
                button -> { reveal = !reveal; });
        addButton(cardX + 130, actionY + 26, 100, "blockvault.card.copy", null,
                button -> copyFromField());
        if (registration) {
            addButton(cardX + 242, actionY + 26, 100, "blockvault.card.regenerate", null,
                    button -> regenerate());
        } else {
            addButton(cardX + 242, actionY + 26, 100, "blockvault.card.never_server", null,
                    button -> {
                        VaultRuntime.setNeverSave(model.host(), true);
                        onClose();
                    });
        }
    }

    private void initRejectedButtons() {
        int y = cardY + 148;
        addButton(cardX + 18, y, 100, "blockvault.card.manual", null, button -> {
            session.dismissRejectedPrompt();
            onClose();
        });
        addButton(cardX + 130, y, 100, "blockvault.card.update", null, button -> {
            password = passwordBox.getValue().toCharArray();
            session.updateRejectedPassword(password);
            onClose();
        });
        addButton(cardX + 242, y, 100, "blockvault.card.forget", null, button -> {
            session.forgetRejectedCredential();
            onClose();
        });
        addButton(cardX + 18, y + 26, 156, "blockvault.card.reveal", null,
                button -> { reveal = !reveal; });
        addButton(cardX + 186, y + 26, 156, "blockvault.card.cancel", null, button -> onClose());
    }

    private void addButton(int x, int y, int buttonWidth, String key, String actionKey, Button.OnPress action) {
        Component label = actionKey == null ? Component.translatable(key)
                : Component.translatable(key, Component.translatable(actionKey));
        addRenderableWidget(Button.builder(label, pressed -> {
            VaultRuntime.activity();
            action.onPress(pressed);
        }).bounds(x, y, buttonWidth, 20).build());
    }

    private void accept(boolean registration) {
        wipePassword();
        password = passwordBox.getValue().toCharArray();
        if (registration) session.confirmRegistration(password, emailBox == null ? null : emailBox.getValue());
        else session.confirmCapturedSave(password);
        onClose();
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
        BackgroundExecutor.scheduler().schedule(30_000, () -> {
            BackgroundExecutor.onGameThread(() -> {
                if (value.equals(minecraft.keyboardHandler.getClipboard())) minecraft.keyboardHandler.setClipboard("");
            });
        });
    }

    private static PasswordGenerator.Policy policy(String preset) {
        return switch (preset) {
            case "pickyServer" -> PasswordGenerator.Policy.pickyServer();
            case "max" -> PasswordGenerator.Policy.max();
            case "memorable" -> PasswordGenerator.Policy.memorable();
            default -> PasswordGenerator.Policy.defaults();
        };
    }

    private void scheduleTimer() {
        timer = BackgroundExecutor.scheduler().schedule(250, () -> {
            BackgroundExecutor.onGameThread(() -> {
                if (Minecraft.getInstance().gui.screen() != this) return;
                long now = System.nanoTime();
                long elapsed = now - lastTimerNanos;
                lastTimerNanos = now;
                boolean hovering = mouseX >= cardX && mouseX < cardX + CARD_WIDTH
                        && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT;
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
        drawNineSlice(graphics, CARD_TEXTURE, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 32, 8);
        graphics.fill(cardX + 8, cardY, cardX + CARD_WIDTH - 8, cardY + 3, 0xff9b72ff);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() < cardX || event.x() >= cardX + CARD_WIDTH
                || event.y() < cardY || event.y() >= cardY + CARD_HEIGHT) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.blit(ICON_TEXTURE, cardX + 18, cardX + 42, cardY + 17, cardY + 41, 0, 1, 0, 1);
        graphics.text(font, Component.translatable("blockvault.card.title"), cardX + 52, cardY + 18, 0xffffffff);
        graphics.text(font, Component.literal(model.username()), cardX + 52, cardY + 34, 0xffc7c3d3);
        if (!BlockVaultConfig.current().streamerMode()) {
            graphics.text(font, Component.literal(model.host()), cardX + 18, cardY + 59, 0xffb2adbe);
        } else {
            graphics.text(font, Component.translatable("blockvault.card.address_hidden"), cardX + 18, cardY + 59, 0xffb2adbe);
        }
        if (model.variant() == CardVariant.REJECTED) {
            graphics.text(font, Component.translatable("blockvault.card.rejected.description"),
                    cardX + 18, cardY + 77, 0xffff9b9b);
        } else {
            graphics.text(font, Component.translatable("blockvault.card.unique"), cardX + 18, cardY + 77, 0xffc7c3d3);
        }
        int barWidth = (int) ((CARD_WIDTH - 36) * Math.max(0, Math.min(1,
                (double) remainingNanos / (BlockVaultConfig.current().cardDurationSeconds() * 1_000_000_000L))));
        graphics.fill(cardX + 18, cardY + CARD_HEIGHT - 8, cardX + CARD_WIDTH - 18, cardY + CARD_HEIGHT - 6, 0xff393342);
        graphics.fill(cardX + 18, cardY + CARD_HEIGHT - 8, cardX + 18 + barWidth, cardY + CARD_HEIGHT - 6, 0xff9b72ff);
    }

    private static void drawNineSlice(GuiGraphicsExtractor graphics, Identifier texture,
                                      int x, int y, int width, int height, int textureSize, int edge) {
        int middleWidth = textureSize - edge * 2;
        int middleHeight = textureSize - edge * 2;
        int[] dx = {x, x + edge, x + width - edge};
        int[] dy = {y, y + edge, y + height - edge};
        int[] dw = {edge, width - edge * 2, edge};
        int[] dh = {edge, height - edge * 2, edge};
        int[] sx = {0, edge, textureSize - edge};
        int[] sy = {0, edge, textureSize - edge};
        int[] sw = {edge, middleWidth, edge};
        int[] sh = {edge, middleHeight, edge};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                graphics.blit(texture, dx[column], dx[column] + dw[column], dy[row], dy[row] + dh[row],
                        (float) sx[column] / textureSize, (float) (sx[column] + sw[column]) / textureSize,
                        (float) sy[row] / textureSize, (float) (sy[row] + sh[row]) / textureSize);
            }
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (timer != null) timer.cancel();
        timer = null;
        if (model.variant() == CardVariant.REJECTED) session.dismissRejectedPrompt();
        wipePassword();
        if (passwordBox != null) passwordBox.setValue("");
        if (emailBox != null) emailBox.setValue("");
        model.clearPassword();
        super.onClose();
        closed.run();
    }

    private void wipePassword() { if (password != null) Arrays.fill(password, '\0'); }
}
