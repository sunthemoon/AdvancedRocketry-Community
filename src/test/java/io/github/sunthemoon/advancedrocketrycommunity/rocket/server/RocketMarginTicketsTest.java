package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W90 (R21 items 1 and 2): margin tickets, their release and the re-expressed readiness predicate. */
class RocketMarginTicketsTest {
    private static final ResourceLocation EARTH = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation MOON = ResourceLocation.tryParse("advancedrocketrycommunity:moon");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static RocketStructureSnapshot snapshot(ResourceLocation level, int x, int z, int... relativeX) {
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        var blocks = new ArrayList<RocketBlock>();
        for (int dx : relativeX) { blocks.add(new RocketBlock(new RocketPosition(dx, 0, 0), iron)); }
        return RocketStructureSnapshot.create(UUID.randomUUID(), level, new RocketPosition(x, 80, z), blocks, List.of(),
                new RocketStats(blocks.size(), 100, 1_000, 2_000, 1, 0, 1, 0), 0);
    }

    /** A DistanceManager stand-in: tickets are equal by Level, column and key, and removing an absent one does nothing. */
    static class Tickets implements RocketTransferEntities.FlightTickets {
        final Set<ResourceLocation> levels;
        final Map<ResourceLocation, Map<ChunkPos, Set<UUID>>> held = new HashMap<>();
        int adds, removes;
        Tickets(ResourceLocation... levels) { this.levels = Set.of(levels); }
        @Override public boolean present(ResourceLocation dimension) { return levels.contains(dimension); }
        @Override public void add(ResourceLocation dimension, ChunkPos column, UUID transfer) {
            assertTrue(present(dimension), "Ticket added to an absent Level"); adds++;
            held.computeIfAbsent(dimension, ignored -> new HashMap<>()).computeIfAbsent(column, ignored -> new HashSet<>()).add(transfer);
        }
        @Override public void remove(ResourceLocation dimension, ChunkPos column, UUID transfer) {
            assertTrue(present(dimension), "Ticket removed from an absent Level"); removes++;
            var columns = held.get(dimension); if (columns == null || !columns.containsKey(column)) { return; }
            columns.get(column).remove(transfer);
            if (columns.get(column).isEmpty()) { columns.remove(column); }
        }
        int count(UUID transfer) {
            return (int) held.values().stream().flatMap(columns -> columns.values().stream()).filter(keys -> keys.contains(transfer)).count();
        }
        Set<ChunkPos> columns(ResourceLocation level, UUID transfer) {
            var found = new HashSet<ChunkPos>();
            held.getOrDefault(level, Map.of()).forEach((column, keys) -> { if (keys.contains(transfer)) { found.add(column); } });
            return found;
        }
    }

