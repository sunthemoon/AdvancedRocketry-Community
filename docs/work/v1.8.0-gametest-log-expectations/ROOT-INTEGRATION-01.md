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
