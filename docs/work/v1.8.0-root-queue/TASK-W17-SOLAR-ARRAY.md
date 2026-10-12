# TASK W17: solar array

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w17-solar-array` |
| Branch | `codex/v1.8.0-w17-solar-array` (local only; Root merges) |
| Ledger plan | C17c (`solararray`, `solararraypanel`, `ENERGY.solarGeneratorMultiplier` if not already done) |
| Record | `docs/work/v1.8.0-root-queue/records/W17.md` |

## Goal

The multiblock solar array generates FE from the same server-side irradiance rules as the
single solar generator (already merged), on planet surfaces and inside operational
stations. It never spends client brightness, and it stops at buffer overflow without
wrapping.

## Read

- ADR-065 (`docs/decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md`):
  - section 9 in full: the solar generator and array, surface eligibility, station
    exposure, warp countdown policy;
  - section 2 (the `solar_array` and `solar_array_panel` IDs);
  - section 11.
- The merged single solar generator (`machine/solar/**`,
  `docs/work/v1.8.0-c17c-solar-generator/`) and its protection. Reuse its irradiance
  resolver and buffer rules; do not fork them.
- The classic multiblock kernel (`machine/classic/runtime/**`) or the v1.2 pattern kernel,
  whichever §9 and the existing solar code imply for the array's structure.

## Scope

1. **Blocks:** the `solar_array` controller and `solar_array_panel`, with the pattern
   §9 gives.
2. **Generation:**
   - the shared irradiance function: planet surface (loaded catalog and body, a
     sky-visible controller top, server daytime, the rain and thunder reduction);
   - station: the orbit body's intensity, and the collector face exposed toward space;
   - a missing orbit body shows as unavailable;
   - the warp guard follows the §9 recommendation.
3. **Buffer:** a bounded FE buffer. Overflow stops generation, with no wrap and no negative
   FE.
4. **Menu:** actual generation, FE, the sky, day, structure and body reason. Scalar server
   sync only.
5. **Data:**
   - models and original textures;
   - loot and recipes;
   - en_us and zh_cn text;
   - JEI presentation where §9 asks for it.

   Follow WORKER-RULES for configuration counts and log expectations.

## Tests (required)

- JUnit: the irradiance function (day, night, rain, thunder, station exposure, missing
  body) and the buffer bounds.
- GameTests:
  - formation and refusal;
  - generation on a surface at day and none at night;
  - generation in a station;
  - buffer overflow stops without loss;
  - save and load keeps the buffer.

  Pace any loading wait with `GameTestTickPacer`.
- The whole unit suite once. At the end: `runData` and `runGameTestServer` (under the
  lock), then the log checker. Report the GameTest totals.

## Not in this task

- The beacon (W18).
- The satellite bay (it waits for W11).
- The orbital solar transmitter (ADR-049).
- Status files and the ledger.
