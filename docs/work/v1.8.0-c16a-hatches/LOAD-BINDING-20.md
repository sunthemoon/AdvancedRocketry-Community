# C16a provisional LOAD binding (sub-scope 20)

Status: PROPOSED design amendment by a delegated Claude author. Not adopted,
not source-assignment-ready; no ADR-068, R-021, contract or Gate effect. Base
`3815db7d09014f2f5f4b8af2a34459fa3bc77113` as stated by the task (this author
had no Git access). Inputs: the private proposal (PROPOSAL-01) sections 2 and
4-8, and readiness REPORT-01 finding 2. Root owns the coupled successor.

Abbreviations (package `machine/classic/adapter/` unless noted): H =
`ClassicHatchBlockEntity`, OS = `ClassicOwnerState`, LW = `ClassicLoadedWorld`,
CO = `ClassicChunkObservation`, GT = `GuardTicket`, AC =
`ClassicAccessCoordinator`, SP = `ClassicSaveProtection`, RP =
`ClassicRawPermit`, GCS = `persistence/GuardedChunkSaves`. Line numbers refer
to the files read in this worktree.

## 1. Four separate states (proposed private CO fields)

- **Origin O**: the existing `CO.capture` (`Capture(scan)`, set once in
  `CO.capture` L67-75). Unchanged and never rewritten.
- **Current expectation E**: a new bounded position-to-type map and opaque
  `revision` object, copied from `O.scan().records()` inside `CO.capture`
  (no Level lookup). Only a terminal publication changes it. With no terminal
  change, E equals O.
- **Operation slot S**: at most one per CO, private constructor. Holds target
  position, the entry `E.revision`, the phase and, once bound, the new owner.
  Only the outer entry creates it; that entry is unresolved (U1).
- **Provisional receipt P**: at most one, inside S. A RetainedOwner-like record
  plus owner lifetime, storage epoch, checkpoint and encoding identities. Never
  in `CO.retained` or E before commit.

S and P are not persisted. A phase name or boolean confers nothing without the
identity checks below. GT identifies a provisional ticket by holding the exact
S reference, which CO compares with its own slot.

## 2. Phase bindings

**NATIVE bind** - `H.onLoad` L305-311 -> `H.prepareLoaded` L53. First
observation of an owner at `S.target` binds it into S once. Pre: exact kind
(`actualKindMatches`), `!OS.loaded()`, no pending, checkpoint, encoding or
rejected value, `!OS.available()`, `!OS.busy()`, no E or `retained` row.
Post: S.owner set; nothing written to the owner.

**FIRST_LOAD** - `H.prepareLoaded` L58 (`world().hatch`), `GT.acquire`
L88-150 through `GT.installed` L152-160 and `validateWitnesses` L201.
Change: new `AC.enterProvisionalLoad(hatch)` builds a single-owner LOAD ticket
whose installed test uses new `LW.provisionalHatch` -> `SP`/
`CO.provisionalObservationMatches`, true only for S.owner in phase FIRST_LOAD
or SECOND_LOAD with no E row and P empty. L58 takes that lookup only when S
binds this owner. Ordinary `AC.enterLoad`, `LW.hatch` and `rawMatches` keep
their predicates. Body L84-90 unchanged. Post: empty checkpoint and encoding
set, ticket closed at L96, `OS.available()` false, phase BETWEEN_LOADS.

**SECOND_LOAD** - `H.recordCompletedLoad` L104-117; `SP.recordValidatedLoad`
L60-62 -> `CO.qualifyRecord` L109-119 -> private `CO.recordValidatedLoad`
L121-137. A fresh provisional ticket (its witness value is now the new
checkpoint, GT L142/L247). For that ticket only, the `rawMatches` calls at
L115, L124 and L134 become the provisional match, and the insert writes P
instead of `retained` (pre: P empty, `retained.get(key) == null`). L112
`OS.installed()` is skipped. The L114-115 success test becomes
`loadJoinStillCurrent && hasCurrentLoadJoin && !OS.available()` plus P bound
to the current lifetime, epoch, checkpoint and encoding. Post: `joined` is
true, so L99 does not retire; phase RETAINED_PROVISIONAL; provisional lookup
closed.

