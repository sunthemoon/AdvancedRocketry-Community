package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterHarness.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * W129bc native cut seams are inert in production: without the release-test property on an empty dedicated server
 * every action refuses, nothing is held, captures carry no cut and writes use the unwrapped policy.
 */
@Timeout(60)
final class SatelliteRegistryWriterCutsTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void withoutTheReleaseTestPropertyEveryActionRefusesAndWritesAreUnchanged() {
        var server = live(); var data = registry(server);
        var cuts = data.cuts();
        assertFalse(SatelliteRegistryWriterCuts.enabled(server));
        assertThrows(IllegalStateException.class, () -> cuts.arm(server, SatelliteRegistryWriterCuts.Point.STAGED,
                SatelliteRegistryWriterCuts.Mode.PAUSE));
        assertThrows(IllegalStateException.class, () -> cuts.release(server));
        assertThrows(IllegalStateException.class, () -> cuts.holdEffects(server, true));
        assertThrows(IllegalStateException.class, () -> cuts.holdDiscovery(server, true));
        assertThrows(IllegalStateException.class, () -> cuts.crashAtNextTick(server, () -> { }, true));
        assertThrows(IllegalStateException.class, () -> cuts.atStopping(server, () -> { }));
        assertThrows(IllegalStateException.class, () -> cuts.status(server, data));
        assertFalse(cuts.effectsHeld()); assertFalse(cuts.discoveryHeld());
        assertSame(ATOMIC, cuts.policy(ATOMIC), "An unarmed seam wrapped the write policy");
        assertNull(cuts.beforeEncode(1L), "An unarmed seam put a cut in the capture");
        launch(data);
        assertNull(data.captureForTests().cut(), "A production capture carried a cut");
        cuts.onTickStart(data); cuts.onStopping(data.mutationSequence()); // unarmed hooks do nothing and do not throw
        Path file = directory.resolve(REGISTRY.fileName());
        write(server, data, file, ATOMIC);
        assertEquals(data.mutationSequence(), data.acknowledgement().orElseThrow().sequence());
        assertEquals(data.save(new net.minecraft.nbt.CompoundTag()), reload(file).save(new net.minecraft.nbt.CompoundTag()));
        data.close();
    }
}
