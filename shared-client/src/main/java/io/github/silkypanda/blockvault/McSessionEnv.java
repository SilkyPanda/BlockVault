package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.LoginSession;
import io.github.silkypanda.blockvault.core.session.PromptModel;
import io.github.silkypanda.blockvault.core.session.Scheduler;
import io.github.silkypanda.blockvault.core.session.SessionEnv;
import io.github.silkypanda.blockvault.core.session.SessionState;
import io.github.silkypanda.blockvault.core.session.VaultAccess;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Minecraft-thread implementation of actions requested by the pure-Java login session. */
final class McSessionEnv implements SessionEnv {
    private static final int MAX_QUEUED_PROMPTS = 16;
    private static final long SCREEN_POLL_MILLIS = 400;
    private final Queue<PromptModel> queuedPrompts = new ArrayDeque<>();
    private LoginSession session;
    private boolean showing;
    private PromptModel waitingModel;
    private Scheduler.Handle waitHandle;

    void attach(LoginSession value) { session = value; }

    @Override
    public void sendCommand(String commandWithoutSlash) {
        Hooks.sendOwnCommand(commandWithoutSlash);
    }

    @Override
    public void showSavePrompt(PromptModel model) {
        if (showing) {
            if (queuedPrompts.size() >= MAX_QUEUED_PROMPTS) queuedPrompts.remove().clearPassword();
            queuedPrompts.add(model);
            return;
        }
        show(model);
    }

    @Override
    public void toast(String translationKey) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.components.toasts.SystemToast.add(mc.gui.toastManager(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("blockvault.toast.title"), Component.translatable(translationKey));
    }

    @Override
    public void chatMessage(String translationKey) {
        BackgroundExecutor.onGameThread(() -> Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
                Component.literal("[BlockVault] ").withStyle(Style.EMPTY.withColor(0x55ff55))
                        .append(Component.translatable(translationKey).withStyle(Style.EMPTY.withColor(0x55ff55)))));
    }

    @Override
    public VaultAccess vault() { return VaultRuntime.access(); }

    /** Shows a card, but never on top of a screen the player is already using (chat, inventory, menus). */
    private void show(PromptModel model) {
        showing = true;
        if (Minecraft.getInstance().gui.screen() == null) {
            display(model);
            return;
        }
        waitingModel = model;
        pollForFreeScreen();
    }

    private void display(PromptModel model) {
        waitingModel = null;
        Minecraft.getInstance().setScreenAndShow(new CredentialConfirmScreen(model, session, this::showNext));
    }

    private void pollForFreeScreen() {
        waitHandle = BackgroundExecutor.scheduler().schedule(SCREEN_POLL_MILLIS, () -> {
            waitHandle = null;
            PromptModel model = waitingModel;
            if (model == null) return;
            if (Minecraft.getInstance().gui.screen() == null) display(model);
            else pollForFreeScreen();
        });
    }

    private void showNext() {
        showing = false;
        if (session != null && (session.state() == SessionState.SENT
                || session.state() == SessionState.UNKNOWN)) {
            PromptModel stale;
            while ((stale = queuedPrompts.poll()) != null) stale.clearPassword();
            return;
        }
        PromptModel next = queuedPrompts.poll();
        if (next != null) show(next);
    }

    void disconnect() {
        if (waitHandle != null) waitHandle.cancel();
        waitHandle = null;
        if (waitingModel != null) waitingModel.clearPassword();
        waitingModel = null;
        PromptModel queued;
        while ((queued = queuedPrompts.poll()) != null) queued.clearPassword();
        showing = false;
        if (Minecraft.getInstance().gui.screen() instanceof CredentialConfirmScreen screen) screen.disconnect();
    }
}
