# C17 revision-4 independent decision-diff review

Date: 2026-10-03. Scoped findings: **0 Critical /0 High /0 Medium /0 Low**.
The reviewed proposal SHA-256 is
`c50de31ea70a56749c55202bd75de258ff3d89a0ae4a9123ff8ee59e9d3545bb`.
The [original report](REVIEW-04.raw.txt) is byte-exact, SHA-256
`8f39d0757fcc2a2a628ff87a03d5ed321c24856cccd3a04c3833af19268f2224`.

Two [independent probe](probe.py) runs and evidence packaging exited 0.
The [actual diff](independent.diff) matches the author diff byte-for-byte:
12 hunks, 22 changed spans. [Static evidence](static-evidence.json) binds
41 complete C17 ledger rows, 26 approved upstream identities, 12 existing
modern source hashes, both owner receipts and the accepted C18 contract.
Fifteen complete technical intervals, six tables and coverage/source companions
are unchanged. [Packaging evidence](packaging-evidence.json) and the
[original manifest](original-file-hashes.json) retain original filenames;
the manifest's REVIEW-04.md/TASK.md refer to REVIEW-04.raw.txt/TASK.raw.txt.

D1-A, full-vector disassembly consent/isolation, D2-A and D3-A match the owner
choices. Clock, shared schemas, checked writers and native forced-stop recovery
admission remain required. No Gradle, native server, GPU or multiplayer check
ran here. Contract acceptance and runtime verification are separate integrator
actions; all version Required Gates remain open.
