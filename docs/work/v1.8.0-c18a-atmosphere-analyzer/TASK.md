# C18a-01: handheld atmosphere analyzer

Date: 2026-10-04. Status: PROPOSED_FOR_INDEPENDENT_REVIEW.
Integrator: Root. Exact unit: `item:atmAnalyser`, currently PLANNED.
Stable item ID: `advancedrocketrycommunity:atmosphere_analyzer`.
Source basis: `bd0148e98d065df6ac77789eca0a5dae9b6a409a`.
Contract: accepted ADR-066 section 3.1 and ADR-061 identity/DataGen policy.
No implementation, recipe/balance adoption or ledger delivery is granted yet.

## Outcome and exclusions

On ordinary server-authoritative item use, report the user's actual eye cell's
air and the actual context/ambient profile in one localized chat message.
No oxygen concentration, equipment-protection status or destination selection
is an air measurement. No screen, block, capability, owned item NBT, persistent
resource root, custom network message, public API change or cross-store writer.
Do not implement detector scheduling, seal detector, torch conversion, oxygen
reserves/refilling, C17 propulsion or other C18 units through this task.

## Exact technical and presentation choices proposed by Root

1. Unstackable, no durability or capability. Authoritative use requires the
   exact item/count in the actual hand of an alive connected nonspectating
   ServerPlayer on its owning thread/server/actual Level. Both hands and creative
   mode use the same query. Client use predicts interaction only, not data.
   Never copy/deserialize held NBT or invoke an oxygen provider for this read.
2. Use the existing player's vanilla item cooldown: two server ticks between
   reports, shared by both hands. Reject cooldown before querying; add it after
   an admitted feedback response, including pending/unavailable/disabled, so
   repeated unsupported reads cannot spam feedback. Invalid player/hand/thread
   admission performs no query, message, cooldown or item mutation.
3. NEW acquisition, not claimed legacy parity: shapeless one `basic_circuit`,
   one `minecraft:glass_pane`, one `minecraft:iron_ingot` to one analyzer; normal
   recipe unlock on basic circuit. Reuse the existing wafer/circuit path and do
   not revise circuit tooltips, costs or final C16d progression. Basic circuit
   ingredients include ordinary quartz access; no fresh-Overworld/full-tech
   reachability claim follows. DataGen must verify the actual recipe/unlock.
4. NEW original 16x16 generated instrument texture and ordinary generated item
   model. Root records authored provenance before asset authoring/import and
   verifies generated source/hashes. No upstream/Minecraft/Forge bitmap copying;
   no unrelated classic asset ledger row is delivered by the new art.
5. One fixed-field translatable chat Component, never the hazard action bar.
   Separate effective cell BREATHABLE/NON_BREATHABLE/PENDING/UNAVAILABLE from
   ambient pressure and Kelvin temperature, and from supplied/control status.
   Do not label positive-pressure nonbreathable air vacuum merely because the
   manager's internal state enum is VACUUM. Numeric domain values remain finite
   doubles; format with `Double.toString`, not rounded/invented quantities.
   No real-world unit is invented for the configured pressure scale.
6. Show logical body/locus, plus actual ambient-body label when different.
   Use existing `body.<namespace>.<path dots>` keys with bounded stable-ID
   translatable fallback; server code never uses client I18n. Add only missing
   Space label and analyzer keys to bilingual v180 output. Existing body names
   and other translations stay unchanged. Each output has only fixed fields,
   bounded IDs (128 characters), no external NBT/error text or mutable readings.
7. Root adds accepted COMMON `lifeSupport.classicDevicesEnabled=true`. Disabled
   gives the localized DISABLED response without querying; item/data stay
   registered. No equipment/survey switch or saved schema is needed here.

These additive testable leaf choices require independent review before adoption;
they do not change the already accepted measurement meaning or old APIs/saves.

## Host-only query and lifecycle boundary

Implement `AtmosphereAnalyzerService.read(ServerPlayer)` and `close()` with an
immutable `AnalyzerReading`; no arbitrary-position or exported public API.
The service owns no inventory/resources, no player/reading cache and no parallel
catalog/atmosphere registry. A reading contains only fixed enums, bounded body
IDs, optional paired finite ambient pressure/temperature and supplied flag,
never server/player/Level/ItemStack/VolumeId references. Paired ambient values
obey existing pressure 0..10 and temperature 0..2,000 K limits.

The service captures the actual MinecraftServer, existing catalog manager,
existing manager and an existing BodyContextResolver at server startup. Build
that resolver with the existing committed StationRegionBodyContextResolver and
startup-acquired StationRegistrySavedData lookup, following current
EnvironmentQueryLifecycle's startup-only acquisition. No query calls SavedData
get/load, reserves/creates a station or touches a foreign capability.

New `AtmosphereAnalyzerLifecycle` creates one private service at ServerStarted
and closes/drops it at both ServerStopping and ServerStopped. The new
`AtmosphereAnalyzerRuntime` holds only that managed service reference, not a
world/player collection. Closed services refuse forever and release server,
manager/catalog/resolver references; old handles never rebind to a new server.
Runtime removal must match the owned service/server, not clear a foreign active
host. Root alone registers these lifecycle listeners with appropriate stopping
priority and injects the already initialized AtmosphereManager/catalog manager.
Missing manager/service yields UNAVAILABLE; never create atmosphere truth stores.

