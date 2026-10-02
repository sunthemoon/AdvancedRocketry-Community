package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.model.ElevatorPair;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.model.ElevatorPairCodec;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.model.ElevatorPairTable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRootCodec;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-059 A0: the bind and ride orders, validity at use, access, costs, the station guard and the pairs section. */
final class ElevatorTest {
    private static final ResourceLocation MOON = ResourceLocation.tryBuild("advancedrocketrycommunity", "moon");
    private static final ResourceLocation MOON_LEVEL = ResourceLocation.tryBuild("advancedrocketrycommunity", "moon");
    private static final ResourceLocation ANCHOR = ResourceLocation.tryBuild("advancedrocketrycommunity",
            "elevator_anchor");
    private static final ResourceLocation TERMINAL = ResourceLocation.tryBuild("advancedrocketrycommunity",
            "elevator_terminal");
    private static final ResourceLocation SPACE = ResourceLocation.tryBuild("advancedrocketrycommunity", "space");
    private static final UUID OWNER = new UUID(1L, 1L);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static ElevatorPair pair(UUID station, UUID terminal, UUID anchor, int x, int z) {
        return new ElevatorPair(UUID.randomUUID(), station, terminal, anchor, MOON, MOON_LEVEL, x, z, 64, 10L, OWNER);
    }

    @Test
    void aBindReportsTheFirstFailureInItsOrder() {
        ElevatorRules.BindFacts ready = new ElevatorRules.BindFacts(ElevatorEndpointCode.VALID, true, true, true, false,
                EndgameCode.OK, false, EndgameCode.OK);
        assertEquals(ElevatorRules.Check.OK, ElevatorRules.bind(ready));
        List<UnaryOperator<ElevatorRules.BindFacts>> breaks = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        breaks.add(f -> new ElevatorRules.BindFacts(f.rule(), f.terminalUsable(), f.anchorActive(),
                f.anchorOwnedByActor(), f.operator(), f.cardinality(), f.warpPending(), EndgameCode.ROOT_FULL));
        expected.add("ROOT_FULL");
        breaks.add(f -> new ElevatorRules.BindFacts(f.rule(), f.terminalUsable(), f.anchorActive(),
                f.anchorOwnedByActor(), f.operator(), f.cardinality(), true, f.admission()));
        expected.add("WARP_PENDING");
        breaks.add(f -> new ElevatorRules.BindFacts(f.rule(), f.terminalUsable(), f.anchorActive(),
                f.anchorOwnedByActor(), f.operator(), EndgameCode.COLUMN_BOUND, f.warpPending(), f.admission()));
        expected.add("COLUMN_BOUND");
        breaks.add(f -> new ElevatorRules.BindFacts(f.rule(), f.terminalUsable(), f.anchorActive(), false,
                f.operator(), f.cardinality(), f.warpPending(), f.admission()));
        expected.add("ANCHOR_FOREIGN");
        breaks.add(f -> new ElevatorRules.BindFacts(f.rule(), f.terminalUsable(), false, f.anchorOwnedByActor(),
                f.operator(), f.cardinality(), f.warpPending(), f.admission()));
        expected.add("ANCHOR_UNAVAILABLE");
        breaks.add(f -> new ElevatorRules.BindFacts(f.rule(), false, f.anchorActive(), f.anchorOwnedByActor(),
                f.operator(), f.cardinality(), f.warpPending(), f.admission()));
        expected.add("TERMINAL_UNAVAILABLE");
        breaks.add(f -> new ElevatorRules.BindFacts(ElevatorEndpointCode.NOT_CURRENT_ORBIT, f.terminalUsable(),
                f.anchorActive(), f.anchorOwnedByActor(), f.operator(), f.cardinality(), f.warpPending(),
                f.admission()));
        expected.add("ELEVATOR_RULE NOT_CURRENT_ORBIT");
        ElevatorRules.BindFacts facts = ready;
        for (int i = 0; i < breaks.size(); i++) {
            facts = breaks.get(i).apply(facts);
            assertEquals(expected.get(i), ElevatorRules.bind(facts).describe(), "step " + i);
        }
        assertEquals(EndgameCode.OK, ElevatorRules.bind(new ElevatorRules.BindFacts(ElevatorEndpointCode.VALID, true,
                true, false, true, EndgameCode.OK, false, EndgameCode.OK)).code(), "an operator binds any anchor");
    }

