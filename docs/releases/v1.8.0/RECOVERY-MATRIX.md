# v1.8.0 recovery matrix — C6 draft

**DRAFT; every C6 native/recovery result PENDING.** Candidate commit
`c8fa16e16be87554f26c402546733df6555d15e6`, host JAR SHA-256
`9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.
The [C6 switch report](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md)
keeps the exact bay B/CAP/EXIT rows, cargo N/Q/X rows, shared-store rows and
accounting scenarios. This matrix indexes them; it does not replace their
assertions or relabel earlier candidate/smoke results.

## Invariants and observation method

For every forced-stop boundary, capture the stopped-world files, actual
installed JAR hash, checkpoint/acknowledgement markers, schema, operation/owner/
rocket identities, typed endpoints, receipts/journals, reservations/locks and
loaded chunks. Restart the same world **twice**, recording state at each start
and continued action. A clean stop/reopen alone is not S2 forced-stop evidence.
Before/after conservation counts world blocks, containers, typed fluids, energy
where applicable, snapshots, passengers, entities and journals; only explicit
recipe/fuel consumption may change the total. Unknown quantities never mean zero.

| Boundary / failure | Required recovery rule | Planned C6 evidence | Result / state / hashes |
|---|---|---|---|
| Assembly/disassembly/transfer cuts | Transaction state chooses one authoritative outcome; no duplicate blocks, inventory, rocket or passenger | A04; inherited ASSEMBLED/FUELED/COUNTDOWN/ASCENT/TRANSIT_PREPARED/DESTINATION_SPAWNED/DESCENT/LANDED comparisons | PENDING |
| Ordinary/governed resource return and retirement | Keep exact proof/claim until durable acknowledgement; no source recreation over restored terrain | `cargo-B3-S6-REUSE`, J22 and fuel inventory's additional cut reconciliation | PENDING |
| Bay D0–D7, receipt/checkpoint/registry writes | Publish/remove only after exact acknowledged prerequisites; failed/UNKNOWN checkpoints keep required holds and reservations | `forced-A` through `forced-H`, retries, `released`, `forced-W331-*` | PENDING |
| Bay removed/changed root, re-arm, capacity and unload | Refuse unsafe admission; preserve durable/pending/REPAIR receipt accounting and exact reason | `forced-W358-*`, `forced-W363-*`, CAP/Q and R-042 families | PENDING |
| Bay passenger release/exit | Preserve assigned-seat/exit authority and bounded fall safeguard; no forged movement authority | `exit` EXIT.0–EXIT.10; J02/J03 dedicated and LAN V2, J04 | PENDING |
| Fluid cargo L1/L2 and partial store prefixes | Reconcile only proven BEFORE/AFTER endpoints; exact typed readback; mismatch holds endpoints | `cargo-C-S1/S2/S3`, accounting cargo load/unload | PENDING |
| Fluid cargo O1–O4/O3J, pruning/binding and veto | Durable settlement intent controls release; veto retains protection; pruning requires exact acknowledgements | `cargo-C-S4`, `cargo-C-S4-LOAD`, `cargo-C-S5` | PENDING |
| Item cargo cut/replay/settlement | Same exact typed/identity boundaries; do not substitute fluid evidence | `cargo-D-S1`, `cargo-D-S2`, `cargo-D-S2-LOAD` | PENDING |
| Fuel loader L1/L2/UNKNOWN/barrier/external storage | No credit before required proof; UNKNOWN or unsupported external storage remains held, not guessed successful | `cargo-B3-S1/S4/S5/S6-REUSE`, `accounting-cuts-loader` (28 cases) | PENDING |
| Typed accounting outcomes, splits, loss/gain/substitution | Keep signed per-fluid accounts, immutable event/digest identity and explicit unresolved holds; not whole-world conservation | `accounting-outcomes`, three `accounting-cuts-*` families, X02 stable differing-vector readback | PENDING |
| Root 2 → root 3 interrupted migration | Checked backup/stage/replacement and safe repeated load; preserve originals and diagnostics | `accounting-root2-upgrade` (4 cases), I03 | PENDING |
| Unsupported/future/invalid ledger | R-031 refuses whole-world startup; retain files and actionable recovery diagnostics | `r042-R031_FUTURE`, `r042-R031_INVALID` and accounting families | PENDING |
| Unknown presence / NOT_LOADED bay or escrow store | Never equate unknown with absent; protective server-wide rocket/device locks; no unsafe new work | `r042-N_BAY/L_LEDGER/U_BAY/U_LEDGER/X_BAY/X_LEDGER` | PENDING |
| Shared pending capacity | Charge durable/captured/UNKNOWN copies exactly once; 64 server / 8 per rocket / 16 per owner shared limits | `cargo-C-Q`, `r042-P_SERVER/P_OWNER`, CAP and shared authority GameTests | PENDING |
| Unsupported device root / shared denial pool | Protect device and terrain chunk; shared per-Level 256-coordinate denial pool can escalate to all later Level terrain saves at 257 | All six `w336-saturation-*` families | PENDING |
| Old JAR on journal schema 3 / ledger root 3 | Explicit refusal is not complete-world byte preservation; keep failed metadata-rewrite checks | `cargo-G2-REUSE`, `w336-downgrade`, `accounting-root3-old-jar` | PENDING; historical failures retained |
| X01 acknowledged/staged bytes rolled back in simulation | Run unchanged recovery/conservation oracles on each retained state, two starts; classify undetected loss | X01 S3 rows X01-01–X01-12 | PENDING |
| X02 writer/read/cleanup/corruption faults | CAUGHT/NOT_CAUGHT/NEEDS_REVIEW classification with exact writer/fault; preserve unobservable outcomes | X02 S5 rows F1–F10, four selected writers, real offline group first | PENDING |
| X03 FF_REBUILD_SOURCE whole-world repair | Only a proved record on a checked stopped complete copy; no invented refunds or general reconstruction | `cargo-X03-REPAIR`, both fresh and migrated root-3 origins and controls; packet P3 | PENDING |
| Inherited mission/endgame crash states | Credit/effect once, exact destination/source and ticket release across stops | G04/H03 plus C6 automated recovery classes | PENDING campaign |
| Real player logout/passenger/seat/rejoin | Compare both clients and authoritative state; playerdata/offline residuals remain explicit | A05/A15/J02–J04/J07/J27–J29 | PENDING V2 |

## Non-coverage, scope and operator safety

- B11.2's native launch shape and B17.5's triple-phase bay group are reasoned
  unreachable. Ordinary loader `LF_S6_F9_RESPAWN` is excluded; the 96 selected S6
  cases and governed analogue are not proof of that ordinary shape. Retain the
  precise NOT_COVERED explanations in the C6 report.
- Process-kill S2 and E4 replay/fault injection do **not** cover real power cuts,
  OS cache/writeback, filesystem/controller/firmware, torn region/entity writes
  or arbitrary file rollback. A destructive test still needs owner authorization.
- Single-file restoration, including one migration backup, is unsafe. Stop,
  preserve the failed copy, and restore a complete consistent world backup with
  its runtime/configuration, never mix data/region/entity/player files from
  different times. A restart alone is not a repair.
- [R-044 sign-off §5](../../work/v1.8.0-root-queue/records/R044-SIGNOFF-DRAFT.md)
  is signed (Root decision R679, on the owner's behalf under the owner's
  2026-11-24 delegation); it is an accepted limit, not a passed downgrade test. An old-JAR refusal
  can exit 0; inspect `ARCE-BETA-2001`. The complete backup rule applies to both
  journal and ledger downgrade boundaries.
- [X03-D guidance](../../work/v1.8.0-root-queue/records/X03-D-OPERATOR-GUIDANCE.md)
  is approved for C6's build and published in the
  [endgame operator guide](../../ENDGAME-OPERATOR-GUIDE.md) (Root decision R681,
  after C6 passed both origins under R616 and R632). It repairs one world shape,
  is not a general repair, and does not authorize single-file rollback.
- [Fuel readiness](../../work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md)
  preserves the narrow 2026-10-26 conditional fuel acceptance. It does not accept
  all later accounting, R-031/R-032, R-040, R-042–R-044 or whole-root availability
  risks, nor enable fuel. Bay/cargo and fuel stay off in v1.8 (Root decision R682).

Actual commands/exit codes, stop/start UTCs, complete state manifests, oracles,
failure/correction history, schema census, tester and independent review are
**PENDING**. See [campaign evidence layout](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md)
§6. Never accept a final result from an overwritten `build/` world or log.
