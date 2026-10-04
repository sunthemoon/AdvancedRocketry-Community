# Small Plate Press acquisition verification

Date:2026-10-04. Status:DEVELOPMENT_VERIFIED (acquisition slice); no version Gate
or whole survival graph approval. The additive contract is accepted as
[ADR-063 revision7](../../decisions/ADR-063-MATERIALS-ORES-AND-PLANETARY-SURFACES.md)
after [independent contract review](reviews/PRESS-ACQUISITION-CONTRACT-REVIEW-01.md).
It restores one vanilla piston above three iron ingots, producing one press;
no steel, motor or powered machine is required. Existing processing/protection
and every prior generated recipe remain unchanged.

## Exact source and correction

One changed provider (`V180MaterialRecipes`,six added lines),two new Java test
classes and two new generated resources. Source closure has2,944 inputs, SHA256
`ab29c2508b97101e1ec9f1afa235012062ee882d12d03bb8178646cf05de33c4`.
The other2,939 prior inputs,all1,164 prior generated files across declared
generated prefixes (769 v1.8) and28 existing API source files stay byte-exact.
No upstream JSON/asset is imported; pinned MIT recipe facts and transformation
are recorded in [development provenance](../../provenance/v1.8.0-development-metadata.md).

[Frozen source01](press-acquisition-source-01.zip):4,733,110 bytes/2,969 entries,
SHA256 `ac830b9db0ad340943e6b87f6849e15f96eb7de888c87550e5d1742d76a87928`;
manifest `e636d20b89156d7ab58bb1d4277a8f5073cfea5ad00e342aa4dd851dad0afdab`.
The original generated-diff artifact has a reproduced Low:unterminated JSON joins
the following diff header. Original bytes/check128 remain immutable. Separately
versioned [patch02](press-source-patch-02.zip) uses Git's no-newline marker:
15,432 bytes/35 entries,SHA256
`1f5f4d75ef1baba75348ad8df19b75890f104947029707d76ea0112106de284f`;
manifest `2b51733fc36a65ad63942f097b6dda8f14d41a748528cd9f94b2a3d372601033`.
Corrected patch SHA `fd8a6a610901fba20cf759840010cabb2754cca6d14f88d7bc98794f64ade650`.
Fresh check0/apply0 reproduces all five exact postimages without changing source.
The [independent final source review](reviews/PRESS-ACQUISITION-SOURCE-REVIEW-01.md)
has0 unresolved C/H/M/L and verifies the corrected artifact by exact replay.

## Actual commands and results

All Java commands use Java17,offline,no-daemon,max-workers2,Xmx2G. Raw command,
exit,log and XML identities are frozen separately; failed artifacts are retained.

| Check | Actual result |
|---|---|
| New four resource tests before provider correction | red01 exit1;4 tests/4 failures,missing JSON/unlock/singleton |
| `runData` after correction | data01 exit0;only two resources written |
| Repeat `runData test --tests '*SmallPlatePressCraftingResourcesTest'` | green01 exit0;4 tests/1 suite,0 failure/error/skip;written0 |
| Root `clean build test runData` | build12 exit0,3m2s;1,642 JUnit/314 suites,0 failure/error/skip;771 generated files,written0 |
| Independent scoped compile/four-unit replay | exit0,33.871s;4 tests/1 suite,0 failure/error/skip;all2,944 inputs unchanged |
| Independent full `runGameTestServer` | exit0,247.244s;all460 required tests pass;source unchanged |
| Root full `runGameTestServer`,GT06 | Gradle0/Minecraft0,3m56s;all460 required tests pass in both captured logs |
| Root package/art comparisons | all771 generated resources and67 unchanged screened PNGs match both non-API JARs;API equals candidate11 |
| Packaged artifact validator | attempt13 exit1 used default0.0.2 version;separate14 with explicit1.20.1-1.8.0-dev exits0/3,267 entries |

Build12 main SHA256
`c3f3cfcf92b6290d9322e43df215da9d22983c8b30375d9ca3d6ea7643c5b9c1`;
sources `90ff5debc51f989a91ff106092993a21975da3903593fb437efc924b91ce515e`;
API `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da`.
The first Root post-build collector call split a PowerShell string argument into
characters and exits1; separately named explicit-action retry exits0. This
does not change source,XML,test assertions,timing or any budget.

## Evidence and remaining version work

[Frozen Root candidate12](../v1.8.0-c16a-integration/ROOT-INTEGRATION-12.zip):
23,673,654 bytes/6,310 entries,SHA256
`928c8fa585c00fdf7b38ad77058fdf2ee3f78ba825faea1d716e64c32e1df2b5`;
manifest `75a75aa900ff5ee51f3b3975ef4333ad197bffcd060dd7a4f1c5d456cf44055a`.
CRC,safe unique names and every entry SHA/size are checked. The
[independent complete result audit](../v1.8.0-c16a-integration/reviews/INTEGRATION12-EVIDENCE-AUDIT-01.md)
has no observed evidence inconsistency within its bounded scope. Actual crafting tests
exercise the registered recipe manager,normalized
offset/mirroring,missing/wrong/extra ingredients and input metadata conservation.
There is no clean-log claim:failure-injection migration/save-denial errors remain
visible. No candidate12 Pump/Tank native run is claimed or borrowed from11.
The press adds no new saved state,network or image asset. Real-client/JEI,
steel/controller/hatch/component acquisition,the complete C16d graph and every
version Required Gate remain open. No new ledger row is closed by this correction.
