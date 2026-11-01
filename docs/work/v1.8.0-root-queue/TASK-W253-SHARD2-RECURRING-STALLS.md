# TASK W253: shard 2's recurring server stalls after W248

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w253` (new; branch `codex/v1.8.0-w253` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W253.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W253/` |
| Background | [W247's record](records/W247.md) and [W248's record](records/W248.md): the K4, K3 and etcher model tests stalled the server while their fixtures generated fresh distant chunks; both fixes shared one region across a test's facings. W247's tools are `scripts/run_w247_models.ps1`, `scripts/w247_compare.py`, `scripts/w247_lag_summary.py`, `scripts/w247_jfr_summary.py` and `scripts/w247_probe/` |
| Evidence | Hosted shard-2 logs after W248, each passing every test with 6 to 8 `server-overloaded` entries against the per-shard maximum of 8: `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-fix3/final/run41354424871-shard2.log`, `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-fix3/shared-run41357637598-shard2.log` and `D:/GitHub/ARCE-Task-Evidence/root-queue/W248/run41351175168-shard2.log`. Root's tool `D:/GitHub/ARCE-Task-Evidence/root-queue/tools/overload_batches.py <log>` lists each warning with the batches before it |

**Root decision R277.** The same batch groups precede shard 2's warnings in every run: 5.5 to 6.4 seconds after
`w05_cutting_rotations`, `endgame_menu_drill_save`, `journal_pre_baseline_launch` and `resource_arrival_recovery`;
about 3 seconds after `venus_volcano_relief` and `tau_ceti_landing_f` or `_g`, and after `endgame_black_hole_idle`,
`beacon_finder_rate`, `escrow_core` and `propulsion_waiver`; 2.4 to 2.7 seconds after `bay_copies_login`,
`atmosphere_analyzer`, `station_controls_confirm_binding` and the batches near them.

1. **Measure first**, as W247 did: run those groups focused under the heavy-JVM lock with the tick probe and JFR, and
   name the batch and the cause of each stall of 2 seconds or more (fresh chunk generation for distant fixtures,
   dimension creation, saving, world generation that the test itself is about, or something else). Vanilla's warning
   is rate-limited and carries the backlog forward, so attribute by tick, not by warning.
2. **Fix on the test side** where a fixture causes avoidable work (for example a rotation test building one distant
   region per facing, or fixtures far apart where nearby or shared chunks would do), keeping every assertion, facing,
   timeout and the isolation from other tests' forced chunks (W233's lesson). Where the work is what the test is
   about (for example generating a new dimension's terrain), leave it and say so.
3. **Show it**: before and after stall sizes from the same focused runs, and one full GameTest run with the log
   checker and its `server-overloaded` count.

No change to the host-lag ceiling, ADR-072, any timeout, the batch order mechanism, the shard split or product code.

## Verification

- The unit tests of any class you change and the whole unit suite.
- The focused before and after runs and one full GameTest run under the heavy-JVM lock.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run and reviews the diff.

## Not in this task

Other shards, the shard split, product code, status files and the ledger.
