# TASK W229: the narrow upstream-URL history rule (D8), then the K1 centrifuge import

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w229` (new; branch `codex/v1.8.0-w229` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W229.md` |
| Owner decision | D8 in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md): "批准一条经审核的窄规则（推荐）": references to the upstream repository's own URLs are not a foreign origin; every file still gets its history, hash, format-budget and reference checks; licence checks are unchanged; the centrifuge pair first |
| Sources | [W51's record](records/W51.md) (the blocked K1 import and its trigger, merge `0c0017bc`); `tools/import/v180_import_checks.py` (`THIRD_PARTY_MESSAGES`, `upstream_history`); `tools/import/import_v180_assets.py`; `tools/import/v180-k1-plan.json`; ADR-061 section 4 item 9; [UPSTREAM.md](../../../UPSTREAM.md); `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` (Q19, D8) |
| Drafts | `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/W229-drafts.md` with an audit script and its JSON result, in the format of `D:/GitHub/ARCE-Task-Evidence/root-queue/W227/W227-drafts.md` |

**Root decision R222.** The owner approved a reviewed narrow rule for the upstream's own URLs. W51's K1 import of
the centrifuge OBJ and texture stopped only on that trigger.

## What to do

1. **The rule.**
   - Establish from evidence which addresses are the upstream repository's own: the current
     `github.com/Advanced-Rocketry/AdvancedRocketry` and the earlier `github.com/zmaster587/AdvancedRocketry` that
     merge `0c0017bc` names. Cite the evidence in the pinned upstream clone; say what you could not prove.
   - Change only the "a link to another project" check, so that a URL which is exactly one of those addresses does
     not trigger it. Only scheme, `www.`, a trailing `.git` or `/`, and case may vary.
   - Every other URL in the same subject still triggers. Every other history rule, the AR 16x set, the derivation
     re-run, hashes, format budgets, references and licence checks stay unchanged.
   - Tests fail first:
     - the `0c0017bc` subject no longer triggers;
     - each of these still triggers: lookalikes (another owner, a fork name, `AdvancedRocketryAddons`, LibVulpes, a
       longer host such as `github.com.example`, a path under another repository), a URL with credentials or a
       port, and an own URL in the same subject as "taken from" or as another URL.
2. **K1.**
   - Run the existing K1 plan with the changed tool (the NEW `centrifuge.mtl` from its authoring commit, the OBJ and
     texture as byte copies). Commit the provenance record and the runtime assets it writes, exactly as the importer
     produces them.
   - Do not edit the asset plan, the content ledger, the allowlist or ADR-061. Put the exact rows they need into the
     drafts: the centrifuge rows, and `event:ClientProxy.modelBakeEvent` with a status that the physical-client-only
     evidence supports.
   - Root applies the drafts as its own commit on your branch.
3. **ADR-061.** Draft an additive revision of section 4 item 9 that states the rule, cites D8 and keeps the AR 16x
   paragraph unchanged.
4. **K2 to K5.** Rerun the history check, without importing, for the other K-batch rows that W51 counted. List which
   ones the rule now clears, and which still trigger and why.

## Verification

- The importer's and the content ledger's Python tests.
- `import_v180_assets.py` in verify mode on the new record.
- The repository validator, with the drafts applied in a temporary copy.
- The centrifuge renderer's and OBJ loader's unit tests.

An independent review follows before the merge.

## Not in this task

K2 to K5 imports, the real-GPU OBJ check (the final campaign), other provenance findings (D7), status files and the
ledger itself.
