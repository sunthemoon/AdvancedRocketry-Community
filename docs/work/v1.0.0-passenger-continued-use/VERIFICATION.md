# v1.0.0 passenger continued use

2026-09-06, branch `codex/v1.0.0-stable-core`, development worktree based on
`34b2e99b48a33f4ba8905b6a69a38efee1649d3f`. No release candidate, commit/tag,
human Gate approval, remote access or long-load run.

## Scope and findings

Continue using the retained migrated rocket after landing: passenger leave,
boarding/reconnect, ordinary fuel loading, return and disassembly lifecycle.
The prior legacy migration evidence remains immutable. No schema, protocol,
public ID, asset, dependency or later-version feature is introduced.

Three new Forge regressions reproduced three defects before the fix:

1. A passenger who explicitly used LEAVE after landing is teleported back on
   login because the old trip manifest is still used.
2. A passenger newly boarded after landing is omitted by that same old manifest.
3. Disassembling a refueled arrival leaves its committed transfer record behind,
   allowing subsequent recovery to rebuild the consumed rocket.

The tests use the production boarding/leaving and disassembly adapters, actual
Forge flight/journal state, and a FakePlayer that counts recovery teleports.
They are not a substitute for native network or visual evidence.

## Changes and authority boundaries

Loaded, owner-matching, journal-bound committed LANDED/FUELED entities supply
their latest seat manifest for login/remount decisions. This initial lookup
does not activate chunks for unrelated players. In-flight/missing-authority
recovery retains the durable trip snapshot and existing bounded lookup.
Refueled arrivals are treated as settled rather than kept in the active flight
tick set, and successful disassembly releases their completed reservation.
Successful unchanged singleton settled recovery is INFO; missing entities,
duplicates, repairs and interrupted phases retain WARN. Scanner unchanged.

Files: `RocketTransferEntities`, `RocketTransferRecoveryService`,
`RocketTransferService`, new `RocketPassengerContinuedUseGameTests`, and a
package-visible fixture in `RocketPassengerPersistenceGameTests`. Also update
changelog, community provenance, implementation log and current action.

## Retained failures and test isolation

- Before run 1: `gradlew.bat runGameTestServer --no-daemon`, exit 1, 57 s,
  48 tests: both new passenger regressions fail, plus the older 16-vent test.
- Before run 2: same command, exit 1, 56 s, 49 tests: all three new regressions
  fail, plus the same older 16-vent test. Original native logs retained.
- First clean build: exit 0, 34 s. First `test runData runGameTestServer`:
  exit 1, 1m 22s; all three new regressions pass, but the vent test still fails.

The vent diagnostic reports 18 tracked / 17 active providers while all 16
fixture vents are ACTIVE. Inspection found that `AtmosphereGameTests` leaves
two cross-dimension Moon vents behind after its open-room and door tests;
normal GameTest structure cleanup only owns the template dimension. The two
tests now clear their local service and remove their own vent in `finally`.
This fixes fixture lifetime; it does not change atmosphere production code,
16-vent workload, performance assertions, timeout or warmup budget. A clean
build alone did not address the within-run contamination, as the retained
first after-run demonstrates.

Final clean build passes (exit 0, 32 s); `test runData runGameTestServer` passes
(exit 0, 1m 37s), with 399 Java tests / 78 suites and all 49 required GameTests.
The final vent warmup has exactly 16 tracked and 16 active vents, zero pending
scans. No test assertions were weakened. Java XML and original native logs
are archived beside the failed attempts.

Artifact SHA-256: `b41db06ec7af9aee36d486525d439bae8f3e0c344dbe18fcf6050ab7b5c74d68`,
1,271,844 bytes, protocol 5; 745 source inputs in `source-inventory.json`.
`git diff --check` and generated-resource diff return 0. Full worktree
`git diff --exit-code` returns 1 for intended/inherited development changes,
not a clean-candidate G2 pass. Python 3.13.15 collected the evidence with exit 0.

## Native continued use and restart

The stopped migrated world from `arce-legacy-clean-restart` was copied to a
new disposable runtime. Dedicated PID 20500 and native client PIDs 25960 /
26696 used the final artifact; both initial joins and the deliberate reconnect
succeeded. All three Java processes and the driver exit 0, with no cleanup
scanner findings. Driver status is `COMPLETE_WITH_OBSERVATIONS`, not a claim
that every diagnostic request succeeded.

The passenger opened the production console and clicked LEAVE: a normal C2S
SUCCESS receipt and manifest 1 were observed. Test setup then placed that
player at `(48.5,80,8.5)` on a small support block and deliberately disconnected
them. Same-process native Direct Connection rejoined; the authoritative
position remained `(48.5,80,8.5)`, rather than the rocket origin. The player
was positioned near the console again and clicked BOARD: SUCCESS, manifest 2.
This tests explicit UI LEAVE, not all vanilla Shift-dismount semantics.

A fixture fuel loader at Moon `(6,80,8)` was initialized with one 500-unit fuel
cell and the existing owner UUID. The ordinary loader tick transferred 382
units to the rocket (618 -> 1000), leaving 118 buffered. No release-test refuel
command or direct rocket-fuel edit was used. The cell/block were test inputs;
this is not a survival crafting or player item-insertion test.

The owner clicked CONFIRM LAUNCH for Moon -> Earth in the real console. Transfer
`e1b2ece9-5769-4e41-b740-c5b56a6e1b9d` completed all phases. Server receipts
show one Earth rocket `2c03e129-ca61-4718-9453-39f666e94d65`, logical identity
`0ce64af6-4ee0-4c9b-b019-08c2b512bfe4`, fuel 618, two assigned and actually
mounted players, and all 17 diamonds. Both inspected F2/F3 images show Earth,
Y=64.15 and the correct seat offsets at landing origin `(288,63,-128)` on the
NVIDIA hardware renderer. Original Moon entity is removed by the transfer.
Fuel conservation is `618 + 500 = 1000 + 118 = 618 + 118 + 382 burned`.

Normal save inspection shows one world-owned rocket and no player RootVehicle.
The short dedicated restart (PID 9652, Java/driver exit 0) passes its strict
scanner, retains the exact Earth entity, and reads the loader's saved 118-unit
buffer. A second saved-world inspection has exactly the same rocket projection,
including manifest, fuel, cargo and position. This final restart is headless;
the two-client observations belong to the preceding real return flight.

Two diagnostic-control mistakes remain recorded: request 2 waited for `blocks`
instead of the actual `block(s)` fill receipt; request 15 queried vanilla
`Passengers` NBT, which was absent, and its lowercase wait did not match
`Found no elements`. Each retained the original 30-second timeout. Neither
restarted the runtime or changed production behavior; other typed command
receipts establish the scoped results. The direct reconnect screenshots are
additional native F2 files; the filename `passenger-stays-departed-f3.png` does
not establish F3 coordinates (that image has no debug overlay). Its position
claim uses the explicit server receipt instead.

`audit.py` exits 0 after checking 745 source hashes, test results, preserved
control errors, native identities/logs/screenshots, vehicle UUID receipts,
full save/resave projection and loader conservation. Full native bytes are
preserved; 11 PNGs are available, including the two additional reconnect views.
The scoped continued-use task is verified. Overall v1.0 remains IN_PROGRESS.
Broader legacy loading orders, initial handshake reliability, full reference
load, final candidate binding and other Required Gates remain separate work.