    @Test
    void validityIsReDerivedAndAFailureNamesItsRule() {
        assertEquals("ELEVATOR_RULE LEVEL_ABSENT", ElevatorRules.validity(ElevatorEndpointCode.LEVEL_ABSENT, true,
                true, true).describe(), "a missing body Level");
        assertEquals(EndgameCode.PAIR_LEVEL_CHANGED, ElevatorRules.validity(ElevatorEndpointCode.VALID, false, true,
                true).code(), "a remapped Level");
        assertEquals(EndgameCode.TERMINAL_UNAVAILABLE, ElevatorRules.validity(ElevatorEndpointCode.VALID, true, false,
                false).code());
        assertEquals(EndgameCode.ANCHOR_UNAVAILABLE, ElevatorRules.validity(ElevatorEndpointCode.VALID, true, true,
                false).code());
        assertTrue(ElevatorRules.validity(ElevatorEndpointCode.VALID, true, true, true).ok());
    }

    @Test
    void membersRideAndShipStrangersDoNotAndUnbindIsWide() {
        assertEquals(EndgameCode.OK, ElevatorRules.access(false, true, true), "a member");
        assertEquals(EndgameCode.UNAUTHORIZED, ElevatorRules.access(false, false, true), "a stranger");
        assertEquals(EndgameCode.ANCHOR_OWNER_NOT_MEMBER, ElevatorRules.access(false, true, false),
                "the anchor's owner left the station");
        assertEquals(EndgameCode.OK, ElevatorRules.access(true, false, false), "operators are exempt");
        assertTrue(ElevatorRules.mayUnbind(false, true, false) && ElevatorRules.mayUnbind(false, false, true)
                && ElevatorRules.mayUnbind(true, false, false));
        assertFalse(ElevatorRules.mayUnbind(false, false, false));
    }

