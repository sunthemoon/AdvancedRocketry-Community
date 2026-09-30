# V150-MIG-01a verification: missing orbit body, natively

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `2900e66`. Obligation:
[ADR-042](../../decisions/ADR-042-STATION-04-NATIVE-SCOPE.md) item 2, the
missing-orbit-body case. The rocket-identity case is still open (MIG-01b).
Development evidence only: v1.5 and G0-G9 remain `IN_PROGRESS`; independent
review pending.

## What was run

`native_missing_orbit_check.py` ran three finite dedicated-server processes on a
copy of the retained genuine v1.4 world. The copy is from
`arce-v140-mig-worlds…/server-final/world`, which is root 2 with two v1.4-written
stations. Its second station (`bca68d47…`, "Removed orbit witness") orbits Mars.

Before the first start, the world's existing fixture data pack (`migration_probe`)
hides Mars and Venus, with their routes, through a pack filter. It uses the v1.4
migration fixture's own `set_removal` helper against the v1.5 host JAR. The host
is the WARP-05 build (`a99f78c0…`, unchanged since `a96569b`); the consumer
fixture is `e3cddd87…`.

1. **upgrade-missing**:
   - the pre-start migration upgrades the v1.4 world with Mars absent
     (`managed=4, migrated=1`);
   - the station keeps its record and its Mars orbit ID;
   - the start reports exactly one `ARCE_STATION_UNKNOWN_ORBIT_BODY` for it (the
     only blocking log finding, and an expected one);
   - `environment`, run as the owner, shows
     `orbit=advancedrocketrycommunity:mars (unavailable)`;
   - the owner charges a warp core through the public Forge Energy capability
     (40 × 200,000 FE);
   - the evacuation quote is `interstellar warp, cost 8000000 FE` (ADR-044 §4:
     leaving an unavailable body costs the interstellar price);
   - the confirmed countdown commits to Earth, and the balance reaches 0;
   - the other station (Moon) is unchanged.
2. **restart-missing** (Mars still hidden): no migration and no unknown-body
   report. The station orbits Earth and the registry is identical.
3. **restored** (filter removed, Mars back): both stations are as after the
   evacuation and the registry is identical. Every other authority is unchanged
   apart from probe-player visit records, and the source world is unchanged.

The evacuated record equals the v1.4 record with only the record schema (1 to 2)
and the orbit (Mars to Earth) changed.

The first phase had 22 WARN lines, all non-project:
- mod-file and loader notices;
- Forge assigning new registry IDs to `warp_core` in a world saved before the
  block existed;
- the first-start config defaults;
- a version-difference notice;
- offline mode.

## Attempts

| Attempt | Result |
|---|---|
| 01 | Exit 1, harness: the probe does not allow `arce station environment`. Migration and the unknown-body report had already matched. The harness now runs the read-only command through `execute as <owner> at <owner>` |
| 02 | Exit 1, harness: the console `setblock` ran before the joining player's chunk ticket applied ("That position is not loaded"). The placement is now wrapped in `forceload add` and `forceload remove` (test setup, as in STATION-04) |
| 03 | Exit 0, PASS as above |

Failed attempts are retained in `attempt-01-failed/` and `attempt-02-failed/`.
No product code changed for this slice.

## Evidence archive

`root-checks.zip` holds:
- the harness;
- the three attempts' outputs (per-phase stdout, logs, receipts, commands, probe
  reports and decoded state);
- the removal pack metadata;
- the summary.

The disposable `station-server/` copies are excluded.

## Not covered

- Rocket identities (MIG-01b).
- Real clients.
- A body removed while its station has a countdown running (unit logic: the quote
  is recomputed at commit, and a changed class cancels it).
- Crash cuts.
