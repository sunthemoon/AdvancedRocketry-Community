# C16a-03 fluid-bank retention amendment

Date: 2026-10-03. Status: proposed; independent review pending.
Author: root integrator. Target: ADR-064 revision 2, section 1.2.

## Authority and scope

The [owner decision](OWNER-DECISIONS.md) selects preservation of controller-owned
fluid when a fluid hatch is removed. This amendment states that behavior in the
accepted batch contract. It does not freeze the yet-unimplemented family codecs,
bank-key encoding, pattern assignments, menus or resource adapters.

Only ADR-064's revision/acceptance metadata, the removal paragraph below and a
review-history entry change. All other sections, root names, versions, numeric
limits and existing v1.2 machine behavior remain unchanged. Revision 2 stays
canonical until independent review is resolved under the owner's conditional
authorization.

## Proposed replacement in section 1.2

Replace the paragraph beginning "Unforming or unloading" and ending
"An unbound new hatch is empty" with:

> Unforming or unloading retains the banks at their controller. Breaking an
> Item hatch removes and drops its assigned Item bank at the controller once.
> Breaking a Fluid hatch retains its Fluid bank at that controller; a rebuilt
> corresponding hatch can access that same bank only after valid formation and
> a new exact instance/generation binding. The retained bank is not a resource
> copy in the removed or rebuilt hatch. Binding removal or generation changes
> alone never empty or duplicate it. Retained inactive banks count toward the
> existing 64-bank and 32,768-byte controller-resource limits. Their stable
> identity and assignment must be independently reviewed in the C16a-03 leaf
> contract before implementation; a rebuild cannot silently discard a bank or
> reassign its fluid to an unrelated hatch. Breaking the controller drops all
> remaining Items once and drains the remaining Fluids without spawning another
> resource copy. Removal of a bound hatch while its controller is unavailable
> or its root unsupported fails closed for ordinary players, explosions and
> Forge-aware entity destruction; it never loads that chunk. An unbound new
> hatch is empty.

## Verification and admission

- Independently compare the actual amendment against revision 2 and the owner's
  receipt; record findings and unchanged sections/limits.
- Before runtime admission, cover removal/rebuild with filled input and output
  Fluid banks, unformation, both chunk-unload orders, stale capability handles,
  changed generation, wrong bank role, resource limits, and controller removal.
- Native cross-chunk saves and at least two restarts must retain exactly one
  controller resource copy. Unsupported roots refuse mutation/removal intact.
- No implementation, formation proof or version Gate is established by this
  amendment. C16a-03 remains in-progress/contract exploration.
