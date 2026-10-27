# Decisions

**Product**
- Per-server unique passwords in an encrypted local vault; no network, no sync.
- The default generator is the server-friendly preset (16 characters, letters and digits). Other presets: balanced, maximum, memorable.
- `Trust this device` is on by default; the owner accepted the trade-off (see README).
- The clipboard is never cleared automatically. The master password minimum is 8 characters.
- The project license stays All Rights Reserved.
- Action-bar (overlay) messages are treated like chat messages on both loaders.

**Data**
- Vault format version 2. Deleted items go to an encrypted trash for 60 days (manual restore or delete; optional backup purge). There are no tombstones. Older vault files are not supported.
- Merge rule: same server (shared host) plus same username is one account; newer `modifiedAt` wins, the local copy wins a tie, future timestamps count as now. Merging never deletes, so an older copy can bring a deleted item back. Details in `CONFLICT_HANDLING.md`.
- Unknown JSON fields at every level are kept and written back unchanged.
- Changing the master password rotates the vault key and re-encrypts all backups; unreadable backups are skipped and reported.
- Import always shows a preview, then takes a restore point before merging.

**Behaviour**
- No auto-lock. A failed or odd server message never deletes a pending credential.
- Cards never open on top of another screen; they wait for a free one.
- If a privacy hook is inactive on the running Minecraft version, BlockVault warns once instead of crashing.
- Manually added logins are saved as verified.
- The data folder is opened with Minecraft's own helper (not `java.awt.Desktop`), with a clipboard-path fallback.
