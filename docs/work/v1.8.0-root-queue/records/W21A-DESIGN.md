# W21a design note: observatory, astrobody data processor and ground survey jobs

Date: 2026-10-13. Task: [TASK-W21A-OBSERVATORY-DESIGN.md](../TASK-W21A-OBSERVATORY-DESIGN.md). Base: `f2fec2bb` on
`codex/v1.8.0-w21a-observatory-design`. Contract: ADR-066 section 6.1 and owner decision D2 (section 9). This is a
design note for a persistence and migration slice. It changes no ADR, risk entry, status file or ledger row: section 1
is the ADR-051 text and section 8 the risk text for Root to add. The only other files are the two pattern fixtures.

## 0. Checked facts

**Upstream.** Both files are in the C18 revision-3 `source-files.json` and were checked at the pinned commit
`c5cd5af6`. `TileObservatory.java` (SHA-256 `b1c35a41…`, matches) gives the structure at lines 53–83, the lens set
`{blockLens, Blocks.GLASS}` at line 51, and the wildcard list at lines 223–229 (`IRON_BLOCK` plus the `'P'` power and
`'D'` data mappings). Its sky rule (line 169) is "not raining, `canBlockSeeSky(pos.up())`, not daytime, or the space
dimension"; observation takes 1,000 ticks (line 86). `TileAstrobodyDataProcessor.java` (`ce95158d…`, matches) gives
its structure at lines 43–49. Layouts are used as behaviour facts only. No code, model or texture is copied.

**Ledger.** `libvulpes:java/blockStructureBlock` became `machine_casing` (row 544). `block:structureTower` is
REDESIGNED with no block successor (row 141). `block:blockLens` became the `laser_lens` item (row 65). Motors are the
four tiers under `#advancedrocketrycommunity:motors` (row 543).

**Repository.** The registry root is schema 3 (`SatelliteLimits.REGISTRY_SCHEMA_VERSION`) with instance schema 1.
Its limits are a 16 MiB root, 4 KiB records, section budgets 4/6/2/1 MiB (`StorageBudget`), 16 live instances per
owner and 2,048 instances (`RegistryLimits`). It flushes at most once per 100 ticks (`SatelliteManager.coalescedFlush`)
and migrates before start with a backup (`WorldDataMigrationService`, at most 5 backups). `formatEpoch` maps only the
current schema to `v1.6.0-satellite-missions`. `SatelliteMissionRegistry` already has 791 lines.

**Vanilla.** These were checked with JDK 17 `javap -p -c` against the mapped Forge 47.4.10 jar (SHA-256 `95eecc59…`,
as in W11a):

| Claim | Class and method |
|---|---|
| Sky visibility is "sky light equals the maximum" and reads only the light engine | `BlockAndTintGetter.canSeeSky(BlockPos)`, `getBrightness(LightLayer, BlockPos)` |
| Glass passes full sky light | `AbstractGlassBlock.propagatesSkylightDown` returns true; `GlassBlock` extends it |
| `isNight()` is false in every fixed-time dimension | `Level.isNight()`, `Level.isDay()` (`hasFixedTime`, `skyDarken < 4`) |
| `skyDarken` follows `fixed_time`, rain and thunder, and is updated each server tick | `Level.updateSkyBrightness()`, `LevelTimeAccess.getTimeOfDay(float)`, `DimensionType.timeOfDay(long)`, `ServerLevel.tick` |
| Rain is a global flag; precipitation is per biome | `Level.isRaining()` (rain level above 0.2), `Level.isRainingAt`, `Biome.getPrecipitationAt(BlockPos)`, `Biome$Precipitation` (NONE, RAIN, SNOW) |
| Biome reads never load a chunk | `LevelReader.getBiome` → `BiomeManager.getBiome`; `LevelReader.getNoiseBiome(int,int,int)` calls `getChunk(…, BIOMES, false)`; `ServerLevel` overrides only `getUncachedNoiseBiome` |
| Loaded check without loading | `ServerChunkCache.getChunkNow(int, int)` |
| Menu buttons run on the server thread after the container ID, spectator and `stillValid` checks | `ServerGamePacketListenerImpl.handleContainerButtonClick`, `AbstractContainerMenu.clickMenuButton(Player, int)` |

