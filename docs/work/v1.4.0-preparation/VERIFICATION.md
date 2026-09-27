# V140-GOV-01 / V140-CON-01 preparation verification

Date: 2026-09-27. Scope: development baseline, first planetary contract,
documentation examples and unchanged-runtime checks. **No v1.4 production
behavior, world content, candidate or Required Gate is delivered here.**

## Identity and decision

- Branch: `codex/v1.4.0-planetary-expansion`.
- Baseline: `1a192b4b9b9a90372c11643e086f7b0fbdcd1860`, the committed
  [v1.3 development handoff](../../releases/v1.3.0/RELEASE-EVIDENCE.md).
- [ADR-030](../../decisions/ADR-030-V140-DEVELOPMENT-BASELINE-EXCEPTION.md)
  accepts only current-version development from that baseline, not inherited
  acceptance or v1.5+ implementation.
- [ADR-031](../../decisions/ADR-031-PLANETARY-DEFINITIONS-AND-FIXED-LEVELS.md)
  freezes schema 2, optional Level mappings, capability/admission semantics,
  legacy defaults, bounded coherent catalog reload and display compatibility.
- Acceptance was recorded under the maintainer's standing authorization to
  follow recommended solutions. The independent reviewer inspected proposed
  records and did not approve the ADRs or a release. The later acceptance
  metadata is the root's administrative record of that authorization.

Production sources, build configuration, generated resources, existing world
files and public API 1.7 are unchanged. The active development pointer is v1.4;
the earliest uncompleted release cursor remains v1.0. New physical destinations
remain unimplemented, and the build still correctly identifies the existing
runtime as `1.20.1-1.3.0-dev`.

## Independent review and dispositions

The unchanged report and its raw evidence are retained in
[independent-review.zip](independent-review.zip); the report member is
`review/REVIEW.md`, SHA-256
`42ed789ec9c3efd89af9e1a01ab4b51a0491a1cc3661265859f3411d512dd5a2`.
[Review identity](independent-review.json) binds the original files and reviewer.
The archive also contains initial proposed documents, source identities,
commands, logs and the pinned Forge bytecode inspection.

| Finding | Disposition |
|---|---|
| Existing JSON preparation can skip a malformed resource before project validation | Still a runtime defect/limitation. DATA-02 must replace this with bounded raw reads and whole-candidate rejection, and test the actual preparation path. |
| Capability checks could invalidate exit/reverse routes from a closed body | Contract distinguishes mapped graph endpoints from final arrival/new-station admission; legitimate departure remains allowed. Runtime behavior still needs implementation. |
| Metadata alone cannot preserve a custom body/Level mapping across restart or remove/re-add | DATA-01 keeps Earth/Moon admission and fixed baseline identities. MAP/MIG must freeze and implement durable binding/old-world initialization before admitting new worlds. |
| Raw UTF-8, nesting and duplicate-key bounds are stricter than legacy compact-character checks | Contract explicitly documents rejection, active-pair retention/initial failure and operator repair; no automatic rewrite or silent partial acceptance. |

No unresolved proposal-document finding remained in the review. This does not
mean runtime changes were independently verified. The reviewer read code and
ran documentation checks only; it did not rerun the root's Gradle command.

## Examples and checks added

[examples.json](examples.json) contains six documentation-only samples: legacy
Earth, schema-2 Earth, closed Moon, Mars-like, Venus-like and an unmapped gas
giant. `example:` IDs and values do not install Levels, grant asset permission
or claim calibrated astronomy.

[check_examples.py](check_examples.py) uses only the Python standard library.
Its 14 tests cover legacy preservation/defaults, required/unknown fields,
optional versus null mappings, capability combinations, finite numeric and
integer bounds, strict JSON/raw-byte/nesting constraints, parent validation,
cycles, duplicates and synthetic 100/128/129-body counts.

Both root and independent reviewer executed all **14 tests successfully**.
This checker is not the production Codec or a complete runtime catalog
validator: it does not prove fixed baseline admission, route pairing, fuel
authority, snapshot behavior, world loading, restart migration or performance.
The finite 100-body sample is not the version's 100-body load acceptance.

## Actual runtime baseline command

