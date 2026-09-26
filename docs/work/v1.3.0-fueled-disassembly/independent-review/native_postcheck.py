"""Read-only ROCKET-04 evidence audit; writes results only beside this helper."""
from pathlib import Path
import gzip
import hashlib
import json
import re
import sys
import zipfile

sys.dont_write_bytecode = True
OUT = Path(__file__).resolve().parent
BASE = Path("C:/Users/Administrator/AppData/Local/Temp/arce-v130-fueled-disassembly-1790442426341")
EVIDENCE = BASE / "flight-evidence"
PRIOR = Path("C:/Users/Administrator/AppData/Local/Temp/arce-v130-adapter-flight-corrected-cb65df26cfb34e3ca448c0979a5e6196/independent_postcheck.py")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def adapt_and_run_prior_native_audit():
    # Keep the prior independent region/journal/payload algorithms. Adapt only
    # input location, the new artifact manifest shape, and the output location.
    code = PRIOR.read_text(encoding="utf-8")
    changes = {
        'BASE = Path(__file__).resolve().parent': f'BASE = Path({str(BASE)!r})',
        'identities = json.loads((BASE / "artifact-identities.json").read_text(encoding="utf-8-sig"))':
            'identities = {"artifacts": [{"copy": item["path"], "sha256": item["sha256"]} for item in summary["artifacts"]]}',
        '(BASE / "independent-observations.json").open':
            f'(Path({str(OUT)!r}) / "native-observations.json").open',
    }
    for old, new in changes.items():
        assert code.count(old) == 1, old
        code = code.replace(old, new)
    adapted = OUT / "adapted-native-postcheck.py"
    adapted.write_text(code, encoding="utf-8")
    namespace = {"__name__": "readonly_native_review", "__file__": str(adapted)}
    exec(compile(code, str(adapted), "exec"), namespace)
    namespace["main"]()
    return namespace


