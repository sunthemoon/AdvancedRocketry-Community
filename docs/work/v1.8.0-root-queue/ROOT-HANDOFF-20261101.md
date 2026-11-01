# Root handoff, 2026-11-01 14:00 (sections 1 to 4 refreshed at 19:55)

Written by the Root session (Claude) that took over from [ROOT-HANDOFF-20261031](ROOT-HANDOFF-20261031.md), for the
next one. [QUEUE.md](QUEUE.md) and [CURRENT_VERSION](../../status/CURRENT_VERSION.md) stay the sources of truth; this
file adds the in-flight state, the decisions since the last handoff and the habits that proved useful. The start-up
steps in ROOT-HANDOFF-20261031 section 1 and the merge flow and tools in [ROOT-HANDOFF-20261030](ROOT-HANDOFF-20261030.md)
sections 5 and 6 are unchanged, except where section 5 below says otherwise.

## 1. Mode and numbers

- **R246 replaces R163's worker mix:** Codex is the main engine with at most two sessions; one Claude worker or
  cross-engine reviewer may run alongside them. Independent reviews go to the other engine when its slot is free.
- **Standing delegation (2026-11-01):** the owner delegated design points to Root's recommendation
  ([OWNER-DECISIONS-20261101](records/OWNER-DECISIONS-20261101.md)). It excludes Gate, version and release approval;
  tags, main merges and force pushes; the D5 and D7 human reviews; the real-client and reference-node campaign; and
  accepting any risk a design choice does not itself state.
- The last Root decision is **R264**; the next is **R265**.

## 2. In flight

| Item | State | Next step |
|---|---|---|
| W242-fix, bay safe exit S2 (Codex, thread `01a1969e-9cb4-79d0-97fa-fb910b900352`) | Run `W242-fix/run-20261101-181859`; showed that R260's test cannot be observed and kept strict R2 (branch at `43d4d28c6`); still running a full GameTest on W242's base | When it exits, resume that thread (`codex-resume`) on R264 in TASK-W242-FIX; then a sharded run and an independent review of W242's whole diff on Claude (TASK-W242-REVIEW to write) |
| W244-fix, the capsule and importer follow-ups (Claude) | Run `W244-fix/run-20261101-190441` on R263 (the legacy `Capsule` group with its own UVs); W244's review approved the rest and its branch run passed at `96e4925c9` | Root reviews the diff, sharded run, a recheck by a fresh Codex session, apply the VERIFICATION draft, merge |
| W246, flat textures T1 to T3 (Codex) | Run `W246/run-20261101-185204` (R262) | Apply its drafts on its branch, merge the shared head, sharded run, independent review |
| Later import batches | W245's `INVENTORY.md` proposes S1 (saw block), P0 (landing pad), P1 to P4 (motors and tanks, a model contract first), H1 (laser gun), U1 (slot icons) and the exclusions X1 to X3 and J0 | Dispatch one by one under R262's pattern |

All run directories are under `D:/GitHub/ARCE-Task-Evidence/root-queue/`. A background `wait_workers.ps1` per run
reports each exit; `ci_wait.py` or `ci_wait_head.py` waits on CI.

## 3. What happened since the last handoff

Merged on the shared branch, each with its review and branch run recorded in its QUEUE row: W238 (16 pending per owner,
with the ADR-065 and risk text corrected by its review), W240 (the adapter restore stall, test only), W237 (the
completion check's midnight rebuild), W241 (K4, with W241-fix's rotation rule R257 for every K1 to K4 owner) and W243
(W238's follow-ups, R258). The last shared regression recorded is at `e1bf12dbb` (second attempt; the first failed
only on host lag in shard 2); governance is green. Root decisions R255 to R264 are in their QUEUE rows.

## 4. Next work

1. The in-flight items in section 2.
2. W230-S3, the two-client and restart evidence for the bay safe exit, after W242.
3. The remaining import batches from W245.
4. Owner-only: D5 (the ten sounds), D7 (fifteen records), W203-Q4, D14, the final campaign and the release.
## 5. Lessons from this session

- **Sharded CI.** The shards download the build artifact named with `github.run_attempt`, so re-running only the failed
  jobs cannot find it. Re-run a failed sharded run whole with `tools/ci_rerun_all.py <run id>` (R242). The host-lag
  ceiling of 8 applies per shard (R236, ADR-072 note).
- **Midnight UTC.** A shard ending after 00:00 UTC can lose its completion marker to Forge's log roll; W237 fixes the
  `build.gradle` check. Until it merges, a failure with all tests passed and a split log is this, not a regression.
- **Art screen.** `tools/audit/screen_generated_art.py` runs only in governance, on the shared branch and main; a worker
  branch with new art needs it run locally before the merge (W232).
- **Drafts.** `tools/apply_drafts.py` reads every Target first; create the "Create only if absent" files before
  applying. Root's reruns of a worker's audit must write to a new output name, never over the worker's pinned files
  (W229-R3).
- **Evidence.** Copy a worker's logs out of its worktree (including `build/` and `run/` logs) before removing it; W232's
  local GameTest log was lost.
- **Commit gate.** Run `python -B scripts/validate_repository.py --require-approved-identity` and commit only on
  `0 failed`; a link to a file that exists only on an unmerged branch fails it, so use a code span until the merge.
- **Python tests.** Run single modules with `python -B -m unittest discover -s tests -p <pattern>`; `tests.<module>`
  imports fail. The whole suite takes longer than the tool timeout, so run it in the background.
- **Fail-first claims.** State exactly how many new cases failed first, from the retained XML; W239's recheck blocked
  only on Root's overstated count (R252).
- **Lock contention.** With three workers each needing one full GameTest, the heavy-JVM lock serialises them for hours.
  Check the owner process is alive at every hourly check; schedule merges, not more workers, while it is contended.
