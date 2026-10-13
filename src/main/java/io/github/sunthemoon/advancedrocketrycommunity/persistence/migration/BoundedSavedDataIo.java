package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Bounded disk codec for Minecraft's outer SavedData wrapper. */
public final class BoundedSavedDataIo {
    private static final String DATA_KEY = "data";
    /**
     * A single NBT charge is made before the element is allocated: arrays charge 1, 4 or 8 bytes per
     * element (equal to their raw size) and lists 4 per element (each element needs at least one raw
     * byte). A declaration beyond this multiple of the remaining raw budget cannot be backed by data.
     */
    static final long DECLARED_BYTES_PER_REMAINING_RAW_BYTE = 4L;
    /** Room for the fixed per-tag charges (at most 48 bytes in 1.20.1). */
    static final long DECLARED_BYTES_SLACK = 64L;
    /** Deflate expands at most about 1032:1, so unread compressed bytes bound the data still to come. */
    static final long MAX_INFLATION_RATIO = 1_032L;
    /** Compressed bytes already buffered by the gzip reader, and output it may still hold. */
    static final long COMPRESSED_SLACK_BYTES = 1_024L;
    static final long INFLATED_SLACK_BYTES = 64L * 1_024L;
    /** Inflated read-ahead; well inside {@link #INFLATED_SLACK_BYTES}, the output the reader may still hold. */
    static final int INFLATED_BUFFER_BYTES = 8 * 1_024;
    private static final int WRAPPER_BYTES = wrapperBytes();

    private BoundedSavedDataIo() {
    }

    static CompoundTag read(Path path, ManagedSavedDataType type) {
        try {
            long compressedBytes = Files.size(path);
            if (compressedBytes <= 0L || compressedBytes > type.maxCompressedBytes()) {
                throw oversized(type, "compressed file size is " + compressedBytes + " bytes");
            }

            try (InputStream file = Files.newInputStream(path, StandardOpenOption.READ)) {
                return readCompressed(file, compressedBytes, type);
            }
        } catch (SavedDataMigrationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid(type, "cannot decode compressed SavedData", exception);
        }
    }

    /** Uses the exact disk-reader quotas before an in-memory transaction can publish its root. */
    public static void validatePayload(CompoundTag payload, ManagedSavedDataType type) {
        validatePayload(payload, type, Long.MAX_VALUE);
    }

    /**
     * {@link #validatePayload(CompoundTag, ManagedSavedDataType)} that also refuses a payload whose own encoding
     * exceeds {@code maxPayloadBytes}, measured during the same write. Writing and reading never change the
     * payload, so the wrapper holds it without a copy.
     */
    public static void validatePayload(CompoundTag payload, ManagedSavedDataType type, long maxPayloadBytes) {
        CompoundTag outer = new CompoundTag();
        outer.put(DATA_KEY, payload);
        byte[] encoded = writeForValidation(outer, type, maxPayloadBytes);
        try (InputStream input = new ByteArrayInputStream(encoded)) {
            CompoundTag decoded = readCompressed(input, encoded.length, type);
            if (!payload.equals(decoded.getCompound(DATA_KEY))) {
                throw invalid(type, "bounded read changed the payload");
            }
        } catch (IOException exception) {
            throw invalid(type, "cannot round-trip encoded payload", exception);
        }
    }

    private static CompoundTag readCompressed(InputStream file, long compressedBytes, ManagedSavedDataType type)
            throws IOException {
        long expandedLimit = type.maxCompressedBytes();
        long heapLimit = Math.multiplyExact(expandedLimit, type.heapAccountingFactor());
        // The buffer sits below the quota, which still counts exactly the bytes the NBT reader takes. Its
        // inflated read-ahead only lowers the compressed estimate below, which cannot admit more data.
        try (CountingInputStream compressed = new CountingInputStream(file);
             GZIPInputStream gzip = new GZIPInputStream(compressed);
             QuotaInputStream bounded = new QuotaInputStream(new BufferedInputStream(gzip, INFLATED_BUFFER_BYTES),
                     expandedLimit);
             DataInputStream input = new DataInputStream(bounded)) {
            BoundedAccounter accounter = new BoundedAccounter(heapLimit, () -> Math.min(bounded.remaining(),
                    Math.max(0L, compressedBytes - compressed.count() + COMPRESSED_SLACK_BYTES)
                            * MAX_INFLATION_RATIO + INFLATED_SLACK_BYTES));
            CompoundTag outer;
            try {
                outer = NbtIo.read(input, accounter);
            } catch (RuntimeException exception) {
                if (accounter.getUsage() > heapLimit || accounter.refusedDeclaration() || bounded.exceeded()) {
                    throw oversized(type, "expanded NBT exceeds the fixed byte limit", exception);
                }
                throw exception;
            }
            if (input.read() != -1) { throw invalid(type, "compressed NBT contains trailing expanded data"); }
            if (!outer.contains(DATA_KEY, Tag.TAG_COMPOUND)) { throw invalid(type, "outer wrapper is missing data"); }
            return outer;
        } catch (QuotaExceededException exception) {
            throw oversized(type, "expanded NBT exceeds the fixed byte limit", exception);
        }
    }

