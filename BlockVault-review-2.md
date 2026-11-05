# BlockVault review, round 2 (post-Codex build)

Review of `BlockVault-main_1.zip` against the version reviewed in round 1. Findings only; nothing in your project was changed. This file replaces the first review.

## How this was checked

| Evidence level | Meaning |
|---|---|
| **[verified]** | Reproduced by me: I compiled `core` with JDK 21 and ran probes or your own tests. |
| **[code]** | Read from the source, not executed. |
| **[in-game]** | Depends on Minecraft 26.3 behaviour I cannot run. `shared-client`, the mixins and the loader hooks were never compiled against Minecraft here. |

- **Core tests:** I ran your 95 unit tests with JUnit 5 on JDK 21: **95 of 95 pass**. Your project targets Java 25, so this is not an identical environment. The run took about 48 s, because every vault creation calibrates the key derivation.
- **Not run:** Gradle builds (Maven Central is unreachable from my sandbox), CI, both loader builds, and anything involving the Minecraft client. Whether Mod Menu 21.0.0, the wrapper checksum, the pinned action SHAs and the NeoForge metadata change actually build is unconfirmed.
- **Codex's summary vs the code:** most claims match. Four do not (listed in section 3).

## 1. Verdict on the first review

| Item | Status |
|---|---|
| C1 master-password change zeroes passwords | **Fixed [verified]**. My exact scenario (change password, save an unrelated server, reopen from disk) now keeps both passwords. |
| H2 aliases and namespaces | **Fixed [verified]**, but see N5 (new false captures). |
| M1 combined login/register nag loop | **Fixed [verified]**: one lookup for four repeats. |
| M2 silent failure of bare `/login` | **Fixed [verified]**: the rejected card now appears. |
| M3 registration template parsing | **Fixed [verified]** for all five sentences I tested. |
| M4 rule vs failure classification | **Fixed [verified]**: `Invalid password format`, `...too weak`, `...length, 8-16` now classify as rules. |
| M6 erase, custom folder | **Fixed [verified]**: honours a custom data folder, removes `patterns.json` and `blockvault-export-*.bv` in `exports/`, leaves your other files. See N7 for the new passwordless path. |
| S5 file-size bound | **Fixed [verified]**: encode and parse now agree. |
| S5 IPv4 validation | **Fixed [verified]**: `999.999.999.999` and `1.2.3` are rejected. |
| S5 case-insensitive usernames | **Fixed [verified]** for merging; the store can still hold `Steve` and `steve` side by side if saved directly (the Add screen blocks it). |
| M10, M11, M13, M15 | Fixed [code] and covered by tests. Moving the last account to Trash now removes the server entry and restoring recreates it. |
| M7 validation | Done, but **too strict**: see N1 to N4. |
| M8 version vs corruption | Done [code]. One side effect: see N2. |
| M9 recovery message | Fixed [code]. |
| M14 backup purge | **Removed** (not fixed). Deleted passwords now always survive in backups and exports; the trash note says so. Reasonable, but it is a feature removal. |
| M18 Windows retry | Added [code], but may miss the common case (N11). |
| M19 autofill | Now fails closed [verified], at the price of N9. |
| H1 trust default | **Partly done [code]**: a choice appears on the create screen, still defaulting to ON. |
| H3 Up-arrow history | Added [code, in-game], with a new ordering risk (N6). |
| H4 prompt tightening | **Mostly done [verified]**: `<Steve> use /register <password>` and the Discord advert are now ignored. Gaps in N13. |
| M5 settings navigation | Fixed [code]. |
| M16 card fixes | **Partly done**: edits survive a resize and timer chains no longer stack, but the new focus code probably broke the auto-close (N3). |
| M17 join timing | Fixed [code, in-game]. |
| M12 backup slots/management | **Not done** (Codex agrees). |
| S1 secret handling | Partly done. `DESIGN.md` still claims all secrets are wiped, which is not true. |
| S2 KDF | Warm-up and median added; strength meter added but misleading (N14). |
| S4 queued secrets | Fixed [code]. |
| Section 4 dead code | Partly done. Still present: `LoginSession.attempted()`, `PromptMatcher.matchPrompt` (tests only), the one-constant `KeyMode`, and the one-use `Platform`. |
| Section 6 build/CI | Done on paper (named Mod Menu coordinate, wrapper checksum, SHA-pinned actions, optional mixin config). Unbuilt. |

