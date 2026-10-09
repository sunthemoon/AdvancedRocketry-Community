# C19 committed source blob session candidate73

Date: 2026-10-10. Version: v1.8.0 under ADR-060. Status: committed corrected candidate; full qualification OPEN.

Root is sole implementer. Base is clean committed8062781ca0cfc3157212f6f313c509cd0e522c7c.
Create D:/GitHub/arce-v180-source-blob-session-20261010-73 on branch
fix/v1.8.0-source-blob-session. Write only scripts/validate_bootstrap_provenance.py,
scripts/collect_v002_manual_evidence.py and new tests/test_committed_source_blobs.py
in that checkout. Root owns this task and central status records separately.

Observable outcome is one finalized existing bounded Git object session for a
collector source-file read group. Reuse the validator's verified exact commit,
tree and blob reads rather than copy a new batch protocol. Introduce one narrow
reader returning path-to-(blob OID, raw bytes) only after session close has
verified EOF and exit. The existing session request4096, aggregate16 GiB,
lifetime180 seconds and request/close15 seconds limits remain unchanged. Each
path lookup retains the existing tree8 MiB and lookup64 MiB limits. Reader
accepts1 to16 unique safe relative paths and a positive non-boolean integer
per-blob bound no greater than existing MAX_PROVENANCE_BLOB_BYTES (16 MiB).
Commit remains exact lowercase SHA-1. Regular blob modes100644/100755 only.
The collector's existing1 MiB JSON check remains; README also gains the existing
16 MiB bounded blob ceiling. Record these stronger rejection boundaries.

Preserve original source commit resolution, require-head equality, clean
tracked/untracked status, working-file checks, schema, source hash/size/OID
bindings, source-review approvals and every collection/staged/committed/final
validation phase. No object, approval or result cache; each phase starts a new
session and repeated requests remain re-read/hash-verified. Do not change the
original138 manual-evidence tests or fixtures, original bootstrap95 tests,
existing protocol tests, packet generation, Git environment, budgets or src.
The session scope's type annotation may become generic without runtime change.

Add real-Git reader tests for raw byte/OID binding, exact commit type, missing
path, unsupported mode, byte/path/input bounds and finalized-session failure.
Run them and unchanged authored key modules with Python3.13.15 -X utf8 -B,
original180 seconds per command, complete original-handle/raw-stream receipts.
Fresh runtime must be a task-specific absolute D: sibling. Read/file1 MiB,
combined streams256 KiB and evidence leaf4 MiB limits apply. Check disks>=10 GiB.
No full Gradle/native/client run, inherited runtime cleanup, unknown untracked
acquisition, upstream copying, Main integration, ADR acceptance or Gate closure.
Independent fresh-session review must inspect actual diff and rerun applicable
tests before candidate commit/push. Record broad qualification separately; a
module improvement cannot qualify the whole suite or rewrite older failures.

Task76 factual correction supersedes this task's claim that the existing
provenance blob constant is16 MiB: the actual unchanged constant is32 MiB.
The initially stated numeric16 MiB source-reader boundary is retained as a
separate MAX_SOURCE_BLOB_BYTES constant. First postimage allowed32 MiB and its
three passing module runs are preserved as that postimage's results only.
Review74 identified the discrepancy before independent execution. Corrected
source is separately tested under Task76 and Review74; no limit is relaxed.
