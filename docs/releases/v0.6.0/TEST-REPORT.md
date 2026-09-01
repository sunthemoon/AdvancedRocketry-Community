# TEST-REPORT — v0.6.0

## Automated verification

| Check | Result |
|---|---|
| Two cache-disabled clean builds | PASS; byte-identical main JAR |
| Java unit tests | PASS; 204 passed, 0 failed, 0 skipped |
| Python repository tests | PASS; 613 run, 609 passed, 4 historical-artifact skips |
| JAR audit | PASS; 591 entries, 0 findings |
| Common/server client-import scan | PASS; 0 findings |
| Celestial identity scan | PASS; 0 findings |
| DataGen | PASS; 11 v0.6 resources, clean worktree |
| Forge GameTests | PASS; 39 passed, 0 failed |
| Repository strict validation | PASS; 0 warnings, 0 failures |

The four Python skips are exact-artifact checks for historical versions whose
old local JARs are not present. They are not v0.6 behavior skips.

## Packaged server, persistence, and multiplayer

- Generic lifecycle: first start, status, save, clean stop, and same-world
  restart passed without project error or client-class linkage failure.
- Flight: 20/20 round trips and 40/40 legs passed with exact fuel debits,
  inventory/material conservation, and one logical authority per leg.
- Recovery: 8/8 lifecycle restart checkpoints passed; the four authority
  presence combinations passed in Forge GameTest.
- Multiplayer: two matching visible Forge clients joined the packaged server
  simultaneously, received the same server marker/snapshot, and shut down
  cleanly. The disclosed user-development-client and no-media limitation is
  accepted by ADR-007.

Exact structured results and filtered logs are under [`evidence/`](evidence/).
