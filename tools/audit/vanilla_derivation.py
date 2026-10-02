#!/usr/bin/env python3
"""Check legacy assets for derivation from vanilla Minecraft assets (ADR-061 §4.8).

Every legacy asset is compared with the assets of the vanilla client JARs given
on the command line (1.12.2 and 1.20.1). The JARs are read locally and never
copied into the repository. For each asset the tool records:

* file hash equality with any vanilla file;
* for PNG images, in all eight orientations and against every vanilla PNG of
  the same size (and, unrotated, of an integer scale of 2 or 4), and between
  the first frames of animated strips:
  - ``overlap``: byte-equal RGBA pixels over the pixels opaque in either image,
    leaving out the vanilla image's dominant colour when it has four or more
    colours; flat (one-colour) vanilla images do not count; ``colours`` is the
    number of distinct colours among the equal pixels;
  - ``iou``: intersection over union of the opaque masks;
  - ``rank``: Spearman rank correlation of luminance over the pixels opaque in
    both, when both images have at least three luminance levels;
* a sub-image search: the candidate, in all eight orientations, inside every
  larger vanilla image up to 512 × 512, anchored on its rarest colours, scored
  by the share of its opaque pixels that are byte-equal (``sub``, with the
  number of distinct matched colours in ``colours``);
* a verdict: ``HIT`` (presumed vanilla-derived), ``SUSPECT`` (needs a recorded
  human review), ``CLEAR`` or ``UNSUPPORTED`` (a format this tool cannot
  decode, reviewed like ``SUSPECT``). An exact match (``overlap`` or ``sub``)
  of a single colour is a shared flat fill and counts for nothing; one of two
  colours matches a shape, not pixel art, so it yields at most ``SUSPECT``;
  rank matches and file hashes are not limited.

The results file is deterministic; ``--check`` regenerates it and compares.
Exact sub-image matching does not find recoloured crops; such files are found
only when they are whole images (by ``rank``).
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import struct
import sys
import zipfile
import zlib
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
ASSET_ROOT = "src/main/resources/assets/advancedrocketry/"
DEFAULT_OUTPUT = Path("docs/work/v1.8.0-vanilla-derivation.json")
SCHEMA = 2

# Thresholds (ADR-061 §4.8).
HIT_OVERLAP = 0.30
HIT_IOU = 0.90
HIT_RANK = 0.90
HIT_SUB = 0.75
SUSPECT_OVERLAP = 0.10
SUSPECT_IOU = 0.85
SUSPECT_RANK = 0.75
SUSPECT_SUB = 0.50
MIN_SHARED_PIXELS = 32
MIN_INFORMATIVE_PIXELS = 16
MIN_RANK_LEVELS = 3
MIN_SUB_PIXELS = 16
DOMINANT_EXCLUSION_COLOURS = 4
MIN_EVIDENCE_COLOURS = 2
MIN_MATCHED_COLOURS = 3
SCALES = (2, 4)
MAX_SHEET = 512
MAX_SUB_CANDIDATE = 64
MAX_SUB_OFFSETS = 4096
MAX_PIXELS = 1 << 20


def thresholds() -> dict:
    return {
        "hit_overlap": HIT_OVERLAP, "hit_iou": HIT_IOU, "hit_rank": HIT_RANK, "hit_sub": HIT_SUB,
        "suspect_overlap": SUSPECT_OVERLAP, "suspect_iou": SUSPECT_IOU, "suspect_rank": SUSPECT_RANK,
        "suspect_sub": SUSPECT_SUB, "min_shared_pixels": MIN_SHARED_PIXELS,
        "min_informative_pixels": MIN_INFORMATIVE_PIXELS, "min_rank_levels": MIN_RANK_LEVELS,
        "min_sub_pixels": MIN_SUB_PIXELS, "dominant_exclusion_colours": DOMINANT_EXCLUSION_COLOURS,
        "min_evidence_colours": MIN_EVIDENCE_COLOURS, "min_matched_colours": MIN_MATCHED_COLOURS,
        "scales": list(SCALES), "max_sheet": MAX_SHEET, "max_sub_candidate": MAX_SUB_CANDIDATE,
        "max_sub_offsets": MAX_SUB_OFFSETS, "orientations": 8,
    }


class DecodeError(Exception):
    pass


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def decode_png(data: bytes) -> tuple[int, int, list[tuple[int, int, int, int]]]:
    """Decode a non-interlaced PNG into (width, height, RGBA pixels)."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise DecodeError("not a PNG")
    position, idat, palette, transparency = 8, [], None, None
    width = height = depth = colour = interlace = None
    while position < len(data):
        length = struct.unpack(">I", data[position:position + 4])[0]
        kind = data[position + 4:position + 8]
        body = data[position + 8:position + 8 + length]
        position += 12 + length
        if kind == b"IHDR":
            width, height, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            transparency = body
        elif kind == b"IDAT":
            idat.append(body)
        elif kind == b"IEND":
            break
    if width is None or interlace != 0:
        raise DecodeError("missing header or interlaced image")
    if width * height > MAX_PIXELS:
        raise DecodeError("image too large")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(colour)
    if channels is None or depth not in (1, 2, 4, 8, 16) or (depth == 16 and colour == 3):
        raise DecodeError(f"unsupported colour type {colour} depth {depth}")
    raw = zlib.decompress(b"".join(idat))
    bits = channels * depth
    stride = (width * bits + 7) // 8
    step = max(1, bits // 8)
    rows, previous, offset = [], bytearray(stride), 0
    for _ in range(height):
        method = raw[offset]
        line = bytearray(raw[offset + 1:offset + 1 + stride])
        offset += 1 + stride
        for i in range(stride):
            left = line[i - step] if i >= step else 0
            up = previous[i]
            corner = previous[i - step] if i >= step else 0
            if method == 1:
                line[i] = (line[i] + left) & 0xFF
            elif method == 2:
                line[i] = (line[i] + up) & 0xFF
            elif method == 3:
                line[i] = (line[i] + ((left + up) >> 1)) & 0xFF
            elif method == 4:
                line[i] = (line[i] + _paeth(left, up, corner)) & 0xFF
            elif method != 0:
                raise DecodeError(f"bad filter {method}")
        rows.append(bytes(line))
        previous = line
    pixels: list[tuple[int, int, int, int]] = []
    for line in rows:
        if depth < 8:
            per_byte = 8 // depth
            mask = (1 << depth) - 1
            samples = [(line[i // per_byte] >> (8 - depth * (i % per_byte + 1))) & mask for i in range(width)]
        elif depth == 8:
            samples = list(line)
        else:
            samples = [line[i] for i in range(0, len(line), 2)]
        for x in range(width):
            if colour == 3:
                index = samples[x]
                if palette is None or index >= len(palette):
                    raise DecodeError("bad palette index")
                alpha = transparency[index] if transparency and index < len(transparency) else 255
                pixels.append((*palette[index], alpha))
            elif colour in (0, 4):
                value = samples[x * channels]
                if depth < 8:
                    value = value * 255 // ((1 << depth) - 1)
                alpha = samples[x * channels + 1] if colour == 4 else 255
                pixels.append((value, value, value, alpha))
            else:
                base = x * channels
                alpha = samples[base + 3] if colour == 6 else 255
                pixels.append((samples[base], samples[base + 1], samples[base + 2], alpha))
    return width, height, pixels


def _ranks(values: list[float]) -> list[float]:
    order = sorted(range(len(values)), key=values.__getitem__)
    ranks = [0.0] * len(values)
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and values[order[j + 1]] == values[order[i]]:
            j += 1
        average = (i + j) / 2.0
        for k in range(i, j + 1):
            ranks[order[k]] = average
        i = j + 1
    return ranks


def _centred(values: list[float]) -> tuple[list[float], float]:
    mean = sum(values) / len(values)
    centred = [value - mean for value in values]
    return centred, sum(value * value for value in centred) ** 0.5


def _correlation(a: list[float], b: list[float]) -> float:
    ca, na = _centred(a)
    cb, nb = _centred(b)
    if na == 0 or nb == 0:
        return 0.0
    return sum(x * y for x, y in zip(ca, cb)) / (na * nb)


def _popcount(value: int) -> int:
    return bin(value).count("1")


Pixel = tuple[int, int, int, int]


class Image:
    """An RGBA image with bit masks per colour, for fast exact and rank comparison."""

    def __init__(self, width: int, height: int, pixels: list[Pixel]):
        self.width, self.height, self.pixels = width, height, pixels
        self.size = width * height
        length = (self.size + 7) // 8
        bitmaps: dict[Pixel, bytearray] = {}
        opaque_bits = bytearray(length)
        for index, pixel in enumerate(pixels):
            if pixel[3] > 0:
                byte, bit = index >> 3, 1 << (index & 7)
                opaque_bits[byte] |= bit
                bitmap = bitmaps.get(pixel)
                if bitmap is None:
                    bitmap = bitmaps[pixel] = bytearray(length)
                bitmap[byte] |= bit
        self.colour_masks: dict[Pixel, int] = {colour: int.from_bytes(bitmap, "little")
                                               for colour, bitmap in bitmaps.items()}
        self.opaque = int.from_bytes(opaque_bits, "little")
        self.opaque_count = _popcount(self.opaque)
        self.colours = len(self.colour_masks)
        counts = {colour: _popcount(mask) for colour, mask in self.colour_masks.items()}
        self.dominant = max(counts, key=lambda colour: (counts[colour], colour)) if counts else None
        self.luminance = [0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2] for p in pixels]
        self.levels = len({round(self.luminance[i]) for i in range(self.size) if pixels[i][3] > 0})
        self.full = self.opaque_count == self.size
        if self.full and self.levels >= MIN_RANK_LEVELS:
            self.rank_vector, self.rank_norm = _centred(_ranks(self.luminance))
        else:
            self.rank_vector, self.rank_norm = None, 0.0

    def transformed(self, orientation: int) -> "Image":
        """One of the eight orientations: 0–3 rotate by 90° steps, 4–7 mirror first."""
        width, height, source = self.width, self.height, self.pixels

        def at(x: int, y: int) -> Pixel:
            return source[y * width + x]

        grid = [[at(x, y) for x in range(width)] for y in range(height)]
        if orientation >= 4:
            grid = [list(reversed(row)) for row in grid]
        for _ in range(orientation % 4):
            grid = [list(row) for row in zip(*grid[::-1])]
        new_height, new_width = len(grid), len(grid[0])
        return Image(new_width, new_height, [pixel for row in grid for pixel in row])

    def downscaled(self, factor: int) -> "Image":
        width, height = self.width // factor, self.height // factor
        return Image(width, height,
                     [self.pixels[(y * factor) * self.width + x * factor] for y in range(height) for x in range(width)])

    def first_frame(self) -> "Image | None":
        if self.height >= 2 * self.width and self.height % self.width == 0:
            return Image(self.width, self.width, self.pixels[: self.width * self.width])
        return None


def compare(candidate: Image, vanilla: Image) -> dict[str, float] | None:
    """Same-size measures; None when neither image has an opaque pixel."""
    union = _popcount(candidate.opaque | vanilla.opaque)
    if union == 0:
        return None
    both_mask = candidate.opaque & vanilla.opaque
    both = _popcount(both_mask)
    overlap = 0.0
    matched_colours = 0
    if vanilla.colours >= 2:
        excluded = vanilla.dominant if vanilla.colours >= DOMINANT_EXCLUSION_COLOURS else None
        equal = 0
        for colour in candidate.colour_masks.keys() & vanilla.colour_masks.keys():
            if colour != excluded:
                shared = _popcount(candidate.colour_masks[colour] & vanilla.colour_masks[colour])
                equal += shared
                matched_colours += shared > 0
        informative = union - (_popcount(vanilla.colour_masks[excluded]) if excluded is not None else 0)
        if informative >= MIN_INFORMATIVE_PIXELS:
            overlap = equal / informative
    rank = 0.0
    if both >= MIN_SHARED_PIXELS and candidate.levels >= MIN_RANK_LEVELS and vanilla.levels >= MIN_RANK_LEVELS:
        if candidate.rank_vector is not None and vanilla.rank_vector is not None:
            rank = sum(x * y for x, y in zip(candidate.rank_vector, vanilla.rank_vector)) / (
                candidate.rank_norm * vanilla.rank_norm)
        elif both / union >= SUSPECT_IOU:
            indices = [i for i in range(candidate.size) if both_mask >> i & 1]
            rank = _correlation(_ranks([candidate.luminance[i] for i in indices]),
                                _ranks([vanilla.luminance[i] for i in indices]))
    return {"overlap": overlap, "colours": matched_colours, "iou": both / union, "rank": rank}


def verdict(measure: dict[str, float]) -> str:
    colours = measure.get("colours", 0)
    rich, evidence = colours >= MIN_MATCHED_COLOURS, colours >= MIN_EVIDENCE_COLOURS
    if (rich and (measure.get("sub", 0.0) >= HIT_SUB or measure["overlap"] >= HIT_OVERLAP)) or (
            measure["iou"] >= HIT_IOU and measure["rank"] >= HIT_RANK):
        return "HIT"
    if (evidence and (measure.get("sub", 0.0) >= SUSPECT_SUB or measure["overlap"] >= SUSPECT_OVERLAP)) or (
            measure["iou"] >= SUSPECT_IOU and measure["rank"] >= SUSPECT_RANK):
        return "SUSPECT"
    return "CLEAR"


SEVERITY = {"CLEAR": 0, "SUSPECT": 1, "HIT": 2}


class SubImageProbe:
    """A candidate orientation prepared for anchored sub-image search."""

    def __init__(self, image: Image):
        self.image = image
        opaque = [(i % image.width, i // image.width, p) for i, p in enumerate(image.pixels) if p[3] > 0]
        counts: dict[Pixel, int] = {}
        for _, _, pixel in opaque:
            counts[pixel] = counts.get(pixel, 0) + 1
        ordered = sorted(opaque, key=lambda item: (counts[item[2]], item[1], item[0]))
        anchors = []
        for x, y, pixel in ordered:
            if all(pixel != anchor[2] for anchor in anchors):
                anchors.append((x, y, pixel))
            if len(anchors) == 3:
                break
        self.anchors = anchors
        self.opaque = opaque
        self.usable = len(opaque) >= MIN_SUB_PIXELS and len(anchors) >= 2


def sub_image_score(probe: SubImageProbe, sheet: Image,
                    positions: dict[Pixel, list[int]]) -> tuple[float, int, int, int]:
    """Best share of the probe's opaque pixels found byte-equal at one offset in ``sheet``,
    with that offset and the number of distinct colours matched there."""
    image = probe.image
    first = probe.anchors[0]
    best = (0.0, -1, -1, 0)
    tried = 0
    total = len(probe.opaque)
    for position in positions.get(first[2], ()):
        ox, oy = position % sheet.width - first[0], position // sheet.width - first[1]
        if ox < 0 or oy < 0 or ox + image.width > sheet.width or oy + image.height > sheet.height:
            continue
        if any(sheet.pixels[(oy + y) * sheet.width + ox + x] != pixel for x, y, pixel in probe.anchors[1:]):
            continue
        tried += 1
        if tried > MAX_SUB_OFFSETS:
            break
        allowed = total - int(SUSPECT_SUB * total)
        misses = 0
        for x, y, pixel in probe.opaque:
            if sheet.pixels[(oy + y) * sheet.width + ox + x] != pixel:
                misses += 1
                if misses > allowed:
                    break
        else:
            score = (total - misses) / total
            if score > best[0]:
                matched = {pixel for x, y, pixel in probe.opaque
                           if sheet.pixels[(oy + y) * sheet.width + ox + x] == pixel}
                best = (score, ox, oy, len(matched))
    return best


def _better(current, level: str, measure: dict, record: dict):
    key = (SEVERITY[level], measure.get("sub", 0.0), measure["overlap"], measure["rank"], measure["iou"])
    if current is None or key > current[0]:
        return key, level, record
    return current


def analyse_images(candidates: list[tuple[str, Image]], vanilla: list[tuple[str, str, Image]]) -> dict[str, tuple]:
    """Best verdict per candidate name against the vanilla images (in memory, for tests and the CLI)."""
    by_size: dict[tuple[int, int], list[tuple[str, str, Image]]] = {}
    for label, name, image in vanilla:
        by_size.setdefault((image.width, image.height), []).append((label, name, image))
        frame = image.first_frame()
        if frame is not None:
            by_size.setdefault((frame.width, frame.height), []).append((label, name + "#frame0", frame))
    best: dict[str, tuple] = {}

    def offer(name: str, measure: dict, record: dict) -> None:
        # Verdicts are taken on the recorded (rounded) measures, so the results file can be re-checked.
        rounded = {key: round(value, 4) for key, value in measure.items()}
        merged = dict(record)
        merged.update(rounded)
        best[name] = _better(best.get(name), verdict(rounded), rounded, merged)

    for name, image in candidates:
        variants = [(orientation, image.transformed(orientation)) for orientation in range(8)]
        frame = image.first_frame()
        if frame is not None:
            variants.append(("frame0", frame))
        for orientation, variant in variants:
            for label, vanilla_name, other in by_size.get((variant.width, variant.height), []):
                measure = compare(variant, other)
                if measure is not None:
                    offer(name, measure, {"method": "same", "vanilla": f"{label}:{vanilla_name}",
                                          "orientation": orientation})
        for factor in SCALES:
            if image.width % factor == 0 and image.height % factor == 0:
                small = image.downscaled(factor)
                for label, vanilla_name, other in by_size.get((small.width, small.height), []):
                    measure = compare(small, other)
                    if measure is not None:
                        offer(name, measure, {"method": "scaled", "vanilla": f"{label}:{vanilla_name}",
                                              "scale": factor})
            for label, vanilla_name, other in by_size.get((image.width * factor, image.height * factor), []):
                measure = compare(image, other.downscaled(factor))
                if measure is not None:
                    offer(name, measure, {"method": "scaled", "vanilla": f"{label}:{vanilla_name}",
                                          "scale": -factor})
    probes = {}
    for name, image in candidates:
        if image.width <= MAX_SUB_CANDIDATE and image.height <= MAX_SUB_CANDIDATE:
            prepared = [(orientation, SubImageProbe(image.transformed(orientation))) for orientation in range(8)]
            probes[name] = [(o, p) for o, p in prepared if p.usable]
    for label, vanilla_name, sheet in vanilla:
        if sheet.width > MAX_SHEET or sheet.height > MAX_SHEET:
            continue
        positions: dict[Pixel, list[int]] | None = None
        for name, prepared in probes.items():
            for orientation, probe in prepared:
                image = probe.image
                if image.width > sheet.width or image.height > sheet.height or (
                        image.width == sheet.width and image.height == sheet.height):
                    continue
                if probe.anchors[0][2] not in sheet.colour_masks:
                    continue
                if positions is None:
                    positions = {}
                    for index, pixel in enumerate(sheet.pixels):
                        if pixel[3] > 0:
                            positions.setdefault(pixel, []).append(index)
                score, x, y, colours = sub_image_score(probe, sheet, positions)
                if score > 0:
                    offer(name, {"sub": score, "colours": colours, "overlap": 0.0, "iou": 0.0, "rank": 0.0},
                          {"method": "subimage", "vanilla": f"{label}:{vanilla_name}", "orientation": orientation,
                           "offset": [x, y]})
    return best


def load_vanilla(specs: list[str]) -> tuple[dict[str, dict], dict[str, list[tuple[str, str]]], list]:
    sources: dict[str, dict] = {}
    hashes: dict[str, list[tuple[str, str]]] = {}
    images: list[tuple[str, str, Image]] = []
    for spec in specs:
        label, _, raw_path = spec.partition("=")
        path = Path(raw_path)
        data = path.read_bytes()
        sources[label] = {"sha256": sha256(data), "bytes": len(data)}
        with zipfile.ZipFile(path) as archive:
            for name in sorted(archive.namelist()):
                if not name.startswith("assets/minecraft/") or name.endswith("/"):
                    continue
                body = archive.read(name)
                hashes.setdefault(sha256(body), []).append((label, name))
                if name.endswith(".png"):
                    try:
                        width, height, pixels = decode_png(body)
                    except (DecodeError, zlib.error, struct.error, IndexError):
                        continue
                    images.append((label, name, Image(width, height, pixels)))
    return sources, hashes, images


def analyse(upstream: Path, specs: list[str], repository: Path) -> dict:
    sources, hashes, images = load_vanilla(specs)
    with (repository / "legacy-manifest/assets.csv").open(encoding="utf-8", newline="") as handle:
        assets = list(csv.DictReader(handle))
    entries: dict[str, dict] = {}
    candidates: list[tuple[str, Image]] = []
    for row in assets:
        relative = row["source_path"][len(ASSET_ROOT):]
        data = (upstream / row["source_path"]).read_bytes()
        if sha256(data) != row["sha256"]:
            raise SystemExit(f"{row['source_path']} differs from legacy-manifest")
        entry: dict = {"asset": relative, "sha256": row["sha256"]}
        entries[relative] = entry
        same = hashes.get(row["sha256"], [])
        if same:
            entry["hash_match"] = [f"{label}:{name}" for label, name in same]
            entry["verdict"] = "HIT"
        elif not relative.endswith(".png"):
            entry["verdict"] = "UNSUPPORTED" if relative.endswith((".jpg", ".jpeg")) else "CLEAR"
        else:
            try:
                width, height, pixels = decode_png(data)
            except (DecodeError, zlib.error, struct.error, IndexError) as error:
                entry.update(verdict="UNSUPPORTED", reason=str(error))
                continue
            entry["size"] = [width, height]
            entry["verdict"] = "CLEAR"
            candidates.append((relative, Image(width, height, pixels)))
    for name, (_, level, record) in analyse_images(candidates, images).items():
        entries[name]["verdict"] = level
        entries[name]["best"] = dict(record)
    results = [entries[row["source_path"][len(ASSET_ROOT):]] for row in assets]
    counts: dict[str, int] = {}
    for entry in results:
        counts[entry["verdict"]] = counts.get(entry["verdict"], 0) + 1
    return {
        "schema": SCHEMA,
        "generator": "tools/audit/vanilla_derivation.py",
        "thresholds": thresholds(),
        "vanilla": sources,
        "counts": dict(sorted(counts.items())),
        "assets": results,
    }


def render(result: dict) -> bytes:
    return (json.dumps(result, ensure_ascii=False, indent=1) + "\n").encode("utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--upstream", required=True, type=Path)
    parser.add_argument("--vanilla", required=True, action="append",
                        help="LABEL=PATH of a vanilla client JAR, for example 1.12.2=client.jar")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args(argv)
    data = render(analyse(arguments.upstream, arguments.vanilla, REPOSITORY_ROOT))
    output = arguments.output if arguments.output.is_absolute() else REPOSITORY_ROOT / arguments.output
    if arguments.check:
        if not output.exists() or output.read_bytes() != data:
            print(f"vanilla derivation: {output} differs from a regenerated result", file=sys.stderr)
            return 1
        print("vanilla derivation: up to date")
        return 0
    output.write_bytes(data)
    print(f"vanilla derivation: wrote {output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
