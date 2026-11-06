# BlockVault review 3

Scope: whole repo (`core`, `shared-client`, `fabric`, `neoforge`, docs, CI). Nothing was coded or changed.

How findings were checked:
- **[probe]** reproduced by running the real `core` classes in a small test harness
- **[code]** read in the source, not executed
- **[in-game]** cannot be settled without launching Minecraft

`core` compiled on JDK 21 and its test suite passed here: 113 tests, 0 failures, about 48 s (the KDF dominates the runtime). The Minecraft-dependent modules (`shared-client`, `fabric`, `neoforge`) cannot be compiled in this environment, so everything in them is **[code]** at best. `STATUS.md` still says 95 tests.


## Bugs

### High

**B1. Bare `/l` and `/log` are swallowed on every server. [code + logic replica]**
In `Hooks.onOutgoingCommand`, any bare single-word command whose name is in `commandNames("login")` is cancelled. That set includes `l` and `log`, because of the short-alias list. The check happens before `LoginSession` decides whether the alias was ever prompted. So:
- `/l` (the lobby command on many networks, Hypixel included) never reaches the server, even with no saved credentials and no login plugin present.
- Bare `/login` is also cancelled when nothing is saved. A server where `/login` means something else (a link or 2FA flow) becomes unusable.

Suggested fix: cancel only when a verified credential exists and the command is a full `login` name, or when the alias was actually prompted. Add a setting to turn passwordless login off.

**B5. NeoForge `neoforge.mods.toml` template has no `modLoader` or `loaderVersion`. [code, needs in-game]**
Older NeoForge versions require both. I cannot confirm whether 26.x relaxed that. If it did not, the NeoForge jar will not load. A one-line launch test settles it.

### Medium

**B2. A password rejected by the server's rules stays saved and is re-offered. [probe]**
`savePending` runs before the register command is sent. When the server answers with a password-rule message, the session offers a fresh, compliant card. If the player dismisses it, the old rule-violating password stays in the vault as PENDING. On the next join the register prompt shows an UPDATE card containing that rejected password instead of generating a new one. Fix: on a rule rejection, replace or remove the pending entry.

**B3. "You're already logged in!" after an autofill is treated as a failure. [probe]**
It lands in the same pattern group as "wrong password", so the state becomes REJECTED and the "server rejected the saved password" card appears. The credential is fine in that case. The card offers Forget, so a tired click deletes a good password. It should count as "already in", not as a rejection.

**B4. PENDING accounts dead-end, and non-English servers never promote. [probe]**
- A PENDING account plus a login prompt does nothing. No autofill, no card, no toast. The state sits at PROMPTED silently.
- If the success message does not match an English pattern (checked with a Spanish message), the session times out after 5 s, the account stays PENDING, and the dead end above applies permanently.
- Together this means the mod only works end to end on English AuthMe-style servers.

Options: allow autofill for PENDING accounts on a login prompt (a wrong guess just fails harmlessly), add a "mark as verified" action, and ship a few more default languages.

**B6. Saving any setting disables trusted unlock for the rest of the session. [code]**
`VaultRuntime.setTrustedDevice()` sets `trustedUnlockAttempted = true` on every settings save. If the player changes any option (card position, say) before the vault has been used, the next login prompt asks for the master password even though "Trust this device" is on. The flag should only be set when trust is turned off or on an explicit lock.

**B7. Post-commit failures in master password change are reported as failure. [code]**
After the new key is committed, `trustThisDevice()` can still throw. The screen then reports an I/O error although the password already changed, the form has been cleared, and the trust file now holds the old key. A retry with the old password fails with "wrong password". Post-commit problems should surface as warnings, and the trust file should be rewritten as part of the commit or reported separately.

**B13. Backups whose re-encryption failed become unreachable while the vault is healthy. [code]**
`restoreBackup` and `inspectBackup` decrypt with the current key. A backup still under the old key reports as damaged. The recovery path that accepts an old password only appears when the main vault is missing or damaged. It should say "made with an older master password" and let the player enter that password.

**B16. Login prompts can be spoofed through chat formatting and whispers. [probe]**
On most servers chat arrives as system messages, so other players' text goes through the matcher. These all matched in the probe:
- `[Steve -> me] Please /login <password>`
- `[Rank] Steve » please /register <password>`
- `Use /register to claim rewards`

Impact is limited: the password is sent only to the connected server, and register still needs a click. But it can trigger autofill at odd times and confuse the result detection. A short listening window after join or world change would fix this and also remove most per-message work (see section 4).

