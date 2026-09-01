package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class StationAccessServiceTest {
    private final StationAccessService access = new StationAccessService();

    @Test
    void ownerMemberOutsiderAndOperatorUseOnePolicy() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        StationState station = StationState.fromReservation(new StationReservation(
                UUID.randomUUID(), owner, "Access", new StationGridCell(0, 0),
                ModIdentity.id("earth"), 0
        )).withMember(member);

        for (StationAccessAction action : StationAccessAction.values()) {
            assertTrue(access.allowed(station, owner, false, action));
            assertTrue(access.allowed(station, outsider, true, action));
            assertFalse(access.allowed(station, outsider, false, action));
        }
        assertTrue(access.allowed(station, member, false, StationAccessAction.VISIT));
        assertTrue(access.allowed(station, member, false, StationAccessAction.BUILD));
        assertFalse(access.allowed(station, member, false, StationAccessAction.MANAGE_MEMBERS));
        assertFalse(access.allowed(station, member, false, StationAccessAction.DELETE));
    }

    @Test
    void removalTakesEffectAgainstNextCheck() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        StationState station = StationState.fromReservation(new StationReservation(
                UUID.randomUUID(), owner, "Removal", new StationGridCell(0, 0),
                ModIdentity.id("earth"), 0
        )).withMember(member);
        assertTrue(access.allowed(station, member, false, StationAccessAction.BUILD));

        StationState removed = station.withoutMember(member);
        assertFalse(access.allowed(removed, member, false, StationAccessAction.BUILD));
    }
}

