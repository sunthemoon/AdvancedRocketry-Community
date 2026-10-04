# C16a-03 fluid hatch removal decision

Date: 2026-10-03. The owner explicitly selects retained controller fluid:
breaking a fluid hatch does not empty its controller-owned bank; rebuilding
the corresponding hatch allows that fluid to be used again after valid formation.
Draining that bank on hatch removal was not selected.

Accepted ADR-064 revision 2 already assigns Item/Fluid resources and transactions
to the controller. Item-hatch removal drops its assigned items at the controller
once; controller removal drains remaining fluid. This receipt clarifies fluid
hatch removal only. It does not approve an unreviewed schema, migration, arbitrary
bank reassignment, resource discard, crash residual or version Gate.

The clarification must enter the independently reviewed C16a-03 leaf contract
before runtime admission. Unsupported/unavailable controller roots still refuse
ordinary removal without loading that chunk. Unformed/unloaded controllers
retain their banks; final resource bounds and recovery tests remain mandatory.
