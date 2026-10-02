# C13 closure: review, native evidence and handoff

Date: 2026-10-02. Branch `codex/v1.7.0-endgame-systems`. Evidence commit:
`33cb72b` (C13 with every fix from three independent review rounds). Base of the
slice: `80fc15a` (the C12 close). No Gate, candidate or tag.

C13 covers the ADR-054 §9.1 destruction bounds, the §7 performance budgets, the
§11 and ADR-059 S2 forced-stop recovery on a packaged dedicated server, the
operator guide, three independent review rounds and the development handoff in
`docs/releases/v1.7.0/`.

## Independent review

One read-only reviewer worked from `git archive` exports in its own directory,
ran the suites on every round's export, and wrote probes that pass when a
suspected defect is present. Its reports, probe sources, logs and per-commit
diffs are in `independent-review.zip`; its repository copies and the Minecraft
bytecode listings it made are excluded.

### Round 1 (at `011e81d`): accept with required changes

High 1, Medium 4, Low 4, Info 4.

| Finding | Disposition |
|---|---|
| **C13R-F2** (High) The closure flush verdict read only the measured windows and missed a 350 ms flush (327 ms in `FileChannel.force`) | **Fixed** in `4025f47`: the timing report counts the flushes over 60 ms since start, and the harness judges the run's maximum and that count. The log is corrected |
| **C13R-F1** A sweep cut short by the reconciliation budget could starve an idle endpoint forever | **Fixed** in `ebbefc3`: a sweep tick re-evaluates every remaining endpoint's idle state. The reviewer's probe is a regression test (fails without the fix) |
| **C13R-F3** The reference load measured field lookups not at all, railguns at a tenth of their rate, 924 endpoints and drills that finished early | **Fixed** in `162a1d0` (test players ticked as connected players, creative), `e53ee32` (one owner per pair, escrow count, 1,024 endpoints, 256-layer shafts), `36b8153` (192 synthetic transfers, after `13998d6` tried 64) and `33cb72b` (Javadoc) |
| **C13R-F4** One crash cut never reached its row; four rows had no native cut; ride checks partly vacuous | **Fixed**: a release-test hook holds the coalesced flush (`2b089f8`), the railguns hook takes a distance (`6b63b65`), and the revised harness reaches twelve rows with crash cuts, a thirteenth (a record pruned while its source was unloaded) without one, an elevator cargo cut and three ride cuts. It found a stub that was never pruned (`3b88c0b`) |
| **C13R-F5** Flush time is mostly server-thread CPU, not fsync | **Corrected** in the log (`2c521d2`); recorded for the writer-thread ADR |
| **C13R-F6** The pattern-block cache was cleared from the client thread | **Fixed** in `102455d` (server tag reloads only; unit test) |
| **C13R-F7** No tests for the C13 behaviour changes | **Fixed** in `4da2860` (idle set over consecutive ticks, pattern cache, generator idle re-check and facing change; a mutation of the box cache fails the GameTest) |
| **C13R-F8** Release-test state outlived the server | **Fixed** in `cff97f6` |
| **C13R-F9** Bind and launch read the terminal's 20-tick placement cache | **Fixed** in `2c0d695` (GameTest) |
| C13R-F10 Operator guide: status fields, root writes during backups | **Fixed** in `a39c89b` |
| C13R-F11 Measurement notes (flush time outside the total, warm idle window, base load) | **Addressed** in the harness: cold idle window and the mean with flushes are reported |
| C13R-F12 Size note | **Fixed** in `097512b`, updated with the closure |
| C13R-F13 A block-entity reload changes transit state without a wake-up | **Covered** by the F1 fix (the next sweep, within 20 ticks) and the F7 test |

### Round 2 (at `13998d6`): accept with required changes

Medium 2, Low 2, Info 1.

| Finding | Disposition |
|---|---|
| **C13R2-N1** Three removal cuts expected outcomes that ADR-054 §9.1 rules out | **Fixed** in the harness: a restored removed endpoint is `ENDPOINT_RETIRED` and frozen; the record is delivered once through a redirect or as registered; `endpoint resolve` destroys or discards the frozen copy. ADR-054 §11 rows 6 and 10 read differently from §9.1; recorded for the maintainer in the handoff's known issues, the accepted ADR not edited |
| **C13R2-N2** 64 synthetic transfers left the root a third below the reference | **Fixed** in `36b8153`; ADR-054 §7's 16 launches a second is a recorded deviation (the persistence gates allow about 3) |
| C13R2-N3 A frozen incoming conflict was frozen and audited every pass | **Fixed** in `17f0204`; round 3 showed the old loop could also deliver a frozen copy a second time, and `ba47916` adds that regression test |
| C13R2-N4 The log's round-1 claims | **Corrected** with this closure |
| C13R2-N5 The reference load left two configuration values changed | **Fixed** in `c85e2f9` |

### Found by the revised native harness

