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

    private BoundedSavedDataIo() {
    }

    static CompoundTag read(Path path, ManagedSavedDataType type) {
        try {
            long compressedBytes = Files.size(path);
            if (compressedBytes <= 0L || compressedBytes > type.maxCompressedBytes()) {
                throw oversized(type, "compressed file size is " + compressedBytes + " bytes");
            }

            long expandedLimit = type.maxCompressedBytes();
            NbtAccounter accounter = new NbtAccounter(expandedLimit);
            try (InputStream file = Files.newInputStream(path, StandardOpenOption.READ);
                 GZIPInputStream gzip = new GZIPInputStream(file);
                 QuotaInputStream bounded = new QuotaInputStream(gzip, expandedLimit);
                 DataInputStream input = new DataInputStream(bounded)) {
                CompoundTag outer;
                try {
                    outer = NbtIo.read(input, accounter);
                } catch (RuntimeException exception) {
                    if (accounter.getUsage() > expandedLimit) {
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
    }

    private static final class QuotaExceededException extends IOException {
        private QuotaExceededException() {
            super("Expanded NBT byte quota exceeded");
        }
    }
}
