# W268-fix - rebased draft verification

Date: 2026-11-03. This supersedes W268's earlier scratch results for the
owner-facing proposal. Earlier logs remain under the original W268 evidence
directory; no sealed review or evidence file was changed.

## Tested source and boundaries

- Proposal/tested candidate: `75a11f1e540b4abc4eb3b4c2a5e7ea0c9bdd3805`.
- Assigned shared baseline: `0cae612d2ec986bfb67d8f6ac3bbe547a0e6d54d`;
  merged locally in `296c7cc6b6b3e3edabf2f88a22327cd301d1a2eb`.
- The [proposal](PROPOSAL.md) and three previews remain drafts. The scratch
  ADR is PROPOSED revision 7; no finding is signed, accepted or adopted.
- The tracked plan, allowlist, ledger, findings, ADR and content-ledger
  validator are byte-identical to that shared baseline. No source or
  resources were changed by W268-fix.

## Commands and observed results

All Python commands use `python -X utf8 -B`. Full command vectors, timings,
exit codes and per-command log hashes are retained in the external
`D:/GitHub/ARCE-Task-Evidence/root-queue/W268-fix/verification/verification.json`.

| Command or check | Observed result |
|---|---|
| `build_proposal.py --base 0cae612d2ec986bfb67d8f6ac3bbe547a0e6d54d --upstream <W229 upstream> --full-scan <external-file>` | PASS. All 95 expected REVIEW files retain their original rule semantics; 80 rules propose exactly 85 exclusions. |
| `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-fix/reproduce.py` | PASS at the committed candidate: all six generated outputs reproduce byte for byte, as does the complete external scan at a different output destination. |
| `test_proposal.py`, normal and `-O` | 14 tests pass in each mode. |
| `adopt_findings.py --findings origin-findings.json.draft --output <external rejected-findings.json>` | Expected exit 1: unresolved reviewer placeholder. No output file was created. Tests also prove refusal leaves an existing destination unchanged. |
| First-match and allowlist audit in scratch | PASS: 898 files enumerated, exactly 85 changed and 813 unchanged; exactly 85 allowlist entries removed. |
| `validate_v180_content_ledger.py` in scratch | Exit 0; 653 units. EXCLUDE 355, IMPORTED 89, REGENERATE 444, REVIEW 10, IMPORT 0. |
| Same, `--require-accepted` | Exit 1: 417 rows depend on deliberately PROPOSED ADR-062. |
| Same, `--closure` | Exit 1: the same ADR dependency, 16 PLANNED rows and ten REVIEW sounds. |
| All 14 v1.8 import plans, including W265, plus v0.1.0 import verify | All 15 commands pass. v1.8 provenance review remains PENDING_HUMAN_REVIEW. |
| `unittest tests.test_import_v180_assets` | 63 tests pass. |
| `unittest tests.test_validate_v180_content_ledger` | 58 run: 54 pass, four fail, as explained below. |
| `unittest tests.test_validate_v180_provenance` | 32 tests pass. |
| `unittest tests.test_validate_v090_resources` | 13 tests pass. |
| `validate_repository.py --require-approved-identity`, scratch and branch | Both exit 0: 45 passed, 0 pending, 0 warnings, 0 failed. |
| `git diff --check` | PASS before the candidate commit. |

The runner's own exit code is not a claim that every nested command passed;
the ledger accepted/closure failures are required draft boundaries.

## Counts and reproduction

The baseline counts are 270 EXCLUDE, 89 IMPORTED, 444 REGENERATE and
95 REVIEW, with no IMPORT. The draft is 355 EXCLUDE, 89 IMPORTED,
444 REGENERATE and ten REVIEW. The groups remain
34 + 11 + 17 + 8 + 4 + 4 + 10 + 7 = 95.

The allowlist is still reduced from 247 to 162 entries, with SHA-256
`aba84a60d93bf847e2a84ded5829c0447140d5a0902e6b69e13d41b0515e5f8d`.
All unaffected rule fields, owners and orders are preserved, including the
integrated W265/W267 decisions.

