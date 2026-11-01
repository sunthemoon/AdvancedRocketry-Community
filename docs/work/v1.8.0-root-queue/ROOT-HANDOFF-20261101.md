# Root handoff, 2026-11-01 14:00

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
- The last Root decision is **R254**; the next is **R255**.

## 2. In flight

| Item | State | Next step |
|---|---|---|
| W238, per-owner pending cap of 16 (Codex) | Run `W238/run-20261101-091508`; commits `1fd100e91` and `2c372190f`, waiting for the heavy-JVM lock for its final build | Independent review on Claude, apply its ADR-065 section 10 and risk drafts, sharded branch run, merge |
| W241, K4 models (Codex) | Run `W241/run-20261101-111356`; corrected source `6e310c287`, Python checks and both validators pass with the drafts applied; its final Java build holds the lock | Apply its drafts on its branch, merge the shared head in, push for a sharded run, independent review |
| W240, adapter restore stall (Claude) | Run `W240/run-20261101-101341`; reproducing shard 0's `mismatchedRestoreCleansPartialInventoryAndRetries` stall locally in measured groups; both hosted logs are in `W240/hosted-shard0-attempt{1,2}.log` | Sharded run of the fixture fix, merge; then re-run W237 and merge it |
| W237, midnight log rollover | Held at `10f50015c` with the `build.gradle` patch applied on its branch; its branch run hit W240's stall twice | Re-run after W240 merges |
| W242, bay safe exit S2 | Queued (R254) | Start when a Codex slot frees; design note first, independent review of the diff |

All run directories are under `D:/GitHub/ARCE-Task-Evidence/root-queue/`. A background `wait_workers.ps1` per run
reports each exit.

## 3. What happened since the last handoff

Merged on the shared branch, each with its review and branch run recorded in its QUEUE row: W222, W224 (D15 option A),
W226 (PAD-D4 O1), W229 (D8 upstream-URL rule and K1), W232 (pad top art), W225 (four GameTest shards, D18), W233 (K2),
W236 (K3), W239 (bay exit S1), and the records of W234, W235 and W230's reviewed design. W227's drafts created
[KNOWN-ISSUES](../../releases/v1.8.0/KNOWN-ISSUES.md); W228's review and recheck preceded the owner's ADR-076
acceptance (D17, D17-R). The owner decided D2, D3, D4, D6, D8, D9, D11, D12, D15, D16 and D17 on 2026-10-31; D13 was
closed by [D13-BOUNDARY-SWEEP](records/D13-BOUNDARY-SWEEP.md). Under the delegation Root decided Q1 = A (R243), D1c-N
= 16 per owner over the loader, cargo and bay union with no operator exemption (R244) and W235-D (R245, recorded as
KI-V180-SAVE-01). The last shared regression recorded is at `dec02149` (sharded, about 30 minutes); governance is
green.

## 4. Next work

1. The in-flight items in section 2.
2. W230-S3, the two-client and restart evidence for the bay safe exit, after W242.
3. K5 (the `spaceelevatorcapsule` texture) after K4, and the etcher pair once its origin finding exists.
4. Owner-only: D5, D7 (fifteen records), W203-Q4, D14, the final campaign and the release.

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