The mod's Moon dimension type has `fixed_time` 18000 and sky light; Space has no sky light.

## 1. ADR-051 amendment (exact text for Root)

> ### W21a implementation note: ground survey origins, instance schema 2 and registry schema 4 (2026-10-13)
>
> ADR-066 section 6.1 (owner decision D2) adds ground survey jobs. This note amends sections 2 and 3 for them and
> changes no other decision.
>
> 1. **Origin.** An instance's `source_mission` becomes a tagged origin. `origin_kind` is `survey_mission` or
>    `ground_survey`, and `origin_id` is exactly one UUID: the survey mission or the ground job. No field is
>    nullable, and no mission state or satellite is invented for a ground job.
> 2. **Instance schema 2.** The record (at most 4 KiB) holds `schema_version` 2, `instance_id`, `owner_id`, `system`,
>    `asteroid_type`, `table_version`, `candidate_fingerprint`, `seed`, `yield`, `created_at`, optional `expires_at`,
>    `state`, `origin_kind`, `origin_id` and optional `allocated_mission`. The key set is exact. Schema 1 differed
>    only in `source_mission`.
> 3. **Registry schema 4.** Root 4 adds the list `ground_surveys`: at most 2,048 job records of at most 4 KiB, in a
>    1 MiB section budget inside the unchanged 16 MiB root. Its format epoch is `v1.8.0-ground-surveys`. Root 3 keeps
>    `v1.6.0-satellite-missions`. Every other section, limit and the save epoch are unchanged. The 16 live instances
>    per owner and 2,048 instance records count both origins.
> 4. **Transitions.** Section 2 gains three rows. A ground survey start creates a PENDING (hidden) instance of origin
>    `ground_survey`. Its completion sets AVAILABLE with `expires_at = now + TTL`. Its cancellation removes it. Each
>    happens in the same registry mutation as the job's transition.
> 5. **Backlinks.** They are checked at load and before every mutation. No two jobs name the same `instance_id`.
>
>    | Instance state | Origin `survey_mission` | Origin `ground_survey` |
>    |---|---|---|
>    | PENDING | The unfinished SURVEY mission `origin_id`, whose payload lists this instance, with the same owner and system (unchanged) | Exactly one job `origin_id` in ACTIVE, naming this `instance_id`, with the same owner, system, asteroid type, table version, candidate fingerprint, seed and yield digest |
>    | AVAILABLE, EXPIRED, DEPLETED | No origin check: the finished survey may have been pruned | Job `origin_id` exists, names this instance, has the same snapshot, and is READY or PROCESSED, or QUARANTINED from one of them |
>    | ALLOCATED | The existing one-to-one allocation check | The row above plus the existing allocation check |
>    | QUARANTINED | None | None |
>
>    | Job phase | Its instance | Other conditions |
>    |---|---|---|
>    | ACTIVE | Exists, PENDING, origin `ground_survey`/this job | No other ACTIVE job has its `source_device`. Analysis is WAITING without a claim. |
>    | READY | Absent, or names this job and is not PENDING | Analysis is WAITING without a claim, or RUNNING with one claim; no other RUNNING job names that processor. |
>    | PROCESSED | Same as READY | Analysis is CREDITED. |
>    | CANCELLED | No instance names this job | Analysis is WAITING; `resolved_at` is present. |
>    | QUARANTINED | None | `previous_phase` and `reason` are present. |
>
> 6. **Quarantine.** A failed row quarantines the records it names, with a reason of at most 48 characters:
>    - a ground PENDING instance without its ACTIVE job: the instance, and the job if present;
>    - an ACTIVE job without its PENDING instance: the job, and the instance if present;
>    - a retained ground instance without its job: the instance, and an ALLOCATED one's mining mission under the
>      existing INSTANCE_MISMATCH rule;
>    - two ACTIVE jobs on one device, or two jobs on one instance: every job and instance involved;
>    - two RUNNING claims on one processor: both jobs, which keep `previous_phase` READY, so their instances stay valid;
>    - a CANCELLED job whose instance still names it: the instance.
>
>    Nothing is deleted, completed, paid or credited, and the registry stays operational. Strict decoding failures
>    are not quarantine cases: an unknown `origin_kind`, phase or analysis state, an unknown key, an instance schema
>    other than 2, a job schema other than 1 or a duplicate job ID. They preserve the whole registry raw and blocked,
>    as for any malformed root (ADR-050 section 10).
>
>    The operator `instance release` of a ground-origin instance requires its job in READY or PROCESSED. The operator
>    `ground-survey release <job>` restores the previous phase only when row 5 holds afterwards.
> 7. **Cancellation and retention.** Cancelling an ACTIVE job is one mutation: it removes the PENDING instance and
>    sets the job CANCELLED with `resolved_at`. Nothing is awarded or refunded. The CANCELLED job is a tombstone,
>    pruned at `resolved_at + 1,200`. A PROCESSED job is pruned once its instance is absent and `resolved_at + 1,200`
>    has passed. An instance is removed only from DEPLETED or EXPIRED, so no allocation or claim reference remains.
>    READY, ACTIVE and QUARANTINED jobs are never pruned automatically.
>
>    Pruning runs in the existing scheduler pass: at most 16 removals and 32 inspections, in (eligible time, job ID)
>    order. It never removes live work to admit new work. Instance retention is unchanged.

