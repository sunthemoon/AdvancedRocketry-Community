# V150-ORBIT-01 / ORBIT-02 verification

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `89600aed700a984cb078ca92236da7a5a02a4e36` (STATION-04).
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`.
No release candidate, tag, real-client result or human Gate approval.
[ADR-041](../../decisions/ADR-041-STATION-ORBIT-ENVIRONMENT.md) is **PROPOSED**:
its independent contract review is pending, as is review of this implementation.

## Implemented scope

- `StationOrbitEnvironmentResolver`: one indexed `findAt` plus one catalog
  lookup. Inside a committed region it yields orbit body (and whether it is in the
  active catalog), configured gravity, effective gravity `min(configured, 4.0)`,
  vacuum, the orbited body's solar intensity (shared Space's if the body is
  missing, 0 with no catalog) and the sun angle. Gaps, other Levels and a blocked
  registry resolve nothing.
- `CelestialGravityController` takes a position-gravity function; the station
  module supplies it at wiring time, so celestial does not depend on station.
  Players in a station region use its effective gravity; elsewhere the Level
  profile applies as before. Existing stations store 0, identical to Space.
- `/arce station gravity <0-100>` sets the configured gravity (percent of 1 g)
  under the same local-actor rule as expansion. It is written through the checked
  commit shared with expansion: validate, stage, force, read back, atomic replace,
  then publish. The registry model now accepts only region or environment changes
  in a checked update; identity, owner, name, cell, pad, orbit and team changes are
  rejected.
- `/arce station environment` shows station, orbit (availability), effective
  gravity (with the configured value when clamped), vacuum, solar and sun angle.
- Refactor, no behavior change for expansion: the actor rule moved to
  `StationLocalActor`; result types were renamed `StationManagementCode/Result`;
  `CheckedExpansion` became `CheckedUpdate` (`COMMITTED`/`UNCHANGED`);
  `EXPAND` became `MANAGE_STATION`; audit keys `ARCE_STATION_EXPANSION` and new
  `ARCE_STATION_GRAVITY` now also log `gravity_milli`. The unauthorized and
  non-local messages now say "manage" instead of "expand".
- Public API 1.7 is unchanged: the API JAR is byte-identical
  (`50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf`) and
  `EnvironmentQueries` still reports the configured station gravity.

Not in this slice (ADR-041 non-goals): per-station sky and client sync
(ORBIT-03), exposure changes in Space, solar power, orbit mutation, warp.

## Tests

- `StationOrbitEnvironmentResolverTest` (5): stored gravity and orbited-body solar,
  clamp at exactly 4.0 and above, missing body and missing catalog fallbacks,
  zero-gravity default, effective ≤ configured invariant.
- `StationCheckedUpdateTest` (renamed from `StationCheckedExpansionTest`; 10):
  adds the gravity checked commit (live state unchanged during the move,
  published and reloaded, unchanged/stale/refused-move results, schema bound) and
  rejects owner/team changes through the checked path.
- `StationOrbitGameTests` (Forge GameTest, connected mock players): member and
  `/execute as` gravity changes rejected; the owner's command sets 35 % with an
  unchanged loaded-chunk count and the checked file on disk; a repeat is reported
  unchanged; the gravity attribute is 0.08×0.35 inside the region, 0 in a Space gap
  and 0.08 in the Overworld; a catalog reload changing the Moon's solar intensity
  is reflected on the next query; the display shows station, orbit, gravity,
  vacuum and angle.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew compileJava compileTestJava compileAdapterTestJava` | Exit 0 |
| `gradlew test --tests '*station.*' --tests '*persistence.migration.*' --tests '*celestial.*' --no-build-cache` | Exit 0; 241 tests / 37 suites |
| `clean build test runData runGameTestServer` run 01 | Exit 1; JUnit 967/174 passed; the new GameTest compared against a station state captured before its member was added (test bug) |
| same, run 02 | Exit 0, 157 s; 967 JUnit / 174 suites executed (`:test` ran), 0 failures; all 239 required GameTests passed; generated files unchanged |

Run 02 audit lines include gravity SET 1, UNCHANGED 1, NOT_LOCAL_PLAYER 1,
UNAUTHORIZED 1, and the unchanged expansion GameTest codes. The GameTest log keeps
15 ERROR lines, all from the missing `server.properties` and existing deliberate
fault injections; zero FATAL. Development JARs: main
`3aeb1e7050362e408f7cf9835c347027ca0779e93ada54f7e957342415b3db2f`, API unchanged.

## Evidence archive

