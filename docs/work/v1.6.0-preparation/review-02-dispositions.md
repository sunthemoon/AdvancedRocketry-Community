# Dispositions of the second independent contract review

The second review checked `ddc71c3` (revision 2). It found 36 of 39 round-1 findings resolved and 3
partial. New findings: 0 Critical, 2 High, 3 Medium, 13 Low. Verdicts: ADR-048 and ADR-052 accept;
ADR-049, ADR-050 and ADR-051 accept with required changes. The unmodified report is
`round-2/REVIEW.md` in `independent-review.zip`. Section references point to revision 3.

| ID | Finding (short) | Disposition | Where |
|---|---|---|---|
| R2-H1 | Non-`data` launches lost the barrier flush, so components can be lost | Every launch and every decommission flushes the registry before the terminal changes; durability order stated; S2 launch cut added | ADR-050 §2; ADR-049 §6, §7, Verification |
| R2-H2 | Rebind can pay twice when the old terminal returns | `paid_terminal` recorded by claim and recovery rows; only the paid terminal rematerializes or acknowledges; `REBIND_CONFLICT` binds back; owner cancel of a `rebound` mission quarantines the instance; the double payment is reduced to the stated operator residual (new terminal claims first). Vectors cover all 64 orderings, and a mutation that restores revision-2 behaviour fails them | ADR-051 §6–§9, §11; ADR-050 §3, §8; `rebind_orderings` |
| R2-M1 | Clock advances make the coalesced flush permanent | Clock-only changes set only the autosave dirty flag; an idle server makes no coalesced flush; C9 reports flush frequency | ADR-050 §2 |
| R2-M2 | Retention deletes ADR-038 discovery evidence | The newest CLAIMED `discovery_required` mission per body (≤ 128) is never pruned | ADR-050 §7 |
| R2-M3 | Terminal menu cannot fit 16 yields in 32,600 bytes | Menu data keeps only the format-1 content plus a flag; a server-side terminal view message (≤ 12 KiB, worst case about 8.6 KiB computed) carries the selected items and one yield | ADR-049 §10 |
| R2-L1 | Writer-thread fallback would break the epoch invariant | The follow-up ADR must separate the snapshot epoch from the durable epoch | ADR-050 §2 |
| R2-L2 | Liveness after reload | State read from chunk storage counts as persisted; carried or in-memory state becomes persisted through a chunk-save tag; the terminal calls `setChanged()` while anything is unpersisted | ADR-051 §5 |
| R2-L3 | New codes vs existing ordinal-persisted enum | Existing codes reused; new constants only appended | ADR-049 §4; ADR-050 §11 |
| R2-L4 | Field lists disagree | `receipt_seen`, `rebound`, `paid_terminal` added to the shapes (and to the lifecycle reservation); `link_epoch` removed; side effects of recovery rows stated; the acknowledgement/paid-elsewhere overlap resolved through `paid_terminal` | ADR-050 §3; ADR-049 §7; ADR-051 §7 |
| R2-L5 | Pruning queue time for unacknowledged records | Enqueued when the acknowledgement becomes durable | ADR-050 §5 |
| R2-L6 | Replay after the satellite started a mission | Any existing satellite with the same identity makes the replay `IDEMPOTENT` and consumes the package | ADR-049 §6 |
| R2-L7 | Decommission ordering | Barrier flush before the chip is blanked; an inert chip is the only residual | ADR-049 §7 |
| R2-L8 | "Absent means pruned" wording; outside-guarantee cases | Absent = pruned or purged; replacing the registry file with an empty one is outside the guarantee; the residual is qualified for power loss | ADR-051 §7, §11 |
| R2-L9 | Selection rate limit | 2 ticks for selection intents, 10 for state changes | ADR-049 §10; ADR-050 §6 |
| R2-L10 | Evidence bookkeeping | `VERIFICATION.md` and `independent-review.zip` are committed with the acceptance; the log points to both disposition files | this directory; implementation log |
| R2-L11 | Over-cap root 3; migration byte arithmetic | Root 3 over the limits loads with the same bound; per-record growth arithmetic given; C7 encodes a worst-case legacy root | ADR-050 §10 |
| R2-L12 | Satellite worst-case figure | About 1,860 bytes, with an encode/decode test at the bound | ADR-049 §7 |
| R2-L13 | Cancel of a `MISSING_SATELLITE` quarantine | Only a satellite that names the mission is released | ADR-050 §8 |
