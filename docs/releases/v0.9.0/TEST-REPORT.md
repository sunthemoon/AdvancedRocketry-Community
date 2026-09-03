# TEST-REPORT — v0.9.0 Beta 1

## Candidate identity

- Tested implementation commit:
  `f6cd77cebdb0a851cab76accbf66de565473b545`
- Build: `1.20.1-0.9.0-beta.1`
- JAR: 1,225,536 bytes, 758 entries
- SHA-256:
  `fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad`

Later evidence/harness commits do not change distributable inputs. Every
packaged record remains bound to the frozen tested implementation and hash.

## Candidate results

| Check | Result |
|---|---|
| Two independent Java 17 clean builds | PASS, byte-identical |
| Java unit tests | PASS, 273/273 |
| Forge GameTests | PASS, 44/44 required |
| Deterministic DataGen and clean worktree | PASS |
| JAR/content audit | PASS, 758 entries |
| Common/server client-class boundary | PASS, 0 findings |
| Localization/resource audit | PASS, 220 files and 231 bilingual keys |
| Packaged first start and same-world restart | PASS |
| Five-root schema-1-to-2 backup/migration/restart | PASS |
| Migrated-world Electrolyzer restart and completion | PASS, exact outputs |
| Migrated-world Earth–Moon continuation | PASS, 20 trips and 8 restart cases |
| Forced process stop and exact recovery | PASS, one authority |
| Forge 47.4.10 and 47.4.23 build/GameTest lanes | PASS, 273/273 and 44/44 each |
| JEI 15.56.0.205 present/absent on both Forge lanes | PASS, 4/4 clients joined |
| Maximum combined two-hour soak | PASS |
| Critical/High findings | 0/0 |
| Owner G0/G8/G9 acceptance | PASS, with ADR-013 visual boundary |
| Pull-request checks | PENDING |
| Merge-commit exact reproduction | PENDING |
| GitHub pre-release asset verification | PENDING |

The maximum combined run uses a 2,048-block rocket, 16 vents, 10 stations,
100 missions, four status clients, periodic saves, and a same-world restart.
The committed performance summary supplies exact duration and sample metrics.

The compatibility server was loopback-only and temporarily disabled online
authentication because Forge user-development clients have no Mojang session.
That setting is test-only. All four clients joined and disconnected cleanly;
both JEI-present cells reported exactly one recipe, absent cells remained
optional, and no cell reported an unknown recipe category or project
ERROR/FATAL finding.
