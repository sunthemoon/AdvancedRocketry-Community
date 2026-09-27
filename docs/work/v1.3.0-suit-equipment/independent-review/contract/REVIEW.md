# V130-ATM-02 independent contract review

Reviewed baseline 02571635e9eb1dca26d71f87b9149e16252b17f7, then actual revised/frozen ADR-025 at f13c6136e513508459f94bbaf81d2062fec45018. ADR SHA256: 10e1276e243863a0aa21effbb165054addee89693ed9d7e53f71342015d74418. This is a pre-implementation contract review; root is the sole tracked-file writer.

## Findings (severity order)

### Medium proposal inconsistency — resolved before implementation

The initial int readOxygen(CompoundTag) could not distinguish malformed provider-owned saved data from provider failure. For a valid envelope with data containing a wrongly typed oxygen field, throwing would disable every user's provider; returning zero would make the item look refillable and risk overwriting the malformed body. This contradicted item-local rejection and preservation.

The actual revision uses OptionalInt (ADR-025:33,70-85): empty means item-local unsupported/malformed input with no mutation; null or present out-of-range values are provider faults; missing-root initialization requires present zero; post-write empty is a provider fault. This resolves the ambiguity without adding a fourth exported type.

### Low documentation boundary — clarify old-host launch compatibility

ADR-025:144-146 says old hosts retain the new root while the item mod remains installed. That is a storage-layer statement, not a guarantee that the integration binary can load against an older API. ADR-021:40-44 explicitly separates API metadata checks from binary loading; a provider directly referencing the new API 1.3 types can prevent an API 1.2 host from loading.

Minimal clarification: qualify retention with the item/integration mod remaining compatible/loadable on that older host. No API redesign or broad downgrade campaign is requested.

## Other reviewed clarifications

- ADR-025:129-130 now explicitly removes both armor mappings and oxygen from disabled providers and recomputes piece count after failed debit. Thus mixed-provider/built-in chest behavior is no longer ambiguous.
- ADR-025:58-59 states registration does not itself make an ordinary non-ArmorItem wearable.
- ADR-025:119-121 requires actual held canister identity/count/hand revalidation after preparation and before committing oxygen. This addresses both resource authorities, not just the worn chest.
- ADR-025:91 repeats bounded validation before recursive equality on mutated read arguments. The existing RocketNbtSize.java:13-19 serializes before checking size, and RocketBlockEntityPayload.java:24-26 copies first, so those existing helpers alone cannot fulfill this stronger new contract.

## Actual existing behavior and compatibility

- PlayerLifeSupportService.java:42-49 counts only actual worn built-in armor and reads one chest, then calls the pure engine. Its existing debit path at 66-89 assumes a validated built-in update cannot fail. External providers must replace that assumption with the ADR's original-phase recomputation, not advance phase and then retry.
- PlayerLifeSupportEngine.java:24-35 consumes one unit on the 20-tick boundary, while 39-55 applies two vacuum damage at that same boundary when unprotected. Recomputing a failed debit with zero usable oxygen and the original phase is compatible with that cadence. Creative/spectator handling at PlayerLifeSupportService.java:57-64 and breathable branches in PlayerLifeSupportEngine.java:11-21 do not debit.
- SpaceSuitOxygen.java:29-51 distinguishes missing, invalid and future schema; 54-73 writes only the legacy owned root. Its original key/schema can remain unchanged and use the existing fast path.
- OxygenTransfer.java:17-24 accepts only a whole 1,000-unit transfer with sufficient capacity. OxygenCanisterItem.java:37-50 fills before spending one canister and returning an empty shell; creative mode fills without spending or returning a shell. The proposed behavior preserves these normal cases.
- SpaceSuitArmorItem.java:20-33 recognizes slot identity; the proposal extends recognition to bounded fixed registrations rather than scanning inventory or capability graphs.
- PlayerLifeSupportSnapshot.java:19-24 bounds four pieces and 0-2,000 oxygen. LifeSupportStatusPacket.java:28-33 has fixed fields; LifeSupportNetwork.java:12-25 retains exact protocol 1, S2C-only main-thread delivery. API minor 1.3 need not alter this wire protocol.
- AdvancedRocketryCommunity.java:202-225 currently constructs life-support services after atmosphere-boundary freeze; suit registration can join that queued setup before service construction. Stop cleanup at 229-235 can clear new session failure state along with player state, without discarding immutable mappings.
- Three new exported types are compatible with ADR-021's minor policy. Expected total classifier surface becomes 13 types while prior 10 signatures/behavior remain unchanged; JDK OptionalInt does not require another exported API class.

## Finite verification recommendations

Preserve existing SpaceSuitOxygenTest, OxygenTransferTest, PlayerLifeSupportEngineTest and built-in Forge player cases. Add the specifically distinguishing cases:
1. Structurally valid but provider-invalid body -> empty/item-local rejection, exact original subtree retained, a second valid item with the same provider still works.
2. Callback throw/null/out-of-range/read mutation or post-write empty -> no commit, provider disabled for the session, mixed armor mapping count recomputed, one bounded diagnostic.
3. Failed debit at original phase 19 -> same-interval vacuum damage, zero usable oxygen in the authoritative snapshot, preserved stored payload.
4. Whole-canister rejection and held/worn stack replacement -> no oxygen commit/spend/shell; successful survival and creative paths preserve existing whole-transfer behavior.
5. Exact envelope size/depth/node boundaries including arrays, long keys and cycles/deep mutation checked before copy/serialization/equality; detached/retained returned references cannot mutate accepted state.
6. Server stop/new integrated lifecycle resets failure state; skipped registration preserves native ItemStack data, not a claim about unknown-item mod uninstall.

The suggested vanilla-leather registration fixture is legal under the foreign-item rule and avoids claiming new visual assets. Persisting its ItemStack in a vanilla chest proves ItemStack NBT persistence; it does not alone prove real-client equipment synchronization, player login restoration or full player-save transaction behavior. Any such stronger report needs separate evidence.

## Unverified limits / decision

No implementation exists in this review, so atomicity, exact bounded walker accounting, callback timing enforcement, session reset, native restart behavior and test results are not verified. Same-JVM arbitrary side effects/non-returning callbacks are expressly outside the sandbox guarantee. No runtime/classifier artifact or full Required Gate is approved.

No unresolved API-design blocker remains after the OptionalInt and lifecycle clarifications. The low old-host documentation qualification should be included when finalizing the public guidance.

## Commands actually run and write scope

Read-only commands: git status --short, git rev-parse HEAD, git diff --check (passed), Get-Content with numbered ranges, and rg searches across actual source/tests/governance/API policy. Mandatory project context and the canonical active ATM-02 leaf were read/rechecked. No Gradle, Java, unit test, server, network or remote command was executed. The user-owned untracked development-docs directory was neither read nor changed.

Only this fresh OS Temp report directory was written. No source, tracked docs, build output or repository file was changed by this reviewer.
