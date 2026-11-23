"""K11 console-built scene kit and parameterized W399 sessions, not V1/V2 evidence.

Generate first; run each native stage under the WORKER-RULES detached lock loop.
No raw NBT writes, clients, account lookup, production switches or source edits.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from uuid import UUID

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_prep import k16_core as common
from scripts.campaign_prep.k04_rockets import Console

NS = common.NS
PREFIX = NS + "."
MARKER = b"ARCE_BAY_V2_DISPOSABLE_WORLD_V1\n"
MARKER_NAME = ".arce-bay-v2-campaign"
OUTPUT = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures/K11"
PARENT = common.OUTPUT / "v2"
LOCK = common.EVIDENCE / "locks/heavy-jvm"
NATIVE_IDS = ["00000000-0000-4000-8000-000000453001", "00000000-0000-4000-8000-000000453002"]
FIXTURE_OWNER = "00000000-0000-4000-8000-000000128d01"
STAGES = ("build", "marked", "nohook")


def write_json(path, value):
    path.write_bytes(common.json_bytes(value))


def verify_seal(directory):
    """Require a complete inventory; never trust traversal, duplicates or linked files."""
    directory = directory.resolve()
    path = directory / "SHA256SUMS.txt"
    expected = {}
    for line in path.read_text(encoding="ascii").splitlines():
        match = re.fullmatch(r"([a-f0-9]{64})  (.+)", line)
        if not match:
            raise ValueError("Malformed checksum line")
        value, name = match.groups()
        member = Path(name)
        if ("\\" in name or ":" in name or member.is_absolute() or ".." in member.parts
                or member.as_posix() != name or name in expected or name == path.name):
            raise ValueError("Unsafe checksum member")
        expected[name] = value
    actual = common.tree_hashes(directory)
    actual.pop(path.name)
    if not expected or actual != expected:
        raise ValueError("Incomplete or mismatched sealed inventory")
    return dict(path=str(path), sha256=common.file_hash(path), files=len(expected))


def properties(mode, ids):
    if mode == "nohook":
        return {}
    if len(ids) != 2 or len(set(ids)) != 2 or any(str(UUID(v)) != v for v in ids):
        raise ValueError("Two distinct canonical UUIDs required")
    result = {PREFIX + "releaseTestHooks": "true", PREFIX + "bayV2CampaignPlayers": ",".join(ids)}
    if mode == "fuel":
        result[PREFIX + "escrowLoaderAdmission"] = "true"
    elif mode == "intent":
        result[PREFIX + "bayCheckpoint"] = "B18_INTENT"
    elif mode != "marked":
        raise ValueError("Unknown session mode")
    return result


def geometry():
    """Unassembled shells: ownership/packages/fuel must come from real player actions."""
    scenes = []
    for name, x in (("safe", 256), ("fall", 320), ("fuel-A", 384), ("fuel-B", 448)):
        origin = [x, 150, 256]
        cells = [([x, 149, 256], "rocket_assembler"), ([x, 150, 256], "rocket_motor"),
                 ([x + 1, 150, 256], "rocket_motor"), ([x - 1, 150, 256], "rocket_fuel_tank"),
                 ([x, 151, 256], "rocket_seat"), ([x + 1, 151, 256], "rocket_seat"),
                 ([x, 152, 256], "guidance_computer")]
        if name in ("safe", "fall"):
            cells += [([x + 2, 150, 256], "rocket_satellite_bay"),
                      ([x + 2, 151, 256], "rocket_satellite_bay")]
        else:
            cells += [([x - 3, 150, 256], "fluid_fuel_loader"),
                      ([x - 3, 150, 258], "fluid_fuel_loader")]
        scenes.append(dict(name=name, origin=origin, assembler=[x, 149, 256],
                           cells=[dict(pos=p, block=NS + ":" + b) for p, b in cells],
                           seat_blocks=[[x, 151, 256], [x + 1, 151, 256]],
                           seat_indices="Read authoritative assignment after boarding; never infer from block order.",
                           hold_origin=[x, 198, 256], seat_anchor_offsets=[[0, 1, 0], [1, 1, 0]],
                           safe_pad=[[x - 1, 149, 255], [x + 1, 149, 257]],
                           fall_floor=[[x - 1, 188, 255], [x + 1, 188, 257]],
                           fall_distance="Proposed seat anchor y=199 to floor top y=189: 10; confirm actual rider Y.",
                           owner="<B_UUID>" if name == "fuel-B" else "<A_UUID>",
                           state="UNASSEMBLED_UNCLAIMED_EMPTY; not a held or fuel-qualified fixture"))
    return scenes


def build_commands(scenes):
    commands = []
    for scene in scenes:
        x = scene["origin"][0]
        commands += [f"fill {x-5} 148 252 {x+5} 202 260 minecraft:air",
                     f"fill {x-5} 147 252 {x+5} 147 260 minecraft:stone"]
        if scene["name"] == "safe":
            commands += [f"fill {x-1} 149 255 {x+1} 149 257 minecraft:stone"]
        if scene["name"] == "fall":
            commands += [f"fill {x-1} 188 255 {x+1} 188 257 minecraft:stone"]
        for cell in scene["cells"]:
            commands.append(f"setblock {' '.join(map(str, cell['pos']))} {cell['block']}")
    return commands


def variant_table():
    online = ["W325_B3_STAGED_ONLINE", "W325_B3_STAGED_ONLINE_ABSENT", "W325_B3_MOVED_ONLINE",
              "W325_B3_MOVED_ONLINE_ABSENT", "W331_B4_GONE_X_S_PRESENT_ONLINE",
              "W331_B4_GONE_X_S_ABSENT_ONLINE", "W331_B4_LIVE_X_S_PRESENT_ONLINE",
              "W331_B4_LIVE_X_S_ABSENT_ONLINE", "W325_B8_STAGED_ONLINE", "W325_B8_MOVED_ONLINE"]
    exits = ["X0_CONTROL", "X1_OFFER", "X2_DESCENT", "X3_CORRECTION", "X4_LANDING", "X5_EXPIRY",
             "X6_TRUSTED", "X7_REVOKED", "X8_SAFE_MENU", "X9_FIRST_TICK", "X10_FIRST_TICK_REVOKED"]
    ids = ["B3.8", "B3.9", "B3.10", "B3.11", "B4.6", "B4.7", "B4.8", "B4.9", "B8.5", "B8.6"]
    rows = []
    for name, native in zip(ids + [f"EXIT.{i}" for i in range(11)], online + exits, strict=True):
        reason = ("Real fall lands before 200 ticks; other suspension actions revoke offer." if name == "EXIT.5"
                  else "No qualified real-client external-stop boundary recipe; facility/native stimulus is not V2.")
        rows.append(dict(variant=name, disposition="synthetic-only", native_case=native,
                         v2_status="NOT_COVERED", reason=reason,
                         source="W331.md" if name.startswith("B4") else "W325-MATRIX.md"))
    return rows


def recipes():
    """Reader actions, including explicit unavailable controls; no guessed packets."""
    return {
        "J01": ["On nohook, A places fresh bays at (258,150,256)/(258,151,256); open own bay empty-handed.",
                "Use normal satellite builder/terminal in the copied K16 core; A creates DATA and SOLAR packages. "
                "Keep SOLAR research locked. Insert only A-owned packages; archive native identities and receipts.",
                "Menu source order: receipt phase/reason, else runtime rejection with NONE, else NONE/DEPLOYMENT_UNAVAILABLE. "
                "Receipt phases are NONE, INTENT, REGISTERED, REJECTED, REPAIR, not an invented linear state sequence.",
                "Fixed-owner cap ONLY: on a separate marked disposable copy, arce bay release-test w358 owner-fill 16; "
                "arce bay release-test pending. Require 16 distinct pending IDs, exact acknowledgements and owner "
                + FIXTURE_OWNER + ". Then arce bay release-test fixture 512 150 256 parked, record logical UUID; "
                "arce bay release-test w358 launch <logical> <fresh-transfer-uuid>; require OWNER_PENDING_FULL "
                "and unchanged packages. Force-load/check that bounded chunk before fixture. Save/reopen same copy; "
                "verify durable union, not just requested=16. This recipe is NOT_RUN, fixed-owner native only, not A cap/menu evidence.",
                "B's admission is tested only with packages/rocket/bays placed and owned by B on B's separate scene. "
                "No foreign-owner cap coverage; no fixed-owner UUID impersonation.",
                "All phase/reason GUI variants beyond normal reachable states are NOT_COVERED until live-qualified; "
                "do not inject receipt/root NBT to display them."],
        "J02": ["A replaces the two bay shells by normal placement, loads A's real packages, assembles via assembler UI. "
                "Fuel by hand, not release-test refuel. Record logical/entity/snapshot/package UUIDs and exact fuel.",
                "Use intent session (B18_INTENT) on a fresh source copy. A/B board distinct seats through the real flight menu; "
                "record actual/assigned seat indices from RocketEntityData, never assume index ordering. A selects Moon/launches.",
                "Require ARCE_RELEASE_BAY_PAUSED cut=B18_INTENT and held INTENT, acknowledged T, observer availability, "
                "rocket Y=198 (origin+48), unchanged packages. Do not exit if any predicate is missing. "
                "Remove assembler at (256,149,256) and fill full safe pad stone only after assembly; fall scene removes "
                "assembler at (320,149,256) to air. Verify no collidable origin-column cell remains within one of y150.",
                "Safe: B sneaks then A uses LEAVE; restore fresh copy and exchange inputs. Separate witness, replay "
                "and simultaneous-exit runs. Keep attempt/capture order, health, packages, assigned seats and tickets.",
                "Fall: floor (319,188,255)..(321,188,257), top189, not a safe origin anchor. "
                "B sneaks while A stays; first ordinary fall protected, second not. Separate fresh runs for teleport "
                "execute at <B_NAME> run tp <B_NAME> ~0.1 ~ ~, pearl and dripstone (replace floor with stone + pointed dripstone by setblock).",
                'W254 step 3.4: "not run; covered natively by X5".',
                "Separate seated logout/reconnect, airborne logout/reconnect, death/respawn and dimension-change runs; "
                "no stale offer/remount while held. Compare raw seats, fuel, packages, forceload query and complete ticket observer. "
                "Release via arce bay release-test release, not a new launch. Online/offline retry separate. "
                "These real-client/hold predicates are NOT_RUN; source shells are not a saved held rocket."],
        "J03": ["NOT_COVERED: dedicated campaign selection is ignored by integrated/LAN; production switches remain off."],
        "J04": ["Use variants.json: all ten online and eleven EXIT rows are individually synthetic-only. "
                "No crash/halt seam is invoked with connected real clients; no V2 result is claimed."],
        "J23": ["A replaces both loader shells on fuel-A through normal placement to obtain A ownership. "
                "A assembles the two-seat fluid rocket with no assigned passengers. Use linker: select owned loader, "
                "wait >=10 ticks, use rocket within 6 blocks; actor within 8. "
                "Supply compatible Fluid by normal container exchange, not a carrier/NBT grant; record native tank/bank amounts.",
                "A empty-hand right-clicks own loader, reads Fluid/amount/binding/status. B ordinary right-click "
                "must refuse NOT_OWNER with action-bar feedback; B never receives A's private menu. "
                "B may observe world motion and B's own refusal, not private synchronized tank data. Reverse on fuel-B.",
                "Repeat en_us/zh_cn, scales2/3/4 and actual windowed/fullscreen, capture native screenshots/F3+T. "
                "No numeric reload-time budget is invented."],
        "J24": ["Compare a fresh nohook copy with fuel session. Eligible nohook attempt must say ADMISSION_DISABLED; "
                "unclaimed/unbound/empty is not that control. C6-created shells alone cannot prove it.",
                "Baseline control requires exact tank/bank 5000/0, normal compatible container exchange and supported "
                "fluid engine/tank definition. Native shell is NOT yet an assembled/qualified fluid bank. "
                "The 749mB control needs separate 1000/1251 baseline ->251/2000; do not overwrite a prior run.",
                "L1 hold/UNKNOWN, exact release into apply 4000/1000, L2 hold/release and pending views: "
                "NOT_COVERED on connected clients. Existing EscrowLoaderReleaseProbe uses "
                "escrowScenario seed/recover with a native case and fixture-owned endpoints, not an arbitrary "
                "A-owned device; do not run it as a connected-client hold command. "
                "PREPARING means no debit; applied pending is locked, not necessarily unmoved."],
        "J25": ["Use linker normally as J23; sneak-use in air clears selection, sneak-use on idle loader clears binding. "
                "Record exact UUID/binding before/after; wait >=10 ticks between changes.",
                "For range use fuel-A loader at381,150,256, rocket origin384,150,256. Actor far point "
                "(372.5,150,256.5) is >8 from loader; record actual feedback (a distant/obstructed owner may get none). "
                "A target rocket >6 away refuses independently. Do not teleport a live escrow-locked rocket.",
                "Stale/unloaded: NOT_COVERED until a legitimate identity-change/unload scene and FULL/entity/ticket "
                "readback are qualified. Never send invented replay packets, force-load the target or edit binding NBT."],
        "J26": ["Repeat J23 matrix for nohook/READY; held PREPARING/pending/refusal cells NOT_COVERED until "
                "their server stimuli exist. Record actual effective scale, physical resolution and raw pre/post endpoints."],
        "J27": ["A alone opens A menu; B records own NOT_OWNER action bar and permitted world view. "
                "B's own bay/loader in fuel-B provides the reversed-owner control.",
                "Two admitted transfers require legal two devices and exact operation IDs; 4000/4000 and bank2000 "
                "only if both were admitted. Synchronized held L1/L2 client cases NOT_COVERED: no connected-safe hold seam."],
        "J28": ["B boards eligible fuel rocket; capture actual rider and assigned seat. Attempt while actual rider exists: "
                "ROCKET_BUSY. B dismounts normally: assignment can remain and fill must still refuse.",
                "Legitimate operator action: arce rocket release-seat <loaded-rocket-entity-uuid> <B_UUID>. "
                "Require success receipt; data get entity <entity-uuid> RocketEntityData plus data get entity <B_NAME> "
                "must prove no assignments/riders before eligible fill. This clears a real assignment, not NBT. "
                "It requires the rocket idle; a held/busy refusal is not a successful clear. Native qualification NOT_RUN."],
        "J29": ["Owner-disconnect after exact acknowledged L1 is NOT_COVERED until connected-safe boundary/proof exists; "
                "PREPARING alone is insufficient. Never reuse native auto-halt probes with real clients.",
                "Separate passenger run: B boards then disconnects, A observes retained assignment and ROCKET_BUSY; "
                "B reconnects, legitimate release-seat as J28, verify both actual and assigned sets empty then retry once. "
                "If recovery restarts are required, preserve same whole world for two observations. NOT_RUN."]}


def generate(output, parent=PARENT):
    parent_receipt = verify_seal(parent)
    scenes = geometry()
    output.mkdir(parents=True, exist_ok=False)
    write_json(output / "plan.json", dict(schema_version=1, candidate_commit=common.CANDIDATE,
               jar_sha256=common.JAR_HASH, parent=parent_receipt, parent_world=str(parent / "world"),
               scenes=scenes, marker_sha256=common.digest(MARKER), accounts="UNBOUND_OWNER_INPUT_REQUIRED",
               coverage="GEOMETRY_ONLY_NOT_READY_FOR_CAMPAIGN", recipes=recipes()))
    write_json(output / "variants.json", variant_table())
    commands = build_commands(scenes)
    (output / "commands.txt").write_text("\n".join(commands) + "\n", encoding="ascii")
    # Templates intentionally fail W399 validation until authenticated metadata is supplied.
    for mode in ("marked", "intent", "fuel", "nohook"):
        write_json(output / (mode + "-session.template.json"), dict(schema_version=1, mode="attach",
                   singleplayer=False, cell="<PRIVATE_CELL_JSON>", run_dir="<NEW_RUN_DIRECTORY>",
                   java="<JAVA17>", time_limit_seconds=3600, input_interval_seconds=.05,
                   clients=[dict(player_name=f"<{role}_NAME>", pid=f"<{role}_PID>", account_label=role,
                                 game_dir=f"<{role}_GAME_DIR>", language="en_us", gui_scale=3,
                                 window_size=[1280, 720]) for role in ("A", "B")],
                   server={"online-mode": True, "white-list": True, "enforce-whitelist": True,
                           "server-ip": "127.0.0.1", "server-port": 25565, "whitelist": [
                               dict(name=f"<{r}_NAME>", uuid=f"<{r}_UUID>") for r in ("A", "B")],
                           "ops": [], "max_heap": "3G", "jvm_properties": {} if mode == "nohook" else
                           {k: v.replace(",".join(NATIVE_IDS), "<A_UUID>,<B_UUID>")
                            for k, v in properties(mode, NATIVE_IDS).items()}}))


def probes(scenes):
    result = [(f"if block {' '.join(map(str, c['pos']))} {c['block']}", f"cell-{i}-{n}")
              for i, scene in enumerate(scenes) for n, c in enumerate(scene["cells"])]
    for scene in scenes:
        x = scene["origin"][0]
        result += [(f"if block {x+4} 198 256 minecraft:air", scene["name"] + "-empty-hold")]
    result += [("if block 256 149 255 minecraft:stone", "safe-pad"),
               ("if block 320 188 256 minecraft:stone", "fall-floor"),
               ("if block 320 149 255 minecraft:air", "no-fall-origin-pad")]
    return result


def readbacks(console, scenes):
    cells, roots = [], {}
    for condition, label in probes(scenes):
        token = "K11_OK_" + label
        lines = console.send(f"execute {condition} run say {token}")
        if not any("[Server] " + token in line for line in lines):
            raise ValueError("Geometry mismatch: " + label)
        cells.append(label)
    for scene in scenes:
        for cell in scene["cells"]:
            if not cell["block"].endswith(("rocket_satellite_bay", "fluid_fuel_loader")):
                continue
            position = " ".join(map(str, cell["pos"]))
            lines = console.send("data get block " + position)
            native = [line.split("has the following block data: ", 1)[1] for line in lines
                      if "has the following block data: " in line]
            if len(native) != 1:
                raise ValueError("Missing unique native root: " + position)
            roots[position] = native[0]
    return dict(cells=cells, roots=roots)


def run(output, stage, cell_path, java, lock_owner):
    """One JVM per external lock hold; source is never the opened world."""
    from scripts import run_dedicated_server_smoke as smoke
    if stage not in STAGES:
        raise ValueError("Unknown native stage")
    if (not lock_owner.startswith("W453 pid=") or not
            (LOCK / "owner.txt").read_text(encoding="utf-8-sig").startswith(lock_owner)):
        raise ValueError("W453 heavy-JVM lock required")
    if min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) < 10 * 1024**3:
        raise ValueError("10 GiB required on C: and D:")
    if (output / "SHA256SUMS.txt").exists():
        raise ValueError("Kit is sealed")
    plan = json.loads((output / "plan.json").read_text(encoding="ascii"))
    if (plan["scenes"] != geometry() or (output / "commands.txt").read_text(encoding="ascii").splitlines()
            != build_commands(geometry()) or plan["candidate_commit"] != common.CANDIDATE
            or plan["jar_sha256"] != common.JAR_HASH):
        raise ValueError("Generated geometry, commands or candidate changed")
    parent = Path(plan["parent"]["path"]).parent
    if verify_seal(parent) != plan["parent"]:
        raise ValueError("Parent identity changed")
    source = output / "source"
    parent_world = parent / "world" if stage == "build" else source / "world"
    if stage != "build":
        verify_seal(source)
    cell = json.loads(cell_path.read_text(encoding="utf-8-sig"))
    template = Path(cell["server_dir"])
    template_before = common.tree_hashes(template)
    world_before = common.tree_hashes(parent_world)
    copy = output / (stage + "-server")
    result_path = output / (stage + ".json")
    if copy.exists() or result_path.exists() or stage == "build" and source.exists():
        raise ValueError("Refusing occupied native outputs")
    report = dict(status="FAILED", stage=stage, client_started=False, native_only=True,
                  candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH,
                  generator_commit=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=common.ROOT, text=True).strip(),
                  generator_sha256=common.file_hash(Path(__file__)), parent=plan["parent"])
    process = console = None
    try:
        shutil.copytree(template, copy, ignore=shutil.ignore_patterns("world", "logs", "crash-reports"))
        installed = list((copy / "mods").glob("*.jar"))
        if len(installed) != 1 or common.file_hash(installed[0]) != common.JAR_HASH:
            raise ValueError("Copied server is not the pinned C6-only mod set")
        shutil.copytree(parent_world, copy / "world")
        (copy / "world" / MARKER_NAME).write_bytes(MARKER)
        props = smoke.expected_server_properties(smoke.allocate_port(), False)
        props.update({"level-name": "world", "online-mode": "true", "white-list": "true",
                      "enforce-whitelist": "true", "enable-rcon": "false"})
        (copy / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
        write_json(copy / "whitelist.json", [dict(name="K11Native" + str(i), uuid=v) for i, v in enumerate(NATIVE_IDS)])
        write_json(copy / "ops.json", [])
        selected = properties("fuel" if stage != "nohook" else "nohook", NATIVE_IDS)
        argument = copy.resolve() / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        command = [str(java), "-Xms512M", "-Xmx3G", *[f"-D{k}={v}" for k, v in selected.items()], "@" + str(argument), "nogui"]
        report.update(command=command, properties=props, selection=selected,
                      native_uuid_notice="Reserved synthetic identifiers, no authenticated account or client.")
        process = smoke.CapturedProcess(command, copy, output / (stage + "-console.log"))
        process.wait_for(smoke.READY_MARKER, 240)
        console = Console(process)
        for scene in plan["scenes"]:
            x = scene["origin"][0]
            console.send(f"forceload add {x-16} 240 {x+16} 272")
            deadline = time.monotonic() + 120
            while not any("[Server] K11_READY" in v for v in console.send(
                    f"execute if loaded {x-5} 150 252 if loaded {x+5} 198 260 run say K11_READY")):
                if time.monotonic() >= deadline:
                    raise ValueError("Scene chunks not loaded")
                time.sleep(.25)
        if stage == "build":
            for value in (output / "commands.txt").read_text(encoding="ascii").splitlines():
                console.send(value)
        report["readbacks"] = readbacks(console, plan["scenes"])
        if stage != "build":
            baseline = json.loads((output / "build.json").read_text(encoding="ascii"))
            if report["readbacks"] != baseline["readbacks"]:
                raise ValueError("Restart changed scene readbacks")
        start_lines = "\n".join(process.lines)
        selection_line = "ARCE_BAY_V2_CAMPAIGN startup property="
        if (selection_line in start_lines) != (stage != "nohook"):
            raise ValueError("Unexpected campaign startup selection")
        if stage != "nohook" and not any(selection_line in v and "marker=true" in v for v in process.lines):
            raise ValueError("Campaign marker was not recognized")
        if stage == "nohook":
            # This is a command-registration refusal, NOT a fuel-attempt/menu proof.
            start = len(process.lines)
            console.commands.append("arce bay release-test pending")
            process.command(console.commands[-1])
            console.send("say K11_REFUSAL_BARRIER")
            lines = process.lines[start:]
            if not any("Unknown or incomplete command" in v or "Incorrect argument" in v for v in lines):
                raise ValueError("No-hook release-test command did not refuse")
            report["nohook_refusal"] = lines
        else:
            console.send("arce bay release-test pending")
        console.send("forceload remove all")
        console.send("save-all flush")
        process.command("stop")
        code = process.finish(180)
        process = None
        if code != 0:
            raise ValueError(f"Native exit {code}")
        log = (copy / "logs/latest.log").read_text(encoding="utf-8")
        if "/ERROR]" in log or "/FATAL]" in log or "ARCE_BAY_EXIT observer unavailable" in log:
            raise ValueError("Native errors or unavailable exit observer")
        if stage == "build":
            source.mkdir()
            shutil.copytree(copy / "world", source / "world")
            write_json(source / "manifest.json", dict(candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH,
                       parent=plan["parent"], world_hashes=common.tree_hashes(source / "world"), readbacks=report["readbacks"]))
            common.seal(source)
        report.update(status="BUILT_GEOMETRY" if stage == "build" else "MATCHED_GEOMETRY", exit_code=code)
    except BaseException as failure:
        report["error"] = str(failure)
        raise
    finally:
        if process is not None:
            common.abort_owned(process, output)
        report["commands"] = console.commands + ["stop"] if console else []
        report["template_unchanged"] = common.tree_hashes(template) == template_before
        report["parent_world_unchanged"] = common.tree_hashes(parent_world) == world_before
        write_json(result_path, report)
        if copy.exists():
            if copy.resolve().parent != output.resolve() or copy.name != stage + "-server":
                raise ValueError("Unsafe cleanup target")
            shutil.rmtree(copy)
    if not report["template_unchanged"] or not report["parent_world_unchanged"]:
        raise ValueError("Immutable input changed")
    return report


def bind_session(output, target, mode, accounts, cell_path, java):
    """Materialize attach-only W399 files from owner-declared metadata, never inspect a profile."""
    from scripts.campaign_client.protocol import load_session
    verify_seal(output / "source")
    if len(accounts) != 2 or any(set(a) != {"name", "uuid", "pid", "game_dir"} for a in accounts):
        raise ValueError("Two accounts with name/uuid/pid/game_dir only required; no credentials")
    selected = properties(mode, [a["uuid"] for a in accounts])
    # Validate names, UUIDs, distinct PIDs and game directories before copying anything.
    spec = json.loads((output / (mode + "-session.template.json")).read_text(encoding="ascii"))
    spec.update(cell=str((target / "cell.json").resolve()), run_dir=str((target / "run").resolve()), java=str(java))
    spec["clients"] = [dict(player_name=a["name"], pid=a["pid"], account_label=role, game_dir=a["game_dir"],
                            language="en_us", gui_scale=3, window_size=[1280, 720])
                       for role, a in zip(("A", "B"), accounts, strict=True)]
    spec["server"].update(whitelist=[dict(name=a["name"], uuid=a["uuid"]) for a in accounts], jvm_properties=selected)
    cell = json.loads(cell_path.read_text(encoding="utf-8-sig"))
    keys = ("schema_version", "forge", "jei", "runtime_root", "server_dir", "client_game_dir",
            "version_id", "candidate", "jei_jar")
    cell = {k: cell[k] for k in keys}
    if cell["candidate"]["sha256"] != common.JAR_HASH:
        raise ValueError("Not C6")
    target.mkdir(parents=True, exist_ok=False)
    write_json(target / "session.json", spec)
    load_session(target / "session.json")
    server = target / "server-template"
    shutil.copytree(Path(cell["server_dir"]), server, ignore=shutil.ignore_patterns("world", "logs", "crash-reports"))
    shutil.copytree(output / "source/world", server / "world")
    # Keep the same marked world in the no-property control too.
    if (server / "world" / MARKER_NAME).read_bytes() != MARKER:
        raise ValueError("Source marker changed")
    cell["server_dir"] = str(server.resolve())
    write_json(target / "cell.json", cell)
    return spec


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("generate", "run", "seal", "bind"))
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--parent", type=Path, default=PARENT)
    parser.add_argument("--stage", choices=STAGES)
    parser.add_argument("--cell", type=Path, default=common.CELL)
    parser.add_argument("--java", type=Path, default=Path("C:/Program Files/Java/jdk-17.0.7/bin/java.exe"))
    parser.add_argument("--lock-owner", default="")
    parser.add_argument("--target", type=Path)
    parser.add_argument("--accounts", type=Path, help="Owner metadata JSON list; no credentials")
    parser.add_argument("--mode", choices=("marked", "intent", "fuel", "nohook"))
    args = parser.parse_args()
    if args.action == "generate":
        generate(args.output, args.parent)
    elif args.action == "run":
        run(args.output, args.stage, args.cell, args.java, args.lock_owner)
    elif args.action == "bind":
        if not args.accounts or not args.target or not args.mode:
            parser.error("bind requires --accounts, --target and --mode")
        bind_session(args.output, args.target, args.mode, json.loads(args.accounts.read_text(encoding="utf-8-sig")),
                     args.cell, args.java)
    else:
        for stage in STAGES:
            report = json.loads((args.output / (stage + ".json")).read_text(encoding="ascii"))
            if report["status"] not in ("BUILT_GEOMETRY", "MATCHED_GEOMETRY"):
                raise ValueError("Unqualified geometry stage")
        common.seal(args.output)


if __name__ == "__main__":
    main()
