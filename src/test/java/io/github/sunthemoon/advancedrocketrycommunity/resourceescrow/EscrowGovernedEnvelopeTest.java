package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedEnvelopeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void uncrewedDigestIsUnchangedAndNormalizationDoesNotMutateItsInput() {
        var source = GovernedTransferFixture.create().source();
        var flight = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec
                .decode(source.getCompound("flight")).data().orElseThrow();
        source.put("flight", io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec.encode(
                flight.withPassengers(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest.empty(1))));
        source.put("passengers", new ListTag());
        var before = source.copy();
        assertEquals(EscrowNativeEnvelope.digest(source), EscrowGovernedEnvelope.digest(source));
        assertEquals(before, source);
        var crewed = GovernedTransferFixture.create(1, 3, false).source();
        var normalized = EscrowGovernedEnvelope.comparison(crewed);
        assertEquals(3, crewed.getList("passengers", Tag.TAG_COMPOUND).size());
        assertTrue(normalized.getList("passengers", Tag.TAG_COMPOUND).isEmpty());
        assertEquals(crewed.getCompound("flight"), normalized.getCompound("flight"));
    }

    @Test void foreignAndDuplicateRidersAreRefusedRatherThanNormalizedAway() {
        var source = GovernedTransferFixture.create(1, 2, false).source();
        var duplicate = source.copy(); var riders = duplicate.getList("passengers", Tag.TAG_COMPOUND);
        riders.add(riders.get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedEnvelope.digest(duplicate));
        var foreign = source.copy(); var entry = new CompoundTag();
        entry.putUUID("physical", GovernedTransferFixture.id(999));
        var outsiders = new ListTag(); outsiders.add(entry); foreign.put("passengers", outsiders);
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedEnvelope.comparison(foreign));
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedEnvelope.same(source, foreign));
    }

    @Test void allOtherFieldsIncludingManifestRemainComparedExactly() {
        var source = GovernedTransferFixture.create(1, 2, false).source();
        var hash = EscrowGovernedEnvelope.digest(source);
        for (var key : source.getAllKeys()) {
            if (key.equals("passengers")) { continue; }
            var changed = source.copy(); changed.remove(key);
            try {
                assertNotEquals(hash, EscrowGovernedEnvelope.digest(changed), key);
                assertFalse(EscrowGovernedEnvelope.same(source, changed), key);
            } catch (IllegalArgumentException invalid) {
                // Required native fields may instead reject the malformed envelope.
            }
        }
    }
}
