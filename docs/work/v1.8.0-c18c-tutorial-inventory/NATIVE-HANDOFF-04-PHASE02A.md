# C18c Task04 phase02a source handoff

Date: 2026-10-05. Status: REVIEW_READY source proposal; Root integration and independent actual-diff review remain separate. No native/host/ownership admission or full-version Gate claim.

## Completion and exact scope

Fixed worktree `D:/GitHub/arce-v180-c18c-inputs-20261005`, branch `codex/v1.8.0-inventory-properties-inputs`, base `b6299c86f7bcb99b00b89b4dd38479e035321954`. Only four new files are proposed: the private bytes helper, its focused tests, [progress](NATIVE-PROGRESS-04-PHASE02A.md) and this handoff. Existing phase01/Java/central/generated/contracts/AGENTS/Git are read-only. Root alone commits or integrates.

Implemented private API:

```python
read_quiescent_properties(owned_server_root: pathlib.Path, role: str, point: str) -> bytes
```

The root must be the exact native `Path` type, absolute local D, at most 1,024 strict UTF-8 bytes, without lexical `..`, device/UNC/alternate-drive redirection. Roles are exact `configured`/`active`; points are exact `seed_preboot`/`reload_preboot`/`stopped`. Fixed locations remain `.classic-inventory-fixture/snapshot-server-properties-v1.txt` and `server.properties`. The finite labels are selectors, not authority or proof of a stopped host.

Every literal directory/ancestor/leaf is checked before canonical resolution. Actual Windows reparse attributes and meaningful file ID fields are required. Canonical locations stay on D and inside the observed root. One read-only unbuffered read requests exactly 16,385 bytes; empty and over-16,384 results refuse. Returned bytes are newly owned and immutable. Pre/open/post descriptor/path/type/size/metadata and canonical observations must agree; detected change refuses without refresh/retry/repair. Context-managed descriptors close on success/refusal. Fixed error codes are `PLATFORM`, `ROOT`, `SELECTOR`, `PATH`, `FILE`, `CHANGED`, `BYTES`, `IO`; public errors/default tracebacks suppress underlying OS text.

Windows identity compares `st_dev`, `st_ino`, `st_mode`, `st_size`, `st_mtime_ns`, explicit `st_birthtime_ns`, `st_nlink`, `st_file_attributes`, `st_reparse_tag`. Access time is excluded because reads can update it. Actual lstat/fstat ctime differs after ordinary writes on this Python 3.13 platform, so it is not treated as one comparable field. Missing required proof capability refuses; no fallback assumes identity safety on older Python or non-Windows platforms.

The helper has no properties decoder, runnable CLI, directory enumeration, writes, world/profile lookup, process access, marker/receipt cache or mutable global state. Future Root composition may pass bytes to the existing phase01 observation, but no driver/helper integration is proposed here.

## Commands, results and preserved failures

All Python commands use `-B` and process-local `TEMP`/`TMP`/`TMPDIR` in `D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-inputs-author-20261005-1b8f40`. No Java/Gradle/server/client/network/dependency install was run.

| Actual command/cohort | Outcome |
| --- | --- |
| `python -B intake01.py` first attempt | exit 1 before source reads; wrong no-binary-fixture assumption retained |
| corrected intake second invocation | exit 0; 2,930 named source/build/script/license pins and 14 governance pins |
| `python -B scripts/test_classic_inventory_fixture_inputs.py` focused-01 | exit 1, 0 tests; invalid raise syntax, original sources retained |
| same suite `-v` focused-02 | exit 1; 31 methods, 5 failures/5 errors, original sources/log retained |
| `python -B fields-03.py` | exit 0; actual descriptor/lstat time-field observations, no server |
| focused-03 | exit 0; 31 methods, 0 failure/error/skip |
| `python -B scripts/test_run_v180_classic_inventory_smoke.py -v` | exit 0; 39 original methods unchanged |
| focused-05 | exit 0; 34 methods, 0 failure/error/skip |
| `python -B run-final-06.py` | exit 0; final source-pinned 34 focused + 39 unchanged phase01 methods, 0 failure/error/skip |
| native PowerShell `cleanup-fixtures-07.ps1` | exit 0; all five fresh owned fixture roots absent, 413,698 regular-file bytes removed |

Final raw command/source pre/post receipts are `focused-final-06.receipt.json` and `phase01-final-06.receipt.json`, with matching raw logs. The platform observations retain actual reparse attributes/tags, descriptor fields, one-read requests and closure. Controlled negative IO seams are labelled test-only; there is no production injection API.

## Frozen source/evidence and integration boundary

External thin evidence is at the D leaf above. `SOURCE-MANIFEST-01.json` binds the four postimages; `SOURCE-01.patch` proposes only those additions. `SOURCE-POSTCHECK-08.json` compares the existing named 2,930 source inputs and 14 governance inputs against intake and verifies fixed base/exclusive scope. `PATCH-REPLAY-09.json` records a fresh four-file-only check/apply/postimage test, not a whole-source export. `EVIDENCE-MANIFEST-01.json` and `SHA256SUMS-01.txt` bind selected reports/logs/results/postimages and preserved failures. No large ZIP, full tree, classes, official JAR or runtime is exported.

Production helper SHA-256: `42dc3299f5b973761926267ed5c69a37cd1be067e951fde515a4c3e55269667e`.
Focused test SHA-256: `c7d7b760b7c800a1096481094b8135d65b896aa25cf4a90592126ba36fe95b9f`.
No central integration lines are needed to compile/run this private Python leaf. Import/composition into any runnable native driver remains Root-owned future work, not permission to launch a host.

## Remaining limits and risks

Caller ownership/quiescence/clean stop and process absence remain prerequisites; success does not prove them. This is not a race-free hostile-filesystem reader, and repeated metadata equality cannot detect every hidden same-identity mutation. Bounds constrain Python byte/path work, not an interruptible OS syscall deadline. Actual Windows ACL-denial behavior, other platforms/Python versions, special nonregular file classes and every possible reparse kind are not all exercised. Reparse attrs are checked generically; unsupported capabilities refuse.

No live properties read, JSON/NBT parser/counting convention, file-absence/identity cache ownership, CREATE_NEW marker/receipt, authority/freshness transport, force setup, console parser, Java hook, seed/reload driver, native run, client V1/V2, repair policy or G0-G9 is completed. Original 39 assertions/budgets remain unchanged. The focused test class is under 500 lines and contains only one fixture boundary; no production framework was added. All earlier sealed evidence/old refused targets/worlds remain unchanged.

Next work is independent exact-source review and focused replay, then Root's separately qualified integration. Full native driver/ownership/JSON supplements require their own current-version task boundaries.
