# TASK W512: prepare round 09's new inputs while round 08 waits for the night, without starting the game

Root: Claude. Date: 2026-11-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w512`, branch `codex/v1.8.0-w512` from the shared branch, whose harness has W498's baseline launch and W501's pairing and JEI-only baseline |
| Decision | Root decision R739 (QUEUE row W512), following R724, R728, R734 and R735, under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in TASK W489 applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Sources | [W506](records/W506.md) and round 08 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w500/round-08/` (read only; it runs tonight); [W500](records/W500.md) for the baseline smoke, the collector spans and the stopped sessions; [W491](records/W491.md), [W496](records/W496.md), [W498](records/W498.md), [W501](records/W501.md) and [W503](records/W503.md) with their packages under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/` (read only); [W509](records/W509.md), [W510](records/W510.md) and `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w509/` (read only); QUEUE rows W500 to W510 (R723 to R735) |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w512/round-09/`, with your notes in `round-09/w512/` and an inventory `w512/delivery-inputs.json`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W512.md` |
| Time | Deliver by **20:00** local time |

## Why

Round 08 runs tonight from `authoring-w500/round-08/`. The inputs that do not depend on its results are ready now:
the frame-time captures (R728) and the four recaptures as amended by R735. Round 08's carry-over is added from one
input file after round 08 is sealed, as round 07's was.

## What to do, in this order

1. **Copy, then verify.** Copy round 08's tools and every file they read from W506's package into `round-09/`, after
   checking each against W506's inventory (`round-08/w506/delivery-inputs.json`, SHA-256 `bc4fd897...`). Copy only
   what the tools need; do not copy round 08's night evidence, which does not exist yet. Never write inside
   `authoring-w500/` or any other source package.
2. **Rebind and set the window.** Bind the tools to this worktree and set every time guard to 2026-11-29 23:30 to
   2026-11-30 07:30, with the 07:43 input deadline and the 07:45 stop, as W500 and W506 did, with the same test.
3. **The recaptures (R734, R735).** Bring in W509's I04 and J18 recaptures unchanged, after checking them against
   W509's inventory (`authoring-w509/delivery-inputs.json`, SHA-256 `b7bfa785...`), and change two items:
   - **J19 step 1.** Do not use W509's JDWP observer or its launch hook. Beside each of J19's 96 frames, add
     `data get block` receipts of the controller's identity root, its `arce_classic_machine` structure and the
     `arce_classic_hatch.energy` of its power-input hatch, with the harness's existing operations (W510, section
     Recommended readback). Name each value's source in C6.
   - **A14 step 1.** Put the Earth lift-off frames in a separate short session that boards and launches the rocket
     with the wide camera, so that `a14-r6-s0` keeps its original options and frames. Leave W510's A14-1 timing
     concern out by keeping only the frames, the ridden ASCENT receipt and the Overworld receipt that step 1 needs.
4. **The frame-time captures (R728).** Activate W491's collector spans, W503's 19 replacement sessions and the
   paired and JEI-only baselines that W501's harness now supports, with W500's rules: PresentMon 2.6.0's pinned
   hash, PID-only selection, the unchanged warm-up, capture and timeout, and the four-clip video limit. Every
   capture session must refuse to start unless Root's `baseline-qualification.json` exists and is bound to round
   08's smoke evidence, as W500 designed. A session whose world cannot pair (W502: K07 and K21 are refused) stops
   with its reason, not with a substitute.
5. **Order.** Recaptures first: J18, the A14 lift-off session, J19, then I04's two JEI-present cells. Then the
   frame-time captures in W491's runbook order, baselines before the candidate sessions they pair with. Leave an
   empty `carry-over.json` for round 08's carry-over. Show the order with each row's estimate against 23:30 to
   07:30 with the runner's dry check, without Java. Rows not reached stay NOT_RUN for round 10.
6. **Keep it runnable.** From this worktree, run and record the dry validator, the operator tests, the three dry
   checks and the harness tests `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q`.
7. **Inventory and record.** Write `w512/delivery-inputs.json` with every file of `authoring-w512/`, its size and
   SHA-256 and the checks of steps 1 and 3. The record lists the copied inputs, every changed file against round 08
   and W509, the J19 and A14 changes with their source citations, every stopped session with its reason, the order
   with its estimate, the commands, and the result of `python -B scripts/validate_repository.py`, whose `Summary:`
   line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- Do not start any Minecraft client, server, Gradle task, PresentMon or other JVM. Make no change under `src/`
  (frozen at C6, R600) or `scripts/campaign_client/`.
- Never write inside rounds 01 to 07, `authoring-w500/`, the other authoring folders or the judgment packages, and
  do not touch the worktree `D:/GitHub/arce-v180-w500`, which runs tonight.
- Never create `baseline-qualification.json`; only Root writes it.
- Never close, change or block a program you did not start.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
