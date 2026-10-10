# C19 Python payload continuation checkpoint 161

Date: 2026-10-10. Version: v1.8.0, IN_PROGRESS / IMPLEMENTING.
[Task161](PYTHON-SUITE-TASK-161.md) defines the exact scope and unchanged limits.
Main preparation base: c8fe8c0f8efe28d8e35e2400e8b1142dfd46d7fb.
Source161 candidate: 82f82b1f422d5165fe42a143c2c473a39bd6707e, tree
8025bf08cb333afe41ee0372b4902e3d305ed8da, parent
d7f4e69be2fc02f29c86533ee1b5cf173d96cf0c. The candidate is separately committed
and normally pushed on fix/v1.8.0-python-suite-cost, not integrated in Main.

## Completed scope and design

Only scripts/prepare_v002_g0_review_packet.py and new
tests/test_packet_payload_transport.py change in Source161. No fixture lifetime,
manifest selection, bootstrap validation, tool authentication, asset, registry,
Gradle, sleep, memory or persistence implementation changes. The original facade
and organization tests and their selected case identities remain unchanged.

Post-manifest ordinary packet payloads reuse the authenticated multi-blob reader
with an optional provisional content sink. Each path is independently resolved
through the existing tree authority and regular-mode checks. Every requested
OID, including repeats, is sent, read, freshly hashed and charged to aggregate
bytes. There is no cross-expectation session, success-result cache or retry.
Manifest and tool reads retain their original independent authentication.
Final EOF, exact framing and zero child exit are required before publishing the
sink. Canonical packet output is byte-identical to the one-shot reference at the
same fixture commit. The static fixture count is 32 ordinary payloads and 31
fewer process starts per expectation; it is not measured timeout causation.

Capture uses unbuffered nonblocking pipes with explicit monotonic request and
aggregate deadlines: 30 seconds per request, N * 30 seconds for N <=512 requests,
including final EOF/exit in the last request. Cleanup retires and reaps the owned
child before publication; actual held-open-pipe regression coverage leaves no
reader thread. The existing size-only branch retains buffered reads and its
phase timer. Windows captured pipes require Python 3.12+, matching CI; local
tests use 3.13.15. Unsupported pipe setup fails closed. Kernel operations and
scheduling are not claimed universally preemptible.

Generator: 122007 bytes, SHA256
b4bcb8399ae58984c066432db79b09eb11c04e3fe842eb6dcf8fd7fa8c8b747b.
New test: 17477 bytes, SHA256
3c6bff6d8a6c62966d9ede02a474892b712485961df642f09fecfe69f921e2e0.
Source staged stat was exactly two files, 539 insertions and 24 deletions;
cached names and whitespace checks passed before commit. Source HEAD/upstream
are equal and the source worktree is clean after normal push.

## Actual commands and development results

Targets use D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe, Source161 cwd,
with -X utf8 -B and without the administration-only -I flag. Each new original
has its own private Job assigned by creation handle before resume, original180,
separate stop/drain10 and dual262144 raw reservations. Full captured streams
have EOF, no overflow or live reader, and unused reservations are released only
after quiescence. A timeout retains original_exit=null even when later owned
cleanup exits 0. Coordinator shell exit 0 is not a positive target verdict.
All listed cohorts have equal sampled entry/terminal source bindings.

| Cohort / actual target suffix | Original seconds | Original result |
| --- | ---: | --- |
| Unchanged d7 literal -m unittest discover -s tests -v | 180.005817 | TIMEOUT, no final suite summary |
| Unchanged d7 -m unittest tests.test_prepare_v002_g0_review_packet tests.test_packet_review_organization -v | 180.002886 | TIMEOUT, unfinished final facade method |
| First development new-module discovery | 27.458084 | 14 pass, intermediate image only |
| Intermediate unchanged facade/organization | 163.798631 | 38 pass, intermediate image only |
| Intermediate literal whole discovery | 180.012595 | TIMEOUT, no final suite summary |
| Partial deadline correction new-module discovery | 30.134948 | exit 1, 17 run /1 fixture failure retained |
| Final development new-module discovery | 29.978273 | 18 pass |
| Final development unchanged facade/organization | 175.262547 | 38 pass, limited deadline headroom |
| Final development adjacent five-module command | 10.615361 | 48 pass |
| Committed82 new-module discovery | 30.307390 | 18 pass; caller-image binding limitation below |
| Committed82 unchanged facade/organization | 176.744708 | 38 pass, caller image and source bindings equal |
| Committed82 literal whole discovery | 180.006667 | TIMEOUT, original exit null, full streams but no final suite summary |
| Committed82 strict repository validation | 33.814310 | exit 1, 44 pass /1 Markdown failure |

The adjacent command is -m unittest tests.test_bootstrap_git_object_session
tests.test_bootstrap_commit_probe tests.test_final_review_input_fixture
tests.test_final_g0_review_fixture tests.test_manual_evidence_fixture -v.
New-module discovery is -m unittest discover -s tests
-p test_packet_payload_transport.py -v. The complete receipts and raw streams
retain each exact argv, original result and separate drain duration.

