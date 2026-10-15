import struct
import zlib


def png_chunk(chunk_type: bytes, content: bytes) -> bytes:
    return (
        struct.pack(">I", len(content))
        + chunk_type
        + content
        + struct.pack(">I", zlib.crc32(chunk_type + content) & 0xFFFFFFFF)
    )


def make_png(
    width: int = 640,
    height: int = 360,
    *,
    metadata: tuple[bytes, bytes] | None = None,
    before_idat: tuple[tuple[bytes, bytes], ...] = (),
    after_idat: tuple[tuple[bytes, bytes], ...] = (),
    seed: int = 0,
) -> bytes:
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    pixel = bytes(((0x10 + seed) % 256, (0x20 + seed) % 256, (0x30 + seed) % 256))
    scanline = b"\0" + (pixel * width)
    chunks = [png_chunk(b"IHDR", header)]
    if metadata is not None:
        chunks.append(png_chunk(*metadata))
    chunks.extend(png_chunk(*item) for item in before_idat)
    chunks.append(png_chunk(b"IDAT", zlib.compress(scanline * height)))
    chunks.extend(png_chunk(*item) for item in after_idat)
    chunks.append(png_chunk(b"IEND", b""))
    return b"\x89PNG\r\n\x1a\n" + b"".join(chunks)
