package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RocketJournalRootSizingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void insertionDoesNotSerializeTheCheckedRootAgain(boolean canonicalResidents) {
        var candidate = RocketTransferSavedDataTest.record();
        var encoded = new ObservedTag();
        encoded.merge(RocketTransferJournalCodec.encodeRecord(candidate));
        RocketJournalInsertionBudget.validate(List.of(), candidate, FirstEventOutbox.empty(), canonicalResidents,
                ignored -> encoded);
        assertEquals(2, encoded.writes, "One record check and one root check; no repeated root sizing");
    }

    @Test void returnedCountIsForThisExactTargetAndIsRecomputedAfterMutation() throws IOException {
        var records = List.of(RocketTransferSavedDataTest.record());
        var target = new CompoundTag(); target.putString("extra\u0000key", "\ud83d\ude80");
        int first = RocketTransferJournalCodec.encodeRootBytes(records, FirstEventOutbox.empty(), target,
                RocketTransferJournalCodec::encodeRecord);
        assertEquals(legacySize(target), first);
        target.putString("extra\u0000key", "\ud83d\ude80\u0000");
        int second = RocketTransferJournalCodec.encodeRootBytes(records, FirstEventOutbox.empty(), target,
                RocketTransferJournalCodec::encodeRecord);
        assertEquals(first + 2, second);
        assertEquals(legacySize(target), second);
        var other = new CompoundTag();
        int third = RocketTransferJournalCodec.encodeRootBytes(records, FirstEventOutbox.empty(), other,
                RocketTransferJournalCodec::encodeRecord);
        assertEquals(legacySize(other), third);
        assertTrue(third < first);
    }

    @Test void recordAtTheLimitIsAcceptedAndOneByteOverStillRefuses() throws IOException {
        var record = RocketTransferSavedDataTest.record();
        var encoded = padded(RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES);
        var root = RocketTransferJournalCodec.encodeRoot(List.of(record), FirstEventOutbox.empty(),
                new CompoundTag(), ignored -> encoded);
        assertEquals(legacySize(root), RocketNbtSize.uncompressedBytes(root));
        assertEquals(RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES, legacySize(encoded));
        encoded.putByteArray("padding", new byte[encoded.getByteArray("padding").length + 1]);
        assertEquals(RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES + 1, legacySize(encoded));
        assertEquals(legacySize(encoded), RocketNbtSize.uncompressedBytes(encoded));
        assertEquals("Encoded transfer record exceeds the fixed NBT limit", assertThrows(IllegalStateException.class,
                () -> RocketTransferJournalCodec.encodeRoot(List.of(record), FirstEventOutbox.empty(),
                        new CompoundTag(), ignored -> encoded)).getMessage());
    }

    @Test void exactJournalLimitAndOneByteOverKeepAllFramingAndRefusals() throws IOException {
        var records = Collections.nCopies(RocketFlightLimits.MAX_ACTIVE_TRANSFERS, RocketTransferSavedDataTest.record());
        // Repeated immutable test payload references avoid allocating 64 large arrays; the real writer visits each.
        var encoded = padded(RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES);
        var root = new CompoundTag(); root.putByteArray("padding", new byte[0]);
        RocketTransferJournalCodec.encodeRoot(records, FirstEventOutbox.empty(), root, ignored -> encoded);
        int remaining = RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES - legacySize(root);
        assertTrue(remaining > 0);
        root.putByteArray("padding", new byte[remaining]);
        assertSame(root, RocketTransferJournalCodec.encodeRoot(records, FirstEventOutbox.empty(), root, ignored -> encoded));
        assertEquals(RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES, legacySize(root));
        assertEquals(RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES,
                RocketTransferJournalCodec.encodeRootBytes(records, FirstEventOutbox.empty(), root, ignored -> encoded));
        assertEquals(RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES, RocketNbtSize.uncompressedBytes(root));
        root.putByteArray("padding", new byte[remaining + 1]);
        assertEquals(RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES + 1, legacySize(root));
        assertEquals(RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES + 1, RocketNbtSize.uncompressedBytes(root));
        assertEquals("Encoded transfer journal exceeds the fixed NBT limit", assertThrows(IllegalStateException.class,
                () -> RocketTransferJournalCodec.encodeRoot(records, FirstEventOutbox.empty(), root,
                        ignored -> encoded)).getMessage());
        assertEquals("Encoded transfer journal exceeds the fixed NBT limit", assertThrows(IllegalStateException.class,
                () -> RocketTransferJournalCodec.encodeRootBytes(records, FirstEventOutbox.empty(), root,
                        ignored -> encoded)).getMessage());
    }

    private static CompoundTag padded(int size) throws IOException {
        var tag = new CompoundTag();
        tag.putString("\u0000key\ud83d\ude80", "\u0000\ud83d\ude80\u0800");
        tag.putByteArray("padding", new byte[0]);
        tag.putByteArray("padding", new byte[size - legacySize(tag)]);
        return tag;
    }

    private static int legacySize(CompoundTag root) throws IOException {
        // Preallocation changes no bytes and avoids growth-copy peaks at the real 147 MB journal ceiling.
        int capacity = root.contains("transfers") && root.contains("padding")
                ? RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES + 1 : 32;
        var bytes = new ByteArrayOutputStream(capacity);
        try (var output = new DataOutputStream(bytes)) { NbtIo.write(root, output); }
        return bytes.size();
    }

    private static final class ObservedTag extends CompoundTag {
        private int writes;
        @Override public void write(DataOutput output) throws IOException { writes++; super.write(output); }
    }
}
