# C16a integrated candidate06 checkpoint

Status: IN_PROGRESS. Version v1.8.0 is not complete. No release/Gate, commit,
tag or push. Root is the sole central writer; author/reviewer source identities
and scopes are recorded in the paired task packets.

## Portable root evidence

[ROOT-INTEGRATION-07.zip](ROOT-INTEGRATION-07.zip) is packaging revision07 of
the unchanged candidate06 evidence, not a new runtime candidate. SHA256
`7ba0db38ff5ba814a5496d100ceadfbff05c32d20332af4549d69f4be833ab4f`,
54,317,074 bytes /7,439 entries; manifest
`b192846572c75fc9af292e3a12a7604ac342e299865929fb6d556699a494bd17`.
Full CRC, safe unique names and every manifest hash/size pass. Prior failed
helper/native/build/parser runs are retained. Full runtimes/worlds/libraries,
official reference client JARs and the user's untracked document bundle are
excluded. Native input JARs are the pinned community host and compatibility fixture.

The ZIP contains exact before/current sources, raw XML, all three frozen JARs,
Gradle/runtime logs, native launch/commands/receipts/chunks/inventories and helper
scripts. Source manifest has2,935 entries, SHA256
`ecf1864fc8709a3656b26f505bad90d5848130bf7ee103526794f9623275e271`.
The source postcheck has zero mismatches. Future revision04 is not in this packet.

## Actual commands and results

| Command/check | Actual result |
|---|---|
| Java17 `gradlew --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2G runData` | exit0,35s;17 tag-recipe changes |
| Same flags `clean build runData` | exit0,3m19s;1,622 JUnit /312 suites,0 failed/error/skip; DataGen written0 |
| Same flags `runGameTestServer` | Gradle1/Minecraft12,3m55s;12 required failures, raw logs retained |
| Package compare/validator | exit0; all766 generated files match main/sources;67 screened PNGs retain exact bytes |
| Pump Python focused tests |25/0,exit0 |
| Pump native01 | launcher/artifact binding failure before world/native execution |
| Pump native02 | exit0,84.8s;four actual phases/two clean restarts, exact typed banks,100FE to1,000mB lava |
| Tank native04 | exit0,146.1s;seven phases, capacity/BE/Item restarts and whole-chunk refusal |
| Strict repository03 |45 passes,exit0 |
| Strict ledger03 |exit0;189 PLANNED /154 REVIEW |
| Ledger closure02 |exit1,correctly refuses those outstanding rows/assets |
| Exploratory broad Python01 |exit1;1,051 tests,10 failures,117 errors,4 skipped; not a frozen acceptance run |

Main SHA256 `58a5ab97a892c4f54f9803d64b6f407eb5c533103be0a758ab64b11e922d3bb0`;
sources `eb29529bc393d42ff0875b27b70e4d2ffca483e384107136d7438b5be5853a57`;
API `252463ff81bdfc1d48c9e6c3ea397e7ca1bb00f4ebf9e5b804405bd27fe6b2eb`.
No wholly clean GT log or native arbitrary-crash atomicity is claimed. Two
unknown-owned older v1.5 Java processes remain untouched; host receipts record
them, so an empty host is not claimed. All known v1.8 Java is exclusive during
root GT/native, otherwise at most two bounded scoped jobs.

## Review history and remaining work

[Portable index](reviews/portable-archive-index-05.zip), SHA256
`6524c3fb2b8adce1e5f82762093b8d359099ce3c165be317c03295b36ff000c5`,
binds11 immutable author/reviewer archives. Its original first link-check
failure remains; a separate Signature wrapper supplies proven companion
closure, without altering original entries/reports. Root additionally verifies
and copies Bank quantity/API qualification and Signature guard packets.

[Bank](../v1.8.0-c16a-hatch-core/VERIFICATION.md) is admitted model-only.
[Signature source](../v1.8.0-c16a-recipe-signatures/reviews/SOURCE-REVIEW-03.md)
and [Pump harness](../v1.8.0-c16a-pump/reviews/NATIVE-HARNESS-REVIEW-02.md)
retain independent scopes. The12 fullGT failures require fixture and runtime
classification corrections plus independent review/replay. Pump/Tank native
audit is pending; clean restart is not crash proof. Native Signature jobs,
menus, physical hatch/controller/writer, all-tier formation, C16b-d/C17/C18/C19,
real client/GPU/multiplayer/performance and all Required Gates remain open.

