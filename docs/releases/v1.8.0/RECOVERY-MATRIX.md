# v1.8.0 recovery matrix — C6 draft

**DRAFT; selected native results recorded; campaign recovery gaps remain PENDING.** Candidate commit
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
| Assembly/disassembly/transfer cuts | Transaction state chooses one authoritative outcome; no duplicate blocks, inventory, rocket or passenger | A04; inherited ASSEMBLED/FUELED/COUNTDOWN/ASCENT/TRANSIT_PREPARED/DESTINATION_SPAWNED/DESCENT/LANDED comparisons | K02: 89 sealed cuts have passing C6 attempts (W461/R664); loaded-chunk flight matrix 7 PASS / 3 FAIL (W465/R667). Three pauses NOT_REACHABLE_ON_C6, four old cuts unmatched; player/client and A04 coverage PENDING. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Ordinary/governed resource return and retirement | Keep exact proof/claim until durable acknowledgement; no source recreation over restored terrain | `cargo-B3-S6-REUSE`, J22 and fuel inventory's additional cut reconciliation | Selected 96-case cargo-B3-S6-REUSE PASS in attempt 2; ordinary F9 and additional fuel cuts remain NOT_COVERED. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Bay D0–D7, receipt/checkpoint/registry writes | Publish/remove only after exact acknowledged prerequisites; failed/UNKNOWN checkpoints keep required holds and reservations | `forced-A` through `forced-H`, retries, `released`, `forced-W331-*` | Selected attempt-2 families PASS; X01 late-loss FAIL is separate; client/REF/soak PENDING. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Bay removed/changed root, re-arm, capacity and unload | Refuse unsafe admission; preserve durable/pending/REPAIR receipt accounting and exact reason | `forced-W358-*`, `forced-W363-*`, CAP/Q and R-042 families | Selected attempt-2 families PASS; unreachable bay shapes remain NOT_COVERED, not passes. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Bay passenger release/exit | Preserve assigned-seat/exit authority and bounded fall safeguard; no forged movement authority | `exit` EXIT.0–EXIT.10; J02/J03 dedicated and LAN V2, J04 | exit family PASS in attempt 2; actual dedicated/LAN V2 and player cases PENDING. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Fluid cargo L1/L2 and partial store prefixes | Reconcile only proven BEFORE/AFTER endpoints; exact typed readback; mismatch holds endpoints | `cargo-C-S1/S2/S3`, accounting cargo load/unload | Selected native/accounting families PASS in attempt 2; X01 lost-history states stay FAIL. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Fluid cargo O1–O4/O3J, pruning/binding and veto | Durable settlement intent controls release; veto retains protection; pruning requires exact acknowledgements | `cargo-C-S4`, `cargo-C-S4-LOAD`, `cargo-C-S5` | Selected C-S4/C-S4-LOAD/C-S5 families PASS in attempt 2; client/REF/soak PENDING. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Item cargo cut/replay/settlement | Same exact typed/identity boundaries; do not substitute fluid evidence | `cargo-D-S1`, `cargo-D-S2`, `cargo-D-S2-LOAD` | Selected D-S1/D-S2/D-S2-LOAD families PASS in attempt 2; X01 late-loss FAIL remains. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Fuel loader L1/L2/UNKNOWN/barrier/external storage | No credit before required proof; UNKNOWN or unsupported external storage remains held, not guessed successful | `cargo-B3-S1/S4/S5/S6-REUSE`, `accounting-cuts-loader` (28 cases) | Selected families PASS in attempt 2; unselected older cuts and ordinary F9 remain NOT_COVERED. Admission stays off (R682). [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Typed accounting outcomes, splits, loss/gain/substitution | Keep signed per-fluid accounts, immutable event/digest identity and explicit unresolved holds; not whole-world conservation | `accounting-outcomes`, three `accounting-cuts-*` families, X02 stable differing-vector readback | Selected accounting families PASS in attempt 2; X02 stable differing-vector observation retains residual classifications, not whole-world conservation. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Root 2 → root 3 interrupted migration | Checked backup/stage/replacement and safe repeated load; preserve originals and diagnostics | `accounting-root2-upgrade` (4 cases), I03 | Four selected native cases PASS in attempt 2; R680 accepts the staging/backup limit. I03 remains PENDING. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Unsupported/future/invalid ledger | R-031 refuses whole-world startup; retain files and actionable recovery diagnostics | `r042-R031_FUTURE`, `r042-R031_INVALID` and accounting families | Both selected r042-R031 families PASS in attempt 2; complete schema/fixture campaign remains PENDING. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Unknown presence / NOT_LOADED bay or escrow store | Never equate unknown with absent; protective server-wide rocket/device locks; no unsafe new work | `r042-N_BAY/L_LEDGER/U_BAY/U_LEDGER/X_BAY/X_LEDGER` | Selected r042 families PASS; R691 signs only the R-039/R-042 protective scopes, not finite waiting or a Gate. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Shared pending capacity | Charge durable/captured/UNKNOWN copies exactly once; 64 server / 8 per rocket / 16 per owner shared limits | `cargo-C-Q`, `r042-P_SERVER/P_OWNER`, CAP and shared authority GameTests | Selected quota families PASS in attempt 2; unchanged 64/8/16 limits and unbounded retained holds remain explicit (R691). [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Unsupported device root / shared denial pool | Protect device and terrain chunk; shared per-Level 256-coordinate denial pool can escalate to all later Level terrain saves at 257 | All six `w336-saturation-*` families | All six saturation families PASS in attempt 2. R691 signs the unsupported loader root and shared pool, after KI-V180-GUARD-01 publication; no other family's data risk is accepted. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| Old JAR on journal schema 3 / ledger root 3 | Explicit refusal is not complete-world byte preservation; keep failed metadata-rewrite checks | `cargo-G2-REUSE`, `w336-downgrade`, `accounting-root3-old-jar` | All three families FAILED (R615/R618); R679 accepts only the downgrade residual and does not pass an assertion. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| X01 acknowledged/staged bytes rolled back in simulation | Run unchanged recovery/conservation oracles on each retained state, two starts; classify undetected loss | X01 S3 rows X01-01–X01-12 | FAIL: 72 of 82 states pass both starts, 10 late-loss states fail (R637). W451/R641 passive quantities are OBSERVED, authority/history lost; next operation untested. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| X02 writer/read/cleanup/corruption faults | CAUGHT/NOT_CAUGHT/NEEDS_REVIEW classification with exact writer/fault; preserve unobservable outcomes | X02 S5 rows F1–F10, four selected writers, real offline group first | Original attempt 3: 44 SEEN_HELD / 10 CAUGHT_LATER / 10 NOT_CAUGHT / 4 INCOMPLETE / 4 NOT_APPLICABLE. Retaining original bay images after triage: 49 / 14 / 5 / 0 / 4 (R636/R639). No blanket PASS. [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| X03 FF_REBUILD_SOURCE whole-world repair | Only a proved record on a checked stopped complete copy; no invented refunds or general reconstruction | `cargo-X03-REPAIR`, both fresh and migrated root-3 origins and controls; packet P3 | Attempt-2 FAILED on Windows path length (R616); separate rerun PASS_ACCOUNTING_ENABLED_BOTH_ORIGINS (R632); operator text approved/published for C6 (R681). [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
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
  journal and ledger downgrade boundaries. The signed scope distinguishes return
  to v1.8: journal boundary (a) needs the pre-downgrade backup, while ledger
  boundary (b) also permits starting the refused world on v1.8 without a restore,
  as measured in R652. Returning to an old JAR always needs the pre-upgrade backup.
- [X03-D guidance](../../work/v1.8.0-root-queue/records/X03-D-OPERATOR-GUIDANCE.md)
  is approved for C6's build and published in the
  [endgame operator guide](../../ENDGAME-OPERATOR-GUIDE.md) (Root decision R681,
  after C6 passed both origins under R616 and R632). It repairs one world shape,
  is not a general repair, and does not authorize single-file rollback.
- [Fuel readiness](../../work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md)
  preserves the narrow 2026-10-26 conditional fuel acceptance. It does not accept
  all later accounting, R-031/R-032, R-040, R-042–R-044 or whole-root availability
  risks, nor enable fuel. Bay/cargo and fuel stay off in v1.8 (Root decision R682).

Native commands/UTCs, manifests, hashes, failed attempts and separate reruns
are indexed in C6 sections 2.1.4/13 and W461/W465, not absent. The complete
schema census, unmatched/structurally unreachable cuts, real-player recovery,
continued-operation coverage and independent final recovery review remain
**PENDING**. Six unsigned scopes under R691 retain their preconditions in
[release evidence](RELEASE-EVIDENCE.md); the four signed scopes are not G5/G6
approval or real power-loss durability. See [campaign evidence layout](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md)
§6. Never accept a final result from an overwritten `build/` world or log.
