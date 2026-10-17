package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
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

    @Test void oneChangedAssignmentWhileTheRidersStayValidComparesUnequal() {
        var source = GovernedTransferFixture.create(1, 3, false).source();
        var riders = source.getList("passengers", Tag.TAG_COMPOUND);
        var aboard = new ListTag(); aboard.add(riders.get(0).copy()); aboard.add(riders.get(1).copy());
        source.put("passengers", aboard);
        var flight = RocketFlightNbtCodec.decode(source.getCompound("flight")).data().orElseThrow();
        // Replace the one assignment that is not riding; both riders aboard stay assigned in the changed manifest.
        var vacant = riders.getCompound(2).getUUID("physical");
        var reassigned = flight.passengers().remove(vacant).assign(GovernedTransferFixture.id(999)).orElseThrow();
        assertNotEquals(flight.passengers(), reassigned);
        var changed = source.copy(); changed.put("flight", RocketFlightNbtCodec.encode(flight.withPassengers(reassigned)));
        var normalized = assertDoesNotThrow(() -> EscrowGovernedEnvelope.comparison(changed), "The riders must stay valid");
        assertTrue(normalized.getList("passengers", Tag.TAG_COMPOUND).isEmpty());
        assertNotEquals(EscrowGovernedEnvelope.digest(source), EscrowGovernedEnvelope.digest(changed));
        assertFalse(EscrowGovernedEnvelope.same(source, changed));
        assertFalse(EscrowGovernedEnvelope.same(changed, source));
        // Control: a different rider subset and order alone still compares equal.
        var reordered = source.copy(); var swapped = new ListTag();
        swapped.add(riders.get(2).copy()); swapped.add(aboard.get(0).copy()); reordered.put("passengers", swapped);
        assertTrue(EscrowGovernedEnvelope.same(source, reordered));
        assertEquals(EscrowGovernedEnvelope.digest(source), EscrowGovernedEnvelope.digest(reordered));
    }

    @Test void probesCompareACrewedArrivalWithTheFixedDestinationUnderR18() {
        var fixture = GovernedTransferFixture.create(1, 3, false);
        var record = fixture.record();
        var fixed = record.nativeTransfer().orElseThrow().destinationEnvelope(record);
        assertTrue(fixed.getList("passengers", Tag.TAG_COMPOUND).isEmpty());
        assertTrue(EscrowDestinationProbe.fixedDestination(record, fixed.copy()), "The uncrewed comparison is unchanged");
        var riders = fixture.source().getList("passengers", Tag.TAG_COMPOUND);
        var crewed = fixed.copy(); var aboard = new ListTag(); aboard.add(riders.get(2).copy()); aboard.add(riders.get(0).copy());
        crewed.put("passengers", aboard);
        assertNotEquals(fixed, crewed, "The raw envelope comparison would mismatch with a rider aboard");
        assertTrue(EscrowDestinationProbe.fixedDestination(record, crewed));
        var duplicate = crewed.copy(); duplicate.getList("passengers", Tag.TAG_COMPOUND).add(riders.get(0).copy());
        var foreign = crewed.copy(); var outsider = new CompoundTag(); outsider.putUUID("physical", GovernedTransferFixture.id(999));
        foreign.getList("passengers", Tag.TAG_COMPOUND).add(outsider);
        var changed = crewed.copy(); changed.putBoolean("invulnerable", !crewed.getBoolean("invulnerable"));
        for (var mismatch : java.util.List.of(duplicate, foreign, changed)) {
            assertFalse(EscrowDestinationProbe.fixedDestination(record, mismatch));
        }
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
