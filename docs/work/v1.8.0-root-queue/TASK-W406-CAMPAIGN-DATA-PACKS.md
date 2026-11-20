# TASK W406: the campaign's data packs, generated with their expected outcomes (package K06, generation part)

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w406`, branch `codex/v1.8.0-w406` from the shared branch |
| Decision | Root decision R543 (QUEUE row W406) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md): package K06, rows B-3, D-3, D-6, E-1, E-2, E-6 and F-3, choices C17, C19 and C21, Root's section 8; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI20 to FI25; the step lists [B](records/CAMPAIGN-CHECKLISTS-B.md) (B03, B06), [D](records/CAMPAIGN-CHECKLISTS-D.md) (D04, D08, D09), [E](records/CAMPAIGN-CHECKLISTS-E.md) (E01, E02, E05) and [F](records/CAMPAIGN-CHECKLISTS-F.md) (F05); the existing builders in [v140_migration_fixture.py](../../../scripts/v140_migration_fixture.py), [run_v130_atmosphere_boundary_smoke.py](../../../scripts/run_v130_atmosphere_boundary_smoke.py) (`tag_pack`) and [run_v130_satellite_payload_smoke.py](../../../scripts/run_v130_satellite_payload_smoke.py); the tracked test data under `src/test/resources/data/`; the codecs and limits in `src/main` that read each kind of file |
| Outputs | A generator `scripts/campaign_prep/k06_packs.py` (do not add `scripts/campaign_prep/__init__.py`: W402 adds it on another branch that merges first; until then the folder works as an implicit namespace package, so import the module as `scripts.campaign_prep.k06_packs` with the repository root on the path, or load it by file path in the test), a test `tests/test_campaign_prep_k06.py`, the record `docs/work/v1.8.0-root-queue/records/W406.md`; outside Git, `D:/GitHub/ARCE-Task-Evidence/final-campaign/659bbcb6a/fixtures/K06/` |
| Write scope | Those files only. No product code, no existing script, test, record, checklist, ADR, register, status file or QUEUE. |
| Not allowed | Any JVM, Gradle, GameTest, server or client: the heavy-JVM lock belongs to candidate C4's native runs. Loading each pack on the candidate and reading the server's quotes is a later step under the lock. |

## What to build

1. **Route packs** (B03, B06, E01, E02): a valid pack, an invalid one (a reference the codec refuses), one naming an
   unknown body, and one with a locked route; and the data-only `test_mars` pack, which must be data only and not
   the built-in Mars. Start from the tracked route JSON and the v1.4 pack builder.
2. **Catalog packs** (E05, C21): a catalog with long IDs at the codec's declared limit and one past it, names of 48
   characters, and catalogs of 100 and 128 bodies. An oversized or invalid one is a refusal test, never the setup of
   another row.
3. **Station pack** (F05): a target-removal and a cost-change pack for the station warp rows.
4. **API packs** (D04, D08, D09): the atmosphere tag pack exactly as the runner's `tag_pack` builds it (C17), and the
   satellite payload pack with its changed-definition variant, from the payload runner's builder.
5. **For every pack**, from reading the codec and its limits in `src/main` (cite file and line): the expected outcome
   when the candidate loads it (accepted, or refused with the reason or log line the code gives), the values the step
   lists need (body IDs, route legs, costs, counts), and what the load check under the lock must read back. Do not
   guess an outcome you cannot find in the code; mark it as to be observed.
6. **Determinism**: ZIPs stored with sorted names and a fixed timestamp, so the same input gives the same bytes;
   `SHA256SUMS.txt` for the output folder; a `manifest.json` with each pack's path, hash, rows served and expected
   outcome.
7. **Checks**: unit tests for each builder's contents, the limit values taken from the code, and determinism. Run
   `python -B -m unittest discover -s tests -p test_campaign_prep_k06.py -v` and
   `python -B scripts/validate_repository.py`, which must end with `0 failed`.
8. **The record**: the packs, their hashes and expected outcomes, the code citations, the commands and results, and
   the load-check list for Root. Link only to tracked files on the shared branch; use a code span for anything else.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
