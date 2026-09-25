# v1.2.0 optional JEI integration checkpoint

```yaml
version: v1.2.0
slice: V120-INT
status: IN_PROGRESS
verified_leaves: [V120-INT-01]
implemented_unverified_leaves: [V120-INT-02]
date: 2026-09-25
branch: codex/v1.2.0-precision-assembler
implementation_commit: 4d0f976
development_jar_sha256: 7ab3822bf690edb95ae1edf7e61ecf35a6ca477303c64ec1b1a8a15fe113b80f
```

## Scope

- The optional JEI plugin now registers Rolling Machine and Precision Assembler recipe
  categories, catalysts and console click areas alongside the existing Electrolyzer.
  The prior Electrolyzer registration log format remains intact for compatibility.
- Both new categories construct slot contents from an immutable, defensively copied
  display view of the same `ProcessDefinition` used by server processing. Rolling
  displays Item, water, output, duration and FE/t; Precision preserves fixed input
  slot order for two- and five-input recipes and shows both defined outputs.
- The JEI API remains compile-only and all JEI references stay in client compat.
  One bilingual cost label is DataGen-owned; no upstream asset was copied.

## Executed checks

| Check | Result |
|---|---|
| `gradlew test --tests '...MachineRecipeViewTest' --offline --no-daemon` | PASS; 3 tests |
| `gradlew clean build --offline --no-daemon` with JDK 17 | PASS; 120 JUnit suites, 584 tests, 0 failures/errors/skips |
| consecutive `gradlew runData --offline --no-daemon` | PASS; second run wrote 0 files |
| `gradlew runGameTestServer --offline --no-daemon` without JEI | PASS; 89/89 Required GameTests |
| `python scripts/validate_v120_machine_resources.py` | PASS; 9 machine blocks and bilingual keys, including JEI cost |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 version plans and 33-input inventory |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 882 relative links |
| JAR class inventory and common/server import scan | PASS; 3 ARCE JEI categories, 0 bundled JEI classes, 0 common/server JEI or client imports |

The tested development JAR SHA-256 is
`7ab3822bf690edb95ae1edf7e61ecf35a6ca477303c64ec1b1a8a15fe113b80f`.
The local GameTest console remains ignored at `build/v120-jei-gametest-console.txt`
(SHA-256 `8701bbe887226913281487c283fefb002a43665530ceb110930e01f35f4a9a64`).
This is not a v1.2.0 release candidate.

## Unverified behavior and next check

No JEI-present client was launched at this checkpoint. Build success and a no-JEI
server prove neither that the optional plugin executes nor that both categories render
correctly in a player session. `V120-INT-02` therefore remains
`implemented-unverified`; the later client check must inspect actual synchronized
recipe counts, catalysts, click areas and slot presentation. Full V1 visual and V2
multiplayer campaigns remain deferred by ADR-018 until all original machines and
dimensions are implemented, and no v1.2.0 Gate is marked passed here.
