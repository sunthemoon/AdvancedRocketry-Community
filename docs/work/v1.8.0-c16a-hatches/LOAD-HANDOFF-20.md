# C16a provisional LOAD binding: handoff (sub-scope 20)

Author: delegated Claude design author, not Root, reviewer or approver.
Date: 2026-10-08. Base: `3815db7d09014f2f5f4b8af2a34459fa3bc77113` as stated
by the task.

## Delivered

- `LOAD-BINDING-20.md`: separate origin, current expectation, operation slot
  and provisional receipt; phase bindings to exact methods and lines; both
  save consumers; lifecycle boundaries; unresolved items U1-U7.
- `LOAD-TEST-DESIGN-20.md`: preservation oracles, binding oracles and the
  native/lifecycle proof still needed. Written only; nothing executed.
- External `REPORT-01.md` in
  `ARCE-Task-Evidence/v1.8.0/hatch-binding20-claude-author-20261008-01/`.

## Use

This is input for Root's coupled successor design and its independent review.
It is not an adopted contract and does not authorize source work.

## Coupled write unit, if ever authorized

H `prepareLoaded`, `recordCompletedLoad`, `saveAdditional` and a join
accessor; CO fields E, S and P plus `rawMatches`, `qualifyRecord`, private
`recordValidatedLoad`, `inspectOutgoing`, `matchesOutgoing`, `retireChunk`,
`close` and a new commit method; LW provisional lookup; GT provisional ticket;
AC `enterProvisionalLoad`; SP pass-through. These must ship with the real
entry (U1), the terminal and the native proof, not as an unused shell. Root
keeps registration, dependency, central and status files.

## Not done

No Java, test, hook, registry, schema, AGENTS, status, ledger, ADR or older
document write. No O1, O2 or O3 choice, startup policy, source cap, durability
claim, new hook or storage policy.

## Remaining obligations

U1-U7, independent review of the whole successor, owner dispositions O1-O3
and the native facts listed in the test design.

## Rollback

Delete the three new files; nothing depends on them.
