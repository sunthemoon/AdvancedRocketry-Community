# TEST-REPORT — v0.7.0

## Candidate identity

- Implementation commit: `e1c2db8ca3e67ae7f92fbbbbd5b6c23a25f7412f`
- Build: `1.20.1-0.7.0-dev`
- JAR: 1,009,631 bytes, 636 entries
- SHA-256:
  `4c049a4e0c2a74f78d383af7bc56ad31d746f8b7f8872cbc7258c58981d9c068`

## Results

| Check | Result |
|---|---|
| Two independent clean builds | PASS, byte-identical |
| Java unit tests | PASS, 220/220 |
| Python discovery | PASS, 634 passed, 4 skipped, 0 failed (638 discovered) |
| Forge GameTest | PASS, 42/42 required |
| DataGen | PASS, five exact generated files, no diff |
| JAR/content audit | PASS, 636 entries |
| Common/server client-class boundary | PASS |
| Packaged first start and same-world restart | PASS |
| Ten-station allocation/restart/deletion | PASS |
| Earth ↔ station and Moon ↔ station | PASS |
| Two simultaneous clients/shared authority marker | PASS |
| Atmosphere fixed-tick performance run | PASS |
| Critical/High station findings | 0 |

The full Python discovery completed in 2,028.46 seconds. The strict repository
validator reported 39 passed checks, 0 warnings, and 0 failures. Both results
are bound in `evidence/automated/summary.json` after the v0.7.0 release and Gate
validators were included.

## Notes

Local dual-window user-development launches showed intermittent Forge login
timeouts until the connected client window was minimized; the accepted run
then connected both clients and closed them cleanly. No media is claimed. See
`KNOWN-ISSUES.md` and ADR-009.
