package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.CardVariant;
import io.github.silkypanda.blockvault.core.session.LoginSession;
import io.github.silkypanda.blockvault.core.session.PromptModel;
import io.github.silkypanda.blockvault.core.session.SessionState;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Small M3 confirmation screen; M4 replaces its layout with the full credential card. */
final class CredentialConfirmScreen extends Screen {
    private final PromptModel model;
    private final LoginSession session;
    private final Runnable closed;
    private char[] password;
    private EditBox passwordBox;
    private EditBox emailBox;
    private boolean reveal;

    CredentialConfirmScreen(PromptModel model, LoginSession session, Runnable closed) {
        super(Component.translatable("blockvault.card.title"));
        this.model = model;
        this.session = session;
        this.closed = closed;
        this.password = model.password();
    }

    @Override
    protected void init() {
        int center = width / 2;
        int top = height / 2 - 58;
        passwordBox = new EditBox(font, center - 100, top + 36, 200, 20,
                Component.translatable("blockvault.card.password"));
        passwordBox.setMaxLength(64);
        passwordBox.setValue(new String(password));
        passwordBox.addFormatter((value, cursor) -> reveal
                ? FormattedCharSequence.forward(value, Style.EMPTY)
                : FormattedCharSequence.forward("\u2022".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(passwordBox);
        int buttonY = top + 68;
        if (model.emailRequired()) {
            emailBox = new EditBox(font, center - 100, top + 62, 200, 20,
                    Component.translatable("blockvault.card.email"));
            emailBox.setHint(Component.translatable("blockvault.card.email"));
            emailBox.setMaxLength(254);
            addRenderableWidget(emailBox);
            buttonY = top + 94;
        }
        if (model.variant() == CardVariant.REGISTER || model.variant() == CardVariant.SAVE
                || model.variant() == CardVariant.UPDATE) {
            String key = model.variant() == CardVariant.REGISTER
                    ? "blockvault.card.register" : model.variant() == CardVariant.UPDATE
                    ? "blockvault.card.update" : "blockvault.card.save";
            addRenderableWidget(Button.builder(Component.translatable(key), button -> accept())
                    .bounds(center - 100, buttonY, 200, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("blockvault.card.reveal"),
                            button -> toggleReveal())
                    .bounds(center - 100, buttonY + 24, 96, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("blockvault.card.cancel"),
                            button -> onClose())
                    .bounds(center + 4, buttonY + 24, 96, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("blockvault.card.cancel"),
                            button -> onClose())
                    .bounds(center - 100, buttonY, 200, 20).build());
        }
        setInitialFocus(passwordBox);
    }

    private void accept() {
        Arrays.fill(password, '\0');
        password = passwordBox.getValue().toCharArray();
        if (model.variant() == CardVariant.REGISTER
                || model.variant() == CardVariant.UPDATE && session.state() == SessionState.PROMPTED) {
            session.confirmRegistration(password, emailBox == null ? null : emailBox.getValue());
        } else {
            session.confirmCapturedSave(password);
        }
        onClose();
    }

    private void toggleReveal() {
        reveal = !reveal;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int y = height / 2 - 48;
        graphics.centeredText(font, Component.literal(model.host()), width / 2, y, 0xffffffff);
        graphics.centeredText(font, Component.literal(model.username()), width / 2, y + 14, 0xffbbbbbb);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Arrays.fill(password, '\0');
        if (passwordBox != null) passwordBox.setValue("");
        if (emailBox != null) emailBox.setValue("");
        super.onClose();
        model.clearPassword();
        closed.run();
    }
}
