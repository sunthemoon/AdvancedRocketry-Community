package io.github.sunthemoon.advancedrocketrycommunity.endgame.authority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority.Decision;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority.Request;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority.StationContext;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** ADR-054 section 3 and the ADR-058 narrowing, over every actor, action and station case. */
class EndgameAuthorityTest {
    private static final UUID DEVICE_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID STATION_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @ParameterizedTest
    @EnumSource(EndgameAction.class)
    void operatorsMayDoEverythingOutsideAndInsideStations(EndgameAction action) {
        assertFull(decide(STRANGER, true, StationContext.outside(), action, false));
        assertFull(decide(STRANGER, true, station(Set.of()), action, false));
        assertFull(decide(STRANGER, true, station(Set.of()), action, true));
    }

    @ParameterizedTest
    @EnumSource(EndgameAction.class)
    void outsideStationsTheOwnerMayDoEverythingAndOthersSeeOnlyPublicStatus(EndgameAction action) {
        assertFull(decide(DEVICE_OWNER, false, StationContext.outside(), action, false));
        Decision stranger = decide(STRANGER, false, StationContext.outside(), action, false);
        if (action == EndgameAction.VIEW) {
            assertTrue(stranger.allowed());
            assertFalse(stranger.detail(), "anyone else sees public status only");
        } else {
            assertRefused(stranger, EndgameCode.UNAUTHORIZED);
        }
    }

    @ParameterizedTest
    @EnumSource(EndgameAction.class)
    void insideAStationTheOwnerNeedsBuildAccess(EndgameAction action) {
        assertFull(decide(DEVICE_OWNER, false, station(Set.of(DEVICE_OWNER)), action, false));
        Decision lostAccess = decide(DEVICE_OWNER, false, station(Set.of()), action, false);
        if (action == EndgameAction.VIEW) {
            assertFull(lostAccess);
        } else {
            assertRefused(lostAccess, EndgameCode.UNAUTHORIZED);
        }
    }

    @ParameterizedTest
    @EnumSource(EndgameAction.class)
    void insideAStationTheStationOwnerMayDoEverythingAndMembersOnlyView(EndgameAction action) {
        assertFull(decide(STATION_OWNER, false, station(Set.of(MEMBER)), action, false));
        Decision member = decide(MEMBER, false, station(Set.of(MEMBER)), action, false);
        if (action == EndgameAction.VIEW) {
            assertTrue(member.allowed());
            assertFalse(member.detail());
        } else {
            assertRefused(member, EndgameCode.UNAUTHORIZED);
        }
        assertRefused(decide(STRANGER, false, station(Set.of(MEMBER)), action, false), EndgameCode.UNAUTHORIZED);
    }

    @ParameterizedTest
    @EnumSource(EndgameAction.class)
    void anUnavailableStationRefusesEverythingButTheOwnersWithdrawal(EndgameAction action) {
        for (UUID actor : new UUID[] {DEVICE_OWNER, STRANGER}) {
            for (boolean operator : new boolean[] {false, true}) {
                Decision decision = decide(actor, operator, StationContext.unavailable(), action, false);
                boolean mayWithdraw = action == EndgameAction.WITHDRAW && (operator || actor.equals(DEVICE_OWNER));
                if (mayWithdraw) {
                    assertFull(decision);
                } else {
                    assertRefused(decision, EndgameCode.STATION_UNAVAILABLE);
                }
            }
        }
    }

    @Test
    void stationManagedSystemsNeedTheStationOwnerToConfigureOrOperateInsideStations() {
        StationContext withOwnerMember = station(Set.of(DEVICE_OWNER));
        for (EndgameAction action : EndgameAction.values()) {
            Decision owner = decide(DEVICE_OWNER, false, withOwnerMember, action, true);
            boolean changes = action == EndgameAction.CONFIGURE || action == EndgameAction.OPERATE;
            if (changes) {
                assertRefused(owner, EndgameCode.UNAUTHORIZED);
            } else {
                assertFull(owner);
            }
            assertFull(decide(STATION_OWNER, false, withOwnerMember, action, true));
            // Outside stations the narrowing does not apply.
            assertFull(decide(DEVICE_OWNER, false, StationContext.outside(), action, true));
        }
    }

    @Test
    void anUnownedDeviceGrantsNoOneButOperatorsAndStationOwnersMoreThanTheTable() {
        Decision outside = EndgameAuthority.decide(new Request(STRANGER, false, Optional.empty(),
                StationContext.outside(), EndgameAction.OPERATE, false));
        assertRefused(outside, EndgameCode.UNAUTHORIZED);
        Decision stationOwner = EndgameAuthority.decide(new Request(STATION_OWNER, false, Optional.empty(),
                station(Set.of()), EndgameAction.CONFIGURE, false));
        assertFull(stationOwner);
    }

    @Test
    void contextsAndDecisionsRejectContradictions() {
        assertThrows(IllegalArgumentException.class, () -> new StationContext(EndgameAuthority.StationKind.OUTSIDE_STATIONS,
                Optional.of(STATION_OWNER), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new StationContext(EndgameAuthority.StationKind.COMMITTED,
                Optional.empty(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new StationContext(EndgameAuthority.StationKind.UNAVAILABLE,
                Optional.empty(), Set.of(MEMBER)));
        assertThrows(IllegalArgumentException.class, () -> new Decision(true, false, EndgameCode.UNAUTHORIZED));
        assertThrows(IllegalArgumentException.class, () -> new Decision(false, true, EndgameCode.UNAUTHORIZED));
    }

    private static StationContext station(Set<UUID> members) {
        return StationContext.committed(STATION_OWNER, members);
    }

    private static Decision decide(UUID actor, boolean operator, StationContext station, EndgameAction action,
                                   boolean stationManaged) {
        return EndgameAuthority.decide(new Request(actor, operator, Optional.of(DEVICE_OWNER), station, action,
                stationManaged));
    }

    private static void assertFull(Decision decision) {
        assertTrue(decision.allowed(), decision.toString());
        assertTrue(decision.detail(), decision.toString());
        assertEquals(EndgameCode.OK, decision.refusal());
    }

    private static void assertRefused(Decision decision, EndgameCode code) {
        assertFalse(decision.allowed(), decision.toString());
        assertEquals(code, decision.refusal());
    }
}
