# BlockVault code review

Review of `BlockVault-main.zip` (Fabric and NeoForge, Minecraft 26.3), 24 October 2026. Findings only; nothing has been changed.

## Read this first

I read every source file, the build files, CI and the docs. I also compiled the `core` module and ran the real classes against the bugs I suspected. Items marked **(reproduced)** were confirmed that way. I had no Minecraft 26.3 jars, so anything that depends on Minecraft or loader behaviour is read-only analysis and marked **(verify in-game)** where I’m unsure. `STATUS.md` itself says nothing has been tested in-game.

The five things I would fix before anyone trusts this with real passwords:

1. **A saved password gets overwritten with NUL characters after the first autofill login** (1.1).
2. **A normal “registered” message can be classified as a password-rule error, which deletes the pending account** and locks you out of a server you just registered on (1.2, 1.3).
3. **“Trust this device” is on by default**, so the vault key sits in plaintext next to the vault, and a corrupt config silently turns it back on (1.6).
4. **Import creates duplicate servers, export can overwrite any file** (1.7, 1.8).
5. **No exception boundary around chat hooks**, so one bad input can throw into the game’s packet handling (1.5).

### What looks solid

- AES-256-GCM with the header authenticated as AAD, and a random vault key wrapped by a PBKDF2-derived key.
- Strict bounded parsing of the file format, fsync plus atomic rename on save, and no networking anywhere.
- Generation and revision tokens in `LoginSession` that discard stale async callbacks.
- Rejection sampling in the password generator (no modulo bias).
- The scrub-from-command-history idea, and keeping secrets as `char[]` where possible.

---

## 1. Critical and high

**1.1 \[Critical\] `VaultRuntime.find()` wipes the live vault’s password (reproduced).** In `find()`, the ` finally {  ``accoun``t.clearPassword(); }` runs on the same `Account` object that lives inside `store.vault()` (the model records copy lists shallowly, so accounts are shared). After the first autofill lookup the in-memory password is all NULs.

What happens next:

- The first autofill still works, because `LoginSession` clones the password before the wipe.
- On login success, `markVerified` calls `old.password()` (now NULs) and saves.
- `VaultMerger` prefers the in-memory copy on equal timestamps (`>=`), so *any* later save, not just this one, writes the NULs to disk.
- On the next join the command renders as ` login  ` with an empty password, because `safe()` strips control characters. Reveal and Copy in the vault also show a blank password.

I ran this against the real `VaultStore`: after the sequence, the password re-read from disk was 14 NULs. This is README manual check #2, so you would likely hit it on your second rejoin.

Fix idea: give callbacks a defensive copy of the `Account` and wipe only the copy. `VaultStore.lock()` zeroes the same shared objects, so an auto-lock racing with a queued callback has the same problem.

**1.2 \[Critical\] PASSWORD\_RULE patterns are too broad and win over SUCCESS (reproduced).** `ResultMatcher.classify` checks PASSWORD\_RULE first, and the default pattern includes `not\s+allowed`, `at\s+least\s+\d+` and `maximum\s+of\s+\d+`. I tested these:

- “Successfully registered! You can change it again after at least 5 minutes.” → PASSWORD\_RULE
- “Successful login! You are not allowed to move until the lobby loads.” → PASSWORD\_RULE

In `onResult`, PASSWORD\_RULE during a registration calls `vault().delete()`. So a server that registered you but sent a chatty success message leaves you with no saved password.

Fix idea: scope the rule patterns tightly, check SUCCESS before PASSWORD\_RULE, and never delete a PENDING account on an ambiguous message.

**1.3 \[High\] Registration failure deletes a previously verified password.** `savePending` overwrites an existing VERIFIED account with the new PENDING password immediately. If registration then fails (or is misclassified, see 1.2), `delete` tombstones the account, so the old working password is gone with no way back.

Fix idea: keep the previous password in a `previous` field until the new one is confirmed.

**1.4 \[High\] The prompt-derived command template is overwritten by the saved default (reproduced).** `LoginSession.arm()` sets the template from the registration prompt, then the `find` callback does `if (saved != null && saved.template() != null) template = saved.template()`. `SavedCredential` never returns null for the template (it defaults to `authme`), so the detected template is discarded whenever any server entry exists.

Test: for `/register <password>` with no server entry the command sent is `register unique-pass`; with an entry present it is `register unique-pass unique-pass`. That breaks one-argument servers on any retry, or on any server you have touched before (for example after “Never save here”).

Fix idea: store “auto” or null unless the user chose a template, and only override when explicitly set.

