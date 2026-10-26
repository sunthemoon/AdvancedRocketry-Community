package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.zip.GZIPInputStream;

/** Streaming installed-file proof. No Minecraft NBT tree, unknown payload allocation, or player-file write. */
final class RocketRejoinPlayerFile {
    enum Status { MATCH, MISMATCH, IO, CHANGED, OVERSIZED, DEPTH, TIMEOUT }
    record Result(Status status, long size, long ceiling) { }
    record Expected(UUID player, UUID release, UUID generation, String dimension, double x, double y, double z) { }
    record Limits(long compressed, long inflated, int depth, long nanos) {
        static final Limits DEFAULT = new Limits(64L << 20, 512L << 20, 512, 10_000_000_000L);
    }
    private RocketRejoinPlayerFile() { }
    static Result scan(Path directory, Expected expected) { return scan(directory, expected, Limits.DEFAULT, System::nanoTime); }
    static Result scan(Path directory, Expected expected, Limits limits, LongSupplier clock) {
        long started = clock.getAsLong();
        try {
            directory = directory.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) { return result(Status.IO); }
            Path file = directory.resolve(expected.player() + ".dat");
            var before = attributes(file);
            if (!before.isRegularFile() || before.isSymbolicLink()) { return result(Status.IO); }
            if (before.size() > limits.compressed()) { return new Result(Status.OVERSIZED, before.size(), limits.compressed()); }
            boolean matches;
            // Default NIO options retain Windows read/write/delete sharing. Do not replace with FileInputStream.
            try (var raw = new Budget(Files.newInputStream(file), limits.compressed(), clock, started, limits.nanos());
                 var gzip = new GZIPInputStream(raw);
                 var inflated = new Budget(gzip, limits.inflated(), clock, started, limits.nanos())) {
                matches = new Scanner(inflated, limits.depth()).read(expected);
                if (inflated.read() != -1) { return result(Status.MISMATCH); }
            }
            var after = attributes(file);
            if (!after.isRegularFile() || after.isSymbolicLink() || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())) { return result(Status.CHANGED); }
            if (clock.getAsLong() - started >= limits.nanos()) { return result(Status.TIMEOUT); }
            return result(matches ? Status.MATCH : Status.MISMATCH);
        } catch (Refusal refused) { return new Result(refused.status, refused.size, refused.ceiling); }
        catch (IOException | SecurityException malformed) { return result(Status.IO); }
    }
    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    private static Result result(Status status) { return new Result(status, 0, 0); }
    private static final class Refusal extends IOException {
        final Status status; final long size, ceiling;
        Refusal(Status status, long size, long ceiling) { this.status = status; this.size = size; this.ceiling = ceiling; }
    }
    private static final class Budget extends FilterInputStream {
        final long ceiling, started, nanos; final LongSupplier clock; long used;
        Budget(InputStream input, long ceiling, LongSupplier clock, long started, long nanos) {
            super(input); this.ceiling = ceiling; this.clock = clock; this.started = started; this.nanos = nanos;
        }
        void check(long requested) throws Refusal {
            if (Thread.currentThread().isInterrupted() || clock.getAsLong() - started >= nanos) { throw new Refusal(Status.TIMEOUT, 0, nanos); }
            if (requested < 0 || requested > ceiling - used) { throw new Refusal(Status.OVERSIZED, used + requested, ceiling); }
        }
        @Override public int read() throws IOException {
            check(1); int value = in.read(); if (value >= 0) { used++; } return value;
        }
        @Override public int read(byte[] bytes, int offset, int count) throws IOException {
            if (count == 0) { return 0; } check(1);
            int n = in.read(bytes, offset, (int) Math.min(count, ceiling - used)); if (n > 0) { used += n; } return n;
        }
    }
    private static final class Scanner {
        private static final Set<String> NEEDED = Set.of("UUID", "Dimension", "Pos", "Motion", "FallDistance", "Health", "DeathTime", "RootVehicle", "ForgeData");
        final Budget bytes; final DataInputStream in; final int maximumDepth; final byte[] discard = new byte[8192];
        Scanner(Budget bytes, int depth) { this.bytes = bytes; in = new DataInputStream(bytes); maximumDepth = depth; }
        boolean read(Expected expected) throws IOException {
            type(in.readUnsignedByte(), 10); skipString();
            var seen = new HashSet<String>(); int present = 0; boolean match = true;
            for (int tag; (tag = in.readUnsignedByte()) != 0;) {
                String key = key();
                if (key == null || !NEEDED.contains(key)) { skip(tag, 1); continue; }
                if (!seen.add(key)) { throw new Refusal(Status.MISMATCH, 0, 0); }
                switch (key) {
                    case "UUID" -> { type(tag, 11); match &= uuid().equals(expected.player()); present |= 1; }
                    case "Dimension" -> { type(tag, 8); match &= expected.dimension().equals(text(128)); present |= 2; }
                    case "Pos" -> { type(tag, 9); match &= vector(expected.x(), expected.y(), expected.z()); present |= 4; }
                    case "Motion" -> { type(tag, 9); match &= vector(0, 0, 0); present |= 8; }
                    case "FallDistance" -> { type(tag, 5); match &= in.readFloat() == 0; present |= 16; }
                    case "Health" -> { type(tag, 5); float health = in.readFloat(); match &= Float.isFinite(health) && health > 0; present |= 32; }
                    case "DeathTime" -> { type(tag, 2); match &= in.readShort() == 0; present |= 64; }
                    case "RootVehicle" -> { match = false; skip(tag, 1); }
                    case "ForgeData" -> { type(tag, 10); match &= forge(expected); present |= 128; }
                    default -> throw new IOException("Unreachable needed key");
                }
            }
            return match && present == 255;
        }
        boolean forge(Expected expected) throws IOException {
            depth(1); boolean seen = false, match = false;
            for (int tag; (tag = in.readUnsignedByte()) != 0;) {
                long start = bytes.used - 1; String key = key();
                if (!RocketRejoinMarker.KEY.equals(key)) { skip(tag, 2); continue; }
                if (seen) { throw new Refusal(Status.MISMATCH, 0, 0); }
                seen = true; type(tag, 10); match = marker(expected);
                if (bytes.used - start > RocketRejoinMarker.MAX_BYTES) { throw new Refusal(Status.MISMATCH, 0, 0); }
            }
            return seen && match;
        }
        boolean marker(Expected expected) throws IOException {
            depth(2); var seen = new HashSet<String>(); boolean match = true;
            for (int tag; (tag = in.readUnsignedByte()) != 0;) {
                String key = key();
                if (key == null || !seen.add(key)) { throw new Refusal(Status.MISMATCH, 0, 0); }
                switch (key) {
                    case "schema" -> { type(tag, 3); match &= in.readInt() == 1; }
                    case "release" -> { type(tag, 11); match &= uuid().equals(expected.release()); }
                    case "player" -> { type(tag, 11); match &= uuid().equals(expected.player()); }
                    case "generation" -> { type(tag, 11); match &= uuid().equals(expected.generation()); }
                    default -> throw new Refusal(Status.MISMATCH, 0, 0);
                }
            }
            return match && seen.size() == 4;
        }
        UUID uuid() throws IOException {
            if (in.readInt() != 4) { throw new Refusal(Status.MISMATCH, 0, 0); }
            return new UUID((long) in.readInt() << 32 | Integer.toUnsignedLong(in.readInt()),
                    (long) in.readInt() << 32 | Integer.toUnsignedLong(in.readInt()));
        }
        boolean vector(double x, double y, double z) throws IOException {
            type(in.readUnsignedByte(), 6);
            if (in.readInt() != 3) { throw new Refusal(Status.MISMATCH, 0, 0); }
            double a = in.readDouble(), b = in.readDouble(), c = in.readDouble();
            return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c) && a == x && b == y && c == z;
        }
        String key() throws IOException { return text(64); }
        String text(int maximum) throws IOException {
            int n = in.readUnsignedShort(); if (n > maximum) { discard(n); return null; }
            // Needed names and canonical dimension IDs are ASCII; unknown UTF payloads need no decoding.
            byte[] value = new byte[n]; in.readFully(value); return new String(value, StandardCharsets.US_ASCII);
        }
        void skipString() throws IOException { discard(in.readUnsignedShort()); }
        void discard(long n) throws IOException {
            bytes.check(n);
            while (n > 0) { int count = (int) Math.min(n, discard.length); in.readFully(discard, 0, count); n -= count; }
        }
        void depth(int depth) throws Refusal {
            if (depth > maximumDepth) { throw new Refusal(Status.DEPTH, depth, maximumDepth); }
        }
        void skip(int tag, int depth) throws IOException {
            depth(depth);
            int fixed = fixed(tag);
            if (fixed > 0) { discard(fixed); return; }
            switch (tag) {
                case 7, 11, 12 -> { int length = length(); discard((long) length * (tag == 7 ? 1 : tag == 11 ? 4 : 8)); }
                case 8 -> skipString();
                case 9 -> {
                    int element = in.readUnsignedByte(), length = length();
                    if (element == 0 && length == 0) { return; }
                    if (element < 1 || element > 12) { throw new IOException("Invalid list type"); }
                    int size = fixed(element);
                    if (size > 0) { discard((long) length * size); }
                    else {
                        bytes.check(length); // Every variable element consumes at least one byte.
                        for (int i = 0; i < length; i++) { skip(element, depth + 1); }
                    }
                }
                case 10 -> { for (int child; (child = in.readUnsignedByte()) != 0;) { skipString(); skip(child, depth + 1); } }
                default -> throw new IOException("Invalid tag type");
            }
        }
        int length() throws IOException { int length = in.readInt(); if (length < 0) { throw new IOException("Negative NBT length"); } return length; }
        static int fixed(int type) { return switch (type) { case 1 -> 1; case 2 -> 2; case 3, 5 -> 4; case 4, 6 -> 8; default -> 0; }; }
        static void type(int actual, int expected) throws Refusal { if (actual != expected) { throw new Refusal(Status.MISMATCH, 0, 0); } }
    }
}
