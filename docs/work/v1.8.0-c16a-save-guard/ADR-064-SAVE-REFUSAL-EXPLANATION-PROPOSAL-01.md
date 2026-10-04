# ADR-064 proposed save-refusal explanation

Date: 2026-10-04. Status: PROPOSED; not ADR acceptance or risk approval.
Task: C16a-R021-DOC01. Target: explanatory addition to ADR-064 section 11.

## Authority and boundary

The owner answers the scoped allocation question in this conversation on
2026-10-04: "授权仅同步 R-021 和 ADR 说明（推荐）". The question permits only
correction of R-021 and a proposed ADR explanation, without changing save
behavior, accepting risk or editing other risk-register rows. This proposal
does not alter the accepted ADR-064 revision 5, any source, root limit, schema,
resource ownership, runtime availability or Required Gate.

The code reference is fixed commit
`1ece7e9d2515003ca705b5634ef654a6bcae8f89`. Its common guard code is unchanged
from the [independent impact audit](reviews/save-refusal-impact-01/REVIEW-01.md)
at `3f3d62aed3980186fe0acc9592cf93ca436405fb`. That audit's report SHA-256 is
`3d04a317ce519df1314fe642681af0fa50127c081e9ecddfcbfd053afb60bb87`;
manifest SHA-256 is
`373dc11496fb4e326f8eef66548ef5bafcf2eebce34356888d76ce696ae809a9`.
Its two Medium disclosure findings are not proof that the underlying serious
rollback risk is only Medium. R-021 remains open and its probability is not
measured by a finite fixture or source inspection.

## Proposed explanatory addition to section 11

### Current consumers and triggers

The existing common guard protects six BlockEntity types through four modules:
TankProtection, PumpProtection, CombustionProtection and
RecipeSignatureProtection. This is not an implemented physical shared-hatch
writer or admission of future Classic codec consumers.

| Existing BE group | Native roots checked | Bytes / depth / nodes |
|---|---|---|
| Pressurized tank | `arce_pressurized_tank` | 8,192 / 16 / 1,024 |
| Pump | `arce_pump` | 8,192 / 16 / 1,024 |
| Combustion generator | `arce_combustion_generator` | 8,192 / 16 / 1,024 |
| Rolling machine, precision assembler, electrolyzer | `arce_recipe_signature` | 1,024 / 16 / 256 |
| Same three controllers | `arce_process`, `arce_process_journal`, `arce_machine`, each separately | 65,536 / 20 / 4,096 |

All four modules also refuse an outgoing BlockEntity list larger than
`256 * chunkHeight`. Native preflight checks depth, node count and lossless
native representation as well as byte size. A refusal may therefore occur
below the byte ceiling. Crafted typed-empty lists, foreign tag implementations
and noncanonical NaN payloads are source-preflight cases, not a claim that every
such representation is reachable unchanged from a naturally loaded world.
Private canonical-byte/digest limits are separate and do not replace this table.

### Save unit and exception mechanism

The Forge save event runs after outgoing terrain-chunk serialization and before
the terrain region writer. The existing guard marks the ChunkAccess unsaved and
throws IllegalStateException; ChunkMap catches Exception, logs and returns false.
This is an explicit exception-based refusal mechanism, not a cancelable event
or an automatic server shutdown.

An ordinary remembered denial applies to the complete outgoing terrain chunk
at one coordinate, including unrelated blocks, biomes, lighting, scheduled ticks,
structures and other BEs. It is not selective BE quarantine, nor atomic rollback
of a region file or whole world. That denied attempt does not replace the old
terrain record; a new coordinate with no previous record has no newly persisted
terrain snapshot from that attempt. Unrelated changes made after the last
successful terrain snapshot may disappear after unload/reload or restart.

### Capacity, affected Level and lifetime

State belongs to each ServerLevel capability, not a global resource collection.
It retains at most 256 distinct chunk identities, with a first-wins reason of
at most 256 characters per coordinate. Repeating a known coordinate does not
grow the map. After 256 entries, a 257th distinct denial sets saturation: every
subsequent terrain chunk coordinate in that ServerLevel is denied, including
healthy or unrecorded coordinates. Other ServerLevel instances are independent.
The memory bound is not a work, retry or logging bound.

