# BlockVault code review, round 2

## Read this first

I diffed the new tree against the old one, read all changed code and docs, compiled the `core` module, and ran the real classes against the fixes your `STATUS.md` says are done. Items marked **(reproduced)** were confirmed by running code. Items marked **(code-read)** come from reading only. Items marked **(verify in-game)** depend on Minecraft or loader behaviour I couldn’t test.

Most of the first review’s serious items are genuinely fixed, including the password-wiping bug. But **several things listed as fixed still fail when run**, and the new backup, restore and password-change features add their own problems. The ones to look at first:

1. **Failed-registration rollback does not work** (reproduced). The old password is restored in memory, then merge-on-save puts the pending one back.
2. **Deleting a server-only row does nothing** (reproduced), for the same root cause.
3. **Backups can’t help when the vault file itself is damaged**, which is the main case you want them for.
4. **Password reveal still has a password prompt, but Copy and backup inspection bypass it**, and auto-lock re-opens the vault by itself while Trust is on.
5. **The success pattern matches negations**: “Unsuccessful login!” is classified as success (reproduced).
6. **The memorable preset still throws** whenever a server rule changes its length (reproduced).

---

## 1. What happened to the first review’s findings

**Fixed on paper, still broken when run:**

- Registration rollback (1.1), memorable preset (1.3), success-versus-rule classification (1.4), prompt precedence (1.5), registration card self-dismiss (1.6).

**Accepted or deferred by you (no action needed beyond documenting):**

- Trust is on by default; no Windows ACL; All Rights Reserved licence; no Gradle wrapper or SHA-pinning; clipboard never cleared; duplicate English locale files; no IDN hosts.

---

## 2. New and remaining high-severity findings

**1.1 \[High\] Merge-on-save cannot express a rollback or a removal (reproduced, two cases).** `VaultStore.save` always merges the in-memory vault with what is on disk, and `VaultMerger` keeps whichever account has the newer `modifiedAt` and never drops a server. So any edit that lowers a timestamp or removes a record is undone.

- **Case A, registration rollback.** `savePending` stores the old verified password as `blockvaultPrevious` and writes a PENDING account with `modified = now`. When registration fails, `delete()` rebuilds the old account with `previous.modified` (older). I ran the sequence: after the rollback save, the stored password was the new PENDING one, not the old verified one. The fix described in `DECISIONS.md` does nothing.
- **Case B, server-only delete.** `deleteAccount` for a server-only row saves the vault without that server. I ran it: after the save, the server count was still 1. The Delete button on those rows is a no-op, which is the very row type you added to fix the earlier crash.

Fix idea: give removals and restores a newer timestamp (rollback should write `modified = now`), add server tombstones, or let explicit local edits write without merging when the file hasn’t changed since reload (compare a version or hash).

**1.2 \[High\] Backups don’t work when the vault file is the thing that’s damaged.** Restore, inspect and list all require `store != null && isUnlocked()`. If `vault.bv` is corrupt, `ensureStore()` throws and `store` stays null, so the recovery screen shows an empty list. `VaultStore.open` also wraps *any* exception, including a plain I/O error, as `VaultCorruptException` (reproduced: an unreadable `vault.bv` is reported as damaged). If the file is deleted, the app offers to create a brand new vault with a new key, and the old backups (encrypted under the old key) can no longer be decrypted. So backups only cover logical mistakes, not file loss or corruption.

Fix idea: a recovery path that works from a damaged or missing `vault.bv` by validating a backup file directly with the master password, and report I/O errors as I/O errors.

**1.3 \[High\] The memorable preset still throws (reproduced).** The `adjustFor` change only covers the alphanumeric branch and the case where the adjusted length stays at 20. A hint of “at least 24” or “at most 12” still ends in `IllegalArgumentException: Enabled character class is empty`. `Hooks.guard` now catches it, but its response is to disconnect the whole login session, so registration quietly stops for that connection.

Fix idea: stop identifying the preset by a sentinel `symbolSet`; give the policy an explicit “memorable” flag.

**1.4 \[High\] The success pattern matches negations (reproduced).** Success now wins over failure, but the success patterns have no word boundary or negation check:

- “Unsuccessful login!” → SUCCESS
- “You were not successfully registered.” → SUCCESS

