# C19 source blob cap correction76

Date: 2026-10-10. Version: v1.8.0 under ADR-060. Status: committed16 MiB correction; scoped PASS, whole TIMEOUT.

Root owns the same three-file candidate write scope as Task73. Review74 found
that Task73 described existing MAX_PROVENANCE_BLOB_BYTES as16 MiB, while actual
base/candidate is32 MiB. First implementation supplied that existing32 MiB
constant, so it did not implement Task73's separately stated numeric16 MiB
reader boundary. Preserve the discrepancy and the first tested patch/inputs.
The first reader9/protocol26/manual138 passes do not qualify corrected code.

Keep the initially frozen numeric16 MiB source-reader boundary. Introduce
MAX_SOURCE_BLOB_BYTES =16 MiB without changing existing32 MiB provenance limit,
session/commit/tree budgets or other validators. The collector and new reader
must use that source-specific bound. Add16 MiB+1 to the new input-bound test;
do not alter original authored tests or assertions. Source edits start only
after Root's running manual command and Review74's static source reads stop.

Own new evidence leaf is
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-source-blob-correction-root-20261010-76.
Each runtime is an absolute fresh D: sibling with prefix
c19-source-blob-correction-runtime-20261010-76-. A fresh capture adapter may
derive from retained Task73 helper bytes, copied as data; no old helper executes
or old output is overwritten. Static-check all fixed output/runtime references
before launch. Run corrected reader9/protocol26 and unchanged bootstrap95 once
each with pinned Python3.13.15 -X utf8 -B, original180 seconds and complete
original-handle/raw-stream receipts. Independently scheduled Review74 reruns
reader/protocol/manual on corrected postimage. No competing full test commands.

Before/after bind HEAD/tree/status/index/all Python inputs/helper/task/Python.
Check C:/D:>=10 GiB. Read/file1 MiB, combined streams256 KiB and leaf4 MiB caps
apply. Timeout stays failure, with distinct maximum10-second owned wait/drain.
Only owned original-child termination and empty fresh runtime nonrecursive
removal are permitted. No old runtime cleanup or Gate/integration decision.

After independent final source bindings and findings disposition, Root may
stage exactly the three candidate files, inspect cached stat/check, commit and
push without force. Bind the committed files to corrected tested raw bytes.
Run a fresh literal full unittest discovery against that clean committed
candidate under the unchanged180-second ceiling; record actual failure or pass,
not a sum of targeted results. This is an explicitly new qualification attempt,
not an automatic retry or replacement of Task71/Root53 failures. Keep source
qualification separate from Main metadata, approval and all Required Gates.
