# C14 contract review, round 3: answers to round 2 (90f1e9e..20ed0e7)

```yaml
reviewer: independent contract reviewer (read-only)
review_round: C14R3
date: 2026-10-02
repository: D:/GitHub/AdvancedRocketry-Community
branch: codex/v1.8.0-classic-content
commit_reviewed: 20ed0e74
range: 90f1e9ee..20ed0e74 (19 commits; review-02-dispositions.md)
verdicts:
  ADR-060: ACCEPT
  ADR-061: ACCEPT WITH REQUIRED CHANGES   # C14R3-H1 and C14R3-M1 block acceptance
  ADR-062: ACCEPT WITH REQUIRED CHANGES   # the plan/allowlist part of C14R3-H1
  ADR-063: ACCEPT                         # C14R3-L2 to be fixed, no re-review needed
finding_counts: {Critical: 0, High: 1, Medium: 1, Low: 2, Info: 3}
round_2_status: {fixed: 12, partially_fixed: 2 (H1, L1), info_answered: 5}
```

## 1. Scope and commands

Reviewed every file changed in `90f1e9ee..20ed0e74`: ADR-061 rev 3, ADR-062
rev 3, ADR-063 rev 2, the rewritten `tools/audit/vanilla_derivation.py`, the
new `tools/audit/fetch_vanilla_clients.py`, the validator, both test files,
the regenerated results, plan, allowlist, ledger, inventory, audit, log,
matrix and CI workflow. The repository was read only; the untracked bundle was
not read; no Gradle.

| # | Command / probe (`round-3/`) | Result |
|---|---|---|
| 1 | `git archive 20ed0e74` → `t/`; upstream zip → `u/ar` | zip SHA-256 `40eb5d43…cca01d` |
| 2 | Vanilla JARs from the coordinator's Temp folder (read only): 1.12.2 SHA-1 `0f275bc1…`, 1.20.1 `client.jar` SHA-1 `0c3ec587…`, SHA-256 `56b71336…` | equal to Mojang's SHA-1 and the ADR-061 pins |
| 3 | `vanilla_derivation.py --upstream u/ar --vanilla 1.12.2=… --vanilla 1.20.1=… --check` | `up to date`, 8 min 56 s; peak working set **9.8 GB** (PowerShell `PeakWorkingSet64`) — `logs/derivation_check.log` |
| 4 | `inventory_v180_content.py --check` | `up to date` |
| 5 | `validate_v180_content_ledger.py` | PASS: 653 units, 52/133/292/93/25/58; assets 211 IMPORT, 139 REVIEW, 430 REGENERATE, 108 EXCLUDE, 10 IMPORTED |
| 6 | `python -m unittest tests.test_validate_v180_content_ledger tests.test_vanilla_derivation` | 50 tests OK |
| 7 | `p/adversarial.py`: ten derivation kinds through the tool's own `analyse_images` | **8 of 10 missed** — `logs/adversarial.log` |
| 8 | `p/anchor.py`: the tab buttons against the vanilla tab sheets | `tabtemplate` HIT; `tabwarp`, `tabplanet_hover`, `tabguidance` CLEAR — `logs/anchor.log` |
| 9 | `p/siblings.py`: IMPORT files measured against legacy files whose verdict is HIT or SUSPECT (ADR-061 §4.8 inheritance) | 12 HIT-level, 14 SUSPECT-level — `logs/siblings.log` |
| 10 | `p/deriv3.py`: verdicts for the round-2 files and the eight cleared files | as listed in §2 |
| 11 | `p/mutate3.py`: 15 mutations of copies through the validator | 7 caught, 8 survive (one is the baseline H1 state) — `logs/mutations3.log` |

I also stopped a stray `python -` REPL that my round-2 probe had left running.

## 2. Status of the round-2 findings

