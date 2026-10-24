# TASK W152B: N3-B2 resumes with sealed staging (Root decisions R93 to R96)

Root: Claude. Date: 2026-10-24. This continues W152 ([TASK-W152](TASK-W152-N3-B2-RETAINED-U.md)) in its session,
worktree `D:/GitHub/arce-v180-w152` and branch `codex/v1.8.0-w152`. First merge the shared branch head named in your
prompt into your branch; it has W151 (W98-R1), the design and these decisions.

Read [records/W154-DESIGN.md](records/W154-DESIGN.md) (revision 2), its review and re-review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W154-review/REVIEW.md` and `REREVIEW.md`), Root decisions R93 to R96 in
[TASK-W154B](TASK-W154B-SEALED-STAGING-REVISION.md), [TASK-W154-REVIEW](TASK-W154-REVIEW.md) (R94),
[TASK-W154-REREVIEW](TASK-W154-REREVIEW.md) (R95) and below (R96), and the N3-B2 isolation bullet in ADR-065 and
R-035.

## Root decision R96 (2026-10-24), on the re-review's W154-R2

The re-review showed that Forge's `AttachCapabilitiesEvent` hands every new block entity to global listeners at
construction, so an unpublished target can be referenced by code outside the adapter. Root resolves it as follows:

1. **Trust boundary, disclosed.** Code that keeps a raw block-entity reference from that event and uses it after the
   block entity is removed is outside the protection. It already defeats every vanilla container instance that a chunk
   unload leaves behind, whose contents also stay on disk. ADR-065 and R-035 now say so.
2. **Hardening.** On abort, the host marks the discarded target removed (`setRemoved`, which invalidates its
   capabilities). A built-in target is also emptied: inventory, tank and bay package, as vanilla's `dropContents`
   leaves a removed chest empty. So a retained reference reads nothing. An external provider's target must refuse
   access once removed, per the provider contract.
3. **Probe.** Extend R95 item 5's probe with an `AttachCapabilitiesEvent<BlockEntity>` listener that keeps the raw
   target. After each failure class, try access through that reference and through the world, and count the
   original and foreign items. For the built-ins, a retained reference must find the target empty. If not, stop and
   report.

## What to do

Implement N3-B2 as W148 section 7 states, with W154 revision 2's sealed staging under R93 to R96 replacing the native
terrain representation during the retained window.

1. **First commit, the probe:** R95 item 5 with R96 item 3, for the four payload hosts, the plain members and the
   `src/adapterTest` fixture provider. It records the callback order and the first reachable capability. If no
   one-step publication exists without a Mixin, or a retained reference reaches resources, stop and report.
2. **Then the slices in order:** the carrier block and its schema-1 root, `SEALED_DISASSEMBLY`, C0 to C5, unsealing
   and lazy unsealing, recovery, then the GameTests and native cuts of the design's section 8. Add the review's
   regressions for W154-R1 and W154-R2 and every R94 to R96 path.
3. **Art, data and text:** a uniform sealed texture with provenance and a pre-authoring declaration, EN and zh text,
   and `onlyOpCanSetNbt()` for the carrier.
4. **Unchanged:** TASK-W152's constraints, verification and "Not in this task" apply, except the saved-format change
   R93 authorizes. New log lines get exact manifest entries.

This is a persistence slice: a fresh Codex session reviews it before merge.

## Root decision R97 (2026-10-24), on the probe's stop (`c2b876c1`)

The probe showed that a server-bound target's `setChanged` reaches a neighbour's `onNeighborChange` synchronously
during restore, and that neighbour used a reference kept from `AttachCapabilitiesEvent` before the target was removed.
That window is inside the protection, and the stop was right.

1. **Every adapter prepares on a target with no Level**, built-in and external. With no Level, `setChanged` and the
   comparator path do nothing, so no callback runs between construction and publication or abort. The construction
   event fires before the target's own fields exist. The Level is set only at publication, by the installation step
   the probe already showed works.
2. **External providers.** At unseal the host calls restore and recapture on that level-less target, on the server
   thread, without the wrapper's level check. The host's own thread check replaces it for this call. The provider
   contract (API documentation, signatures unchanged) says restore at unseal receives a host-created target that is
   in no Level yet. A provider that needs a Level fails restore and the member is held, so nothing is lost. This
   widens R95's compatibility effect from "needs an installed host" to "needs a Level"; ADR-065 and R-035 say so.
3. **Abort is unchanged:** C keeps its original payload; the target is emptied (built-ins) and marked removed.
4. **Rerun the probe** with the attachment listener and every exit for the four payload hosts, the plain members and
   the external fixture. It passes when the listener never gets a callback while the target is unpublished, and the
   original and foreign items are each counted exactly once in all 20 conservation cases. Then continue with the
   slices above. If any case still fails, stop and report.

The other GameTest failure in your run (`EscrowGovernedWarmupRefusalGameTests`, the warm-up ended before the discard)
is outside your slice. Root tracks it. Rerun it alone once and report the result, without changing it.

## Root decision R98 (2026-10-24), on the recapture stop (`42806dec`)

Level-less preparation closed the neighbour path (all 62 original scenarios and the 20 conservation cases pass). The
remaining failure is the chest and barrel adapter's `saveWithoutMetadata()`, which runs every attached capability's
`serializeNBT` during recapture.

1. **Built-in chest and barrel capture read their owned data directly:** the item list without unpacking loot, and
   the loot table and seed. They no longer call `saveWithoutMetadata()`, so no attached capability's serialization
   runs during preparation or recapture. Access Transformer entries for the needed `RandomizableContainerBlockEntity`
   members are permitted, with a comment naming R98 as the existing entries do; no Mixin. `Container.getItem`
   unpacks loot, so do not use it here.
2. **The payload stays byte-identical** to today's for the same contents. Rocket snapshots persist it, and assembly
   capture uses the same code. Add a test that compares the new capture with `saveWithoutMetadata()`'s whitelisted
   result over the existing capture fixtures: empty, full, every slot count, loot table with and without seed, and
   the maximum payload.
3. **Trust boundary, disclosed** (Root adds it to ADR-065 and R-035). Capability serialization is read-only by contract.
   An attached capability whose attachment, serialization or invalidation callback moves resources is outside the
   protection; it already makes every chunk save inconsistent. An external provider's capture is provider code under
   the API's existing "must not mutate state" rule. If that capture runs attached-capability serialization, the same
   boundary applies.
4. **Rerun the probe** with every scenario, including the 12 serialization cases. If all pass, continue with the
   slices; otherwise stop and report. Keep the 90-second lock hand-off rule.