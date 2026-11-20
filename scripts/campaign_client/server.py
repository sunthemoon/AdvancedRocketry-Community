"""Configure only the disposable server copy, with no RCON credentials."""
from .evidence import write_json
from .protocol import heap_mebibytes, validate_server


def write_settings(directory, spec):
    validate_server(spec)
    path = directory / "server.properties"
    protected = {"online-mode", "white-list", "enforce-whitelist", "server-ip", "server-port", "enable-rcon",
                 "rcon.password", "rcon.port"}
    old = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    kept = []
    for line in old:
        stripped = line.lstrip()
        if not stripped or stripped.startswith(("#", "!")):
            kept.append(line)
            continue
        # Preserve escaped values (vanilla writes minecraft\:normal), but refuse
        # ambiguous key spellings and continuations rather than accepting overrides.
        words = stripped.split("=", 1)[0].split(":", 1)[0].split()
        key = words[0] if words else ""
        trailing_slashes = len(line) - len(line.rstrip("\\"))
        if not key or "\\" in key or trailing_slashes % 2:
            raise ValueError("Escaped property keys and continuations are not allowed")
        if key not in protected:
            kept.append(line)
    for key in ("online-mode", "white-list", "enforce-whitelist", "server-ip", "server-port"):
        value = spec[key]
        kept.append(f"{key}={str(value).lower() if type(value) is bool else value}")
    kept.append("enable-rcon=false")
    path.write_text("\n".join(kept) + "\n", encoding="utf-8")
    write_json(directory / "whitelist.json", spec["whitelist"])
    write_json(directory / "ops.json", [dict(p, level=4, bypassesPlayerLimit=False) for p in spec["ops"]])


def server_command(java, directory, forge, spec):
    spec = validate_server(spec)
    argument = directory / f"libraries/net/minecraftforge/forge/1.20.1-{forge}/win_args.txt"
    if not argument.is_file():
        raise ValueError("Prepared server arguments missing")
    maximum = spec["max_heap"]
    initial = "1G" if heap_mebibytes(maximum) >= 1024 else maximum
    return [str(java), "-Xms" + initial, "-Xmx" + maximum, *[f"-D{k}={v}" for k, v in spec["jvm_properties"].items()],
            "@" + str(argument), "nogui"]
