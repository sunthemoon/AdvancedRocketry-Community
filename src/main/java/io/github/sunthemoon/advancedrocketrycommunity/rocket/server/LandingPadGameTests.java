package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestAutosave;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickDriver;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.NamedLandingPadStaging;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.*;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.*;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native block/authority, staging, transfer and restart-equivalent journal-cut coverage. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandingPadGameTests {
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create(
            "arce_gametest_landing_pad", Comparator.<UUID>naturalOrder(), 400);
    /**
     * W187: a source-side cut's survivor proof captures its own slot and, one tick later, a discarded copy's slot; each
     * completes with its Level's next 20-tick force. The cut allows 40 ticks after its recovery for that proof, so a
     * later autosave cannot reach the cut, which has passed or failed by then.
     */
    private static final int CUT_WINDOW_TICKS = 40;

    private LandingPadGameTests() { }
    private record Fixture(ServerLevel space, StationState station, BlockPos pad,
                           List<RocketTransferRecord> flightTickets) {
        void retainFlight(UUID transfer) {
            flightTickets.add(RocketTransferSavedData.get(space.getServer()).find(transfer).orElseThrow());
        }

        void releaseFlights() {
            // These tests remove/reload journals themselves. Keep the ticket identities independently
            // so a completed or failed fixture never leaves its tickets until their 400-tick expiry.
            flightTickets.forEach(record -> RocketTransferEntities.releaseFlightTickets(space.getServer(), record));
            flightTickets.clear();
        }
    }

    @GameTest(template = "rocket_test", batch = "landing_pads", timeoutTicks = 160)
    public static void ownerOperatorAndMemberRegisterRemoveMatrix(GameTestHelper helper) {
        Fixture f = fixture(helper);
        UUID member = UUID.randomUUID(); UUID operator = UUID.randomUUID();
        var registry = StationRegistrySavedData.get(helper.getLevel().getServer());
        registry.addMember(f.station.stationId(), member);
        var station = registry.find(f.station.stationId()).orElseThrow();
        helper.assertTrue(new StationAccessService().allowed(station, member, false, StationAccessAction.VISIT), "Member cannot visit");
        rejected(helper, () -> LandingPadService.register(f.space, f.pad, member, false, "Private"));
        var ownerPad = LandingPadService.register(f.space, f.pad, station.ownerId(), false, "Owner Dock");
        rejected(helper, () -> LandingPadService.remove(f.space, f.pad, member, false));
        helper.assertTrue(LandingPadService.remove(f.space, f.pad, station.ownerId(), false), "Owner removal failed");
        var operatorPad = LandingPadService.register(f.space, f.pad, operator, true, "Operator Dock");
        helper.assertTrue(!ownerPad.padId().equals(operatorPad.padId()) && ownerPad.epoch() < operatorPad.epoch(), "Pad replacement inherited identity");
        helper.assertTrue(operatorPad.ownerId().equals(station.ownerId()), "Operator made a private pad for themselves");
        helper.assertTrue(LandingPadService.remove(f.space, f.pad, operator, true), "Operator removal failed");
        cleanup(helper, f); helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "landing_pads", timeoutTicks = 160)
    public static void staleIdentityObstructionSmallAndUnloadedPadsAreRefused(GameTestHelper helper) {
        Fixture f = fixture(helper); var source = sourceSnapshot(helper);
        var entry = LandingPadService.register(f.space, f.pad, f.station.ownerId(), false, "Dock");
        var staging = new NamedLandingPadStaging();
        BlockPos obstacle = f.pad.above(2);
        f.space.setBlock(obstacle, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var blocked = staging.select(f.space, source, UUID.randomUUID(), f.station, entry.reference(), List.of(), 0);
        helper.assertTrue(!blocked.success() && blocked.detail().endsWith("obstructed"), "Obstructed pad accepted");
        f.space.removeBlock(obstacle, false);
        // This non-square rocket's second footprint column needs support.
        BlockPos support = f.pad.east(); f.space.removeBlock(support, false);
        var small = staging.select(f.space, source, UUID.randomUUID(), f.station, entry.reference(), List.of(), 0);
        helper.assertTrue(!small.success() && small.detail().endsWith("too_small"), "Too-small pad accepted");
        f.space.setBlock(support, Blocks.GRAY_CONCRETE.defaultBlockState(), Block.UPDATE_ALL);
        var stale = new LandingPadReference(entry.padId(), entry.epoch() + 1);
        helper.assertTrue(!staging.select(f.space, source, UUID.randomUUID(), f.station, stale, List.of(), 0).success(), "Stale epoch accepted");
        LandingPadService.remove(f.space, f.pad, f.station.ownerId(), false);
        f.space.removeBlock(f.pad, false); f.space.setBlock(f.pad, ModBlocks.LANDING_PAD.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(LandingPadService.resolve(f.space.getServer(), f.station.stationId(), entry.reference()) == null, "Replacement inherited old reservation");
        // Index a legitimate in-region location whose chunk has never been loaded.
        var index = LandingPadIndex.get(f.space.getServer());
        int farX = f.station.region().maximumX() - 8;
        int farZ = f.station.region().maximumZ() - 8;
        helper.assertTrue(f.space.getChunkSource().getChunkNow(farX >> 4, farZ >> 4) == null, "Unloaded fixture was already loaded");
        var unloaded = index.register(f.station.stationId(), f.station.ownerId(), UUID.randomUUID(), "Unloaded",
                new io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition(farX - f.station.landingPad().x(), f.pad.getY(), farZ - f.station.landingPad().z()));
        helper.assertTrue(!staging.select(f.space, source, UUID.randomUUID(), f.station, unloaded.reference(), List.of(), 0).success(), "Unloaded pad accepted");
        helper.assertTrue(f.space.getChunkSource().getChunkNow(farX >> 4, farZ >> 4) == null, "Pad admission forced a chunk");
        index.remove(f.station.stationId(), unloaded.reference(), unloaded.physicalId());
        cleanup(helper, f); helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "landing_pads", timeoutTicks = 160)
    public static void sparseWideFootprintAndHeightBudgetsRefuseBeforeInspection(GameTestHelper helper) {
        Fixture f = fixture(helper); var source = sourceSnapshot(helper);
        var entry = LandingPadService.register(f.space, f.pad, f.station.ownerId(), false, "Dock");
        // Only two actual blocks, but a full 64*64 bounding footprint must not be probed.
        var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), Map.of())),
                new RocketBlock(new RocketPosition(63, 0, 63), new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
        var wide = RocketStructureSnapshot.create(UUID.randomUUID(), source.sourceDimension(), source.sourceOrigin(), blocks, List.of(),
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats(2, 2, 0, 0, 0, 0, 0, 0), 0);
        var selected = new NamedLandingPadStaging().select(f.space, wide, UUID.randomUUID(), f.station, entry.reference(), List.of(), 0);
        helper.assertTrue(!selected.success() && selected.detail().endsWith("block_budget"), "Wide sparse footprint bypassed inspection budget");
        helper.assertTrue(selected.chunksLoaded() == 0, "Wide footprint loaded chunks");
        var tallBlocks = List.of(blocks.get(0), new RocketBlock(new RocketPosition(7, 63, 7), blocks.get(1).state()));
        var tall = RocketStructureSnapshot.create(UUID.randomUUID(), source.sourceDimension(), source.sourceOrigin(), tallBlocks,
                List.of(), wide.stats(), 0);
        var tallSelection = new NamedLandingPadStaging().select(f.space, tall, UUID.randomUUID(), f.station,
                entry.reference(), List.of(), 0);
        helper.assertTrue(!tallSelection.success() && tallSelection.detail().endsWith("block_budget"), "Height bypassed complete clearance budget");
        helper.assertTrue(tallSelection.chunksLoaded() == 0, "Tall footprint loaded chunks");
        cleanup(helper, f); helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "landing_pads", timeoutTicks = 400)
    public static void namedFlightLandsOnPadReleasesCommitClaimAndKeepsDefaultCell(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        Fixture f = fixture(helper); var snapshot = sourceSnapshot(helper);
        var entry = LandingPadService.register(f.space, f.pad, f.station.ownerId(), false, "Arrival");
        await(helper, driver, 80, () -> ready(helper.getLevel(), snapshot.sourceOrigin()) && f.space.areEntitiesLoaded(new ChunkPos(f.pad).toLong()), () -> {
            RocketEntity source = addSource(helper, snapshot, f.station.ownerId());
            UUID logical = source.assemblyTransactionId().orElseThrow(); UUID transfer = UUID.randomUUID();
            var result = RocketRuntime.requestAdminPadFlight(source, f.station.stationId(), entry.reference(), transfer);
            f.retainFlight(transfer);
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.assertPendingLaunch(helper, result, source);
            helper.assertTrue(transfer.equals(LandingPadIndex.get(f.space.getServer()).find(f.station.stationId(), entry.reference()).orElseThrow().claim()), "Prepared claim missing");
            await(helper, driver, helper.getTick() + 270, () -> {
                var journal = RocketTransferSavedData.get(f.space.getServer()); var record = journal.find(transfer).orElse(null);
                var destination = record == null || record.destinationEntityId().isEmpty() ? null : f.space.getEntity(record.destinationEntityId().orElseThrow());
                return destination instanceof RocketEntity r && r.flightData().orElseThrow().state() == RocketFlightState.LANDED;
            }, () -> {
                var record = RocketTransferSavedData.get(f.space.getServer()).find(transfer).orElseThrow();
                var landed = (RocketEntity) f.space.getEntity(record.destinationEntityId().orElseThrow());
                helper.assertTrue(landed != null && landed.flightData().orElseThrow().state() == RocketFlightState.LANDED, "Named rocket never landed");
                var region = io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion.fromSnapshot(landed.snapshot().orElseThrow());
                helper.assertTrue(region.minimum().y() == f.pad.getY() + 1 && region.minimum().x() == f.pad.getX(), "Rocket missed indexed pad footprint");
                helper.assertTrue(landed.assemblyTransactionId().orElseThrow().equals(logical), "Logical identity changed");
                helper.assertTrue(LandingPadIndex.get(f.space.getServer()).find(f.station.stationId(), entry.reference()).orElseThrow().claim() == null, "Commit claim retained");
                helper.assertTrue(f.station.landingPad().equals(StationRegistrySavedData.get(f.space.getServer()).find(f.station.stationId()).orElseThrow().landingPad()), "Named pad rewrote allocated cell");
                io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketMonitorPadAssertions.verify(helper, f.space, f.pad, landed);
                landed.discard(); RocketTransferSavedData.get(f.space.getServer()).remove(transfer);
                var defaultSelection = new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector()
                        .selectStation(f.space, snapshot, UUID.randomUUID(), f.station, List.of(), 0);
                helper.assertTrue(defaultSelection.success(), "Untargeted default cell pad stopped working");
                helper.assertTrue(defaultSelection.destinationSnapshot().sourceOrigin().y() + snapshot.bounds().minimum().y() == f.station.landingPad().y(), "Default pad elevation changed");
                cleanup(helper, f); helper.succeed();
            });
        });
    }

    /**
     * W98: its own batch. Its return now waits a few ticks for the survivor proof, and the recovery matrix in
     * landing_pads replaces the world journal object (a restart simulation), which would abandon that pending return.
     */
    @GameTest(template = "rocket_test", batch = "landing_pads_cancel", timeoutTicks = 180)
    public static void cancellationReleasesExactlyOneTransferClaim(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        Fixture f = fixture(helper); var snapshot = sourceSnapshot(helper);
        var entry = LandingPadService.register(f.space, f.pad, f.station.ownerId(), false, "Cancel");
        await(helper, driver, 80, () -> ready(helper.getLevel(), snapshot.sourceOrigin()) && f.space.areEntitiesLoaded(new ChunkPos(f.pad).toLong()), () -> {
            var source = addSource(helper, snapshot, f.station.ownerId()); var transfer = UUID.randomUUID();
            var countdown = countdown(source, f.station, entry, transfer);
            var service = new RocketTransferService();
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.assertPendingLaunch(helper,
                    service.prepareLaunch(source, countdown), source);
            f.retainFlight(transfer);
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.acknowledgeFixture(helper);
            helper.assertTrue(service.cancelCountdown(source).code() == RocketFlightRequestCode.JOURNAL_PENDING,
                    "Cancellation did not await its removal acknowledgement");
            // W98 (N3-A1): the record and claim stay until the returned source's own entity slot is read back.
            helper.assertTrue(RocketTransferSavedData.get(f.space.getServer()).find(transfer).isPresent()
                    && transfer.equals(LandingPadIndex.get(f.space.getServer()).find(f.station.stationId(), entry.reference()).orElseThrow().claim()),
                    "Cancellation released its record or claim before the survivor proof");
            await(helper, driver, helper.getTick() + 60, () -> retired(helper, service, transfer), () -> {
                helper.assertTrue(LandingPadIndex.get(f.space.getServer()).find(f.station.stationId(), entry.reference()).orElseThrow().claim() == null, "Cancel claim retained");
                helper.assertTrue(RocketTransferSavedData.get(f.space.getServer()).find(transfer).isEmpty(), "Cancelled transfer retained");
                source.discard(); cleanup(helper, f); helper.succeed();
            });
        });
    }

    /** W98: drive a detached service's ordinary retirements and acknowledge; true once the record has left the journal. */
    private static boolean retired(GameTestHelper helper, RocketTransferService service, UUID transfer) {
        RocketSourceRetirementFixtures.recovery(service).tickRetirements(helper.getLevel().getServer());
        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.acknowledgeFixture(helper);
        return RocketTransferSavedData.get(helper.getLevel().getServer()).find(transfer).isEmpty();
    }

    @GameTest(template = "rocket_test", batch = "landing_pads", timeoutTicks = 300)
    public static void allFiveRecoveryPhasesAndFourPresenceCutsReleaseClaimsAfterReload(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        Fixture f = fixture(helper); var snapshot = sourceSnapshot(helper);
        var entry = LandingPadService.register(f.space, f.pad, f.station.ownerId(), false, "Recovery");
        await(helper, driver, 100, () -> ready(helper.getLevel(), snapshot.sourceOrigin()) && f.space.areEntitiesLoaded(new ChunkPos(f.pad).toLong()),
                () -> recoveryCut(helper, driver, f, snapshot, entry, 0));
    }

    /**
     * One phase/presence cut, then the next. W98 (N3-A1): a source-side recovery keeps its record until the returned or
     * rebuilt source's own slot is read back, so its RECOVERED report follows a short wait; every assertion is unchanged.
     */
    private static void recoveryCut(GameTestHelper helper, GameTestTickDriver driver, Fixture f, RocketStructureSnapshot snapshot,
                                    LandingPadEntry entry, int cut) {
        if (cut / 4 == RocketTransferPhase.values().length) {
            helper.assertTrue(cut == 20, "Recovery matrix did not cover every phase/presence cut");
            cleanup(helper, f); helper.succeed(); return;
        }
        RocketTransferPhase phase = RocketTransferPhase.values()[cut / 4]; int presence = cut % 4;
        var action = RocketTransferRecoveryDecision.decide(phase, new RocketTransferPresence((presence & 1) != 0, (presence & 2) != 0));
        // W187: a periodic autosave stores the survivor's entity section and supersedes the source return's pending
        // capture. The failed attempt releases its reservation, and the runtime's own recovery can then take T over and
        // remove it, so this detached service reports NOT_FOUND (hosted run 40725066567, COMMITTED/1). Start a source
        // return only when the next autosave falls after the cut's window; destination cuts take no native capture.
        boolean sourceReturn = action == RocketTransferRecoveryAction.KEEP_SOURCE
                || action == RocketTransferRecoveryAction.REMOVE_DESTINATION_KEEP_SOURCE || action == RocketTransferRecoveryAction.REBUILD_SOURCE;
        if (sourceReturn && GameTestAutosave.within(f.space.getServer(), CUT_WINDOW_TICKS)) {
            await(helper, driver, helper.getTick() + CUT_WINDOW_TICKS + 1L, () -> !GameTestAutosave.within(f.space.getServer(), CUT_WINDOW_TICKS),
                    () -> recoveryCut(helper, driver, f, snapshot, entry, cut));
            return;
        }
        var source = addSource(helper, snapshot, f.station.ownerId()); UUID transfer = UUID.randomUUID();
        var countdown = countdown(source, f.station, entry, transfer);
        var selection = new NamedLandingPadStaging().select(f.space, snapshot, transfer, f.station, entry.reference(), List.of(), 0);
        helper.assertTrue(selection.success(), "Recovery staging failed: " + selection.detail());
        var transit = countdown.completeCountdown(countdown.stateStartedGameTime() + 60).beginTransit(transfer, countdown.stateStartedGameTime() + 140);
        var destinationSnapshot = selection.destinationSnapshot();
        var arrival = transit.arriveAtDestination(transit.fuel().debit(transit.plan().orElseThrow()).state(),
                f.station.orbitBody(), CelestialIds.SPACE_LEVEL.location(), destinationSnapshot.sourceOrigin(), transit.stateStartedGameTime() + 20);
        source.updateFlightData(transit);
        RocketEntity destination = ModEntities.ROCKET.get().create(f.space);
        helper.assertTrue(destination != null, "Recovery destination could not be created");
        destination.initializeTransferred(destinationSnapshot, transit.logicalRocketId(), f.station.ownerId(), arrival);
        RocketTransferEntities.positionAtAltitude(destination, destinationSnapshot.sourceOrigin());
        var record = RocketTransferRecord.create(transfer, transit.logicalRocketId(), f.station.ownerId(), source.getUUID(), snapshot,
                destinationSnapshot, transit, arrival, transit.plan().orElseThrow().requiredFuel(), 0);
        if (phase != RocketTransferPhase.PREPARED) {
            record = record.destinationSpawned(destination.getUUID());
            while (record.phase() != phase) { record = record.advance(RocketTransferPhase.values()[record.phase().ordinal() + 1]); }
        }
        if ((presence & 1) == 0) { source.discard(); }
        if ((presence & 2) != 0) { helper.assertTrue(f.space.addFreshEntity(destination), "Recovery destination spawn failed"); }
        var journal = RocketTransferSavedData.get(f.space.getServer()); journal.put(record); journal.flush(f.space.getServer());
        f.retainFlight(transfer);
        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.acknowledgeFixture(helper);
        // Real native SavedData files + BE serializer, then fresh Java lifecycle owners.
        var storage = f.space.getServer().overworld().getDataStorage(); storage.save();
        var padBe = LandingPadService.loadedPad(f.space, f.pad); var padTag = padBe.saveWithFullMetadata();
        var restoredPad = new LandingPadBlockEntity(f.pad, f.space.getBlockState(f.pad)); restoredPad.load(padTag); f.space.setBlockEntity(restoredPad);
        try {
            var folder = f.space.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
            var root = NbtIo.readCompressed(folder.resolve(LandingPadIndex.DATA_NAME + ".dat").toFile()).getCompound("data");
            storage.set(LandingPadIndex.DATA_NAME, LandingPadIndex.load(root));
            var transferRoot = NbtIo.readCompressed(folder.resolve(RocketTransferSavedData.DATA_NAME + ".dat").toFile()).getCompound("data");
            journal.stop();
            storage.set(RocketTransferSavedData.DATA_NAME, RocketTransferSavedData.load(transferRoot));
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        var recovery = new RocketTransferService();
        var first = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport
                .recoverFixture(helper, () -> recovery.recover(f.space.getServer(), transfer));
        var reports = new ArrayList<RocketTransferRecoveryReport>(List.of(first));
        Runnable finish = () -> {
            var report = reports.get(reports.size() - 1);
            helper.assertTrue(report.status() == RocketTransferRecoveryReport.Status.RECOVERED, "Recovery cut " + phase + "/" + presence + " failed: " + report.status());
            helper.assertTrue(LandingPadIndex.get(f.space.getServer()).find(f.station.stationId(), entry.reference()).orElseThrow().claim() == null, "Recovery cut retained claim: " + phase + "/" + presence);
            var restoredRecord = RocketTransferSavedData.get(f.space.getServer()).find(transfer).orElse(null);
            helper.assertTrue(report.action().orElseThrow() == RocketTransferRecoveryDecision.decide(phase,
                    new RocketTransferPresence((presence & 1) != 0, (presence & 2) != 0)), "Recovery authority decision changed");
            if (restoredRecord != null) {
                RocketTransferEntities.discardAll(RocketTransferEntities.findMatches(f.space.getServer(), restoredRecord, false));
                RocketTransferEntities.discardAll(RocketTransferEntities.findMatches(f.space.getServer(), restoredRecord, true));
                RocketTransferSavedData.get(f.space.getServer()).remove(transfer);
            }
            source.discard(); destination.discard();
            // Source recovery removes the journal first; remove its rebuilt authority explicitly.
            for (var entity : helper.getLevel().getEntitiesOfClass(RocketEntity.class, source.getBoundingBox().inflate(2, 100, 2))) {
                if (entity.assemblyTransactionId().filter(transit.logicalRocketId()::equals).isPresent()) { entity.discard(); }
            }
            recoveryCut(helper, driver, f, snapshot, entry, cut + 1);
        };
        if (first.status() != RocketTransferRecoveryReport.Status.RETRY_LATER) { finish.run(); return; }
        // W98: a source-side recovery's record is removed only after its survivor proof; its report follows that removal.
        await(helper, driver, helper.getTick() + 40, () -> {
            // await evaluates its predicate again before finishing; the completed report can be taken only once.
            if (reports.size() > 1) { return true; }
            if (!retired(helper, recovery, transfer)) { return false; }
            reports.add(recovery.recover(f.space.getServer(), transfer));
            return true;
        }, finish);
    }

    private static RocketFlightData countdown(RocketEntity source, StationState station, LandingPadEntry entry, UUID transfer) {
        var flight = source.flightData().orElseThrow();
        var plan = new RocketFlightPlan(3, transfer, CelestialIds.EARTH_ID, station.orbitBody(), helperDimension(source),
                CelestialIds.SPACE_LEVEL.location(), station.stationId(), 100, source.level().getGameTime(), new TravelTarget.Station(station.stationId()));
        return flight.withPlan(plan.withLandingPad(entry.reference())).startCountdown(source.level().getGameTime());
    }
    private static net.minecraft.resources.ResourceLocation helperDimension(RocketEntity source) { return source.level().dimension().location(); }
    private static Fixture fixture(GameTestHelper helper) {
        var space = helper.getLevel().getServer().getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space Level unavailable");
        var station = new StationCreationService(new StationPlatformGenerator(), id -> true).create(helper.getLevel().getServer(),
                UUID.randomUUID(), "Pad Test", CelestialIds.EARTH_ID, false).station().orElseThrow();
        var pad = new BlockPos(station.landingPad().x() + 32, station.landingPad().y() - 1, station.landingPad().z());
        var fixture = new Fixture(space, station, pad, new ArrayList<>());
        retainPadChunks(helper, fixture);
        for (int x = -2; x <= 2; x++) { for (int z = -2; z <= 2; z++) {
            var pos = pad.offset(x, 0, z); space.getChunk(pos); space.setBlock(pos, Blocks.GRAY_CONCRETE.defaultBlockState(), Block.UPDATE_ALL);
        } }
        space.setBlock(pad, ModBlocks.LANDING_PAD.get().defaultBlockState(), Block.UPDATE_ALL);
        return fixture;
    }

    private static void retainPadChunks(GameTestHelper helper, Fixture fixture) {
        ServerLevel space = fixture.space;
        UUID ticket = fixture.station.stationId();
        BlockPos pad = fixture.pad;
        var chunks = new ArrayList<ChunkPos>();
        for (int x = (pad.getX() - 2) >> 4; x <= (pad.getX() + 2) >> 4; x++) {
            for (int z = (pad.getZ() - 2) >> 4; z <= (pad.getZ() + 2) >> 4; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        helper.assertTrue(chunks.size() <= 4, "Pad fixture exceeded its four-chunk footprint");
        // getChunk's UNKNOWN ticket lasts only one tick. If entity IO misses that window, the
        // paced wait can observe an unloaded pad forever. Retain FULL, not ENTITY_TICKING: the
        // production transfer still owns its destination ticking tickets and readiness checks.
        // The terminal listener also releases these test-owned tickets after a failed assertion.
        try {
            var field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                private void release() {
                    fixture.releaseFlights();
                    chunks.forEach(chunk -> space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, 0, ticket));
                }
                @Override public void testStructureLoaded(GameTestInfo info) { }
                @Override public void testPassed(GameTestInfo info) { release(); }
                @Override public void testFailed(GameTestInfo info) { release(); }
            });
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pad fixture terminal listener unavailable", failure);
        }
        chunks.forEach(chunk -> space.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 0, ticket));
    }
    private static RocketStructureSnapshot sourceSnapshot(GameTestHelper helper) {
        BlockPos relative = new BlockPos(3, 2, 3); BlockPos origin = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.ROCKET_MOTOR.get()); helper.setBlock(relative.above(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(relative.above(2), ModBlocks.GUIDANCE_COMPUTER.get()); helper.setBlock(relative.east(), ModBlocks.ROCKET_FUEL_TANK.get());
        var task = new RocketStructureScanTask(new ServerLevelRocketScanWorld(helper.getLevel(), RocketBlockEntityAdapters.defaults()),
                helper.getLevel().dimension().location(), new RocketPosition(origin.getX(), origin.getY(), origin.getZ()), UUID.randomUUID(), helper.getLevel().getGameTime());
        var result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        for (int i = 0; i < 64 && result.status() == io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult.Status.RUNNING; i++) {
            result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        }
        helper.assertTrue(result.snapshot().isPresent(), "Legal source scan failed: " + result.issues());
        var snapshot = result.snapshot().orElseThrow();
        for (var block : snapshot.blocks()) { var pos = snapshot.sourceOrigin().add(block.position()); helper.getLevel().removeBlock(new BlockPos(pos.x(), pos.y(), pos.z()), false); }
        return snapshot;
    }
    private static RocketEntity addSource(GameTestHelper helper, RocketStructureSnapshot snapshot, UUID owner) {
        var rocket = ModEntities.ROCKET.get().create(helper.getLevel()); helper.assertTrue(rocket != null, "Source create failed");
        rocket.initialize(snapshot, UUID.randomUUID(), owner);
        var flight = rocket.flightData().orElseThrow(); rocket.updateFlightData(flight.withFuel(flight.fuel().fill(flight.fuel().capacity()).state(), helper.getLevel().getGameTime()));
        helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Source spawn failed"); return rocket;
    }
    private static boolean ready(ServerLevel level, RocketPosition position) { return level.areEntitiesLoaded(ChunkPos.asLong(position.x() >> 4, position.z() >> 4)); }
    private static void await(GameTestHelper helper, GameTestTickDriver driver, long deadline, BooleanSupplier ready, Runnable done) {
        await(helper, driver, deadline, ready, done, new GameTestTickPacer(helper.getLevel().getServer()));
    }
    private static void await(GameTestHelper helper, GameTestTickDriver driver, long deadline, BooleanSupplier ready, Runnable done, GameTestTickPacer pacer) {
        if (ready.getAsBoolean() || helper.getTick() >= deadline) { helper.assertTrue(ready.getAsBoolean(), "Pad fixture/flight did not become ready"); done.run(); return; }
        pacer.pace(); driver.after(1, () -> await(helper, driver, deadline, ready, done, pacer));
    }
    private static void rejected(GameTestHelper helper, Runnable operation) {
        boolean refused = false; try { operation.run(); } catch (IllegalArgumentException expected) { refused = true; }
        helper.assertTrue(refused, "Unauthorized pad operation accepted");
    }
    private static void cleanup(GameTestHelper helper, Fixture f) {
        f.releaseFlights();
        var index = LandingPadIndex.get(f.space.getServer());
        for (var entry : index.entries(f.station.stationId())) { index.remove(entry.stationId(), entry.reference(), entry.physicalId()); }
        for (int x = -2; x <= 2; x++) { for (int z = -2; z <= 2; z++) { f.space.removeBlock(f.pad.offset(x, 0, z), false); } }
        StationRegistrySavedData.get(f.space.getServer()).delete(f.station.stationId());
        new StationPlatformGenerator().removeTemplate(f.space, f.station.cell());
    }
}
