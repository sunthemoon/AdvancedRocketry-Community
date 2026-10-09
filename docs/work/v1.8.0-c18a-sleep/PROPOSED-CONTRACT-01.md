# C18a actual off-world sleep: proposed contract 01

Status: **PROPOSED. Not frozen, approved, implemented or runtime-verified.**

Research baseline: `96236acb682c060796e41d3837af7991d492d3b1`, Minecraft 1.20.1,
Forge 47.4.10, Java 17. This proposal covers one v1.8 vertical slice. It does not
close the wider C18 respawn, actors, equipment or safety-return units.

## 1. Product assumption and scope

The owner's recorded target is genuine sleeping in breathable off-world rooms,
conditional on separate freezing and review of dimension, spawn and time behavior.
The minimal proposed interpretation is **the native sleeping lifecycle, without
new global night-skipping or opt-in planetary respawning**. Sleeping pose, bed
occupancy, native counter/quorum, wake synchronization and native sleep
statistic/criterion are real; successful off-world sleep need not turn the shared
Overworld clock to morning. This distinction must be stated in player-facing docs.

The recommended first delivery is a default-policy sleep slice, not the entire
ADR-066 section 3.3 implementation:

- Supported packaged hosts: Moon, Space, Mars, Venus, Tau Ceti f and Tau Ceti g.
- Actual server-known breathable air is necessary, regardless of suit oxygen,
  creative immunity or the future `forcePlanetRespawn` option.
- Ordinary off-world bed use does not replace the player's existing native
  respawn point. Overworld bed behavior is unchanged.
- No new persistent sleep object, native spawn-field migration, private-player
  field access, custom sleeping packet, global time service or atmospheric scan.
- Planetary respawn options, old off-world spawn-record migration, arbitrary
  data-pack hosts, remote room loading, custom beds and safety return are separately
  scoped C18 work, not silently marked implemented by this slice.

These are reviewable proposed defaults, not inferences that the owner's short
reply already authorized every policy. If actual sleep must also skip the shared
night or preserve the old random compass/clock behavior, that changes the contract
and must be resolved before source assignment. No additional owner question is
intrinsically required to review this minimal native-lifecycle proposal.

## 2. Integration choice and startup boundary

Recommended candidate: set `natural=true` on only the six packaged off-world
dimension types, then use ordinary Forge events for additional admission and
wake checks. Keep `bed_works=true`, `respawn_anchor_works=false`, all other flags,
IDs, heights, effects, generation, mappings and native time ownership unchanged.
Moon/Space retain `fixed_time=18000`; other packaged surfaces retain their cycles.

This is a deliberate native semantic change, **not a sleep-only flag**:

1. Ordinary non-lodestone compasses obtain the current Level's shared spawn
   target instead of an absent target. They do not point to the player's bed or
   station landing pad. Native DerivedLevelData shares spawn coordinates with
   the primary data; no safe landing is implied by that compass target.
2. Vanilla clocks use the Level's time-of-day rather than the non-natural random
   value. Moon/Space therefore show their fixed presentation time.
3. Native random-ticking Nether portal blocks can attempt zombified-piglin
   spawning where their remaining gamerule, difficulty and floor checks permit.
   It is `STRUCTURE` spawning, not proof of natural atmosphere-spawn admission.

These effects must be explicit in the eventual ADR and independent review. Do
not silently replace vanilla compass/clock item properties or broadly cancel
STRUCTURE mob spawning to disguise them. Such preservation work would be an
additional compatibility contract, not a trivial sleep patch. If these effects
are rejected, retain this proposal as an alternative, do not implement it anyway.

Data types load at startup; a catalog `/reload` must not mutate a running Level's
dimension type. Verify fresh-world startup and upgrade/restart of a copied prior
world. Never delete dimension directories or remap a ResourceKey. Do not promise
that editing a generated JSON alone replaces a saved world's actual registry
holder: inspect the started Level and the world's saved registry outcome.

Avoid editing historical v0.3/v1.4 authoring helpers in a way that changes older
generation outputs accidentally. Prefer a v1.8 authoritative replacement for the
six types plus explicit old-copy exclusions, or a similarly reviewed precise
provider change. The packaged JAR must contain exactly one resource per type ID.
Both `processResources` and `sourcesJar` exclusions are integration-owned.

## 3. Admission: native action with an extra air boundary

