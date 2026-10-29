package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Installed player entry, unlike the isolated historical Design M fixtures. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketI6DisassemblyGameTests {
    private static final List<Scenario> RUNNING = new ArrayList<>();
    private RocketI6DisassemblyGameTests() { }

    @GameTest(template = "rocket_test", batch = "w164_m0", timeoutTicks = 1200)
    public static void playerRequestKeepsTUntilProofAndPublishesOnlyAfterB2(GameTestHelper helper) { start(helper, true); }
    @GameTest(template = "rocket_test", batch = "w164_no_t", timeoutTicks = 1200)
    public static void standalonePlayerRequestUsesRetainedNativeCompletion(GameTestHelper helper) { start(helper, false); }
    @GameTest(template = "rocket_test", batch = "w164_cold", timeoutTicks = 1200)
    public static void coldDemandOnlyPreparesAndNeedsANewFuelOffer(GameTestHelper helper) {
        var scenario = new Scenario(helper, true, true); RUNNING.add(scenario); helper.onEachTick(scenario::tick);
    }
    @GameTest(template = "rocket_test", batch = "w164_cold_border", timeoutTicks = 1200)
    public static void coldBorderFootprintPreparesAllDiscoveryColumns(GameTestHelper helper) {
        var scenario = new Scenario(helper, true, true, true); RUNNING.add(scenario); helper.onEachTick(scenario::tick);
    }
    private static void start(GameTestHelper helper, boolean predecessor) {
        var scenario = new Scenario(helper, predecessor); RUNNING.add(scenario); helper.onEachTick(scenario::tick);
    }
    @AfterBatch(batch = "w164_m0") public static void cleanup(ServerLevel level) { RUNNING.forEach(Scenario::close); RUNNING.clear(); }
    @AfterBatch(batch = "w164_no_t") public static void cleanupStandalone(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "w164_cold") public static void cleanupCold(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "w164_cold_border") public static void cleanupColdBorder(ServerLevel level) { cleanup(level); }

    private static final class Scenario {
        final GameTestHelper helper;
        final ServerLevel source, destination;
        final GameTestTickPacer pacer;
        final boolean predecessor, cold, border;
        final BlockPos at;
        final RocketI6ColdColumns observation = new RocketI6ColdColumns();
        RocketSourceRetirementFixtures.Flight fixture;
        RocketTransferRecord record;
        RocketEntity rocket;
        RocketLandedDisassemblyGameTestSupport.Requester player;
        boolean forced, closed;
        int step;
        Scenario(GameTestHelper helper, boolean predecessor) {
            this(helper, predecessor, false);
        }
        Scenario(GameTestHelper helper, boolean predecessor, boolean cold) {
            this(helper, predecessor, cold, false);
        }
        Scenario(GameTestHelper helper, boolean predecessor, boolean cold, boolean border) {
            this.helper = helper; this.predecessor = predecessor; this.cold = cold; this.border = border; source = helper.getLevel();
            destination = source.getServer().getLevel(CelestialIds.MOON_LEVEL);
            at = new BlockPos(border ? 891272 : cold ? 890520 : predecessor ? 890264 : 890392, 100, 890008);
            pacer = new GameTestTickPacer(source.getServer());
        }
        void tick() {
            if (closed) { return; }
            pacer.pace(); var server = source.getServer(); var journal = RocketTransferSavedData.get(server);
            if (step == 0) {
                if (!journal.retryChecked(server)) { return; }
                fixture = RocketSourceRetirementFixtures.countdown(source,
                        border ? new BlockPos(891008, 100, 890000)
                                : cold ? new BlockPos(890648, 100, 890008) : helper.absolutePos(new BlockPos(3, 2, 3)),
                        destination, at, List.of(), 0);
                if (border) { fixture = RocketI6ColdColumns.crossingFootprint(fixture); }
                if (!cold) { RocketTransferEntities.keepLoaded(source, fixture.record().sourceSnapshot(), fixture.record().transferId()); }
                RocketTransferEntities.keepLoaded(destination, fixture.record().destinationSnapshot(), fixture.record().transferId());
                forced = !destination.getForcedChunks().contains(fixture.destinationSlot().toLong());
                if (forced) { destination.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true); }
                step++; return;
            }
            if (step == 1) {
                if (!destination.areEntitiesLoaded(fixture.destinationSlot().toLong())
                        || !cold && !RocketTransferEntities.recoveryEndsAlreadyLoaded(server, fixture.record())) { return; }
                rocket = fixture.spawnDestination(helper, destination);
                rocket.updateFlightData(rocket.flightData().orElseThrow().land(destination.getGameTime()));
                RocketTransferEntities.positionAtOrigin(rocket, fixture.record().destinationSnapshot().sourceOrigin());
                record = fixture.record().destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                        .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
                if (predecessor) {
                    journal.put(record);
                    // This fixture represents a completed arrival, not an unclassified restart. In particular, cold
                    // setup must not let restart recovery load S before the first player demand.
                    RocketSourceRetirementFixtures.settled(RocketRuntime.installedTransfers()).add(record.transferId());
                    journal.flushChecked(server);
                }
                player = new RocketLandedDisassemblyGameTestSupport.Requester(destination, record.ownerId());
                player.setPos(rocket.getX() + 2, rocket.getY(), rocket.getZ());
                step++; return;
            }
            if (step == 2) {
                if (!RocketRuntime.resourceMutationAllowed(rocket)) { return; }
                if (cold) {
                    helper.assertTrue(!source.areEntitiesLoaded(fixture.originSlot().toLong()), "Cold fixture origin was already loaded");
                    if (border) { observation.observe(helper, fixture.record(), "before-demand"); }
                }
                RocketRuntime.requestDisassembly(player, rocket);
                if (cold) {
                    helper.assertTrue(player.token == null && player.offers == 0, "Preparation issued destructive consent");
                    helper.assertTrue(player.messages.contains("message.advancedrocketrycommunity.rocket.disassembly_preparing"),
                            "Preparation did not report its distinct pending answer");
                    helper.assertTrue(journal.find(record.transferId()).orElse(null) == record && rocket.isAlive(),
                            "Preparation changed T or D");
                    step = 20; return;
                }
                helper.assertTrue(player.token != null, "Player did not receive a fresh fuel quote");
                step++; return;
            }
            if (step == 20) {
                if (border) { observation.observe(helper, record, "preparing"); }
                helper.assertTrue(journal.find(record.transferId()).orElse(null) == record && rocket.isAlive()
                        && player.token == null && player.discarded == 0, "Preparation consumed a destructive request");
                if (!RocketRuntime.resourceMutationAllowed(rocket)) { return; }
                helper.assertTrue(RocketTransferEntities.recoveryEndsAlreadyLoaded(server, record), "Preparation did not ready both ends");
                if (border) { observation.observe(helper, record, "ready"); }
                RocketRuntime.requestDisassembly(player, rocket);
                helper.assertTrue(player.token != null && player.offers == 1, "Fresh request did not offer fresh consent");
                step = 3; return;
            }
            if (step == 3) {
                helper.assertTrue(server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),
                        "arce rocket-disassembly confirm " + player.token) == 1, "Confirmed request was refused");
                helper.assertTrue(rocket.isAlive(), "Player entry restored synchronously instead of admitting B2");
                helper.assertTrue(predecessor == journal.find(record.transferId()).isPresent(), "M0 removed T before proof");
                helper.assertTrue(player.discarded == 0, "Pending request published fuel disposal");
                step++; return;
            }
            if (rocket.isAlive()) { helper.assertTrue(player.discarded == 0, "Live D published fuel disposal"); return; }
            for (var block : fixture.record().destinationSnapshot().blocks()) {
                if (!destination.getBlockState(at.offset(block.position().x(), block.position().y(), block.position().z()))
                        .is(Blocks.IRON_BLOCK)) { return; }
            }
            helper.assertTrue(journal.findByLogicalRocket(record.logicalRocketId()).isEmpty(), "Completed request retained T");
            var data = RocketTransactionSavedData.get(server);
            helper.assertTrue(data.acknowledgedCurrent() && data.entries().stream().noneMatch(entry ->
                    entry.record().rocketEntityIdOptional().filter(rocket.getUUID()::equals).isPresent()), "Completion preceded checked U absence");
            helper.assertTrue(player.discarded == rocket.flightData().orElseThrow().fuel().totalAmount(), "Disposal differs from consent");
            close(); helper.succeed();
        }
        void close() {
            if (closed) { return; } closed = true;
            if (rocket != null && rocket.isAlive()) { rocket.discard(); }
            if (fixture != null) {
                RocketTransferEntities.releaseFlightTickets(source.getServer(), fixture.record());
                if (forced) { destination.setChunkForced(at.getX() >> 4, at.getZ() >> 4, false); }
            }
            if (record != null) {
                var journal = RocketTransferSavedData.get(source.getServer()); journal.remove(record.transferId()); journal.flushChecked(source.getServer());
            }
            if (fixture != null) {
                for (var block : fixture.record().destinationSnapshot().blocks()) {
                    var pos = at.offset(block.position().x(), block.position().y(), block.position().z());
                    if (destination.getBlockState(pos).is(Blocks.IRON_BLOCK)) { destination.removeBlock(pos, false); }
                }
            }
        }
    }
}
