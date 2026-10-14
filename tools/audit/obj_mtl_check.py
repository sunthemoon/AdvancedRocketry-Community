#!/usr/bin/env python3
"""Parse, check and rewrite OBJ and MTL files for the Forge 1.20.1 OBJ loader (ADR-066 sections 7.1 and 7.3).

The rules follow what ``net.minecraftforge.client.model.obj`` does with a file, and refuse what it would load
wrongly or silently:

* text is strict UTF-8 without a byte-order mark, ends lines with LF or CRLF, has no other control character
  and no ``\\`` line continuation;
* an OBJ uses only ``v``, ``vt``, ``vn``, ``f``, ``l``, ``s``, ``o``, ``g``, ``mtllib`` and ``usemtl``; it names
  at most one material library, before any ``usemtl``; every face has three or four corners whose indices exist,
  and lies inside a material (Forge drops faces without one and truncates larger polygons); line elements
  (``l``), which Forge ignores, are checked and counted as ``ignored_lines``;
* an MTL uses only the statements Forge reads or ignores harmlessly, starts with ``newmtl``, defines each
  material once and gives texture maps without options;
* a reference (``mtllib``, ``map_Ka``, ``map_Kd``, ``map_Ks``) is a relative path or a ``namespace:path``
  resource location in the mod's own namespace; absolute, external, parent-directory, URL, ``#variable`` and
  backslash forms are refused.

Budgets (ADR-066 section 7.1): at most 65,536 vertices per baked object (one OBJ file: the larger of its
positions and its baked quad corners, four per face) and 262,144 vertices or 16 MiB per cached model set. Every
file is size-checked before it is parsed, and counting stops at the first statement over a budget.

``check_model_set`` applies all of this to a set of files, such as one import plan: every material library,
material and texture must resolve to a file of the set, and every material a face uses needs a ``map_Kd``
texture (without one Forge shows the missing texture).
"""

from __future__ import annotations

import math
import re

MOD_ID = "advancedrocketrycommunity"
MAX_BAKED_OBJECT_VERTICES = 65_536
MAX_MODEL_SET_VERTICES = 262_144
MAX_MODEL_SET_BYTES = 16 * 1024 * 1024
MAX_FILE_BYTES = MAX_MODEL_SET_BYTES
QUAD_CORNERS = 4

OBJ_STATEMENTS = frozenset({"v", "vt", "vn", "f", "l", "s", "o", "g", "mtllib", "usemtl"})
COLOUR_STATEMENTS = frozenset({"Ka", "Kd", "Ks", "Ke"})
SCALAR_STATEMENTS = frozenset({"Ns", "Ni", "d", "Tr"})
INTEGER_STATEMENTS = frozenset({"illum", "forge_TintIndex"})
MAP_STATEMENTS = frozenset({"map_Ka", "map_Kd", "map_Ks"})

RESOURCE_PART = re.compile(r"[a-z0-9_.-]+")
RESOURCE_PATH = re.compile(r"[a-z0-9_./-]+")
MATERIAL_NAME = re.compile(r"[A-Za-z0-9_.-]+")
SPLIT = re.compile(r"[\t ]+")


class FormatError(ValueError):
    """A file the loader would refuse, or load differently from what it says."""


def _lines(data: bytes, label: str) -> list[tuple[int, bytes, bytes]]:
    """(line number, content, ending) for every line; the content is checked to be loader-safe text."""
    if len(data) > MAX_FILE_BYTES:
        raise FormatError(f"{label}: {len(data)} bytes exceed the {MAX_FILE_BYTES}-byte model set budget")
    if data.startswith(b"\xef\xbb\xbf"):
        raise FormatError(f"{label}: starts with a byte-order mark")
    result = []
    raw_lines = data.split(b"\n")
    for number, raw in enumerate(raw_lines, start=1):
        if number == len(raw_lines) and not raw:
            break
        ending = b"\n" if number < len(raw_lines) else b""
        if raw.endswith(b"\r"):
            raw, ending = raw[:-1], b"\r" + ending
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as error:
            raise FormatError(f"{label}:{number}: not UTF-8 ({error.reason})") from None
        if any(ord(character) < 32 and character != "\t" or ord(character) == 127 for character in text):
            raise FormatError(f"{label}:{number}: contains a control character")
        if text.endswith("\\"):
            raise FormatError(f"{label}:{number}: uses a line continuation")
        result.append((number, raw, ending))
    return result


