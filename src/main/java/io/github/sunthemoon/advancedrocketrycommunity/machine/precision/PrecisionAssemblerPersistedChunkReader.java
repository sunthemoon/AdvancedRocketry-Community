package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

/** Read-only, bounded check of an already-flushed Anvil chunk on disk. */
final class PrecisionAssemblerPersistedChunkReader {
    static final int MAX_COMPRESSED_CHUNK_BYTES = 1_048_576;
    static final int MAX_DECODED_CHUNK_BYTES = 4_194_304;
    private static final int SECTOR_BYTES = 4_096;
    private static final int HEADER_BYTES = 8_192;

    private PrecisionAssemblerPersistedChunkReader() {
    }

    static Optional<CompoundTag> readBlockEntity(Path regionDirectory, BlockPos position) throws IOException {
        ChunkPos chunk = new ChunkPos(position);
        Optional<CompoundTag> diskChunk = readChunk(regionDirectory, chunk);
        return diskChunk.flatMap(root -> findBlockEntity(root, position));
    }

    static Optional<CompoundTag> findBlockEntity(CompoundTag root, BlockPos position) {
        ChunkPos chunk = new ChunkPos(position);
        if (!root.contains("xPos", Tag.TAG_INT) || root.getInt("xPos") != chunk.x
                || !root.contains("zPos", Tag.TAG_INT) || root.getInt("zPos") != chunk.z
                || !root.contains("block_entities", Tag.TAG_LIST)) {
            return Optional.empty();
        }
        var entities = root.getList("block_entities", Tag.TAG_COMPOUND);
        CompoundTag found = null;
        for (int index = 0; index < entities.size(); index++) {
            CompoundTag entity = entities.getCompound(index);
            if (entity.contains("x", Tag.TAG_INT) && entity.contains("y", Tag.TAG_INT)
                    && entity.contains("z", Tag.TAG_INT)
                    && entity.getInt("x") == position.getX() && entity.getInt("y") == position.getY()
                    && entity.getInt("z") == position.getZ()) {
                if (found != null) {
                    return Optional.empty();
                }
                found = entity;
            }
        }
        return Optional.ofNullable(found);
    }

    static Optional<CompoundTag> readChunk(Path regionDirectory, ChunkPos chunk) throws IOException {
        if (!Files.isDirectory(regionDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        Path path = regionDirectory.resolve("r." + Math.floorDiv(chunk.x, 32)
                + "." + Math.floorDiv(chunk.z, 32) + ".mca");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = file.size();
            if (size < HEADER_BYTES) {
                return Optional.empty();
            }
            int headerOffset = ((chunk.x & 31) + ((chunk.z & 31) << 5)) * 4;
            ByteBuffer location = ByteBuffer.allocate(4);
            readFully(file, location, headerOffset);
            location.flip();
            int sector = ((location.get() & 255) << 16) | ((location.get() & 255) << 8)
                    | (location.get() & 255);
            int sectors = location.get() & 255;
            long start = (long) sector * SECTOR_BYTES;
            if (sector < 2 || sectors == 0 || start + 5 > size) {
                return Optional.empty();
            }
            ByteBuffer recordHeader = ByteBuffer.allocate(5);
            readFully(file, recordHeader, start);
            recordHeader.flip();
            int length = recordHeader.getInt();
            int compression = recordHeader.get() & 255;
            // RegionFile pads its final sector on close, not on flush. Require
            // the complete record, but do not require unused sector padding.
            if (length < 2 || length - 1 > MAX_COMPRESSED_CHUNK_BYTES
                    || length + 4 > sectors * SECTOR_BYTES || start + 4L + length > size
                    || (compression & 128) != 0) {
                return Optional.empty();
            }
            ByteBuffer encoded = ByteBuffer.allocate(length - 1);
            readFully(file, encoded, start + 5);
            return decode(encoded.array(), compression);
        }
    }

    private static Optional<CompoundTag> decode(byte[] encoded, int compression) throws IOException {
        InputStream input = switch (compression) {
            case 1 -> new GZIPInputStream(new ByteArrayInputStream(encoded));
            case 2 -> new InflaterInputStream(new ByteArrayInputStream(encoded));
            case 3 -> new ByteArrayInputStream(encoded);
            default -> null;
        };
        if (input == null) {
            return Optional.empty();
        }
        try (input; ByteArrayOutputStream expanded = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > MAX_DECODED_CHUNK_BYTES - expanded.size()) {
                    return Optional.empty();
                }
                expanded.write(buffer, 0, read);
            }
            try (DataInputStream data = new DataInputStream(
                    new ByteArrayInputStream(expanded.toByteArray()))) {
                CompoundTag root = NbtIo.read(data, new NbtAccounter(MAX_DECODED_CHUNK_BYTES));
                return data.available() == 0 ? Optional.of(root) : Optional.empty();
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }
    }

    private static void readFully(FileChannel file, ByteBuffer target, long position) throws IOException {
        while (target.hasRemaining()) {
            int read = file.read(target, position);
            if (read <= 0) {
                throw new IOException("Precision migration region record ended early");
            }
            position += read;
        }
    }
}
