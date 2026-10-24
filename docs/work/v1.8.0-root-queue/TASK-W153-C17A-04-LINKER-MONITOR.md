# TASK W153: C17a-04, the full linker and the rocket monitoring station

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md), ADR-065 sections 4.2 (docked infrastructure, the
linker) and 4.3 (the monitor) in [ADR-065](../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md), the
ledger rows `block:monitoringStation` and `libvulpes:java/itemLinker` in
[v1.8.0-content-ledger.csv](../v1.8.0-content-ledger.csv), the records of the leaves that built the linker's
rocket-binding half and the loaders (W11b3's fuel loader records and the cargo devices' W109 and W120), and the
provenance rules in [docs/08-ASSET-LICENSE-AND-PROVENANCE.md](../../08-ASSET-LICENSE-AND-PROVENANCE.md) first. You are a
new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w153` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w153` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W153.md` (new; a one-page plan at its top before any code: what exists, what ADR-065 4.2 and 4.3 still require, the slices you will commit) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W153/` |

## What to do

Implement what ADR-065 sections 4.2 and 4.3 specify and the code does not have yet: the full `linker` (binding loaders,
monitoring stations and pads server-side, with its schema-1 stored selection) and the rocket monitoring station (status,
height relative to the captured origin, and the redstone launch through the server flight path, governed where the
rocket is escrow-governed). The server decides every binding and launch; a C2S packet is validated for player,
distance, permission, state, range and loaded chunks, and never loads a chunk from a client position; NBT and packet
sizes are bounded. Art, data, recipes, bilingual text and provenance follow the same rules as W109's and W120's
resources (a pre-authoring declaration before authoring; no upstream asset without a provenance entry). Production cargo
and fuel admission stay as they are.

If ADR-065 leaves a behaviour open, choose the smallest reading, state it in the plan, and keep it consistent with the
existing flight and escrow services; if a change to a saved format, protocol field or ADR text seems needed, stop and
report it.

This slice adds a saved item field and a network packet, so an independent read-only review by a fresh Codex session
follows before merge.

## Verification

Unit tests for the binding and launch rules, GameTests for the linker, the monitor's status and height and its
redstone launch (including a refused launch), runData twice with a clean tree, the art screen and the provenance check,
the whole unit suite and one complete GameTest with the unchanged log checker, under the heavy-JVM lock with the atomic
pattern and the hand-off rule. Check C: and D: above 10 GiB and 6 GiB free memory first.

## Not in this task

The satellite bay (loader/1), the cargo loader ledger rows (they close when admission is enabled), enabling admission,
real-client V1 and V2 evidence, ADRs, the risk register, status files and the ledger (Root).