Historical guard build1,548/GT441/Tank03 facts belong to the previous edc06a…
JAR, not blanket acceptance of this candidate. Generated assets are automated
screened original bytes, not human visual approval. The current branch remains
dirty; no version status is advanced by this checkpoint.

## Subsequent reviewed evidence

The [independent native audit](reviews/NATIVE-AUDIT-02.md) is now complete,
with no new findings. It re-extracts the stopped Pump02/Tank04 disk data and
checks the pinned source/JAR/historical identities, without native replay.
The candidate06 ZIP's earlier pending note remains frozen historical context.

The [Root two-fixture review](../v1.8.0-c16a-recipe-signatures/reviews/ROOT-FIXTURE-REVIEW-01.md)
finds no introduced defect but preserves the existing current-orphan runtime
Medium. Root applies/author-seeds the exact two test postimages, not a runtime
fix. The author revision04's actual red/green and new current-clock coverage
remain separate; no conclusion is inferred from source existence.

Repository strict08 also passes45 checks. Packaging retains failed rounds:
archive05 rejected the wrapper's nested manifest; corrective07 verifies all
17 packets, including already copied immutable files. Archive08 assumed a dict
manifest and failed before any copy; corrective09 handles the actual list and
copies the two new native/fixture packets after full checks. Root packet06's
first helper failed double-prefixing Windows extended paths before creating
a ZIP; packet07 fixes only path handling and retains the failed log. An atomic
documentation patch failed on an obsolete context line without writing either
addition; a later separate no-op context patch also failed after the two
independent additions had succeeded. These helper errors are not Gradle/native
test results or a relaxation of source assertions/budgets.

## Coherent candidate10 — 2026-10-04

The [independent candidate10 evidence audit](reviews/INTEGRATION10-EVIDENCE-AUDIT-01.md)
contains the exact source companion, XML and raw logs. Its unresolved Medium
is the observed fullGT04 failure, not a finding on the auditor's own Signature
source. It verifies2940 input identities, eight independently reviewed central
postimages, actual1637 JUnit /313 suites with zero failure/error/skip, all769
generated resource bytes and67 screened PNG identities in both non-API JARs.

Java17 `clean build test runData` exits0 in3m29s; cached DataGen written0.
The successful build is not the pre-R5 build09, whose separate orchestration
failure/log remains retained. FullGT04 exits1 in3m55s (Minecraft1),459 complete
with one required rolling prepared-journal recovery failure. Pump03/Tank05
commands are prepared only; no execution is borrowed from candidate06.

Candidate10 main SHA
`0801cf5cd345c41dab6d33a6562e232f1a9fdece8e63fbda425fe7d3e7ac148c`;
sources `2eb8aa2835b38b46bdded518651b60cf54f75d0815a84ebf33f82287544d046a`;
API `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da`.
The API whole-JAR date/notice change is explicit; no old API SHA equality is
claimed. All2,940 captured inputs still match after build. A new separately
reviewed correction and fresh coherent regression/native execution are required.

The [historical documentation rehosting review](reviews/DOC-REHOSTING-REVIEW-01.md)
also independently verifies unchanged raw originals and619 Markdown files
/2,840 links. The earlier seven-link failure remains preserved. This narrow
documentation admission is not source/native/contract or Gate approval.

## Coherent candidate11 — 2026-10-04

The [independent two-test-file correction](../v1.8.0-c16a-recipe-signatures/reviews/SOURCE-REVIEW-06.md)
replaces only the marked-current rolling fixture's historical item recipe with
the actual shipped tag recipe, and adds its signature-distinction regression.
Production refusal, historical legacy fixtures, all resource/journal assertions
and timings are unchanged. Independent replay passes120 scoped units and459
required GameTests with no unresolved findings. Root applies the exact two
postimages; the other2,938 captured inputs remain unchanged.

