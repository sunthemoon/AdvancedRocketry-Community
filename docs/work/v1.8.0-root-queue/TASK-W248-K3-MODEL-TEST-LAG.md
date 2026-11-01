# TASK W248: the K3 model GameTests' server-thread stalls

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w248` (new; branch `codex/v1.8.0-w248` from the shared head named in your prompt, which includes W247) |
| Record | `docs/work/v1.8.0-root-queue/records/W248.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W248/` |
| Background | [W247's record](records/W247.md) (R266): each facing of a K4 model test built its own 25-chunk `K3ModelTestRegion`, so every test generated about 230 fresh chunks while the server thread waited. W247 put the four facings of each K4 test in one shared radius-0 region and asserted that no facing or mirror leaves that chunk or overlaps another. K4's longest tick fell from 8,076 to 1,274 ms. W247's measurement tools are `scripts/run_w247_models.ps1`, `scripts/w247_compare.py`, `scripts/w247_lag_summary.py`, `scripts/w247_jfr_summary.py` and the probe in `scripts/w247_probe/` |
| Subject | K3's model tests, which build regions the same way and still stall 4.3 to 7.4 seconds each locally (184 to 263 chunks generated): the batches `w236_analyser_model` and `w236_observatory_model` (`machine/classic/runtime/K3ResearchModelGameTests.java`), `w236_solar_model` (`machine/solar/K3SolarModelGameTests.java`) and `w236_beacon_model` (`gametest/K3BeaconModelGameTests.java`). `w236_observatory_model` runs just before `w133_escrow_fill_launch` in shard 2 |

**Root decision R269.** Apply W247's fix to K3's model tests, on the test side only.

1. **Fix the fixtures.** Put the facings of each K3 test in one shared region with as few ticketed chunks as its
   structure allows: radius 0 where every facing's reach fits in one chunk, otherwise the smallest radius that fits.
   Assert, as K4 now does, that every cell any facing or mirror could validate lies in a ticketed chunk and apart from
   the other facings. Keep each test's own row, the forced-chunk fringe check (W233's lesson), every assertion, all
   four facings, the timeouts and K4's use of `K3ModelTestRegion`.
2. **Show it.** Before and after stall sizes for the four batches from the same focused runs (reuse W247's tools), and
   one full GameTest run with the log checker and its `server-overloaded` count.

No change to the host-lag ceiling, ADR-072, any timeout, the batch order mechanism, K4's tests or product code.

**Root decision R273 (2026-11-02, 04:00) adds one batch.** W246's branch run 41341199945 failed shard 2's ceiling
with 9 warnings, every test passing; besides 13.7 seconds after `w236_observatory_model`, an 8.4-second stall follows
`w244_etcher_model` (`machine/classic/runtime/ClassicModelProjectionGameTests.java`), whose four facings also build one
25-chunk `K3ModelTestRegion` each. Apply the same fix there, with the same reach assertion and nothing else in that
class changed, and add it to the before and after comparison (focused runs are enough for this batch).

## Verification

- The K-model unit tests and the whole unit suite.
- The focused before and after runs and one full GameTest run under the heavy-JVM lock.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run and reviews the diff.

## Not in this task

Other batches' lag, K4, the bay exit, the import batches, status files and the ledger.
