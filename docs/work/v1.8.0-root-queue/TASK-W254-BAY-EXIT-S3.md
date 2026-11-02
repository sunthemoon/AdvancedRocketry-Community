# TASK W254: W230-S3, the bay safe exit's recovery evidence and two-client checklist

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w254` (new; branch `codex/v1.8.0-w254` from the shared head named in your prompt, which includes W242) |
| Design | [W230-DESIGN](records/W230-DESIGN.md) Revision 1, section 10's row W230-S3 and the evidence it asks for; [TASK-W242](TASK-W242-BAY-EXIT-S2.md), [TASK-W242-FIX](TASK-W242-FIX.md) and [TASK-W242-FIX2](TASK-W242-FIX2.md) (R254, R264, R274) |
| Background | [W242's record](records/W242.md) (S2 as merged), R-042 and R-043 in [the risk register](../../11-RISK-REGISTER.md); the native harness pattern of `scripts/run_v180_bay_forced_stop.py` and `scripts/run_v180_bay_released_flight.py` with their Python tests |
| Record | `docs/work/v1.8.0-root-queue/records/W254.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W254/` |

**Root decision R279**, after W242's merge (S2), under the owner's delegation:

1. **Cut matrix.** A native harness `scripts/run_v180_bay_exit_*.py` with Python tests, following the bay forced-stop
   harness: for each S1/S2 phase of an exit (offer issued, descent in progress, correction window, landing, expiry,
   trusted move), stop the dedicated server at that point and start it twice more. Show that no exit state survives a
   restart (R-043: protection is RAM-only), no passenger, seat, receipt, reservation or journal is changed by the
   exit or lost by the cut, and no chunk is loaded or ticket added by the exit. Add only narrowly scoped release-test
   hooks or fixtures where the harness needs a deterministic point, gated like the existing ones.
2. **Packaged observer smoke.** W230-DESIGN requires a packaged, mapped-observer smoke before S2 can be used: on the
   built and reobfuscated JAR, on a native dedicated server, show that the read-only teleport-counter observer and the
   landing-frame name resolution work (the reflective names resolve in the production namespace), with one exit that
   is granted and one that is revoked.
3. **Two-client checklist.** Write the dedicated or LAN V2 checklist for two real clients, for the owner's campaign:
   what to do, what to see, what to record. Do not run clients.
4. **Evidence.** Manifest, resource and hash evidence for the tested JAR, the chunk-load and ticket counts, and every
   missing campaign item recorded as missing.

Never turn on production admission, change a budget, deadline, radius or contact rule, or approve a Gate. Give any
risk-register or ADR text to Root as a draft.

## Verification

- The harness's Python tests, the cut matrix and the packaged smoke under the heavy-JVM lock, with their logs.
- The unit tests of any class you change, the whole unit suite and the repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

Real clients, production admission, W231's quota, status files, the ledger and the risk register itself.
