# C16a-06 pump — development verification

Date:2026-10-04. Version remains `IN_PROGRESS`; no release or Required Gate
approval. Accepted contract:ADR-064 revision4, sections7/11. The delivered
legacy unit is `block:blockPump`, modern Block/Item/BE
`advancedrocketrycommunity:pump`. Its existing merged BE/particle dispositions
are not a claim of finished C18 presentation or complete classic parity.

## Delivered behavior

The server owns a16,000-mB bank,10,000-FE buffer and real-player placement
owner. Each source drain costs100 FE for1,000 mB, then a five-tick cooldown.
The transient deterministic search examines at most64 nodes per tick and4,096
per search, within32 horizontally/64 downward, and never loads arbitrary chunks.
It accepts standard registered bucket-liquid sources, including water/lava and
the registered classic liquids; custom pickup adapters, waterlogged blocks,
flowing cells and block entities are not source-debit authorities.

Ownerless placement drains nothing. Server protection checks include spawn,
body/region policy, modification permission and Forge break cancellation.
Loaded destinations receive at most1,000 mB/t through bounded guarded callbacks.
Simulation, refused/oversized inputs, stale capabilities and reentrant live load
do not grant a resource mutation. `machines.pumpEnabled` pauses draining/export
without deleting the bank. Right-click shows status; only the real owner or an
authorized operator retries. Normal self-drops retain the bounded pump root;
placement rebinds ownership to the server-derived real placer.

Schema1 `arce_pump` is independently bounded to8,192 NBT bytes. Unsupported
bounded roots stay verbatim and unavailable; oversized roots use the shared
whole-chunk save refusal. Search frontier is not persisted. This is not external
capability/crash atomicity or proof for every possible protection-mod callback.

## Actual verification and evidence

- [Independent module revision2](reviews/REVIEW-02.md):22 JUnit /five suites,
  unchanged ID-bound probe and all433 required GT passed; the three original
  Medium findings and failing probes remain retained.
- [Independent opt-in hook/harness review](reviews/NATIVE-HARNESS-REVIEW-02.md):
  seven Java checks and25 Python checks passed, including console-only authority
  and stable terminal reporting. These alone are not native execution.
- [Root candidate11](../v1.8.0-c16a-integration/VERIFICATION.md):Java17
  `clean build test runData` exit0,1,638 JUnit /313 suites,zero F/E/S;
  DataGen written0. FullGT05 exits0 with all459 required tests. All2,940 frozen
  inputs,769 generated resource files and67 screened PNG identities match.
- Fresh Pump04 `run_v180_pump_smoke.py` exits0 in84.071956s on main SHA256
  `ea311ed02b4d1531e902e4031ad5e1174466e6378d3a6cb15e6679ca039e1e2a`.
  Four actual phases include a v1.7-world open and two clean same-world restarts.
  Seven exact typed roots/metadata survive; a real partial-search checkpoint
  changes100FE/empty bank to0FE/1,000mB lava and removes the actual source cell.
  Original135-file historical world inventory and all input JAR hashes remain
  unchanged. Raw stopped chunks/receipts are in Root packet11.
- [Independent candidate11 result audit](../v1.8.0-c16a-integration/reviews/INTEGRATION11-EVIDENCE-AUDIT-01.md)
  re-decodes the stopped bytes and postchecks all input/artifact identities with
  no new evidence inconsistency; it is not an independent server replay.

The earlier launcher-only failure and failing fullGT candidates remain retained.
Ordinary clean restart does not establish forced-stop recovery. Actual client
V1/V2, reference-hardware performance, broader machine-family integration and
all inherited/current Required Gates remain open. New art is recorded in the
[pump provenance](../../provenance/v1.8.0-c16a-pump-new-resources.md), screened
and packaged byte-identically, not human visual approval.
