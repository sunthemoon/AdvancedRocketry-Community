# C19 checksum local and Git input hardening 16

Date: 2026-10-10. Owner/implementer/integrator: Root. Fresh Codex independent
source/test review is required. No Claude or nested worker delegation.
Status: in-progress. v1.8 development proceeds under ADR-060; no Gate approval.

## Outcome, identity and scope

Preserve checksum predicates/public returns, index-membership plus local-byte
identity, optional-artifact distinction and deterministic update bytes for
admitted inputs. Add explicit bounded, non-link local/Git admission and observed
read stability, with clear failure rather than truncated success. Do not change
historical source/bundle identities, skip checks or infer a timeout cause.

Base is the normally pushed Main ed06dcc8b2e0ee036b1f41ad35f4d57ea250490f.
Root's exclusive checkout will be D:/GitHub/arce-v180-checksum-inputs-20261010,
branch fix/v1.8.0-checksum-input-bounds. Source write_scope is exactly:

- scripts/validate_release_checksums.py;
- scripts/release_checksum_inputs.py (new, stdlib-only task-specific helper);
- tests/test_validate_release_checksums.py.

Root exclusively owns this task/checkpoint, canonical plan/status/log and new
external evidence leaves. All other source/build/registry/asset/ADR/ledger/user
files are forbidden. Do not alter or copy inherited untracked evidence. No
upstream file or asset import. One helper avoids the validator import cycle;
do not build a general validation framework.

## Input and compatibility contract

Keep load_artifact_metadata(Path) usable by v0.2-v0.9 callers; checksum operations
share their own input session through optional internal keyword parameters.
No selected-commit replacement for the current index API or newline conversion.
Keep coverage as the union of index and local evidence, including missing/index
and untracked/local evidence. Existing positive and negative tests remain.

Task-specific caps: Git stdout 4 MiB/32768 paths/30-second query, UTF-8 path
4096 bytes/64 components; checksum text 2 MiB/8192 records, metadata 8 MiB;
evidence 8192 filesystem entries/4096 files/512 directories/50 MiB per file/
100 MiB aggregate; hashed/read session total 512 MiB, optional artifact physical
file 256 MiB. Local loops use a shared 60-second cooperative deadline; OS calls
and delegated archive construction are not represented as interruptible by it.
These are new admission bounds, not relaxed existing qualification deadlines.
Verify the fixed accepted evidence and shared metadata callers are admitted.

Check lexical containment and every path component, including root ancestors,
for ordinary directory/file type and symlink/junction/reparse before reads.
Compare selected/opened/final handle and final pathname identities; observe
parent identity again, and retain session consistency when metadata is reopened.
Detect ordinary path/content changes; do not claim atomic filesystem sandboxing
or protection against malicious same-identity/ancestor ABA mutation. Root and
target inspection must not hide redirects by resolving them first. Preserve
portable-path grammar; Windows alternate-stream paths are not ordinary file
inputs. No new global reserved-name or casefold acceptance policy is added.

Update only a caller-requested checksum output, with ordinary parent/leaf
checks and clear failure; no Gate changes. Never run --update against committed
sealed evidence during qualification. New guards do not authorize rewriting
historical manifests or refusing/deleting player world data.

Explicit non-goals: historical-call caching/reordering; Markdown evidence
rehosting; Java/gameplay/sleep/save changes; JSON memory-window/helper acceptance;
archive-builder schema/decompression redesign. Delegated build_content_manifest
remains a separate archive-resource obligation; do not claim fully bounded
artifact verification from these local/Git bounds. Existing physical artifact
verification predicates must continue to run when an artifact is supplied.

## Verification and custody

Run the unchanged parent checksum tests, then source tests and relevant shared
metadata/repository/build-artifact callers. Add real ordinary-input positives,
boundary/count/aggregate/time negatives, actual parent reparse where supported,
observed read/path changes, Git framing/output/timeout failure and deterministic
update checks. Preserve actual skips and failures; no false broad PASS.

Commit/push fixed source after scope/stat/whitespace checks, then independently
review actual diff and rerun key checks in a separate checkout/Temp. Qualify the
fixed source with clean build, explicit test, two runData/empty diff cohorts,
unfiltered GameTest, ledger/provenance/whitespace and one broad Python/strict
attempt at unchanged limits. Actual source SHA binds receipts; Main alias is
not Main-SHA execution. All G0-G9 stay open unless independently accepted.

New Root local/Git evidence leaf <=4 MiB, standard subset leaf <=100 MiB, each
file <=50 MiB, streams bounded; own TEMP/TMP only. Record initial ownership,
raw argv/cwd/UTC/source/input/hash/exit/classification. Before sustained Gradle/
native commands ensure both volumes >=10 GiB. No source/world/JAR archives.
Use only runtime API/MCP for pet progress; unreachable pet is a safe failure.

Future cleanup must address R14-OPS01: retain each exact target's full ancestor
chain and reparse/identity/containment observations before removal. Check all
own commands ended, initial ownership and process identities. One native
PowerShell attempt only; no retry, peer takeover or earlier refused target.
Independent R11-OPS01 retained outputs are not this task's property.

Final checkpoint reports actual source/diff/tests/commands/custody, independent
findings and remaining risks. No release, tag, ADR acceptance or ledger delivery.
