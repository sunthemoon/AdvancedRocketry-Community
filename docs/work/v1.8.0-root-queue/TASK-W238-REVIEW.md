# TASK W238-review: independent read-only review of the per-owner pending cap

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w238` at `61d2e409f`. It holds W238's commits `1fd100e91` (policy, adapters and tests), `2c372190f` (loader language provenance) and `775f10bdc` (record), and Root's merge of the shared head `6d2c14a72`. Review worktree `D:/GitHub/arce-v180-w238-review`, a detached checkout of `61d2e409f` |
| Task it answers | [TASK-W238](TASK-W238-PER-OWNER-PENDING-CAP.md), Root decision R244 (D1c-N under the owner's delegation, [OWNER-DECISIONS-20261101](records/OWNER-DECISIONS-20261101.md)) |
| Inputs | W231's measured data and candidate texts (Git `986e11fa7`, `docs/work/v1.8.0-root-queue/records/W231.md` there); [ADR-065](../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md) sections 8 and 10; R-039, R-042 and R-043 in the [risk register](../../11-RISK-REGISTER.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W238.md` on the subject branch; logs, audits and `verification-summary.json` in `D:/GitHub/ARCE-Task-Evidence/root-queue/W238/` |
| Drafts | `D:/GitHub/ARCE-Task-Evidence/root-queue/W238/W238-drafts.md`, SHA-256 `51e2acc6246a166c6a6a67d33dd862adc7eb09d7884a6bd07d2a5425ab0889b8` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W238-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The policy is R244.** Sixteen pending operations per stored, server-validated owner over the unique loader,
   cargo and bay union, with no operator exemption; checked after identity, availability, exact repeats and the
   unchanged 64 per server and 8 per rocket; `OWNER_PENDING_FULL` appended without moving any existing code; W231's
   English and Chinese messages exactly.
2. **Saves and restarts.** No persisted field, schema, codec rule or configuration is added. The owner count is
   rebuilt on restart. A save that already holds more than 16 for one owner still loads and saves, and its operations
   still recover, change phase and retire without a new slot.
3. **The floor operation's owner.** A new floor operation now stores the owner of the matching checked
   transfer-journal record in its existing operation-owner field instead of the command actor, and the settlement
   still audits the actor. Confirm that production always supplies the checked owner (the compatibility overloads
   are reached only from tests), that historical floor operations are not reassigned, and that this changes no
   authority a player did not already have.
4. **Preflights.** Device preflights reserve nothing and run before native capture; final admission rechecks before
   charging; a full owner gets a typed refusal, not the coordinator's reservation assertion, and nothing is charged,
   captured or loaded.
5. **Changed existing tests.** The global-pressure fixtures now spread 64 operations over four owners, one
   combined-reservation test now expects the typed refusal, and a cargo language-key count grew. Confirm each is a
   necessary consequence of R244 and that no global, rocket, retained, receipt, byte or timing assertion was reduced.
6. **Size.** The coordinator is 604 lines. Say whether the additions keep it to one responsibility.
7. **The drafts.** They are true to the code, quote W231's text exactly, and claim nothing the tests do not show.
   Name any sentence Root should change before applying them.
8. **Host lag.** Two locally filtered GameTest runs counted `server-overloaded` 10 against the maximum of 8; the full
   run passed. Read the sharded branch run 41281084756 on `61d2e409f` once it finishes and say whether anything
   remains.
9. **Nothing weakened.**

## Rerun

In your review worktree only:

- `W238OwnerPendingTest`, `W231BayFairnessTest` and `W231BayCapacityTest`;
- the `resourceescrow`, `rocket.bay` and `rocket.server.Bay*` unit packages, and the loader and cargo status tests;
- the whole unit suite;
- the repository validator.

Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W238-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
