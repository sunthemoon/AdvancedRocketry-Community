#!/usr/bin/env python3
"""Check legacy assets for derivation from vanilla Minecraft assets (ADR-061 §4.8).

Every legacy asset is compared with the assets of the vanilla client JARs given
on the command line (1.12.2 and 1.20.1) and with the other legacy assets. The
JARs are read locally and never copied into the repository.

Measures, per PNG image:

* file hash equality (also between legacy files);
* whole-image measures, in all eight orientations, at the same size, at an
  integer scale of 2 or 4 in either direction, and between the first frames of
  animated strips:
  - ``overlap``: byte-equal RGBA pixels over the pixels opaque in either image,
    leaving out the reference's dominant colour when it has four or more
    colours, with the number of distinct matched colours in ``colours``;
  - ``iou``: intersection over union of the opaque masks;
  - ``rank``: Spearman rank correlation of luminance over the shared opaque
    pixels, when both images have at least three luminance levels;
* a region search: every 4 x 4 block of the candidate, in all eight
  orientations and downscaled by 2 (two phases) and 4, is looked up among the
  grid-aligned 4 x 4 blocks of every reference at scales 1, 1/2 and 1/4 (blocks
  of at least three colours; a block found more than 64 times is ignored). A
  candidate up to 64 px a side is also looked up by its blocks' colour-equality
  pattern, which survives recolouring and small colour shifts. Each block match
  votes for an alignment; the best alignments are verified pixel by pixel over
  the overlapping rectangle (at least 64 pixels):
  - ``exact``: the share of informative pixels (outside the reference's
    dominant colour) that are byte-equal, with ``colours``;
  - ``near``: the share within 2 per channel, with ``near_colours``;
  - ``mapped``: how consistently each reference colour maps to one candidate
    colour (a recolour), over ``classes`` reference colours seen twice or more.
  The same search runs the other way for candidates up to 64 px a side: their
  own grid-aligned blocks (all orientations and scales) are looked up at every
  position of every reference (exact pixels only; a block seen more than 4,096
  times in the references stops voting), so an edited crop is found even when no
  reference grid block lies inside the unedited part. This finds crops, edited
  crops, scaled crops and vanilla sprites inside larger legacy sheets.
* whole-image measures of images larger than 64 px a side are taken on every
  k-th pixel in both images (k the smallest power of two that brings the side
  to 64 px or less); the region search covers such images in full.

Verdicts: ``HIT`` (presumed derived), ``SUSPECT`` (needs a recorded human
review), ``CLEAR`` or ``UNSUPPORTED`` (a format the tool cannot decode,
reviewed like ``SUSPECT``):

* ``HIT``: equal hashes; ``iou`` >= 0.90 with ``rank`` >= 0.90; ``overlap`` >=
  0.30 or region ``exact`` >= 0.75, each over at least three matched colours;
* ``SUSPECT``: ``iou`` >= 0.85 with ``rank`` >= 0.75; ``overlap`` >= 0.10 or
  region ``exact`` >= 0.50 over at least three matched colours, or at the
  ``HIT`` level over two; region ``near`` >= 0.75 over at least three colours;
  region ``mapped`` >= 0.90 over at least six colour classes on each side.

One shared flat colour is no evidence, and two colours match a shape rather
than pixel art, so a two-colour match counts only when it is strong.
``related`` lists every such match from this file's side of the search; the
measures are not symmetric (the dominant colour left out is the reference's),
so a relation counts for inheritance when either file lists the other.
``mapped`` is the lower of the two directions' consistency (reference colour to
candidate colour and back), so a flat candidate area does not map onto a
detailed reference. Each legacy asset also lists the other legacy assets it matches
at ``SUSPECT`` level or above (``related``), so that a derivative of an excluded
or quarantined file can inherit that handling (ADR-061 §4.8).

Known limits: a recolour that merges colours inside most 4 x 4 blocks, a
recoloured or colour-shifted region inside a candidate larger than 64 px a side,
and a copy whose blocks all have fewer than three colours are found only as
whole images.

The results file is deterministic; ``--check`` regenerates it and compares.
"""

from __future__ import annotations

import argparse
import csv
from array import array
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
SCHEMA = 3
LEGACY = "legacy"

# Whole-image thresholds (ADR-061 §4.8).
HIT_OVERLAP = 0.30
HIT_IOU = 0.90
HIT_RANK = 0.90
SUSPECT_OVERLAP = 0.10
SUSPECT_IOU = 0.85
SUSPECT_RANK = 0.75
MIN_SHARED_PIXELS = 32
MIN_INFORMATIVE_PIXELS = 16
MIN_RANK_LEVELS = 3
DOMINANT_EXCLUSION_COLOURS = 4
MIN_EVIDENCE_COLOURS = 2
MIN_MATCHED_COLOURS = 3
SCALES = (2, 4)
# Region search.
BLOCK = 4
MIN_BLOCK_COLOURS = 3
POSTING_CAP = 64
OFFSETS_PER_REFERENCE = 2
OFFSETS_PER_VARIANT = 16
MIN_VOTES_LARGE = 2
PATTERN_MAX_SIDE = 64
MIN_REGION_PIXELS = 64
HIT_REGION = 0.75
SUSPECT_REGION = 0.50
SUSPECT_NEAR = 0.75
NEAR_DELTA = 2
SUSPECT_MAPPED = 0.90
MIN_MAPPED_CLASSES = 6
WHOLE_IMAGE_MAX_SIDE = 64
REFERENCE_SCAN_CAP = 4096
MAX_PIXELS = 1 << 20