def _tokens(raw: bytes) -> list[str]:
    text = raw.decode("utf-8")
    if text.lstrip(" \t").startswith("#"):
        return []
    return [token for token in SPLIT.split(text) if token]


def _floats(tokens: list[str], low: int, high: int, where: str) -> list[float]:
    if not low <= len(tokens) <= high:
        raise FormatError(f"{where}: expected {low} to {high} numbers")
    try:
        values = [float(token) for token in tokens]
    except ValueError:
        raise FormatError(f"{where}: not a number") from None
    if not all(math.isfinite(value) for value in values):
        raise FormatError(f"{where}: not a finite number")
    return values


def _integer(tokens: list[str], where: str) -> int:
    if len(tokens) != 1 or not re.fullmatch(r"-?[0-9]{1,9}", tokens[0]):
        raise FormatError(f"{where}: expected one integer")
    return int(tokens[0])


def _corner_index(raw: str, count: int, kind: str, where: str) -> None:
    if not re.fullmatch(r"-?[0-9]{1,9}", raw) or int(raw) == 0:
        raise FormatError(f"{where}: {kind} index {raw!r} is not a non-zero integer")
    index = int(raw)
    resolved = index - 1 if index > 0 else count + index
    if not 0 <= resolved < count:
        raise FormatError(f"{where}: {kind} index {index} is outside the {count} defined so far")


def parse_obj(data: bytes, label: str, references: bool = True) -> dict:
    """Counts and references of an OBJ file; raises FormatError on anything the loader would mishandle.
    ``references=False`` checks the format only, for a legacy source whose references the plan rewrites."""
    positions = coordinates = normals = faces = lines = 0
    library = None
    materials: set[str] = set()
    current = None
    for number, raw, _ in _lines(data, label):
        tokens = _tokens(raw)
        if not tokens:
            continue
        where = f"{label}:{number}"
        keyword, arguments = tokens[0], tokens[1:]
        if keyword not in OBJ_STATEMENTS:
            raise FormatError(f"{where}: unsupported statement {keyword!r}")
        if keyword == "v":
            values = _floats(arguments, 3, 4, where)
            if len(values) == 4 and values[3] == 0:
                raise FormatError(f"{where}: vertex weight is zero")
            positions += 1
            if positions > MAX_BAKED_OBJECT_VERTICES:
                raise FormatError(f"{label}: more than {MAX_BAKED_OBJECT_VERTICES} vertices in one baked object")
        elif keyword == "vt":
            _floats(arguments, 1, 3, where)
            coordinates += 1
        elif keyword == "vn":
            _floats(arguments, 3, 3, where)
            normals += 1
        elif keyword == "f":
            if not 3 <= len(arguments) <= QUAD_CORNERS:
                raise FormatError(f"{where}: a face needs 3 or 4 corners, not {len(arguments)}")
            if current is None:
                raise FormatError(f"{where}: face outside any material (Forge drops it)")
            for corner in arguments:
                parts = corner.split("/")
                if len(parts) > 3 or not parts[0]:
                    raise FormatError(f"{where}: malformed corner {corner!r}")
                _corner_index(parts[0], positions, "position", where)
                if len(parts) > 1 and parts[1]:
                    _corner_index(parts[1], coordinates, "texture coordinate", where)
                if len(parts) > 2:
                    if not parts[2]:
                        raise FormatError(f"{where}: malformed corner {corner!r}")
                    _corner_index(parts[2], normals, "normal", where)
            faces += 1
            if QUAD_CORNERS * faces > MAX_BAKED_OBJECT_VERTICES:
                raise FormatError(f"{label}: more than {MAX_BAKED_OBJECT_VERTICES} baked vertices in one object")
        elif keyword == "l":
            if len(arguments) < 2:
                raise FormatError(f"{where}: a line element needs at least two points")
            for point in arguments:
                parts = point.split("/")
                if len(parts) > 2 or not parts[0]:
                    raise FormatError(f"{where}: malformed line point {point!r}")
                _corner_index(parts[0], positions, "position", where)
                if len(parts) > 1 and parts[1]:
                    _corner_index(parts[1], coordinates, "texture coordinate", where)
            lines += 1
        elif keyword in ("s", "o", "g"):
            if len(arguments) != 1:
                raise FormatError(f"{where}: {keyword} takes exactly one name")
        elif keyword == "mtllib":
            if len(arguments) != 1:
                raise FormatError(f"{where}: mtllib takes exactly one library")
            if library is not None:
                raise FormatError(f"{where}: a second material library")
            if materials:
                raise FormatError(f"{where}: material library named after usemtl")
            if references:
                check_reference(arguments[0], relative=True, where=where)
            library = arguments[0]
        else:
            if not arguments:
                raise FormatError(f"{where}: usemtl without a name")
            if library is None:
                raise FormatError(f"{where}: usemtl before any material library")
            current = " ".join(arguments)
            materials.add(current)
    return {
        "format": "obj",
        "positions": positions,
        "texture_coordinates": coordinates,
        "normals": normals,
        "faces": faces,
        "baked_vertices": QUAD_CORNERS * faces,
        "ignored_lines": lines,
        "material_library": library,
        "materials": sorted(materials),
    }


