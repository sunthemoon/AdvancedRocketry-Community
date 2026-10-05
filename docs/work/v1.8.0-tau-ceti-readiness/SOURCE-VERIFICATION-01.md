# Destination-readiness source checkpoint

Date: 2026-10-06. Status: implemented-unverified, not verified delivery.

The [independent source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/tau-ceti-readiness-review-20261005-72a461/REVIEW-01.md)
finds no C/H/M in the two production diffs and new native test declaration. Root
imports all five exact author postimages via `apply_patch`; their hashes match
the source manifest before the documented handoff correction below.

One Low concerns publication order. The Root handoff copy now distinguishes a
reviewed, unverified pushed checkpoint needed to trigger hosted execution from
verified delivery after qualified execution. The original author worktree/seal
and original independent finding remain unchanged. No Java source changes for
this wording correction. Root's first correction patch fails its incomplete
line-context match before edits; the fresh exact-context patch is separate.

The reserved destination origin must have loaded entity data and entity-ticking
eligibility before creation/phase advancement. Existing fuel, phases, schema,
ticket/recovery budgets and pad revalidation are retained. The source service
remains package-private; only its spawn method gains package visibility for the
new actual-call-site test. The original cold Tau Ceti test remains byte-exact,
SHA-256 `a18b481ac8cd256ee27d8b0a4ed5661c62a489252ee575dd6dad3728fd9a18d3`.

Java compilation, the new 300-tick native test and the unchanged cold full flight
are unrun for this candidate. Hosted run 37333529725 predates it and still fails
Tau Ceti and disabled-gravity assertions; that is not proof this narrow readiness
change fixes either failure or their unique causes. A later exact committed
hosted result must supply fresh build/JUnit/DataGen and full GameTest evidence.
Native persistence/restart, real clients, ledger delivery and G0-G9 remain open.
