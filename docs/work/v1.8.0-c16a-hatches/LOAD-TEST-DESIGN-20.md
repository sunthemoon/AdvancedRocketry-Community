# C16a provisional LOAD binding: verification design (sub-scope 20)

Written design only. Nothing here was executed. Each oracle observes actual
owner, observation or save state, not a helper's verdict. Abbreviations and
U-numbers are those of LOAD-BINDING-20.md.

## 1. Existing-flow preservation

- The 7 `ClassicLoadJoinOwnershipTest` and 6
  `ClassicEmptyHatchRemovalAdmissionTest` declarations, and the existing five
  save-admission GameTests named in PROPOSAL-01 section 11, keep every
  assertion, deadline and budget.
- E equals O: on a chunk with no terminal publication, `matchesOutgoing`
  accepts and refuses the same fixture tags as at the base commit.
- Ordinary `AC.enterLoad`, `LW.hatch` and `rawMatches` never consult S: a held
  S at position A admits neither an unrelated new owner at B nor any owner
  through `enterLoad`.
- An ordinary retained owner saved while its guard is held still emits its
  checkpoint (RP L34-45).
- With an existing sticky reason for the chunk, a held S yields that reason
  from `GCS.beforeSave` and adds none.
- `stageRevalidation` and failed-publication retirement (H L97-101,
  L222-237) are unchanged for loaded owners.

## 2. Binding oracles (GameTest on an actually installed owner)

Tests cannot mint native operation authority. Until U1 and U2 are closed,
these remain designs that need the real entry path.

1. Without S, a newly placed owner is not admitted: `prepareLoaded` returns at
   L58 with no checkpoint and no `retained` row (current baseline).
2. After FIRST_LOAD: the ticket is closed, checkpoint and encoding are set,
   `OS.available()` is false and P is empty.
3. After SECOND_LOAD: the ticket identity differs from the first; P is bound;
   `retained` and E sizes are unchanged; `retainedOwnerMatches` is false and
   `enterEmptyHatchRemoval` is empty.
4. `setRemoved`, `onChunkUnloaded` or `reviveCaps` between or after the LOADs:
   the terminal never publishes; E and `retained` are unchanged.
5. COMMIT: exactly one E row and one `retained` row are added; the revision
   object is replaced; every other row is the same reference; the owner is
   available; a later ordinary `LW.hatch` admits it; the next ordinary save
   emits roots byte-equal to the encoding.
6. UNCHANGED: with the owner absent and the cell restored, S and P are cleared
   and E, `retained` and O are identical to entry.
7. Save during NATIVE to RETAINED_PROVISIONAL: the hatch entry carries no
   roots; `ChunkSaveDenials` gains no reason; the chunk stays unsaved through
   deferral; the first save after COMMIT succeeds.
8. A throwing callback: the same throwable instance reaches the caller; S is
   released exactly once; UNCHANGED only when every witness is equal.
9. Reentrant `prepareLoaded` in RETAINED_PROVISIONAL: no `stageRevalidation`,
   no pending capture.
10. E or `retained` at capacity: COMMIT is refused and old data is kept.

## 3. Native and lifecycle proof still required

- U2: the actual order of `setBlock`, BE installation and `onLoad` within
  `useItemOn` in survival and creative, on the packaged build.
- U1 and U3: the genuine target/provenance join and source, tool and drop
  outcomes on real player paths, including the same-context first-use
  counterexample in PLACEMENT-ENTRY-FACTS-01.
- U4: generated FULL and disk Proto-to-FULL origin.
- U6 and U7: other `saveAdditional` consumers; whether the throwing Save
  handler prevents the write; final save before `Provider.invalidate`; unload
  during an operation; two restarts with byte-verified roots and separate
  player and drop manifests.
- The version's full fixed-commit build, unit tests, `runData` twice without
  diff, unfiltered GameTests and dedicated-server evidence.
