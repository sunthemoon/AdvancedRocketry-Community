# Current-seed input amendment: development source record

Date: 2026-10-07. Task: [SEED-INPUTS-TASK-02](SEED-INPUTS-TASK-02.md).
Status: implemented-unverified; pre-publication source snapshot, not repair.
Base: `ff56fd7e2d394bc4dc31005b3be05186597f7cd1`.

This historical development snapshot precedes review/publication; the separate
[SEED-INPUTS-INTEGRATION-02](SEED-INPUTS-INTEGRATION-02.md) binds the later exact
source commit, different-author review, normal cleanup and dated hosted status.

Only the existing native GameTest family's initial-supply failure diagnostic
changes executable instructions. A private helper appended after all previous
observations captures one guarded current seed block registry ID/isAir, its
own-airlock properties if applicable, and later native sky/height operands.
The original supply predicate, successful path, assertions/messages, deadlines,
seven subjects, six cases, native actions, controlled calls, budgets and cleanup
are unchanged. No production/resource/save/config/registry/network change.

The separate proposal is adopted with one explicit formatting refinement:
oversized registry IDs emit a fixed sentinel rather than a truncated prefix.
Namespace/path lengths are checked before constructing their combined text.
The 256-character ID and 1,024-character segment limits do not change registry
admissibility. The helper has one nonloading chunk lookup, one direct seed-state
read and at most one sky and one height sample, with build/loaded guards and a
loaded recheck before height. Native getter/cache internals are not counted as
one primitive read or qualified as effect-free by this static record. Sampling
both operands does not claim that the earlier production short-circuit branch
read both. Neither current identity nor operand samples prove first-scan cause.

The separate nonfatal fallback returns only UNAVAILABLE, preserving already
collected observations and the original supply assertion. It does not swallow
VM exhaustion or ThreadDeath. There is no traversal, retry, ticket, repair,
resource debit, arbitrary state/property/NBT dump or exception content.

Postimage: 41,616 B /574 lines /SHA-256
`cc1278a666c80d667ac0eb4546cb9afa8a4993686126cc93aa8063d1da359a90`.
The existing >500-line responsibility review remains limited to private
failure evidence for this same native test family; no production/domain or
generic tracing responsibility is introduced. The class remains below 800.

Root's `python -B check01.py` (`3a9a61`, exit 0) writes
[CHECKS-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-seed-inputs-root-20261007-01/CHECKS-01.json).
Sixteen static controls pass, including exact normalized fixed-base restoration
after removing only two imports, one append and the private helper. Planning,
accepted-ledger and whitespace checks exit 0. These are source-level controls,
not Java compilation, native purity, branch coverage, recovery or acceptance.
Different-author actual-diff review and fixed committed-source hosted clean
build, twice DataGen/cleanliness and unfiltered GameTests remain pending.
Local C is below 10 GB; no local JVM/Gradle/native work runs. The three required
native failures, unwaived errors, all content obligations and v1.8 G0-G9 remain
open. No R-021 acceptance, physical hatch enabling or Claude execution occurs.
