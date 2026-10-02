import struct
import unittest
import zlib

from tools.audit.vanilla_derivation import Image, analyse_images, decode_png


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


def _png(width: int, height: int, pixels) -> bytes:
    raw = b"".join(b"\x00" + bytes(c for pixel in pixels[y * width:(y + 1) * width] for c in pixel)
                   for y in range(height))

    def chunk(kind: bytes, body: bytes) -> bytes:
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


class VanillaDerivationCalibrationTests(unittest.TestCase):
    """Positive cases must all be found (false-negative rate 0); unrelated art stays CLEAR."""

    @classmethod
    def setUpClass(cls) -> None:
        texture = Image(16, 16, _noise(16, 16, seed=7))
        icon = Image(16, 16, _icon())
        sheet_pixels = _noise(64, 64, seed=11)
        sheet = Image(64, 64, sheet_pixels)
        frame = Image(16, 16, _noise(16, 16, seed=19))
        pattern = Image(32, 32, _pattern(32, 32))
        cls.vanilla = [("v", "texture.png", texture), ("v", "icon.png", icon), ("v", "sheet.png", sheet),
                       ("v", "frame.png", frame), ("v", "pattern.png", pattern)]
        crop = [sheet_pixels[(20 + y) * 64 + 30 + x] for y in range(12) for x in range(12)]
        upscaled = [texture.pixels[(y // 2) * 16 + x // 2] for y in range(32) for x in range(32)]
        strip = frame.pixels + _noise(16, 48, seed=23)
        cls.positives = {
            "exact copy": Image(16, 16, list(texture.pixels)),
            "recolour": Image(16, 16, _recolour(texture.pixels)),
            "low-palette icon copy": Image(16, 16, list(icon.pixels)),
            "crop of a sheet": Image(12, 12, crop),
            "mirrored copy": texture.transformed(4),
            "rotated copy": texture.transformed(1),
            "x2 upscale": Image(32, 32, upscaled),
            "first frame of a strip": Image(16, 64, strip),
            "recoloured icon": Image(16, 16, _recolour(icon.pixels)),
        }
        flat = [(0, 0, 0, 255) if index % 16 < 9 else pixel for index, pixel in enumerate(_noise(16, 16, seed=107))]
        cls.negatives = {
            "shares one flat colour with a pattern": Image(16, 16, flat),
            "unrelated texture": Image(16, 16, _noise(16, 16, seed=101)),
            "unrelated small art": Image(12, 12, _noise(12, 12, seed=103, colours=6)),
        }
        cls.shapes = {
            "two-colour shape inside a pattern": Image(16, 16, _pattern(16, 16)),
        }
        candidates = list(cls.positives.items()) + list(cls.negatives.items()) + list(cls.shapes.items())
        cls.results = {name: level for name, (_, level, _) in analyse_images(candidates, cls.vanilla).items()}

    def test_every_derivation_is_found(self) -> None:
        missed = [name for name in self.positives if self.results.get(name) != "HIT"]
        false_negative_rate = len(missed) / len(self.positives)
        self.assertEqual([], missed, f"false-negative rate {false_negative_rate:.2f}")

    def test_unrelated_art_stays_clear(self) -> None:
        for name in self.negatives:
            self.assertEqual("CLEAR", self.results.get(name, "CLEAR"), name)

    def test_two_colour_shape_matches_need_review_not_exclusion(self) -> None:
        for name in self.shapes:
            self.assertEqual("SUSPECT", self.results.get(name), name)

    def test_png_decoder_reads_rgba(self) -> None:
        pixels = _noise(5, 3, seed=3)
        width, height, decoded = decode_png(_png(5, 3, pixels))
        self.assertEqual((5, 3), (width, height))
        self.assertEqual(pixels, decoded)


if __name__ == "__main__":
    unittest.main()
