# TASK W154: container isolation for N3-B2's retained disassembly window (design amendment, Root decision R91)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md), [records/W148-DESIGN.md](records/W148-DESIGN.md)
(sections 3.2 and 5), the W148 review's implementation prerequisite 1, Root decision R87 and R91 (QUEUE), and W152's
record (`records/W152.md` on branch `codex/v1.8.0-w152`) with its six probes in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W152/` first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w154` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w154` (local commits only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W154-DESIGN.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W154/` (probes, if any) |

## The finding

W152 showed that extraction from vanilla containers (hoppers, other mods through the item capability, player or block
access) bypasses the region locks and capability invalidation that N3-B2 relied on. While U is retained, items taken out
of a restored container would be restored again by a rollback after a crash, so the window is not isolated.

## Root decision R91 (2026-10-24)

Keep the supported set as it is (no narrowing), add no Mixin (that would need an ADR and the owner), and accept no
duplication residual. Design an isolation that works within today's architecture, for example:

- **deferred contents:** the disassembly places containers empty and keeps their contents in U; the contents are
  inserted only at the acknowledged completion step, so nothing exists to extract during the window, and a crash before
  completion restores from U with nothing taken;
- or another mechanism you can show is complete for every supported container and its capability, including startup
  ordering and chunk reload.

## What to write (no code)

1. The windows and accesses W152 observed, and which mechanism closes each (vanilla `Container`, the item capability,
   comparator and hopper ticks, player menus, block breaking, explosions, chunk unload and reload).
2. The mechanism, its write and acknowledgement order with U and the terrain transaction, and what a crash at each step
   restores, so that every item exists exactly once.
3. What players and operators see (a container shown empty until completion, for example), its duration and recovery,
   for Root's R-035 text.
4. Constraints: no saved format, schema, bound, lease, budget or timeout change unless unavoidable (then state it);
   bounded per-tick work; nothing that blocks the server thread.
5. Tests that prove it: unit, GameTest with a hopper and an extracting capability, and native cuts with two restarts.

An independent read-only review by a fresh Codex session follows; Root then resumes W152.

## Verification

Read-only source inspection and probes kept in the evidence folder; no production change; the repository validator.

## Not in this task

Implementation (W152 resumes it), a Mixin, narrowing the supported set, ADR and risk-register edits, status files and the
ledger (Root).
