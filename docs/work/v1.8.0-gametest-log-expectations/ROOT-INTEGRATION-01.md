# GameTest log expectations - Root integration 01

Date: 2026-10-10. Scope: v1.8 development log attribution only. No version or Gate approval.

## Owner request and source identity

Owner, interactive Codex channel, 2026-10-10: "抽空审核下claude处理的问题，无误则合并下分支".
The supplied follow-up explicitly requests review of `e2ed43eb` and `30419f76`, an
ADR decision, and the checker command after the hosted GameTest command.
Root calls no Claude process; two fresh independent Codex workers review the
original source read-only. Root owns the isolated successor and CI edits.

- Original code: `e2ed43ebcf2aa64e6e59486adbc035126fddfe7d`.
- Original records: `30419f76f3dced6de23cbcfa5d39bd3b09cf4644`.
- Original base: `ba68099617027560db132596883a0c1186add4e9`.
- Root baseline: `cf8ae1915ad9802b28541ea5fab62abc54019668`.
- Root worktree: `D:/GitHub/arce-v180-log-integration-20261010-01`.
- Root evidence: `D:/GitHub/ARCE-Task-Evidence/v1.8.0/gametest-log-review-20261010-01`.

The normal merge preserves Main's subsequent loader records. The original owner
checkout remains clean and unchanged. Protected AGENTS.md and unrelated Main
changes are excluded from staging; the mixed implementation log is not adopted.

## Independent findings and disposition

Original code review, Temp `arce-log-checker-review-20261010-01/REPORT.md`, releases
source reads at 2026-10-10T14:50:17.087719+00:00. Policy review, Temp
`arce-log-policy-review-20261010-01/REPORT.md`, releases reads at
2026-10-10T14:51:31.804Z. Both exact original checkouts are clean. Reports and
bounded captures are retained at the external locations, not merged as product docs.

The original source is not accepted unchanged. Root corrects:

- malformed/BOM-prefixed headers that could hide new errors;
- completion-banner-only acceptance, absent/failed summaries and truncated shutdown;
- arbitrary-text stack matches and missing available Precision failure-handler frames;
- generic ChunkMap failures without the injected throwable;
- unconstrained Precision verification suffixes;
- aggregated recipe IDs and transfer recovery outcomes that could substitute for
  a missing case.

[ADR-072](../../decisions/ADR-072-V180-GAMETEST-LOG-EXPECTATIONS.md) accepts only the
revised classification policy under the owner's delegated review scope. It keeps
native outcomes, R-021, performance/resource budgets and all release Gates open.
The original 31-rule manifest becomes 38 narrower rules; the expected event totals
remain 62 ERROR and 52 exact-count test WARN, with six separate environment WARN ceilings.
The 161 raw WARN total is observed historical output, not a mandatory aggregate.

## Actual verification and remaining confirmation

Root's original 27 unit tests pass. The seven retained complete logs pass and four
negative logs fail; original private probes demonstrate the omitted-header and
shutdown-truncation defects. Independent policy audit verifies all 41 external
packet checksums, three tested source hashes and equality to the loader original.
Root does not execute the sealed packet's mutation or validator helpers; their
reported 11 mutations are historical submitter evidence, not a new Root execution.

Root's revised checker tests pass 48/48 before the successor checkpoint. No local
JVM or Gradle was launched during review. Full current native/build/data execution
and Linux attribution belong to the first instrumented hosted run. The complete
Python suite and strict validator are not qualified by this focused task; inherited
timeouts and broken documentation links remain open.

Successor commit, independent successor review and hosted outcome must be recorded
after they occur. Until then, this is an isolated integration candidate, not a
current-source Linux pass. All Required Gates remain unapproved.

## Committed successor review and follow-up

First successor `df29008dd59d8faa114d0a3e099b2154989856cf` has 37 rules and 48
passing focused tests. Fresh code review releases reads at 2026-10-10T15:02:46.129Z;
policy review releases reads at 2026-10-10T15:04:23.5932446Z. Their independent
retained-log checks reproduce seven passes and four rejections. The code review
finds whitespace-prefixed headers can still be ignored. Policy review finds the
one-shot EventBus rule lacks its available exact failure-handler frame, plus a
Low recipe rule's same-class attribution. Root independently demonstrates that
the two port diagnostic roots can still substitute across their batches.

