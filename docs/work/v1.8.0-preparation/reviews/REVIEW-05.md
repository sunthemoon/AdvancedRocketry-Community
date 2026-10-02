# C14 contract review, round 5: answers to round 4 (d939942..46d0a7f)

```yaml
reviewer: independent contract reviewer (read-only)
review_round: C14R5
date: 2026-10-03
repository: D:/GitHub/AdvancedRocketry-Community
branch: codex/v1.8.0-classic-content
commit_reviewed: 46d0a7f0
range: d939942e..46d0a7f0 (7 commits; review-04-dispositions.md)
verdicts:
  ADR-060: ACCEPT
  ADR-061: ACCEPT        # C14R5-L1 recommended, not blocking
  ADR-062: ACCEPT
  ADR-063: ACCEPT
finding_counts: {Critical: 0, High: 0, Medium: 0, Low: 1, Info: 4}
round_4_status: {fixed: 4 (H1, M1, L1, L2), info_answered: 5 (I1-I5)}
archived_reports: REVIEW-01..04 in docs/work/v1.8.0-preparation/reviews/ are byte-identical to my originals
```

## 1. Scope and commands

I reviewed every file changed in `d939942e..46d0a7f0`:
- the commits `56dc04a`, `e16f393`, `90d72db`, `0d45fdb`, `be9c78d`, `236c511` and `46d0a7f`;
- ADR-061 rev 5 and ADR-062 rev 5;
- the tool, the validator and both test files;
- the results, plan, allowlist and origin findings;
- the audit, the log and the dispositions.

The repository was read only. I did not read the untracked bundle and did not
run Gradle.

| # | Command / probe (`round-5/`) | Result |
|---|---|---|
| 1 | `git archive 46d0a7f0` → `t/`; upstream zip → `u/ar` | zip SHA-256 `40eb5d43…cca01d`; the archived REVIEW-01..04 equal my files byte for byte |
| 2 | `vanilla_derivation.py … --check --report-memory` | `up to date`, peak 350 MB, 3 min 38 s; results match the new pin `27149ba8…` |
| 3 | `inventory_v180_content.py --check` | `up to date` |
| 4 | `validate_v180_content_ledger.py` | PASS: 188 IMPORT, 157 REVIEW, 430 REGENERATE, 113 EXCLUDE, 10 IMPORTED (= 898). `--require-accepted` fails only on the PROPOSED ADRs |
| 5 | `python -m unittest tests.test_validate_v180_content_ledger tests.test_vanilla_derivation` | 65 tests OK (58 in round 4) |
| 6 | `p/relstats.py` on the committed results | 1,816 relations (none capped; at most 22 per file), 120 one-sided; read both ways, **no IMPORT file relates to a flagged file** |
| 7 | `p/sib4b.py`: every IMPORT PNG against every flagged PNG, both directions, in a fresh run | **no escapes**; the only relations are 12 to files excluded for content reasons (redesigned or deferred), which §4.8 does not count |
| 8 | `p/budget.py`: the model texture in the full reference set with the committed caps | now lists `warpcore.png` (HIT); in round 4 it needed the budget lifted |
| 9 | `p/crowd.py`, `p/crowd2.py`: vanilla textures in real legacy sheets, pooled vs vanilla-only | 30 of 30 HIT (unchanged) |
| 10 | `p/adversarial.py`, `adversarial2.py`, `realcrops.py`, `realfilt.py`, `realfilt2.py` | verdicts identical to round 4: 0 of 10 round-3 cases missed; the filtered cases still CLEAR, as now documented |
| 11 | `p/capprobe.py`: a casing tile shared by N legacy files and embedded in a model sheet | C14R5-I1 |
| 12 | `p/mutate5.py`, `p/mutate5b.py`: 32 mutations through the validator | 22 caught; 10 survive (5 by design, 1 baseline, 4 in C14R5-L1) — `logs/mutations5*.log` |

## 2. Status of the round-4 findings

