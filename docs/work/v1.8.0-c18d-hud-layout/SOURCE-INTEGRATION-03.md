# C18d-HUD-ENV-O2-03: bounded HUD source checkpoint

Updated: 2026-10-08. Owner/integrator: Root/Codex. Status:
implemented-unverified; source review and intermediate targeted JUnit passed.
This record accompanies the source checkpoint; it does not deliver the eight
ledger units, qualify a client, or close any v1.8.0 Required Gate.

## Source identity and changes

Isolated branch/worktree and pre-edit scope are in [ROOT-TASK-03](ROOT-TASK-03.md).
Base: `90f257ff72a38a4f29f135c5437014b83b34547e`. The committed checkpoint carrying
this record identifies the new source; no uncommitted check is delivery evidence.

Root applied the original sealed three-file Claude patch after `git apply
--check --verbose` passed. Helper, original 18-case test and SOURCE-01 retain
their exact authored bytes; original evidence and execution deviations stay
unchanged. [ADOPTION-02](ADOPTION-02.md) is the narrow interface decision.
Root added a separate independently generated candidate/selection oracle:
5,445 anchor/offset/viewport combinations and 4,096 fixed-seed asymmetric cases.

ClientConfig defines only the eight contracted CLIENT keys, their independent
defaults, strict bounded integral-wrapper predicates and native enum correction.
Immutable accessors distinguish unloaded defaults from loaded native values.
The existing three effects/sky settings and assertions remain. Five added config
tests cover paths, predicates, actual correction and same-instance cache reload.
This does not claim validation of arbitrary ConfigValue.set/raw-map edits or
execution of the TOML file watcher.

The registered LifeSupportHud consumes one server snapshot, reads each settings
pair once and invokes the pure placement helper once. Full Font text measurement
and compact wrapping match actual drawing; logical origins remain long and use
scoped double pose translation with finally-pop. The two-pixel accent stripe
occupies compact padding. Status/visibility/oxygen 2,000/suit truth is unchanged.
No asset, registry, protocol, server authority, schema or save behavior changes.

## Independent combined-source review

Root fully read the different-agent
[REPORT-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-hud-binding-independent-20261007-01/REPORT-01.md)
on 2026-10-08 (`38e21f41ee3eefe9c759a19e14eb95eb6ff3c9b63b1e42e4a639d4f47c49eb48`).
It reports no established C/H/M/L finding on six exact source/test postimages,
25/25 static controls and mapped native method descriptors. Its initial
instrument errors remain in separate original logs; no executable/visual/Gate
assessment is asserted. Reviewer reads/HEAD interests are released.

| Source/test, below the Java package root | SHA-256 |
| --- | --- |
| main/client/LifeSupportHud.java | `3310274707a5a496ba24b24289048a7f0e82304aca4dd39d149f3355ccc840f5` |
| main/client/LifeSupportHudLayout.java | `ba7a4415a6ffd63e45334e48d4de1ace161b714c1c32e0b372c58c3768cfccf6` |
| main/config/ClientConfig.java | `c16820bc7a9fdfecd530d596f7a027d1631de13ecb3f2e7e873d4e4a4d558e59` |
| test/client/LifeSupportHudLayoutTest.java | `5402593d74f3ddf921bac5ddc9179d9c8d99a670d0d08db8198f7b86f271c948` |
| test/client/LifeSupportHudLayoutOracleTest.java | `142325526312167734a44a70bc635d1c0d2e711947f7134816aaec1c800a0adc` |
| test/config/ClientConfigTest.java | `f0f4d661f5fc8bb4c165096de6b5b3739f5f377088919097b8f6ace709cdedf1` |

## Actual intermediate execution

Root evidence:
[c18d-hud-integration-root-20261007-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-hud-integration-root-20261007-01/).
Space preflight: C 29,905,580,032 bytes / D 320,681,418,752 bytes; no other JVM
was running. Java 17.0.7 and process-local TEMP/TMP/TMPDIR/java.io.tmpdir used
the owned D evidence temp, with no global configuration change.

```text
gradlew.bat test --tests '*LifeSupportHudLayout*' --tests '*ClientConfigTest'
  --no-daemon --no-build-cache --max-workers=2
  -Djava.io.tmpdir=<owned D evidence temp> --stacktrace
```

TARGETED-01.log/exit record exit 0, BUILD SUCCESSFUL in 1m32s. Actual JUnit
Platform XML: original geometry 18, independent oracle 2, config 8; total 28,
zero failures/errors/skips. All main/test sources compiled. Existing 27 main
and 61 test deprecation warnings remain; no warning cleanup is included.
The session interruption did not stop the process; on 2026-10-08 Root confirmed
exit 0 and the XML rather than starting a duplicate job. This uncommitted
development run is intermediate evidence only, not committed-source acceptance.

## Open qualification and preserved risks

Exact committed-source hosted clean build/all JUnit, twice runData with tracked
and untracked cleanliness, and full native GameTests are still required.
Different-agent executable rerun, actual file reload, Font/client dispatch,
real-GPU V1 and two-client V2 remain unrun. Tiny UNFIT viewports may clip;
huge font metrics may exceed checked native int extents and pose/GPU precision
remains a platform limit. Default placement does not guarantee avoidance of
expanded other overlays; that remains a V1 observation obligation.

The previous source-bound regression at `b8136f0b` remains failed: Tau arrival,
initial airlock supply, ticketed destination readiness and 65 unwaived ERRORs.
Prior loader absence is not repair. Shared hatch/save readiness and R-021 stay
open. No content/CSV/ledger/ADR/Gate promotion follows from this HUD checkpoint.
