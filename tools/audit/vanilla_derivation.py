#!/usr/bin/env python3
"""Check legacy assets for derivation from vanilla Minecraft assets (ADR-061 §4.8).

Every legacy asset is compared with the assets of the vanilla client JARs given
on the command line (1.12.2 and 1.20.1). The JARs are read locally and never
copied into the repository. For each asset the tool records:

* file hash equality with any vanilla file;
* for PNG images, against every vanilla PNG of the same size (and of an integer
  scale of 2 or 4): the share of byte-equal RGBA pixels over the pixels opaque
  in either image, not counting the vanilla image's dominant colour and only
  for vanilla images with enough luminance levels (``overlap``), the
  intersection over union of the opaque masks
  (``iou``), and the Spearman rank correlation of luminance over the pixels
  opaque in both (``rank``);
* a verdict: ``HIT`` (presumed vanilla-derived), ``SUSPECT`` (needs a recorded
  human review), ``CLEAR`` or ``UNSUPPORTED`` (a format this tool cannot decode,
  which needs a review like ``SUSPECT``).

The results file is deterministic; ``--check`` regenerates it and compares.
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
SCHEMA = 1

# Thresholds (ADR-061 §4.8).
HIT_OVERLAP = 0.30
HIT_IOU = 0.90
HIT_RANK = 0.90
SUSPECT_OVERLAP = 0.10
SUSPECT_IOU = 0.85
SUSPECT_RANK = 0.75
MIN_SHARED_PIXELS = 32
MIN_LUMINANCE_LEVELS = 8
SCALES = (1, 2, 4)
MAX_PIXELS = 1 << 20


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


def _pearson(a: list[float], b: list[float]) -> float:
    n = len(a)
    mean_a, mean_b = sum(a) / n, sum(b) / n
    cov = sum((x - mean_a) * (y - mean_b) for x, y in zip(a, b))
    var_a = sum((x - mean_a) ** 2 for x in a)
    var_b = sum((y - mean_b) ** 2 for y in b)
    if var_a == 0 or var_b == 0:
        return 0.0
    return cov / (var_a * var_b) ** 0.5


class Image:
    def __init__(self, width: int, height: int, pixels: list[tuple[int, int, int, int]]):
        self.width, self.height, self.pixels = width, height, pixels
        self.opaque = [p[3] > 0 for p in pixels]
        self.luminance = [0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2] for p in pixels]
        self.levels = len({round(v) for v, o in zip(self.luminance, self.opaque) if o})
        counts: dict[tuple[int, int, int, int], int] = {}
        for pixel, opaque in zip(pixels, self.opaque):
            if opaque:
                counts[pixel] = counts.get(pixel, 0) + 1
        self.dominant = max(counts, key=lambda colour: (counts[colour], colour)) if counts else None

    def downscaled(self, factor: int) -> "Image":
        width, height = self.width // factor, self.height // factor
        pixels = [self.pixels[(y * factor) * self.width + x * factor] for y in range(height) for x in range(width)]
        return Image(width, height, pixels)


def compare(candidate: Image, vanilla: Image) -> dict[str, float] | None:
    union = equal = both = informative = 0
    shared_a: list[float] = []
    shared_b: list[float] = []
    for pa, pb, oa, ob, la, lb in zip(candidate.pixels, vanilla.pixels, candidate.opaque, vanilla.opaque,
                                      candidate.luminance, vanilla.luminance):
        if oa or ob:
            union += 1
            # Byte equality counts only on the vanilla image's non-dominant colours, so that
            # shared flat backgrounds, black or white do not look like copying.
            if pb != vanilla.dominant:
                informative += 1
                if pa == pb:
                    equal += 1
            if oa and ob:
                both += 1
                shared_a.append(la)
                shared_b.append(lb)
    if union == 0:
        return None
    overlap = equal / informative if informative and vanilla.levels >= MIN_LUMINANCE_LEVELS else 0.0
    rank = 0.0
    if both >= MIN_SHARED_PIXELS and candidate.levels >= MIN_LUMINANCE_LEVELS:
        rank = _pearson(_ranks(shared_a), _ranks(shared_b))
    return {"overlap": overlap, "iou": both / union, "rank": rank}


def verdict(measure: dict[str, float]) -> str:
    if measure["overlap"] >= HIT_OVERLAP or (measure["iou"] >= HIT_IOU and measure["rank"] >= HIT_RANK):
        return "HIT"
    if measure["overlap"] >= SUSPECT_OVERLAP or (measure["iou"] >= SUSPECT_IOU and measure["rank"] >= SUSPECT_RANK):
        return "SUSPECT"
    return "CLEAR"


SEVERITY = {"CLEAR": 0, "SUSPECT": 1, "HIT": 2}


def load_vanilla(specs: list[str]) -> tuple[dict[str, dict], dict[str, list[tuple[str, str]]], dict[tuple[int, int], list]]:
    sources: dict[str, dict] = {}
    hashes: dict[str, list[tuple[str, str]]] = {}
    images: dict[tuple[int, int], list] = {}
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
                    images.setdefault((width, height), []).append((label, name, Image(width, height, pixels)))
    return sources, hashes, images


def analyse(upstream: Path, specs: list[str], repository: Path) -> dict:
    sources, hashes, images = load_vanilla(specs)
    with (repository / "legacy-manifest/assets.csv").open(encoding="utf-8", newline="") as handle:
        assets = list(csv.DictReader(handle))
    results = []
    for row in assets:
        relative = row["source_path"][len(ASSET_ROOT):]
        data = (upstream / row["source_path"]).read_bytes()
        if sha256(data) != row["sha256"]:
            raise SystemExit(f"{row['source_path']} differs from legacy-manifest")
        entry: dict = {"asset": relative, "sha256": row["sha256"]}
        same = hashes.get(row["sha256"], [])
        if same:
            entry["hash_match"] = [f"{label}:{name}" for label, name in same]
            entry["verdict"] = "HIT"
            results.append(entry)
            continue
        if not relative.endswith(".png"):
            entry["verdict"] = "UNSUPPORTED" if relative.endswith((".jpg", ".jpeg")) else "CLEAR"
            results.append(entry)
            continue
        try:
            width, height, pixels = decode_png(data)
        except (DecodeError, zlib.error, struct.error, IndexError) as error:
            entry.update(verdict="UNSUPPORTED", reason=str(error))
            results.append(entry)
            continue
        candidate = Image(width, height, pixels)
        best = None
        for factor in SCALES:
            for scaled, target_size in ((candidate, (width // factor, height // factor)),):
                if factor > 1:
                    if width % factor or height % factor:
                        continue
                    scaled = candidate.downscaled(factor)
                for label, name, vanilla in images.get(target_size, []):
                    measure = compare(scaled, vanilla)
                    if measure is None:
                        continue
                    level = verdict(measure)
                    key = (SEVERITY[level], measure["overlap"], measure["rank"], measure["iou"])
                    if best is None or key > best[0]:
                        best = (key, level, label, name, factor, measure)
            # Vanilla images larger than the candidate by the factor.
            if factor > 1:
                for label, name, vanilla in images.get((width * factor, height * factor), []):
                    measure = compare(candidate, vanilla.downscaled(factor))
                    if measure is None:
                        continue
                    level = verdict(measure)
                    key = (SEVERITY[level], measure["overlap"], measure["rank"], measure["iou"])
                    if best is None or key > best[0]:
                        best = (key, level, label, name, -factor, measure)
        entry["size"] = [width, height]
        if best is None:
            entry["verdict"] = "CLEAR"
        else:
            _, level, label, name, factor, measure = best
            entry["verdict"] = level
            entry["best"] = {
                "vanilla": f"{label}:{name}",
                "scale": factor,
                "overlap": round(measure["overlap"], 4),
                "iou": round(measure["iou"], 4),
                "rank": round(measure["rank"], 4),
            }
        results.append(entry)
    counts: dict[str, int] = {}
    for entry in results:
        counts[entry["verdict"]] = counts.get(entry["verdict"], 0) + 1
    return {
        "schema": SCHEMA,
        "generator": "tools/audit/vanilla_derivation.py",
        "thresholds": {
            "hit_overlap": HIT_OVERLAP, "hit_iou": HIT_IOU, "hit_rank": HIT_RANK,
            "suspect_overlap": SUSPECT_OVERLAP, "suspect_iou": SUSPECT_IOU, "suspect_rank": SUSPECT_RANK,
            "min_shared_pixels": MIN_SHARED_PIXELS, "min_luminance_levels": MIN_LUMINANCE_LEVELS,
            "scales": list(SCALES),
        },
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
