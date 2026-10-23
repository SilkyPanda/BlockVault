# Decisions

## 2026-10-23 — Do not claim CI verification without repository metadata

The supplied checkout contains only the plan file and an empty, read-only `.git` directory. The `gh` CLI is unavailable. Files for M0 were prepared, but committing, pushing, observing CI, and downloading API artifacts are not possible in this environment. Per the plan, M0 remains PARTIAL and M1/M2 are not started because each milestone depends on the prior green CI run. No toolchain versions or API names have been independently verified here.

## 2026-10-23 — Gradle wrappers omitted

No repository history or wrapper-generation output is available in the supplied checkout. CI uses the specified Gradle 9.7.1 fallback. Wrapper generation is deferred until the real project checkout is available.
