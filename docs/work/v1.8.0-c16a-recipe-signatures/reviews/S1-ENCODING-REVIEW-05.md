# C16a-01-S1 stopped encoding review05 and Root integration17

Date:2026-10-04. Scope:two Python fixture files,not production save semantics.

The [independent source review](signature-stopped-encoding-independent-05.zip)
has zero unresolved Critical/High/Medium/Low. Its original patch applicability
Low remains recorded; the distinct [LF delivery02 supplement](../signature-stopped-encoding-patch-delivery-02.zip)
passes fresh check/apply and yields the same two proposed postimages. Neither
the original rejected patch nor [failed native16](../SIGNATURE-NATIVE-16-01-FAIL.zip)
is overwritten or relabelled.

Review archive SHA-256: `10c2ee592ed771132571760795c2cea556a9295660b8506261895aa83553674e`,
39,633,657 bytes/156 entries; manifest
`2d85c0b17fafbf1d0024039c380dda7fd97c7676157995d466fc9ffb5ee6b3af`.
Root verifies all CRCs, safe unique names and every manifested byte before copying.
Actual reviewer checks:41 green tests, unchanged-source41 red/1 failure/0 errors,
eight boundary controls and exact captured-cut replay. All39 original test
methods and every existing allocation,typed equality,resource and timing bound
remain unchanged. Fixed zlib level9 does not promise arbitrary roots will fit.

Root integrates the exact reviewed postimages:

- `scripts/run_v180_signature_smoke.py`:58,441 bytes,
  `61d0f001cb0644fa2c7647d458b2fc6866d0d444ab26bac10c613f26ff45585e`.
- `tests/test_run_v180_signature_smoke.py`:36,237 bytes,
  `0b86157bd7e97310b0fb1db1a92e851b66fc4d2ce0ed897ed2def51ed80f2778`.

Source17 has2,974 inputs; manifest
`d962518afda1803ed2b02a5d29d95ff1ec16445ae27e74f23c332d3f3ce2c664`.
Only these two files and the separately adopted ADR-064 revision5 differ from
source16;2,971 other inputs remain exact. Java/generated/API still belong to
[candidate15](../../v1.8.0-c16a-integration/ROOT-INTEGRATION-15.zip),not a new Java build.
Root focused Python82/0F/E,strict ledger,common/client import,explicit-version
artifact and `git diff --check` all exit0. Ledger closure and dirty-tree
`git diff --exit-code` both remain1,not waived passes.

Fresh [native17](../SIGNATURE-NATIVE-17-01.zip) executes six clean phases for
each of Rolling/Precision/Electrolyzer,outer exit0 and18 receipts. Both stopped
cuts per adapter are explicitly injected,not naturally captured crashes.
Original source world135 files remain exact. Result audit is a separate pending
review; this source review does not admit menu/client,physical hatches,typed-empty
native preservation,forced-stop atomicity or any full-version Gate.
