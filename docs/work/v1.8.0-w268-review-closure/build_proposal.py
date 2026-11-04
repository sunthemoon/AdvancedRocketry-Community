"""Build W268's review evidence and inert drafts from the assigned Git snapshot.

Writes beside this script and saves the full scan in the named external folder.
Upstream is read through Git at the manifest pin.
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

from consumer_routes import CONSUMER_NOTES, CONSUMER_UNITS, NAMED_TARGETS, PROVIDERS, REDESIGN_CLASSES
from adoption_fixtures import BEACON, TEST as LEDGER_TEST, fixture_patch, new_releases

REVIEW_IDENTITY_BASE = "a65465138adca914d61d492d940fca015b86c9c6"
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
PREPARED_AT = "2026-11-03"
DECISION = "docs/work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261104.md"
G7_SUPERSEDED = "Keep all ten legacy OGGs in REVIEW until D5's in-use listen and per-file decisions, deferring closure and granting no permission to ship them?"
GROUPS = {
    "G1": ("Unbound machine and controller faces", "EXCLUDE",
           "Keep the modern machine designs, giving up the old faces and their running-state faces (modern machines show none)? The old monitorfront also faced the rejected deployable rocket builder; guidancecomputer faced the deferred biome scanner and the planet selector, now a screen; guidancecomputeraccesshatch faced the guidance access hatch, now the star-map screen; panelside and satellitebay faced the holo selector and station marker, now commands. Any future block of those kinds would need NEW art unless its legacy file is reopened, including a future biome scanner."),
    "G2": ("Unbound item icons", "EXCLUDE",
           "Keep the NEW item icons, including distinct iron saw blade and pressure tiers, giving up the old icon artwork?"),
    "G3": ("GUI button sheets and warning", "EXCLUDE",
           "Keep code-drawn/native-widget screens, giving up the legacy button/tab sheets and warning artwork?"),
    "G4": ("Space suit base and overlay textures", "EXCLUDE",
           "Keep the NEW non-dyeable suit? The 1.12 leather-armour suit could be dyed (tinted base, untinted overlays); the modern suit cannot, so accepting G4 accepts that loss."),
    "G5": ("Legacy lava images and animation metadata", "EXCLUDE",
           "Keep the NEW static enriched-lava sprites, giving up the old artwork and its animation?"),
    "G6": ("LibVulpes casing and motor exports", "EXCLUDE",
           "Keep project casing and NEW motor tiers, giving up the AR-bundled casing/motor exports without resolving LibVulpes authorship?"),
    "G7": ("Legacy sounds", "EXCLUDE",
           "Keep all ten NEW sounds accepted by the owner under D5; exclude all ten legacy OGGs without clearing their authorship."),
    "G8": ("Beacon and force field", "EXCLUDE",
           "Keep the modern beacon, field/projector and gravity/vacuum-laser replacements, giving up the old textures and force-field animation (the modern field is static)?"),
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


def scan(assets: list[str], files: dict[str, bytes], source_hashes: dict[str, str]) -> dict[str, list[dict]]:
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
        for token in (asset, base, name, stem, base.removeprefix("textures/").removesuffix(".png"), source_hashes[asset]):
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


def consumer_routes(consumed: dict, ledger: dict[str, dict]) -> list[dict]:
    routes = []
    for path in sorted({r["file"] for r in consumed["direct"]}):
        key = Path(path).stem
        if key not in CONSUMER_UNITS:
            raise ValueError("Unmapped upstream consumer: " + path)
        unit = CONSUMER_UNITS[key]
        routes.append(dict(upstream_consumer=path, ledger_unit=ledger[unit], note=CONSUMER_NOTES.get(key, "")))
    return routes


def replacement(asset: str, owners: list[dict], files: dict[str, bytes], routes: list[dict]) -> dict:
    g = group(asset)
    if g in ("G1", "G8"):
        owners = [r["ledger_unit"] for r in routes]
    targets = sorted({x for owner in owners for x in re.findall(r"advancedrocketrycommunity:([a-z0-9_]+)", owner["target"])})
    targets = sorted(set(targets) | {t for owner in owners for t in NAMED_TARGETS.get(owner["unit_id"], [])})
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
    if g in ("G1", "G8"):
        classes = [PROVIDERS[t] for t in targets]
        classes += [c for owner in owners for c in REDESIGN_CLASSES.get(owner["unit_id"], [])]
        formed = {"precision_laser_etcher": "precisionlaseretcher", "crystallizer": "crystallizer", "cutting_machine": "cuttingmachine"}
        for t in targets:
            if t in formed:
                classes += ["ObjModelFixture", "ClassicObjModelRenderer"]
                extra += [p for p in files if p.endswith("/models/" + formed[t] + ".obj")]
        if not routes:
            note = "No consumer in 1.12; orphaned texture with no one-to-one modern replacement obligation. Inherited plan owners do not establish a binding."
        else:
            note = "Modern targets follow every actual 1.12 consumer in the preceding mapping, not the inherited plan owners. No running-state face is shown on these modern machines; formed geometry is a separate binding."
        if g == "G8":
            note = note.replace("these modern machines", "these modern replacements")
            if "planetary_beacon" in targets:
                classes += ["PlanetaryBeaconBlock", "ClassicObjModelRenderer", "ObjModelFixture"]
                extra += [p for p in files if p.endswith(("/models/beacon.obj", "/models/beacon.mtl", "/textures/models/beacon_new.png"))]
                note += " The live imported K3 beacon mesh uses its NEW texture, not either legacy beacon PNG."
            if "force_field" in targets:
                note += " The modern force field is not animated; its NEW sprite has no .mcmeta."
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
        note = "The 1.12 leather-armour suit could be dyed: tinted base layers and untinted overlay files, for icons and the worn suit. The modern NEW suit cannot be dyed; accepting G4 accepts that loss. Restoring dyeing needs NEW tintable layers, not these files. The old AUDIT-01 iron-armour placeholder observation is superseded."
    elif g == "G5":
        classes = ["ClassicFluidType", "ClassicFluids", "V180FluidArt", "V180FluidData"]
        extra += [GEN + "textures/block/fluid/enriched_lava_" + face + ".png" for face in ("still", "flow")]
        extra += [GEN + "models/block/enriched_lava.json"]
        note = "The 1.12 sheets and .mcmeta animated enriched lava. Modern enriched_lava_still/flow sprites via ClassicFluidType are NEW and static: enriched lava is not animated."
    elif g == "G6":
        classes = ["MotorArt", "MotorDataFiles", "ModBlockStateProvider", "SatelliteBlockStateProvider", "MachineCasingBlock"]
        note = "Keep project casing and NEW four-tier motor models. Exclusion would NOT resolve the shared LibVulpes authorship chain; reopening import needs ADR-061 section 4.1 per-file history and authorship evidence."
        if asset == "textures/blocks/machinegeneric.png":
            note += " Only three consumer lines bind AR's bundled copy: forcefieldprojector.json bottom and particle (lines 12-13), and vacuumlaser.json particle (line 13). The other 73 matching lines use libvulpes:blocks/machinegeneric from LibVulpes's own jar, not this AR copy; excluding it removes nothing they used and settles nothing about LibVulpes authorship."
    elif g == "G7":
        sound_map = dict(airhissloop="air_hiss_loop", basiclasergun="basic_laser_gun", combustionrocket="combustion_rocket", electricshocksmall="electric_shock_small", gravityohhh="gravity_controller", laserdrill="laser_drill", lathe="lathe", machinelarge="machine_large", railgunbang="railgun_bang", rollingmachine="rolling_machine")
        extra += [GEN + "sounds/" + sound_map[Path(asset).stem] + ".ogg", GEN + "sounds.json"]
        classes = ["V180SoundArt", "V180SoundDefinitions", "ClassicSound", "ClassicSoundClient", "ModSounds"]
        note = "Owner decision 2026-11-04 (D5/G7) keeps all ten NEW sounds and excludes all ten legacy OGGs. This supersedes W268's proposed REVIEW hold. No OGG origin clearance is inferred from the PNG detector's CLEAR or the owner's choice of NEW audio."
        if Path(asset).name in ("lathe.ogg", "rollingmachine.ogg"):
            note += " lathe.ogg and rollingmachine.ogg are the same recording (identical source SHA-256); both legacy files are excluded, without an origin finding that clears either."
    class_paths = sorted(p for p in files if p.startswith(JAVA) and Path(p).stem in classes)
    assert not routes and g == "G1" or class_paths, (asset, classes)
    assert {Path(p).stem for p in class_paths} == set(classes), (asset, classes, class_paths)
    assert all(p in files for p in extra), (asset, extra)
    return dict(targets=targets, models=models, textures=sorted(textures), additional_files=sorted(set(extra)), classes=class_paths, consumer_routes=routes, note=note)


def consumers(asset: str, references: list[dict], old: dict[str, bytes]) -> dict:
    """Select actual path-bearing consumers from the broad lexical candidate scan."""
    base = asset.removesuffix(".mcmeta")
    aliases = {base.lower()}
    if base.startswith("textures/"):
        aliases.add(base.removeprefix("textures/").removesuffix(".png").lower())
    else:
        aliases.add(Path(base).name.lower())
    binding = re.compile(r"(?<![a-z0-9_/:])(?:(?P<namespace>[a-z0-9_]+):)?(?:" +
                         "|".join(re.escape(a) for a in sorted(aliases, key=lambda a: (-len(a), a))) +
                         r")(?![a-z0-9_])")
    direct = []
    for ref in references:
        p = ref["file"]
        if p == PREFIX + asset or not p.endswith((".java", ".json", ".mtl", ".obj")):
            continue
        line = old[p].decode("utf-8-sig").splitlines()[ref["line"] - 1].lower()
        # Do not treat LibVulpes's jar resource as AR's bundled texture.
        if any(m.group("namespace") in (None, "advancedrocketry") for m in binding.finditer(line)):
            direct.append(ref)
    dynamic = ""
    if asset.startswith("textures/armor/"):
        dynamic = "ItemSpaceArmor.getArmorTexture at armor/ItemSpaceArmor.java:162-174 selects the base/overlay paths by slot. AdvancedRocketry.java:513-516 uses LEATHER; ItemSpaceArmor.java:96-113 reads display.color; ClientProxy.java:130-136 tints base item layer 0, not overlays. Legacy layer numbers do not equal the modern 1/2 route."
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


def check_review_base(manifest: dict, plan: list[dict]) -> list[str]:
    original = rows(git("show", REVIEW_IDENTITY_BASE + ":" + PLAN))
    original_assets = rows(git("show", REVIEW_IDENTITY_BASE + ":legacy-manifest/assets.csv"))
    expected = {r["source_path"].removeprefix(PREFIX) for r in original_assets
                if first(r["source_path"].removeprefix(PREFIX), original)["handling"] == "REVIEW"}
    review = sorted(a for a in manifest if first(a, plan)["handling"] == "REVIEW")
    if len(expected) != 95 or set(review) != expected:
        raise ValueError("Base must retain the expected 95 REVIEW files; missing=" +
                         repr(sorted(expected - set(review))) + "; extra=" + repr(sorted(set(review) - expected)))
    for asset in review:
        before, now = first(asset, original), first(asset, plan)
        if any(before[k] != now[k] for k in ("pattern", "handling", "plan", "units", "reason")):
            raise ValueError("REVIEW baseline semantics changed: " + asset)
    return review


def trim_references(evidence: list[dict]) -> list[dict]:
    trimmed = json.loads(json.dumps(evidence))
    for entry in trimmed:
        for field in ("current_references", "upstream_references"):
            refs = entry[field]
            keep = [r for r in refs if not r["file"].startswith("docs/work/v1.8.0-root-queue/") and
                    (any("/" in m or "." in m or re.fullmatch(r"[0-9a-f]{64}", m) for m in r["matched"]) or
                     field == "upstream_references" and entry["group"] == "G7" and r["file"].endswith(".java"))]
            entry[field] = keep
            entry[field + "_summary"] = dict(total=len(refs), retained=len(keep),
                all_by_category=Counter(r["category"] for r in refs),
                omitted_by_category=Counter(r["category"] for r in refs) - Counter(r["category"] for r in keep))
    return trimmed


def build(upstream: Path, base: str, full_scan: Path) -> None:
    base = git("rev-parse", "--verify", base + "^{commit}").decode().strip()
    full_scan = full_scan.resolve()
    if full_scan.is_relative_to(ROOT.resolve()):
        raise ValueError("Full scan must be saved outside the worktree.")
    manifest = {r["source_path"].removeprefix(PREFIX): r for r in rows(git("show", base + ":legacy-manifest/assets.csv"))}
    plan = rows(git("show", base + ":" + PLAN))
    ledger = {r["unit_id"]: r for r in rows(git("show", base + ":docs/work/v1.8.0-content-ledger.csv"))}
    review = check_review_base(manifest, plan)
    tracked = git("ls-tree", "-r", "--name-only", base).decode().splitlines()
    with tarfile.open(fileobj=io.BytesIO(git("archive", "--format=tar", base))) as tar:
        files = {m.name: tar.extractfile(m).read() for m in tar.getmembers() if m.isfile()}
    assert set(files) == set(tracked), "The Git archive must include every baseline file."
    # Pin-specific Git archive is read in memory; upstream working-tree state is irrelevant.
    archive = git("archive", "--format=tar", PIN, "src", root=upstream)
    with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
        old = {m.name: tar.extractfile(m).read() for m in tar.getmembers() if m.isfile()}
    for asset in review:
        assert digest(old[PREFIX + asset]) == manifest[asset]["sha256"], asset
    # Always rescan immutable Git inputs; generated evidence is never trusted as input.
    source_hashes = {a: manifest[a]["sha256"] for a in review}
    current_refs = scan(review, files, source_hashes)
    old_refs = scan(review, old, source_hashes)
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
        consumed = consumers(asset, old_refs[asset], old)
        routes = consumer_routes(consumed, ledger) if group(asset) in ("G1", "G8") else []
        evidence.append(dict(id=f"R{n:03}", asset=asset, source_sha256=manifest[asset]["sha256"],
                             source_commit=PIN, derivation=verdicts[asset], allowlist_ceiling=allow[asset],
                             rule=rule, owners=owners, group=group(asset), proposed=GROUPS[group(asset)][1],
                             replacement=replacement(asset, owners, files, routes), source_hash_copies=hashes[manifest[asset]["sha256"]],
                             current_references=current_refs[asset], upstream_references=old_refs[asset],
                             upstream_consumers=consumed,
                             all_matching_plan_rules=[r for r in plan if fnmatch.fnmatchcase(asset, r["pattern"])]))
        assert not evidence[-1]["source_hash_copies"], asset
    full = dict(source_commit=base, upstream_commit=PIN,
         scan_scope="Every tracked file at the named base; case-insensitive lexical path/name/stem/source-hash matches. Binary files excluded from text scan but all tracked src Git blobs checked by SHA-256. Matches are candidates, not proof of binding. Dynamic family evidence is explained in PROPOSAL.md.", files=evidence)
    full_data = (json.dumps(full, indent=2, ensure_ascii=True) + "\n").encode()
    full_scan.parent.mkdir(parents=True, exist_ok=True)
    full_scan.write_bytes(full_data)
    # The external destination is not encoded: identical inputs reproduce the
    # committed outputs even when a reviewer chooses another evidence folder.
    full_hash = digest(full_data)
    dump("REVIEW-EVIDENCE.json", dict(full, files=trim_references(evidence), full_scan_sha256=full_hash,
         retention="Path/hash candidates outside root-queue records, actual upstream consumers, sound event Java uses, and per-file/category scan counts; remaining lexical hits are in the external full scan."))
    changes = []
    for rule in plan:
        effective = [e for e in evidence if e["rule"]["pattern"] == rule["pattern"]]
        if effective and effective[0]["proposed"] == "EXCLUDE":
            assert all(e["proposed"] == "EXCLUDE" for e in effective)
            new = dict(rule, handling="EXCLUDE", plan="-", reason="excluded by an origin finding (owner decision 2026-11-04 / ADR-062 revision 7; not authorship clearance)")
            changes.append(dict(pattern=rule["pattern"], before=rule, after=new, assets=[e["asset"] for e in effective]))
    changed = {c["pattern"]: c["after"] for c in changes}
    after_plan = [changed.get(r["pattern"], r) for r in plan]
    excluded = {e["asset"] for e in evidence if e["proposed"] == "EXCLUDE"}
    assert len(excluded) == 95
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
            reviewed_at=PREPARED_AT, draft_status="PENDING_OWNER_NOT_A_REVIEW",
            review_record=ADR, basis=f"DRAFT W268 {e['id']} / {e['group']}; not an owner decision. Date is preparation date only, not an attestation. Source SHA-256 {e['source_sha256']}. Proposed non-shipment of the old file; does not clear authorship, approve NEW art, or satisfy D7/V1. " + e["replacement"]["note"]))
    dump("origin-findings.json.draft", findings)
    before = Counter(first(a, plan)["handling"] for a in manifest)
    after = Counter(first(a, after_plan)["handling"] for a in manifest)
    after.setdefault("REVIEW", 0)
    counts = dict(manifest=len(manifest), changed_rules=len(changes), excluded=len(excluded),
                  allowlist_before=len(allow), allowlist_after=len(allow_data.splitlines()),
                  planned=sum(r["disposition"] == "PLANNED" for r in ledger.values()))
    releases = new_releases(derivation, excluded)
    patch = fixture_patch(files[LEDGER_TEST].decode("utf-8"), first(BEACON, plan),
                          files[ALLOW].decode().splitlines(), releases)
    (HERE / "ledger-fixtures.patch.draft").write_text(patch, encoding="utf-8", newline="\n")
    dump("changes.json", dict(source_commit=base, before_counts=before, after_counts=after, counts=counts, prepared_at=PREPARED_AT,
         beacon_new_releases=releases,
         allowlist_before_sha256=digest(files[ALLOW]), allowlist_after_sha256=digest(allow_data),
         source_sha256={p: digest(files[p]) for p in (PLAN, ALLOW, FINDINGS, ADR)},
         asset_plan=changes, retained_review=[e["asset"] for e in evidence if e["proposed"] == "REVIEW"]))
    lines = proposal(evidence, before, after, digest(allow_data), counts, base, full_hash, releases)
    (HERE / "PROPOSAL.md").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(dict(files=len(evidence), groups=Counter(e["group"] for e in evidence), changes=len(changes), before=before, after=after, allowlist_sha256=digest(allow_data)), indent=2))


def proposal(evidence: list[dict], before: Counter, after: Counter, pin: str, counts: dict, base: str, full_hash: str,
             releases: list[str]) -> list[str]:
    released_files = ", ".join(f"`{asset}`" for asset in releases)
    lines = ["# W268 - REVIEW closure proposal, adopted by W274", "",
             f"**Adopted on 2026-11-04 under [the owner decision](../v1.8.0-root-queue/records/OWNER-DECISIONS-20261104.md), R304.** G1-G6/G8 are adopted as drafted; G7 is amended to exclude all ten legacy OGGs after the owner accepted all ten NEW sounds under D5. The separate findings draft remains inert and unsigned; the tracked findings carry the recorded review.", "",
             "## Eight decision groups", ""]
    lines += [f"- **{g}**: {question}" for g, (_, _, question) in GROUPS.items()]
    lines += ["", f"Baseline: `{base}`; original AR 1.12 source pin: `{PIN}`.",
             "Input inventory: `D:/GitHub/ARCE-Task-Evidence/root-queue/W263/INVENTORY.md`, SHA-256 `861320967dde0492569d8e31f2874a0d7288c603af8d2bca5720c720fb97925a` (checked). Its projected 95 REVIEW files equal the assigned shared head's actual first matches. The historical AUDIT-01 is not edited or treated as a current binding audit.",
             "W268 originally wrote only this directory and its task record. W274 adopts the plan, allowlist, reviewed findings and ADR-062 revision 7, hardens the validator and updates its fixtures. The ledger, status, NEW art/audio and D7 records are outside W274's write scope.", "",
             "## Decision groups", "", "95 EXCLUDE decisions account for all 95 formerly REVIEW files. No IMPORT is adopted: there is no cleared legacy source that a live consumer needs instead of its existing replacement.", "",
             "| Group | Files | Adopted handling | Accepted choice / original question |", "|---|---:|---|---|"]
    for group_id, (title, handling, question) in GROUPS.items():
        lines.append(f"| {group_id}: {title} | {sum(e['group'] == group_id for e in evidence)} | {handling} | {question} |")
    lines += ["", "## Owner and provenance boundaries", "",
              "Adopting G1-G8 excludes the legacy files only; it does not clear their authorship. G6 explicitly leaves the LibVulpes chain unresolved; a future import requires the per-file pinned blob/hash, history, derivation and authorship finding specified by ADR-061 section 4.1, not a category approval. W274 does not approve or edit the D7 batch records. Real-GPU V1 and applicable V2 remain separate.",
              "For G7, the owner accepted all ten NEW sounds and chose them instead of the ten old OGGs. No source/author/format clearance of a legacy OGG is made. A future import would need an ADR revision and the existing ADR-061 finding/release/override controls.", "",
              "### Superseded G7 draft (retained verbatim)", "", G7_SUPERSEDED, "",
              "The original draft retained ten REVIEW files (85 exclusions, EXCLUDE 355, allowlist 162). The owner's 2026-11-04 D5/G7 decision supersedes that handling with ten more exclusions, not ten clearances.", "",
              "`sounds/lathe.ogg` and `sounds/rollingmachine.ogg` are the same recording: both have SHA-256 `673d70feacea2d3a1031ae13d960879ef79b7eca3539e75a2322fb1190d75a32`. Both are excluded; their shared recording is not cleared.", "",
              "### Draft handling, not a signature", "",
              "The origin draft deliberately keeps reviewer `<OWNER_PENDING_DECISION>` and `draft_status: PENDING_OWNER_NOT_A_REVIEW`. Its ISO date is the preparation date only, not a completed human review. W274's reviewed findings instead name sunthemoon, 2026-11-04 and the owner decision record. The validator rejects draft markers even on EXCLUDED findings. The historical scratch helper still makes only a PROPOSED draft, never an acceptance; fixture simulations are not owner signatures.", "",
              "## Adoption steps (authorized by the 2026-11-04 owner decision)", "",
              f"1. Record the owner's actual group decisions, date, channel and scope in a committed decision record. The decision concerns exactly {counts['excluded']} files in {counts['changed_rules']} patterns, not permission to overwrite a later plan. Rebuild with `--base <adoption-head>` and recompute counts, rule numbers and hashes; the builder refuses a base that changes the expected 95-file REVIEW set or its rule semantics. Repeat independent review and scratch checks if inputs change.",
              "2. Prepare a separate reviewed findings file. For each added finding, replace the reviewer placeholder only from the recorded owner decision, use the decision date for `reviewed_at`, remove `draft_status`, and cite the decision record in `basis` and `review_record`. Do not reinterpret the preparation date as a review. Preserve existing findings and all non-clearance boundaries.",
              f"3. Before installing any findings, run `adopt_findings.py --findings <reviewed-findings.json> --output <checkout>/docs/provenance/v1.8.0-origin-findings.json`. It refuses the entire write if ANY finding still has a placeholder/empty reviewer, `draft_status`, or draft basis wording (`DRAFT W268`, `not an owner decision`, `preparation date`). Added findings must cite a decision record rather than ADR-062 and must not reuse the preparation date `{PREPARED_AT}` as `reviewed_at`; even an existing output is unchanged on refusal. `apply_scratch.py` intentionally bypasses this adoption operation only to inspect inert drafts in a disposable copy; it cannot adopt into a worktree.",
              "4. W274 hardens `scripts/validate_v180_content_ledger.py`: enforce the same reviewer, draft-status, draft-basis, preparation-date and decision-record checks on tracked findings, including EXCLUDED findings, with negative tests. The helper's structural guard does not authenticate a reviewer; ADR-061's owner/recorded-reviewer commit and review controls still apply. Install the reviewed plan/allowlist and owner-accepted revision 7 with its matching pin.",
              "5. Preserve the existing ledger assertions while updating exactly these fixture baselines. The generated inert `ledger-fixtures.patch.draft` implements this table; `test_proposal.py` applies it in a disposable draft copy and runs the whole ledger module with revision 7 simulated as accepted. That simulation is not acceptance. The patch restores beacon's pre-exclusion REVIEW state rather than depending on sounds remaining REVIEW:", "",
              "| Ledger test | Exact adoption fixture action |", "|---|---|",
              "| `test_excluded_finding_forces_exclusion` | Restore `textures/blocks/beacon.png` from its `changes.json` before row (handling REVIEW, plan C17c, units block:beacon, reason `same name as a vanilla texture; compare before import`) before writing its EXCLUDED test finding. Preserve the assertion `excluded by an origin finding but is REVIEW`. G7 has no still-REVIEW sound alternative after adoption. |",
              "| `test_import_for_a_deferred_unit_fails` | Restore the same beacon REVIEW/C17c row and original reason before changing `units` to `block:terraformer`. Preserve `REVIEW for block:terraformer, which is DEFERRED`. |",
              f"| `test_review_needs_a_cleared_finding_to_import` | Restore the same beacon REVIEW/C17c row, units and original reason; restore its REVIEW allowlist entry and re-pin the fixture ADR-062; remove its EXCLUDED finding. Then exercise REVIEW -> IMPORT as before. Add {released_files} to the CLEARED finding's existing `releases`: these are all newly excluded HIT relations, computed from the derivation data in both directions and the draft findings. Preserve the REVIEW-ceiling and releases assertions. |",
              "| `test_require_accepted_reports_proposed_adrs` | No fixture change: it passes unchanged once the fixture baseline has owner-accepted revision 7. The draft-only scratch failure correctly reports the deliberately PROPOSED ADR. |", "",
              f"6. Rerun the complete proposal, importer, ledger, provenance and resource Python suites, every import verify, the repository validator and ledger normal/accepted/closure modes. Report the remaining {counts['planned']} PLANNED rows and {after['REVIEW']} REVIEW files. Root closes ledger rows separately; this adoption grants no test/threshold waiver, D7 record approval or visual Gate approval.", "",
              "## Revision 7 (adopted on 2026-11-04)", "",
              "### Published language and appearance boundaries", "",
              "Retain the named modern machine faces, tier icons, code/native-widget GUI, NEW suit and enriched-lava sprites, project casing and NEW motor tiers, modern beacon/field presentation and all ten NEW sounds. Modern machines show no running-state faces. The 1.12 leather-armour suit could be dyed (tinted base, untinted overlay files); the modern suit cannot, and G4 accepts this loss. Restoring dyeing later needs NEW tintable layers, not these files. The old enriched lava and force field were animated; modern enriched lava and force field are not animated. The legacy GUI sheets and old artwork are not reproduced. No gameplay owner, inventory, recipe or registered ID is removed by these asset exclusions. G7 is terminal EXCLUDE, including both copies of the shared lathe/rolling-machine recording.", "",
              "### Current handling and exact adopted files", "",
              "| Handling | Before | After adoption |", "|---|---:|---:|"]
    for h in ("IMPORT", "REVIEW", "REGENERATE", "EXCLUDE", "IMPORTED"):
        lines.append(f"| {h} | {before[h]} | {after[h]} |")
    lines += ["", f"Total {counts['manifest']} files. Exactly {counts['excluded']} REVIEW files in {counts['changed_rules']} existing rules become EXCLUDE, with their plan set to `-` as the validator requires. No row order or owner is changed. {after['REVIEW']} legacy files remain REVIEW. The allowlist drops exactly {counts['allowlist_before'] - counts['allowlist_after']} REVIEW entries, {counts['allowlist_before']} -> {counts['allowlist_after']}; all other ceilings are unchanged. Adopted allowlist SHA-256: `{pin}` (UTF-8, LF, final newline). These counts were recomputed on W274's pre-adoption head.", "",
              "W265/W267 have no REVIEW-row overlap. The scratch applicator refuses changed central input hashes; rebuild on any later adoption head before applying its previews. Their decisions and the 35 retained NEW textures are not altered or approved here. Several G1/G8 plan-owner lists are inherited and stale: the consumer-to-ledger mappings below correct the evidence, not those lists. A later owner-trim task may correct the ownership metadata independently.", "",
              "Each row below has an exact source identity and an evidence ID into `REVIEW-EVIDENCE.json`. It contains the current rule, ceiling, derivation, plan owners, actual G1/G8 consumer-to-ledger mappings, live files/classes, retained path/hash references and per-file scan counts. The four orphaned G1 textures have no consumer in 1.12 and no replacement obligation. Paths in this table are relative to `assets/advancedrocketry/`.", "",
              "| Legacy asset | Source SHA-256 | Group / adopted | Per-file evidence and disposition |", "|---|---|---|---|"]
    for e in evidence:
        r = e["replacement"]
        named = r["models"] + r["textures"] + r["additional_files"] + r["classes"]
        short = "; ".join(f"`{p}`" for p in named)
        origins = "; ".join(f"`{v['file']}:{v['line']}`" for v in e["upstream_consumers"]["direct"])
        owners = e["rule"]["units"]
        routes = "; ".join((f"`{v['upstream_consumer']}` -> `{v['ledger_unit']['unit_id']}` ({v['ledger_unit']['disposition']}) -> `{v['ledger_unit']['target']}`" +
                            (". " + v["note"].rstrip(". ") if v["note"] else "")) for v in r["consumer_routes"])
        parts = [f"{e['id']}; first rule {e['rule']['order']} `{e['rule']['pattern']}`; ceiling REVIEW; derivation {e['derivation']['verdict']}; owners `{owners}`; no tracked src source-hash copy.",
                 f"Upstream consumer: {origins or 'no direct path binding found'}.", e["upstream_consumers"]["note"],
                 "Consumer-to-modern mapping: " + routes + "." if routes else "",
                 f"Live replacement: {short or 'none; no consumer in 1.12'}.", r["note"]]
        detail = " ".join(part for part in parts if part)
        if e["proposed"] == "EXCLUDE":
            detail += " Adopted EXCLUDED finding does not clear authorship."
        lines.append(f"| `{e['asset']}` | `{e['source_sha256']}` | {e['group']} / {e['proposed']} | {detail} |")
    lines += ["", "## Reference audit method and limits", "",
              "The scan covers every tracked baseline file, not only Java: models, MTL, blockstates, JSON, import plans, provenance, records, data generators, tests and historical audit material are included. Exact path, basename, extensionless lexical tokens and source SHA-256 strings are searched case-insensitively. The committed JSON retains path/hash candidates outside root-queue records, actual consumers, sound event Java uses and per-file/category counts of omitted matches. Bare-stem hits such as `copy`, `motor`, `solar` and `beacon` are broad candidates, not proof of a runtime binding. Same-named modern resources are namespace/path-distinct. Source SHA-256 comparison separately checks every tracked src Git blob, including binary files, independently of Windows checkout line endings. Generated build outputs/untracked files are not the source of the delivery claim.",
              f"The complete scan, including all non-binding lexical matches, is retained externally as `D:/GitHub/ARCE-Task-Evidence/root-queue/W274/REVIEW-EVIDENCE-full.json`, SHA-256 `{full_hash}`. It is reproducible from the named Git base and pinned upstream with the builder; its destination does not affect any committed output bytes.",
              "The upstream scan reads the pinned Git archive in memory, not the upstream working tree. It does not execute 1.12.2. Family-generated paths (numbered tanks, suit layers/overlays, lava animation and GUI state suffixes) require the explicit dynamic notes in the per-file table above in addition to literal hits. Namespace filtering separates AR's bundled machinegeneric from the other 73 LibVulpes consumer lines. No static scan proves a real-client render, provenance clearance or audio listen.", "",
              "## Draft files and reproduction", "",
              "`asset-plan.csv.draft`, `allowlist.txt.draft` and `origin-findings.json.draft` are separate inert full-file previews; `changes.json` records exact semantic operations and central input hashes. `build_proposal.py --base <commit> --upstream <pinned-clone> --full-scan <external-file>` rebuilds them and the evidence from that immutable baseline. It also checks that each REVIEW rule's handling, plan, units, pattern and reason match the original scoped set (renumbering is allowed). `apply_scratch.py --scratch <new-directory>` creates a new Git-backed LF export of the named pre-adoption baseline, verifies its input hashes, applies only these previews plus draft ADR metadata/pin/table there, and audits first-match changes. It therefore remains reproducible after the real adoption without installing anything in a later tree. It refuses this worktree, another worktree, and any existing destination. It never creates acceptance or fills in the owner's name. The nested `.gitattributes` keeps draft bytes LF, including the allowlist pin.",
              "`verify_scratch.py --upstream <pinned-clone> --clients <jar-directory> --output <external-W268-directory>` runs all requested Python/import checks, records every exit code and output, and removes only its own export in finally. See `SCRATCH-RESULTS.md` for the actual results, including failures and remaining closure items."]
    return lines


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", type=Path, required=True)
    parser.add_argument("--base", required=True, help="Immutable adoption baseline commit; must retain the expected 95 REVIEW files")
    parser.add_argument("--full-scan", type=Path, required=True, help="External destination for the complete lexical scan")
    args = parser.parse_args()
    build(args.upstream, args.base, args.full_scan)