    @Test
    void aRideRequestAndItsCommitFollowTheirOrders() {
        ElevatorRules.RideFacts ready = new ElevatorRules.RideFacts(true, true, true, EndgameCode.OK, true, false, true,
                0, 0, ElevatorRules.Check.OK);
        assertTrue(ElevatorRules.rideRequest(ready).ok());
        assertEquals(EndgameCode.RIDE_LIMIT, ElevatorRules.rideRequest(new ElevatorRules.RideFacts(true, true, true,
                EndgameCode.OK, true, false, true, 4, 0, ElevatorRules.Check.OK)).code(), "four wait here");
        assertEquals(EndgameCode.RIDE_LIMIT, ElevatorRules.rideRequest(new ElevatorRules.RideFacts(true, true, true,
                EndgameCode.OK, true, false, true, 0, 64, ElevatorRules.Check.OK)).code(), "64 on the server");
        assertEquals(EndgameCode.RATE_LIMITED, ElevatorRules.rideRequest(new ElevatorRules.RideFacts(true, true, true,
                EndgameCode.OK, true, false, false, 0, 0, ElevatorRules.Check.OK)).code(), "cooldown");
        assertEquals(EndgameCode.RIDE_PENDING, ElevatorRules.rideRequest(new ElevatorRules.RideFacts(true, true, true,
                EndgameCode.OK, true, true, false, 0, 0, ElevatorRules.Check.OK)).code());
        assertEquals(EndgameCode.DISMOUNT_FIRST, ElevatorRules.rideRequest(new ElevatorRules.RideFacts(true, true,
                true, EndgameCode.OK, false, true, false, 0, 0, ElevatorRules.Check.OK)).code());
        assertEquals(EndgameCode.ANCHOR_OWNER_NOT_MEMBER, ElevatorRules.rideRequest(new ElevatorRules.RideFacts(true,
                true, true, EndgameCode.ANCHOR_OWNER_NOT_MEMBER, false, true, false, 0, 0,
                ElevatorRules.Check.OK)).code());
        assertEquals(EndgameCode.NOT_ON_PLATFORM, ElevatorRules.rideRequest(new ElevatorRules.RideFacts(true, true,
                false, EndgameCode.UNAUTHORIZED, false, true, false, 0, 0, ElevatorRules.Check.OK)).code());
        assertEquals(EndgameCode.NOT_BOUND, ElevatorRules.rideRequest(new ElevatorRules.RideFacts(true, false,
                false, EndgameCode.UNAUTHORIZED, false, true, false, 0, 0, ElevatorRules.Check.OK)).code());
        assertEquals(EndgameCode.SYSTEM_DISABLED, ElevatorRules.rideRequest(new ElevatorRules.RideFacts(false, false,
                false, EndgameCode.UNAUTHORIZED, false, true, false, 0, 0, ElevatorRules.Check.OK)).code());
        assertEquals(EndgameCode.PAIR_LEVEL_CHANGED, ElevatorRules.rideRequest(new ElevatorRules.RideFacts(true, true,
                true, EndgameCode.OK, true, false, true, 0, 0, ElevatorRules.Check.of(EndgameCode.PAIR_LEVEL_CHANGED)))
                .code(), "validity is checked before the ticket");

        assertEquals(ElevatorRules.Commit.TELEPORT, ElevatorRules.commit(ElevatorRules.Check.OK, EndgameCode.OK, true,
                true, false, true, EndgameCode.OK).commit());
        assertEquals(EndgameCode.TARGET_PROTECTED, ElevatorRules.commit(ElevatorRules.Check.OK, EndgameCode.OK, true,
                true, false, true, EndgameCode.TARGET_PROTECTED).check().code());
        assertEquals(EndgameCode.ARRIVAL_OBSTRUCTED, ElevatorRules.commit(ElevatorRules.Check.OK, EndgameCode.OK,
                true, true, false, false, EndgameCode.TARGET_PROTECTED).check().code());
        assertEquals(ElevatorRules.Commit.WAIT, ElevatorRules.commit(ElevatorRules.Check.OK, EndgameCode.OK, true,
                false, false, false, EndgameCode.TARGET_PROTECTED).commit(), "no synchronous load: wait");
        assertEquals(EndgameCode.ARRIVAL_UNLOADED, ElevatorRules.commit(ElevatorRules.Check.OK, EndgameCode.OK, true,
                false, true, false, EndgameCode.TARGET_PROTECTED).check().code(), "after 100 more ticks");
        assertEquals(EndgameCode.INSUFFICIENT_ENERGY, ElevatorRules.commit(ElevatorRules.Check.OK, EndgameCode.OK,
                false, false, true, false, EndgameCode.TARGET_PROTECTED).check().code());
        assertEquals(EndgameCode.UNAUTHORIZED, ElevatorRules.commit(ElevatorRules.Check.OK, EndgameCode.UNAUTHORIZED,
                false, false, true, false, EndgameCode.TARGET_PROTECTED).check().code());
        assertEquals("ELEVATOR_RULE NOT_CURRENT_ORBIT", ElevatorRules.commit(ElevatorRules.Check.rule(
                ElevatorEndpointCode.NOT_CURRENT_ORBIT), EndgameCode.UNAUTHORIZED, false, false, true, false,
                EndgameCode.TARGET_PROTECTED).check().describe(), "a warp after a lost unbind fails closed");
    }

    @Test
    void costsFollowTheEnergyPercentWithinTheBuffer() {
        assertEquals(20_000, ElevatorRules.cargoCost(100));
        assertEquals(50_000, ElevatorRules.rideCost(100));
        assertEquals(2_000, ElevatorRules.cargoCost(10));
        assertEquals(500_000, ElevatorRules.rideCost(1000), "the largest ride cost is the 500,000 FE buffer");
        assertThrows(IllegalArgumentException.class, () -> ElevatorRules.rideCost(9));
        assertThrows(IllegalArgumentException.class, () -> ElevatorRules.cargoCost(1001));
    }