**1.5 \[High\] `memorable` preset plus a server rule hint throws (reproduced), and there is no exception boundary.** The memorable preset is a sentinel (`symbols=true`, `symbolSet="\u0000"`) detected with `equals`. `adjustFor` changes the length, so it stops equalling the sentinel, and `generate()` throws `IllegalArgumentException: Enabled character class is empty`. That happens inside `onResult` during a chat event.

More broadly, `Hooks.onSystemMessage`, `onCommandTree` and `onOutgoingCommand` have no try/catch, and `LoginSession` can throw in several places (`adjustFor` on conflicting min/max, `render()`, the generator). Exceptions escaping a chat or packet event may disconnect the client (verify in-game). Wrap every entry point in a catch-all that logs the class name only and resets the session.

**1.6 \[High\] “Trust this device” defaults to ON, and fails open.**

- The random vault key is written in plaintext to `vault.bv.trust`, next to `vault.bv` and its backups. Anyone who can read the folder (malware, a synced or backed-up AppData or Application Support folder, a copied profile) opens the vault with no password. A password manager should not opt users into that silently on first vault creation.
- If `config.json` is corrupt or unreadable, `BlockVaultConfig` falls back to defaults, which means `trustDevice=true`. A user who chose “ask every launch” gets the key file re-created on the next unlock. Config writes are also non-atomic (`Files.writeString`), so a crash mid-write can cause exactly this.
- The README says the key file has “owner-only permissions”. On Windows `setPermissions` silently does nothing (the `UnsupportedOperationException` is swallowed) and no ACL is set, so that claim is inaccurate there.
- Toggling the setting has no confirmation dialog.

Fix idea: default to asking, make trust an explicit choice on the create-vault screen, fail closed on config errors, and set an owner-only ACL on Windows.

**1.7 \[High\] `exportEncrypted` overwrites any existing file (reproduced).** The doc comment says the destination must not exist, but `Files.move(ATOMIC_MOVE)` replaces an existing target. The only guard is `target == vault.bv`. I exported onto an existing text file (replaced) and onto `vault.bv.trust` (it became a 142-byte vault copy; trusted unlock then silently breaks). The path is free-typed text resolved against the process working directory, so a relative path lands somewhere unpredictable and the UI never says where.

Fix idea: refuse existing targets (`CREATE_NEW`), reject the data directory’s own files, and show the resolved absolute path on success.

**1.8 \[High\] Import and merge duplicate servers (reproduced).** Server ids are random UUIDs created per device (`UUID.randomUUID()`), and `VaultMerger` merges by id. Importing a vault that has the same host produces two entries (test: 2 servers, same host). `findServer` returns only the first match, so accounts in the second entry are unreachable and the list shows duplicate rows. This defeats the point of import.

Fix idea: merge by normalised host set, or derive the id from the host.

**1.9 \[High\] KDF iteration count is unbounded on parse (reproduced).** `VaultFile.Header` accepts anything above 0 (I constructed one with `Integer.MAX_VALUE`). On this machine 210k iterations took about 0.7 s, so a crafted import file means roughly two hours of CPU on the single background thread. That thread also runs autofill timers, card countdowns and every vault operation, so the game’s login handling stalls too. A tiny value (for example 1) is also accepted.

Fix idea: enforce a sane range (for example 100k to 10M) in `VaultFile.parse` and `Header`.

**1.10 \[High\] Neither “Never save here” nor server policy can be undone from the UI.** `setNeverSave` creates a server entry with no accounts. `showVaultRows` only lists accounts, so that server never appears in the vault list, and the per-server toggles only exist on account rows. After “Never save here”, BlockVault stays silent for that server permanently (`saved.neverSave()` returns early in the register path). The same happens to a server’s settings once its last account is deleted.

Fix idea: list servers, not just accounts, and add a server-level settings screen.

**1.11 \[High\] The registration card dismisses itself and the dismissal sticks.** The card closes after `cardDurationSeconds` (default 10 s) and on any click outside it. Because it is a real `Screen`, mouse capture is lost and a stray click while playing dismisses it. `dismissPrompt()` sets `dismissedPromptKind = REGISTER`, which suppresses every later register prompt for the session. Many AuthMe setups kick unregistered players after roughly 30 s (verify against your test server), so a missed card can mean reconnecting.

Fix idea: no auto-dismiss for REGISTER and REJECTED cards, and make dismissal temporary.

---

## 2. Medium

**2.1 Auto-lock is wired to every key press.** `KeyboardHandlerMixin` calls `VaultRuntime.activity()` on every key press anywhere in the game. Each call cancels and reschedules a task. `ScheduledThreadPoolExecutor` keeps cancelled tasks queued until their delay expires unless `setRemoveOnCancelPolicy(true)` is set, so tasks pile up. The timer also starts when the vault was never unlocked, and then shows a “Vault locked” toast after every idle period. It also means the vault effectively never locks while you play. Make “activity” mean vault UI interaction only.

