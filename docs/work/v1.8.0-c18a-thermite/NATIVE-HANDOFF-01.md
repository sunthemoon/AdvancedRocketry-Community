# Thermite native source handoff 01

TASK-NATIVE-01 delegated implementation, 2026-10-08. Worktree:
D:/GitHub/arce-v180-thermite-native-tests-20261008. Branch:
codex/v1.8.0-thermite-native-tests-20261008. Base and final observed HEAD:
101c7882856b4b15482c7db8a8d9b4d83fea6ef1. Root owns integration and Git operations.
Live main governance, RUNTIME-CONTRACT-04, ADOPTION-04 and the NEW resource
declaration were read before authoring. No upstream code or pixels were used.

## Completed source and tests added

Only these two new files were authored:

- src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/ThermiteTorchGameTests.java
- docs/work/v1.8.0-c18a-thermite/NATIVE-HANDOFF-01.md

The Java file has 408 lines and nine GameTests. Its SHA-256 is
40b328ac0f942df68ff095c740d4edaa6efc02ee1c52b20ebc230d76144d7146.
Coverage added: literal material/block/item identities, pairing and no BE;
survival ItemStack.useOn floor/four-wall placement; floor/wall fallback after a
ceiling click; unsupported and isolated-ceiling stack-preserving refusal;
scoped Forge cancellation rollback in survival/creative; native survival,
creative and support-loss drops for both block forms; separate seeded native
loot/explosion controls; dark loaded block-light publication/removal; passive
placement/light with classic devices disabled; installed Moon analyzer vacuum.

## Design decisions and boundaries

Survival placement uses disposable, unjoined FakePlayers configured SURVIVAL.
ConnectedTestPlayers always overrides isCreative=true, so it is used only for
the actual joined, non-FakePlayer analyzer observer. No analyzer handle is
installed/replaced and no local atmosphere service is constructed. The Moon
reading must have Moon surface/body/ambient identity, present pressure zero,
NON_BREATHABLE state and no supply; the eye-cell reading remains identical
after placement, neighbor invalidation and removal.

Placement is separate from the setter-only light test. The light state machine
measures a zero baseline in an opaque 125-cell fixture, requires emission 14,
published centre 14/neighbor 13, then removal back to the measured baseline.
Async cases retain the original 40-tick deadline. Owned cell
budget is 160; new-drop budget is 32. One finite Moon chunk is loaded for fixture
setup; only a newly acquired force flag is released. Timeout listeners restore
cells, remove the joined observer, clear the owned switch override and dispose
new fixture drops. The disabled test has a distinct batch.

## Actual commands and verification status

Get-Content/rg inspected governance/source. Git branch, rev-parse, status and
diff --check confirmed the base and no tracked changes. PowerShell found
408 lines, nine annotations and zero trailing
whitespace; Get-FileHash produced the hash above. The /dev/null no-index check
had no whitespace diagnostics; exit 1 represents the new-file difference.

No JVM, Gradle, GameTest server/client, network, archive/cache read, cleanup or
Git write was performed. All nine tests are ADDED, NOT RUN. This is source
handoff evidence, not native execution or Required Gate acceptance.

## Unfinished work, assumptions and risks

Root must integrate registry/resources, compile and run the native tests plus
the distinct adapter tag/recipe/press checks and existing regression suite.
The literal registry content is absent from this leaf until integration.
Mapped platform calls are not compiler-verified here. Timeout cleanup reflects
GameTestHelper.testInfo, following the existing AirlockDoorGameTests convention;
its field name remains an API coupling. Seeded loot and asynchronous lighting
outcomes remain unobserved. S1 crafting/pickup, reload/restart, prior-world
operation and real-client V1/V2 visuals remain unproved.
No ledger, version state, ADR or Required Gate conclusion was changed.

All process/read/write/source/HEAD/index/branch/worktree interests are released.
No child or background process was started. Root may copy, commit and move the
branch/worktree. The next work is current-version integration and actual checks.
