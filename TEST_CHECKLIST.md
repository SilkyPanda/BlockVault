# Manual test checklist

Treat every item as untested. Run these checks on Fabric and NeoForge; do not mark an item complete until you have seen it work in-game.

## Launch and vault access

- [ ] Run `./gradlew test` in `core/` and build both loader jars with `./gradlew build` from their directories.
- [ ] Build and launch each loader with YACL installed and absent. Confirm the mod loads and the config button opens the appropriate settings screen.
- [ ] From settings, open and close the vault and each settings sub-screen with both its Back button and Escape. Confirm it returns to the previous screen. Verify `/blockvault` is no longer registered and both vault keybinds still work.
- [ ] Create a vault with device trust on, then off. Check restart unlock behavior, manual lock, wrong master password, and master-password change.
- [ ] Create and unlock with composed and decomposed Unicode master passwords. Confirm too-short normalized passwords get a length message rather than a damaged-vault message.
- [ ] Try native settings tooltips, both Autofill delay mouse buttons, the Basic/Memorable presets, and card timing. Confirm settings persist after restart and idle cards close after 10 seconds.

## Accounts and server login

- [ ] Add, edit, reveal, copy, trash, restore, and permanently delete an account. Confirm invalid account input stays visible but is never sent.
- [ ] Register on a test login-plugin server. Confirm the password is saved before sending; a password-rule rejection offers a compliant replacement and does not re-offer the rejected value.
- [ ] Reconnect with a verified account. Check autofill on/off, delay, manual passwordless `/login` on/off, and that bare `/l` or `/log` reaches the server unless that short alias appeared in its prompt.
- [ ] With a pending account, confirm no password is sent until Try login is selected. Check success promotes it and rejection leaves it recoverable.
- [ ] Check wrong-password, already-logged-in, registration success/failure, and timeout messages. Confirm a good password is not offered as rejected after an “already logged in” response.
- [ ] Edit `patterns.json`, reconnect, and confirm the custom prompt or result rule takes effect. Check that malformed entries are ignored.
- [ ] Send a login prompt as an action bar and as a system message. Confirm player chat cannot trigger credential actions and prompts after the 20-second join window are ignored.

## Vault data and privacy

- [ ] Export and import between two profiles. Check preview, cancel, merge results, wrong password, and restore point creation.
- [ ] Export with a blank folder and with a custom folder. Confirm the screen reports the full path and the file is created there; check title/help text do not overlap at common window sizes.
- [ ] Create, inspect, and restore backups. Change the master password, then check backups encrypted with the new key and recovery of a backup still using an older password.
- [ ] Erase a healthy vault with its master password; check passwordless erase is only offered for missing or unreadable vaults.
- [ ] Confirm sensitive commands are absent from Minecraft command history and passwords never appear in logs or chat. Check streamer mode and that cards wait while another screen is open.
- [ ] On Windows, macOS, and Linux, create and unlock a vault; check data location and file permissions. On Windows, also exercise backup/restore or another operation that replaces an existing file.
