# TASK W181: JEI per-category markers and a v1.8 Forge × JEI matrix (VDOC 10.4)

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w181` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w181` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W181.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W181/` |
| Scope source | section 3, B4c, of `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` (SHA-256 `134fb14c…64ca`) |

The v1.8 version document's section 10.4 asks for JEI evidence across the classic recipe categories. Today
`client/compat/jei/ArceJeiPlugin.java` logs only the electrolyzer's `status=registered recipes=1`.

## What to do

1. **One marker per category.** Make `ArceJeiPlugin` log one bounded INFO marker per registered category, with its
   recipe count, in the existing marker's format. Client-only logging; no gameplay or protocol change.
2. **A v1.8 matrix runner.** Copy `scripts/run_v100_compatibility_matrix.py` to
   `scripts/run_v180_compatibility_matrix.py`, with its Python tests. It must:
   - expect every classic category;
   - run with and without JEI;
   - cover Forge 47.4.10 and 47.4.23.
3. **Run it once locally** on a JAR built from your committed source, under the heavy-JVM lock, and record the result.
   Real-client checks remain final-campaign evidence (E) and are not part of this task.

## Verification

- The runner's Python tests.
- The unit suite.
- The local matrix run.
- `python -B scripts/validate_repository.py --require-approved-identity`.

## Not in this task

Gameplay changes, real-client sessions, ADRs, status files and the ledger.

## Continuation under Root decision R145 (2026-10-28)

The first session committed `25f0b6dd8` (the markers, the runner and its 63 Python tests; 5,425 unit tests pass)
and wrote no record. Its detached driver `D:/GitHub/ARCE-Task-Evidence/root-queue/W181/run-heavy-3.ps1` took the
lock at 22:45 and built the JAR. Runtime preparation then failed on a network fault: one asset download from
`resources.download.minecraft.net` ended with a TLS handshake error (curl exit 35). The matrix did not run. The
driver went on to a complete GameTest. Its outputs are in `D:/GitHub/ARCE-Task-Evidence/root-queue/W181/attempt-3/`.

Both Codex slots are taken, so a fresh session finishes the slice on `codex/v1.8.0-w181`:

1. Read `attempt-3/heavy-result.json` and the GameTest result, and keep them as evidence.
2. **Retry runtime preparation outside the lock.** It only downloads and unpacks, so it needs no heavy-JVM lock.
   Retry a failed download a bounded number of times, and record each attempt. If the network still fails, stop
   and report.
3. **Run the matrix under the lock**, on a JAR built from the committed source, and only the matrix: no second
   complete GameTest.
4. Write `records/W181.md` (both sessions, the commands and their results) and commit it.

Do not change the runner's expectations to make a cell pass. A cell that fails is a finding for Root.
