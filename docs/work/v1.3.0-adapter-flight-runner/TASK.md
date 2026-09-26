# V130-ROCKET-03C packaged flight runner

Status: implemented; Python checks executed; corrected packaged rerun pending.

Baseline: `7c543d7072ae89c9b497d9699c674507f3759643`.
Branch: `codex/v1.3.0-adapter-flight`.
Write scope: the new flight runner, its focused Python test file, and this task directory.
Production, fixture, build, shared helpers and canonical version documents are unchanged.

## Contract

Four owned, cleanly stopped processes in one new disposable world:

1. Assemble five blocks with the external two-slot cargo container, fill once,
   fly from Earth to Moon through production flight/transfer services, save/stop.
2. Restart on Moon, verify landed entity and retained transfer receipt, return
   using remaining fuel without topping up, save the Earth landing and stop.
3. Restart on Earth, verify the landed entity/receipt, disassemble through the
   production command, verify native cargo, save/stop.
4. Restart the native container, verify the same cargo and empty journals, stop.

The driver requires the published normal host JAR and separately reobfuscated
fixture JAR, never the API classifier alone. It records SHA-256, actual Forge
status, registration event, configuration, commands, process exits and raw logs.
Only its own process is stopped or aborted. Existing output directories, linked
ancestors, preexisting server content other than libraries, and nested evidence
are rejected by the existing recovery-runner input validator.

## Observations

- One real outbound and return flight; no teleport, checkpoint, synthetic journal,
  crash, passengers, historical 20-trip matrix, remote work or player simulation.
- Exact native item slots, counts and named diamond metadata; exact external
  adapter/version/envelope; all immutable snapshot fields conserved on relocation.
- Physical/snapshot UUID and snapshot hash change; logical ID and owner persist.
- Positive default fuel quotes of 372; entity fuel 1000 -> 628 -> 256 and exact
  ordered transfer UUID debit ledger. No return refuel command.
- LANDED entity has no active plan/transfer, while the bound COMMITTED journal
  intentionally retains TRANSIT/DESCENT receipt data. The return replaces the
  first reservation; disassembly releases the second and leaves both journals empty.
- Raw region/entity files are copied only after clean stop. The finite observation
  covers three actual origins, their complete block footprint and one-block margin,
  at most 12 chunks. Loaded rocket enumeration is cross-dimension, once only.

## Validation performed

- Required project/governance, version, quality-budget and parallel-work documents
  read; reviewed production command, snapshot, flight, landing and journal codecs.
- `python -B -m unittest discover -s tests -p test_v130_adapter_flight_smoke.py -v`:
  20 tests passed after the timestamp regression fix; no Java or server process was started by this worker.
- `python -B scripts/run_v130_adapter_flight_smoke.py --help`: exit 0.
- `git diff --check`: exit 0.

Initial static drafts had a wrong tank ID and incomplete wait-condition prefix;
these were corrected before any packaged run. Independent runtime review found
the existing cross-dimension selector behavior; one enumeration plus a regression
replaced the draft per-dimension enumeration. Missing/duplicate block observations
are rejected before validating material authority.

### First packaged attempt / narrow timestamp correction

The independent run stopped after its second clean JVM exit (0), before second
disk capture: byte equality compared the Java-generated `server.properties`
timestamp comment. The two lines were `#Sun Sep 27 00:24:52 CST 2026` and
`#Sun Sep 27 00:25:37 CST 2026`; both TOML files were byte-identical. Evidence
remains at `C:/Users/Administrator/AppData/Local/Temp/arce-v130-adapter-flight-e1769690262f4565a228cc18bf07a75c/flight-evidence`.
This is a failed attempt, not a four-process pass.

The correction validates the exact generated header and bounded timestamp syntax,
then excludes only that second line from comparison. Every remaining properties
byte and all TOML bytes stay binding. Raw files and per-file SHA-256 remain in
each phase. Regression coverage accepts the observed time change but rejects
changed properties, unknown settings, added comments/lines, line-ending changes,
invalid/missing timestamp headers and TOML byte changes. A read-only check against
the two actual captured configurations confirmed all three corrected comparison
identities match. No server was launched for this correction.

## Limits / remaining work

Integration and independent corrected packaged execution are not performed by
this worker. Python fixtures establish oracle behavior, not Minecraft behavior.
NBT comparison is decoded semantic comparison with untouched raw files retained;
the script binds production snapshot hashes/checksums but does not reimplement
their Java hashing algorithms. Cargo/drop claims are scoped to touched chunks
and loaded rockets, not an unbounded scan of the entire world.

Existing fuel-disassembly behavior is an explicit separate issue: the remaining
256 units are entity flight state; plain restored tanks have no persisted fuel
export/refund. This runner checks fuel through pre-disassembly only and does not
claim fuel survives teardown. No production change or concealment by emptying
the tank is included. v1.3.0 full Required Gates remain unapproved.