A wrong password could be saved as VERIFIED, a PENDING account promoted, or “Logged in automatically” printed on a failed login. Separately, “Password contains unallowed characters!” classifies as NONE, so that server’s rule error is missed (result is a PENDING account holding a rejected password; verify the real AuthMe strings).

Fix idea: require word boundaries and a negative look-behind for `un` and `not` on the success patterns; add a test fed with realistic failure messages.

**1.5 \[High\] Prompt precedence is now wrong in the other direction (reproduced).** The matcher now checks REGISTER before LOGIN, so a message that mentions both (“Use /login … or /register …”) returns REGISTER. A returning player with a saved verified account gets a register or update card instead of autofill. The fix is to decide by vault state: a verified account for this host means LOGIN; otherwise REGISTER.

**1.6 \[High\] The registration card can still dismiss itself, and Cancel is not sticky.**

- The auto-dismiss timer and click-outside close were disabled only for the REGISTER and REJECTED variants. The UPDATE variant used for PENDING recovery behaves like a registration card (state is PROMPTED) but still times out after 10 s and closes on an outside click.
- Cancel now suppresses the same prompt for only 5 s. Many login plugins repeat the prompt every few seconds, so a user who declines gets the card again repeatedly. The registration card has no “Never save here” button (it shows Regenerate instead).
- After a failed registration, `attempted` is reset and the state is REJECTED, so a persistent server error (for example an IP limit) re-opens the card on every repeated prompt.

**1.7 \[High\] Reveal is gated, but Copy and backup inspection are not.** The plan and README say reveal requires re-entering the master password. In practice:

- The vault list’s Copy button calls `copyPassword` with no re-authentication.
- Backup details has a Reveal button on every account with no re-authentication.
- With Trust on (the default), the vault list opens with no password at all.

So anyone at an unlocked computer can click Copy and paste into chat, or open Settings → Backups. Either gate all three behind the same prompt or drop the pretence in the docs.

**1.8 \[High\] Auto-lock is cosmetic with Trust on, and doesn’t cover the new screens.**

- After an auto-lock, `trustedUnlockAttempted` is reset to false, so the next operation (even opening the vault list) silently re-unlocks from the key file. Manual lock keeps it locked, so the two behave differently.
- `activity()` and the auto-lock close handler only know six screens. `VaultRecoveryScreen`, `VaultBackupDetailScreen` and `VaultMasterPasswordScreen` aren’t included, so the timer isn’t reset while you use them, and when it fires the screen (with any revealed passwords) stays open on a now-locked vault.

**1.9 \[High\] Backup, restore and password change fail silently when the vault is locked.** `listBackups`, `createManualBackup`, `inspectBackup`, `restoreBackup` and `changeMasterPassword` check `isUnlocked()` directly instead of going through `withVault`. They never try the trusted unlock and never show the unlock prompt. On a fresh launch with Trust on, Settings → Backups shows “no backups” and Change master password fails with a generic error (code-read; verify in-game). The empty list is the worse failure, because it looks like backups don’t exist.

---

## 3. Medium

**2.1 First-day backup hole (reproduced).** Five saves on a new vault produced one automatic backup, taken before the first account existed (0 accounts). Nothing new is backed up for 24 hours, so the first day’s registrations rely on manual backups.

**2.2 Old passwords live on in backups.** Changing the master password does not re-wrap or remove existing backups, so all 3 + 10 files stay decryptable with the old password. Manual backups only rotate when you create another one, so deleted accounts and old passwords can persist indefinitely. The README mentions 30-day tombstones but not this. There is no per-backup delete.

**2.3 Tombstones keep plaintext previous passwords.** Deleting a PENDING account from the UI keeps its `blockvaultPrevious` (the old password) in the tombstone for 30 days.

**2.4 Template handling is fragile and not migrated.**

- Existing vaults store template `authme` on every server (the old default). `find()` treats anything other than `auto` as explicit, so those servers stay pinned to the default and prompt detection never applies. There is no migration (code-read).
- The “explicit authme” choice is stored as the magic string `authme-explicit`, which exists nowhere in the plan. A nullable or boolean field would be cleaner.

**2.5 Malformed `patterns.json` can still disable detection (reproduced).** Only parse errors are caught. A file like `{"prompt": 5}` or `{"result": {"success": [1, {}]}}` throws `ClassCastException` or `UnsupportedOperationException` from `PatternPack.load`. `Hooks.onJoin` catches it and turns detection off, and since `patterns` stays null it repeats on every join. The file is also read on the game thread at join. User-supplied regexes are unbounded, so a bad one could stall chat handling.

