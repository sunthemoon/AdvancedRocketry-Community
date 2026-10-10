# Disabled jackhammer START feedback integration

Date: 2026-10-11 (Asia/Taipei). Status:
`DEVELOPMENT_INTEGRATED_GLOBAL_STRICT_BLOCKER_CLIENT_AND_GATES_OPEN`.
This records the narrow [repair task](START-FEEDBACK-TASK-01.md), not completion
of the ordinary tool, all C18 equipment or the v1.8 release.

## Player behavior and source

The original client report found that disabled jackhammer feedback waited for
break completion. The new stateless Forge listener sends the existing translated
action-bar text on logical-server START with a main-hand jackhammer and the
adopted equipment switch disabled. STOP, ABORT and client hold add no such notice.
It does not modify event cancellation/results, read target blocks, load chunks,
change resources, add a packet or replace final mining/melee refusal. COMMON
client prediction/tooltip synchronization remains unchanged.

Root committed preparation `c898370b6cce533fcd04638861605080bb0f092b` and source
`d95aa9b549416a93afed5ab68ca5724205633210` from `063edab0`. The normally pushed
`fix/v1.8.0-jackhammer-start-feedback` was fast-forwarded into and normally pushed
on `codex/v1.8.0-classic-content`. At integration its complete tree equals the
tested tree `1e78cb0b0b6695254bf71106e389678ed2e2ddf9`. Later record-only changes
do not constitute a fresh test on their SHA. No existing assertion, log rule,
product item behavior, recipe, art, balance or schema was modified.

Fresh independent source review found no change-required source finding; cutoff
`2026-10-10T20:26:18Z`, report SHA-256
`c5ba246125fbf7e9abf5555d3f60b9f0b84ef909c8caa3311a05ec6a1a8264e6`.

## Actual original commands

All Gradle commands used Java 17.0.7 and prefix `cmd.exe /d /c gradlew.bat
--offline --no-daemon --max-workers=2 --no-build-cache --rerun-tasks` in the clean
isolated source checkout. Native version is Minecraft 1.20.1 / Forge 47.4.10.

| Command | Original exit / seconds | Result |
|---|---:|---|
| `clean build` | 0 / 184.340484 | build and test task executed |
| separate `test` | 0 / 181.412412 | 382 XML, 2202 testcase children, zero F/E/S |
| `runData` | 0 / 42.189206 | followed by empty tracked diff |
| `git diff --exit-code` | 0 / 0.361047 | clean generated/source output |
| `runGameTestServer` | 0 / 259.988692 | 607 Required passed, normal final shutdown |
| unchanged strict GameTest checker | 0 / 0.176041 | 62 ERROR / 161 WARN / 0 FATAL; 38 original rules |
| accepted content ledger | 0 / 0.326232 | mechanical check passed |
| bootstrap provenance | 0 / 27.406126 | historical authority consistent |
| strict repository | null / 180.010030 | TIMEOUT; already failed=1; not passed |
| whitespace | 0 / 0.088020 | candidate diff passed |

Python is 3.13.15 with `-I -X utf8 -B`. The strict validator had failed=1 at
markdown-link checking and stopped at `check_v002_g4_applicability begin`, without
a final report or captured failing link target. Its post-deadline zero exit is
not an original successful exit. Neither the specific target nor a repair-caused
failure is established. The P1 verification blocker is retained, not waived.
Full Python suite/resource qualification was not executed.

Each original stream reached EOF without overflow/error/live reader. Original
limits were 900 seconds for Gradle and 180 for other commands, plus 10-second
post-exit capture and 4 MiB per stream. The private new process job read back a
6 GiB aggregate commit cap before resume; space exceeded 10 GiB and D TEMP was
owned. These are isolation settings, not peak-memory or reference performance
qualification. Native log retains a 2527 ms / 50 tick lag WARN. No new CME was
observed, but the earlier hosted storage CME is not reproduced, attributed or fixed.

## Independent result audit and retained evidence

The fresh result reviewer recomputed every XML testcase child and compared all
382 snapshots with original files before retirement. These snapshots belong to
the separate `test`, not preserved original `clean build` XML. It compared the
retained/native-original log and independently replayed the unchanged checker:
exit 0, 0.118 seconds. Cutoff `2026-10-10T20:39:18.4658718Z`; report SHA-256
`906da6d940c00c891e139d27dcbb66c66c7228ff1c87b98de50038cd86ce62e4`.

155 batch records sum to 607. The 18-test jackhammer batch is supported by 13
existing and five new annotated methods covering early hard-target feedback,
repeated/non-START actions, creative final refusal, enabled/ordinary-tool controls
and canceled-event result preservation. Raw success logs do not name individual
new methods or provide standalone packet-value receipts; this is native batch
coverage with source assertions, not actual client rendering or full transport proof.

Sealed external evidence:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/jackhammer-start-feedback-20261011-01`.
It contains 460 manifest-listed files, 6746212 B, plus the manifest. Manifest hash:
`5fa87248c0220c55f01daad45a6ee828c189b523438f446af284122e79d98fe7`.
The complete retained log is 1874417 B, SHA-256
`bce4fe04fd2fceb91cd7f7be4e115e8739498ddff0d87b80ba0857eda2399abd`.
Whole peer ledgers and replay receipts remain with original/copy hash checks.
Archived helpers must not be executed again.

After reviewer release, one guarded native PowerShell attempt retired only the
new-owned build/run-data/TEMP: 6351 entries, containment/reparse/process guards
passed. Main's original 149-row state and two protected owner-file hashes remained
unchanged; no original worlds, other worktrees, unowned processes, old refused
outputs or shared caches were touched. The mixed implementation log was excluded;
this separate record is the repair implementation log.

Hosted run `38084795112` targets the exact source SHA. The earlier metadata
observation `2026-10-10T20:45:48.7437808Z` was in progress and is superseded by
the official observation `2026-10-10T20:53:24.3172705Z`: completed/failure.
Job `114308955412` failed in the combined GameTest/log-check step; build and
data-generation steps succeeded. That step contains both the native server
and checker, so its status does not establish which failed or whether CME
recurred. One unauthenticated raw-log request returned HTTP 403. No hosted raw
log, count or cause is claimed; the original evidence is requested separately.
The later records audit/copy receipts and both metadata observations are retained
at `D:/GitHub/ARCE-Task-Evidence/v1.8.0/jackhammer-start-records-20261011-01`.

## Remaining scope

Real-client V1/V2, unsynchronized COMMON tooltip, survival titanium acquisition,
full tool/equipment behavior, assets and human approval, dedicated restart,
performance, full Python/strict validation, the new hosted failure diagnosis
and storage-cause proof remain open.
The development integration does not release or waive them. v1.8 remains
IN_PROGRESS / IMPLEMENTING and the acceptance cursor remains v1.0 under ADR-060.
