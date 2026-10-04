# Task04 phase02a committed Python verification

Date: 2026-10-05. Tested source commit:
`254af9e4b7f2fe007eea87989e1534a238b140b7`.
Scope: the private Windows quiescent properties reader and its unchanged
phase01 dependency. This is not native inventory-driver or version acceptance.

## Actual checks and independent review

Root runs `python -B scripts/test_classic_inventory_fixture_inputs.py -v`
and `python -B scripts/test_run_v180_classic_inventory_smoke.py -v` at that
fixed commit: respectively 34 and 39 methods, exit 0, no failures/errors/skips.
Their unit times are 0.125 and 0.020 seconds. All eight related source
postimages match fixed Git before and after the checks. The separate source
review executes 34 + 39 + 10 methods with no failures/errors/skips.

The independent committed-result/record review finds no unresolved C/H/M/L
in its inspected scope. It also executes six in-memory forensic controls;
these are not six more inventory test methods. Review member:
`result-review/REVIEW-01.md`, SHA-256
`f0ef8d30736eacbaa5867d89b86f1b34688ee5f546ba7537fc47f0613e9b9f76`.
Its original sentence-line-wrap comparison failure and corrected postcheck
are both retained. See the [source disposition](NATIVE-SOURCE-REVIEW-DISPOSITION-04-PHASE02A.md)
for the adopted private-reader boundary.

## Portable evidence

[Evidence ZIP](phase02a-01/C18C-PHASE02A-SOURCE-RESULTS-01.zip),
[archive checksum](phase02a-01/C18C-PHASE02A-SOURCE-RESULTS-01.zip.sha256):

* Archive: 430,724 bytes / 223 members; SHA-256
  `a7b916ffbcb34143981cfcb9f85e286522fc27d88fa8b4b35d035dd0e92a09ce`.
* Internal `EVIDENCE-MANIFEST-01.json`: 221 payloads / 1,725,251 bytes;
  SHA-256 `0adfedd3b1a3dd137ab8832fc9bd936f6f65241929410a503701e49f1c5f7b16`.
* Internal `SHA256SUMS-01.txt`: 222 entries; SHA-256
  `d57a5a4dad6952d1616e3c4b99c2b3f699401a0b48d24726d06166da76d3cc98`.

Root's actual `intake01.py` exits 0: CRC, safe unique member names, all manifest
and checksum identities, the three nested evidence manifests, all 217 original
files and four reviewed pending document pins match. The imported archive and
sidecar are byte-exact; no sealed evidence is edited or extracted to a tree.
Four duplicate Git stdout blobs are omitted only with exact commit:path/SHA and
identical bundled postimage locators in `EXCLUSIONS-01.json`. This packet is not
a complete raw-publication-input or runtime closure. `DOCUMENT-CONTROLS-01.json`
reconstructs three pending document postimages against the fixed source commit;
the fourth disposition is separately captured, not a fabricated future commit.

The Root intake/publication records use the project-parent evidence directory:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-phase02a-doc-publication-20261005-01`.
The ZIP contains the earlier actual source publisher, Python output, independent
reviews and collection records; no new publication outcome is implied here.

## Temporary-file accounting and unverified scope

Process temporary files and fresh fixtures use owned D directories. Root's first
fixture cleanup fails its ancestor precheck before deleting anything; a separate
corrected helper removes six aliases nonrecursively and the checked fresh root,
totaling 99,375 regular-file bytes. These are file lengths, not measured free-space
recovery. Cleanup shell exits 1/0 are retrospectively supplied Root tool-API
observations, not independently captured child-exit receipts. Failure/pass JSON,
inventory, corrected helper and absence observation remain distinct in the ZIP.
The author's successful 413,698-byte cleanup, later rejected 39,792-byte replay
cleanup and source review's absent roots with null byte accounting are not merged.
Old rejected C/D cleanup targets have not been retried or declared deleted.

No Java, Gradle, GameTest, normal packaged server, native inventory driver,
live-read qualification, ownership admission, restart, crash, GUI or client
verification is run in this cohort. Earlier Java results remain tied to their
original commit. R-021, C18c-01 delivery, the ledger and G0-G9 remain open.