| Finding | Status | Evidence |
|---|---|---|
| H1 one-sided, capped inheritance | **Fixed** | `MAX_RELATED` is gone. Legacy references verify their own two best alignments outside the 16-offset vanilla budget. The validator merges both directions, keeping the stronger level. `areagravitycontroller.png` (the model texture) now lists `warpcore.png` itself, with the committed caps (probe 8). Both files are REVIEW (#147, #123), the allowlist and digests are re-pinned, and the counts (188/157, "27 more") agree in ADR-061, ADR-062 and the audit. Probes 6 and 7 find no other escape. Mutations R5-e and R5-g are caught. Removing one side of a relation no longer works, because the other side still lists it: R4-h is now caught through `tabdata`. New tests cover a widely shared tile, more than eight relations, an off-grid embed and a one-sided relation |
| M1 filtered derivatives | **Fixed** | §4.8 lists the limits. `test_known_limits_stay_visible` asserts five filtered cases CLEAR, so any change in what the tool finds is visible. §4.5 requires an eye comparison of machine faces and casings, recorded in the batch record. My filtered probes give the same verdicts as in round 4 (minor test gap: C14R5-I2) |
| L1 records, releases | **Fixed**; residue C14R5-L1 | R4-e, R4-f, R5-i and R5-j (record is the findings file, or does not name the asset), R4-m and R5-b (`releases` missing or naming the wrong file) and R5-k (`releases` as a string) are caught. R4-l (a plan row citing a missing finding) is caught |
| L2 tab-template finding | **Fixed; attribution accepted** | `review_record` is the archived REVIEW-02.md, which names the file. The basis now gives 522 of 570 pixels at (1, 34) and (157, 34), the fill 456 / white 41 / dark 25 split and the no-vote mechanism, matching my `tabpos` log. "Rounds 2 to 4" is accurate |
| I1 two-colour policy | Accepted | — |
| I2 off-grid embed | Added | `test_vanilla_texture_off_the_block_grid_inside_a_sheet` |
| I3 best record | Fixed | Matches of the same level are now ordered only by measures that meet their colour rule. The tab template's best is now a real near-miss (recipe book, exact 0.38 over 3 colours), not a one-class mapping. Verdicts are unchanged (809/42/35/12). Small remainder in C14R5-I3 |
| I4 Linux memory | Deferred to the first CI run | accepted |
| I5 by-design survivors | Plan-row check added | residue (glob rows) in C14R5-L1 |

## 3. Findings

### Low

#### C14R5-L1 — Three small gaps in the new finding checks

1. **A HIT-level copy of an EXCLUDED source is released by a plain finding.**
   - The `releases` rule covers HIT-level relations to files whose *verdict*
     is HIT. It does not cover sources that an origin finding excludes as
     vanilla-derived.
   - Mutation R5-l: an owner's plain `CLEARED` finding on `tabwarp.png` (no
     `releases`), with the plan and allowlist set to IMPORT, passes. Yet
     `tabwarp.png` lists the tab template at HIT level and contains every
     non-dominant tab-template pixel, while the tab template itself can never
     be imported.
   - This is the near-copy argument of ADR-061 §4.2 again. Require `releases`
     for HIT-level relations to a file with an EXCLUDED finding too.
2. **An alias of the findings file passes as a record.**
   - `_record_names` compares the record path as a string.
     `docs/../docs/provenance/v1.8.0-origin-findings.json` (R5-c, as
     `confirmation_record`) and `docs/provenance/./v1.8.0-origin-findings.json`
     (R5-d, as `review_record`) both pass.
   - Compare resolved paths, and reject `..` segments.
3. **The plan-row citation check works only on a literal pattern.**
   - `cited` holds the patterns of rows whose reason starts "excluded by an
     origin finding", and the check is `asset in cited`.
   - Mutation R5-h: change the tab-template row to the glob
     `textures/gui/buttons/tabtemplat?.png` and delete the finding. It
     passes.
   - Check each asset that such a row matches (the asset's handling rule),
     not the pattern string.

None of these weakens a current file. Each needs a deliberate edit in a
reviewed commit.

### Info

- **C14R5-I1 — The posting cap still limits relations through a very common
  tile.**
  - Blocks that occur at more than 64 reference positions are dropped from
    the index. In `capprobe.py`, a model sheet that embeds a casing shared by
    80 legacy files still lists the casing file itself. It no longer lists the
    80 sharers (it does at N = 8, 16, 24 and 40).
  - Inheritance still flows through the casing file, and the real data peaks
    at 22 relations per file, so this has no effect today.
  - A note in §4.8 would make the limit explicit for future asset sets.
- **C14R5-I2 — The known-limits test covers the first limit, not the second.**
  ADR-061 §4.8 says the tests keep "the first two" limits as expected-CLEAR.
  The five cases are all of the first kind (filtered crop, sub-region or
  overlay). Add a whole-image blur of a low-contrast texture (cobblestone-like:
  rank 0.565 in my round-4 probe), or reword the sentence.
- **C14R5-I3 — The best record can still be a zero-strength match.** When no
  measure meets its colour rule, the recorded best is arbitrary. For example,
  `areagravitycontroller.png` shows `end_rod`, exact 0.0 over 0 colours.
  Omitting `best` in that case, or marking it, would avoid misleading readers.
- **C14R5-I4 — Survivors by design.**
  - R3-c and R5-f (an edit of the results with the digests re-pinned) are
    caught by the CI `--check`, not by the validator.
  - R4-c (token suffixes) and R4-d and R5-a (a correctly formed override or
    release) are left to commit review, as ADR-061 §4.2 and ADR-062 §7 state.
  - R3-e is now caught only as a side effect: my mutation drops the
    tab-template finding, which the plan-row check notices.

## 4. Verdicts

| ADR | Verdict | Conditions |
|---|---|---|
| ADR-060 | ACCEPT | unchanged |
| ADR-061 | ACCEPT | C14R5-L1 (three small validator checks) and I1–I3 recommended in the next revision; none blocks acceptance |
| ADR-062 | ACCEPT | plan, allowlist and counts are consistent (188/157/430/113/10) |
| ADR-063 | ACCEPT | unchanged since rev 3 |

The round-4 High and Medium are resolved:
- inheritance now reads every relation from both sides;
- my independent two-way run finds no IMPORT file related to a flagged one;
- the filtered-derivative blind spot is stated, pinned by a test and assigned
  to the record review.

What remains is small validator hardening around the new finding fields.

## 5. Artifacts

Kept: `round-5/p/` (the probes listed above, `mutate3lib.py`, `mutate4lib.py`,
`mutate5lib.py`, `relstats.py`, `capprobe.py`) and `round-5/logs/`.

Deleted: the exported tree, the upstream zip and its unpacked copy.

No Python process of mine is left running. The vanilla JARs were used read
only, in place, and were neither copied nor deleted.
