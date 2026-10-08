# Ordinary thermite: bounded source integration 09

2026-10-08. Root integrates and normally pushes ordinary thermite and paired
floor/wall torches at `a6675a4eb7497a3626840e150ca3a464c1feab74` on
`codex/v1.8.0-classic-content`. This is development-source admission, not release
acceptance, ledger delivery, artwork approval or a Required Gate conclusion.

## Authority and scope

The owner's direct delegation, recorded on 2026-10-08 in this task conversation,
is: “需要确认的点由你进行确认即可，按照你建议的方案来，全权交给你了”.
The earlier condition authorizes implementation of reviewed contracts without
unresolved Critical/High/Medium; major semantic changes still need separate
confirmation. [ADOPTION-04](ADOPTION-04.md) at
`101c7882856b4b15482c7db8a8d9b4d83fea6ef1` remains the scoped contract decision.
This integration introduces no new gameplay, save, hatch or risk policy.

The bounded feature includes literal stable thermite/torch IDs, additive dust
tags, two loaded crafting recipes, original generated images, paired standing
and wall placement, vanilla light/loot behavior and classic-device-disabled
craft/place/light behavior. It has no block entity, save schema, new protocol,
heat/damage behavior, unlit conversion or world scan. The press switch blocks
new pressing only; existing dust crafting stays available. C15a is its machine
dependency. C16d's complete graph and the rest of C18 are not admitted here.

## Source identity and independent evidence

The merge preserves its separate source, adapter, native-test, asset and
correction commits. Its entire `src` tree and `build.gradle`, `settings.gradle`,
`gradle.properties`, `gradle` and wrapper scripts equal independently tested
`230afbf0f3e27675d23ba98f1de0b560ccdff306`; Root's exact Git diff exits 0.
The last correction changes eight test lines only: raw JSON production-tag
cardinality is checked in addition to the normalized unique member count.
No production assertion, timing or performance budget is relaxed.

Final different-agent report:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18a-thermite-correction09-independent-20261008-01/reviewer-01/REPORT-01.md`.
Report SHA-256: `064f269dd74df051025c51173c3a3fb66d0eb3b607cee0f431d2e9fc91fedff5`.
Manifest SHA-256: `b6b330f45dd85aadd753a092842bb3833b5d6aeb64a7819e41a1fcf5923178af`.
The once-sealed packet has 405 verified payloads, 3,103,333 bytes. No correction-
introduced Critical/High/Medium/Low is found; all reviewer interests release.

Actual JDK 17 commands on that exact committed source, 04:36:26–04:43:20 UTC:

| Command | Actual result |
| --- | --- |
| `gradlew.bat clean build --no-daemon --stacktrace` | Exit 0; 376 XML, 2,146 JUnit tests, zero failures/errors/skips |
| `gradlew.bat runData --no-daemon` twice | Both exit 0 |
| `git diff --exit-code` after each DataGen | Both exit 0; empty output |
| `gradlew.bat runGameTestServer --no-daemon` | Exit 0; all 540 required tests pass; 62 ERROR / zero FATAL headers unwaived |

Nine compilation tasks are FROM-CACHE; `:test` and the native suite actually
execute. The exact command records/logs take precedence over abbreviated table
commands. Build log SHA-256 is
`5dc4353593d5e40da5a0a371f2ae904abbc085ea54f5a39359f4cfd51b53fb79`;
native log SHA-256 is
`e6a9646391d530e8509a1351ef97ae30b9b552d1f0d8bf297d556e9381f49b86`.
All 12 final source/process observations pass. A supplementary log-window
record corrects an instrumentation matcher without altering the original
matcher/result. A quiet local window does not causally waive asynchronous logs.

Root also compares 26 committed paths containing `small_plate_press` or
`/pressing_` with contract-adoption commit `101c7882`; all are byte-identical.
This explicitly selected set includes 17 pressing recipes and nine other named
press resources. It is not a comparison of every shared tag/language resource.
The original 17-recipe source-review observation stays unchanged.

## Validator and process limits

On the clean integration copy at `a6675a4e`, Root actually runs:

- `python -B scripts/validate_repository.py --require-approved-identity`: exit 1;
  44 checks pass, one Markdown-link check fails, stopping at 256 missing/unsafe
  external evidence links. This remains an open repository failure.
- `python -B scripts/validate_v180_content_ledger.py --require-accepted`: exit 0.
- `python -B scripts/validate_bootstrap_provenance.py`: exit 0.
- `git diff --check`: exit 0.

The command/result logs, `VALIDATORS10.json` and
`PRESS-RESOURCE-STABILITY09.json` are in
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-thermite-source-execution-20261008-01/`.
No tested commit is rebound to a later metadata commit. The earlier hosted
`f9f2d9d2` failed airlock cohort and its unwaived errors remain historical
failures; the new passing cohort proves no unique cause or durable repair.

The source-review-08 CLI completed actual verification, but its original
postprocessor crashed on a string message and its runner failed; an independent
new capture preserves that failure and separately derives the result. The
unauthorized startup Git, denied shell attempts and excess/wasted tool turns
remain disclosed, not retrospectively authorized. Source-review-09 supplies
the subsequent independent exact-source evidence used here.

Root normally removes two released, clean source-only worker worktrees after
status/path/output checks. Their branches, commits and thin evidence remain.
This is not retirement of the older blocked build/run-data outputs. A separate
reviewer TEMP cleanup was rejected before execution; two owned TEMP artifacts
remain pending, excluded from its seal. No deletion retry, C temporary script,
source world, prior world or other agent's output is claimed cleaned.

## Remaining work

Packaged survival S1, connected-player/C2S behavior, restart/prior-world,
dedicated/multiplayer S2, real V1/V2, artwork/provenance acceptance and complete
version Gates are unproven. No R-021/save/shared-hatch risk closes. Ledger
allocations and delivery statuses are unchanged; v1.8 remains IN_PROGRESS.

Claude's separate two-file survival-menu supplement at `f535a130` is backed up
and undergoing independent review. It is not part of this integration and its
test cohort cannot be substituted for the 540-test source evidence above.
