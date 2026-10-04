# Save-refusal / R-021 boundary research audit

## Findings first

**0 Critical / 0 High / 2 Medium / 0 Low new findings in this read-only disclosure/evidence audit.** The underlying risk of rolling back unrelated terrain-chunk changes remains serious and open; these counts do not downgrade or accept R-021. No new current-server reproduction or source regression is claimed.

### M1 — R-021 and ADR-064 do not describe the actual scope and trigger set

**Locations:** live `docs/11-RISK-REGISTER.md:25`; live `docs/decisions/ADR-064-CLASSIC-MACHINES-FLUIDS-AND-COMPONENTS.md:392-423`; immutable code `persistence/ChunkSaveDenials.java:9-29`, `machine/recipe/RecipeSignatureProtection.java:42-62`, `machine/recipe/RecipeSignatureMigration.java:35-43`, `persistence/BoundedNbt.java:22-79`.

R-021 names three 8,192-byte resource roots and a single affected chunk. Code at the reviewed commit also guards Rolling Machine, Precision Assembler and Electrolyzer marker/process/journal/legacy roots. Refusal is not byte-only: depth/node limits and non-lossless native tag shapes can reject inputs below the byte cap. Most importantly, after **256 distinct remembered chunk denials**, the **257th distinct denial saturates the per-ServerLevel guard and every subsequent otherwise-unrecorded chunk coordinate in that Level is denied too**. Other Level instances are independent. Duplicate denials do not grow the map or saturate it early.

The accepted ADR says the old chunk must not be replaced and requires backup-based administrator repair, but does not specify this Level-wide overflow consequence, 256-record threshold, sticky lifetime, production reset prohibition or exact recovery authority. Live `AGENTS.md:68-70` now expressly requires the affected object/chunk/Level, duration, log volume and recovery to be in an ADR. An earlier task/source review disclosed the conservative saturation design; that historical disclosure is not an amendment of ADR-064 or an owner acceptance of R-021.

**Required boundary:** keep R-021 open; have the document owner correct the six-BE/four-consumer inventory and byte/shape/physical-count trigger set; separately version an ADR amendment stating actual chunk versus saturated-Level scope, lifetime and recovery authority, or propose a reviewed implementation change. Neither automatic truncation nor silently selecting a new quarantine policy is justified by this audit. The row's “uncommitted” wording also no longer describes code committed at the immutable reviewed commit.

### M2 — The one-ERROR-per-save disclosure is false and there is no guard-level retry/log cap

**Locations:** live `docs/11-RISK-REGISTER.md:25`; immutable `persistence/GuardedChunkSaves.java:47-64`; pinned primary `ChunkMap` save/catch and `saveChunkIfNeeded` excerpts in [input evidence](INPUT-EVIDENCE-01.json); historical Tank03 stdout lines 67/114, 150/196, 231/269, 314/343, 361/392 and 412/441, independently measured in [verification results](VERIFY-04.json).

Each of the six actual historical native refusal attempts emitted **an EventBus ERROR and a ChunkMap ERROR**, with exception details/stack traces: **12 ERROR headers**, not six. That run exited cleanly with code 0 and emitted “All chunks are saved” information despite preserving the rejected old compressed chunk. The guard source in that historical packet is byte-identical to both common guard files at the reviewed commit; the historical module/JAR is nevertheless not a new native execution of this commit.

The shared guard has no logging or retry quota. Vanilla's 10-second successful-save cooldown is only recorded when save returns true; the failed path restores dirty eligibility but does not receive that success cooldown. Accessibility/unload scheduling still affects attempts, so this is **not a claim of one attempt per tick**. Repeated explicit saves, unload attempts and stop passes can emit further error pairs for the Level lifetime. Fixed 256-record memory bounds are not a bound on total logging work or bytes.

**Required boundary:** correct the quantitative disclosure and document the chosen operational/logging recovery policy in the ADR. A finite, source/JAR-pinned dedicated-server run must measure attempts, both logger contexts and total log bytes under a fixed workload before a cap or rate is claimed. Merely adding a logger throttle in the shared class would not suppress Forge EventBus/ChunkMap's exception logs. Do not treat exit 0 or “All chunks are saved” as a per-chunk durability acknowledgement.

## Scope, identity and independence

