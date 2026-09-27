# ADR-038 — Ordered planetary discovery recovery

```yaml
status: ACCEPTED
date: 2026-09-27
owner: sunthemoon
scope: v1.4.0 migration and recovery
authority_basis: standing maintainer authorization to use recommended implementation decisions
```

## Context

The mission store owns captured research awards/fees and claim phases. The
celestial store owns shared discoveries and visits. The existing manager moves
both in-memory objects to their final states and calls the whole DataStorage
save. Pinned Forge 47.4.10 / Minecraft 1.20.1 bytecode shows unordered cached
SavedData iteration and a file writer that catches an IOException and clears
dirty state. A completed mission can therefore reach disk independently of its
discovery, or discovery can reach disk before the corresponding paid receipt.

ADR-037 deliberately does not certify arbitrary interruptions. This decision
strengthens that runtime behavior without changing its gameplay policy.

## Decision

1. Keep both stable `.dat` identities, outer `data` / `DataVersion` wrapper,
   all existing root/record schemas and state values. No merged progress store,
   migration rewrite, new public API or wire status is introduced.
2. Give these two SavedData types acknowledged, bounded, same-directory atomic
   replacement. Serialize a full snapshot, write and force a staging file,
   bounded-read it back, then atomically replace the authority. No non-atomic
   fallback. Ordinary autosave/shutdown uses the same writer and keeps dirty
   state on failure; explicit claim barriers propagate failure. Other stores
   and the existing pre-start migration transaction remain unchanged.
   The fixed staging file is scratch, never a second authority. A retry may
   overwrite a regular staging file left by an interrupted write, only from the
   loaded authoritative state. Refuse links and non-regular files/directories.
   Existing launch/start/cancel mutation paths also retry a dirty failed save
   before returning, including an idempotent launch retry. A player cancellation
   with no current mission still returns MISSION_NOT_FOUND without flushing;
   any dirty state remains eligible for ordinary autosave. This does not make
   cancellation-by-identity idempotent or make the terminal's separate chunk
   inventory and mission file atomic.
3. Claim in ordered stages: durably write the existing pending claim plus its
   single research award/fee; durably write the celestial discovery; only then
   finalize and durably write the mission. Do not publish a newly awarded
   discovery in memory until its checked disk replacement succeeds. A failed
   barrier reports the existing unsupported-data result and remains retryable;
   it must not continue to the next stage or credit research again.
   Before proceeding on a retry, acknowledge the mission snapshot even for an
   unchanged pending/claimed result. Likewise acknowledge an existing discovery
   (including a dirty visit) before finishing; presence alone proves no disk
   commit. An unavailable target can remain queued without a per-tick disk write;
   the initial claim still saves its paid pending receipt.
4. Startup replays paid pending receipts. It also repairs missing discoveries
   evidenced by historical CLAIMED missions with `discoveryRequired=true`;
   cancelled, active, ready and non-discovery missions grant nothing. Removed
   targets wait until the same ID is restored. Historical CLAIMED receipts stay
   CLAIMED while waiting for missing/full/future celestial data; they are queued
   for repair, not demoted or paid again. Existing discovery
   and visit timestamps are not replaced; a repaired missing record uses the
   present nonnegative world time, not the distinct mission logical clock.
5. Runtime pending claims join the same deduplicated, server-lifetime queue.
   Keep at most the existing 8192 mission-record bound and process no more than
   eight receipts per tick. Rotate unavailable work so one removed body cannot
   prevent another body from progressing. Persistence errors remain queued and
   are reported without a per-tick exception storm. Clear the queue on stop.
6. Preserve discovery's 128 retained-ID cap, future-schema refusal, private
   accounts, shared unlocks, captured concurrent fees and station membership.
   No synthetic visits, station-orbit rewrite, forced chunks or replacement of
   missing definitions. A full store keeps the paid receipt pending.

## Compatibility and limits

The ordinary schema-1-to-2 startup migration still validates and backs up old
data. This change cannot reconstruct pre-existing corrupted files or infer a
lost historical fee from a discovery without a paid mission receipt. Restore
those worlds from a complete backup; do not manufacture or revoke progress.
Do not delete the planetary binding ledger to resolve a mapping conflict.

The target guarantee is ordered process-interruption recovery on a filesystem
supporting same-directory atomic replacement. File force and rename are not a
portable guarantee against disk failure or sudden power loss of directory
metadata. Keep complete backups; unsupported atomic moves fail closed. Legacy
artifact downgrade must use its matched pre-upgrade backup, not a mixed pair
of SavedData files. Other rocket/chunk/inventory transaction guarantees are not
expanded by this two-store change.

## Verification

Test actual persistence bytes at each barrier, retry in the same process and
after reload, ordinary autosave failure, unavailable/restored bodies, old paid
receipts, no research duplication, full/future stores and replay bounds. Include
finite packaged restart/interruption, complete short build/DataGen/GameTests and
independent diff/key-check review. Preserve original failed outputs. This does
not approve V1/V2, reference-hardware performance or any version Required Gate.

Platform references: [Forge SavedData documentation](https://docs.minecraftforge.net/en/1.20.1/datastorage/saveddata/)
describes dirty/save ownership; local pinned `javap` inspection supplies the
specific file-write and cached-iteration evidence. The documentation alone is
not evidence of crash atomicity.

Recorded under standing maintainer authorization after independent source/draft
review clarified repeated acknowledgments and historical terminal-state repair.
This is not a new numbered human vote or a release approval. Implementation and
independent final verification remain separate tasks.
