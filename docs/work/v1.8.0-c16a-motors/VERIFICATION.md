# C16a-07 motors and casing identity - development verification

Date: 2026-10-03. Branch `codex/v1.8.0-classic-content`, HEAD
`cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6` plus retained uncommitted work.
Accepted contract: ADR-064 revision 2, section 4. This is not a candidate,
release approval, full v1.8 completion or Required Gate pass.
Only `C16a-07a` is verified; `C16a-07b` all-tier family formation is planned.
The two primary motor/casing ledger rows remain PLANNED, so **189 PLANNED
rows /156 REVIEW assets** still remain. No unfinished unit is closed by this packet.

## Delivered behavior

Four stable block/item IDs: `motor`, `advanced_motor`, `enhanced_motor` and
`elite_motor`, all in `advancedrocketrycommunity`. Both block/item `motors`
tags contain all four tiers. They are inert crafting/structure parts, with
their own drops, pickaxe/stone-tool tags, models and twelve original faces.

The shapeless base recipe uses one copper coil, two steel plates, two iron
rods and one steel ingot. Each upgrade consumes exactly the preceding tier,
one coil and two plates of gold, titanium or iridium respectively. Plate
ingredients use the common Forge tags; coils retain this project's tag
identities. Shapeless preserves the accepted multiset without inventing a
grid arrangement absent from the contract.

The existing `endgame_casing` block/item IDs, properties, crafting, loot and
saved resources remain intact. A description-only block subclass uses the
unique `block.advancedrocketrycommunity.advanced_machine_casing` key, so the
Advanced Machine Casing label does not depend on historical language namespace
iteration. The old key remains in immutable historical resources. No new
casing ID or resource conversion is introduced.

Future classic controller patterns still need all-tier formation tests when
implemented. This slice does not deliver those patterns, controller-owned
hatches, tags/signature migration, tanks/pumps, C16b-d or C17-C19. Actual GPU
V1, multiplayer V2 and all inherited/current Required Gates remain separate.

## Actual commands and observations

Windows, Java 17.0.7, Forge 47.4.10, Minecraft 1.20.1; heavy jobs serialized.

| Check | Actual result |
|---|---|
| Worker `compileJava compileTestJava test --tests '*classiccomponent.*'` | Exit 0; 14 JUnit in 3 suites |
| Root `--offline runData test --tests '*classiccomponent.*'`, targeted-01 | Exit 0; 14 JUnit; current generated files |
| Root `--offline clean build runData runGameTestServer`, full-01 | Exit 0; 1,501 JUnit /283 suites and 413 required GT |
| Corrected root targeted-02 | Exit 0; 15 JUnit /4 suites |
| Corrected root full-02, same mandatory command | Exit 0, 6m10s; **1,502 JUnit /284 suites**, zero failure/error/skip; **413 required GT** |
| Frozen source/DataGen comparison | All 2,827 captured inputs unchanged; final manifest equals corrected frozen manifest |
| `python -B -m unittest tests.test_run_v180_motor_smoke -v` | Exit 0; 7 cases |
| Original-art screen against vanilla 1.12.2/1.20.1 | Exit 0; **61 CLEAR**, no SUSPECT/HIT; includes 12 new motor faces |
| Packaged art identity comparison | All 61 screened PNGs match corrected main/source JAR bytes |
| `python -B scripts/run_v180_motor_smoke.py ...`, native-01 and native-02 | Exit 0 each; actual v1.7 saved casing upgrade and two same-world restarts |
| Independent `--offline clean build runData test --tests '*classiccomponent.*' runGameTestServer` | Exit 0, 4m12s; 15 scoped JUnit and all 413 required GT; not a replay of all 1,502 JUnit |
| Independent corrected artifacts | Main, sources and API byte-identical to root JARs |
| Independent native evidence audit | Own bounded decoder checks all four raw saved chunks/receipts in native-02; **not a native server replay** |
| Strict repository, planning and bootstrap provenance validators | Exit 0 each; repository 45 passed, no pending/warnings/failures |
| Final accepted ledger, machine resources, common/client separation, packaged artifact | Exit 0 each; 653 units, still 189 PLANNED /156 REVIEW |
| Ledger `--closure` | Exit 1 on the actual remaining rows/assets |
| Explicit newly added Markdown links | Exit 0; 38 files /107 links, no errors |
| `git diff --check`; `git diff --exit-code` | Exit 0 /1; intentional uncommitted differences, not G2 candidate evidence |

