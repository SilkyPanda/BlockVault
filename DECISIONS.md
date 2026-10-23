# Decisions

## 2026-10-23 — CI result reported by owner, but not attributable here

The owner reports that the repository built successfully. The supplied workspace has an empty, read-only `.git` directory and no `gh` executable, so the run SHA, result, and API artifacts cannot be inspected and these workspace changes cannot be committed or pushed. M0 remains PARTIAL until its green run can be associated with a commit. M3 remains blocked on reading both API dumps; no Minecraft API names were added in this continuation.

## 2026-10-23 — Gradle wrappers omitted

No repository history or wrapper-generation output is available in the supplied checkout. CI uses the specified Gradle 9.7.1 fallback. Wrapper generation is deferred until the real project checkout is available.

## 2026-10-23 — Gson vault password serialization uses a temporary immutable string

The specified Gson JSON tree API represents JSON string values as Java `String`. Vault serialization therefore creates a short-lived password `String` to construct the `pass` JSON value; the temporary `char[]` copy is wiped immediately. The JSON tree's UTF-8 byte buffer is wiped after encryption. Java cannot guarantee erasure of the immutable string before garbage collection.
