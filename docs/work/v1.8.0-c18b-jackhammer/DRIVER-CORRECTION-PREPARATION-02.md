# C18b external harness binding correction preparation

Date: 2026-10-09. Harness source archive 01 was committed at 269ff8c3 before
any native launch. Independent actual-code/pure/binary review identifies two
general binding gaps: unstaged-only source checks can miss staged/untracked
helper substitution, and accepting a MinecraftServer-log marker is broader
than the adapter's actual fixture stdout emission. Retain the original archive
and independent reproductions, without calling them a native observation.

Root will freeze a separate revision that verifies exact helper bytes against
the requested committed Git blobs before executing them, and constrains the
marker to the fixture's raw/known stdout forms. Add positive and negative pure
checks for the changed behavior; no test deadline or result assertion is relaxed.
The new harness must be committed and independently reviewed before launch.

The tool source itself has advanced separately to committed 3b18a6bc for host
template resolution and actual upstream recipe history. Full Root qualification
continues at that source; Main still contains no tool source. Changing a source
input means explicitly recording the actual new tool/adapter artifact cohort,
not rebinding prior failure evidence to a new SHA. No packaged result, gameplay,
player-saving implementation, survival/asset/client delivery or Gate is claimed.
