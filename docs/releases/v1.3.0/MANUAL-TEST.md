# v1.3 real-player acceptance checklist

**NOT RUN for a v1.3 candidate.** Execute at the ADR-018 scheduling point on
copies of backed-up worlds. Record exact host/integration hashes, Forge/Java,
hardware, operators/date, expected versus actual, screenshots/video and logs
for every case. Blank results are not passes.

Prepare a supported integration using the [public guide](../../PUBLIC-API-GUIDE.md)
or the [disposable fixture](../../../compat-test-mod/README.md); do not distribute
the fixture as gameplay content. Use one matching real GPU client for V1 and two
real clients for V2. FakePlayer/operator scripts are separate automated evidence.

| ID | Setup and action | Required observation | Actual |
|---|---|---|---|
| M130-01 | Install matching host and integration; connect both players, reconnect one | Correct loader/API diagnostics, shared state, no client-only server linkage | NOT_RUN |
| M130-02 | Prepare an external cargo container with named items on a valid rocket; assemble, Earth-Moon-return and disassemble | Exact cargo/metadata and single authority; explicit remaining-fuel disposal choice | NOT_RUN |
| M130-03 | Try an unsupported container and an intentionally rejected provider in the assembler | Readable bounded diagnostic, source unchanged, healthy supported retry | NOT_RUN |
| M130-04 | Operate a room with an integration-provided boundary; change its state and reload supported tags | Room authority and visible oxygen feedback follow the new state | NOT_RUN |
| M130-05 | Equip external oxygen armor in vacuum, debit/refill and reconnect | Server-authoritative oxygen/protection, correct visible equipment/HUD and exact canister count | NOT_RUN |
| M130-06 | Load a registered fuel into a partial tank; save/restart, complete and collect remainder | Captured units persist, remainder once, names/icons and diagnostics usable | NOT_RUN |
| M130-07 | Assemble with registered engine/tank definitions, restart with changed definitions, then reassemble | Old rocket values stay captured; new assembly uses new values and readable UI | NOT_RUN |
| M130-08 | Manufacture/launch a registered satellite payload; reload definitions with menu open; claim and replay | Correct targets, stale menu rejected/reopened, captured reward and no duplicate claim | NOT_RUN |
| M130-09 | Second non-owner attempts inventory/rocket/terminal operations; send bounded stale/out-of-range intents | Denied operations leave resources unchanged and load no arbitrary chunks | NOT_RUN |
| M130-10 | On disposable copied saves, omit provider registration, then the fixture mod; reinstall exact dependencies | Documented refusal/quarantine and recovery; distinguish missing item/block from missing registration | NOT_RUN |
| M130-11 | Test the declared optional-client integration combinations with matching mandatory host mods | Permitted absence works; unsupported combinations fail clearly rather than silently corrupt state | NOT_RUN |

M130-10 is not a license to delete ordinary world blocks or remove arbitrary
third-party mods. Preserve originals and inventory native data before/after;
follow the [migration limits](MIGRATION-REPORT.md). Do not manually repair a
world and count the repaired result as automatic recovery.

An uninvolved tester must also follow the installation instructions from a
clean candidate environment and report the actual outcome. This installation
test, V1/V2 and human approval remain outstanding.
