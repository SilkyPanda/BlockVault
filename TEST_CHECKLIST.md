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
- [x] Native settings tooltips appear on hover.
- [x] Native settings button labels fit inside their bounds at the tested scale/window size.
- [x] Unlock screen spacing works at the tested small and large window sizes.
- [x] Per-account Autofill and per-account command templates work for two accounts on the same server.
- [x] The account edit screen has no Delete button; the vault list moves a whole account to Trash.

## Still to test

### Build and settings

- [ ] Check the latest GitHub Actions run for core tests and both loader builds. You run builds on GitHub, so no local build is needed.
- [ ] Test Unicode master passwords in two separate vaults:
  1. Create and unlock one with `Café-Moon-42!` (`é` is U+00E9).
  2. Create and unlock another with `Café-Moon-42!` (`e` followed by U+0301). It looks similar but uses a different character sequence.
  3. Try `Aa1!`. At 4 characters, it is below the 8-character minimum; expect a short-password message, not an invalid-character message or damaged-vault error.
- [ ] In native settings, left-click Autofill delay to increase by 100 ms and right-click to decrease by 100 ms. Right-click previously did nothing; retest after the mouse-button change. Check the 200 ms and 5000 ms limits and verify settings persist after restart.
- [ ] The native settings hint should sit below Back, not between the settings rows and action buttons. Confirm this at common GUI scales and window sizes.
- [ ] Check idle card timeout: join a test server, trigger a save/update card, then do not touch it. It should close after 10 seconds. A registration card should stay until dismissed; it previously stayed open, so verify the fix.
- [ ] Create with a short but otherwise valid new master password during password change. It should show the 8-character minimum message. After a successful password change, confirm the vault locks immediately and can be unlocked with the new password.

### Vault entries and data transfer

- [ ] Test restore in three cases: restore with no replacement account; restore after creating a same-username account with a different password (the deleted password should replace it); restore while another username exists (that unrelated entry should remain).
- [ ] If restoring over a live same-name account, verify the warning says the account already exists, suggests testing it first, and explains the overwrite. Cancel and verify no change; confirm and verify the trashed password replaces the live one.
- [ ] Export a vault, click **Copy folder path**, and paste the result into a text field. It should be the containing folder. Compare the displayed full file path with the actual exported file.
- [ ] With no `vault.bv` present, click **Import vault** in both settings screens. Import should be blocked with a message to create a vault first. Create a vault and confirm import becomes available.

### Login detection and results

- [ ] Short aliases `/l`, `/log`, and `/reg` previously failed. Test them one at a time on a server whose login prompt explicitly shows them, within 20 seconds of joining:
  1. Wait for `/l <password>`, then type `/l test-value`. Confirm BlockVault captures/handles it as a login command.
  2. Repeat with `/log <password>` and `/log test-value`.
  3. Repeat with `/reg <password>` and `/reg test-value`.
  4. On a server whose prompt does not mention an alias, enter it and confirm it passes through normally.
- [ ] Register a test account. Confirm BlockVault saves the password before sending the command. If the server rejects its rules, confirm the retry uses a compliant replacement and does not offer the rejected value again.
- [ ] With a verified account, test Autofill on/off, its delay, and bare `/login` with passwordless login on/off. The saved password should only be sent by bare `/login` when passwordless login is enabled.
- [ ] With a pending account, confirm no password is sent until **Try login** is selected. Success should verify it; rejection should leave it recoverable.
- [ ] Exercise wrong-password, already-logged-in, registration success/failure, and timeout messages. “Already logged in” must not mark a correct password as rejected.
- [ ] Add a valid custom rule to `patterns.json`, reconnect, and confirm it works. Add a malformed rule and confirm it is ignored without breaking built-in rules.
- [ ] Trigger prompts as action-bar and system messages; both should work. Player chat must not trigger credential actions. A prompt after the 20-second join window should be ignored.

### Backups, erase, files, and privacy

- [ ] Export and import between two profiles. Check preview, cancel, merge results, wrong export password, and restore point creation.
- [ ] Create and inspect a backup, restore it, then change the master password. New backups should use the new password; an older backup should remain recoverable with its original password.
- [ ] Open **Permanently erase data** and verify its warning names the vault, backups, and exports in BlockVault's data folder and says to save wanted files elsewhere. Press erase and confirm a separate confirmation screen appears. Cancel and verify nothing was deleted. Then test erase with the master password and **Forgot password? Erase data** without it; create a fresh vault afterward.
- [ ] Confirm passwords never appear in logs or chat. Check streamer mode hides server details and prompt cards wait until another screen closes.
- [ ] On Windows, macOS, and Linux, create and unlock a vault; check data location and file permissions. On Windows, also replace an existing file through backup/restore or another vault operation.
