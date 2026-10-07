# Ordinary placement: primary target and invocation facts

Date: 2026-10-07. Fixed source:
`22f7d1cae0735a8b8c072165e7a3cf8089e3719c`. Status: factual research only.

Root reads/hashes the complete independent
[REPORT-01.md](D:/GitHub/ARCE-Task-Evidence/v1.8.0/placement-entry-primary-facts-20261007-r1-637dc2/REPORT-01.md)
(`31035f`, exit 0), 15,963 bytes /SHA-256
`06ce09e1ebc72bee7b0d665a57a1d3761156778f764e328e700aeafa4fabf320`.
The exact mapped Forge JAR is rehashed and only six declared <=512 KiB members
are parsed in memory; seven bounded operand-flow checks and 23 scalar controls
pass. No native body is copied and no JVM/native/player path executes. Original
instrument selector failures and qualified live metadata drift remain preserved.

Actual target reaches protected `BlockItem.placeBlock` through the returned
placement context before default `Level.setBlock`; clicked-hit position alone
is not the selected target. Replaceability/state/collision callbacks occur
earlier. These APIs are available adapter seams, not demonstrated hatch authority.
Forge placement events occur after `Item.useOn`, not pre-mutation admission.

One native UseOnContext is passed to both first-use and later ordinary item use.
It captures the player's current Level/hand source; server ItemStack.useOn passes
only context to Forge, which resolves the source Item from that context. Outer
receiver, player, stack or context identity alone is not authenticated invocation
provenance. A same-context first-use direct call is a static counterexample;
no exploit or installed-hatch bypass was run. Future final Root-owned adapters
may change reachable callbacks, but their actual restrictions need proof.

Java 17 StackWalker supplies possible frame/call-site observations, not receiver,
argument or local values. No stack mechanism, transformed mapping/frame bound
or packaged authentication test is adopted. The three ADR-068 sites remain
unchanged; no fourth interception, source activation or owner-policy selection.

The [assignment review](PRIVATE-OPERATION-REVIEW-01.md) still has two Medium
technical gaps. A separate coupled successor design is in progress, including
exact entry and provisional LOAD/availability/terminal bindings. Source bounds,
uncertainty/coverage protection, cross-store durability and generated/Proto/final
writer ordering remain distinct requirements. No physical hatch, save writer,
R-021 acceptance, ledger delivery or Gate closure. All factual-task reads and
native archive streams are closed/released; fixed inputs/JAR remain unchanged.