| Actual Root command/check | Result |
|---|---|
| Java17 `gradlew.bat clean build test runData --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2G` | exit0,2m58s;1,638 JUnit /313 suites,0 failure/error/skip; DataGen written0 |
| Same flags `runGameTestServer`, fullGT05 | Gradle0/Minecraft0,3m56s;all459 required tests passed in both captured logs |
| Positional packaged-artifact validator12 | exit0;3,263 main entries |
| Generated/artifact comparisons | all769 generated files and67 screened original PNGs match both non-API JARs |
| `run_v180_pump_smoke.py`, Pump04 | exit0,84.071956s;four phases/two ordinary clean same-world restarts |
| `run_v180_tank_smoke.py`, Tank06 | exit0,142.353304s;seven phases, bank/Item restarts, reduced capacity and whole-chunk refusal |
| Post-native identity check | all2,940 inputs unchanged;all three frozen artifact identities unchanged |

Source manifest SHA256
`7e58060fa42017170d08e10d9596623d29a82495b94052670ab951367bd8f9d4`.
Main5,347,958 bytes:
`ea311ed02b4d1531e902e4031ad5e1174466e6378d3a6cb15e6679ca039e1e2a`;
sources `0bb22470dd3ddf7fe95ea0d648489ea751b3265370b2df6cac7d48c61a6b8904`;
API `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da`.
The API matches candidate10, not the older candidate06 API.

[Frozen Root source/result packet11](ROOT-INTEGRATION-11.zip):37,553,082 bytes,
7,160 entries, SHA256
`404d44c1df772e6a318a1d4c45ddd054a5df11dd32d8345953982867811bae65`;
manifest `e59990b63e69f6301b1a19190ff633c7d438d3dc0163608086c19b57a5c9432e`.
CRC, safe unique paths and every entry hash/size are verified. It contains exact
candidate10/11 source closures, XML/JARs, failed and corrected command logs,
fresh native stopped chunks/receipts, and pinned old-community/fixture JARs.
It excludes full servers/worlds/libraries, official client JARs and the foreign
development-document bundle. The initial build11 XML directory-copy failure
is retained; corrective file-by-file copying verifies313 actual XML identities
and testcase totals. This was not a Gradle/test failure or relaxed assertion.

The successful GT05 still has45 ERROR logger lines in16 groups; no wholly
clean-log or blanket error exemption is claimed. Candidate10's459/1 failure and
candidate06's456/12 failures remain immutable historical results. Prepared
Pump03/Tank05 commands were never run. These clean-stop native slices do not
prove arbitrary-crash atomicity, native Signature jobs, physical hatches, real
clients/GPU/multiplayer, reference-hardware performance or any Required Gate.

The [independent candidate11 actual-result audit](reviews/INTEGRATION11-EVIDENCE-AUDIT-01.md)
now verifies these observations with no new evidence inconsistency, including
independent decoding of the stopped Pump04/Tank06 bytes. This is an evidence
audit, not a Java/native replay or source review of the auditor's own work.

## Coherent candidate12 — 2026-10-04

The [independently reviewed acquisition correction](../v1.8.0-c15a-materials/reviews/PRESS-ACQUISITION-SOURCE-REVIEW-01.md)
adds only the missing press crafting/unlock and its resource/registered crafting
tests:six provider lines,four new files,the other2,939 prior inputs unchanged.
All1,164 old generated resources remain byte-exact (769 in v1.8). Independent
source replay passes4 scoped units/all460 required GT with0 unresolved findings.
The original malformed diff check128 and separate corrected five-postimage
check/apply0 are preserved. No production Bank/Signature/state/network changes.

Root Java17 `clean build test runData --offline --no-daemon --max-workers=2
-Dorg.gradle.jvmargs=-Xmx2G` exits0 in3m2s:1,642 JUnit/314 suites,0 F/E/S;
771 generated resources,written0. FullGT06 with the same flags exits0 in3m56s
(Minecraft0),all460 required tests pass in both captured logs. Actual XML
testcase totals equal declared counts. Both non-API packages contain all771
generated files and67 byte-identical screened PNGs;API equals candidate11.
Positional validator14 with explicit version exits0/3,267 main entries.
Validator13 omitted the version and fails three default0.0.2 identity checks;
first post-build collector invocation exits1 on PowerShell argument splitting.
Separate explicit-option/action retries pass without changing source/checks.

Source manifest
`ab29c2508b97101e1ec9f1afa235012062ee882d12d03bb8178646cf05de33c4`;
main `c3f3cfcf92b6290d9322e43df215da9d22983c8b30375d9ca3d6ea7643c5b9c1`;
sources `90ff5debc51f989a91ff106092993a21975da3903593fb437efc924b91ce515e`;
API `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da`.
All2,944 source inputs still match after build,GT,freeze and subsequent contract
documentation updates. Root's briefly inserted ADR-064 cross-reference is
removed;the accepted r4 source identity remains unchanged.