| Finding | Status | Evidence |
|---|---|---|
| H1 derivation false negatives | **Partially fixed; reopened as C14R3-H1** | `plank_blue` HIT/EXCLUDE (rank 1.0 vs big-oak planks), `tabtemplate` HIT/EXCLUDE (sub 0.81), oxygen sheets and `.mcmeta` REVIEW; gates removed; orientations, ×2/×4, frames and a sub-image search added; calibration tests added. Edited crops, embedded sprites and scaled crops are still missed, and §4.8 inheritance is not computed |
| M1 results binding | Fixed; new CI-capacity issue C14R3-M1 | ADR-061 pins the results and both JAR digests; the validator checks pins, schema, thresholds, counts and each verdict against its measures (mutations R2-a, R2-k, R3-d caught); the CI step fetches the clients and runs `--check` |
| M2 16x set | Fixed | the #1809/#1811/#1889/#1976 files I sampled (`atmospheredetector`, `controlpanel`, `warpcore`, `pressuretank0`, `forcefieldprojector`, `satellitepowersource0`, `laserbottom`) are REVIEW; the v0.1.0 three are named |
| M3 iridium | Fixed | Moon and Mars iridium (1 of 16, y 4–40); C17a depends on C15b; ledger rows moved |
| M4 Tau Ceti access | Fixed | data-satellite targets, interstellar warp, three in-system routes, optional content, A0/A1 tests |
| M5 Moon terrain/landing | Fixed (player-impact wording C14R3-L2) | surface y 12–36, nothing above y 63; fixed pads and the y 79 platform unchanged; seams quantified; the `level.dat` precedence claim matches my `javap` of `WorldDimensions.bake` |
| M6 generation mechanisms | Fixed | single-piece structures (clipped per chunk) for craters, volcanoes and geodes; features within 12 blocks; bound tests |
| L1 validator residue | Mostly fixed; residue C14R3-L1 | evidence folder pattern, owner batch, model-texture order and reviewer role are enforced |
| L2 counts | Fixed | 653 / 135 (132 names) / 292 / 211 + 139 agree across ADR-061, ADR-062, audit, log and validator; results 794/39/53/12 = 898 |
| L3 wording | Fixed | audit §3.1 press, ADR-062 §3, ledger and matrix landing-float text |
| L4 materials | Fixed | ADR-063 table equals LibVulpes flags 349–359 and AR 897–898; nothing added or dropped |
| L5 ledger coverage | Fixed | copper/iridium/Luna rows to C15b; JEI category in ADR-063 §9 |
| L6 DataGen layout | Fixed | ADR-063 §8: `src/generated/v1.8/resources`, superseded paths, build exclusions, old biomes kept |
| L7 legacy facts | Fixed | crater radius to about 91 (capped 48), ore sets, mushroom light 0, `PistonEvent.Pre` exception (ADR-061 §6) |
| I1–I5 | Answered | HIT appeal path, gravity split (653 units), 16x v0.1.0 note, rutile tags, 32-variant limit |

The eight round-1 SUSPECTs that are now CLEAR (`fuelingmachine`,
`guidancecomputer`, `lens1`, `monitorfrontright`, `monitorside`,
`starshipcontrolpanel`, `userinterface`, `hardsquare`): their best matches
share a single colour (`colours = 1`: a glazed-terracotta grey, banner-mask
black, a debug-texture grey), which is no evidence of copying. I accept the
clearing.

## 3. Findings

### High

#### C14R3-H1 — Edited crops still pass, and the §4.8 inheritance rule is not computed: eight tab buttons and several overlays are IMPORT

The sub-image search anchors on the candidate's three rarest colours and
requires all three, byte-equal, in the vanilla sheet
(`SubImageProbe` lines 337–346; `analyse_images` lines 446–447). Any edit that
adds a colour the sheet lacks therefore moves the anchors onto the new pixels,
and the search skips the sheet. There is also:
- no search of vanilla sprites inside a larger legacy sheet;
- no scaled sub-image search;
- no tolerance for near-exact pixels.

The calibration set has none of these cases.

Probe 7 (the tool's own functions, a 128×128 vanilla sheet and a 16×16
vanilla texture) misses 8 of 10 plausible derivations:

