# Changes in this round

Requested: removed auto-lock; added trash (60-day retention, manual restore/delete, accounts and servers); removed tombstones; documented conflicts (`CONFLICT_HANDLING.md`).

Review items implemented: stuck unlock screen (H1); cancelled unlock no longer drops work or nags (H2); rule-hint parsing, ranges, "non-alphanumeric", fallback retry, built-in patterns over the 256-char cap (H3); no deletion on failure-looking messages (H4); password-change/unregister success patterns (H5); vault-key rotation on password change (M1); real corpus test (M4); import hardening (M5, partial); previous-password carried through pending saves (M6); backup re-encryption continues past bad files (M7); corrupt config handling (M8); recovery lock/damaged copy/stale trust (M12); Regenerate uses session policy (M13); message length cap (M14); `removed()` cleanup (M16, partial); plus many low items (see conversation summary).

Follow-up: Windows data folder is now `%LOCALAPPDATA%\BlockVault` (no migration); the memorable word list loader accepts the EFF short list as downloaded (first 1,024 words used); BlockVault now warns once, with a toast and a log line, if its command or history hook is not active on the running Minecraft version.
