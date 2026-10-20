# W112: resumable cargo preparation plan

Status: original local verification complete; independent review follow-ups are
implemented with verification in progress (see W112F in the record). Base:
`9f6668aadd64015f669810a00b75f6254b6a02a8`. Results: [W112](W112.md).
Authority: TASK-W112, the frozen [cargo design](W11CD-DESIGN.md), sections 2.3,
2.4 and 3.2, and ADR-065 section 4.1.1. This is the pre-Java persistence plan.

## Scope and implementation

- **P1 (verified): snapshot reconstruction.** Add a payload-only preparation cursor
  beside the immutable snapshot model. Reuse the existing canonical hash writers,
  streaming their header, individual block records and trailer into two SHA-256
  states. Construction accepts only the internal completed computation, never
  caller-supplied digest strings. Preserve every existing hash byte and schema.
  A codec hook verifies all decoded fields against that exact prepared snapshot,
  avoiding another synchronous hash of identical canonical input. Ordinary decode
  still computes and checks its hashes normally.
- **P2 (verified, depends on P1): bounded cargo preparation.** One server-session
  owner holds one active reconstruction, its logical rocket UUID, snapshot UUID,
  before hash, block index and charged totals. Reserve conservative step costs
  before copies/encodes/hashes, then charge actual work. Retain only bounded
  snapshot references, one replacement payload, digest states and bounded native
  envelopes; never accumulate hash input for the whole rocket or tick credit.
  An active large request keeps its turn until completion/cancellation; small
  requests cannot displace it. C-E still owns the shared fair admission queue.
- **P3 (verified, depends on P2): lock and publication.** The session-owned cursor
  locks resource mutation, launch and disassembly. Removal/unload, cancellation,
  server-capability invalidation and server stop must discard it, without changing the old
  snapshot. A privately issued, owner-thread, one-use permit binds the exact
  before snapshot, flight/resources, wrapper and frozen after digests. Recheck
  those bindings at consumption, then publish snapshot, flight, wrapper/revision
  and synchronized hash together. No public snapshot setter is introduced.
- **P4 (verified, depends on P2/P3): capture checks.** Reuse W106 eligibility before
  sequence issuance, reservation and queue entry, and repeat changed-input
  validation at final capture. Immutable prepared snapshots may supply verified
  codec witnesses, but no payload, envelope, capacity or O1 check is omitted.
  Production cargo admission remains disabled; fixtures exercise these hooks.

## Verification and boundaries

P1 pins old content/structure hashes and compares resumed/one-pass results across
split points, including both W106 fixtures. P2 tests byte/visit/reconstruction
ceilings, fixed retention bounds, fairness and every discard event. P3 tests
changed-before, stale, cross-thread and reused permits; A1 covers the real entity
locks and publication. P4 checks refusal before admission side effects.

Run every new test class alone, all unit tests, and a full GameTest with the
unchanged log checker on committed source. Check both drives and acquire the
heavy-JVM lock for detached GameTest execution. Record commands, results and
remaining work in W112.md; Root arranges independent review before merging.

Write scope is the TASK's snapshot/codec, new cargo classes, minimal runtime,
entity and escrow hooks, tests, this plan and W112.md. No device, shared fair
queue, production gate, art/data, save format/bound, proof, lease, replay,
ADR/risk/status/ledger or S2 changes. No upstream files are imported.

Original checkpoint: the committed isolated tests, full unit suite and A1 run are
recorded in W112.md. Independent review found that the unload test called `remove`
rather than vanilla `setRemoved`, the terminal-successor gate missed the cursor,
and native-factory suppliers had no direct test. W112F adds these checks; the
original runs do not establish their outcomes. The Overworld's existing `EscrowRuntime`
capability owns the single preparation slot, not the common-setup rocket manager;
this avoids retaining world state or the setup thread across server sessions.
Native-envelope publication checks run separately for BEFORE and AFTER so their
block walks cannot be combined into an over-budget tick. Hash writes are counted
exactly; native steps charge conservative pass ceilings at actual input sizes,
with 8 MiB reserved for the capture's final O1 step and a whole tick reserved for
final admission and native publication envelope steps.
