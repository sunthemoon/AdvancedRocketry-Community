# C14 contract review, round 4: answers to round 3 (20ed0e7..d939942)

```yaml
reviewer: independent contract reviewer (read-only)
review_round: C14R4
date: 2026-10-03
repository: D:/GitHub/AdvancedRocketry-Community
branch: codex/v1.8.0-classic-content
commit_reviewed: d939942e
range: 20ed0e74..d939942e (6 commits; review-03-dispositions.md)
verdicts:
  ADR-060: ACCEPT
  ADR-061: ACCEPT WITH REQUIRED CHANGES   # C14R4-H1 (inheritance) and C14R4-M1 (stated limits)
  ADR-062: ACCEPT WITH REQUIRED CHANGES   # the two files of C14R4-H1 move to REVIEW (counts, allowlist re-pin)
  ADR-063: ACCEPT                         # C14R3-L2 fixed
finding_counts: {Critical: 0, High: 1, Medium: 1, Low: 2, Info: 5}
round_3_status: {fixed: 3 (M1, L1, L2), partially_fixed: 1 (H1), info_answered: 3 (I1, I2, I3)}
tab_template_attribution: no objection in principle; see C14R4-L2 for the record and wording
```

## 1. Scope and commands

I reviewed every file changed in `20ed0e74..d939942e`:
- the six commits `0c8b8e9`, `3c75c4d`, `f799dab`, `96c1e51`, `cfddf36` and `d939942`;
- ADR-061 rev 4, ADR-062 rev 4 and ADR-063 rev 3;
- the schema-3 `tools/audit/vanilla_derivation.py` and the validator;
- both test files, the results, plan, allowlist and origin findings;
- the audit, the log, the dispositions and the CI workflow.

The repository was read only. I did not read the untracked bundle and did not
run Gradle.

| # | Command / probe (`round-4/`) | Result |
|---|---|---|
| 1 | `git archive d939942e` → `t/`; upstream zip → `u/ar` | zip SHA-256 `40eb5d43…cca01d`; results `23f8af00…` and allowlist `2790bb18…` equal the ADR-061 and ADR-062 pins |
| 2 | Vanilla JARs from the coordinator's Temp folder (read only) | as in round 3 (pins equal) |
| 3 | `vanilla_derivation.py … --check --report-memory` | `up to date`, **peak 350 MB, 3 min 26 s** — `logs/derivation_check.log` |
| 4 | `inventory_v180_content.py --check` | `up to date` |
| 5 | `validate_v180_content_ledger.py` | PASS: 653 units, 52/133/292/93/25/58; assets 190 IMPORT, 155 REVIEW, 430 REGENERATE, 113 EXCLUDE, 10 IMPORTED (= 898). `--require-accepted` fails only on the PROPOSED ADRs (1 + 510 rows) |
| 6 | `python -m unittest tests.test_validate_v180_content_ledger tests.test_vanilla_derivation` | 58 tests OK (50 in round 3) |
| 7 | `p/adversarial.py`: my ten round-3 cases | **0 of 10 missed** — `logs/adversarial.log` |
| 8 | `p/adversarial2.py`: 12 new synthetic cases | 4 undocumented misses (C14R4-M1) — `logs/adversarial2.log` |
| 9 | `p/realcrops.py`: exact and edited crops of real vanilla GUI sprites, plus the real tab template | all found; tab template CLEAR — `logs/realcrops.log` |
| 10 | `p/realfilt.py`, `p/realfilt2.py`: filtered real 1.12.2 block textures, alone and as machine faces | C14R4-M1 — `logs/realfilt*.log` |
| 11 | `p/crowd.py`, `p/crowd2.py`: vanilla textures embedded in real legacy sheets, aligned and not aligned to the 4-px grid; vanilla-only vs pooled with all legacy references | **30 of 30 HIT** both ways — `logs/crowd*.log` |
| 12 | `p/rel.py`, `p/tabpos.py`, `p/rankband.py`: handling of the named files, the tab-template offsets, the rank band 0.60–0.75 | as cited below |
| 13 | `p/sib4b.py`: inheritance measured in both directions, without the cap; `p/pair4.py`, `p/budget.py` | **2 IMPORT files escape inheritance** (C14R4-H1) — `logs/sib4.log`, `pair4.log`, `budget.log` |
| 14 | `p/mutate4.py`: 20 mutations of copies through the validator | 10 caught, 10 survive (6 by design, 1 baseline, 3 real: C14R4-L1) — `logs/mutations4.log` |
| 15 | `p/tokens.py`: unit IDs found inside other unit IDs by the whole-token rule | 0 of 653 — `logs/tokens.log` |

