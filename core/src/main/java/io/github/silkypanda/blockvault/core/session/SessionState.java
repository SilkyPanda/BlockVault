package io.github.silkypanda.blockvault.core.session;

/** Per-connection login detection state. */
public enum SessionState { IDLE, PROMPTED, SENT, CONFIRMED, REJECTED, UNKNOWN }
