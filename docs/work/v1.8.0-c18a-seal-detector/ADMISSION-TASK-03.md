# C18a-SEAL-ADMISSION-03

Date: 2026-10-09. Owner/implementer/integrator: Root.
Preparation base: `9429d00b6b24e9bd7de3b747d63528b7b7d06fa1`.
Status: IN_PROGRESS; no source or result exists at preparation.

## Outcome, dependencies and boundaries

Continue accepted ADR-067 revision 1 and its existing internal signatures.
Add bounded Forge GameTests for item actor/hand/host refusal, service thread
refusal, terminal local-handle closure and post-query held-identity revalidation.
Each refusal needs a successful admitted control and observable absence of
selected-cell access, feedback, cooldown or held-data changes where applicable.
Preserve the actual assertion/result distinction and record every failure.

Use owned ordinary embedded connected survival players. Registered-item
admission tests call the actual item entry directly, separating it from the
native caller's own gates; positive controls use native game-mode item use.
Local service tests use a new uninstalled handle and manager; they do not
replace the installed runtime or qualify a different catalog as installed.
A controlled context accessor may change its owned held identity after the
local manager query. Attribute that fixture callback precisely, not to a
native client/provider mutation. Package-private internals require the new
GameTest class to share the instrument package, following existing main-source
GameTest discovery; this adds test code to normal JARs, not a production hook.

No Claude assistance or delegated implementation. Actual-diff independent
Codex review and reruns are required separately by repository policy.

## Write scope and checkout

Root alone owns a NEW isolated worktree
`D:/GitHub/arce-v180-seal-admission-20261009`, branch
`test/v1.8.0-seal-admission`, created from the committed preparation record.

- NEW `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/instrument/SealDetectorAdmissionGameTests.java`.
- NEW `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/instrument/SealDetectorAdmissionWorkerGateTest.java` for exceptional worker/cleanup ordering.
- This task and NEW `ADMISSION-VERIFICATION-03.md` in this directory.
- NEW compact evidence archives for this task in this directory.
- Narrow preparation/integration records in `docs/status/COMPLETION-PLAN.md`,
  `docs/work/v1.8.0-implementation-log.md`, then `docs/status/CURRENT_VERSION.md`
  only when actual source/test evidence changes.

Root evidence belongs only to the NEW external leaf
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/seal-admission-root-20261009-01`.
Independent reviewer uses a different NEW leaf and disposable checkout.
No other source, event/registry/build/API/schema, asset, provenance or ledger
write. User-owned dirty AGENTS.md, inherited untracked files, other worktrees
and all sealed evidence are excluded and remain untouched.

## Non-goals and unchanged authority

No production behavior change, physical hatch/hook/save writer, O1/O2/O3
decision, recovery policy, oxygen/resource authority or acquisition change.
Local closure is not installed server-stop/restart certification. These tests
do not finish all Level/chunk/cell/query-order cases, installed precedence,
unlock, reload/unload, protection-mod, packaged S1/S2 or true-client V1/V2
obligations. No full item/ledger unit or Required Gate is approved. ADR-068
remains PROPOSED and R-021 OPEN. The acceptance cursor remains v1.0.0 under
ADR-060; active development remains v1.8.0 IN_PROGRESS /IMPLEMENTING.

## Verification and evidence

Publish this preparation task, version implementation log and active plan
before source authoring. Commit the source candidate before hosted checks.
Independently review the actual fixed diff and rerun applicable commands.
Run Java 17 uncached forced clean build, explicit test, twice runData with
empty git diff --exit-code, unfiltered runGameTestServer, content ledger,
bootstrap provenance, strict repository and whitespace checks. Preserve raw
argv/times/outputs/XML, source/input/artifact identities, errors and failed
attempts. Never weaken assertions, timeouts or budgets to manufacture success.

Check C: and D: free space >=10 GiB before full commands; use owned D:
TEMP/TMP/java.io.tmpdir. Bounded worker refusal checks may only touch already
captured inputs and must terminate before fixture cleanup. New outputs are
disposable, source-world inputs are not. Native PowerShell cleanup requires
ended-process, ownership, containment and reparse checks, one attempt, no
retry of denied targets. Keep compact evidence with relative manifests, not
whole reproducible source/build/world copies; enforce repository size budgets.

## Corrective verification scope (2026-10-09)

Independent actual review of original e456ffc0 identifies a Medium exceptional
harness-lifecycle issue: a worker surviving cancellation/join can throw while
outer try-with-resources still closes its local reader and restores fixture
cells. The external diagnostic invokes the actual helper and an external
resource-unwinding witness, not the Minecraft cleanup body or a normal detector
hang. Original source, diagnosis and complete cohort remain separately pinned.

The additional pure Java test path above is declared before correction.
Require each actual local-reader/fixture close to check its owned worker's
terminal state before any cleanup. If termination cannot be established, fail
and retain the owned fixture rather than close/restore it while a worker lives;
do not claim automatic recovery of that exceptional fixture. Normal checks must
finish their workers before cleanup. Explicitly released diagnostic workers
must be joined before diagnostic cleanup. Existing wait/join/tick budgets remain
unchanged; no forced thread stop, new hook, periodic poller or production seam.
Test actual helper completion, exception, unresolved-worker cleanup refusal and
terminal-state recovery using finite owned threads. No Required Gate is waived.

## Candidate checkpoints

Preparation is committed/pushed at 77154351. Original source is
`e456ffc0eab1c0752fa5e6e57b4c63ddedbac964`; its complete Root cohort ends with
2,179 actual JUnit in each forced build/test and all 565 required GameTests
passing. Strict repository exit 1 and 62 native ERROR records remain unwaived.
Those results do not dispose of the independent Medium lifetime finding.

Corrective scope is committed/pushed at a2e3c476 before correction. The separate
corrected source is `452fd2a0dc12895c2e2b955c80115457459888bb`, complete src tree
`f085b52691c4f72814566841f2d0ff69394a99e3`. Both actual resource closers check
their fixture-owned worker gate before cleanup. Four finite unit cases use the
actual helper/gate; exceptional retention is not automatic world recovery.
The unchanged full command sequence is running on that fixed source, with
distinct evidence files. Corrected review/results and source integration are
pending. Neither candidate is a delivered item or release acceptance.
