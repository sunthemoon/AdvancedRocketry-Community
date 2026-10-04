# C16a-S1-001 rejected native chunk saves

Status: in-progress. Owner: root integrator. Independent reviewer: delegated
incident/source reviewer. Version: v1.8.0, main integration worktree/branch.

Observed failure: Tank native attempt 2 passed six clean upgrade/resource/Item
phases, then the oversized phase failed exact chunk preservation. Five save
refusals were logged, but native BlockEntity entries fell from four to zero and
the other stone mutation persisted. The failed original and actual chunks,
logs and JAR identity remain unchanged in the integration evidence directory.
The Java/GT successes do not establish this native guarantee.

Scope: retain the refusal across native unload/retry and shutdown, without
retaining resource copies, loading chunks, changing schemas or accepting
unsupported payloads. Root owns the new minimal persistence domain/Forge
bridge, Tank/Pump/combustion guard integration, focused tests and documentation.
Signature author scope stays read-only; any integration there is a later patch.
No new transaction framework, asset/config changes or release approval.

Proposed implementation boundary: one transient Level-owned bounded denial
record per rejected chunk, surviving that chunk's unload. The first reason is
retained; saturation refuses further writes for that Level instead of evicting
protection. Only ending the Level lifetime clears the record. Normal repair
requires an offline backed-up world and restart; ordinary saves do not reset it.
The exact internal API and cap are subject to actual-diff review, not frozen
by this document. Test-only fixture cleanup must not expose production recovery.

Verification: denial/retry/saturation/ownership unit tests; actual Forge event
handling after serialized carriers disappear; unchanged mandatory full build,
DataGen/GameTest; and the original native oversized oracle on a fresh historical
world copy, including byte-identical original chunk and absent stone mutation.
No timeout, assertion or existing performance budget may be relaxed.

- [x] Minimal corrective implementation and focused tests.
- [x] Independent source/incident review and regression replay.
- [x] Fresh native reproduction verifies the original byte-preservation oracle.
- [ ] Full root regression and portable failed/corrected evidence.

Actual checkpoint: independent exact ten-file review has no findings and five
focused JUnit tests pass. Root `clean build runData` passes 1,548 JUnit /296
suites; `runGameTestServer` passes all 441 required tests. Fresh native attempt
3 exits 0 with all seven phases passing. The rejected native chunk remains
byte-identical (SHA-256 `b5a1e68eb7a52fc47c87278bf67c92f0f327878c7c5ab057b41df9ca17c63199`),
four BE entries remain and the unrelated stone mutation is absent. The owner
input world matches all three attempt inventories. Independent native outcome
audit and portable root packet are pending; no checkbox or Gate is closed yet.

Final independent [native audit](reviews/NATIVE-CORRECTION-03.md) re-extracts
the stopped region and verifies all three chunk identities, four BE roots,
absent stone mutation, actual refusal/retry stacks and unchanged input-world/JAR
inventories. It closes H1 only for this exact Tank case; no independent native
replay or Gate is claimed. The three verified task boxes refer to this evidence,
not to Pump/Signature native recovery or arbitrary-crash guarantees.
