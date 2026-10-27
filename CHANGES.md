# Changes in this round

- **Removed** auto-lock and tombstones. **Added** the trash (60 days), manual add, import preview, `/blockvault` command, Caps Lock hint, backup purge on delete-forever, unknown-field preservation, Gradle wrappers.
- **Fixed** the stuck unlock screen, cancelled-unlock loops, server-rule parsing (and a built-in pattern that was silently dropped), deletion on failure messages, password-change and unregister detection, master-password key rotation, corrupt config handling, recovery safety, regenerate ignoring server rules, regex time limits, strict host and command validation.
- **Changed** Windows data folder to Local AppData, default preset to server-friendly, NeoForge icon key to `logoFile`.
