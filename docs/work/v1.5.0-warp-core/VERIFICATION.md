# V150-WARP-03 verification

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `9969550` (WARP-02). Contract:
[ADR-044](../../decisions/ADR-044-STATION-WARP-LOGICAL-RELOCATION.md) §2, §4 and §7.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`.
No release candidate, tag or human Gate approval. Independent review pending.

## Implemented scope

- **Warp core** (`advancedrocketrycommunity:warp_core`): a stateless terminal.
  - Its block entity stores only `schema_version=1`.
  - It exposes a receive-only Forge Energy view: `getEnergyStored` is 0,
    `getMaxEnergyStored` is the cap, and extraction is refused.
  - It accepts energy only in the fixed Space Level, on the server thread, inside
    a committed station region, while the registry is operational and not
    quarantined.
  - Acceptance is bounded by the station's balance plus pending credit (cap
    10,000,000 FE) and by 200,000 FE per station per tick; `simulate` changes
    nothing.
  - It drops as the plain block.
- **Pending credits** (`StationWarpCredits`): kept in memory, with at most one
  entry per station (4,096). They are folded into the registry (WARP-02
  `foldWarpCredits`):
  - every 200 server ticks;
  - before every quote, confirmation recheck and commit check;
  - in `ServerStoppingEvent` before the stop save.
- **COMMON config:**
  - `stations.warpEnabled` (default true);
  - `stations.warpCostInSystem` (default 2,000,000);
  - `stations.warpCostInterstellar` (default 8,000,000);
  - both costs are bounded to 100,000..10,000,000.
  - Defaults apply until the config is loaded.
- **Commands** (`StationWarpCommands`, under `/arce station warp`):
  - `<body>`: the ADR-040/041 local actor (connected player, own non-silent
    source, owner or operator standing in the committed region) must be looking
    at a warp core of that station (server ray pick, 5 blocks, loaded chunk). The
    target must be present, orbitable, known (ADR-043) and not the current orbit,
    with no cooldown or countdown running and the balance covering the cost.
    - The cost class is in-system or interstellar; interstellar also applies
      when leaving an unavailable orbit body.
    - The reply shows the station, bodies, class, cost and balance. It warns
      that docked rockets move and that a return warp costs again, and it warns
      when the target system has no rocket routes.
  - `confirm <station_id>`: a one-shot confirmation (one per player, 128 in
    total, 200 ticks, dropped on logout and at stop), bound to the actor, the
    observed state, the target, the cost and the class. It is rechecked in full
    before the countdown starts.
  - `cancel`: owner or operator, local rule.
  - `status`: owner, members or operators in the region; shows the balance after
    a fold, the settings and the countdown.
- **Countdown** (`StationWarpCountdowns`):
  - 200 ticks, at most one per station and 64 in total, memory only;
  - announced to online members in chat, and on the action bar at 10 s (at
    confirmation) and at 5, 3, 2 and 1 s;
  - discarded at stop; the owner logging out does not cancel it.
- **Commit:** at most one per server tick. It rechecks:
  - warp is enabled;
  - authority is available;
  - the station equals the confirmed state;
  - the actor is still the owner, or an operator (`getProfilePermissions >= 2`);
  - the quote recomputed from the current catalog and config has the same class
    and cost;
  - the balance covers the cost;
  - the rocket port reports nothing in motion.

  It then uses WARP-02 `checkedRelocation`. A commit or a failed write starts
  the 100-tick cooldown. Online members see the result or the abort reason.
- **Rocket port:**
  - `StationRocketAuthority` is fail-closed by default: every warp is refused
    with "rocket state is not yet known".
  - WARP-04 wires the rocket module's journal-based implementation. GameTests
    install a stub.
- **Content** (v1.5 DataGen, original work):
  - the model uses the project's machine-casing textures and references the
    vanilla `block/crying_obsidian` texture;
  - self-drop loot;
  - the shaped recipe is four machine casings, four advanced circuits and one
    data storage unit;
  - names in the `advancedrocketrycommunity_v150` language namespace;
  - complete v1.5 copies of `mineable/pickaxe` and `needs_iron_tool` add the core.
- **Packaging:** `build.gradle` excludes the superseded v1.2 copies of those two
  tags. The provenance note is updated (same modification date).
- **Refactors:**
  - `StationLocalActor` is now public for the warp package;
  - `V150StarSystemLanguageProvider` is renamed `V150LanguageProvider`;
  - `ConnectedTestPlayers` also captures action-bar messages.
- **Runtime bridge:** `StationWarpRuntime` is installed once and never cleared.
  The service clears its own state at stop.

Not in this slice:
- the real in-motion rule;
- the admission change for docked rockets;
- the deletion guard;
- passengers and logout GameTests (WARP-04);
- restart and native warp evidence (WARP-05);
- UI screens;
- sky.

## Tests

- `StationWarpStateTest` (6):
  - credit allowance, cap, simulation and bound;
  - confirmation one-shot, binding, expiry and capacity;
  - countdown uniqueness, capacity, announcements and commit order;
  - cost class from star systems, including evacuation;
  - settings and quote bounds.
- `CommonConfigTest`: now expects exactly five values, and a new test pins the
  warp defaults, bounds and fallback.
- `StationWarpGameTests` (5 tests, each in its own batch so the stub port never
  overlaps):
  - **charge**:
    - two cores of one station share the per-tick allowance;
    - simulation does not change anything;
    - a gap core, an Overworld core and an off-thread call get 0;
    - the energy view is as specified;
    - pending credit reaches the registry only through a fold, which dirties it;
    - deleting the station drops the balance.
  - **commit**:
    - the owner's quote text;
    - confirmation, with the member notified;
    - the exact action-bar sequence 10/5/3/2/1;
    - after the countdown, the station equals the old state with only the orbit
      changed, the balance is debited once, and the checked write is on disk;
    - the commit notice, then a cooldown on the next request.
  - **interstellar**: cost 8,000,000, the no-routes warning, the commit to Tau
    Ceti e, and `/arce station environment` showing `solar=0.50`.
  - **rejections**:
    - `/execute as` (non-local), member, not looking at a core, star, missing
      and same-orbit targets;
    - empty balance, with the numbers shown;
    - the unwired port fails closed;
    - the kill switch;
    - confirm or cancel with nothing pending;
    - member status and outsider status;
    - nothing changed.
  - **abort**: three concurrent countdowns, none of which warps or charges:
    - one is cancelled;
    - the station of one changes (`STATION_CHANGED`);
    - one is repriced by a config change (`WARP_QUOTE_CHANGED`).

Catalog reload or target removal during a countdown uses the same recomputed
quote as repricing. It is not exercised by a GameTest, because the GameTest
server cannot swap data packs; it stays a native item (WARP-05/MIG-02).

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew compileJava` / `compileTestJava` | Exit 0 |
| `gradlew runData` | Exit 0; eight new v1.5 files (warp core blockstate, block and item models, loot, recipe, recipe advancement, two tool tags) and the two v1.5 language files updated |
| focused `gradlew test --tests "*station*" --tests "*Station*"` | Exit 0; 119 tests |
| `clean build test runData runGameTestServer` run 01 | Exit 1; 1,007 JUnit, 1 failed: `CommonConfigTest` pinned two config values (intended change; updated to exactly five, plus a new warp-settings test) |
| `gradlew test --tests "*CommonConfigTest"` | Exit 0 |
| same, run 02 | Exit 0, 3 m 30 s; all 245 GameTests passed. The abort GameTest gained its repricing case while this run was in progress, so run 03 is the evidence run |
| same, run 03 | Exit 0, 3 m 42 s; `:test` executed, 1,008 JUnit / 181 suites, 0 failures; all 245 GameTests passed (240 existing plus 5 warp); commit outcomes in the log: 2 `WARP_COMMITTED`, 1 `STATION_CHANGED`, 1 `WARP_QUOTE_CHANGED` |
| `git add src/generated/v1.5` then `gradlew runData`, `git diff --exit-code -- src/generated` | Exit 0 and 0: DataGen reproduces the staged output exactly |
| packaged JAR inspection | One copy each of `mineable/pickaxe` and `needs_iron_tool`, 12 entries each including the warp core; core model, recipe, loot and both names present (`packaging-check.json`) |

Main JAR `10a21dad540cd88f6b25be98c06e1f39a285ac630edc4e3a684a4929d0f222d7`; the API JAR
is byte-identical to WARP-02 (`97d1aaad…`). Failed run 01 is retained.

## Evidence archive

Source directory: `arce-v150-warp03-*` under the Windows Temp directory (named in
`evidence-archives.json`). `root-checks.zip` holds:
- the focused and full Gradle logs, including the failed run 01;
- the run-02 GameTest log;
- the final JUnit XML and GameTest logs;
- the source, generated-file and artifact hashes;
- the packaging script.

The member index, `source-identity.json`, `links.json` and `SHA256SUMS.txt`
bind this directory and the staged tree.

## Risks

- Until WARP-04, production warps are always refused (fail closed); the
  end-to-end commit is proven only with a stub port.
- Warp settings are read from the COMMON config at each check. A server that
  edits the file during a countdown aborts it (by design: new consent).
- Pre-existing, not changed here: `StationRuntime`, `RollingMachineRuntime`,
  `PrecisionAssemblerRuntime` and `SatelliteRuntime` are cleared at server stop
  but installed only at mod construction. A second integrated-server session in
  one client JVM therefore loses them. The warp bridge avoids this; the others
  are recorded for a separate fix.