    @Test
    void theStationGuardFailsClosed() {
        assertEquals(ElevatorRules.Guard.UNAVAILABLE, ElevatorRules.warp(false, false));
        assertEquals(ElevatorRules.Guard.BOUND, ElevatorRules.warp(true, true));
        assertEquals(ElevatorRules.Guard.ALLOWED, ElevatorRules.warp(true, false));
        assertEquals(ElevatorRules.Guard.UNAVAILABLE, ElevatorRules.delete(false, false, false));
        assertEquals(ElevatorRules.Guard.BOUND, ElevatorRules.delete(true, false, true), "cargo of the region");
        assertEquals(ElevatorRules.Guard.BOUND, ElevatorRules.delete(true, true, false));
        assertEquals(ElevatorRules.Guard.ALLOWED, ElevatorRules.delete(true, false, false));
    }

    @Test
    void thePairsSectionKeepsOnePairPerStationEndpointAndColumn() {
        ElevatorPairTable table = new ElevatorPairTable(() -> { });
        UUID station = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        UUID anchor = UUID.randomUUID();
        ElevatorPair first = pair(station, terminal, anchor, 10, 20);
        assertEquals(EndgameCode.OK, table.conflict(station, anchor, terminal, first.column()));
        table.add(first);
        assertEquals(EndgameCode.STATION_BOUND, table.conflict(station, UUID.randomUUID(), UUID.randomUUID(),
                new ElevatorPair.Column(MOON_LEVEL, 0, 0)));
        assertEquals(EndgameCode.ANCHOR_BOUND, table.conflict(UUID.randomUUID(), anchor, UUID.randomUUID(),
                new ElevatorPair.Column(MOON_LEVEL, 0, 0)));
        assertEquals(EndgameCode.TERMINAL_BOUND, table.conflict(UUID.randomUUID(), UUID.randomUUID(), terminal,
                new ElevatorPair.Column(MOON_LEVEL, 0, 0)));
        assertEquals(EndgameCode.COLUMN_BOUND, table.conflict(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), first.column()));
        assertThrows(IllegalArgumentException.class, () -> table.restore(pair(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 10, 20)), "a second pair on one column");
        assertTrue(table.names(anchor) && table.names(terminal) && table.forStation(station).isPresent());
        assertEquals(terminal, first.otherEnd(anchor));
        table.remove(first.pairId());
        assertFalse(table.names(anchor) || table.forStation(station).isPresent());
        for (int i = 0; i < 1024; i++) {
            table.add(pair(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), i, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> table.add(pair(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 5000, 0)), "beyond 1,024 pairs");
        assertThrows(IllegalArgumentException.class, () -> new ElevatorPair(UUID.randomUUID(), station, anchor,
                anchor, MOON, MOON_LEVEL, 0, 0, 64, 0L, OWNER), "one endpoint at both ends");
    }

    @Test
    void thePairsRoundTripPinTheirEndpointsAndNeedThem() {
        EndgameRoot root = EndgameRoot.create();
        UUID anchor = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        root.register(anchor, ANCHOR, OWNER, MOON_LEVEL, 5L, false, 2048, 64);
        root.register(terminal, TERMINAL, OWNER, SPACE, 7L, false, 2048, 64);
        ElevatorPair bound = pair(UUID.randomUUID(), terminal, anchor, 0, 0);
        root.pairs().add(bound);
        assertTrue(root.changedSinceEpoch());
        assertEquals(512L, root.pairs().accountedBytes());
        root.remove(anchor);
        assertTrue(root.pinned(anchor), "a pair pins the anchor's tombstone");
        CompoundTag encoded = EndgameRootCodec.encode(root, new CompoundTag());
        EndgameRoot decoded = EndgameRootCodec.decode(encoded);
        assertEquals(List.of(bound), List.copyOf(decoded.pairs().pairs()));
        assertEquals(encoded, EndgameRootCodec.encode(decoded, new CompoundTag()));
        CompoundTag orphan = encoded.copy();
        ListTag pairs = new ListTag();
        pairs.add(ElevatorPairCodec.encode(pair(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1)));
        orphan.put(EndgameRootCodec.ELEVATOR_PAIRS, pairs);
        assertThrows(IllegalArgumentException.class, () -> EndgameRootCodec.decode(orphan),
                "a pair naming no endpoint or tombstone");
        CompoundTag extra = ElevatorPairCodec.encode(bound);
        extra.putInt("rotation", 1);
        assertThrows(IllegalArgumentException.class, () -> ElevatorPairCodec.decode(extra), "an unknown key");
    }
}
