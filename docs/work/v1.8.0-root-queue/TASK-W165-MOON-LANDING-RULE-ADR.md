# TASK W165: a landing-rule ADR for Moon arrivals on the heightmap (design only)

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

## Owner decision

On 2026-10-26, in the Claude Code Root session, Root asked: "Moon landings: a landed rocket can float above lower
terrain because of the y=80 floor (finding F2). Keep the floor, or have a landing-rule ADR drafted that lands on the
heightmap?" The owner chose "Draft heightmap ADR (Recommended)". That option's text: a worker drafts the ADR and the
W156 plan, and the owner accepts the ADR before any code.

Read [records/W156.md](records/W156.md) first. It traces the floor's history, what it protects and the tests that
assert it. Then read:

- [ADR-033](../../decisions/ADR-033-PLANETARY-WORLDS-AND-SURFACE-ADMISSION.md) (physical admission, lines 75-81);
- [ADR-063](../../decisions/ADR-063-MATERIALS-ORES-AND-PLANETARY-SURFACES.md) sections 5 and 9;
- [ADR-006](../../decisions/ADR-006-ROCKET-CROSS-DIMENSION-TRANSFER.md);
- the selector `RocketLandingPadSelector` and `SafeCelestialTravel`;
- `MoonArrivalGameTests` and the W83b arrival-readiness path (QUEUE W83b row).

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w165` |
| Branch | `codex/v1.8.0-w165` from the shared head that records this task (local commits only; Root merges) |
| Output | `docs/decisions/ADR-075-MOON-LANDING-ON-THE-HEIGHTMAP.md` (status PROPOSED) and `docs/work/v1.8.0-root-queue/records/W165.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W165/` (only if you run probes) |

## What the ADR decides

1. **Where a new Moon arrival lands.** Over loaded terrain it lands on the real surface. Name the exact column rule
   (for example the highest motion-blocking block under each footprint column) and the support rule. Moon is no
   longer exempt from ground support.
2. **A footprint without ground.** All-empty or partly empty columns, craters and the void. Pick a rule: search
   further within the existing candidate and inspection limits, or refuse with an existing refusal and keep T. Raising
   the rocket into unsupported air is not allowed.
3. **What stays.**
   - Existing pads and claimed landing sites keep their positions.
   - Captured recovery identities and a rocket already in flight under the old rule keep working.
   - Stations and other bodies do not change.
   - Name exactly which arrivals keep Y=80 behaviour, if any.
4. **Limits.** Bounds, inspection budgets, reservations, pad and footprint exclusions, passenger placement and W83b's
   readiness wait stay unchanged. If the rule needs more inspection, say how much and why, and stop for Root.
5. **Tests.**
   - The two `MoonArrivalGameTests` cases change meaning: list them as named test transitions with their new
     assertions.
   - Add cases for empty and mixed columns, pads, passengers, recovery identities and restart.
6. **Supersession.** List the pointer notes that ADR-033 and ADR-063 sections 5 and 9 get on acceptance.
7. **Saved data and protocol.** State whether either changes; the expected answer is no.

## Also deliver

- The record W165.md: a short implementation plan for W156's resumption, with the classes, the tests that fail first,
  and the verification (unit classes, a complete GameTest with the unchanged checker, and a native restart case for
  recovery identities).
- Source inspection is enough. Probes are optional. No production or test code changes.
- Run `python -B scripts/validate_repository.py --require-approved-identity` with the new files staged.

An independent read-only review follows. The owner accepts the ADR before any code.

## Not in this task

- Implementing the rule (W156 resumes after acceptance).
- Other bodies' landing rules.
- The status files, the ledger and the risk register, which stay with Root.
