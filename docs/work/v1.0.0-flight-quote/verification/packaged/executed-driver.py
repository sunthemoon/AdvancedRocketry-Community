"""One baseline packaged connection/restart check for the quote update."""
from pathlib import Path
from types import SimpleNamespace
import shutil
import sys

ROOT = Path(r'D:\GitHub\AdvancedRocketry-Community')
sys.path.insert(0, str(ROOT))
from scripts import run_v100_compatibility_matrix as matrix
from scripts import v100_compatibility_runtime as runtime
from scripts import run_dedicated_server_smoke as server
from scripts.validate_build_artifact import validate_artifact

args = SimpleNamespace(
    runtime=Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-v100-compatibility\runtime-1'),
    work=Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-v100-flight-quote-smoke'),
    evidence=ROOT / 'docs/work/v1.0.0-flight-quote/verification/packaged',
    artifact=ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar',
    version='1.20.1-1.0.0-dev',
    java=r'C:\Program Files\Java\jdk-17.0.7\bin\java.exe',
)
summary = dict(status='FAIL', started_at=matrix.now(), clients=[], servers=[], owned_processes=[],
               scope='one baseline connection and dedicated restart; not GUI or release acceptance',
               authentication_tested=False, source_state='development_worktree')
if args.work.exists() or args.evidence.exists():
    raise SystemExit('Refusing to overwrite an existing execution')
args.work.mkdir()
args.evidence.mkdir()
try:
    args.java, summary['java_version'] = server.resolve_java(args.java)
    errors, details = validate_artifact(args.artifact, args.version)
    if errors:
        raise server.SmokeError(str(errors))
    summary['artifact'] = details
    summary['source_inputs'] = [dict(path=str(path.relative_to(ROOT)), sha256=server.digest_file(path))
        for path in (Path(matrix.__file__), Path(runtime.__file__), Path(server.__file__))]
    shutil.copy2(__file__, args.evidence / 'executed-driver.py')
    matrix.CELLS = (('47.4.10', False, 'V100Quote'),)
    matrix.run_lane(args, '47.4.10', args.artifact, summary)
    if server.digest_file(args.artifact) != details['sha256']:
        raise server.SmokeError('Input artifact changed during smoke')
    summary['status'] = 'PASS'
    print('PASS: packaged client and dedicated first start/restart; no GUI approval', flush=True)
finally:
    summary['completed_at'] = matrix.now()
    runtime.write_json(args.evidence / 'summary.json', summary)
