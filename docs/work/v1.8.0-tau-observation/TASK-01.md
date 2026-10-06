# Tau Ceti failure-only fixture observation

Status: PROPOSED. This brief is not source assignment, adoption, implementation, or a runtime verdict.

## 1. Fixed input and objective

Source basis: `a34de0ad5edb0a2b3efe6ad2bb76c17e40fafc43`.
Target: `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/TauCetiPathGameTests.java`, 15699 bytes, SHA-256 `d69fd897c39d0b1700d69f55061955bf695749bc9dcbaa9384feef9d954ca11d`.

The exact-a34 investigation reports a missing Tau destination while its journal is PREPARED, destination UUID unassigned, and source TRANSIT/not removed. The existing marker is emitted after `findLogicalRocket` primes pad chunks. Add a before/after observation that distinguishes those samples without changing execution, transaction policy, or the required result. No unique cause is established or selected.

## 2. Exclusive prospective source scope

One existing file only: the target above. Allowed changes are the single route fixture's private `landed` call sites, the missing-rocket diagnostic branch, and private bounded capture/format/emission helpers. Both other GameTests, the Tau-f and Tau-g ground cases, remain byte-identical. Existing shared fixture, runtime, transfer service, journal, loaders, entity classes, registries, APIs, schema, configuration, assets, budgets and central/status documents are not writable. No live-service trace is included.

Use only local immutable sample text or a private nested scalar value. Do not retain Entity, ServerLevel, transfer-record, NBT, exception, or world references in a sample; no static mutable collection or cross-tick history. No new source file, generic logging framework, test hook, production callback, or public signature. Keep the class under 500 lines; report if that cannot be met instead of expanding scope.

## 3. Exact call order

1. The original `clearTransferJournal(earth)` at line 60 already initializes the journal. In the original setup try/catch (lines 74-94), capture one nullable observer journal reference from the existing cached `RocketTransferSavedData.get(fixture.server)`. This is not a loaded-only acquisition API: it delegates to `computeIfAbsent`. Its use here is justified only after that existing initialization in this exact fixture. Contain diagnostic-only acquisition exceptions and use null on failure; never force reinitialization or add a save/flush. Pass this reference through the four private `landed` calls (lines 96,112,117,129), without changing their order or arguments' existing meanings.
2. At entry to private `landed`, capture a PRE scalar sample using that reference and the provided expected Level/logical UUID. Capture occurs before the **unchanged, single** `findLogicalRocket(level, logical)` call at current line 179.
3. Run the original lookup exactly once in the original position. Do not intercept its exceptions, remove its existing pad priming, or add loading/ticket behavior.
4. Only if the returned rocket is null, capture POST independently from the same already initialized journal reference. Do not reuse a pre-sample transfer record/phase as if it were the post state. Attempt exactly one PRE and one POST diagnostic emission, then execute the original `helper.assertTrue(rocket != null, "No rocket at " + where)` outside all diagnostic catches.
5. A successful lookup emits no new line and discards PRE immediately on return. The subsequent flight/landed-state failure body (current lines 184-192) is unchanged; this brief instruments only the null/missing assertion. The existing setup catch, guarded callbacks, cleanup, and success path remain unchanged.

Replace the old missing-only post logger rather than retaining it as a third line. Prefix stays `ARCE_TAU_CETI_MISSING`, with explicit `sample=PRE` or `sample=POST`; these are private diagnostic labels, not a public/network/persistence protocol.

## 4. Fields and read-only primitives

Each sample is independent. Record fields are read through `journal.findByLogicalRocket(logical)` only; this searches at most the existing 64-record journal bound, without encoding/copying/saving it. Source/destination Level resolution uses the existing server `getLevel` route, not creation/loading. For absent record, Level, UUID, entity, or flight, explicitly mark unavailable; never substitute `false`, position zero, or an empty identity as an observed value.

