"""Evidence serialization and credential redaction, shared by all output paths."""
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

MASK = "[REDACTED]"
SECRETS = {"accesstoken", "clientid", "xuid"}
VALUE = r'''(?:\[REDACTED\]|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^\s,;}\]]+)'''
FIELDS = re.compile(r'''(?i)((?:["']?(?:accessToken|clientId|xuid)["']?)\s*[:=]\s*)''' + VALUE)
TOKEN = re.compile(r"(?i)(--(?:accessToken|clientId|xuid)(?:\s*[,=]\s*|\s+))" + VALUE)


def now():
    return datetime.now(timezone.utc).isoformat()


def redact(text):
    return FIELDS.sub(lambda m: m[1] + '"' + MASK + '"',
                      TOKEN.sub(lambda m: m[1] + MASK, text))


def sanitized(value):
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, dict):
        return {k: MASK if k.lower() in SECRETS else sanitized(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [sanitized(v) for v in value]
    return redact(value) if isinstance(value, str) else value


def dumps(value):
    return json.dumps(sanitized(value), ensure_ascii=False, allow_nan=False)


def write_json(path, value):
    path = Path(path)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(dumps(value) + "\n", encoding="utf-8")
    temporary.replace(path)


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def copy_log(source, target):
    """Never copy raw client/server logs, even transiently, into evidence."""
    remaining = Path(source).stat().st_size
    if remaining > 512 * 1024 * 1024:
        raise ValueError("Log snapshot exceeds 512 MiB")
    with Path(source).open("rb") as src, \
            Path(target).open("x", encoding="utf-8") as dst:
        # A running attach client may append forever; consume a bounded snapshot.
        while remaining > 0 and (raw := src.readline(min(65536, remaining))):
            # Refuse oversized lines rather than splitting a credential at a chunk boundary.
            if len(raw) == 65536 and not raw.endswith(b"\n"):
                raise ValueError("Log line exceeds 64 KiB redaction bound")
            dst.write(redact(raw.decode("utf-8", errors="replace")))
            remaining -= len(raw)
