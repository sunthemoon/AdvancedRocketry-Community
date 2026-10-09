# C19 bounded Git-object candidate checkpoint34

Date: 2026-10-10. Status: implemented-unverified; not integrated into Main.
Version: v1.8 development under ADR-060; all Required Gates remain open.
Scope: [Task34](GIT-OBJECT-SESSION-TASK-34.md). No Claude or nested delegation.

## Source and design

Baseline:631494559a808938b631eab07f0877225d4f6d9a. Root commits and normally pushes
candidate607c626d246477887bf2a501ae68c3d908feaf02 on
fix/v1.8.0-provenance-object-session. Its exact three-file diff has892 additions/
2 deletions: the bootstrap validator, one new protocol test module and additive
methods in the existing validator tests. Main90b1f6e2 does not contain this source.
No Java, build, asset, ID, schema, approval, ADR acceptance or ledger changes.

Transport is self-contained within the already bound validator. Every requested
OID retains type/header/size/body/terminator/type-qualified SHA-1 verification,
including repeated OIDs. There is no object, approval or mutable-file result
cache. One public call owns a session; nested/thread contexts restore previous
state, foreign-root/private callers retain the isolated original reader, and
deferred EOF/exit/trailing-output/cleanup failures reach returned errors.
The pre-edit policy is4096 admitted requests/16 GiB aggregate payload/new180-second
scoped lifetime, retaining original15-second request waits and individual limits.
Timers and cleanup do not prove process-creation, arbitrary OS-call, descendant
pipe/absence or maximum-load/lifetime compatibility guarantees.

Static independent36 derives loose worktree1780 requests/11423 MiB and selected
2562/12962 MiB ceilings, including malformed-but-processed metadata and tree-body
overshoot. Root reads its entire report and exact two-payload manifest; package
has3 files/33047 bytes. Report95b47022ee89be488b4d28c5878fc031d513a9fb6898be519cfd28aba5fcd82a;
manifest6056c665ccf80b44f0cd511f27079875bc5f24ea5b0545e8386611db25d2f27f.
These static maxima are neither attainable-load execution nor budget acceptance.

## Actual execution, including failures

All captures use pinned CPython3.13.15/-X utf8/-B and the original180-second
outer ceiling. Exact argv/PID/UTC/source state/TEMP/TMP/stream hashes are retained.
Declared before/after inputs match for all11 attempts; that finite set is not all
historical Git objects, environment or hostile-filesystem/ABA binding.

| Attempt | Actual result | Capture seconds |
| --- | --- | ---: |
| unchanged baseline bootstrap90 | TIMEOUT124/child1; no final summary | 180.178612 |
| first protocol23 | FAIL1/child1; one fake EOF-process failure | 2.781006 |
| final protocol26 | PASS0/child0; zero F/E/S | 0.812921 |
| focused24 | PASS0/child0; zero F/E/S | 19.898767 |
| candidate bootstrap93 | TIMEOUT124/child1;38 ok-prefix lines, no final summary | 180.189570 |
| fixed607 canonical G4 | PASS0/child0; no errors | 6.958475 |
| fixed607 strict | FAIL1/child1;44 PASS/1 Markdown FAIL, no timeout | 35.757131 |
| fixed607 broad Python | TIMEOUT124/child1; no final summary | 180.210579 |
| fixed607 original shard0 |30 methods pass; zero F/E/S | 146.776564 |
| fixed607 original shard1 |30 methods pass; zero F/E/S | 137.377631 |
| fixed607 original shard2 |30 methods pass; zero F/E/S | 138.410746 |

The baseline/precommit commands run their recorded snapshots, not commit607.
Final protocol/focused/bootstrap93 postimages exactly alias the three committed
candidate blobs; only fixed-prefixed attempts run at already committed607.
The initial fake contradicted its blocking EOF by marking the process exited0.
Its simulation is corrected without changing the assertion; three distinct cases
are added. Failure receipt/postimages remain separate; no budget is enlarged.
All90 original method source/ASTs are unchanged. Their disjoint focused coverage
does not replace the failed full93-method deadline result.

The original qualification commands are unchanged:

```text
python -X utf8 -B -m unittest -v tests.test_validate_bootstrap_provenance
python -X utf8 -B -m unittest discover -s tests -v
python -X utf8 -B scripts/validate_repository.py --require-approved-identity
```

Protocol26 uses `python -X utf8 -B -m unittest -v
tests.test_bootstrap_git_object_session`. Focused24's exact method selections,
the standalone G4 observer and shard0/1/2 argv are in their separate receipts;
no filtered command is described as the original broad qualification.

