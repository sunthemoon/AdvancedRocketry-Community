# Private canonical digest: qualified source integration

Date: 2026-10-04. Status: reviewed private calculation committed and regressed.

Root applies exactly the two new files reviewed in the [independent evidence](reviews/source-review-01.zip)
(`REVIEW-01.md` inside that packet).
Production SHA-256 is `6ca5b028317766e9106953a011134096e896b08b557db041eb0c92ad493b5ec8`;
test SHA-256 is `cf295730469021a70dc2eba85d030379860b16388025359eafb249a526d08c27`.
The existing code baseline is immutable `3f3d62aed3980186fe0acc9592cf93ca436405fb`;
Root's subsequent documentation tip is `37286766e75688b1f2ddb4784adb55576145ba5d`.
The exact two-file addition is committed and non-force pushed at
`1ece7e9d2515003ca705b5634ef654a6bcae8f89`. It makes no ledger change.
[Root regression](VERIFICATION.md) records actual current-version commands and
the completed, separate different-agent regression-evidence audit. Admission
remains limited to this private computation, not any resource or save consumer.

The source reviewer identifies no introduced Critical/High/Medium/Low finding
in that two-file scope. Actual author checks pass 15 cases in one suite;
independent checks pass 23 cases in two suites, including eight separate
controls, with no failure/error/skip. Reviewer execution exits 0 in 40.433907
seconds; compileJava uses the recorded cache, while compileTestJava and test
execute. Tooling failures remain in separate raw evidence, not rewritten.

Report SHA-256 is `bf4c35622a6af1c8d80215c16bec9ee2a6efbefba88af83cb1f7a165a7e5402a`;
the internal manifest SHA-256 is
`f7f0dc8cf5f78213456c70694ff39d5ef165968cc13d6966e796f59f5aadf133`.
Root verifies all 145 manifested files and preserves the 147 original payloads
including manifest/checksum companion, 4,081,024 uncompressed bytes. The compact
Root packet has 147 entries and the archive identity recorded separately in
the integration receipt. Input Markdown snapshots stay inside the packet as
evidence rather than being published as live documentation. No JAR or complete
source-tree export is added. The original reviewer loose bundle is unchanged;
Root's redundant untracked loose copy is preserved externally on D.

The only surface is package-private String-returning
`ClassicNbtCanonicalHash.sha256(CompoundTag, ClassicNbtLimits)` over unchanged
K2 bytes, using a fresh per-call JDK SHA-256 and lowercase hexadecimal output.
K1 order, native framing, K2 eligibility, ownership and fixed/stricter limits
remain unchanged. The unavailable-provider branch is statically reviewed but
not injected; finite different hashes do not prove general collision freedom.

Admission permits only this private calculation and Root automatic regression.
It does not admit resource capture/getters, full hash/frame/native codecs,
GuardTicket, physical hatches, persistence, charged-mode interception, natural
negative-zero round-trip, native/client evidence or any version Gate. Existing
save-refusal risks and all unfinished current-version features remain open.
