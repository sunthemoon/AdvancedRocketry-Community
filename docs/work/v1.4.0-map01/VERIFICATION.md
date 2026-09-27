# V140-MAP-01 — persistent planetary bindings

Date: 2026-09-27. Development slice, not a candidate or Gate approval.
Status: verified development slice with a recorded P3 diagnostic follow-up.

## Identity, decision and implementation

- Branch: `codex/v1.4.0-planetary-expansion`.
- Base: `eee45f5010e1332c69c6eb18f127fc468cfea5df`.
- Java 17.0.7, Forge 47.4.10, build `1.20.1-1.4.0-dev`.
- Accepted [ADR-032](../../decisions/ADR-032-PERSISTENT-PLANETARY-BINDINGS.md)
  under standing maintainer authorization after independent draft/source review;
  not a claim of a new manual numbered-ADR approval.
- Root is the sole tracked writer. The untracked user documentation bundle was
  excluded. Independent reviewer writes only fresh Temp evidence/build outputs.

The world-owned binding service records mapped and explicitly unmapped body IDs
in an independent schema-1 JSON file. Identities remain after pack removal,
with reverse Level reservation. Reload cannot swap mappings, convert
mapped/unmapped identity or reuse a retired body's Level. The catalog's existing
128 active-body limit is distinct from 128 lifetime bindings and a 32768-byte
binding file; exceeding either bound rejects rather than evicts.

First attachment prospectively adopts the validated catalog. It does not infer
pre-feature history from discovery/station data or certify authentic historical
migration. Operators must retain their last working packs and take a complete
backup before first upgrade. Existing save schemas, station/rocket authority,
world DataVersion, API 1.7 and display protocol 2 remain unchanged.

The pre-Level startup hook attaches to the world's explicit persistence service.
Pinned bytecode confirms that hook precedes `loadLevel` for Dedicated, Integrated
and GameTest servers. After startup, a paired reload checks binding compatibility
and disk identity, stages/forces/reads back/atomically replaces new binding data,
then publishes the already validated pair. Metadata-only reloads also check disk
identity but do not rewrite it. Stop clears the attachment. Failed initial
attachment invalidates readiness; rejected later reload retains the previous
pair, generation and route-cache owner.

Only `NoSuchFileException` means an absent file. Malformed/future/oversized,
non-regular/symlink, unreadable and externally changed state never initializes
empty authority. A previous `.pending` stage blocks startup; caught ordinary
failures clean only their own stage. There is no non-atomic replacement fallback.
This process-level ordering is not a hardware power-loss or whole-Forge rollback
guarantee. No new Level/chunk, terrain, physical destination or public API is
introduced by recording a mapping.

See the [data guide](../../CELESTIAL-DATA-GUIDE.md) for operator behavior and
the [implementation log](../v1.4.0-implementation-log.md) for remaining scope.

## Actual commands and results

Working directory: repository root. `JAVA_HOME=C:/Program Files/Java/jdk-17.0.7`.

```text
gradlew.bat test --tests '*celestial.binding.*' --tests '*celestial.data.*' --offline --no-daemon --console=plain
gradlew.bat clean build runData runGameTestServer --offline --no-daemon --console=plain
```

Both exited **0**. Initial targeted command: **40 tests**, **32 seconds**.
Full root command: **832 JUnit / 149 suites**, zero failures/errors/skips;
`:test` actually executed. **213 required GameTests passed**; startup attached
three baseline bindings before world loading. Build completed in **177 seconds**,
DataGen wrote zero files. The root GameTest run retains **2241 ms / 44 ticks**
lag and the existing deliberate fault-injection diagnostics; not performance
acceptance. Frozen XML/logs are in [root checks](root-checks.zip), with
[counts](root-checks.json).