[Frozen source/build/GT/package packet12](ROOT-INTEGRATION-12.zip):23,673,654
bytes/6,310 entries,SHA256
`928c8fa585c00fdf7b38ad77058fdf2ee3f78ba825faea1d716e64c32e1df2b5`;
manifest `75a75aa900ff5ee51f3b3975ef4333ad197bffcd060dd7a4f1c5d456cf44055a`.
CRC,safe unique names and every entry hash/size are verified. Packet contains
exact11/12 source and package comparisons,fresh12 XML/GT and failed/corrected
command artifacts;no full runtime/world/official client/private document bundle.
The45 ERROR logger lines remain visible;no clean-log or blanket exemption.
Candidate12 does not claim a fresh Pump/Tank or Signature native run;candidate11
native results remain scoped to its exact ea311… artifact. Full survival,
physical adapters,real-client/multiplayer/reference-performance and all Gates
remain open. The [independent complete candidate12 evidence audit](reviews/INTEGRATION12-EVIDENCE-AUDIT-01.md)
has no observed inconsistency in its bounded source/XML/package/command/archive
scope;it does not replay Java/native or admit the auditor's later model code.

## Candidate13/14 source guard and failed Signature native — 2026-10-04

The separately reviewed Signature S1 revision02 adds four exact source/test
files and the Root-owned listener registration. Candidate13 passes1,652 JUnit/
315 suites and DataGen written0;its [build-only packet](ROOT-BUILD-13.zip) is
SHA90fb263e… . No GT/native13 result is claimed. The subsequent two-file
[non-mutating NBT admission correction](../v1.8.0-c16a-save-guard/BOUNDED-NBT-VERIFICATION-01.md)
preserves the original failed probe and independently passes46/7/0FES. It does
not establish universal native emission or durable raw-tag preservation.

Candidate14 Java17 required build/test/DataGen exits0 in3m0s:1,659 JUnit/
316 suites/0FES,771 resources/written0. FullGT07 exits0 in4m20s,all460 required
tests pass;45 ERROR logger lines remain,not a blanket exemption. Exact2,949
source inputs SHA256
`df4ee13e6ecbaf3f7206ad5b8e7c285b89ba86f73a6fe052c722320adf6456c5`.
Main `8e3cf31e0057252b4c794e1a989b959c0715335fa51050e59545c34f0a255f2a`;
sources `e028b3aa3679e0d24a197866494b1984e527fde450109370cb355f234351525b`;
API `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da`.
All771 generated files match both non-API packages. Focused Python69 checks,
strict ledger,planning,approved provenance,machine resources,common/client,
artifact and diff-check exit0. Boundary-safe stock validator45 passes and
expanded Markdown646 files/2,956 links has0 errors. Ledger closure/diff-exit
remain1,not passes;the excluded Git-untracked foreign documentation tree is
neither traversed nor altered. Stock assertions remain unchanged.

[Frozen packet14](ROOT-INTEGRATION-14.zip) is12,141,439 bytes/3,345 entries,
SHA `de2a59a294a5acd93add5eea170130252191a9b5c22cdc43bb259f9fdf74b652`,
manifest `27df93a614969d02e2992ab885809ddb013edfac8f435e251c43974befa47953`.
CRC,safe unique paths,every manifest hash/size are verified. It includes exact
source/JAR/XML/commands/GT/checks and references the independent failures.

Actual Signature native14-02 **fails** after two historical clean Rolling
hosts and one upgrade abort,before current seeding/save/restarts. The wrong CLI
enum exit2 precedes Java and is retained separately. The independent
[failed-checkpoint audit](../v1.8.0-c16a-recipe-signatures/reviews/S1-NATIVE-FAILURE-REVIEW-01.md)
identifies1 Medium in the runtime refusal oracle;it does not report a production
defect. Synthetic stopped legacy PREPARED and unobserved elapsed FE/callback
qualification are explicit. No18-host/S1/durable-NBT/current completion or
Precision/Electrolyzer native pass is inferred. Source world135→135 unchanged
is a pinned Root postcheck,not independent re-enumeration or new native success.