Use the native bed interaction and `ServerPlayer.startSleepInBed` path. Do not
call `LivingEntity.startSleeping` directly. `PlayerSleepInBedEvent` may supply a
failure but cannot bypass the subsequent native `natural` check by returning null.
`SleepingTimeCheckEvent.ALLOW` alone also cannot bypass that earlier check.

The server event adapter must check its installed server thread before mutable
queries, exact connected nonspectating living ServerPlayer identity (not a
FakePlayer), current actual Level identity, finite/in-bounds positions, loaded
full chunks, and current supported-host/catalog identity. Preserve a prior mod's
failure; never clear cancellation or manufacture successful native actions.

Normalize the real bed head through the native interaction. Validate its loaded
state and expected native BedBlock/FACING/PART properties before deriving cells;
unknown/custom bed implementations are not silently admitted by a tag alone.
No client supplies a final head, breathability, station, profile or safe position.

Perform bounded air checks on:

- The player's actual entry eye cell; and
- The native intended sleeping eye cell at the normalized bed head.

Primary native pose math puts the player at `(head.x+.5, head.y+.6875,
head.z+.5)` and sleeping eye height at `.2`, so the unmodified sleeping eye is
inside the **head block's own cell**, not automatically `head.above()`. Native
fixtures must pin this against the real player pose; compatible eye-height
extensions require the actual pose-derived value, not an unchecked magic offset.
Deduplicate equal cells. This is at most two atmosphere lookups per admitted
attempt, never a new room scan. Both queried chunks must be loaded before calling
the manager: ambient breathability currently short-circuits its loaded-cell test.

Each sampled cell must be known `BREATHABLE`. `VACUUM`, `PENDING`, unsupported or
missing mapping/profile, malformed coordinates and unloaded data refuse. A valid
supplied sealed room may override exterior air through the existing manager.
Do not use `PlayerProtectionStatus.SUIT_PROTECTED`, HUD cache, profile name,
station vacuum flag or the orbit body's surface atmosphere as a substitute.

Surface profile identity must be unambiguous. Shared Space air comes from the
actual Space host and valid supplied volumes, never the orbited surface. Inside
a committed station, revalidate VISIT using the live operational registry and
existing access service; a blocked registry cannot be treated as an unrestricted
gap. A loaded supplied room outside any committed region may satisfy the air
rule while the existing independent adrift/safety-return policy remains applicable;
sleep is not a station ownership or safety-return exemption. This preserves the
accepted air rule without inventing an all-Space Earth atmosphere.

After these checks, leave native alive/already-sleeping, distance, obstruction,
occupied-bed, monster, time and gamerule behavior intact. Do not add an ALLOW to
force daytime sleeping on cyclic surfaces. A prior compatible mod's time result
may stand if air is valid; air refusal overrides any time ALLOW.

The added event must not let a rejected air attempt set a spawn. Explain refusal
with localized bounded server text distinguishing pending/unavailable from
nonbreathable air. One message per ordinary attempt; no per-tick log warning.

## 4. Continued sleeping and wake

Use a server-side wake check on the native sleep-time check or a correctly ordered
player tick adapter. While actually sleeping, sample the **actual current eye**
cell and revalidate live host/access/loaded state. On nonbreathable, pending or
unavailable state, return DENY through the native sleep-time check or call the
normal `ServerPlayer.stopSleepInBed(false, true)` wake path. Do not merely toggle
pose, clear one bed half or send a client-only wake packet.

The guarantee is waking on the first native player check after the authoritative
service reports lost protection; it is not zero latency from an arbitrary world
write that has not yet invalidated that service. Test door edits, wall damage,
provider removal, supply exhaustion, chunk unload, boundary/profile reload and
missing context. The existing oxygen/exposure engines continue; sleeping grants
no free oxygen, damage immunity, artificial healing or climate control. There is
no second debit or independent suffocation timer.

Bed removal, manual leave-bed, death, logout, player replacement, teleport and
server stop must use/retain native lifecycle behavior. Any small narration/rate
cache is per-server and bounded by connected players, removed at the applicable
lifecycle event. No static player map or offline sleep continuation.

On saved-player reload, the inspected native ServerPlayer reader stops a saved
sleeping pose. Respawn fields survive independently. Do not claim that restart
resumes an in-progress sleep or that an in-memory sleeping counter is durable.
Verify occupancy reconciliation after disconnect and restart with actual beds.

## 5. Players, dimensions, time and weather

