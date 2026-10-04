# C16a-03b-01-K3 private canonical NBT digest

Date: 2026-10-04. Status: ready. Root owns integration and central files.

## Outcome and boundary

Add the remaining private computation of lowercase SHA-256 text over the exact
K2 canonical bytes. The new package-private surface is
`ClassicNbtCanonicalHash.sha256(CompoundTag root, ClassicNbtLimits limits)`.
Its return type is String. This is not the frozen GuardTicket-bound
`ClassicNativeHash.resources` API, a resource getter, native capture, hash/frame
codec, persistence writer or authority to publish a resource/checkpoint.

The accepted adapter framing, K1 order, K2 eligibility/ownership and all existing
per-root limits remain unchanged. Every call uses its own digest; no mutable
global digest/cache or new framework. Caller-owned input remains stable and
exclusive for the entire call. Refusal behavior comes from K2, not a new
supported/protected admission policy. No normalization or native callback API.

## Dependencies and ownership

The code baseline is immutable commit
`3f3d62aed3980186fe0acc9592cf93ca436405fb`, already pushed. K2 production SHA
is `1aa5ad420a1288820f148ea55c149510c5206c7351c928186548cada54b3dfd6`.
The separate read-only feasibility report identifies the existing digest domains
and sequencing; its SHA is
`bae5bf975f2653107db1862babd2af41c916823f4c5f70f545de48b3d9c092fa`.
This task freezes only a computation leaf, not a public contract change.

Author: delegated c18. Write scope: only two new files in an isolated same-version
worktree, `machine/classic/adapter/ClassicNbtCanonicalHash.java` and its test.
All existing files, Root, central registration/build/protocol/generated/assets,
Bank/value/K1/K2 code, old tests and the private documentation bundle are read-only.
User-maintained Root AGENTS.md, including its latest sections, governs the task.
The worktree's older instruction file does not supersede it.

## Verification and admission

Derive tests from exact byte digest/framing, deterministic compound order,
type/list/array/string fidelity, input ownership, fixed and stricter bounds,
invalid-input refusal and independent-call isolation. Verify actual result
against an independent JDK SHA-256 reference; no prefilled native/world verdict.
Preserve failures and exact baseline/postimage identities.

One bounded Java 17, offline, no-daemon, two-worker, 2 GB compile/scoped-test job
may run only after Root slot grant and recorded disk precheck. No full build,
DataGen, GameTest, native server or client. Different-agent actual-source review
and Root regression precede application. Keep a small patch/log/XML/result/
manifest report; no whole copied source tree or redundant large ZIP. Do not
commit, push, change HEAD or remove another agent's files; Root integrates.

- [ ] Exact two-file proposal and author checks.
- [ ] Different-agent actual-source review and replay.
- [ ] Root integration, current-version regression and phase commit/push.

Full codec/hash/Guard consumers, physical hatches, server creative interception,
world/native/client validation, C16b–C19 and all Required Gates remain separate.
