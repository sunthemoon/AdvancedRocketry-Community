# ADR-023 — Explicit disposal before fueled rocket disassembly

```yaml
status: ACCEPTED
date: 2026-09-27
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.3.0
authorization: maintainer instruction to use recommended solutions
```

## Context

ROCKET-03C exposed an inherited behavior: disassembly restores capacity-only
fuel-tank blocks and removes the entity carrying remaining fuel. No refund or
disposal policy existed. Existing whole 500-unit fuel cells cannot represent an
arbitrary remainder; rounding creates or destroys resources. Durable partial-cell,
tank-BlockEntity or reverse-loader storage would require a separate transaction
and migration design. This decision does not pretend that disposal preserves fuel.

## Decision

Keep zero-fuel disassembly unchanged. Positive remaining fuel requires a separate
explicit server-authorized disposal-and-disassembly action. A normal sneak-use
request leaves the rocket untouched and shows the exact units to be discarded.
The chat action suggests `/arce rocket-disassembly confirm <uuid>`; the player
must submit that separate command. A second interaction, including offhand, never
confirms. Existing operator-only `arce rocket` commands keep their permissions.

The server holds at most 64 confirmations, one per requesting player, for 200
server game ticks. Each random token binds the player, entity UUID, owner,
snapshot UUID/hash, full immutable flight state and entity position. Repeating an
unchanged offer does not renew its expiry. Confirmation consumes its own token
once, then revalidates the same loaded entity, alive/non-spectator player,
permission, range, dimension, state, journal and unchanged bound data. Commands
carry only the token, never an authoritative amount, target position or snapshot.
Reuse the bounded rocket intent limiter; clear offers on logout and manager stop.
No confirmation survives restart, and no request forces chunk loading.

Do not empty fuel before entering the existing disassembly transaction. Fuel
remains attached to the entity if restoration fails and rolls back. Successful
entity removal deliberately disposes the confirmed remainder; emit the exact
amount in player feedback and the server receipt. Existing durable transaction
recovery is unchanged. This does not claim new arbitrary-crash atomicity.

The opt-in release-test command similarly rejects implicit fueled teardown.
Automation must explicitly supply `discard-fuel <expected-units>` and match the
actual amount before the transaction. These hooks remain startup- and operator-
restricted; they are not the player confirmation mechanism.

## Compatibility and boundaries

No public Java API, packet, save schema, tank identity or item format changes.
The new command and bilingual message IDs are additive and stable. Pending offers
are lifecycle-owned transient state, not saved authority or migration data.
Existing fueled teardown scripts need explicit opt-in; old evidence remains tied
to its original artifacts. Unfueled commands retain their syntax and behavior.

This selects an explicit disposal policy, not fuel recovery. Partial-fuel storage
can be designed separately if needed; it is not implied by the component API.
No Required Gate is waived or marked passed. V1/V2 and full acceptance remain
subject to ADR-018's existing schedule.

## Verification

- Unit tests: capacity, expiry, replay, wrong-player/token isolation, unchanged
  offer stability, replacement, logout/clear, exact binding and rate limiting.
- GameTests: non-operator command access without opening admin commands; refusal
  without mutation; successful exact disposal and cargo restoration; stale fuel,
  owner/range/state, rejected target, lifecycle and replay behavior.
- Keep the landed-reservation cleanup regression, now requiring explicit consent.
- Short packaged restart/external-cargo regression with explicit operator disposal.
- Bilingual generated text plus a reproducible manual confirmation checklist;
  automatic tests are not real-client visual approval.