## Candidate15 pure hatch values — 2026-10-04

Exactly25 new data-only files are integrated over2,949 unchanged candidate14
inputs. The [independent corrected review](../v1.8.0-c16a-hatches/reviews/VALUES-SOURCE-REVIEW-02.md)
passes55 tests/6 suites including its original4-case negative probe;the Root
source itself adds51 tests/5 suites. The original exact-capacity Medium/red
and packaging/helper failures are retained. There are no physical hatches,
guard-ticket/frame/native-resource/hash bridges or charged carrier in this delta.

Root Java17 `clean build test runData --offline --no-daemon --max-workers=2
-Dorg.gradle.jvmargs=-Xmx2G` exits0 in3m24s:1,710 JUnit/321 suites/0FES,
771 generated files/written0. Fresh file-by-file XML capture verifies declared
counts against actual testcase elements. FullGT08 with identical flags exits0
in4m11s (Minecraft0),all460 required tests pass in both retained logs. The45
ERROR logger lines remain visible;no clean-log or log waiver is claimed.

Source2974 SHA256
`22b1007e5f741f43f8644a667cdb83bfe10c67ded6f3ea50733fbcc678428fbe`;
main `5e0b790559f7035c49e2fe5942d85a33e37ab77f7563c33e590c3c85233ba37a`
(5,411,906 bytes/3,293 entries);
sources `0e3719bfc028c3225812ef95a75d777bf99114f8bc2f7d096ad028109333b110`
(2,607,357 bytes/2,652 entries);API remains7484de… (51,045 bytes/48 entries).
All771 generated resources match both non-API JARs. Scoped15 strict ledger,
common/client,69 focused Python,explicit-version artifact and diff-check exit0;
closure/diff-exit remain1. Full stock/provenance last observed14 is not relabelled15.

[Frozen packet15](ROOT-INTEGRATION-15.zip) is12,211,462 bytes/3,354 entries,
SHA `10b0c3a19cc443a18b85725c041f8b1a40e050a9f97782f854ab39c883ccb693`,
manifest `939f617a4f6f20187ac262005229db12f38837b6f5e5b9e540b60a31ab5338d7`;
all CRC/entry identities are verified before later fixture changes. Archive helper26
fails SyntaxError before copying;separate27 verifies/copies the exact author and
review packets. This is not a source/test failure or relaxed check. Full source,
JARs,321 XML,build/GT/scoped commands and failure artifacts are portable;no full
world/runtime/libraries/official client/private document bundle is included.

The owner now [selects retained charge in the dropped plug Item/restored placement](../v1.8.0-c16a-hatches/OWNER-POWER-REMOVAL-01.md).
Its schema/lifecycle/removal writer is not implemented. Guard/carrier and hash
technical refinements are independent pending proposals. No fresh native Pump,
Tank or Signature15 run,physical API,all-root codec,real lathe,all-tier formation,
survival completion,real clients/performance/remaining C16b-C19 or Gate is claimed.
The ledger remains186 PLANNED/154 REVIEW;version stays IN_PROGRESS/IMPLEMENTING.

### Subsequent candidate15 static supplement

After freezing packet15,Root repeats the full bounded static checks against
the still-exact2,974 inputs. The boundary-safe unchanged stock validator exits0
in310.839s,45 pass/0 pending/warnings/failures. Approved provenance exits0 in
28.318s;planning/machine resources exit0. Expanded Markdown652 files/2,991
links has0 errors. These actual15 results supplement,not rewrite,packet15's
earlier last-observed14 statement. No Java/native or version Gate is added.

[Static supplement15-01](ROOT-STATIC-15-01.zip) is199,512 bytes/26 entries,
SHA `642a3ab34327da8572edaff39f8b3183e6b15d9535138fc9e04ae9cdd5f1e2aa`,
manifest `4aee358758a6dcb53b3e44629270145cb946960772c891dcbeea06a87b24d727`.
CRC,unique paths,all hashes and unchanged input identities are verified. Source
and artifact cohorts remain in the referenced immutable Root15 packet.

## Signature fixture-only cohort16 — 2026-10-04