Retain native per-ServerLevel SleepStatus and `playersSleepingPercentage`.
Spectators do not vote; native creative behavior is preserved except for the new
air requirement. Default 100% requires all active players **in that Level**.
Two stations in Space share one quorum; they do not get independent nights.
Players on separate surfaces do not share a sleep quorum.

Native deep sleep is 100 player sleep ticks. If the native quorum is met, the
Level executes its native completion and wakes its sleepers. With percentage
greater than 100, or insufficient sleepers, completion need not occur; manual
wake and loss-of-air/day checks still work. Preserve native gamerule effects,
including completion's wake even when `doDaylightCycle=false`.

The inspected MinecraftServer creates non-Overworld Levels with DerivedLevelData
and `tickTime=false`. Their day/game time reads delegate to primary data, whereas
their time/weather setters are no-ops. Consequently this candidate adds **no
Overworld time/weather write** for off-world sleep. No ARCE listener forwards
SleepFinishedTimeEvent to the primary world. Native Overworld sleep can advance
the shared clock, affecting cyclic off-world surfaces' observed time; they may
wake at daylight under the native check. Moon/Space fixed visual time is unchanged.

Do not describe Moon/Space as ordinary day/night cycles: Level.isDay and isNight
both return false with fixed time, and the native time check's default `!isDay`
permits the bed-time check. Native night completion does not change the fixed sky.

No simulated machine/satellite/vent catch-up, offline oxygen debit, duration-to-
gameTime conversion, per-station clock or cross-Level global sleep quorum is added.
Record gameTime, dayTime, actual data type, sky/fixed-time values and rain/thunder
state in every Level before/after completion, including simultaneous sleepers.
Bytecode expectations must be verified in started production Levels, not proved
by a fabricated ServerLevel with different data ownership.

## 6. Spawn saving, death and invalid beds

### 6.1 Default-only sleep slice

The accepted C18 defaults are `allowPlanetRespawn=false` and
`forcePlanetRespawn=false`; neither switch exists in the inspected runtime source.
The first sleep slice implements that default behavior for **ordinary bed-caused
spawn updates only**. Use cancellable PlayerSetSpawnEvent in the exact ordinary
sleep action context to stop that update without changing old native respawn
fields. Successful sleep must not be refused merely because spawn saving is
denied. Explain that rest succeeded but the home respawn remains unchanged.

Cancellation occurs before native spawn fields and the native set-spawn message
change. Native startSleepInBed tries to set spawn **before** daytime and monster
admission; tests must cover those failed native attempts as well as successful
sleep. Scope the adapter to the ordinary sleep operation, not all spawn operations:
do not accidentally veto null resets, Overworld updates, the Nether, operator
`/spawnpoint`, respawn restoration or other mods' administrative actions. Any
short action marker must be bounded, same-thread, non-reentrant and clear even
when native admission fails or another listener throws; prefer evidence-backed
event context over a lasting UUID flag.

**Technical input still open before source assignment:** the exact cause
discriminator/wrapping mechanism is not established by PlayerSetSpawnEvent's
payload. The inspected native method has a distinct caller but the event itself
has only player, Level, position and forced flag. A successful admission flag
that persists after an earlier range/obstruction failure is not sufficient.
Prove and independently review a cause-scoped public interaction boundary or
another precisely bounded mechanism first; record its effect on canceled
interaction, item-first/secondary-use behavior, other listeners, exceptions,
operator spawnpoint and respawn restoration. No AT, private-field access or
blanket veto is authorized by this outcome-only paragraph. This proposal is
not implementation-assignment-ready until that technical input is frozen.

The preserved native point is still saved through the existing player data fields
`SpawnDimension`, `SpawnX/Y/Z`, `SpawnAngle`, `SpawnForced`. There is no new schema
or SavedData merely to remember sleep. A removed/obstructed preserved Overworld
bed follows native invalid-bed fallback and notification. Off-world bed removal
only invalidates sleep, not the preserved home point.

Existing off-world **native** spawn records created by operators, old tests or
other mods are not erased or silently reinterpreted here. Vanilla can already
use a bed there because respawn selection tests bedWorks, not natural. This slice
does not supply ADR-066's future air/access/load-safe respawn policy for those
records and must not claim the planetary-respawn ledger unit complete. Prior-world
qualification must identify this residual case explicitly; no migration or
save-veto may be improvised under the sleep write scope.

### 6.2 Separate prerequisite for opt-in planetary respawn

