# Signature runtime fixture revision03 independent review

Date:2026-10-04. Status:CHANGES_REQUESTED; source/Python scope only.

[Independent actual-diff review03](signature-s1-runtime-independent-03.zip)
has0 Critical/0 High/1 Medium/0 Low. Revision03 fixes the original observed
upgrade-stage reason mismatch,but its `restart-2-finish` still powers the
current row and immediately demands settled legacy reasons. FULL/power
acknowledgments do not prove the legacy controllers have ticked. A source-only
AST-extracted original function with pending-then-settled observations aborts
on its first report rather than applying the same startup gate.

This is a source sequencing finding,not an observed native restart failure.
No revised native series ran. Do not replace the unchanged strict final
status/resource/marker/protection assertions with a broad reason allowlist.
The original restart probe and revision03 bytes remain immutable;revision04
is a separate two-Python-file proposal before independent review/integration.

Actual reviewer runs:original28,proposed34 and own6 Python checks pass;
the same immutable native-row probe is1error on old code and1pass on revision03.
Exact two-postimage patch check/apply exit0. Original28 methods,45 other
functions and constants remain unchanged. No Java/native/server launched.

ZIP SHA256
`b5a1d73d106d41f43e779bba8fb4ef59cefd6c34ab6a7d36a829bac73637b4d3`,
117,884,996 bytes/320 entries;manifest
`4f91d48fb5911020ef639c8096d63b943595679db811cc76e30d286c6e565d16`;
report `beafaea1ee8675e1c9ef0881574ab2c128c1568a65db20aa79eaae960132fa0a`.
Root verifies/copies the exact archive without editing prior findings.
S1,raw-tag durable preservation,menus,physical adapters and all Gates stay open.
