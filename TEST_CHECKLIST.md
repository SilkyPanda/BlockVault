# Manual test checklist

Use Fabric and NeoForge for the UI and login checks. For server behavior, use a test account and a password that you do not use elsewhere. Items are only marked passed when you reported them as passed.

## Passed

- [x] GitHub Actions builds passed; builds will be run on GitHub rather than locally.
- [x] Launch both loaders with YACL present and absent; the settings entry opens YACL or the native fallback.
- [x] Return from the vault and its sub-screens using Back and Escape. `/blockvault` and BlockVault keybinds are absent.
- [x] Add, edit, reveal, copy, trash, restore, and permanently delete an account. Invalid account input is not sent.
- [x] Export/import and backup flows, including recovering a deleted account.
- [x] Sensitive login commands do not appear in command history.
- [x] Create with **Trust this device** enabled; relaunch unlocks without a prompt. Manual lock, correct password, and incorrect password behavior work.
- [x] Create with device trust disabled; relaunch asks for the master password. Changing the master password works after restart; the old password does not.

## Still to test

### Build and settings

- [ ] Check the latest GitHub Actions run for `core` tests and both loader builds. No local build is needed.
- [ ] Test Unicode master passwords in two separate vaults:
  1. Create and unlock one with `Café-Moon-42!` (the `é` is one character, U+00E9).
  2. Create and unlock another with `Café-Moon-42!` (the accent is U+0301 after the `e`). It looks similar but has a different character sequence.
  3. Try `Aa1!` as a master password. It is shorter than the 8-character minimum; creation should report a length error, not a damaged vault.
- [ ] In native settings, hover each control for its tooltip. Click Autofill delay with the left mouse button: it should increase by 100 ms. Right-click it: it should decrease by 100 ms. Repeat at 200 ms and 5000 ms to check the limits, then change another setting and restart to verify settings persist.
- [ ] Check idle card timeout: join a test server, trigger a save/update card, then do not touch it. It should close after 10 seconds. A registration card should stay until dismissed.
- [ ] At common GUI scales and window sizes, inspect the native settings screen. Every toggle, value, and action button label must stay inside its button. Hover tooltips, including for **Permanently erase data**.
- [ ] Open Unlock vault at a small and large window size. The password field, Unlock button, and Recover from backup button should have even spacing and not overlap.

### Vault entries

- [ ] Create two accounts on one test server. Turn Autofill on for one and off for the other. Trigger login prompts for each; only the enabled account should fill. Restart and confirm each choice persists.
- [ ] Choose different command templates for those accounts. Trigger each account’s login or registration flow; each should use its own template.
- [ ] Edit an account password and save. Confirm the edit screen has no Delete button. From the vault list, delete the account entry; it should move to Trash with its password intact.
- [ ] Test restore in three cases: restore with no replacement account; restore after creating a same-username account with a different password (the deleted password should replace it); restore while another username exists (that unrelated entry should remain).
- [ ] Export a vault, click **Copy folder path**, and paste the result into a text field. It should be the containing folder. Compare the displayed full file path with the actual exported file.

### Login detection and results

- [ ] Verify short aliases one at a time on a test server that explicitly shows each command in its prompt:
  1. Join and wait for a prompt containing `/l <password>`. Type `/l test-value` and submit. Confirm it is captured/handled as a login command.
  2. Repeat with `/log <password>` and `/log test-value`.
  3. Repeat with `/reg <password>` and `/reg test-value`.
  4. Reconnect to a server whose prompt does not mention one of these aliases. That alias should pass through normally; short aliases should only be recognized when the server prompt lists them.
- [ ] Register a test account. Confirm BlockVault saves the password before sending the command. If the server rejects its password rules, confirm the retry uses a compliant replacement and does not offer the rejected value again.
- [ ] With a verified account, test Autofill on/off, its delay, and bare `/login` with passwordless login on/off. The saved password should only be sent by bare `/login` when passwordless login is enabled.
- [ ] With a pending account, confirm no password is sent until **Try login** is selected. Success should verify it; rejection should leave it recoverable.
- [ ] Exercise wrong-password, already-logged-in, registration success/failure, and timeout messages. “Already logged in” must not mark a correct password as rejected.
- [ ] Add a valid custom rule to `patterns.json`, reconnect, and confirm it works. Add a malformed rule and confirm it is ignored without breaking built-in rules.
- [ ] Trigger prompts as action-bar and system messages; both should work. Player chat must not trigger credential actions. A prompt after the 20-second join window should be ignored.

### Backups, erase, files, and privacy

- [ ] Export and import between two profiles. Check preview, cancel, merge results, wrong export password, and restore point creation.
- [ ] Create and inspect a backup, restore it, then change the master password. New backups should use the new password; an older backup should still be recoverable with its original password.
- [ ] Open **Permanently erase data** and read the warning. Confirm it says backups and exports in the BlockVault data folder will be deleted and tells you to save wanted copies somewhere safe. Cancel and confirm nothing was deleted. Then erase a test vault with its master password and confirm success. Passwordless erase should only be available if the vault is missing or unreadable.
- [ ] Confirm passwords never appear in logs or chat. Check streamer mode hides server details and prompt cards wait until another screen closes.
- [ ] On Windows, macOS, and Linux, create and unlock a vault; check data location and file permissions. On Windows, also replace an existing file through backup/restore or another vault operation.
