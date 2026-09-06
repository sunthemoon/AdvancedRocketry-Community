# v1.0.0 representative-world upgrade preparation

Date: 2026-09-05. Overall status: IN_PROGRESS.
Only `V100-DATA-02-BASELINE` is verified here. No representative world capture,
candidate upgrade, populated-world comparison or release Gate is claimed.
This is the preparation record; the later completed development-world capture
and upgrade checks are in [VERIFICATION.md](VERIFICATION.md).

## Accepted source runtime

The JAR was downloaded from the project's published
[v0.9.0 Beta 1 release](https://github.com/sunthemoon/AdvancedRocketry-Community/releases/tag/v0.9.0-beta.1),
not rebuilt with the changed v1.0 working tree. The accepted release identity
also matches the immutable repository release evidence.

- Artifact: `advancedrocketry-community-1.20.1-0.9.0-beta.1.jar`.
- Size: 1,225,536 bytes; entries: 758.
- SHA-256: `fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad`.
- [Download hash/size check](download-verification.txt),
  [JAR audit against accepted source](artifact-audit-accepted-source.txt),
  [content manifest](accepted-beta-artifact.json).
- Download used HTTPS only, a 2,000,000-byte transfer cap and an exclusive new
  output path. It required no launcher credentials or repository authentication.

The [initial audit](artifact-audit.txt) returned 1 because it compared Beta's
packaged THIRD-PARTY-NOTICES with the modified v1.0 development notice. The
correct rerun uses the unmodified validator and five notice/license inputs
materialized from accepted commit `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`;
their [source hashes](accepted-source-audit.json) are recorded. That audit returns
0. No repository notice, JAR byte or validator assertion was changed to pass.

## Actual dedicated lifecycle

The existing packaged-server installer/runner created a new disposable server:
`C:\Users\Administrator\AppData\Local\Temp\arce-v100-upgrade\beta-baseline-server`.
The matching downloaded JAR is retained in the sibling `accepted-beta` directory.
Java 17.0.7, Forge 47.4.10, JEI absent, loopback port 25612, online-mode=true.

`python -u scripts/run_dedicated_server_smoke.py <accepted versioned JAR>
--expected-mod-version 1.20.1-0.9.0-beta.1 --work-root <upgrade>/dedicated-work
--installer <transactions>/dedicated-work/cache/forge-1.20.1-47.4.10-installer.jar
--session-dir <upgrade>/beta-baseline-server
--evidence-dir <upgrade>/beta-baseline-evidence --port 25612` returned 0.

The installer is checked by the existing fixed SHA-1/SHA-256 contract. First
start/status/save/stop and same-world restart/status/save/stop both pass with
exit 0. [Command output](baseline-command.txt) and
[runtime summary/full logs](accepted-beta-runtime/summary.json) bind these
observations to the accepted Beta JAR. Both Java processes have stopped.

## Preparation boundary

This baseline runtime was left empty and unchanged. The later
[representative fixture](VERIFICATION.md) is generated in a separate copy and
retained as its own original archive. The canonical
[implementation plan](../v1.0.0-implementation-log.md) distinguishes completed
development checks from remaining final-candidate acceptance. No earlier
release evidence was changed by this preparation.

The archived preparation files are covered by [checksums.txt](checksums.txt).
