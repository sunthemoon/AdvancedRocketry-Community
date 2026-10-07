# C16a empty hatch: scoped outer-hook owner decision

Date: 2026-10-07. Source: root conversation's asynchronous question reply.
Owner: repository maintainer/user. Integrator: Root. Scope: ADR-068's three
listed ordinary-player invocation sites, not the entire C16 machine family.

The presented question asked whether ADR-068 may add a version-bound wrapper
only at those three sites, preserving the original behavior and delegating once.
It explicitly required independent review, save/exception-recovery contracts
and actual verification before physical hatch activation, without accepting
R-021 or exempting any Gate. The reply was:

> 允许限定拦截，审核后实现（推荐）

This is the new owner architecture selection. It supersedes the unanswered
choice in ADR-068 revision 1 and the physical lifecycle task's earlier status;
it does not retroactively approve original investigations or inactive source.
Revision 1 remains in Git; sealed original proposals/reports are unchanged.

## Confirmed scope and retained conditions

- One invocation in ServerGamePacketListenerImpl.handleUseItemOn; one in
  ServerPlayerGameMode.tick; one in ServerPlayerGameMode.destroyAndAck.
  Original receiver/arguments/return/throwable and exactly-once delegation remain.
- Independent architecture/ADR review and bounded operation/save/recovery
  contract freezing precede source implementation. Actual transformed/package/
  native/player/persistence verification precedes physical activation.
- No direct-mod/automation route, charged power hatch, controller resource
  facade, complete machine writer or general ASM/coremod migration is selected.
- R-021 remains OPEN, neither accepted nor extended. New uncertainty protection
  still needs documented object/chunk/Level scope, duration, logging cap and
  recovery; existing sticky refusals must remain unchanged.
- No existing assertion, deadline, resource budget, Gate, stable ID or stored
  player data may be weakened, removed or waived by this decision.

ADR-068 revision 2 remains PROPOSED until its final independent review and
remaining private/dependency/recovery contracts are resolved. No physical
interception assignment, build dependency, runtime hook or saved-data writer
is installed merely by recording the selection. The immediate v1.8 action is
independent review of the selected bounded architecture and its dependencies;
airlock fixture repair proceeds separately without requiring this exception.
