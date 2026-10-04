# C16a-05 pressurized tank

Status: development-verified (module/regression/clean-stop native scope). Version: v1.8.0. Owner: delegated tank implementer;
root owns central integration and final independent review assignment.
Branch: `codex/v1.8.0-c16a-tank`; worktree:
`D:/GitHub/arce-v180-tank-20261003`; base `cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`.
The verified motor final-02 source overlay has 2,827 inputs, manifest SHA-256
`d5f73cf1c23f1beeb6c7d63628129a8c50f5dfa75a995d0829551f54b70b0367`.
Seeded unrelated changes are not the worker's implementation diff.

Outcome: accepted ADR-064 sections 6/11, `pressurized_tank` and schema-1
`arce_pressurized_tank`, including retained over-capacity fluid, item persistence,
whole-unit containers and bounded fair/nonreentrant vertical transfer.
Dependencies: verified C16a-04 fluids. No hatch, machine-family, pump, C17 or
later-version implementation in this task. No arbitrary-crash atomicity claim.

Exclusive write scope: new `machine/tank/**` production/tests, `TankGameTests.java`,
`V180Tank*.java` providers, own task documents and own fresh Temp evidence.
Central registries/config/bootstrap/DataGen/language/provenance/build/status,
generated output, existing Java and the root checkout are read-only; propose
integration patches in the task directory. Record NEW art before generation.
No commits/tags/push or protected document-bundle access.

Validation: pure capacity/scheduler/codec bounds and simulations; actual bucket/
canister/automation, vertical stack, queue saturation/fairness, unload/removal/
reentrancy and unsupported-root tests; packaged persistence and two restarts;
mandatory root regression/static checks and independent actual-diff review.
Root integrates before Forge/GameTest/native tests requiring registration.
Parallel compile/scoped unit jobs: at most two, Java 17, workers 2, 2-GiB Gradle
heap. Root coordinates full GT/native; no parallel benchmark/GPU claim.

- [x] Domain/codec and automated tests (reviewed module scope).
- [x] Forge adapters, original providers and integration handoff.
- [x] Root integration, actual GameTests and native persistence.
- [x] Independent module/native review and portable evidence (scoped development delivery, not version acceptance).

2026-10-03 checkpoint: isolated author handoff is frozen at 16 scoped JUnit
tests (0 failures/errors/skips); eight registered GameTests are authored, not
executed. The independent reviewer is preparing a registered Temp candidate.
Root authored `scripts/run_v180_tank_smoke.py` and eight Python evidence-check
tests, all passing; the packaged native run itself remains NOT_PERFORMED.
No checkbox or ledger unit is closed by source existence or scoped compilation.

Revision 2 checkpoint: 18 author JUnit tests in six suites pass after bounded
defensive copies on both codec boundaries; the two red regressions and original
independent failure evidence remain preserved. Independent revision-2 replay is
pending. The native harness now includes oversized whole-chunk save veto and
has eleven passing Python checks; no native phase has yet been executed.

Final independent revision-2 checkpoint: 20 JUnit and all 422 required
GameTests passed, with no open findings; see [review](reviews/REVIEW-02.md).
Root integrated the exact 21 author files and central proposals. The native
harness's additional refusal check has twelve passing Python tests and an
independent no-open-finding [review](reviews/HARNESS-REVIEW-02.md).
Root native execution and actual-client visuals remain unverified.

Corrective checkpoint: combined root 1,548 JUnit /441 GameTest pass. Native
attempt 2 exposed data loss despite refusal logs; the original failed chunks
remain archived in [incident evidence](../v1.8.0-c16a-save-guard/reviews/NATIVE-INCIDENT-01.md).
The lifecycle save-guard correction has an independent no-finding source review
and five passing focused tests. Fresh native attempt 3 passes all seven phases,
including byte-identical oversized chunk (four retained BE entries), absent
unrelated stone mutation, two bank restarts and one dropped-Item restart. The
historical input world and all input JAR identities remain unchanged. Independent
native outcome audit is pending; arbitrary-crash atomicity and real-client
visuals remain unclaimed. The final harness has 16 passing Python tests and
reviewed unchanged timeout/marker boundaries.

Integrated candidate checkpoint: native04 completes seven phases on58a5ab97…;
independent stopped-region extraction re-verifies the byte-identical oversized
chunk, four retained BEs and markerAIR, plus reduced-capacity and Item/bank
restarts. See [actual-result audit](../v1.8.0-c16a-integration/reviews/NATIVE-AUDIT-02.md).
Root units pass1,622, but fullGT03 fails12 Signature cases, so full integrated
regression remains open. This does not prove arbitrary-crash atomicity, every
future save refusal, visual/performance acceptance or a Required Gate.

Candidate11 checkpoint:1,638 JUnit /313 suites/all459 required GT pass and
DataGen written0. Fresh Tank06 passes all seven native phases on ea311ed0…;
independent stopped-region/Item decoding finds no evidence inconsistency.
All2,940 source inputs remain unchanged. The tank and capacity-setting units
have [dedicated development evidence](VERIFICATION.md); only their checked
module/regression/clean-stop scope is verified, not crash/client/Gate acceptance.