### Low

**B8.** A master password that shrinks under NFC (for example five decomposed accented letters, typed as 10 code points, normalising to 5) passes the UI length check, then throws `IllegalArgumentException`. `VaultRuntime` maps that to the "vault appears damaged" message. **[probe]** More generally, mapping any `IllegalArgumentException` to "damaged" is misleading.

**B10.** `moveReplacing` retries only on `AccessDeniedException`. On Windows a sharing violation surfaces as a plain `FileSystemException`, so the retry would not fire.

**B11.** The "Reload patterns" action calls `onJoin()` when a session exists, which tears down a live session and any open card.

**B12.** The history-scrub self-check reads a flag set by `CommandHistory.addCommand`. If `sendCommand` runs first, the check could raise a false "history not scrubbed" warning. **[in-game]**

**B14.** Server rows with zero accounts cannot be deleted from the UI (`deleteAccount` returns early for `serverOnly`). They only appear via imports, so this is rare.

**B15.** The import preview diff and the actual merge match servers slightly differently (id or shared host versus id first). In unusual cases the preview can describe a different server than the merge touches.

**B17.** Idle save and update cards vanish after 5 s by default. A save prompt for a password the player just typed is easy to miss, especially while the world loads. UPDATE cards in particular should stay until dismissed.

**B18.** `refreshVaultRows` can open the vault list over gameplay if a delayed async result arrives after the player has closed the screen.

---

## 4. Improvements and optimisations

**Security and trust model**
- **Trust this device defaults to ON.** The raw vault key is stored beside the vault, so the master password protects only a copy taken without the trust file. The create screen shows this as a small toggle with no explanation. Make it an explicit first-run choice.
- **KDF target is 250 ms.** Consider 500 ms or more. Calibration also runs five KDFs on every create and is not cached.
- **Server password rejections and `neverSave`:** decide whether `neverSave` is gone for good and drop its remnants.

**Behaviour and UX**
- **No way to alias or merge hosts.** Joining via `play.example.com` and via the raw IP creates two entries, and a second generated password can collide with the first account.
- **Server names show `:25565` and cannot be renamed.** Hide the default port in labels and allow renaming.
- **A verified login is filled once per connection.** Networks that re-authenticate after a backend switch will not be handled.
- **Titles are not drawn on unlock and create screens.** There is no on-screen explanation of what the screen is.
- **Wording:** "code points" in the password-length messages should say "characters". `blockvault.vault.reveal` displays "Edit". The "Tpl" and "Auto ON/OFF" labels are cryptic.
- **Status labels:** `PENDING` uppercase beside `Verified` is inconsistent. `blockvault.import.summary` has no plural handling.
- **Chat notice** says Trust is "ON by default" even when the player just chose it.


**Code health**
- `Hooks.onJoin` and `reloadPatterns` duplicate the pattern-loading code (three copies with the field initialiser).
- `closeBlockVaultScreen` lists 12 screen classes. A shared base class or marker interface is safer.
- `BlockVaultConfig.Settings` has a long positional constructor used in three places.
- `Vault`/`Account` have several overlapping constructors.
- Two sources of truth for trust: the `trustDevice` config flag and the existence of `vault.bv.trust`.
- Timers poll every 250 to 400 ms where `Screen.tick()` would do.
- Open-data-folder uses reflection on `net.minecraft.util.Util`. Now that the build targets named 26.3 classes, call it directly or drop the feature.
- `DataDirs` Windows detection uses `os.contains("win")`. Prefer `startsWith("windows")`.
- Mixin config declares `JAVA_21`; confirm what current Fabric on 26.x expects.
- Update `STATUS.md` (95 versus 113 tests).
- Delete `BlockVault-review-2.md` from the repo.

---

## 5. Over-engineering: candidates to remove

1. **`KeyboardHandlerMixin` and the Caps Lock hint.** The mixin exists only for the keybind and modifier tracking. A normal `KeyMapping` with a per-tick `consumeClick()` and `Screen.hasShiftDown()` avoids a third fragile mixin. The Caps Lock hint is a typing-pattern guess and unreliable.
2. **Small items:** "Reload patterns" button, open-data-folder with clipboard fallback, `serverOnly` rows.
3. **YACL as a hard dependency.** Vault management (import, export, backups, password change, erase) hangs off YACL button options. If YACL breaks on a new Minecraft version, vault management goes with it. Either keep a minimal native settings screen or at least keep these actions reachable elsewhere.

---