# TASK W18: planetary beacon and beacon finder

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w18-beacon` |
| Branch | `codex/v1.8.0-w18-beacon` (local only; Root merges) |
| Ledger plan | C17c (`beacon`, `beaconFinder`) |
| Record | `docs/work/v1.8.0-root-queue/records/W18.md` |

## Goal

A player places a planetary beacon. A player who wears or holds a beacon finder gets that
beacon's bounded marker snapshot from the server and sees it as a waypoint. Ownership,
visibility and the finder's equipment integration follow the ADR exactly.

## Read

- ADR-065 (`docs/decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md`):
  - section 7 in full: it is the specification;
  - section 2 (the `planetary_beacon` and `beacon_finder` IDs);
  - section 11.1: the `classic_controls` channel, index 2 `BeaconMarkerSnapshotPacket`,
    which W07F already registered with its frozen codec and does not yet send.
- ADR-066 §5.1 and the merged equipment code:
  - `beacon_finder` occupies one of the existing HEAD module slots;
  - the server-only `EquipmentSummary.beaconFinderEnabled`, which W03 already defines.

  C17 owns the waypoint policy and the selection between held and worn finders.
- The merged `classic_controls` code (`classiccontrol/network/**`) and the W07/W07F records.

## Scope

1. **`planetary_beacon` block and block entity:**
   - owner and visibility as §7 defines;
   - a bounded registry per Level, with no world scan.
2. **`beacon_finder` item:**
   - works when held, or worn as a HEAD module through the merged equipment code;
   - never as a second registration;
   - the equipment summary is the only source for the worn case.
3. **The marker snapshot:**
   - the server sends `BeaconMarkerSnapshotPacket` (index 2), bounded and only to eligible
     players, at the rate §7 sets;
   - the client caches and renders waypoints, in the client package only;
   - no beacon coordinates of other owners' private beacons leak.
4. **Data:**
   - models and original textures;
   - loot and recipes;
   - en_us and zh_cn text.

   All through data generation.

## Tests (required)

- JUnit: the marker snapshot codec (using W07F's frozen codec) and the visibility filter.
- GameTests:
  - placement and ownership;
  - a held finder and a worn finder receive the right markers;
  - an unequipped finder receives none;
  - private beacons are not sent to others;
  - the send rate is bounded.
- The whole unit suite once. At the end: `runData` and `runGameTestServer` (under the
  lock), then the log checker. Report the GameTest totals.

## Not in this task

- The solar array (W17).
- The satellite bay.
- Any change to the frozen channel table.
- Status files and the ledger.
