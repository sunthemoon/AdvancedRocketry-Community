# W110 plan: item cargo kinds and bounded native stacks

Assigned base: `20a82b88`, branch `codex/v1.8.0-w110`. This persistence leaf
implements D-K only. Root owns independent read-only review and integration.

## Base recheck and write scope

W11CD sections 2.2, 3.2 and 3.3 remain applicable. C-K already supplies
`EscrowOperation.rocket()`, composite native bindings/floors, transfer guards,
five-marker capacity preview and fuel-vector settlement observations. W100F's
audit/pruning accessors and C-I's identity rule are present. C-P part 1 adds
fluid-only `EscrowCargoEligibility`; W112 is absent. No design change is needed.

Change `EscrowOperation`, `EscrowEndpoint`, `EscrowSignatureKind`,
`EscrowAdapterIds`, `EscrowCargoCapture`, `EscrowSettlementCapacity` and, where
needed, `EscrowSettlementIntent`. Add item stack/payload/buffer codecs in
`resourceescrow` and new tests there. The plan and `W110.md` are the only repository
documentation writes. Existing floors, bindings and guards reuse the rocket-side
accessor. Item endpoints use `CONTAINER`; their rocket projection is composite,
their device projection is `ITEM_BUFFER_V1`, and O1's rocket projection stays fuel.
The unpublished escrow root has production admission disabled (R-035); these
reserved operation kinds need no released-world migration.

## Codec and signatures

Preflight before recursive copy/decode: registered canonical non-air item ID,
at most 128 ASCII characters, exact byte Count in 1..min(64, native max), optional
compound tag, no ForgeCaps key, at most 4,096 native bytes, depth 16 and 512 nodes.
Require exact `ItemStack.of(saved).save` equality; never repair or normalize input.
Return `CARGO_TOO_LARGE` for bounds and `INVALID_DATA` for malformed/noncanonical
data. The schema-1 item payload contains cursor, stack and byte from/to slots;
validate directional 4-slot/27-slot ranges and the existing 8,192/12/384 frame.

Merge comparison uses `ItemStack.isSameItemSameTags` plus canonical identity NBT
digests (id and tag, excluding Count so partial stacks can merge). Full-stack and
payload signatures retain Count. Buffer canonical form is a compound containing
an ordered four-element list: each element is the exact saved stack, or an empty
compound for an empty slot. Hash `arce-escrow-item-buffer-1:` plus device UUID,
colon and the list-wrapper's `RocketNativeResources.signature`. Slot position,
count and typed NBT are signed. Item O1 quantity is always diagnostic zero;
presence is determined by the observation state, never by quantity.

## Verification and limits

New A0 classes run alone, then the whole unit suite. Measure with real codecs
both directions, maximal local item bytes/depth/nodes, framing/O1 intersections,
0..4 retained markers, longest endpoint names, worst actor/sticky reason,
neutral normalization, ACCEPT and both ABANDON survivors, O3/O4, pruning/arming,
full flight tables and R6/R8/R9 reservations. Pin existing fuel/floor outputs and
obtain fluid receipt/inspector pins before production edits. Refusal must precede
sequence allocation, reservation, writer submission and endpoint mutation.
No limit, receipt dictionary, proof, lease or replay-exclusion change is allowed.

Run serial offline Gradle commands. Shared core code is reached by existing
GameTests, so run one full regression on committed source with the unchanged log
checker, under the heavy-JVM lock and after C:/D: free-space checks. Preserve logs
in `D:/GitHub/ARCE-Task-Evidence/root-queue/W110/`; remove only this worker's world
and lock in the acquiring script's finally block. Run strict repository validation
before the final record commit. No native/restart, device/mutation/queue work,
data/art, S2/V/B evidence, status/ledger updates, push or merge belongs here.