Windows / Java 17.0.7 / pinned Forge 47.4.10, offline cached dependencies:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17.0.7'
./gradlew.bat clean build runData runGameTestServer --offline --no-daemon --console=plain
```

Exit **0**, **121 seconds**; 27 tasks: 18 executed, 8 from cache, 1 up-to-date.

- Required GameTests: **210 newly executed and passed** in the disposable
  `build/gametest` world after `clean`.
- JUnit: **769 tests / 143 suites**, zero failures/errors/skips, restored
  **FROM-CACHE**. These were not newly executed during preparation.
- DataGen: **zero files written**. Production/resource/configuration diff was
  empty after the command.
- Retained warning: **2941 ms / 58 ticks behind**. The log also retains expected
  Precision migration-save-failure diagnostics from failure-injection tests;
  this is not a claim of an error-free log or performance acceptance.

[baseline-checks.json](baseline-checks.json) records the exact command, task
counts, JUnit/cache status, warning, build-log hash and all three artifact
identities. Host/API/sources bytes exactly match the v1.3 handoff. In particular,
the host SHA-256 remains
`d1f9f564ed1dcd1df7ac0648b4091a07dfa9a02f63b36b827f36aad591224475`.
It is not a new v1.4 runtime artifact. The independent consumer was not rebuilt.

## Documentation and archive verification

Python commands use `PYTHONUTF8=1` and
`D:/python/pyenv/pyenv-win/shims/python.bat -B`; retained helpers record the
resolved interpreter for their subprocesses. No `--package-root` was supplied,
and the user-provided untracked documentation bundle was not read or changed.

```text
python -B docs/work/v1.4.0-preparation/check_examples.py
python -B scripts/validate_v1plus_planning.py
python -B scripts/validate_repository.py --require-approved-identity
git diff --check
git diff --cached --check
git diff --exit-code 1a192b4b9b9a90372c11643e086f7b0fbdcd1860 -- src build.gradle gradle.properties settings.gradle compat-test-mod
```

All exited **0**. Root's final example rerun passed 14 tests; planning validated
11 plans and the 33-input inventory. Strict repository validation passed
**45 checks**, zero pending/warnings/failures, in 250.059 seconds. The local
link pass checked **367 Markdown files / 1,550 targets**, zero missing targets;
it does not check external URLs or anchor contents. The independent earlier
draft check covered 366 files / 1,526 targets. These are separate captures,
not inferred from the build. The final report edit records these observations;
links and staged-byte checks are repeated after packaging.

The root's full build log, frozen 143 JUnit XML files, documentation logs and
capture helper are retained in [root-checks.zip](root-checks.zip).
[SHA256SUMS.txt](SHA256SUMS.txt) identifies each preparation file, excluding
itself. Final verification checks working and staged file hashes, ZIP CRCs and
unique member names, plus archived build-log and XML identities. The build
directory may subsequently change without altering these retained results.

Capture entry point: `preparation_evidence.py` in the root archive. Its `docs`,
`links`, `review`, `package` and `verify` modes record/check only the stated
scope. Final staged verification covers seven checksummed files and two ZIP
archives; the checksum list itself is intentionally excluded from its own
manifest. No artifact or original review hash was changed to make a check pass.

Read-only exploration included a few incorrect guessed documentation names;
tracked-file enumeration identified the actual files. Those read errors were
not test failures or runtime results and did not change repository files.

## Remaining work and acceptance

Continue `V140-DATA-01` in the [implementation log](../v1.4.0-implementation-log.md):
implement versioned definitions together with all optional-mapping consumers,
server-side destination checks and the bounded display protocol. DATA-02 then
implements raw bounded preparation and atomic celestial/route publication.
Preserve immutable accepted flight/transfer authority and exported API types.

MAP/MIG still needs a durable body/Level binding and old-world initialization
contract before new-world travel. Environmental effects, two explorable worlds,
navigation/discovery enforcement and sky/assets remain current-version work.
No native packaged restart, authentic world upgrade, GPU/two-real-client,
remote or long-load test ran for this documentation-only slice.

G0-G9 and inherited acceptance remain open. Candidate fields stay empty; no tag
or release is authorized. ADR-018's original-mechanics/dimensions trigger has
not been reached, so the full acceptance campaign remains deferred.
