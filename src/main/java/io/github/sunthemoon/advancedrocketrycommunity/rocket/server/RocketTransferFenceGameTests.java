package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real loaded-index/readiness adapters, with isolated immutable ledger input and no global test authority. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketTransferFenceGameTests {
    private RocketTransferFenceGameTests() { }

    @GameTest(template = "rocket_test", batch = "escrow_transfer_fence", timeoutTicks = 1_300)
    public static void startupJoinAndOperatorRetriesNeverTicketUnloadedOrUnavailableEnds(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var unloaded = record(false); var unavailable = record(true);
        var journal = new RocketTransferSavedData(); journal.put(unloaded); journal.put(unavailable);
        var ledger = new EscrowLedger(UUID.randomUUID(), 1, 1, List.of(), List.of(floor(unloaded), floor(unavailable)), List.of());
        var fences = new RocketTransferFences(ignored -> ledger);
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        var ticketingChecks = new AtomicInteger();
        // Startup classification happens with neither end loaded, including a Level that cannot exist.
        fences.classify(server, journal, live, settled);
        helper.assertTrue(fences.ids().size() == 2, "Startup left a protected record unclassified");
        assertUnavailable(helper, fences, unloaded, unavailable);
        RocketEntity joined = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(joined != null, "No join fixture");
        joined.initializeTransferred(unloaded.sourceSnapshot(), unloaded.logicalRocketId(), unloaded.ownerId(), unloaded.sourceFlightData());
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)); joined.setPos(pos.getX(), pos.getY(), pos.getZ());
        helper.assertTrue(helper.getLevel().addFreshEntity(joined), "Join fixture was not published");
        fences.joined(server, unloaded.logicalRocketId(), true, journal, live, settled);
        for (int tick = 1; tick <= 1_201; tick++) {
            helper.runAfterDelay(tick, () -> {
                for (int command = 0; command < 8; command++) {
                    helper.assertTrue(!fences.permit(server, unloaded, live, settled), "Unloaded proof promoted a record");
                    helper.assertTrue(!fences.permit(server, unavailable, live, settled), "Unavailable Level promoted a record");
                }
                fences.classify(server, journal, live, settled);
                RocketTransferRecoveryService.step(journal.entries(), live, settled, fences.ids(), 0,
                        record -> { ticketingChecks.incrementAndGet(); return true; });
                assertUnavailable(helper, fences, unloaded, unavailable);
            });
        }
        helper.runAfterDelay(1_202, () -> {
            helper.assertTrue(ticketingChecks.get() == 0, "A fenced record reached the ticketing readiness predicate");
            joined.discard(); helper.succeed();
        });
    }

    private static void assertUnavailable(GameTestHelper helper, RocketTransferFences fences, RocketTransferRecord... records) {
        for (var record : records) {
            helper.assertTrue(fences.ids().contains(record.transferId()), "Retry removed FENCED classification");
            helper.assertTrue(flightTickets(helper.getLevel().getServer(), record.transferId()) == 0, "Recovery added a flight ticket");
            for (var snapshot : List.of(record.sourceSnapshot(), record.destinationSnapshot())) {
                var level = RocketTransferEntities.level(helper.getLevel().getServer(), snapshot.sourceDimension());
                var origin = snapshot.sourceOrigin();
                if (level != null) {
                    helper.assertTrue(!level.hasChunk(origin.x() >> 4, origin.z() >> 4)
                            && !level.areEntitiesLoaded(ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4)), "Recovery loaded a remote end");
                }
            }
        }
        var unrelated = new RocketStationMotionRule.Region(ModIdentity.id("space"), 0, 0, 511, 511);
        helper.assertTrue(!RocketStationMotionRule.blocks(true, List.of(records), fences.ids()::contains,
                fences.ids()::contains, helper.getLevel().getGameTime(), unrelated), "Fenced retry blocked an unrelated station warp");
    }

    /** Test-only inspection by field type survives mapped and packaged names without expanding the production AT. */
    static int flightTickets(MinecraftServer server, UUID id) {
        int count = 0;
        try {
            for (var level : server.getAllLevels()) {
                DistanceManager distance = null;
                for (var field : ServerChunkCache.class.getDeclaredFields()) {
                    if (DistanceManager.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true); distance = (DistanceManager) field.get(level.getChunkSource()); break;
                    }
                }
                if (distance == null) { throw new IllegalStateException("No ticket manager"); }
                for (var field : DistanceManager.class.getDeclaredFields()) {
                    if (!it.unimi.dsi.fastutil.longs.Long2ObjectMap.class.isAssignableFrom(field.getType())) { continue; }
                    field.setAccessible(true);
                    var map = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>) field.get(distance);
                    for (var value : map.values()) {
                        if (!(value instanceof Iterable<?> tickets)) { continue; }
                        for (var ticket : tickets) {
                            if (ticket instanceof Ticket<?> && ticket.toString().contains("arce_rocket_flight")
                                    && ticket.toString().contains(id.toString())) { count++; }
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Cannot inspect recovery tickets", exception); }
        return count;
    }

    private static CompoundTag floor(RocketTransferRecord record) {
        var tag = new CompoundTag(); tag.putInt("schema_version", 1); tag.putUUID("rocket_id", record.logicalRocketId());
        tag.putUUID("transfer_id", record.transferId()); tag.putLong("journal_generation", 1); tag.putString("journal_digest", record.checksum());
        tag.putUUID("entity_id", UUID.randomUUID()); tag.putLong("resource_epoch", 1);
        tag.putString("signature", record.checksum()); tag.putLong("highest_seq", 1); return tag;
    }

    private static RocketTransferRecord record(boolean unavailable) {
        var sourceOrigin = new RocketPosition(12_000_000, 72, 12_000_000);
        var destinationOrigin = new RocketPosition(12_000_128, 80, 12_000_128);
        var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())),
                new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
        var stats = new RocketStats(2, 100L, 1_000L, 1_000L, 1, 1, 1, 0);
        var dimension = unavailable ? ModIdentity.id("unavailable_fence_test") : RocketFlightPlanner.MOON.dimensionId();
        var source = RocketStructureSnapshot.create(UUID.randomUUID(), RocketFlightPlanner.EARTH.dimensionId(), sourceOrigin,
                blocks, List.of(new RocketPosition(0, 1, 0)), stats, 0);
        var destination = source.relocated(UUID.randomUUID(), dimension, destinationOrigin, 160);
        var fuel = RocketFuelState.empty(1_000).fill(1_000).state();
        UUID id = UUID.randomUUID(), logical = UUID.randomUUID();
        var plan = new RocketFlightPlan(3, id, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), dimension, 500, 0);
        var flight = RocketFlightData.initial(logical, 1_000, 1, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(), sourceOrigin, 0)
                .withFuel(fuel, 0).withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(id, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(id, 500).state(), RocketFlightPlanner.MOON.bodyId(), dimension, destinationOrigin, 160);
        return RocketTransferRecord.create(id, logical, UUID.randomUUID(), UUID.randomUUID(), source, destination, flight, arrival, 500, 0);
    }
}
