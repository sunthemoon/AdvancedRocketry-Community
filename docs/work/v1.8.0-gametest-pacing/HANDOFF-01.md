# GameTest readiness pacing: Claude handoff 01

Date: 2026-10-10. Author: Claude (Claude Code session). Status: implemented and
verified on the author's checkout, **not reviewed or integrated**. This record does
not approve any Required Gate, change the version status, or replace Root's
independent review and integration.

## Authorization

The owner asked in the interactive Claude Code session on 2026-10-10 (wording as
given): "或许先看看按照建议协助修复下这个气闸和往返问题吧，进度确实比较慢了，得想办法提提速".
When asked how to deliver the result, the owner selected, in the same session:
"Claude 建修复分支并推送（推荐）": an isolated worktree branch from `c8fe8c0f`
that commits only the three test files and is pushed without force, then
independently reviewed and merged by Codex. Scope covers this branch only. It
covers no edits to the shared branch, central status files or ledgers.

## Identity

| Item | Value |
|---|---|
| Branch | `fix/v1.8.0-gametest-tick-pacing` |
| Base | `c8fe8c0f` (`src` and Gradle inputs identical to `d1272416`, the verified base) |
| Source commit | `988352bb2098f257d0fbfb83be1403798505f453` |
| Changed files | `gametest/GameTestTickPacer.java` (new), `gametest/TauCetiPathGameTests.java`, `rocket/server/RocketDestinationReadinessGameTests.java` |
| Production code | unchanged |

The committed diff equals the verified `PATCH-01.diff` line for line. It was not
rerun at the commit SHA.

## Root cause

`GameTestServer.waitUntilNextTick()` only runs `runAllTasks()` and never sleeps
(javap of the Forge 47.4.10 mapped jar), so GameTest ticks run at roughly
300–400 per second. Chunk generation, entity-section loading and lighting progress
on background threads in wall-clock time. The Tau Ceti path descent (270 ticks) and
the destination readiness retries (80 ticks) therefore spanned about 1 s and 0.2 s
on hosted CI. All three retained hosted failures of the Tau test show the same
pattern: `loaded=YES`, `entities_loaded=NO` and `WAIT_ENTITY_READY` 110 times at
`test_tick=743`. A dedicated server at 20 TPS waits correctly. The earlier airlock
skylight failure, already repaired by `23bcb6b1`, is the same class of problem.

## Change

- `GameTestTickPacer` holds each waiting tick to 50 ms. While it waits, it runs
  `ServerChunkCache.pollTask()` for every level, as `MinecraftServer` does while it
  waits for its next tick. A plain park was tried first and was too slow, because
  each chunk-status step hops through the main thread.
- The readiness test paces its three existing retries. `READINESS_ATTEMPTS = 80`
  and `timeoutTicks = 300` are unchanged.
- The Tau path test adds its own radius-2 ticket on Tau Ceti f's first pad at
  test start and removes it in cleanup. The other flight tests already prime their
  pads with `primePadChunks`. Before the descent, the test waits for that pad to be
  entity-ready, with at most 200 paced attempts. `FLIGHT = 270`,
  `timeoutTicks = 1400` and all assertions are unchanged
  (270 + 203 + 200 + 270 + 270 = 1,213 ticks).

## Verification

Java 17.0.7, Gradle 8.8, Forge 47.4.10. Every run used a D: export, not this
worktree. Full commands, logs and scripts are in the [evidence zip](evidence-01.zip)
(1,731,822 B, SHA-256
`bb2f6a2698290083f7d1ba3d4d467699ee5cb4304398176d857c8c5a32eea1b1`). Its
`SHA256SUMS.txt` has SHA-256
`8fda2e5c9ac486ab5954574160dc2bd700480f615f6dd00ab5a1d10cc1b259d2`.

| Run | Result |
|---|---|
| Unchanged tests, 4/2 CPUs and 2-CPU affinity | 597/597 pass; CPU limits alone do not reproduce the failures |
| Unchanged tests, 3 s stall of every background worker (experiment hook) | Both failures reproduced with the hosted signature |
| This change, same stall | 597/597 pass |
| Clean patch on a fresh export: `clean build` | exit 0; 381 XML, 2,192 tests, 0 failures/errors/skips |
| `runGameTestServer` | exit 0; all 597 required pass; 62 ERROR / 0 FATAL, as in the baseline, unwaived |
| `runData` and `src/generated` comparison | exit 0; only the ignored `.cache` directory differs |

The experiment hook is only in `EXPERIMENT-HOOKS-UNPATCHED.diff` inside the zip
and must not be integrated. Run deviations are listed in `ANALYSIS.md` section 5:
missing offline cache artifacts, one overlapped and discarded verification chain,
and one launcher quoting error.

## Root actions and open items

1. An independent review of commit `988352bb`, then integration. The integrator
   updates the central records.
2. A hosted CI run of the integrated commit. This is the first Linux confirmation;
   none has been run.
3. `FuelLoaderPlacementGameTests` (`count=0`) belongs to the same class and is
   unchanged. It has `timeoutTicks = 20` and needs its own task.
4. Strict validation, the 62 ERROR records, and every Gate are untouched.
