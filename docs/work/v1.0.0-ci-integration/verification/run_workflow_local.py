"""Execute the checked workflow's run commands locally; not a GitHub Actions run."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(r"D:\GitHub\AdvancedRocketry-Community")
OUT = Path(r"C:\Users\Administrator\AppData\Local\Temp\arce-v100-ci")
sys.path.insert(0, str(ROOT))
from scripts.validate_repository import parse_workflow_jobs, validate_forge_workflow_text, _run_commands


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    job_name = sys.argv[1]
    workflow = ROOT / ".github/workflows/forge-bootstrap.yml"
    text = workflow.read_text(encoding="utf-8")
    errors = validate_forge_workflow_text(text)
    if errors:
        raise RuntimeError(errors)
    job = parse_workflow_jobs(text)[job_name]
    label = sys.argv[2] if len(sys.argv) > 2 else ""
    if label not in {"", "attempt-2"}:
        raise RuntimeError("Unsupported verification attempt label")
    destination = OUT / (job_name + "-local" + ("-" + label if label else ""))
    destination.mkdir()
    environment = dict(os.environ)
    environment["REVIEW_COMMIT"] = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    environment_file = destination / "github-env.txt"
    environment_file.touch()
    environment["GITHUB_ENV"] = str(environment_file)
    if job_name == "latest-compatibility":
        environment["ORG_GRADLE_PROJECT_forge_version"] = "47.4.23"
    report = {"schema_version": 1, "job": job_name, "identity_mode": "development_worktree",
              "head": environment["REVIEW_COMMIT"], "workflow_sha256": digest(workflow),
              "remote_actions_run": False, "commands": [], "actions_not_executed": [],
              "local_platform_adaptations": ["gradlew.bat instead of ./gradlew", "explicit Python executable",
                  "chmod is not applicable on Windows", "hash-verified Forge installer cache seeded locally",
                  "continue after dirty-worktree checks only to test remaining commands independently"],
              "started_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    for relative in (".github/workflows/forge-bootstrap.yml", "gradle.properties", "scripts/ci_artifact_identity.py",
                     "scripts/validate_repository.py", "scripts/validate_bootstrap_provenance.py"):
        target = destination / "inputs" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / relative, target)
    failures = 0
    try:
        for step in job.steps:
            if "uses" in step.fields:
                report["actions_not_executed"].append(step.fields["uses"])
                continue
            for original in _run_commands(step.fields.get("run", "")):
                item = {"step": step.fields.get("name"), "workflow_argv": original}
                report["commands"].append(item)
                if original[0] == "chmod":
                    item["status"] = "NOT_APPLICABLE_WINDOWS"
                    continue
                argv = list(original)
                for index, value in enumerate(argv):
                    for key in ("ARCE_ARTIFACT", "ARCE_BUILD_VERSION", "GITHUB_ENV", "REVIEW_COMMIT"):
                        value = value.replace("${" + key + "}", environment.get(key, ""))
                    argv[index] = value
                if argv[0] == "python":
                    argv[0] = sys.executable
                elif argv[0] == "./gradlew":
                    argv[0] = str(ROOT / "gradlew.bat")
                elif argv[0] != "git":
                    raise RuntimeError("Unsupported local command: " + repr(argv))
                if "scripts/run_dedicated_server_smoke.py" in argv:
                    installer = ROOT / "build/dedicated-server-smoke/cache/forge-1.20.1-47.4.10-installer.jar"
                    installer.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(OUT.parent / "arce-v100-compatibility/runtime-1/forge-47.4.10-installer.jar", installer)
                    if hashlib.sha1(installer.read_bytes()).hexdigest() != "66bfea9963bfa60d88bab6b2750e74a958392715":
                        raise RuntimeError("Cached installer hash differs")
                log = destination / (f"{len(report['commands']):02d}-command.txt")
                item.update({"actual_argv": argv, "log": log.name, "started_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())})
                print(job_name, item["step"], flush=True)
                start = time.monotonic()
                with log.open("wb") as handle:
                    result = subprocess.run(argv, cwd=ROOT, env=environment, stdout=handle, stderr=subprocess.STDOUT)
                item.update({"exit_code": result.returncode, "seconds": round(time.monotonic() - start, 3),
                             "log_sha256": digest(log), "status": "PASS" if result.returncode == 0 else "FAIL"})
                print("exit", result.returncode, "seconds", item["seconds"], flush=True)
                (destination / "summary.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
                if "scripts/ci_artifact_identity.py" in argv and result.returncode == 0:
                    for line in environment_file.read_text(encoding="utf-8").splitlines():
                        key, _, value = line.partition("=")
                        if key not in {"ARCE_ARTIFACT", "ARCE_BUILD_VERSION", "ARCE_SOURCES"}:
                            raise RuntimeError("Unexpected environment key")
                        environment[key] = value
                if result.returncode:
                    failures += 1
                    if original not in (("git", "diff", "--exit-code"), ("python", "scripts/check_clean_worktree.py")):
                        break
            else:
                continue
            break
    finally:
        uploads = next(step for step in job.steps if step.fields.get("uses") == "actions/upload-artifact@v7")
        files = []
        for relative in uploads.fields["with.path"].splitlines():
            relative = relative.strip()
            for key in ("ARCE_ARTIFACT", "ARCE_SOURCES"):
                relative = relative.replace("${{ env." + key + " }}", environment.get(key, "missing-output"))
            sources = sorted(ROOT.glob(relative)) if "*" in relative else [ROOT / relative]
            for source in sources:
                for path in sorted(source.rglob("*") if source.is_dir() else [source]):
                    if not path.is_file():
                        continue
                    name = path.relative_to(ROOT).as_posix()
                    files.append({"path": name, "bytes": path.stat().st_size, "sha256": digest(path)})
                    if path.suffix != ".jar":
                        target = destination / "outputs" / name
                        target.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copyfile(path, target)
        def nodes(value):
            if isinstance(value, dict):
                yield value
                for child in value.values():
                    yield from nodes(child)
            elif isinstance(value, list):
                for child in value:
                    yield from nodes(child)
        missing = []
        for document_path in (destination / "outputs/build").glob("*/evidence/summary.json"):
            document = json.loads(document_path.read_text(encoding="utf-8"))
            for cycle in nodes(document):
                if "full_log_file" in cycle and not any(
                    Path(item["path"]).name == cycle["full_log_file"]
                    and item["sha256"] == cycle["full_log_sha256"] for item in files
                ):
                    missing.append({"summary": document_path.relative_to(destination).as_posix(),
                                    "log": cycle["full_log_file"], "sha256": cycle["full_log_sha256"]})
        report.update({"upload_file_inventory": files, "failed_commands": failures,
                       "archive_complete": not missing, "missing_full_logs": missing,
                       "completed_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())})
        (destination / "summary.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        if missing:
            raise RuntimeError("Referenced full logs are missing from the upload archive; preserve the runtime")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
