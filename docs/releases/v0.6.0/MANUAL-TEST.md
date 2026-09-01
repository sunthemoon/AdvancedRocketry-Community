# MANUAL-TEST — v0.6.0

## Bound candidate

- Build: `1.20.1-0.6.0-dev`
- Tested implementation: `6a293f705e939a67b5b617b1dfaa7deef4d6d7b6`
- Main JAR SHA-256:
  `cb8d34e797a57e94a1efb595af8dace6f40072cf0d96715a3d8db73a3518668d`
- Tester / approver: `sunthemoon`
- Date: `2026-09-01`

## Executed flow

1. Start the packaged Forge server on loopback with the bound JAR.
2. Query status, save, stop, and restart the same world cleanly.
3. Execute 20 Earth-Moon-Earth round trips: 40 production flight legs.
4. Verify an exact fuel debit on every leg, one logical authority after every
   transfer, five rocket blocks, and 17 diamonds plus 64 iron ingots.
5. Stop and restart at ASSEMBLED, FUELED, COUNTDOWN, ASCENT,
   TRANSIT_PREPARED, DESTINATION_SPAWNED, DESCENT, and LANDED.
6. Disassemble after the final return and verify exact material/container
   conservation.
7. Join the packaged server simultaneously with `Dev` and `PilotB`, both from
   the exact tested implementation commit. Confirm both receive the same
   generation-1, 469-byte celestial snapshot and shared server marker.
8. Disconnect both clients, save, and stop the server cleanly.

## Result and approval

All recorded steps passed. The two-client record proves simultaneous modded
connectivity and shared server state; flight concurrency, hostile intents,
passenger recovery, blocked landing, feedback events, and two-rocket isolation
also pass the 39-test Forge GameTest suite.

No screenshot or video is claimed. Repository owner `sunthemoon` directly
approved G8 and G9 with this limitation disclosed and accepted under
[`ADR-007`](../../decisions/ADR-007-V060-VISUAL-EVIDENCE-ATTESTATION.md). The
canonical attestation is
[`evidence/manual/owner-attestation.json`](evidence/manual/owner-attestation.json).