## 2. Migration

**Chain.** `SavedDataSchemaMigrator.migrate` keeps its contract.
- **1 or 2 → 4:** run the existing 1/2 → 3 step unchanged (records 1 → 2, empty instance list, save epoch 1), then 3 → 4.
- **3 → 4:** copy the root, upgrade each instance 1 → 2, add an empty `ground_surveys`, keep `save_epoch`, then
  `decodeCurrent` the result. The upgrade refuses a schema-1 record that is not schema 1, that already carries
  `origin_kind` or `origin_id`, or that fails schema-1 decoding.
- **4:** already current. It returns `CURRENT` without a backup or a write, so a second load is a no-op.

**Root shape.** `ManagedSavedDataType` changes in three places:
- current schema 4;
- `formatEpoch(3)` stays `v1.6.0-satellite-missions` and `formatEpoch(4)` is `v1.8.0-ground-surveys`. Without this
  explicit mapping a schema-3 root would expect `v0.9.0-beta` and fail;
- `validateRootShape` requires `ground_surveys` from schema 4 and forbids it below.

Migration writes the upgraded payload, not a repaired one. Load-time quarantines persist with the next flush, as today.

**Budget snapshot.** The pure upgrader returns a snapshot of instance count, largest migrated instance, root bytes
before and after, and instance-section reservation before and after. Migration refuses with `INVALID_SCHEMA` when:
- a migrated instance encodes above 4,096 bytes;
- the root encodes above 16 MiB;
- a list exceeds its fixed count;
- the result fails the `BoundedSavedDataIo` read limits.

The server then does not start, and the original file stays unchanged. A section byte budget is an admission bound
(`StorageBudget`: an over-budget root loads and admits nothing new), so it is logged in the snapshot line, not
refused. The envelope cannot break a decodable record. Measured with the real `NbtIo.write`, the worst case is 3,169
bytes for schema 1 and 3,194 for schema 2 (+25), with every ID at 128 characters, 17 yield entries and every optional
field. `SatelliteRecordFit.instanceBytes` switches to schema 2; every table keeps at least a 902-byte margin.

**Unknown and future data.** These are preserved raw: registry schema 5, instance schema 3, job schema 2, an unknown
`origin_kind`, phase or key. Before start, `WorldDataMigrationService` throws `FUTURE_SCHEMA` or `INVALID_SCHEMA`.
At a later load, `SatelliteMissionSavedData` keeps `preservedBlockedData` and saves that tree back unchanged.

**Old host.** A host with schema 3 reads schema 4 as FUTURE (`SavedDataSchemaMigrator` line 35) and refuses to start
(`WorldDataMigrationService.discoverSources`). Downgrading means restoring the pre-upgrade copy in
`advancedrocketrycommunity-backups/`, or running the matched build.