Canonical G4's sys.setprofile counters distinguish ordinary and dynamically
compiled module instances: each507 requests/read returns, one object-transport
start, clean final close,696225/696485 payload bytes. This is a current observation,
not recovered attribution for [Root33](G4-PROFILE-CHECKPOINT-33.md), an exact speedup
ratio or unique historical timeout-cause proof. The retained observer helper is
manifest-bound, not raw-byte bound by its command receipt at execution time.
Strict returns all33 phases; G4 takes6.240 seconds. Markdown retains its original
256-error prefix. Console truncation of its long line is not claimed as a full
individual finding review. Historical strict timeouts remain unchanged evidence.

Owned taskkill results are baseline128, candidate-bootstrap255 and broad0;
unsupported descendant termination errors remain in the first two receipts.
No additional termination or cleanup is attempted; no full descendant absence
or historical custody/refusal gap is closed. Runtime scratch is separate from
the sealed observation packet and is not operational acceptance evidence.

## Independent source review and finite packet

Fresh read-only worker /root/c19_object_source_review37 inspects actual diff at
fixed607. Independent protocol26/dispatch-G419/public5 pass,50 methods/zero skips.
All72 original top-level definitions compare equal after declared renames.
No additional material production defect is identified in that finite review.
R37-01 is a Medium verification blocker: the required bound-packet test's shared
Git clone exits128 in setUpClass; command exits1 withRan0/one setup error. Nested
clone stderr is unavailable, so no source regression or root cause is diagnosed.
No retry, source/fixture change, cleanup or extra termination is performed.
Root reads its complete sealed report and rehashes exact45-file coverage:
44 payloads/46 package files/352577 bytes. Report052d96e396bc8c67d560e9846d3a1d9adc2da7f05b8342c2ad84a6ff717d6c4f;
manifest4fafe09873a8d69656cb1745ebf69da4e010a1e1fef1dcb16f6779a1d222a10f.

Root packet is external c19-object-session-root-20261010-34 under
D:/GitHub/ARCE-Task-Evidence/v1.8.0. It has39 payloads/41 files/297275 bytes;
its40-file manifest and both cited peer manifests rehash exactly. Report
b667cd790b1781bf6a2903e556501c8827f314f67c2e48302da17d6a964d2af3;
manifestbb541eb1043e1bcb9e74a25a8b8240eb5123b6f18a098cece97517b2708263d7.
FINAL-OBSERVATIONS.json records actual terminal source/stream/postimage aliases,
disjoint corpus partition, phase inventory and peer identities. Both disks remain
above10 GiB; evidence meets local4 MiB/file1 MiB limits. Seal command exits0.
Fresh delegated read-only /root/c19_object_packet_audit38 audits this packet under
external C19-OBJECT-PACKET-AUDIT-TASK-38.md. Its completed report identifies no
new material packet-consistency finding and independently corroborates R37-01.
Root reads the complete report/exploration register and rehashes exact coverage:
four payloads/five files/46678 bytes. Report
ff31fb2e8ffb49482645f9d076d60ca11f01b20328d4c9f1eee5b02a819f45f3;
manifest a35ec5dd15bf380886631bd9e6795d369ff697412d49baedb9149a1d05e70af9.
The original Root34 pending packet-audit statement is superseded only by this
separate sealed audit. Failed development snapshots have retained hashes and
tracebacks, not full reconstructible source bytes; the fake-repair/construction
narratives cannot be independently reconstructed solely from those bindings.
This residual limit is not a diagnosed weakened assertion or passing result.
The audit's exploration read/selector/calculation/truncation mistakes remain in
its own notes; corrected calculations are separate from target execution.

Two documentation apply_patch context failures make no writes. One absent peer
seal-verification filename read fails; existing manifest/COVERAGE files are used
instead. These are separate exploration/documentation outcomes, not test results.
Sealed peer/Root evidence, user AGENTS and inherited untracked materials are not
modified. User AGENTS SHA-256 remains
c2448e9357ec77d062ab52ecefbb24724fb5c767fb4955a4cd23ef0efbd8ff09.

## Remaining scope and Gate conclusion

This candidate is not Main-integrated or delivered. Required build, explicit
test, two DataGen/empty diffs and unfiltered GameTest have not executed at607.
R37-01, passing full bootstrap/broad/strict qualification, malformed-ADR diagnostics,
G4 wrapper coverage, portable evidence references, historical operations and
native ERRORs remain open. JSON dual-memory-window resources, true sleep,
shared save, physical machines, progression/soak and real-client visual/multiplayer
evidence remain separate v1.8 work. No ledger delivery, ADR acceptance, release
tag or Required Gate approval is assigned. Next work stays within v1.8 transport
qualification and its recorded failures before considering Main integration.