## 2. Status of the round-3 findings

| Finding | Status | Evidence |
|---|---|---|
| H1 edited crops, inheritance | **Partially fixed**; residue in C14R4-H1 and C14R4-M1 | **Tool:** the block search finds all ten round-3 cases (probe 7). It also finds exact and edited crops of real vanilla GUI sprites (probe 9) and vanilla textures inside real legacy sheets (probe 11). **Inheritance:** computed and enforced. All tab buttons I named, both auto-eject variants, the overlays, `panelsideworkstation` and `vacuumlaserfront` are REVIEW #117–#146 (25 moved from IMPORT, 5 already REVIEW; the audit's "25 more" is right). The tab template is EXCLUDE #116 through the origin finding. **Residue:** relations recorded on one side only, or cut at eight, escape (C14R4-H1); filtered derivatives are an undocumented limit (C14R4-M1) |
| M1 CI capacity | **Fixed** | Reproduced: 350 MB, 3 min 26 s, `up to date`. The CI step is mandatory (no `continue-on-error`) and prints `ru_maxrss` |
| L1 validator residue | **Fixed**; new residue in C14R4-L1 | R3-b (substring) and R4-a/R4-b (missing or longer modern ID) are caught. R3-f is caught: an override now needs a `confirmation_record`. R3-a is caught because the target ID must be listed. R3-e and R4-d survive as ADR-061 §4.2 now states (names are text; commit review is the control) |
| L2 Moon arrival | **Fixed** | ADR-063 §5: 80 − 36 = 44 and 80 − 12 = 68. The y 79 platform exists (`SafeCelestialTravel`: feet y 80, floor y 79, radius 2). The gravity-scaled fall is planned in C18a (`PlanetEventHandler.fallEvent`). The landing-rule ADR is named |
| I1 validator-only edits | Answered | the CI `--check` now fits; R3-c and R4-h still pass the validator alone, as expected |
| I2 impossible origins | Implemented | a `CLEARED` source passes nothing on (validator and test `test_a_cleared_source_passes_nothing_on`); see C14R4-L1 for one side effect |
| I3 JAR download | Accepted | unchanged |

## 3. Findings

### High

#### C14R4-H1 — Inheritance misses relations recorded only on the other file's side or cut at eight; two IMPORT files should be REVIEW

ADR-061 §4.8 says a file related to a file with a HIT, SUSPECT or UNSUPPORTED
verdict, a REVIEW handling or an EXCLUDED finding is at most REVIEW, and that
the validator enforces it. The validator reads only the importing file's own
`related` list (`validate_assets`, lines 591–600). The tool:
- keeps at most eight relations per file (`MAX_RELATED`, `_related_entries`);
- records a pair only from the side whose search finds it.

The measures are not symmetric: the dominant colour that is left out is the
reference's, and a large candidate's region search has a budget of 16 offsets
per variant. My probe 13 measured every IMPORT PNG against every flagged PNG,
in both directions and without the cap. Two IMPORT files escape:

