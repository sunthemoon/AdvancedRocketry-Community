# W126: design note for R48's D2, destination authority for a landed rocket with a retained named floor

Date: 2026-10-21. Status: **design for an independent read-only review**; no code, test or saved-format change.
Assignment: [TASK-W126](../TASK-W126-D2-DESTINATION-AUTHORITY-DESIGN.md). Base: shared commit `b58ed302`; all line numbers
are at that commit. Sources: [W121-DESIGN](W121-DESIGN.md) with Root decision R48, the W121 review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W121-review/REVIEW.md`), [TASK-W125](../TASK-W125-F1-NAMED-FILL-FIX.md) and
[W11A-DESIGN](W11A-DESIGN.md) sections 4, 5 and 7. "By reading" marks a claim that no test or run exercised.

**Terms.** T, L, D and X are as in W121. S′ is a stale source copy: the pre-launch source rocket as last saved in its
chunk before the in-memory discard at SOURCE_REMOVED (`RocketTransferService.java:740-744`), reloaded after a crash.
"Bound" means that the rocket's escrow section names the ledger.

## 1. What a landed rocket needs, and what the source proof protects

**Refuelling and disassembly** need `resourceMutationAllowed`: T COMMITTED, settled and not live, and the rocket T's
replaceable landed authority (`RocketTransferService.java:502-510`; disassembly at `RocketDisassemblyService.java:143`).
Landed disassembly then removes T with no source check (`RocketLandedDisassemblies.java:170-180, 261`).

**Launch needs T gone, not only settled** (by reading, with one existing test pinning the rule).
- The fill binds D to the ledger for good (`EscrowLoaderAdmission.java:224`; `RocketEscrowSection.java:15, 37-41`), so
  D's next launch is governed (`RocketGovernedAdmission.java:36`).
- A governed launch refuses while L has any journal record (`EscrowGovernedPreparation.java:47-49`;
  `EscrowFlightAuthority.java:43-45`, "Launch waits for checked predecessor cleanup", pinned by
  `EscrowFlightAuthorityTest.java:118-119`). The optional COMMITTED-predecessor replacement is not implemented, and only
  landed disassembly and operator settlement O3J (`EscrowSettlementRecovery.java:153-158`) remove an ordinary T.
- So **after any named fill, on the same Level or not, D cannot launch while T exists.** Loading the old site restores
  refuelling and disassembly only. W125's "usable for its next launch" must be judged by a launch.

**F1b also forms in the same session.** `approved` is written only by `permit` (`RocketTransferFences.java:124`), and
its key hashes every floor for L (`:144-149`). After L2 adds (L, T), the next per-tick `classify`
(`RocketTransferService.java:294`) fences T with FLOOR_PROOF_REQUIRED (`RocketTransferFences.java:63-64, 246-250`).
Same-Level fixtures pass the loaded-only proof at once. Across Levels T stays fenced; re-evaluation runs at most twice
per tick and once per 1,200 ticks per record (`:24-25, 131`) and adds no tickets (`:22`; `RocketTransferBayDispatch.java:33`
keeps fenced records out of `step`).

**What the source half protects.** For a COMMITTED T every action keeps the destination
(`RocketTransferRecoveryDecision.java:16-29`), so source copies are only ever discarded
(`RocketTransferRecoveryService.java:399`). The loaded-only proof (`RocketTransferFences.java:165-205`) requires both
discovery regions FULL, entity-loaded and entity-ticking (`RocketTransferEntities.java:211-230`) so that its census is
complete. A protected source copy (marked, non-zero epoch or invalid) then holds FLOOR_CONFLICT instead of being
discarded (`EscrowRecoveryFence.java:49-61`), and an ordinary S′ is discarded. The destination half protects the credit:
a missing D (whose rebuild would restore arrival resources), another identity, an epoch below the floor and protected
duplicates hold FLOOR_CONFLICT (`:55-68`).

**T is the interlock.** S′ is inert only because T names D: it fails the landed-authority test, and its launch returns
REQUEST_REPLAYED (`RocketTransferService.java:88-93`). A pre-fill S′ is unbound at epoch 0, so with no record for L it is
escrow-idle (`RocketEscrowGate.java:47-57`), and an S′ saved before its countdown becomes a free second rocket with its
blocks and fuel. **Settling T while T stays gives S′ nothing; removing or replacing T without an absence proof frees
it.**

## 2. Options

**(a) Destination-only proof, in-place scope.** For FLOOR_PROOF_REQUIRED only, a loaded-only observation of D's region
alone (one destination candidate, equal to D, meeting the floor's identity and epoch; no record, ARMED entry or
tombstone for L; bay UNCLAIMED) settles T under a RAM scope. It permits in-place changes to D (hand, ADR-027 loader,
escrow fills, later cargo). It forbids every recovery action, landed disassembly and any removal or replacement of T;
launch stays refused (section 1).
- *Format and restarts:* none; the scope is RAM and repeats after every start, every fill (new key) and every join of L
  (`RocketTransferFences.java:157-160`).
- *Governed, bay, cargo:* governed records route first (`:55-58`); bay UNCLAIMED only; cargo inherits the scope.
- *Stale copies:* S′ stays inert under T; its later join fences T again and the full proof runs.
- *Verdict:* refuelling only, plus a scope check that every T-removing path (today W85's M0) must remember.

**(b) Durable retirement of T.** A destination site for W98's coordinator (`RocketOrdinaryRetirement.java:26-37`):
own-store, force and readback prove that D's slot holds exactly one entity naming L, equal to D's frozen projection, and
that T's source origin slot and every discarded slot hold none (`RocketRetirementProof.java:54-63`); a checked journal
write then removes T. The floor goes through the existing collection, which needs a clean checked journal without T
(`EscrowFloors.java:97-106`; `EscrowCoordinator.java:337-341, 549-553`).
- *Format:* none; journal removal already serves W85, W98, O3J and F8.
- *Crash windows:* before the removal is acknowledged, T stays; after it, both restarts find no record, nothing touches
  the old site and the governed launch is admissible.
- *Governed, bay, cargo:* never for governed records; W98's REMOVE_JOURNAL bay gate; no operation may name L.
- *Stale copies:* S′ on the origin column is proved absent on disk; a loaded off-column candidate holds
  (`RocketOrdinaryRetirement.java:166-178`); a copy moved into an unloaded chunk by a bypass stays W98's residual.
- *Timing decides whether it helps.* **(b1) After the fill,** as the TASK states, T is already fenced and fenced records
  add no tickets (ADR-065 lines 1343-1344 and 1356), so across Levels the proof still waits for (c) or (c′). **(b2)
  Before the first fill,** T has no floor and is an ordinary unfenced record: ordinary recovery already tickets and loads
  both of its origin chunks at every start (`RocketTransferEntities.java:129-141, 570-582`), and W98-R3's ticket on
  end-origin census columns applies unchanged. Admission refuses with the existing TRANSFER_JOURNAL_UNPROVED
  (`LoaderEligibility.java:94-97`) and starts the retirement; afterwards the fill is NONE-guarded
  (`EscrowLoaderAdmission.java:216-217`) and leaves no floor (`EscrowFloors.java:14`). **(b3) Retiring only the floor**
  is rejected: D's non-zero epoch fences T again (`RocketTransferFences.java:65, 96-97`), a rebuild loses its guard and
  launch still needs T gone.

**(c) Operator-assisted.** The operator loads every column of the old source's checked set, not one block (progress via
the read-only `k/n` at `RocketTransferEntities.java:233-242`), then waits up to 1,200 ticks or runs `/arce rocket
recover`, which for a fenced record only reads already-loaded ends (`RocketTransferRecoveryService.java:161-166`).
- *Format and restarts:* no write and no crash window; `approved` is RAM, so this repeats after every start and fill.
- *Governed, bay, cargo, stale copies:* unchanged, because the full proof runs. Launch stays refused without (b1).
- *Variant (c′):* automatic non-forced tickets for a FLOOR_PROOF_REQUIRED record on both ends' checked sets (at most 16
  columns per end), with no action until the unchanged proof passes. No format change, but it reverses ADR-065 line 1356
  ("the legacy fences add none"), Root's ADR decision.

**(d) Document for v1.8.** Same-Level-only admission is not enough: every named fill blocks D's launch, and the
same-Level case relies on the source region happening to be loaded. The safe limitation admits only fills with no record
for L (NONE guard), W121's (c1). No format or restart change. An escrow loader then cannot fill a landed rocket that still
has T; hand and ADR-027 refuelling remain, and an unbound D's ordinary launch replaces T
(`RocketTransferService.java:189-193`). Governed flights, bay and cargo are unchanged; no copy gains authority.

**(e) A new durable marker** (for completeness): a positive election of D, like a READY head without a receipt, keeping
other copies of L locked after T leaves. Heads need a permanent receipt today ([W11B2-DESIGN-2](W11B2-DESIGN-2.md) line
53), so this is a format and compatibility decision, and S′ would stay on disk indefinitely. Not recommended.

## 3. Recommendation: (b2), with (d) until it is merged

Retire T before the first escrow fill (and, under D4, the first cargo load) on a landed rocket, through W98's proof with
a destination site; keep admission NONE-guard only until then. Test-world floors keep using (c). Production worlds have
no ledger ([risk register](../../../11-RISK-REGISTER.md) R-035), so (b1) and (c′) are not needed there.

**Rules.**
- Start only for an ordinary T that is COMMITTED, settled, not live and unfenced, whose loaded rocket is T's replaceable
  landed authority, unbound at epoch 0; L has no floor, record, ARMED entry, tombstone or pending landed disassembly, and
  the bay is UNCLAIMED.
- The census is D's slot, T's source origin slot and every discarded slot. A loaded source candidate is discarded under
  the unchanged `actionAllowed` for REMOVE_SOURCE_KEEP_DESTINATION and joins the census; a protected one holds
  FLOOR_CONFLICT. W98's crew rule applies to D's manifest, with W98's waits and holds (SLOT_UNLOADED, SAVE_OFF,
  CENSUS_LIMIT, off-column).
- Unlike W98's R5(c) (`RocketOrdinaryRetirement.java:378-383`), unsupported entity storage holds rather than removing T
  unproved, because (d) is a safe fallback. Admission reads the acknowledged guard, so a RAM-only removal still refuses.

**Invariants.** *No resource is spent twice:* T leaves only after every census slot is read back from disk, fills start
only from a checked generation without T, and no debit, credit, proof or L2 order changes. *No fence is weakened:*
`classify`, `permit`, the loaded-only rule, `joined`, `actionAllowed` and `EscrowRecoveryFence` are unchanged, fenced
records add no tickets, and the retirement never touches a rocket that escrow protects. *Every hold resolves or shows a
reason:* the loader shows TRANSFER_JOURNAL_UNPROVED during the retirement, and W98's bounded WAIT, RETRY and HELD lines
name the operator cases.

**Accepted side effects.** The old site's origin column is ticketed once per landing (attempts of at most 100 ticks,
400-tick expiry), as ordinary recovery does at every start today, which stops once T is gone. Pad reservation then rests
on the selector's entity check (`RocketLandingPadSelector.java:318-321`), as after a governed F8. W27d must recheck any
bay step that needs T. W125 is unaffected; its named cuts stay as coverage.

**Tests.** *Unit:* the start matrix; the destination projection; survivor and absent slots; the off-column,
census-limit, save-off and unsupported-storage holds; admission refusing a RAM-only removal and admitting a NONE guard
after acknowledgement; `EscrowFlightAuthority.arm` accepting the retired rocket while
`EscrowFlightAuthorityTest.java:118-119` stays; no flight ticket in the old Level afterwards (the `FlightTickets` sink,
`RocketTransferEntities.java:512-517`). *GameTest:* same-Level and Overworld-to-Moon retirement, NONE fill, retirement of
the fill and a governed launch reaching COUNTDOWN; S′ in the origin slot, off the column, and saved after its discard.

**Native cuts**, two restarts each. Conservation (loader −1,000 mB and rocket +1,000 mB, exactly once) and usability (a
second fill admitted and a governed launch reaching COUNTDOWN) are reported separately. "Old source unloaded" means
outside the spawn chunks, with no column of its checked set loaded or holding an `arce_rocket_flight` ticket.

| Cut | First restart | Second restart |
|---|---|---|
| Retirement stopped before its journal replace | T present and unfenced; ordinary recovery loads both origins and settles T; fill refused until a new retirement | Same |
| Retirement stopped after the replace, before or after its callback | No record; old site neither loaded nor ticketed; NONE fill admitted | Same |
| S′ saved, process stopped after its in-memory discard | S′ returns, inert under T; the proof fails until a save without S′ | Never two usable copies |
| Cross-Level NONE fill at K2, old source unloaded | Fill rolls forward; conserved; usable | Idle; conserved; usable |
| Cross-Level NONE fill at K9, old source unloaded | No replay; markers pruned; conserved; usable | Idle; conserved; usable |
| Control: today's named fill at K2 and K9 | Held (F1b); recorded, not counted as a pass | Held |

W125's cuts and the log checker stay unchanged.

## 4. Decisions for Root

- **D2-1.** Adopt (b2) as its own persistence slice, with a one-page plan and a read-only review before merge.
- **D2-2.** Keep fuel admission disabled until then; if it opens first, admit NONE guards only (d).
- **D2-3.** Confirm the launch finding in section 1, and that W125 reports "usable" by a governed launch.
- **D2-4.** Confirm the unsupported-storage hold, a deliberate difference from W98's R5(c).
- **D2-5.** Apply the same pre-admission retirement to cargo under D4.
