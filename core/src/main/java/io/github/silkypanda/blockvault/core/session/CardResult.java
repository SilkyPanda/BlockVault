package io.github.silkypanda.blockvault.core.session;

/** Progress and outcome updates for the credential card currently awaiting owner input. */
public enum CardResult {
    SAVING,
    SENDING,
    SUCCESS,
    FAILURE,
    TIMEOUT,
    ALREADY_LOGGED_IN
}
