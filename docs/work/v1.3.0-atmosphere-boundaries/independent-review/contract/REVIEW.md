# V130-ATM-01 independent contract review

Reviewed primary HEAD e62346d768284d75581a3c84ca64f50d73f3ff7b, including accepted ADR-024 and active implementation-log leaf. Read-only source/governance review; no Gradle, Java, server, or gameplay execution. No repository files changed.

## Findings and disposition

1. Medium integration risk, now addressed by the accepted contract, not yet verified in implementation: queued invalidation can permit a stale in-flight room scan to republish breathable authority. AtmosphereLevelService.markDirty (115-141) removes cached volumes synchronously, but processDirtyQueue (278-290) cancels scan work only after the affected positions reach a 256-position batch. With an existing dirty backlog, a scan which already observed a wall can complete first (tick 169-201; acceptCompletedScans 314-335). ADR-024:75-84 now explicitly requires synchronous revocation of affected in-flight and unconsumed completed results, including replacement and backlog. A deterministic backlog regression is included at ADR-024:94-99. The production behavior has not yet been corrected or executed in this review.

2. Medium lifecycle requirement for implementation: tag invalidation must mutate manager/level state only on the owning server thread. ADR-024:81-84 specifies revocation but not dispatch. Cached Forge 47.4.10 TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD Javadoc (58-59) explicitly notes that single-player initial data loading still happens on the client thread; CLIENT_PACKET_RECEIVED must not invalidate server authority. Use an appropriate server lifecycle/thread handoff and do not carry a deferred action into another stopped/restarted server. This is a concrete implementation check, not an observed new production defect.

3. Low integration wording: ADR-024:34-36 promises registration before world services are installed, while AdvancedRocketryCommunity.java:119-120 currently constructs/installs AtmosphereManager in the constructor, before common setup at 169-170. Either move installation after freeze or precisely mean before any per-level service/world use with one-shot catalog installation; an empty usable catalog must not escape into world operation.

## Resolved proposal gaps

- ADR-024:37-39 explicitly rejects reentrant registration during compilation. Provider callbacks are invoked while claims are still being validated; a captured registrar must not recursively bypass outer preflight.
- ADR-024:45-53 excludes dynamic tag queries from pure BlockState classification. Tag reload remains a host runtime-priority operation, not recompilation of stored callbacks.
- ADR-024:34-36 explicitly covers both physical client and dedicated-server common setup.
- Compiled tables retain no providers/world references; callbacks do not run in scans, ticks, reloads, or chunk events. Existing loaded-before-state-read guard and legacy tag/door priority remain above extension lookup.

## Bounded compatibility and verification assessment

The four-type API 1.2 addition fits ADR-021 without save/network changes. Preserve the prior six API classes/signatures and extend exact artifact allowlists to ten; retain runtime/classifier byte-identity and classifier-only consumer checks. Compile all claimed states, including DEFAULT results, within stated cardinality limits; rejection must not add claims/table entries. The retry statement should be understood as bounded retained registry state, not preemption or a guarantee against arbitrary repeated same-JVM calls.

Finite useful tests are owner/window/thread/reentrancy/conflict/cardinality/atomic rejection/fake-clock limits; real Forge event-driven custom state and replacement transitions; affected scan cancellation behind a dirty backlog; tag reload cancellation; loaded-only observation; no callback after freeze; independent classifier-only build and bounded packaged startup/restart. Existing AtmosphereGameTests door test directly calls markDirty and therefore cannot alone prove the Forge notification bridge.

## Boundaries

No new implementation diff was reviewed, no tests were started, no physical-client behavior was observed, and no full version/release Gate is approved. No contract blocker remains after the accepted additions, subject to the lifecycle and initialization details above being implemented and verified. Commands were read-only git/Get-Content/Python ZIP inspection plus this Temp-only note. Root remains sole repository writer.