**2.6 The vault search box probably loses focus on every keystroke (verify in-game).** The responder now calls `rebuildWidgets()` for each character. Vanilla `rebuildWidgets()` clears focus and recreates the widget, so typing may need a click per letter.

**2.7 `reload()` before every operation.** It decrypts and parses the whole vault each time, takes no file lock, and a failure turns the action into a generic error. On Windows, replacing the file while another process reads it can fail.

**2.8 Config read failures delete the trust file.** A transient read error now disables trust (good), but the next unlock then calls `forgetTrustedDevice()` and deletes the key file. `loaded` is also set true even on failure, so there is no retry before restart.

**2.9 Trust notice has delivery problems.** It is a chat message shown only at vault creation. If the vault is created outside a world (main menu), joining clears chat and the user never sees it. It points to “Mods > BlockVault > Config”, which on Fabric needs Mod Menu (declared as optional).

**2.10 No guaranteed way to open the vault on Fabric.** Both keybinds default to unbound, the multiplayer-screen button from the plan isn’t implemented, and Mod Menu is optional. Without Mod Menu or a user-set key there is no entry point.

**2.11 The commands-packet mixin is now mostly useless and slightly harmful.** `onCommandTree` only sets `canRegister = register`. That is already set from the prompt message, and the tree can overwrite it with false when a server doesn’t list `register` (for example a plugin intercepting messages). The mixin, and one of your three mixin slots, buy almost nothing.

**2.12 Export failure deletes whatever it hit (reproduced).** `exportEncrypted` catches `IOException` and runs `deleteIfExists(target)`. If `CREATE_NEW` fails because something already exists there (for example a dangling symlink), the pre-existing entry is deleted. The cleanup should only run if this call created the file.

**2.13 Async callbacks are still unguarded.** `Hooks.guard` covers the synchronous entry points, but `find`, `savePending` and the other callbacks run later on the game thread. An exception there is logged by Minecraft with a stack trace (README check 12 says none expected) and can leave the session stuck in PROMPTED.

**2.14 Disconnect handling may hide the disconnect screen (verify in-game).** `McSessionEnv.disconnect()` calls `setScreenAndShow(null)` if a card is open. Depending on event order this can replace the kick or disconnect screen. Replacing a screen also calls `removed()` rather than `onClose()`, so the card’s wipe and `closed` callback don’t run.

**2.15 Recovery screen goes stale after a restore.** The parent screen doesn’t reload its list, and the restore rotates the slots. Stale entries are caught by the modification-time check (good) but just show “failed”.

**2.16 Master-password change.** There is no minimum length, and a new password equal to the old one is accepted. Failure is one generic message (wrong current password, locked vault and I/O error are indistinguishable). Re-saving the trust key afterwards is pointless because the vault key doesn’t change.

**2.18 Shared vault plus discarded unknown fields.** Different game instances share one data directory. An older mod build saving the vault strips fields a newer build wrote (for example `blockvaultPrevious`) as long as the schema version is still 1.

---

## 4. Low and polish

- **Locale files.** All eight are still byte-identical English (same checksum). Minecraft falls back to `en_us` for missing files anyway, so the stubs add nothing; either translate or drop them from the plan.
- **0 ms autofill delay** is now selectable and may send before the server is ready.
- **Memorable preset** is about 46.6 bits (first review); the README now says so, which is fair.
- **Backup labels** use creation time of the backup file, not of the contents, which can confuse (a “today” backup holds yesterday’s state).
- **Settings screen** still reuses a vanilla layout through internal fields (`list`, `layout`), which may break on updates.
- **`VaultRuntime` import and error handling:** import still returns only success or failure (wrong password, bad path and damaged file look the same).
- **Naming.** `BlockVault code review.md` has spaces in the name and sits in the repo root; consider `docs/`.

---

## 5. Dead, unused or redundant code

Still present from round one: `Platform.gameDir()`, `isDevEnvironment()`, `BlockVault.platform()`, `BlockVaultInfo`, `CardVariant.UNLOCK`, `entropyBits` (tests only), single-value `KeyMode`, `kdfId` and `reserved`, `PromptMatcher.KEYWORDS`, identical `nlogin` and `loginsecurity` templates, the nine `Settings.withX` methods, the import temp-directory dance, the `PromptModel` password on REJECTED cards.

