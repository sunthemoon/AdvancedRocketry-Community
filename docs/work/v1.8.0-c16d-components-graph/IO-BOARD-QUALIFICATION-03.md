# Item IO board local functional qualification

2026-10-11. Status: committed-source local functional qualification, independent
inert review and local Main source fast-forward complete. Source fix branch is
normally pushed. Main remote push and later record-only commits are separate
integration observations, not extra JVM-tested sources. No release Gate or full
C16d content delivery is accepted.

## Source and scope

Exact tested final source: 50d4a3eaa2c3792904e6e3e134f40c00eb3b59cd, the
fixture-isolation descendant of IO source fd88f71b5378296569d6bf0c24340e78429160bf.
IO authoring is b581baf13809d3ff926d3a24556dbd31a30c3498, followed by freshness
language-layer correction 5cc1b86c082988ed31b6eea97565713ce137b668 and generated
resources/provenance fd88. Main locally fast-forwards from
6c53eca90437c3ce26eacd5612c9cfcd5fde5c69 to the exact final tested source.
Record-only descendants are not claimed as additional JVM runs.

The [adopted leaf](IO-BOARD-ADOPTION-03.md) adds one plain item_io_board, its
workbench and precision producers, unlock, bilingual names and NEW generated
model/icon. Crafting preserves rvr/dwd/dpd: redstone dust x2, quartz x1, gold
dust x4, wooden slab x1 and iron plate x1. Precision preserves ordered redstone
dust/gold plate/steel plate inputs and one output at 200 ticks x10 FE. Existing
stand-in circuits/recipes, kernel, hatches, networking and persistence do not
change. Only asset-plan row 245 changes to REGENERATE; schema-2 art remains
PENDING_HUMAN_REVIEW, not originality/client acceptance.

The [separate fixture correction](../v1.8.0-endgame-config-fixtures/TASK-01.md)
uses the existing bounded in-memory switch mechanism for six existing endgame
flags. Five GameTest classes replace temporary COMMON data writes and release
at existing AfterBatch hooks. Ordinary loaded/default flags, numeric limits,
all assertions, timings, batch names, standard checker/manifest and runtime
settlement remain unchanged. Raw/effective gravity observations remain distinct.

## Actual final cohort

Root alone executes JVM/Gradle/native/checker; reviewers inspect source and raw
results with inert checks, not independent runtime replay.

| Command | Original exit / seconds | Actual evidence |
| --- | --- | --- |
| Wrapper clean build | 0 / 210.26478789997054 | test task executed; 2223 actual children / 384 XML; zero failure, error, skip |
| Wrapper runData data-01 | 0 / 29.965853000001516 | all seven targets equal prior bindings/committed bytes; empty diff |
| Wrapper runData data-02 | 0 / 23.743938700004946 | same seven actual bytes; empty diff |
| Wrapper runGameTestServer | 0 / 284.6807570999954 | 619 complete/required-pass summaries, six-test IO batch, normal stopping; empty stderr |
| Separate unchanged log checker | 0 / 0.13218239997513592 | 62 ERROR /160 WARN /0 FATAL; 37 unchanged strict expectations |

Eight new IO A0 cases and two new switch cases execute in the final archive.
The switch cases cover six flags, replacement/clear, loaded-memory config,
raw-data isolation and reload-cache invalidation. Loaded crafting tests match/
assemble but do not debit a real player's inventory. The formed precision
fixture uses supplied steel and FE; it is not initial steel/survival acquisition.
Native logs expose the six-test IO batch and overall pass, not individually
named case-pass records. Full dedicated/restart, V1/V2 and acquisition graph
qualification are not established.

Every original command has complete capture, no overflow/capture error and zero
final owned processes. The external envelope fixes 6 GiB owned Job /900-second
original +10-second retirement /2 MiB streams /two Gradle workers, with 1 GiB
Gradle and 256 MiB initial/2 GiB maximum native heap observed. It does not
measure lifecycle peak memory, long-load performance, or prove every child
exited naturally. Both native worlds remain retained and guarded against rerun.

Independent source review: 23964 bytes, SHA-256
ff9754273a42ac5b5cfb8900054f1d80cf0ae76dd716debdd9fe290fc758e769.
Independent final result review: 117915 bytes, SHA-256
58e3e98359e2ac02e63dd004186954589ca0112a230879433178fc4d34f59283.
The latter retains a Low off-procedure rg discovery/source search; later byte
accounting does not authorize it retroactively. Root uses the separately
evidenced permitted receipt/XML/resource/log checks, not that search, and does
not claim a fully compliant reviewer runtime replay or Gate approval.

Independent frozen five-document review rechecks both seals and permitted
result/resource/Main metadata without an actionable record finding: 15226
bytes, SHA-256 cfdaa35b3da5eb629da73bd0a72c914812817533b022068708bd9d1bb6e9e00d.
It retains its own Low oversized private-ledger defect; reducing that private
output is not retroactive procedural compliance. Root retains both reviewer
limitations, adopts no Gate waiver, and adds this association only after all
record interests release. This association is not another peer runtime check.

## Preserved failures, evidence and integration boundary

The fd88 sample has 2221 unit cases and all 619 required native assertions, but
its separate checker originally exits 1: one unexpected config-correction WARN
plus a watcher duplicate-table exception in native stderr. That original sample
remains failed. The new sample removes the fixture's direct writes and passes
unchanged policy; it does not prove the original config object/interleaving,
long-term watcher safety, or the historical hosted IOWorker CME cause.
Preparation parser/path/grid/helper failures remain visible. No whitelist,
timeout or assertion is relaxed, and original reports are not rewritten.

Sealed packets under D:/GitHub/ARCE-Task-Evidence/v1.8.0/:

| Packet | Files / retained bytes, excluding manifest | SHA256SUMS.txt SHA-256 |
| --- | --- | --- |
| io-board-acquisition-20261011-06 | 51 /4368997 | 8b61540e494d62deefa6db25f1511221c5e38123224a6da2f020566114d7100a |
| endgame-config-fixtures-20261011-07 | 41 /4414408 | d1649798bf74f430888a30d6e29c09dc311dc99fdc22cf611ede0b1a816e93b1 |

The completed final server log is 1897581 bytes, SHA-256
2c51dae4bacf3af9c03e9ed11f6bd1efb099ebfd55270e3e8f88a897b67c5e13.
Immediate native receipt/custody/log binding precedes the separate checker.
Packet seals contain only internal retained files, not mutable build/Temp/world
origins. Subsequent record review, integration/push observations and correction
history belong to io-board-qualification-records-20261011-08.

Owner reply on 2026-10-11, send_user_message_question_reply
call_f0159b2821604ec38c63bc946bf8a9f8: "没有使用主检出的 HEAD，可以合入".
This only authorizes the HEAD-use boundary. Actual source fast-forward preserves
the exact original 149 NUL-porcelain rows, empty index and both owner-file hashes;
main-before/main-after observations are in the final packet. Owner AGENTS/global
implementation log bodies are not displayed, edited or staged. No foreign
process is controlled. Later records do not reconstruct original Git receipts.

All current Python/repository-strict qualification, hosted raw Linux outcomes,
human NEW art approval, steel bootstrap, physical machines/hatches, full graph,
save/restart/client and G0-G9 remain open. Earlier strict timeouts/link failure,
storage-causality gaps and inherited release Gates are not closed. The acceptance
cursor stays v1.0; active development stays v1.8 IN_PROGRESS / IMPLEMENTING.
