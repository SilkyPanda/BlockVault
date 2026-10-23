package io.github.silkypanda.blockvault.core.detect;

/** Password constraints inferred from a server's system message. */
public record ServerRuleHint(Integer minLength, Integer maxLength, boolean alphanumericOnly) { }
