# TASK W72: three small follow-ups from today's reviews

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w72-small-followups` (from the shared branch) |
| Branch | `codex/v1.8.0-w72-small-followups` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W72.md` (new) |

Give each item its own commit and test.

## 1. The persistence-to-progression seam (from W44L)

`AtomicSavedData.flush(Path)` (`persistence/migration/AtomicSavedData.java`) names
`progression.classic.firstevent.FirstEventCutSeams` directly. That makes a two-way package
dependency (AGENTS.md section 3.4).

- Move the seam behind a hook that the persistence package owns: for example, a package-visible
  static committer selector in `persistence.migration` that defaults to the normal committer.
  `FirstEventCutSeams` installs it only when the release-test cut is configured.
- Behaviour must be identical in production and in W44L's cuts.
- Prove it:
  - the progression GameTests and the first-event unit tests pass;
  - `git grep` shows that no class under `persistence` names a class under `progression`.

## 2. The fuel loader's status for a recovery-locked rocket (from W63)

When transfer recovery locks the only candidate rocket, `FuelLoaderBlockEntity` shows
`waiting_for_rocket`. `FuelLoaderStatus` has no member for the lock.

- Add a status member that names the recovery lock, with English and Chinese text, and show it when
  the loader's only candidate is refused by the recovery admission.
- Check whether the status is sent through `ContainerData`. If it is, the wire form changes; say
  exactly how, and keep the ordinal order of the existing members.
- Add a GameTest with a PREPARED source and an offline passenger (W63's fixture shows how): the loader
  shows the new status and changes no fuel.

## 3. Links to untracked files in the Markdown check (from W61F)

`scripts/validate_repository.py`'s Markdown check tests a link target with `exists()` on disk. A link
to an untracked file therefore passes in a dirty checkout, and fails in a clean clone and on hosted
CI.

- Resolve link targets against the files Git tracks (the index inventory the check already uses), so
  that a link to an untracked or ignored file is broken everywhere.
- Keep the correction-file rule from W61.
- Tests:
  - a link to an untracked file fails;
  - a link to a tracked file passes;
  - the real tree still passes in the owner's main checkout, which holds the untracked evidence ZIPs.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker.
- `runData` under the lock if the language data changes.
- The Python tests of the changed scripts (`python -B -m unittest tests.<module>` from the repository
  root, with `TEMP` and `TMP` set to `C:/Users/Administrator/AppData/Local/Temp`).
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- Workflows, status files, the ledger and ADRs.
- Any recovery or admission rule change.
