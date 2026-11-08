# Owner decisions of 2026-11-08: the stand-in art replacements and the D7 comparison sheets

Channel: the owner's Claude Code Root session, 2026-11-08. Between Root's hourly checks of 22:39 and 23:38 the owner
wrote, verbatim:

> 美术我看了，滚压机和端口可以，气罐通过

The message answers the art judgement that Root's checks had listed as open for the owner since round 7 of the
single-player test: the owner's three remarks of 2026-11-04 (the rolling machine's piston-head front, the ports'
redstone or concrete tops and drawer-like sides, and the canisters' glass bottle; see
[OWNER-DECISIONS-20261104](OWNER-DECISIONS-20261104.md), item 2 and R308), the NEW art that W275 and W277 drew for
them, and the round 7 contact sheet
`D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/evidence/r07-sp/sheets/art-owner-complaints.png`.

The two provenance records of that art cover more than the three named items. Root therefore asked in the same session,
in a question dialog, whether both records should become APPROVED in full, naming what else they cover: W275's
precision assembler front, and W277's plain side panel that replaced the drawer-like side on 24 blocks and its new faces
on the guidance computer, rocket assembler, fuel loader, warp core, railgun, satellite builder, orbital laser drill,
electrolyzer and others. In the same dialog Root asked again the question of 2026-11-04 about per-record comparison
sheets for the 30 records of D7. At about 23:05 the owner selected, verbatim (the option labels):

> Approve both in full (Recommended)

> No, approval stands (Recommended)

## What the owner decided

| Item | The owner's words | Recorded meaning and scope |
|---|---|---|
| The three remarks of 2026-11-04 | 滚压机和端口可以，气罐通过 | The replacements are accepted: the rolling machine's front, the ports (their tops and sides, each with its type icon) and the gas containers (the three canisters, the nitrogen canister and the four pressure tanks, drawn as metal cylinders). No redraw is needed. |
| `v1.8.0-w275-stand-in-art` and `v1.8.0-w277-stand-in-art` | 美术我看了; Approve both in full | The owner's visual and originality review (D7, ADR-061 section 4.5) passes both records in full, as they stand at the shared head `e07b09627`. Their review status becomes APPROVED, with `sunthemoon` as reviewer and 2026-11-08 as the review date. Art that changes later gets a new pending review. |
| The D7 comparison sheets | No, approval stands | No per-record comparison sheets are added for the 30 records approved on 2026-11-04. That approval stands as the owner gave it, and the question is closed. |

This does not mark V1, V2, S1, any Gate, the version or a release as passed. It finds nothing about the authorship of
the excluded AR 1.12 art (W279).

## Root's follow-up decisions

| ID | Decision | Reasons |
|---|---|---|
| R383 | **W324 sets `v1.8.0-w275-stand-in-art` and `v1.8.0-w277-stand-in-art` to APPROVED** through the mechanism W276 added: review status, reviewer `sunthemoon`, `reviewed_at` 2026-11-08 and a basis citing this record, with the content digests unchanged and the tests that assert these records' pending state changed to assert the exact approval. A fresh Codex session implements it; Root reviews the diff and merges after a green hosted run. | The owner's review passed both records in full. W276 already provides and tests the approval mechanism, so this change records an owner decision in two files without changing the mechanism; Root's review of a Codex change is cross-engine. |
| R384 | **The D7 comparison-sheet question is closed** without sheets; [OWNER-DECISIONS-20261104](OWNER-DECISIONS-20261104.md) gets a pointer to this record. | The owner's answer. |