| IMPORT file | Related flagged file | Measure | Why the validator misses it |
|---|---|---|---|
| `textures/models/areagravitycontroller.png` (128×64, IMPORT #301, C18d) | `textures/blocks/warpcore.png` (REVIEW #59: the #1811 16x set, which credits an unmerged third-party pull request, §4.9) | `warpcore` is at offset (48, 16) of the model texture: region exact 0.7686 over 45 colours (**HIT**). Measured alone the other way: exact 0.8596 (HIT) | Recorded only in `warpcore`'s list. The model texture's own list is empty |
| `textures/blocks/monitorrear.png` (IMPORT #215) | `textures/blocks/crystallizer_active.png` (REVIEW #7, #1976 set) | whole image, overlap 0.1523 over 3 colours (**SUSPECT**), from `crystallizer_active`'s side only | `crystallizer_active`'s list already holds eight HIT relations (the cap), so this one is cut |

`p/budget.py` shows the cause for the first file. In the full reference set
(vanilla + all legacy), the model texture's search does not find `warpcore`:
- with the committed caps: not found;
- with the posting cap lifted: not found;
- with the 16-offsets-per-variant budget lifted as well: `warpcore` HIT.

The AR casing is shared by dozens of legacy machine textures, and their
high-vote alignments use up the budget.

Scale: 81 assets fill the eight-relation cap, and 214 of the 1,194 recorded
relations appear on one side only.

The vanilla verdicts are not affected. A vanilla texture embedded in a real
legacy sheet stays HIT when the legacy references are pooled in (probe 11, 30
of 30, aligned and unaligned). The crowding happens only between legacy files
that share a tile.

Required change:
1. In the validator (and wherever the plan applies §4.8), treat a relation as
   symmetric. A file inherits from B when its own list names B, or when B's
   list names it.
2. In the tool, make what inheritance reads complete:
   - record every SUSPECT-or-above legacy relation, uncapped, in a separate
     list or in full (`MAX_RELATED` may limit display only);
   - give legacy references their own verification budget, for example per
     reference, so a widely shared casing cannot use up the 16 offsets.
3. Add tests for:
   - a relation visible from one side only (the dominant-colour asymmetry);
   - a file with more than eight relations;
   - a large legacy sheet that contains a widely shared legacy tile.
4. Regenerate the results and move both files to REVIEW. Update the ADR-061 and
   ADR-062 counts (188 IMPORT, 157 REVIEW if nothing else moves) and re-pin
   both digests.

### Medium

#### C14R4-M1 — Filtered derivatives are an unstated limit of the schema-3 check

The block search needs byte-equal or near-equal 4×4 blocks: `near` allows ±2
per channel, and `mapped` needs a consistent colour mapping over at least six
classes. Whole-image `rank` tolerates filters, but only when most of the image
is unchanged. The project's own history has this pattern: issue #1527's
author ran the vanilla piston through filters for the plate press faces. Those
faces are found only because they are whole images (rank ≥ 0.98).

Probes 8 and 10 used real 1.12.2 block textures (`piston_side`,
`furnace_front_off`, `iron_block`, `cobblestone`, `dispenser_front_horizontal`).

Found:
- whole-image ±6/±12 noise, darkening, channel swaps and 8×8 or 10×10 centre
  replacements: HIT or SUSPECT;
- a ×2 upscale followed by noise or blur: HIT or SUSPECT;
- whole-image 3×3 blur: SUSPECT, except cobblestone, which is CLEAR (rank
  0.565).

Missed (CLEAR) and not named in ADR-061 §4.8 "Known limits":

| Derivation | Verdict |
|---|---|
| ±6 noise **plus** an 8×8 centre overlay (a machine face on a filtered casing) | CLEAR in 4 of 4 (rank 0.570–0.748) |
| 3×3 blur plus a 6×6 overlay | CLEAR in 4 of 4 (rank 0.698–0.736) |
| 12×12 crop with ±6 noise | CLEAR in 5 of 5 |
| synthetic ±6-noise crop, 3×3-blurred crop, noisy crop inside a 128 px sheet | CLEAR |
| a GUI element of four colour classes with its fill recoloured, when the fill is not the reference's dominant colour (exact 0.31, mapped 1.0 over 4 classes < 6) | CLEAR |

The last case matters little: such elements are simple bevelled rectangles.

Lowering the rank threshold is not a remedy. Twenty-one CLEAR files already
have a best whole-image match with IoU ≥ 0.85 and rank 0.60–0.75 (probe 12).
Nearly all of those matches are against 1.20-only textures (paintings, the
barrel top, the respawn anchor), which cannot be origins. That is the noise
floor of a maximum over thousands of references.

Required change:
1. Name these cases in ADR-061 §4.8 "Known limits":
   - pixels filtered beyond ±2 per channel (noise, blur, sharpen) in a crop,
     in a sub-region, or combined with an overlay of about a quarter of the
     image;
   - blur of low-contrast textures;
   - recolours of elements with fewer than six colour classes.
2. Add them to `tests/test_vanilla_derivation.py` as expected-CLEAR "known
   limit" cases, so that any change in what the tool finds is visible.
3. In the §4.5 record review, compare every machine face and casing by eye
   with its vanilla counterparts (piston, furnace, dispenser, dropper, iron
   block, stone, cobblestone).

Optional: a windowed rank measure (for example 8×8 windows at the identity
alignment of same-size pairs), calibrated against the committed set before it
gets a threshold.

### Low

#### C14R4-L1 — Records can point at the findings file, and a plain finding releases a HIT-level copy of a HIT file

- **R4-e:** an override passes with
  `"confirmation_record": "docs/provenance/v1.8.0-origin-findings.json"`. The
  findings file names the asset and the confirmer, because the finding itself
  does.
- **R4-f:** an independent reviewer's `CLEARED` finding passes with the same
  file as its `review_record`.

  With both, the "committed record" requirement adds nothing to the finding.
  Require the record to be another file, for example under `docs/work/` or
  `docs/reviews/`, and never the findings file.
- **R4-m:** `textures/gui/warning.png` is REVIEW #137 by inheritance: it is
  related at HIT level (exact 1.0) to `progressbars.png`, which is HIT because
  the sheet contains the vanilla obsidian texture. An owner's **plain**
  `CLEARED` finding on `warning.png` makes it importable. That is reasonable
  here, since the sprite is another region of the sheet. The same path,
  however, releases a near-identical copy of a HIT file, while the HIT file
  itself would need the override and a second confirmation. Choose one:
  - require the override form when the relation is HIT-level and the source's
    verdict is HIT;
  - require the finding to name the relation it releases and say why, for
    example `"releases": ["textures/gui/progressbars/progressbars.png"]` with
    the region.

#### C14R4-L2 — The tab-template finding: attribution and wording

**I do not object to my evidence being cited, and I agree with the decision
EXCLUDED.** The tab template is the vanilla creative tab with its bevel lines
redrawn.

The `reviewer` field, however, reads as the person who made the decision, and
I did not write or sign this finding. Choose one:
- name the owner as `reviewer` and cite my reports in `basis` (C14R2-H1,
  C14R3-H1, archived with the preparation evidence);
- keep my attribution and add a `review_record` that links the archived
  round-2 and round-3 reports, so the attribution can be checked like a
  `CLEARED` one.

Corrections to `basis` (probe 12, `logs/tabpos.log`):
- **The offset (1, 34) is correct.** 522 of 570 opaque pixels are equal there,
  and also at (157, 34), which my round-2 log recorded; the sheet repeats the
  tab. No change is needed.
- **"The equal pixels are the tab fill and one bevel edge" understates the
  match.** The equal pixels are the fill (456), all 41 white bevel pixels and
  25 of the 32 dark ones. Most of the 48 differing pixels are template fill
  where vanilla has a bevel line.
- **"Scores it SUSPECT or CLEAR" should read CLEAR.** The tool verifies no
  alignment: its best record is an unrelated horse-armour match with exact
  0.0. The reason is that every 4×4 block of three or more colours touches a
  moved bevel line, so no block votes for the alignment. The dominant-colour
  rule only matters after an alignment is found. The new "outline redrawn
  around a vanilla fill" limit in §4.8 describes the case well enough.

### Info

- **C14R4-I1 — The two-colour policy is acceptable.**
  - An exact copy of a two-colour vanilla sprite is still flagged: the
    furnace progress arrow, exact 1.0 over 2 colours, is SUSPECT (probe 9).
  - The IMPORT files cleared by the change had weak round-2 matches, for
    example `smallairlockdoor_lower` overlap 0.155 with the stone slab top,
    `girder` 0.244 with `debug2`, and the `jetpack` model texture 0.106 with
    the sun.
  - The others on the audit list are REVIEW or EXCLUDE for other reasons:
    beacon, laser etcher, monitors, railgun, satellite bay, chestplate overlay,
    rear transceiver, drill.
  - What now passes is a two-colour vanilla sprite edited to below 75 % equal
    pixels. Such sprites have little originality, so I accept the trade.
- **C14R4-I2 — The tool works on real vanilla art.**
  - Exact crops of the creative tab, an inventory slot, a widget button and
    the furnace flame are HIT; the tab with one pixel changed is HIT; the tab
    with its fill recoloured is HIT.
  - Vanilla textures embedded in real 128×64, 256×256 and 512×512 legacy
    sheets are HIT at grid-aligned and unaligned offsets.
  - The calibration set embeds only at grid-aligned offsets; consider adding
    one unaligned case.
- **C14R4-I3 — The `best` record of a CLEAR asset often shows a meaningless
  match.**
  - `_strength` ranks `mapped / 2`, so a record with `mapped` 1.0 over one or
    two classes and `exact` 0.0 can outrank a real but weaker two-colour match.
    Examples: `beacon`, `tabtemplate` and `jetpack` show horse-armour or flower
    matches.
  - The two-colour matches that the audit says were "checked visually" are
    therefore not in the results.
  - Prefer the strongest exact/overlap match with at least two colours for the
    record.
- **C14R4-I4 — M1 on Linux.** Windows is reproduced at 350 MB. The first CI run
  should record its Linux `ru_maxrss` and duration in the batch evidence, as
  ADR-061 says.
- **C14R4-I5 — Validator survivors that are by design.**
  - R4-c: the token rule accepts `block:lathe/old` and text such as "not
    delivered: block:lathe". No unit ID collides (probe 15), and ADR-062 §7
    leaves meaning to review.
  - R4-l: deleting the tab-template finding passes the validator while the plan
    keeps EXCLUDE with a reason that cites the missing finding. Optional: check
    that a plan row citing an origin finding has one.
  - R3-c / R4-h: a self-consistent edit with both digests re-pinned is caught
    by the CI `--check`, not by the validator.
  - R4-n: an owner's `CLEARED` finding on `buttonautoeject` does not release
    `tabwarp`, which still inherits from `tabdata` (REVIEW). The defence in
    depth works.

## 4. Verdicts

| ADR | Verdict | Conditions |
|---|---|---|
| ADR-060 | ACCEPT | unchanged |
| ADR-061 | ACCEPT WITH REQUIRED CHANGES | C14R4-H1: symmetric, complete inheritance in the tool and the validator, with tests. C14R4-M1: the filtered cases named under "Known limits", with expected-CLEAR calibration cases and the record-review check. C14R4-L1 and L2 recommended |
| ADR-062 | ACCEPT WITH REQUIRED CHANGES | after the C14R4-H1 regeneration: `areagravitycontroller.png` (model texture) and `monitorrear.png` to REVIEW, counts updated, allowlist re-pinned |
| ADR-063 | ACCEPT | C14R3-L2 resolved in rev 3; nothing else changed |

Round 3's High is largely resolved. The block search finds edited, scaled and
embedded crops on synthetic and real vanilla art. The check runs in 0.35 GB.
Inheritance is computed and enforced, and every file I named is under review.

The remaining High is mechanical: inheritance reads a capped, one-sided list,
so two IMPORT files that contain or share pixels with third-party-credited
textures slip through. The Medium asks only that the filtered-derivative blind
spot be stated and covered by the record review.

## 5. Artifacts

Kept:
- `round-4/p/`: `adversarial.py`, `adversarial2.py`, `realcrops.py`,
  `realfilt.py`, `realfilt2.py`, `crowd.py`, `crowd2.py`, `rel.py`,
  `tabpos.py`, `rankdist.py`, `rankband.py`, `sib4.py`, `sib4b.py`,
  `pair4.py`, `budget.py`, `mutate3.py`, `mutate3lib.py`, `mutate4.py`,
  `tokens.py`, plus the superseded `anchor.py` and `siblings.py`, whose
  schema-2 APIs no longer exist;
- `round-4/logs/`.

Deleted: the exported tree, the upstream zip and its unpacked copy.

One of my probe commands started a stray `python -` REPL, which wrote a 320 MB
file into `p/`. I stopped the process and deleted the file. No Python process
of mine is left running.

The vanilla JARs live in the coordinator's Temp folder and were used read
only. I neither copied nor deleted them.