## 2. New bugs and regressions

Ranked by how likely they are to hurt a real user.

### N1. Master passwords are now ASCII-only with no spaces [verified / code]
The unlock, create, change and recovery screens filter input, and `Kdf.pbkdf2HmacSha512` itself now throws for anything outside `!` to `~`.
- **Passphrases are impossible.** `correct horse battery staple` cannot be used, though it is the strongest form a person can actually remember.
- **Silent mutation on paste.** The filter strips disallowed characters as you type or paste. Pasting `my pass phrase` into both create fields silently becomes `mypassphrase`, the two fields match, and the vault is created with a password the owner does not know.
- **Lock-out of existing vaults.** A vault whose master password contains a space or non-ASCII character can no longer be unlocked, recovered or changed. No migration or notice exists.
- **Fix idea:** allow any Unicode and spaces for the master password (the key derivation already normalizes to NFC), and reject with a message instead of stripping. Keep the ASCII rule only for passwords sent in commands.

### N2. One invalid entry makes the whole vault unreadable [verified]
`Account` now throws unless the password is non-empty printable non-space ASCII and the username is `[A-Za-z0-9_]{1,64}`. Probe: a vault with one `gööd pass` password and one fine account fails with `Malformed vault JSON`, which maps to "corrupt".
- This also hits backups, imports and trash, so a single odd entry can make an import or restore fail entirely.
- `ServerEntry` has the same all-or-nothing rule for non-ASCII server names.
- **Fix idea:** validate on write and in the UI, but read tolerantly (keep the entry, flag it, skip it for autofill).

### N3. The "Save login?" card probably never closes [in-game, likely]
The idle timer now calls `passwordBox.setFocused(true)` every 250 ms after 400 ms, and the countdown only runs while no box is focused. Result: the countdown never runs, so the **Card duration** setting does nothing, and outside clicks are now ignored. The capture card therefore blocks the game until you press a button.
- The same line may fight over focus when the owner clicks the email field.
- **Fix idea:** focus once, after the delay, only if nothing else is focused; count the delay separately from the idle countdown.

### N4. File paths are ASCII-only [code]
Import and export path boxes use `AsciiInput.isPrintable`. A Windows profile such as `C:\Users\José\...` cannot be typed or pasted, and import has no file picker. Export works only through the blank-folder default.

### N5. Short aliases capture ordinary commands [verified]
`l`, `log`, `reg`, `unreg` and `cp` are now treated as auth commands on every server.
- Probe: `/l spawn` is captured as a login attempt; a later `Successfully registered your spawn point!` from another plugin produced a **SAVE card offering `spawn` as the password**.
- `/log errors`, `/reg area` and `/unreg zone` were captured too.
- `isSensitive` also drops plain chat that starts with those words (for example `reg` or `l`) from the Up-arrow history, because the recall mixin does not require a leading `/`.
- **Fix idea:** only capture when the server previously prompted for that command, or scope the short aliases per server template.

### N6. History-scrub self-check now depends on call order [in-game]
The old check waited two seconds and tolerated either order. The new one reads a thread-local immediately. If Minecraft calls `sendCommand` before `CommandHistory.addCommand`, the owner gets a false "password hiding is not working" warning on the first sensitive command and a stale flag afterwards.

### N7. Erase without password [code]
- Works on a healthy, unlocked vault, so anyone at the keyboard can wipe it with two clicks and no typed confirmation. Gate it to cases where verification is impossible (missing or unreadable vault).
- The progress text still says "Verifying password and deleting data…", and the settings description still says "after confirming the master password".
- Exports saved to a folder you chose are not erased (only `exports/` inside the data folder). Say so in the warning.

### N8. Missing translation key [verified]
`LoginSession` shows `blockvault.toast.password_invalid`, which does not exist in `en_us.json`, so the toast shows the raw key text.

### N9. A prompt that arrives before settings load is lost [verified]
With autofill failing closed, an early prompt leaves the session `PROMPTED` and does nothing. Autofill only works when the server repeats its prompt. Queue the prompt and replay it once `configure()` runs.

### N10. Reloading patterns resets the live session [code]
`reloadPatterns` calls `onJoin()` mid-connection, which tears down the session and any open card. Replace the pattern pack in place instead.

