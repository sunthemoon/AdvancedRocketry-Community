# TASK W61: sealed files with correction files in the Markdown check; R32-03 tests (C19-C)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w61-validator-corrections` (from the shared branch) |
| Branch | `codex/v1.8.0-w61-validator-corrections` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W61.md` (new) |
| Input | `records/W60.md` ("Sealed files" and "For Root"); `records/W58.md` section 1 (R32-03) and section 5 (slice C19-C) |

## Part 1: correction files in the Markdown link check

After W60, the strict validator's Markdown check (`scripts/validate_repository.py`, around lines
930 to 970) still fails on 32 links. All are in six sealed files, which AGENTS.md section 10 forbids
editing. W60 wrote a correction file next to each one, `<name>-CORRECTION-W60.md`, which gives the
corrected text line by line.

**Root's rule.** A broken link in a Markdown file F is excused only when all of these hold:
- a committed file next to F is named `<F's stem>-CORRECTION-<id>.md`;
- that correction file names F's line number of the broken link in a form the validator can parse
  exactly. Read W60's six files and use their existing form if it is unambiguous; otherwise say so
  and stop before changing them;
- the correction file's own links pass the normal check.

Anything else stays an error. In particular:
- a correction file that does not name the line;
- a correction file that is not committed;
- a correction file in another folder.

The validator reports the excused links as a count on its PASS line.

**Tests** (in the validator's existing test module for the Markdown check):
- excused when every condition holds;
- each missing condition fails, one test per condition;
- a correction file with a broken link of its own fails;
- the real tree: the Markdown phase passes on the repository with the six correction files.

## Part 2: R32-03

`validate_v002_g4_applicability(repository_root)` (`scripts/validate_v002_g4_applicability.py`,
lines 345 to 389), `_read_stable_file` and `main()` have no direct test.
`tests/test_validate_repository.py` (lines 2035 to 2081) only patches their return value.

Add tests to `tests/test_validate_v002_g4_applicability.py` that drive them on a temporary tree:
- a missing ADR file;
- an oversized ADR file;
- a non-UTF-8 ADR file;
- a linked (symlink or junction) ADR file, where the platform allows it; skip with a reason
  otherwise;
- an absent bundle and an invalid bundle;
- `main()` exit codes and output for a passing and a failing tree.

Change production code only if a test finds a defect. In that case give the fix its own commit and
say so in the record.

## Commands

- The modules you change: `python -B -m unittest tests.<module>` from the repository root, with
  `TEMP` and `TMP` set to `C:/Users/Administrator/AppData/Local/Temp`, because the short `ADMINI~1`
  path breaks some validator tests.
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Report
  every FAIL line. Expect the v0.1.0 asset failures until W52F merges.
- `git diff --check`. No Gradle run is needed.

## Not in this task

- Editing a sealed file or a correction file's content. Exception: if the line form is ambiguous,
  stop and report.
- Status files, the ledger and ADRs (Root).
