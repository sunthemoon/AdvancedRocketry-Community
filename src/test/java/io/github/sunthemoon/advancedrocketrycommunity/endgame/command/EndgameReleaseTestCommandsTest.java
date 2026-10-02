package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRootCodec;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The C13 flush benchmark's synthetic roots decode, so their flush timings are of loadable roots. */
final class EndgameReleaseTestCommandsTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theSyntheticReferenceAndMaximumRootsDecode() {
        EndgameRoot reference = EndgameReleaseTestCommands.synthetic(1024, 1024, 256, 8);
        assertEquals(1024, reference.endpoints().size());
        assertEquals(1024, reference.settledTombstones().size());
        assertEquals(256, reference.transits().size());
        assertEquals(8, reference.pairs().size());
        assertTrue(EndgameSavedData.load(EndgameRootCodec.encode(reference, new CompoundTag())).operational());
        EndgameRoot maximum = EndgameReleaseTestCommands.synthetic(2048, 8192, 256, 1024);
        assertEquals(256, maximum.zones().size());
        assertTrue(EndgameSavedData.load(EndgameRootCodec.encode(maximum, new CompoundTag())).operational());
    }
}
