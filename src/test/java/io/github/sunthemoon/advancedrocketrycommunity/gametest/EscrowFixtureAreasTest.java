package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReconciliationGameTests;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W111: each batch hook prepares exactly the fixture areas its tests hold, and the fixtures are separate slots. */
class EscrowFixtureAreasTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test
    void eachHookCoversEveryRemoteFixtureChunkOfItsBatch() {
        assertEquals(List.of(new ChunkPos(42_563, 42_563), new ChunkPos(42_564, 42_563), // joint, own-store
                new ChunkPos(42_565, 42_563), new ChunkPos(42_566, 42_563), new ChunkPos(42_563, 42_564),
                new ChunkPos(42_564, 42_564), new ChunkPos(42_565, 42_564), new ChunkPos(42_566, 42_564)), // reconciliation
                EscrowFixtureAreas.centres(EscrowFixtureAreas.CORE));
        assertEquals(List.of(new ChunkPos(34_125, 34_125), new ChunkPos(34_250, 34_125)), // terrain and entity retry
                EscrowFixtureAreas.centres(EscrowFixtureAreas.RETRY));
        assertThrows(IllegalArgumentException.class, () -> EscrowFixtureAreas.centres("escrow_other"));
    }

    @Test
    void noTwoFixturesShareASlot() {
        // EscrowSaveMonitor keys a watch by Level, slot kind and chunk; a save of another chunk cannot supersede it.
        var all = new ArrayList<>(EscrowFixtureAreas.centres(EscrowFixtureAreas.CORE));
        all.addAll(EscrowFixtureAreas.centres(EscrowFixtureAreas.RETRY));
        assertEquals(all.size(), new HashSet<>(all).size(), "Two fixtures share a chunk");
    }

    @Test
    void eachAreaIsTheFullSquareOfTheTestsStartTicket() {
        var centre = new ChunkPos(10, -7);
        var area = EscrowFixtureAreas.area(centre);
        assertEquals(25, new HashSet<>(area).size());
        assertTrue(area.contains(centre) && area.contains(new ChunkPos(8, -9)) && area.contains(new ChunkPos(12, -5)));
        assertTrue(area.stream().allMatch(pos -> Math.abs(pos.x - centre.x) <= 2 && Math.abs(pos.z - centre.z) <= 2));
    }

    @Test
    void coreSharesOnlyGenerationFringesAndKeepsEveryEndpointInsideItsOwnSlot() {
        var centres = EscrowFixtureAreas.centres(EscrowFixtureAreas.CORE);
        assertEquals(8, centres.size());
        assertEquals(EscrowCoreGameTests.JOINT_FIXTURE, EscrowFixtureAreas.coreFixture(0));
        assertEquals(EscrowCoreGameTests.OWN_FIXTURE, EscrowFixtureAreas.coreFixture(1));
        for (int index = 0; index < centres.size(); index++) {
            var position = EscrowFixtureAreas.coreFixture(index);
            assertEquals(centres.get(index), new ChunkPos(position));
            assertEquals(100, position.getY());
            assertEquals(8, position.getX() & 15);
            assertEquals(8, position.getZ() & 15);
            // The source, the bank two blocks east, the joint source four east and its adjacent cells.
            for (int dx = -1; dx <= 5; dx++) {
                assertEquals(centres.get(index), new ChunkPos(position.offset(dx, 0, 0)));
            }
            if (index >= 2) { assertEquals(position, EscrowReconciliationGameTests.fixture(index - 2)); }
        }
        var full = new HashSet<ChunkPos>();
        centres.forEach(centre -> full.addAll(EscrowFixtureAreas.area(centre)));
        var expected = new HashSet<ChunkPos>();
        for (int x = 42_561; x <= 42_568; x++) {
            for (int z = 42_561; z <= 42_566; z++) { expected.add(new ChunkPos(x, z)); }
        }
        assertEquals(48, expected.size());
        assertEquals(expected, full, "Eight START tickets should share exactly the 8-by-6 FULL rectangle");
    }

    @Test
    void coreAndReconciliationRejectNonexistentFixtureIndices() {
        assertThrows(IllegalArgumentException.class, () -> EscrowFixtureAreas.coreFixture(-1));
        assertThrows(IllegalArgumentException.class, () -> EscrowFixtureAreas.coreFixture(8));
        assertThrows(IllegalArgumentException.class, () -> EscrowReconciliationGameTests.fixture(-1));
        assertThrows(IllegalArgumentException.class, () -> EscrowReconciliationGameTests.fixture(6));
    }

    @Test
    void forcedChunkIsolationIncludesBothFullFringes() {
        var centre = EscrowFixtureAreas.centres(EscrowFixtureAreas.CORE).get(0);
        assertTrue(EscrowFixtureAreas.isolated(centre, new LongOpenHashSet()));
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                assertFalse(EscrowFixtureAreas.isolated(centre,
                        new LongOpenHashSet(new long[]{ChunkPos.asLong(centre.x + dx, centre.z + dz)})));
            }
        }
        assertTrue(EscrowFixtureAreas.isolated(centre,
                new LongOpenHashSet(new long[]{ChunkPos.asLong(centre.x + 5, centre.z)})));
        assertTrue(EscrowFixtureAreas.isolated(centre,
                new LongOpenHashSet(new long[]{ChunkPos.asLong(centre.x, centre.z - 5)})));
    }
}
