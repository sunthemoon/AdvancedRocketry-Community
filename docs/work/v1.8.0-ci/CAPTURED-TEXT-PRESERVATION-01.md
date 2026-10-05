# Preserve the reconnect diagnostic's captured bytes

Date: 2026-10-06. Scope: v1.8 hosted checkout cleanliness only.
Status: candidate revision 2; independent review and committed replay remain pending.

The hosted Laser-fixture cohort at source
`86a819ca564c43206c6b9272c0b79076fceaa874`, run 37353443122, attempt 1,
builds and generates data but fails the unchanged whole-tree diff check on
`docs/work/v1.0.0-native-reconnect/recovery-bytecode.txt`. GameTests are skipped.
The six changed lines are a Git text-conversion observation, not evidence that
DataGen rewrote that diagnostic or the new fixture failed at runtime.

The historical `SHA256SUMS.txt` names the captured diagnostic as SHA-256
`bf67cefd598b6916acf7b4c5117b162b5741fd5344f71af8e938c3b4cafda45b`.
The existing Windows file is 69,100 bytes and matches that hash. Its ordinary
CRLF and six CRCRLF delimiters are captured bytes, not newly authored source.
The earlier Git blob is 68,268 bytes and has residual CRLF on those six lines;
the general `*.txt text eol=lf` rule cleans it again on Linux. Both old Git
history and the historical checksum record remain unchanged.

The first `-text -eol` candidate passes independent byte-roundtrip controls,
but a separate staged-diff control exits 2 on the preserved captured line
delimiters. That failed candidate and its independent records remain preserved
under `captured-text-staged-review-20261006-c18-ace710` in the external D evidence.
It is not published as fully verified.

Revision 2 changes only an exact-path `.gitattributes` override to
`binary -eol`, with the corresponding modifier-date notice. Root will re-index
the already checksum-matching file without modifying its bytes. This makes the
committed representation match the historical captured identity and disables
conversion only for that one raw diagnostic. Git's binary macro also refuses
automatic text merge and presents binary diffs for the immutable captured bytes;
review must compare exact hashes rather than normalize those delimiters. No
broad text/whitespace rule, build/DataGen command, whole-tree cleanliness check,
product test, timeout or acceptance criterion changes.

Before publication, independently verify the actual attribute/notice diff,
the old and candidate blob identities, the historical checksum and unchanged
working-file bytes, and finite LF/CRLF checkout behavior. After publication,
the ordinary unfiltered hosted build/DataGen/diff/GameTest cohort must execute.
No v1.0 functionality, old result, native replay, current Solar source result,
R-021 policy or version Gate is accepted by this correction.
