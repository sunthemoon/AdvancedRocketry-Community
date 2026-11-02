# TASK W268: a proposal to close the 95 REVIEW files (ADR-062 revision 7 draft, nothing applied)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w268` (new; branch `codex/v1.8.0-w268` from the shared head named in your prompt) |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W263/INVENTORY.md`, SHA-256 `861320967dde0492569d8e31f2874a0d7288c603af8d2bca5720c720fb97925a`: section 2 "REVIEW" (the 95 files), section 4 (owners and consumers), section 6 "What asset plan closed still requires" and section 7 (exact files and hashes) |
| Rules | ADR-061 section 4 (origin findings), ADR-062 revision 6 (the format to follow: its per-file table and counts), ADR-066 section 7.3 (REVIEW to EXCLUDE needs an ADR-062 revision, a new allowlist pin and matching origin evidence), and `scripts/validate_v180_content_ledger.py` |
| Parallel batches | W265 (the laser gun) and W267 (the X1, X2, X3, J0, U1 and active-face exclusions) change the asset plan, but no REVIEW row; do not touch their rows |
| Record | `docs/work/v1.8.0-root-queue/records/W268.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/` |

**Root decision R296**, under the owner's standing delegation for design points. ADR-062 revision 6 closed 98 files on
the owner's scope decisions D2, D3, D4 and D6 and left 95 REVIEW files open. Closing them needs an ADR-062 revision
that only the owner can accept, together with D7, the owner's provenance review. This task prepares that decision so the
owner can take it in one step. It changes nothing that ships.

For each of the 95 REVIEW files:

1. **Evidence.** The source SHA-256, the derivation verdict, the allowlist ceiling, the owners, every reference in the
   tree (models, MTL, blockstates, Java, JSON, import plans and records, data generators), upstream's own consumer at
   the pin, and the live replacement in the mod today (file and class), or that there is none.
2. **A proposed terminal disposition** with its reason, one of:
   - EXCLUDE: the legacy file is not shipped; the reason names the live replacement, and the finding states that it
     does not clear authorship (as revision 6 did);
   - keep REVIEW until a named owner step (for example D5's listen for the legacy sounds, or an authorship question
     for the LibVulpes casing and motor), saying exactly what that step must decide;
   - IMPORT after a CLEARED origin finding, only where a live consumer would use the file and the evidence supports
     clearing it; say who could record that finding under ADR-061's rules.
3. **Groups.** Group the files the way revision 6 grouped its decisions (for example: unbound machine faces with NEW
   replacements, unbound item icons, GUI button sheets, space suit textures, legacy lava, the LibVulpes casing and
   motor, the legacy sounds, the beacon and force field), with counts that add up to 95 and one owner question per
   group.

Deliver, under `docs/work/v1.8.0-w268-review-closure/`:

- `PROPOSAL.md`: the groups, the owner questions, and a draft "Revision 7" section in revision 6's format (per-file
  table with source SHA-256 and evidence, counts before and after, the new allowlist SHA-256), marked as a draft
  pending the owner's decision;
- the draft asset plan, allowlist and origin-findings changes as separate files beside it (the findings' reviewer is
  left as the owner's placeholder, never filled in as if the owner had decided), and a script that applies them to a
  scratch copy;
- the scratch-copy results.

Do not change the tracked asset plan, allowlist, origin findings, ADRs, ledger or status files on the branch.

## Verification

With the drafts applied in a scratch copy only: the content-ledger validator (also with `--require-accepted` and
`--closure`, reporting what `--closure` still lists), a first-match audit showing exactly the proposed files change, all
import verifies, the importer, ledger, provenance and resource Python tests, and the repository validator. On the
branch itself: the repository validator.

Commit the proposal, drafts, script and record on the branch; do not push. Root reviews, pushes, and a fresh session of
the other engine reviews. The owner decides; nothing is applied before that.

## Not in this task

Applying any draft, accepting the ADR revision, recording a finding in the owner's name, D5's listen, D7's review of
the imported records and NEW art, the 35 retained NEW textures (W267 listed them), new imports, Java or resources.
