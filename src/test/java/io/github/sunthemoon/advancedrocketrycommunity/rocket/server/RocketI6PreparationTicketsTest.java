package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketI6PreparationTicketsTest {
    private static final ResourceLocation EARTH = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation MOON = ResourceLocation.tryParse("advancedrocketrycommunity:moon");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void destinationPreparationUsesTheCheckedRecordedMarginsInsteadOfOriginOnlyTickets() throws Exception {
        var source = Files.readString(Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketDestinationRetirement.java"));
        var method = source.substring(source.indexOf("@Override public void keepLoaded("), source.indexOf("@Override public java.util.function.Consumer"));
        assertTrue(method.contains("RocketTransferEntities.refreshMargins(record.transferId(), record.sourceSnapshot(),")
                && method.contains("record.destinationSnapshot(), RocketTransferEntities.levelTickets(server)"),
                "Preparation must use exactly the recorded checked discovery columns");
        assertFalse(method.contains("refreshOriginTicket"), "An origin-only refresh cannot ready a crossing footprint");
    }

    @Test void nativeFixtureRequiresFourSourceColumnsAndOneDestinationColumn() {
        var source = RocketMarginTicketsTest.snapshot(EARTH, 384, 384, -1, 0, 1);
        var destination = RocketMarginTicketsTest.snapshot(MOON, 8, 8, -1, 0, 1);
        var tickets = new RocketMarginTicketsTest.Tickets(EARTH, MOON); var key = UUID.randomUUID();
        assertEquals(5, RocketTransferEntities.refreshMargins(key, source, destination, tickets));
        assertEquals(4, tickets.columns(EARTH, key).size());
        assertEquals(1, tickets.columns(MOON, key).size());
        for (var snapshot : List.of(source, destination)) {
            assertTrue(RocketTransferEntities.marginReady(snapshot,
                    (x, z) -> tickets.columns(snapshot.sourceDimension(), key).contains(new net.minecraft.world.level.ChunkPos(x, z))));
        }
        RocketTransferEntities.releaseMargins(key, source, destination, tickets);
        assertEquals(0, tickets.count(key));
    }

    @Test void largestAcceptedEndsUseThirtyTwoTicketsWithoutIncreasingTheExistingColumnBound() {
        var source = maximum(EARTH); var destination = maximum(MOON);
        assertEquals(16, RocketFlightLimits.MAX_LANDING_CHUNKS);
        assertEquals(RocketFlightLimits.MAX_LANDING_CHUNKS, RocketDiscoveryColumns.of(source).count());
        var tickets = new RocketMarginTicketsTest.Tickets(EARTH, MOON); var key = UUID.randomUUID();
        assertEquals(32, RocketTransferEntities.refreshMargins(key, source, destination, tickets));
        assertEquals(32, tickets.count(key));
        assertEquals(32, RocketTransferEntities.refreshMargins(key, source, destination, tickets));
        assertEquals(32, tickets.count(key), "Refresh must not accumulate tickets");
        RocketTransferEntities.releaseMargins(key, source, destination, tickets);
        assertEquals(0, tickets.count(key));
    }

    private static RocketStructureSnapshot maximum(ResourceLocation dimension) {
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), iron),
                new RocketBlock(new RocketPosition(46, 0, 46), iron));
        return RocketStructureSnapshot.create(UUID.randomUUID(), dimension, new RocketPosition(1, 100, 1), blocks,
                List.of(), new RocketStats(2, 200, 1_000, 1_000, 1, 0, 1, 0), 0);
    }
}
