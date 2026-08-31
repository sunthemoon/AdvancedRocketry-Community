# MANUAL-TEST — v0.5.0

## Bound candidate

- Build: `1.20.1-0.5.0-dev`
- Tested implementation: `eae8d9224c708924930b781d7332eb69b6a4bf8d`
- Main JAR SHA-256:
  `45782780eeec54f1710cee4425f96b4d0152d29590559f519130ca9f227f0ba0`
- Tester / approver: `sunthemoon`
- Date: `2026-09-01`

## Executed player flow

1. Start the packaged Forge server on loopback with the bound JAR.
2. Assemble a motor, seat, guidance computer, and chest through the
   server-authoritative manager command path.
3. Save and stop the server, then restart the same world.
4. Confirm the same entity UUID, snapshot hash, and complete entity data after
   restart.
5. Join with the matching Forge 47.4.10 user-development client.
6. Confirm all four blocks render as one cached `RocketEntity` after restart.
7. Disconnect, save, and stop cleanly.
8. Stage a pre-commit transaction, restart again, and confirm exact block and
   chest-item recovery with the journal cleared.

## Result

All steps passed. The restored chest retained 17 diamonds and 64 iron ingots.
The persisted entity data was byte-equal across restart, and the staged stale
transaction restored four blocks without a duplicate entity or durable lock.

The canonical machine-readable record is
[`evidence/client/manual-evidence.json`](evidence/client/manual-evidence.json).
The visible result is
[`evidence/client/screenshots/rocket-render.png`](evidence/client/screenshots/rocket-render.png),
with privacy-filtered lifecycle excerpts under
[`evidence/client/logs/`](evidence/client/logs/).

The visible recording predates a GameTest-only one-tick scheduling correction;
the final candidate's packaged server and recovery flow were rerun, while all
rocket runtime classes and resources remained byte-identical. The repository
owner directly approved G8 and G9 against this disclosed bundle. The
user-development client boundary and single visible client are retained in
[`KNOWN-ISSUES.md`](KNOWN-ISSUES.md), not hidden from the acceptance record.