| Case | Verdict |
|---|---|
| crop with **one** pixel repainted in a new colour | CLEAR |
| crop with a 4×4 icon painted on it | CLEAR |
| crop upscaled ×2 | CLEAR |
| vanilla 16×16 texture inside a 64×64 legacy sheet | CLEAR |
| the same inside a 256×256 legacy sheet | CLEAR |
| crop with every channel +1 | CLEAR |
| recoloured crop (documented limit) | CLEAR |
| 70×70 crop (over the 64-px limit) | CLEAR |
| texture shifted by 1 px | SUSPECT |
| 40 % of a texture copied | HIT |

On the committed plan (probes 8, 9):
- `textures/gui/buttons/tabwarp.png`, `tabwarp_hover.png`, `tabplanet.png`,
  `tabplanet_hover.png`, `tabguidance.png` and `tabguidance_hover.png`
  (IMPORT rules #246–#248, C17b/C16a) are `tabtemplate.png`, the excluded
  vanilla tab, with an icon drawn on it. Every non-dominant `tabtemplate`
  pixel is byte-equal in each (overlap 1.000 over 3 colours, IoU 1.0); I
  checked this visually. The tool scores them CLEAR because their rarest
  colours are the icon's.
- `buttonautoeject_hover.png` and `buttonautoeject_pressed.png` (IMPORT #248)
  are the tab shape recoloured (0.797 of the template's pixels on 2 colours).
  Their unrecoloured sibling `buttonautoeject.png` is SUSPECT/REVIEW.
- ADR-061 §4.8 says "a derivative of an `EXCLUDE` or `REVIEW` file inherits
  that handling", but nothing compares legacy files with each other. Measured
  with the tool's own `compare()`:
  - `spacehelmet_overlay.png` shares 33 % of exact pixels (3 colours) with
    `space_helmet.png` (HIT);
  - `spaceboots_overlay.png` shares 48 % (4 colours) with `space_boots.png`
    (SUSPECT);
  - `panelsideworkstation.png` shares 76 % (57 colours) with `panelside.png`
    (REVIEW);
  - `vacuumlaserfront.png` shares 38 % (13 colours, mirrored) with
    `forcefieldprojectorfront.png` (REVIEW);
  - `tabdata_hover.png` has rank 0.92 against `tabdata.png` (REVIEW).

  All five are IMPORT.

The validator passes all of these. The new calibration test's "false-negative
rate 0" holds only on its own set.

Required change:
1. Make the sub-image search robust:
   - draw anchors only from colours present in the sheet, and try several
     anchor sets (or a row-hash search);
   - keep the miss budget;
   - search vanilla sprites and tiles inside legacy sheets up to 512 px (the
     reverse direction);
   - search at ×2/×4;
   - accept near-equal pixels (for example ±2 per channel) as a separate,
     SUSPECT-level measure;
   - lift or tile the 64-px candidate limit.
2. Add the legacy-to-legacy inheritance pass that §4.8 already requires: every
   IMPORT candidate is measured against every legacy file that is HIT, SUSPECT
   or REVIEW for origin, and takes that handling on a SUSPECT-level match.
3. Add my ten cases (`round-3/p/adversarial.py`) and the inheritance cases to
   `tests/test_vanilla_derivation.py`. Report the false-negative rate per case
   kind, and state the documented limits in §4.8.
4. Regenerate the results. Move the eight tab buttons to EXCLUDE (they contain
   the excluded vanilla tab) or REVIEW. Move the five overlays/variants above
   to REVIEW. Revise the allowlist and re-pin both digests.

### Medium

#### C14R3-M1 — The new CI step will not fit a private-repository runner

`vanilla_derivation.py --check` peaked at a **9.8 GB** working set here (the
coordinator measured about 7 GB) and runs about 9 minutes. The repository is
private (ADR-004). GitHub's standard hosted Linux runner for private
repositories has 2 vCPU and 7 GB RAM. The step added to `repository-docs.yml`
is therefore likely to be OOM-killed, and it is what makes the round-2 M1
binding hold. Without it, a self-consistent edit is caught only by review:
mutation R3-c (CLEAR without measures, both digests re-pinned) passes the
validator.

The memory comes from keeping every vanilla image as Python tuples, luminance
lists and per-colour bitmaps at once, plus eight orientations per candidate.

Required change:
- Bring the peak well under 7 GB: stream the JARs, group by size, keep pixels
  as `bytes`/`array`, release the decoded lists, and build orientations lazily.
  Alternatively, document a larger runner and its cost.
- Record the measured peak on Linux in the M1 evidence.
- Keep the step mandatory, not `continue-on-error`.

### Low

#### C14R3-L1 — Validator residue (surviving mutations)

- R3-a/R3-b: a delivery row passes when any file in a batch evidence folder
  contains the unit ID as a **substring**. `block:lathe` passes with
  `machine_casing` as target and a file that says `block:latheX`. Match the ID
  as a token and check that the target ID is the unit's delivered ID in that
  file.
- R3-e/R3-f: `reviewer` and `confirmed_by` are free strings. Writing the
  owner's name clears a SUSPECT, and any `confirmed_by` string overturns a HIT
  (the allowlist re-pin is the only other gate). State that commit authorship
  and review are the control, or require a signed or linked record.
- R2-h, R2-j, R2-m remain semantic (an unrelated earlier-batch owner unit; an
  unrelated existing sound as IMPLEMENTED target; moving a unit to REJECTED
  citing ADR-062 without a §4 row). The optional group key was declined, which
  is acceptable if ADR-062 §7's revision rule is enforced by review.

#### C14R3-L2 — ADR-063 does not state the Moon-arrival player impact

The new Moon surface lies at y 12–36 by design, while arrivals stay at the
fixed pads at y 80 or above with no ground support. Every Moon arrival
therefore hangs 44–68 blocks above the regolith, where legacy rockets landed on
the surface. ADR-063 §5 calls arrivals "unaffected" and §5 "Seams" omits this.
State the height of the drop and how players get down (for example the y 79
platform, or the C18a gravity-scaled fall). Alternatively, name a later
landing-rule ADR that lands rockets on the Moon heightmap once existing
arrivals no longer need the fixed y 80.

### Info

- **C14R3-I1** — The validator alone accepts a PNG entry set to CLEAR without
  measures once both digests are re-pinned (R3-c). The CI `--check` is the
  real guard, which is why C14R3-M1 matters.
- **C14R3-I2** — Some SUSPECT matches cannot be origins: `panelside.png`
  (2017–2020) matches the 1.20 decorated-pot side. Recording the first vanilla
  version of each match, or comparing 1.20.1-only textures only with files
  changed after their release, would cut such false positives. Inheritance
  from them (C14R3-H1 item 2) then stays meaningful.
- **C14R3-I3** — `fetch_vanilla_clients.py` downloads Mojang's client JARs on
  every CI run (about 33 MB, outside the tree, hash-pinned). That is the
  ForgeGradle practice; keep the files out of caches and artifacts, as the
  script already does.

## 4. Verdicts

| ADR | Verdict | Conditions |
|---|---|---|
| ADR-060 | ACCEPT | unchanged since revision 2 |
| ADR-061 | ACCEPT WITH REQUIRED CHANGES | C14R3-H1 (tool, inheritance, calibration) and C14R3-M1 before acceptance; L1 |
| ADR-062 | ACCEPT WITH REQUIRED CHANGES | the plan and allowlist moves of C14R3-H1 (eight tab buttons, five overlays/variants) with the digest re-pin |
| ADR-063 | ACCEPT | C14R3-L2 to be fixed in its next revision; M3–M6 and L4–L7 of round 2 are resolved |

The round-2 contract fixes are sound. The ledger, the bindings, the 16x
review, the iridium source, the Tau Ceti path and the Moon terrain bound all
check out against the legacy code and the modern landing selector. The
remaining High is again in the derivation instrument: it now finds whole-image
copies, low-palette icons and clean crops, but an edited crop slips through
because the edit itself becomes the search anchor.

## 5. Artifacts

Kept: `round-3/p/` (`adversarial.py`, `anchor.py`, `siblings.py`,
`deriv3.py`, `mutate3.py`) and `round-3/logs/`. Deleted: the exported tree,
the upstream zip and unpacked copy, and the rendered legacy images. The vanilla
JARs used read-only live in the coordinator's Temp folder
(`arce-v180-c14-189c61a748584cab97c5881f8bdc1251/vanilla/`). I neither copied
nor deleted them.
