# v1.8.0 visual validation — C6 draft

**DRAFT; all C6 visual results PENDING.** Candidate commit
`c8fa16e16be87554f26c402546733df6555d15e6`, host JAR SHA-256
`9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.
[Quality levels](../../17-V1PLUS-QUALITY-BUDGETS.md) require V1 real GPU and V2
two real clients; V0 Xvfb/LLVMpipe is supplemental only. No client was run for
this draft. Earlier listening, artwork approval and client rounds retain their
own identities, not C6 observations.

## Environment sheets

| Field | Primary real-GPU machine | Second GPU category |
|---|---|---|
| OS / CPU / RAM / Java / JVM flags | PENDING | PENDING |
| GPU / vendor / driver / renderer | PENDING | PENDING |
| Resolution / window mode / effective GUI scale | PENDING | PENDING |
| Forge / JEI / packs/configuration hashes | PENDING | PENDING |
| Tester / UTC interval / client and server log hashes | PENDING | PENDING |
| Frame-time tool/version / CSV hash / baseline scene | PENDING | PENDING |

Use windowed 1280×720 effective scale 2/3 and native fullscreen scale 4 only
when offered, both en_us/zh_cn. A clamped request is not a scale-4 observation.
J14 additionally investigates HUD scales 1–6, recording unavailable cells.
The full primary pass uses Forge 47.4.10, with key 47.4.23 and JEI smokes.
Packet O9 proposes E06/H06/I07 plus a fifteen-minute A12 tour on the second
GPU; owner equipment/time confirmation is PENDING. E06 requires its two-GPU
matrix in full, not a software-renderer substitution.

## Candidate visual inventory and result slots

| Rows | Required observation | C6 actual / media / tester-date |
|---|---|---|
| A12 | 64/512/2,048-block rockets, single/multiple, first display/flight/transition; mean FPS, 1% low, first stall, memory/cache release | PENDING |
| A13/B06/C06/C07 | Core/destination/three machine screens, long names, errors and JEI agreement in display cells | PENDING |
| A14/E04–E07/E11 | Environment, skies/sun/stars/fog day/night/fluids/status effects, reload/rapid transition and stale-cache release | PENDING |
| F07/G06/H06 | Orbit/warp feedback, satellite/mission video, all endgame effects/screens without hidden critical feedback | PENDING |
| I06 | Every screen once per language, one pick per other family, every high-risk asset, producer audio and complete log review | PENDING |
| I07 | Every high-risk OBJ/multiblock/armour and corrected model: facings/UV/overlap/collision, motors/tanks/landing pad/laser gun in both hands | PENDING |
| I08/I09 | GUI scales/languages/resource reload; complex models; three reload time/heap captures and JEI opening/search latency | PENDING |
| J01/J06 | Bay/cargo labels/fluid/status/cursor and refresh after cargo changes on gated world | PENDING |
| J12/J13 | Owner judgment of code sky/black hole/Earth disc and retained fixed-midnight Moon | PENDING |
| J14/J15 | HUD extremes/anchors/mounts and audio seams/30–32 block boundaries/32-loop cap/pause/reload/logout | PENDING |
| J16/J18–J21 | Drills/jackhammer/railgun, off-world clock/compass, research/survey, trails/high-seat console and all retained client findings | PENDING |
| J23–J26 | Fuel/linker labels, server-matching fluid/amount/status, binding refusals, languages/scales/reload; disabled and gated contexts distinguished | PENDING |
| A15/J02–J04/J07/J14/J16–J19/J27–J29 | Second-client passenger/mount/effect/state comparisons; full V2 report separately | PENDING |
| I01 | Candidate base-to-endgame recording with visible build identity and route observations | PENDING |

Inventory sources: [W402](../../work/v1.8.0-root-queue/records/W402.md) and
[C6 refresh W423](../../work/v1.8.0-root-queue/records/W423.md). K13 classifies
857 presentation assets: 190 high-risk, 90 sampled picks, 529 represented family
members and 48 unreferenced textures. The latter are outside the campaign as
Low v1.9 cleanup under R546, not visually passed. Its 32 screen rows include
28 menu types and four non-menu presentations; the eleven without another
specific checklist row still receive I06/I08's every-screen pass. Hash the
C6 K13 manifest and map each selected asset to its actual scene and receipt.
Scene construction/navigation inputs K05/K16 remain PENDING preparation.

All eleven sound events and seventeen producer rows must be reconciled, not
only the ten previously accepted NEW sounds. J15 uses
`/playsound advancedrocketrycommunity:<id>`; `machine_large` is preview-only,
and `ui_select` is included. Earlier D5 audio acceptance fixes content choice;
it does not prove C6 trigger/distance/loop behaviour.

## Correctness, failures and media rules

No normal-run missing texture/model is acceptable. Check offsets, facings,
collisions, readable controls/errors, celestial/server agreement, stable seats,
reconnect consistency and absence of recurring OpenGL error storms. Record
all log matches, including findings; a screenshot alone does not prove clean logs.
I07's deliberately corrupt-resource pack is an isolated fallback test: retain
pack/override hashes and expected fallback versus actual, then remove the test
pack for the normal zero-missing-resource check. It is not permission to ship
corrupt resources or to ignore normal-run missing-model warnings.

Use the [campaign §6 layout](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md):
screenshots `<row>-<step>-<client>-<language>-<scale>.png`, videos
`<row>-<client>.mkv`; record actual dimensions, GPU/driver, build hash, UTC,
tester, expected/actual, SHA-256 and finding ID. Raw screenshots/video/CSV stay
outside Git; `videos-or-links.md` indexes them. Evidence references, actuals,
frame statistics, owner judgment and independent reviewer: **PENDING**.
