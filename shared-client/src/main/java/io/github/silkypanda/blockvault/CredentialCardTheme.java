package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.session.CardVariant;

/** Shared colors and spacing for the credential prompt card. */
final class CredentialCardTheme {
    static final int MUTED = 0xffb2adbe;
    static final int REGISTER = 0xff62c99b;
    static final int UPDATE = 0xffffc45e;
    static final int REJECTED = 0xffff6f73;
    static final int SAVE = 0xff9b72ff;
    static final int PENDING = 0xff69baff;

    private CredentialCardTheme() { }

    static int accent(CardVariant variant) {
        return switch (variant) {
            case REGISTER -> REGISTER;
            case UPDATE -> UPDATE;
            case REJECTED -> REJECTED;
            case SAVE -> SAVE;
            case PENDING_LOGIN -> PENDING;
        };
    }

    static String statusKey(CardVariant variant) {
        return "blockvault.card.status." + switch (variant) {
            case REGISTER -> "register";
            case UPDATE -> "update";
            case REJECTED -> "rejected";
            case SAVE -> "save";
            case PENDING_LOGIN -> "pending";
        };
    }
}
