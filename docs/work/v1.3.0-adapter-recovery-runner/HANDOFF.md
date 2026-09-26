# External cargo recovery runner

## Scope and contract

The runner launches exactly six owned, cleanly stopped production Forge processes
against one disposable world. A four-block rocket carries one independent two-slot
fixture container: 17 named diamonds and 64 iron ingots. Its fixed phases are:

1. Assemble through the installed host command and bounded production scan.
2. Restart the same entity with both original JARs.
3. Keep the fixture installed but skip its provider registration, reject ordinary
   disassembly without mutation, then stage the existing synthetic EXTRACTING journal.
4. Actually remove the exact verified fixture JAR, retry the persisted journal,
   retain entity/snapshot/journal authority and reject journal-busy disassembly.
5. Reinstall the exact original fixture bytes and let production recovery restore
   blocks/inventory and retire entity/journal authority.
6. Restart the restored BlockEntity and verify inventory and empty journal again.

All phases use actual status-ping mod identities, installed hashes, registration
observations, live entity/block/drop checks and a bounded 20-tick second observation.
After each process exits, the runner archives actual region/entity-region/journal
and level files. Existing bounded Anvil extraction and NBT readers inspect the
saved fixture chunk. Full `RocketEntityData` and complete journal compounds are
compared across applicable phases, including snapshot hash/UUID, owner, transaction,
region bindings, external block IDs, adapter/version and exact inventory metadata.
This is semantic NBT equality plus saved snapshot hash and original archived bytes;
it is not a claim that complete world-region files remain byte-identical.

`operational=true` is expected for an otherwise-valid opaque snapshot even while
the provider is missing. The skipped-provider phase does not restart a pending
journal: the existing staging command suppresses recovery until stop. Actual
uninstall is the phase that exercises missing-dependency journal retries.

## Usage

Prepare a new dedicated server directory containing **only** the already installed
Forge 1.20.1-47.4.10 `libraries` tree, without runtime state or linked ancestors.
Supply explicit approved host and independently reobfuscated fixture JARs outside
the output directories. The fixture must support the startup property
`arce_adapter_test.skipRocketAdapter=true` and API 1.1 registration observations.

```powershell
python scripts/run_v130_adapter_recovery_smoke.py C:/Temp/new-server `
  --host-jar D:/artifacts/advancedrocketry-community-1.20.1-1.3.0-dev.jar `
  --fixture-jar D:/artifacts/arce-adapter-compat-test-1.0.0.jar `
  --evidence-dir C:/Temp/new-recovery-evidence `
  --java 'C:/Program Files/Java/jdk-17.0.7/bin/java.exe' --accept-eula
```

The explicit EULA flag acknowledges the Minecraft server EULA. Evidence must not
exist and must be disjoint from the server. No installer or network download is
performed. The server binds loopback, uses a fresh allocated port and the existing
offline disposable-server configuration; no real players are permitted. Only
the copied, hash-checked fixture file is removed, between stopped processes. The
runner never deletes worlds, existing evidence, arbitrary files or foreign processes.

Startup defaults to 240 seconds and cannot exceed that bound. Ordinary command
observations are 30 seconds, assembly/recovery 45, save 60 and stop 90. The existing
owned-child cleanup is reused on failure. Each phase records its command list,
launch/mod hashes, full stdout, debug/latest logs, result/exit code, status and
active config. Failures retain completed evidence and a failure document; SHA256SUMS
covers the final capture files. No failure is converted to PASS.

The exact Forge `GameData/REGISTRIES` unidentified-mappings ERROR is allowed only
in the actual-uninstall phase and is separately recorded. Other ERROR/FATAL,
project WARN and client-linkage findings fail. All warnings remain in the report.
Independent execution should additionally review the copied debug logs.

## Implementation and verification

Files: `scripts/run_v130_adapter_recovery_smoke.py`,
`tests/test_v130_adapter_recovery_smoke.py`, and this task directory only.
No host Java/build, fixture Java or shared Python helper was changed.

Actual commands on Python 3.13.15:

```text
python -m unittest tests.test_v130_adapter_recovery_smoke -v
python -m py_compile scripts/run_v130_adapter_recovery_smoke.py tests/test_v130_adapter_recovery_smoke.py
python scripts/run_v130_adapter_recovery_smoke.py --help
git diff --check
git diff --cached --check
```

The initial focused suite passed all 21 test methods in 0.221 seconds; the final
implementation rerun passed 21/21 in 0.204 seconds, exit 0. Syntax compilation and
CLI help also exited 0. The focused suite covers
all six disk states, outer/inner journal identity mutations, complete-root equality,
inventory metadata/count/schema/version changes, duplicate/lost authority, malformed
compressed NBT, padded block palettes, registration/status modes, exact log allowance,
fresh/disjoint/unlinked directory handling, archive class boundaries and owned-process
failure capture. Subsequent output is retained in this directory.

No Gradle, Java version probe, Minecraft process, live status request or runtime
recovery was executed by the implementation agent. Integration and the six-process
run remain the independent verifier's responsibility; Python mocks are not runtime
evidence. Initial command exploration included incorrect read-only file paths and
one harmless PowerShell expression error; neither changed source or ran Java.

## Limits and remaining acceptance

This is clean restart and **synthetic pre-commit journal** coverage, not a crash,
power-loss/fsync claim, cross-dimension flight, real-player test, arbitrary missing
item-mod preservation or complete v1.3 acceptance. Opaque data remains authoritative
inside the rocket/journal; ordinary external blocks must not be left in world chunks
when removing the fixture. The bounded single-chunk case does not prove global
unloaded-entity uniqueness. Full Required Gates remain unapproved.

Next within v1.3: integrate this runner, execute it with the final two artifacts,
archive actual results/failures, then review the remaining external-provider cases.
