# TEST-REPORT — v0.8.0

## Candidate identity

- Implementation commit: `a3b4192d37c524687a0a26bf12d075a8ec6c1e99`
- Build: `1.20.1-0.8.0-dev`
- JAR: 1,166,061 bytes, 723 entries
- SHA-256:
  `0ce6c6bf9eb603f5973f35c19a47b295454a1f8c74ee74a6a99af3c2627a1937`

## Results

| Check | Result |
|---|---|
| Two independent clean builds | PASS, byte-identical |
| Java unit tests | PASS, 247/247 |
| Targeted Python tests | PASS, 133/133 |
| Forge GameTest | PASS, 44/44 required |
| DataGen | PASS, 21 exact generated files, no diff |
| JAR/content audit | PASS, 723 entries |
| Common/server client-class boundary | PASS |
| Packaged first start and same-world restart | PASS |
| Two-owner satellite/restart/exact-once flow | PASS |
| 100 logical missions | PASS, scheduler passes 32/32/32/4 |
| Two simultaneous clients/shared authority marker | PASS |
| Server restart and both clients reconnect | PASS |
| JFR scheduler performance window | PASS |
| Critical/High authority findings | 0 |
| Pull-request checks | PENDING |
| Merge-commit clean reproduction | PENDING |

The strict repository validator is run after the candidate bundle and exact
checksum inventory are complete; its final count is recorded in
`evidence/automated/summary.json`. The final candidate also passed the
generic packaged-server lifecycle and a distinct satellite-specific packaged
run against the same JAR.

## Notes

One preliminary concurrent user-development client launch timed out while the
other OpenGL client was active. It is not accepted evidence. Both accepted
cycles connected `ClientA` and `PilotB` simultaneously, received the same
server marker, disconnected cleanly, and repeated that result after an actual
server restart.
