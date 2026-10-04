package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PumpReleaseTestCommandsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void preflightIsImmutableBoundedUniqueAndInsideOneFixedChunk() {
        var cells = PumpReleaseTestCommands.emptyCells();
        assertEquals(72, cells.size());
        assertEquals(cells.size(), new HashSet<>(cells).size());
        assertTrue(cells.stream().allMatch(p -> p.getX() >> 4 == 13 && p.getZ() >> 4 == 13));
        assertThrows(UnsupportedOperationException.class, () -> cells.clear());
    }
    @Test void everyCellRequiresAnExplicitValidIndex() {
        assertThrows(IllegalArgumentException.class, () -> PumpReleaseTestCommands.seed(-1));
        assertThrows(IllegalArgumentException.class, () -> PumpReleaseTestCommands.seed(7));
    }
    @Test void worldCommandBlocksNamedServerPlayersAndRconAreNotTheNativeConsole() {
        assertFalse(PumpReleaseTestCommands.consolePermission(false, true, "Server"));
        assertFalse(PumpReleaseTestCommands.consolePermission(true, false, "Server"));
        assertFalse(PumpReleaseTestCommands.consolePermission(true, true, "Rcon"));
        assertFalse(PumpReleaseTestCommands.consolePermission(true, true, null));
        assertFalse(PumpReleaseTestCommands.consolePermission(true, true, "server"));
        assertTrue(PumpReleaseTestCommands.consolePermission(true, true, "Server"));
    }
    @Test void noEnergyDuringCooldownIsNotAStableTerminalReport() {
        for (int cooldown = 1; cooldown <= 10; cooldown++) {
            assertFalse(PumpReleaseTestCommands.terminalReady(PumpCode.NO_ENERGY, cooldown));
        }
        assertFalse(PumpReleaseTestCommands.terminalReady(PumpCode.DRAINED, 0));
        assertFalse(PumpReleaseTestCommands.terminalReady(PumpCode.NO_ENERGY, -1));
        assertTrue(PumpReleaseTestCommands.terminalReady(PumpCode.NO_ENERGY, 0));
    }
    @Test void currentAndTaggedSeedsHaveExactResourcesAndNoOwnerOrSearchFrontier() {
        CompoundTag current = (CompoundTag) PumpReleaseTestCommands.seed(0);
        assertEquals(12_345, current.getCompound("fluid").getInt("Amount"));
        assertEquals(7_654, current.getInt("energy"));
        assertEquals(4, current.size());
        CompoundTag tagged = (CompoundTag) PumpReleaseTestCommands.seed(5);
        assertEquals("native-retained", tagged.getCompound("fluid").getCompound("Tag").getString("batch"));
        assertEquals("minecraft:lava", tagged.getCompound("fluid").getString("FluidName"));
        assertEquals(7_000, tagged.getCompound("fluid").getInt("Amount"));
    }
    @Test void refusedCasesRetainTheirExactOversizeFutureCorruptAndOpaqueInputs() {
        assertEquals(16_001, ((CompoundTag) PumpReleaseTestCommands.seed(1)).getCompound("fluid").getInt("Amount"));
        CompoundTag future = (CompoundTag) PumpReleaseTestCommands.seed(2);
        assertEquals(2, future.getInt("schema"));
        assertEquals("native-future-retained", future.getString("extension"));
        assertEquals(-1, ((CompoundTag) PumpReleaseTestCommands.seed(3)).getCompound("fluid").getInt("Amount"));
        assertEquals(StringTag.valueOf("opaque-pump-root"), PumpReleaseTestCommands.seed(4));
    }
    @Test void partialSearchSeedIsIndependentAndDoesNotSerializeTransientWork() {
        CompoundTag root = (CompoundTag) PumpReleaseTestCommands.seed(6);
        assertEquals(100, root.getInt("energy"));
        assertEquals(PumpReleaseTestCommands.OWNER, root.getUUID("owner"));
        assertTrue(root.getCompound("fluid").isEmpty());
        assertEquals(5, root.size());
        root.putInt("energy", 0);
        assertEquals(100, ((CompoundTag) PumpReleaseTestCommands.seed(6)).getInt("energy"));
    }
}
