# Changes in this round

- **Removed** auto-lock and tombstones. **Added** the trash (60 days), manual add, import preview, `/blockvault` command, Caps Lock hint, unknown-field preservation, Gradle wrappers.
- **Fixed** the stuck unlock screen, cancelled-unlock loops, server-rule parsing (and a built-in pattern that was silently dropped), deletion on failure messages, password-change and unregister detection, master-password key rotation, corrupt config handling, recovery safety, regenerate ignoring server rules, regex time limits, strict host and command validation.
- **Changed** Windows data folder to Local AppData and the default preset to server-friendly.

# Changes in the current work

- **Fixed** memorable password generation when server length rules are narrower than the preset's expected length but can still fit a sampled word combination.
- **Changed** vault deletion to trash one account at a time. Removing a server's final account removes that server entry; restoring the trashed account recreates it.
- **Added** a second confirmation click for permanent trash deletion, optional export folder with generated filenames, and a default `exports/` folder under BlockVault's data directory.
- **Redesigned** settings with YACL categories and controls shared by Fabric and NeoForge. Updated NeoForge mod-list icon/banner metadata.
- **Removed** the edit-password Generate action and the vault screen's Config action; Back now closes the vault screen.
- **Kept** the jar under the documented 256 KiB limit by bundling only the 1,024 words the generator already consumes and retaining source/line debug data without local-variable tables.
- **Added** passwordless `/login`: typing a bare login command sends the verified saved password through the server's configured login template, regardless of global or per-server autofill settings.
- **Changed** credential capture to always be allowed, removed its per-server toggle and opt-out action, and kept autofill as a separate per-server setting.
- **Fixed** YACL Reset bindings to use BlockVault's actual default values instead of the values loaded when the screen opens. Restored the vault-row Copy label.
- **Changed** the default idle save/update card duration from 10 seconds to 5 seconds; the YACL setting remains configurable.
- **Moved** import/export from the vault list into YACL's Vault and files tab, and placed credential prompt settings under Login. Widened vault-row labels to reduce clipped server names.

## Review follow-up

- Preserved the Unicode password, username, and punycode host input rules; password pastes containing rejected characters are refused as a whole.
- Made malformed account records readable and marked unusable account entries so they do not autofill.
- Fixed save-card focus timing, queued an early prompt until settings load, gated passwordless erase, and added the missing invalid-password toast text.
- Limited short-alias capture and history scrubbing to aliases explicitly requested by a server prompt.
- Wrapped long help text, corrected password-screen layout, added Enter submission to password forms, and preserved the entered password when confirmation mismatches.
- Updated project docs and QA steps to reflect the current trash, backup, erase, input, and review status.
