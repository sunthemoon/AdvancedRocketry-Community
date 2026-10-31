# TASK W230: design of the satellite bay's safe passenger exit (D1b), design only

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. Design only: no
production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w230` (new; branch `codex/v1.8.0-w230` from the shared head named in your prompt) |
| Output | `docs/work/v1.8.0-root-queue/records/W230-DESIGN.md`, committed on your branch |
| Owner decision | D1b in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md): "先设计安全退出位置与许可（简报推荐）": design, then implement and review, a server-side safe exit position and permit, passenger identity and recovery, without changing playerdata authority or cancelling dismount, before bay admission |
| Sources | [W27A-DESIGN](records/W27A-DESIGN.md) sections 4, 8, 8.1 and 10 (risk entry C); [W127-PLAN](records/W127-PLAN.md) Q2 (a dismount is never cancelled; a passenger can leave the waiting rocket 48 blocks up and fall while T keeps the seat); risk R-042 and R-043 in [the risk register](../../11-RISK-REGISTER.md); ADR-065 near line 1393; `RocketEscrowInterlocks`; the bay dispatch and receipt code under `rocket/` |

**Root decision R223.** The owner chose to design a safe exit before bay admission is turned on.

## What the design settles

1. **The position.** A server-side rule for where a passenger who leaves a held rocket is placed:
   - which candidates are tried, in what order and how many, with a hard per-tick budget;
   - what counts as safe (collision, support, fluids, hazards, world border, loaded chunks only, no forced loading);
   - what happens when no candidate is safe. Dismount is never cancelled, so say what the fallback is and why it is
     acceptable.
2. **The permit.** What lets a passenger exit (owner, crew, operators), how it is checked on the server, and how
   it relates to the seat that T keeps.
3. **Identity and recovery.** How the seat and the passenger are tied together while the bay holds the rocket. What
   happens on logout, death, dimension change, server restart and crash windows. The playerdata authority does not
   change: say exactly which existing records are read, which are written, and when.
4. **Dedicated and LAN.** Behaviour with two real clients, and what the V2 evidence must show.
5. **Risk and tests.** The R-042 text that changes, the unit tests and GameTests, and the two-client checks that
   the implementation must add.
6. **Implementation slices.** One or more slices, each with its write scope and size, and the classes that are near
   the 500-line and 800-line thresholds.

State the alternatives you rejected and why. Mark every open question for Root or the owner.

## Verification

The repository validator. Root reads the design; an independent review follows before implementation starts.

## Not in this task

Code, the per-owner pending cap (W231), bay admission itself, status files and the ledger.
