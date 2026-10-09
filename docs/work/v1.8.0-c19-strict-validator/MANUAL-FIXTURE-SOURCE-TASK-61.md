# C19 manual-evidence fixture source observation61

Date: 2026-10-10. Version: v1.8.0, ADR-060. Status: independent static report sealed and Root-rehashed.

## Identity and scope

The executor is an independent worker subagent, not Root. Do not delegate or
send OpenPet events. Root owns central records and all Git movement. Read the
applicable AGENTS.md and mandatory governance files before analysis.

Inspect the committed source at
D:/GitHub/arce-v180-bootstrap-fixture-20261010-49,
8062781ca0cfc3157212f6f313c509cd0e522c7c. Scope is
tests/test_collect_v002_manual_evidence.py and the functions it actually invokes
in scripts/collect_v002_manual_evidence.py, with adjacent helpers as needed.
Describe authored fixture lifecycle, case independence, subprocess call sites,
and behavior-preserving opportunities with file/line evidence. Static call-site
counts are not runtime cost measurements or an explanation of earlier timeouts.

## Permissions and boundaries

Source checkout is read-only. No tests, imports of target modules, builds,
network, source changes, Git mutation or cleanup of inherited material.
Read-only Git commands use GIT_OPTIONAL_LOCKS=0. Own analysis scripts may use
Python -I -X utf8 -B and must not execute target code. Write only a new leaf
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-manual-fixture-source-review-20261010-61.
Use apply_patch for manually authored files. No existing evidence edits.
Read/file cap 1 MiB; combined command streams 256 KiB; leaf cap 4 MiB.

Record actual HEAD, tracked status, scoped file hashes and index entries before
and after analysis. Do not claim whole-index, ABA, environment or runtime proof.
Return findings first, followed by lifecycle/dependency map, possible changes
and their verification requirements, actual commands/results and limitations.
Seal an exact payload manifest and REPORT.md. No ADR acceptance, integration,
ledger, resource or Required Gate decision is authorized. Last line: REPORT path.

Actual worker /root/c19_manual_fixture_source61 completes source-only analysis:
137 manual plus1 CLI methods,20 helper-only cases and a Low organization finding.
Sealed leaf3 files/24256 bytes. Root reads/rechecks it; no fixture implementation,
test execution, elapsed-cost or whole-timeout conclusion follows.
