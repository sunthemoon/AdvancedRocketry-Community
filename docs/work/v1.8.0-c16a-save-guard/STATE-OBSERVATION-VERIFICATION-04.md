# Fixed chunk state diagnostic verification

Date: 2026-10-04. Owner and sole integrator: Root.
Version: v1.8.0, IN_PROGRESS / IMPLEMENTING. All Required Gates remain open.

## Committed source and scope

The reviewed bounded diagnostic source is committed and pushed at
`50b46f091382d19fa683404fc3ace0896220b0c9`. Full checks ran at
`a385ba452ab756b5689a1cbf21e2177129b93dc9`, whose additional C18 contract
records do not implement C18 runtime. Source24 means the declared 2,997-input
diagnostic build cohort, not a whole-repository snapshot. The user's modified
AGENTS and Gradle wrapper line endings are individually pinned and qualified,
not misrepresented as raw Git-object identity.

The opt-in command observes only fixed Overworld chunk (11,11) from a
permission-4 non-player source on the server thread. It reads noSave and the
existing visible-holder debug entry, escapes at most 256 ASCII bytes and adds
one query inside the original unload deadline. It neither acquires a chunk nor
changes a ticket, resource, save policy, denial or event. A holder entry/state
line is not a replacement for an Unload event or completed disposal.

## Actual execution

Java 17.0.7 / Forge 47.4.10, offline/no-daemon/two Gradle workers/Xmx2G.
All new helpers/process TEMP/TMP/TMPDIR and Java temporary output use
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/guard-observation-04/`.
Fresh C/D prechecks exceeded 10,000,000,000 bytes before Java/native launches.

| Actual check | Result |
|---|---|
| `gradlew.bat clean build test runData` with the recorded offline/temp flags | exit 0 / 175.897354 s; 1,773 JUnit / 331 suites / zero failures, errors or skips; DataGen 771 files and zero repeat writes |
| Same flags, `runGameTestServer` | exit 0 / 244.635672 s; all 464 required tests pass |
| Development formatter selector | exit 0 / 28.679004 s; eight JUnit cases; not a separate full build |
| Four focused Python modules | exit 0 / 2.341967 s; 120 cases |
| Bounded unchanged repository validator with the sole owner-private inventory exclusion | exit 0 / 327.118643 s; 45 passed, zero pending, warnings or failures |
| Client imports, accepted ledger, planning, provenance, machine resources, packaged artifact, generated diff and whitespace | exit 0 |
| Content-ledger closure and original global dirty diff | each exits 1; still failures |
| Fresh strict native04 | exit 1 / 91.332594 s; actual Unload event absent within the original 60 s |

The three captured GameTest streams each contain 61 ERROR headers. They remain
disclosed and are not covered by a blanket clean-log waiver. Original commands,
durations, raw streams, XML and nonzero results are in the compact packets.

The exact artifacts remain external under the diagnostic cohort's
`artifacts-01/`, not duplicated into repository evidence:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Main | 5,446,846 | `f8fe53aa38f2221488bcd1d4b52a3795ab9674c9dc64d3a02b9a347f613dc6c2` |
| Sources | 2,623,401 | `5fbf173072c588bb78a66a20c6fb95c16cd44d6b2d4294ac115ceb649647034d` |
| API | 51,045 | `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da` |

## Native failure and bounded observations

Native04 names the fresh attempt with this artifact. It is not a successful
retry. Exactly 27 commands include one state query. Initial/post-removal save
windows each contain three paired EventBus/ChunkMap ERROR headers; the complete
stdout contains seven pairs. The actual state reports no_save=false and an
untruncated 112-byte encoded holder frame beginning with level36, full status
and INACCESSIBLE. No real Unload marker, reacquisition, live restoration, clean
stop, second cycle or PASS summary was obtained.

After the failed process was aborted, the stopped fixed compressed record is
exactly 5,898 bytes, SHA-256
`d9b170538589303c92cef7a2430407ed20f790c23df4e80da30388434ea54ea8`.
All four typed tank roots and both AIR cells are retained. This is strictly
post-abort observation, not a successful runtime restoration/restart.

The copied Forced metadata remains 79 bytes, SHA-256
`c9b7030c645d2995ac9be13d08964eb98f1942b2170c34b3f893100daa6acc50`:
exactly five marks (11,11), (16,16), (16,-2), (24,24), (32,32). Together with
primary ticket propagation and the actual level36 holder, these identify a
copied-fixture eligibility gap. They do not establish a unique production
cause or guarantee that a fixture amendment will pass. The proposed
[separate setup task](COPIED-FORCED-SETUP-TASK-05.md) preserves every original
event/restoration/record/restart oracle and mutates only three copied marks.

Source 2,997 inputs, source-world 139 files, 104 original libraries, installed
main/fixture artifacts and tested HEAD are unchanged. Stopped runtime properties
are explicitly captured after vanilla rewrites defaults; their stopped hash
is not equated with the initial configuration hash.

## Independent review and compact evidence

Independent five-file source review is frozen with no introduced findings:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/agor-2b74c058e1/REVIEW-01.md`, SHA-256
`302af9f958a9d03eb97414bed490ddd3bf9e93b2372d146ae6341b5c57cb7b34`.
It reruns 120 Python cases/eight controls and 1,035,790 standalone pure Java
formatter assertions, without claiming Forge/native replay.

