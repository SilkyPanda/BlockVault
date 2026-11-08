# Project status

**Version:** `0.2.0-alpha.1` in metadata; not published.

**Verification:** The checked items in `TEST_CHECKLIST.md` are passes reported by the user. Items under **Still to test** remain unverified. The latest credential-card changes (favicon privacy, result-card Enter handling, card animation/alignment) still need in-game confirmation. Automated checks run for the current tree are recorded below; a successful build does not replace the manual checklist.

**Automated verification:** Pending. Gradle distributions are not locally cached, and you asked me to leave builds to you and report their results at the end. I did not run core tests in this phase; this phase changes documentation only, so source and behavior are unchanged.

**Known product limitation:** bundled prompt and result matching is English-only.

See [ARCHITECTURE.md](ARCHITECTURE.md) for module boundaries, thread ownership, persisted data, and code navigation. See [TEST_CHECKLIST.md](TEST_CHECKLIST.md) for reported passes and the remaining manual checks.