Strict target is scripts/validate_repository.py --require-approved-identity.
It completes all 33 entered phases, but Markdown validation reports broken,
unsafe/oversized or missing evidence targets and stops at a 256-error prefix.
That prefix is not a total repository error count. Root reads the complete
29462-byte stdout and 4800-byte stderr, retaining the actual failure rather than
adopting protected untracked ZIPs or changing validators. Its separate drain is
0.000177 seconds; committed whole's drain is 0.006513 seconds. Both have full
captures, equal sampled source and caller images and zero outstanding reservations.
The whole run ends while a new integration method is unfinished; no final
suite summary exists. No clean build,
test, runData, git diff --exit-code, GameTest server, dedicated/restart, GPU or
multiplayer command is executed for this new SHA. Standard152 qualifies its old
d7 source only and cannot qualify the new source implicitly. Current C:/D:
free-space metadata is 27.36/250.57 GiB; no full build/native run starts here.

## Independent work and retained failures

Independent162 proposes the batch by static source analysis only; no tests or
timing. Its assigned-read cutoff is 2026-10-10T10:32:07.6158932Z.
Independent163 reviews the intermediate whole diff, runs 15 passing tests and
reports one Medium: an exited child can disable timer-only final EOF checking
and allow a late result. Its finite probe reproduces publication beyond the
declared window. The successor adds explicit clocks, bounded nonblocking reads
and real held-open-pipe tests rather than merely changing the timer callback.
Historical163 remains CHANGES_REQUESTED for its original image.
Its assigned-read cutoff is 2026-10-10T10:49:54.889051+00:00.

Independent164 reads the complete final tracked diff and new file, reruns all
18 tests with original31.590570 /unit31.232 seconds, exit0 and complete capture,
and finds no scoped correctness/authority/deadline defect. Source is dirty
development evidence for this peer run, not committed delivery or whole-suite
acceptance. Its assigned-read cutoff is 2026-10-10T11:04:45.130451+00:00.
Root moves source HEAD only after every assigned-read cutoff. Root reads each
full report and freshly verifies the sealed peers as inert data:162 has
10 files /37822 bytes,163 has 51 /215779,164 has 46 /364436. No sealed helper
is executed, imported or copied for execution.

The development17-test failure is a test-double lifetime error: replacing a
fake stdin lets the old object finalize and mark the fake process exited.
The correction retains that old input until assertions finish; assertions,
deadlines and negative scenarios are not removed or weakened. Its failed runtime
and raw receipt remain. Baseline, before and after whole/packet timeout runtimes
also remain. No prior output, world, unknown PID or inherited material is cleaned.

Administrative failures are explicit: guessed paths are repaired with exact
tracked names; a Task163 generator pin has one missing hex digit and is corrected
in an additive message while the original task/error remains; an optional absent
tests/__init__.py assumption is removed before a target starts. Peer162's first
PowerShell ancestry verifier fails before hashing and is repaired additively.
Peer164's first observer compares access time and its first sealer compares
cross-API creation time; both failed images are retained. Successor comparisons
exclude read-induced access time, preserve same-API stability and shared
cross-API identity, without changing source pins or command limits. Root's
first fixed-path Git observations use only the named two-link Task150 exception;
the generic multiple-link rejection is unchanged.

## Evidence references and limits

The separately capped external packets are:

```text
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-python-suite-continuation-root-20261010-161
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-python-suite-committed-root-20261010-161
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-python-suite-cost-review-20261010-162
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-payload-transport-review-20261010-163
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-payload-transport-review-20261010-164
```

Each packet remains <=4 MiB, ordinary files <=1 MiB and inventory <=5000;
aggregate slice evidence remains below100 MiB. The committed packet preserves
the first packet's raw data and unchanged 4 MiB ceiling rather than deleting
failure artifacts. Unsealed Root coordinators may be used while owned and pinned;
sealed peer scripts are inert audit data only. Final envelope hashes belong in
the externally sealed result, not a self-referential in-repository checksum.

Fresh Root callers and peer observers are not independently qualified standard
callers. They bind sampled tracked Python, case-ID and named bootstrap bytes,
current helpers/interpreter, named Git and v5 images, not all host state, loader
lineage, DLLs or arbitrary environment. Source utility pins remain unchanged.
The first committed-module administration did not retain an entry/terminal hash
of its outer admin161.py wrapper; it is module evidence with that additional
binding limitation, not a fully qualified caller. Later committed cohorts bind
that wrapper explicitly. No old Root150 helper is reused or executed.
Direct os.set_blocking setup-failure injection and real delayed-child-exit
coverage would strengthen tests; the peer establishes no defect from these gaps.

## Remaining scope and version status

The owner replies repeat the existing sleep decisions01/03/05 and dual-memory
decision07 with ADR-069's limited adoption. They do not authorize changes to
production sleep, resource helpers, budgets, saved spawn points or version Gates
in Task161. Source139 d7 and sealed150/152 stay unchanged; protected AGENTS.md
and183 inherited Main rows are not adopted or staged. Main changes exactly the
five assigned records, not source integration, state acceptance or ledger delivery.

Whole Python/strict qualification, committed-source standard obligations,
independent full-suite receipt, native62 ERROR disposition, G4 d605 disposition,
portable evidence, Main integration and all G0-G9 remain open. Ledger remains
186 PLANNED /154 REVIEW. No release/tag, v1.9 or version completion is claimed.
Continue only v1.8's bounded qualification and explicit remaining gaps.
