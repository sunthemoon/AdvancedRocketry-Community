package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class StationExpansionServiceTest {
    private final StationAccessService access = new StationAccessService();
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID invitee = UUID.randomUUID();
    private final StationState station = StationState.fromReservation(new StationReservation(
            UUID.randomUUID(), owner, "Policy", new StationGridCell(-3, 2), ModIdentity.id("earth"), 0
    )).withMember(member).invite(invitee);

    @Test
    void onlyOwnerAndOperatorMayExpand() {
        assertNull(check(station, owner, false));
        assertNull(check(station, UUID.randomUUID(), true));
        assertEquals(StationManagementCode.UNAUTHORIZED, check(station, member, false));
        assertEquals(StationManagementCode.UNAUTHORIZED, check(station, invitee, false));
        assertEquals(StationManagementCode.UNAUTHORIZED, check(station, UUID.randomUUID(), false));
    }

    @Test
    void onlyADirectlyCommandingConnectedPlayerIsAccepted() {
        for (boolean operator : new boolean[]{false, true}) {
            assertEquals(StationManagementCode.NOT_LOCAL_PLAYER, StationExpansionService.check(
                    access, true, false, true, true, Optional.of(station), owner, operator));
        }
    }

    @Test
    void authorityAndLocationAreCheckedBeforePermission() {
        Optional<StationState> here = Optional.of(station);
        for (UUID actor : new UUID[]{owner, member, UUID.randomUUID()}) {
            assertEquals(StationManagementCode.AUTHORITY_UNAVAILABLE,
                    StationExpansionService.check(access, false, true, true, true, here, actor, false));
            assertEquals(StationManagementCode.NOT_IN_SPACE,
                    StationExpansionService.check(access, true, true, false, true, here, actor, false));
            assertEquals(StationManagementCode.CHUNK_UNLOADED,
                    StationExpansionService.check(access, true, true, true, false, here, actor, false));
            assertEquals(StationManagementCode.NOT_IN_STATION,
                    StationExpansionService.check(access, true, true, true, true, Optional.empty(), actor, false));
        }
    }

    @Test
    void expandedStationIsIdempotentForManagersOnly() {
        StationState expanded = station.withExpandedRegion();
        assertEquals(StationManagementCode.ALREADY_EXPANDED, check(expanded, owner, false));
        assertEquals(StationManagementCode.ALREADY_EXPANDED, check(expanded, UUID.randomUUID(), true));
        assertEquals(StationManagementCode.UNAUTHORIZED, check(expanded, member, false));
    }

    private StationManagementCode check(StationState state, UUID actor, boolean operator) {
        return StationExpansionService.check(access, true, true, true, true, Optional.of(state), actor, operator);
    }
}