def main():
    native = adapt_and_run_prior_native_audit()
    summary = load(EVIDENCE / "summary.json")
    identities = load(BASE / "artifact-identities.json")
    tested = load(OUT / "comparison.json")
    assert summary["result"] == "PASS"
    assert load(BASE / "runner-exit.json")["exit_code"] == 0
    assert identities["source_commit"] == tested["commit"]
    expected_artifacts = {Path(item["path"]).name: item["sha256"] for item in tested["artifacts"]}
    for item in identities["artifacts"]:
        assert digest(BASE / "artifacts" / item["name"]) == item["sha256"] == expected_artifacts[item["name"]]
    # Verify the retained notice-only packaging refresh from actual ZIP entries.
    refresh = {}
    for old in (BASE / "pre-notice-artifacts").glob("*.jar"):
        new = BASE / "artifacts" / old.name
        with zipfile.ZipFile(old) as a, zipfile.ZipFile(new) as b:
            assert set(a.namelist()) == set(b.namelist())
            changed = [name for name in a.namelist() if a.read(name) != b.read(name)]
        assert changed == ["META-INF/THIRD-PARTY-NOTICES.md"], (old.name, changed)
        refresh[old.name] = changed
    assert len(refresh) == 3
    # Validate every archived checksum against actual bytes, not disk-state.json.
    manifest_entries = []
    for line in (EVIDENCE / "SHA256SUMS").read_text().splitlines():
        sha, relative = line.split("  ", 1)
        path = (EVIDENCE / relative).resolve()
        assert path.is_relative_to(EVIDENCE.resolve()) and path.is_file()
        assert digest(path) == sha, relative
        manifest_entries.append(relative)
    assert len(manifest_entries) == len(set(manifest_entries))
    assert set(manifest_entries) == {p.relative_to(EVIDENCE).as_posix() for p in EVIDENCE.rglob("*")
                                    if p.is_file() and p.name != "SHA256SUMS"}
    configs = None
    phase_records = []
    for cycle in summary["cycles"]:
        folder = EVIDENCE / cycle["phase"]
        assert cycle == load(folder / "result.json")
        assert cycle["result"] == "PASS" and cycle["exit_code"] == 0
        launch = load(folder / "launch.json")
        assert Path(launch["cwd"]).resolve() == (BASE / "server").resolve()
        assert "-Dadvancedrocketrycommunity.releaseTestHooks=true" in launch["command"]
        assert launch["mods"] == {a["name"]: a["sha256"] for a in summary["artifacts"]}
        stdout = (folder / "stdout.txt").read_text()
        assert len(re.findall(r"Done \([^\n]+For help", stdout)) == 1
        assert stdout.count("Stopping server") == 1
        current = {}
        for name, sha in cycle["configuration_sha256"].items():
            content = (folder / name).read_bytes()
            assert hashlib.sha256(content).hexdigest() == sha
            if name == "server.properties":
                lines = content.splitlines(keepends=True)
                assert lines[0] == b"#Minecraft server properties\r\n"
                assert re.fullmatch(rb"#[A-Za-z]{3} [A-Za-z]{3} \d{2} \d{2}:\d{2}:\d{2} [A-Za-z]+ \d{4}\r\n", lines[1])
                content = lines[0] + b"".join(lines[2:])
            current[name] = hashlib.sha256(content).hexdigest()
        assert configs is None or configs == current
        configs = current
        phase_records.append({"phase": cycle["phase"], "exit_code": cycle["exit_code"],
                              "observation_ticks": cycle["observation_ticks"]})
    # ROCKET-04-specific raw refusal and explicit-disposal evidence.
    folder = EVIDENCE / "disassembly"
    lines = (folder / "stdout.txt").read_text().splitlines()
    record = native["read_saved"](EVIDENCE / "earth-landing/world/data/advancedrocketrycommunity_rocket_transfers.dat", "transfers")["transfers"][0]
    entity = native["nbt_uuid"](record["destination_entity_id"])
    logical = native["nbt_uuid"](record["logical_rocket_id"])
    transfer = native["nbt_uuid"](record["transfer_id"])
    def one(marker):
        hits = [(i, line) for i, line in enumerate(lines) if marker in line]
        assert len(hits) == 1, (marker, hits)
        return hits[0]
    implicit = one("Release-test disassembly failed: FUEL_DISPOSAL_REQUIRED")
    mismatch = one("Release-test disassembly failed: WORLD_CHANGED")
    removal = one(f"ARCE_ROCKET_ENTITY_REMOVED entity={entity} reason=DISCARDED operational=true")
    disposal = one(f"ARCE_ROCKET_FUEL_DISCARDED entity={entity} logical={logical} amount=256 reason=confirmed_disassembly")
    committed = one(f"ARCE_RELEASE_TEST_DISASSEMBLY entity={entity} logical={logical} code=SUCCESS blocks=5 rolled_back=0")
    assert implicit[0] < mismatch[0] < removal[0] < disposal[0] < committed[0]
    assert one(f"event=landed_reservation_released entity={entity}")[0] < disposal[0]
    snbt = [(i, line.split("has the following entity data:", 1)[1].strip())
            for i, line in enumerate(lines) if "has the following entity data:" in line]
    assert len(snbt) == 2 and snbt[0][1] == snbt[1][1]
    assert snbt[0][1] == (EVIDENCE / "earth-landing/RocketEntityData.snbt").read_text().strip()
    assert snbt[0][0] < implicit[0] < mismatch[0] < snbt[1][0] < removal[0]
    inspect = [line.split("Transfer ", 1)[1] for line in lines if f"Transfer {transfer} logical={logical} phase=COMMITTED" in line]
    assert len(inspect) == 2 and inspect[0] == inspect[1]
    assert f"checksum={record['checksum']}" in inspect[0]
    for marker in ("DISPOSAL_REJECTED_COUNT", "DISPOSAL_REJECTED_0", "DISPOSAL_REJECTED_1", "DISPOSAL_REJECTED_2"):
        assert mismatch[0] < one(f"[Server] {marker}")[0] < removal[0]
    commands = load(folder / "commands.json")
    disassemble = [c for c in commands if " release-test disassemble " in c]
    base_command = f"execute in minecraft:overworld run arce rocket release-test disassemble {entity}"
    assert disassemble == [base_command, base_command + " discard-fuel 257", base_command + " discard-fuel 256"]
    assert load(folder / "result.json")["explicit_fuel_disposal"] == 256
    report = {"result": "PASS", "runtime_base": str(BASE), "reviewed_commit": tested["commit"],
              "prior_helper_sha256": digest(PRIOR), "adapted_helper_sha256": digest(OUT / "adapted-native-postcheck.py"),
              "manifest_files_verified": len(manifest_entries), "manifest_sha256": digest(EVIDENCE / "SHA256SUMS"),
              "artifacts": expected_artifacts, "notice_only_refresh_verified": refresh,
              "phases": phase_records, "logical": logical, "final_entity": entity, "return_transfer": transfer,
              "implicit_refusal": implicit[1], "wrong_amount_refusal": mismatch[1], "exact_disposal": disposal[1],
              "successful_disassembly": committed[1], "rejection_entity_snbt_unchanged": True,
              "rejection_transfer_inspection_unchanged": True,
              "scope": "One operator-hook round trip and explicit fuel disposal; no real-player confirmation/V1/V2 or arbitrary-crash proof"}
    (OUT / "native-review.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
