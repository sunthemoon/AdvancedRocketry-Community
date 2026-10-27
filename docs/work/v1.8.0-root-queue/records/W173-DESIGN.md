# W173: decoded satellite registry records that cannot be encoded again (design note)

Date: 2026-10-28. Status: **design note for Root's decision**; no production code, test, format, ADR, risk-register or
status change. Assignment: [TASK-W173](../TASK-W173-UNENCODABLE-DECODED-REGISTRY.md), including its added scope. Base:
shared commit `e8f5b2d17`, which contains W168's merge `016539ce0`; every line number below is at that commit. Sources:
W168-R3 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W168-rereview/REVIEW.md` (SHA-256 `b112ec0a…5886`), W168-R5 and the
runtime-admission risk in `D:/GitHub/ARCE-Task-Evidence/root-queue/W168-rereview2/REVIEW.md` (SHA-256 `56999b90…8005`),
[ADR-050](../../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md) sections 3, 7 and 9,
[ADR-052](../../../decisions/ADR-052-RESOURCE-TABLES-AND-SEEDS.md) section 2,
[ADR-074](../../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md) items 9 to 12, and R-030 and R-041 in
the [risk register](../../../11-RISK-REGISTER.md).

**Evidence.** `D:/GitHub/ARCE-Task-Evidence/root-queue/W173/`, manifest `SHA256SUMS.txt` (SHA-256 `6fae439a…8639`). The
probes are unit tests with the real codec, compiled from the evidence folder by `w173.init.gradle` against the test
classpath; nothing was added to the repository. The passing run is `probes.log` (SHA-256 `5136feda…dc8c`). Numbers
marked (P) come from that run; "by reading" marks a claim that no probe exercised.

## Summary

- **Only identifiers grow.** `parseLocation` (`SatelliteNbtCodec.java:563-572`) bounds the spelling at 128 characters
  and `ResourceLocation.tryParse` adds `minecraft:` to a name without a namespace: +10 characters, or +9 for a leading
  colon (P). Enum spellings never re-encode longer, kinds are matched exactly, and dropped unknown keys only shrink.
- **Only asteroid and gas missions can cross their record bound**, by up to 200 bytes: a raw record of 3,897 to 4,096
  bytes with 20 bare identifiers re-encodes at 4,097 to 4,296 bytes (P). Satellites, data and survey missions,
  instances, ground jobs and accounts stay inside their bounds at their largest. **The root can cross its 16 MiB bound
  even when every record fits** (P: 8,192 missions of 2,050 bytes, root 16,769,277 → 17,670,397 bytes).
- **Today** such a root loads operational, passes the start-time validator, and can never be written. The writer
  reports one ERROR per failure streak and one WARN at stop; the file on disk is never replaced, and every change of
  the session is lost at the next start (P). With any unrelated load repair in the same root, the same file is
  blocked and the world refuses to start, with no reason in the log (P).
- **Recommendation: option A.** Every loaded root must re-encode: run the existing repair-path encode on every load
  and refuse an unencodable root as an over-bound root (ADR-050 section 9, R-030), with a bounded reason that names
  the record. This mod never writes such a root, so only edited or foreign files are affected.
- **Runtime admission** is a different gap: admission ignores the proof flag, but every state a runtime mission can
  reach fits whenever the ADR-052 table check accepted its table (P). The one real path past 4,096 bytes is the same
  root cause on the content side: three content parsers bound the spelling, not the canonical identifier (P: the
  shipped catalog accepts a satellite definition with a 138-character ID; its satellite saves and is refused at the
  next load).
- **W168-R5:** replace the `int` overload with an accounts-only method and add a declared-methods test for the codec
  sizer. Both test `CountingSizer`s inherit the proven-by-contract defaults; delegating them is a test-only change.

## 1. Decoded fields whose canonical encoding can be longer

### 1.1 What normalizes

- **Identifiers.** Every field read through `requireLocation` (`SatelliteNbtCodec.java:559-561`) or the blueprint
  component loop (`:285-287`). `tryParse` inserts `minecraft` only when the colon is missing or first. Measured over
  the edge cases and 400,000 random spellings, growth is exactly 0, 9 or 10 (P, `ID` and `ID_RANDOM` lines).
- **Enums** (`requireEnum`, `:574-588`) upper-case the spelling with `Locale.ROOT` and the encoder writes
  `name().toLowerCase`. Java upper-cases code point by code point outside the lt, tr and az locales. Over all
  1,114,112 code points, the ten non-ASCII characters that map into `[A-Z_]` (`ß ı ſ` and the ligatures U+FB00-FB06)
  shrink or stay equal; maximum growth is 0 (P). Example: `quarantıned` reads as 12 bytes and writes as 11.
- **Kinds** (`MissionKind.parse`, `SatelliteKind.parse`) accept only the exact lowercase id. Booleans, numbers and
  UUIDs are type-checked and re-encode at the same size.
- **Unknown keys** in satellite and mission records are not rejected (instances and ground jobs use `requireKeys`).
  They are dropped on re-encode, which only shrinks the record. That is silent data loss of foreign keys, outside this
  task.

### 1.2 Largest growth per record kind (P, `RECORD` lines)

The model bounds some canonical identifiers to 128 characters: the terminal level (`MissionPayload.java:110-117`), the
instance system and type (`AsteroidInstance.java:46`) and all four ground identifiers (`GroundSurveyJob.java:184`). For
those fields a bare spelling of 119 to 128 characters is already refused at decode (P), so their longest bare spelling
is 118 characters and still grows by 10.

| Kind | Bound | Identifiers that grow | Largest raw → canonical | Can cross? |
|---|---:|---|---|---|
| Satellite | 2,048 | `definition_id`, `orbit_body`, ≤ 8 components: 10 × 10 | 1,728 → 1,828 | No |
| Data mission | 4,096 | `definition_id`, `target_body_id`: 2 × 10 | 1,389 → 1,409 | No |
| Survey mission | 4,096 | the same 2 × 10 | 1,466 → 1,486 | No |
| Asteroid or gas mission | 4,096 | the same, ≤ 17 reward items and `terminal_level`: 20 × 10 | 4,096 → 4,296 | **Yes**, raw 3,897-4,096 |
| Instance | 4,096 | `system`, `asteroid_type`, ≤ 17 yield items: 19 × 10 | 3,174 → 3,364 | No |
| Ground job | 4,096 | `source_level`, `processor_level`, `system`, `asteroid_type`: 4 × 10 | 1,366 → 1,406 | No |
| Account | 128 | none | 121 → 121 | No |

The "largest" rows use every optional field, the longest status and the 600-byte Unicode reward version where it
applies. A resource mission with *k* bare identifiers crosses for raw sizes 4,097 − 10*k* to 4,096. The review's GAS
record (4,090 → 4,280) is one point in that window.

**Root level.** The root is 16 MiB (`SatelliteLimits.MAX_REGISTRY_NBT_BYTES`), checked on the raw payload at decode
(`SatelliteRegistryPayload.java:33-35`) and on the encoding at save (`:86-88`). Growth adds up across records, at most
409,600 (satellites) + 1,638,400 (missions) + 389,120 (instances) + 81,920 (ground jobs) = 2,519,040 bytes. The probe's
8,192 claimed gas missions with 11 bare identifiers each fit their record bound (2,050 → 2,160 bytes), but the root
grows from 16,769,277 to 17,670,397 bytes. It decodes with `restoreReport().changed() == false`, loads operational,
and `save` throws `IllegalStateException` (P, `ROOT_*` lines).

### 1.3 Where unqualified names come from

- **Not this mod.** Every encoder of the registry since it was introduced in `e78e63fc4` writes
  `ResourceLocation.toString()`, which always includes the namespace. A history audit of every version of
  `SatelliteNbtCodec` and `GroundSurveyCodec` found no other spelling. The legacy upgrades (roots 1 to 3) copy the
  source tags, but those tags were written by the same encoders. No importer brings satellite data in from the
  1.12.2 mod.
- **Edited or foreign files only:** an operator's NBT edit (commands accept `iron_ingot` for `minecraft:iron_ingot`), an
  external tool, or a crafted file. A realistic edit grows 10 bytes per bare name and crosses only if the record is
  already within that many bytes of its bound. Crossing in practice needs many long bare names, which do not
  resolve to real items or bodies.

## 2. What happens now

| Phase | Behaviour with a root from section 1 (record level or root level) |
|---|---|
| Load | `decodeCurrent` accepts it, and the registry is operational and clean (P). By reading, `loaded()` (`SatelliteRegistryWriter.java:107-114`) then treats it as the acknowledged disk state. |
| Start-time check | `ServerAboutToStart` runs `WorldDataMigrationService.discoverSources`, which loads every managed root through `ManagedSavedDataPayloadValidator.java:28`. This root passes (P). |
| Same root plus any repair | When `finishRestore` changes anything (P: an orphan ACTIVE mission is quarantined), `SatelliteRegistryPayload.java:75-78` re-encodes the root, the encoder throws, `load` blocks the registry (`SatelliteMissionSavedData.java:112-114`), and the validator refuses the world. The exception is dropped at `:112`, so the startup ERROR says only "failed semantic validation" (P: cause `null`). |
| `save(CompoundTag)` | Throws `IllegalArgumentException` (record) or `IllegalStateException` (root) (P). |
| Ground work (W168) | The mission's reservation is the capped 4,096 bytes, unproven (`CodecRecordSizer.java:183-189`), so every ground operation takes the whole-root check, which fails: START answers `STORAGE_BUDGET` (P). |
| Other registry changes | The scheduler, claims, launches and reconciliation change RAM as usual (P: a data mission goes ACTIVE → READY). |
| Off-thread writer (ADR-074, W129b) | Every attempt throws in `SatelliteRegistryCapture.encode` on the IO thread, before replacement (`:107-110`), and is published as FAILED; dirty state is kept (P). By reading, retries start no sooner than 200 ticks after each failure (`CheckedRootWriter.java:21`), and each retry encodes up to the failing record again. |
| Autosave, `/save-all` | Running, they only request (ADR-074 item 9), so the same FAILED loop applies. |
| Stop | The drain spends its three attempts: `DrainReport[success=false, committed=0, attempts=3, reason=BUDGET_EXHAUSTED]` (P). |
| Next start | Loads the same, older file. The session's changes are gone (P: the data mission is ACTIVE again). |

**Can the world lose data?** The file on disk loses nothing: every attempt fails before replacement, and the probe's
sentinel bytes are unchanged after two flushes and the stop drain (P). But every registry change made since load is
lost at stop or crash: mission progress, claims and research credits, and satellites launched in the session. By
reading R-041 and ADR-074 item 6, the effects that depend on a registry acknowledgement wait for one that never comes:
terminal launch, decommission and data actions answer REGISTRY_PENDING, and discovery and deliveries wait. So nothing
was found that pays a reward twice, but players can lose a whole session of progress, again in every session. I did not
run those effect paths.

**Does the writer report it (R-041)?** Yes, but only once per session and without a cause the operator can act on:
- one ERROR `ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED outcome=FAILED`, logged at the first failure of the streak
  (`SatelliteRegistryWriter.java:337`), carrying the exception "Satellite runtime record exceeds its fixed NBT bound";
- one WARN `ARCE_CHECKED_WRITER_DRAIN_INCOMPLETE … reason=BUDGET_EXHAUSTED` at stop.

The probe saw exactly one of each over two flushes and the drain (P). Both are R-041 trigger signals. Nothing is logged
at load, and no message names the record.

## 3. Options

### A. Refuse the root at load, as an over-bound root (R-030)

**Change.**
- In `SatelliteRegistryPayload.decode`, run the existing `encodeCurrent` check on every current-root load, not only
  after a repair (`:75`). The validator, the SavedData load and the migration's staged validation all go through it.
- Keep the first exception's message, bounded, in the blocked `SatelliteMissionSavedData`, and pass it to the
  validator's `SavedDataMigrationException` so the startup ERROR shows it.
- Make the codec's bound error name the record kind, the record ID and the canonical size.
- The check is the codec's own encode, so it covers every record kind, the root total, and any future normalization.

**Cost (P, `ROOT_TIMING`).** One encode of a canonical 16,769,277-byte root takes 106 ms median (138 ms max, 7 runs) in a
unit JVM on this host; decoding the same root takes 453 ms. A start loads the root at least twice (validator, then
SavedData), and more often when it migrates. Optional later: skip the encode when W168's reservations already prove the root
(every reservation proven and their sum within 16 MiB, R132 review section 4).

**Compatibility.**
- A world whose satellite root loads today but can never be written (sections 1.2 and 2) refuses to start after the
  upgrade, with one ERROR naming the record. The file and every other managed file stay unchanged.
- That world is already broken today: nothing played there survives a restart.
- Roots this mod wrote, and edits with short names, re-encode within their bounds and are unaffected: the encoder's own
  output re-encodes at the same size. The implementation slice must run the migration and fixture tests to confirm it.

**Risk-register text** (an extension of R-030):
- Trigger: a satellite root whose canonical re-encoding exceeds a record bound (2 KiB or 4 KiB) or the 16 MiB root
  bound, for example identifiers without a namespace that load as `minecraft:`. Only an edited or foreign file can
  contain them.
- Scope: the whole registry; the start-time check refuses the world, as for any invalid satellite root.
- Duration: every start, until the file is repaired.
- Log volume: one ERROR per start attempt, with a bounded reason (record kind, ID, canonical size); nothing per tick.
- Recovery: stop and back up the world, then write each named identifier with its namespace (or remove the record)
  in an NBT editor, and restart.

**ADR.** No new decision. ADR-050 section 3 says "a save therefore cannot fail on size", and section 9 says an
over-bound file blocks the whole registry and preserves the payload. Option A applies the bound to the canonical
encoding. Root adds an implementation note to ADR-050 section 9 with the AGENTS 3.3 items above. The owner has to
approve, because worlds that start today will stop starting.

### B. Check the canonical length at decode, and refuse or quarantine only that record

**Change.**
- Refusing one record drops it, which R-030 forbids ("不静默丢弃任何玩家记录").
- A domain quarantine cannot hold it either: the record is already over its bound in canonical form, and the
  quarantine fields add about 110 bytes.
- Holding it therefore needs a new concept: the raw tag kept verbatim, written back by the encoder and carried by the
  capture, and excluded from the domain.
- Excluding it fires cross-record repairs: its satellite becomes RECOVERY_REQUIRED, and an allocated instance is
  quarantined.
- The root-level case has no single record to hold.

**Compatibility.** These worlds keep starting, minus the held records and whatever the repairs touch.

**Risk text.**
- Scope: one record, plus the repairs it triggers.
- Duration: until offline repair.
- Log volume: one WARN per load.
- Recovery: the same as A.

**ADR.** A new ADR: a new persisted held-record form and a change to ADR-050 section 9 and ADR-074 item 3.

**Verdict.** The largest change, and still incomplete for the root-level case.

### C. Keep the input spelling for re-encoding

**Change.**
- Identifiers carry their spelling to the encoder, either in the model records or in a codec-side map by record ID
  that the capture also carries.
- Unchanged fields then re-encode at their raw size.
- Transitions that add fields can still push a near-bound record over, so the sizer would need the spellings too.
- Equality-based proofs (W119 read-back) would compare non-canonical tags.

**Compatibility.** No load change.

**Risk text.** No new protection, but a new permanent invariant: the encoding is no longer canonical.

**ADR.** An amendment to ADR-050 section 3 and ADR-074 item 3.

**Verdict.** Invasive, permanent, and it hides edits.

### D. One-time offline repair

**Change.**
- A tool or documented procedure reads a stopped world's root, lists every record whose canonical form exceeds its
  bound, and rewrites identifiers with their namespace, or removes a record with the operator's consent.
- Alone it changes nothing at runtime, and nothing tells the operator to run it.
- It is the recovery half of A or E. A's named record plus an NBT editor is enough for the expected handful of cases.

**Compatibility.** None.

**Risk text.** Recovery wording in R-030 only.

**ADR.** None.

**Verdict.** Not now. Add a tool later if the owner wants one.

### E. Keep loading, but make the registry read-only

**Change.**
- At load, encode once. On failure, log one ERROR naming the record and keep the registry operational but unwritable.
- Every change is refused through the existing epoch-capacity gate (`SatelliteRegistryWriter.admitsChange`,
  ADR-074 item 5), with its own code and diagnostic, and the writer skips writes.

**Compatibility.** The world still starts and satellites are visible, but every satellite action is refused for the
whole session.

**Risk text.** A new protection.
- Scope: the whole registry.
- Duration: every session until repair.
- Log volume: one ERROR per load.
- Recovery: the same as A.

**ADR.** An amendment: a third registry state beside blocked and exhausted, in ADR-050 section 9 and ADR-074 item 5.

**Verdict.** It lets the rest of the world start, but it needs a new state, codes, UI text and tests, and still
needs the same offline repair.

## 4. Recommendation

**Adopt A, with its diagnostic.**
- It is the smallest change and reuses the reviewed, risk-registered blocked-root path.
- It covers the record and root levels and every record kind.
- It removes today's dependence on whether an unrelated repair happened in the same root (section 2).
- It restores ADR-050 section 3's promise that a save cannot fail on size, for every root that loads.

**One implementation slice, "canonical identifier bounds".** It refuses data at load, so AGENTS section 13 requires a
design note before implementation (this note can serve) and a read-only review before merge. It contains:
1. option A and its diagnostic;
2. the parser checks of section 6.3;
3. the W168-R5 guards of section 5.

**Tests for the slice:**
- raw-NBT regressions at the record window (3,897 and 4,096 bytes) and at the root level;
- a check that the start-time validator refuses each case and names the record;
- the unchanged W168 decoder tests, together with the migration fixtures (`GroundSurveyFixtures.schema3()` and the
  root 1 and 2 upgrades), still passing.

A world refused by A follows R-030's offline recovery.

## 5. Added scope: W168-R5

The two proof-by-contract entry points are sound today (P, `R5` line): `CodecRecordSizer` declares all four
`*Reservation` methods, and the largest account encodes in 121 bytes against the 128 reserved.

**Options for the `int` overload** (`StorageBudget.java:45-47`):
- (a) An assertion that the section is ACCOUNTS and the size is `RecordSizer.ACCOUNT_BYTES`.
- (b) **Recommended:** replace the overload with `reserveAccount(UUID owner)`, which reserves `ACCOUNT_BYTES` in
  ACCOUNTS, proven.
  - The four production calls change: `SatelliteMissionRegistry.java:135, :651`, `GroundSurveys.java:450` and
    `AccountRecovery.java:45`.
  - The three test calls that use the overload with other sizes (`StorageBudgetTest.java:37`,
    `AccountRecoveryTest.java:42, :55`) move to the explicit `reserve(section, id, new Reservation(bytes, true))`, so
    each test states its proof. No value or answer changes.
  - The account contract stays pinned by `GroundSurveyScaleTest.java:113-114`.

**Options for the sizer defaults** (`RecordSizer.java:34-48`):
- (a) **Recommended:** a test, for example in `ReservationCoverageTest`, that `CodecRecordSizer` declares all four
  `*Reservation` methods (the probe's reflective check).
- (b) Remove the defaults or make them unproven. This changes every test sizer, and the whole-root-check counts that
  the scale tests assert.

**Observation.** Both test `CountingSizer`s (`GroundPreflightBenchmark.java:168` and `GroundSurveyScaleTest.java:223`)
delegate byte counts to the codec sizer but inherit the proven-by-contract defaults. With today's canonical fixtures
they agree with the codec sizer. A future fixture with an unprovable record would make them claim a proof the
production sizer refuses, which is W168-R5's risk in test code. Delegating the four `*Reservation` methods in both is a
test-only change; include it.

## 6. Added scope: runtime mission admission

### 6.1 What admission checks

`SatelliteMissionRegistry.admission` (`:618-646`) checks `budget.fits(MISSIONS, sizer.missionBytes(mission))` (`:641`).
For a mission whose sizer variant does not encode, `missionBytes` is the capped 4,096 bytes and the `proven` flag is
ignored, so the mission is admitted unproven (P: `startResource` returns SUCCESS with reservation 4,096, unproven).

### 6.2 Can an admitted mission become unencodable?

**Not when its table passed ADR-052's check and its identifiers have at most 128 canonical characters.**
- An asteroid or gas mission can reach only ACTIVE, READY, QUARANTINED from ACTIVE or READY, CLAIMED and CANCELLED.
  The constructor allows CLAIM_PENDING_DISCOVERY only for data missions (`MissionState.java:68-70`).
- The table check (`SatelliteRecordFit.resourceMissionBytes`, `:49-66`, called from `ResourceTables.java:58, :76`)
  measures QUARANTINED from READY with 128-character definition, target and terminal identifiers, a 48-character
  reason and every payload extra. That is exactly the largest reachable state.
- The probe's mission has a table fit of 4,057 bytes. Its reachable states encode at 3,919 to 4,057 bytes, and the
  largest equals the fit. Save and reload succeed (P, `RUNTIME_*` lines).
- It is unproven only because the sizer's variant, held from CLAIM_PENDING_DISCOVERY with both times, is 40 bytes
  larger and cannot be reached by a resource mission at runtime. Only a decoded record can be in that state.

So for content within those assumptions, the gap costs performance, not correctness. While such a mission exists,
every ground operation takes the whole-root check (W168's slow path, about 200 ms P95 on large roots), as R-030's W168
text already says. This affects only custom tables with a fit of 4,057 to 4,096 bytes. Shipped content is far below:
a gas mission carries one product (`ResourceTables.java:76`), and even W168's fixture shapes with 17 long reward items
reserve only 3,559 to 3,817 bytes (W168 record).

### 6.3 The real path past 4,096 bytes

The assumption "identifiers of at most 128 characters" fails where content parsers bound the spelling:
- `SatelliteKindDefinition.parse`, `SatelliteKindDefinition.java:158-167`;
- `ResourceTableCodec.id`, `:134-144`;
- `SatelliteComponentDefinition.location`, `:100-107`.

The data-definition codec (`BoundedCelestialCodecs.validateResourceLocation`) and the celestial reload listener
already check the canonical length.

**For kind definitions it is reachable from a datapack.** The catalog only requires the file's resource ID to equal the
declared ID (`SatelliteCatalogDecoder.java:90`). So a file `data/minecraft/satellite_definitions/<128 characters>.json`
that declares the bare ID defines a 138-character satellite definition (P, `KIND_DEFINITION` lines):
- the shipped catalog, with its data definition and components, accepts it;
- a satellite launched from it saves;
- the next load refuses `definition_id` (spelling over 128), so the registry is blocked and the world refuses to start.

**The fit check also undercounts its missions.** Asteroid and gas missions copy the satellite's definition ID, so the
table check undercounts them by 10 bytes, and a near-bound mission can then exceed 4,096 bytes in a reachable state.

Table items and component items must name existing items. A 129-to-138-character canonical item is possible only if
some mod registers such an item in the `minecraft` namespace.

### 6.4 Same root cause as the decoded case? Does the same option close both?

**Not the same root cause, with one exception.**
- The decoded case is a persistence-boundary check of the spelling instead of the canonical form: the loaded record is
  over its bound already.
- The admission gap is a conservative lifecycle variant that runtime missions cannot reach, plus an admission that
  ignores the proof. The two share only the symptom, an unproven reservation capped at 4,096 bytes.
- The exception is the only path by which a runtime mission really exceeds 4,096 bytes: section 6.3's spelling-bounded
  parsers. That is the same root cause as the decoded case.

**Option A does not close the runtime case**, because it acts at load. Section 6.3's parser checks close the real path.
Both apply one principle: bound and check the canonical identifier wherever an identifier enters.

### 6.5 Options and recommendation

- **R-a (recommended): no admission change**, plus the parser checks of section 6.3 in the slice of section 4.
  - The parser checks add `id.toString().length() <= 128` after parsing.
  - Compatibility: a datapack with such an identifier is refused at reload. On the initial load it fails to start
    (`SatelliteDefinitionReloadListener.java:68`) instead of producing a world that refuses to start after its next
    save.
  - Risk text: none new, since these are content-load refusals of the kind ADR-052 already makes.
  - ADR: an implementation note under ADR-050 section 3 ("record bounds are derived … with 128-character IDs").
- **R-b: refuse unproven missions at admission.** It changes answers for tables the ADR-052 check accepts today: their
  missions would never start. Making it coherent means aligning `SatelliteRecordFit` with the sizer's +40-byte variant,
  which is a datapack-visible change. Not recommended.
- **R-c: a state-aware sizer** that reserves only the states a record can still reach, while still covering a decoded
  record's current state. Runtime missions from accepted tables would then be proven and keep the fast path, with no
  change in answers. But it reworks the sizer that W168 just had reviewed twice. Keep it as a later performance item,
  if the G7 frequency measurement shows that the fallback matters.

## 7. Commands and results

All Gradle runs used `JAVA_HOME=C:/Program Files/Java/jdk-17.0.7` and
`gradlew.bat --offline --no-daemon --max-workers=2 -I D:/GitHub/ARCE-Task-Evidence/root-queue/W173/w173.init.gradle w173Probes --console=plain`,
in this worktree at `e8f5b2d17`. Free memory before the runs was 13.7 GB. No heavy-JVM lock was needed.

| Command | Result |
|---|---|
| `git merge --ff-only e8f5b2d17` (the shared head with W168, from `24ae278af`) | Fast-forward; no conflict. |
| Probe run 1 (`probes-run1.log`) | Exit 1: 15 tests, 4 failed. All four were fixture errors in my probes (a mission base too small to pad to 4,096 bytes, a battery stat below its cap, a timing fixture too small, and a catalog without a data definition), not product behaviour. The fixtures were fixed; no assertion was relaxed. |
| Probe run 2 (`probes.log`, `probe-results/`) | Exit 0: **15 tests, 0 failures, 0 skipped** in 5 classes: normalization 2, record growth 6, root growth 2, current behaviour 2, runtime admission 3. |
| History audit (`git log --follow`, `git show` of each version, every `putString` checked) | `SatelliteNbtCodec`: 3 versions since `e78e63fc4`; `GroundSurveyCodec`: 1 version. No identifier is written without `toString()`. `git grep` found no 1.12.2 satellite importer; the only legacy importer is the celestial XML one. |
| `python -B scripts/validate_repository.py --require-approved-identity`, with this record staged | Exit 0: 45 passed, 0 pending, 0 warnings, 0 failed. `git diff --cached --check` exit 0; the file is LF. The rerun on the record commit is in the final answer. |

**Cleanup.** I removed this session's probe outputs, `build/w173-probes` and `build/test-results/w173Probes`. The rest
of the worktree's `build` is ordinary Gradle output. Nothing outside this worktree and the W173 evidence folder was
written; the launcher's `run-20261028-054632` folder is not mine and is not in the manifest.

## 8. Not done, and known risks

**Not done**, as the TASK requires: no production change, ADR, risk-register entry or status file. Root decides, with
the owner, whether a compatibility change is involved. Not run: GameTest, full unit suite (no repository code
changed), `runData`, build, server. The dependent effects of section 2 (terminal, discovery, deliveries) are by reading.

**Known risks:**
- **Option A refuses some worlds that start today.** Only edited or foreign roots are affected, and they already lose
  every session; owner approval is needed.
- **The diagnostic gap is not specific to W173.** `SatelliteMissionSavedData.load` drops its exception
  (`SatelliteMissionSavedData.java:112-114`), so today every R-030 refusal at start says only "failed semantic
  validation". Option A's diagnostic fixes it for all of them.
- **Lifecycle growth of loaded records.** A loaded near-bound record that re-encodes at load can still outgrow its
  bound through a reachable transition, for example quarantine or a claim adding times and a paying terminal. That
  happens only for edited or foreign records that are already unproven, so W168's fallback covers their ground
  answers, but a later save can still fail.
  - Closing it fully needs R-c plus a load check that every record's reachable states encode.
  - Requiring today's proof at load instead would refuse missions this mod admitted itself (section 6.2).
- **Unknown keys are dropped** in satellite and mission records (section 1.1). That is silent loss of foreign data,
  outside this task.