JUnit covers tier/recipe catalog bounds, deterministic generated JSON and art,
and unique bilingual labels. Three new real Forge GameTests verify registration,
tags, tool/loot/BlockItem descriptions and exact accepted/rejected crafting
multisets, including missing/extra inputs, wrong preceding tiers, reverse slot
order and unchanged input stacks. Harness tests cover exact nonspanning block
states, duplicate/extra slots, IDs/counts/metadata and coordinate bounds.

## Corrections and persistence evidence

The first integration displayed the casing name by re-emitting the historical
key in the v1.8 namespace. Independent review found one Low issue: namespace
iteration can let the historical label win. The description-only subclass,
unique translations, one new JUnit test and stronger actual GameTest assertions
fix it without changing the saved ID. Both original and corrected source/JAR,
build and native packets are retained. No assertion, timeout or budget is weakened.

The native harness uses a fresh copy of the existing historical fixture world,
hash-pinned v1.7/current/adapter-test JARs and one fixed loaded chunk. Its v1.7
phase persists the casing block and a marked 16-count casing stack. The v1.8
phase adds the four exact motor blocks and marked item stacks of counts
1/4/8/16. Both restarts verify actual coordinates and complete item records;
palette presence alone is insufficient. Source world and input JAR hashes stay
unchanged. The adapter fixture preserves historical world compatibility.
No full-world crash, machine formation, GPU or multiplayer result is claimed.

The full-01 command's post-run log copy initially used a nonexistent guessed
directory; the actual `build/gametest/logs/latest.log` was then copied. The
Gradle exit stayed 0. The independent reviewer retained a long-path copy failure
and rebuilt from a fresh short Temp copy; no root file was reverted or deleted.
The first updated ledger check rejected evidence fields attached to the two
still-PLANNED rows. Those completion-only fields were removed; both failed
attempts remain in the validator packet and the unchanged strict validator then
passes. Closure still fails on 189 rows/156 assets; no rule is weakened.

## Immutable identity and evidence

Main JAR: **5,103,035 bytes**, SHA-256
`36665d1a80d393db8dda666378fc7741f62deed84738597d5ba008ae81de1f63`.
Sources: `20e5ae6c1855be41a132e95e173f508711125469cb00baea9494be6a70c9926c`.
API: `252463ff81bdfc1d48c9e6c3ea397e7ca1bb00f4ebf9e5b804405bd27fe6b2eb`.

The [root archive](root-evidence.zip) has 6,340 entries; CRC and every entry hash
pass in [archive audit](ARCHIVE-AUDIT.json). Archive SHA-256:
`88fd89b0800756ff24cd870532405b064d0e94df94512e9c2d1df60777520926`.
It preserves the actual pre-motor dirty source baseline, corrected final source,
all build/JUnit/native attempts, raw chunks, final/pre-fix JARs and worker packet.
It excludes official client JARs, runtime libraries, full worlds and the user's
protected untracked development-document bundle. Source hashes are in
[source identity](source-identity-final.json), artifact entries in
[artifact identity](artifacts-final.json), exact screened pixels in
[packaged art identity](packaged-art-identity.json), and automatic counts in
[JUnit identity](junit-final.json).

Independent review and final static validator receipts are archived separately
after the root runtime snapshot. Documentation/status updates do not replace
those source/JAR identities or upgrade historical evidence into Gate approval.
`git diff --exit-code` remains an intentional dirty-tree check, not clean-candidate
G2 evidence. Ledger closure must still fail until remaining content and assets
are genuinely delivered.

The [final independent review](reviews/REVIEW-FINAL.md) reports no remaining
Critical/High/Medium/Low in the technical sub-slice, but explicitly does not
close all-tier formation or the primary ledger units. Its
[raw report](reviews/REVIEW-FINAL.raw.txt) and
[evidence packet](reviews/review-evidence.zip) are retained unchanged.
The [validator packet](validator-evidence.zip) and
[validation receipt](validation.json) preserve actual commands/exits, the failed
first ledger attempts, strict results, artifact content manifest and both
status-only contract acceptance comparisons. [Checksums](checksums.txt) bind
the archived task packet and portable/raw reports.
