# Liquid IO board local functional qualification

2026-10-11. Committed-source local functional qualification, independent inert
source/generated/result reviews and Main local source fast-forward complete.
Source branch is normally pushed. Final record descendants, Main remote push
and packet seals are separate integration observations, not extra JVM sources.
No full C16d delivery, human asset approval or release Gate is accepted.

## Exact source and product scope

Tested source: 0dc14e4f175d365cf4216d9457b6e5e3bdfddbb8, direct child of authoring
b3166eda1707446c7bd8a9a0a5d576b254011465; authoring's sole parent is
7beccfcfb64640c7aa1f074f216be6482da04c22. Root adopts only the
[scoped leaf](LIQUID-IO-BOARD-ADOPTION-04.md), after its
[contract](LIQUID-IO-BOARD-CONTRACT-04.md) and preauthoring declaration.

The plain liquid_io_board Item stacks to 64, with one creative entry and no
custom NBT, capability, fuel, ticking, packet or world-state root. Crafting keeps
rvr/dwd/dpd: redstone x2, quartz x1, lapis x4, wooden slab x1 and iron plate x1,
output one; unlock is lapis OR existing recipe. Precision keeps ordered redstone,
lapis and steel plate x1 each, input channels 3/4 empty, output channel 0 one
board and output channel 1 empty,
200 ticks x10 FE = 2000 FE. Kernel/signature/journal/tag bounds are unchanged.
Existing IDs, stand-in circuits, hatches and other producer recipes are retained.

Product changes are ModItems/ModCreativeTabs, BootstrapDataGenerators,
V180LanguageProvider, new V180LiquidIoBoardData, V180LiquidIoBoardDataTest and
LiquidIoBoardGameTests; five scoped generated model/icon/recipe/unlock targets
and two language additions; dedicated provenance/preauthoring/contract/adoption/
local log, CHANGELOG and asset-plan row 246 only. The record descendant changes
only this qualification, the local log and CURRENT_VERSION/COMPLETION-PLAN.
Build, workflow, log checker/manifest, schema and machine services do not change.

Schema-2 NEW icon is an original 16x16 droplet PCB, 170 bytes, SHA-256
9e4fd59b320b74f49e4c32f60ca6742c0dd0a4db8b788dff8d35376edf64a2b9.
Provenance source_commit is authoring's parent; authoring_commit identifies
the new generator source. All seven paths remain PENDING_HUMAN_REVIEW.
Informational review.content_digest hashes compact sorted pre-review JSON plus
one LF; it is not reviewed_content_sha256 or repository human-review approval.

## Actual command cohort

Root alone executes; independent reviewers use inert parsing, not JVM replay.
Fixed caller argv and original/enriched receipt/custody/stream identities are
retained externally. The wrappers use Java 17.0.7, --no-daemon --max-workers=2,
an external finite-native.gradle and explicit -Dorg.gradle.jvmargs=-Xmx1G
-Xms128m -Dfile.encoding=UTF-8. Commands below identify tasks, not new run claims.

| Command | Original exit / seconds | Actual result |
| --- | --- | --- |
| Wrapper clean build | 0 /194.48437210003613 | test task executes; 2231 actual testcase children /385 XML; zero failure, error or skip |
| Wrapper runData data-01 | 0 /28.8787875000271 | seven target bytes equal seed/committed source; whole diff empty |
| Wrapper runData data-02 | 0 /25.545000299985986 | same seven target bytes and empty diff |
| Wrapper runGameTestServer | 0 /288.0156689999858 | 625 complete/required-pass summaries; six-test liquid batch; normal shutdown |
| python -B scripts/check_gametest_log.py copied-log --manifest scripts/gametest_expected_log.json | 0 /0.13687390001723543 | 62 ERROR /160 WARN /0 FATAL; 37 unchanged expectations |

Build retains 69 deprecation/removal compiler warnings and 29388 stderr bytes.
Both final DataGen runs are cached/idempotent (written: 0), not fresh no-cache
generations. Fresh seed at authoring in the new checkout exits 0 in 48.135929
seconds with old count 0/written 865 and all seven targets bound. Data/native/
checker stderr is empty. Exact liquid eight-case JUnit suite executes alongside
the existing eight-case Item IO suite. Repeated parameterized display labels
are counted as actual testcase children; no unsupported uniqueness claim is made.

Native fixtures check ordinary ItemStack round trips, installed selector
membership including lapis, crafting/slot/NBT refusals, ordered precision and
a formed assembler's bounded consumption/FE. Crafting queries/assembly do not
debit a real player's inventory; supplied steel/FE do not prove initial steel
or survival acquisition. Logs declare the six-test batch and overall required
pass, not individually named native-case verdicts. Physical classic hatches,
full graph, discovery policy and arbitrary restart/crash are not qualified.

