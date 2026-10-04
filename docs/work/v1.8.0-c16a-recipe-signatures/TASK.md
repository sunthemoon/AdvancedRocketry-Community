# C16a-01 recipe tags and signature migration

Date: 2026-10-03. Status: in-progress (reviewed source integrated; native/menu scope remains).
Contract: ADR-064 revision 4, section 2; reviewed legacy policy amendment.
Owner: delegated signature implementer, exclusive existing recipe/controller
domain writer in `D:/GitHub/arce-v180-signatures-20261003`, branch
`codex/v1.8.0-c16a-recipe-signatures`; base HEAD cd63c5ff with the verified
2,827-input final-02 source overlay. Root owns central integration and review
assignment. Tank and pump worktrees have disjoint new-module scopes.

Observable outcome: rolling, precision and electrolyzer recipes retain their
validated JSON, resolve bounded item tags after binding/reload, and use a
semantic JSON signature. Tag-only changes retain progress; recipe changes or
unprovable old signatures retain resources and refuse conversion. Unproved old
pending journals retain their original roots and resources without replay or a
new marker; supported current-format journals reconcile their retained plans.

Dependencies: accepted ADR-064; existing process/journal and resource schemas
remain unchanged. New marker root arce_recipe_signature is schema 1 and at
most 1,024 NBT bytes. No hatch ownership or new machine family is implemented
in this leaf. No v1.9 feature or Gate approval is implied.

Verification: A0 canonical JSON/bounds/legacy refusal, A1 deferred binding/reload
and conservation on all three adapters, S1 old partial/pending upgrade and
two restarts; mandatory Gradle and static checks, independent actual-diff review.

Exclusive write scope: machine/recipe, rolling, precision and electrolyzer
production/test modules, their existing/new GameTests and own task records.
Root/central registries, bootstrap, config, network registration, providers,
generated data, language, build, provenance and status are read-only; supply
integration/DataGen proposals in the task directory. No commits/tags/push or
protected document-bundle access. Preserve all seeded unrelated changes.
Compile/scoped unit jobs require root slot assignment (at most two across
the three implementers); use Java 17, workers 2 and 2-GiB Gradle heap.
Root coordinates full GT/native integration and exact historical fixtures.

- [x] Validated semantic signatures and bounded delayed resolution (source/unit scope).
- [x] Persisted marker and fail-closed legacy progress/journal preservation (source/unit scope).
- [x] Existing kernel recipe payloads move to material tags without changing IDs (reviewed central generation/package scope).
- [x] Complete automated regression checks including full integrated GameTest (candidate11 scope).
- [ ] Native persistence and independent review evidence.

Revision-03 checkpoint: the owner's preservation choice is implemented without
a builtin-ID/hash compatibility assumption. All three adapters preserve
unmarked active/pending work and resources, pause before charging/replay, and
do not add a marker. Independent exact-source replay passes 107 JUnit /31
suites with no findings. Earlier Medium reports and the obsolete probe's
actual compile failure are retained. Root integrates the 43 reviewed Java
files, the separately reviewed shared save-guard consumer and two synthetic
registered-event GameTests. Those event tests do not prove native chunk
preservation. The reviewed four-file central DataGen patch generates 17
changed tag recipes; a second DataGen pass writes zero changes. Root complete
build passes 1,622 JUnit /312 suites. Full GameTest, actual native partial/
pending restart and the proposed menu status-channel work are not yet claimed.

### Kernel recipe generation correction — 2026-10-04

The 17 generated material recipes are verified, but the reviewed historical
kernel providers are not registered in v1.8 DataGen. Their three process
recipes still ship the immutable v1.2 item-only payloads. Root owns a separate
correction: generate same-ID current copies using the existing process recipe
builders, exclude only those three historical paths from runtime/sources
packaging, and leave all historical inputs and machine/port crafting unchanged.
No new migration or legacy-provenance assumption is introduced.

Acceptance: generated selectors match the agreed Forge tags; every other JSON
field equals its historical payload; processed/runtime/sources resources
contain one current copy; historical files stay byte-identical; DataGen second
pass writes zero; independent actual-diff review and complete regression pass.
The three new resource tests fail before correction (exit 1, three missing
current payloads). Raw XML/logs are preserved in the root integration evidence.

The [independent central review](../v1.8.0-c16a-integration/reviews/KERNEL-TAGS-REVIEW-01.md)
now passes12 tests and checks both artifacts, all61 immutable v1.2 files and
the exact eight-file patch, with no remaining findings in that scope. Full
integrated regression remains required. Independently reviewed Signature
[revision04](reviews/SOURCE-REVIEW-04.md) passes116 tests but has one reproduced
mixed-root classification Medium; it is not integrated/admitted. Revision05
is independently reviewed and integrated as the exact thirteen cumulative
postimages; see [source review05](reviews/SOURCE-REVIEW-05.md). Root's shared
save-guard consumer remains an explicit separately reviewed overlay.

Coherent Root integration10 passes1,637 JUnit /313 suites and cached DataGen
written0; all769 current generated resources match both non-API packages.
Actual full GameTest04 completes459 cases but fails the current rolling
prepared-journal recovery test. Exact failed logs/source/JAR identities remain
retained. A separately reviewed correction and complete replay are still
required; no native Signature/menu or full leaf completion is claimed.

### Candidate11 automated checkpoint — 2026-10-04

The [independent two-test-file correction](reviews/SOURCE-REVIEW-06.md) has no
unresolved findings and independently passes120 scoped units and459 required
GameTests. Root applies only its exact postimages, leaving all2,938 other
captured inputs unchanged. Production signature-change refusal and all old
unmarked-work preservation remain intact; the corrected marked-current fixture
uses the actual shipped tag recipe without changing its assertions or timings.

Root `clean build test runData` passes1,638 JUnit /313 suites, cached DataGen
written0; fullGT05 passes all459 required tests. All2,940 source inputs remain
unchanged after build; current main isea311ed0… and769 generated resources match
both non-API packages. Candidate10's459/1 and earlier456/12 failed runs remain
frozen. Native old partial/pending Signature upgrade/two restarts, menu-state
coverage and final leaf acceptance are still owed; unrelated Pump/Tank native
results cannot supply them. The whole leaf, ledger and version are not complete.

The separate [S1 proposal task](S1-TASK.md) records the frozen baseline,
disjoint Temp-only patch-return scope, actual old-hook limitations and the
distinction between observed old partial state and injected stopped legacy
pending cut. No native result or final S1 admission follows from starting it.
