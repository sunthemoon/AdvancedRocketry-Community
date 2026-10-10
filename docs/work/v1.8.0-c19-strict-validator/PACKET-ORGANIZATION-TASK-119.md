# C19 packet review scenario organization task 119

Date: 2026-10-10. Milestone: v1.8.0 under accepted ADR-060. Owner, implementer
and integrator: Root. Status: IN_PROGRESS; independent reviewer assigned after
the actual diff exists. This task resolves the inherited packet-class organization
prerequisite identified in checkpoint 114, not complete C19 qualification.

## Identity, dependencies and frozen boundary

Main is D:/GitHub/AdvancedRocketry-Community, branch codex/v1.8.0-classic-content,
entry commit d71c097ce1e07241201e4f284705095ee4b1837f. Source base is
be2abdffd65de25b9a6e8871b20b464ca023a7d3, tree
89d5010bd10b2a4b3ed91c905ee960593c3545a9. Root creates the absent independent
checkout D:/GitHub/arce-v180-packet-organization-20261010-119 on the absent
branch fix/v1.8.0-packet-scenario-organization. Previous source checkout and Main
source are not edited. No candidate integration or owner/Gate decision is assigned.

The original packet class has 52 directly defined methods: 35 tests and 17
fixture/lifecycle/helpers, plus nested functions and a Windows-only decorator.
Retain all original method source segments, AST/decorators/assertions and helper
semantics. Preserve tests.test_prepare_v002_g0_review_packet.V002G0ReviewPacketTests,
sorted case identities, discovery/named loading, import and direct-entry behaviour.
Fixture root resolution must remain the repository root. Original class/case
lifecycles, seed checkout, --shared clone/object references, per-case clone and
packet file copies, mutation isolation and canonical packet/validator patch
targets remain unchanged. Do not improve fixture semantics in this task.

Separate the runnable facade, a non-TestCase fixture mixin and cohesive plain
scenario mixins. No helper/scenario becomes separately runnable, no duplicate
test reexport or scenario lifecycle override. Keep changed/new declared classes
below 500 lines, avoiding a size waiver. Add focused organization checks for
original selections/loading, non-runnable composition, lifecycle/root/global
bindings and class sizes. No original test assertion or selection change.

## Exclusive writes and non-goals

Root-only source write scope in the new checkout:

- tests/test_prepare_v002_g0_review_packet.py
- tests/packet_review_fixture.py
- tests/packet_review_cases/__init__.py and scenario .py files under that directory
- tests/test_packet_review_organization.py
- tests/fixtures/packet_review_case_ids.txt

Root-only central records in Main: this TASK, the implementation log, current
version/completion-plan records and a new PACKET-ORGANIZATION-CHECKPOINT-119.md.
Fresh own external evidence leaf:
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-packet-organization-root-20261010-119.
Independent agents are read-only with their own fresh leaves; no nested delegation
or Claude. Root is the sole writer and committer/pusher of the source slice.

Forbidden: production scripts/Java/resources, registry/build/protocol/schema,
numeric policies, assertion/fixture changes, runtime/result/approval caches,
G4 correction import, Main integration, ADR acceptance, ledger delivery, sleep
or save-writer activation, upstream imports and sealed packet/helper execution
or modification. Protected user AGENTS.md and inherited unknown acquisitions
are not edited, staged, read for adoption or cleaned. No v1.9 work.

## Verification and custody

Before edits, bind Main/base/new checkout HEAD/tree/full status/scoped index and
source input bytes. Retain a finite original function/case inventory; after
edits compare every original method and nested function, imports, direct entry,
case identities, bindings and class sizes. No manual-source edits outside
apply_patch. Read/file <= 1 MiB, command streams <= 256 KiB, fresh own evidence
leaf <= 4 MiB; inspect free space before builds/native work, require >= 10 GiB.

Pinned Python: D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe, SHA256
85b71d8c6ec1905935f74be0c9869aae198d00e98f39df699ec66f9c5a84cecd.
Own analyses use -I -X utf8 -B; test children use -X utf8 -B. Root executes
new organization module and unchanged original packet module once each at the
precommit postimage. Independent actual-diff review reruns both in its own fresh
runtime. After review and source commit, execute the new organization module
and literal python -X utf8 -B -m unittest discover -s tests -v once each.
Original Python deadline stays 180 s; no retry or budget relaxation. Preserve
original wait/outcome, complete bounded raw pipes, launch and paired bindings.
Only the original owned child handle may be controlled; postdeadline owned
wait/drain uses a separate total 10 s and cannot qualify the original result.
Only fresh owned empty runtime directories may be removed nonrecursively;
nonempty runtimes remain uninspected, no descendant/unowned process control.

Required version commands remain clean build, test, runData, diff --exit-code and
unfiltered runGameTestServer. Committed-candidate standard qualification uses
its separately declared finite command cohort with unchanged known ceilings:
2400 s build, 1200 s test, 1200 s DataGen and 1800 s GameTest. Until actually
executed at that identity they are UNEXECUTED, not N/A/waived; older 607 evidence
does not qualify a new head. Dedicated/restart, resource, real V1/V2 and long-load
requirements remain separate and unexecuted by organization tests.

Hold assigned source/Main inputs until independent final read cutoffs. Read and
freshly verify full sealed reports/inventories before any staging or HEAD change.
Stage exact owned files, inspect cached stat/names/check, commit and normally
push only the verified slice. Retain actual committed identities and bounded
receipts. No Main source integration. Seal evidence only after final receipts;
report failures, skips, unavailable observations and limits without rewriting.
G0-G9 remain required/open and v1.8 IN_PROGRESS / IMPLEMENTING. No release tag.

## Post-cutoff status, 2026-10-10

Root commits/pushes the assigned source candidate f87ff4c3. Independent actual-diff
review 121 and finite evidence/held-record review 123 are complete; the latter's
input cutoff is 2026-10-10T04:13:26.833170+00:00. Root fully reads and freshly
verifies the sealed reports/inventories before central staging. Original packet
and whole Python qualification remain timed out; native ERROR disposition and
console-only administrative-failure audit limits remain open. This section is
later factual status metadata, not a change to the frozen contract and not
implicitly independently reviewed. Task119 remains IN_PROGRESS/[~]; no Main
source integration, delivery marking or Gate approval.
