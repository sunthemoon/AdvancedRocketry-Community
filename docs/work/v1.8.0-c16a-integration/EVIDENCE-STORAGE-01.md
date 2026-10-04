# Development evidence storage and commit binding

Date: 2026-10-04. Code and data are pushed at
`3f3d62aed3980186fe0acc9592cf93ca436405fb`; this record is not a release approval.

The [archive location inventory](HISTORICAL-ARCHIVE-LOCATIONS-01.json) identifies
141 existing sealed historical ZIPs by exact location, length and SHA-256.
Their total is 2,590,728,550 bytes. They remain local, outside Git delivery;
the inventory does not claim a remote upload, public attachment, new review,
or deletion/externalization of that historical storage debt. Relative ZIP links
in older handoffs name these original local records and are not guaranteed to
exist in a plain Git checkout. Consult the inventory before obtaining a packet.
No original packet is rewritten to satisfy the newer storage policy.

The current [K2 verification](../v1.8.0-c16a-k2/VERIFICATION.md) instead records
a compact external packet, immutable artifact locations and exact hashes.
It does not duplicate complete committed sources or JARs in Git. Its disk-input
manifest and its code-commit identity are distinct: all source-JAR Java is bound
to the code commit; user-maintained AGENTS.md, then-pending documentation and
the declared gradlew.bat CRLF/LF representation are not a literal all-input Git
object match. An unchanged code artifact does not validate later documentation.

Historical standalone checksum files that name untracked archives or volatile
build/Temp paths are preserved locally but excluded from the documentation
commit. They are not promoted to compliant delivery checksums. New acceptance
records use committed code identities or package-local immutable manifests;
remaining archive storage, real clients, recovery and version Gates stay open.

The first documentation staging check rejects frozen unified-diff files for
their literal space-only context lines. That exit remains recorded; original
patch bytes are not stripped or rewritten. Scoped C17/C18 attributes classify
only these `*.diff` evidence payloads as binary, preserving patch syntax and
original hashes. Source files, tests, validators and their whitespace rules are
unchanged. A subsequent staging check is a separate execution, not a relabelled
pass for the original command.

A second staging attempt exposes the original line-ending bytes in historical
JSON/raw companions when normalization is disabled. That check also remains
failed. Frozen proposal/review payloads, raw companions and the new immutable
audit bundle are stored as binary evidence so their original checksum bytes
survive Git. Current ADRs, status, implementation code, tests and validators
remain ordinary text with unchanged checks; no failed behavior test is waived.