The follow-up rejects whitespace/BOM-prefixed header-shaped records, pins both
available named EventBus origins and splits the two port roots by batch. The
manifest now has 38 rules without changing total expected errors or test warnings.
Three new tests cover those boundaries; their actual final committed result and
independent follow-up disposition are recorded after execution, not inferred.

At `df29008d`, the actual strict command
`python -B scripts/validate_repository.py --require-approved-identity` reaches its
original 180-second deadline without an original exit or final summary. Its owned
postdeadline retirement completes in 0.0048681 seconds with both streams at EOF.
The late exit 0 is not an original-command pass. No timeout is enlarged, no
44-check conclusion is inferred, and the failed qualification remains open.

Final-origin candidate `08c73eba91bd60d3f7177a54e6734807297c3021` passes Root's
51 checker plus 17 CI-host tests (68 total) and the seven/four retained-log audit.
A fresh reviewer releases this source at 2026-10-10T15:12:14.8789809Z and finds
one remaining Medium: delimiter-damaged header-shaped events can be ignored.
The follow-up recognizes native timestamp/event prefixes before accepting a
continuation, including damaged delimiters and unsupported control prefixes.
An added test covers seven forms. Final review and hosted status remain separate
actual observations; no prior candidate report is relabeled as a final-source pass.

At `bd4db1e9`, a fresh reviewer verifies 52 checker tests and the seven/four logs,
then reproduces a remaining Medium when the opening timestamp bracket alone is
missing. Reads end at 2026-10-10T15:19:21.4543076+00:00. The next small follow-up
recognizes the timestamp without requiring that bracket and tests ERROR, WARN
and FATAL variants. This original finding and its exact reviewed SHA are retained.

At `74f7cfe7`, Root's exact committed checker/CI-host tests pass 53+17. A fresh
reviewer independently confirms 53 checker tests and seven/four retained logs,
but finds a Medium involving damaged date prefixes and timestamp-less events
behind control prefixes. Its source-read cutoff is
2026-10-10T15:28:33.4671336+00:00. The follow-up recognizes native level/logger
fields independently of the timestamp or line-start position. An isolated level
mention without logger fields remains ordinary exception text; an embedded full
native event shape is rejected rather than silently treated as continuation.
This boundary is deliberately fail-closed and is not causal authentication.

## Final reviewed integration and hosted observation

The earlier candidate/pending paragraphs above are historical checkpoints,
superseded by this section. Exact final candidate is
`3e93c2c3d5e8fd1242c83d99184ef4b3466abd36`. Root's precommit tests execute 54
checker and 17 CI-host tests, all passing. A fresh independent final review runs
54 checker tests, seven retained positives and four negatives, plus 36 native
field-boundary probes. There is no blocking finding for pinned native-format
events. One Low remains: recognition is heuristic, not exhaustive arbitrary
corruption or timestamp validation. Fully native-looking literals in throwable
text are rejected conservatively; causal authentication is not claimed.

Final report is retained in the external Root leaf as `reviews/fields-final.md`,
SHA-256 `b3d2c40a91e17632c1adb55800f3dbbb81d85b5e8150ae535ed8779e03596556`.
Its exact source-read cutoff is 2026-10-10T15:34:29.9402666Z; the candidate is clean.
After all source reads are released, Root normally merges to
`52a29c1e76977033a1b7e85baf32ca5d19c7414c` and pushes Main. The full tracked merge
tree equals the reviewed candidate; src/Gradle/Wrapper inputs also equal the
previous local native candidate, without claiming a new local JVM execution.
Main's 149 pre-existing dirty/untracked rows, protected AGENTS.md and mixed
implementation-log bytes are unchanged and excluded from staging.

