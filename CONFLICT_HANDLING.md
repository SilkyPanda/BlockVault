# How BlockVault handles conflicts

Several copies of a vault can exist: two Minecraft instances sharing one data folder, an exported file, a backup.
BlockVault reconciles them with one rule, implemented in `VaultMerger`.

## The rule

1. **Same server.** Two server entries are the same server if they share at least one host (`host:port`).
2. **Same account.** Within one server, accounts are identified by exact username (case-sensitive).
3. **Newer wins.** If both copies have the account, the one with the larger `modifiedAt` wins. On an exact tie the
   local copy (the vault you are using) wins. A `modifiedAt` in the future is treated as "now", so a foreign file
   cannot win forever by claiming a far-future time.
4. **Everything else is kept.** An account or server present on only one side is kept. Merging never deletes anything.
5. **Server settings** (name, autofill, template, host list) always come from the local copy; servers have
   no timestamp, and an imported file must not be able to add host aliases to one of your servers.

## Trash

Deleting moves the account (or whole server) into the vault's trash. Trash entries are merged by id and removed after
60 days. Restoring an entry applies the same rule against whatever is live now, so if you registered the same user
again in the meantime, the newer password wins.

## Where merging happens

| Action | Behaviour |
| --- | --- |
| Create vault, import | Merge with the file on disk using the rule above. An import is previewed first (new servers, new accounts, newer passwords, kept local passwords) and a restore point is taken before it is applied. |
| Master-password change | Merges with the file on disk, then re-encrypts under a new vault key. |
| Edit, delete, restore from trash, restore a backup | No merge. The edit is applied to the freshly reloaded vault and saved only if the file's SHA-256 is unchanged; otherwise it fails with "Vault changed on disk". |
| Restore a backup | Replaces the vault; the current state is saved as a restore point first. |

## Accepted trade-off

Because deletions are not remembered, importing or merging an older copy can bring back an account you deleted (and
emptied from the trash). Newest-wins still applies to accounts that exist in both copies.

## Unknown fields

Fields this version does not recognise (on the vault, a server, a trash entry or an account) are kept and written back unchanged. When two copies are merged, the local copy's unknown fields are the ones kept.
