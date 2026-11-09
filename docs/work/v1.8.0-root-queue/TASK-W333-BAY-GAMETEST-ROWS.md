# TASK W333: the satellite bay's 31 missing GameTest rows

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w333`, branch `codex/v1.8.0-w333`, created by Root from the shared head |
| Decision | Root decisions R398 and R401 (QUEUE row W333), from W325's result [W325](records/W325.md) and its matrix [W325-MATRIX](records/W325-MATRIX.md) |
| Contract | `records/W27A-DESIGN.md` section 9 (the F2 to F5 matrices), the registry transport and N2 rows as W325-MATRIX states them, and the records the matrix cites |
| Record | `docs/work/v1.8.0-root-queue/records/W333.md` and the plan `records/W333-DESIGN.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W333/` |

## Why

W325 mapped the bay's GameTest rows to the hosted run on candidate C1. 31 rows have no dedicated GameTest, and unit
tests or nearby native cases do not count as a GameTest pass. The next candidate C2 runs every GameTest on its hosted
build, so the missing tests are written now. Production admission stays off; GameTests run only on a GameTest server.

## The rows

From W325-MATRIX (each row's exact requirement is there):

- **F2** (initial mission and replay): F2.2 to F2.8;
- **F3** (shared pending pool): F3.2 to F3.7;
- **F4** (terminal extraction): F4.3 to F4.6 and F4.8;
- **F5** (bodies and orbits): F5.5 to F5.7;
- **RT** (registry transport): RT.4 to RT.7;
- **N2** (UNKNOWN resolution): N2.1 to N2.6.

## What to do

1. **Plan first** (`records/W333-DESIGN.md`, committed before the code). For each row: the test class and method that
   will cover it, the fixture, the assertions that make it this row and not a nearby one, and whether it needs a
   restart. A GameTest cannot restart the server. Where a row needs a restart (for example F3.4 or F3.6), use the
   existing gated reload or replace seams that other bay and registry GameTests use. If none reaches the row, say so
   and leave it NOT_COVERED with the reason. Do not add a production seam.
2. **Write the tests.** Put them in the existing bay, registry and saturation GameTest classes where they fit, or in
   new classes beside them. Each test must fail without the protection it checks: show this once per family (F2, F3,
   F4, F5, RT, N2) by disabling the protection in a scratch copy outside the worktree, then delete the scratch copy.
   Keep every existing test, assertion and timeout as it is.
3. **Batches.** Keep new batches few. The shared branch has about 788 batch names, and batch order is HashMap order
   (see the batch-order note in `records/W68.md` section 6). A new batch can move other batches, so run the full
   GameTest suite, not only your batches. If an unrelated test fails because of a new placement, report it with both
   orders; do not change that test.
4. **Log expectations.** An addition to `scripts/gametest_expected_log.json` may name only lines that your new tests
   emit, each with a reason. Never use it to hide another warning.

## Verification

- Your new tests alone, by batch, with `JAVA_TOOL_OPTIONS=-Dadvancedrocketrycommunity.gameTestBatches=...`.
- `./gradlew build` with every unit test, then the full `runGameTestServer` with `scripts/check_gametest_log.py`, under
  the heavy-JVM lock (backslash lock path, as WORKER-RULES says). W330 and W331 share the lock, so take it per step and
  release it between steps. Call Python by its full path in any detached script.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production behaviour and admission; native runners and fixtures (W331); the audit's shared-risk native cases (W332);
cargo (W330); the risk register, the status file and QUEUE.
