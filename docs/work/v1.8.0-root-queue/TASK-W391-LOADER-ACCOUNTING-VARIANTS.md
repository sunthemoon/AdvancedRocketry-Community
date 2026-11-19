# TASK W391: the fuel loader's missing native accounting variants (F23)

Root: Claude. Date: 2026-11-19. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w391`, branch `codex/v1.8.0-w391` from the shared branch at dispatch (after W373's merge) |
| Decision | Root decision R520 (QUEUE row W391) |
| Source of the item | [FUEL-ADMISSION-REPORT](records/FUEL-ADMISSION-REPORT.md), row F23; [W373B](records/W373B.md), sections 9.3, 9.4 and 9.7 |
| Record | `docs/work/v1.8.0-root-queue/records/W391.md` |
| Write scope | `resourceescrow/EscrowLoaderS6SettlementProbe.java` (the gated probe; its existing cases keep their boundaries) and its unit test; `scripts/run_v180_accounting_cuts.py`, `scripts/accounting_cut_oracle.py`, `scripts/loader_cut_oracle.py` only for a reader it lacks, `scripts/run_v180_accounting_format.py`, their tests; the probe-case inventory and its test, and the integrated campaign's case counts and its test, re-pinned from added cases only; the record. No other production file. No limit, budget, timeout or log-manifest change without stopping for Root first. |

## Why

W373B and W373C gave the fuel loader's settlement the exact account oracle at L1, L2, O1 to O4 and pruning, with a
known loss and gain on the loader's tank (`accounting-cuts-loader`, 23 cases). Five variants are still missing, and
the cargo cases do not stand in for them, because they do not use the loader's own endpoint reader or its pruning
branch. Fuel admission needs each one covered natively or given a reviewed disposition.

## Root's decisions on the five variants

1. **A substitution of the loader's fluid: add it.** Case `LF_S6_AC_SUBSTITUTE`: before the preview, the held
   loader's stored tank is replaced, as W373C's `LF_S6_AC_LOSS` replaces it (the whole stored block entity, same
   device identity, binding and escrow section), with the same amount of another fluid; stop after O1's replacement.
   Choose a fluid the loader's tank accepts, and state which. If the tank accepts only one fluid, use one it does not
   accept and record what the loader's reader does with it on load: an exact hold with its reason, or the two-key
   adjustment, is a valid outcome; a silent loss of either fluid is a failure. Expected, if accepted: two frozen keys
   in ID order with opposite adjustments equal to the tank amount, the scalar total unchanged and not read as zero,
   then a released KNOWN receipt with both keys byte-equal, as the cargo case `FS_AC_SUBSTITUTE` shows.
2. **A loss and a gain on the rocket's bank: add them.** Cases `LF_S6_AC_BANK_LOSS` and `LF_S6_AC_BANK_GAIN`: the
   held rocket's stored bank changes from 1,000 to 900 and to 1,100 mB before the preview. Use a gated edit of the
   unloaded or stored rocket in the style of the tank edit, or an offline edit by the runner between starts if the
   probe cannot reach the stored entity; say which and why. Expected: the frozen KNOWN row with the target side changed
   and the adjustment that makes `r + a = 0`, then a released KNOWN receipt with the row byte-equal and the bank left
   as edited; nothing is refunded.
3. **A stop inside the loader's pruning: add it as a split save.** The pruning has no checkpoint of its own, and a
   stop between in-memory removals leaves nothing on disk. What can be left on disk is one side saved with its marker
   pruned and the other not, because the loader's chunk and the rocket's entity storage save separately. Cases
   `LF_S6_AC_PRUNE_SPLIT_TANK` and `LF_S6_AC_PRUNE_SPLIT_BANK`: after both markers are pruned in memory, save only the
   loader's chunk, or only the rocket's entity section, then stop as the other cuts do. Expected at the cut: zero
   markers on the saved side and one on the other, the receipt and the row unchanged; after both restarts: zero
   markers on both sides, the receipt and the row byte-equal, both amounts unchanged. If a split save cannot be made
   with a gated probe and no production change, stop and report to Root.
4. **A stop after the migration's atomic replacement and before the start completes: add it.** Case
   `UPGRADE_DURING_STARTUP` in `run_v180_accounting_format.py`: the watcher kills the first start after the migrated
   ledger has replaced the old one on disk (the target file is root 3 and no staged file of this attempt remains)
   and before the migration report or the ready line. Use the watcher's existing completion and artifact rules from
   W373C; a completed start is NOT_COVERED, as for the other DURING cases. Expected: the kept snapshot is root 3 with
   the exact migrated content, the backup of this attempt exists, and both restarts start without migrating and pass
   the account check. The window is the rest of the start, so it should be hit reliably; report how often it was.
5. **The stable readback from disk with a differing vector: not here.** It belongs to X02 S5, which C4 runs.

## What to do

- Write each case with its exact oracle at the cut and after each restart, in the style of section 9.3 of W373B's
  record: the exact row and key digest, the signed residual, the receipt's schema, release and knowledge, both
  epochs and exact marker counts, `issued_through`, and the raw total. Each new probe case gets one exact result line
  per start.
- Add regression tests for each new oracle branch, with a negative control each (a wrong amount, a wrong key order,
  a marker left on the wrong side, a root-2 snapshot in the startup case).
- Re-pin counts from added cases only, and list every changed count with its old value, new value and reason.
- Run natively under the heavy-JVM lock: a smoke with the new cases, then `accounting-cuts-loader` and
  `accounting-root2-upgrade` through `scripts/run_v180_integrated_campaign.py` with `--only`, on a JAR built from your
  branch (the root-2 JAR flags as W373B used them: `--root2-jar` and `--root2-jar-sha256 459669bf...f81744b`).
- Run the whole unit suite once, the changed Python modules and the inventory check, and one full GameTest run with the
  log checker if any file of `src/main` changed (the probe does), with `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). No earlier case's boundary or
  expected value changes.
- Commit on the branch in reviewable commits in the style of AGENTS.md section 6. Do not push.
- A different engine reviews the change read-only before Root merges it.
- Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W391/`, outside Git, with SHA-256 for each kept file.
- The last line of your answer is the path of the record.
