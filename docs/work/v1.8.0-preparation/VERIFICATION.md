# V180-GOV-01 / C14 preparation verification

Date: 2026-10-03. Scope: the v1.8 development baseline, the item-by-item audit
of the pinned upstream commit and the classic content contracts: identity,
import and validation, the disposition of every legacy content unit, the batch
plan, and the first batch (materials, ores and planetary surfaces). **No v1.8
production feature, world migration, asset import, candidate or Required Gate
is delivered here.**

Baseline: `55da6a58842762c382edce0a5a842d06bb76ff6e` (v1.7 development
handoff). Branch: `codex/v1.8.0-classic-content`. The canonical task tree is
the [implementation log](../v1.8.0-implementation-log.md). Inherited G0–G9
stay open. No upstream code or asset is imported, and the public API does not
change.

## Decisions

| ADR | Content | Result |
|---|---|---|
| [ADR-060](../../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md) | v1.8 development from `55da6a5`, separate from inherited acceptance | ACCEPTED, revision 2 |
| [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md) | IDs and tags, machines on the v1.2 kernel, sources and the import pipeline, the vanilla derivation check and inheritance, the recipe graph, budgets | ACCEPTED, revision 6 |
| [ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) | Dispositions of 653 legacy units with player impact, the C15a–C18d batches, change control, the ADR-018 trigger | ACCEPTED, revision 5 |
| [ADR-063](../../decisions/ADR-063-MATERIALS-ORES-AND-PLANETARY-SURFACES.md) | C15: materials, ores and the small plate press; the Moon, Mars and Venus surfaces; the Tau Ceti f and g worlds | ACCEPTED, revision 3 |
| [ADR-018](../../decisions/ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md) | Campaign trigger amended (owner decision in ADR-062 §8, option 1) | ACCEPTED, revision 2 |

Root recorded the acceptance on 2026-10-03 after the maintainer confirmed it in
the development session, following the reviews below. The maintainer also
chose to amend ADR-018's campaign trigger and accepted the deferral of
terraforming and the hovercraft past v2.0. The reviews supply findings and
scoped verification, not maintainer authority or Gate approval. The
[coverage table](../v1.8.0-contract-coverage.md) maps every item of the version
document to a contract section, test level and batch, or to a recorded
disposition. Deferred and rejected items are rows in `docs/PORTING_MATRIX.md`.

## Audit and inventory

The [content audit](../v1.8.0-content-audit.md) read the pinned MIT commit
`c5cd5af` from an archive fetched into a Temp directory **outside** the
repository; all 510 Java files and 898 assets equal `legacy-manifest`.
`tools/audit/inventory_v180_content.py` generates the
[inventory](../v1.8.0-legacy-inventory.json) of 653 units, and
`scripts/validate_v180_content_ledger.py` checks the
[ledger](../v1.8.0-content-ledger.csv) (52 IMPLEMENTED, 133 REDESIGNED, 292
PLANNED, 93 MERGED, 25 DEFERRED, 58 REJECTED), the
[asset plan](../v1.8.0-asset-plan.csv) (188 IMPORT, 157 REVIEW, 430
REGENERATE, 113 EXCLUDE, 10 IMPORTED), the pinned
[import allowlist](../v1.8.0-asset-import-allowlist.txt), the
[origin findings](../../provenance/v1.8.0-origin-findings.json) and the bound
[vanilla derivation results](../v1.8.0-vanilla-derivation.json) (809 CLEAR,
42 HIT, 35 SUSPECT, 12 UNSUPPORTED, 1,816 legacy-to-legacy relations).

## Independent reviews

One general-purpose reviewer did five rounds. It had read-only repository
access and wrote only to its own Temp directory. Each round reviewed a
`git archive` of a committed tree, ran the inventory, derivation and validator
checks and a mutation harness, and added its own probes.

| Round | Commit | Critical / High / Medium / Low / Info | Verdicts | Dispositions |
|---|---|---|---|---|
| 1 | `f05eec2` | 0 / 2 / 12 / 7 / 6 | all accept with changes | [review-01](review-01-dispositions.md) |
| 2 | `90f1e9e` | 0 / 1 / 6 / 7 / 5 | 060 accept; 061–063 with changes | [review-02](review-02-dispositions.md) |
| 3 | `20ed0e7` | 0 / 1 / 1 / 2 / 3 | 060, 063 accept; 061, 062 with changes | [review-03](review-03-dispositions.md) |
| 4 | `d939942` | 0 / 1 / 1 / 2 / 5 | 060, 063 accept; 061, 062 with changes | [review-04](review-04-dispositions.md) |
| 5 | `46d0a7f` | 0 / 0 / 0 / 1 / 4 | all accept | [review-05](review-05-dispositions.md) |

Every finding was answered in its own commit. The five reports are archived
unchanged in [`reviews/`](reviews/).

The main findings:
- round 1: vanilla derivation judged by file name, and LibVulpes approved by
  category although its textures came from an unmerged third-party pull request;
