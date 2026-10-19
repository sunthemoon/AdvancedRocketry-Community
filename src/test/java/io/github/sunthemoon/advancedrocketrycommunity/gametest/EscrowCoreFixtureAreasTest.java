package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W111: the escrow_core batch hook prepares exactly the fixture areas its tests hold, and they are separate slots. */
class EscrowCoreFixtureAreasTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test
    void theHookCoversEveryRemoteFixtureChunkOfTheBatch() {
        var expected = List.of(new ChunkPos(33_125, 33_125), new ChunkPos(32_500, 32_500), // joint, own-store
                new ChunkPos(34_125, 34_125), new ChunkPos(34_250, 34_125), // terrain and entity retry
                new ChunkPos(42_563, 42_563), new ChunkPos(42_565, 42_563), new ChunkPos(42_567, 42_563),
                new ChunkPos(42_569, 42_563), new ChunkPos(42_571, 42_563), new ChunkPos(42_573, 42_563)); // reconciliation
        assertEquals(expected, EscrowCoreFixtureAreas.centres());
    }

    @Test
    void injectedRetrySavesUseSlotsNoCoreProofWatches() {
        // EscrowSaveMonitor keys a watch by Level, slot kind and chunk; a save of another chunk cannot supersede it.
        var centres = EscrowCoreFixtureAreas.centres();
        var core = Set.of(centres.get(0), centres.get(1));
        assertTrue(!core.contains(centres.get(2)) && !core.contains(centres.get(3)), "A retry fixture shares a core proof slot");
        assertEquals(centres.size(), new HashSet<>(centres).size(), "Two fixtures share a chunk");
    }

    @Test
    void eachAreaIsTheFullSquareOfTheTestsStartTicket() {
        var centre = new ChunkPos(10, -7);
        var area = EscrowCoreFixtureAreas.area(centre);
        assertEquals(25, new HashSet<>(area).size());
        assertTrue(area.contains(centre) && area.contains(new ChunkPos(8, -9)) && area.contains(new ChunkPos(12, -5)));
        assertTrue(area.stream().allMatch(pos -> Math.abs(pos.x - centre.x) <= 2 && Math.abs(pos.z - centre.z) <= 2));
    }
}