### N11. Windows replace retry may miss the usual error [code, Windows unverified]
The retry catches only `AccessDeniedException`. A sharing violation from antivirus, sync tools or a second instance is normally a plain `FileSystemException`, which is not retried.

### N12. NeoForge metadata lost `modLoader` and `loaderVersion` [code]
Codex says the 26.3 MDK omits them. If that is wrong, NeoForge will refuse to load the mod. This is the first thing to confirm with an actual launch.

### N13. Prompt filtering still has holes [verified]
- `Use /register to claim rewards` and `Hint: type /login to continue` are still accepted as prompts.
- Only the `<Name>` chat format is rejected. `Steve: please /register <password>` or `[Rank] Steve » ...` are not.

### N14. The strength meter is misleading [verified]
Ratings: `password`, `12345678`, `qwertyuiop`, `P@ssw0rd`, `Abcdefgh1!` all show **Fair**; only a repeated single character shows Weak, and **Strong** needs 20 characters. There is no dictionary or pattern check, and the five-step scale shows four labels. Either make it honest (length first, common-password list) or remove it.

### N15. Claims in Codex's summary that the code does not support
- **"IDN normalization":** non-ASCII hostnames are now simply rejected (`bücher.de` gives no key). Punycode input works.
- **"Extended the timeout to 15 seconds":** `RESULT_TIMEOUT_MILLIS` is still 5 seconds. The new timeout toast appears only on the autofill/passwordless path, not for manual commands.
- **"Erase includes exports":** only `blockvault-export-*.bv` files in the default `exports/` folder.
- **"Aliases scrubbed":** yes, but see N5 for the side effects.

### N16. Smaller regressions and oddities
- Trashing the last account on a server deletes the server entry, so a server where you had turned autofill off comes back with autofill ON when you re-add an account (restoring from Trash keeps the setting).
- `vault.bv.damaged.<timestamp>` copies accumulate with no cap (Erase removes them).
- The calibration now runs five key derivations at vault creation and every password change (about 1.3 s here at the floor count). Cache the result per session, and make the cost injectable so tests run faster.
- Both loaders call `Hooks.onJoin()` directly inside the join event when on the game thread; confirm the server and user are already set at that point.
- `Hooks.onOutgoingCommand` still calls the session in two branches, and still compiles a regex with `String.matches` per command.

## 3. UX, layout and polish

Text widths below use Minecraft's default font metrics, estimated from the language file and the button widths in code. They are accurate to a few pixels, not exact.

### 3.1 Text that does not fit

| Where | Text | Needs | Has |
|---|---|---|---|
| Erase screen, main button (when armed) | `Confirm erase without password` | about 161 px | 140 px button |
| Erase screen, passwordless button | `Vault damaged or missing? Erase without password` | about 255 px | 240 px button |
| Erase warning (passwordless) | `This bypasses password verification...` | about 396 px | single line, no wrapping |
| Export help | `Creates a new encrypted copy. Leave the folder blank...` | about 795 px | single line |
| Backup help | `Automatic snapshots are made during saves...` | about 585 px | single line |
| Import help | `Enter an encrypted BlockVault export...` | about 508 px | single line |
| Trash note | `Items are removed for good after 60 days...` | about 422 px | single line |
| Erase warning 3 | `Close other instances first...` | about 402 px | single line |
| Export success | `Encrypted copy saved: <full path>` | often 600 px or more | single line |

- A 1080p window at the default "Auto" GUI scale is about 480 px wide, so the first three help lines run off both edges. At 854x480 and 427 px, almost all of them do. Wrap them (`font.split`) or shorten them.
- The export success line should show the file name plus a button to copy or open the folder, not the full path.
- **Card buttons at small windows:** widgets scale down but fonts do not. At 1080p Auto the card scale is about 0.8, which makes the three utility buttons about 97 px wide; `Another password` (about 92 px) is clipped there, and more so at 854x480.
- **Hand-rolled clipping:** the card header, the list rows and several labels truncate at 6 px per character. Proportional text makes that cut too early or still overflow. Use `font.substrByWidth` or `StringSplitter`.
- **List row buttons:** at the minimum GUI width (320), `Auto OFF` (about 44 px) and `Template` (about 43 px) exceed the 42 px buttons.
- **Create screen height:** the strength line starts 80 px above centre and the last button ends 80 px below, so it overflows at very large GUI scales.

