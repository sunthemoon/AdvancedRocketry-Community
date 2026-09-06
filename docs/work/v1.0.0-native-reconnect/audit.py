"""Audit controlled native reconnect observations without claiming real disk latency."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import sys

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
sys.path.insert(0, str(ROOT))
from scripts.run_dedicated_server_smoke import scan_log

NATIVE = OUT / "native"
WORK = Path(r"C:\Users\Administrator\AppData\Local\Temp\arce-native-reconnect")
OWNER = "62bbb9cb-b2fa-39aa-9a6b-430b71f5493f"
PASSENGER = "6482f9f6-ff26-3046-b7ba-86db04b972f8"
ROCKET = "2c03e129-ca61-4718-9453-39f666e94d65"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


summary = json.loads((NATIVE / "summary.json").read_text())
assert summary["status"] == "COMPLETE_WITH_OBSERVATIONS" and not summary["cleanup_errors"]
assert not any("error" in item for item in summary["actions"])
assert len(summary["auto_logout"]) == 1 and summary["auto_logout"][0]["command_sent"]
for process in summary["processes"]:
    assert process["exit_code"] == 0
    assert sha(NATIVE / process["full_log_file"]) == process["full_log_sha256"]
    for log in process.get("native_logs", []):
        assert sha(NATIVE / log["file"]) == log["sha256"]
    if process["kind"] != "probe":
        assert not scan_log((NATIVE / process["full_log_file"]).read_text(encoding="utf-8").splitlines())
for screenshot in summary["screenshots"]:
    assert sha(NATIVE / screenshot["file"]) == screenshot["sha256"]
lines = (NATIVE / "probe-full.txt").read_text(encoding="utf-8").splitlines()
assert "PROBE_STOP requested=true" in lines[-1]
events = []
for line in lines:
    match = re.fullmatch(r"EVENT seq=(\d+) ms=(\d+) (\S+)\s*(.*)", line)
    assert match, line
    seq, ms, kind, detail = match.groups()
    events.append(dict(seq=int(seq), ms=int(ms), kind=kind, detail=detail))
assert [e["seq"] for e in events] == list(range(1, len(events) + 1))


def selected(kind, *terms):
    return [e for e in events if e["kind"] == kind and all(t in e["detail"] for t in terms)]


for player in (OWNER, PASSENGER):
    reads = selected("READINESS", "uuid=" + player, "mode=delay")
    assert len(reads) == 5
    assert all("forced_false=true" in r["detail"] for r in reads[:4])
    assert "forced_false=false" in reads[-1]["detail"]
    assert all("method=m_143319_" in r["detail"] for r in reads)
    assert all("f_19825_:f_82479_=288.5,f_82480_=64.15,f_82481_=-127.5," in r["detail"] for r in reads)
    movement = selected("MOVE", "uuid=" + player)[0]
    assert movement["seq"] > reads[-1]["seq"] and "rocket=" + ROCKET in movement["detail"]
    complete = selected("COMPLETE", "uuid=" + player, "caller=tickReconnects")[0]
    assert complete["seq"] > movement["seq"]
    assert any(e["seq"] > complete["seq"] for e in selected("QUEUE_NEXT", "size=0"))
held = selected("READINESS", "mode=hold-owner")
assert len(held) == 4 and all("forced_false=true" in e["detail"] for e in held)
logout = next(e for e in selected("LOGOUT", "uuid=" + OWNER) if e["seq"] > held[-1]["seq"])
cleanup = next(e for e in selected("COMPLETE", "uuid=" + OWNER, "caller=onPlayerLoggedOut")
               if e["seq"] > logout["seq"])
empty = next(e for e in selected("QUEUE_NEXT", "size=0") if e["seq"] > cleanup["seq"])
assert not any(held[0]["seq"] < e["seq"] < empty["seq"] for e in selected("MOVE"))
assert len(selected("MOVE", "uuid=" + OWNER)) == 2
assert len(selected("MOVE", "uuid=" + PASSENGER)) == 1
assert selected("READINESS", "mode=open")[0]["seq"] > empty["seq"]
assert len(selected("OFFER", "uuid=" + OWNER)) == 2
assert len(selected("OFFER", "uuid=" + PASSENGER)) == 1

world = json.loads((OUT / "saved-world/projection.json").read_text())
before = json.loads((ROOT / "docs/work/v1.0.0-passenger-readiness/verification/resaved-world/projection.json").read_text())
assert world["rockets"] == before["rockets"] and world["world_owned_only"]
seed = Path(r"C:\Users\Administrator\AppData\Local\Temp\arce-passenger-readiness-server\world")
seed_manifest = json.loads((OUT / "seed-world-manifest.json").read_text())
assert {r["path"] for r in seed_manifest} == {p.relative_to(seed).as_posix() for p in seed.rglob("*") if p.is_file()}
for row in seed_manifest:
    assert sha(seed / row["path"]) == row["sha256"]
inventory = json.loads((ROOT / "docs/work/v1.0.0-passenger-logout/source-inventory.json").read_text())
for row in inventory["files"]:
    assert sha(ROOT / row["path"]) == row["sha256"], row["path"]
assert sha(ROOT / "build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar") == summary["artifact_sha256"] == inventory["artifact_sha256"]
for directory in ("server", "client", "client-2"):
    jars = list((WORK / directory / "mods").glob("*.jar"))
    assert len(jars) == 1 and sha(jars[0]) == summary["artifact_sha256"], directory
shutil.copy2(ROOT / "docs/work/v1.0.0-passenger-logout/source-inventory.json", OUT / "source-inventory.json")
requests = NATIVE / "requests"
requests.mkdir(exist_ok=True)
for path in (WORK / "requests").glob("*.json"):
    shutil.copy2(path, requests / path.name)
server_lines = (NATIVE / "server-full.txt").read_text(encoding="utf-8").splitlines()
result = dict(status="PASS_CONTROLLED_NATIVE_QUEUE", driver_exit=0,
    process_exits={p["kind"]: p["exit_code"] for p in summary["processes"]},
    readiness_false_counts=dict(owner_initial=4, passenger_initial=4, owner_pending_logout=4),
    moves=dict(owner=2, passenger=1), saved_rocket_projection_equal=True,
    original_seed_files_unchanged=len(seed_manifest), source_files=len(inventory["files"]),
    screenshots=len(summary["screenshots"]), retained_server_lag_warnings=sum("Can't keep up!" in l for l in server_lines),
    genuine_disk_loading_order="NOT_TESTED", expiry="NOT_TESTED", performance="NOT_MEASURED")
(OUT / "audit-result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, indent=2))
