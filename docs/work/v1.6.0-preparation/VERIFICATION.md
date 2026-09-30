# V160-GOV-01 / V160-CON-01 preparation verification

Date: 2026-09-30. Scope: the v1.6 development baseline, the satellite, scheduler,
resource-mission and reward contracts, the legacy audit and the reference vectors.
**No v1.6 production feature, world migration, candidate or Required Gate is
delivered here.**

Baseline: `940a5ed3b90a4da0ca4e43417b2bddf41ebb7307` (v1.5 closure).
Branch: `codex/v1.6.0-satellite-resource-missions`. The canonical task tree is the
[implementation log](../v1.6.0-implementation-log.md). Inherited G0–G9 stay open.
No upstream code or asset is imported, and the public API does not change.

## Decisions

| ADR | Content | Result |
|---|---|---|
| [ADR-048](../../decisions/ADR-048-V160-DEVELOPMENT-BASELINE-EXCEPTION.md) | v1.6 development from `940a5ed`, separate from inherited acceptance | ACCEPTED |
| [ADR-049](../../decisions/ADR-049-SATELLITE-BLUEPRINTS-AND-ASSEMBLY.md) | Five kinds, component catalog, blueprints, Satellite Builder, launch order, survey scan, solar receiver, menus and channel | ACCEPTED, revision 3 |
| [ADR-050](../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md) | One registry and clock, write policy and save epoch, status machine, queues, limits, byte budgets, retention, cancellation, invariants, root-3 migration | ACCEPTED, revision 3 |
| [ADR-051](../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md) | Logical asteroid instances, survey/asteroid/gas missions, bound-terminal delivery, total reconciliation, rebind, crash cuts | ACCEPTED, revision 3 (revision 1 rejected) |
| [ADR-052](../../decisions/ADR-052-RESOURCE-TABLES-AND-SEEDS.md) | Asteroid and gas tables, seeds, SplitMix64, `survey-v1`/`asteroid-v1`/`gas-v1`, versioned audit | ACCEPTED, revision 2 |

Root recorded acceptance under the maintainer's standing direction to follow
recommended solutions after the independent reviews. This is not a new numbered
approval message. The reviews supply findings and scoped verification, not
maintainer authority or Gate approval. The
[coverage table](../v1.6.0-contract-coverage.md) maps every item in sections
8–14 of the version document to a contract section, test level and chunk, or to
a recorded disposition. The deferred and rejected items are rows in
`docs/PORTING_MATRIX.md`.

## Legacy audit

The [audit](../v1.6.0-legacy-audit.md) fetched 43 upstream files from the pinned
MIT commit `c5cd5af` into a Temp directory **outside** the repository. All 43
SHA-256 values equal `legacy-manifest/java-files.csv`. Two guessed paths did not
exist and are recorded as such. Per-file hashes are in
`upstream-audit-fetch.json` inside `root-checks.zip`; the upstream bytes are not
archived. The reviewer independently re-hashed 16 of the files, all matching,
and confirmed the gas-duration source line.

## Independent reviews

One general-purpose reviewer did three rounds. It had read-only repository
access and wrote only to its own Temp directory. Each round reviewed a
`git archive` of a committed tree. It ran the checker plus its own Python and
Java re-derivations, and checked the Minecraft/Forge save-ordering claims
against the Forge 47.4.10 bytecode.

| Round | Commit | Critical / High / Medium / Low | Verdicts | Dispositions |
|---|---|---|---|---|
| 1 | `ab207f0` | 0 / 7 / 16 / 16 | 048, 049, 050, 052 accept with changes; **051 rejected** | [review-01](review-01-dispositions.md) |
| 2 | `ddc71c3` | 0 / 2 / 3 / 13 (36 of 39 resolved) | 048, 052 accept; 049, 050, 051 accept with changes | [review-02](review-02-dispositions.md) |
| 3 | `ba275f0` | 0 / 0 / 0 / 6 (16 of 18 resolved, 2 partial) | 049, 050, 051 accept | [review-03](review-03-dispositions.md) |

The six round-3 Lows and the two partial items are applied in the accepted text.

Round 1's main findings:
- the registry is written by a barrier flush after every operation, so the write
  policy had to be specified;
- stale scheduler entries;
- record bounds smaller than valid data;
- over-cap legacy registries that would block startup;
- receipts that were never freed;
- a reconciliation table that was not total;
- cancels that bypassed reconciliation.

Round 2's main findings: component loss on non-`data` launches, and a double
payment after an operator rebind.

In round 3 the reviewer searched 1,111,110 event sequences with flushes, chunk
saves, crashes and cancels. It found no double payment outside the stated
operator residual, no freed instance and no lost reward.

