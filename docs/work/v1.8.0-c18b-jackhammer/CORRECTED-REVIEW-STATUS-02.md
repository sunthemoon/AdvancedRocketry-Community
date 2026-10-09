# C18b fixed bab14562 independent review preservation

Date: 2026-10-09. The [unchanged independent archive](SOURCE-INDEPENDENT-02.zip)
reviews only bab145626633a7c778ba3606d400d5f29f8659b5 against adoption 106d3165.
Verdict: changes requested; two Medium findings concern Boolean test expectation
and recipe/asset-plan inconsistency, plus one Low console-only fixture boundary.
Later candidates are not credited by this report.

Actual independent forced build and explicit test each execute 2,192 JUnit/381
XML, one failure, zero errors/skips. Both DataGen/separate empty diffs and all
578 required native cases pass. Accepted ledger and strict repository validation
fail; strict link findings are separately attributed to unchanged base records.
Bootstrap/whitespace pass. No native restart or Gate approval is inferred.

Archive: 1,253,095 compressed /8,189,261 uncompressed bytes, 833 entries.
Archive SHA-256: 98a712304041f77787e9f37c5a1f82e7047fa96bc948abf93a332edb8e129ac3.
Report SHA-256: 817d004e6eb90d3a00ce5e5b7f20db83f58cbaa32c2abc322b52bce82ba66487.
Manifest SHA-256: 6e3a9adcf9064bc4a95a0409aebff01fdfda9fd71d58562ef389a027039b36a2.
Root verifies covered hashes, relative/case-unique paths and archive CRC before
publication. One checked reviewer-owned native cleanup removes 1,449,899,962
disposable bytes; zero unsealed bytes remain. No other-agent cleanup occurs.

See [source status](SOURCE-STATUS-01.md) for subsequent separately committed
corrections; no content unit or full tool is delivered by this failed cohort.
