"""Narrow JDK diagnostic API. No process discovery and no argument-printing calls."""

from dataclasses import dataclass
import json
import re
import subprocess

from .common import child, safe_path
from .evidence import check_free_space
from .process import valid_pid
from .ticks import percentile

JCMD_ALLOWLIST = frozenset(("GC.heap_info", "GC.class_histogram", "JFR.start", "JFR.stop"))


def check_jcmd_command(command):
    if command not in JCMD_ALLOWLIST:
        raise ValueError(f"jcmd command refused: {command}")
    return command


@dataclass(frozen=True)
class CampaignJvm:
    """The harness supplies its own Popen handle, not a discovered/owner PID.

    Retain the handle for the entire run. poll() checks its lifetime before every
    attachment. This is an integration trust boundary, not OS proof of ownership.
    """
    process: object
    role: str

    @property
    def pid(self):
        return valid_pid(self.process.pid)

    def require_live(self, *, server=False):
        if self.role not in ("server", "client") or (server and self.role != "server"):
            raise ValueError("JFR requires a campaign-started server")
        if self.process.poll() is not None:
            raise ValueError("Campaign JVM has exited")
        return self.pid


def _execute(target, command, options=(), *, jcmd="jcmd", runner=subprocess.run):
    check_jcmd_command(command)
    pid = target.require_live(server=command.startswith("JFR."))
    result = runner([str(jcmd), str(pid), command, *options], capture_output=True, text=True,
                    timeout=30, check=True)
    if re.search(r"(?im)^(?:error|exception|could not|unknown diagnostic command)|\bAttachNotSupportedException\b", result.stdout):
        raise ValueError("JDK diagnostic command reported an error")
    expected = {"JFR.start": "Started recording", "JFR.stop": "Stopped recording"}.get(command)
    if expected and expected not in result.stdout:
        raise ValueError("JFR command did not confirm its requested action")
    return result.stdout


def heap_info(target, **kwargs):
    return _execute(target, "GC.heap_info", **kwargs)


def class_histogram(target, **kwargs):
    # -all avoids requesting a full GC. This is an explicit, intrusive observation.
    return _execute(target, "GC.class_histogram", ("-all",), **kwargs)


def recording_path(root, relative):
    path = child(root, relative)
    if path.suffix != ".jfr" or not path.parent.is_dir() or path.exists():
        raise ValueError("JFR output must be a new .jfr file in an existing evidence folder")
    if any(c in str(path) for c in '\"\'\n\r'):
        raise ValueError("Unrepresentable JFR filename")
    return path


def start_jfr(target, root, relative, *, name="campaign", duration_s=600, max_mib=128, **kwargs):
    target.require_live(server=True)
    if re.fullmatch(r"[a-zA-Z0-9_-]{1,64}", name) is None:
        raise ValueError("Invalid recording name")
    if type(duration_s) is not int or not 1 <= duration_s <= 14400:
        raise ValueError("Recording duration must be 1 to 14400 seconds")
    if type(max_mib) is not int or not 16 <= max_mib <= 256:
        raise ValueError("Recording retention must be 16 to 256 MiB")
    path = recording_path(root, relative)
    check_free_space((path,))
    return _execute(target, "JFR.start", (f"name={name}", "settings=profile", f"duration={duration_s}s",
                    f"maxsize={max_mib}M", "disk=true", "dumponexit=true", f'filename="{path.as_posix()}"'), **kwargs)


def stop_jfr(target, root, relative, *, name="campaign", **kwargs):
    target.require_live(server=True)
    if re.fullmatch(r"[a-zA-Z0-9_-]{1,64}", name) is None:
        raise ValueError("Invalid recording name")
    path = recording_path(root, relative)
    check_free_space((path,))
    return _execute(target, "JFR.stop", (f"name={name}", f'filename="{path.as_posix()}"'), **kwargs)


def duration_ms(value):
    """jfr print JSON encodes java.time.Duration as ISO-8601, not bare numbers."""
    match = re.fullmatch(r"PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?", value)
    if match is None or not any(match.groups()):
        raise ValueError(f"Unsupported JFR duration: {value}")
    h, m, s = (float(x or 0) for x in match.groups())
    return (h * 3600 + m * 60 + s) * 1000


def summarize_jfr_json(document):
    events = document["recording"]["events"]
    pauses, ticks = [], []
    for event in events:
        if event["type"] == "jdk.GCPhasePause":
            # Exclude Level1/2/etc to avoid double counting nested phases.
            pauses.append(duration_ms(event["values"]["duration"]))
        elif event["type"] == "minecraft.ServerTickTime":
            ticks.append(duration_ms(event["values"]["averageTickDuration"]))
    return {"gc_pauses": {"count": len(pauses), "total_ms": sum(pauses), "max_ms": max(pauses, default=None)},
            "minecraft_tick_events": {"count": len(ticks), "mean_ms": sum(ticks) / len(ticks) if ticks else None,
                "p95_ms": percentile(ticks, 95), "p99_ms": percentile(ticks, 99), "max_ms": max(ticks, default=None),
                "basis": "event averages, not individual ticks; absent unless enabled by Minecraft"}}


def summarize_recording(recording, json_output, *, jfr="jfr", runner=subprocess.run):
    """Offline only. Preserve filtered JSON; never attach to its source JVM."""
    recording = safe_path(recording)
    if not recording.is_file():
        raise ValueError("Recording does not exist")
    with safe_path(json_output).open("x", encoding="utf-8") as stream:
        runner([str(jfr), "print", "--json", "--events", "jdk.GCPhasePause,minecraft.ServerTickTime",
                str(recording)], stdout=stream, stderr=subprocess.PIPE, text=True, timeout=120, check=True)
    return summarize_jfr_json(json.loads(safe_path(json_output).read_text(encoding="utf-8")))
