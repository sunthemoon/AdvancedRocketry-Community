# V170-BH-01 (C12a) black-hole generator

Date: 2026-10-02. Scope: ADR-057, the singularity and fuel data, the burn
rule, the black-hole generator and the Cygnus X-1 example system. No release
Gate is claimed.

Base: `8d52d7f` (the C11d evidence commit). Commits: `7b062c6` (data, burn
rule, settings, example content) and `e5d267a` (generator block, block
entity, menu, screen, accretion disc, pattern, GameTest) on
`codex/v1.7.0-endgame-systems`. The evidence run used `e5d267a`. The
[implementation log](../v1.7.0-implementation-log.md) records the decisions
for the C12 review.

## Delivered

| Area | Contract | Result |
|---|---|---|
| Data | ADR-057 §1–§2 | `singularities` (≤ 4 KiB, 16 files) and `black_hole_fuels` (≤ 8 KiB, 16 tables), strict schema-1 codecs with raw-byte versions; the set is cross-checked (one profile per body, every named fuel table present) and replaced all or nothing on reload |
| Eligibility | §2 | A profile applies only to a live, orbitable, not landable body; the generator burns only in a committed station whose live orbit body has a profile (`NO_SINGULARITY` otherwise) |
| Burn rule | §4–§5 | One item is taken only while the buffer is not full; its rate is fixed when it starts and bounded by 8,192 FE per tick; the burn pauses (`PAUSED_FULL`) when the whole rate does not fit and nothing is wasted; the C10 vectors match |
| Generator | §3 | 3 × 3 × 3 structure-only multiblock (Endgame Casing, obsidian sides and back, crying obsidian core); 9 plain-item fuel slots (stacks with NBT are refused, also through automation); 2,000,000 FE buffer |
| Output | §3 | Up to 20,000 FE per tick pushed to loaded neighbours in a fixed face order, or pulled through the capability; both share the per-tick output; no `receiveEnergy`; no chunk is loaded |
| Settings and admission | §5 | `energyPercent` 10..400, 4 active per owner, 64 on the server (COMMON values); ineligibility releases the active place |
| Station moves | §2 | A warp away pauses with the burn state kept; a warp back resumes it |
| Menu and visuals | §6 | Energy bar, rate, remaining burn and status with its stable code; no buttons; update tag `generating` drives a 48-quad accretion disc (192 vertices) |
| Audit | §6 | Status changes and a summary every 1,200 ticks |
| Content | ADR-054 §16 | Cygnus X-1 (public, orbitable, not landable root body without a Level), its 500 FE per tick singularity, the legacy default fuel table, the `black_hole_generator` recipe, model, loot, tool tags and translations |

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` at `e5d267a` | Exit 0, 168 s. **304 required GameTests** passed (303 earlier, 1 generator). `git status --porcelain --untracked-files=no` after DataGen: empty |
| `gradlew test --rerun --no-daemon` | Exit 0, 124 s. **1,295 JUnit tests / 239 suites**, 0 failures, errors or skips |
| API jar against the C11d run | SHA-256 `24b6527e…5218ab` in both: no public API change |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

The full build log has **18 ERROR lines and 0 FATAL**, the same intentional
failure-injection set as the earlier runs.

Development GameTest runs before the generator commit are kept in
`root-checks.zip` under `attempt-01-failed` (runs 16–19). Two tests failed in
each: the generator stayed `NO_FUEL`/`UNFORMED` because vanilla stops a
Level's block entities 300 ticks after it has neither players nor forced
chunks (region tickets do not count), so the fixture now forces its chunk;
and the packaged-body GameTest pinned eight bodies while Cygnus X-1 makes
nine, so the pin was updated to the exact new count. Neither changed
production code.

## Tests added

- JUnit (`BlackHoleTest`, 4 tests): strict codecs and raw-byte versions; the
  cross-checked set and the orbitable, not landable rule; the burn state
  machine against the reference vectors; the rate fixed per item and bounded
  by 8,192. `CommonConfigTest` counts the three new values (48 in total).
- GameTest: a generator orbiting Earth idles with `NO_SINGULARITY` and burns
  nothing; at Cygnus X-1 it generates 500 FE per tick from one item; a warp
  away pauses and keeps the burn; a warp back resumes and pushes to a
  neighbour; a full buffer pauses without consuming; a named stick is refused
  as fuel; the switch pauses it.

## Not verified

- The accretion disc and the screen on a real client (V1 is `[H]`).
- Push into third-party energy receivers (only the mod's own receiver is
  used in the GameTest).
- Generators at the active caps and their cost (C13).

Next: C12b, the ADR-054 §11 transit ledger. The C11 review findings are fixed
before it, one commit each.
