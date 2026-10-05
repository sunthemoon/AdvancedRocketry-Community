# CURRENT_VERSION

```yaml
current_version: v1.0.0
status: IN_PROGRESS
next_action: Review remaining adjacent-chunk loading and expiry behavior plus candidate acceptance gaps in docs/releases/v1.0.0/RELEASE-EVIDENCE.md; controlled native queue recovery and pending-player logout pass, genuine storage ordering and final v1.0 Gates remain open, no long-load work
last_updated: 2026-09-06
prerequisite_version: v0.9.0
prerequisite_status: PASSED
prerequisite_merge_commit: a7196ff9b22220c344071a1af69a663036f76aef
work_branch: codex/v1.0.0-stable-core
base_commit: 34b2e99b48a33f4ba8905b6a69a38efee1649d3f
build: 1.20.1-1.0.0-dev
tested_implementation_commit: ""
artifact_sha256: ""
```

The accepted Beta identity, approvals and published artifact remain immutable
in [v0.9.0 GATE-STATUS](../releases/v0.9.0/GATE-STATUS.md). v1.0 stabilizes that
core without implementing v1.1+ features. Development-tree checks are not a
frozen release-candidate commit or stable approval. See the
[implementation log](../work/v1.0.0-implementation-log.md).

## Active development checkout

The acceptance cursor above remains at the earliest unfinished release Gate.
Under [ADR-060](../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md),
v1.8 development proceeds separately from inherited release acceptance.

```yaml
active_development_version: v1.8.0
active_development_branch: codex/v1.8.0-classic-content
phase: IMPLEMENTING
execution_state: ACTIVE
accepted_development_baseline: 55da6a58842762c382edce0a5a842d06bb76ff6e
development_log: docs/work/v1.8.0-implementation-log.md
previous_development_handoff: docs/releases/v1.7.0/RELEASE-EVIDENCE.md
runtime_build: 1.20.1-1.8.0-dev
tested_code_commit: a0873a30a2e1ad9fe0b42d11a0b89903fc478d5c
tested_python_commit: 7affd485fc99b944dd5d3b2e94ab6c4651d3903e
last_updated: 2026-10-05
```

## Current development evidence

The latest complete [Root Source27 regression](../work/v1.8.0-c18a-atmosphere-analyzer/VERIFICATION-02.md)
tests the committed source above with no-build-cache: actual clean build/test/DataGen
passes with 1,807 JUnit /339 suites /0FES; all 477 required GameTests pass. Repeat
DataGen has 781 files and zero changes, eight scoped static checks and package-member
checks pass, and API is byte-exact. The 3,039 named inputs are not a whole repository
snapshot; live owner AGENTS and one LF/CRLF wrapper expansion are qualified.
Ledger closure and global dirty diff remain actual failures. The 61 ERROR headers
are disclosed without blanket waiver. The [independent result audit](../work/v1.8.0-c18a-atmosphere-analyzer/RESULT-REVIEW-02.md)
finds no additional receipt/source/artifact inconsistency within those limits;
it does not replay Java or accept native/client/leaf or version Gates. Its
portable 493-member thin packet is verified; older source ZIP/binary stdout
remain exact separate references, not a complete publication-output closure.
The [exact fixture correction](../work/v1.8.0-c18a-atmosphere-analyzer/FIX-REVIEW-DISPOSITION-01.md)
and its independent actual-source review preserve original conditions and budgets.
The [Source26 four failures](../work/v1.8.0-c18a-atmosphere-analyzer/VERIFICATION-01.md)
and [failed-result audit](../work/v1.8.0-c18a-atmosphere-analyzer/RESULT-REVIEW-01.md)
remain historical, not waived or overwritten.

The development main JAR SHA-256 is
`cb7d3b48148bea78cf5ffb0da18a6bb578db1f6f10040c894e7d9721df976206`.
The API artifact remains unchanged. This is not a frozen release candidate.
The content ledger has **186 PLANNED units /154 REVIEW assets**; its closure
check still fails. v1.8 remains **IN_PROGRESS /IMPLEMENTING**, with **G0–G9 open**.

All four historical copied-world lifecycle attempts remain failures. New
[SETUP05 fixed diagnostic](../work/v1.8.0-c16a-save-guard/SETUP05-VERIFICATION-01.md)
passes in 70.746109 seconds with independent exact result review: first-host
true unload/live restoration/clean stop, then same-copy second-host refusal
retrigger and retained native bytes. Its separate Python commit is
`f1d649238284bef515ff8fafafd65611ac37bc32`; Java remains the prior Source25
artifact at `a5abc34809891d6b10724ee60ab9b5e97f036bae`, main SHA
`61c5499318dcd17d929acd9f3d28ab1e99066ba2c29da1de34603096f10f3921`.
This native result is not rerun on the newer analyzer artifact. Metadata
progresses five marks to exact target/far two marks.
The old native04 stopped observation remains observation-only, not rewritten
as PASS. Unique production cause, final BE disposal instrumentation, first-save
writer, cross-store/crash/client and all version Gates remain unverified.