Before exposing `allowPlanetRespawn=true`, separately freeze and independently
review a pre-native-selection or safe-fallback-first integration and old-record
migration. `PlayerRespawnEvent` alone is too late to prevent vanilla from reading
the target and selecting/consuming its spawn block. `PlayerSetSpawnEvent` alone
does not revalidate a bed after the room, owner, pack or loaded state changes.

Required semantics remain ADR-066: real saved bed/working anchor, actual destination
profile, station permission, bounded loaded safe arrival; allow requires air,
force bypasses air only. Default false retains safe Overworld fallback. Do not
equate the product force-air option with native SpawnForced, which also permits
non-bed positions. Existing packaged anchors stay disabled; no new anchor support
follows from enabling sleep.

A viable research alternative is a bounded, schema-versioned per-player off-world
target while native fields retain an Overworld fallback, followed by checked
post-native respawn relocation to an already-loaded safe target. It avoids using
an unsafe off-world native lookup, but adds persistence, clone, old-world migration,
teleport/activation-footprint and packet ordering obligations. It is **not frozen**
here. The preserved target must survive unavailable air/pack/permission and be
recoverable, not silently cleared; future/corrupt/oversized data protection first
needs the AGENTS risk-register/ADR process. Direct late teleport from an unchecked
native off-world respawn is not the same design.

## 7. Authority, budgets and presentation

No custom C2S sleep request or channel/protocol change. Native use/leave-bed traffic
remains transport; server events and live services decide air, permissions and
mutation. Never accept arbitrary coordinates/NBT or force-load missing cells.
Tests must invoke the actual connected player's normal bed use, not only direct
domain calls. Respect Forge interaction vetoes and prior sleep failures.

Constant-size admission/wake observations reuse existing manager/index budgets:
4096 cells/volume, 256 task inspections/tick, 1024 Level inspections/tick,
64 scan jobs, 128 indexed volumes, 65536 indexed cells, 8192 dirty positions,
1024 tracked vents; no limit is raised. Admission starts no scan. Wake adds at
most one actual-eye observation per sleeping player check, plus fixed-size live
identity/access checks. No new connected-player cap silently makes some sleepers
unmonitored; measure the additional O(connected sleeping players) cost within
existing reference-load budgets rather than inventing a large scheduler.

Refusal and wake messages are server-generated localized text, with state-change
or per-attempt bounded narration. Native pose/bed occupancy/wake animation and
position synchronization remain presentation authority. LifeSupportClientCache
does not admit sleep. No Minecraft/Forge art is copied. Common/server code must
not reference net.minecraft.client. Compass/clock/native bed UI consequences need
real GPU and two-client acceptance later; unattended clients are not V1/V2.

## 8. Proposed write scopes and delivery stages

No implementation scope is assigned by this report. Suggested future ownership:

| Task | Exclusive proposed writes | Verification dependency |
|---|---|---|
| Sleep domain/Forge adapter | new `atmosphere/sleep/**`, new sleep-specific unit/GameTests, its own task record | frozen contract and native API facts |
| Integrator | AdvancedRocketryCommunity listener/lifecycle wiring; precise config/lang/datagen providers; generated six type replacements; build exclusions; docs/status/ledger/ADR | reviewed adapter and generation diff |
| Independent reviewer | no repository writes; exclusive evidence leaf | actual proposed contract/diff and bounded reruns |
| Opt-in respawn | separately declared player persistence/respawn package and fixtures | separate finalized recovery/migration contract, not this sleep source assignment |

First establish the missing exact spawn-update cause boundary, then qualify
native admission, air refusal/wake and default spawn preservation as
one default slice. Then implement any separately frozen respawn slice. Each
implemented slice still needs its own clean build/test/DataGen/native and finite
packaged/restart evidence. Neither short slice closes v1.8 G0-G9; full acceptance
remains governed by ADR-018 and the version's Required Gates.

## 9. Verifiable test matrix (all unexecuted)