**Fixtures** (SNBT under `src/test/resources/migrations/v180/`, in W21b):
- **M1.** Root 1 → 4: satellites and missions as today, empty instance and job lists.
- **M2.** Root 2 → 4.
- **M3.** Root 3 → 4 with one instance each in PENDING (with its unfinished survey), AVAILABLE, AVAILABLE with the
  survey pruned, ALLOCATED with the survey pruned and an unfinished asteroid mission, DEPLETED, EXPIRED and
  QUARANTINED. Each becomes `survey_mission` with the same UUID, and every other field and account balance is
  unchanged. No new quarantine.
- **M4.** Root 4 → no-op (`CURRENT`, file bytes unchanged).
- **M5.** Root 4 with a ground PENDING instance and its ACTIVE job: save, load and a second save are identical.
- **M6.** Root 4 with READY, PROCESSED (instance retained and pruned), CANCELLED and QUARANTINED jobs: round trip.
- **M7.** Backlink permutations, one fixture each:
  - a job is missing, or in the wrong phase;
  - `instance_id` and `origin_id` mismatch;
  - two jobs name one instance;
  - two ACTIVE jobs share a device;
  - two RUNNING claims share a processor;
  - wrong owner, system or asteroid type;
  - wrong table version, fingerprint, seed or yield digest;
  - a ground origin on a survey payload;
  - a `survey_mission` origin naming a job;
  - a CANCELLED job with its instance;
  - an AVAILABLE ground instance with an ACTIVE job.
- **M8.** Refused and raw-preserved: registry 5, instance 3, job 2, unknown `origin_kind`, unknown phase, extra key,
  duplicate job ID, and a schema-1 record carrying `origin_id`.
- **M9.** Generated in memory: a root that would exceed 16 MiB after the envelope, and a list above its count. Both
  are refused.

## 3. Job model

| Key | Type and bound |
|---|---|
| `schema_version` | int, 1 |
| `job_id`, `owner_id`, `instance_id`, `source_device` | UUID |
| `source_level` | Level key, at most 128 characters |
| `source_pos` | long (`BlockPos.asLong`) |
| `system`, `asteroid_type` | resource ID, at most 128 characters |
| `table_version`, `candidate_fingerprint`, `yield_digest` | 16 hex characters. The digest is the `sha256Hex16` of the yield's `item\tcount\n` lines; the instance keeps the only full yield. |
| `seed` | long |
| `survey_ticks`, `survey_energy`, `survey_progress` | int 20..72,000 (proposal 1,000); 1..10,000 FE/t (proposal 20); 0..`survey_ticks` |
| `created_at`, `start_epoch` | long ≥ 0; long ≥ 1 |
| `phase` | `active`, `ready`, `processed`, `cancelled` or `quarantined` |
| `completed_at`, `resolved_at` | optional long |
| `analysis` | `state` (`waiting`, `running`, `credited`); `ticks` 1..72,000 (proposal 200); `energy` 1..10,000 (20); `credit` 1..10,000 (120); `progress`; optional `claim_epoch`, `processor`, `processor_level`, `processor_pos` (all four or none) |
| `quarantine` | optional `reason` (at most 48 characters) and `previous_phase` |

All numbers are snapshotted at start and never recomputed. The worst case is 1,310 bytes and 35 tags (measured
with `NbtIo.write`, every ID at 128 characters), so 800 worst-case jobs fit in 1 MiB. Admission reserves each job's
largest lifecycle encoding, PROCESSED with a claim and a quarantine block, through a new
`RecordSizer.groundSurveyBytes`.

The phases and analysis subphases combine as section 1, item 5 states. In addition, `survey_progress` is below
`survey_ticks` in ACTIVE and equal to it from READY on. Analysis progress is 0 in WAITING, below `ticks` in RUNNING
and equal to it in CREDITED. A CREDITED claim is kept as the claim tombstone. The record constructor checks these
single-record combinations, so a violation blocks the registry. Cross-record checks quarantine (section 1, item 6).

**Limits.** They are checked in this order before any mutation; the first one reached refuses.

