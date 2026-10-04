# C18a-01 analyzer integration checkpoint

Date: 2026-10-05. Status: IMPLEMENTED_UNVERIFIED for complete leaf acceptance.
The [actual source disposition](SOURCE-REVIEW-DISPOSITION-01.md) permits the
reviewed source integration only. This checkpoint is not a version Gate.

## Exact implementation and changes

Root imports 17 exact author postimages: nine main Java files, five scoped test
classes and three author task records. Eight Root central files wire the item,
creative tab, lifecycle, COMMON `lifeSupport.classicDevicesEnabled` switch and
DataGen/language providers, plus the existing config-count assertion and new
three-test switch suite. The existing assertion changes 69 to 70 solely for
the added leaf; ranges and other assertions are retained.

The original resources were registered before authoring; generated output
identities are appended in [provenance](../../provenance/v1.8.0-c18a-analyzer-new-resources.md).
Four new generated files and two bilingual language additions are integrated.
Every pre-existing language key/value remains unchanged, with 15 new keys per
locale. There are no schema, network, public API or build dependency changes.

## Actual intermediate execution

Base HEAD: `16e555111ad957e80570317cb103fb9dadc9895a`, with reviewed uncommitted
source. This is a development intermediate, never a delivered/source cohort.
The command uses Java 17, offline Gradle, no daemon, two workers and a 2 GiB heap.
It selects the five author suites and `ClassicDevicesConfigTest` plus
`CommonConfigTest`, then runs `runData`.

- Scoped test/DataGen command: exit 0, 47.9025835 seconds; 36 JUnit /7 suites,
  0 failures/errors/skips. All 25 named source postimages remain exact.
- DataGen: 781 generated files; only four added and two language files changed.
- Separate repeat `runData`: exit 0, 25.6664387 seconds; zero changed files.
- `git diff --check`: exit 0. No full clean build or GameTest is credited here.

Raw receipts, seven XML files, logs, exact central/source patches and generated
postimages are under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-analyzer-integration-20261005-01/`.
The intermediate log SHA-256 is
`3e15c46eba709c34be108322036fe846355efa10d67b5ca0eecce4cecaf0ba9e`;
the repeat log is
`5ab019e812f4c62fa6e8a91ee6ac92dc6a424d56a037377880e26bef5d97cfac`.
Author/independent raw failures are preserved, not excused by the later results.

## Remaining acceptance

After a scoped source commit/push, run a fresh full committed regression and
capture actual artifacts. All ten registered analyzer GameTests, packaged
before/after-restart actual item use and true-client model/chat/recipient/
reconnect evidence remain unverified. PNG inspection is not V0/V1 or GPU proof.
The API artifact's expected stability must be verified from new output rather
than borrowed. The content ledger remains 186 PLANNED /154 REVIEW; G0-G9 remain
open. User AGENTS and private/unrelated untracked content are excluded.
