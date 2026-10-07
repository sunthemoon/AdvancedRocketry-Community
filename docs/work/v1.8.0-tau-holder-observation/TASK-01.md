# Failure-only Tau holder observation implementation

Status: READY_FOR_REVIEW; source authoring only, not independently reviewed or executed.
Date: 2026-10-07. Owner: delegated implementation worker. Root is the sole integrator.
Base: `709845551df7b7ffd87e89b6f3159da79598b3e0`; branch
`codex/v1.8.0-tau-holder-observation-20261007`; isolated worktree
`D:/GitHub/arce-v180-tau-holder-observation-20261007`.

## Authority and exact write scope

Root's external assignment is `tau-holder-observation-assignment-20261007/TASK-01.md`.
Root clarified in the 2026-10-07 collaboration channel that the new native query must
be strictly failure-only: one honestly labelled POST_LOOKUP capture, not a synthetic
pre-priming sample. This clarification changes neither production nor the old observations.

Only these four paths are owned:

- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/TauCetiPathGameTests.java`
- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/TauHolderObservationText.java`
- `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/TauHolderObservationTextTest.java`
- `docs/work/v1.8.0-tau-holder-observation/TASK-01.md`

All production, shared fixtures, registration, build, central status, accepted policy,
AGENTS and other worktrees remain readonly. New code is original/MIT; no upstream
code, resource or native method body is copied.

## Implemented boundary

Only the missing-rocket branch allocates a one-element local context slot. The original
POST capture reuses its already selected journal record, destination Level and origin
after its unchanged old world queries. No second SavedData acquisition or world lookup
is made for the new observation. Context remains local to this assertion helper.
The new query occurs once after the original priming lookup, both old observation
emissions and the existing manager diagnostic. It requires the selected destination's
server thread, then calls the real `ServerChunkCache.getChunkDebugData(ChunkPos)`.
Missing context, off-thread context and native query failure are diagnostic statuses;
they are not readiness success, retry or loading permission. Logging is attempted once.

The stdlib-only formatter emits fixed `ARCE_TAU_HOLDER sample=POST_LOOKUP` text, target
UUIDs, bounded Level identity, signed chunk coordinates, diagnostic status and holder.
Payload is at most 512 printable ASCII bytes; holder at most 160 characters. It visits
only each field's bounded prefix, replaces whitespace/non-ASCII with `_`, and marks
truncation with `~`. Null and empty holder values are explicit `NA` / `EMPTY` text.
No getter, callback, world reference or mutable state is present in the formatter.

Original 2048-character text construction, the 512-byte manager output, all three
GameTest bodies, assertions, setup, cleanup, original lookup priming, 270/1400 clocks,
readiness predicates and tickets are preserved. Removing the marked additions and the
single POST context argument restores the exact original fixture; two new Java files
and this task record are then removed by the integrator if reverting the slice.

## Verification and limits

Nine focused JUnit methods are authored for exact output, target/coordinate identity,
whitespace/control/surrogate sanitation, 160-character boundary, oversized fields,
512-byte ASCII ceiling and null/empty distinction. They are not executed by this author.
Fresh Python/Git controls completed with exit 0: 30 static checks and nine finite
text-model inputs, exact raw inverse to the baseline, maximum-field arithmetic 504
ASCII bytes, empty index and exactly the four owned paths. These are not Java tests.
Static controls and inverse proof are retained under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/tau-holder-observation-implementation-20261007`.

No Java, Gradle, bootstrap, native/server/client, network, install, cleanup or Git
mutation is authorized here. Root must obtain different-agent actual-source review,
run a separately granted serial stdlib/JUnit cohort and replay exact committed hosted
source. Native getter/linkage, actual emitted holder contents, restoration on native
failure and runtime overhead remain unverified. The holder text does not prove pending
I/O health or a unique Tau cause. No loader fix, source delivery, risk/ADR acceptance
or Required Gate closure is claimed.
