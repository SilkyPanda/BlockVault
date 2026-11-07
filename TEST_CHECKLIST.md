# Manual test checklist

Use Fabric and NeoForge for UI checks. For server behavior, use a test account and a password you do not use elsewhere. Passed items below reflect the results you reported; all remaining items still need checking.

## Passed

- [x] GitHub Actions builds for both loaders succeeded.
- [x] Launch both loaders with YACL present and absent; settings opens YACL or the native fallback.
- [x] Return from the vault and sub-screens with Back and Escape. `/blockvault` and BlockVault keybinds are absent.
- [x] Add, edit, reveal, copy, trash, restore, and permanently delete an account. Invalid account input is not sent.
- [x] Export/import and backup flows, including recovering a deleted account.
- [x] Sensitive login commands do not appear in command history.
- [x] Create with **Trust this device** on; relaunch unlocks without a prompt. Manual lock, correct password, and incorrect password behavior work.
- [x] Create with device trust off; relaunch asks for the master password. Change the master password, restart, and verify the new password works while the old one does not.
- [x] Unicode master passwords work as separate sequences: `Café-Moon-42!` (`é` U+00E9) and `Café-Moon-42!` (`e` + U+0301). `Aa1!` shows the short-password message.
- [x] Native settings tooltips appear on hover.
- [x] Native settings button labels fit inside their bounds at the tested scale/window size.
- [x] Native settings hint sits below Back at the tested common GUI scales and window sizes.
- [x] Unlock screen spacing works at the tested small and large window sizes.
- [x] A short valid password during password change shows the 8-character minimum; after a successful change, the vault locks and unlocks with the new password.
- [x] Restore with no replacement, with a same-username replacement, and with an unrelated username preserves/replaces the expected entries.
- [x] Restoring over a live same-name account warns about the existing account and overwrite; cancel leaves it unchanged and confirm restores the trashed password.
- [x] Export's **Copy folder path** copies the containing folder; displayed file path matches the exported file.
- [x] Import requires an existing vault in both settings screens; creating one makes import available.
- [x] Per-account Autofill and per-account command templates work for two accounts on the same server.
- [x] The account edit screen has no Delete button; the vault list moves a whole account to Trash.
- [x] GitHub Actions built both loader artifacts successfully.
- [x] Native Autofill delay changes in 100 ms steps between 200 ms and 5000 ms and persists after restart.
- [x] Save, Update, Register, Pending login, and Rejected cards remain open after 30 seconds until manually closed.
- [x] With no vault, Import vault opens the create-vault screen; import is available after creating a vault.
- [x] With a verified account, Autofill on/off, delay, and bare `/login` with passwordless login on/off behave as expected.
- [x] Passwords do not appear in logs or chat; streamer mode hides server details and prompt cards wait until another screen closes.

## Still to test

### Vault entries and data transfer

- [ ] With no vault, choose **Import vault** from both settings screens. Confirm the create-vault screen opens; create a vault and verify the import window reopens. Import a file and confirm preview and merge.

### Login detection and results

- [ ] Short aliases `/l`, `/log`, and `/reg` previously failed. Test them one at a time on a server whose login prompt explicitly shows them, within 30 seconds of joining:
  1. Wait for `/l <password>`, then type `/l test-value`. Confirm BlockVault captures/handles it as a login command.
  2. Repeat with `/log <password>` and `/log test-value`.
  3. Repeat with `/reg <password>` and `/reg test-value`.
  4. On a server whose prompt does not mention an alias, enter it and confirm it passes through normally.
- [Give step by step guide.] Register a test account. Confirm BlockVault saves the password before sending the command. If the server rejects its rules, confirm the retry uses a compliant replacement and does not offer the rejected value again.
- [How to get pending account?] With a pending account, confirm no password is sent until **Try login** is selected. Success should verify it; rejection should leave it recoverable.
- [Divide this in multiple tests and tell me what to do exactly.] Exercise wrong-password, already-logged-in, registration success/failure, and timeout messages. “Already logged in” must not mark a correct password as rejected.
- [Step by step guide.] Add a valid custom rule to `patterns.json`, reconnect, and confirm it works. Add a malformed rule and confirm it is ignored without breaking built-in rules.
- [How to test each?] Trigger prompts as action-bar and system messages; both should work. Player chat must not trigger credential actions. A prompt after the 30-second join window should be ignored.
- [ ] Test chat-shaped prompt spoofs as system messages: `<Steve>`, `[Steve -> me]`, `[Rank] Steve »`, and `Steve:` followed by login/register instructions must not open a card. A plain server line such as `Use /register to claim rewards` should still be detected.
- [ ] With a verified saved account and passwordless login enabled, type bare `/login`. Confirm only the credentialed command reaches the server. Repeat without a verified saved account and confirm bare `/login` passes through unchanged.
- [ ] Type `/login wrong-value` while already logged in. Confirm BlockVault does not offer to save that value.

### Card and settings changes

- [ ] Check native settings at Auto GUI scale on a 1920×1080 display and in a small window. Confirm settings and action labels remain visible, then change Autofill delay by dragging and verify it saves on release.
- [ ] Toggle Card animations off and on in both YACL and native settings. Confirm the preference persists, motion is absent when off, and cards still require an explicit close when idle.
- [ ] Open each credential card in a narrow window. Confirm controls stack without clipped labels and keyboard input during the first 250 ms does not confirm an action.

### Backups, erase, files, and privacy

- [ ] Export and import between two profiles. Check preview, cancel, merge results, wrong export password, and restore point creation.
- [ ] Create and inspect a backup, restore it, then change the master password. New backups should use the new password; an older backup should remain recoverable with its original password.
- [ ] Open a backup's detail screen and delete one manual and one automatic backup with confirmation. Verify each disappears from the list and neighboring backups remain available.
- [ ] With an empty vault, request a manual backup and save an edit. Confirm neither creates a backup. Add an account and confirm a manual backup is created.
- [ ] Save unchanged vault data after the automatic backup interval and confirm no duplicate automatic backup appears. Change an account, save, and confirm one new automatic backup appears.
- [ ] Open **Erase BlockVault data** and verify the warning names the vault, backups, and exports in BlockVault's data folder and says to save wanted files elsewhere. Confirm the separate confirmation screen, cancel and verify nothing was deleted, then confirm erase without entering a password. Create a fresh vault afterward.
- [ ] On Windows, macOS, and Linux, create and unlock a vault; check data location and file permissions. On Windows, also replace an existing file through backup/restore or another vault operation.
