# TASK W221: governed named-pad crash windows with a real loader

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w221` (new; from the shared head named in your prompt, which contains W211) |
| Branch | `codex/v1.8.0-w221` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W221.md` (new; the cut design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W221/` |
| Finding | W211-R2 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W211-review/REVIEW.md` (SHA-256 `8456bc10…0d65d580`) |
| Code | `rocket/server/RocketTransferService.java` (lines 149 to 160, the governed named selector), `rocket/transfer/NamedLandingPadStaging.java` (lines 29 to 48), `rocket/server/RocketGovernedCleanupDispatcher.java` (lines 52 to 69, release after checked journal absence), `station/pad/LandingPadService.java` (W204's acknowledged-absence rule) |
| Background | `records/W204.md` (N3-C: N2_REFUSED, N2_STOPPED, N2_PAD_MOVED, N2_PAD_UNKNOWN on ordinary transfers); R-035 in `docs/11-RISK-REGISTER.md` |
| Harness | W211's S6 harness on the shared head (`scripts/run_v180_loader_s6.py`, `scripts/loader_s6_*.py`, the `EscrowLoaderS6*` probes), with R190 to R205's rules in [TASK-W211](TASK-W211-B3S6-LOADER-REUSED-FAMILIES.md) |

**Root decision R207.** W211's final review found that governed flights can select and claim a named station pad,
and governed cleanup releases an exact transfer's claim after checked journal absence, but no real-loader evidence
covers those windows: W211's flight fixture uses an unnamed destination, and W204's N2 cuts use ordinary transfers
with a scalar debit. This task supplies the governed analogue.

## What to do

1. **Cut design first, committed before any native run:** a real loader fill, then a governed flight to a named
   station pad; map W204's N2 purposes onto the governed boundaries (reservation and claim at preparation, a refusal
   that must keep the source and the claim unchanged, a stop, the claim through flight, the release after the
   transfer's acknowledged absence in governed cleanup, an UNKNOWN write that must not release early), with the
   stops, two restarts and the oracle for each row (the complete source and typed fuel, the pad's identity, epoch and
   claim, exactly one release only after acknowledged absence). Use only the product's own selection, claim and
   cleanup paths; existing release-gated observation hooks only, or a new one named in the record and gated like
   W211's (R200), with a test that the policy is untouched when the gate fails.
2. **The fixtures and oracle**, test-side, with tests that fail first, reusing W211's S6 oracle.
3. **Run natively** under the heavy-JVM lock, on a JAR built from committed source: each row with two restarts. R190
   and R201's renewal rule applies; a production cause (for example a claim released before acknowledged absence, or
   lost on a refusal) stops for Root.

## Verification

The new tests alone, failing first; the whole unit suite; the loader S6, escrow and pad Python tests; the station, pad
and governed GameTest batches; the native rows. Root's hosted run on the pushed branch stands in for a complete local
GameTest. An independent review follows.

## Not in this task

The governed publication failure (W220), the ordinary N2 form, production changes, pad policy, budgets, timeouts,
ADRs, status files and the ledger. W220 runs native cases under the same heavy-JVM lock; wait for it, never remove it.