- round 2: the derivation check missed low-palette copies and crops, its
  results were not bound, AR's 16x texture set was not under origin review,
  and ADR-063 lacked an iridium source, a Tau Ceti access path, a Moon terrain
  that fits the fixed landing rule and implementable generators;
- round 3: edited crops slipped through, inheritance between legacy files was
  not computed, and the check needed about 10 GB of memory;
- round 4: inheritance read a capped, one-sided relation list, and filtered
  derivatives were an unstated limit.

The derivation check went through three versions in answer: schema 3 uses a
4 × 4 block search in both directions with near-equal and recolour measures,
records every legacy-to-legacy relation, applies inheritance in the plan and
the validator, runs in 0.35 GB, and is bound to ADR-061 by digests and a CI
`--check` step. Its calibration tests assert a false-negative rate of 0 per
kind of derivation and keep the known limits visible as expected-`CLEAR`
cases.

[independent-review.zip](independent-review.zip) holds the reviewer's reports,
logs and probes, indexed in
[independent-review-files.json](independent-review-files.json). It leaves out
the reviewer's repository exports, the upstream archives, the vanilla JARs and
any extracted or rendered vanilla image, and the bytecode disassembly logs.

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8, Python 3.13.

| Command | Result |
|---|---|
| `gradlew --offline clean build test runData runGameTestServer` on `bc4b96e` (later edits change documentation only) | Exit 0, 3 min 4 s. **337 required GameTests** passed. `:test` was FROM-CACHE. `git status -- src/generated` after DataGen: clean |
| `gradlew --offline test --rerun` | Exit 0, 2 min 3 s. **1,376 JUnit tests / 254 suites executed**, 0 failures, errors or skips |
| `python -B scripts/validate_v180_content_ledger.py` | Exit 0, PASS: 653 units, 898 assets |
| `python -B scripts/validate_v180_content_ledger.py --require-accepted` | Exit 0 after the acceptance edits |
| `python -B scripts/validate_v180_content_ledger.py --closure` | Exit 1 as intended: 292 rows PLANNED, 157 assets REVIEW (C19 closes them) |
| `python -B tools/audit/inventory_v180_content.py --upstream <Temp> --check` | Exit 0, up to date |
| `python -B tools/audit/vanilla_derivation.py --upstream <Temp> --vanilla … --check --report-memory` | Exit 0, up to date; peak memory 351 MB |
| `python -B -m unittest tests.test_validate_v180_content_ledger tests.test_vanilla_derivation` | First run: 68 tests, 1 failure: `test_require_accepted_reports_proposed_adrs` assumed ADR-062 was still PROPOSED. The test now sets PROPOSED in its own fixture copy (`post-run-changes.json`); rerun: exit 0, 68 tests |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B tools/audit/fetch_vanilla_clients.py --output <Temp>` | Exit 0; both clients match the pinned SHA-1 and SHA-256 |
| `python -B scripts/validate_repository.py --require-approved-identity`, `scripts/validate_v1plus_planning.py`, `-m unittest tests.test_v1plus_planning` | Final, after packaging: see below |

Final validator results after packaging: `validate_repository.py` exit 0
(45 passed, 0 failed); `validate_v1plus_planning.py` exit 0;
`tests.test_v1plus_planning` exit 0 (15 tests). They need the finished
packet, so they ran after packaging; their log is
`packaging/out/validation-final.log` in the Temp evidence directory, which is
deliberately outside `root-checks.zip`.

The three rebuilt JARs are **byte-identical** to the v1.7 development handoff
build (`artifact-comparison.json`). The seven non-documentation files changed
since the baseline are the audit and derivation tools, the client fetcher, the
validator, their two test files and the documentation workflow
(`implementation-files.json`); none is part of the runtime. The runtime stays
`1.20.1-1.7.0-dev`.

The full build log has **18 ERROR lines and 0 FATAL**. They come from
intentional failure-injection GameTests and the missing `server.properties`.
This is not a clean-log claim.

## Retained defects in the evidence trail

- Several Python patch scripts failed on Git Bash heredoc escaping (doubled
  backslashes are halved even in quoted heredocs). Two left a broken string
  literal in a test file, which the next test run caught; both were repaired
  before any commit.
- A round-2 tool run held about 7 GB of memory; schema 3 replaced it.
- The first unit-test run after the acceptance edits failed one test that
  assumed ADR-062 was PROPOSED (`py-06-unittest.log`, kept). The test now
  makes its own PROPOSED fixture; the change is listed in
  `post-run-changes.json` and the rerun passes (`py-06b-unittest.log`).
- The CI derivation step has not run on Linux yet; its first run records the
  Linux peak memory and duration in the batch evidence (ADR-061 §4.8).

## Not verified

- Any classic content runtime: the batches start with C15a.
- The CI workflow on GitHub (this branch has no pull request).
- The full Python test suite outside the v1.8 tests: on the clean v1.7 handoff
  export it already shows 9 failures and 117 errors from tests that need a
  Linux host or v0.0.2 manual evidence (implementation log).

Next chunk: C15a (materials, ores and the small plate press) in
[COMPLETION-PLAN](../../status/COMPLETION-PLAN.md).