The [independent revision04 disposition](../v1.8.0-c16a-recipe-signatures/reviews/S1-RUNTIME-REVIEW-04.md)
has0C/H/M/L in two Python files. Root integrates exact postimages without
changing Java/generated/API or any other2,972 candidate15 inputs. Source2974
SHA `4bf252b380b8ca57dc7396cbc832a9ef4414eadc13b80bc4d06936d5372bbdfd`.
Scoped16 runs80 focused Python tests/0F/E;strict ledger,common/client,artifact15
and diff-check exit0. Closure/diff-exit remain1. There is no newly run Java16
build/GT/full-static claim:those retained results are explicitly candidate15.
Fresh native execution uses JAR15/main5e0b7905… plus Python16,not failed14's
artifact or outcomes. Stopped-byte/resource/restart audit remains separate.

### Actual native16-01 failure and preserved partial scope

Using built JAR15 and Python16,Rolling completes all6 clean phases in129.799s;
Precision completes3 clean phases in63.772s,then stops at its injected current
cut before patch write/restart1.Record8,216 exceeds original allocation8,192.
Whole runner exits1;no Precision current restarts or Electrolyzer launch.
Source2,974 inputs and historical sourceworld135→135 files remain exact.
All known v1.8 Java processes have ended;unowned historical v1.5 processes are
left untouched.No empty-host/performance or all-adapter/S1 result is inferred.

[Failure packet16-01](../v1.8.0-c16a-recipe-signatures/SIGNATURE-NATIVE-16-01-FAIL.zip):
90,634,828 bytes/353 entries,SHA
`5d064201ae9447269bbf7d55f27f512375c1f829f616a42cd496fc57eeba1910`;
manifest `b28ab25f0b4d03a3c12efc42dfb9ccf74e408d20ad8389c56853769db48014ad`.
Safe unique paths,CRC and every manifested byte/hash are verified.It contains
actual phase/cut logs,stopped bytes,source/checks/input identities and the
explicit partial failure scope,not a live runtime or official JAR.

Root's separate stopped-byte replay measures level9 record8,070 and unchanged
typed root/allocation/header/outside bytes.The proposed fixture-only correction
has41 focused tests green after retained final red1F;39 original methods are
unchanged.Independent actual-source review and a fresh3-adapter run remain
required.The Root harness/tests remain r4 until that review qualifies.

The separately reviewed menu contract is adopted as ADR064 revision5 section14,
not implemented transport/client evidence.A Temp-only source task now proceeds;
full runtime,raw-native durability,physical hatches/C16b-C19 and G0-G9 remain open.
## Source17 stopped encoding and actual native17

Independent [review05](../v1.8.0-c16a-recipe-signatures/reviews/S1-ENCODING-REVIEW-05.md)
qualifies only two Python fixture postimages;original rejected patch and separate
LF correction remain frozen.Root source17 exact manifest is
`d962518afda1803ed2b02a5d29d95ff1ec16445ae27e74f23c332d3f3ce2c664`/2,974
inputs;2,971 are unchanged from16,other drift is the separately adopted ADR064.
No Java/generated/API change from15 and no new Java build is claimed.

Root focused Python82 passes;strict ledger,common/client,explicit-version
artifact15 and diff-check exit0;closure1 and dirty diff1 remain failures.
`python -B run_signature_native17_01.py` with fixed original phase/bounds and
fresh copied worlds exits0:Rolling130.092724s,Precision124.648396s,
Electrolyzer129.104404s,six clean hosts each.Source world135/135 exact.
[Frozen native17](../v1.8.0-c16a-recipe-signatures/SIGNATURE-NATIVE-17-01.zip):
168,634,745 bytes/459 entries,SHA
`87d2955ef623a2e82edfb137cd6e194e9c35b1d315191b2be85d698c7b9ad251`,
manifest `7bce4bc472b866ab6d7889056e7a7a4f2beafbb038c3b3e69550721cb84daa1f`.
Root validates all bytes/CRC/unique entries.Independent actual-result audit
remains pending.Native16 remains failed,stopped cuts are injected,not genuine
crash callback captures,and unsafe native tags/menu/client/Gates remain open.

## Source18 menu transport and Forge registry observation

Root integrates the independently reviewed28 author files and a separately
reviewed one-file N1 GameTest. Source18 has2,986 pinned inputs,manifest
`3a9c366f879f220ad4e7411aeaea01e210b518f231d2ff761a68e9676eae234f`.
First DataGen adds exactly7 labels per language without changing old entries.
Java17/offline/no-daemon/workers2/heap2G clean build/test/repeated DataGen exits0
in196.380462s:1,722 JUnit/327 suites/0FES,771 generated resources,written0.
Main JAR `d06d73644d88bb2cae3854e7e39800f583e83aeadc54ec682956ea60ff27238f`;
API exact15. Both non-API artifacts match all771 generated resources.

