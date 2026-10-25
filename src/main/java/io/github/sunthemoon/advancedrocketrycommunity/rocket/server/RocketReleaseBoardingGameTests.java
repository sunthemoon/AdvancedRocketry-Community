package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real boarding, mount events, native entity saves and checked release acknowledgements (W158-R2). */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketReleaseBoardingGameTests {
    private RocketReleaseBoardingGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_release_boarding", timeoutTicks = 400)
    public static void sameSeatBoardsOnlyAfterS2AndSurvivesReloadWithoutRevivingOldCopies(GameTestHelper helper) {
        new Boarding(helper).start();
    }

    private static final class Boarding {
        private static final TicketType<UUID> TICKET = TicketType.create("arce_w158_boarding", java.util.Comparator.<UUID>naturalOrder());
        final GameTestHelper helper;
        final ServerLevel level;
        final MinecraftServer server;
        final GameTestTickPacer pacer;
        final UUID first = UUID.randomUUID(), second = UUID.randomUUID(), retained = UUID.randomUUID(), ticket = UUID.randomUUID();
        final RocketFlightService intents;
        final RocketTransferService transfers;
        final RocketTransferSavedData journal;
        RocketRejoinSavedData originalSidecar, sidecar;
        RocketReleaseAccess.Owner originalOwner;
        RocketSourceRetirementFixtures.Flight fixture;
        RocketTransferRecord record;
        RocketEntity rocket, stale;
        ServerPlayer retainedPlayer, firstPlayer;
        FakePlayer stranger;
        Path sidecarFile;
        ChunkPos originSlot, otherSlot;
        BlockPos origin, other;
        UUID release;
        RocketRejoinRoot.Resources resources;
        CompoundTag reloadedImage;
        final java.util.Set<ChunkPos> savedSlots = new java.util.HashSet<>(), savingSlots = new java.util.HashSet<>();
        String saveFailure;
        int phase, ticks, seat;
        boolean closed, installed, departed;
        boolean b1Captured, captureFailed;
        java.util.concurrent.atomic.AtomicLong b1Generation;
        long capturedGeneration;

        Boarding(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel(); server = level.getServer();
            pacer = new GameTestTickPacer(server); transfers = RocketRuntime.installedTransfers();
            journal = RocketTransferSavedData.get(server);
            try {
                var service = RocketRuntime.class.getDeclaredField("service"); service.setAccessible(true);
                intents = RocketSourceRetirementFixtures.field(service.get(null), "flights");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        void start() { helper.onEachTick(this::tick); }
        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++ticks < 380, "Release boarding fixture exceeded its bounded wait at phase " + phase);
                helper.assertTrue(saveFailure == null, "Native fixture save failed: " + saveFailure);
                switch (phase) {
                    case 0 -> setup();
                    case 1 -> saveOldCopies();
                    case 2 -> admit();
                    case 3 -> awaitS2();
                    case 4 -> saveNewAssignment();
                    case 5 -> reload();
                    case 6 -> awaitPlacement();
                    default -> throw new IllegalStateException("Unknown boarding phase");
                }
                if (!closed) { pacer.pace(); }
            } catch (IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void setup() throws IOException {
            origin = helper.absolutePos(new BlockPos(3, 2, 3)); other = origin.offset(32, 0, 0);
            originSlot = new ChunkPos(origin); otherSlot = new ChunkPos(other);
            level.getChunkSource().addRegionTicket(TICKET, otherSlot, 2, ticket);
            level.getChunk(otherSlot.x, otherSlot.z);
            if (!level.areEntitiesLoaded(originSlot.toLong()) || !level.areEntitiesLoaded(otherSlot.toLong())) { return; }
            // Own only an initially absent sidecar, and restore that absence after the test. Keep its actual world
            // filename so a vanilla autosave cannot try to rebind a detached writer. Other journal entries stay intact.
            originalSidecar = RocketRejoinEvents.get(server);
            var file = ManagedRootSupplier.dataFile(server, RocketRejoinSavedData.TYPE);
            helper.assertTrue(originalSidecar.root().equals(RocketRejoinRoot.empty()) && !Files.exists(file),
                    "Boarding fixture requires an unused sidecar; existing release state must not be replaced");
            sidecarFile = file;
            originalOwner = RocketSourceRetirementFixtures.field(transfers, "releases");
            sidecar = RocketRejoinSavedData.open(sidecarFile);
            installSidecar(); installed = true;
            retainedPlayer = FirstEventGameTestPlayers.join(server, retained, "W158Retained", level, origin);
            fixture = RocketSourceRetirementFixtures.countdown(level, origin, other, List.of(first, second, retained), 0);
            record = fixture.record(); journal.put(record);
            RocketSourceRetirementFixtures.settled(transfers).add(record.transferId());
            rocket = fixture.spawn(helper, level, RocketTransferEntities.stationarySource(record, level.getGameTime()), false);
            firstPlayer = new FakePlayer(level, new GameProfile(first, "W158Returning"));
            stranger = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "W158NewSeat"));
            helper.assertTrue(retainedPlayer.startRiding(rocket, true), "Retained passenger did not mount before release");
            seat = rocket.flightData().orElseThrow().passengers().assignment(first).orElseThrow().seatIndex();
            resources = RocketReleasePlan.resources(rocket.snapshot().orElseThrow(), rocket.flightData().orElseThrow());
            // A discarded old destination image remains in its recorded native slot until B1 proves that slot clean.
            stale = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(stale != null, "No stale rocket type");
            stale.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), record.ownerId(),
                    record.destinationFlightData().land(level.getGameTime()));
            RocketTransferEntities.positionAtOrigin(stale, record.destinationSnapshot().sourceOrigin());
            helper.assertTrue(level.addFreshEntity(stale), "Old recorded copy was not published");
            phase = 1;
        }
        void saveOldCopies() {
            if (!journal.retryChecked(server)) { return; }
            saveSlot(originSlot, rocket.getUUID()); saveSlot(otherSlot, stale.getUUID());
            if (!savedSlots.containsAll(List.of(originSlot, otherSlot))) { return; }
            var old = RocketSourceRetirementFixtures.diskImage(level, otherSlot, stale.getUUID());
            var source = RocketSourceRetirementFixtures.diskImage(level, originSlot, rocket.getUUID());
            if (old == null || source == null) { return; }
            helper.assertTrue(hasSeat(old, first) && hasSeat(source, first), "Old copies did not persist the released seat");
            stale.discard(); phase = 2;
        }
        void admit() {
            if (!journal.actionable(record.logicalRocketId())) { return; }
            EscrowRuntime.of(server).monitor().observerForTests(server, observation -> {
                if (observation.slot().entities() && observation.slot().chunk() == originSlot.toLong()) {
                    if (!b1Captured && observation.cause().equals("CAPTURE") && release != null
                            && transfers.releaseOwner(server).coordinator.stage(release).equals("B1")) {
                        b1Captured = true; b1Generation = observation.counter(); capturedGeneration = observation.generation();
                    }
                }
            });
            release = RocketRuntime.releaseAbsentForTests(server, record.transferId()).orElseThrow();
            helper.assertTrue(RocketRuntime.releaseFenced(rocket), "Admission did not hold the release fence");
            var before = rocket.flightData().orElseThrow().passengers();
            helper.assertTrue(intent(stranger, RocketFlightAction.BOARD) == RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED,
                    "Fence admitted a new assignment");
            helper.assertTrue(intent(firstPlayer, RocketFlightAction.BOARD) == RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED,
                    "Fence admitted the released rider");
            helper.assertTrue(rocket.flightData().orElseThrow().passengers().equals(before) && stranger.getVehicle() == null,
                    "Rejected boarding changed the manifest or mounted the player");
            helper.assertTrue(!firstPlayer.startRiding(rocket, false) && !firstPlayer.startRiding(rocket, true),
                    "Normal or forced mounting bypassed the release fence");
            phase = 3;
        }
        void leaveUnderFence() {
            helper.assertTrue(RocketRuntime.releaseFenced(rocket), "Retained rider left outside the release fence");
            helper.assertTrue(intent(retainedPlayer, RocketFlightAction.LEAVE) == RocketFlightRequestCode.SUCCESS
                    && retainedPlayer.getVehicle() == null
                    && rocket.flightData().orElseThrow().passengers().assignment(retained).isEmpty(),
                    "The fence prevented a non-released passenger from leaving");
            // Their now-offline seat in MT keeps a scrubbed T, exercising both persistent seat sources after S2.
            server.getPlayerList().remove(retainedPlayer); retainedPlayer = null;
            departed = true;
        }
        void awaitS2() {
            // B0 proves the frozen ME exactly. Exercise permitted departure after that proof, while B1 is running.
            if (!departed && b1Captured) {
                var work = ((java.util.Map<?, ?>) RocketSourceRetirementFixtures.field(transfers.releaseOwner(server).coordinator, "work")).get(release);
                helper.assertTrue((boolean) RocketSourceRetirementFixtures.field(work, "capturing"), "Departure missed the pending B1 capture");
                leaveUnderFence();
                helper.assertTrue(b1Generation.get() == capturedGeneration, "Departure alone invalidated the native capture");
                // W80 permits this changed manifest. A newer native save, however, must invalidate B1.
                // Explicitly supersede the pending capture instead of depending on incidental entity saves.
                helper.assertTrue(level.entityManager.storeChunkSections(originSlot.toLong(), entity -> { }), "Departure save was refused");
                helper.assertTrue(b1Generation.get() > capturedGeneration, "Departure save did not supersede the B1 generation");
            }
            var work = ((java.util.Map<?, ?>) RocketSourceRetirementFixtures.field(transfers.releaseOwner(server).coordinator, "work")).get(release);
            if (work != null && "CAPTURE_FAILED".equals(RocketSourceRetirementFixtures.field(work, "notice"))) { captureFailed = true; }
            if (RocketRuntime.releaseFenced(rocket)) { return; }
            helper.assertTrue(departed, "S2 completed before the retained-rider departure was exercised");
            helper.assertTrue(captureFailed, "The superseded B1 capture was not rejected before retrying");
            helper.assertTrue(sidecar.acknowledgedCurrent() && sidecar.acknowledgedRoot().entries().stream()
                    .filter(e -> e.release().equals(release)).allMatch(e -> e.seat() == RocketRejoinRoot.Seat.VERIFIED),
                    "Boarding reopened before the checked S2 acknowledgement");
            var batch = sidecar.root().batches().stream().filter(b -> b.release().equals(release)).findFirst().orElseThrow();
            helper.assertTrue(batch.released().containsAll(List.of(first, second)) && !batch.released().contains(retained)
                    && batch.slots().size() == 2, "Wrong released set or persisted slot census");
            helper.assertTrue(rocket.flightData().orElseThrow().passengers().assignments().isEmpty(), "E1 restored the departed rider");
            var image = RocketSourceRetirementFixtures.diskImage(level, originSlot, rocket.getUUID());
            helper.assertTrue(image != null && !hasSeat(image, first) && !hasSeat(image, second),
                    "S2 did not revoke the authority's saved pre-release seats");
            assertOldSourcesClean();
            helper.assertTrue(intent(firstPlayer, RocketFlightAction.BOARD) == RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED,
                    "S2 admitted an AP passenger before placement proof");
            // W159: the same released passenger now completes P1, vanilla save, installed readback and P2.
            // The fixture supplies terrain, not equipment, outside the recorded footprint.
            var safe = new BlockPos(batch.minimum().x() + (batch.maximum().x() - batch.minimum().x()) / 2,
                    batch.maximum().y() + 8, batch.minimum().z() - 1);
            for (int x = -1; x <= 1; x++) { for (int z = -1; z <= 1; z++) { for (int y = -1; y <= 2; y++) {
                level.setBlockAndUpdate(safe.offset(x, y, z), (y == -1 ? net.minecraft.world.level.block.Blocks.STONE
                        : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState());
            } } }
            firstPlayer = FirstEventGameTestPlayers.join(server, first, "W159Returning", level, origin);
            helper.assertTrue(RocketRejoinProtection.guarded(firstPlayer), "Returning AP passenger was not guarded");
            phase = 6;
        }
        void awaitPlacement() {
            if (RocketRejoinProtection.guarded(firstPlayer)) { return; }
            helper.assertTrue(sidecar.acknowledgedRoot().entries().stream().noneMatch(e -> e.player().equals(first)),
                    "Guard ended before checked P2 retired the seat-verified entry");
            helper.assertTrue(!firstPlayer.getPersistentData().contains("arce_passenger_rejoin") && firstPlayer.getVehicle() == null,
                    "Verified placement retained its live marker or vehicle");
            var file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(first + ".dat");
            try (var input = Files.newInputStream(file)) {
                var saved = net.minecraft.nbt.NbtIo.readCompressed(input);
                helper.assertTrue(saved.hasUUID("UUID") && saved.getUUID("UUID").equals(first)
                        && saved.getFloat("Health") > 0 && !saved.contains("RootVehicle")
                        && saved.getCompound("ForgeData").getCompound("arce_passenger_rejoin").hasUUID("generation"),
                        "P2 had no installed healthy, unmounted marker-bearing player file");
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            helper.assertTrue(intent(firstPlayer, RocketFlightAction.BOARD) == RocketFlightRequestCode.SUCCESS,
                    "Post-P2 real boarding was refused");
            assertNewAssignment();
            savedSlots.remove(originSlot);
            phase = 4;
        }
        void saveNewAssignment() {
            saveSlot(originSlot, rocket.getUUID());
            if (!savedSlots.contains(originSlot)) { return; }
            var image = RocketSourceRetirementFixtures.diskImage(level, originSlot, rocket.getUUID());
            if (image == null || !hasSeat(image, first)) { return; }
            reloadedImage = image;
            assertOldSourcesClean();
            firstPlayer.stopRiding(); rocket.discard();
            transfers.releaseOwner(server).close(); sidecar.close();
            sidecar = RocketRejoinSavedData.open(sidecarFile);
            installSidecar(); phase = 5;
        }
        void reload() {
            var restored = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(restored != null, "No reload rocket type"); restored.load(reloadedImage);
            rocket = restored;
            helper.assertTrue(level.addFreshEntity(restored), "Saved entity could not rejoin the Level");
            helper.assertTrue(!RocketRuntime.releaseFenced(restored), "Reloaded VERIFIED seats restored the fence");
            helper.assertTrue(intent(firstPlayer, RocketFlightAction.BOARD) == RocketFlightRequestCode.ALREADY_BOARDED,
                    "Reload lost the replacement assignment before mounting");
            assertNewAssignment(); assertOldSourcesClean();
            helper.assertTrue(!hasSeat(reloadedImage, second), "Reload revived the other released passenger");
            helper.assertTrue(sidecar.root().entries().stream().filter(e -> e.release().equals(release))
                    .allMatch(e -> e.seat() == RocketRejoinRoot.Seat.VERIFIED && e.placement() == RocketRejoinRoot.Placement.PENDING),
                    "Reload changed release-only placement obligations");
            close(); helper.succeed();
        }
        void assertNewAssignment() {
            helper.assertTrue(rocket.flightData().orElseThrow().passengers().assignment(first).orElseThrow().seatIndex() == seat
                    && firstPlayer.getVehicle() == rocket, "Same-player/same-seat assignment did not actually mount");
            helper.assertTrue(resources.equals(RocketReleasePlan.resources(rocket.snapshot().orElseThrow(), rocket.flightData().orElseThrow())),
                    "Boarding or reload changed snapshot/fuel/debit resources");
        }
        void assertOldSourcesClean() {
            helper.assertTrue(RocketSourceRetirementFixtures.diskCopies(level, otherSlot, record.logicalRocketId()) == 0,
                    "Old recorded destination copy survived B1 or returned after boarding");
            var installed = CheckedSavedDataFile.readPayload(ManagedRootSupplier.dataFile(server, ManagedSavedDataType.ROCKET_TRANSFERS),
                    ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow();
            var saved = RocketTransferSavedData.load(installed).find(record.transferId()).orElseThrow();
            for (var flight : List.of(saved.sourceFlightData(), saved.destinationFlightData())) {
                helper.assertTrue(flight.passengers().assignment(first).isEmpty() && flight.passengers().assignment(second).isEmpty()
                        && flight.passengers().assignment(retained).isPresent(), "Reloaded T revived a released seat or lost its remaining rider");
            }
        }
        RocketFlightRequestCode intent(ServerPlayer player, RocketFlightAction action) {
            player.setPos(rocket.position());
            return intents.request(player, rocket.getId(), action, new TravelTarget.BodySurface(CelestialIds.EARTH_ID), UUID.randomUUID()).code();
        }
        static boolean hasSeat(CompoundTag entity, UUID player) {
            return entity.getCompound("RocketEntityData").getCompound("flight_data").getCompound("passengers")
                    .getList("assignments", 10).stream().map(CompoundTag.class::cast)
                    .anyMatch(seat -> seat.hasUUID("passenger_id") && seat.getUUID("passenger_id").equals(player));
        }
        void saveSlot(ChunkPos slot, UUID entity) {
            if (savedSlots.contains(slot) || savingSlots.contains(slot)) { return; }
            var runtime = EscrowRuntime.of(server); var store = runtime.store(level);
            if (!runtime.barrier().availableOrdinary(store, level.dimension().location().toString(), slot, server.getTickCount())) { return; }
            savingSlots.add(slot);
            boolean admitted = runtime.barrier().beginOrdinary(store, level.dimension().location().toString(), slot,
                    root -> root.getList("Entities", 10).stream().map(CompoundTag.class::cast)
                            .anyMatch(tag -> tag.hasUUID("UUID") && tag.getUUID("UUID").equals(entity) && hasSeat(tag, first)),
                    server.getTickCount(), label -> { }, result -> {
                        savingSlots.remove(slot);
                        if (result.confirmed()) { savedSlots.add(slot); }
                        else { saveFailure = slot.toString(); }
                        if (result.proof() != null) { result.proof().release(); }
                    });
            if (!admitted) { savingSlots.remove(slot); }
        }
        void installSidecar() {
            level.getServer().overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), sidecar);
            setOwner(null);
        }
        void setOwner(RocketReleaseAccess.Owner owner) {
            try { var field = RocketTransferService.class.getDeclaredField("releases"); field.setAccessible(true); field.set(transfers, owner); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        void close() {
            if (closed) { return; } closed = true;
            EscrowRuntime.of(server).monitor().observerForTests(server, null);
            if (firstPlayer != null) { firstPlayer.stopRiding(); }
            if (firstPlayer != null && firstPlayer.connection != null) { server.getPlayerList().remove(firstPlayer); }
            if (stranger != null) { stranger.stopRiding(); }
            if (retainedPlayer != null) { retainedPlayer.stopRiding(); server.getPlayerList().remove(retainedPlayer); }
            if (rocket != null) { rocket.discard(); } if (stale != null) { stale.discard(); }
            if (installed) {
                var owner = (RocketReleaseAccess.Owner) RocketSourceRetirementFixtures.field(transfers, "releases");
                if (owner != null) { owner.close(); } sidecar.close();
                server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), originalSidecar); setOwner(originalOwner);
            }
            if (record != null) {
                journal.remove(record.transferId());
                RocketSourceRetirementFixtures.settled(transfers).remove(record.transferId());
                RocketTransferEntities.releaseFlightTickets(server, record);
            }
            if (otherSlot != null) { level.getChunkSource().removeRegionTicket(TICKET, otherSlot, 2, ticket); }
            if (sidecarFile != null) {
                try { Files.deleteIfExists(sidecarFile); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
    }
}
