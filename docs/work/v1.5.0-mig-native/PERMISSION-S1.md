# v1.5 multiplayer permission report: S1 subset (native, development copy)

ADR-046 UI-03, level S1: a named subset of the matrix on the packaged JAR and a native
dedicated server, with the fixture probe's connected players. It complements the A1
report ([PERMISSION-MATRIX.md](../v1.5.0-elevator-ui/PERMISSION-MATRIX.md)). V2 (two
real clients) stays open for ACC-02. This is evidence for the maintainer, **not a Gate
approval**.

## Identity

- Host `advancedrocketry-community-1.20.1-1.5.0-dev.jar`, SHA-256 `a12db9de4ed0c2e549ebbf4abbabd84bbb7b8e2106bccd9e0fa71d308162fb6d` (the JAR of full run 02).
- Fixture `arce-adapter-compat-test-1.0.0.jar`, SHA-256 `db6e890b10fae8f2e807de8fb73ebab66a79e1c05bf1fbb3afa84505c7d81f60`, built from `src/adapterTest`.
- Minecraft 1.20.1, Forge 47.4.10, Java 17.0.7, native dedicated server; phase `upgrade` of
  `native_mig_c3_check.py` (attempt 10), exit 0.
- Station: MIG-A `5198587e-19b5-43cc-b66c-949d80ac65ad`, created by the v1.4 host and upgraded to root 4.

## Actors

| Actor | Construction |
|---|---|
| owner | probe player 0, the station's owner (v1.4-created), on the platform |
| member | probe player 1, invited natively by the owner and accepted |
| invitee | probe player 2, invited natively by the owner |
| outsider | probe player 3, no relation |
| console | the server console (operator) |

All four players are connected over embedded channels, stand on the platform and issue
the commands from their own sources. Every player command left the loaded chunk count
unchanged (checked by the harness for each run).

## Cells (in execution order)

