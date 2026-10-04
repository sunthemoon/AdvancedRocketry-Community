# SETUP05 handoff

Date: 2026-10-04. Status: READY_FOR_REVIEW, development candidate only.
Root is the sole integrator. No worker commit, push or native admission exists.

## Checkout, implemented scope and exact source

Registered worktree: `D:/GitHub/arce-v180-guard-forced-setup-20261004`.
Branch: `codex/v1.8.0-guard-forced-setup`.
Fixed base: `73fc017ac3b121b34216e660e66b0cb583ff289a`.
The only changed source/test files are:

| File | Bytes | SHA-256 |
|---|---:|---|
| `scripts/run_v180_guard_lifecycle_smoke.py` | 23,600 | `7eea858bc9808e035252f79ae56970bea996ddde80b3a1c4e3128cbff5fcb77a` |
| `tests/test_run_v180_guard_lifecycle_smoke.py` | 35,993 | `27fd2c7d11f3d1e664d833f59a849a2ae48bb27c17133611bf4b4c5e0955082f` |

The two new records are SETUP05-PROGRESS.md and this handoff. Central files,
Java, build, status, original task, AGENTS and other agents' work remain read-only.

## Fixture design and mutation permissions

The initial source and copied metadata require the exact original 79-byte
SHA-256 `c9b7030c645d2995ac9be13d08964eb98f1942b2170c34b3f893100daa6acc50`.
The parser reuses the existing bounded NbtReader with native type retention.
Compressed and expanded metadata each cap at 4,096 bytes; existing depth16,
tag4,096 and collection4,096 parser ceilings are not raised. The accepted fixed
shape has an empty-name compound root, exact data/Forced TAG_Long_Array and
DataVersion TAG_Int3465, no unrelated fields or duplicates, and exactly four
native tags. Bootstrap requires the exact five unique marks; retained phases
require only (11,11) and (32,32). Native array order may differ, but fields/types,
version, uniqueness and membership may not. Trailing/concatenated gzip, malformed,
unknown and oversized inputs refuse rather than repair.

After the original target add/LOADED observation, a single 60-second setup
deadline covers protected positive queries, existing spawn relocation and the
fixed copied-world removal batch. Only the first cycle issues:

```text
execute in minecraft:overworld run forceload remove 256 256
execute in minecraft:overworld run forceload remove 256 -32
execute in minecraft:overworld run forceload remove 384 384
```

Target176,176 and far512,512 are positively queried before and after setup.
Exact authoritative native acknowledgements must occur freshly and uniquely
before the corresponding fresh say barrier. Failed INFO feedback, stale,
absent, wrong coordinate/dimension/logger/thread/severity or duplicate success
does not pass. Fixed command constants are not computed from decoded marks.
All barriers use the remaining shared time; late success fails.

Each launch captures and validates phase-specific metadata before starting Java.
After each original successful clean stop, another bounded capture requires
the exact two-mark set. Restart never repeats neighbor removals. Captured tiny
raw metadata is retained before decoding when bounded input is malformed.
The unchanged source-world inventory and artifact postchecks remain mandatory.
Python performs no direct metadata/ticket/terrain edit beyond copying inputs
and writing its evidence. Vanilla commands change only the disposable copy.

## Preserved native oracles and non-goals

The original 60-second/240-probe unload boundary is independent and unchanged.
Original real-event, live culprit/marker restoration, four typed resource/root,
exact compressed terrain, explicit save-refusal, clean-stop300-second and
same-world second-host assertions remain. No guard reset, save-policy change,
arbitrary-coordinate command, Java hook, writer/GuardTicket/resource schema or
network authority is added. One bounded setup does not prove actual unload,
BE disposal, other-ticket absence, cross-store conservation or arbitrary-crash
atomicity. Native01-04 remain FAIL. R-021 and G0-G9 remain open.

Root may later choose a separately pinned diagnostic Java artifact, including
a fresh C18-integrated cohort only if guard classes and dependencies are verified.
These Python bytes neither claim a new Java build nor a runtime PASS.

## Actual tests, failures and evidence

External loose evidence:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/ags5-0a8c27de64`.
It retains named input pins, two-file before/postimages, four-file patch,
raw commands/logs/exits/results, static checks and the final internal manifest.
No full-source tree, JAR/world copy or redundant ZIP is created.

- `python -B intake.py`: exit0, fixed checkout and source/primary/governance pins.
- `python -B run_checks.py red-01`: actual14 methods /41 missing-helper subtest
  errors, exit1. Original harness and new-test bytes are retained.
- `python -B run_checks.py green-01`: actual108 methods /one original mock-signature
  error, exit1; these exploratory helper selectors are recorded distinctly.
- `python -B run_checks02.py green-02`: actual136 methods /0 failures/errors/skips,
  exit0 /2.560190-second wrapper. Original source assertions are preserved.
- `python -B run_checks03.py green-03`: final136 methods /0 failures/errors/skips,
  exit0 /2.532115-second wrapper; preserved checkpoint before barrier-window
  strengthening. Root's exact guard/pump/tank/signature selectors.
- `python -B run_checks04.py green-04`: final137 methods /0 failures/errors/skips,
  exit0 /2.570876-second wrapper, including the post-barrier rejection control.

Seventeen new methods cover fixed metadata pins/native scalar/array types,
signed coordinates, phase sets, duplicate/missing/extra fields, gzip and NBT
bounds, capture-before-refusal, literal/fresh feedback, timestamped framing,
shared deadlines, pre-launch refusal, phase sequencing and absence of repeated
restart removals. The two-host positive test is explicitly mocked and does not
count as native execution. No test deletion or weakened original assertion is
used. Static checks independently compare original ASTs/critical calls and
pre/postimages; any failure is preserved in the external raw history. The first
forward Git patch check fails because the newly authored records lacked the
`new file mode` marker. The original patch/helper/log are retained; the separate
corrected patch explicitly marks both new files without normalizing source
bytes. Its actual check/apply results are recorded in the external static receipt.

## Integration, rollback and remaining work

Root must independently review the exact source diff and rerun the four focused
modules before integrating/committing these four postimages. No worker Git
index change is made. The bounded patch affects only the allocated files and
is checked against exact before/postimages.

Rollback restores only the two Python files to the fixed base and removes the
two new records via Root's normal scoped change process. It does not rewrite
sealed failures, modify source fixtures, reset a guard or change Java artifacts.

Still unrun: Root integration regression, fresh actual two-cycle native lifecycle,
copied saved two-mark observations after real clean stops, real unload and live
restoration, independent source review, client/peer/GPU, crash recovery and all
version Gates. The recommended next current-v1.8 action is independent source
review; only afterward may Root commit and schedule a separately pinned native
diagnostic execution. A failed execution must remain FAIL.