Six C18 inventory tutorials now have exact reviewed source integrated by Root;
12 scoped JUnit and integrated DataGen pass on the earlier intermediate check;
committed complete tests above are a separate cohort. Three registered tests
exercise the actual inventory listener. Packaged restart and ledger delivery
remain pending. The original native draft's offline-player ownership finding
is preserved, as is the revision 2 constructor/join scene finding. The separate
[revision 3 review](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-REVIEW-DISPOSITION-03.md)
addresses that scene at contract/source level but identifies two Medium native
lifecycle conflicts: startup properties rewriting and repeated reload forceload
refusal. Revision 3 remains unadopted. The [revision 4 disposition](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-REVIEW-DISPOSITION-04.md)
adopts the independently reviewed phase-bound contract only, committed and
pushed at `d04a116e6e6001f166bbf5e8f31fb78623a6b392`. A fresh isolated fixture
implementation has a [limited phase01 source disposition](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-SOURCE-REVIEW-DISPOSITION-04-PHASE01.md):
two Python modules/two records are independently reviewed with no C/H/M/L and
imported byte-exact and pushed at `d0f9cbde`. A separate [committed Python cohort](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-PYTHON-VERIFICATION-01.md)
passes the phase01 pure checks; CLI2 explicitly refuses the unimplemented
full driver. Independent raw-result audit verifies the limited observations,
including original observer/preparation failures. The119-member committed-result
thin packet is verified; ten duplicate Git stdout blobs remain precise immutable
locators, not complete publication-output closure.
Other file-role/live acquisition, JSON/NBT/transport,
Java fixture, clean host selection and native execution remain pending.
The separate [phase02a contract disposition](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-REVIEW-DISPOSITION-04-PHASE02A.md)
adopts only four new private-file paths for quiescent snapshot/preboot/stopped
properties input, after exact independent0 C/H/M/L review. Its61-member contract
packet is verified. The contract is committed and pushed at
`b6299c86f7bcb99b00b89b4dd38479e035321954`. The isolated four-file source has
[limited committed adoption](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-SOURCE-REVIEW-DISPOSITION-04-PHASE02A.md)
at `254af9e4`: exact independent source review finds no C/H/M/L, and Root's
fixed-commit replay passes 34 focused + 39 unchanged phase01 methods /0FES.
Its fresh D fixture is cleaned, 99,375 bytes, after a separately retained
precheck correction. This is only private quiescent properties input. Live,
ownership, JSON/NBT, receipt/driver and native authority remain unimplemented.
The [pure JSON source disposition](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-SOURCE-REVIEW-DISPOSITION-04-JSON.md)
adopts only the four-new-file byte consumer at the tested Python commit above.
Independent actual-source review finds no introduced C/H/M/L and runs 125
tests plus 14 separate controls. Root commits/pushes all four exact postimages;
the [fixed-commit replay](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-PYTHON-VERIFICATION-03-JSON.md)
passes 52 JSON + 39 unchanged phase01 + 34 unchanged properties tests /0FES.
All 12 related source/task postimages match fixed Git before/after. The fresh D
fixture is cleaned, 99,375 regular bytes and six aliases. The five role budgets,
key-inclusive counters and immutable numbers are private syntax observations,
not JSON file acquisition, fixed-field validation, live/native or writer authority.
Java results remain at their separate older commit; no whole-source rebind occurs.
The [terminal-field contract](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-REVIEW-DISPOSITION-04-REPORT-TERMINALS.md)
is independently reviewed and technically frozen for four new private files.
READY_FOR_STOP/FAILED shape observations convey no completion or native authority;
the contract is published at `3e5610e5`. Root creates the clean independent
`D:/GitHub/arce-v180-c18c-report-terminals-20261005` checkout at that commit and
assigns c16a04_fluids only the four new files. Isolated implementation is assigned;
actual-source review, integration and committed replay remain pending.
Exact proposals/failures/primary controls are
portable. No setup or native execution is admitted by this contract review.
See
[source integration](../work/v1.8.0-c18c-tutorial-inventory/SOURCE-INTEGRATION-01.md).

## Implemented development scope

- C15 materials, plate press acquisition, planetary surfaces and Tau Ceti worlds
  have submitted implementation and review fixes, including radius 224 coverage
  without restricting legal rockets. Real client acceptance remains open.
- C16a combustion generator, five fluids/canisters, motor/casing component
  definitions, pump and pressurized tank have reviewed development code.
  All-tier machine formation is not completed by component registration.