| Field group | Exact content and primitive | Bound |
| --- | --- | --- |
| Sample identity | PRE/POST; logical UUID; existing four `where` labels (`the station`, `the warped station`, `Tau Ceti f`, `the station on return`) | 4/36/32 ASCII characters |
| Clocks | `helper.getTick()`, expected Level game time, available source/destination Level game times, `record.createdAtGameTime()`; `System.nanoTime()` sampled once per capture | signed LONG, maximum 20 characters each; unavailable is `NA` |
| Journal/record | `journal.operational()`; FOUND/NONE/UNAVAILABLE; record transfer UUID and phase enum | fixed booleans/codes; UUID 36; enum maximum 32 ASCII characters |
| Level identities | expected, source, destination ResourceLocation IDs from existing Levels/record snapshots | maximum 128 ASCII characters per ID; otherwise fixed `OVERSIZE_ID`/`UNAVAILABLE`, no raw oversize value |
| Source identity/state | record source UUID; exact `sourceLevel.getEntity(uuid)` kind ABSENT/NOT_ROCKET/ROCKET/LEVEL_MISSING; for a rocket, observed UUID, flight state or NO_FLIGHT, `isRemoved`, and `blockPosition` x/y/z primitives | UUID 36; fixed kind/state codes; signed INT position components |
| Destination identity/state | recorded destination UUID or UNASSIGNED; if assigned and Level exists, one exact `destinationLevel.getEntity(uuid)` and the same scalar kind/state/removed/position fields | same bounds; no search when UUID is unassigned |
| Destination origin | snapshot source-origin x/y/z primitives, or explicit unavailable | three signed INT values |
| Cached block presence | `destination.getChunkSource().getChunkNow(origin.x >> 4, origin.z >> 4) != null` | YES/NO/NA |
| Entity predicates | `destination.areEntitiesLoaded(ChunkPos.asLong(origin.x >> 4, origin.z >> 4))`; `destination.isPositionEntityTicking(new BlockPos(origin...))` | independent YES/NO/NA, not one combined inferred state |
| Diagnostic status | OK/NO_RECORD/JOURNAL_UNAVAILABLE/CAPTURE_FAILED/FORMAT_FAILED | fixed codes only |

Exact normal-message key order: `sample logical where test_tick journal record transfer phase expected_level expected_time source_level source_entity source_observed source_kind source_state source_removed source_x source_y source_z source_time destination_level destination_entity destination_observed destination_kind destination_state destination_removed destination_x destination_y destination_z destination_time origin_x origin_y origin_z loaded entities_loaded entity_ticking created_time sample_nanos diagnostic`. Use `key=value` ASCII tokens after the fixed prefix, one space between fields. `where` replaces its fixed label spaces with underscores; no arbitrary input is admitted. Boolean observations use YES/NO/NA. Identity/state/position/time unavailable uses NA except the explicit UNASSIGNED destination UUID and the fixed kind/status codes. If the entire capture is unavailable, a short line may contain only sample/logical/diagnostic; it must not present defaults as measured values.

Allowed observation calls are these exact getters, immutable journal/snapshot/flight scalar getters, native exact UUID lookup and `getChunkNow`. No `getChunk`, `getChunkAt`, `getEntities`/`getAllEntities`, pad priming, recovery/inspection service, region ticket, native-manager reflection, disk/NBT read, producer tick, `save`, `flush`, `setDirty`, journal/entity mutation, or new computed SavedData acquisition inside capture/emission. The original lookup and original non-LANDED failure path are not relabelled loading-free.

Do not print arbitrary `toString()` values from Entity, BlockPos, NBT, exceptions, or collections. For ResourceLocation formatting, check namespace/path component lengths before joining; avoid overflowing a length sum. No user-controlled labels or string interpolation containing newline/control characters. Enum names and unavailable codes are fixed; position/clock/UUID output is scalar.

## 5. Finite failure and allocation limits

