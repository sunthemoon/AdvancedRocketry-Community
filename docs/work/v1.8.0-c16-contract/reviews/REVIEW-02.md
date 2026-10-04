# ADR-064 revision 2 independent contract review

Reviewed checkout base: `cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`, branch `codex/v1.8.0-classic-content`.
Reviewed artifact: uncommitted ADR-064 revision 2, final SHA-256 **`41645ee12011bf33d74f7fd43962566790ac43a8232bca48283ea4b5d5400cdb`**.
Scope: the revised contract and relevant existing kernel/governance, read-only repository. No C16 production implementation is claimed or reviewed. No heavy Gradle/GameTest was run; root owns that execution and OpenPet. The untracked user bundle was never read.

## Result

The revised technical proposal is acceptable **subject to the two explicitly pending owner choices**. It must remain `PROPOSED`, not frozen/accepted for implementation, until the owner confirms resource ownership/removal and the nitrogen source/balance. No additional unresolved Critical/High/Medium contract finding was identified in the final hash.

This is not an implementation verification, Required Gate approval or release approval. Of the original three High findings, H2 is resolved and H1/H3 are technically answered by owner-pending proposals; they are not recorded as approved/closed. All seven original Medium findings are answered in the final text.

## Original finding dispositions

| Finding | Disposition | Evidence in final ADR-064 |
|---|---|---|
| H1 physical cross-chunk ownership | **Technically addressed; OWNER DECISION PENDING** | Lines 109-124 propose one authoritative controller Item/Fluid bank and journal snapshot, facade hatches, retained banks on unload/unform, fail-closed removal and separate FE guarantee. This follows ADR-019's same-store solution. The owner must confirm the changed ownership/removal semantics; the pending front matter correctly prevents acceptance. |
| H2 pump protection | **Resolved in contract** | Lines 280-286 explicitly use the complete accepted ADR-054 section 5 chain, server-derived owner-bound FakePlayer and no mutation on refusal. Runtime must still test every protection step. |
| H3 nitrogen eligibility | **Technically addressed; OWNER DECISION PENDING** | Lines 245-256 propose 8 nitrogen canisters per 1,000 ticks at the existing eligible `advancedrocketrycommunity:gas_giant`, keep its 8 hydrogen rate and existing oxygen electrolyzer route, and explicitly require confirmation. Existing generated gas-giant capability/table were independently checked. Tau Ceti f remains ineligible and unchanged. |
| M1 signature/journal migration | **Resolved in contract** | Lines 167-179 define canonical semantic SHA-256, unchanged-signature tag-only continuation and same-ID JSON change/removal refusal without resetting progress/resources. Lines 182-193 mark the new signature format, validate shipped/unchanged Item-only legacy payloads, reconcile retained old journals before conversion, preserve unprovable data and require three-machine in-progress/journal fixtures plus a second restart. |
| M2 chance bounds/replay | **Resolved in contract** | Lines 335-354 separate at most 16 ordered chance candidates from deterministic outputs, cap successes at four, disclose effective iridium probability 0.25%, retain outcome/transaction identity, re-simulate only stale pre-PREPARED resource plans and lock/recover exact prepared plans. Machine UUID/signature/monotone ordinal stabilize the batch seed and overflow refuses a new batch. The same-channel lens convention is explicit. |
| M3 zero FE | **Resolved in contract** | Line 148 now requires 1-10,000 FE/t, consistent with actual ProcessDefinition and the R1 Java probe. |
| M4 machine gravity | **Resolved in contract** | Lines 313-318 use body/containing-station gravity and explicitly exclude player/trust-list area fields; no fabricated player identity. |
| M5 tank callback cascade | **Resolved in contract** | Lines 261-270 define deferred deduplicated scheduling, one loaded edge/1,000 mB per tank per tick, 64 transfers per Level per tick, a 1,024-entry fair queue and bounded retry. Cross-tank external-transfer crash guarantees are explicitly limited. |
| M6 bounded stable persistence | **Resolved in contract** | Section 11 defines stable new roots and byte caps, bounded bank counts, owner/burn/ordinal fields, shared journal preflight, invalid/future preservation/refusal and distinct old-progress/journal/repeat-migration/resource-conservation fixtures. These are requirements, not claims that the decoder/adapter exists. |
| M7 deterministic/bounded selection | **Resolved in contract** | Lines 128-142 define full-ID order, 1,024 recipes/type, 32 checks/controller/tick, 256 checks/Level/tick, fair resumable lookup, reload cursor invalidation and unchanged old-machine ambiguity refusal. |

