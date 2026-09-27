# ADR-037 — Planetary discovery and research

```yaml
status: ACCEPTED
date: 2026-09-27
owner: sunthemoon
scope: v1.4.0 discovery
authority_basis: standing maintainer authorization to use recommended implementation decisions
```

## Context

Satellite missions already award owner-scoped research accounts and write
world-shared discoveries to `CelestialSavedData`. Navigation currently offers
every physically eligible destination. Connect these existing systems instead
of creating a second research currency, player ledger or unlock purchase UI.

## Decision

- Add optional strict boolean `discovery_required` to celestial definition
  schema 2, default false. Legacy definitions and existing Java authoring
  constructors retain unrestricted arrival. Legacy export rejects a true flag;
  schema-1 input cannot contain the new field. New Mars, Venus and gas-giant
  definitions opt in. Earth, Moon and shared Space retain their old behavior.
  Older strict schema-2 implementations cannot consume the new resources;
  downgrade uses matched artifacts and pre-upgrade backups, not silent erasure.
- Discoveries are shared by the whole server/world, not by team or individual.
  A recorded discovery or visit unlocks a required body for every player.
  Research balance, lifetime totals, satellite ownership and claim permission
  remain individual. Joining, leaving or changing teams transfers no currency.
  Discovery grants no station membership, rocket ownership, physical surface,
  route, fuel, protection or operator permission.
  Retain the existing login/dimension-change Level visit tracker: a surface
  visit records that mapped body; entering shared Space records Space, not the
  orbit body of a station. A station visit alone does not discover its planet.
- Reuse the data satellite and terminal's assembly, launch/start, wait and claim
  workflow. Extend its allowed targets to the three new bodies while retaining
  the old targets, duration (200 ticks), yield (120) and discovery cost (100).
  The existing mission captures its definition values and whether discovery is
  needed when it starts. Thus concurrently started discovery missions each pay
  their captured cost; later missions on an already recorded body do not. This
  is a mission research fee, not an exclusive purchase or an account transfer.
  Neither a client quote nor a selected body is evidence of discovery.
- The catalog's names, parent relationships, environment and route data remain
  public, matching ADR-035. The star map shows locked surface destinations with
  a research-required explanation rather than hiding metadata. The terminal
  remains the discovery indicator for unmapped/non-landable bodies. A discovered
  gas giant still has no surface target; only valid, accessible orbit stations
  can be travel destinations.
- Check the destination body's discovery in the server planner used by both
  quotes and launch. Resolve station destinations through the committed station
  registry's orbit body, never client metadata. A missing/future discovery store
  must not unlock required bodies. Missing definitions remain unavailable.
  Owner/operator rocket launch requests obey the same discovery policy.
  Explicit administrative teleport/world-edit tools remain recovery tools,
  not survival progression; authoritative visits through them can discover a
  body. Creating a station does not discover its orbit body or bypass arrival.
- Only the final destination is gated. Route graph anchors are abstract transit
  distances, not implicit visits; an undiscovered intermediate graph node is not
  entered or discovered. Source discovery is not required, preserving evacuation
  from old/administratively placed rockets. An already prepared transfer keeps
  its captured journal; this change does not rewrite in-flight recovery.
  An identical source/target retains CURRENT, not a discovery refusal; it grants
  no movement even if that source has not been recorded as discovered.
- Reload toggles the flag for new launch planning. Existing discovery/visit
  records are retained on removal and re-addition. No default discovery records
  are fabricated for old worlds and no old research or mission values migrate.
  New-world planets start locked; already visited/discovered planets stay open.
- Add explicit navigation wire status 12, `DISCOVERY_REQUIRED`, with zero quoted
  fuel and no launch permission. It follows existing control/state precedence
  and planner ordering: physical admission, identical CURRENT, discovery gate,
  then ordinary route/component/fuel planning. Add the matching launch refusal code. Bump the
  exact-match flight channel from 7 to 8; packet layout/bounds, celestial display
  channel/schema, public API 1.7 and all SavedData schemas remain unchanged.
  Existing menu refresh propagates discovery within 20 ticks. Launch always
  checks fresh authority, including after reload; stale display cannot unlock.
- Preserve the historical generated satellite definition. A current generated
  replacement is packaged once by an exact-path exclusion in main/sources JARs,
  with packaging and DataGen checks. No upstream code, art or asset is imported.

## Boundaries and verification

Reuse the 128-body and existing mission/account/route/network limits. Navigation
must not load chunks, mutate discovery or charge research. The claim workflow
uses two SavedData authorities and a whole-DataStorage save, not a checked
cross-file atomic transaction; this slice proves normal
restart and claim replay, not every arbitrary crash/write-failure point. Broader
removed-content and recovery work remains V140-MIG. No lossless power-cut claim,
individual exploration privacy, direct research shop or v1.5 feature is implied.

The historical discovery store also caps retained IDs at 128, including removed
bodies. A full or future-version store refuses a new discovery. Existing mission
claim semantics preserve the claim as pending after its single research award/
fee; retries must not repeat the account mutation or unlock the body. Report
the pending result, preserve all records, and require operator data recovery
before completion when capacity cannot become available. Do not silently evict
old IDs, expand limits or treat a failed discovery as success.

Verify legacy/strict flag decoding, shared unlock with private balances and
ownership, repeat claims, locked surface and station denial, no debit/journal/
chunk mutation on denial, stale/reloaded planning, source evacuation, absent or
future records, removed/re-added IDs and packet status rejection/round trip.
Run short build/DataGen/GameTests, a packaged research/unlock/restart scenario,
and independent actual-diff review/key reruns. Record manual terminal/star-map
and two-real-client cases as unexecuted; full V1/V2/load acceptance stays under
ADR-018 and no Required Gate is approved by this decision.
