# Charged plug operation modes - owner decision02

Date:2026-10-04.Status:OWNER_CONFIRMED,implementation/native proof pending.

The owner selects refusal of charged operations that cannot conserve charge;
valid zero-charge operations retain vanilla behavior. This supplements the
[normal-removal decision](OWNER-POWER-REMOVAL-01.md),which retains FE in the
dropped plug Item and restores it on placement. It does not authorize a
creative-mode copying exception or deliberate charge loss under `doTileDrops=false`.

Implementation must reject a charged placement/clone/removal when its actual
mode cannot consume the originating charged Item or produce the required
conserving output. Refusal must precede any resource/block/item mutation or
publication;an unavailable consumption/drop is not a reason to discard FE.
The zero-charge exception applies only to validated supported zero-charge
state. Unknown/future/malformed or unresolved pending state is protected,not
read as zero;save protection,loaded-chunk and ordinary permission checks remain.

This decision resolves the earlier pending product rows,not runtime feasibility.
The immutable [inventory proposal](guard-inventory-proposal-01.zip) records its
earlier excluded/pending state;it is not edited or automatically accepted.
Independent review must separately state any exact inventory/preflight/native
test amendment needed before dependent implementation. No callback/charged-cut,
first-save,public API,physical hatch/lathe,ledger or Required Gate pass follows.
