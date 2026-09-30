package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-046 UI-03 caps: 32 members and 32 invitations; inviting the owner, a member or an invitee changes nothing. */
final class StationTeamCapsTest {
    private static final UUID OWNER = new UUID(46, 0);

    @Test
    void membersAndInvitationsStopAtThirtyTwo() {
        StationState station = station();
        for (int index = 0; index < StationLimits.MAX_MEMBERS; index++) {
            station = station.withMember(new UUID(46, 100 + index));
        }
        assertEquals(StationLimits.MAX_MEMBERS, station.members().size());
        StationState fullMembers = station;
        assertEquals("Station member list is full", assertThrows(IllegalStateException.class,
                () -> fullMembers.withMember(new UUID(46, 999))).getMessage());
        for (int index = 0; index < StationLimits.MAX_INVITATIONS; index++) {
            station = station.invite(new UUID(46, 200 + index));
        }
        assertEquals(StationLimits.MAX_INVITATIONS, station.invitations().size());
        StationState fullInvitations = station;
        assertEquals("Station invitation list is full", assertThrows(IllegalStateException.class,
                () -> fullInvitations.invite(new UUID(46, 998))).getMessage());
    }

    @Test
    void invitingTheOwnerAMemberOrAnInviteeChangesNothing() {
        UUID member = new UUID(46, 1);
        UUID invitee = new UUID(46, 2);
        StationState station = station().withMember(member).invite(invitee);
        assertSame(station, station.invite(OWNER), "Inviting the owner (the actor themselves) changed the station");
        assertSame(station, station.invite(member), "Inviting a member changed the station");
        assertSame(station, station.invite(invitee), "Inviting an invitee again changed the station");
    }

    private static StationState station() {
        StationRegistryModel registry = new StationRegistryModel();
        UUID id = new UUID(46, 50);
        registry.reserve(id, OWNER, "Caps", ModIdentity.id("earth"), 0);
        return registry.commit(id);
    }
}
