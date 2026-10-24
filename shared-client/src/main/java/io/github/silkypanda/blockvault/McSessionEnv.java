package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.LoginSession;
import io.github.silkypanda.blockvault.core.session.PromptModel;
import io.github.silkypanda.blockvault.core.session.SessionEnv;
import io.github.silkypanda.blockvault.core.session.SessionState;
import io.github.silkypanda.blockvault.core.session.VaultAccess;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Minecraft-thread implementation of actions requested by the pure-Java login session. */
final class McSessionEnv implements SessionEnv {
    private static final int MAX_QUEUED_PROMPTS = 16;
    private final Queue<PromptModel> queuedPrompts = new ArrayDeque<>();
    private LoginSession session;
    private boolean showing;

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
    public VaultAccess vault() { return VaultRuntime.access(); }

    private void show(PromptModel model) {
        showing = true;
        Minecraft.getInstance().setScreenAndShow(new CredentialConfirmScreen(model, session, this::showNext));
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
        PromptModel queued;
        while ((queued = queuedPrompts.poll()) != null) queued.clearPassword();
        showing = false;
        if (Minecraft.getInstance().gui.screen() instanceof CredentialConfirmScreen) {
            Minecraft.getInstance().setScreenAndShow(null);
        }
    }
}
