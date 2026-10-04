# C17 revision-3 M6 disposition

Date: 2026-10-03. Delegated author, Temp-only proposal update. This is not an
independent closure verdict, acceptance or runtime/Gate evidence.

Revision-2 proposal SHA-256:
`24158c9bb75fe7455a3ddf2bf9781b1a4a33a99b313f8d830a381825e584f0e6`.
Independent REVIEW-02 original path:
`C:/Users/Administrator/AppData/Local/Temp/arce-v180-c17-review-r2-d2f35effb88246f084b6ce11e76650af/REVIEW-02.md`.
Review SHA-256:
`709b32387b9ec031830eafde7dde1a10c1ae24f035935ee028ff2744b7aba61f`.
Counts: 0 Critical / 0 High / 1 Medium (M6) / 0 Low. The original eight
revision-1 findings were independently addressed as conditional specifications;
their owner/implementation limitations remain unchanged in revision 3.

## M6 - missing restart-safe orbital clock

**Author change:** [ADR-065](ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md)
section 5.2.1 adds a private logical-server clock with its sole persisted time
inside the same station root 5. No getTickCount, separately saved world time,
wall time, satellite time or client time sets/recovers its epoch.

- Strict schema-1 root clock, one nonnegative long, <=128 bytes, lifecycle bound.
  Runtime starts exactly at saved logical_tick, one advance/server END tick,
  no offline catch-up. Root-4 migration uses epoch/clock zero and preserves every
  original station field; valid root-5 no-op, future/missing/malformed blocks intact.
- Checked candidates co-commit one sampled clock and orientation epoch/phase;
  every station's epoch must be <= serialized clock. Existing transition,
  geometry/balance, write spacing and outcome-unknown/reassertion behavior remain.
  Unknown outcome disables context and clock until coherent root reload, not a
  guessed successful write. World-time rollback cannot invalidate its time basis.
- Ordinary healthy saves sample current counter with current accepted records;
  active rotation marks dirty at most once/20 ticks without forced per-tick disk
  writes. Abrupt restart may rewind uncheckpointed *visual* rotation to the last
  coherent surviving root; this is disclosed, not per-tick durability. Clean
  saved restart resumes its exact sample and adds no offline time.
- Epoch-ahead is blocked data, not clamped/repaired. Long overflow saturates with
  CLOCK_EXHAUSTED, no wrap; phase reduces elapsed modulo 72,000 before the signed
  rate product. Authorized STOP remains possible and preserves exact phase.
- Wire uses the same validated clock sample and bounded 40-tick client-only
  extrapolation with active-rate heartbeats. NONE/session/context change clears
  estimates. Protocol numbers/discriminators/byte ceilings do not change again.
- Required pure/native fixtures now cover initial migration, positive/negative/
  zero rate, long elapsed, clean/offline restart, world save behind checked clock,
  refused/unknown candidates, missing/future/mismatched clock, saturation/STOP,
  autosave rollback, heartbeat horizon/cache reset and save ordering.

**Disposition:** specified in proposal; awaiting independent revision-3 review.
No new runtime clock exists in the repository and no native fixture was run.
[probe-03.py](probe-03.py) exercises a small arithmetic/codec model of the proposed
clock and rechecks coverage/source/wire baselines. It is not a test of a shipped
Java service or native disk writer. Results are [static-checks-03.json](static-checks-03.json).

## Decisions and integration limitations retained

D2-A's continuous analytic rotation/logical altitude remains owner-confirmed.
The technical clock is not implicitly accepted by that product choice. D1,
D1-disassembly and D3 remain owner-pending. No typed resource/disposal/durability
choice is inferred. The related C18 D2/D3 and D4 feasibility limits also remain.
Conditional C18 first-event source outboxes must share the station root-5 /
journal-3 integrated migration if selected; they are not independently bumped or
approved here. Matching C18 finder/sky interfaces and per-leaf evidence remain.

No ledger disposition changed: exact 41 C17 PLANNED rows, 24/9/8. All 26 inherited
approved AR source identities remain exact; no assets copied. Repository and
earlier Temp revisions/reviews are untouched. Required Gates are not met.