def parse_mtl(data: bytes, label: str, references: bool = True) -> dict:
    """Materials and texture references of an MTL file; raises FormatError on anything the loader would mishandle.
    ``references=False`` checks the format only, for a legacy source whose references the plan rewrites."""
    materials: dict[str, dict[str, str]] = {}
    current = None
    for number, raw, _ in _lines(data, label):
        tokens = _tokens(raw)
        if not tokens:
            continue
        where = f"{label}:{number}"
        keyword, arguments = tokens[0], tokens[1:]
        if keyword == "newmtl":
            if not arguments:
                raise FormatError(f"{where}: newmtl without a name")
            current = " ".join(arguments)
            if current in materials:
                raise FormatError(f"{where}: material {current!r} defined twice")
            materials[current] = {}
            continue
        if current is None:
            raise FormatError(f"{where}: {keyword!r} before any newmtl")
        if keyword in COLOUR_STATEMENTS:
            _floats(arguments, 1, 4, where)
        elif keyword in SCALAR_STATEMENTS:
            _floats(arguments, 1, 1, where)
        elif keyword in INTEGER_STATEMENTS:
            _integer(arguments, where)
        elif keyword in MAP_STATEMENTS:
            if len(arguments) != 1:
                raise FormatError(f"{where}: {keyword} takes one texture and no options")
            if keyword in materials[current]:
                raise FormatError(f"{where}: {keyword} given twice for {current!r}")
            if references:
                check_reference(arguments[0], relative=False, where=where)
            materials[current][keyword] = arguments[0]
        else:
            raise FormatError(f"{where}: unsupported statement {keyword!r}")
    return {
        "format": "mtl",
        "materials": {name: dict(sorted(maps.items())) for name, maps in sorted(materials.items())},
        "textures": sorted({value for maps in materials.values() for value in maps.values()}),
    }


def check_reference(value: str, relative: bool, where: str) -> str:
    """A reference the loader resolves inside the mod's own namespace; ``relative`` allows a bare relative path."""
    if (not value or "\\" in value or value.startswith(("/", "#", "~")) or "://" in value
            or re.match(r"[A-Za-z]:[\\/]", value)):
        raise FormatError(f"{where}: {value!r} is an absolute, external or variable reference")
    namespace, separator, path = value.partition(":")
    if not separator:
        if not relative:
            raise FormatError(f"{where}: {value!r} has no namespace, so it resolves outside the mod")
        namespace, path = MOD_ID, value
    if not RESOURCE_PART.fullmatch(namespace) or ":" in path or not RESOURCE_PATH.fullmatch(path):
        raise FormatError(f"{where}: {value!r} is not a valid resource location")
    if namespace != MOD_ID:
        raise FormatError(f"{where}: {value!r} is in the external namespace {namespace!r}")
    if any(part in ("", ".", "..") for part in path.split("/")):
        raise FormatError(f"{where}: {value!r} has an empty, current or parent directory segment")
    return value


def resolve_library(model: str, value: str) -> str:
    """The resource location (``namespace:path``) of an OBJ's ``mtllib``; ``model`` is the OBJ's own location."""
    if ":" in value:
        return value
    namespace, _, path = model.partition(":")
    directory = path.rpartition("/")[0]
    return f"{namespace}:{directory + '/' if directory else ''}{value}"


def resolve_texture(value: str) -> str:
    """The resource location of the PNG file a texture map names (the block atlas adds ``textures/`` and ``.png``)."""
    namespace, _, path = value.partition(":")
    return f"{namespace}:textures/{path}.png"


