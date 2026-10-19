package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W98 (N3-A1): the census validators, the frozen projection and R3's crew table, without a running server. */
final class RocketRetirementProofTest {
    private static final UUID LOGICAL = UUID.randomUUID(), SURVIVOR = UUID.randomUUID();

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test
    void theSurvivorSlotNeedsExactlyTheFrozenImageAndNoOtherCopyOfL() {
        var frozen = rocket(SURVIVOR, LOGICAL, 1_000, 101.0D);
        assertTrue(RocketRetirementProof.survivorSlot(slot(frozen.copy(), other()), LOGICAL, frozen));
        assertFalse(RocketRetirementProof.survivorSlot(slot(other()), LOGICAL, frozen), "The survivor's image is missing");
        assertFalse(RocketRetirementProof.survivorSlot(slot(frozen.copy(), rocket(UUID.randomUUID(), LOGICAL, 1_000, 101.0D)), LOGICAL, frozen),
                "A second physical copy of L shares the slot");
        assertFalse(RocketRetirementProof.survivorSlot(slot(rocket(UUID.randomUUID(), LOGICAL, 1_000, 101.0D)), LOGICAL, frozen),
                "A rebuilt UUID is not the frozen survivor");
        assertFalse(RocketRetirementProof.survivorSlot(slot(rocket(SURVIVOR, LOGICAL, 500, 101.0D)), LOGICAL, frozen),
                "Different fuel or debits are not the frozen survivor");
        assertFalse(RocketRetirementProof.survivorSlot(slot(rocket(SURVIVOR, LOGICAL, 1_000, 149.0D)), LOGICAL, frozen),
                "An older in-flight image above the pad is not the returned survivor");
        var mounted = frozen.copy(); mounted.put("Passengers", list(other()));
        assertFalse(RocketRetirementProof.survivorSlot(slot(mounted), LOGICAL, frozen), "The survivor carries a nested entity");
        assertFalse(RocketRetirementProof.survivorSlot(new CompoundTag(), LOGICAL, frozen), "A root without an entity list");
    }

    @Test
    void aDiscardedSlotMustNotNameLAtAnyDepth() {
        assertTrue(RocketRetirementProof.absentSlot(slot(other()), LOGICAL, SURVIVOR));
        assertTrue(RocketRetirementProof.absentSlot(slot(), LOGICAL, SURVIVOR), "An empty slot proves absence");
        assertFalse(RocketRetirementProof.absentSlot(slot(rocket(UUID.randomUUID(), LOGICAL, 0, 80.0D)), LOGICAL, SURVIVOR),
                "A discarded destination image is still on disk");
        var carrier = other(); carrier.put("Passengers", list(rocket(UUID.randomUUID(), LOGICAL, 0, 80.0D)));
        assertFalse(RocketRetirementProof.absentSlot(slot(carrier), LOGICAL, SURVIVOR), "A nested copy of L");
        var flightOnly = rocket(UUID.randomUUID(), UUID.randomUUID(), 0, 80.0D);
        flightOnly.getCompound(RocketRetirementProof.DATA).getCompound("flight_data").putUUID("logical_rocket_id", LOGICAL);
        assertFalse(RocketRetirementProof.absentSlot(slot(flightOnly), LOGICAL, SURVIVOR), "A copy naming L only in its flight data");
        var stranger = other(); stranger.putUUID("UUID", SURVIVOR);
        assertFalse(RocketRetirementProof.absentSlot(slot(stranger), LOGICAL, SURVIVOR), "Any entity with the survivor's UUID");
        assertNull(RocketRetirementProof.named(new CompoundTag(), LOGICAL, SURVIVOR));
    }