    static byte[] write(CompoundTag outer, ManagedSavedDataType type) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(outer, output);
            if (output.size() > type.maxCompressedBytes()) {
                throw oversized(type, "migrated compressed NBT exceeds the fixed byte limit");
            }
            return output.toByteArray();
        } catch (SavedDataMigrationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.STAGING_FAILED,
                    type.dataName() + " cannot encode migrated SavedData",
                    exception
            );
        }
    }

    /**
     * The validation copy of {@link #write}. It is never stored, so it deflates at the fastest level: the expanded
     * and heap quotas do not depend on the level, and a payload within its raw bound compresses below the
     * compressed bound at any level, because deflate's stored blocks add only a few bytes per 64 KiB.
     */
    private static byte[] writeForValidation(CompoundTag outer, ManagedSavedDataType type, long maxPayloadBytes) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int raw;
            try (DataOutputStream data = new DataOutputStream(new BufferedOutputStream(new FastGzipOutputStream(output)))) {
                NbtIo.write(outer, data);
                raw = data.size();
            }
            if (raw - WRAPPER_BYTES > maxPayloadBytes) {
                throw oversized(type, "payload encodes in " + (raw - WRAPPER_BYTES) + " bytes");
            }
            if (output.size() > type.maxCompressedBytes()) {
                throw oversized(type, "compressed NBT exceeds the fixed byte limit");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw invalid(type, "cannot encode payload for validation", exception);
        }
    }

    /** The bytes the outer wrapper adds to its payload's own named-root encoding. */
    private static int wrapperBytes() {
        CompoundTag outer = new CompoundTag();
        outer.put(DATA_KEY, new CompoundTag());
        return rawBytes(outer) - rawBytes(new CompoundTag());
    }

    private static int rawBytes(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(tag, output);
            }
            return bytes.size();
        } catch (IOException exception) {
            throw new IllegalStateException("In-memory NBT sizing failed", exception);
        }
    }

    static CompoundTag payload(CompoundTag outer, ManagedSavedDataType type) {
        if (!outer.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            throw invalid(type, "outer SavedData wrapper is missing data");
        }
        return outer.getCompound(DATA_KEY);
    }

    static CompoundTag withPayload(CompoundTag outer, CompoundTag payload) {
        CompoundTag result = outer.copy();
        result.put(DATA_KEY, payload.copy());
        return result;
    }

    private static SavedDataMigrationException invalid(
            ManagedSavedDataType type,
            String detail
    ) {
        return new SavedDataMigrationException(
                MigrationDiagnosticId.INVALID_SCHEMA,
                type.dataName() + " " + detail
        );
    }

    private static SavedDataMigrationException invalid(
            ManagedSavedDataType type,
            String detail,
            Throwable cause
    ) {
        return new SavedDataMigrationException(
                MigrationDiagnosticId.INVALID_SCHEMA,
                type.dataName() + " " + detail,
                cause
        );
    }

    private static SavedDataMigrationException oversized(
            ManagedSavedDataType type,
            String detail
    ) {
        return new SavedDataMigrationException(
                MigrationDiagnosticId.OVERSIZED_DATA,
                type.dataName() + " " + detail
        );
    }

    private static SavedDataMigrationException oversized(
            ManagedSavedDataType type,
            String detail,
            Throwable cause
    ) {
        return new SavedDataMigrationException(
                MigrationDiagnosticId.OVERSIZED_DATA,
                type.dataName() + " " + detail,
                cause
        );
    }

    private static final class QuotaInputStream extends InputStream {
        private final InputStream delegate;
        private final long limit;
        private long consumed;

        private QuotaInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                account(1L);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                account(count);
            }
            return count;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void account(long count) throws QuotaExceededException {
            consumed += count;
            if (consumed > limit) {
                throw new QuotaExceededException();
            }
        }

        /** NbtIo may wrap the quota IOException in a runtime exception; this survives the wrapping. */
        private boolean exceeded() {
            return consumed > limit;
        }

        private long remaining() {
            return Math.max(0L, limit - consumed);
        }
    }

    /** Counts compressed bytes taken from the file (including the gzip reader's read-ahead). */
    private static final class CountingInputStream extends java.io.FilterInputStream {
        private long count;

        private CountingInputStream(InputStream delegate) {
            super(delegate);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        private long count() {
            return count;
        }
    }

    /**
     * Refuses a single declared charge that the remaining raw bytes cannot back, before Minecraft
     * allocates the array or list for it; the cumulative heap quota still applies.
     */
    private static final class BoundedAccounter extends NbtAccounter {
        private final java.util.function.LongSupplier remainingRaw;
        private boolean refusedDeclaration;

        private BoundedAccounter(long quota, java.util.function.LongSupplier remainingRaw) {
            super(quota);
            this.remainingRaw = remainingRaw;
        }

        @Override
        public void accountBytes(long bytes) {
            long backed = remainingRaw.getAsLong() * DECLARED_BYTES_PER_REMAINING_RAW_BYTE + DECLARED_BYTES_SLACK;
            if (bytes > backed) {
                refusedDeclaration = true;
                throw new IllegalStateException("NBT element declares " + bytes
                        + " bytes but at most " + backed + " can follow within the byte quota");
            }
            super.accountBytes(bytes);
        }

        private boolean refusedDeclaration() {
            return refusedDeclaration;
        }
    }

    private static final class FastGzipOutputStream extends GZIPOutputStream {
        private FastGzipOutputStream(OutputStream output) throws IOException {
            super(output, 64 * 1_024);
            def.setLevel(Deflater.BEST_SPEED);
        }
    }

    private static final class QuotaExceededException extends IOException {
        private QuotaExceededException() {
            super("Expanded NBT byte quota exceeded");
        }
    }
}
