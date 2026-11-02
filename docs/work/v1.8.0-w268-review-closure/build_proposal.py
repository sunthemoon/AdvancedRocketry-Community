"""Build W268's review evidence and inert drafts from the assigned Git snapshot.

Only writes beside this script. Upstream is read through Git at the manifest pin.
No asset is imported and no central repository file is changed.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import csv
import fnmatch
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tarfile

BASE = "a65465138adca914d61d492d940fca015b86c9c6"
PIN = "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
PREFIX = "src/main/resources/assets/advancedrocketry/"
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
JAVA = "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/"
GEN = "src/generated/v1.8/resources/assets/advancedrocketrycommunity/"
PLAN = "docs/work/v1.8.0-asset-plan.csv"
ALLOW = "docs/work/v1.8.0-asset-import-allowlist.txt"
FINDINGS = "docs/provenance/v1.8.0-origin-findings.json"
ADR = "docs/decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md"
GROUPS = {
    "G1": ("Unbound machine and controller faces", "EXCLUDE",
           "Retain the named live NEW/static or imported formed presentation instead of these old faces?"),
    "G2": ("Unbound item icons", "EXCLUDE",
           "Retain the named NEW item icons, including distinct iron saw blade and pressure tiers?"),
    "G3": ("GUI button sheets and warning", "EXCLUDE",
           "Retain code-drawn/native-widget screens rather than these legacy sheets?"),
    "G4": ("Space suit base and overlay textures", "EXCLUDE",
           "Retain the NEW coordinated suit with fixed overlays merged into its base, not the old suit pixels?"),
    "G5": ("Legacy lava images and animation metadata", "EXCLUDE",
           "Retain NEW enriched-lava sprites and leave ordinary vanilla lava as resource references, excluding these old sheets?"),
    "G6": ("LibVulpes casing and motor exports", "EXCLUDE",
           "Exclude these AR-bundled shared-origin files in favour of project casing and NEW motor tiers, leaving authorship unresolved?"),
    "G7": ("Legacy sounds", "REVIEW",
           "After D5's listen, retain the NEW sounds and exclude each legacy OGG, or request a specific origin investigation before any import?"),
    "G8": ("Beacon and force field", "EXCLUDE",
           "Retain the live NEW beacon/projector/field presentation instead of the six old textures and sidecar?"),
}


def git(*args: str, root: Path = ROOT) -> bytes:
    return subprocess.check_output(["git", "-c", "core.autocrlf=false", "-C", str(root), *args])


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def rows(data: bytes) -> list[dict]:
    return list(csv.DictReader(io.StringIO(data.decode("utf-8-sig"))))


def dump(name: str, value) -> None:
    (HERE / name).write_text(json.dumps(value, indent=2, ensure_ascii=True) + "\n", encoding="utf-8", newline="\n")


def first(asset: str, plan: list[dict]) -> dict:
    return next(r for r in plan if fnmatch.fnmatchcase(asset, r["pattern"]))


def group(asset: str) -> str:
    if asset.startswith("sounds/"):
        return "G7"
    if asset in ("textures/blocks/machinegeneric.png", "models/motor.obj") or asset.startswith("models/item/models/motor."):
        return "G6"
    if asset.startswith("textures/blocks/fluid/lava_"):
        return "G5"
    if asset.startswith("textures/gui/"):
        return "G3"
    if asset.startswith("textures/armor/") or asset.startswith("textures/items/space"):
        return "G4"
    if asset in ("textures/blocks/beacon.png", "textures/models/beacon.png") or asset.startswith("textures/blocks/forcefield"):
        return "G8"
    if asset.startswith("textures/items/"):
        return "G2"
    assert asset.startswith("textures/blocks/"), asset
    return "G1"


def category(path: str) -> str:
    if path.startswith("src/"):
        if "/datagen/" in path:
            return "data generator"
        if "/test/" in path or "GameTest" in path:
            return "test"
        if path.endswith(".java"):
            return "Java (candidate; not necessarily a binding)"
        return "resource/model/MTL/blockstate"
    if path.startswith("tools/import/"):
        return "import plan/tool"
    if path.startswith("docs/provenance/"):
        return "provenance record"
    return "inventory/documentation/test/tool"


def scan(assets: list[str], files: dict[str, bytes]) -> dict[str, list[dict]]:
    """All matching lines, no truncation. Bare stems deliberately over-approximate.

    Case-insensitive whole lexical tokens also find extensionless IDs and Java
    resource strings. A match is evidence to inspect, not proof of a live binding.
    No upstream source text is reproduced in the report.
    """
    tokens = defaultdict(set)
    for asset in assets:
        base = asset.removesuffix(".mcmeta")
        name = Path(base).name
        stem = Path(base).stem
        for token in (asset, base, name, stem, base.removeprefix("textures/").removesuffix(".png")):
            tokens[token.lower()].add(asset)
    pattern = re.compile(r"(?<![a-z0-9_])(?:" + "|".join(re.escape(t) for t in sorted(tokens, key=lambda x: (-len(x), x))) + r")(?![a-z0-9_])", re.I)
    found = defaultdict(list)
    for path, data in sorted(files.items()):
        if b"\0" in data:
            continue
        try:
            text = data.decode("utf-8-sig")
        except UnicodeDecodeError:
            continue
        for number, line in enumerate(text.splitlines(), 1):
            by_asset = defaultdict(set)
            for match in pattern.finditer(line):
                for asset in tokens[match.group().lower()]:
                    by_asset[asset].add(match.group())
            for asset, hits in sorted(by_asset.items()):
                found[asset].append(dict(file=path, line=number, matched=sorted(hits), category=category(path)))
    return found


def resources(identifier: str, kind: str, files: dict[str, bytes]) -> list[str]:
    suffix = f"/models/{kind}/{identifier}.json"
    choices = [p for p in files if p.startswith("src/generated/") and p.endswith(suffix)]
    modern = [p for p in choices if p.startswith("src/generated/v1.8/")]
    return sorted(modern or choices)


def replacement(asset: str, owners: list[dict], files: dict[str, bytes]) -> dict:
    g = group(asset)
    targets = sorted({x for owner in owners for x in re.findall(r"advancedrocketrycommunity:([a-z0-9_]+)", owner["target"])})
    if asset == "textures/blocks/gaschargertop.png":
        targets = ["gas_charge_pad"]
    if asset == "textures/blocks/satellitebay.png":
        targets = ["rocket_satellite_bay"]
    if asset == "textures/blocks/machinegeneric.png":
        targets = ["machine_casing", "endgame_casing"]
    models = sorted({p for target in targets for kind in ("block", "item") for p in resources(target, kind, files)})
    textures = set()
    for path in models:
        model = json.loads(files[path])
        for ref in model.get("textures", {}).values():
            if ref.startswith("advancedrocketrycommunity:"):
                local = ref.split(":", 1)[1]
                choices = [p for p in files if p.endswith("/assets/advancedrocketrycommunity/textures/" + local + ".png")]
                textures.update(choices)
    extra = []
    classes = []
    if g == "G1":
        provider = {
            "atmosphere_detector": "V180LifeSupportData", "co2_scrubber": "V180LifeSupportData", "pipe_seal": "V180LifeSupportData",
            "station_altitude_controller": "V180StationControlData", "station_gravity_controller": "V180StationControlData", "station_orientation_controller": "V180StationControlData",
            "warp_controller": "V180StationControlData", "suit_workstation": "V180EquipmentData", "gas_charge_pad": "V180GasChargePadData",
            "rocket_satellite_bay": "V180SatelliteBayData", "solar_generator": "V180SolarData", "nuclear_core": "V180PropulsionData",
            "rocket_monitoring_station": "V180RocketMonitorData", "crystallizer": "V180C16cMachineData", "cutting_machine": "V180ArcCuttingData",
            "precision_laser_etcher": "V180C16cMachineData", "quartz_crucible": "V180C16cMachineData",
            "fuel_loader": "FlightBlockStateProvider", "guidance_computer": "RocketBlockStateProvider",
        }
        classes = [provider[t] for t in targets if t in provider]
        formed = {"precision_laser_etcher": "precisionlaseretcher", "crystallizer": "crystallizer", "cutting_machine": "cuttingmachine"}
        for t in targets:
            if t in formed:
                classes += ["ObjModelFixture", "ClassicObjModelRenderer"]
                extra += [p for p in files if p.endswith("/models/" + formed[t] + ".obj")]
        note = "Keep the live presentation named here. Static faces do not promise the legacy active/multipart face set; formed geometry is a separate binding."
    elif g == "G2":
        classes = ["V180EquipmentData"]
        if asset == "textures/items/atmanalyzer.png":
            classes = ["V180AtmosphereAnalyzerData"]
        elif asset == "textures/items/beaconfinder.png":
            classes = ["V180BeaconData", "BeaconFinderItem"]
        elif asset == "textures/items/sawbladeiron.png":
            classes = ["V180ArcCuttingArt", "V180ArcCuttingData"]
        note = "Keep the named NEW icon(s); the saw_blade block item separately reuses W256's imported block mesh."
    elif g == "G3":
        classes = ["ClassicControllerScreen", "StationControllerScreen", "SuitWorkstationScreen"]
        note = "No live legacy-sheet consumer or one-to-one bitmap replacement. Modern screens draw fills, text and native widgets; code files below are the replacement."
    elif g == "G4":
        classes = ["V180SpaceSuitArt", "V180ClosureModels", "SpaceSuitArmorItem"]
        extra += [GEN + "textures/models/armor/space_suit_layer_1.png", GEN + "textures/models/armor/space_suit_layer_2.png"]
        note = "NEW coordinated suit, item icons and worn layers; old overlays are merged into NEW bases, not separate layers. The old AUDIT-01 iron-armour placeholder observation is superseded."
    elif g == "G5":
        classes = ["ClassicFluidType", "ClassicFluids", "V180FluidArt", "V180FluidData"]
        extra += [GEN + "textures/block/fluid/enriched_lava_" + face + ".png" for face in ("still", "flow")]
        extra += [GEN + "models/block/enriched_lava.json"]
        note = "Legacy enriched lava's old sheets are replaced by NEW enriched_lava_still/flow sprites via ClassicFluidType. The NEW sprites are static (no copied legacy animation sidecars). Ordinary vanilla lava is unchanged; no vanilla bitmap is copied."
    elif g == "G6":
        classes = ["MotorArt", "MotorDataFiles", "ModBlockStateProvider", "SatelliteBlockStateProvider", "MachineCasingBlock"]
        note = "Keep project casing and NEW four-tier motor models. Exclusion would NOT resolve the shared LibVulpes authorship chain; reopening import needs ADR-061 section 4.1 per-file history and authorship evidence."
    elif g == "G7":
        sound_map = dict(airhissloop="air_hiss_loop", basiclasergun="basic_laser_gun", combustionrocket="combustion_rocket", electricshocksmall="electric_shock_small", gravityohhh="gravity_controller", laserdrill="laser_drill", lathe="lathe", machinelarge="machine_large", railgunbang="railgun_bang", rollingmachine="rolling_machine")
        extra += [GEN + "sounds/" + sound_map[Path(asset).stem] + ".ogg", GEN + "sounds.json"]
        classes = ["V180SoundArt", "V180SoundDefinitions", "ClassicSound", "ClassicSoundClient", "ModSounds"]
        note = "Keep REVIEW until owner D5 listens to the ten NEW events in their actual uses, records accept/replace choices and explicitly decides per-legacy-OGG exclusion. No OGG origin clearance is inferred from the PNG detector's CLEAR."
    else:
        if "forcefield" in asset:
            targets = ["force_field", "force_field_projector"]
            models = sorted({p for t in targets for k in ("block", "item") for p in resources(t, k, files)})
            extra += [GEN + "textures/block/force_field.png", GEN + "textures/block/force_field_projector_front.png", GEN + "textures/block/force_field_projector_side.png"]
            classes = ["V180ForceFieldData", "ForceFieldBlock", "ForceFieldProjectorBlock"]
        else:
            classes = ["V180BeaconData", "PlanetaryBeaconBlock", "ClassicObjModelRenderer", "ObjModelFixture"]
            extra += [p for p in files if p.endswith(("/models/beacon.obj", "/textures/models/beacon.png"))]
        note = "Keep NEW field/projector sprites and live beacon controller plus imported K3 beacon mesh using its NEW texture, not the legacy beacon PNG. This is not pixel-identical legacy appearance or origin clearance."
    class_paths = sorted(p for p in files if p.startswith(JAVA) and Path(p).stem in classes)
    assert class_paths, (asset, classes)
    assert all(p in files for p in extra), (asset, extra)
    return dict(targets=targets, models=models, textures=sorted(textures), additional_files=sorted(set(extra)), classes=class_paths, note=note)


def consumers(asset: str, references: list[dict], old: dict[str, bytes]) -> dict:
    """Select actual path-bearing consumers from the broad lexical candidate scan."""
    base = asset.removesuffix(".mcmeta")
    aliases = {base.lower()}
    if base.startswith("textures/"):
        aliases.add(base.removeprefix("textures/").removesuffix(".png").lower())
    else:
        aliases.add(Path(base).name.lower())
    direct = []
    for ref in references:
        p = ref["file"]
        if p == PREFIX + asset or not p.endswith((".java", ".json", ".mtl", ".obj")):
            continue
        line = old[p].decode("utf-8-sig").splitlines()[ref["line"] - 1].lower()
        if any(alias in line for alias in aliases):
            direct.append(ref)
    dynamic = ""
    if asset.startswith("textures/armor/"):
        dynamic = "ItemSpaceArmor.getArmorTexture at armor/ItemSpaceArmor.java:160-175 selects the literal base/overlay paths by armour slot; legacy layer numbers do not equal the modern 1/2 route."
    if asset.endswith(".mcmeta"):
        dynamic = "Minecraft animation metadata is implicitly paired with " + base + "; consumers of that sprite also select its sidecar."
    if asset.startswith("sounds/"):
        stem = Path(asset).stem
        sound_json = PREFIX + "sounds.json"
        sound_lines = old[sound_json].decode().splitlines()
        direct = [dict(file=sound_json, line=n, matched=[stem], category="resource/model/MTL/blockstate") for n, line in enumerate(sound_lines, 1) if stem.lower() in line.lower()]
        dynamic = "sounds.json maps the legacy event to this OGG; util/AudioRegistry.java defines the event handles. Java gameplay uses are retained in upstream_references (case-insensitive event tokens)."
    if asset.startswith("models/") and "motor" in asset:
        dynamic = "No AR Java/blockstate/model-JSON route to either motor OBJ is found at this pin. The item OBJ's mtllib motor.mtl is an internal export dependency, not a registered consumer. The LibVulpes motor unit's behaviour does not establish an AR resource binding or approve its source."
    if not direct and not dynamic:
        dynamic = "No literal source-path/texture-resource consumer was found in pinned AR Java or resources; the inventory's ledger owners are not proof of a binding."
    return dict(direct=direct, note=dynamic)


def build(upstream: Path) -> None:
    manifest = {r["source_path"].removeprefix(PREFIX): r for r in rows(git("show", BASE + ":legacy-manifest/assets.csv"))}
    plan = rows(git("show", BASE + ":" + PLAN))
    ledger = {r["unit_id"]: r for r in rows(git("show", BASE + ":docs/work/v1.8.0-content-ledger.csv"))}
    review = sorted(a for a in manifest if first(a, plan)["handling"] == "REVIEW")
    assert len(review) == 95
    tracked = git("ls-tree", "-r", "--name-only", BASE).decode().splitlines()
    # Current source tree is unchanged from BASE; read it without a per-file git process.
    assert not git("diff", BASE, "--", "src", "tools", "scripts", "tests", "legacy-manifest").strip()
    files = {p: (ROOT / p).read_bytes() for p in tracked if (ROOT / p).is_file()}
    # Pin-specific Git archive is read in memory; upstream working-tree state is irrelevant.
    archive = git("archive", "--format=tar", PIN, "src", root=upstream)
    with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
        old = {m.name: tar.extractfile(m).read() for m in tar.getmembers() if m.isfile()}
    for asset in review:
        assert digest(old[PREFIX + asset]) == manifest[asset]["sha256"], asset
    cache = HERE / "REVIEW-EVIDENCE.json"
    previous = json.loads(cache.read_bytes()) if cache.exists() else {}
    # The immutable baseline and pin make the prior exhaustive scan reusable.
    if previous.get("source_commit") == BASE and previous.get("upstream_commit") == PIN:
        current_refs = {e["asset"]: e["current_references"] for e in previous["files"]}
        old_refs = {e["asset"]: e["upstream_references"] for e in previous["files"]}
    else:
        current_refs = scan(review, files)
        old_refs = scan(review, old)
    derivation = json.loads(files["docs/work/v1.8.0-vanilla-derivation.json"])
    verdicts = {r["asset"]: r for r in derivation["assets"]}
    allow = dict(line.split("\t") for line in files[ALLOW].decode().splitlines())
    hashes = defaultdict(list)
    for path, data in files.items():
        if path.startswith("src/"):
            hashes[digest(data)].append(path)
    evidence = []
    for n, asset in enumerate(review, 1):
        rule = first(asset, plan)
        owners = [ledger[u] for u in rule["units"].split() if u != "-"]
        evidence.append(dict(id=f"R{n:03}", asset=asset, source_sha256=manifest[asset]["sha256"],
                             source_commit=PIN, derivation=verdicts[asset], allowlist_ceiling=allow[asset],
                             rule=rule, owners=owners, group=group(asset), proposed=GROUPS[group(asset)][1],
                             replacement=replacement(asset, owners, files), source_hash_copies=hashes[manifest[asset]["sha256"]],
                             current_references=current_refs[asset], upstream_references=old_refs[asset],
                             upstream_consumers=consumers(asset, old_refs[asset], old),
                             all_matching_plan_rules=[r for r in plan if fnmatch.fnmatchcase(asset, r["pattern"])]))
        assert not evidence[-1]["source_hash_copies"], asset
    dump("REVIEW-EVIDENCE.json", dict(source_commit=BASE, upstream_commit=PIN,
         scan_scope="Every tracked file at BASE; case-insensitive lexical path/name/stem matches. Binary files excluded from text scan but all tracked src bytes checked by SHA-256. Matches are candidates, not proof of binding. Dynamic family evidence is explained in PROPOSAL.md.", files=evidence))
    changes = []
    for rule in plan:
        effective = [e for e in evidence if e["rule"]["pattern"] == rule["pattern"]]
        if effective and effective[0]["proposed"] == "EXCLUDE":
            assert all(e["proposed"] == "EXCLUDE" for e in effective)
            new = dict(rule, handling="EXCLUDE", reason="excluded by an origin finding (DRAFT W268 / ADR-062 revision 7; pending owner; not authorship clearance)")
            changes.append(dict(pattern=rule["pattern"], before=rule, after=new, assets=[e["asset"] for e in effective]))
    changed = {c["pattern"]: c["after"] for c in changes}
    after_plan = [changed.get(r["pattern"], r) for r in plan]
    excluded = {e["asset"] for e in evidence if e["proposed"] == "EXCLUDE"}
    assert len(excluded) == 85
    buf = io.StringIO()
    writer = csv.DictWriter(buf, fieldnames=list(plan[0]), lineterminator="\n")
    writer.writeheader()
    writer.writerows(after_plan)
    (HERE / "asset-plan.csv.draft").write_bytes(buf.getvalue().encode())
    allow_data = "".join(line + "\n" for line in files[ALLOW].decode().splitlines() if line.split("\t")[0] not in excluded).encode()
    (HERE / "allowlist.txt.draft").write_bytes(allow_data)
    findings = json.loads(files[FINDINGS])
    for e in evidence:
        if e["proposed"] != "EXCLUDE":
            continue
        findings["findings"].append(dict(asset=e["asset"], decision="EXCLUDED", reviewer="<OWNER_PENDING_DECISION>",
            reviewed_at="2026-11-03", draft_status="PENDING_OWNER_NOT_A_REVIEW",
            review_record=ADR, basis=f"DRAFT W268 {e['id']} / {e['group']}; not an owner decision. Date is preparation date only, not an attestation. Source SHA-256 {e['source_sha256']}. Proposed non-shipment of the old file; does not clear authorship, approve NEW art, or satisfy D7/V1. " + e["replacement"]["note"]))
    dump("origin-findings.json.draft", findings)
    before = Counter(first(a, plan)["handling"] for a in manifest)
    after = Counter(first(a, after_plan)["handling"] for a in manifest)
    dump("changes.json", dict(source_commit=BASE, before_counts=before, after_counts=after,
         allowlist_before_sha256=digest(files[ALLOW]), allowlist_after_sha256=digest(allow_data),
         source_sha256={p: digest(files[p]) for p in (PLAN, ALLOW, FINDINGS, ADR)},
         asset_plan=changes, retained_review=[e["asset"] for e in evidence if e["proposed"] == "REVIEW"]))
    lines = proposal(evidence, before, after, digest(allow_data), len(changes))
    (HERE / "PROPOSAL.md").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(dict(files=len(evidence), groups=Counter(e["group"] for e in evidence), changes=len(changes), before=before, after=after, allowlist_sha256=digest(allow_data)), indent=2))


def proposal(evidence: list[dict], before: Counter, after: Counter, pin: str, rules: int) -> list[str]:
    lines = ["# W268 - REVIEW closure proposal", "", "**DRAFT - PENDING OWNER DECISION. Nothing here is applied or accepted.**", "",
             f"Baseline: `{BASE}`; original AR 1.12 source pin: `{PIN}`.",
             "Write scope: this directory and `docs/work/v1.8.0-root-queue/records/W268.md`; external W268 logs/scratch copies only. No source, resources, tracked plan, allowlist, origin findings, ADR, ledger or status changes.", "",
             "## Decision groups", "", "85 EXCLUDE proposals and 10 named REVIEW holds account for all 95 files. No IMPORT is proposed: there is no cleared legacy source that a live consumer needs instead of its existing replacement.", "",
             "| Group | Files | Proposed handling | One owner question |", "|---|---:|---|---|"]
    for group_id, (title, handling, question) in GROUPS.items():
        lines.append(f"| {group_id}: {title} | {sum(e['group'] == group_id for e in evidence)} | {handling} | {question} |")
    lines += ["", "## Owner and provenance boundaries", "",
              "Accepting G1-G6/G8 would exclude the legacy files only; it would not clear their authorship or approve their replacements. G6 explicitly leaves the LibVulpes chain unresolved; a future import requires the per-file pinned blob/hash, history, derivation and authorship finding specified by ADR-061 section 4.1, not a category approval. NEW art, original imports and their pending batch records still require D7. D5's in-use listen is not performed here. Real-GPU V1 and applicable V2 remain separate.",
              "For G7, the owner must record which NEW sounds are accepted or need changing after the listen, and whether each of the ten old OGGs can be excluded. If any old sound is wanted, keep its REVIEW ceiling until source/author/format evidence supports a finding. A plain CLEARED finding may be recorded by the owner or a named independent reviewer with a committed record naming the file; inherited HIT relations require releases, and a HIT override requires the owner and second-person confirmation (ADR-061 section 4.2/4.8). This proposal supplies none of those clearances.", "",
              "### Draft handling, not a signature", "",
              "The origin draft deliberately keeps reviewer `<OWNER_PENDING_DECISION>` and `draft_status: PENDING_OWNER_NOT_A_REVIEW`. Its ISO date is the preparation date only, not a completed human review. The existing validator checks schema, not authenticity of an EXCLUDED reviewer; a structural PASS is not approval. Scratch ADR-062 has `status: PROPOSED`, `revision: 7`; revision 6's historical acceptance text is not revision 7 acceptance. Therefore accepted/closure checks must report their actual pending-ADR errors, never simulate owner approval.", "",
              "## Revision 7 (draft; pending owner's decision)", "",
              "### Published language and appearance boundaries", "",
              "Retain the named modern machine faces, tier icons, code/native-widget GUI, NEW coordinated suit and enriched-lava sprites, project casing and NEW motor tiers, and live beacon/field presentation. Ordinary vanilla lava remains unchanged. Active faces, suit overlays, lava animation and GUI sheets are not promised pixel-identical to the old artwork. No gameplay owner, inventory, recipe or registered ID is removed. G7 is intentionally not terminal yet.", "",
              "### Current handling and exact proposed files", "",
              "| Handling | Before | After (draft) |", "|---|---:|---:|"]
    for h in ("IMPORT", "REVIEW", "REGENERATE", "EXCLUDE", "IMPORTED"):
        lines.append(f"| {h} | {before[h]} | {after[h]} |")
    lines += ["", f"Total 898 files. Exactly 85 REVIEW files in {rules} existing rules become EXCLUDE; no row order, plan or owner is changed. Ten legacy OGGs remain REVIEW. The allowlist drops exactly 85 REVIEW entries, 247 -> 162; all other ceilings are unchanged. New draft allowlist SHA-256: `{pin}` (UTF-8, LF, final newline). These counts apply only to the assigned shared head, not later W265/W267 integration.", "",
              "W265/W267 have no REVIEW-row overlap. The script refuses changed central input hashes; Root must rebase these semantic changes onto any later shared head and rerun the 898-file audit, rather than overwrite their rows. Their exclusion/import decisions and the 35 retained NEW textures are not altered or approved here.", "",
              "Each row below has an exact source identity and an evidence ID into `REVIEW-EVIDENCE.json`. The JSON contains the current rule, ceiling, complete derivation entry, every ledger owner (including PLANNED), named live files/classes, and every candidate reference with file and line in the tracked baseline and pinned upstream tree. Paths in this table are relative to `assets/advancedrocketry/`.", "",
              "| Legacy asset | Source SHA-256 | Group / proposed | Per-file evidence and disposition |", "|---|---|---|---|"]
    for e in evidence:
        r = e["replacement"]
        named = r["models"] + r["textures"] + r["additional_files"] + r["classes"]
        short = "; ".join(f"`{p}`" for p in named)
        origins = "; ".join(f"`{v['file']}:{v['line']}`" for v in e["upstream_consumers"]["direct"])
        owners = e["rule"]["units"]
        detail = (f"{e['id']}; first rule {e['rule']['order']} `{e['rule']['pattern']}`; ceiling REVIEW; derivation {e['derivation']['verdict']}; owners `{owners}`; "
                  f"no tracked src source-hash copy. Upstream consumer: {origins or 'no direct path binding found'}. {e['upstream_consumers']['note']} Live replacement: {short or 'no one-to-one bitmap replacement; see named code and group note'}. " + r["note"])
        if e["proposed"] == "EXCLUDE":
            detail += " Proposed EXCLUDED finding does not clear authorship."
        lines.append(f"| `{e['asset']}` | `{e['source_sha256']}` | {e['group']} / {e['proposed']} | {detail} |")
    lines += ["", "## Reference audit method and limits", "",
              "The scan covers every tracked baseline file, not only Java: models, MTL, blockstates, JSON, import plans, provenance, records, data generators, tests and historical audit material are included. Exact path, basename and extensionless lexical tokens are searched case-insensitively. Every matching line is retained in JSON; bare-stem hits such as `copy`, `motor`, `solar` and `beacon` are deliberately broad candidates, not evidence of a runtime binding. Same-named modern resources are namespace/path-distinct. Source SHA-256 comparison separately checks every tracked src file, including binary files. Generated build outputs/untracked files are not the source of the delivery claim.",
              "The upstream scan reads the pinned Git archive in memory, not the upstream working tree. It does not execute 1.12.2. Family-generated paths (numbered tanks, suit layers/overlays, lava animation and GUI state suffixes) require the explicit dynamic notes below in addition to literal hits. No static scan proves a real-client render, provenance clearance or audio listen.", "",
              "## Draft files and reproduction", "",
              "`asset-plan.csv.draft`, `allowlist.txt.draft` and `origin-findings.json.draft` are separate inert full-file previews; `changes.json` records exact semantic operations and central input hashes. `build_proposal.py --upstream <pinned-clone>` rebuilds them and the evidence from the assigned baseline. `apply_scratch.py --scratch <new-directory>` creates a new Git-backed LF export, verifies inputs, applies only these previews plus draft ADR metadata/pin/table there, and audits first-match changes. It refuses this worktree, another worktree, and any existing destination. It never creates acceptance or fills in the owner's name.",
              "`verify_scratch.py --upstream <pinned-clone> --clients <jar-directory> --output <external-W268-directory>` runs all requested Python/import checks, records every exit code and output, and removes only its own export in finally. See `SCRATCH-RESULTS.md` for the actual results, including failures and remaining closure items."]
    return lines


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", type=Path, required=True)
    build(parser.parse_args().upstream)
