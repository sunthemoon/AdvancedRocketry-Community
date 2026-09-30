package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Bounded disk codec for Minecraft's outer SavedData wrapper. */
final class BoundedSavedDataIo {
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

    private BoundedSavedDataIo() {
    }

    static CompoundTag read(Path path, ManagedSavedDataType type) {
        try {
            long compressedBytes = Files.size(path);
            if (compressedBytes <= 0L || compressedBytes > type.maxCompressedBytes()) {
                throw oversized(type, "compressed file size is " + compressedBytes + " bytes");
            }

            // The raw decompressed-byte quota below is the size bound. NbtAccounter estimates heap cost;
            // its quota is the raw bound times the type's measured factor (stations 8: about 5.7x at
            // capacity), and no single declaration may exceed what the remaining raw bytes can back.
            long expandedLimit = type.maxCompressedBytes();
            long heapLimit = Math.multiplyExact(expandedLimit, type.heapAccountingFactor());
            try (InputStream file = Files.newInputStream(path, StandardOpenOption.READ);
                 CountingInputStream compressed = new CountingInputStream(file);
                 GZIPInputStream gzip = new GZIPInputStream(compressed);
                 QuotaInputStream bounded = new QuotaInputStream(gzip, expandedLimit);
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
                if (input.read() != -1) {
                    throw invalid(type, "compressed NBT contains trailing expanded data");
                }
                if (!outer.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
                    throw invalid(type, "outer SavedData wrapper is missing data");
                }
                return outer;
            } catch (QuotaExceededException exception) {
                throw oversized(type, "expanded NBT exceeds the fixed byte limit", exception);
            }
        } catch (SavedDataMigrationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid(type, "cannot decode compressed SavedData", exception);
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

    private static final class QuotaExceededException extends IOException {
        private QuotaExceededException() {
            super("Expanded NBT byte quota exceeded");
        }
    }
}