New or newly dead:

- **`VaultStore.backupExists(int)`** and **`restoreBackup(int)`**, plus the three-argument `BackupSummary` constructor, are only for tests.
- **`VaultRuntime.requiresMasterPassword`** always returns true (single key mode), so its false branch is dead.
- **`ClientPacketListenerMixin` handleCommands injection** (see 2.11).
- **`SessionEnv.chatMessage`** has a no-op default that hides a missing implementation.
- **`changeMasterPassword`’s trust-key refresh** does nothing useful.
- **`authme-explicit`** and the two-meaning `auto` template ids (see 2.4).

---

## 6. Review of the updated plan

**What improved.** Removing authenticator-code support, `l`/`log` aliases, command-tree arming and `disableAutoLogin` makes the spec much tighter. The new bounds on KDF iterations, no-overwrite export and tombstone/backup descriptions are clear. Gating M6 on the first review’s findings is a good rule.

**Plan versus code drift:**

- The plan’s vault screen still lists a **multiplayer-screen button**; the code omits it (see 2.10).
- The plan doesn’t mention the **`auto` / `authme-explicit` template ids**, **`reload()` before every operation**, or the **`blockvaultPrevious` rollback mechanism** beyond a schema line.
- Plan section 2.2 says the vault is read “only when a login prompt is detected or the vault screen is opened”; the code now decrypts it on every operation.

**Contradictions inside the plan:**

- “Reveal requires re-confirming the master password” versus backup inspection that decrypts and reveals passwords with no check, and a Copy button with no check (1.7).
- “Never overwrite a vault file that fails authentication” versus a restore that replaces the file (fine in intent, but unspecified for a damaged main file).
- Autofill “at most one attempt per connection” versus code that resets `attempted` after registration and send failures.

**Gaps worth adding to the plan:**

- **Merge semantics for removals.** The plan says merge by host and modification time but never says how a deleted server, removed host or rolled-back account survives a merge. This is the root cause of 1.1.
- **Recovery from a damaged main vault file**, and what happens to backups after a master-password change.
- **A backup threat model**: manual backups never expire, and tombstones and previous passwords persist.
- **A migration rule** for existing vaults (template ids) now that forward compatibility was removed.
- **Which screens require re-authentication** (list copy, backup inspection, settings).
- **A default way to open the vault** on Fabric without Mod Menu.
- **Regression tests named in the milestone**: a test that fails on 1.1 (rollback and server delete through `VaultStore.save`) would have caught the two reproduced bugs; add one for “Unsuccessful login”, one for memorable plus length hints, and one for malformed `patterns.json`.

**Process notes:**

- The plan tells the agent not to stop and ask, and to treat CI as truth. `STATUS.md` shows the last CI run had three failing tests and nothing from this round has been built. Consider not accepting M6 as DONE until a green run and the in-game list are complete on both loaders.
- The plan requires the English-duplicate locale files; I’d remove that requirement.

---

## 7. Tests

There are 52 core tests now, including backup rotation, restore, master-password change and trust-file cases. The gaps line up with the findings above:

- No test saves a vault through `VaultStore.save` after a rollback or server removal, which is why 1.1 passed review.
- No realistic-message tests for success, failure and rule classification (1.4, 1.5).
- No test for memorable plus a length hint (1.3), malformed `patterns.json` (2.5), or export onto an existing or dangling path (2.12).
- `shared-client` still has no tests, so reauthentication, locking and queueing logic (1.7–1.9) is untested; extract the non-Minecraft parts into `core`.
- CI, Gradle files and `check-rules.sh` are unchanged from the first version, so the earlier notes on the missing wrapper and unpinned actions still apply.

---

## 8. What I couldn’t check

- Anything that depends on Minecraft 26.3 or loader behaviour: screen lifecycle, event ordering, `rebuildWidgets()` focus handling, the disconnect screen, and chat visibility at vault creation.
- Real server message formats (AuthMe, nLogin, LoginSecurity) for the success, failure and rule patterns.
- Whether the imported-server host merge behaves as intended end to end (I read it but did not run it).
- The three CI failures you mentioned; I only have the code as shipped.

I can share the small harness used to reproduce 1.1–1.5, 2.1, 2.5 and 2.12 so you can turn them into regression tests.
