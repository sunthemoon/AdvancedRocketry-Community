# C16a-01-MENU bounded transport/display implementation

Date:2026-10-04. Status:in-progress,Temp-only patch-return implementation.
Owner:delegated `/root/c18_contract`;independent actual-source reviewer is a
different agent. Root owns all integration/central/status writes.

Outcome:three real menus transport the adopted bounded recipe reason and
render localized display without changing server authority or resource state.
Authority:[adoption01](MENU-ACCEPTANCE-01.md),ADR-064 revision5 section14 and
unchanged exact proposal002. Baseline is Root Java/source15 plus reviewed
two-Python-file cohort16;adopted ADR revision5 is separately snapshotted.

Write scope:fresh Temp candidate/patch/evidence only. Eligible proposals modify
the three existing Menu/Data/Wire and Block opening writers,three existing
Screens,small shared reason/opening helpers,the V180 language generator and
scoped unit/GameTests. A new required-channel class and common-setup call are
returned in a separate CENTRAL patch for Root,never directly applied by the
author. No Root/worktree write,world/process/resource schema,existing channel
protocol/C2S change,API extension,generic UI redesign,upstream asset import,
status/ledger/Gate decision or protected bundle access.

Narrow supplier extension:ElectrolyzerBlockEntity may append its existing data
count/supplier;ElectrolyzerProcessController may add only a package-private
display-only reason accessor. No state/resource/save mutation is allowed.
Rolling/Precision controllers remain read-only and use existing accessors.

Use an exact named manifest snapshot and preserve prior tests/assertions.
Actual tests cover exact count equality including over-count,opening malformed
and trailing cases,bounded IDs/unknown fallback,old indices/enums/protocols,
registered menu/provider values and unchanged refused-work resources. Reasons
must respect existing structural/unsupported precedence and never authorize
work. Preserve existing project conventions and comment language.

Static/Python checks are allowed. No Java/Gradle/DataGen/GT/native/client launch
until a separate Root slot grant. Author freezes source/central patches,pre/post
identities,commands/results and remaining evidence before independent actual-
source review. Real peer joins/client rendering/V1/V2 remain explicitly unrun.

- [x] Exact code and scoped tests proposed,with immutable baseline.
- [x] Independent actual-source review;no introduced C/H/M/L,original32/1F
  registration environment limitation remains failed,not waived.
- [ ] Root integration/build/DataGen/GameTest and applicable dedicated checks.
- [ ] Scheduled real client/player evidence;no version Gate inference.

Author checkpoint04 is Temp-only:28 owned files,25 SOURCE and3 CENTRAL.
The separate CENTRAL pin-test proposal classifies exactly one `machine_menu|1`
empty channel while preserving the six original messageful channels,committed
message/protocol table,handler/index assertions and refusal of any other empty
channel.No old network-protocol table bytes or production controller semantics
are changed for the test.

Scoped01 fails at compileJava because the new GT referenced a package-private
revision accessor.Scoped02 compiles but has25 tests/11 suites/one failure in a
new Electrolyzer purity fixture lacking its existing legacy mirror.Candidate03
corrects only the new test/GT fixture coherence;candidate04 adds the separate
pin-test classification proposal.Original failures/postimages remain frozen.
Separately granted scoped03 exits0 with26 actual JUnit/12 fresh suites/0FES,
including the revised pin test.All2,985 author postimages match and363 other
original tests/wires/networks remain exact.Three new GT are compiled,not run.
Independent actual-source review qualifies exact source integration;Root has
integrated author28 plus the separately reviewed N1 test as source18.
First DataGen adds only7 labels per language.Full clean build/test/repeated
DataGen exits0,1,722 JUnit/327 suites/0FES and written0;both non-API JARs contain
all771 exact generated resources. FullGT09 exits0 with464 required tests passed;
61 ERROR logger lines remain disclosed,not a clean-log assertion. Dedicated
current-artifact/actual peers/client validation is still unrun.
See [source adoption](MENU-SOURCE-ACCEPTANCE-01.md).Actual peers/client/V1/V2 remain open.