- Four original private `landed` calls maximum. Each normally creates one PRE sample; only the first null failure creates POST and terminates through the original assertion/guard. No loop, retry, delayed task, tick listener, growing list or per-tick logging.
- At most one journal lookup, two exact UUID lookups, one destination cached-chunk lookup, and two native destination-predicate reads per sample; zero entity/world enumeration. No additional chunk activations.
- At most two log attempts/lines per fixture, each own formatted message at most 2048 ASCII bytes (fixed logging-system prefix excluded); combined own message budget 4096 bytes. Bound fields before formatting and enforce the final message cap. If capture/format fails or a final cap check fails, use one short fixed unavailable message for that sample, not an extra third log.
- Capture only bounded immutable text/scalars. No world/record/exception retained in static state or scheduled closures beyond the existing fixture-local observer journal reference. That journal reference is released with the original fixture closures; no listener or new lifecycle owner is installed.
- Catch `RuntimeException | Error` only around diagnostic acquisition/capture/format/emission, with fixed error codes and no exception message/stack/locals output. Never put original lookup, oracle, transfer, or cleanup inside these catches. If logging itself throws, do not try an additional warning/fallback log; execute the original assertion. Do not attach diagnostic errors as causes/suppressed errors to the required failure. Fatal diagnostic failures cannot be claimed immune to exhausted JVM resources; the intended containment is ordinary finite fixture execution.
- Original cleanup remains responsible for existing rocket/station/journal lifecycle. This task neither fixes nor weakens cleanup failure behavior.

## 6. What this cannot establish

PRE is before the fixture lookup; POST is after it. Neither is an atomic native transaction, a continuous time series, proof of no on-disk entities, proof of live-transfer membership, exact FRESH-versus-PENDING state, queue/future completion, or a durable save result. Exact UUID ABSENT means absent from that native lookup at that instant. Native ticking readiness is distinct from entity-load completion. Background work can advance while a capture executes; do not claim zero timing perturbation or a unique causal verdict. Unknown or unavailable observations remain unknown.

No timer/radius increase, prewarm, readiness bypass, new rollback/recovery behavior, retained-journal rewrite, fuel/owner/phase change, or owner policy selection is authorized by this diagnostic proposal.

## 7. Verification and publication order

After separate explicit assignment: author in an isolated fixed-base worktree using only the one-file source scope, preserve original preimage and any failures, freeze actual source pins/diff and release for different-agent source review. Independently compare all original assertion/fail calls and messages, delay arguments, 270/1400, annotations, route/ownership/fuel/target/block oracles, setup and cleanup bodies. Verify new capture allowlist, maximum lookup/log counts, exception boundaries, labels/field caps and immutable samples; inverse diff must restore exact a34 preimage. Static checks are not product execution.

Root alone integrates/commits/pushes a reviewed **unverified source checkpoint** for hosted execution. Record the actual committed SHA; do not claim verified delivery from a worktree or names of tests. On the existing reviewed ordinary v1.8 hosted workflow, bind checkout/head SHA/run/attempt and run the actual clean uncached build/JUnit, DataGen twice with tracked/untracked cleanliness checks, and **unfiltered `runGameTestServer`** at that exact commit. Collect raw command/exit/log/XML/receipt identities; independently audit the actual full required failure set and the Tau PRE/POST rows when present. Run/listener logging or an absence of a Tau failure does not alone certify the diagnostic fields or a unique fix. No new rerun/dispatch/network permission is granted here.

Keep every prior failed cohort and every new failure; neither filtered tests nor relaxed budgets may replace the full regression. Full local build/GameTest/native still requires the effective AGENTS >=10 GB space precheck. Native restart, real-client/GPU/multiplayer, persistence and all version Required Gates remain separate; this leaf adds observation, not product/Gate completion.

## 8. Root successor qualification

Date: 2026-10-06. Task: C17-TAU-OBSERVATION-02. Integrator: Root.
This is a separately authored assignment input, not yet adopted or assigned.
The original sealed proposal/report and independent review remain unchanged.
This successor corrects only the singular other-test scope wording: both existing
ground GameTests must remain byte-identical. It does not rewrite that historical
author report or claim any new source/native result.

The prospective checkout basis is published
`e0c601e4d58cb906d79f744070316c0a2d810c06`. Root must verify the target and
used dependencies still match the original a34 basis before assigning a fresh
isolated worktree. No worktree, Java/native execution or source authority is
granted by this document. A separate reviewed disposition and exact bounded
command grant precede source/verification work. Root retains central/task/Git
ownership; the future worker source scope remains exactly one existing file.
