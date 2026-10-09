# C19 bounded Git-object transport 34

Date: 2026-10-10. Status: ready; implementation not started. Owner: Root.
Current version: v1.8 development under ADR-060. No Claude/nested delegation.
Source baseline: 631494559a808938b631eab07f0877225d4f6d9a. Its current
provenance/G4 postimages match the historical strict source 0ce. Read current
Main HEAD/status/worktrees/agents before claiming implementation.

## Outcome and authority

Reduce repeated verified Git-object startup overhead in bootstrap provenance
validation without skipping a request or reusing approval/mutable-file results.
Task33 measures the unchanged G4 call at 53.219968 seconds with 1134 profiled
subprocess initializations; this is not unique historical timeout proof.
Independent32 describes complete delegated predicates and failure cases.
Independent35 completes the finite observation-packet audit; correction01
withdraws only a module-instance profile attribution. Neither audit executes
the source or establishes a complete object-read count. Task34 remains unclaimed.
Complete Python/strict still fail at their original 180-second ceiling. This
task cannot mark C19 or any v1.8 Gate complete from a focused improvement.

The chosen scope is request-scoped bounded transport, not a global cache,
top-level validator reordering or acceptance-ready result reuse. Serve each
requested exact OID and recompute its type-qualified object SHA-1 every time.
Preserve all selected/import/audited/README commit distinctions, per-path mode
checks, raw-versus-materialized bytes and source/history predicates. Ordinary
bundle payloads and final rereads are not cached or bypassed.

## Proposed write scope and checkout

Root implementation uses a fresh worktree/branch fix/v1.8.0-provenance-object-session
based on the explicit source baseline. Record the actual new absolute checkout
at claim; never move an active reviewer's baseline. Source write_scope:

- scripts/validate_bootstrap_provenance.py;
- scripts/bootstrap_git_object_session.py (only if needed, stdlib-only private
  transport; no generic validation framework or circular dependency);
- tests/test_bootstrap_git_object_session.py (new focused protocol/lifetime cases);
- tests/test_validate_bootstrap_provenance.py (additive integration cases; every
  original method/assertion retained).

Root exclusively updates this task/checkpoint, canonical plan/status/log and
fresh external evidence. Build/registry/Java/resources/assets/IDs/ADR/ledger,
other validators and user/peer files are forbidden. No upstream import. Changes
needed outside this scope require a separately recorded revision before edits.
Malformed ADR enum/parser handling and G4 wrapper coverage are separate open
R32-02/R32-03 work, not silently combined with transport implementation.

## Required invariants

- Scope transport to one complete public provenance validation; direct/private
  callers remain usable. Nested/sequential/different-root invocations must not
  inherit a stale process. Always close/reset owned state, including exceptions.
- Retain sanitized Git environment/executable/repository selection and no lazy
  fetch, replacements, graft/config/attribute overrides under existing policy.
- Verify every returned header, requested OID/type, nonnegative admitted size,
  exact payload length, terminator and recomputed object identity. Missing,
  truncated, malformed or extra output never becomes a successful prefix.
- Preserve existing per-request size/timeout constants. Freeze explicit
  request-count, aggregate-byte and total-lifetime caps from actual current
  bounded caller analysis before writing the transport, never after a failure.
- Terminal EOF, child exit, trailing bytes, timeout, pipe errors and cleanup
  errors remain validation failures. If validation computes a result before
  terminal transport verification, deferred failures must reach its returned
  errors rather than allow a false success. No automatic failed-process retry.
- Bound retained/session memory and output without reading arbitrary response
  bodies into unbounded buffers. Own-process cleanup is not whole-tree or
  hostile-filesystem proof; state these unavailable guarantees accurately.

## Verification and delivery

Run genuine predecessor checks before edits; retain every failure/skip. Add
real Git positive cases and controlled protocol negatives for wrong OID/type,
size/count/aggregate/time bounds, truncation, undeclared/trailing bytes, nonzero
exit, write/read failure and final close; include scope reset, root isolation,
multiple requests and unchanged per-request verification after repeated OIDs.
Keep full old bootstrap/tree/ancestry/history/approval/materialization tests.

Commit/push the finite fixed source after exact scope/stat/whitespace review.
Fresh independent read-only/source-test review must inspect actual diff and
rerun key checks in its own checkout/Temp. Compare predecessor/successor real
canonical G4 outcomes and recorded object/launch costs. Use explicitly scoped
session/code-object counters for attribution, not filename/line/name profile
rows that can collide under dynamic module compilation; Root33 correction01
records that limitation. Profile numbers are not the only acceptance criterion.
Then execute required build, explicit test,
two DataGen/empty diffs, unfiltered GameTest and original 180-second broad/strict
attempts. Prior Markdown failures and native ERRORs stay open unless separately
resolved with actual evidence. No weakened assertion or extended qualification.

Check disks >=10 GiB; local evidence <=4 MiB, standard evidence <=100 MiB,
files <=50 MiB with bounded streams and exact owned TEMP/command receipts.
Observe initial outputs and terminal commands; future selected cleanup needs
full per-target ancestor/identity/proof/retained-log admission before a single
attempt. No takeover/retry of old refused, peer or unproven outputs. Only
committed, reviewed source and sealed evidence support delivered state.
