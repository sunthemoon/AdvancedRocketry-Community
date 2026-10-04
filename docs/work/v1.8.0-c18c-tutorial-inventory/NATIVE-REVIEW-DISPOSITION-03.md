# C18 inventory native fixture revision 3 review disposition

Date: 2026-10-05. Integrator: Root. Status: REVISION_REQUIRED.
Scope: proposed test contract only, not implementation or native execution.

## Exact reviewed inputs and decision

[Task revision 3](NATIVE-TASK-03.md) is 9,718 bytes, SHA-256
`c75fec1a7f1cb5291147abb838712ed695c7c011f060bff1f6f7378011a6b6d4`.
Its cumulative proposal02 companion identities remain exactly as recorded in
that task. Earlier drafts, offline-record ownership finding and the
[revision 2 constructor/join finding](NATIVE-REVIEW-DISPOSITION-02.md) remain
immutable historical evidence. No source or host was run in this review phase.

The new copy-only default-mode, loaded envelope, dual-construction/pre-join
position and native saved-location requirements materially address the prior
scene gap at contract/source level. They do not establish an executed native
footprint. Independent review still identifies two Medium lifecycle conflicts;
Root does not adopt revision 3, enable reflection/hooks or assign implementation.

## Independent findings

1. The pre-boot raw `server.properties` hash is required to stay identical
   across boots, but actual native Main invokes DedicatedServerSettings.forceSave
   before startup. The pinned writer delegates to inherited JDK Properties.store,
   which emits a fresh Date comment. The contract lacks distinct raw-file phase
   bindings for this native write; removing comments or pretending unequal bytes
   are equal is not an accepted correction.
2. The literal `forceload add -80 -80 95 95` is required after each startup.
   A clean seed persists the 121 members, native startup reinstates them, and
   the second add has zero membership changes. The inspected native command
   then selects ERROR_ALL_ADDED, a refusal the contract requires to fail. Seed
   mutation and reload membership/loaded observation need separate exact rules.

These are source-backed contract inconsistencies, not observed native failures
or data loss. A separate proposal is preparing phase-specific technical fixes.
It must retain ownership, player records, loaded admission, resource/time
ceilings, ordinary listener/reload meaning and all original failures. No
gameplay/config/original-world adjustment or runtime acceptance follows yet.

## Actual review checks and failure preservation

The frozen independent report is
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-native-contract03-review-8c51604d2f/REVIEW-03.md`,
SHA-256 `1670cf847adff69ce01679b159e6e37b779ca00314ed134587f8dd37b69edea0`.
Its 46-payload /1,057,687-byte internal manifest SHA is
`744258fd63b5a8571d90ecb764ae7c463f80298c1127bb1cdfc7a3f7d6a61afa`.
Actual 30 bounded controls pass: 18 unchanged author methods and 12 independent
controls. The earlier reviewer 30-test run with one failure/one error, postcheck
type error and exploratory locators remain separate failed records.
All 78 named inputs remain exact. Twelve fixed Git objects, 23 independently
parsed primary classes and twenty author native member identities are checked;
29 report-local links resolve. There are 0 Critical/High, 2 Medium and 0 Low.
The reviewer did not run Java, Gradle, a native host or real client.

## Portable exact proposal and independent evidence

[CONTRACT-CHECKS-01.zip](native-contract03-01/CONTRACT-CHECKS-01.zip) and its
[SHA-256 sidecar](native-contract03-01/CONTRACT-CHECKS-01.zip.sha256) preserve the
exact proposal02, review03, their original metadata/failures and Root Task03.
The packet is 259,900 bytes, SHA-256
`1c18becf49d74b3f3a4e3418fa4f13457780fbb74bb908b126837ccf6adad448`.
Its 103 payloads total 1,498,929 expanded bytes. Root's first collector exits 1
before creating a ZIP because it guesses the proposal checksum filename with
a .txt suffix. That helper/log remains unchanged; a separately corrected
collector exits 0 and verifies CRC, singleton names, all sizes/digests and 103
original payload pins. No full source/JAR/runtime/world or private export.
Internal checksum manifests name only packet-local evidence, not mutable builds.
Original read-input locations remain explicitly qualified observations.

## Remaining scope and delivery status

Suitable clean source-host selection, packaged mappings/cache inspection,
constructor/callback/tracking/timing, actual record readers/positive emission,
owned partial cleanup, real listener/two-host save/load and independent result
audit remain unrun. Connected mocks are not V1/V2/GPU/multiplayer proof.
The future fixture requires its own reviewed committed source/JAR cohort.
Six tutorial units remain PLANNED; R-021/new guarded writer and all other
v1.8 Required Gates remain separate and open. New helpers/process temp use
project-parent D; previously policy-rejected cleanup is still unfinished.
