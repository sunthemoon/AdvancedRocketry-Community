package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-074 item 2 (W129-R6): a captured terminal display position never aliases a caller's mutable position. */
final class TerminalLocationAliasTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aSuppliedMutablePositionIsFrozenAtConstruction() {
        var mutable = new BlockPos.MutableBlockPos(10, 64, -20);
        var location = new MissionPayload.TerminalLocation(ModIdentity.id("terminal_level"), mutable);
        mutable.set(99, 1, 99);
        assertEquals(new BlockPos(10, 64, -20), location.pos());
        assertFalse(location.pos() instanceof BlockPos.MutableBlockPos, "The accessor must not hand out a mutable alias");
        var viaFactory = MissionPayload.TerminalLocation.of(ModIdentity.id("terminal_level"), mutable).orElseThrow();
        mutable.set(-5, 2, 7);
        assertEquals(new BlockPos(99, 1, 99), viaFactory.pos());
        assertEquals(location, new MissionPayload.TerminalLocation(ModIdentity.id("terminal_level"), new BlockPos(10, 64, -20)));
    }
}
