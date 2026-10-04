# Tank independent review 01 — portable record

Outcome: CHANGES_REQUESTED; 0 Critical, 0 High, 1 Medium, 0 Low.
The Medium finding covers Fluid metadata aliasing across codec encode and
decode; both independent probes actually failed. Author revision 2 is separate
and does not overwrite this original evidence.

[Immutable evidence](round-01-evidence.zip), SHA-256
`e8adea8d1b0ea55ff85472baa4ebedd2f63fcecc7c12c27a355e8cb48c2e2472`:
96 ZIP entries, 95 manifest-pinned files verified individually; CRC check clear.
The raw report is `REVIEW-01.md` inside the archive, SHA-256
`c296690f9db9b701216ed8c644cfd401aefc4b6f605e462295cd593e71b74d5e`.
Its relative source, probe, XML and command-log links refer to archive entries.

Actual scoped result: 16 JUnit / 6 suites passed; runData exit 0. The registered
421-test run exited 1 with one unchanged atmosphere required-test failure;
the eight Tank names were absent from the failed list, not a whole-run PASS.
The cause is unproven. Original two-probe reproduction exited 1 with two
failures. No native, V1/V2 or v1.8 Gate result is inferred.
