# TASK W246: import batches T1 to T3, the flat block and item textures

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w246` (new; branch `codex/v1.8.0-w246` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W246.md`; logs and audits under `D:/GitHub/ARCE-Task-Evidence/root-queue/W246/` |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W245/INVENTORY.md` (SHA-256 `77134f1a8f5f653811c7cfd9b651cca8f5e214a3066372d4c9d19db43076af55`) and `INVENTORY.json`: batches T1, T2 and T3, with each file's SHA-256, history result and current resource graph |
| Precedent | [W229](records/W229.md) (K1) and [W241](records/W241.md) (K4) for the import plan, provenance and drafts |

**Root decision R262**, under the owner's delegation, on W245's proposed batches:

- **T1** (asset-plan rows 229 and 230): static faces only. The three blast-brick faces and the crucible bottom map to
  the existing blocks' current states; the arc furnace's active face is not imported in this task unless the existing
  client state already selects an active model. Crucible faces under REVIEW or EXCLUDE stay out.
- **T2** (rows 241, 251, 252, 254, 255 and 260) and **T3** (rows 243, 244, 246, 247, 248 and 249): replace the current
  textures through the owning DataGen providers. Keep every ID, recipe, model parent, geometry and the airlock's
  half, open and hinge states and the torch's standing and wall forms.
- `textures/items/sawbladeiron.png` (REVIEW, the AR 16x set) is not imported.
- Retained NEW art that these imports replace: list each file and propose its disposition (kept as a fallback, or no
  longer referenced) as a draft; do not delete tracked art by hand.

## Steps

1. A `v180-w246-plan.json`, upstream byte copies through `tools/import/import_v180_assets.py` into
   `src/imported/v1.8/resources`, provenance PENDING_HUMAN_REVIEW, and import verify passing.
2. The provider and reference changes, runData output and tests that pin the new texture references.
3. **Drafts for Root:** asset-plan rows, any content-ledger rows, `.gitattributes` entries if needed, the replaced-art
   dispositions and a VERIFICATION summary, in `D:/GitHub/ARCE-Task-Evidence/root-queue/W246/` with an audit that
   writes each run under a distinct name. Do not edit the asset plan, the ledger, the allowlist or ADRs.

## Verification

- The importer and content-ledger Python tests, import verify and the generated-art screen.
- runData twice with identical output, the client and data unit tests, and the whole unit suite.
- Both validators with the drafts applied in a temporary copy.

Root pushes for a sharded run, and an independent review follows.

## Not in this task

The model batches (S1, P0 to P4, H1), the GUI rows, the exclusions, sounds, status files and the ledger itself.
