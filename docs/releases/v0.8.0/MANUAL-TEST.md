# Manual and dedicated acceptance — v0.8.0

## Bound candidate

- Build: `1.20.1-0.8.0-dev`
- Implementation commit: `a3b4192d37c524687a0a26bf12d075a8ec6c1e99`
- JAR SHA-256:
  `0ce6c6bf9eb603f5973f35c19a47b295454a1f8c74ee74a6a99af3c2627a1937`
- Reviewer: `sunthemoon`
- Review date: `2026-09-03`

## Executed observations

| Flow | Expected | Actual evidence | Result |
|---|---|---|---|
| Manufacture through discovery | assemble, launch, wait, claim once, discover | ordered seven-frame terminal sequence plus packaged ledger | PASS |
| Restart during mission | same mission and deadline resume | satellite server summary and lifecycle log | PASS |
| Two owners | separate missions, claims, and discoveries | mission ledger | PASS |
| Duplicate claim | second request changes nothing | `ALREADY_CLAIMED` ledger entries | PASS |
| 100 offline missions | bounded deadline work, no chunk ticket | satellite and JFR summaries | PASS |
| Invalid access/input | no owner bypass or client-authored outcome | authority matrix and GameTests | PASS |
| Two simultaneous players | both receive one server state marker | multiplayer logs and screenshots | PASS |
| Restart and reconnect | both identities return to unchanged shared state | reconnect logs | PASS |
| Client rendering | world, other player, atmosphere/suit HUD render | final candidate screenshots | PASS |

The owner directly confirmed G0, G8, and G9 for this internal milestone. The
complete terminal sequence is an ordered pre-candidate screenshot set, not a
continuous video. It is paired with final-candidate multiplayer screenshots,
packaged logs, automated terminal authority tests, and the exact candidate
hash under the v0.8.0-only ADR-011 decision.

## High-risk review

- Critical/High satellite authority findings: `0`
- Client-authored mission duration, yield, target, coordinate, or research:
  rejected by the server boundary
- Mission completion work: at most `32` completions and `64` queue inspections
  per scheduler pass
- Permanent satellite chunk tickets: `0`

## Accepted limitations

See [`KNOWN-ISSUES.md`](KNOWN-ISSUES.md) and
[`ADR-011`](../../decisions/ADR-011-V080-VISUAL-EVIDENCE-SEQUENCE.md).
