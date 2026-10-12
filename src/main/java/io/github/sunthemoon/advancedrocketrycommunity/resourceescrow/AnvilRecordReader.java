package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;

/** Stable inline Anvil read, never an IOWorker pending-read result. No file or chunk is created. */
public final class AnvilRecordReader {
    public static final int COMPRESSED_BYTES = 1_048_576;
    public static final int DECODED_BYTES = 4_194_304;
    public record SizeProbe(boolean external, int compressedBytes, int decodedBytes, boolean supported) {
        /** Conservative headroom for subsequent marker/resource growth. */
        public boolean admissionRoom() {
            return supported && !external && compressedBytes <= COMPRESSED_BYTES * 3 / 4
                    && decodedBytes <= DECODED_BYTES * 3 / 4;
        }
    }
    public record Record(CompoundTag root, SizeProbe size) {
        public Record { root = root.copy(); }
        @Override public CompoundTag root() { return root.copy(); }
    }
    private record Header(int location, int timestamp, int length, int compression, long start) { }
    private AnvilRecordReader() { }

    public static SizeProbe probe(Path directory, ChunkPos pos) throws IOException {
        return read(directory, pos, true).size();
    }
    public static Record read(Path directory, ChunkPos pos) throws IOException { return read(directory, pos, false); }
    static Record read(Path directory, ChunkPos pos, Runnable afterRead) throws IOException {
        return read(directory, pos, false, afterRead);
    }
    private static Record read(Path directory, ChunkPos pos, boolean probe) throws IOException {
        return read(directory, pos, probe, () -> { });
    }
    private static Record read(Path directory, ChunkPos pos, boolean probe, Runnable afterRead) throws IOException {
        Path path = directory.resolve("r." + Math.floorDiv(pos.x, 32) + "." + Math.floorDiv(pos.z, 32) + ".mca");
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) { throw new IOException("Missing Anvil directory"); }
        BasicFileAttributes before = attributes(path);
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            Header header = header(file, pos);
            if ((header.compression() & 128) != 0) {
                if (!header.equals(header(file, pos)) || !same(before, attributes(path))) { throw new IOException("Unstable external Anvil header"); }
                if (!probe) { throw new IOException("External Anvil records cannot prove an escrow endpoint"); }
                // A size-only probe may inspect the fixed vanilla filename, but it can never issue proof.
                SizeProbe size = externalProbe(directory, pos, header.compression() & 127);
                if (!header.equals(header(file, pos)) || !same(before, attributes(path))) { throw new IOException("Unstable external Anvil header"); }
                return new Record(new CompoundTag(), size);
            }
            int compressed = header.length() - 1;
            if (compressed > COMPRESSED_BYTES) {
                if (!probe) { throw new IOException("Compressed Anvil record limit exceeded"); }
                return new Record(new CompoundTag(), new SizeProbe(false, compressed, -1, false));
            }
            byte[] bytes = bytes(file, header.start() + 5, compressed);
            byte[] decoded = expand(bytes, header.compression(), probe);
            afterRead.run();
            if (!header.equals(header(file, pos)) || !Arrays.equals(bytes, bytes(file, header.start() + 5, compressed))
                    || !same(before, attributes(path))) { throw new IOException("Anvil record changed during readback"); }
            if (decoded == null) { return new Record(new CompoundTag(), new SizeProbe(false, compressed, DECODED_BYTES + 1, false)); }
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(decoded))) {
                // Preallocation charges have an independent heap quota. Raw expanded bytes remain the exact bound.
                NbtAccounter accounter = new NbtAccounter(DECODED_BYTES * 8L) {
                    @Override public void accountBytes(long charge) {
                        try {
                            if (charge > input.available() * 4L + 64L) {
                                throw new IllegalArgumentException("Anvil declaration cannot fit the remaining raw bytes");
                            }
                        } catch (IOException failure) { throw new IllegalArgumentException(failure); }
                        super.accountBytes(charge);
                    }
                };
                CompoundTag root = NbtIo.read(input, accounter);
                if (input.read() != -1) { throw new IOException("Trailing Anvil NBT"); }
                EscrowTags.bound(root, DECODED_BYTES, 64, 262_144);
                return new Record(root, new SizeProbe(false, compressed, decoded.length, true));
            } catch (RuntimeException invalid) { throw new IOException("Invalid bounded Anvil NBT", invalid); }
        }
    }
    private static Header header(FileChannel file, ChunkPos pos) throws IOException {
        if (file.size() < 8_192) { throw new IOException("Short Anvil header"); }
        int slot = (pos.x & 31) + ((pos.z & 31) << 5);
        int location = ByteBuffer.wrap(bytes(file, slot * 4L, 4)).getInt();
        int timestamp = ByteBuffer.wrap(bytes(file, 4_096L + slot * 4L, 4)).getInt();
        int sector = location >>> 8, sectors = location & 255; long start = sector * 4_096L;
        if (sector < 2 || sectors == 0 || start + 5 > file.size()) { throw new IOException("Missing or invalid Anvil slot"); }
        ByteBuffer record = ByteBuffer.wrap(bytes(file, start, 5)); int length = record.getInt(); int compression = record.get() & 255;
        if (length < 1 || length + 4L > sectors * 4_096L || start + length + 4L > file.size()) { throw new IOException("Invalid Anvil record length"); }
        return new Header(location, timestamp, length, compression, start);
    }
    private static byte[] expand(byte[] encoded, int compression, boolean probe) throws IOException {
        InputStream source = switch (compression) {
            case 1 -> new GZIPInputStream(new ByteArrayInputStream(encoded));
            case 2 -> new InflaterInputStream(new ByteArrayInputStream(encoded));
            case 3 -> new ByteArrayInputStream(encoded);
            default -> throw new IOException("Unsupported Anvil compression");
        };
        try (source; var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192]; int count;
            while ((count = source.read(buffer)) != -1) {
                if (count > DECODED_BYTES - output.size()) {
                    if (probe) { return null; } throw new IOException("Decoded Anvil record limit exceeded");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
    private static SizeProbe externalProbe(Path directory, ChunkPos pos, int compression) throws IOException {
        Path external = directory.resolve("c." + pos.x + "." + pos.z + ".mcc");
        if (!Files.exists(external, LinkOption.NOFOLLOW_LINKS)) { return new SizeProbe(true, -1, -1, false); }
        BasicFileAttributes before = attributes(external);
        int compressed = (int) Math.min(Integer.MAX_VALUE, before.size());
        if (before.size() > COMPRESSED_BYTES) { return new SizeProbe(true, compressed, -1, false); }
        try (FileChannel file = FileChannel.open(external, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] encoded = bytes(file, 0, compressed); byte[] decoded = expand(encoded, compression, true);
            if (!same(before, attributes(external)) || !Arrays.equals(encoded, bytes(file, 0, compressed))) {
                throw new IOException("External record changed during size probe");
            }
            return new SizeProbe(true, compressed, decoded == null ? DECODED_BYTES + 1 : decoded.length, false);
        }
    }
    private static byte[] bytes(FileChannel file, long offset, int length) throws IOException {
        ByteBuffer target = ByteBuffer.allocate(length);
        while (target.hasRemaining()) { int read = file.read(target, offset); if (read <= 0) { throw new IOException("Short Anvil record"); } offset += read; }
        return target.array();
    }
    private static BasicFileAttributes attributes(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) { throw new IOException("Anvil authority must be a regular file"); } return attributes;
    }
    private static boolean same(BasicFileAttributes first, BasicFileAttributes second) {
        return Objects.equals(first.fileKey(), second.fileKey()) && first.size() == second.size()
                && first.lastModifiedTime().equals(second.lastModifiedTime());
    }
}
