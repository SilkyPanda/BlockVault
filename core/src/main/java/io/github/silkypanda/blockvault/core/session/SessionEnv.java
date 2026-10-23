package io.github.silkypanda.blockvault.core.session;

/** Game-facing actions required by {@link LoginSession}. */
public interface SessionEnv {
    /** Sends a command without the leading slash. */
    void sendCommand(String commandWithoutSlash);
    /** Opens or queues a save card. */
    void showSavePrompt(PromptModel model);
    /** Shows a localized toast key. */
    void toast(String translationKey);
    /** Returns the asynchronous vault facade. */
    VaultAccess vault();
}