    @Test void borderCrossingEndsTicketEightColumnsInTheirOwnLevelsAndReleaseThemAll() {
        var transfer = UUID.randomUUID(); var tickets = new Tickets(EARTH, MOON);
        var source = snapshot(EARTH, 15, 15, 0); var destination = snapshot(MOON, 31, 31, 0);
        assertEquals(8, RocketTransferEntities.refreshMargins(transfer, source, destination, tickets));
        assertEquals(Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(0, 1), new ChunkPos(1, 1)), tickets.columns(EARTH, transfer));
        assertEquals(Set.of(new ChunkPos(1, 1), new ChunkPos(2, 1), new ChunkPos(1, 2), new ChunkPos(2, 2)), tickets.columns(MOON, transfer));
        RocketTransferEntities.releaseMargins(transfer, source, destination, tickets);
        assertEquals(0, tickets.count(transfer)); assertTrue(tickets.held.values().stream().allMatch(Map::isEmpty));
    }

    @Test void overlappingEndsInOneLevelShareColumnsAndAnotherTransferKeepsItsOwnTickets() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(); var tickets = new Tickets(EARTH);
        var source = snapshot(EARTH, 15, 8, 0); var destination = snapshot(EARTH, 31, 8, 0);
        // Source columns 0..1, destination 1..2: the shared column 1 is one ticket of T.
        assertEquals(3, RocketTransferEntities.refreshMargins(first, source, destination, tickets));
        assertEquals(3, tickets.adds);
        assertEquals(3, RocketTransferEntities.refreshMargins(second, snapshot(EARTH, 31, 8, 0), snapshot(EARTH, 47, 8, 0), tickets));
        RocketTransferEntities.releaseMargins(first, source, destination, tickets);
        assertEquals(0, tickets.count(first));
        assertEquals(Set.of(new ChunkPos(1, 0), new ChunkPos(2, 0), new ChunkPos(3, 0)), tickets.columns(EARTH, second),
                "Releasing T removed another transfer's ticket on a shared column");
    }

    @Test void repeatedRefreshesAndRepeatedReleasesAreIdempotent() {
        var transfer = UUID.randomUUID(); var tickets = new Tickets(EARTH, MOON);
        var source = snapshot(EARTH, 15, 15, 0); var destination = snapshot(MOON, 8, 8, 0);
        for (int visit = 0; visit < 5; visit++) { assertEquals(5, RocketTransferEntities.refreshMargins(transfer, source, destination, tickets)); }
        assertEquals(5, tickets.count(transfer));
        for (int release = 0; release < 3; release++) { RocketTransferEntities.releaseMargins(transfer, source, destination, tickets); }
        assertEquals(0, tickets.count(transfer));
    }

    @Test void anAbsentLevelGetsNoCallsTheOtherEndIsStillReleasedAndNothingIsRefreshed() {
        var transfer = UUID.randomUUID(); var both = new Tickets(EARTH, MOON);
        var source = snapshot(EARTH, 15, 15, 0); var destination = snapshot(MOON, 15, 15, 0);
        RocketTransferEntities.refreshMargins(transfer, source, destination, both);
        var earthOnly = new Tickets(EARTH); earthOnly.held.putAll(both.held);
        RocketTransferEntities.releaseMargins(transfer, source, destination, earthOnly);
        assertTrue(earthOnly.columns(EARTH, transfer).isEmpty(), "The present end was not released");
        assertEquals(0, RocketTransferEntities.refreshMargins(transfer, source, destination, new Tickets(EARTH)),
                "A transfer with an absent Level was half-ticketed");
    }

    @Test void anInvalidOrOutsideOriginEndGetsNoMarginTicketAndReleasesOnlyItsOrigin() {
        var transfer = UUID.randomUUID();
        var valid = snapshot(EARTH, 15, 15, 0);
        // 1 x 17: x from 2 spans 1..256; outside origin: x 5..6 from 15 misses column 0.
        var wide = snapshot(MOON, 2, 8, 0, 252); var outside = snapshot(MOON, 15, 8, 5, 6);
        for (var invalid : List.of(wide, outside)) {
            var tickets = new Tickets(EARTH, MOON);
            assertEquals(0, RocketTransferEntities.refreshMargins(transfer, valid, invalid, tickets));
            assertEquals(0, RocketTransferEntities.refreshMargins(transfer, invalid, valid, tickets));
            assertEquals(0, tickets.adds, "A recorded invalid end received a margin ticket");
            var removed = new ArrayList<String>();
            var recording = new Tickets(EARTH, MOON) {
                @Override public void remove(ResourceLocation dimension, ChunkPos column, UUID key) {
                    removed.add(dimension + "@" + column); super.remove(dimension, column, key);
                }
            };
            RocketTransferEntities.releaseMargins(transfer, valid, invalid, recording);
            var origin = new ChunkPos(invalid.sourceOrigin().x() >> 4, invalid.sourceOrigin().z() >> 4);
            assertEquals(4, removed.stream().filter(entry -> entry.startsWith(EARTH.toString())).count());
            assertEquals(List.of(MOON + "@" + origin), removed.stream().filter(entry -> entry.startsWith(MOON.toString())).toList(),
                    "An invalid end released more than its origin column");
        }
    }

    @Test void theReExpressedPredicateEqualsThePreW90FormulaOnRandomAndEdgeRegions() {
        var random = new Random(9_043L);
        var snapshots = new ArrayList<RocketStructureSnapshot>();
        for (int i = 0; i < 4_000; i++) {
            int x = random.nextInt(2_000) - 1_000, z = random.nextInt(2_000) - 1_000;
            int width = 1 + random.nextInt(i % 7 == 0 ? 300 : 40);
            snapshots.add(width == 1 ? snapshot(EARTH, x, z, 0) : snapshot(EARTH, x, z, 0, width - 1));
        }
        for (int x : new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 17, -1, 0, 15, 16,
                Integer.MAX_VALUE - 300, Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
            snapshots.add(snapshot(EARTH, x, 8, 0)); snapshots.add(snapshot(EARTH, 8, x, 0));
        }
        snapshots.add(snapshot(EARTH, Integer.MAX_VALUE - 252, 8, -252, 0));
        int checked = 0;
        for (var snapshot : snapshots) {
            for (int salt : new int[] {0, 1, 2, 7}) {
                BiPredicate<Integer, Integer> ready = (cx, cz) -> salt == 0 || Math.floorMod(cx * 31 + cz * 17 + salt, 13) != 0;
                assertEquals(preW90(snapshot, ready), RocketTransferEntities.marginReady(snapshot, ready), snapshot.sourceOrigin().toString());
                checked++;
            }
        }
        assertTrue(checked > 16_000);
    }

    /** The predicate as it was before W90 (RocketTransferEntities at 379525ea). */
    private static boolean preW90(RocketStructureSnapshot snapshot, BiPredicate<Integer, Integer> ready) {
        var region = RocketRegion.fromSnapshot(snapshot);
        int minX = (region.minimum().x() - 1) >> 4, maxX = (region.maximum().x() + 2) >> 4;
        int minZ = (region.minimum().z() - 1) >> 4, maxZ = (region.maximum().z() + 2) >> 4;
        return RocketTransferEntities.columnsAlreadyLoaded(minX, maxX, minZ, maxZ, ready);
    }
}
