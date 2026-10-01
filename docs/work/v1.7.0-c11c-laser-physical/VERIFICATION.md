# V170-LAS-02 (C11c) orbital laser drill, physical mode

Date: 2026-10-02. Scope: ADR-055 physical mode, the `laser_target` endpoint
and ADR-054 endpoint registration, removal and retirement. No release Gate is
claimed.

Base: `b571ac8` (the C11b evidence commit). Commits: `df344db`, `36afa15`,
`3006637` on `codex/v1.7.0-endgame-systems`. The evidence run used `3006637`.
The [implementation log](../v1.7.0-implementation-log.md) records the
decisions, including the per-link ID and the revoked links that refine the
ADR-055 §3 counters for the C11 review.

## Delivered

| Area | Contract | Result |
|---|---|---|
| Shaft geometry | ADR-055 §3 | Footprint in cell order, floor, chunk-edge rule; the C10 shaft vectors match |
| Classification | §3 | The first immune or fluid cell in order stops the layer; break events only after that |
| Payment counters | §3 | Debt paid layer by layer whatever the switches, credit only while breaking is allowed; all 97,656 event sequences up to seven events settle to one payment per layer |
| Registration | ADR-054 §9 | `AWAITING_WORLD_SAVE` until a chunk-save or chunk-load tag shows the ID; 32 per tick; limits and a full root retry; the tag's freeze flag is read |
| Removal | §9.1 | Buffer drops, the ID becomes a young tombstone; a returning copy is `ENDPOINT_RETIRED` and frozen in its own root |
| `laser_target` | ADR-055 §3, ADR-054 §9.1 | 27-slot extract-only buffer, cursor, link, reset; blast resistance 1,200, no pushing, wither and dragon immune, excluded from block movers |
| Link and selection | ADR-055 §3, ADR-054 §9 | Owner's `ACTIVE` targets in ID order (operators: any), rules 1–4, change rules, `LINK_ABANDONED` |
| Danger confirmation | ADR-055 §5 | Start then the same player's confirm within 200 ticks; mode and link changes stop the drill |
| Layers | §3, §4 | Chain steps 1–7 and the whole drop set before any change; any stop untouched and unpaid; 7 layers per tick |
| Visuals | §5 | Update tags only (`active`, `depth`; controller `active`): a 3-wide beam up to 384 blocks, the emitter glow, at most 8 particles per tick, none at minimal particles |
| Audit | §6 | Per layer, `DEBT_SETTLED`, `CREDIT_USED`, `LINK_ABANDONED`, `MARKER_RESET`, mode and link changes, status changes, protection notices |

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` at `3006637` | Exit 0, 309 s. **301 required GameTests** passed (299 earlier, 1 target, 1 physical). `git status --porcelain --untracked-files=no` after DataGen: empty |
| `gradlew test --rerun --no-daemon` | Exit 0, 138 s. **1,281 JUnit tests / 237 suites**, 0 failures, errors or skips |
| API jar against the C11b run | SHA-256 `24b6527e…5218ab` in both: no public API change |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

The full build log has **18 ERROR lines and 0 FATAL**, the same intentional
failure-injection set as the earlier runs.

Development GameTest runs before the commits are kept in `root-checks.zip`
under `attempt-01-failed`: a forced save hit vanilla's 10-second per-chunk
save cooldown; the fixture then posted the save before the target had
queued itself; the first link crashed the server tick (abandoning a link that
did not exist; fixed in `3006637`); and the next run failed station tests on
the world that crash left behind (a fresh world passed).

## Tests added

- JUnit: shaft vectors and classification order; counter steps, all crash
  cuts up to seven events, the named cuts and a debt larger than the buffer.
- GameTests: a target registers only after a save, drops its buffer and
  retires when removed, and a returning copy is frozen; on an Earth station a
  drill links a target in the Overworld through its menu, needs the
  confirmation, digs two layers of stone into the target, pays once per
  layer, and every stop (bedrock, water, a chest, a full buffer, a zone, an
  API veto, a break-event veto, the switch) leaves the layer untouched and
  unpaid; no chunk is loaded; a marker reset makes the drill lose its link.

## Not verified

- A marker whose surrounding chunks are not loaded (`TARGET_UNLOADED`): the
  GameTest area keeps them loaded; the chain test of C11a covers the step.
- Forced-stop recovery of the counters on a real server (S2, C13).
- The beam, glow, particles and screens on a real client (V1 is `[H]`).
- Multiplayer and performance (C13).

Next: C11d, the area gravity controller (ADR-058).