Source: `C:/Users/Administrator/AppData/Local/Temp/arce-v150-orbit-a59be71836d64d11a8c15d66a4c0b009`.
`root-checks.zip` (401 members, SHA-256
`39595a09146f84caf49237f597d509bec7ba08076ff705899a1aea3dcb3bcd43`) holds the
compile, focused and both full-run logs with their JUnit XML and GameTest logs,
source/artifact hashes, the JUnit/audit summary and the generic packaging script.
`root-checks-files.json` indexes it; `source-identity.json`, `links.json` and
`SHA256SUMS.txt` bind the staged commit. The packer asserted that every tested
file and JAR matches the tree. After the documentation edits the repository
(45 passed), planning, provenance and planning-regression (15) validators and
`git diff --check` exited 0; their logs are in `packaging/out/`, not the ZIP.

## Not covered; risks

- Native dedicated-server gravity set/restart readback is not yet run.
- Gravity attribute synchronization to a real client and movement feel are not
  observed (no real client); the attribute is Forge's synchronized one.
- Non-player entities keep Level gravity, as before.
- ADR-041 and this code await independent review; G0-G9 remain open.

## Independent review and fixes

An independent reviewer reviewed ADR-041, `f27b11a` and the confirmation of
`442e4a7`; its unmodified report, logs, bytecode listings and probe sources are in
`independent-review.zip`. Verdict: ADR-041 "accept with changes". It found no
Critical or High issues. It confirmed:

- edge and negative-coordinate gravity (32 probes);
- no effect on non-station positions and no write for an unchanged value;
- the expansion refactor preserves decisions, and the API JAR is byte-identical;
- the `442e4a7` non-silent check is correct for Forge 47.4.10, and its GameTests
  fail for the right reason.

Its full `gradlew test` executed 967 tests with 0 failures.

| ID | Severity | Finding | Fix |
|---|---|---|---|
| M1 | Medium | `replaceChecked` accepted any region/environment change (shrink, vacuum/angle) | `StationState.isCheckedUpdateOf`: only 512→768 growth with an unchanged environment, or a gravity-only change; the persistence path refuses anything else before writing; tests reject shrink, vacuum, angle, and growth combined with gravity |
| M2 | Medium | Every gravity command rewrites and fsyncs the whole registry with no rate bound | One committed write per station per 100 ticks (`StationWriteCooldown`, bounded and cleared at stop); an unchanged value writes nothing and starts no cooldown; ORBIT-04 measures the commit time |
| M3 | Medium | "Actual solar behavior" had no home | ADR-041 "Solar scope in v1.5": display plus the effective value; no power machine exists; the leaf is retitled |
| L1 | Low | "No gravity change for existing worlds" was too broad | ADR-041 lists the three cases in which a station region now differs |
| L2 | Low | ADR-028 not amended explicitly | ADR-041 "Relation to ADR-028" |
| L3 | Low | Test gaps | Pure `resolveAt` with edge/negative/other-Level/blocked-registry tests; the GameTest posts a real `LivingTickEvent` to the production-registered controller; the API reports the configured 5.0 while physics clamps to 4.0; the cooldown is covered by a GameTest and unit tests |
| L4 | Low | ADR-042 was accepted without review, contained a vacuous rocket bullet, and the STATION-04 leaf text was stale | Review record added; the bullet now says there is no native rocket evidence; the leaf is corrected |
| L5 | Low | `environment` showed a station's name and UUID to any visitor | Only the owner, members and operators see them, as with `/arce station list` |
| L6 | Low | ORBIT-02 was committed before ADR-041 was accepted | Recorded in the implementation log and the ADR acceptance record |

It also noted that diagnostic keys changed to `ARCE_STATION_UPDATE_*` and that
some reply texts now say "manage"; both are listed in this report.

Fix round (`review-fix-checks.zip`, base `442e4a7`):

| Command | Result |
|---|---|
| `gradlew compileJava compileTestJava compileAdapterTestJava` | Exit 0 |
| focused `test` (`station`, `persistence.migration`, `celestial`) with `--no-build-cache` | Exit 0; 245 tests / 38 suites |
| `clean build test runData runGameTestServer` | Exit 0, 163 s; `:test` executed, 971 JUnit / 175 suites, 0 failures; all 239 GameTests passed; generated files unchanged; API JAR unchanged |

Gravity audit lines in this run: SET 1, UNCHANGED 1, COOLDOWN 1,
NOT_LOCAL_PLAYER 1, UNAUTHORIZED 1. The main JAR is
`a5ee7b109df8250f5e4d0847a859d556dbfe4a729a2dce1d0c3299aefb8764d6`.
