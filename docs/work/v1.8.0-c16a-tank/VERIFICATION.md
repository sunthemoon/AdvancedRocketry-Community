# C16a-05 pressurized tank — development verification

Date:2026-10-04. Version remains `IN_PROGRESS`; no release or Required Gate
approval. Accepted contract:ADR-064 revision4, sections6/11. Delivered units:
`block:liquidTank` -> `advancedrocketrycommunity:pressurized_tank`, and
`config:CATEGORY_GENERAL.blockTankCapacity` -> COMMON
`machines.tankCapacityMultiplier`. The merged `block_entity:ARFluidTank` follows
its block; this packet does not authorize another machine-family resource bank.

## Delivered behavior

One fluid/its complete metadata is retained in the server bank. Capacity is
`floor(64000 × multiplier)` mB, multiplier0.25–4.0/default1.0. Reducing capacity
never truncates a balance: existing overflow remains drainable and refuses
further fills. Epoch-bound capabilities expose detached native views and refuse
stale/reentrant mutations. Whole-unit buckets/canisters conserve returned items;
partial/unsupported transfers refuse. Normal self-drops carry the same bounded
fluid root; dropped-Item placement does not apply arbitrary `BlockEntityTag`.

A transient Level-owned FIFO is bounded to1,024 requests/64 attempts per tick.
Each admitted tank pulls at most1,000 mB from the loaded tank directly above;
old dirty overflow remains eligible ahead of recently serviced tanks. There is
no column traversal, unbounded static world collection or arbitrary chunk load.
Ordinary Forge callback transfers are not independent-chunk crash transactions.

Schema1 `arce_pressurized_tank` is bounded to8,192 NBT bytes in both BE and
Item. Unsupported bounded roots remain verbatim and unavailable to ordinary
removal/capabilities. Oversized retained roots refuse the whole chunk save,
including unrelated changes in that chunk, until backup/offline repair.

## Actual verification and evidence

- [Independent module revision2](reviews/REVIEW-02.md):20 JUnit /seven suites
  and all422 required GT passed; the two metadata-alias red probes are retained.
- [Independent harness review](reviews/HARNESS-REVIEW-02.md) and
  [save-guard source review](../v1.8.0-c16a-save-guard/reviews/SOURCE-REVIEW-01.md)
  retain their exact scopes. The original actual four-BE loss is preserved in
  [incident evidence](../v1.8.0-c16a-save-guard/reviews/NATIVE-INCIDENT-01.md);
  the separate lifecycle correction does not relabel that failed run.
- [Root candidate11](../v1.8.0-c16a-integration/VERIFICATION.md):Java17
  `clean build test runData` exit0,1,638 JUnit /313 suites,zero F/E/S;
  DataGen written0. FullGT05 exits0 with all459 required tests. All2,940 frozen
  inputs,769 generated resource files and67 screened PNG identities match.
- Fresh Tank06 `run_v180_tank_smoke.py` exits0 in142.353304s on main SHA256
  `ea311ed02b4d1531e902e4031ad5e1174466e6378d3a6cb15e6679ca039e1e2a`.
  Seven actual phases retain300,000mB through capacity64,000→16,000 and bank
  restarts; a Count TAG_Byte1 dropped Item retains11,345mB through its restart.
  The oversized stopped chunk equals the original patched compressed bytes,
  with all four BEs and unrelated markerAIR; six guard refusals pair with six
  actual ChunkMap save errors. Raw stopped regions/receipts are in Root packet11.
  Original135-file historical world inventory and all input JAR hashes remain
  unchanged.
- [Independent candidate11 result audit](../v1.8.0-c16a-integration/reviews/INTEGRATION11-EVIDENCE-AUDIT-01.md)
  re-decodes exact stopped chunk/Item bytes and postchecks all identities with
  no new evidence inconsistency; it is not an independent server replay.

This closes only the checked clean-stop persistence/refusal scenario, not
arbitrary-crash atomicity or every future refusal. Actual client V1/V2,
reference-hardware performance, native Signature jobs/physical hatches and all
inherited/current Required Gates remain open. Original art is recorded in the
[tank provenance](../../provenance/v1.8.0-c16a-tank-new-resources.md); automated
screening and packaged byte identity are not human visual approval.
