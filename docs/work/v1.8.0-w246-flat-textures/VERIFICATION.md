# W246: flat block and item textures

Implementation source: W246's `dddd32ebccb9d070bbe72d7c89ee1d153d988edb`, with W246-fix (`4ec6cef2a`, Root decision R267) and W246-fix2 (`c2c5974fc`, `f38e03773` and `1c8988c44`, Root decision R268). Root decision R262 imports only the selected static T1 faces and the T2/T3 icons and faces. Root applied W246's asset-plan, ledger and verification drafts in `8be926991`. This summary is not Gate approval.

The plan `tools/import/v180-w246-plan.json` and provenance `docs/provenance/v1.8.0-w246-flat-textures.json` cover sixteen upstream byte copies (6,313 bytes). History and derivation checks pass. Human provenance review remains PENDING_HUMAN_REVIEW.

W246 first kept the 1.12 folders `textures/blocks/` and `textures/items/`. No 1.20.1 block-atlas source stitches them, so the independent review's blocking finding W246-R1 showed that all 24 rebound models would render as the missing texture. W246-fix2 moved the sixteen files, with unchanged bytes, into the stitched `textures/block/` and `textures/item/` folders. Retained NEW art already holds three of the names, so those imports are `item/jackhammer_classic.png`, `item/jetpack_classic.png` and `item/thermite_classic.png`. The new unit test `PackagedBlockAtlasCoverageTest` (W246-R2) checks every packaged model texture against the block-atlas sources.

The rebinding changed two item models that earlier records pin. The superseding record [v1.8.0-w246-rebound-item-models.json](../../provenance/v1.8.0-w246-rebound-item-models.json) (W246-fix, regenerated in place by W246-fix2) names their current SHA-256 values and the earlier entries it supersedes, which stay unchanged.

The furnace has facing only, so blastbrickfront_active is not imported. The crucible keeps cube_all and overrides down only; its NEW all texture still covers the other faces. The workstation replaces top only. Model parents, geometry, IDs, recipes, the door states, the standing/wall torch forms and the separate worn jetpack model do not change. REVIEW/EXCLUDE crucible faces and sawbladeiron stay out.

All replaced NEW PNGs remain tracked and generated with their original bytes. Fifteen are no longer referenced by runtime JSON, and quartz_crucible still supplies five faces and the particle. None is a fallback, and no deletion is authorized here.

Actual commands, log identities, Python/Java counts and validators are recorded in [W246](../v1.8.0-root-queue/records/W246.md) and the external W246, W246-fix and W246-fix2 evidence directories. A recheck of W246-fix2 by a fresh session, human license review and V1 remain. No release, V1/V2 or all-Gate pass is claimed.

## Executed verification

### W246

The Java/resource source is `5bcd56046bef38c6748b972ace32084849e66b9a`; `dddd32ebccb9d070bbe72d7c89ee1d153d988edb` changes only three Python rejection fixtures to use the still-unimported active furnace face, retaining exact rejection assertions. Their Java, Java-test, generated and imported trees are identical.

| Command/check | Result |
|---|---|
| Importer generate and verify, with both pinned clients | 16 targets reproduce; PENDING_HUMAN_REVIEW |
| Python importer and content-ledger unit modules, in the draft-applied copy | 113 pass |
| Offline runData, twice under the owned machine lock | Both pass; all 1,915 resource hashes identical |
| Isolated V180FlatTextureImportTest | 5 pass |
| Client/data plus equipment/tool data unit tests | 469 pass in 91 classes |
| Whole offline Java unit suite | 6,076 pass in 1,094 classes; zero failure/error/skip |
| Generated-art screen against both pinned vanilla clients | 202 CLEAR |
| Accepted content-ledger validator in draft-applied copy | Pass; 653 units |
| Approved-identity repository validator in draft-applied copy | 45 pass; no pending/warning/failure |
| Source checkout content validator before Root applies central drafts | Expected failure: exactly 16 pending IMPORT-to-IMPORTED rows |

The runData manifest SHA-256 is `0c2b698392ec6dbe801df2124a5473d694718c2d76ca1fd29886d32a65e04262`. The audit checks this against the committed generated tree, checks all copied bytes against the plan, record and Git blobs, compares history to W245, checks effective binary attributes, and verifies first-match handling for every legacy manifest asset. Runtime results, if separately executed, are reported in the W246 record rather than inferred from these resource tests.

### W246-fix2

| Command/check | Result |
|---|---|
| `PackagedBlockAtlasCoverageTest` alone, on `e495440bb` with only the test added | Fails as intended: 16 uncovered sprites, all W246's, of 421 references in 385 packaged models |
| The same test after the move, alone and with the client/data filters | Passes: 421 references, 0 uncovered, no other problem |
| Importer generate and verify, with both pinned clients | 16 targets reproduce; PENDING_HUMAN_REVIEW |
| Offline runData, twice under the owned machine lock | Both pass; the 1,444 files of `src/generated/v1.8` are identical, and the same 24 models change, each only in a texture value |
| Client/data plus equipment/tool data unit tests | 471 pass in 92 classes |
| Whole offline Java unit suite | 6,078 pass in 1,095 classes; zero failure/error/skip |
| Python importer and content-ledger unit modules; provenance unit tests | 113 pass; 32 pass |
| Provenance audit with the CI build's arguments | Rules 1 to 5 pass (rule 4 failed for the two item models until the R267 record was regenerated) |
| Accepted content-ledger validator | Pass; 653 units |
| Approved-identity repository validator | 45 pass; no pending/warning/failure |