**2.2 Trusted unlock is attempted once per launch.** `trustedUnlockAttempted` is never reset. After an auto-lock (or a transient read failure) the user gets a master-password screen mid-game even with trust on, which contradicts the setting’s name.

**2.3 Keybinds fire while typing.** The keybinds dispatch from a `keyPress` mixin regardless of the open screen, so a bound letter opens the vault while you type in chat, an anvil box, or this mod’s own password fields. Vanilla keybinds only fire with no screen open. Both default to unbound, so this only bites once someone binds them.

**2.4 Backups are close to useless and there is no restore.** `rotateBackups()` runs on every save, including every autofill login and every per-server toggle click, so three saves replace all three backups with near-identical recent states. No code reads `vault.bv.bak*`, and the README doesn’t say how to restore. A corrupt `vault.bv` surfaces as a wrong-password message. Fix idea: time-based backups (for example one per day, keep N), a restore path, and distinct error states.

**2.5 Errors are mislabelled.**

- Any create or unlock failure (corrupt file, unsupported version, I/O) shows “unlock failed”, and a failed *create* shows the “passwords do not match” text.
- `withVault` logs “could not open its local vault” for any exception thrown by the action, including failed saves.
- Logging drops the exception entirely; the exception class name alone would help without leaking secrets.

**2.6 The `WAITING` queue is unbounded and never cleared.** Actions queued while the vault is locked survive pressing ESC on the unlock screen, disconnecting, and moving on. They run at some later unlock, potentially long after. The drain loop has no per-action catch, so one exception aborts it and the rest stay stuck. The `failed` callbacks of queued actions are never invoked.

**2.7 `reauthenticateForReveal` locks the vault first.** It calls `store.lock()` then `unlock(copy)`. A wrong password leaves the vault locked and discards the in-memory state. It also costs a full PBKDF2 each time. Verify the password without locking.

**2.8 Vault list search only filters the current page.** `updateVisibleRows` only toggles buttons for `pageRows()`, and paging is computed before filtering. Searching for something on page 2 from page 1 shows nothing. Hidden rows leave gaps. In streamer mode the search still matches the hidden host, which leaks it.

**2.9 Every toggle and delete resets the list.** `updateServerPolicy`, `updateServerTemplate` and delete all call `openVaultScreen()`, which builds a new screen on page 0. Each click also does a full re-encrypt plus a backup rotation (see 2.4).

**2.10 Settings inconsistencies.**

- The delay option cycles through 0, but `BlockVaultConfig.read` clamps to a minimum of 400, so 0 doesn’t survive a restart.
- Three overlapping controls decide autofill: `autofill`, `disableAutoLogin`, and the per-server switch.
- `generator` and `cardPosition` strings are not validated, so a bad value shows a raw translation key.

**2.11 Command aliases `l` and `log` are too greedy.** `l` is a very common lobby command. If you have saved credentials for a host and its command tree contains `/l` or `/log`, `onCommandTree` arms autofill and sends `login <password>` to a server that may not use a login plugin. The same aliases make `SensitiveCommands` hide `/l` and `/log` from command history, and make `onOutgoingCommand` treat their first argument as a password candidate.

**2.12 Prompt precedence and ordering.**

- `matchPrompt` checks LOGIN before REGISTER (reproduced). A message that mentions both returns LOGIN, so a new player may never see the register card.
- `onCommandTree` sets `canRegister = !login && register`, which is false on servers exposing both commands. If the tree arrives *after* the register prompt, it re-arms as LOGIN with `canRegister=false`, and the Register button silently does nothing.

**2.13 Silent failure paths.** REJECTED and UNKNOWN end states, a missing rule hint, and failed registrations often show nothing. After a failed registration `attempted` stays true, so later register prompts are ignored until you reconnect.

**2.14 `send()` is not exception safe.** `template.render()` can throw after `attempted = true` and `state = SENT` are set. The session is then stuck, no timeout is scheduled, and `pending` is not wiped.

**2.15 Template substitution re-scans its own output (reproduced).** `render()` chains `.replace()` calls, so a password containing `{username}` becomes the username (`pa{username}ss` → `login paStevess`). Use a single pass.

**2.16 Email field.** When required, it accepts blank or malformed values, and spaces add extra command arguments. The email is not stored anywhere, so you can’t look it up later.

**2.17 Card password box truncates at 64 characters.** `setMaxLength(64)` silently truncates a longer captured password before Save writes it.


