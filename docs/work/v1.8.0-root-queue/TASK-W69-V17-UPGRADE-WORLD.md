# TASK W69: the v1.7 to v1.8 upgrade world (W62 slice S7)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w69-upgrade-world` (from the shared branch) |
| Branch | `codex/v1.8.0-w69-upgrade-world` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W69.md` (new) |
| Specification | `records/W62.md` section 7, row S7 (version document 10.3: upgraded worlds keep their content, with no placeholder or old ID lost) |

## What to do

1. **Build a v1.7 jar.** Use the last v1.7 development commit on the shared branch's history, or the
   v1.7 release-evidence commit. Find it in `docs/releases/` or the v1.7 status records, and record
   which commit you chose and why.
2. **Write `scripts/v170_world_fixture.py`.** It starts a dedicated server on the v1.7 jar with the
   gated release-test hooks, as `v140_migration_fixture.py` does, and creates a world that holds every
   block, item and block entity v1.7 registers.
   - Take the list from that jar's registries, not from a hand list.
   - Place one of each block with its block entity in a known layout, and put one of each item in a
     known container.
   - Include the persisted roots the v1.7 systems write: stations, satellites, research, endgame,
     escrow if present, and the transfer journal. Use the existing gated fixtures where they exist.
3. **Write `scripts/run_v180_world_upgrade.py`.** It copies that world and starts the v1.8 jar on the
   copy, then performs two restarts. After each start it compares, against the v1.7 inventory:
   - every block and block entity ID at its position;
   - every item and its count;
   - the managed SavedData roots and their schema versions. Expect each declared migration and no
     refusal, unless a refusal is documented, as the W44 epoch migration is;
   - the log: no unexpected ERROR, and the migration lines its records declare.

   Pin every run to a committed source SHA and its jar SHA-256, as the other v1.8 native runners do.
4. **Write `tests/test_run_v180_world_upgrade.py`,** unit tests of the comparison logic with small
   synthetic inventories: a missing block, a renamed ID, a count change, a refused root.
5. **Run it once, locally, under the lock.**
   - Keep the evidence under 100 MB in `D:/GitHub/ARCE-Task-Evidence/root-queue/W69/`, with an
     inventory.
   - Delete the world copies and server copies afterwards (AGENTS.md section 9).
6. **If the upgrade loses or changes content without a documented migration,** stop and report it
   with the evidence. Do not fix production code in this task.

## Commands

- The new Python tests (`python -B -m unittest tests.test_run_v180_world_upgrade` from the repository
  root).
- The whole unit suite once, if you change Java code. A new gated fixture is Java code.
- The native run under the lock.
- At the end, delete your generated worlds, server copies and older jars with a plain `rm -rf`, and
  make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes
  them.

## Not in this task

- Production changes, workflows, status files, the ledger and ADRs.
