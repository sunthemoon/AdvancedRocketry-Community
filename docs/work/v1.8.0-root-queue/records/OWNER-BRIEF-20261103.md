# Owner brief, 2026-11-03: the decisions only the owner can take for v1.8

Root: Claude. This brief collects every open v1.8 decision that the standing delegation of 2026-11-01
([OWNER-DECISIONS-20261101](OWNER-DECISIONS-20261101.md)) leaves with the owner. It is a summary for one sitting; the
linked records hold the evidence. Nothing here is decided, and no Gate, version or release is marked passed.

## Where v1.8 stands

- **Content.** Every ledger unit that does not wait on the owner is implemented. 16 units are still PLANNED: the five
  loader variants (they wait for admission, item 4), the gas charge pad (O1 is merged; item 6) and the ten sounds
  (item 2).
- **Assets.** No IMPORT file remains: 89 legacy files are imported, 270 excluded and 444 regenerated. 95 files are
  still REVIEW (item 1).
- **Bay safe exit.** S2 and its real-client landing fix are merged and natively evidenced (W242, W254, W257, W262,
  W266); the two-client V2 evidence is part of the campaign (item 5).
- **Hosted CI.** The last shared runs passed all 1,655 GameTests and the governance checks (`CURRENT_VERSION.md`).

## 1. The 95 REVIEW files (W268, ADR-062 revision 7 draft)

The proposal is `docs/work/v1.8.0-w268-review-closure/PROPOSAL.md` on branch `codex/v1.8.0-w268` at `b5639aee1`
(reviewed by a Claude session, rechecked, and corrected twice; see the W268 row in [QUEUE](../QUEUE.md)). Accepting a group only stops the old
files from shipping; it does not clear their authorship or approve the replacements (that is D7). Each question says
what accepting gives up:

| Group | Files | Question |
|---|---:|---|
| G1 | 34 | Keep the modern machine designs, giving up the old faces and their running-state faces (modern machines show none)? Some old faces belonged to blocks with no modern version; a future block of that kind would need NEW art. |
| G2 | 11 | Keep the NEW item icons, including the distinct iron saw blade and pressure tiers, giving up the old icon artwork? |
| G3 | 17 | Keep the code-drawn and native-widget screens, giving up the legacy button and tab sheets and the warning artwork? |
| G4 | 8 | Keep the NEW suit, which cannot be dyed? The 1.12 leather-armour suit could be dyed; accepting G4 accepts that loss. |
| G5 | 4 | Keep the NEW static enriched-lava sprites, giving up the old artwork and its animation? |
| G6 | 4 | Keep the project casing and NEW motor tiers, giving up the AR-bundled casing and motor exports without resolving LibVulpes authorship? |
| G7 | 10 | Keep the ten legacy sounds under REVIEW until your listen (item 2), granting no permission to ship them? (`lathe.ogg` and `rollingmachine.ogg` are one recording.) |
| G8 | 7 | Keep the modern beacon, field and projector, and gravity and vacuum-laser replacements, giving up the old textures and the force-field animation (the modern field is static)? |

Root recommends accepting G1 to G6 and G8, and G7 as written. If you accept, Root records your words, adopts revision
7 (the asset plan, allowlist, origin findings in your name, the fixture updates and the validator guard the proposal
lists), and asks a fresh session to review the adoption.

## 2. D5: listen to the ten NEW sounds

ADR-066 section 7.1. The ten `sound_event` ledger rows (air hiss, laser gun, rocket engine, electric shock, gravity
controller, laser drill, lathe, large machine, railgun and rolling machine) stay PLANNED until you listen in game and
say, per sound, keep or change. The legacy OGGs (G7) can then be excluded or investigated one by one.

## 3. D7: the human provenance and licence review

30 of the 35 v1.8 provenance records under `docs/provenance/` say `PENDING_HUMAN_REVIEW`: the upstream imports (K1 to
K6, W244, W246, W249, W255, W256, W258, W260, W261 and W265) and the NEW art records. ADR-061 section 4.5 keeps them
pending until you set APPROVED after a visual and licence check; the W263 inventory section 6 lists what each check
compares (for example machine faces against vanilla piston, furnace, dispenser and dropper art). Root can prepare
side-by-side comparison sheets per record if that helps; say which records first.

## 4. Risk decisions that wait for evidence (W203-Q4 and D14)

Not decidable yet. Before each admission switch (fuel, each cargo kind, the bay) is turned on, you accept a precisely
listed remaining scope of R-035, R-040, R-042, R-043 and R-044 for that interaction, or keep it disabled (W203 plan
section 4, question 4). D14 is the release-level disposition. Both need the campaign's evidence first; Root will bring
each one with its evidence.

## 5. The final campaign

Yours to schedule: real-GPU V1 checks, two-client V2 sessions (the bay exit's checklist is
[W254-V2-CHECKLIST](W254-V2-CHECKLIST.md)), the full S1 restart campaign, the docs/17 reference-node workload and the
joint four-hour soak. For information: under the delegation, Root chose the V2 gate (R298): a campaign-only release-test
gate variant that admits two named real clients on a disposable, marked world. Production bay admission stays off
everywhere; on that campaign world the full release-test gate, and with it the bay's gated admission (D0), opens for
the two named clients, as it already does for the test facility's players (corrected after W269's review, W269-R1).
W269 built it; its review fixes are in progress, and it runs no campaign.

## 6. The gas charge pad's automatic charging: v1.8 or v1.9?

On 2026-10-31 you chose O1 for the single-player host (PAD-D4: "O1：单人房主用有限罐交换（推荐）"), with dedicated
servers and LAN guests keeping W174's automatic charging. O1 is merged (W226): the pad fills a canister and any player,
on any server, refills a suit tank from it. The automatic route is not built. W174's leaves L0 to L4 are about 2,500 to
3,500 lines of persistence-sensitive code (a receipt in the player's file, a readback through W80's reader, shared
proof admission with the rocket escrow) plus a native crash-cut runner, each leaf with an independent review: several
worker sessions and a new S2 campaign row. Until one of the two happens, the pad's ledger row (`block:oxygenCharger`)
stays PLANNED.

- **A (Root recommends):** v1.8 ships O1 for every player and the pad's ledger row closes on O1's evidence; the
  automatic route moves to v1.9 with its design (W174) kept as is.
- **B:** build the automatic route in v1.8, as PAD-D4 says today; v1.8's completion waits for it and its S2 evidence.

## 7. Smaller open question

Whether sleeping off-world should save a planet respawn point when `allowPlanetRespawn` is on (today it keeps the
existing point). Only a change of that behaviour needs an answer; no answer keeps it.

## How to answer

In the Root session, in your own words, for example: "G1-G6、G8 接受，G7 按写的；充气台选 A", or any change per item.
Root records the exact words in an owner-decision record with the date and channel, as before. Items 2, 3 and 5 need
your time rather than a choice; say when, and Root prepares the material.
