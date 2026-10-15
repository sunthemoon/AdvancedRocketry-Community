#!/usr/bin/env python3
"""Reject references from common and server bytecode to client-only classes.

``check_client_imports.py`` reads Java source text. This checker reads what ships: the
class files of the built JAR, compiler-generated lambdas and nested classes included.

* A class is *client-only* when its internal name starts with one of
  ``CLIENT_ONLY_PREFIXES``. The four Minecraft prefixes hold exactly the classes that
  Mojang's 1.20.1 client mappings list and its server mappings do not. Forge's
  ``net/minecraftforge/client/`` package is treated as client-only as a whole.
* A class of the JAR is *client-bound* when it references a client-only or a
  client-bound class, transitively, through an edge that the allowlist does not hold.
* A class is in a *client package* when one of its package segments is ``client``, the
  rule of ``check_client_imports.py``. Such a class may reference anything. A
  client-package class that is not client-bound is data only: common code may use it.
* Every other class of the JAR is common or server code. Each of its references to a
  client-only or client-bound class fails, unless the allowlist holds the pair. An
  allowlisted reference is a reviewed site that runs only on the client, so it does not
  make its source client-bound. An allowlist entry that matches no reference fails too.

A reference is any class that the constant pool names, except a nested or enclosing
class that only the ``InnerClasses``, ``EnclosingMethod``, ``NestHost``, ``NestMembers``
or ``PermittedSubclasses`` metadata names. Every class in a descriptor is a reference too:
in the class's own fields and methods, and in its ``NameAndType`` and ``MethodType``
constants. Generic signatures, annotations and string constants are not references: the
JVM loads no class for them when it links the class or runs its code; only reflection
does. Calls to vanilla members marked ``@OnlyIn(Dist.CLIENT)`` are out of scope, because
finding them needs the mapped Minecraft JAR.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import sys
import zipfile
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path


CLIENT_ONLY_PREFIXES = (
    "net/minecraft/client/",
    "net/minecraft/realms/",
    "com/mojang/blaze3d/",
    "com/mojang/realmsclient/",
    "net/minecraftforge/client/",
)
DEFAULT_ALLOWLIST = Path(__file__).with_name("client_bytecode_allowlist.txt")
MAX_CLASS_BYTES = 4 * 1024 * 1024
MAX_CLASSES = 100_000

OBJECT_TYPE = re.compile(r"L([^;<>]+);")
PATTERN = re.compile(r"[A-Za-z_$][\w$]*(?:/[A-Za-z_$][\w$]*)*(?:/\*\*|/\*)?")

# Operand bytes of each fixed-length opcode up to jsr_w (0xc9); the rest of 0x00 to 0xc9 take
# none. tableswitch (0xaa), lookupswitch (0xab) and wide (0xc4) are decoded separately.
_OPERAND_BYTES = {
    **{opcode: 1 for opcode in (0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19,
                                0x36, 0x37, 0x38, 0x39, 0x3A, 0xA9, 0xBC)},
    **{opcode: 2 for opcode in (0x11, 0x13, 0x14, 0x84, *range(0x99, 0xA9), *range(0xB2, 0xB9),
                                0xBB, 0xBD, 0xC0, 0xC1, 0xC6, 0xC7)},
    0xC5: 3,
    **{opcode: 4 for opcode in (0xB9, 0xBA, 0xC8, 0xC9)},
}
# new, anewarray, checkcast, instanceof and multianewarray name a class; ldc and ldc_w may.
_CLASS_OPERAND = {0xBB, 0xBD, 0xC0, 0xC1, 0xC5}
_LOADABLE_OPERAND = {0x12, 0x13}


class ClassFormatError(ValueError):
    """The bytes are not a class file this reader understands."""


class AllowlistError(ValueError):
    """The allowlist file is malformed."""


@dataclass(frozen=True)
class ClassFile:
    name: str
    references: frozenset[str]


@dataclass(frozen=True)
class AllowEntry:
    line: int
    source: str
    target: str
    reason: str

    def matches(self, source: str, target: str) -> bool:
        return _matches(self.source, source) and _matches(self.target, target)


@dataclass
class ScanResult:
    classes: int = 0
    client_package_classes: int = 0
    client_bound: list[str] = field(default_factory=list)
    violations: list[dict] = field(default_factory=list)
    allowed: list[dict] = field(default_factory=list)
    unused_allowlist: list[dict] = field(default_factory=list)
    data_only_references: list[dict] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        return not (self.violations or self.unused_allowlist or self.errors)


class _Reader:
    def __init__(self, data: bytes) -> None:
        self.data = data
        self.offset = 0

    def take(self, size: int) -> bytes:
        end = self.offset + size
        if size < 0 or end > len(self.data):
            raise ClassFormatError("truncated class file")
        chunk = self.data[self.offset:end]
        self.offset = end
        return chunk

    def u1(self) -> int:
        return self.take(1)[0]

    def u2(self) -> int:
        return struct.unpack(">H", self.take(2))[0]

    def u4(self) -> int:
        return struct.unpack(">I", self.take(4))[0]


def descriptor_classes(descriptor: str) -> set[str]:
    """Return the classes named in a field or method descriptor or an array class name."""
    return set(OBJECT_TYPE.findall(descriptor))


def parse_class(data: bytes) -> ClassFile:
    """Read a class file and return its name and the classes it references."""
    reader = _Reader(data)
    if reader.u4() != 0xCAFEBABE:
        raise ClassFormatError("bad magic number")
    reader.u2()
    major = reader.u2()
    if major < 45 or major > 70:
        raise ClassFormatError(f"unsupported class file version {major}")

    count = reader.u2()
    pool: list[tuple | None] = [None] * count
    index = 1
    while index < count:
        tag = reader.u1()
        if tag == 1:
            raw = reader.take(reader.u2())
            pool[index] = ("utf8", raw.decode("utf-8", errors="surrogateescape"))
        elif tag in (3, 4):
            reader.take(4)
            pool[index] = ("number",)
        elif tag in (5, 6):
            reader.take(8)
            pool[index] = ("number",)
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = ({7: "class", 8: "string", 16: "methodtype", 19: "module", 20: "package"}[tag],
                           reader.u2())
        elif tag in (9, 10, 11):
            pool[index] = ("member", reader.u2(), reader.u2())
        elif tag == 12:
            pool[index] = ("nameandtype", reader.u2(), reader.u2())
        elif tag == 15:
            reader.u1()
            pool[index] = ("methodhandle", reader.u2())
        elif tag in (17, 18):
            pool[index] = ("dynamic", reader.u2(), reader.u2())
        else:
            raise ClassFormatError(f"unknown constant pool tag {tag} at {index}")
        index += 1

    def utf8(position: int) -> str:
        if not 0 < position < count or pool[position] is None or pool[position][0] != "utf8":
            raise ClassFormatError(f"constant {position} is not UTF-8")
        return pool[position][1]

    def check_class(position: int) -> int:
        if not 0 < position < count or pool[position] is None or pool[position][0] != "class":
            raise ClassFormatError(f"constant {position} is not a class")
        return position

    used: set[int] = set()
    metadata: set[int] = set()
    descriptors: list[str] = []

    reader.u2()
    this_index = check_class(reader.u2())
    used.add(this_index)
    super_index = reader.u2()
    if super_index:
        used.add(check_class(super_index))
    for _ in range(reader.u2()):
        used.add(check_class(reader.u2()))

    for _ in range(2):
        for _ in range(reader.u2()):
            reader.u2()
            reader.u2()
            descriptors.append(utf8(reader.u2()))
            _read_attributes(reader, utf8, check_class, used, metadata, count)
    _read_attributes(reader, utf8, check_class, used, metadata, count)
    if reader.offset != len(data):
        raise ClassFormatError("trailing bytes after the class file")

    for entry in pool:
        if entry is None:
            continue
        if entry[0] == "member":
            used.add(check_class(entry[1]))
        elif entry[0] == "nameandtype":
            descriptors.append(utf8(entry[2]))
        elif entry[0] == "methodtype":
            descriptors.append(utf8(entry[1]))

    name = utf8(pool[this_index][1])
    references: set[str] = set()
    for position, entry in enumerate(pool):
        if entry is None or entry[0] != "class":
            continue
        if position in metadata and position not in used:
            continue
        class_name = utf8(entry[1])
        if class_name.startswith("["):
            references.update(descriptor_classes(class_name))
        else:
            references.add(class_name)
    for descriptor in descriptors:
        references.update(descriptor_classes(descriptor))
    references.discard(name)
    return ClassFile(name, frozenset(references))


def _read_attributes(reader, utf8, check_class, used, metadata, count) -> None:
    for _ in range(reader.u2()):
        name = utf8(reader.u2())
        body = _Reader(reader.take(reader.u4()))
        if name == "Code":
            _read_code(body, utf8, check_class, used, metadata, count)
        elif name == "StackMapTable":
            _read_stack_map(body, check_class, used)
        elif name == "Exceptions":
            for _ in range(body.u2()):
                used.add(check_class(body.u2()))
        elif name == "InnerClasses":
            for _ in range(body.u2()):
                inner, outer = body.u2(), body.u2()
                body.u2()
                body.u2()
                metadata.add(check_class(inner))
                if outer:
                    metadata.add(check_class(outer))
        elif name == "EnclosingMethod":
            metadata.add(check_class(body.u2()))
        elif name == "NestHost":
            metadata.add(check_class(body.u2()))
        elif name in ("NestMembers", "PermittedSubclasses"):
            for _ in range(body.u2()):
                metadata.add(check_class(body.u2()))


def _read_code(body, utf8, check_class, used, metadata, count) -> None:
    body.u2()
    body.u2()
    code = body.take(body.u4())
    position = 0
    while position < len(code):
        opcode = code[position]
        position += 1
        if opcode in _OPERAND_BYTES:
            size = _OPERAND_BYTES[opcode]
            if position + size > len(code):
                raise ClassFormatError("truncated instruction")
            if opcode in _CLASS_OPERAND:
                used.add(check_class(struct.unpack_from(">H", code, position)[0]))
            elif opcode in _LOADABLE_OPERAND:
                constant = code[position] if size == 1 else struct.unpack_from(">H", code, position)[0]
                if not 0 < constant < count:
                    raise ClassFormatError(f"ldc of missing constant {constant}")
                try:
                    used.add(check_class(constant))
                except ClassFormatError:
                    pass  # a number, string, method type, method handle or dynamic constant
            position += size
        elif opcode == 0xC4:
            position += 5 if position < len(code) and code[position] == 0x84 else 3
        elif opcode in (0xAA, 0xAB):
            position += (4 - position % 4) % 4
            if position + (12 if opcode == 0xAA else 8) > len(code):
                raise ClassFormatError("truncated switch")
            if opcode == 0xAA:
                low, high = struct.unpack_from(">ii", code, position + 4)
                if high < low:
                    raise ClassFormatError("bad tableswitch bounds")
                position += 12 + 4 * (high - low + 1)
            else:
                pairs = struct.unpack_from(">i", code, position + 4)[0]
                if pairs < 0:
                    raise ClassFormatError("bad lookupswitch size")
                position += 8 + 8 * pairs
        elif opcode > 0xC9:
            raise ClassFormatError(f"unknown opcode 0x{opcode:02x}")
    if position != len(code):
        raise ClassFormatError("instruction runs past the end of the code")
    for _ in range(body.u2()):
        body.take(6)
        catch = body.u2()
        if catch:
            used.add(check_class(catch))
    _read_attributes(body, utf8, check_class, used, metadata, count)


def _read_stack_map(body, check_class, used) -> None:
    def verification_types(amount: int) -> None:
        for _ in range(amount):
            tag = body.u1()
            if tag == 7:
                used.add(check_class(body.u2()))
            elif tag == 8:
                body.u2()
            elif tag > 8:
                raise ClassFormatError(f"bad verification type {tag}")

    for _ in range(body.u2()):
        frame = body.u1()
        if frame < 64:
            continue
        if frame < 128:
            verification_types(1)
        elif frame < 247:
            raise ClassFormatError(f"reserved stack map frame {frame}")
        elif frame == 247:
            body.u2()
            verification_types(1)
        elif frame < 252:
            body.u2()
        elif frame < 255:
            body.u2()
            verification_types(frame - 251)
        else:
            body.u2()
            verification_types(body.u2())
            verification_types(body.u2())


def _matches(pattern: str, name: str) -> bool:
    if pattern.endswith("/**"):
        return name.startswith(pattern[:-2])
    if pattern.endswith("/*"):
        prefix = pattern[:-1]
        return name.startswith(prefix) and "/" not in name[len(prefix):]
    return pattern == name


def is_client_only(name: str) -> bool:
    return name.startswith(CLIENT_ONLY_PREFIXES)


def in_client_package(name: str) -> bool:
    return "client" in name.split("/")[:-1]


def read_allowlist(path: Path) -> list[AllowEntry]:
    """Read ``<source> <target> <reason>`` lines; ``#`` starts a comment line."""
    entries: list[AllowEntry] = []
    seen: dict[tuple[str, str], int] = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        text = line.strip()
        if not text or text.startswith("#"):
            continue
        parts = text.split(None, 2)
        if len(parts) < 3:
            raise AllowlistError(f"{path.name}:{number}: expected a source, a target and a reason")
        source, target, reason = parts
        for pattern in (source, target):
            if not PATTERN.fullmatch(pattern):
                raise AllowlistError(f"{path.name}:{number}: not a class name or pattern: {pattern}")
        package_probe = source[:source.rindex("/") + 1] + "X" if source.endswith("*") else source
        if in_client_package(package_probe):
            raise AllowlistError(f"{path.name}:{number}: client-package sources need no entry: {source}")
        if (source, target) in seen:
            raise AllowlistError(f"{path.name}:{number}: duplicate of line {seen[(source, target)]}")
        seen[(source, target)] = number
        entries.append(AllowEntry(number, source, target, reason))
    return entries