- [Build/check packet](state-observation-04/BUILD-CHECKS-01.zip): 796,951 bytes,
  SHA-256 `8c4ed91c71b9fb5e5f5fb3f232c77c033da8db43b533407bcf5f91c671cbeb63`;
  385 payloads / 6,654,785 uncompressed bytes.
- [Preserved native failure packet](state-observation-04/NATIVE-FAILURE-01.zip):
  67,367 bytes, SHA-256
  `2fb4a7fa44350f941d6c7c5185071759a0562f227c95edb5034dbf9b2c03342b`;
  27 payloads / 443,534 uncompressed bytes.

CRC and every internal payload hash were checked. No full source/JAR/runtime/
world/region export is included. Frozen originals and historical failed cohorts
remain untouched. A different-agent terminal-result audit is recorded separately;
source/full-check success does not upgrade native04 or any Gate.

That audit is frozen at
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/agoa-0f4bcd13ef/REPORT-01.md`, 10,602
bytes, SHA-256
`525986227a471583053736d8d67b0ade181dfd35d1f9b005fad7cf43d6e16863`.
Its internal manifest SHA-256 is
`4593db3d51e56fdf2dcfa3509d57640c84bfff5fd987fed5fe84fa5ca67ec11e`.
It independently checks the counts, JAR CRC/source/resource identities, all
2,997 named inputs, actual failed native state and typed stopped fragments,
45 repository checks, nonzero checks and blocked cleanup. Eight independent
controls pass; its four original assumption/locator failures remain preserved.
The raw wrapper distinction is exactly 92 LF-to-CRLF expansions. This audit
does not claim its own Gradle, Forge/native or source-world replay.

## Cleanup and open work

The exact newly owned native-04/server path was prechecked: 260 regular files,
209,666,732 bytes, no reparse points or listener, with compact stopped captures
preserved. Its native PowerShell literal recursive deletion was rejected by
tool policy before OS execution. No alternate deletion method was attempted;
the copy remains cleanup debt. The first malformed newline suffix and inline
metadata-correction quoting failure are preserved with a separate corrected
precheck. Earlier C scripts and blocked old D copies also remain undeleted.
No source world, sealed evidence or other-agent file was removed.

R-021 remains open and unaccepted. Real GuardTicket/writers, native codecs,
first-save/final-unload admission, physical hatches/controller, cross-store and
crash conservation, real-client/GPU/multiplayer and all v1.8 G0-G9 remain open.
Six independently reviewed C18 inventory tutorials are in a separate worktree;
their author-only 12 JUnit/three compiled GameTests are not this cohort's runtime
or content-ledger delivery. v1.8 is not complete.
