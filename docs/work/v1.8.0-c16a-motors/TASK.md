# C16a-07 motors and casing identity

Date: 2026-10-03. Status: in-progress; component sub-slice verified,
all-tier family-formation proof remains planned.
Contract: accepted ADR-064 revision 2, section 4.
Root branch: `codex/v1.8.0-classic-content`, HEAD `cd63c5ff` plus preserved work.
Worker: isolated `D:/GitHub/arce-v180-motors-20261003`; only new component,
motor DataGen/test and own harness files. Root owns central integration,
generated output, provenance, translations and status. No commits/tags/push.

Observable result: four stable motor block/item IDs, both motors tags, the exact
accepted recipe multisets, original art, and the existing `endgame_casing` ID
with its Advanced Machine Casing label. No new casing ID or resource migration.

Encoding assumption: recipes are shapeless because the accepted contract defines
ingredient quantities but no grid shape; do not add a positional restriction.

Verification: pure catalog/generated-data checks; actual crafting/tag/loot
GameTests; packaged v1.7 casing block/item upgrade and four tiers saved through
two same-world restarts; original-art screening and independent actual-diff review.
Future classic machine patterns still need all-tier formation tests when those
controllers exist. V1/GPU and all version Required Gates remain separate.

- [x] `C16a-07a`: stable registration, both tags, exact recipes, generated art/data
  and central integration - verified by 15 component JUnit, three real GT,
  mandatory root checks, native upgrade/two restarts and independent actual-diff
  [review](reviews/REVIEW-FINAL.md); [verification](VERIFICATION.md).
- [ ] `C16a-07b`: every tier forms each applicable classic machine family -
  planned; depends on C16b/c controllers and their frozen exact patterns.
  Verification requires actual structure formation/refusal tests, not only tag
  membership. Keep both primary ledger rows PLANNED until this criterion passes.

Real-client V1/JEI and version-wide progression/compatibility/Gate requirements
remain separate. The component review does not approve the full leaf or version.