FullGT09 exits0 in239.194333s,464 required tests pass. The actual
`machine_menu_channel` batch observes the registered Forge map and both native
validation directions without modifying the registry. Its16 deliberate refusal
ERROR lines are attributed between exact batch boundaries;total log ERROR61 is
retained,not a clean-log claim. Plain-JUnit registration32/1F remains failed.
Scoped18 strict ledger/imports/Python82/artifact/diff-check exit0;closure1 and
dirty-tree diff1 remain failed. Full stock/provenance last actual run remains15.

[Root18 packet](ROOT-INTEGRATION-18.zip):12,419,585 bytes/3,375 entries,SHA
`e001ff7f8990300839c66927dd5d3976c80226cda905f938f41f9ca0e6e4c82f`,
manifest `a0743db889293757d9a6dff9a081ff7e4a0bb5e7bf22bd37801729640706e8b5`.
Root verifies every CRC/hash/input/artifact/XML. Actual peers/client menus/V1/V2,
current18 dedicated save/restart,unsafe native tags and all version Gates remain
open. K1 and same-host live-tag proposals are not part of this frozen cohort.

The earlier [native17 independent audit](../v1.8.0-c16a-recipe-signatures/reviews/S1-NATIVE-17-REVIEW-01.md)
now corroborates its exact18-phase JAR15 result,not source18. Protected native
payloads retain types/values,while36/60 compound-field-order comparisons differ;
that audit makes no serialized cross-phase identity claim. Native17's explicit
reload changes generation on an already-expanded table,not same-host additions.

## Source19 K1/live-tag integration with failed full static check

Exact four-file delta:two new pure key-order files plus two independently
reviewed Python fixture postimages.2,988 inputs,manifest
`36350bd1efe4a68edac31a5e536f028cf49fdc7eb9380c1d63e4235b82553390`.
Root clean build/test/DataGen exit0 in209.202260s,1,730 JUnit/328 suites/0FES,
written0;fullGT10 exit0 in254.555822s,464 cases and61 ERROR lines preserved.
Main `9b0682c1308bf7ad5ad84755a7f7e6eaed6b573b591535311395dc196d78895d`,
5,433,741 bytes/3,302 entries;API remains exact15. Scoped Python90/strict ledger/
imports/artifact/diff-check exit0,closure1/dirty diff1.

Full stock19 is **FAILED**,44pass/1 textual UI marker failure. The run also
uses the older boundary15 wrapper and hits its existing receipt assertion;
both its command and raw failure remain. All checker/assertion/budget bytes stay
unchanged. Provenance/planning/machine-resource checks exit0;this is not all
static/Gate pass. A separate one-file fallback source repair is under review.
[Failed-static Root19](ROOT-INTEGRATION-19-STATIC-FAIL.zip):12,315,352 bytes/
3,398 entries,SHA`199fbb9ec179ef5078b231994753f486c7e75a2ad73b0d5b9f1c5d61d512146a`,
manifest `0c57b9d87881575c72b243ada232be12fe8e9683fa16c7f2a2f7d08c62542e61`.

Fresh [native19](../v1.8.0-c16a-recipe-signatures/SIGNATURE-NATIVE-19-01.zip)
uses actual JAR19/Python19:three machines/six phases each,outer0,18 clean stop
receipts,source world135 files unchanged. Rolling/Precision have live original
reports before installation and exact new alternatives after reload;
Electrolyzer is only literal-recipe/generation control. Original bounds/allocation
and two injected stopped cuts per adapter remain. Packet168,378,287 bytes/435
entries,SHA`857fc96dd0833eb5753c86a8be79a8b97d0232d38e36269b1f35af8d0d966f55`,
manifest `efdacdb10e25bce462300939f219f9bb276420d1ba8c9005b13c5f62daa54328`.
Root CRC/all-hash verification is not independent actual-result audit/native
replay;that audit and full S1/peer/client/unsafe native-tag/Gate evidence remain open.

## Source20 repaired textual contract and current-artifact regression