All original command captures are complete, without overflow/errors and with
zero owned processes after retirement. Envelope: 900 original seconds plus
10 retirement seconds, 2 MiB per pipe, verified-before-resume 6 GiB Windows Job,
1 GiB Gradle and observed 256 MiB initial/2 GiB maximum native heap. Wrapper
heap is 64 MiB. This is neither lifecycle peak-memory/soak evidence nor proof
of every child's natural exit. One existing allowed server-overloaded WARN is
not a performance pass. The final native source world remains preserved.

## Reviews and retained failures

| Independent review | Bytes | SHA-256 |
| --- | ---: | --- |
| Source 03 | 23058 | 1646ed3ec63f87408eb2aefa8166e130ad3a4e64721d63651bb3d66d596d24d2 |
| Generated/provenance 11 | 19937 | 944287d268f9ce75672040c28a5e7e8029e3071eccec600616ced9f172bd0c88 |
| Final results 13 | 31150 | c74a680411273e3424761630777d2f2d639642fe42893361e020425fc9967c5e |
| Bounded Main helper 12 | 6678 | aba8a72b4f96de81a15e993168fb5052115ba4e9f2d3863c307b70b39a9926f8 |
| Four-record review 15 | 20292 | 3494cdf4519fd8afe10b8f920e81f2ba011b41434f77a2ddd72b65e41cc9c169 |

These exact reports are preserved unchanged. Final results review checks 69
stable inputs/actual XML/resources/strict event attribution with no new correction
finding. It excludes Main metadata helper/body review. Historical helper P2s
are addressed only by the separately reviewed capture_main_bounded.py; the
old capture_main.py is retained and not executed. Static review is not future
input certification or a filesystem lock.

The four-record review checks 46 input identities and 299 metadata-only local
links without an actionable record correction. It retains a Low: four read-only
cat-file size queries were outside its permitted command list, with one query
exiting 128 for the not-yet-committed new qualification path. Later authorization
does not cure that deviation. Root adopts the separately permitted record/evidence
checks, not the excluded queries, and does not call this a fully command-policy-
compliant review. This report association is added after all review interests
release; the association itself is not part of the reviewed four-file snapshot
or an additional runtime/Gate qualification.

Initial packet-09 seed evidence does not qualify final source. Its second seed
exits 0, but resource binder exits 1: five targets, absent language additions.
Stale cache after restoring language files is an inference, not a proven defect.
The combined snapshot/cache/target deletion was refused before process creation;
no action or following patch ran and the deletion was not retried. Five owned
untracked outputs and the original cache remain in that checkout. Root proceeds
from identical authoring in a new checkout; no assertion/target/log rule is relaxed.
First seed's older receipt-pairing limitation also remains historical.

Reviewer-only incorrect ZIP type-bit/display-name guards and refused oversized
draft remain failed probes. Root's own unsupported display-name guard, mistaken
read paths and truncated displays are preserved in READ-PROBES-14.md. None is
relabelled a passing target test; target/archive/caller/results are unchanged.

## Evidence, integration and open boundaries

Packets under D:/GitHub/ARCE-Task-Evidence/v1.8.0/:

- liquid-io-board-acquisition-20261011-09: original semantic source facts,
  preauthoring/contract/source/helper reviews, seed snapshots and failed binding.
- liquid-io-board-cohort-20261011-10: fresh seed, committed-source original
  receipts/streams/custody, retained JUnit ZIP/result, two-run resource bindings,
  copied server log, inert result review and Main integration metadata.

Copied final server log: 1913167 bytes, SHA-256
85a60ddcca0046da91b159d5be2423cd2ceb8a75db14db3ef70b513368737d7f.
Native receipt/custody/copied-log binding precedes the unchanged checker. Seals
and later remote/record observations are separate; no sealed helper is executed.

Owner reply on 2026-10-11, send_user_message_question_reply
call_f0159b2821604ec38c63bc946bf8a9f8: "没有使用主检出的 HEAD，可以合入".
This authorizes only HEAD custody, not art, ADR expansion or Gates. Source branch
push resolves to exact tested source; Main locally fast-forwards from 7beccfcf.
At 2026-10-11T06:58:38.834031Z, original 149 raw status rows, empty index, branch
and two owner hashes are preserved. Owner AGENTS/global implementation-log bodies
are not displayed/edited/staged, and no foreign process or world is controlled.

Previous record descendant 7beccfcf has regression job 114397107064 in run
38114677677 completed/success in the 2026-10-11T05:38:15.903789Z public metadata
snapshot. This is not raw Linux counts/log qualification, whole-run inference,
or a hosted result at the new Liquid IO source. Earlier IO configuration failure,
strict timeout/link failure, reviewer Low and hosted storage CME remain open.
Current Python/repository-strict, full survival graph/initial steel, shared
physical hooks, dedicated restart, human NEW art, V1/V2 and G0-G9 still need
separate evidence. Acceptance cursor stays v1.0; v1.8 stays IN_PROGRESS/IMPLEMENTING.
