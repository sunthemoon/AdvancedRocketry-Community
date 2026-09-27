# V140-MIG-03 — finite packaged discovery interruption and recovery

## Scope and inputs

2026-09-28. Development base `b56e40e1c42c4e5b28cfdc4fcdbd64ead8530f1a`,
branch `codex/v1.4.0-planetary-expansion`. Production remains at
`a9143c2b62d5daab412cefedcee198f1fd032588`: no gameplay, public API, protocol,
save schema, resource or production Java change. This slice adds an external
JDI observer, a finite process harness, two-store readback predicates and
14 Python test methods. It verifies the process-interruption portion of
[ADR-038](../../decisions/ADR-038-ORDERED-PLANETARY-DISCOVERY-RECOVERY.md),
not arbitrary power loss or every original subsystem.

The input is MIG-02's retained **original v1.3 world**, not its upgraded final
world. Its completed 269-member evidence manifest and original 34-file /
13,580,236-byte inventory are verified before copy. The current host boots a
new copy and starts one Mars research mission for fresh owner `000...005`.
A clean READY/unpaid stop produces a complete 39-file / 13,605,845-byte backup;
each of the four cuts starts from an exact independent copy. Both original
and prepared backups remain unchanged. The historical consumer remains installed.

| Installed artifact | SHA-256 |
|---|---|
| v1.4 host | `576eaaf8a5e23df1b049fd5fa5029f1cd6d6ffcfd0cf50bd5980fc280c31d475` |
| historical consumer | `e58de48ff038ff42fa1173bfd210d9e8321cfcfa66849ba1c13a0e31c1c02a47` |

The clean build's main/API/sources JARs remain byte-identical to MIG-01/02;
see [complete artifact identities](development-artifacts.json). These are
development artifacts, not release candidates or tags.

## Actual finite observations

The helper attaches only to an owned loopback debugger. It reads fields and
arguments; it does not invoke target methods, redefine classes or inject save
state. Exact writer signature, source line/bytecode index, selected mission,
claim/recovery stack and store order are checked. All four observations record
writer bytecode SHA-256
`08e46faf9c8a5da8fbe65abedffabe39f593fc6b530f7279451e4b8a840dfc50`.

At the selected event, SUSPEND_ALL remains held while both compressed authority
files and any scratch are copied. The parent kills only its owned Popen handle,
then checks observer disconnection and exact paused/killed/pre-restart bytes.
The complete killed-world inventory is unchanged before restart. No repair,
scratch promotion, world merge or debugger is used during recovery.

| Cut | Actual site | Durable pair at kill | Restart before any manual claim |
|---|---|---|---|
| 1 | line 90 / BCI 326, first mission replacement not executed | READY, unpaid; Mars absent; paid receipt only in scratch | Still READY/unpaid/undiscovered; exact scratch remains ignored |
| 2 | line 90 / BCI 326, celestial replacement not executed | Paid pending; Mars absent; discovery candidate in scratch | Automatic CLAIMED/discovered, no new payment |
| 3 | line 90 / BCI 326, final mission replacement not executed | Paid pending; Mars discovered; completion in scratch | Automatic CLAIMED, original discovery timestamp retained |
| 4 | line 100 / BCI 394, third replacement completed before dirty acknowledgment | CLAIMED/discovered; no scratch | Remains CLAIMED, without changed payment or discovery |

Mission `e895c8a5-0543-3fb0-89e3-0c162691e733` and satellite
`6f79aa6b-007f-4dba-b3c5-d1817a1f6edb` are identical across all four prepared
copies. Captured duration is 200 ticks, yield 120, fee 100. Only cut 1's explicit
retry returns SUCCESS; every later manual claim returns ALREADY_CLAIMED.
Final balance/earned/spent are exactly **20/120/100**, never doubled. Scratch
is absent after successful claims. Existing account **337/348/11**, two claimed
missions, old satellite and Earth discovery timestamp **461** remain unchanged.
Mission snapshots, receipt timestamps and current-mission bindings are checked,
not merely totals. Discovery adds no visit. Only the two validated scheduler
clock fields may differ in otherwise equal authority comparisons.

The final attempt has nine owned server processes: preparation plus four clean
recovery stops exit **0**; four deliberate force kills exit **1** on this Windows
host. All four observers exit **0** after the selected cut disconnects. Server
and debugger ports close. The final native manifest contains **237 files**.
Each native log has zero ERROR/FATAL, project warning or client-linkage failure;
ordinary Forge/JVM warnings remain retained, not relabeled as zero-warning logs.
Bracketed WARN counts are 16 for preparation, 15 per killed process and 10 per
recovery; each stdout also contains one unbracketed terminal-feature warning.
No native region decoding or terminal/chunk atomicity claim is made here.

## Actual commands and short checks

