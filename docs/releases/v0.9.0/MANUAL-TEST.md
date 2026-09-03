# Manual and dedicated acceptance — v0.9.0 Beta 1

## Bound candidate

- Build: `1.20.1-0.9.0-beta.1`
- Tested implementation commit:
  `f6cd77cebdb0a851cab76accbf66de565473b545`
- JAR SHA-256:
  `fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad`
- Reviewer: repository owner `sunthemoon`
- Review date: `2026-09-03`

## Executed observations

| Flow | Expected | Actual evidence | Result |
|---|---|---|---|
| Packaged first start and restart | same world, exact JAR, clean stop | dedicated-server summary and extracts | PASS |
| Alpha-to-Beta upgrade | five roots backed up byte-exactly, migrate to schema 2, continue after restart | migration summary, manifest, lifecycle | PASS |
| Forced stop at durable transfer checkpoint | restart keeps one authority and exact material/inventory/fuel | recovery summary and ledger | PASS |
| Forge baseline/latest with JEI present/absent | all four clients join; optional adapter remains optional | four-cell compatibility summary and extracts | PASS |
| Core Earth–Moon, station, atmosphere, machine, and satellite regressions | server-authoritative behavior remains within fixed bounds | 44 GameTests, 273 JUnit tests, packaged core runs | PASS |
| Maximum combined workload | 2,048-block rocket, 16 vents, 10 stations, 100 missions | combined scenario and two-hour soak record | PASS |
| Localization and resources | both catalogs match; references are case-exact; color states have text | resource audit | PASS |
| Core client rendering | unchanged world, multiplayer, atmosphere, suit HUD, and terminal visuals remain accepted | immutable v0.8.0 screenshots plus ADR-013 | PASS |
| Optional JEI presentation | category loads one synchronized Electrolyzer recipe | both JEI-present client extracts report `recipes=1` | PASS |

## Owner decision and visual boundary

The owner directly confirmed G0, G8, and G9 for this exact candidate. No new
v0.9.0 screenshot is claimed. ADR-013 explicitly binds unchanged core visuals
to the original v0.8.0 screenshot paths and hashes; current Forge/JEI clients,
DataGen, localization/resource checks, and the side-boundary scan supply the
v0.9.0 execution evidence. A fresh exact-candidate, multi-scale visible-client
record is mandatory for v1.0.0.

G9 owner approval authorizes publishing the candidate as a GitHub pre-release
after required PR checks, merge, and exact post-merge reproduction. It does not
permit labeling this Beta as stable.

## High-risk review

- Known Critical findings: `0`
- Known High findings: `0`
- Permanent satellite or transfer chunk tickets during acceptance: `0`
- Client-authored flight, station, mission, research, or migration outcome: `0`
- Forced-stop authority after restart: exactly `1`

Accepted residual limits are listed in [`KNOWN-ISSUES.md`](KNOWN-ISSUES.md).
