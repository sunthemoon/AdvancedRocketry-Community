# V180-FUEL-DROP-02 — native drop/placement fixture readiness

Date: 2026-10-10. Status: in-progress. Root is author/integrator; independent
review uses a separate Codex worker session, without Claude dispatch.

## Source and ownership

Base: 6daca57def98225bbbb0d4be8bd713bf232053c0.
Isolated checkout: D:/GitHub/arce-v180-fuel-loader-readiness-20261010.
Branch: fix/v1.8.0-fuel-loader-readiness.
Root's source write_scope consists only of FuelLoaderPlacementGameTests.java,
EntitySections.java and this slice's new task/handoff records. Central status and
the canonical completion plan remain Root's integration scope. Existing Main
AGENTS, inherited untracked files and the deferred C19 tree-transport prototype
are not adopted, edited or staged. The already modified implementation log has
mixed deferred work and is excluded from this slice's staging.

## Observable outcome and existing evidence

The five loader native drop/place cases perform their original synchronous
round-trip and conservation assertions only after the fixture's entity section
is ready for native entity queries. They still fail on zero/multiple drops, wrong
items, changed owner/root/item metadata or failed native placement.

The existing [diagnostic](../v1.8.0-regression-observation/FUEL-DROP-DIAGNOSTIC-TASK-01.md)
and [failed cohort](../v1.8.0-ci/RESULT-33.md) remain immutable. A zero or absent
query does not prove a production omission or establish the unique historical
cause. Exact native query bytecode skips inaccessible sections. The same-package
EntitySections helper already guards other entity-dependent tests; these five
cases do not use it. Its opt-in pacing overload will allow the existing20-tick
deadlines to progress with normal server-length waiting, without changing any
existing unpaced caller.

## Contracts and non-goals

Preserve all six timeoutTicks=20 annotations, native destroyBlock/BlockItem.place,
the unfiltered nearby ItemEntity query and exactly-one predicate, carried-count,
raw-root, owner, native item, buffered-unit and refusal assertions. Prepare no
loader input before the asynchronous readiness wait, so automatic machine ticks
cannot change the round-trip operand while waiting. Keep the oversized-refusal
case unchanged. No production, schema, network, registry, asset, error exemption
or content disposition change. No new chunk tickets or forced-loading utility.

Do not weaken the test by filtering extra drops, bypassing native placement,
ignoring failures or expanding a timeout. No Gate/ADR/ledger/tag approval.

## Verification and status

Read-only investigation task:
D:/GitHub/ARCE-Task-Evidence/v1.8.0/fuel-loader-independent-20261010/TASK.md.
Root commands, receipts and results:
D:/GitHub/ARCE-Task-Evidence/v1.8.0/fuel-loader-root-20261010/.
Use finite independent checks of readiness sequencing, unpaced compatibility,
unchanged assertions and deadlines, plus actual candidate-bound clean build,
fresh unit tests, two DataGen/clean diffs and complete Forge GameTests. Check
disk space before each target and retain every failure and unwaived log.
Independent review and committed-source qualification precede integration.

The official hosted pacing regression run38056013604 at SHA30e89b0a completed
successfully on2026-10-10 at13:38:52Z. Root observed its run/job metadata, including
the successful complete Forge GameTest step, through GitHub's official REST API
and [run page](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/38056013604).
Native per-test raw counts are not inferred from job metadata. This is a new
terminal observation, not a change to the earlier sealed in-progress packet.
v1.8 remains IN_PROGRESS and all full-version Required Gate obligations remain.