def rewrite(data: bytes, kind: str, mapping: dict[str, dict[str, str]], label: str) -> bytes:
    """Replace the reference values the plan maps, keeping every other byte. ``mapping`` has the keys
    ``mtllib`` and ``usemtl`` (OBJ) or ``textures`` (MTL); every reference in the file must be mapped and every
    mapping must be used."""
    statements = {"obj": {"mtllib": "mtllib", "usemtl": "usemtl"},
                  "mtl": {keyword: "textures" for keyword in MAP_STATEMENTS}}.get(kind)
    if statements is None or set(mapping) != set(statements.values()):
        raise FormatError(f"{label}: a {kind} rewrite maps exactly {sorted(set((statements or {}).values()))}")
    used: dict[str, set[str]] = {group: set() for group in mapping}
    output = bytearray()
    for number, raw, ending in _lines(data, label):
        tokens = _tokens(raw)
        group = statements.get(tokens[0]) if tokens else None
        if group is None:
            output += raw + ending
            continue
        old = " ".join(tokens[1:])
        if old not in mapping[group]:
            raise FormatError(f"{label}:{number}: {tokens[0]} {old!r} is not mapped by the plan")
        new = mapping[group][old]
        pattern = MATERIAL_NAME if group == "usemtl" else re.compile(r"[a-z0-9_.:/-]+")
        if not pattern.fullmatch(new):
            raise FormatError(f"{label}:{number}: replacement {new!r} is not a plain name or path")
        used[group].add(old)
        output += f"{tokens[0]} {new}".encode("utf-8") + ending
    for group, values in sorted(mapping.items()):
        stale = sorted(set(values) - used[group])
        if stale:
            raise FormatError(f"{label}: the plan maps {group} {stale} that the source does not use")
    return bytes(output)


def check_model_set(rendered: dict[str, bytes], asset_root: str) -> tuple[dict[str, dict], dict[str, int], list[str]]:
    """ADR-066 section 7.3 over a whole set of files (path to bytes, every path under ``asset_root``, the mod's
    ``assets/<namespace>/`` directory): parse every OBJ and MTL, resolve every library, material and texture inside
    the set and hold the section 7.1 budgets. Returns the per-file summaries, the set totals and the errors."""
    locations = {f"{MOD_ID}:{target[len(asset_root):]}": target for target in rendered}
    summaries: dict[str, dict] = {}
    errors: list[str] = []
    for target, data in sorted(rendered.items()):
        try:
            if target.endswith(".obj"):
                summaries[target] = parse_obj(data, target)
            elif target.endswith(".mtl"):
                summaries[target] = parse_mtl(data, target)
        except FormatError as error:
            errors.append(str(error))
    vertices = 0
    model_files: set[str] = set()
    for target, summary in sorted(summaries.items()):
        if summary["format"] == "mtl":
            model_files.add(target)
            for texture in summary["textures"]:
                image = locations.get(resolve_texture(texture))
                if image is None or not image.endswith(".png"):
                    errors.append(f"{target}: texture {texture!r} does not resolve to a PNG in the plan")
                else:
                    model_files.add(image)
            continue
        model_files.add(target)
        vertices += max(summary["positions"], summary["baked_vertices"])
        if not summary["faces"]:
            errors.append(f"{target}: has no faces")
            continue
        location = f"{MOD_ID}:{target[len(asset_root):]}"
        library = locations.get(resolve_library(location, summary["material_library"]))
        if library is None or library not in summaries or summaries[library]["format"] != "mtl":
            errors.append(f"{target}: material library {summary['material_library']!r} does not resolve to an MTL "
                          f"in the plan")
            continue
        defined = summaries[library]["materials"]
        for material in summary["materials"]:
            if material not in defined:
                errors.append(f"{target}: material {material!r} is not defined in {library} (dangling material)")
            elif "map_Kd" not in defined[material]:
                errors.append(f"{target}: material {material!r} has no map_Kd texture (Forge shows the missing "
                              f"texture)")
    total = sum(len(rendered[path]) for path in model_files)
    if vertices > MAX_MODEL_SET_VERTICES:
        errors.append(f"the model set has {vertices} vertices, over the {MAX_MODEL_SET_VERTICES} budget")
    if total > MAX_MODEL_SET_BYTES:
        errors.append(f"the model set has {total} bytes, over the {MAX_MODEL_SET_BYTES}-byte budget")
    for summary in summaries.values():
        if summary["format"] == "mtl":
            summary["materials"] = sorted(summary["materials"])
    return summaries, {"vertices": vertices, "bytes": total}, errors
