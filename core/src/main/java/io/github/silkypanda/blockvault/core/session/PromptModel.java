package io.github.silkypanda.blockvault.core.session;

/** Data passed to a card UI, with secret-safe representation and defensive password access. */
public record PromptModel(CardVariant variant, String host, String username, char[] password) {
    public PromptModel {
        password = password == null ? new char[0] : password.clone();
    }
    @Override public char[] password() { return password.clone(); }
    @Override public String toString() { return "PromptModel[variant=" + variant + ", host=" + host + ", password=****]"; }
}
