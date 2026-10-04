# C16a-04 classic fluids and whole-unit gas canisters

Date: 2026-10-03. Status: verified (development only). Contract: accepted ADR-064 revision 2,
sections 5 and 9. Implementation: isolated worker; central integration: root.

Scope: five registered fluid types; non-placeable oxygen/hydrogen/nitrogen;
rocket fuel and enriched lava sources, flows and buckets; stable 16-stack,
1,000 mB canister swaps; gas-giant nitrogen table and new-volcano enriched lava.
No pump, tank, classic machine family, C17/C18 runtime or release approval.

Checks: pure bounded planners, Forge capability and conservation tests,
mandatory build/DataGen/GameTests, native save/restart, original-art screen
and independent actual-diff review. Existing canister IDs/stack sizes and suit/
vent behavior must remain compatible. Direct multi-stack fill/drain refuses;
callers detach one unit and preflight its destination before exchanging fluid.

- [x] Root registration/data/asset integration.
- [x] Automated checks and deterministic DataGen (1,487 JUnit /410 GT).
- [x] Native saved-world restart receipt (four phases, two restarts).
- [x] Independent review and evidence (17 JUnit /410 GT, native packet audit).

See [verification](VERIFICATION.md). V1/V2 and version Required Gates remain
open; tanks, pumps and the other C16 leaves are not completed by this task.