- Immutable code: **`3f3d62aed3980186fe0acc9592cf93ca436405fb`**. Git object reads use that full SHA, never moving HEAD. Initial inspection and final postcheck found 18 selected committed source/test files byte-identical to the live copies; the additional Precision disk verifier is pinned separately by the verification helper. No production files were changed.
- Live governance/risk/ADR are dated observations, not claims about what the code commit contained: observed UTC `2026-10-04T05:45:54.346792+00:00`. AGENTS SHA `1be0391c3f69f566ae3f627e37107adfbd0bbfa6f581b105ff6bd2a2c2630dc0`; risk-register SHA `fc84e8d2233cccba1abd009cdcb4eecad02f2921fc5f46126e6cf3e0ee32d0f5`. All 16 named live documents were unchanged at [postcheck](OBSERVATIONS-03.json).
- Mandatory project/product/porting/version/test/Gate/quality/parallel documents were read in full. This is a bounded current-v1.8 research slice, not an implementation or release assessment.
- This reviewer independently reviewed the earlier Root-authored shared guard and earlier Tank03 native result. Those original packets remain unchanged. This task rechecked selected bytes, native logger counts and primary flow; it does not pretend those earlier executions are new independent runtime replay.
- Own output only: `D:/ARCE-Task-Evidence/v1.8.0/asrf-5ae80336d4`. No ZIP, whole source export, mutable runtime/world read, private documentation-bundle access, Java/Gradle/server/client/process action, network or repository mutation.

## Actual save unit and lifetime

### Terrain chunk, not one BlockEntity and not one entire region file

The Forge patch posts `ChunkDataEvent.Save` **after ChunkSerializer builds the outgoing chunk compound but before the terrain region writer**. Guard failure marks that ChunkAccess unsaved and throws. The primary ChunkMap catch logs and returns false. Thus a denied attempt does not replace that coordinate's existing stored terrain chunk; a newly generated coordinate with no existing record has no newly persisted terrain snapshot from that attempt.

The unit includes the outgoing sections/blocks/biomes/light, all saved BEs and their resources, scheduled terrain tick data, structures and other chunk metadata. Neighboring coordinates in the region file are not automatically denied by one ordinary per-coordinate denial. Saturation broadens this to all later terrain chunk writes in that ServerLevel. This is not an atomic rollback of the whole world or all persistence stores.

Guarded BE/root inventory at this commit:

| BE / module | Checked resource roots and native preflight limits |
|---|---|
| Pressurized Tank | `arce_pressurized_tank`: 8,192 bytes / depth 16 / 1,024 nodes |
| Pump | `arce_pump`: 8,192 / 16 / 1,024 |
| Combustion Generator | `arce_combustion_generator`: 8,192 / 16 / 1,024 |
| Rolling / Precision / Electrolyzer | `arce_recipe_signature`: 1,024 / 16 / 256; `arce_process`, `arce_process_journal`, `arce_machine`: each 65,536 / 20 / 4,096 |

The four consumers also reject an outgoing BE list over `256 * chunkHeight`. This is a separate structural trigger. `BoundedNbt` validates native fidelity as well as size: for example, a typed empty non-End list fails because native writing changes its subtype. Foreign writers and noncanonical NaN payloads also fail source preflight; this audit does not claim every crafted shape or NaN payload is reachable unchanged from a native on-disk load.

### Sticky identity, bounded memory, no production online clear

The state is a Level-owned capability, not a global static inventory or BE-owned flag. It stores at most 256 chunk longs and one bounded, first-wins reason per coordinate (maximum 256 characters), never resources/BE/chunk references. The HIGHEST common listener checks it before any module checks. Therefore a later outgoing chunk with no BE, a replacement ChunkAccess, corrected live roots or ordinary chunk unload does **not** clear a recorded denial.

The provider does not implement NBT serialization, so Forge's Level capability SavedData does not persist these denials. A newly created ServerLevel receives a fresh state. Provider invalidation closes its state and invalidates its optional; a closed state refuses all coordinates. Source does not expose a DedicatedServer/player/property reset. The only per-coordinate reset requires the actual `GameTestServer`, and cannot reset closed or saturated state. Do not infer that LevelEvent.Unload itself invokes this helper's close method: the precise bridge is capability invalidation, and the ordinary write lifetime remains the Level instance. Restart is not a repair: the unchanged bad on-disk root can trigger refusal again after loading.

### Dirty does not prevent unloading or prove durability