The **17 new JUnit tests** cover adopted/fixed identity, immutable metadata,
removal/re-add and restart, reverse-Level reuse/alias/swap, explicit unmapped
entries, lifetime and encoded-byte capacity, strict codec/schema/types/IDs,
actual file persistence, malformed/future/nonregular/pending state, injected
atomic-commit failure and retry, indeterminate file attributes, actual Windows
symbolic-link rejection, retained paired generation/cache, failed startup and
second-world lifecycle separation. No test was skipped for symlink permissions.

Using `PYTHONUTF8=1` and `D:/python/pyenv/pyenv-win/shims/python.bat -B`:

```text
python -B -m unittest tests.test_v140_celestial_schema_smoke -v
python -B scripts/validate_v1plus_planning.py
python -B scripts/validate_repository.py --require-approved-identity
git diff --check
git diff --exit-code HEAD -- src/generated
```

Root Python **10/10 passed** in **0.218 seconds**, including one new raw binding
capture/invariant test. Planning passed **11 plans / 33 inputs**. Strict repository
validation exited **0**, **45 passed / zero pending, warnings or failures**.
No package-root validation or
full acceptance campaign was requested. Generated resources have no diff.

Independent forced key rerun:

```text
.\gradlew.bat test --tests *PlanetaryBindingsTest --tests *PlanetaryReloadTest --tests *BoundedDefinitionJsonTest --tests *BodyContextResolverTest --tests *LegacyFlightTargetMigratorTest --tests *RocketTargetFlightPlannerTest --tests *ServerEnvironmentQueriesTest --rerun-tasks --offline --no-daemon --no-build-cache --console=plain
```

**66 tests / 7 suites**, no failures/errors/skips,
**53.210 seconds**, **16/16 tasks executed**, exit **0**. It includes bindings,
actual reload/raw reader, context, legacy flight targets, target planning and
environment queries. Independent Python **10/10**, **0.176 seconds**. All **1248**
inspected source/build inputs and all three JARs were unchanged before/after.
Exact arguments/XML/results belong to [independent evidence](independent-review.zip).
The final unchanged report is member `review-2/REVIEW-FINAL.md`, SHA256
`b604e6df18207f2b8353ff169574fe1ded7d0013257da69ce53088ed9011106e`.
The prior contract report and initial source finding are retained separately.

## Native successful cycles

```text
python -B scripts/run_v140_celestial_schema_smoke.py <libraries-only-server> --host-jar <frozen-main.jar> --evidence-dir <fresh-evidence> --java <java17> --accept-eula --check-bindings
```

Harness exit **0**, both packaged JVMs exit **0**. Loopback port **62551**;
host/Minecraft/Forge only, no players. A real Moon station is created before
reload; its complete native `data` compound remains unchanged through both
cycles. Station UUID: `5f902884-429e-4bb9-a845-2f6e7f98e42a`.

1. Startup: generation 1, three bodies/four routes; three recorded bindings.
2. Generation 2 adds unmapped gas metadata and closes Moon arrivals, with the
   Earth-Moon route distance 123; gas records an explicit unmapped identity.
3. Generation 3 adds a mapped, closed test body: five bodies/four routes. This
   is metadata for a test key, not a newly created physical world.
4. Remove it: generation 4 returns to four bodies; binding bytes stay unchanged.
5. Re-add unchanged: generation 5, same binding. Attempt a body remap and a
   renamed body on the reserved Level: both retain generation 5, route distance
   123, the original body mapping and exact ledger bytes.
6. Repair: generation 6 accepts the original mapping; save/stop. Restart the
   same world: generation 1, five bodies/four routes, byte-identical ledger.

The final ledger, fault inputs, route/body/generation commands and raw station
captures are archived, not inferred only from packaged definitions. The clean
phases contain respectively two exact intentional reload ERROR lines and zero
ERROR/FATAL on restart. Ordinary standard WARN counts are **26/10**, plus the
terminal bootstrap warning outside that format. Native startup retains a
**4769 ms / 95 ticks** lag warning; no workload/performance claim follows.

## Separate offline-remap startup refusal

