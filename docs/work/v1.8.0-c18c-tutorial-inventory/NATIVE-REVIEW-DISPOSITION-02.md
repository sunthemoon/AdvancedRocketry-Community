# C18 inventory native fixture revision 2 review disposition

Date: 2026-10-04. Integrator: Root. Status: REVISION_REQUIRED.
Scope: test-only contract, not implementation or native execution.

## Reviewed inputs and decision

The unchanged first draft has SHA-256
`5f21c97023477d762bb317d56c43083756abf8d3f790bb5601090806f844a02a`.
Its earlier offline-record ownership Medium and source-backed counterexample
are preserved, including the historical READY_FOR_CONTRACT_REVIEW header; it
does not describe an admitted fixture. The new separately proposed
[task revision 2](NATIVE-TASK-02.md)
is 11,198 bytes, SHA-256
`d1eebd7b4683ff9da0cacafa2e72f9bf98ed98d1552047c880b5310884e44ced`.
Its normative companion identities are recorded in that task; none was edited
after review intake. All six committed tutorial source files remain unchanged.

The replacement proposes both identities' non-mutating pre-constructor file,
profile/progress-cache admission and a driver-created same-copy ownership
receipt after verified clean stop. Those proposed protections do not authorize
existing-record adoption or supply advancement progress. However, independent
review identifies a new Medium M1 in the proposed loaded-only scene. Root does
not adopt revision 2, enable its hook or assign implementation from it.

## Independent finding and actual checks

The proposal admits only loaded target chunk `(0,0)` and later teleports the
players there. Native `ServerPlayer` construction first invokes
`fudgeSpawnLocation`; its conditional skylight/non-Adventure route calls native
Overworld respawn search, which accesses chunks through a default create=true
path. Native join also loads and selects player location/Level and adds the
player before a later fixture teleport. Final position therefore does not prove
the promised pre-construction/join loaded-only boundary.

This is a source-backed contract gap, **not an observed forced load or player
data loss**. A separately versioned correction must bind and pre-admit the
constructor's actual host/default-mode/shared-spawn search/collision region and
reload's native dimension/position before construction. The exact correction
needs independent review; ordinary gameplay/server configuration is unchanged.

The independent frozen report is
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-native-proposal-review-4ea16d018d/REVIEW-01.md`,
SHA-256 `9569049b655529fc33808d2145ecbf81b94cd2fe8eb81525ba1e59ebcd710cd6`.
Its 30-payload /501,504-byte internal manifest SHA is
`7120aa8d39c369a96d903b8e33897b77db6adfabf4933ea9d95c30f3499416e3`.
Actual bounded Python checks exit 0: 18 rerun author specification checks plus
12 independent controls. All 74 named external/Git inputs are rechecked without
drift, and 26 local links resolve. There is one Medium, no Critical/High/Low.
The two failed reviewer parser attempts and locator error remain in its bundle.
No Java, Gradle, native server, real client, source or Git mutation was performed
by the reviewer. These controls do not test an implemented fixture.

## Preserved authority and remaining work

The reviewer separately verifies the proposed exact read-only native field
descriptors/modifiers and vanilla saved empty Inventory tag 9/subtype 0/count 0
against pinned primary binary facts. That does not prove packaged reflective
access, record readers, partial joins/disposal or clean restart at runtime.
The proposed technical reflection remains unadopted, not a public policy/API.

Suitable clean copied-world selection is unrun. Historic Guard scenes contain
protected oversized roots and ERROR pairs; their logs/artifacts are not clean
C18 restart proof. The contract's no-earlier-fixture and unknown ERROR/FATAL
refusal conditions are not relaxed. A future fixture requires its own reviewed,
committed source/JAR cohort and actual first/second host records and result audit.

## Portable proposal and failure evidence

[CONTRACT-CHECKS-01.zip](native-contract02-01/CONTRACT-CHECKS-01.zip) and its
[SHA-256 sidecar](native-contract02-01/CONTRACT-CHECKS-01.zip.sha256) preserve the
exact proposal, independent finding/controls/failures, separately frozen selected
spawn/join primary facts and both unchanged Root task inputs. The packet is
244,220 bytes, SHA-256
`29d41094c02a7047ea881bc7448a4143da55283cd80f83116fe3f531b5099439`.
Its 114 payloads total 1,290,973 expanded bytes. Root's actual Python collector
exits 0; CRC, singleton member names, all sizes/digests and 114 external payload
pins are checked. Internal manifests name only packet-local evidence. Selected
read-source pins are observations, not mutable build inputs in that checksum
list. No full source tree, JAR, runtime, world/region or private-bundle export.
Evidence-owned helpers and exact reviewed draft documents are included.

The author's separate spawn-fact report is
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-native-spawn-facts-02165b7f3c/REPORT-01.md`,
SHA `dfd35b6f0d66bf5904c1a05ca45b049da83678dcf103d7e72d41363973184d18`.
It pins 20 selected classes, six external inputs and eight branch-model rows;
21 local links resolve. It establishes that the alternate mode still reads
shared spawn/collision, and join can load dimension/position/optional RootVehicle.
It does not establish a sufficient scene correction or executed loaded-only proof.
Its failed inspectors/publication ordering remain preserved. The independently
reviewed source route above, not the author's labels, supports the Medium.

## Unchanged delivery status

Six tutorial units stay PLANNED. Whole v1.8 remains IN_PROGRESS /IMPLEMENTING,
186 PLANNED units /154 REVIEW assets and G0-G9 open. Source25's actual build,
GameTests and DataGen, and the separate fixed Guard native diagnostic, are not
rerun or credited to this proposal phase. R-021/shared writer/crash/client/GPU
obligations remain separate. Temporary helpers stay under project-parent D;
prior policy-rejected C and stopped-copy cleanup remains unfinished.