**2.18 History scrubbing gaps.**

- `SensitiveCommands.NAMES` is hardcoded instead of derived from the pattern pack, so custom aliases leak.
- It doesn’t cover namespaced forms such as `/authme:login`.
- The chat’s in-memory recent-message list (the up-arrow history) may still hold the command; verify in-game.
- `MixinHealth` produces false warnings: the command and history hooks legitimately haven’t run within 5 s of join, and it only checks once per launch.
- All mixins use `require = 0`, so if the history mixin fails after a Minecraft update the user gets no visible warning and passwords go into `command_history.txt`.

**2.20 Screens are shown and closed unconditionally.** `setScreenAndShow` interrupts typing and gameplay, and a successful unlock calls `setScreenAndShow(null)`, which closes whatever screen the user is on. `McSessionEnv.showing` is only reset via the card’s `onClose`, so a replaced screen could leave it stuck and block later prompts.

**2.21 Multiple game instances see stale data.** The vault lives outside the game folder and is shared. Saves merge against disk, but reads (autofill) use the in-memory snapshot and never reload.

**2.22 Merge semantics.**

- Server settings (`neverSave`, `autofill`, template) always come from the “current” side, not by timestamp.
- Hosts only ever union, so they can never be removed.
- Tombstones keep username and timestamps for 30 days, which contradicts the README’s “removes it”.
- Merging relies on wall-clock `modifiedAt`.

**2.23 `VaultJson.read` is brittle.** A missing field or an unknown `status` value throws and makes the entire vault unreadable, despite the “preserves unknown fields” goal. There is no check on `v`.

**2.24 Single background thread for everything.** PBKDF2 (about 0.25 to 0.7 s), file I/O, autofill timers and the card countdown all share one thread. `BlockVaultConfig.load` adds two extra thread hops to every vault operation.

**2.25 Generator notes.** The memorable preset is about 46.6 bits (reproduced): four words from a 1,024-entry syllable list plus two digits. That’s fine against a rate-limited login but weak if a server leaks fast-hashed passwords. The length setting is ignored for it, and the word list is re-read from the jar on every generation.

**2.26 `HostKey` limits.** No IDN or punycode handling (those hosts are rejected and the feature turns off silently), and LAN worlds with random ports get a new key every time.

**2.27 `changeMasterPassword` has no caller or UI.** Users can’t change their master password at all. If it is ever wired up: it updates in-memory `header` and `wrappedKey` before `save` succeeds, backups stay protected by the old password, and the vault key is not rotated.

---

## 3. Low and polish

- **Fake localisation.** All eight lang files (`de_de`, `es_es`, `fr_fr`, `pt_br`, `ru_ru`, `tr_tr`, `zh_cn`, `en_us`) are byte-identical English (same md5, reproduced). Just use english. There are no plans for other languages in the future.
- **Template screen.** `Map.of` makes button order differ between launches, and the current selection isn’t marked.
- **Setting labels.** Boolean values go straight into `Component.translatable(key, value)`, so they likely render as “true”/“false” (verify).
- **Auto-dismiss timer** in `CredentialConfirmScreen` hops threads twice (`schedule` already returns on the game thread).
- **Misc code hygiene.** `VaultListScreen` has an unused local (`left`) and three separate page-size computations. `MasterPasswordKek.toString` lacks `@Override`, `unwrap` throws bare `Exception`, and a wrong blob length throws `IllegalArgumentException` instead of an auth failure. `AesGcm` has an unused `Arrays` import. `Account` is a record with array components, so `equals`/`hashCode` are identity-based.
- **Atomic move handling** is inconsistent: only `exportEncrypted` handles `AtomicMoveNotSupportedException`. There is no retry for Windows antivirus or sharing violations.
- **File creation order.** `trustThisDevice` creates the file and then chmods it (the key is written after, so the window is harmless). Prefer creating with `PosixFilePermissions.asFileAttribute`, and check file ownership.
- **`blockvault.dataDir`** override: `setPermissions(directory)` chmods whatever directory that property points at.
- **Spoofed prompts.** Servers, or plugins that relay player chat as system messages, can trigger the card or autofill by sending text that matches `/login`. Impact is limited because credentials only go to that host, but it’s worth a README line.
- **R****emo****ve clip****board c****leari****n****g ****from** **.m****d files****.**

---

## 4. Dead, unused or redundant code

