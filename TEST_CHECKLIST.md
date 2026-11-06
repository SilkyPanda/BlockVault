# Manual test checklist

Run the in-game checks on Fabric and NeoForge. The passed section records checks you reported as passed; everything else remains unverified. For login behavior, use a test server/account and avoid a password you use elsewhere.

## Reported passed

- [x] GitHub Actions build passed (as reported). The in-game checks below are tracked separately.
- [x] Build and launch both loaders with YACL installed and absent; verify the settings entry opens YACL or the native fallback.
- [x] Return from the vault and its sub-screens with Back and Escape. Verify `/blockvault` and BlockVault keybinds are absent.
- [x] Add, edit, reveal, copy, trash, restore, and permanently delete an account; invalid account input is never sent.
- [x] Export/import and backup flows were exercised, including recovery of a deleted account. Recheck the restore cases below after this change.
- [x] Confirm sensitive login commands do not appear in command history.

## Still to test

### Build and settings

- [ ] Run `./gradlew test` in `core/`. Rebuild the loader jars locally with `./gradlew build` in `fabric/` and `neoforge/` if you need local artifacts; CI already passed its build.
- [ ] In each loader, launch once with YACL and once without it. Open settings from the loader's normal entry point and verify the right screen appears.
- [ ] Create a vault with **Trust this device** on. Close and relaunch: it should unlock without a prompt. Lock it manually, then unlock with the correct and an incorrect master password.
- [ ] Repeat vault creation with device trust off. Close and relaunch: require the master password. Change the master password, restart, and verify the new one works while the old one does not.
- [ ] Test master passwords containing composed and decomposed Unicode. Try one whose normalized length is below the minimum; it should show a length error, not a corrupt-vault error.
- [ ] Hover each native settings control for its tooltip. Left-click and right-click the Autofill delay control; confirm it increases/decreases by 100 ms and stays within bounds. Change settings, restart, and verify they persist. Leave an idle card open and confirm it closes after 10 seconds.
- [ ] Open Unlock vault at a small and large window size. Check the password field, Unlock button, and **Recover from backup** button have even spacing and do not overlap.

### Vault entries

- [ ] Create two accounts on the same server. In the vault, toggle Autofill for only one account. Reconnect and trigger a login prompt for each: only the enabled account should autofill. Toggle the setting again and verify it persists after restart.
- [ ] Set different command templates for two accounts on the same server. Trigger registration/login for each and verify the selected account's template is used.
- [ ] Edit an account's password and save. Confirm there is no action to delete only the password; Delete should move the entire account entry to Trash.
- [ ] Delete an account, add the same username again with a different password, then restore the deleted entry. The restored entry should contain the original password, replacing the newer same-name entry. Also restore when no replacement exists and when unrelated accounts exist; those unrelated accounts should remain.
- [ ] Use **Copy folder path** after exporting. Paste into a text field and confirm it is the containing folder (not the encrypted file path). Check the export's displayed full path still matches the created file.

### Login detection and outcomes

- [ ] On a test server whose prompt says `/l <password>`, `/log <password>`, or `/reg <password>`, join and wait for the prompt. For each alias, enter a test password and confirm BlockVault captures it or offers the expected confirmation card. Confirm the same alias is not intercepted when the server has not prompted it.
- [ ] Register a test account. Confirm the password is saved before the command is sent. If the server rejects its format, confirm the retry uses a compliant replacement and does not offer the rejected value again.
- [ ] With a verified account, reconnect and test autofill enabled/disabled, the configured delay, and bare `/login` with passwordless login enabled/disabled. Verify BlockVault sends the password only after bare `/login` when enabled.
- [ ] With a pending account, confirm no password is sent until **Try login** is selected. Verify success marks it verified and rejection leaves it recoverable.
- [ ] Exercise wrong password, already logged in, registration success/failure, and timeout responses. An “already logged in” response must not mark a correct password as rejected.
- [ ] Add a valid custom rule to `patterns.json`, reconnect, and verify it takes effect. Add a malformed rule and confirm it is ignored without breaking built-in rules.
- [ ] Trigger a login prompt as an action-bar message and as a system message; neither should require player chat. Confirm player chat cannot trigger credential actions and prompts arriving after the 20-second join window are ignored.

### Backups, files, and privacy

- [ ] Export and import between two profiles. Check the preview, cancel, merge result, wrong export password, and creation of a restore point.
- [ ] Create a backup, inspect it, restore it, then change the master password. Verify new backups use the new password and an older backup can still be recovered with its original password.
- [ ] Erase a healthy vault with its master password. Confirm passwordless erase is offered only when the vault is missing or unreadable.
- [ ] Confirm passwords never appear in logs or chat. Check streamer mode hides server details and prompt cards wait until another screen closes.
- [ ] On Windows, macOS, and Linux, create and unlock a vault and check its data location and file permissions. On Windows, also replace an existing file through backup/restore or another vault operation.