- Recipe-signature preservation and paused unproven legacy tasks, bounded save
  refusal, menu reasons and transport compatibility are integrated. Historical
  restart/reload evidence applies only to its recorded artifact and scope.
- Bank/value models, K1 ordering, K2 bounded bytes and K3 digest are private/model-only
  infrastructure, not completed physical hatch/controller or full codec APIs.

## Confirmed decisions and unfinished work

The owner confirms controller-owned resources across chunks; broken fluid hatches
retain the corresponding controller inventory for rebuilding. Removed charged
power plugs retain FE in their item; charged operations that cannot conserve FE
must refuse, while supported zero-energy operations retain vanilla behavior.
The reviewed ordinary/mode inventories are technical dependencies only: server
creative pre-transform interception and real FE conservation are unimplemented.

Earlier independent reviews record qualified contract acceptance for ADR-064
revision 5, ADR-065 revision 4 and ADR-066 revision 3. The owner's condition is
no unresolved Critical/High/Medium in the reviewed final contract; major semantic
changes still require separate confirmation. The later save-refusal audit below
leaves two disclosure/ADR findings open, so that historical record does not
authorize new guarded runtime admission. These decisions do not supply runtime,
migration or native recovery evidence. C18 D4 remains unproven.

Remaining: full Guard lifecycle and first-save/final-disposal proof; full hash/frame/
native codecs and resource consumers; physical hatches, lathe and motor-tier
formation; steel and component acquisition; C16b–d, C17–C19; real clients/GPU,
multiplayer, crash recovery, performance and inherited release acceptance.
The underwater mutual-routing proposal is technically reviewed but not adopted:
connected alive deferred sampling awaits the owner choice. A separate correction
must preserve non-current ServerPlayer immediate handling and restrict the new
tick fence to the connected bridge. No underwater implementation is assigned.
Whole-chunk save refusal can prevent unrelated chunk changes from being saved;
its impact, repair workflow and release disposition remain open. Historical
failures and oversized evidence-storage debt remain documented, not erased by
passing automatic tests.
Future temporary helpers/output use the owner's requested project-parent
`D:/GitHub/ARCE-Task-Evidence/v1.8.0` location, including process temporary
directories. The tool rejects checked C-script removal before execution;
11 ordinary files, totaling 24,930 bytes, remain in the last read-only inventory.
C cleanup is not complete and no bypass is attempted.
The new stopped native04 runtime copy also remains 209,666,732 bytes of cleanup
debt after its checked literal deletion was rejected before OS execution.
Compact logs/captures are preserved; no source world or sealed evidence is removed.

The separate [save-refusal audit](../work/v1.8.0-c16a-save-guard/SAVE-REFUSAL-DISPOSITION-01.md)
records two original Medium disclosure/ADR findings. Their missing facts are now
addressed by the corrected R-021 and an independently reviewed explanation
proposal; the accepted ADR is unchanged and R-021 is still open and unaccepted.
See the [factual correction disposition](../work/v1.8.0-c16a-save-guard/DISCLOSURE-REVIEW-DISPOSITION-01.md).
After the 257th distinct refused
chunk, the guard denies subsequent terrain saves throughout that ServerLevel.
Historical attempts emit EventBus/ChunkMap ERROR pairs with no guard log quota;
dirty state does not prevent unload or provide cross-store durability. No
quarantine/reset/stop policy, runtime risk acceptance or repair proof is selected.

The user assigns this agent sole integration of the existing v1.8 work and
requires phase commits/pushes. User-maintained AGENTS.md and the private document
bundle are excluded. On 2026-10-04 the owner allocates only R-021 correction and
a proposed ADR disclosure amendment, with no save behavior, risk acceptance or
other-row changes. That factual correction is independently reviewed and
separately published; the explanatory amendment remains PROPOSED.
Other newly observed external edits are not silently staged.
The separate [C18a analyzer task](../work/v1.8.0-c18a-atmosphere-analyzer/TASK.md)
has an independently reviewed, narrowly adopted contract and pre-authoring
original-resource record. Isolated implementation at committed base
`9135c20c34a402b7cf0715148aeb6683a35c1f50` has exact independent source review
with no unresolved C/H/M/L and 19 independently executed tests. Root integrates
25 source/task postimages and six generated files, committed and pushed.
The original full cohort's four analyzer GameTest failures are preserved.
Their isolated fixture correction and independent review are published; the
latest complete committed cohort passes all 477 required GameTests, as recorded
above. Packaged use/restart, client and ledger delivery remain open.
See the [source checkpoint](../work/v1.8.0-c18a-atmosphere-analyzer/SOURCE-INTEGRATION-01.md).
It does not enable
unproven refill/shared-save dependencies.
The [completion plan](COMPLETION-PLAN.md) lists the remaining leaves. Historical
status checkpoints have moved to the implementation log, not this current-state
summary. No version Gate, tag or release is approved.
