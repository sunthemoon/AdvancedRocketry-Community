# TASK W402: C4's asset, screen, sound and access inventory, and the two resource packs (package K13)

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w402`, branch `codex/v1.8.0-w402` from the shared branch |
| Decision | Root decision R536 (QUEUE row W402) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md): package K13 in section 4.2, rows I-2, I-3, I-4, I-6 and J-7 in section 2, choices C14, C30, C31, C32 and C38 in section 3, and Root's changes in section 8; the step lists [CAMPAIGN-CHECKLISTS-I](records/CAMPAIGN-CHECKLISTS-I.md) (I02, I03, I04, I06, I07, I08, I11) and [CAMPAIGN-CHECKLISTS-J](records/CAMPAIGN-CHECKLISTS-J.md) (J12 to J16); records [W16](records/W16.md), [W87](records/W87.md), [W241](records/W241.md), [W244](records/W244.md), [W261](records/W261.md), [W265](records/W265.md); the network pin [network-protocols.txt](../../../src/test/resources/network-protocols.txt) |
| Outputs | The record `docs/work/v1.8.0-root-queue/records/W402.md`; a generator `scripts/campaign_prep/k13_inventory.py` with `scripts/campaign_prep/__init__.py`; a test `tests/test_campaign_prep_k13.py`; outside Git, `D:/GitHub/ARCE-Task-Evidence/final-campaign/659bbcb6a/fixtures/K13/` |
| Write scope | Those files only. No product code, no other script, test, record, checklist, ADR, register, status file or QUEUE. |
| Not allowed | Any JVM, Gradle, GameTest, server or client: the heavy-JVM lock belongs to candidate C4's native runs. JEI's category and recipe counts are read in the JEI cells during the campaign (C31), not here. |

## Inputs

C4's JAR: `D:/GitHub/ARCE-Task-Evidence/root-queue/C4/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`,
SHA-256 `c048743e61da4f939ce0492ca3ec73b7d69ef1634577bebc3279c91cf506cea1`. Check the hash first, then read it
as a ZIP from where it is or from a copy in a Temp folder; never change it. The source of the same build is this
branch at `659bbcb6a` or later; read `src/main` for registrations and handlers. Where the JAR and the source differ,
the JAR wins and the record says so.

## What to build

1. **Asset inventory** (CSV files under `fixtures/K13/`): every texture, model (JSON and OBJ), blockstate, item
   model, particle, sound file, `sounds.json` event and language key in the JAR, with its path, size and SHA-256;
   the language files' key sets for `en_us` and `zh_cn`, with keys missing from either side listed.
2. **Classification by C30**: mark as high-risk every OBJ model, every multiblock's model and texture set, every
   armour model and texture, and every asset W244, W261 or W265 corrected; all high-risk assets are checked in the
   campaign. Group the other block and item models by model family (the parent model they share) and pick one per
   family, by a fixed rule written in the record (for example the first path in sorted order). Output one CSV with
   the class and the reason.
3. **Screens**: every menu type and screen the build registers, with the block, item or command that opens it, the
   step list rows that cover it, and the ones no row covers. Every screen is opened once per language in the
   campaign (C30), so the list is complete.
4. **Sound producers (C38)**: every sound event and every code site that plays it (block, entity, item, screen or
   command), with the event's subtitle key and whether `zh_cn` has it. Compare with W16 and W87 and list what was
   added or removed since.
5. **Access matrix (C14, I-6)**: for every menu and every client-to-server channel in the network pin, who may open
   or use it (owner, member, anyone, operator level), the distance check and its value, the loaded-chunk check, and
   the refusal message or log line, each with the source location. This is what the K18 intent tool and checklist I's
   I11 test against.
6. **Two resource packs** (ZIPs with their SHA-256, built by the generator from the JAR, never from upstream or other
   art): a missing-resource pack for I07 that replaces a stated small set of the mod's textures and one model with
   files that do not resolve, so that the campaign sees the game's fallback and the mod's handling; and a long-text
   pack for J12 to J16 that overrides the mod's `en_us` and `zh_cn` strings with longer variants of the same text (a
   fixed rule, for example each value repeated to at least twice its length, capped at 256 characters), so that the
   campaign checks overflow on every screen. The record states the rules and lists what each pack overrides.
7. **Checks**: unit tests for the generator on a small fake JAR (inventory rows, the family pick rule, both packs'
   contents and their determinism: the same input gives byte-identical ZIPs). Run
   `python -B -m unittest discover -s tests -p test_campaign_prep_k13.py -v` and
   `python -B scripts/validate_repository.py`, which must end with `0 failed`. Write `SHA256SUMS.txt` for
   `fixtures/K13/`.
8. **The record**: the counts per class, the screens no row covers, the sound and access findings that need Root
   (for example a channel without a distance check), what changed since W16, W87 and W241, and the commands and
   results. Link only to tracked files on the shared branch; use a code span for anything else.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