| Limit | Refusal |
|---|---|
| One ACTIVE job per observatory device, one RUNNING claim per processor | `DEVICE_BUSY`, `PROCESSOR_BUSY` |
| 16 live instances per owner, shared with satellite surveys | `OWNER_LIMIT` |
| 16 job records per owner, every phase including tombstones | `OWNER_LIMIT` |
| min(config, 2,048) instances; 2,048 jobs | `CAPACITY_REACHED` |
| Instance reservation in the 2 MiB section; job reservation in the 1 MiB section; a new account in the account section | `STORAGE_BUDGET` |
| Encoded instance and job each at most 4,096 bytes, root at most 16 MiB | `STORAGE_BUDGET` |

Derived indexes are rebuilt on restore, never persisted, and bounded by the job count: device to ACTIVE job,
processor to RUNNING job, owner to jobs, and instance to job. No count is raised.

**Per-tick budget.** A server-scoped scheduler admits at most 32 paid advances and 64 inspections per server tick,
for observatories and processors together. It is created at server start and cleared at stop, and a Level's queue is
cleared when it unloads. Each Level has a deduplicated FIFO queue. The scheduler serves one entry per Level per round,
starting one Level later each tick. A controller enqueues itself from its block-entity ticker only after its local
checks pass, so only ticking chunks can progress. An entry left at the end of a tick shows `QUEUED`.

## 4. Mutations, barriers and crash cuts

Every mutation is one synchronized change of the registry, followed by the existing coalesced flush. No new
synchronous flush is added. The barrier is the existing ADR-050 section 2 save epoch: the first paid advance of a
start or a claim waits until `save_epoch > start_epoch` (or `claim_epoch`), shown as `WAITING_SAVE` for up to about
100 ticks. No FE is therefore debited for registry work that a crash could erase whole. If the registry cannot flush,
the work never pays, so a registry root failure means no start. Controller roots hold only identity (section 6). The
FE in power plugs is the only value in another store, under the existing machine FE limitation.

| Mutation | Checks before the change | The single registry change | A crash before it is flushed leaves |
|---|---|---|---|
| START | Owner, formed and loaded, enabled, body, tables, candidates, limits, then one `survey-v1` instance with a server seed | PENDING instance, ACTIVE job, reservations and account | No job, no instance, no FE paid |
| Survey advance | Job found by device UUID and exact Level and position; sky; FE | `survey_progress + 1` | Earlier progress; FE paid since the flush is lost |
| Completion (the last advance) | As above | Job READY with `completed_at`; instance AVAILABLE with TTL | ACTIVE and PENDING again; it completes once later |
| CANCEL | Owner or operator, job ACTIVE | Instance removed; job CANCELLED | The job and instance again; cancel again |
| REBIND survey | Owner, job ACTIVE; target formed, loaded, owned and idle | Source device, Level and position replaced; progress kept | The old source |
| CLAIM | Owner, READY and WAITING, processor idle | RUNNING; claim with `claim_epoch`; progress 0 | WAITING |
| Analysis advance | The claim matches the processor UUID, Level and position; FE | Progress + 1 | Earlier progress |
| Credit (the last advance) | The account takes `credit`; otherwise `ACCOUNT_FULL` and no debit | Account credited; job PROCESSED; analysis CREDITED; `resolved_at` | RUNNING; credited exactly once later |
| REBIND analysis | Owner, RUNNING, new processor idle | Claim replaced; progress kept; new `claim_epoch` | The old claim |
| Prune | Section 1, item 7 | Job removed; reservation released | Pruned again later |

**No duplication.**
- **Copy:** a controller root copied to another position keeps the device UUID but fails the exact Level and position
  match. It neither advances nor completes, and START there reports `DEVICE_COLLISION`.
- **New controller at the old place:** it has a new UUID and must rebind.
- **Rebind:** it changes only the source of the existing job and instance.
- **Retries:** a repeated START finds the device busy. Completion requires ACTIVE at full progress. A repeated claim
  returns `ALREADY_CLAIMED`. A repeated credit finds PROCESSED. A repeated cancel returns `CANCELLED` until the
  tombstone is pruned, and `JOB_NOT_FOUND` after.
- **Breaking a device:** the job stays and pauses with `SOURCE_MISSING`. Paid FE is not refunded.