The first instrumented hosted run is
[38064322640](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/38064322640)
at that exact merge SHA. Official REST observation at 2026-10-10T15:37:03.356777Z
reports IN_PROGRESS, not a Linux pass. Native execution and the subsequent log
checker remain required in the same failing Bash step; old Linux evidence does
not qualify this current run.

Two Root invocation mistakes are retained as failures: a nonexistent CI-host
test module and a missing argument to the scoped link helper. Corrected calls
pass; they do not erase the originals. The final pre-fields scoped link check
finds 12 links and zero broken links, not a whole-repository Markdown pass.
Strict validation's original 180-second timeout remains unqualified.

Root also identifies an operational read-accounting deviation: its audit driver
resets the read counter per invocation, not across the declared review task.
Three raw audit passes plus the named archive total at least 67,270,984 bytes,
exceeding the declared 67,108,864-byte aggregate before ordinary source reads.
`READ-ACCOUNTING-CORRECTION.md` in the external leaf preserves this failure.
No limit is retrospectively raised, no more raw Root replays occur, and no
resource qualification is claimed. Independent workers have separately bounded
cohorts. All version resource, persistence and release obligations remain open.

### Actual first hosted result

Fresh official REST observation at 2026-10-10T15:51:40.519338Z confirms the exact
run completed FAILURE at 15:48:50Z; job 114248775390 likewise fails. The pending
observation above is superseded. Tooling, clean build/fresh unit tests, artifact
audit and both DataGen passes succeed. The composite `Run all Forge GameTests`
step fails at 15:48:43Z. Metadata alone cannot distinguish native failure from
subsequent checker failure; no Linux pass or raw counts are claimed.

The always-uploaded raw evidence artifact is 11674276944, named
`v180-regression-52a29c1e76977033a1b7e85baf32ca5d19c7414c-1`, 1,873,290 bytes,
official digest `sha256:a8ed53f146acac4da4eb7da193c40f0b9d1222dffda063cdb4e4f3b644459db1`.
Anonymous job-log download returns HTTP 403 at 15:53:37.503980Z. A successful
HTTP-inspection process receipt is not successful log retrieval. Root requests
the missing raw output and assigns a separate bounded anonymous public-artifact
inspection without credentials or source writes. No assertion, timeout or
manifest is changed in response to an unknown cause, and no blind rerun occurs.

### Verified current Linux failure distinction

The missing-output request is superseded by anonymous public artifact retrieval
and exact official digest verification. A fresh independent inspector completes
source reads at 2026-10-10T16:00:18.571Z (2026-10-11 00:00:18.571 +08:00).
Its report is preserved as `reviews/linux-failure.md`, SHA-256
`483cf11d613a66b362acf21acb36379628b84731b8bb0e92a6d5150fefd2e14f`.
Root separately preserves the exact artifact, selected inert logs and identities
in the external leaf's `ci-failure/`, 15 files / 5,506,222 bytes, all copy hashes
equal; no archive helper or copied code is executed.

The hosted native console reports 597/597 required tests passed, normal shutdown
and `BUILD SUCCESSFUL in 5m 36s`. The checker then exits 1. Its exact output
identifies latest.log line 2326: IOWorker ERROR `Failed to store chunk [-13, 7]`,
temporal batch `endgame_gravity_station`, with no GameTest frame. The throwable is
`java.util.ConcurrentModificationException` in `CompoundTag.write` via `NbtIo`
and `RegionFileStorage.write`. The reviewed live checker independently reproduces
the same single rejection. Raw totals are 63 ERROR /161 WARN /zero FATAL;
all original expected errors/warnings and exact counts match. This is a newly
observed undeclared storage error, not a count mismatch or parser defect.

The artifact's tested SHA, checker/manifest/workflow blob identities and official
metadata agree with `52a29c1e`. Hosted checker units also execute 54 passing tests.
This qualifies current Linux execution evidence, not a successful Linux log Gate.
Batch time does not identify the asynchronous producer, dimension or data-loss
impact. A separate readonly source triage is assigned; no producer, product fix,
new expected-log rule, save-risk acceptance or successful rerun is claimed.