The committed evidence is 1,444,063 bytes (about 1.44 MB), down from
5,061,139 bytes. It retains bindings/consumer evidence, path/hash candidates,
sound-event Java uses and per-file/category scan counts. The complete scan
is 5,098,633 bytes at
`D:/GitHub/ARCE-Task-Evidence/root-queue/W268-fix/REVIEW-EVIDENCE-full.json`,
SHA-256 `c18f42192d6dae7f0eea4cf10efd54d5413721af1112817d8bb50940a5cdeb2c`.
The nested `.gitattributes` remains unchanged and pins draft files to LF.

## Four observed ledger failures and adoption fixture work

The four requested modules run 166 tests: 162 pass and four fail. The four
failures are the same tests identified in the independent review. None was
removed, weakened or treated as a pass.

[PROPOSAL.md](PROPOSAL.md) lists the exact changes for the four ledger tests:

- `test_excluded_finding_forces_exclusion`: restore beacon's original
  REVIEW/C17c row and reason before inserting the test's EXCLUDED finding.
  The current draft already excludes beacon, so the expected contradiction
  is absent and the actual error list is empty.
- `test_import_for_a_deferred_unit_fails`: restore that REVIEW/C17c row
  before changing its owner to `block:terraformer`. The current fixture sees
  EXCLUDE rather than the REVIEW handling its deferred-owner assertion needs.
- `test_review_needs_a_cleared_finding_to_import`: restore that row, its
  REVIEW allowlist line and fixture ADR-062 pin; remove its EXCLUDED finding;
  add `textures/blocks/controlpanel.png` to the CLEARED finding's releases.
  The actual errors report ceiling none, plan `-`, the EXCLUDED finding and
  the inherited controlpanel exclusion rather than the expected REVIEW ceiling.
- `test_require_accepted_reports_proposed_adrs`: no fixture change.
  It passes once revision 7 is genuinely accepted; this draft is not.
  Its initial accepted-baseline assertion fails on the 417 dependent rows.

Only the first three need fixture edits. This corrects the earlier W268
record's blanket fixture-update wording; no existing assertion is changed.
A separate adoption change must also harden the content-ledger validator
against placeholder reviewers and `draft_status`. The new adoption helper
does not authenticate an owner decision or replace ADR-061's commit/review
controls.

## Remaining closure and non-goals

The six non-audio PLANNED units remain `block:oxygenCharger` and
`block_variant:loader/1` through `/5`. The ten sound-event rows are also
PLANNED. G7 retains all ten legacy OGG files for D5's listen and per-file
decisions; `lathe.ogg` and `rollingmachine.ogg` are the same recording.

No clean build, runData, GameTest, Java unit suite, server/restart, D5 listen,
D7 review, real-GPU V1 or two-client V2 was run. This task changes only an
inert proposal and its Python helpers; it does not pass any v1.8 Required
Gate. Independent recheck and the owner's decisions remain pending.

## Evidence and cleanup

Evidence is under `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-fix/`:
`verification/` and its console log, `reproduction.json`,
`reproduction-build.log`, both proposal-test logs, `adoption-refusal.log`,
`scope-and-evidence.json`, the complete scan and the reproduction helper.
The full runner reports `completed: true` and `scratch_removed: true`;
its owned export `s038b4d64` no longer exists. The temporary second full-scan
copy was compared and removed by the reproduction helper. No server copy,
upstream cache, source world or other agent's directory/process was removed.

| Evidence file (relative to the external W268-fix folder) | SHA-256 |
|---|---|
| `verification/verification.json` | `0eea9f0e940bbb1a8912a6e369dae18dbb541081832bbf2f1b8d1bcb3e37b70f` |
| `verification/first-match-audit.json` | `b838b7e9a1c4127c4e611a61fe7b34798435b259947e44413d420515ca85febb` |
| `reproduction.json` | `5e34e630166f72566e7cc13b2411727ac4c2d36fa871f2c2046e16ec3d2caef3` |
| `scope-and-evidence.json` | `4edbc3c14f5eade5890fb2b8a73c46369384fea32c17d7aab678363b191f8aed` |

The first development generation stopped on a misnamed star-map class;
that mapping was corrected before the successful builds and candidate
commit. The committed-source regeneration succeeds. Earlier unsuccessful
development output is not represented as acceptance evidence.
