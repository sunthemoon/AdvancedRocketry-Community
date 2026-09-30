# Station registry capacity fix and ORBIT-04 measurement

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `82568b85f5b5f088fad6a6488bebe00043204b18`.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`.
No release candidate, tag or human Gate approval.

## Defect found by the ORBIT-04 scale test

`BoundedSavedDataIo.read` bounds a managed SavedData file two ways:

- a raw decompressed-byte quota, which is the real size bound, equal to the
  type's payload bound plus 64 KiB;
- an `NbtAccounter` heap-estimate quota with the **same number**.

Heap accounting charges per-element overhead, so a station registry at its own
4,096-station bound measures 1,592,342 raw bytes but 9,023,141 accounted bytes
(ratio 5.67), above the 4.19 MiB quota. Any file above roughly 2,900 stations was
therefore rejected as oversized:

- The **pre-start migration** (ADR-040) fails closed with `OVERSIZED_DATA`, so a
  large v1.4 station world could not be upgraded.
- **Checked writes** (expansion, gravity) fail their read-back with
  `WRITE_FAILED`, so the station can never be changed.

Both fail safely, but they block data that the payload bound accepts. The defect
dates from STATION-01/02: earlier capacity tests exercised 4,096 stations in the
model, not through the disk codec.

**Fix.** The raw-byte quota is unchanged and remains the size bound. The heap
quota is now 8× the raw bound (`HEAP_ACCOUNTING_FACTOR`, measured ratio 5.67).
`QuotaInputStream` now remembers when it was exceeded: `NbtIo` wraps its
`IOException` in a runtime exception, and without this the existing
compression-bomb case would be reported as `INVALID_SCHEMA` instead of
`OVERSIZED_DATA`. The existing test
(`WorldDataMigrationServiceTest.compressedAndExpandedSizeLimitsFailClosed`) is
unchanged and passes again; this is shown in the failed-then-passing focused runs
below. A new test proves raw bytes beyond the bound are still rejected. The bound
applies to every managed type; for the largest (rocket transactions) the heap
estimate may now reach 8× its raw bound before rejection.

## ORBIT-04 measurement (bounded, not a load campaign)

`StationCheckedUpdateScaleTest` runs the full checked gravity write (encode,
validate, stage, force, read back, atomic replace, publish) three times per size,
using the local disk and JDK 17:

| Stations | File bytes | Median | Max |
|---:|---:|---:|---:|
| 10 | 977 | 7.8 ms | 9.8 ms |
| 100 | 6,463 | 11.7 ms | 12.1 ms |
| 4,096 | 271,111 | 172.4 ms | 176.0 ms |

This is under the 500 ms single-spike budget (docs/17), but it is a real spike on
the server thread at capacity. The 5-second per-station cooldown bounds its rate.
Region lookup at 4,096 stations: 200,000 lookups in 24.7 ms (123 ns each), so it
is constant time.

## Also in this change (confirmation review of `2fb9bce`)

| Note | Change |
|---|---|
| The pre-write refusal in `checkedReplace` was untested (mutation A survived) | `checkedReplace` is package-private for tests; a disallowed transition (vacuum change) throws before any write, the file bytes and live state are unchanged, and the committer is never called |
| Failed gravity writes did not start the cooldown | Committed, failed and unknown-outcome writes all start it; unchanged, stale and unavailable do not |
| "Matching `/arce station list`" wording | Corrected (the list also shows invitees) |
| Resolver test name claimed negative cells | Renamed to negative coordinates |

The same review verified `isCheckedUpdateOf`, the cooldown bounds, eviction and
tick reset, the gravity ordering and `resolveAt`, all by mutation. Its report is
archived with the star/warp contract packet.

## Commands actually executed

| Command | Result |
|---|---|
| focused `test` (`station`, `persistence.migration`, `celestial`), attempt 1 | Test compile error (a new test method missing `throws`); retained |
| same, attempt 2 | Exit 1: scale test at 4,096 stations hit the defect above; retained |
| capacity test before the fix | Exit 1; ratio 5.67 measured; retained |
| same focused set, attempt 3 | Exit 1: the compression-bomb case reported `INVALID_SCHEMA`; retained |
| same focused set, attempt 4 | Exit 0; 249 tests / 40 suites |
| `clean build test runData runGameTestServer` | Exit 0, 172 s; `:test` executed, 975 JUnit / 177 suites, 0 failures; all 239 GameTests passed; generated files unchanged; API JAR unchanged |

Main JAR `0a41090812bee9244ae413ecefa22bd0422c90f7bfe7a65b97ca8b89eb1a174f`.

## Evidence archive

Source: `C:/Users/Administrator/AppData/Local/Temp/arce-v150-contracts-*` (named in
`evidence-archives.json`). `root-checks.zip` (232 members, SHA-256
`d0ec1ff49d4f00747124a328207616a9abefb30a306e5c400afa8a190e9704cd`) holds every
focused attempt (including the failed ones), the pre-fix capacity run, the full
run with its JUnit XML (including the timing lines) and GameTest logs, and source
and artifact hashes. Repository (45 passed), planning, provenance and
planning-regression validators and `git diff --check` exited 0 (logs in
`packaging/out/`).

## Not covered

Native pre-start migration of a real 4,096-station world, other disks and file
systems, heap use under concurrent load, and a real-client tick profile. The
timings above come from one development machine.