## Additional compatibility clarification, applied during review

The initial revision-2 draft did not specify how the new fluid item handler interacts with existing stackable canisters. Actual `ModItems.java` registers the empty/hydrogen/oxygen canisters with `stacksTo(16)`, and `OxygenCanisterItem.use` still fronts a whole-canister suit refill.

The final text at lines 237-244 now retains stack limit 16, allows mutation only on a detached count-1 unit, rejects direct stacked fill/drain in both simulation and execution, splits/rejoins a single-unit interaction without altering the rest, and requires 16-unit/partial/simulate/full-inventory/suit/vent regressions. This answers the compatibility concern; it is not an unresolved finding and does not require making existing stacks unstackable.

## Independent checks

- Re-read the actual revision-2 diff, final contract sections and existing process/journal/recovery/canister code. Exact-path reads succeeded. No repository writes.
- `Get-FileHash docs/decisions/ADR-064-CLASSIC-MACHINES-FLUIDS-AND-COMPONENTS.md -Algorithm SHA256`: final artifact hash `41645ee...5400cdb`, independently confirmed twice (including Python SHA-256).
- Read generated `v1.6/.../gas_harvest/gas_giant.json`: hydrogen rate 8; read generated `v1.4/.../celestial_bodies/gas_giant.json`: `gas_giant:true`, `landable:false`, `orbitable:true`. The proposed nitrogen data fits the existing table/mission eligibility and rate bounds, subject to owner confirmation.
- `C:/Program Files/Java/jdk-17.0.7/bin/javac.exe -cp <out>/classes -d <out>/classes <out>/RetryPlanProbe.java`: exit **0**, `r2-javac.log`.
- `C:/Program Files/Java/jdk-17.0.7/bin/java.exe -cp <out>/classes RetryPlanProbe`: exit **0**, `r2-plan-probe.log`. Actual kernel result for unchanged balances with an advanced revision and a captured plan: `STALE_TRANSACTION`, no journal. Re-simulation with the retained concrete output and current revision: `APPLIED`, output count 1. This independently supports the final pre-PREPARED retry rule.
- `git diff --check`: exit **0**, twice during R2.
- `git status --short`: read-only; reports root's concurrent integration fixes and review archives, plus the excluded user bundle. None are reviewer writes.
- Full Gradle build/data/GameTest/native server/S2/visual checks: **NOT RUN by this reviewer**, per scope/coordination. Root's separate execution is not substituted for independent runtime verification here.

## Remaining acceptance boundaries

- Owner confirmation of the proposed controller-owned resources/removal and nitrogen source/balance is still required. These choices are not approved by this review.
- Every contract requirement still needs production code and actual targeted/full tests; no C16 runtime was implemented by this reviewer.
- The disclosed centrifuge cap lowers effective iridium chance to 0.25%; progression/release notes and deterministic vectors should preserve that declared behavior if accepted.
- Forceful non-Forge-aware destruction, third-party capability side effects and ordinary container/player/external-FE torn saves are not newly proven arbitrary-crash atomic by this contract.
- Required Gates remain open. Freeze/owner acceptance and release approval are distinct.

Output directory: `C:/Users/Administrator/AppData/Local/Temp/arce-c16-contract-4bb2b067ea484b0980c779fdfd8cb079`.
Reviewer writes: this report, `RetryPlanProbe.java`, its classes and logs, all confined to the originally fresh Temp directory.
