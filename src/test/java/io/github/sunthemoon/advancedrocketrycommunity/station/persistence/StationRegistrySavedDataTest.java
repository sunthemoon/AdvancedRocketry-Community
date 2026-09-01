package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

final class StationRegistrySavedDataTest {
    @Test
    void stateMembersAndOccupiedCellsRoundTripAcrossRestart() {
        StationRegistrySavedData data = new StationRegistrySavedData();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stationId = UUID.randomUUID();
        StationReservation reservation = data.reserve(
                stationId, owner, "Persistent", ModIdentity.id("earth"), 42
        );
        StationState state = data.commit(stationId);
        data.invite(stationId, member);
        data.acceptInvitation(stationId, member);

        StationRegistrySavedData restored = StationRegistrySavedData.load(data.save(new CompoundTag()));
        assertTrue(restored.operational());
        StationState decoded = restored.find(stationId).orElseThrow();
        assertEquals(state.cell(), decoded.cell());
        assertTrue(decoded.members().contains(member));
        assertTrue(decoded.invitations().isEmpty());

        UUID nextId = UUID.randomUUID();
        StationReservation next = restored.reserve(
                nextId, UUID.randomUUID(), "Next", ModIdentity.id("moon"), 43
        );
        assertFalse(next.cell().equals(reservation.cell()));
    }

    @Test
    void unfinishedReservationRoundTripsForRestartRecovery() {
        StationRegistrySavedData data = new StationRegistrySavedData();
        UUID stationId = UUID.randomUUID();
        StationReservation reservation = data.reserve(
                stationId, UUID.randomUUID(), "Reserved", ModIdentity.id("earth"), 1
        );
        StationRegistrySavedData restored = StationRegistrySavedData.load(data.save(new CompoundTag()));
        assertEquals(reservation, restored.reservations().get(0));
        assertTrue(restored.release(stationId));
        assertTrue(restored.reservations().isEmpty());
    }

    @Test
    void futureRegistrySchemaIsPreservedAndBlocked() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 99);
        future.putString("future_payload", "keep me");
        StationRegistrySavedData data = StationRegistrySavedData.load(future);
        assertFalse(data.operational());
        assertEquals(future, data.save(new CompoundTag()));
        assertThrows(IllegalStateException.class, () -> data.reserve(
                UUID.randomUUID(), UUID.randomUUID(), "Blocked", ModIdentity.id("earth"), 0
        ));
    }

    @Test
    void malformedGeometryBlocksWholeRegistryInsteadOfPartiallyLoading() {
        StationRegistrySavedData data = new StationRegistrySavedData();
        UUID stationId = UUID.randomUUID();
        data.reserve(stationId, UUID.randomUUID(), "Malformed", ModIdentity.id("earth"), 0);
        data.commit(stationId);
        CompoundTag encoded = data.save(new CompoundTag());
        encoded.getList("stations", Tag.TAG_COMPOUND)
                .getCompound(0)
                .putIntArray("region", new int[]{0, 0, 1, 1});

        StationRegistrySavedData blocked = StationRegistrySavedData.load(encoded);
        assertFalse(blocked.operational());
        assertTrue(blocked.stations().isEmpty());
        assertEquals(encoded, blocked.save(new CompoundTag()));
    }
}