The independently reviewed one-file expression factoring preserves keys/branch/
reads/layout/color and restores the unchanged textual UI marker. Source20 has
2,988 inputs,2,987 unchanged from19,manifest
`6ad7b69e413de857f71d3cb3497edc8ddabefddeb48d56f07285935e1a0c3c8e`.
Root clean build/test/DataGen0 in222.896869s,1,730 JUnit/328 suites/0FES,written0;
fullGT11/464/258.344811s and61 ERROR lines preserved. Scoped20 Python90/strict
ledger/imports/artifact/diff-check0;closure1/dirty diff1 remain failed.
Correct wrapper20/full stock45pass/0 pending/warnings/failures in367.932260s;
provenance/planning/machine-resource checks0. Source19 original failures remain.

Main5,433,734 bytes/3,302 entries,
`90ab8ba5379fd10e3c199645289d7dc43296028377f5cc65a0b84062d6e96468`;
sources2,617,913 bytes/2,660 entries,
`3e6447ae9e6232fd6a93b5989750866e4dc11c5e101de20ccd2ac15b131054f0`.
API exact15;both non-API artifacts match all771 v1.8 generated resources.
[Root20](ROOT-INTEGRATION-20.zip):12,310,298 bytes/3,403 entries,SHA
`3b6176f549d8bde523df1245fe7c4902e4633f093c40c6bce87db78fbf364bd2`,
manifest `3e949645da7787df3e5b708d8b465dfc3217db0640028463c0bb934f5847f363`.
Its [independent actual-evidence audit20](reviews/root20-evidence-audit-01.zip)
is complete,SHA
`a0f8d55ba9fced009c37c6e90a961ef7540b5db892ccc73ac9385174e8033b5f`,
25,533,017 bytes/6,864 entries,manifest
`f66196e0214c158c1593b4a8bd0a2371c91105b119c2e859f1accb46e7dbcd10`.
0C/H/M/L only in frozen evidence scope. Raw1,730/328 XML,2,988 source inputs,
five full-static actual commands,all1,209 Java source exports,771 generated
files and three artifact deltas are independently checked. Eight controls and
unchanged artifact-validator replay pass;no independent Java/GT/native/client
execution.61 ERROR lines retain measured contexts:recipe-save24,channel16,
Precision-save/readback13,satellite4,unbound serializers3,combustion1. These
are finite negative-fixture observations,not whole-log waiver/Gate inference.

Fresh [native20](../v1.8.0-c16a-recipe-signatures/SIGNATURE-NATIVE-20-01.zip) uses
actual JAR20/Python20,outer0/18 clean phases/sourceworld135→135 exact.
Rolling145.870554s,Precision152.221089s,Electrolyzer138.497599s;all original
limits/allocation kept,two stopped injected cuts per adapter. Live tag additions
only Rolling/Precision;Electrolyzer literal control. Packet168,889,947 bytes/436
entries,SHA`a1502f575fa7bc487197b9068eabafbf39bef9b046ef8b9362fb1977d574d77f`,
manifest `f68e971d196a0dcc99b11adce6881a41cd8ae17e400cd2c795f584c13937025c`.
Root CRC/SHA verification is distinct from the completed
[different-agent native20 actual-result audit](../v1.8.0-c16a-recipe-signatures/reviews/signature-native20-independent-01.zip),
SHA `b32b4b561d4d3bda5a244c24ef88b1fa73bfbabbb8eb6f75cffe4cf81ec01504`,
181,699,734 bytes/3,868 entries,manifest
`39f5ddc5f60f68becc26e0485a3d483c77e426f9dd8bda13f8254e261d26d955`.
No introduced C/H/M/L in the new cohort;18 phases/six injected-cut pairs/
60 retained typed comparisons/three-adapter resource and once-completion/
restart idempotence and17 independent controls pass.36 CompoundTag insertion-
order changes are disclosed;no raw compressed byte identity is asserted.
No old19 outcome is used as20 proof. Independent native replay,natural crash,
full S1/unsafe native tags/hostile execute/removal,actual peers/V1/V2/Gates remain open.

Root archive47/48 additionally verify and copy the ordinary-cut Guard inventory
review/context addendum and the two actual20 audits,without modifying originals.
New owner-mode contract amendment and native framing eligibility exploration
are separate current-version dependencies,not physical hatch/API/native admission.