## 5. Patterns

The fixtures are `src/test/resources/fixtures/v180/machine_patterns/observatory.json` (SHA-256 `eaeca0f3…`, 17,820
bytes) and `…/astrobody_data_processor.json` (`fc59c4df…`, 1,823 bytes). They use pattern schema 1, the four
rotations 0/90/180/270 and no mirror. They are in the `DataProvider.saveStable` layout: the formatter that wrote them
reproduces the generated `lathe`, `centrifuge`, `arc_furnace` and `precision_laser_etcher` files byte for byte. W21c's
DataGen output must equal them.

**Observatory** (5 × 5 × 5; controller anchor (2,1,0); layers top first; rows are local z from the controller row;
columns are local x):

```text
y4 ..... .SGS. .SSS. .SSS. .....      y1 .*C*. *SSS* *SSS* *SSS* .***.
y3 ..... .SSS. .SGS. .SSS. .....      y0 .***. *TTT* *TMT* *TTT* .***.
y2 .SSS. SAAAS SAAAS SAGAS .SSS.
```

| Symbol | Cells | Role | Matcher | Upstream source (`TileObservatory`) |
|---|---|---|---|---|
| C | 1 | controller | `controller` | `'c'`, line 73 |
| S | 37 | casing | `advancedrocketrycommunity:machine_casing` | `blockStructureBlock`, lines 56–76; ledger row 544 |
| T | 8 | tower | `machine_casing` | `blockStructureTower`, lines 80–82; row 141, no successor block |
| M | 1 | C16 motor | tag `advancedrocketrycommunity:motors` | `LibVulpesBlocks.motors`, line 81; row 543 |
| G | 3 | lens or glass | `minecraft:glass` | `lens`, lines 51, 56, 63 and 70; row 65 |
| A | 8 | air | `air` | `Blocks.AIR`, lines 68–70 |
| * | 23 | shell; power equivalent | new tag `advancedrocketrycommunity:observatory_shell_parts` = `machine_casing`, `minecraft:iron_block`, `power_input_plug` | `'*'`, lines 73–83; wildcard list, lines 223–229 |
| . | 44 | clearance | tag `machine_clearance` | `null`; W01G convention |

**Processor** (3 × 2 × 2; anchor (1,1,0)): `y1 LCL LLL`, `y0 PSS SSS`.

| Symbol | Cells | Role | Matcher | Upstream source (`TileAstrobodyDataProcessor`) |
|---|---|---|---|---|
| L | 5 | slabs | tag `minecraft:slabs` (as the etcher) | `"slab"`, lines 44–45 |
| C | 1 | controller | `controller` | `'c'`, line 44 |
| P | 1 | power | port `energy_input` | `'P'`, line 47 |
| S | 2 | input and output equivalents | `machine_casing` | `'I'`, `'O'`, line 47 |
| S | 3 | casing (no data bus) | `machine_casing` | `'D'`, line 48 |

**Rotations.** By `PatternTransform.applyOffset`, the top lens offset (0,+3,+1) becomes (0,3,1), (−1,3,0), (0,3,−1)
and (1,3,0) at 0, 90, 180 and 270 degrees. The processor's power offset (−1,−1,0) becomes (−1,−1,0), (0,−1,−1),
(1,−1,0) and (0,−1,1).

**Fixture check.** A throwaway harness in the Temp directory compiled the real `machine/multiblock/pattern` sources.
The strict codec decoded both files (125 and 12 cells). In each rotation the validator returned FORMED for a world
built by independent quarter turns, MISMATCH at exactly one replaced cell, and WAITING_UNLOADED for one unloaded cell.

## 6. Runtime

**Ownership and roots.**
- The controller's owner is the placing player, derived on the server.
- Roots `arce_observatory` and `arce_astrobody_data_processor` (schema 1) hold only `owner_id` and `device_id`, a UUID
  made at placement. Progress, job IDs and credits live in the registry.
- Both block entities override `onlyOpCanSetNbt()`.
- An unsupported root is kept raw and the device stays inert, under the ADR-066 section 2 save guard (section 8).
- START, CANCEL, CLAIM and REBIND require the owner. The job owner is the controller owner.

