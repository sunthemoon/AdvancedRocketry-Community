# TASK W373: Slice E part 2a, the campaign families on the activated format

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w373`, branch `codex/v1.8.0-w373`, at the shared head with the activation merged (`5951d6df2` or later) |
| Decision | Root decision R505 (QUEUE row W373) |
| Design | [W357-DESIGN](records/W357-DESIGN.md), section 7.3 (the family table and the shared-parser paragraph), section 5 (bounds) and section 10 (R441's re-pinning rule) |
| Prior work | [W365](records/W365.md) (versioned Python readers and oracles), [W366](records/W366.md), [W372](records/W372.md) (the activation and its two native smokes) |
| Record | `docs/work/v1.8.0-root-queue/records/W373.md` |
| Write scope | The record; the Python runners and oracles of the listed families, `scripts/escrow_accounting.py`, `scripts/inspect_escrow_receipt.py`, `scripts/loader_cut_nbt.py`, and their tests, only to fix a defect a native run proves. Each fix gets a test that fails without it. No production code, no Java, no log manifest. |

## Why

`EscrowAccountingFormat.ENABLED` is now true on the shared branch, so every native world writes a root-3 ledger. W365
made the Python readers and oracles version-aware with synthetic fixtures only. Candidate C4 reruns the whole campaign;
this preflight finds the families that break on root 3 before then. It is development evidence, not C4 evidence.

## What to do

1. **Build a JAR** of your branch head locally (`gradlew --offline build` under the heavy-JVM lock) and record its
   SHA-256.
2. **Run these families** with `scripts/run_v180_integrated_campaign.py --only ...`, as a detached process, in one or
   more invocations, each with a new evidence directory:
   - the families section 7.3 says change: `cargo-C-S1`, `cargo-C-S2`, `cargo-C-S3`, `cargo-C-S4`,
     `cargo-C-S4-LOAD`, `cargo-C-S5`, `cargo-D-S1`, `cargo-D-S2`, `cargo-D-S2-LOAD`, `cargo-C-Q`,
     `cargo-B3-S1-REUSE`, `cargo-B3-S4-REUSE`, `cargo-B3-S5-REUSE`, `cargo-B3-S6-REUSE`, the six
     `w336-saturation-*` families, `r042-L_LEDGER`, `r042-U_LEDGER`, `r042-P_SERVER`, `r042-P_OWNER` and
     `r042-X_LEDGER`;
   - one sample from each group that only reads the ledger: `forced-A`, `released`, `exit`, `forced-W331-L`,
     `forced-W358-B11-ESCROW-MAJOR` and `forced-W363-B6-GOVERNED`.

   Do not run `cargo-G2-REUSE` or `w336-downgrade`; their historical failures stay, and W373B adds the root-3
   downgrade cases.
3. **For each family**, report the result, and check that fresh fluid cases take the KNOWN branch where section 7.3
   says so. A fresh fluid case that silently takes the legacy UNKNOWN branch is a defect, not a pass.
4. **Saturation counts.** The byte refusal of `w336-saturation-fluid-load`, `w336-saturation-fluid-unload` and
   `w336-saturation-fuel` is expected to come earlier, because each fluid operation carries one more row. Measure the
   new counts with the real JAR, give old value, new value and the arithmetic, and do not change any ceiling, limit or
   side-effect check (R441). Root accepts the numbers in a decision row.
5. **A failing family.** If a runner or oracle is wrong, fix it within the write scope with a test, rerun that family,
   and record both runs. If a family shows a production defect, do not fix production: record the evidence and stop
   that family. Never loosen an oracle, accept a broader version set without an explicit reader, or replace an
   `UNKNOWN` expectation with `KNOWN` in a string assertion (section 7.3).

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete server copies after each run. Launch detached Python with the real `python.exe`,
  not the pyenv shim. Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W373/`.
- Run the Python tests of every runner or oracle you change.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The last line of the record, and the last line of your answer, is the path of `records/W373.md`.
