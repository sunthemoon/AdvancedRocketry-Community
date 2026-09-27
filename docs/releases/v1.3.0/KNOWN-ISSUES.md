# v1.3 known limitations and acceptance gaps

This development build is not recommended as a stable replacement for a
backed-up world. The latest scoped review has no unresolved finding within its
tested fixture changes; the incomplete candidate matrix is not a zero-defect
claim. Report exact artifacts and reproduction steps as described in the
[compatibility guide](../../API-COMPATIBILITY.md).

| ID | Status and impact | Action / completion condition |
|---|---|---|
| KI130-01 | Open acceptance gap: inherited v1.0-v1.2 and current G0-G9 incomplete | Candidate owner must obtain actual evidence/precise approved dispositions before candidate freeze; no automatic inheritance |
| KI130-02 | Open recovery evidence: clean/staged recovery is not forced-crash or cross-file power-loss atomicity | Preserve backups; complete genuine interrupted-storage matrix, especially loader/rocket boundaries |
| KI130-03 | Unmeasured performance; finite runs retain tick warnings | Retain warnings and collect the defined reference workload at the ADR-018 trigger |
| KI130-04 | Low presentation issue: packaged `mod_description` still describes initial version metadata and further APIs as in progress | Update description during the next metadata refresh, before a candidate; do not relabel existing JAR hashes |
| KI130-05 | Client acceptance missing; satellite opening frame changed despite unchanged channel labels | Install matching host versions on both sides; complete V1/V2 and optional-integration matrix |
| KI130-06 | Supported-contract limit: synchronous callback checks cannot interrupt hangs or undo arbitrary same-JVM side effects | Install trusted integrations; a returned-time check is not process isolation |
| KI130-07 | Supported-contract limit: missing arbitrary third-party world blocks/items, no-drop destruction and corrupt native storage are not universally recoverable | Keep pre-change backups, exact dependencies and raw quarantined data; follow migration notes |
| KI130-08 | Compatibility scope: no current candidate Forge 47.4.23 or arbitrary modpack proof | Run explicit supported combinations and retain exact artifact evidence before advertising support |

KI130-04 is observed in
[`gradle.properties`](../../../gradle.properties), not a changed runtime contract.
It is recorded rather than silently altering the immutable artifacts underlying
this handoff. A package description correction must receive a new actual hash.

No issue here authorizes accepting known duplication, corrupt saves or an
authority bypass. Discovery of such a defect requires a bounded regression and
repair; it cannot be deferred merely because long-load testing is scheduled later.