| # | Action | Actor | Command | Result | Last reply | Expected | Pass |
|---|---|---|---|---|---|---|---|
| 1 | invite | owner | `arce station invite 5198587e-19b5-43cc-b66c-949d80ac65ad probe1` | 1 | Invitation recorded for probe1; station=5198587e-19b5-43cc-b66c-949d80ac65ad | owner allowed; member refused | PASS |
| 2 | accept | member | `arce station accept 5198587e-19b5-43cc-b66c-949d80ac65ad` | 1 | Station invitation accepted; station=5198587e-19b5-43cc-b66c-949d80ac65ad | the invited player only | PASS |
| 3 | invite | member | `arce station invite 5198587e-19b5-43cc-b66c-949d80ac65ad probe3` | 0 | Station action rejected: Station action is unauthorized | owner allowed; member refused | PASS |
| 4 | invite | owner2 | `arce station invite 5198587e-19b5-43cc-b66c-949d80ac65ad probe2` | 1 | Invitation recorded for probe2; station=5198587e-19b5-43cc-b66c-949d80ac65ad | owner allowed; member refused | PASS |
| 5 | expand | owner | `arce station expand` | 1 | Expand MIG-A id=5198587e-19b5-43cc-b66c-949d80ac65ad from region 768,768..1279,1279 to 640,640..1407,1407. ... | owner allowed (confirmation issued); everyone else UNAUTHORIZED | PASS |
| 6 | expand | member | `arce station expand` | 0 | Station expansion rejected: only the owner or an operator standing in the station can manage it | owner allowed (confirmation issued); everyone else UNAUTHORIZED | PASS |
| 7 | expand | invitee | `arce station expand` | 0 | Station expansion rejected: only the owner or an operator standing in the station can manage it | owner allowed (confirmation issued); everyone else UNAUTHORIZED | PASS |
| 8 | expand | outsider | `arce station expand` | 0 | Station expansion rejected: only the owner or an operator standing in the station can manage it | owner allowed (confirmation issued); everyone else UNAUTHORIZED | PASS |
| 9 | warp | owner | `arce station warp advancedrocketrycommunity:moon` | 0 | Station warp rejected: the station's warp energy does not cover the cost (in-system warp costs 2000000 FE; ... | owner reaches the quote (balance 0: energy does not cover the cost); everyone else UNAUTHORIZED | PASS |
| 10 | warp | member | `arce station warp advancedrocketrycommunity:moon` | 0 | Station warp rejected: only the owner or an operator standing in the station can manage it | owner reaches the quote (balance 0: energy does not cover the cost); everyone else UNAUTHORIZED | PASS |
| 11 | warp | invitee | `arce station warp advancedrocketrycommunity:moon` | 0 | Station warp rejected: only the owner or an operator standing in the station can manage it | owner reaches the quote (balance 0: energy does not cover the cost); everyone else UNAUTHORIZED | PASS |
| 12 | warp | outsider | `arce station warp advancedrocketrycommunity:moon` | 0 | Station warp rejected: only the owner or an operator standing in the station can manage it | owner reaches the quote (balance 0: energy does not cover the cost); everyone else UNAUTHORIZED | PASS |
| 13 | status | owner | `arce station warp status` | 1 | Warp status; station=5198587e-19b5-43cc-b66c-949d80ac65ad orbit=advancedrocketrycommunity:earth energy=0 FE... | owner and member; invitee and outsider refused | PASS |
| 14 | status | member | `arce station warp status` | 1 | Warp status; station=5198587e-19b5-43cc-b66c-949d80ac65ad orbit=advancedrocketrycommunity:earth energy=0 FE... | owner and member; invitee and outsider refused | PASS |
| 15 | status | invitee | `arce station warp status` | 0 | Station warp rejected: only the owner or an operator standing in the station can manage it | owner and member; invitee and outsider refused | PASS |
| 16 | status | outsider | `arce station warp status` | 0 | Station warp rejected: only the owner or an operator standing in the station can manage it | owner and member; invitee and outsider refused | PASS |
| 17 | remove_uuid | member | `arce station remove 5198587e-19b5-43cc-b66c-949d80ac65ad uuid 00000000-0000-0000-0000-0000000c3a02` | 0 | Station action rejected: Station action is unauthorized | owner allowed; member refused | PASS |
| 18 | remove | owner | `arce station remove 5198587e-19b5-43cc-b66c-949d80ac65ad probe1` | 1 | Member removed immediately; station=5198587e-19b5-43cc-b66c-949d80ac65ad | owner allowed | PASS |
| 19 | accept | invitee | `arce station accept 5198587e-19b5-43cc-b66c-949d80ac65ad` | 1 | Station invitation accepted; station=5198587e-19b5-43cc-b66c-949d80ac65ad | the invited player only | PASS |
| 20 | remove_uuid | owner | `arce station remove 5198587e-19b5-43cc-b66c-949d80ac65ad uuid 00000000-0000-0000-0000-0000000c3a03` | 1 | Member 00000000-0000-0000-0000-0000000c3a03 removed immediately; station=5198587e-19b5-43cc-b66c-949d80ac65ad | owner allowed; member refused | PASS |
| 21 | admin | owner | `arce station admin inspect 5198587e-19b5-43cc-b66c-949d80ac65ad` | -1 | Incorrect argument for command at position 13: ...e station <--[HERE] | no player reaches the admin command; the console does | PASS |
| 22 | admin | member | `arce station admin inspect 5198587e-19b5-43cc-b66c-949d80ac65ad` | -1 | Incorrect argument for command at position 13: ...e station <--[HERE] | no player reaches the admin command; the console does | PASS |
| 23 | admin | outsider | `arce station admin inspect 5198587e-19b5-43cc-b66c-949d80ac65ad` | -1 | Incorrect argument for command at position 13: ...e station <--[HERE] | no player reaches the admin command; the console does | PASS |
| - | admin | console | `arce station admin inspect <A>` | 1 | region=... pad=... | operator allowed | PASS |

Totals: 24 cells, 24 PASS, 0 FAIL.

A non-operator's admin command fails at parse ("Incorrect argument for command ... station"),
so the `admin` node is not reachable for them. The warp `status` for a member and the
refusals name their reason in the reply.

This report is not a Gate approval.