Pinned actual Forge-patched ChunkMap disassembly shows the unload continuation calls save, **discards the boolean**, then unloads LevelChunk. ServerLevel unload clears live BEs; the Forge LevelChunk patch invokes BE unload callbacks first. The sticky Level identity intentionally survives that BE disappearance so later serialization cannot write an empty BE list over the preserved record. It does not retain all unsaved ordinary chunk changes in a durable sidecar or veto unload.

Refusal is an IllegalStateException, caught by ChunkMap as Exception, not a mandatory server shutdown. The dirty flag makes another attempt eligible while the relevant state exists; dispatch/cooldown/accessibility still determine whether and when attempts occur. An explicit save-all barrier returning normally is insufficient. Existing Precision legacy Item migration additionally reads controller/port roots from disk before activation (`PrecisionAssemblerManager.java:348-411`); that qualified mechanism is not a general acknowledgement API for every guarded chunk.

## Unrelated-state and cross-store consequences

The known R-021 tradeoff is real: every ordinary change made only in that refused terrain chunk since its last successful snapshot can disappear upon unload/reload/clean restart, while the old unrecognized resource record remains. Historical Tank03 successfully changed the unrelated marker block at `(190,180,180)` to stone (stdout line 65); the resulting compressed terrain record nevertheless remained byte-identical to the injected original, SHA `b5a1e68eb7a52fc47c87278bf67c92f0f327878c7c5ab057b41df9ca17c63199`, 5,898 bytes. This is evidence of that particular rollback, not a full mixed-state test at the reviewed commit.

Primary save flow also shows **POI flush happens before guard admission**, Level SavedData is saved separately, full-LevelChunk entity state uses its independent store, and player saving is separate during server stop. Terrain refusal is therefore not a cross-store transaction. Resource transfers between a refused terrain BE and a player/entity/other chunk could create divergent durable generations if not independently coordinated. That is a source-derived risk scenario, **not a reproduced current duplication/loss finding** here. Existing save-all completion/clean-stop messages cannot close it.

## Evidence actually executed

1. Read-only Git object/search/status/worktree queries and named source/primary/archive inspection. [Inspection helper](inspect03.py), [raw log](inspect-03.log), [exit](inspect-03.exit): **0**. Source/doc/primary member hashes and exact line excerpts are in [input evidence](INPUT-EVIDENCE-01.json).
2. Independent Python **static/source/archive consistency controls**, not Java domain tests or native execution: [helper](verify04.py), [raw output](verify-04.log), [result](VERIFY-04.json), [exit](verify-04.exit): **12 tests, 0 failures/errors/skips, exit 0**. They check guard branch/callback order/lifecycle, six BE IDs, faithful preflight, pinned primary save/unload/cooldown flow, historical actual error pairs and compressed-record equality, plus existing Precision disk readback.
3. [Additional observations/postcheck helper](observe03.py), [raw output](observe-03.log), [result](OBSERVATIONS-03.json), [exit](observe-03.exit): **0**; 34 original-input postchecks (18 immutable source/test, 16 live named documents), all unchanged; three additional primary Level capability declarations; one successful historical marker mutation checked.

All failed reviewer tooling is preserved in [failure history](TOOL-FAILURES.md): parser/path/manifest/descriptor/method-reference assumptions were corrected in separately named helpers and logs, not overwritten or called PASS. They are not production failures. No production assertions, budgets or timeouts were changed.

Primary pins:

- Forge `1.20.1-47.4.10-sources.jar`: 1,621,504 bytes, SHA `918a11bdfceace2752d4c29bddbdf327981e1f6a1e1f0675f23e5fbf01e226c0`.
- Exact mapped Forge 47.4.10 JAR: 19,355,242 bytes, SHA `95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`; reread/hash only, no Java invocation.
- Historical actual patched-server primary classfile source declaration: 4,848,366 bytes, SHA `1dcf74ad4961877f5c79d29b361596fd4222c8849f6db31fbcf663d1ba5ff072`; previously executed disassembly consumed from immutable `native-incident-01-evidence.zip`, not a new read of a runtime server.
- Historical Tank03 main JAR: SHA `edc06a9a5042349131a04d665321f51803319e4562deafbe4ec37b4dd071dc74`. Both common guard source bytes match the immutable current commit; other current code/artifact/native equivalence is not inferred.