    @Test
    void crewRuleFollowsR17PlacementAndOnlyACountdownCancellationIgnoresOfflineSeats() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var seats = List.of(a, b);
        var observed = new java.util.HashMap<UUID, RocketRetirementProof.Seat>(Map.of(
                a, RocketRetirementProof.Seat.PLACED, b, RocketRetirementProof.Seat.OFFLINE));
        assertTrue(RocketRetirementProof.crewReady(seats, observed::get, new HashSet<>(), true), "Countdown: offline seats never left the pad");
        assertFalse(RocketRetirementProof.crewReady(seats, observed::get, new HashSet<>(), false), "End of transit: an offline seat waits");
        observed.put(b, RocketRetirementProof.Seat.UNPLACED);
        assertFalse(RocketRetirementProof.crewReady(seats, observed::get, new HashSet<>(), true), "An online but unplaced seat always waits");
        var delivered = new HashSet<UUID>();
        observed.put(b, RocketRetirementProof.Seat.PLACED);
        assertTrue(RocketRetirementProof.crewReady(seats, observed::get, delivered, false));
        assertEquals(java.util.Set.of(a, b), delivered);
        observed.put(a, RocketRetirementProof.Seat.OFFLINE); observed.put(b, RocketRetirementProof.Seat.UNPLACED);
        assertTrue(RocketRetirementProof.crewReady(seats, observed::get, delivered, false),
                "A dismount or logout after delivery never invalidates it");
        assertTrue(RocketRetirementProof.crewReady(List.of(), observed::get, new HashSet<>(), false), "No crew");
    }

    @Test
    void seatClassificationIsR17CancellationPlacement() {
        assertEquals(RocketRetirementProof.Seat.OFFLINE, RocketRetirementProof.seat(false, false, false, false, false));
        assertEquals(RocketRetirementProof.Seat.PLACED, RocketRetirementProof.seat(true, true, true, true, false), "Aboard");
        assertEquals(RocketRetirementProof.Seat.PLACED, RocketRetirementProof.seat(true, true, true, false, true), "Unmounted");
        assertEquals(RocketRetirementProof.Seat.UNPLACED, RocketRetirementProof.seat(true, true, true, false, false), "Riding something else");
        assertEquals(RocketRetirementProof.Seat.UNPLACED, RocketRetirementProof.seat(true, true, false, true, false), "Another Level");
        assertEquals(RocketRetirementProof.Seat.UNPLACED, RocketRetirementProof.seat(true, false, true, true, false), "Dead or disconnected");
    }

    @Test
    void aFullCensusRefusesANewSlotInsteadOfDroppingIt() {
        var census = new java.util.ArrayList<RocketRetirementProof.Slot>();
        var dimension = net.minecraft.resources.ResourceLocation.tryParse("minecraft:overworld");
        for (int x = 0; x < 3; x++) { assertTrue(RocketRetirementProof.addSlot(census, slot(dimension, x), 3)); }
        assertTrue(RocketRetirementProof.addSlot(census, slot(dimension, 1), 3), "A slot already in the census needs no room");
        assertFalse(RocketRetirementProof.addSlot(census, slot(dimension, 3), 3), "A new slot past the limit is refused (W98-R7)");
        assertEquals(List.of(slot(dimension, 0), slot(dimension, 1), slot(dimension, 2)), census, "Nothing is dropped or replaced");
        assertEquals(1 + 2 * io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES,
                RocketOrdinaryRetirement.MAX_SLOTS, "The survivor's slot plus every discarded candidate of both ends");
    }

    private static RocketRetirementProof.Slot slot(net.minecraft.resources.ResourceLocation dimension, int x) {
        return new RocketRetirementProof.Slot(dimension, new net.minecraft.world.level.ChunkPos(x, 0));
    }

    @Test
    void theStationarySourceKeepsTheRecordsSourceFuelAndDebits() {
        var record = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        var stationary = RocketTransferEntities.stationarySource(record, 900L);
        assertEquals(record.sourceFlightData().fuel(), stationary.fuel(), "The projection's fuel check compares T's source fuel");
        assertTrue(stationary.plan().isEmpty() && stationary.activeTransferId().isEmpty());
    }

    private static CompoundTag rocket(UUID id, UUID logical, long fuel, double y) {
        var flight = new CompoundTag(); flight.putUUID("logical_rocket_id", logical); flight.putLong("fuel", fuel);
        var data = new CompoundTag(); data.putUUID("assembly_transaction_id", logical); data.put("flight_data", flight);
        var tag = new CompoundTag(); tag.putString("id", "advancedrocketrycommunity:rocket"); tag.putUUID("UUID", id);
        var pos = new ListTag(); pos.add(DoubleTag.valueOf(384.5D)); pos.add(DoubleTag.valueOf(y)); pos.add(DoubleTag.valueOf(384.5D));
        tag.put("Pos", pos); tag.put(RocketRetirementProof.DATA, data);
        return tag;
    }

    private static CompoundTag other() {
        var tag = new CompoundTag(); tag.putString("id", "minecraft:armor_stand"); tag.putUUID("UUID", UUID.randomUUID());
        return tag;
    }

    private static ListTag list(CompoundTag... entities) {
        var list = new ListTag();
        for (var entity : entities) { list.add(entity); }
        return list;
    }

    private static CompoundTag slot(CompoundTag... entities) {
        var root = new CompoundTag(); root.put("Entities", list(entities));
        return root;
    }
}