**Surface observation.** Let L be the world position of the top lens. Observation runs while every pattern cell is
loaded (`getChunkNow`, never a load) and all of the following hold:
- **sky:** `level.canSeeSky(L)`. This is one light read in a pattern chunk; glass passes sky light, so it is the lens
  column's view of the sky;
- **night:** `level.getSkyDarken() >= 4`. This equals the legacy "not daytime" in ordinary dimensions and follows
  `fixed_time`; `isNight()` would never be true on the Moon;
- **weather:** not (`level.isRaining()` and `level.getBiome(L).value().getPrecipitationAt(L) != NONE`). A dry biome
  never pauses.

**Station orbit.** The controller is in `CelestialIds.SPACE_LEVEL` inside the station that `findAt(x, z)` returns. The
station's `orbitBody()` resolves to the job's system, and the owner passes that station's VISIT rule. Sky, night and
weather are then skipped, matching the legacy space branch. A `checkedRelocation` to another system pauses with
`SYSTEM_CHANGED`.

**START derivation.** The body comes from the existing `EnvironmentQueries.at` (SURFACE locus), or the station's orbit
body, then the catalogue's `systemOf`. The candidates and fingerprint come from `ResourceAlgorithms` over the loaded
tables, and the seed from the server's `MissionSeeds`.

