# Testing guide and how to proceed

The `core` library is covered by automated tests. Everything that touches Minecraft (`shared-client`, `fabric`, `neoforge`) has never been compiled or run, so work through this file in order.

## 1. How to proceed

1. **Compile.** Push the branch and read the GitHub Actions run. Expect compile errors in the Minecraft-facing code and fix them first; nothing else can be tested until both jars build. The most likely trouble spots, because they depend on Minecraft 26.3 and loader APIs that could not be checked offline:
   - `KeyEvent.modifiers()` (Caps Lock hint, `Keybinds.trackModifiers`)
   - the folder-opening helper (found by reflection; replace with a direct call once you know its name)
   - `Screen.removed()`, `Minecraft.getInstance().gui.screen()`, `Button.setMessage`, `EditBox.setResponder`, `GuiGraphicsExtractor.centeredText` and `text`
   - `ClientCommandManager` / `ClientCommandRegistrationCallback` (Fabric) and `RegisterClientCommandsEvent` (NeoForge)
   - the three mixin targets (see `DESIGN.md`)
2. **Run `core` tests** (section 2). They must be green before anything else.
3. **Dev client, offline** (sections 3.1-3.4): vault creation, unlock, UI screens. No server needed.
4. **Local Paper server with AuthMe** (offline mode) for the login flows (3.5-3.8). Repeat once with nLogin or LoginSecurity if you can.
5. **Both loaders.** Run everything on Fabric, then repeat the parity checks (3.14) on NeoForge.
6. **Other platforms** (3.16): Windows, macOS, Linux at least for vault creation, opening the data folder and file permissions.
7. **Fix, then re-run the affected section plus section 3.12 (privacy).** Do not release with any failure in 3.12.
8. **Release checklist** (section 4).

Report each failure with: step number, loader, OS, what you expected, what happened, and the relevant lines of `logs/latest.log` (BlockVault never logs secrets, so they are safe to share).

## 2. Out of game

| # | Check | Expected |
|---|---|---|
| 2.1 | `./gradlew --no-daemon test` in `core/` | 90 tests pass |
| 2.2 | `./gradlew --no-daemon build` in `fabric/` and `neoforge/` | Both build; jar under 256 KiB |
| 2.3 | `bash scripts/check-rules.sh` | Exits 0 |
| 2.4 | Open each jar as a zip | Contains `blockvault.mixins.json`, `assets/blockvault/lang/en_us.json`, `icon.png`, patterns and the word list; no stray files |
| 2.5 | Load each jar in a launcher | Mod list shows name, description and icon on both loaders (NeoForge uses `logoFile`) |
| 2.6 | Install the EFF word list (see "Memorable word list" in the README) and rerun 2.1 | Still 90 pass; memorable passwords look like `word-word-word-word-NN` with real words |
| 2.7 | After creating a vault, list the data folder | Only the files named in the README table; nothing BlockVault-related in the game folder |
| 2.8 | POSIX: `ls -l` the data folder | Folder `700`, vault/backup/trust files `600` |
| 2.9 | `grep` the vault file and backups for a saved password | Not found in plaintext |
| 2.10 | `vault.bv.trust` size | Exactly 32 bytes, present only while `Trust this device` is on |
| 2.11 | Flip one byte in a copy of `vault.bv` and import or recover it | Reported as damaged, never crashes |

## 3. In game

Use a throwaway vault. Delete the data folder between big sections if you want a clean start.

### 3.1 Vault creation
1. First open of the vault (keybind or `/blockvault`) shows the create screen.
2. Different passwords in the two boxes: mismatch message, boxes cleared.
3. Fewer than 8 characters: too-short message.
4. Type a capital letter with Caps Lock on (and then lowercase with Shift held): the Caps Lock hint appears and disappears correctly in every password box, including change-master-password and manual add.
5. Valid password: "Creating your vault..." text is shown, then the vault opens. **Watch for any freeze or FPS drop** while it works and report how long and where (create, unlock or change password).
6. Trusted-device notice appears in chat once.

### 3.2 Unlock
1. Wrong password: clear wrong-password message, nothing changes.
2. Restart with `Trust this device` on: vault opens without asking.
3. Switch to `Ask for master password every launch`, restart: it asks, and the trust file is gone.
4. A login prompt arrives while locked and you press Esc on the unlock screen: no login happens, and the screen does **not** come back for repeated prompts on the same connection.
5. Open the unlock screen, then get disconnected or kicked (e.g. AuthMe login timeout): afterwards the keybind and `/blockvault` still open the unlock screen.
6. With the unlock screen open, let a register card arrive: the card waits (see 3.5) and the unlock screen still works afterwards.

### 3.3 Vault list and editing
1. Search, paging, reveal, copy (paste elsewhere to confirm), streamer mode hides server names everywhere including cards, import preview and trash.
2. Edit a password; change a server's template and autofill / never-save policy; verify they persist after restart.
3. **Add** a login by hand: valid host and name saves and appears; bad host, name with spaces, empty password and a duplicate each show the right error; Generate and Show/Hide work; Caps Lock hint works.
4. Join the server and confirm a manually added account autofills.