- **User pattern file is never loaded.** `Hooks.onJoin` calls `PatternPack.load(defaults, null, …)`, so `patterns.json` is never read. The README describes it as optional custom data (reproduced by grep: no reference to the file in code). Either wire it up or delete the user-file branch, the warning plumbing, and the README sentence.
- **`Platform.gameDir()`, `Platform.isDevEnvironment()` and `BlockVault.platform()`** are never called. `BlockVault` only logs on init, and NeoForge’s version is a TODO returning false.
- **`BlockVaultInfo`** only holds `MOD_ID`, which nothing uses (its test only checks that).
- **`CardVariant.UNLOCK`** is never produced, though the card screen handles it.
- **`PasswordGenerator.entropyBits`** is only called from tests.
- **`VaultStore.changeMasterPassword`** is unreachable (see 2.27).
- **`KeyMode`** is a one-value enum with a lookup loop, left over from the removed OS-keystore modes. `Header.kdfId` and `reserved` are always 1 and 0.
- **`PromptMatcher.KEYWORDS`** prefilter is effectively dead: every default pattern requires `/`, and any `/` already makes the message a candidate. It is also limited to a few languages.
- **Identical templates.** `nlogin` and `loginsecurity` are the same as `authme`, which makes three indistinguishable choices in the template screen. There is also no custom template editor despite `CommandTemplate` supporting validation.
- **Redundant setting.** `disableAutoLogin` duplicates `autofill`.  Delete 'Force manual login'
- **`BlockVaultConfig.Settings`** has nine near-identical `withX` methods; a small builder or helper would shrink it.
- **Import goes through a temp directory** (`Files.createTempDirectory`, a `vault.bv` copy, lock-file cleanup), but the imported store never saves. Parsing from the byte array would remove the temp-file step and its cleanup.
- **Two delete flows:** `VaultDeleteScreen` and the inline confirm in `VaultCredentialScreen`.
- **`PromptModel` carries the rejected password** for the REJECTED card, but the UI blanks the field and `wipePending()` follows immediately.
- **`MixinHealth`** is misleading as written (see 2.19).
- **Seven duplicate locale files** (see section 3).
- **Repo root clutter.** `BLOCKVAULT_PLAN.md` (about 59 KB) plus the running `STATUS.md` and `DECISIONS.md` logs are AI-process artifacts; consider moving them to `docs/`.

---

## 5. Security design notes

- **Best-effort wiping is partly theatre.** `VaultJson.write` builds the whole vault as immutable `String`s (`new String(password)`, `root.toString()`), and `read` does the same on the way in. `Account.password()` clones every call, and `TemplateRender` and the EditBox hold `String` copies. `DECISIONS.md` admits part of this; the README doesn’t. State a clear threat model: the vault protects the file at rest only when trust is off, and it does not protect against malware running as the same user.
- **KDF.** PBKDF2-HMAC-SHA512 at 210k to 2M iterations is acceptable but not memory-hard. `Kdf.calibrate` uses one cold-JIT probe at the floor value, so the result is noisy and machine-dependent. If a dependency is acceptable, Argon2id or scrypt is stronger.
- **No master-password guidance.** Only “non-empty and matching” is checked at creation, with no minimum length, no strength hint, and no warning that a forgotten password means unrecoverable data.
- **Trust file and backups** are all decryptable with the same key, so deleting or rotating one does not protect the others.

---

## 6. Build, CI and repo

- **No Gradle wrapper.** CI falls back to a hard-coded Gradle version, and local builds depend on whatever Gradle is installed. Add the wrapper and wrapper validation.
- **Actions are pinned by tag, not commit SHA**, and there is no dependency verification or lockfile. That matters more than usual for a password manager.
- **Mod Menu** is declared as `maven.modrinth:mOgUt4GM:kyy7dbrZ` (opaque ids); add a comment naming the human-readable project and version.
- **NeoForge** is pinned to a beta build with an open-ended range `[26.3.0.7-beta,)`.
- **Mixin config** declares `compatibilityLevel: JAVA_21` while the project requires Java 25; check this matches what Mixin supports.
- **`check-rules.sh`** is grep-based, so comments can trigger false positives, and it doesn’t scan Gradle files or resources.
- **License** is “All Rights Reserved”. For a security tool, an auditable licence tends to help adoption and trust (a judgement call).

---

## 7. Test gaps

- **The `shared-client` module has no tests at all**, and that is where 1.1, 2.1 and 2.6 live. Extract the non-Minecraft vault logic from `VaultRuntime` into the core module so it can be unit tested.
- **Missing store tests:** concurrent saves, a corrupt file, the trust-file path, export onto an existing file, duplicate hosts on merge, and an account surviving a lookup followed by a save.
- **Missing session tests:** classifier precedence with realistic server messages, template precedence with an existing server entry, and exceptions thrown from `send()`.
- **Backup rotation** has no test for “three saves in a row”.
