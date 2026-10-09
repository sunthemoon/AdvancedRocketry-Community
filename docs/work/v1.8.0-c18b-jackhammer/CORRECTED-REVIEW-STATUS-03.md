# C18b fixed 53523bcf failed-native review preservation

Date: 2026-10-09. The [unchanged independent archive](SOURCE-INDEPENDENT-03.zip)
reviews only 53523bcf68af203934845521e97af2f824b2958d against adoption 106d3165.
It retains a Medium missing host-template namespace finding that aborts the native
suite and a Low omitted upstream-touch history finding. Later correction 3b18a6bc
is not credited by this fixed review.

Both independently forced full JUnit cohorts pass 2,192 actual cases/381 XML,
zero failures/errors/skips. Both DataGen/separate empty diffs pass. Unfiltered
native exits 1 with an unexpected tick exception and no final completion marker;
its raw headers are 114 WARN, eight ERROR and two FATAL. Those are not a passed
578/579 cohort. Strict reaches the unchanged 180-second wrapper deadline with
no report; TIMEOUT is not a link-check result. Ledger/bootstrap/whitespace pass.

Archive: 1,648,124 compressed /8,451,171 uncompressed bytes, 860 entries.
Archive SHA-256: adea7ef4acf53d284c1b0b3511ea60d59503d19cd2da21ccec026a82c1585622.
Original report SHA-256: be70983b8a786e764e5cb6c6d584c5da2ba1ea7c46effa05c737fd487bd05158.
Original manifest SHA-256: 4109dcde4b43427f2f8fd6ba06eb1bc7262a28771bc8ac345d8e443a1df5d05b.
Supplementary manifest SHA-256: 143d09e38c7523ba5243c3a642609203fac495379c245d21fb8bf1a6e2ef91d0.

Read REPORT.md together with SEAL-CORRECTION-01.md and use the supplementary
SHA256SUMS-CORRECTION-01.txt for the complete package. The initial helper's byte
accounting assertion fails because text output uses CRLF, undercounting by 854
bytes; a subsequent report read uses the wrong default codec and fails. All 854
original covered-file hashes are valid. Original files and manifest stay unchanged;
the additive correction rechecks all 859 covered entries and records actual totals.
Root verifies the supplementary hashes, relative/case-unique paths and CRC before
packaging; no original evidence is rewritten. One checked reviewer cleanup succeeds,
with zero residual bytes. No Gate approval or packaged restart is inferred.

The corrected source and new actual cohorts remain separately tracked in
[source status](SOURCE-STATUS-01.md). Main still contains no tool source at this
record publication. Source qualification, complete tool delivery and release
acceptance remain different outcomes.
