# TASK W31: per-batch delivery evidence files and a ready-to-apply ledger patch

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w31-ledger-evidence` |
| Branch | `codex/v1.8.0-w31-ledger-evidence` (local only; Root merges) |
| Input | W30's proposal: `docs/work/v1.8.0-root-queue/records/W30-STATUS-PROPOSAL.md` on branch `codex/v1.8.0-w30-status` (`2bc87cee`), sections 1.1, 2 and Appendix A |
| Record | `docs/work/v1.8.0-root-queue/records/W31.md` |

## Goal

Make W30's ledger proposal applicable. The ledger validator
(`scripts/validate_v180_content_ledger.py`) accepts a v1.8.0 IMPLEMENTED or REDESIGNED row only
when its evidence file exists, matches the path pattern, and lists the unit ID and every modern
ID in the row's target (W30 section 2.1).

1. **Create the ten evidence files** that W30 section 2.1 names, each under
   `docs/work/v1.8.0-<batch>/VERIFICATION.md`:
   - `c16b-machines`;
   - `c16c-machines`;
   - `c16d-components-graph`;
   - `c17a-propulsion`;
   - `c17b-station`;
   - `c17c-solar-beacon`;
   - `c18a-life-support`;
   - `c18b-equipment`;
   - `c18c-research`;
   - `c18d-presentation`.

   Each file contains:
   - the merge commits on `codex/v1.8.0-classic-content`, and the tested HEADs;
   - the hosted CI runs;
   - each independent review path with its SHA-256;
   - a "Delivered ledger units" table listing each unit ID and its modern IDs from Appendix A;
   - the open S1, S2, V1/V2 and dedicated-server items;
   - the statement that no Gate is approved.

   Take everything from QUEUE.md, the merge commit messages (`git log --merges`) and the
   records. Verify each SHA with `git cat-file -e`.
2. **The ledger patch.** Do **not** commit the change to the ledger CSV,
   `docs/work/v1.8.0-content-ledger.csv`; under AGENTS.md section 13 it is Root's. Instead:
   - apply Appendix A to a copy in your Temp directory;
   - run `python -B scripts/validate_v180_content_ledger.py --require-accepted` against that
     copy, with the evidence files present (use the script's path options, or a temporary
     copy of the tree);
   - save the exact diff as `docs/work/v1.8.0-root-queue/records/W31-LEDGER.patch`;
   - record the validator's output and counts in your record.

   Root applies the patch.
3. **Flag anything W30 marked uncertain**, for example `makeMaterialsForOtherMods` and the
   advancement IMPLEMENTED-versus-REDESIGNED choice, as questions in your record. Do not
   decide them.

## Not in this task

- Editing the ledger CSV, the status files, the ADRs or the risk register.
- Code.