**Pauses**, each with its status:
- `REGISTRY_BLOCKED`, `DISABLED`;
- `UNFORMED` (the first diagnostic's local position and expected role), `UNLOADED`;
- `NO_SKY`, `DAYTIME`, `WEATHER`;
- `BODY_UNAVAILABLE`, `SYSTEM_CHANGED`, `CATALOG_UNAVAILABLE`;
- `NO_POWER` (stored and needed FE);
- `WAITING_SAVE`, `QUEUED`;
- `SOURCE_MISSING`, `DEVICE_COLLISION`, `QUARANTINED`, `ACCOUNT_FULL`.

Nothing scans offline, forces a chunk, or allocates a physical asteroid or a dimension. The COMMON switch
`research.groundSurveysEnabled` (default true) stops START, CLAIM and paid advances. Cancellation, menus and operator
commands keep working.

**Menus.**
- They are display-only `ContainerData`, at most 16 integers: status; phase; analysis state; progress and required;
  stored and needed FE; owner relation (none, you, another player); the first missing cell's local x, y and z and its
  role; selection index and count.
- Intents are `clickMenuButton` IDs 0–5: START, CANCEL, PREVIOUS, NEXT, CLAIM and REBIND_HERE. Each re-checks the same
  Level, at most 8 blocks, loaded chunk, controller identity, owner and 10 intents per second. No network channel is
  added.
- The processor's selection is a server-held list of the owner's READY and WAITING jobs (at most 16, in job-ID order).
  No client index, yield or research number is accepted.

## 7. Slices

**W21b: registry schema 4, migration and pure invariants (no blocks).**
- **Scope:** `InstanceOrigin` replaces `sourceMission` at every use: generation, claim, cancel, ledger, sizers and
  record fit. It also adds the job record and codec with strict keys, schema 4 shape and epochs, the 3 → 4 step and
  snapshot, `GroundSurveyInvariants` folded into `RegistryInvariants.plan` (ground quarantines are computed before the
  mission rows, so a quarantined ALLOCATED instance holds its mining mission), and a `GroundSurveys` operations class
  beside `ResourceMissions` for start, advance, complete, cancel, rebind, claim, credit, prune and release.
  `SatelliteMissionSavedData` delegates to it. New classes keep `SatelliteMissionRegistry` below 800 lines.
- **Tests:** JUnit for codec round trips and strict refusals, and for the M1–M9 fixtures. Every section 4 row with its
  refusals and replays; every section 1 table row in both directions; limits at 15/16 and 2,047/2,048; byte-budget
  refusal before mutation; tombstone and prune timing; overflow without state change. Existing schema-3 tests are
  updated to schema 4, not weakened.
- **Evidence:** targeted tests and the full unit suite; `runGameTestServer` and its log checker; `git diff --check`.
  Natively: a pre-W21b world with survey PENDING, AVAILABLE and ALLOCATED instances opens on W21b with a backup and
  identical values, and a second start is a no-op. The pre-W21b jar then refuses it with `FUTURE_SCHEMA`, and the
  backup opens. An independent read-only review is required before merge.

**W21c: blocks, menus and runtime.**
- **Scope:**
  - the two controllers, their items, recipes (proposed in its record) and loot;
  - original code-drawn art and en_us/zh_cn language;
  - the shell tag, and DataGen patterns that must equal the fixtures byte for byte (a unit test);
  - the config key and its exact log expectation;
  - the Level manager, which reuses the pattern catalogue, dirty queue and the power plug's existing claim and debit
    path, and stops for Root if a plug cannot bind to a non-recipe controller without a new ownership rule;
  - the scheduler, section 6, and operator commands `ground-survey inspect|cancel|release`.
- **GameTests:**
  - formation in four rotations and refusal with a wrong part, for each machine;
  - a covered lens, day, rain and an unloaded cell, each pausing without loading a chunk;
  - the station case;
  - no plug;
  - exact FE (20,000 per survey, 4,000 per analysis);
  - the instance visible through `ResourceMissions.available`;
  - 120 points credited once; claim replay and a second processor refused; account overflow;
  - break, copy, rebind and cancel;
  - more than 32 controllers in two Levels both progressing;
  - save and load;
  - `onlyOpCanSetNbt`, menu distance and spectator.
- **Evidence:** dedicated-server restarts in mid-survey and mid-analysis with exact progress and balance; a kill after
  completion but before the flush that completes once; and the R-027 scope change. GPU V1 and two-client V2 remain for
  the final campaign.

## 8. Choices for review, risks and exclusions

**Choices** where ADR-066 left room:
1. The processor's `'I'`/`'O'` cells are casing: analysis moves no items. The alternative is item ports as unbound
   structural hatches, at a crafting cost with no function.
2. The tower is casing, because no tower block exists.
3. Observatory `'*'` cells are a new tag without item or fluid hatches or a data bus.
4. The lens is exactly `minecraft:glass`, which guarantees sky light.
5. The job stores a yield digest, not a second yield.
6. The barrier is the epoch gate, with no new synchronous flush.
7. The per-owner 16 counts every job record.
8. Section byte budgets do not refuse migration.
9. Night means `skyDarken >= 4`, and weather means precipitation at the lens.
10. One budget is shared by both machines.
11. Analysis moves by rebind; there is no release.

**Proposed risk entry** (AGENTS.md section 3.3; quarantine and migration refusal isolate player data; Root assigns the
number, R-028 is the next free one):

> | R-028 | 地面巡天任务与地面来源小行星实例在跨记录不变量失败时被隔离；未知或未来格式使整个卫星注册表保留原始数据并阻断，预启动迁移拒绝启动 | 低 | 高 | 回链、所有者、星系、快照、设备或处理器重复不一致；未知 `origin_kind`、阶段或键，实例 schema 非 2，任务 schema 非 1；迁移后记录超过 4 KiB 或根超过 16 MiB | 影响范围仅为所列任务和实例（ALLOCATED 实例的采矿任务按现有规则一并隔离），注册表其余部分照常运行。隔离持续到操作员 `ground-survey release` 或 `instance release`，或离线修复。每次载入一条汇总日志，逐条审计行最多 64 条、每条不超过 512 字节。整表阻断或拒绝启动时，用 `advancedrocketrycommunity-backups/` 中的迁移前备份或匹配版本，离线修复后重启。不删除、不结算、不记研究点。详见 ADR-051 的 W21a 实施说明 | `v1.8.0` |

W21c must also add the two device roots to R-027's scope through an ADR-066 implementation note. Retained unsupported
roots reuse `GuardedChunkSaves` with its whole-chunk refusal, saturation and logging limits; no new save policy is
added.

**Exclusions.** This note does not cover the advancements (ADR-066 section 6.2), recipe ingredients, art and JEI
detail, client sky visuals, and ADR, risk-register, status or ledger edits. It writes no production or test code
other than the two fixtures.