def read_jar(path: Path) -> tuple[dict[str, ClassFile], list[str]]:
    classes: dict[str, ClassFile] = {}
    errors: list[str] = []
    with zipfile.ZipFile(path) as archive:
        entries = [info for info in archive.infolist()
                   if info.filename.endswith(".class") and not info.filename.startswith("META-INF/")
                   and info.filename.rsplit("/", 1)[-1] != "module-info.class"]
        if len(entries) > MAX_CLASSES:
            return {}, [f"{path.name}: more than {MAX_CLASSES} class files"]
        for info in entries:
            if info.file_size > MAX_CLASS_BYTES:
                errors.append(f"{info.filename}: larger than {MAX_CLASS_BYTES} bytes")
                continue
            try:
                parsed = parse_class(archive.read(info))
            except ClassFormatError as exc:
                errors.append(f"{info.filename}: {exc}")
                continue
            if parsed.name + ".class" != info.filename:
                errors.append(f"{info.filename}: declares class {parsed.name}")
                continue
            if parsed.name in classes:
                errors.append(f"{info.filename}: duplicate entry")
                continue
            classes[parsed.name] = parsed
    return classes, errors


def scan(classes: dict[str, ClassFile], allowlist: list[AllowEntry]) -> ScanResult:
    result = ScanResult(classes=len(classes))
    result.client_package_classes = sum(1 for name in classes if in_client_package(name))

    def allowed_by(source: str, target: str) -> AllowEntry | None:
        if in_client_package(source):
            return None
        return next((entry for entry in allowlist if entry.matches(source, target)), None)

    # Breadth-first from the client-only classes keeps each recorded chain shortest. next_hop
    # maps a client-bound class of the JAR to the next class on its way to a client-only one.
    next_hop: dict[str, str] = {}
    referrers: dict[str, list[str]] = {}
    queue: deque[str] = deque()
    for name in sorted(classes):
        if is_client_only(name):
            next_hop[name] = name
            queue.append(name)
    for name in sorted(classes):
        for target in sorted(classes[name].references):
            if allowed_by(name, target) is not None:
                continue
            if target in classes:
                referrers.setdefault(target, []).append(name)
            elif is_client_only(target) and name not in next_hop:
                next_hop[name] = target
                queue.append(name)
    while queue:
        bound = queue.popleft()
        for referrer in referrers.get(bound, ()):
            if referrer not in next_hop:
                next_hop[referrer] = bound
                queue.append(referrer)
    result.client_bound = sorted(next_hop)

    def chain(start: str) -> list[str]:
        path = [start]
        while path[-1] in next_hop and next_hop[path[-1]] != path[-1]:
            path.append(next_hop[path[-1]])
        return path

    used_entries: set[int] = set()
    for name in sorted(classes):
        if in_client_package(name):
            continue
        for target in sorted(classes[name].references):
            if not (is_client_only(target) or target in next_hop):
                if target in classes and in_client_package(target):
                    result.data_only_references.append({"source": name, "target": target})
                continue
            row = {"source": name, "target": target,
                   "kind": "direct" if is_client_only(target) else "transitive",
                   "chain": chain(target)}
            entry = allowed_by(name, target)
            if entry is None:
                result.violations.append(row)
            else:
                used_entries.add(entry.line)
                result.allowed.append({**row, "allowlist_line": entry.line, "reason": entry.reason})
    result.unused_allowlist = [
        {"line": entry.line, "source": entry.source, "target": entry.target}
        for entry in allowlist if entry.line not in used_entries
    ]
    return result


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("jar", type=Path, help="built mod JAR")
    parser.add_argument("--allowlist", type=Path, default=DEFAULT_ALLOWLIST,
                        help="reviewed references (default: scripts/client_bytecode_allowlist.txt)")
    parser.add_argument("--report", type=Path, help="also write the full result as JSON")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        allowlist = read_allowlist(args.allowlist)
    except (OSError, AllowlistError) as exc:
        print(f"[FAIL] Allowlist: {exc}")
        return 1
    try:
        classes, errors = read_jar(args.jar)
    except (OSError, zipfile.BadZipFile) as exc:
        print(f"[FAIL] Cannot read {args.jar}: {exc}")
        return 1
    result = scan(classes, allowlist)
    result.errors.extend(errors)
    digest = sha256(args.jar)

    print(f"[INFO] {args.jar.name} SHA-256 {digest}: {result.classes} classes, "
          f"{result.client_package_classes} in client packages, {len(result.client_bound)} client-bound")
    for row in result.data_only_references:
        print(f"[INFO] Data-only client-package class: {row['source']} -> {row['target']}")
    for row in result.allowed:
        print(f"[ALLOW] {row['source']} -> {row['target']} (line {row['allowlist_line']}): {row['reason']}")
    for error in result.errors:
        print(f"[FAIL] Unreadable class: {error}")
    for row in result.violations:
        print(f"[FAIL] {row['kind'].capitalize()} client reference: {row['source']} -> "
              f"{' -> '.join(row['chain'])}")
    for row in result.unused_allowlist:
        print(f"[FAIL] Allowlist line {row['line']} matches no reference: {row['source']} {row['target']}")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        report = {"jar": args.jar.name, "sha256": digest, "allowlist": args.allowlist.name,
                  "client_only_prefixes": list(CLIENT_ONLY_PREFIXES), "passed": result.passed,
                  **{key: value for key, value in vars(result).items()}}
        args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if not result.passed:
        return 1
    print(f"[PASS] No common or server class references a client-only class outside the allowlist "
          f"({len(result.allowed)} allowed references)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
