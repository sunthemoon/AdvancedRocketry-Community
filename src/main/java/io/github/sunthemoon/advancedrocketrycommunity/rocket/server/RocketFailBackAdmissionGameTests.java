package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalSchedulingGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** G-N2: real fail-back, source entity, named pad claim and session membership. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketFailBackAdmissionGameTests {
    private RocketFailBackAdmissionGameTests() { }

    @GameTest(template = "rocket_test", batch = "journal_fail_back_admission", timeoutTicks = 100)
    public static void fullQueueLeavesSourceRecordPadAndLiveMembershipIntact(GameTestHelper helper) {
        check(helper, 0);
    }

    @GameTest(template = "rocket_test", batch = "journal_fail_back_admission", timeoutTicks = 100)
    public static void stoppedJournalLeavesSourceRecordPadAndLiveMembershipIntact(GameTestHelper helper) {
        check(helper, 1);
    }

    @GameTest(template = "rocket_test", batch = "journal_fail_back_admission", timeoutTicks = 100)
    public static void abortedJournalLeavesSourceRecordPadAndLiveMembershipIntact(GameTestHelper helper) {
        check(helper, 2);
    }

    private static void check(GameTestHelper helper, int mode) {
        var level = helper.getLevel(); var server = level.getServer();
        var journal = new RocketTransferSavedData(); var service = new RocketTransferService();
        var index = LandingPadIndex.get(server);
        UUID station = UUID.randomUUID(), owner = UUID.randomUUID();
        var pad = index.register(station, owner, UUID.randomUUID(), "N2", new StationPosition(0, 80, 0));
        RocketEntity source = null; Path directory = null;
        try {
            directory = Files.createTempDirectory("arce-n2-admission-");
            source = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(source != null, "No source entity");
            var origin = helper.absolutePos(new BlockPos(3, 2, 3));
            var record = record(helper, source.getUUID(), owner, station, pad.reference(), origin, 0);
            source.initializeTransferred(record.sourceSnapshot(), record.logicalRocketId(), owner, record.sourceFlightData());
            RocketTransferEntities.positionAtAltitude(source, record.sourceSnapshot().sourceOrigin());
            helper.assertTrue(level.addFreshEntity(source), "Source was not published");
            journal.put(record);
            var otherA = record(helper, UUID.randomUUID(), owner, station, null, origin, 64);
            var otherB = record(helper, UUID.randomUUID(), owner, station, null, origin, 128);
            journal.put(otherA); journal.put(otherB);
            RocketJournalGameTestSupport.confirmDetached(helper, journal,
                    directory.resolve(RocketTransferSavedData.DATA_NAME + ".dat"));
            helper.assertTrue(index.claim(station, pad.reference(), pad.physicalId(), record.transferId()), "Pad was not claimed");
            var live = live(service); live.add(record.transferId());
            if (mode == 0) {
                journal.put(otherA.destinationSpawned(UUID.randomUUID()));
                helper.assertTrue(RocketJournalSchedulingGameTestSupport.queue(helper, journal, otherA.logicalRocketId(),
                        () -> { }), "First continuation was refused");
                journal.put(otherB.destinationSpawned(UUID.randomUUID()));
                helper.assertTrue(RocketJournalSchedulingGameTestSupport.queue(helper, journal, otherB.logicalRocketId(),
                        () -> { }), "Second continuation was refused");
            } else if (mode == 1) { journal.stop(); }
            else { journal.abort(); }
            var flight = source.flightData().orElseThrow(); var position = source.position();
            var root = journal.save(new CompoundTag());
            helper.assertTrue(!service.failBackToSource(server, journal, record, source,
                    RocketTransferReturnReason.COUNTDOWN_CANCELLED, RocketRequestCompletion.NONE), "Fail-back was admitted");
            helper.assertTrue(source.flightData().orElseThrow() == flight && source.position().equals(position),
                    "Refused fail-back mutated the source");
            helper.assertTrue(journal.find(record.transferId()).orElseThrow() == record
                    && root.equals(journal.save(new CompoundTag())), "Refused fail-back mutated the journal");
            helper.assertTrue(record.transferId().equals(index.find(station, pad.reference()).orElseThrow().claim()),
                    "Refused fail-back released the named pad");
            helper.assertTrue(live.contains(record.transferId()), "Refused fail-back dropped live membership");
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        finally {
            journal.abort(); service.clear();
            if (source != null) { source.discard(); }
            index.remove(station, pad.reference(), pad.physicalId());
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> live(RocketTransferService service) {
        try {
            var field = RocketTransferService.class.getDeclaredField("liveTransfers"); field.setAccessible(true);
            return (Set<UUID>) field.get(service);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static RocketTransferRecord record(GameTestHelper helper, UUID entity, UUID owner, UUID station,
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference pad, BlockPos origin, int offset) {
        long now = helper.getLevel().getGameTime(); UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID();
        var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1,
                RocketPropulsion.Settings.DEFAULTS);
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), helper.getLevel().dimension().location(),
                new RocketPosition(origin.getX(), origin.getY(), origin.getZ()),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(
                        net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(1, 100, 1_000, 1_000, 1, 1, 1, 0), now, propulsion);
        var destination = snapshot.relocated(UUID.randomUUID(), CelestialIds.SPACE_LEVEL.location(),
                new RocketPosition(1_000 + offset, 80, 1_000), now);
        var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), ModIdentity.id("space"),
                snapshot.sourceDimension(), destination.sourceDimension(), station, 500, now);
        if (pad != null) { plan = plan.withLandingPad(pad); }
        var flight = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(),
                snapshot.sourceOrigin(), now).withFuel(fuel, now).withPlan(plan).startCountdown(now)
                .completeCountdown(now).beginTransit(transfer, now);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(),
                destination.sourceOrigin(), now);
        return RocketTransferRecord.create(transfer, logical, owner, entity, snapshot, destination, flight, arrival, 500, now);
    }
}