### 3.4 Trash
1. Delete one account: it disappears from the list and appears in Trash with its name and days left.
2. Delete a whole server (delete screen > "Move the whole server to trash"): all its accounts are in one Trash entry.
3. Restore each: it returns with the same password. Restore an account when a newer login with the same server and name already exists: the newer one is kept.
4. Delete forever with the backup-purge toggle OFF, then inspect a backup: the password is still there (the screen warned you). Repeat with the toggle ON: it is gone from every backup.
5. Empty trash (two-step button).
6. Expiry: delete something, set the system clock 61 days ahead, reopen the vault: the entry is gone. Restore the clock.

### 3.5 Registration (Paper + AuthMe)
1. Fresh join, register prompt: card shows a generated password; confirm; registered; "Login saved".
2. While the register prompt is pending, open your inventory or the chat: the card appears only after you close it. No typed text is lost.
3. Cancel the card: no further register prompts this connection.
4. Kill the game right after confirming: relaunch, the account shows Pending; log in manually once: it becomes verified.
5. Registration failure message (for example register twice): the pending credential is **not** deleted.

### 3.6 Login and capture
1. Rejoin: autofill sends `/login` once; green "Logged in automatically".
2. Change the saved password: rejoin shows the Rejected card with no retry loop; Update works.
3. Type `/login <correct>` yourself with a stale saved password: Update card offered after success.
4. Server using `/l <password>` or `/log <password>` in its prompt is recognised.
5. A second prompt after the first autofill does not send the password again.

### 3.7 Server rules and password changes
Use AuthMe settings or a test plugin to produce each message:
1. "Password must be between 8 and 16 characters", "at least 12 characters", "no more than 10 characters": the retry card respects it, and Regenerate keeps respecting it.
2. "...non-alphanumeric character...": the retry contains a symbol.
3. AuthMe's "Your password is too short or too long!": a conservative 12-character alphanumeric retry is offered, not an error.
4. `/changepassword old new` with a success message: the vault now holds the new password.
5. `/unregister` success: the account moves to the trash.

### 3.8 Import and export
1. Export to a new file; try exporting over an existing file (refused).
2. Import into a second profile: the preview lists new servers, new accounts, newer passwords and kept local passwords; Cancel changes nothing; Import applies and a restore point exists.
3. Wrong password, wrong file and a corrupted file each show their own message.
4. Importing the same file twice is a no-op ("nothing new").
5. A file with an older password for an account you changed recently: preview says your newer password is kept.

### 3.9 Backups and recovery
1. Automatic backups: at most one per day; manual backups up to ten; inspect and reveal inside a backup.
2. Restore a backup, then restore the restore point.
3. Damage `vault.bv`, then use `Recover from backup` with that backup's password: vault returns, `vault.bv.damaged` exists.
4. Change the master password: afterwards the new password opens the vault and every backup, the trust file changed, and the old password opens none of them.
5. Make one backup unreadable first: the change still succeeds and you are told which backup could not be re-encrypted.

### 3.10 Settings and data
1. Every setting persists across restarts. Corrupt `config.json` by hand: BlockVault starts with defaults, moves the bad file to `config.json.bad`, and settings can be saved again.
2. Default generator preset is server-friendly on a fresh install.
3. Open data folder works on Windows, macOS and Linux. If nothing opens, the path is copied and a toast says so; note that as a bug, because it means the Minecraft helper was not found.
4. Erase all data: needs the master password, removes BlockVault's files, leaves any other file you put in the folder.

### 3.11 Multiple instances
1. Two instances on the same data folder: add an account in one, then use the other; it picks up the change.
2. Change the master password in one: the other instance reports an error on its next vault action and recovers after you unlock again.

### 3.12 Privacy (must all pass)
1. After typing `/login`, `/register`, `/changepassword` and `/unregister` commands, `command_history.txt` does not contain them, and no "hooks inactive" warning appeared.
2. `logs/latest.log` after a full session has no passwords, keys or stack traces from BlockVault.
3. Autofill and registration never print the password in chat.
4. No BlockVault file appears in the game folder.

### 3.13 Spoofing and noise
1. A chat message from another player containing "/register" or "wrong password" does not delete anything.
2. A very long system message (use a command block or plugin) does not stall the game.
3. A user pattern in `patterns.json` with catastrophic backtracking (for example `(a+)+$`) does not freeze the game.
4. Action-bar login prompts (a plugin that sends the prompt as an action bar) are handled on both loaders.

### 3.14 Loader parity
Repeat 3.1, 3.5, 3.6 and 3.9 on NeoForge. Also: `/blockvault` and `/blockvault lock` on both; keybinds appear under Controls; NeoForge Config button opens settings; Fabric works with and without Mod Menu.

### 3.15 No interference
Joining a vanilla server, singleplayer and LAN does nothing and logs nothing. Frame rate is unchanged with the mod installed.

### 3.16 Platforms
Windows (data in `%LOCALAPPDATA%\BlockVault`, not Roaming), macOS and Linux: vault creation, unlock, trust file permissions, open-folder.

## 4. Release checklist

- All sections above pass on both loaders, with 3.12 clean.
- Word list installed (and credited if you use EFF's) and 2.6 re-run.
- Gradle wrapper checksum added (see `STATUS.md`).
- Version number, changelog and `STATUS.md` updated; known gaps listed honestly.
- Jars built by CI from the tagged commit, never from a local build.