Before world reads, require owning server thread and actual current
PlayerList/player/Level identity. Derive precisely
`BlockPos.containing(player.getX(), player.getEyeY(), player.getZ())`; check
finite coordinates, world bounds, build height and the chunk via loaded-only
access. Unknown/unloaded/outside cells are unavailable, without a ticket or clip.

Capture one current catalog and use its captured overload for BodyContext and
ambient lookup. SURFACE uses the actual unambiguous mapped surface definition.
Actual Space, including committed stations, uses the Space Level's ambient
definition, never the orbited body's surface atmosphere. Unknown/blocked/
reserved/outside station contexts and unknown ambient data are unavailable,
even if a supplied room exists. The public orbit-atmosphere Optional stays
unchanged. Do not use Overworld fallback gameplay safety as instrument metadata.

Only after those guards query existing manager `breathabilityAt`/`controlledAt`
for the eye. A known supplied cell is effective breathable/control while
displayed profile numbers remain explicitly ambient. PENDING makes no positive
control/breathability claim; known ambient/context may be labelled as such.
Recheck captured catalog identity after composition; a changed snapshot gives
UNAVAILABLE instead of mixed generations. Accepted reload becomes visible;
rejected reload follows existing last-good behavior. No scans, vent ticks,
resource debits, explicit dirty marks or persistent writes are requested.
Existing manager query-local environment/cache maintenance is not a new store
or proof that ordinary world ticking has stopped.

## Future isolated write scope

After exact review/disposition Root creates a fresh D worktree at a declared
committed base. One worker may create only these files under the existing Java
package and their corresponding scoped unit tests under `src/test/java`:

- `atmosphere/instrument/AnalyzerReading.java`;
- `atmosphere/instrument/AtmosphereAnalyzerService.java`;
- `atmosphere/instrument/AtmosphereAnalyzerRuntime.java`;
- `atmosphere/instrument/AtmosphereAnalyzerLifecycle.java`;
- `atmosphere/instrument/AtmosphereAnalyzerItem.java`;
- `atmosphere/instrument/AtmosphereAnalyzerFeedback.java`;
- `datagen/V180AtmosphereAnalyzerData.java`;
- `datagen/V180AtmosphereAnalyzerLanguage.java`;
- `gametest/AtmosphereAnalyzerGameTests.java`;
- this task directory's own `PROGRESS.md`, `HANDOFF.md` and central integration
  proposal naming only required Root changes, not editing their target files.

Root owns ModItems/creative tab/config/main event registration, central tests,
provider/language wiring, generated resources, pre-authoring provenance,
ledgers/status and all commits/pushes. Existing atmosphere/BodyContext/station/
equipment/API code stays worker-read-only. No dependency/build changes or public
API expansion. Prefer small focused classes; over 500 lines requires review,
over 800 needs an ADR. Any additional write need must be proposed first.

## Evidence obligations

Independent exact source review and key reruns precede integration. Unit tests
cover reading invariants/finite bounds, Space-vs-orbit projection, supplied-vs-
ambient and pending/unknown guards, locale/fallback/disabled response, same
snapshot and closed/cross-server/off-thread refusal, deterministic resources
and the new exact recipe/unlock. No provider call or held NBT/resource mutation.

Registered GameTests exercise the actual registered item/native use with a
connected non-FakePlayer, both hands/shared cooldown/recipient, actual Earth,
Moon and committed station context, eye-versus-feet room edge, invalidation/
pending/supply, suit-independent reads and unloaded no-ticket refusal. Check
unchanged held item/custom data and vent balances while accounting separately
for ordinary ticking; test actual crafting output and stale lifecycle handles.
Preserve existing atmosphere/suit/context/environment API tests and budgets.

Root runs scoped and full Java17 clean build/test/DataGen twice/generated diff,
all required GameTests, and strict source/provenance/resource/content checks
against a committed cohort. A later packaged before/after-restart exercise
needs legitimately owned connected player records and actual item use; this
leaf does not authorize a synthetic native hook or reuse the unresolved C18
native ownership draft. Real-client V1/V2 model/language/readability/recipient/
reconnect evidence remains separately required. Code/tests/static preparation
alone cannot deliver the item ledger unit or approve any G0-G9 Gate.

## Preparation and temporary storage

Frozen feasibility bundle:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-next-leaf-b9a73f16d2/`.
ACTIONABLE-01 SHA `5fe719a90fb6d7c4ed048404b4d5250de8948b5746b1ac9e404053b835079c4a`;
internal manifest SHA
`a9aeec17a742199ead21d240bc447208ec94df2302d117e4779350ac243ce3ed`.
Actual preparation: 14 static checks exit 0; 81 named inputs unchanged and 20
links resolve. Literal Git equivalence has one explicitly qualified historical
CSV CRLF expansion and the separate user-maintained AGENTS. Original collector
and locator failures remain preserved; no Java/native/client ran there.

Helpers/logs/process TEMP/TMP/TMPDIR/Java temp stay under project-parent
`D:/GitHub/ARCE-Task-Evidence`. Before heavy Java/native work require C and D
each have 10,000,000,000 free bytes. No global settings, private bundle/huge
inherited ZIP scan, unrelated process kill or other-agent edits. Clean only new
owned stopped unsealed temporary copies through checked native literal paths;
policy rejection is unfinished cleanup, never an alternate-tool bypass.