**RETAINED_PROVISIONAL** - no new gate needed: `emptyRemovalReadyLocal`
L152-160 and `GT.acquire` L101-102 require availability; `retainedMatches`
L149-165 reads `retained` only; `world().hatch` fails (no E row), so a
reentrant `prepareLoaded` returns at L58 before `stageRevalidation`
L222-237. Any `retireAccess` (L51, L302-315) changes the lifetime and
invalidates P. Never refresh it.

**COMMIT** - new terminal method (proposed `CO.commitProvisional`) run from
the outer operation's finally. Callback-bearing qualification first: fresh
`LW.fullChunk`, `CO.find`, `containsOwner`, `actualKindMatches`,
`preflightHatch(encoded)` (outside the monitor, as at H L190-194). Then one
callback-free tail under `synchronized (CO)`: S identity and phase, E.revision
equal to the entry revision, P equal to owner, lifetime, epoch, checkpoint and
encoding, `hasCurrentLoadJoin` (new package-private H accessor), E and
`retained` below capacity. Then add the E row
`advancedrocketrycommunity:classic_hatch`, move P into `retained`, replace
E.revision, clear P, and call `OS.installed()` last. The CO -> OS lock order
already occurs in `EmptyRemovalSelection.locallyCurrent` L225-233. Post: one
new E row and one `retained` row, available, S released, every other row
identical.

**UNCHANGED** - only if the owner is absent from the chunk BE map, the target
pre-state is restored (witness API: U3), and E, `retained` and O equal their
entry values. Then clear P and S.

**UNCERTAIN** - every other exit, including a throwable with any changed
witness. Needs an O1 disposition before release; none is selected. P stays out
of E and `retained`.

Throwable: classification runs in the outer finally. The native result or
primary throwable returns unchanged; cleanup failures are added as suppressed.

## 3. Comparison and both save consumers

- `rawMatches` L92-97 and `matchesOutgoing` L311-312 compare with E instead
  of `O.scan().records()`. All ordinary callers (`ownerObservationMatches`,
  `qualifyRecord`, `retainedMatches`, `selectEmptyHatchRemoval`,
  `EmptyRemovalSelection.locallyCurrent`) follow. While E equals O the
  behavior is identical.
- Early: in `H.saveAdditional` L264-288, after the thread check and before
  `RP.emission` L269, if S is held and bound to this owner in phases NATIVE to
  RETAINED_PROVISIONAL and the owner is new-empty (`!OS.loaded()`, no pending
  or rejected value), return with no roots and no `recordObservedDenial`.
  Looking up S is a capability call inside serialization (U6). Busy retained
  EMIT (RP L34-45) is unchanged for every other owner.
- Late: in `CO.inspectOutgoing` L293-304, after the identity check and before
  the `try` at L297, if S is held for this chunk and Level, call `GCS.defer`
  L69-75. It applies sticky precedence through `beforeSave` first. Inside the
  try, the deferral would become a sticky refusal through L302-303.
- Without a held S, a provisional entry in an outgoing tag fails the L311-312
  cardinality test and is refused, as today.

## 4. Lifecycle boundaries

- `SP.chunkUnload` L80-86 -> `CO.retireChunk` L338-354 iterates `retained`
  only. With S held it must classify UNCERTAIN.
- `Provider.invalidate` L368 -> `CO.close` L358 clears `retained`. With S held
  it must not silently drop S or P.
- `beginQuiesce` (SP L88-94) ends RUNNING; later tickets fail and the terminal
  sees UNCERTAIN.
- Removal (delete one E and one `retained` row) uses the same qualification
  and tail split but is not bound here beyond PROPOSAL-01 section 6.

## 5. Unresolved or missing authority

- U1: authentic pre-mutation target and invocation provenance (REPORT-01
  finding 1). Creating S is unbound; no outer flag substitutes.
- U2: whether Forge 1.20.1 calls `onLoad` for a placed BE synchronously inside
  the native invocation. If not, both LOADs fall outside S and this binding
  does not apply.
- U3: native outcome witnesses (source, tool, drop, restored cell).
- U4: generated and disk-Proto origin. `CO.capture` accepts only LEVELCHUNK
  (L69-72) and SP L35-40 has no observation, so placement there stays
  unadmitted.
- U5: O1 uncertainty, O2 coverage and O3 source eligibility are outside this
  task.
- U6: other `saveAdditional` consumers, and whether a throwing Save handler
  prevents the write, are not verified here.
- U7: final save versus `Provider.invalidate` order, and stop/restart.