### 3.2 Overlaps and missing pieces

- **Unlock and create screens:** the status or error text is drawn at the same height as the "Recover from backup" button (unlock: text at +32, button at +34; create: text at +59, button at +60). The message overprints the button's top edge. This predates round 1 but is still there.
- These two screens draw no title, no explanation of what the vault is, and no note about the ASCII, 8-character and trust rules; there are only two placeholder-hinted fields.
- The create screen's trust button shows `Trust this device: ON/OFF` with no explanation of what it means. The explanation exists only in a chat notice shown later, and that notice still says "ON by default" after the owner chose.

### 3.3 Interaction oddities

- **Enter does nothing** on any screen. There is no key handling anywhere; unlock, create, change password, erase, recovery and add all need a mouse click or Tab navigation.
- **Typos wipe both fields.** A mismatch, a too-short password or an invalid character clears the password and the confirmation, forcing the owner to retype a long password. Clear only the confirmation.
- **No list scrolling.** The vault list, trash, backups and import preview are paged with Previous/Next and `Page x of y`; the mouse wheel does nothing. A scrolling list widget is what players expect.
- **Import, Export, Backups and Settings are not reachable from the vault list.** They live only under Mods, then Config. The list's footer is Previous, Next, Add, Trash, Lock, Back.
- **Footer order:** Lock sits next to Back, and Back closes everything instead of returning to the previous screen.
- **Two different "Trash" buttons.** The footer `Trash` opens the trash; each row's `Trash` moves that account there.
- **`Auto ON` / `Auto OFF`** shows the current state as the label, so it reads as an action. A checkbox or a coloured On/Off with the tooltip would be clearer.
- **Rejected-login card buttons** `Manual`, `Update` and `Forget` are cryptic and have no tooltips. Something like `I'll log in manually`, `Save new password` and `Forget this login` reads better.
- **Passphrase paste is silently altered** (N1); the add, edit and card password fields behave the same way.
- **Sensitive actions are one click.** Erase without password has no typed confirmation.
- **No search or sort options** beyond a substring filter; the search box is ASCII-only.
- **Card behaviour:** it opens over gameplay and takes focus after 400 ms, so keystrokes in flight can still land in it. The outside click now does nothing at all.

### 3.4 Wording and consistency

- Terms drift: `Back`, `Cancel`, `Not now`; `Delete`, `Trash`, `Move to trash`, `Delete forever?`; `Reveal`, `Show`, `Edit`.
- Case and punctuation drift: `Auto ON`, `PENDING — not yet confirmed`, `Verified`; some messages end with a period and some do not; `…` and `...` are both used.
- No plurals: `1 new servers`, `1 days left`.
- `blockvault.vault.reveal` is labelled `Edit`.
- `blockvault.backup.entry` shows raw byte counts (`123456 bytes`).
- `blockvault.config.action.backups.description` says "...or remove encrypted vault backups", but there is no way to remove one.
- `blockvault.settings.description.duration` hard-codes "The default is 5 seconds".
- The two hook-failure toasts are long enough to take five or more lines.
- **Dead strings:** `blockvault.data.open`, `blockvault.data.erase`, `blockvault.trash.row.server`, `blockvault.add.empty_password`. Duplicates: `blockvault.vault.page`/`blockvault.backup.page`, and the several master-password length messages.
- English only; there are no other language files.

### 3.5 Questionable design decisions

- ASCII-only everywhere (usernames, hosts, names, paths, search, email, master password) was applied as one blanket rule, and the strictness is enforced in constructors rather than at the edges (N1, N2, N4).
- Trust-this-device still defaults to ON, so the master password mostly protects a copy of `vault.bv` taken without the trust file.
- Removing the backup purge entirely means the vault can never fully forget a deleted password.
- Manual login capture and history scrubbing apply to very short aliases on every server (N5).
- YACL is a hard dependency for the whole mod although only the settings screen uses it.
- Timers poll every 250 to 400 ms where a screen `tick` would do, and every vault operation still bounces I/O thread, game thread, I/O thread to read the config.

## 4. Optimizations (new and remaining)

