# Changelog

## 0.2.0-alpha.1 — current development (not published)

### Vault, accounts, backups, and transfer

- Changed vault deletion to trash one account at a time. Removing a server's final account removes that server entry; restoring the trashed account recreates it.
- Added a second confirmation click for permanent trash deletion, optional export folder with generated filenames, and a default `exports/` folder under BlockVault's data directory.
- Added confirmed deletion for manual and automatic backups.
- Skip manual and automatic snapshots for empty vaults, and skip an automatic snapshot when the newest automatic backup contains the same vault data.
- Return to the encrypted import form after creating a vault from an import action; importing requires an existing vault.
- Added per-account Autofill and command-template preferences; older server-level preferences remain as migration defaults.
- Added passwordless `/login`: typing a bare login command sends the verified saved password through the server's configured login template, regardless of global or per-server autofill settings.
- Added **Copy folder path** to export and use a generated filename when the destination folder is selected.
- Preserved the Unicode password, username, and punycode host input rules; password pastes containing rejected characters are refused as a whole.
- Made malformed account records readable and marked unusable account entries so they do not autofill.
- Added and retained trash recovery and restore-over-existing confirmation flows.

### Login detection and credential cards

- Changed credential capture to always be allowed, removed its per-server toggle and opt-out action, and kept autofill as a separate per-account setting.
- Limited short-alias capture and history scrubbing to aliases explicitly requested by a server prompt.
- Added a 30-second prompt-listening window and reject chat-shaped prompt text.
- Fixed save-card focus timing, queued an early prompt until settings load, and added the missing invalid-password toast text.
- Changed login cards to remain open until manually dismissed; registration success closes without a confirmation card message.
- Added the nine-slice credential card, result states, status chips, server favicon with an initial fallback, and card animations.
- Hide the server favicon and fallback initial when **Hide server address** is enabled.
- Removed the card keyboard hint; Enter is consumed on result cards to prevent resubmitting credentials.
- Fixed successful login/registration result handling so the same server message is not processed as a new prompt, and discard queued prompts after confirmation.
- Aligned the top accent line to the rounded card border and changed card screen transitions to avoid an extra synchronous frame.
- Built-in prompt and result detection remains English-only.

### Settings, screens, and platform integration

- Redesigned settings with YACL categories and controls shared by Fabric and NeoForge. YACL is optional; the native settings screen is the fallback.
- Changed native Autofill delay control to a draggable slider; removed the unused card-duration setting.
- Moved import/export from the vault list into YACL's Vault and files tab, and placed credential prompt settings under Login. Widened vault-row labels to reduce clipped server names.
- Removed the edit-password Generate action and the vault screen's Config action; Back returns to the previous screen.
- Removed BlockVault keybinds and the `/blockvault` command.
- Fixed YACL Reset bindings to use BlockVault's actual default values instead of the values loaded when the screen opens. Restored the vault-row Copy label.
- Wrapped long help text, corrected password-screen layout, added Enter submission to password forms, and preserved the entered password when confirmation mismatches.
- Updated NeoForge mod-list icon/banner metadata.

### Security, compatibility, and maintenance

- Removed master-password entry from **Erase BlockVault data**; the separate confirmation authorizes deletion directly and warns that vault, backups, and exports are removed.
- Fixed Windows data folder to Local AppData and the default preset to Basic.
- Kept the packaged word list to the 1,024 words the generator consumes and retained source/line debug data without local-variable tables.
- Updated version metadata to `0.2.0-alpha.1`; no release has been published.
- Changed the project license to GPL-3.0-only and linked the source and license in user/developer documentation.
- Updated `STATUS.md`, `TEST_CHECKLIST.md`, README, and review notes to distinguish reported manual passes from outstanding checks.
- Core CI runs `cleanTest test`, reports discovered test totals, and fails if no test cases are discovered.

## Earlier development history (release version not specified)

### Vault and recovery

- Removed auto-lock and tombstones. Added trash with a 60-day retention period, manual add, import preview, unknown-field preservation, and Gradle wrappers.
- Fixed master-password key rotation, corrupt config handling, recovery safety, and deletion on failure messages.
- Fixed the stuck unlock screen and cancelled-unlock loops.

### Login and password handling

- Added the `/blockvault` command (later removed in 0.2.0-alpha.1).
- Added a Caps Lock hint (later removed) and per-server login behavior (later migrated to account preferences).
- Fixed server-rule parsing and a built-in pattern that was silently dropped; fixed password-change and unregister detection.
- Fixed regenerate ignoring server rules, regex time limits, strict host and command validation, and NFC password length handling.
- Fixed Windows replace retry handling for `FileSystemException`.

### Settings and user interface

- Changed the default generator preset from Server-friendly to Basic.
- Removed server-only rows and Reload Patterns; retained user-editable `patterns.json` support.
- Removed the open-data-folder action and related code.
- Made YACL optional and added native settings fallback; explained the trusted-device choice during vault creation.

### Later maintenance recorded during the earlier development period

- Fixed memorable password generation when server length rules are narrower than the preset's expected length but can still fit a sampled word combination.
- Changed all login cards to remain open until manually dismissed.
- Removed the unused card-duration setting.
- Updated project docs and QA steps to reflect the current trash, backup, erase, input, and review status.