## Minimal validation controls still needed

Keep all current budgets and original failures. Before any runtime risk acceptance, collect separately authorized, immutable source/JAR-pinned evidence on copied fixtures:

1. Mixed terrain chunk: unsafe root plus a healthy resource BE, block/biome/tick changes and an unrelated marker. Verify exact old record preservation and explicit expected rollback, no truncated resource root. Include a new coordinate with no old record. Inspect stopped typed NBT, not summary strings alone.
2. Denial lifetime: repeated save, BE removal/replacement, unload/reload, live repair attempt and clean restart; show what is denied before and after new Level creation. Do not expose the GT reset to a real DedicatedServer.
3. Capacity boundary: 256 unique denials plus a healthy coordinate, then the 257th; independently prove exact Level-wide denial and unaffected second Level. Existing five unit cases cover the domain branch, not this native saturation consequence.
4. Cross-store matrix: POI/Level SavedData/entity/player/neighbor chunk changes and a legitimate resource transfer. Compare every stopped store generation and count invariant; do not infer atomicity from terrain-record preservation. Test unload as well as clean stop. Forced-stop/crash testing is a separate claim.
5. Fixed-duration repeated-save logging: both logger headers, stack-trace/log bytes, elapsed time and attempts, including saturation; no console-success whitelist as durability proof.
6. Offline repair on a copied backup: exact selected region/chunk/root provenance and raw escrow, repeatable bounded scan, explicit repair decision, outside-window byte identity, two clean restarts and restored supported operations. Preserve unknown typed data or original compressed records before choosing a conversion; errors do not justify deletion.

New fixture/test code, DataGen/native execution and eventual release evidence are future authorized work, not completed by this research task. Client/GPU/V2/version Required Gates were not run.

## Feasible workflow/options — not selected owner semantics

**Immediate documentation/operational work without behavior changes:** correct R-021; write a scoped ADR amendment with current exception mechanism, normal per-coordinate and saturated-Level scope, known logging, bounded memory, lifetime, missing diagnostics and exact backup/restart limitations. Provide a bounded read-only diagnostic inventory (dimension/chunk/reason/saturated status, no raw resources/online reset) and a server-owner procedure: halt interactions, preserve copied full affected stores and manifests, stop, work only on a backup copy, validate the selected repair, then reopen a newly created Level after evidence. This does not accept the existing loss tradeoff by itself.

**Offline repair option:** retain exact compressed terrain/root bytes as escrow with a bounded index before selecting an explicit conversion/removal. A repair tool must reject unsupported compression/size/shape safely, account for list subtypes and scalar widths, never silently rewrite a whole world, and require an operator-selected repair policy. Raw passthrough that the native writer cannot preserve is not made safe by calling `copy()` or by a JSON/SNBT round trip.

**Narrower BE quarantine option:** requires a separately frozen contract for durable raw sidecar ownership, schema/budgets, coordinate/generation binding, resource-access disablement, cross-store coordination and atomic acknowledgement/recovery. Merely dropping the offending BE, truncating its root, throwing from `saveAdditional`, or writing an old BE into an otherwise-new chunk is not demonstrated resource-conserving. Forge's BE serialization catches a thrown save exception and omits that BE; `ChunkDataEvent.Save` and `ChunkEvent.Unload` are not cancelable. Current evidence does not admit a general writer adapter or a claimed durable per-BE quarantine.

**Stop/read-only containment option:** preventing interactions/transfer publication after the first denial or stopping the server may reduce additional divergence, but changes live behavior and availability. Global stop, automatic quarantine, online reset, resource discard and a revised saturation policy are material product/operator decisions. This audit selects none of them. Any writer-hook/Access Transformer proposal must show why existing Forge facilities are insufficient under the repository rule; no ASM/coremod permission is inferred.

## Completion / remaining scope

Completed: static current-code behavior inventory, two disclosure findings, exact primary/historical observation crosschecks, repair/validation options and a small loose immutable evidence bundle. Changed repository files: **none**. Added checks: reviewer-only Python/source/archive controls in this directory. Uncompleted: production repair, admin tool, current dedicated-server reproduction, saturation/unload/cross-store native validation, owner risk acceptance and all version Gates. Next work can only be the documented current-v1.8 R-021/ADR/repair-validation slice. Root alone decides adoption/status; no ADR, risk or Gate closure is granted by this report.