| Finding | Disposition |
|---|---|
| A stub whose destination chunk was saved only within 40 ticks of the acknowledgement was never pruned | **Fixed** in `3b88c0b`: the ledger keeps such a chunk dirty until a save prunes the stub. Unit test (fails without the fix) |
| A chunk within 13 chunks of a forced chunk stays in memory below FULL and never unloads; the ledger kept reconciling its endpoints, whose changes Minecraft does not mark unsaved | **Fixed** in `1ca6fbe`: the ledger and the operator lookups leave such endpoints alone until their chunk is FULL again or unloads. Unit test (fails without the check) |

### Round 3 (at `1ca6fbe`): accept

No code finding open. The reviewer confirmed N2, N3 (by mutation, including the
duplicate-delivery case), N5 and both harness findings, and N1 by reading and by
the development shakeout. Its closure conditions are this run on the final jar,
the log corrections and two notes, all done here (`33cb72b` for the Javadoc).

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8, at `33cb72b`.
The native harnesses ran one after the other, alone on the host, on the tested
host jar `4326d3d…`.

| Command | Result |
|---|---|
| `gradlew --offline clean build test runData runGameTestServer` | Exit 0, 5 min 4 s. **All 337 required GameTests** passed on a fresh world |
| `git status --porcelain -- src/generated` after DataGen | Clean |
| `gradlew --offline test --rerun` | Exit 0, 2 min 8 s. **1,376 JUnit tests in 254 suites**, 0 failures, errors or skips |
| API jar against the first C13 closure run | Byte-identical (`824813c…`), as at the C12 close |
| `native_v170_check.py`, attempt 01 | Every one of 39 phases passed; the summary write failed (settled tombstones have no `id`). Kept in `native-attempt-01-summary-write-failed/` |
| `native_v170_check.py`, attempt 02 | Exit 0, **PASS**: every phase, on the same jar (below) |
| `native_v170_refload.py` | Exit 0, **PASS**: every ADR-054 §7 budget within in this run (see the handoff's PERFORMANCE) |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

The full build log has **18 ERROR lines and 0 FATAL**: the same intentional
failure-injection set as the C11 and C12 close runs.

One tested file changed after the run: `CHANGELOG.md`, with the release notes for
the review fixes. It is not built into any jar; `post-run-changes.json` in
`root-checks.zip` records both hashes.

## Native results

Attempt 02, on a copy of the retained v1.6 world (`native/summary.json`):

- **Upgrade.** No endgame file is written before the first endgame change. The
  satellite registry (1,011 satellites, 1,012 missions, 1,000 unfinished, 12
  finished) is unchanged at the final restart. Asteroid instances fell from 6
  to 3 as ADR-051 §7 expires them with game time.
- **Flush benchmark** (unloaded, 10 runs): the reference root 33.6 ms mean and
  41.9 ms max; the accounted maximum 84.2 ms mean and 95.6 ms max.
- **Crash cuts.** Twelve railgun cuts over three pairs, each reaching its
  ADR-054 §11 row on disk:
  - escrow unsaved and saved;
  - registered with the ledger held and flushed;
  - claimed with nothing saved, only the ledger or only D saved;
  - moved with the acknowledgement held and flushed;
  - D removed with the removal unsaved (frozen copy, redirect, resolve) and
    saved (pair C, redirect);
  - S removed after its record was durable (frozen source, resolve).

  Each verify phase checks where the payload ended, that it exists exactly
  once, that the source paid the launch energy exactly when its escrow was
  saved (25,240 FE at pairs A and C, 27,560 FE at pair B), and the row's audit
  anomaly (`CLAIM_RECOVERED`, `REMATERIALIZED`, `INCOMING_DESTROYED`,
  `OUTBOX_DISCARDED`). The destinations received 8, 2 and 0 payloads as the
  rows predict, and pair C's went back to its source once.
- **A record pruned while its source was unloaded** (pair B, sixteen to twenty
  chunks from every forced chunk, so its chunk really unloads): the entry is
  dropped as `OUTBOX_STALE_DROPPED` when the source loads again; delivered once.
- **Elevator.** The anchor-to-terminal cargo cut with the claim only in the
  flushed ledger is rematerialized once in space. The rider rides up and down
  once with saves on, then: a stop during the countdown and one right after a
  commit, with nothing saved, leave neither the ride nor its charge; a commit
  saved before the halt leaves both.
- **Final restart.** The forced chunks are exactly the world's four and the
  harness's seven; no record remains; the delivered totals are unchanged.

The reviewer's round-3 report counts fourteen ledger rows: the twelve crash
cuts, the pruned-while-unloaded source and the elevator cargo cut.

## Archives

- `root-checks.zip` holds the closure run directory: the Gradle logs, JUnit XML,
  GameTest logs, artifact and implementation-file hashes, the API-jar
  comparison, both native attempts and the reference load (inputs, summaries,
  every phase's receipt, commands and server logs), the harness scripts as run,
  and `development/`: every development attempt of C13 with its note, its JSON
  and harness logs and the scripts it used, the first closure run (round 1) and
  the second reference-load shakeout. Server worlds, frozen jars, JFR recordings
  and the development attempts' full server logs are not archived.
- `independent-review.zip` holds the reviewer's three reports, its probes, logs,
  scripts and per-commit diffs.