[independent-review.zip](independent-review.zip) holds the three unmodified
reports, logs, scripts and hash lists, indexed in
[independent-review-files.json](independent-review-files.json). It excludes the
reviewer's repository copies (`tree/`), its Minecraft/Forge bytecode
disassembly (`mc/`) and fetched upstream sources, because those are not ours to
redistribute. Only `upstream/hashes.txt` is kept from them.

## Reference vectors

[examples.json](examples.json) and [check_examples.py](check_examples.py)
contain 13 standard-library checks:

- SplitMix64 against its published seed-0 outputs (independent of this project);
- bounded draws, survey and yield vectors, candidate fingerprints, and a
  mixed-namespace ordering case with a negative test for path-first ordering;
- truncation, asteroid and gas durations, and gas amounts (hand-computed);
- 17 slot-based blueprints with the refusal order;
- scheduler backlog passes;
- the 36-row reconciliation table, hand-written from ADR-051;
- all 64 rebind orderings. They are checked against a declarative rule written
  separately from the simulator, and no ordering frees the instance.

Generated vectors are pinned for the Java slices; they are not independent
evidence of correctness. A mutation that restores the revision-2 rebind
behaviour fails the rebind vectors (`mutation-rebind.log`).

These are projections of integer rules, not NBT, runtime, migration,
permission or performance evidence.

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, `PYTHONUTF8=1`, offline Gradle.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --offline --no-daemon --console=plain` | Exit 0, 2m35s. **260 required GameTests** passed. `:test` was **FROM-CACHE**. DataGen wrote 0 files; generated-resource diff empty |
| `gradlew test --rerun --offline --no-daemon --console=plain` | Exit 0, 1m51s. **1,058 JUnit tests / 195 suites executed**, 0 failures/errors/skips |
| `python -B -m unittest tests.test_v1plus_planning -v` | Exit 0, 15 tests, at the initial state and again after packaging |
| `python -B docs/work/v1.6.0-preparation/check_examples.py` | Revision 1: exit 0, 11 tests. Final: exit 0, 13 tests |
| `python -B scripts/mutation_rebind.py` (Temp copy) | The mutated checker fails as intended (exit 1) |
| `python -B scripts/validate_repository.py --require-approved-identity` | Before this file existed: exit 1, broken links to it (retained as `validation-02.log`). Final, after packaging: exit 0, 45 passed |
| `python -B scripts/validate_v1plus_planning.py` | Before this file existed: exit 1, same link (retained). Final, after packaging: exit 0, 11 plans / 33-input inventory |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| Temp `fetch_upstream.py`, `record_extra.py` | 43 of 43 manifest hashes match; 2 guessed paths absent |

The final validator and planning runs need the finished packet, so they ran after
packaging. Their log is `packaging/out/validation-03.log` in the Temp evidence
directory, which is deliberately outside `root-checks.zip`.

The three rebuilt JARs are **byte-identical** to the v1.5 closure build
(`artifact-comparison.json`), and no implementation file changed
(`implementation-files.json` is empty). The runtime stays `1.20.1-1.5.0-dev`.

The full build log has **15 ERROR lines and 0 FATAL**. They come from
intentional failure-injection GameTests (migration save failures, test-region
chunk saves) and the missing `server.properties`. This is not a clean-log claim.
Startup also reports running 2,300 ms (46 ticks) behind. Passing functional
checks do not turn that into performance acceptance.

## Retained defects in the evidence trail

- The proposal commits `ab207f0`, `ddc71c3` and `ba275f0` already link to this
  file, which exists only from the acceptance commit. `validate_repository.py`
  was not run on those trees and would fail on those links there. The first
  failing run is retained as `validation-02.log`.
- A shell heredoc quoting failure and a Python one-liner failure during
  preparation were retried as script files. Two guessed upstream paths were
  absent. No missing source was treated as audited behaviour.

## Evidence and boundaries

[root-checks.zip](root-checks.zip) retains:

- command logs and both JUnit result sets;
- artifact identities and the comparison with v1.5;
- the upstream hash record;
- the helper, generator and mutation scripts.

[evidence-archives.json](evidence-archives.json) and
[root-checks-files.json](root-checks-files.json) index it.
[source-identity.json](source-identity.json), [links.json](links.json) and
[SHA256SUMS.txt](SHA256SUMS.txt) bind this packet to the staged tree.

Not done in C6: any production change, native or dedicated-server run, S1/S2,
V0/V1/V2 (no Xvfb/LLVMpipe on this host), performance measurement, candidate,
tag or Gate change. All G0–G9 remain unapproved. The next implementation chunk
is **C7** (ADR-049 and the complete root-3 codec), not a release step.
