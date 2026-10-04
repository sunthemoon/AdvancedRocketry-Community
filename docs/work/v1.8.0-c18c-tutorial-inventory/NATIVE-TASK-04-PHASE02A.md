# C18c Task04 phase02a: bounded quiescent properties input

Date: 2026-10-05. Status: PROPOSED; independent exact-text review and Root
disposition required before implementation. This is a test-tooling sub-slice,
not a new gameplay/save policy or full native fixture admission.

## Dependency and exclusive write scope

The four phase01 postimages are committed at
`d0f9cbdeaf5921da7ee6157a99873dd47fb7f1ad`; their parser/observations/bindings
and inert CLI remain read-only. Root assigns one fresh independent worktree
and exact base commit at launch. Only these four new files may be written:

* `scripts/classic_inventory_fixture_inputs.py`
* `scripts/test_classic_inventory_fixture_inputs.py`
* `docs/work/v1.8.0-c18c-tutorial-inventory/NATIVE-PROGRESS-04-PHASE02A.md`
* `docs/work/v1.8.0-c18c-tutorial-inventory/NATIVE-HANDOFF-04-PHASE02A.md`

Root owns central driver integration, status, contracts and Git. No existing
script, source, fixture, generated resource, AGENTS or sealed evidence is edited.
The new helper is private to this fixture, not a generic filesystem framework.
It has no CLI/driver or imports from the existing runnable parsing module;
future Root composition feeds its returned bytes to `PropertiesObservation`,
avoiding a future driver/helper import cycle.

## Caller prerequisites and returned observation

The caller has separately admitted the owned server root and established a
quiescent copied host: before seed launch, before reload launch, or after clean
stop. These are **caller prerequisites**, not assertions proved by a phase
argument, path/hash match or successful helper return. The helper does not prove
ownership, clean stop, process absence, valid marker/receipt or launch permission.
Concurrent malicious ancestor swaps are outside this quiescent-only boundary;
there is no silent claim of race-free adversarial filesystem access.

The API selects a fixed role and observation point. Roles are `configured` or
`active`; points are `seed_preboot`, `reload_preboot` or `stopped`, exact strings.
The only relative locations, generated from private constants, are:

* configured: `.classic-inventory-fixture/snapshot-server-properties-v1.txt`
* active: `server.properties`

No decoded JSON/acknowledgement/record/command field supplies a path or filename.
The root is an absolute local D path supplied by the admitted caller, bounded
to 1,024 strict UTF-8 bytes. It cannot contain lexical `..`, UNC/device/alternate
drive redirection. Literal ancestors are inspected before canonical resolution;
resolved target remains within that same observed root. Root and intermediate
components must be directories; the leaf must be an existing regular file.
Missing, dangling, directory, nonregular, link/reparse or permission errors
are refusals, not an admissible absent seed record.

Return newly owned immutable raw bytes only. Success is a bounded quiescent
read observation, not native-origin/past-byte/hook authority. The existing
phase01 decoder retains its separate 1,023-pair/2,048-node and whole-map/raw
binding rules; no properties decoding/default/normalization happens here.

## Byte, identity and resource requirements

* Read-only access only. Inspect actual Windows reparse attributes, not merely
  optional junction/symlink convenience methods. Refuse any unsupported platform
  proof capability rather than silently bypassing the requested checks.
* Capture literal-root/ancestor/leaf observations, then open read-only and bind
  actual descriptor regular-file identity/size. Compare relevant observations
  before/open/after. A detected replacement, size/metadata change, reparse or
  containment mismatch refuses; there is no refresh/retry or repair.
* A single unbuffered bounded byte read requests at most **16,385** bytes.
  Reject empty and >16,384 before returning bytes or structural decoding.
  Do not rely only on stat size, read_bytes or buffered readahead. Validate
  consumed length against descriptor observations; short/changed observations
  refuse within the same call rather than an unbounded read/retry loop.
* Close every owned file handle on success/refusal. No filesystem writes,
  directory enumeration, profile lookup, world reads, process/console access,
  mutable global state, dependency installation or arbitrary command execution.
* Errors use a small explicit fixed-code exception with no source path, raw
  configuration, OS exception text or secret-value echo. Underlying errors are
  chained neither in public strings nor default tracebacks (`from None`).

Document which identity fields are meaningful on the actual platform and what
the checks establish under the quiescent prerequisite. Repeated stat equality
is not a general hostile-race proof. Live `server.properties` during a running
host remains a separate unimplemented stronger read/authority requirement.

## Verification and command permissions

Python/static tests only; no Java, Gradle, Minecraft/native server, network,
Git mutation, source-host copying or runtime launch is assigned. New fixtures
and helpers/process temp use a unique child of
`D:/GitHub/ARCE-Task-Evidence/v1.8.0`. Fresh own test fixtures may be created and
cleaned there with checked literal boundaries; old rejected C/D targets, sealed
records/source worlds and other owners' files are not touched or retried.

Exercise actual fixed paths and exact-limit/+1/empty bytes; immutable byte
ownership; invalid role/point/root and containment; missing/nonregular/leaf and
ancestor reparse; descriptor/file replacement/metadata/size/short-read refusal;
permission failures/no secret echo; closed handles; no writes, retry or decode.
Use controlled race/IO seams only for negative observations, and qualify which
checks additionally execute actual platform file/reparse operations. Keep the
existing 39 phase01 tests unchanged and run them against the assigned source.
Report all actual failures/skips/platform limits separately; model tests do not
prove actual hostile races, host status, native admission or complete Gates.

## Explicitly unassigned work

No live read, JSON/NBT/native record parser, directory/profile/cache ownership,
absence scan of the eight player files, CREATE_NEW snapshot/marker/receipt,
configuration copy/rewrite, forced setup, console framing/freshness, Java
fixture/hook, clean-host selection or seed/reload driver. JSON root/key-node
accounting is still a separate technical supplement; this leaf does not select
or consume it. It changes no node/time/Gate budget and opens no guarded writer.
Ledger, R-021 and all version G0-G9 remain open. Author handoff gives exact
postimage/patch hashes, actual commands/tests/temporary-fixture cleanup evidence,
remaining limits and no prefilled source/runtime verdict. A different agent
reviews the actual diff and reruns focused checks before Root integration.
