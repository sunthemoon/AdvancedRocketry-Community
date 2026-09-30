# Dispositions of the first independent contract review

Review of `ab207f0` (0 Critical, 7 High, 16 Medium, 16 Low; ADR-051 rejected).
The unmodified report is archived in `independent-review-01.zip` in this
directory. Section references point to revision 2 of each ADR.

| ID | Finding (short) | Disposition | Where |
|---|---|---|---|
| H1 | Barrier flush after every operation; write policy unspecified | Barriers kept only for existing `data` paths (ADR-038 ordering); everything else marks dirty, coalesced flush ≤ once/100 ticks; save epoch defined; flush cost is a C9 gate with an off-thread-writer follow-up if over budget | ADR-050 §2; ADR-051 Context, §6 |
| H2 | Stale queue entries; schedule throws after mutation | Entries removed on cancel/quarantine/early claim; capacity checked before mutation; failed schedule changes nothing; queue ≤ served records; churn test | ADR-050 §5, Verification |
| H3 | Record bounds below valid maxima | Bounds derived: mission 4 KiB, instance 4 KiB, satellite 2 KiB; enforced at mutation; tables/components rejected at load if worst case does not fit; byte budgets separate from counts | ADR-050 §3, §7; ADR-049 §7; ADR-052 §2 |
| H4 | Over-cap legacy registry blocks startup | Legacy roots load above count limits (≤ 8,192); admission refuses until retention drains; per-owner limits only at admission; 8,192/300-satellite S1 fixture | ADR-050 §6, §10, Verification |
| H5 | Receipts only freed by pruning | Receipt dropped after a durable acknowledgement (`ack_epoch < E`), independent of pruning; >256-claims A1 test | ADR-051 §7, Verification |
| H6 | Reconciliation table not total; blocked registry reads as "absent" | Total table incl. registry blocked, CANCELLED, QUARANTINED, rebound; "absent" defined as pruned with its justification; 36-row vector set | ADR-051 §7; `examples.json`, `check_examples.py` |
| H7 | Cancels bypass reconciliation | Owner cancel of resource missions only at the bound terminal after reconciliation; operator/quarantine cancel sends the instance to QUARANTINED; ADR-050/051 aligned | ADR-050 §8; ADR-051 §2, §8 |
| M1 | `serialized` set by any `saveAdditional` | Set only when a `ChunkDataEvent.Save` tag contains the receipt; IOWorker residual named | ADR-051 §6, §11 |
| M2 | Terminal ID not durable before binding | Binding requires the ID seen in a chunk-save tag; `AWAITING_WORLD_SAVE` otherwise | ADR-051 §4, §5 |
| M3 | No audit trail for rebind | `ARCE_MISSION_DELIVERY` lines for claim, rematerialize, ack, drop, conflicts, cancel, rebind, purge; `mission inspect` fields | ADR-051 §10 |
| M4 | Unknown kind: quarantine vs block | Block the registry (same as a future schema) in both ADRs | ADR-049 §1; ADR-050 §9 |
| M5 | Quarantine/recovery ambiguous | Precise invariants; QUARANTINED counts as unfinished and keeps the satellite binding; `satellite recover`; finished records may reference removed satellites/missions | ADR-050 §4, §8, §9 |
| M6 | In-place growth exceeds budget | Lifecycle-maximum size reserved at admission | ADR-050 §7 |
| M7 | Retention throughput; no per-owner retained cap | Eligibility 1,200 ticks after resolution; per-owner retained cap 128; throughput stated | ADR-050 §6, §7 |
| M8 | No inspection bound; unprunable records | Eligibility-ordered queues with inspection budgets; `mission purge`; instance pruning independent of the 1,536 condition | ADR-050 §5, §7; ADR-051 §2 |
| M9 | Menu format and protocols unspecified | Terminal menu format 2, builder/receiver menus, new `satellite` channel protocol 1, existing channel versions unchanged, bounds | ADR-049 §10 |
| M10 | Kind parameters not snapshotted | Snapshotted into the satellite kind state at launch | ADR-049 §6, §7; ADR-050 §11 |
| M11 | Receiver link ping-pong; unlink; root | Existing holder keeps the link; unlink on removal, by owner if receiver missing, by operator; receiver root schema 1 | ADR-049 §9 |
| M12 | Candidate ordering trap | Full-string `String.compareTo` specified; mixed-namespace vector and path-first negative test | ADR-052 §4; vectors |
| M13 | Survey verification needs the candidate set | `candidate_fingerprint` stored with survey and instances; `VERSION_CHANGED` on mismatch | ADR-052 §4, §7; ADR-050 §3 |
| M14 | Coverage gaps; configurable limits | Coverage table; star-map disposition; limits configurable up to fixed maxima | `v1.6.0-contract-coverage.md`; ADR-049 §8, §10; ADR-050 §6 |
| M15 | C7/C8 root-3 split | C7 implements the complete root-3 codec; no root 4; C8 split into C8a/C8b | ADR-050 §10; implementation log; COMPLETION-PLAN |
| M16 | Crash-cut model misses incremental chunk saves | Context rewritten with verified save paths; crash cuts rewritten; S2 cuts after an incremental chunk save and after a flush | ADR-051 Context, §11, Verification |
| L1 | `/time` wording | Corrected | ADR-050 §1 |
| L2 | "Only the scheduler moves ACTIVE to READY" | Lazy `data` completion and reconciliation listed | ADR-050 §4 |
| L3 | 64-bit arithmetic | Stated; overflow check in the checker | ADR-052 §3; ADR-049 §4, §8 |
| L4 | Builder/`data`, refusal codes, launch revalidation, replay, package item, builder energy | `data` terminal-only; schema-1 `data` definitions; codes and order; launch re-validation; missionless replay `IDEMPOTENT`; `satellite_package`; 10,000 FE | ADR-049 §3–§6 |
| L5 | Scan details | Y range, ratio, divisibility, scans independent of missions | ADR-049 §3, §8 |
| L6 | Legacy numbers and notes | Iridium richness variability 30; rebalance stated; 250 × zoom; chip-copy slot; legacy blueprint label | ADR-052 §5, §8; ADR-049 §7, Deferred; audit |
| L7 | Evidence/bookkeeping | Evidence packet committed with this chunk; porting-matrix rows; status files updated at acceptance | this directory; `PORTING_MATRIX.md` |
| L8 | Epoch reload semantics | Defined (file holds E+1; load sets E from file) | ADR-050 §2 |
| L9 | Stale indices | Server-side selection; no index in packets | ADR-049 §10; ADR-051 §4 |
| L10 | Canister minting; stack sizes; withdraw; capability | Minting recorded as a balance decision; buffer counted in items; menu-only withdrawal; existing capability unchanged | ADR-051 §4, §5 |
| L11 | Reconciliation mechanics | Terminal→missions index; multi-tick pass blocks actions; missing-terminal detection | ADR-051 §7, §9 |
| L12 | Class sizes | Split plan per chunk | implementation log |
| L13 | Downgrade | Whole world refused by v1.5; builder/receiver blocks and chips lost; backup required | ADR-049 Migration; ADR-050 §10 |
| L14 | Removed/re-parented bodies | `BODY_UNAVAILABLE`, solar 0, starts refused; system recomputed at start | ADR-050 §11 |
| L15 | Verify after pruning | `INPUTS_UNAVAILABLE` | ADR-052 §7 |
| L16 | Checker coverage | Slot-based blueprints with missing/duplicate/misplaced parts and refusal order; total reconciliation | `check_examples.py`, `examples.json` |