| ID / layer | Fixture and action | Contract observations to record |
|---|---|---|
| S01 A0 | Admission table: actual/target eye air, unavailable, malformed/unsupported hosts, identity/thread | deterministic refusal codes; no mutation/provider callback for non-admitted actor |
| S02 A1 | Real registered bed interaction in supplied room on each of six actual packaged Levels | real SLEEPING pose, head occupancy, actual eye cell, counter, native stat/criterion; no direct-start substitute |
| S03 A1 | Entry eye/head cell differ; only one breathable, then both; head border/full-chunk edge | both cells required; exact sleeping eye math; zero new chunks/tickets |
| S04 A1 | Native daytime, obstruction, distance, occupied bed, monster, dead/spectator, prior Forge refusal | native refusal retained; no ALLOW bypass; respawn point unchanged |
| S05 A1 | Vacuum/PENDING/missing host, creative, charged/empty/full suit, force-air future option | air refusal independent of protection and future respawn policy; no item/oxygen debit by sleep adapter |
| S06 A1 | Open door/break wall, remove vent, exhaust supply while player sleeps | native wake on first check after authoritative loss; bed released; oxygen/exposure not duplicated |
| S07 A1 | Unload relevant provider chunk; reload boundary/profile; delete/replace supported mapping | unknown state wakes; no forced load; no stale atmosphere/HUD authorization |
| S08 A1 | Station owner/member/outsider/removal; operational and blocked registry; supplied gap room | live VISIT inside region; blocked fails closed; Space air never from orbit surface; existing adrift behavior not disabled |
| S09 A1 | Two connected players same Level; 100%, 50%, >100%; spectator; disconnect | exact native quorum/count/counter and wake; departed player not retained |
| S10 A1 | Two stations in Space and two separate surface Levels | one Space quorum, separate surface quorums, no cross-Level voting |
| S11 A1/S1 | Off-world completion with Overworld awake; every Level time/weather snapshots; daylight/weather rules true/false | no ARCE primary time/weather mutation; native local wake; fixed sky retained |
| S12 A1/S1 | Simultaneous Overworld and off-world sleepers; Overworld native night completion | shared read clock reacts to actual primary skip; surface daylight wake; no duplicate skip or weather rewrite |
| S13 A1/S1 | Preset valid Overworld spawn, successful/failed off-world sleep, save/stop/restart/death | byte-equivalent intended Spawn fields; default respawn stays home; no new sleep root |
| S14 A1/S1 | Remove/obstruct preserved Overworld bed; remove off-world bed during sleep | native home invalid-bed fallback/notification; normal off-world wake/recovery; no infinite sleeping occupancy |
| S15 S1/S2 | Save while sleeping, disconnect, stop/restart, reconnect; finite interrupted save fixture | no resumed stale pose, leaked cache or occupied bed; original player/world data retained; native durability limits reported |
| S16 S1 | Prior-world copy with native off-world bed/forced spawn records | no silent clearing/remap; exact preserved residual behavior reported; does not count as safe planetary-respawn acceptance |
| S17 A1/S1/V1 | Ordinary/lodestone/recovery compasses and clocks in six Levels; world spawn moved | ordinary compass current-Level shared spawn; other variants unchanged; clocks fixed/cyclic as specified; screenshots and hashes |
| S18 A1/S1 | Loaded off-world Nether portal with controlled random/difficulty/gamerules; canceled Forge spawn | native natural-gated STRUCTURE attempt characterized; air-spawn policy not falsely credited; no unrelated suppression |
| S19 A0/A1 | Resource generation and packaged registry readback in fresh/prior world | exactly one type resource/ID, all flags except reviewed natural delta identical; started types match; deterministic DataGen |
| S20 S1/V2 | Two real clients sleep/wake/reconnect concurrently, native leave-bed, permission/air changes | consistent pose/occupancy/messages/time/compass; bounded network/logs; dedicated sidedness |
| S21 S1 | Existing reference workload with sleepers, all manager counters and MSPT; cleanup lifecycle | no scan creation/extra force load; existing budgets retained; mean/P95/P99 within quality budgets, not empty-server evidence |
| R01 separate respawn | allow/force truth table, invalid/missing/unloaded/unsafe/permission-changed target; restart | air bypass only; no native target load before check; preserved target/fallback; no unsafe teleport/anchor consumption |
| R02 separate respawn | old native fields, future/corrupt target, clone, save-order interruption | explicit migration/recovery with bounds; no data erasure; no scope or Gate credit to default sleep |

All implementation checks remain unexecuted in this research assignment. Required
version commands remain `clean build`, `test`, `runData`, `git diff --exit-code`
and `runGameTestServer`, plus affected packaged save/restart/security checks.
They are not NOT_APPLICABLE for implementation; they were prohibited here.

## 10. Primary references

See REPORT.md for ordered findings and precise offsets, INPUT-HASHES.json for
fixed-blob/native hashes, commands.log for actual checks, and retained javap/Forge
event excerpts. The fixed owner decision is the sole new product direction.
No upstream Advanced Rocketry/LibVulpes file or art was inspected or imported.
