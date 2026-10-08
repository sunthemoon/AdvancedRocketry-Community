# CL18A-THERMITE HANDOFF-02 (author return, unreviewed)

## Scope

Done: bounded successor for REPORT-01 M1 (tag ingredient), M2 (NEW art
separated from legacy disposition) and L1 (installed press fixture), in
CONTRACT-CORRECTION-02 and TEST-CORRECTION-02. Not done: everything outside
thermite ingredient/acquisition and the NEW-art plan; no source, test,
resource, art, config, ledger, asset-plan or provenance file; no run; no
adoption, freeze or delivery claim. Candidate d9d97eed is unchanged.

## Decisions proposed and remaining items

Proposed: tag `forge:dusts/thermite` as the torch ingredient and unlock;
unchanged existing press recipes; a survival press installation with
per-operation conservation; NEW declaration before pixels, legacy rows
226/229 left as IMPORT candidates for a later terminal decision.
Root: substitute-member test mechanism; TagKey helper location; recorded
outcome of `classic.smallPlatePress=false`; leaf dependency decision
(ADR-066:939-941); legacy-row route and the ADR-061:209-211 versus
ADR-066:869-870 text tension; still-open switch non-gating and umbrella tag.
Platform [U]: native torch/wall/BlockItem behaviour, template_torch parents,
dropsLike loot identity, tag unlock criteria, recipe-book display, Forge
`forge:ores/iron` contents. Resources: grids, palette, originality screen,
independent art review.

## Changed files and tests

Three new files in docs/work/v1.8.0-c18a-thermite/: CONTRACT-CORRECTION-02.md,
TEST-CORRECTION-02.md, HANDOFF-02.md. No test added or run.

## Tools, failures and deviations

Read, Glob, Grep, Write only; about 19 assistant turns. No shell, Git, Python,
JVM, Gradle, network, cache/JAR/archive or other agent; no tool call failed.
Live-main files listed in TASK-02 were read in full, plus ADR-060. ADR-061
(lines 85-214) and ADR-066 (104-117, 200-234, 860-873, 934-943) and the
sources/data cited were read in bounded ranges. The original three returns,
TASK-01 and REPORT-01 were read in full; other reviewer files were not.
Deviation: CONTRACT and TEST were each written twice with Write. Byte sizes
of every version, including the first CONTRACT draft that may have exceeded
6 KiB, were never measured; LF endings are expected from Write but unchecked.
Elapsed time and client cost were not measured here; Root's session record
is authoritative.

## Unrun checks

Byte sizes and LF; all A0/A1 cases; Gradle build/test/runData/GameTest;
validators; S1 N05 survival fixture; N04'; V1; tag-substitute behaviour.

## Risks and Gates

Without a bound substitute mechanism, M1 is designed but not provable. The
press-disabled configuration leaves thermite without new acquisition unless
Root records or authorizes otherwise. No Gate (G0-G9, A0/A1/S1/S2/V1/V2) is
advanced; no inherited acceptance or ADR-018 schedule is changed.

## Release of interests

No process, lock or session remains. All read interest in the main checkout,
this worktree and the reviewer leaf is released. Write interest covered only
the three files above and is released. No Git command was run: HEAD, index,
branch, stash and worktree are untouched. The added evidence directory
`c18a-thermite-correction02-claude-author-20261008-01` was not written; Root
captures the actual session, commits unchanged returns and obtains review.
