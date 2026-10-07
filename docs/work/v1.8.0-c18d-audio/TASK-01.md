# CL18D-AUDIO-01: ten audio lifecycles and verification contract draft

Date: 2026-10-07. Milestone: v1.8.0 / C18d. Task type: contract/test design.
Owner: Claude, delegated external worker. Integrator/sole Git writer: Root/Codex.
Reviewer: a different read-only Codex session, assigned on actual draft return.
Current status: author-returned / CHANGES_REQUESTED after independent review:
seven Medium. Original READY registration below remains historical.
See [Root review](../v1.8.0-claude-return-review/REVIEW-01.md) and [TASK-02](TASK-02.md).
Contract remains proposed, not frozen; no audio implementation/import authorized.

## Registered checkout and ownership

- Base branch: codex/v1.8.0-classic-content.
- Actual base commit: a65dcbf68143ce63af3b2205b0c36af02eaae0e8.
- Branch: codex/v1.8.0-claude-audio-contract-20261007.
- Worktree: D:/GitHub/arce-v180-claude-audio-contract-20261007.
- Evidence: D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-audio-claude-author-20261007-01/.
- Read the published Root-checkout TASK and record its SHA-256; the TASK is
  newer than and absent from the fixed worker base.

Entire repository write_scope: three NEW files in the registered worktree:
- docs/work/v1.8.0-c18d-audio/CONTRACT-01.md
- docs/work/v1.8.0-c18d-audio/TEST-DESIGN-01.md
- docs/work/v1.8.0-c18d-audio/HANDOFF-01.md

No edits to this TASK or any other repository file. All actual source, public
contracts, registry/config/network/save, global client entry points, shared
machine/rocket/station state, DataGen/generated sounds/lang/resources, build,
source approvals, AGENTS, status/log/ledger/CSVs and Git mutation remain Root's.
No SoundEvent registration, audio/controller implementation, OGG creation or
import is authorized. No silent placeholder or unrelated asset is delivery.

## Exact assigned units and observable output

Ten fixed CL18D-AUDIO CSV units:
`sound_event:airHissLoop`, `sound_event:basicLaserGun`,
`sound_event:combustionRocket`, `sound_event:ElectricShockSmall`,
`sound_event:gravityOhhh`, `sound_event:laserDrill`, `sound_event:lathe`,
`sound_event:MachineLarge`, `sound_event:railgunBang`,
`sound_event:rollingMachine`.

For each, propose modern ID, loop/one-shot semantics, actual authoritative
producer and immutable client state input, start/pause/stop/reload behavior,
distance/priority and asset provenance path. Separate existing observable
events from unavailable future producer/summary ports; never invent a working
server event or infer authority from a client animation/name. Specify bounded
duplicates/stale state/reconnect/Level change/unload behavior and ownership.

ADR-066 section 7.1 limits actual loops to client distance 32 and at most 32
simultaneous ARCE loops, prioritizing nearest sources. Define deterministic
ties, finite caches/lifetimes/work and no per-audio-frame server packets.
Reusing an approved OGG for lathe/rolling does not merge their SoundEvent IDs.
Propose exact Root integration surfaces and any genuinely missing inputs.

## Inputs, dependencies and non-goals

Read mandatory governance/current-version docs, live Root AGENTS read-only,
fixed allocation CSVs, ADR-066 sections 7.1/7.3/8, ADR-061/062 asset rules,
current client registration, synchronized caches and producer implementations,
plus relevant existing tests/source records.
Current registry/ModSounds.java and datagen/ModSoundDefinitionsProvider.java
register only ui_select; adjacent ambience/feedback is not these ten events.
Before upstream inspection read
UPSTREAM, NOTICE and docs 02/08. Inspect only existing locally available,
pinned permitted sources; no cache/archive, new download, copy or import.
Record full commits/hashes and unresolved authorship, not absence of Vorbis
comments as license clearance. Five rejected silent placeholders stay rejected.

Machine/vent/weapon/rocket inputs and bounded S2C binding depend on separately
reviewed Root capabilities; this task cannot clear shared-save dependencies.
NEW synthesis/recording needs pre-authoring provenance and later waveform plus
manual listening evidence, not a text promise or PNG detector result. No
creation/import/asset approval here. Real GPU/multiplayer/actual audio checks
are future executions, not achieved by designing tests.

## Allowed commands and evidence

Use the owner's existing interactive Claude session; no Root-launched or newly
authorized paid CLI/API run. Report actual model/session/time/usage observations
and unknowns, never credentials. Verify status/HEAD/branch/worktrees before
writing; do not adopt others' changes. Begin HANDOFF with actual start time,
TASK hash, checkout identity and scope. At 90 minutes return a checkpoint if
unfinished; do not broaden the task. Each repository draft <=128 KiB, owned
evidence <=10 MiB.

Allowed: finite PowerShell file reads/hashes/listing, rg, read-only Git status/
rev-parse/branch --show-current/worktree list/diff/show/ls-files, manual scoped
edits and explicit Python -B stdlib text/CSV/hash/diff/link checks (not pyenv
shim). Scripts/logs stay in the owned evidence leaf; TEMP/TMP/TMPDIR are set
only for the worker process to its temp subdirectory. No javac/java/JVM,
Gradle/server/client/audio runtime, cache/archive inspection, downloads/network/
installs, nested agents, global settings, others' cleanup, Git writes or
ledger/Gate changes. Report any needed additional check instead of running it.

## Acceptance, return and release

CONTRACT covers all ten units, evidence-backed producers/asset disposition,
proposed immutable inputs and lifecycle/budget boundaries, with open issues
explicit. TEST-DESIGN separates pure lifecycle/priority cases, future actual
producer/S2C/client fixtures and real listening/V1/V2 runs. Include stop/pause,
duplicate/stale/missing state, distance/ties/32-loop saturation, unload/Level
change/disconnect, resource/config reload and rejected/missing/invalid sounds.
Audibility/provenance acceptance must require actual appropriate evidence.

HANDOFF: actual new-file diff/hashes, commands/exits/raw failures, executed
versus planned checks, license/config/schema/network/save impacts, exact Root
needs and unresolved/unrun items. Preserve sealed inputs and explicitly release
all processes/reads/HEAD/index interests. Root reviews actual drafts before
contract freeze or a new atomic source/asset task. No content/G0-G9 delivery.
