# Owner decision of 2026-11-17: design and decision points go to Root

Channel: the owner's Claude Code Root session, 2026-11-17, at about 20:43, after Root's reports of 2026-11-13. The
open questions to the owner were: the environment for X01 and X02 (E1, E2 or E3,
[X01-X03-ISOLATION-PLAN](X01-X03-ISOLATION-PLAN.md)), and whether to sign R-044 after candidate C4
([R044-SIGNOFF-DRAFT](R044-SIGNOFF-DRAFT.md)). The owner wrote, verbatim:

> 那你是知道的，我不善做决策和设计，看你的了，纯蹲个成品

## Recorded meaning and scope

| Item | Meaning |
|---|---|
| Design and decision points | Root makes them all from now on, without asking: technical design, test design, the X01 to X03 approach, sequencing, the campaign plan's open choices that are design, and the work needed to make each switch ready. This extends the delegation of 2026-11-01. |
| The aim | A finished, playable v1.8 is what the owner waits for. |
| What stays with the owner | Under AGENTS.md sections 5, 7 and 13, three things stay with the owner. First, marking a Gate, the version or a release as passed, tagging, and merging to `main`. Second, accepting a residual risk by signature (R-044, and each switch's risk scope). Third, the real-client sessions that need the owner's accounts. Root prepares each of these so that it is a single yes or no in one final packet, with Root's recommendation. |
| Earlier instructions | They still hold where this message does not replace them. Production admission stays off until the final packet. Multiplayer and performance come last. A destructive test still needs the owner's word; Root chooses approaches that need none. |

## Root's follow-up decisions

| ID | Decision | Reasons |
|---|---|---|
| R456 | **The owner's words are recorded as above.** QUEUE and the status file point to this record. | Section 10 of AGENTS.md. |
| R457 | **X01 and X02 use option E4: application-level crash and fault simulation on this PC.** It needs no virtual machine, no system change, no root access and no destructive step. X01 records every file operation of the mod's checked writers through the existing `CheckedSavedDataFile` committer seam: each write, flush, atomic move and close. It replays every crash prefix into a fresh copy, keeping only the data that a flush or an atomic move made durable. Each copy then gets two starts with the existing oracles. X02 injects faults at the same seam: an I/O error, a write that reports success but is lost, a failure of the flush, the move or the close, and a full disk. Each failure is classified as seen and held, caught later, or not caught. The residual of real OS, file-system and disk-firmware behaviour, and of a real power cut, is stated in the report. A real virtual-machine power-off stays an optional later extension. | The owner asked Root to decide and to keep the work moving. E1 needs a system change and a reboot, E2 needs root access on the owner's node, and E3 leaves a risk decision with the owner. E4 tests the mod's own write protocol, where the mod's defects would be, with no owner action. |
| R458 | **R-044's signature waits for candidate C4**, as the draft's section 3 recommends. Root then gives the revised text in the final packet, covering both refusal reasons and citing C4's downgrade cases. The failed results stay failed. | The planned ledger root 3 would make an earlier signature lapse. |
| R459 | **Each of the five switches gets made ready, not enabled.** The bay gains a production admission constant beside the cargo kinds' constants, still false. The final packet then gives each switch's risk scope and evidence and Root's recommendation, and enabling one becomes a one-line change once the owner says yes. | It keeps the product one decision away from enabled, without accepting a risk on the owner's behalf. |
