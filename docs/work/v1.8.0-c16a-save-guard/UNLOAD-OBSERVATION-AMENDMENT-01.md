# Native unload observation amendment

Date: 2026-10-04. Owner: Root. Status: IN_PROGRESS.
Baseline: pushed tool-source commit `a382e36e4e1ce5949bb9cc9c9ab3517ebfd9d0ce`.

The original native attempt failed its live restoration assertion. Its loaded
predicate is not an unload-completion signal. Preserve that exact attempt and
every restoration/resource/record assertion. This amendment adds observation,
not a guard-policy change, online repair, resource authority or risk acceptance.

Write scope: one new common/server diagnostic class,
`gametest/GuardedChunkUnloadObservation.java`; the already committed lifecycle
script and its focused tests; this amendment and later verification records.
No existing production class, registration, schema, protocol, budget, recipe,
asset, AGENTS.md or sealed packet is changed. Root is the only source writer.

The listener uses the existing opt-in release-test property. It observes only
the server thread, overworld LevelChunk (11, 11), and logs one exact INFO marker
per observed Unload event. It stores no world/chunk/resource state and neither
loads chunks, modifies events/world data, clears denials nor changes capabilities.
Normal unconfigured runtime produces no probe marker.

Pinned Forge 47.4.10 primary flow posts ChunkEvent.Unload before ChunkMap.save
and ServerLevel.unload/BlockEntity cleanup. The marker is therefore an unload-
begin observation, not a cleanup-completed claim. DedicatedServer queues console
input and executes it synchronously; absent reentrant/erroring listeners, the
later ticket-add command cannot interleave that server-thread continuation.
Require observed predicate change AND a fresh actual event before reacquisition,
then independently require the original live restoration and stopped bytes.
Keep one total 60-second/240-probe observation budget, not two separate waits.

Validation: exact logger/INFO marker, fresh start index, no fake/old/wrong marker,
deadline exhaustion, modeled retained mutable chunk refusal; separate actual-
source review; full build/test/DataGen/GameTest for the new Java class; a new
copied-world native attempt and independent stopped-byte/log audit. Pin the new
JAR separately. Do not reuse the previous JAR's native result as this build's
evidence. Source review and actual native result are separate dispositions.

Remaining: reentrant foreign listener behavior, exact BE callback order,
first-save observer/writer, protected hashes/codecs, 256/257 and cross-store
recovery, physical hatches and all Required Gates. R-021 remains open.