```text
python -B <archived probe_rejected_startup.py> <same-server> <frozen-main.jar> <fresh-rejection-evidence> <java17>
```

After the two clean cycles, change only the disposable pack's test Level, start
the same world, and restore the original pack bytes in `finally`. Helper exit
**0**, result **OBSERVED_REJECTION**: the explicit recorded-body conflict occurs
before `Preparing level` and before readiness; ledger and station files stay
byte-identical. This is not a third clean startup, a forced storage crash or an
S2 pass. It tests an actual cross-process attempted remap.

The native JVM returns **0 despite rejected startup**. Raw logs retain **4 ERROR
and 1 FATAL** lines: event dispatch rejection, server exception/crash reporting,
and a subsequent Minecraft shutdown `NullPointerException` because its Level
was never initialized. The crash report and full stack traces are preserved.
Native `level.dat` bytes differ from the preceding clean capture; pre-Level
refusal is not a guarantee of no vanilla startup writes. Ledger/station byte
conservation is the narrower observed result, not whole-world immutability.
The probe therefore uses explicit refusal, absence of world readiness and file
identity, not the exit code, as its oracle. Do not treat this as a warning-free
cycle or hide the platform shutdown error. A post-refusal successful fourth JVM
was not run; the input pack was restored and its hash recorded.

See [native summary](native-summary.json), [unchanged raw captures](native-checks.zip)
and [rejection summary](rejected-startup.json).

Independent raw readback verified **33/33** clean-cycle manifest files and
**11/11** refusal manifest files, the exact five-entry ledger, full station NBT
and all three log variants. The first supplemental audit incorrectly asserted
that live `level.dat` would also remain byte-identical after the separate failed
startup. That failed helper/output remain unchanged in the review archive as
`review-2/audit_native-first.py` and `review-2/native-audit-first.txt`.
The corrected audit records both raw identities and retains the ledger/station
conservation assertions; no runtime test or original evidence was changed.
Final audit output is `review-2/native-audit.json` / `native-audit.txt`.

## Findings, limitations and identities

- **Resolved P2:** initial store used `Files.exists == false` as absence; JDK 17
  also returns false for indeterminate attributes. Independent source review
  identified this before verification. Checked attributes now propagate errors
  other than `NoSuchFileException`; an injected AccessDenied regression covers
  initial open and metadata-only reload. Original source/report remain archived.
- **Open P3 diagnostic:** reverse-Level conflict text can name the new rejected
  alias as the reserving body when it sorts first. The native refusal shows this;
  the old ledger still retains the true owner and neither candidate is accepted.
  Correct the wording/retained-owner lookup in remaining v1.4 work. This is not
  an ownership escape or an assertion that the rejected alias was persisted.
- Prospective adoption does not certify custom history before this feature.
  Physical Level/landing admission and old-target checks still belong to MAP-02;
  authentic whole-world migration remains a separate obligation.

[Artifact identities](development-artifacts.json): main JAR
`9fc582f1b83599526627b065d72b9995fbb7a69e04065a135dce7aa8697b1acf`;
API remains byte-identical to DATA-02/v1.3,
`50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf`.
Independent archive inspection matched all 27 exported API classes to the main
JAR; it is not a new consumer-mod execution or publication.
No consumer mod publication, new dependency or upstream source/art import.

[Source identities](source-identity.json), [review file identities](independent-review.json)
and [SHA256SUMS](SHA256SUMS.txt) bind the evidence. Final packaging checks staged
blobs, raw file identities and ZIP CRC/member uniqueness. [Local link checks](links.json)
check file existence, not anchors or external URLs.

All v1.4/inherited Required Gates remain open. No candidate/tag/release/Gate PASS.
The full remote, S2, GPU/two-real-client, historical-upgrade and long-load campaign
remains deferred under ADR-018 until the original mechanics/dimensions are
implemented. Continue MAP-02's actual world data, bounded landing and station
integration, including the recorded low-severity diagnostic follow-up.
