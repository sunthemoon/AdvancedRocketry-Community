import struct
import unittest
import zlib

from tools.audit.vanilla_derivation import LEGACY, Image, analyse_images, decode_png, match_images, verdict


def _noise(width: int, height: int, seed: int, colours: int = 24) -> list[tuple[int, int, int, int]]:
    """Deterministic textured image with ``colours`` distinct greys and tints."""
    state = seed
    palette = []
    for index in range(colours):
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        palette.append((state & 0xFF, (state >> 8) & 0xFF, (state >> 16) & 0xFF, 255))
    pixels = []
    for _ in range(width * height):
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        pixels.append(palette[state % colours])
    return pixels


def _icon() -> list[tuple[int, int, int, int]]:
    """A 16 x 16 low-palette ingot-like icon: three colours on transparency."""
    clear, edge, body, shine = (0, 0, 0, 0), (60, 60, 60, 255), (170, 170, 170, 255), (240, 240, 240, 255)
    pixels = []
    for y in range(16):
        for x in range(16):
            if 4 <= y <= 11 and 2 + (11 - y) // 3 <= x <= 13 - (y - 4) // 3:
                pixels.append(edge if y in (4, 11) or x in (2 + (11 - y) // 3, 13 - (y - 4) // 3)
                              else shine if y == 5 else body)
            else:
                pixels.append(clear)
    return pixels


def _pattern(width: int, height: int) -> list[tuple[int, int, int, int]]:
    """A two-colour shape: black with grey bars, like a banner pattern mask."""
    black, grey = (0, 0, 0, 255), (191, 191, 191, 255)
    return [grey if y % 8 in (3, 4) and 2 <= x % 16 <= 13 else black for y in range(height) for x in range(width)]


def _recolour(pixels):
    """A monotone luminance transform with a tint, as a recolour would do."""
    out = []
    for r, g, b, a in pixels:
        lum = 0.299 * r + 0.587 * g + 0.114 * b
        out.append((int(lum * 0.4) + 30, int(lum * 0.35) + 20, int(lum * 0.3) + 10, a))
    return out


def _crop(pixels, width, x0, y0, w, h):
    return [pixels[(y0 + y) * width + x0 + x] for y in range(h) for x in range(w)]


def _jitter(pixels, amount: int, seed: int):
    """Add up to ``amount`` of deterministic noise per channel, as a filter would."""
    state, out = seed, []
    for r, g, b, a in pixels:
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        delta = state % (2 * amount + 1) - amount
        out.append((max(0, min(255, r + delta)), max(0, min(255, g + delta)), max(0, min(255, b + delta)), a))
    return out


def _blur(pixels, width: int, height: int):
    """A 3 x 3 box blur."""
    out = []
    for y in range(height):
        for x in range(width):
            near = [pixels[yy * width + xx] for yy in (y - 1, y, y + 1) for xx in (x - 1, x, x + 1)
                    if 0 <= xx < width and 0 <= yy < height]
            out.append(tuple(sum(p[c] for p in near) // len(near) for c in range(3)) + (255,))
    return out


def _overlay(pixels, width: int, size: int):
    """Paint a square in the middle, as a machine face drawn on a casing."""
    out, start = list(pixels), (width - size) // 2
    for y in range(start, start + size):
        for x in range(start, start + size):
            out[y * width + x] = (200, 30, 30, 255)
    return out


def _png(width: int, height: int, pixels) -> bytes:
    raw = b"".join(b"\x00" + bytes(c for pixel in pixels[y * width:(y + 1) * width] for c in pixel)
                   for y in range(height))

    def chunk(kind: bytes, body: bytes) -> bytes:
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


class VanillaDerivationCalibrationTests(unittest.TestCase):
    """Every derivation kind must be found (false-negative rate 0 per kind); unrelated art stays CLEAR."""

    @classmethod
    def setUpClass(cls) -> None:
        texture = Image(16, 16, _noise(16, 16, seed=7))
        icon = Image(16, 16, _icon())
        sheet_pixels = _noise(64, 64, seed=11)
        large_pixels = _noise(128, 128, seed=13)
        frame = Image(16, 16, _noise(16, 16, seed=19))
        pattern = Image(32, 32, _pattern(32, 32))
        cls.vanilla = [("v", "texture.png", texture), ("v", "icon.png", icon),
                       ("v", "sheet.png", Image(64, 64, sheet_pixels)), ("v", "large.png", Image(128, 128, large_pixels)),
                       ("v", "frame.png", frame), ("v", "pattern.png", pattern)]
        crop = _crop(sheet_pixels, 64, 30, 20, 12, 12)
        base = _crop(large_pixels, 128, 40, 30, 24, 24)
        upscaled = [texture.pixels[(y // 2) * 16 + x // 2] for y in range(32) for x in range(32)]
        one_new = list(base)
        one_new[5 * 24 + 5] = (1, 2, 3, 255)
        painted = list(base)
        for i, (x, y) in enumerate([(x, y) for y in range(10, 14) for x in range(10, 14)]):
            painted[y * 24 + x] = ((i * 40) % 250 + 3, 7, 9, 255)
        embedded = _noise(64, 64, seed=999, colours=10)
        for y in range(16):
            for x in range(16):
                embedded[(y + 20) * 64 + x + 24] = texture.pixels[y * 16 + x]
        gui = [(198, 198, 198, 255)] * (256 * 256)
        for y in range(16):
            for x in range(16):
                gui[(y + 100) * 256 + x + 60] = texture.pixels[y * 16 + x]
        partial = [p if (i % 5) < 2 else (i % 7 + 1, 0, 200, 255) for i, p in enumerate(texture.pixels)]
        # Kind of derivation -> case -> (image, expected verdict).
        cls.cases = {
            "whole image": {
                "exact copy": (Image(16, 16, list(texture.pixels)), "HIT"),
                "recolour": (Image(16, 16, _recolour(texture.pixels)), "HIT"),
                "low-palette icon copy": (Image(16, 16, list(icon.pixels)), "HIT"),
                "recoloured icon": (Image(16, 16, _recolour(icon.pixels)), "HIT"),
                "40 % of a texture copied": (Image(16, 16, partial), "HIT"),
            },
            "orientation": {
                "mirrored copy": (texture.transformed(4), "HIT"),
                "rotated copy": (texture.transformed(1), "HIT"),
                "texture shifted by 1 px": (Image(16, 16, [texture.pixels[y * 16 + (x + 1) % 16]
                                                           for y in range(16) for x in range(16)]), "HIT"),
            },
            "scale and frames": {
                "x2 upscale": (Image(32, 32, upscaled), "HIT"),
                "first frame of a strip": (Image(16, 64, frame.pixels + _noise(16, 48, seed=23)), "HIT"),
                "crop upscaled x2": (Image(48, 48, [base[(y // 2) * 24 + x // 2] for y in range(48) for x in range(48)]),
                                     "HIT"),
            },
            "crop": {
                "crop of a sheet": (Image(12, 12, crop), "HIT"),
                "70 x 70 crop": (Image(70, 70, _crop(large_pixels, 128, 10, 10, 70, 70)), "HIT"),
            },
            "edited crop": {
                "crop with one new-colour pixel": (Image(24, 24, one_new), "HIT"),
                "crop with an icon painted on it": (Image(24, 24, painted), "HIT"),
                "crop with every channel +1": (Image(24, 24, [(min(r + 1, 255), min(g + 1, 255), min(b + 1, 255), a)
                                                             for r, g, b, a in base]), "SUSPECT"),
                "recoloured crop": (Image(24, 24, [(int((0.299 * r + 0.587 * g + 0.114 * b) * 0.5) + 20, 30, 40, a)
                                                   for r, g, b, a in base]), "SUSPECT"),
            },
            "vanilla inside a legacy sheet": {
                "texture inside a 64 x 64 sheet": (Image(64, 64, embedded), "HIT"),
                "texture inside a 256 x 256 sheet": (Image(256, 256, gui), "HIT"),
            },
        }
        cls.negatives = {
            "shares one flat colour with a pattern": Image(16, 16, [(0, 0, 0, 255) if index % 16 < 9 else pixel
                                                                    for index, pixel in enumerate(_noise(16, 16, 107))]),
            "unrelated texture": Image(16, 16, _noise(16, 16, seed=101)),
            "unrelated small art": Image(12, 12, _noise(12, 12, seed=103, colours=6)),
            "unrelated sheet": Image(64, 64, _noise(64, 64, seed=109)),
        }
        cls.shapes = {"two-colour shape inside a pattern": Image(16, 16, _pattern(16, 16))}
        candidates = [(name, image) for kind in cls.cases.values() for name, (image, _) in kind.items()]
        candidates += list(cls.negatives.items()) + list(cls.shapes.items())
        best = analyse_images(candidates, cls.vanilla)
        cls.results = {name: entry[1] for name, entry in best.items()}
        cls.records = {name: entry[2] for name, entry in best.items()}

    def test_every_derivation_kind_is_found(self) -> None:
        rates = {}
        for kind, cases in self.cases.items():
            missed = [name for name, (_, expected) in cases.items() if self.results.get(name, "CLEAR") != expected]
            rates[kind] = (len(missed) / len(cases), missed)
        self.assertEqual({kind: (0.0, []) for kind in self.cases}, rates,
                         "false-negative rate per kind of derivation (rate, missed cases)")

    def test_unrelated_art_stays_clear(self) -> None:
        for name in self.negatives:
            self.assertEqual("CLEAR", self.results.get(name, "CLEAR"), name)

    def test_two_colour_shape_matches_need_review_not_exclusion(self) -> None:
        for name in self.shapes:
            self.assertEqual("SUSPECT", self.results.get(name), name)

    def test_recorded_measures_reproduce_the_verdict(self) -> None:
        for name, record in self.records.items():
            self.assertEqual(self.results[name], verdict(record), name)

    def test_legacy_derivatives_are_related(self) -> None:
        """ADR-061 §4.8 inheritance: legacy files that share pixels with each other are listed as related."""
        base = _noise(16, 16, seed=211)
        overlay = [(0, 0, 0, 0) if (x + y) % 3 else base[y * 16 + x] for y in range(16) for x in range(16)]
        variant = list(base)
        for x in range(16):
            variant[8 * 16 + x] = (250, 10, 10, 255)
        legacy = [("helmet.png", Image(16, 16, base)), ("helmet_overlay.png", Image(16, 16, overlay)),
                  ("helmet_variant.png", Image(16, 16, variant)), ("unrelated.png", Image(16, 16, _noise(16, 16, 223)))]
        matcher = match_images(legacy, [(LEGACY, name, image) for name, image in legacy])
        related = {name: sorted(table) for name, table in matcher.related.items()}
        self.assertIn("helmet.png", related.get("helmet_overlay.png", []))
        self.assertIn("helmet.png", related.get("helmet_variant.png", []))
        self.assertNotIn("unrelated.png", related.get("helmet.png", []))
        self.assertNotIn("unrelated.png", related)

    def test_relations_are_complete_when_a_tile_is_widely_shared(self) -> None:
        """A tile shared by many legacy files neither crowds out another relation nor is cut at a fixed count."""
        tile = _noise(16, 16, seed=301)
        other = _noise(16, 16, seed=307)
        legacy = []
        for index in range(12):
            copy = list(tile)
            copy[index] = (index * 9 + 1, 2, 3, 255)
            legacy.append((f"machine_{index:02d}.png", Image(16, 16, copy)))
        sheet = _noise(128, 64, seed=311, colours=8)
        for y in range(16):
            for x in range(16):
                for left in (0, 32, 64):
                    sheet[(y + 8) * 128 + left + x] = tile[y * 16 + x]
                sheet[(y + 37) * 128 + 101 + x] = other[y * 16 + x]
        legacy += [("tile.png", Image(16, 16, tile)), ("other.png", Image(16, 16, other)),
                   ("sheet.png", Image(128, 64, sheet))]
        matcher = match_images(legacy, [(LEGACY, name, image) for name, image in legacy])
        self.assertIn("other.png", matcher.related.get("sheet.png", {}))
        self.assertIn("tile.png", matcher.related.get("sheet.png", {}))
        self.assertGreater(len(matcher.related.get("tile.png", {})), 8)

    def test_vanilla_texture_off_the_block_grid_inside_a_sheet(self) -> None:
        texture = Image(16, 16, _noise(16, 16, seed=401))
        sheet = _noise(96, 96, seed=409, colours=10)
        for y in range(16):
            for x in range(16):
                sheet[(y + 33) * 96 + x + 50] = texture.pixels[y * 16 + x]
        best = analyse_images([("sheet", Image(96, 96, sheet))], [("v", "texture.png", texture)])
        self.assertEqual("HIT", best["sheet"][1])

    def test_known_limits_stay_visible(self) -> None:
        """Filtered derivatives that ADR-061 section 4.8 names as known limits: expected CLEAR.

        A change in what the tool finds makes this test fail, so the limits list and the record review
        (ADR-061 section 4.5) are revisited whenever the tool changes."""
        texture = _noise(16, 16, seed=501, colours=12)
        sheet = _noise(128, 128, seed=503, colours=16)
        vanilla = [("v", "texture.png", Image(16, 16, texture)), ("v", "sheet.png", Image(128, 128, sheet))]
        crop = _crop(sheet, 128, 20, 30, 12, 12)
        embedded = _noise(128, 128, seed=507, colours=10)
        noisy = _jitter(_crop(sheet, 128, 40, 40, 16, 16), 6, 11)
        for y in range(16):
            for x in range(16):
                embedded[(y + 50) * 128 + x + 60] = noisy[y * 16 + x]
        limits = {
            "noise of 6 plus an 8 x 8 overlay": Image(16, 16, _overlay(_jitter(texture, 6, 7), 16, 8)),
            "3 x 3 blur plus a 6 x 6 overlay": Image(16, 16, _overlay(_blur(texture, 16, 16), 16, 6)),
            "12 x 12 crop with noise of 6": Image(12, 12, _jitter(crop, 6, 9)),
            "blurred crop": Image(12, 12, _blur(crop, 12, 12)),
            "noisy crop inside a 128 px sheet": Image(128, 128, embedded),
        }
        best = analyse_images(list(limits.items()), vanilla)
        self.assertEqual({name: "CLEAR" for name in limits},
                         {name: best.get(name, (None, "CLEAR"))[1] for name in limits})

    def test_png_decoder_reads_rgba(self) -> None:
        pixels = _noise(5, 3, seed=3)
        width, height, decoded = decode_png(_png(5, 3, pixels))
        self.assertEqual((5, 3), (width, height))
        self.assertEqual(pixels, decoded)

    def test_png_decoder_clears_transparent_colour(self) -> None:
        pixels = [(10, 20, 30, 0), (40, 50, 60, 255)]
        self.assertEqual([(0, 0, 0, 0), (40, 50, 60, 255)], decode_png(_png(2, 1, pixels))[2])


if __name__ == "__main__":
    unittest.main()
