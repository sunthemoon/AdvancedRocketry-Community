# C18c-01-NATIVE: vanilla inventory advancement restart fixture

Date: 2026-10-04. Status: READY_FOR_CONTRACT_REVIEW. Integrator: Root.
Depends on the frozen six-tutorial task and independently reviewed candidate01;
the candidate is being integrated separately and has no native delivery yet.

## Outcome and boundary

Verify the packaged server's actual inventory listener and vanilla advancement
save/load for the six tutorial IDs across two clean process runs. This is a
server-connected automation fixture, not a real client, GPU or V1/V2 result.
Use only a fresh disposable copied world and pinned artifact/libraries, with
source-world and artifact pre/postchecks. Never alter the source world or an
original player's files, and never turn an aborted attempt into restart PASS.

A small test-only fixed console command exposes only seed/reload actions. It
requires both release-test hooks and a separate classicInventoryFixture opt-in,
permission 4, no source entity and the actual server thread. There are no
coordinate, item, UUID or player-name arguments. The two fixture UUIDs/names
are fixed synthetic identities, non-operator/non-FakePlayer ServerPlayers over
the existing ConnectedTestPlayers pattern. Reject already-connected identities
before mutations; create and remove only the fixture's own connections in
try/finally. No mutable static player map or world state is allowed.

Only the normal inventory change/listener may award criteria. Do not use
advancement grant, direct award, CriteriaTriggers invocation, custom progression
stores, outboxes, NBT roots, packet protocols or resource/recipe rewards.

## Behavioural and restart requirements

In the first run, inspect fresh criteria for both identities. Drive the six
inventory targets for the first player, an unrelated item for the second, and
check per-player isolation, unchanged resources/experience and empty tutorial
rewards. The existing registered GameTests cover wrong targets, partial suits,
current-inventory conjunction, display ancestry and replay separately.

Remove the targets through ordinary inventory changes before player save and
clean server stop. The copied vanilla player-data inventory must then be empty.
Preserve the exact UUID advancement JSON criteria and their saved timestamp
strings. Native CriterionProgress serializes timestamps at second precision;
do not compare falsely exact pre-save millisecond timestamps with reload.

In the second run, join the same identities with empty inventories and inspect
loaded progress before any target acquisition. Compare saved criterion timestamp
strings/normalized seconds with the first run. Replay acquisition through the
normal listener, check receipts and resources remain unchanged, clear items,
remove owned players and stop cleanly. Inspect the second saved progress and
input preservation. Reload must be distinct from fresh inventory re-earning.

Observations are finite, fixed-field, bounded server-thread records. A narrowly
scoped Python driver validates fresh framing, phases, identities, all six IDs,
criteria/timestamps and bounded vanilla files, rather than trusting a hook's
success label. Unrelated vanilla/recipe criteria may react legitimately and
are not claimed as tutorial rewards or reset. Preserve actual raw failures.

## Ownership and verification

After independent leaf review, delegated writes may include only a new
gametest/ClassicInventoryAdvancementNativeFixture.java, a new
scripts/run_v180_classic_inventory_smoke.py and its new focused Python test,
plus own NATIVE-PROGRESS/NATIVE-HANDOFF in this task directory. Root owns all
central code/resources/status/ledger and every commit/push. Do not modify the
already frozen six candidate source files. No hook is enabled by normal play.

Independently review actual code and controls; commit source before delivery
evidence. Run full checks, actual registered GameTests, parser negative controls
and the pinned packaged two-run fixture. Only after all six whole-unit
obligations pass may Root consider their ledger updates. Whole-v1.8 real-client,
source/asset, performance, R-021 and G0-G9 obligations remain separate/open.

Use project-parent D helpers/output and per-process TEMP/TMP/TMPDIR/Java temp.
Check C/D >=10,000,000,000 free bytes before Java/native launches. Cleanup only
fresh owned stopped copies using checked native literal paths; policy-rejected
deletions remain explicit and are not bypassed. No private bundle/huge inherited
ZIP, source-world, sealed-evidence or other-agent file changes are permitted.
