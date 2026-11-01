# W246: flat block and item textures

Implementation source: `dddd32ebccb9d070bbe72d7c89ee1d153d988edb`. Root decision R262 imports only the selected static T1 faces and the T2/T3 icons and faces. This summary is a Root integration draft, not Gate approval.

The plan `tools/import/v180-w246-plan.json` and provenance `docs/provenance/v1.8.0-w246-flat-textures.json` cover sixteen upstream byte copies (6,313 bytes). History and derivation checks pass. Human provenance review remains PENDING_HUMAN_REVIEW.

The furnace has facing only, so blastbrickfront_active is not imported. The crucible keeps cube_all and overrides down only; its NEW all texture still covers the other faces. The workstation replaces top only. Model parents, geometry, IDs, recipes, the door states, the standing/wall torch forms and the separate worn jetpack model do not change. REVIEW/EXCLUDE crucible faces and sawbladeiron stay out.

All replaced NEW PNGs remain tracked and generated with their original bytes. The disposition draft lists live partial art separately from files no longer referenced by runtime JSON. None is automatically selected as a fallback, and no deletion is authorized here.

Actual commands, log identities, deterministic runData manifests, Python/Java counts and validators are recorded in [W246](../v1.8.0-root-queue/records/W246.md) and the external W246 audit directories. Root still applies the drafts, obtains independent review and sharded regression, and arranges human license review and V1. No release, V1/V2 or all-Gate pass is claimed.

## Executed verification

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