Java 17.0.7; Python via `D:/python/pyenv/pyenv-win/shims/python.bat`,
`PYTHONUTF8=1`, `-B`:

```text
javac --add-modules jdk.jdi -d <classes> scripts/java/DiscoveryCutProbe.java
python -B -m unittest tests.test_v140_discovery_cut_smoke tests.test_v140_migration_smoke tests.test_v140_celestial_schema_smoke tests.test_v140_planetary_worlds_smoke -v
python -B scripts/run_v140_discovery_cut_smoke.py <new-work-dir>
  --libraries-dir <Forge-libraries> --migration-evidence <MIG-02-native-final>
  --host-jar <unchanged-v1.4-host> --fixture-jar <historical-consumer>
  --evidence-dir <new-evidence> --java <jdk17>/bin/java.exe --accept-eula
gradlew.bat clean build test runData runGameTestServer --offline --no-daemon --console=plain
git diff --exit-code -- src/generated
git diff --check
python -B scripts/validate_repository.py --require-approved-identity
python -B scripts/validate_v1plus_planning.py
python -B scripts/validate_bootstrap_provenance.py
```

Root clean build exits 0 in **2m36s**. **922 JUnit results / 167 suites are
restored FROM-CACHE**, not freshly executed; **236 required GameTests actually
execute and pass**. DataGen produces no tracked changes. Fault-injection test
diagnostics and Gradle warnings remain in the raw log. Root and independent
review each execute **57 Python methods**, including 14 new cut checks; both
pass. The helper compiles independently and in the actual native harness.
Repository validation passes 45 checks, planning validates 11 plans, and
provenance passes. Changed documentation/archive checks are recorded separately.

## Failure retained and independent review

The first attempt reaches cut 1 and correctly restarts READY/unpaid with the
paid scratch ignored, but the original harness incorrectly demands immediate
scratch deletion. Production does not promise that deletion before the next
write. Its failed recovery, 103-member manifest and original script inputs
remain archived. The corrected predicate allows absent scratch or the **exact
unchanged killed scratch** while still requiring unpaid authority. Successful
claim must clear it. No reward, fee, identity or recovery assertion is removed.
A new regression test rejects premature payment, altered scratch and changed
bytes; the entire native attempt is rerun from new disposable directories.

Independent actual-source review and key-command reruns confirm that correction.
Source report SHA-256:
`ac25a267c7beef18e091f39b31e534553658cad71a957d9006a1b29363900f98`.
The separate independent raw audit verifies all 237 final and 103 failed-attempt
manifest files, packaged writer bytecode/line sites, original/prepared inventories,
owned process and command ordering, and raw NBT authority/scratch transitions.
It does not call the new runner's acceptance predicates. The successful source
and native dispositions certify only the stated finite development scope, not
all Required Gates. The prior source-review report is not retroactively changed
to claim that it had already audited the native run.
Native report SHA-256:
`3029a10b7321a78a0c4e8921873c01916d2ef9bf7bca7011b6bc04608259db83`.
The independent audit verifies full two-store/scratch bytes, not every vanilla
file's pre-restart bytes from a separate full-world capture. The runner's broader
inventory comparison is separately attributed. Discovery world time and mission
logical time are not treated as one shared transaction clock.

P3 observer failure boundary: on its 45-second timeout/error, `dispose()` can
resume a VM before parent cleanup. Such an attempt cannot pass the live-observer,
successful-disconnection and exact-byte checks and must remain a **failed**
attempt, not a proven held cut. The four successful observations do not exercise
that timeout path. This limitation does not justify widening the timeout or
claiming whole-machine power-loss safety.

## Evidence and remaining scope

- [Native summary](native-summary.json), [native raw archive](native-cuts.zip)
  and [complete archive inventory](native-cuts-files.json), including failure.
- [Root summary](root-checks.json), [logs/XML](root-checks.zip) and
  [inventory](root-checks-files.json).
- [Independent reports and raw checks](independent-review.zip) and
  [inventory](independent-review-files.json). Exploration is an explicit subset
  excluding copied JDK source text; its original local archive is not changed.
- [Staged source identity](source-identity.json), [local link checks](links.json)
  and [checksums](SHA256SUMS.txt).
- Root raw output:
  `C:/Users/Administrator/AppData/Local/Temp/arce-v140-mig-cuts-2cee140e075a4234b93df8bb6df2d28e/`.

This is bounded two-store process recovery on the tested filesystem. It does
not prove disk-loss or power-loss durability, general rocket/chunk/inventory
atomicity, real-client V1/V2, hardware performance or long-term stability.
Whole-subsystem migration and full mechanical/dimensional completion remain
outside this slice. Full campaigns stay deferred under ADR-018. Keep complete
matched backups; neither scratch files nor unrelated old worlds are recovery
sources. All v1.4 G0-G9 remain open and version status stays IN_PROGRESS.