def thresholds() -> dict:
    return {
        "hit_overlap": HIT_OVERLAP, "hit_iou": HIT_IOU, "hit_rank": HIT_RANK,
        "suspect_overlap": SUSPECT_OVERLAP, "suspect_iou": SUSPECT_IOU, "suspect_rank": SUSPECT_RANK,
        "min_shared_pixels": MIN_SHARED_PIXELS, "min_informative_pixels": MIN_INFORMATIVE_PIXELS,
        "min_rank_levels": MIN_RANK_LEVELS, "dominant_exclusion_colours": DOMINANT_EXCLUSION_COLOURS,
        "min_evidence_colours": MIN_EVIDENCE_COLOURS, "min_matched_colours": MIN_MATCHED_COLOURS,
        "scales": list(SCALES), "orientations": 8,
        "block": BLOCK, "min_block_colours": MIN_BLOCK_COLOURS, "posting_cap": POSTING_CAP,
        "offsets_per_reference": OFFSETS_PER_REFERENCE, "offsets_per_variant": OFFSETS_PER_VARIANT,
        "min_votes_large": MIN_VOTES_LARGE, "pattern_max_side": PATTERN_MAX_SIDE,
        "min_region_pixels": MIN_REGION_PIXELS, "hit_region": HIT_REGION, "suspect_region": SUSPECT_REGION,
        "suspect_near": SUSPECT_NEAR, "near_delta": NEAR_DELTA, "suspect_mapped": SUSPECT_MAPPED,
        "min_mapped_classes": MIN_MAPPED_CLASSES,
        "whole_image_max_side": WHOLE_IMAGE_MAX_SIDE, "reference_scan_cap": REFERENCE_SCAN_CAP,
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


def decode_rgba(data: bytes) -> tuple[int, int, bytes]:
    """Decode a non-interlaced PNG into (width, height, RGBA bytes); transparent pixels become 0, 0, 0, 0."""
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
            palette = [bytes(body[i:i + 3]) for i in range(0, len(body), 3)]
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
    out = bytearray()
    previous, offset = bytearray(stride), 0
    for _ in range(height):
        method = raw[offset]
        line = bytearray(raw[offset + 1:offset + 1 + stride])
        offset += 1 + stride
        if method == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xFF
        elif method == 2:
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 0xFF
        elif method == 3:
            for i in range(stride):
                left = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 0xFF
        elif method == 4:
            for i in range(stride):
                left = line[i - step] if i >= step else 0
                corner = previous[i - step] if i >= step else 0
                line[i] = (line[i] + _paeth(left, previous[i], corner)) & 0xFF
        elif method != 0:
            raise DecodeError(f"bad filter {method}")
        previous = line
        if colour == 6 and depth == 8:
            out += line
            continue
        if depth < 8:
            per_byte = 8 // depth
            mask = (1 << depth) - 1
            samples = [(line[i // per_byte] >> (8 - depth * (i % per_byte + 1))) & mask for i in range(width)]
        elif depth == 8:
            samples = line
        else:
            samples = line[0::2]
        for x in range(width):
            if colour == 3:
                index = samples[x]
                if palette is None or index >= len(palette):
                    raise DecodeError("bad palette index")
                out += palette[index]
                out.append(transparency[index] if transparency and index < len(transparency) else 255)
            elif colour in (0, 4):
                value = samples[x * channels]
                if depth < 8:
                    value = value * 255 // ((1 << depth) - 1)
                out += bytes((value, value, value, samples[x * channels + 1] if colour == 4 else 255))
            else:
                base = x * channels
                out += bytes((samples[base], samples[base + 1], samples[base + 2],
                              samples[base + 3] if colour == 6 else 255))
    if out[3::4].count(0):
        for i in range(3, len(out), 4):
            if out[i] == 0:
                out[i - 3] = out[i - 2] = out[i - 1] = 0
    return width, height, bytes(out)


def decode_png(data: bytes) -> tuple[int, int, list[tuple[int, int, int, int]]]:
    """Decode a PNG into (width, height, RGBA pixel tuples)."""
    width, height, rgba = decode_rgba(data)
    return width, height, [tuple(rgba[i:i + 4]) for i in range(0, len(rgba), 4)]


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
    """RGBA pixels as bytes; the per-colour masks and ranks for whole-image measures are built on demand."""

    def __init__(self, width: int, height: int, pixels):
        self.width, self.height, self.size = width, height, width * height
        if isinstance(pixels, (bytes, bytearray)):
            self.rgba = bytes(pixels)
        else:
            self.rgba = b"".join(bytes(pixel) if pixel[3] else b"\0\0\0\0" for pixel in pixels)
        if len(self.rgba) != 4 * self.size:
            raise ValueError("pixel count does not match the size")
        self._prepared = False
        self._dominant_region: bytes | None = None

    @property
    def pixels(self) -> list[Pixel]:
        rgba = self.rgba
        return [tuple(rgba[i:i + 4]) for i in range(0, len(rgba), 4)]

    def cells(self) -> list[bytes]:
        rgba = self.rgba
        return [rgba[i:i + 4] for i in range(0, len(rgba), 4)]

    def prepare(self) -> "Image":
        if self._prepared:
            return self
        cells = self.cells()
        length = (self.size + 7) // 8
        bitmaps: dict[bytes, bytearray] = {}
        opaque_bits = bytearray(length)
        for index, cell in enumerate(cells):
            if cell[3]:
                byte, bit = index >> 3, 1 << (index & 7)
                opaque_bits[byte] |= bit
                bitmap = bitmaps.get(cell)
                if bitmap is None:
                    bitmap = bitmaps[cell] = bytearray(length)
                bitmap[byte] |= bit
        self.colour_masks = {colour: int.from_bytes(bitmap, "little") for colour, bitmap in bitmaps.items()}
        self.opaque = int.from_bytes(opaque_bits, "little")
        self.opaque_count = _popcount(self.opaque)
        self.colours = len(self.colour_masks)
        counts = {colour: _popcount(mask) for colour, mask in self.colour_masks.items()}
        self.dominant = max(counts, key=lambda colour: (counts[colour], colour)) if counts else None
        self.luminance = array("d", (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2] for c in cells))
        self.levels = len({round(self.luminance[i]) for i in range(self.size) if cells[i][3]})
        full = self.opaque_count == self.size
        if full and self.levels >= MIN_RANK_LEVELS:
            vector, norm = _centred(_ranks(self.luminance))
            self.rank_vector, self.rank_norm = array("d", vector), norm
        else:
            self.rank_vector, self.rank_norm = None, 0.0
        self._prepared = True
        return self

    def release(self) -> None:
        """Drop the whole-image fields; the pixels stay."""
        for name in ("colour_masks", "opaque", "opaque_count", "colours", "dominant", "luminance", "levels",
                     "rank_vector", "rank_norm"):
            self.__dict__.pop(name, None)
        self._prepared = False

    def region_dominant(self) -> bytes | None:
        """The dominant colour when the image has enough colours to leave it out of region measures."""
        if self._dominant_region is None:
            counts: dict[bytes, int] = {}
            for cell in self.cells():
                if cell[3]:
                    counts[cell] = counts.get(cell, 0) + 1
            dominant = max(counts, key=lambda colour: (counts[colour], colour)) if counts else b""
            self._dominant_region = dominant if len(counts) >= DOMINANT_EXCLUSION_COLOURS else b""
        return self._dominant_region or None

    def transformed(self, orientation: int) -> "Image":
        """One of the eight orientations: 0–3 rotate by 90° steps, 4–7 mirror first."""
        if orientation == 0:
            return self
        width, cells = self.width, self.cells()
        grid = [cells[y * width:(y + 1) * width] for y in range(self.height)]
        if orientation >= 4:
            grid = [row[::-1] for row in grid]
        for _ in range(orientation % 4):
            grid = [list(row) for row in zip(*grid[::-1])]
        return Image(len(grid[0]), len(grid), b"".join(cell for row in grid for cell in row))

    def downscaled(self, factor: int, phase: int = 0) -> "Image":
        width, height = (self.width - phase) // factor, (self.height - phase) // factor
        rgba, stride = self.rgba, self.width
        parts = []
        for y in range(height):
            row = (y * factor + phase) * stride + phase
            for x in range(width):
                start = (row + x * factor) * 4
                parts.append(rgba[start:start + 4])
        return Image(width, height, b"".join(parts))

    def first_frame(self) -> "Image | None":
        if self.height >= 2 * self.width and self.height % self.width == 0:
            return Image(self.width, self.width, self.rgba[: 4 * self.width * self.width])
        return None


def compare(candidate: Image, reference: Image) -> dict[str, float] | None:
    """Same-size measures; None when neither image has an opaque pixel."""
    candidate.prepare()
    reference.prepare()
    union = _popcount(candidate.opaque | reference.opaque)
    if union == 0:
        return None
    both_mask = candidate.opaque & reference.opaque
    both = _popcount(both_mask)
    overlap = 0.0
    matched_colours = 0
    if reference.colours >= 2:
        excluded = reference.dominant if reference.colours >= DOMINANT_EXCLUSION_COLOURS else None
        equal = 0
        for colour in candidate.colour_masks.keys() & reference.colour_masks.keys():
            if colour != excluded:
                shared = _popcount(candidate.colour_masks[colour] & reference.colour_masks[colour])
                equal += shared
                matched_colours += shared > 0
        informative = union - (_popcount(reference.colour_masks[excluded]) if excluded is not None else 0)
        if informative >= MIN_INFORMATIVE_PIXELS:
            overlap = equal / informative
    rank = 0.0
    if both >= MIN_SHARED_PIXELS and candidate.levels >= MIN_RANK_LEVELS and reference.levels >= MIN_RANK_LEVELS:
        if candidate.rank_vector is not None and reference.rank_vector is not None:
            rank = sum(x * y for x, y in zip(candidate.rank_vector, reference.rank_vector)) / (
                candidate.rank_norm * reference.rank_norm)
        elif both / union >= SUSPECT_IOU:
            indices = [i for i in range(candidate.size) if both_mask >> i & 1]
            rank = _correlation(_ranks([candidate.luminance[i] for i in indices]),
                                _ranks([reference.luminance[i] for i in indices]))
    return {"overlap": overlap, "colours": matched_colours, "iou": both / union, "rank": rank}


def verdict(measure: dict) -> str:
    """The level a recorded measure produces (whole-image or region)."""
    colours = measure.get("colours", 0)
    if measure.get("method") == "region":
        if measure.get("region", 0) < MIN_REGION_PIXELS:
            return "CLEAR"
        if measure["exact"] >= HIT_REGION and colours >= MIN_MATCHED_COLOURS:
            return "HIT"
        if (measure["exact"] >= SUSPECT_REGION and colours >= MIN_MATCHED_COLOURS) or (
                measure["exact"] >= HIT_REGION and colours >= MIN_EVIDENCE_COLOURS) or (
                measure.get("near", 0.0) >= SUSPECT_NEAR and measure.get("near_colours", 0) >= MIN_MATCHED_COLOURS) or (
                measure.get("mapped", 0.0) >= SUSPECT_MAPPED and measure.get("classes", 0) >= MIN_MAPPED_CLASSES):
            return "SUSPECT"
        return "CLEAR"
    if (colours >= MIN_MATCHED_COLOURS and measure["overlap"] >= HIT_OVERLAP) or (
            measure["iou"] >= HIT_IOU and measure["rank"] >= HIT_RANK):
        return "HIT"
    if (colours >= MIN_MATCHED_COLOURS and measure["overlap"] >= SUSPECT_OVERLAP) or (
            colours >= MIN_EVIDENCE_COLOURS and measure["overlap"] >= HIT_OVERLAP) or (
            measure["iou"] >= SUSPECT_IOU and measure["rank"] >= SUSPECT_RANK):
        return "SUSPECT"
    return "CLEAR"


SEVERITY = {"CLEAR": 0, "SUSPECT": 1, "HIT": 2}


def _strength(measure: dict) -> float:
    """Orders matches of the same level: a measure counts only with the colours its rule needs, so the
    recorded best match of a CLEAR file is its strongest real one, not a mapping over one colour class."""
    colours = measure.get("colours", 0)
    if measure.get("method") == "region":
        exact = measure["exact"] if colours >= MIN_EVIDENCE_COLOURS else 0.0
        near = measure.get("near", 0.0) if measure.get("near_colours", 0) >= MIN_MATCHED_COLOURS else 0.0
        mapped = measure.get("mapped", 0.0) if measure.get("classes", 0) >= MIN_MAPPED_CLASSES else 0.0
        return max(exact, 0.5 * near, 0.5 * mapped)
    overlap = measure["overlap"] if colours >= MIN_EVIDENCE_COLOURS else 0.0
    return max(overlap, measure["iou"] * measure["rank"])


def _block_cells(rgba: bytes, start: int, stride: int) -> list[bytes]:
    cells = []
    for row in range(BLOCK):
        line = rgba[start + row * stride:start + row * stride + 4 * BLOCK]
        cells.extend(line[i:i + 4] for i in range(0, 4 * BLOCK, 4))
    return cells


def _pattern(cells: list[bytes]) -> bytes:
    seen: dict[bytes, int] = {}
    return bytes(seen.setdefault(cell, len(seen)) for cell in cells)


class RegionIndex:
    """Grid-aligned 4 x 4 blocks of the references, by exact pixels and (vanilla only) by colour pattern."""

    def __init__(self) -> None:
        self.variants: list[tuple[str, str, int, Image]] = []  # label, name, scale, image
        self.exact: dict[bytes, list[int]] = {}
        self.pattern: dict[bytes, list[int]] = {}

    def add(self, label: str, name: str, image: Image, scale: int, patterns: bool) -> None:
        number = len(self.variants)
        self.variants.append((label, name, scale, image))
        stride, rgba = 4 * image.width, image.rgba
        for gy in range(0, image.height - BLOCK + 1, BLOCK):
            for gx in range(0, image.width - BLOCK + 1, BLOCK):
                start = gy * stride + 4 * gx
                cells = _block_cells(rgba, start, stride)
                if len(set(cells)) < MIN_BLOCK_COLOURS:
                    continue
                posting = (number << 24) | (gx << 12) | gy
                self.exact.setdefault(b"".join(cells), []).append(posting)
                if patterns:
                    self.pattern.setdefault(_pattern(cells), []).append(posting)

    def finish(self) -> None:
        for table in (self.exact, self.pattern):
            for key in [key for key, postings in table.items() if len(postings) > POSTING_CAP]:
                del table[key]


def _region_measures(candidate: Image, reference: Image, dx: int, dy: int, full: bool) -> dict | None:
    """Pixel-by-pixel measures over the rectangle where candidate (x, y) meets reference (x + dx, y + dy)."""
    x0, y0 = max(0, -dx), max(0, -dy)
    x1, y1 = min(candidate.width, reference.width - dx), min(candidate.height, reference.height - dy)
    if x1 - x0 < 1 or y1 - y0 < 1 or (x1 - x0) * (y1 - y0) < MIN_REGION_PIXELS:
        return None
    dominant = reference.region_dominant()
    cw, rw = 4 * candidate.width, 4 * reference.width
    union = informative = equal = near = 0
    equal_colours: set[bytes] = set()
    near_colours: set[bytes] = set()
    forward: dict[bytes, dict[bytes, int]] = {}
    backward: dict[bytes, dict[bytes, int]] = {}
    for y in range(y0, y1):
        line = candidate.rgba[y * cw + 4 * x0:y * cw + 4 * x1]
        other = reference.rgba[(y + dy) * rw + 4 * (x0 + dx):(y + dy) * rw + 4 * (x1 + dx)]
        for i in range(0, len(line), 4):
            c, r = line[i:i + 4], other[i:i + 4]
            if not (c[3] or r[3]):
                continue
            union += 1
            if r == dominant:
                continue
            informative += 1
            if c == r:
                equal += 1
                equal_colours.add(r)
                if full:
                    near += 1
                    near_colours.add(r)
            elif full and c[3] and r[3] and abs(c[0] - r[0]) <= NEAR_DELTA and abs(c[1] - r[1]) <= NEAR_DELTA \
                    and abs(c[2] - r[2]) <= NEAR_DELTA and abs(c[3] - r[3]) <= NEAR_DELTA:
                near += 1
                near_colours.add(r)
            if full and r[3] and c[3]:
                targets = forward.setdefault(r, {})
                targets[c] = targets.get(c, 0) + 1
                sources = backward.setdefault(c, {})
                sources[r] = sources.get(r, 0) + 1
    if union < MIN_REGION_PIXELS or informative < MIN_INFORMATIVE_PIXELS:
        return None
    measure = {"region": union, "exact": equal / informative, "colours": len(equal_colours)}
    if full:
        forward_share, forward_classes = _consistency(forward)
        backward_share, backward_classes = _consistency(backward)
        measure.update(near=near / informative, near_colours=len(near_colours),
                       mapped=min(forward_share, backward_share), classes=min(forward_classes, backward_classes))
    return measure


def _consistency(table: dict[bytes, dict[bytes, int]]) -> tuple[float, int]:
    """How consistently each colour maps to one colour, beyond the first pixel of each class seen twice or more."""
    agree = total = counted = 0
    for targets in table.values():
        size = sum(targets.values())
        if size >= 2:
            agree += max(targets.values()) - 1
            total += size - 1
            counted += 1
    return (agree / total if total else 0.0), counted


class Matcher:
    """Best match per candidate against vanilla references, and matches against the other legacy files."""

    def __init__(self, references: list[tuple[str, str, Image]]):
        self.references = references
        self.best: dict[str, tuple] = {}
        self.related: dict[str, dict[str, tuple]] = {}
        self.order = 0

    def offer(self, name: str, label: str, reference: str, measure: dict, record: dict) -> None:
        rounded = {key: (round(value, 4) if isinstance(value, float) else value) for key, value in measure.items()}
        level = verdict(rounded)
        merged = dict(record)
        merged.update(rounded)
        self.order += 1
        key = (SEVERITY[level], round(_strength(rounded), 4), -self.order)
        if label == LEGACY:
            if level == "CLEAR":
                return
            table = self.related.setdefault(name, {})
            current = table.get(reference)
            if current is None or key > current[0]:
                table[reference] = (key, level, merged)
            return
        current = self.best.get(name)
        if current is None or key > current[0]:
            self.best[name] = (key, level, merged)

    # Whole-image measures, one size at a time so that only one group's masks are in memory.
    def whole_images(self, candidates: list[tuple[str, Image]]) -> None:
        reference_sizes: dict[tuple[int, int], list[tuple[str, str, int, Image]]] = {}
        for label, name, image in self.references:
            reference_sizes.setdefault((image.width, image.height), []).append((label, name, 0, image))
            frame_height = image.width
            if image.height >= 2 * image.width and image.height % image.width == 0:
                reference_sizes.setdefault((image.width, frame_height), []).append((label, name, -1, image))
            if label != LEGACY:
                for factor in SCALES:
                    if image.width % factor == 0 and image.height % factor == 0:
                        size = (image.width // factor, image.height // factor)
                        reference_sizes.setdefault(size, []).append((label, name, factor, image))
        requests: dict[tuple[int, int], list[tuple[str, Image, dict, bool]]] = {}
        for name, image in candidates:
            for orientation in range(8):
                size = (image.width, image.height) if orientation % 2 == 0 else (image.height, image.width)
                if size in reference_sizes:
                    requests.setdefault(size, []).append((name, image.transformed(orientation),
                                                          {"method": "same", "orientation": orientation}, False))
            frame = image.first_frame()
            if frame is not None and (frame.width, frame.height) in reference_sizes:
                requests.setdefault((frame.width, frame.height), []).append(
                    (name, frame, {"method": "same", "orientation": "frame0"}, False))
            for factor in SCALES:
                if image.width % factor == 0 and image.height % factor == 0:
                    size = (image.width // factor, image.height // factor)
                    if size in reference_sizes:
                        requests.setdefault(size, []).append(
                            (name, image.downscaled(factor), {"method": "scaled", "scale": factor}, True))
        for size in sorted(requests):
            built = []
            for label, name, kind, image in reference_sizes[size]:
                if kind == 0:
                    built.append((label, name, kind, image))
                elif kind == -1:
                    built.append((label, name + "#frame0", kind, image.first_frame()))
                else:
                    built.append((label, name, kind, image.downscaled(kind)))
            sample = 1
            while max(size) // sample > WHOLE_IMAGE_MAX_SIDE:
                sample *= 2
            if sample > 1:
                built = [(label, name, kind, other.downscaled(sample)) for label, name, kind, other in built]
            for name, variant, base, scaled in requests[size]:
                compared = variant.downscaled(sample) if sample > 1 else variant
                for label, reference, kind, other in built:
                    if label == LEGACY and reference.split("#", 1)[0] == name:
                        continue
                    if scaled and kind > 0:
                        continue
                    measure = compare(compared, other)
                    if measure is None:
                        continue
                    record = dict(base)
                    if kind > 0:
                        record.update(method="scaled", scale=-kind)
                    if sample > 1:
                        record["sampled"] = sample
                    record["reference"] = f"{label}:{reference}"
                    self.offer(name, label, reference.split("#", 1)[0], measure, record)
                compared.release()
            for _, _, _, other in built:
                other.release()

    def regions(self, candidates: list[tuple[str, Image]]) -> None:
        index = RegionIndex()
        for label, name, image in self.references:
            if label == LEGACY:
                index.add(label, name, image, 1, False)
                continue
            index.add(label, name, image, 1, True)
            for factor in SCALES:
                if image.width >= factor * BLOCK and image.height >= factor * BLOCK:
                    index.add(label, name, image.downscaled(factor), factor, True)
        index.finish()
        own: dict[str, set[int]] = {}
        for number, (label, name, _, _) in enumerate(index.variants):
            if label == LEGACY:
                own.setdefault(name, set()).add(number)
        for name, image in candidates:
            variants = []
            for factor, phases in ((1, (0,)), (2, (0, 1)), (4, (0,))):
                for phase in phases:
                    base = image if factor == 1 else image.downscaled(factor, phase)
                    if base.width < BLOCK or base.height < BLOCK:
                        continue
                    for orientation in range(8):
                        variants.append((orientation, factor, phase, base.transformed(orientation)))
            for orientation, factor, phase, variant in variants:
                self._scan(index, name, own.get(name, set()), variant,
                           {"method": "region", "orientation": orientation, "candidate_scale": factor, "phase": phase})

    def small_regions(self, candidates: list[tuple[str, Image]]) -> None:
        """Grid blocks of the candidates up to 64 px a side, looked up at every position of every vanilla image."""
        exact: dict[bytes, list[int]] = {}
        variants: list[tuple[str, dict, Image]] = []
        for name, image in candidates:
            if image.width > PATTERN_MAX_SIDE or image.height > PATTERN_MAX_SIDE:
                continue
            for factor, phases in ((1, (0,)), (2, (0, 1)), (4, (0,))):
                for phase in phases:
                    base = image if factor == 1 else image.downscaled(factor, phase)
                    if base.width < BLOCK or base.height < BLOCK:
                        continue
                    for orientation in range(8):
                        variant = base.transformed(orientation)
                        number = len(variants)
                        variants.append((name, {"method": "region", "orientation": orientation,
                                                "candidate_scale": factor, "phase": phase}, variant))
                        stride = 4 * variant.width
                        for gy in range(0, variant.height - BLOCK + 1, BLOCK):
                            for gx in range(0, variant.width - BLOCK + 1, BLOCK):
                                cells = _block_cells(variant.rgba, gy * stride + 4 * gx, stride)
                                if len(set(cells)) >= MIN_BLOCK_COLOURS:
                                    exact.setdefault(b"".join(cells), []).append((number << 24) | (gx << 12) | gy)
        for key in [key for key, postings in exact.items() if len(postings) > POSTING_CAP]:
            del exact[key]
        references: list[tuple[str, str, int, Image]] = []
        for label, name, image in self.references:
            if label == LEGACY:
                continue
            references.append((label, name, 1, image))
            for factor in SCALES:
                if image.width >= factor * BLOCK and image.height >= factor * BLOCK:
                    references.append((label, name, factor, image.downscaled(factor)))
        seen: dict[bytes, int] = {}
        votes: dict[tuple[int, int, int, int], int] = {}
        for number, (_, _, _, image) in enumerate(references):
            stride, rgba = 4 * image.width, image.rgba
            for y in range(image.height - BLOCK + 1):
                for x in range(image.width - BLOCK + 1):
                    start = y * stride + 4 * x
                    key = (rgba[start:start + 16] + rgba[start + stride:start + stride + 16]
                           + rgba[start + 2 * stride:start + 2 * stride + 16]
                           + rgba[start + 3 * stride:start + 3 * stride + 16])
                    postings = exact.get(key)
                    if postings is None:
                        continue
                    count = seen.get(key, 0) + 1
                    seen[key] = count
                    if count > REFERENCE_SCAN_CAP:
                        del exact[key]
                        continue
                    for posting in postings:
                        alignment = (posting >> 24, number, x - ((posting >> 12) & 0xFFF), y - (posting & 0xFFF))
                        votes[alignment] = votes.get(alignment, 0) + 1
        chosen: dict[int, dict[int, list[tuple[int, int, int]]]] = {}
        for (variant_number, number, dx, dy), count in votes.items():
            chosen.setdefault(variant_number, {}).setdefault(number, []).append((-count, dx, dy))
        del votes
        for variant_number in sorted(chosen):
            name, base, variant = variants[variant_number]
            offsets = []
            for number, alignments in chosen[variant_number].items():
                for negative, dx, dy in sorted(alignments)[:OFFSETS_PER_REFERENCE]:
                    offsets.append((negative, number, dx, dy))
            for negative, number, dx, dy in sorted(offsets)[:OFFSETS_PER_VARIANT]:
                label, reference, scale, other = references[number]
                measure = _region_measures(variant, other, dx, dy, True)
                if measure is None:
                    continue
                record = dict(base)
                record.update(reference=f"{label}:{reference}", reference_scale=scale, offset=[dx, dy],
                              votes=-negative, search="reference positions")
                self.offer(name, label, reference, dict(measure, method="region"), record)

    def _scan(self, index: RegionIndex, name: str, own: set[int], variant: Image, base: dict) -> None:
        full = variant.width <= PATTERN_MAX_SIDE and variant.height <= PATTERN_MAX_SIDE
        votes: dict[tuple[int, int, int], int] = {}
        stride, rgba = 4 * variant.width, variant.rgba
        exact, pattern, variants = index.exact, index.pattern, index.variants
        for y in range(variant.height - BLOCK + 1):
            for x in range(variant.width - BLOCK + 1):
                start = y * stride + 4 * x
                key = (rgba[start:start + 16] + rgba[start + stride:start + stride + 16]
                       + rgba[start + 2 * stride:start + 2 * stride + 16]
                       + rgba[start + 3 * stride:start + 3 * stride + 16])
                found = exact.get(key)
                if full:
                    shape = pattern.get(_pattern([key[i:i + 4] for i in range(0, 64, 4)]))
                    found = (found or []) + (shape or []) if shape else found
                if not found:
                    continue
                for posting in found:
                    number = posting >> 24
                    if number in own:
                        continue
                    alignment = (number, ((posting >> 12) & 0xFFF) - x, (posting & 0xFFF) - y)
                    votes[alignment] = votes.get(alignment, 0) + 1
        if not votes:
            return
        minimum = 1 if full else MIN_VOTES_LARGE
        per_reference: dict[int, list[tuple[int, int, int]]] = {}
        for (number, dx, dy), count in votes.items():
            if count >= minimum:
                per_reference.setdefault(number, []).append((-count, dx, dy))
        chosen, related = [], []
        for number, offsets in per_reference.items():
            for negative, dx, dy in sorted(offsets)[:OFFSETS_PER_REFERENCE]:
                (related if variants[number][0] == LEGACY else chosen).append((negative, number, dx, dy))
        # Vanilla references share one budget; every legacy reference keeps its own two alignments, so a tile
        # shared by many legacy files cannot crowd out a relation.
        for negative, number, dx, dy in sorted(chosen)[:OFFSETS_PER_VARIANT] + sorted(related):
            label, reference, scale, other = variants[number]
            measure = _region_measures(variant, other, dx, dy, full)
            if measure is None:
                continue
            record = dict(base)
            record.update(reference=f"{label}:{reference}", reference_scale=scale, offset=[dx, dy], votes=-negative)
            self.offer(name, label, reference, dict(measure, method="region"), record)


def match_images(candidates: list[tuple[str, Image]], references: list[tuple[str, str, Image]]) -> Matcher:
    """Run every measure; references labelled ``legacy`` produce ``related`` matches instead of verdicts."""
    matcher = Matcher(references)
    matcher.whole_images(candidates)
    matcher.regions(candidates)
    matcher.small_regions(candidates)
    return matcher


def analyse_images(candidates: list[tuple[str, Image]], vanilla: list[tuple[str, str, Image]]) -> dict[str, tuple]:
    """Best (key, verdict, record) per candidate name against the vanilla images (for tests and probes)."""
    return match_images(candidates, vanilla).best


def load_vanilla(specs: list[str]) -> tuple[dict[str, dict], dict[str, list[tuple[str, str]]], list]:
    sources: dict[str, dict] = {}
    hashes: dict[str, list[tuple[str, str]]] = {}
    images: list[tuple[str, str, Image]] = []
    for spec in specs:
        label, _, raw_path = spec.partition("=")
        path = Path(raw_path)
        data = path.read_bytes()
        sources[label] = {"sha256": sha256(data), "bytes": len(data)}
        del data
        with zipfile.ZipFile(path) as archive:
            for name in sorted(archive.namelist()):
                if not name.startswith("assets/minecraft/") or name.endswith("/"):
                    continue
                body = archive.read(name)
                hashes.setdefault(sha256(body), []).append((label, name))
                if name.endswith(".png"):
                    try:
                        width, height, rgba = decode_rgba(body)
                    except (DecodeError, zlib.error, struct.error, IndexError):
                        continue
                    images.append((label, name, Image(width, height, rgba)))
    return sources, hashes, images


def _related_entries(matcher: Matcher, name: str) -> list[dict]:
    """Every legacy file this one matches at SUSPECT level or above, from this file's side of the search."""
    table = matcher.related.get(name, {})
    return [dict({"asset": other, "level": level}, **record) for other, (_, level, record) in sorted(table.items())]


def analyse(upstream: Path, specs: list[str], repository: Path) -> dict:
    sources, hashes, vanilla = load_vanilla(specs)
    with (repository / "legacy-manifest/assets.csv").open(encoding="utf-8", newline="") as handle:
        assets = list(csv.DictReader(handle))
    entries: dict[str, dict] = {}
    candidates: list[tuple[str, Image]] = []
    by_hash: dict[str, list[str]] = {}
    for row in assets:
        relative = row["source_path"][len(ASSET_ROOT):]
        data = (upstream / row["source_path"]).read_bytes()
        if sha256(data) != row["sha256"]:
            raise SystemExit(f"{row['source_path']} differs from legacy-manifest")
        by_hash.setdefault(row["sha256"], []).append(relative)
        entry: dict = {"asset": relative, "sha256": row["sha256"]}
        entries[relative] = entry
        same = hashes.get(row["sha256"], [])
        if same:
            entry["hash_match"] = [f"{label}:{name}" for label, name in same]
            entry["verdict"] = "HIT"
        if not relative.endswith(".png"):
            entry.setdefault("verdict", "UNSUPPORTED" if relative.endswith((".jpg", ".jpeg")) else "CLEAR")
            continue
        try:
            width, height, rgba = decode_rgba(data)
        except (DecodeError, zlib.error, struct.error, IndexError) as error:
            entry.setdefault("verdict", "UNSUPPORTED")
            entry["reason"] = str(error)
            continue
        entry["size"] = [width, height]
        entry.setdefault("verdict", "CLEAR")
        candidates.append((relative, Image(width, height, rgba)))
    references = vanilla + [(LEGACY, name, image) for name, image in candidates]
    matcher = match_images(candidates, references)
    for name, (_, level, record) in matcher.best.items():
        if "hash_match" not in entries[name]:
            entries[name]["verdict"] = level
            entries[name]["best"] = dict(record)
    for name in entries:
        related = _related_entries(matcher, name)
        twins = [other for other in by_hash[entries[name]["sha256"]] if other != name]
        related = [entry for entry in related if entry["asset"] not in twins]
        related += [{"asset": other, "level": "HIT", "method": "hash"} for other in twins]
        if related:
            entries[name]["related"] = sorted(related, key=lambda entry: entry["asset"])
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


def peak_memory_mb() -> float | None:
    """The process's peak resident memory, for the CI log (Linux and Windows)."""
    try:
        import resource
        peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        return peak / 1024.0 if sys.platform != "darwin" else peak / (1024.0 * 1024.0)
    except ImportError:
        pass
    try:
        import ctypes
        from ctypes import wintypes

        class Counters(ctypes.Structure):
            _fields_ = [("cb", wintypes.DWORD), ("PageFaultCount", wintypes.DWORD),
                        ("PeakWorkingSetSize", ctypes.c_size_t), ("WorkingSetSize", ctypes.c_size_t),
                        ("QuotaPeakPagedPoolUsage", ctypes.c_size_t), ("QuotaPagedPoolUsage", ctypes.c_size_t),
                        ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t), ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
                        ("PagefileUsage", ctypes.c_size_t), ("PeakPagefileUsage", ctypes.c_size_t)]

        counters = Counters()
        counters.cb = ctypes.sizeof(Counters)
        current = ctypes.windll.kernel32.GetCurrentProcess
        current.restype = wintypes.HANDLE
        query = ctypes.windll.psapi.GetProcessMemoryInfo
        query.argtypes = [wintypes.HANDLE, ctypes.POINTER(Counters), wintypes.DWORD]
        if query(current(), ctypes.byref(counters), counters.cb):
            return counters.PeakWorkingSetSize / (1024.0 * 1024.0)
    except (ImportError, AttributeError, OSError):
        pass
    return None


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--upstream", required=True, type=Path)
    parser.add_argument("--vanilla", required=True, action="append",
                        help="LABEL=PATH of a vanilla client JAR, for example 1.12.2=client.jar")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--report-memory", action="store_true", help="print the peak memory to stderr")
    arguments = parser.parse_args(argv)
    data = render(analyse(arguments.upstream, arguments.vanilla, REPOSITORY_ROOT))
    if arguments.report_memory:
        peak = peak_memory_mb()
        print(f"vanilla derivation: peak memory {peak:.0f} MB" if peak else "vanilla derivation: peak memory unknown",
              file=sys.stderr)
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
