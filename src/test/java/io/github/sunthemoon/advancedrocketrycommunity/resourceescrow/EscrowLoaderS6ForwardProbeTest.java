package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6ForwardProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void checkedPreparedWitnessHashesEveryRecordFieldWithoutChangingThePayload() throws Exception {
        var journal = new RocketTransferSavedData(); journal.put(GovernedTransferFixture.create().record());
        var payload = journal.save(new CompoundTag()); var before = payload.copy();
        var row = payload.getList("transfers", 10).getCompound(0);
        String hash = EscrowNativeEnvelope.digest(row);
        assertEquals(hash, EscrowLoaderS6JournalProbe.transferDigest(payload));
        assertEquals(before, payload);
        for (String key : row.getAllKeys()) {
            var changed = payload.copy(); changed.getList("transfers", 10).getCompound(0).remove(key);
            if (key.equals("schema_version") || key.equals("phase") || key.equals("native_transfer")) {
                assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6JournalProbe.transferDigest(changed));
            } else { assertNotEquals(hash, EscrowLoaderS6JournalProbe.transferDigest(changed), key); }
        }
        var empty = payload.copy(); empty.getList("transfers", 10).clear();
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6JournalProbe.transferDigest(empty));
        var extra = payload.copy(); extra.getList("transfers", 10).add(row.copy());
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6JournalProbe.transferDigest(extra));
        // Retained canonical codec sample for the Python type-framing regression; never a native-run claim.
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(row, new java.io.DataOutputStream(bytes));
        System.out.println("S6_FORWARD_CODEC_FIXTURE=" + hash + ":" + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }
}
