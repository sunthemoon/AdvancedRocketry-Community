package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptCommitCut;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W185 A1: actual queued D7 guard, a returning assigned passenger and late joins at both ends; W185-R2's in-session source
 * end wider than its origin column. Each B16 cut scenario has its own batch, because the cut pauses every in-session guard.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayCopiesGameTests {
    private BayCopiesGameTests() { }

    /** W185-R4: the held login is retained, neither routed nor settled, and the standing passenger is never pulled back. */
    @GameTest(template = TEMPLATE, batch = "bay_copies_login", timeoutTicks = 1_200)
    public static void loginAndCopiesDuringQueuedRetirementInvalidateItWithoutSettlement(GameTestHelper helper) {
        new Scenario(helper, false).start();
    }

    /** W185-R4: only a passenger riding a recognized copy that will be removed is moved, into the exact survivor. */
    @GameTest(template = TEMPLATE, batch = "bay_copies_riding", timeoutTicks = 1_200)
    public static void aPassengerRidingARemovedCopyIsMovedIntoTheSurvivor(GameTestHelper helper) {
        new Scenario(helper, true).start();
    }

    /** W185-R2: an in-session claim tickets its whole source end from CONSUME on, so D6 cannot wait for ever. */
    @GameTest(template = TEMPLATE, batch = "bay_copies_wide", timeoutTicks = 1_200)
    public static void anInSessionSourceEndWiderThanItsOriginColumnFinishes(GameTestHelper helper) {
        new WideSource(helper).start();
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final boolean riding;
        final UUID owner = UUID.randomUUID(), passenger = UUID.randomUUID();
        final GameTestTickPacer pacer;
        final List<RocketEntity> copies = new ArrayList<>();
        Flight flight;
        ItemStack pkg;
        BayClaim claim;
        BayReceiptCommitCut cut;
        BayDeploymentCuts previousCuts;
        BooleanSupplier token;
        ServerPlayer player;
        int stage, waits;
        boolean installed, closed;

        Scenario(GameTestHelper helper, boolean riding) {
            this.helper = helper; this.riding = riding; pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }
        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 1_150, "Copy/login fixture exceeded its wait at " + stage);
                if (stage == 0) { setup(); }
                else if (stage == 1) { whileGuardQueued(); }
                else { finished(); }
                if (!closed) { pacer.pace(); }
            } catch (ReflectiveOperationException failure) { close(); throw new IllegalStateException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void setup() throws ReflectiveOperationException {
            var level = helper.getLevel(); var server = level.getServer();
            var origin = helper.absolutePos(new BlockPos(3, 2, 3)); var target = helper.absolutePos(new BlockPos(14, 2, 3));
            if (!level.isPositionEntityTicking(origin) || !level.isPositionEntityTicking(target)) { return; }
            pkg = solarPackage(owner); registerSolar(server, pkg, owner);
            flight = transit(helper, origin, level.dimension().location(), position(target), owner, List.of(pkg),
                    RocketEscrowSectionPatch.NEUTRAL, RocketFlightPlanner.EARTH.bodyId(), passenger);
            var journal = RocketTransferSavedData.get(server); journal.put(flight.record());
            // Use the existing B16 guard cut, only inside this isolated GameTest batch; no new production hook.
            cut = EscrowRuntime.of(server).bayReceipts(server).commitCutForTests(server).orElseThrow();
            var constructor = BayDeploymentCuts.class.getDeclaredConstructor(BayDeploymentCuts.Cut.class, BayReceiptCommitCut.class);
            constructor.setAccessible(true);
            var field = BayTransferRecovery.class.getDeclaredField("cuts"); field.setAccessible(true);
            previousCuts = (BayDeploymentCuts) field.get(bays(helper));
            field.set(bays(helper), constructor.newInstance(BayDeploymentCuts.Cut.B16_GUARD, cut)); installed = true;
            bays(helper).ascentComplete(server, journal, flight.record(), flight.source());
            claim = claim(helper, flight.record().logicalRocketId()).orElseThrow(); stage = 1;
        }

        void whileGuardQueued() throws ReflectiveOperationException {
            var field = BayReceiptCommitCut.class.getDeclaredField("guardPaused"); field.setAccessible(true);
            if (!((AtomicBoolean) field.get(cut)).get()) { return; }
            var server = helper.getLevel().getServer(); var journal = RocketTransferSavedData.get(server);
            var record = journal.find(flight.record().transferId()).orElseThrow();
            helper.assertTrue(claim.stage() == BayClaim.Stage.RETIRE && record.phase() == RocketTransferPhase.PREPARED,
                    "The actual D7 guard was not queued on a PREPARED record");
            token = claim.group().orElseThrow().token(); helper.assertTrue(token.getAsBoolean(), "Initial token invalid");
            player = FirstEventGameTestPlayers.join(server, passenger, "W185Passenger", helper.getLevel(),
                    helper.absolutePos(new BlockPos(3, 2, 6)));
            RocketSourceRetirementFixtures.recovery(RocketRuntime.installedTransfers()).onPlayerLoggedIn(player, journal);
            // W185-R4 (Root's scope): the held login stays queued for its 200 ticks; it moves nobody and proves nothing stale.
            helper.assertTrue(player.getVehicle() == null && token.getAsBoolean() && queued(player),
                    "The held login was routed, invalidated the group, or was not retained");
            helper.assertTrue(journal.find(record.transferId()).orElse(null) == record
                    && record.sourceFlightData().passengers().assignment(passenger).isPresent()
                    && record.destinationFlightData().passengers().assignment(passenger).isPresent(), "Login settled or cleared the seat");
            for (boolean destination : new boolean[] {false, true}) {
                var copy = ModEntities.ROCKET.get().create(helper.getLevel());
                // A copy that already carried the passenger, as before the claim held L: the mount precedes L's binding.
                if (riding && !destination) { helper.assertTrue(player.startRiding(copy, true), "The passenger could not ride the copy"); }
                copy.initializeTransferred(destination ? flight.record().destinationSnapshot() : flight.record().sourceSnapshot(),
                        record.logicalRocketId(), owner, destination ? record.destinationFlightData() : record.sourceFlightData());
                helper.assertTrue(helper.getLevel().addFreshEntity(copy), "Late copy not published"); copies.add(copy);
            }
            helper.assertTrue(!token.getAsBoolean(), "Late copies did not invalidate the queued token");
            helper.assertTrue(!riding || player.getVehicle() == copies.get(0), "The fixture passenger is not riding the source copy");
            helper.assertTrue(bays(helper).sessionForTests().find(satellite(pkg)).isPresent(), "Queued removal lost receipt");
            cut.release(); stage = 2;
        }

        void finished() {
            var server = helper.getLevel().getServer(); var journal = RocketTransferSavedData.get(server);
            if (claim(helper, flight.record().logicalRocketId()).isPresent()) {
                helper.assertTrue(journal.find(flight.record().transferId()).orElseThrow().phase() == RocketTransferPhase.PREPARED,
                        "Held login settled the transfer"); return;
            }
            helper.assertTrue(claim.handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE, "Not READY_SOURCE");
            helper.assertTrue(!token.getAsBoolean() && copies.size() == 2 && copies.stream().allMatch(RocketEntity::isRemoved),
                    "Queued token or duplicate removal was not enforced");
            helper.assertTrue(copies.get(0).snapshot().orElseThrow().contentHash().equals(claim.group().orElseThrow().srcAfter())
                    && copies.get(1).snapshot().orElseThrow().contentHash().equals(claim.group().orElseThrow().dstAfter()),
                    "Late copies were removed without their own consumed image");
            helper.assertTrue(bays(helper).sessionForTests().find(satellite(pkg)).isEmpty(), "Receipt not retired");
            helper.assertTrue(riding ? player.getVehicle() == flight.source() : player.getVehicle() == null,
                    riding ? "The removed copy's passenger was not moved into the survivor" : "The standing passenger was pulled back");
            close(); helper.succeed();
        }

        /** Whether the installed transfer service still holds this player's login in its bounded reconnect queue. */
        boolean queued(ServerPlayer player) {
            Object queue = RocketSourceRetirementFixtures.field(RocketSourceRetirementFixtures.recovery(RocketRuntime.installedTransfers()),
                    "reconnects");
            java.util.Map<UUID, Long> waiting = RocketSourceRetirementFixtures.field(queue, "waiting");
            return waiting.containsKey(player.getUUID());
        }

        void close() {
            if (closed) { return; } closed = true;
            if (cut != null) { cut.release(); }
            if (installed) {
                try { var field = BayTransferRecovery.class.getDeclaredField("cuts"); field.setAccessible(true); field.set(bays(helper), previousCuts); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }
            var server = helper.getLevel().getServer();
            if (player != null) { player.stopRiding(); server.getPlayerList().remove(player); }
            if (flight == null) { return; }
            bays(helper).forgetForTests(flight.record().logicalRocketId());
            var journal = RocketTransferSavedData.get(server);
            journal.find(flight.record().transferId()).ifPresent(record -> {
                record.destinationEntityId().map(helper.getLevel()::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
                journal.remove(record.transferId());
            });
            flight.source().discard(); copies.forEach(RocketEntity::discard);
            RocketTransferEntities.releaseFlightTickets(server, flight.record());
            bays(helper).sessionForTests().find(satellite(pkg)).ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), ignored -> { }));
            SatelliteMissionSavedData.get(server).decommission(satellite(pkg), owner, true, true);
        }
    }

    /**
     * W185-R2: the source end is two columns wide, far from every other fixture, and nothing but this flight's tickets
     * loads it. Before the claim, the fixture renews only the origin ticket, as the in-session claim itself does; the low
     * column is then loaded but not entity-ticking, so D6 can finish only once the claim tickets the whole source end.
     */
    private static final class WideSource {
        /** x is a multiple of 16, so the checked set's low edge, origin x - 2, lies in the column west of the origin. */
        static final BlockPos ORIGIN = new BlockPos(750_000, 100, 750_008);
        final GameTestHelper helper;
        final GameTestTickPacer pacer;
        final UUID owner = UUID.randomUUID();
        Flight flight;
        ItemStack pkg;
        BayClaim claim;
        ChunkPos origin, low;
        int stage, waits;
        boolean closed, lowTicking;

        WideSource(GameTestHelper helper) { this.helper = helper; pacer = new GameTestTickPacer(helper.getLevel().getServer()); }
        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 1_150, "Wide-source fixture exceeded its wait at " + stage
                        + (claim == null ? "" : " " + claim.stage() + " " + claim.reason()));
                if (stage == 0) { publish(); } else if (stage == 1) { begin(); } else { finished(); }
                if (!closed) { pacer.pace(); }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void publish() {
            var level = helper.getLevel(); var server = level.getServer();
            var target = helper.absolutePos(new BlockPos(14, 2, 3));
            if (!level.isPositionEntityTicking(target)) { return; }
            pkg = solarPackage(owner); registerSolar(server, pkg, owner);
            flight = transit(helper, ORIGIN, level.dimension().location(), position(target), owner, List.of(pkg),
                    RocketEscrowSectionPatch.NEUTRAL, RocketFlightPlanner.EARTH.bodyId(), null);
            var columns = RocketDiscoveryColumns.of(flight.record().sourceSnapshot());
            origin = columns.origin(); low = new ChunkPos(columns.minColumnX(), columns.minColumnZ());
            helper.assertTrue(columns.admissible() && columns.count() == 2 && !low.equals(origin),
                    "The source end is not two columns wide: " + columns);
            stage = 1;
        }

        void begin() {
            var level = helper.getLevel(); var server = level.getServer();
            RocketTransferEntities.refreshOriginTicket(level, flight.record().sourceSnapshot(), flight.record().transferId());
            if (level.getEntity(flight.source().getUUID()) != flight.source()
                    || !RocketTransferEntities.columnReady(level, origin.x, origin.z, ORIGIN.getY())) { return; }
            helper.assertTrue(!RocketTransferEntities.columnReady(level, low.x, low.z, ORIGIN.getY()),
                    "control: the origin ticket alone already made the low column entity-ticking");
            var journal = RocketTransferSavedData.get(server); journal.put(flight.record());
            bays(helper).ascentComplete(server, journal, flight.record(), flight.source());
            claim = claim(helper, flight.record().logicalRocketId()).orElseThrow(); stage = 2;
        }

        void finished() {
            if (RocketTransferEntities.columnReady(helper.getLevel(), low.x, low.z, ORIGIN.getY())) { lowTicking = true; }
            if (claim(helper, flight.record().logicalRocketId()).isPresent()) { return; }
            helper.assertTrue(claim.handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE, "Not READY_SOURCE: " + claim.reason());
            helper.assertTrue(lowTicking, "The claim never made the source end's low column entity-ticking");
            helper.assertTrue(bays(helper).sessionForTests().find(satellite(pkg)).isEmpty(), "Receipt not retired");
            close(); helper.succeed();
        }

        void close() {
            if (closed) { return; } closed = true;
            if (flight == null) { return; }
            var server = helper.getLevel().getServer();
            bays(helper).forgetForTests(flight.record().logicalRocketId());
            var journal = RocketTransferSavedData.get(server);
            journal.find(flight.record().transferId()).ifPresent(record -> {
                record.destinationEntityId().map(helper.getLevel()::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
                journal.remove(record.transferId());
            });
            flight.source().discard();
            RocketTransferEntities.releaseFlightTickets(server, flight.record());
            bays(helper).sessionForTests().find(satellite(pkg)).ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), ignored -> { }));
            SatelliteMissionSavedData.get(server).decommission(satellite(pkg), owner, true, true);
        }
    }
}