- `ServerEntry`'s constructor re-validates every host (regex work) on every copy, including each `withAccounts`. Validate once at the boundary.
- `PromptMatcher.isDirectedPrompt` compiles a `Pattern` per alias per candidate message; precompile per pack.
- `Hooks.onOutgoingCommand` and `AsciiInput.isUsernameText`/`isEmailText` use `String.matches` (compile per call).
- `AsciiInputFilter` rebuilds the string by substrings on every keystroke; a code-point loop is cheaper, and a pasted string should be rejected, not re-set.
- Cache the KDF calibration for the session and let tests inject a cheap cost; the suite takes about 48 s.
- `CommandTemplate.builtins()` still rebuilds its map on each call.
- Use real font metrics instead of the 6 px heuristic (3.1).

## 5. Documentation and repository state

- `README.md` line 10 still says deleting for good "can optionally also remove the password from backups" and mentions "older whole-server trash entries".
- `README.md` line 40 describes erase as password-verified only.
- `DECISIONS.md` ("optional backup purge") and `CHANGES.md` ("backup purge on delete-forever") describe removed features.
- `STATUS.md` and `TESTING.md` say 91 tests (now 95), and `TESTING.md` still tells the tester to try the purge toggle and restore whole-server records.
- `DESIGN.md` still says secrets are wiped when no longer needed, and `README.md`/`DESIGN.md` do not mention the ASCII rules beyond one README line.
- `BlockVault code review.md` (the old review) sits in the repository root; delete it before committing.
- Dead code still present: `LoginSession.attempted()`, `PromptMatcher.matchPrompt` (tests only), `KeyMode` (one constant), `Platform`.
- Still open from round 1: `Hooks.onJoin` loads patterns on the game thread, EFF word-list attribution, no `shared-client` tests, the jar-size budget, one Gradle build per module.

## 6. What is done, what needs testing, what needs work

**Done and verified (core, by test or probe):** C1; the login state machine fixes (M1, M2, M3, M4); aliases and namespaces; erase targeting; trash and server cleanup; case-insensitive usernames; file-size bound; IPv4 validation; case-merge; the Account/vault validation (but see N2).

**Done in code, needs a real client:** history scrubbing (Up-arrow suppression and its self-check), join timing on both loaders, card resize and focus behaviour, settings navigation, import paging, export path display, pattern reload, setup trust choice, strength meter display, Mod Menu dependency and NeoForge metadata, CI SHA pins.

**Needs fixing before more features:** N1, N2, N3, N4, N5, N7, N8, N9, then the text fits and overlaps in section 3.

**Not started:** separate restore points and named/deletable manual backups; custom per-server command templates; diagnostics toggle; recent-folder history and a file picker; edit-server screen and server-list names; shared plugin packs (nLogin, LoginSecurity); alias-to-IP and LAN identity; moving pattern loading off the game thread; single-use abstraction cleanup; the secret-handling redesign.

## 7. In-game checklist

1. Both loaders launch (N12), and the mod loads without YACL and Mod Menu problems.
2. Type `/login test`, close chat, reopen it, press Up: nothing recalled. Repeat for `/l test`, `/authme:login test`, then watch for a false "password hiding is not working" warning (N6).
3. On a real AuthMe and an nLogin server: register, log in manually, autofill, wrong-password rejection, `/changepassword`, `/unregister`.
4. Save card: does it ever auto-close (N3)? Click the email field in a registration card with an email prompt.
5. Resize the window with a card open; try GUI scales 2, 3 and 4 at 1080p for text clipping.
6. Create vault: paste a passphrase with spaces (N1); press Enter on each screen.
7. Erase flows, including the long labels.
8. Export and import with a non-ASCII profile path (N4).
9. Change the master password, then add an account and restart: all passwords intact.
10. Windows: save while an antivirus or sync tool is scanning the data folder (N11).

## 8. Suggested order

1. Build both loaders and the core tests; confirm N12.
2. Relax the ASCII rules (N1, N2, N4) and add tolerant reads.
3. Fix the card focus/timer (N3), the missing key (N8) and the short-alias captures (N5).
4. Do the layout pass (3.1, 3.2): wrap help text, fix the overlaps, add Enter-to-submit, stop clearing both fields.
5. Update the docs and delete the old review file.
6. Run the in-game checklist, then decide the open design questions (trust default, backups).
