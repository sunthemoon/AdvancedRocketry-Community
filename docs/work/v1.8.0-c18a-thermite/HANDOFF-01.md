# CL18A-THERMITE HANDOFF-01 (author return)

## Scope

Done: draft contract (CONTRACT-01) and test design (TEST-DESIGN-01) for
thermite acquisition and the ordinary standing/wall light only. Not done: any
source, test, asset, PNG, ledger, provenance or evidence file; any run; any
adoption. Remaining CL18A-TORCHES units stay PLANNED/CONTRACT_ONLY.

## Decisions proposed (none adopted)

Stable IDs `thermite`, `thermite_torch`, `thermite_wall_torch`, tag
`forge:dusts/thermite`; Root's shapeless 1+1->1 and stick+thermite->4 recipes
with exact-item torch ingredient; constant light 14 with no atmosphere read; no
new switch and no gating by `lifeSupport.classicDevicesEnabled`; NEW art in a
leaf DataGen class; narrow C18 dependency claim (consumes none) for review.

## Changed files and tests

Three new files under `docs/work/v1.8.0-c18a-thermite/`: CONTRACT-01.md,
TEST-DESIGN-01.md, HANDOFF-01.md. No test added or run.

## Tools actually used and failures

Read, Glob, Grep, Write, Edit only. No shell, Git, Python, JVM, Gradle,
server, client, network or other agent. Failures: a brace-glob Grep count
returned no files (no effect); ADR-066 was not under `docs/adr`, found under
`docs/decisions`. CONTRACT-01 was trimmed by Edit after its first write.

Read deviations: main-checkout AGENTS.md was read as headings plus lines
233-328; lines 1-232 were read only from this worktree's copy, which lacks
sections 9-12. Material, config, registry and DataGen sources and the readiness
report were inspected by targeted ranges/Grep, not end to end.
`V180LanguageProvider`, `V180StationLightLanguage`, `ModCreativeTabs` and
GameTest registration were identified by file name only.

## Unrun checks

File byte sizes (limits 8/5/5 KiB) and LF endings were not measured; the
author's rough count is 7.7, 4.1 and 4.7 KiB. Not confirmed: `ALUMINUM.hasOwnOre()`,
`TorchBlock`/`WallTorchBlock` constructor visibility, `template_torch*`
parents, vacuum GameTest fixture reuse, any other dust source. No build,
DataGen, GameTest, server or client check exists for this draft.

## Risks, dependencies, Gates

1. **Blocker**: asset plan rows 226/229 say IMPORT for the legacy textures;
   NEW art needs the ADR-062 revision/pin (ADR-066:866-870) first.
2. **Blocker**: with `classic.smallPlatePress=false` no dust route was found;
   Root decides whether thermite may become unobtainable.
3. **Decision**: `forge:dusts` umbrella membership lives in shared
   `V180MaterialData.Items`.
4. Until the unlit-torch leaf lands, vanilla torches still burn in vacuum, so
   this torch is a costlier ordinary light. Closing ledger
   `block:thermiteTorch` ("light that works in vacuum") now may overstate
   parity; `item:thermite` and `material:Thermite` fit this leaf more cleanly.
5. The frozen IDs constrain the later unlit leaf: thermite torches must stay
   outside `combustion_torches` and `torch_pairs`.
6. Assignment CSV `source_commit` 10eb561a is historical, not this base.
No Gate is advanced; G0-G9, A0/A1/S1/S2/V1 evidence and ADR-018 scheduling
remain. This draft is not a contract freeze.

## Next current-version prerequisite

Root commits these returns unchanged, then a different agent reviews the
actual diff. After that, Root decides recipe form, switch non-gating,
press-disabled acquisition, the asset-plan revision, umbrella tag and the
dependency claim. Only then can Root register an implementation TASK with a
base SHA, worktree, exact files and allowed commands.

## Release of interests

No process, lock or session is held. Read interest in the main checkout,
worktree and readiness report is released. Write interest covered only the
three files above and is released with this return. No Git command was run:
HEAD, index, branch, stash and worktree are untouched. The evidence leaf
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18a-thermite-contract-claude-author-20261008-01`
was not written; Root owns its capture.