The HIGHEST common listener checks existing denial state before module admission.
BE removal/replacement, an empty outgoing BE list, a replacement ChunkAccess,
ordinary unload or live root correction does not clear it. Provider capability
invalidation closes the state; closed state refuses all coordinates. This
description does not assert that LevelEvent.Unload directly calls that helper.

There is no production online reset. The fixture reset requires the actual
GameTestServer and cannot reset closed or saturated state. The provider does
not serialize this transient state into SavedData. A newly created ServerLevel
starts fresh, but unchanged bad on-disk data can refuse again; restart alone is
not a repair or a resource-conservation proof.

### Logging, retries and durability limitations

The pinned historical Tank03 run contains six actual refusal attempts, each
with an EventBus ERROR and a ChunkMap ERROR plus exception detail/stack trace:
12 ERROR headers in that finite observation. It is not one ERROR per save,
nor a promise that every environment can produce only those two headers.
There is no guard-level total logging/retry quota or measured rate ceiling.
The successful-save cooldown is not recorded for a failed save return;
accessibility and unload scheduling still govern attempts. This is not a
one-attempt-per-tick claim. Repeated explicit saves, unload or stop passes can
produce further errors for the Level lifetime. A common-logger throttle alone
would not suppress exception logging in Forge EventBus and ChunkMap.

The unload continuation ignores the save boolean and can remove live BEs.
Keeping dirty eligibility does not veto unload, retain all unrelated changes
durably or acknowledge the refused coordinate. Exit 0, a normally returning
save-all barrier and an "All chunks are saved" message are not per-chunk
durability acknowledgements.

POI flush occurs before guard admission; players, entities, Level SavedData and
neighbor terrain coordinates use separate persistence. Guard refusal is not a
cross-store transaction. Resource transfers across these stores could leave
divergent durable generations unless independently coordinated. This is a
source-derived risk scenario, not a newly reproduced duplication/loss finding
at the current artifact.

### Recovery authority and unproved work

The accepted requirement remains explicit administrator repair against a backup.
No working general diagnostic/repair tool, verified repair procedure or owner
risk acceptance is supplied by this proposal. Proposed operational precautions
are to limit further interactions, preserve complete affected stores and their
identities, and perform any selected repair only on an offline backup copy.
Those precautions are not implemented enforcement or a selected automatic
server-stop/read-only policy.

Before choosing a conversion, removal or quarantine policy, retain the exact
compressed terrain/root input as escrow and obtain separate authority. A repair
implementation needs bounded supported-format scans, selected coordinate/root
identities, outside-window byte comparisons, resource accounting and repeated
restart validation. No truncation, automatic discard, online reset or narrowed
BE quarantine is authorized here. Reopening a fresh Level cannot by itself
prove that all refused stores were repaired or that resources were conserved.

### Evidence and release boundary

The existing audit measures source, pinned primary save/unload flow and one
historical server cohort. Its common guard bytes match the current code; its
historical JAR is not relabelled as a current packaged-server execution. K3's
passing automatic regression is not current saturation, logging, cross-store
or repair evidence.

Required unrun current-artifact controls remain separate:

1. Mixed and new terrain coordinates, unrelated-state rollback and exact
   compressed-record preservation.
2. Removal/replacement, unload/reload, live correction and Level recreation.
3. 256/257 distinct denials, a healthy coordinate and an unaffected second Level.
4. Player/entity/POI/SavedData/neighbor resource accounting, including unload;
   forced-stop recovery is its own claim.
5. Fixed-workload attempt counts, both logger contexts and total log bytes.
6. Backup-copy offline repair, outside-window bytes and repeated clean restarts.

Native execution, repair tooling, changed saturation behavior, automatic
quarantine/reset/discard/stop policy and any risk acceptance require separately
scoped review and authority. G0-G9 remain open. This explanatory proposal does
not accept R-021, admit GuardTicket/shared writers or close persistence Gates.

## Candidate disposition

Only the R-021 row is corrected in the risk register; all other bytes must match
the owner-edited baseline. The accepted ADR remains byte-identical. A separate
read-only reviewer checks actual candidates and source facts before publication.
This proposal remains PROPOSED even if its factual disclosure review passes.
