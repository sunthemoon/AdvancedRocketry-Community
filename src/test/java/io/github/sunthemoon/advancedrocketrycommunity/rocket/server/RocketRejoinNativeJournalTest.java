package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketRejoinNativeJournalTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void peerKeepsItsNativeFuelManifestAndEnvelopeAcrossCodec() {
        var t = GovernedTransferFixture.create().record();
        var legacy = RocketTransferRecord.create(t.transferId(), t.logicalRocketId(), t.ownerId(), t.sourceEntityId(),
                t.sourceSnapshot(), t.destinationSnapshot(), t.sourceFlightData(), t.destinationFlightData(), t.requiredFuel(), t.createdAtGameTime());
        var peer = RocketRejoinNativeJournal.governed(legacy);
        assertEquals(3, peer.schemaVersion()); assertEquals(legacy.sourceFlightData(), peer.sourceFlightData());
        assertEquals(legacy.destinationFlightData(), peer.destinationFlightData());
        var journal = new RocketTransferSavedData(); journal.put(peer);
        var restored = RocketTransferSavedData.load(journal.save(new net.minecraft.nbt.CompoundTag())).find(peer.transferId()).orElseThrow();
        assertEquals(peer.checksum(), restored.checksum());
        assertEquals(peer.nativeTransfer().orElseThrow().encode(), restored.nativeTransfer().orElseThrow().encode());
    }
}
