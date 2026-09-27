# v1.3 development tests

## Current-checkpoint verification

Source checkpoint `884908ebfb936402bcc7a437791f8d9683c27506`; documentation-only
handoff changes do not change runtime, fixture, resources or build inputs.
Commands and results from this handoff are recorded in `handoff-verification.json`
and `handoff-checks.zip`. Artifact identities are in
[development-artifacts.json](development-artifacts.json).

The required short command is:

```powershell
.\gradlew.bat clean build runData runGameTestServer --offline --no-daemon --no-build-cache --console=plain
```

Actual result: exit 0 in **2m 38s**, 26 tasks executed / one up-to-date;
**769 JUnit tests / 143 suites** newly executed with zero failures/errors/skips;
**210 Required GameTests passed**; DataGen wrote **0** files. Host/API/sources
JAR bytes equal the preceding COMPAT artifacts; all 27 API classes match their
runtime copies. This disposable GameTest world is under `build/gametest`, after
the command's `clean`; it is not an authentic historical world-upgrade fixture.

The historical-report integrity check pins 14 verification reports and their
metadata; 325 entries in the 11 outer manifests attached to those reports match.
Reports without such an outer manifest are identified, not silently described
as manifest-verified. This is integrity evidence, not a replay of old scenarios.

This uses the installed Java 17 toolchain and cached external dependencies,
not a fresh OS/CI environment. `--no-build-cache` distinguishes new test
execution from COMPAT's earlier cached full JUnit result. GameTest logs contain
expected deliberate storage-failure diagnostics; inspect the actual test result,
not an assumption that every logged ERROR is unexpected. Lag warnings remain
visible and are not performance acceptance.
The actual handoff GameTest warning is **2,233 ms / 44 ticks**. No native server,
real client, remote, process-kill or long-load scenario was launched by this
documentation handoff. The standalone consumer was not rebuilt again.

## Handoff integrity and review

`python -B scripts/validate_repository.py --require-approved-identity` passes
45 checks; `python -B scripts/validate_v1plus_planning.py` validates 11 plans
and the 33-input inventory. The final local-link check covers tracked Markdown
and these new reports; it checks file/directory destinations, not remote pages
or section anchors. `git diff --check` and the scoped production/build-input
diff are clean. Raw commands/helpers are retained in `handoff-checks.zip`.

The [independent review records](independent-review.json) identify two reports
in [their archive](independent-review.zip). The reviewer checked requirement
attribution, planning, 180 links in its snapshot, 69 pinned evidence identities,
325 manifest entries, frozen artifacts/API bytes and the raw build log/XML.
The reviewer read these runtime results; it did not rerun them. A wording error
about the disposable post-clean GameTest world was corrected, leaving no
unresolved finding within this documentation scope.

An attempted checksum check during assembly encountered changed document bytes;
that failed attempt is retained, not presented as a final integrity result.
After attachment, root regenerated the manifest and checked every listed file
against working and staged bytes, both ZIP CRC/entry sets, archived log/XML and
review-report hashes. This is development-evidence integrity, not independent
candidate approval or release acceptance.

## Historical development evidence

Each record below retains its original source/artifact, commands, failures,
raw data and scope. Counts are not added together as one test campaign.

| Slice | Evidence and supported conclusion |
|---|---|
| Version policy/classifier | [API](../../work/v1.3.0-api-version/VERIFICATION.md): metadata semantics and isolated compile boundary |
| Internal containment | [Failure containment](../../work/v1.3.0-rocket-failure-containment/VERIFICATION.md): checked recovery and retained authority |
| Public registry | [Registration](../../work/v1.3.0-adapter-registration/VERIFICATION.md): actual owner-bound event and external payload integration |
| Standalone packaging | [Consumer](../../work/v1.3.0-consumer-packaging/VERIFICATION.md): actual published classifier and separate reobfuscated mod |
| Missing provider/mod | [Recovery](../../work/v1.3.0-external-recovery/VERIFICATION.md): six clean processes and staged journal preservation |
| Cross-dimension cargo | [Flight](../../work/v1.3.0-external-flight/VERIFICATION.md): actual Earth-Moon-return; remaining fuel proof stops before disassembly |
| Fuel disposition | [Disassembly](../../work/v1.3.0-fueled-disassembly/VERIFICATION.md): explicit positive-fuel disposal consent, failure does not preclear fuel |
| Atmosphere | [Boundaries](../../work/v1.3.0-atmosphere-boundaries/VERIFICATION.md): real room scanner, state/tag reload and native restart |
| Equipment | [Suit](../../work/v1.3.0-suit-equipment/VERIFICATION.md): service events and saved chest items, not real-player files/HUD |
| Components | [Components](../../work/v1.3.0-rocket-components/VERIFICATION.md): numeric snapshot stability until reassembly |
| Fuels | [Fuel Loader](../../work/v1.3.0-rocket-fuels/VERIFICATION.md): whole-item batch, remainder, schema migration and bounded quarantine |
| Environment | [Queries](../../work/v1.3.0-environment-queries/VERIFICATION.md): metadata lifecycle/reload and native station identity |
| Satellite | [Payloads](../../work/v1.3.0-satellite-payloads/VERIFICATION.md): real manufacturing/launch/claim, preserved old task across uninstall |
| Compatibility completion | [COMPAT](../../work/v1.3.0-compatibility/VERIFICATION.md): 210 Required GameTests, five independently forced budget tests, 77 Python tests and consumer boundary |

COMPAT's 769-test full JUnit XML was restored from cache, explicitly not a fresh
execution there. Its final fixture `759f214d...` differs from its native-tested
`4207b9b3...` only in GameTest classes; native registration/restart is bound to
the latter. This handoff does not change those historical attributions.

## Deferred / absent evidence

- Final-candidate clean OS/hosted CI and Forge 47.4.23 compatibility lane.
- Full-world stable/Beta upgrade and genuine storage-cut recovery, rather than
  targeted legacy roots or staged journals.
- Actual real-client join, UI, equipment display, optional-client combinations
  and two-player authority/synchronization.
- Defined reference-load performance, long-duration memory and broad modpack
  compatibility. A finite callback delay is not a soak test.

Refer to [the requirement map](REQUIREMENT-MAP.md) and
[Gate status](GATE-STATUS.md). No deferred scenario receives a PASS here.
