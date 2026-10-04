# C16a-03b-01-K2 bounded canonical NBT bytes

Date: 2026-10-04. Status: integrated development computation; evidence audit complete.
Author: delegated c18. Different source reviewer: c16a04_fluids; report frozen.
Root owns main/central/status writes.

Outcome:a package-private pure computation of named-empty-root canonical NBT
bytes under the already adopted adapter02 framing and K1 ordering. This does
not implement ClassicNativeHash.resources,resource/native getters,GuardTicket,
frame codecs,persistence or publication. No consumer/world authority is inferred.

Exact new private surface:
`ClassicNbtCanonicalBytes.encode(CompoundTag root,ClassicNbtLimits limits)`
returns a newly owned byte array or refuses before returning a partial result.
Input is caller-owned standard native NBT. Eligibility uses the unchanged
current BoundedNbt native-lossless preflight and fixed limits;this is this
helper's restricted input subset,not a change to ClassicNbtShape,OwnedNativeTag,
RootBundle or the full codec's supported/protected admission policy. It may
not normalize retained metadata or call native registry/resource callbacks.

Helper-only fixed-ceiling clarification:requested byte/depth/node limits may
be stricter than the existing constants,but no component may exceed the
unchanged REJECTED maximum (1,873,117 bytes/25 depth/86,529 nodes). Greater
requests are refused,not clamped. This does not modify ClassicNbtLimits or
any other helper/caller policy;future individual-root callers still use their
actual frozen per-root limits. Buffer allocation remains bounded independently
of the requested value,and no partial result is returned on refusal.

Dependencies:exact Source20 closure;accepted adapter02 native uncompressed
type/name/payload framing;accepted K1 complete compound-key order;existing
BoundedNbt lossless eligibility. Exact native/Java source facts must be pinned
before implementation. Any missing authority is reported,not silently chosen.

Write boundary:only two fresh files,ClassicNbtCanonicalBytes.java and its new
focused test in machine/classic/adapter,inside a fresh same-version Temp
patch-return candidate. All2,988 baseline inputs,public/Bank/value contracts,
schemas,guard inventory,old tests,build/registry/network/assets remain exact.
No old worktree/packet mutation,protected bundle access,commit,tag or push.

Verification:actual source-defined encoding differential and deterministic
order checks,strict byte/depth/node edges,all native payload types and exact
string/type/list/array fidelity;bounded refusal and input/output alias isolation.
Executable checks are evidence,not prefilled results. Preserve failures. Root
grants one Java17/offline/no-daemon/workers2/heap2G compile/scoped-unit slot;
no fullbuild/DataGen/GT/native/client launch. Independent actual-source review
and replay precede Root application/regression. Full native eligibility and
GuardTicket/frame/hash/API/native admission remain separate tasks.

- [x] Exact source-feasibility handoff and source/test proposal.
- [x] Actual bounded Java checks and different-agent source review.
- [x] Exact Root integration and complete automatic regression for the new source.

[Feasibility01](canonical-framing-feasibility-01.zip) SHA
`6278874b1c27327f3c6a4178ffad5a62c3809c2ab92bdd1f1495dbde139ce913`,
557,808 bytes/123 entries,manifest
`06f06169b8c1fe80f9e9127f33be03b6bb23f057a4ace77d20d5bba77638bb27`.
Eight finite Python framing controls and15 native/one JDK static declaration
checks pass;no Java/native execution in that analysis packet. Author disclosed
own phase01/K1 authorship;this is feasibility,not independent source review.
Root archive49 checks every CRC/manifest identity/safe unique path.

Fresh author candidate01 has2new files with2,988 Source20 baseline inputs
unchanged.19 annotated new tests exist;the granted bounded Java job is running,
not a passing result. Original default-autocrlf patch replay failure and separate
LF delivery remain. That is the historical candidate01 checkpoint,not the final
result. Final author20/1 suite/0FES and different-agent28/2/0FES have frozen raw
evidence;the original19/1 failure remains. [Source acceptance01](CANONICAL-BYTES-SOURCE-ACCEPTANCE-01.md)
records the limited disposition and negative-zero fixture distinction. Root
has applied exactly two new files. The 2,990-input development snapshot is
called Source21; its later R1 manifest separately records user-maintained
AGENTS.md changing during execution. The original failed collection remains.
Code is now committed and pushed at `3f3d62aed3980186fe0acc9592cf93ca436405fb`.
The [actual integration record](../v1.8.0-c16a-k2/VERIFICATION.md) reports
1,750 JUnit cases in 329 suites, 464 required GameTests, deterministic DataGen
and the bounded static checks passing. Its 61 ERROR lines, ledger closure
failure, original dirty diff and missing new-artifact native/client evidence
remain disclosed. Different-agent actual-evidence audit has qualified that
record without admitting consumer, persistence, native/client or version Gates.
