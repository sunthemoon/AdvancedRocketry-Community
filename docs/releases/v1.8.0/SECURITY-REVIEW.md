# v1.8.0 security review — C6 draft

**DRAFT inventory, not a completed security audit.** Candidate C6 is
`c8fa16e16be87554f26c402546733df6555d15e6`, JAR SHA-256
`9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.
Automated hosted and selected native results exist; V2 and the final independent
audit remain PENDING. Root's recorded decisions are not new security approval. Test counts cannot establish zero Critical/High.

## Reviewed-input identity

[W402](../../work/v1.8.0-root-queue/records/W402.md) built a source-derived
35-row access matrix on C4: 28 menus, three C2S messages and four block-use
devices. It was a single-reader inventory, not an independent review of every
policy. Root R546 accepted its findings/dispositions in
[QUEUE, W402](../../work/v1.8.0-root-queue/QUEUE.md). [W410](../../work/v1.8.0-root-queue/records/W410.md)
records C5 regeneration under
`D:/GitHub/ARCE-Task-Evidence/final-campaign/9172113ad/fixtures/K13/`;
`SHA256SUMS.txt` SHA-256
`0cc5c63da827e88b99b6dd55aa307b8f6e6ef4ddba63d31eab130df187a11c39`.

For the current C6 inputs, [W423](../../work/v1.8.0-root-queue/records/W423.md)
verifies `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K13/`.
Its `SHA256SUMS.txt` SHA-256 is
`6553bcb6a17fdea8f79f4d7569912f9c626e3e5b36cd21855ed5824ed46c6628`;
`access-matrix.csv` remains
`3888f04c844db2deeb2cec16770f2d2f417a7414de031126672ae481d111d882`.
This is input integrity, not a new policy or live-refusal verdict.

Before final review, verify the C6 access CSV itself against that manifest and
the source, then record its hash and review commit here: **PENDING**.

## Server-authority policy to verify

| Surface | Access/open policy from W402 | Mutation boundary |
|---|---|---|
| Unowned ordinary machinery | Any admitted player: combustion generator, electrolyzer, rolling/precision assembler, solar generator/array, classic controllers, microwave receiver, satellite builder, suit workstation | Valid holder/menu, distance, state, chunk load and rate rules still apply; do not invent an owner restriction |
| Terminal/bay/monitor/endgame | Owner or operator level 2; an unowned satellite terminal is open to all; station members receive only the permitted public endgame view | Re-evaluate action-specific authority on server; membership is not device ownership |
| Fluid fuel loader and four cargo devices | Owner only; operator status alone does not grant menu access | Owner/binding/busy/receipt/ledger checks; production admission remains off |
| Station controllers | Owner, station member or operator may open | Station services check each mutation again |
| Rocket flight | Riding player or player within 8 blocks may open, board and leave | Launch/cancel require owner, creative player or operator level 2 |
| Beacon/landing pad/projector/pump | Block use, not invented menu packets | Device-specific server checks; force-field binding is operator-only |

All targeted menu/intent reach checks use **8 blocks**. The three C2S messages
carry a container ID, an entity ID or no target, never a client-supplied block
position. `rocket_flight`, `classic_controls` and `suit_motion` are pinned in
[network-protocols.txt](../../../src/test/resources/network-protocols.txt).
The server validates sender/main-thread context, holder/session, range, loaded
state, action/permission, rate and state before mutations. The self-only
`suit_motion` jetpack intent has no distance target: R546 treats distance as
not applicable, not a missing remote-position check. Do not infer authorization
from a client display or accept client resource/fuel/target calculations.

No request may force a client-named chunk. Variable strings, collections,
NBT, recipe/catalog loads and snapshots retain their hard bounds and protocol
compatibility checks. Frequent UI sends summaries/deltas. Replays must be
bounded/idempotent or rate-limited. For refusals record unchanged world/resource
and loaded-chunk projections, not merely a message shown on the client.

## R546 dispositions (recorded, not newly decided)

| Finding | Root's disposition | Remaining evidence |
|---|---|---|
| Most refusals are silent in server logs | No new v1.8 product logging. K18 receipts plus unchanged server-state readback prove delivery/refusal; logs only where emitted | I11 per-channel/holder/negative-case receipts PENDING; invalid-menu and rate branches cannot use a nonexistent log as oracle |
| suit_motion has no distance check | Self-only worn-jetpack action; no distance step needed | Codec/authority/rate checks and self-only state observations PENDING final review |
| J15 preview used obsolete namespace | W408 corrected it to `advancedrocketrycommunity:<id>` | C6 listen J15 PENDING; eleven events, including ui_select; machine_large preview-only |
| Projector use by non-operator is silent | Low KI-V180-UI-01, no permission bypass inferred | I11 unchanged state check PENDING |
| Eleven screens lacked a dedicated named row | C30 every-screen pass, both languages | I06/I08 coverage PENDING; no silent omission |
| 48 unreferenced textures | Low cleanup for v1.9, outside campaign | Record exclusion, not a successful visual check |

## Abuse and recovery evidence still required

| Threat / invariant | Planned exact evidence | C6 verdict |
|---|---|---|
| Foreign/stale/remote/spectator/unloaded/rate intents | I11/K18 plus C08/D09/H02; holder-specific C6 GameTests | PENDING live/independent review |
| Concurrent automation, replayed credit and inventory duplication | I05/I10, cargo C/D families, loader B3 reuse and accounting outcomes | Selected native families PASS in attempt 2; [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md). X01 lost-history FAIL/untested next operation remain; I05/I10 PENDING. |
| Endgame owner/operator/member boundaries | H02/I11 and per-system authority tests | PENDING V2 |
| Oversize/invalid recipes and reload logging | DataPackOverLimitReloadGameTests, serializer bounds; review ADR-076 precise platform exception/expiry | PENDING final audit; hosted tests are partial automated evidence |
| Unreviewed/excluded asset in JAR | C6 provenance/JAR audit, approved ledger/manifest reconciliation | P12 closed by R683/R685: all 62 records approved by Root as an AI reviewer under delegation; final G0 audit/approval remains PENDING. |
| Malformed/future stores, unknown file presence, shared capacity | `r042-*`, W336 saturation and accounting-root families; no unsafe reads/admission while NOT_LOADED | Selected r042/saturation families PASS; old-JAR families stay FAILED; [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md). R691 signatures are narrow protective dispositions, not general safety or availability. |
| Real player exit, disconnect, retained seats and observer consistency | J02–J04/J07/J27–J29 | PENDING V2; fake players do not qualify |
| Unobservable storage, file rollback, corruption and power loss | X01 S3/X02 S5 E4 classification and residual review | X01 FAIL: 72/82 states pass, 10 late-loss failures. X02 retains 49 SEEN_HELD / 14 CAUGHT_LATER / 5 NOT_CAUGHT / 0 INCOMPLETE / 4 NOT_APPLICABLE with original images (R636/R639). [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) section 13; not real power-loss proof. |

See [C6 report](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §§8–13
and [fuel readiness](../../work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md)
F22–F29. Save-denial risks include device/chunk protection, the shared
256-coordinate per-Level pool and Level-wide terrain-save refusal after its
257th coordinate, server-wide NOT_LOADED locks, and whole-world invalid/future
ledger startup refusal. Quiet successful tests do not bound continuous-traffic
holds or repair their residual loss cases. No routine exception-based save
control is approved anew here.

**Remaining review step:** an independent session must reconcile every C6 K13
matrix policy with actual source/negative-case receipts, reconcile every C6
native and V2 result with the threat model, and issue a candidate-wide findings
report including severity, affected data scope, reproduction and closure.
Reviewer, review commit/date, commands, receipts, findings and zero-Critical/High
verdict are **PENDING**. [The owner delegation](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261124.md)
records P1 downgrade acceptance (R679), P2 upgrade limit (R680), P3 narrow X03
guidance (R681), and all six switches staying off (R682). R691 signs R-039,
R-042 and R-044's unsupported loader root/shared pool, after the GUARD/HOLD
known issues were published. R-021/R-030/R-035/R-040/R-041/R-043 remain unsigned
until the named campaign/reference prerequisites in
[the residual drafts](../../work/v1.8.0-root-queue/records/RESIDUAL-RISK-SIGNOFF-DRAFTS.md)
exist. These decisions are separate from the technical audit and the owner's
one P11 confirmation for Gate/version/tag/main/release approval. No signature
changes a failed, NOT_COVERED or unrun row into a pass.
