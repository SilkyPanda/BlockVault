package io.github.silkypanda.blockvault.core.session;

/** Data passed to a card UI, with secret-safe representation and defensive password access. */
public record PromptModel(CardVariant variant, String host, String username, char[] password, boolean emailRequired) {
    /** Creates a prompt model that does not require an email field. */
    public PromptModel(CardVariant variant, String host, String username, char[] password) {
        this(variant, host, username, password, false);
    }

    public PromptModel {
        password = password == null ? new char[0] : password.clone();
    }
    @Override public char[] password() { return password.clone(); }
    /** Wipes the password retained by this presentation model after the UI no longer needs it. */
    public void clearPassword() { java.util.Arrays.fill(password, '\0'); }
    @Override public String toString() { return "PromptModel[variant=" + variant + ", host=" + host + ", password=****]"; }
}
