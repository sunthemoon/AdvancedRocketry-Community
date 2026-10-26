package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTestFixtures.snapshot;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketVisualSynchronizerTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void cargoLoadAndUnloadEachTriggerOnePublicationEvenWhenGeometryIsUnchanged() {
        var before = snapshot(FluidStack.EMPTY);
        var delta = new EscrowCargoPayload(new EscrowCargoPayload.Cursor(before.snapshotId(), 0, 0, 0,
                PressurizedTankRocketAdapter.ID.toString()), new FluidStack(Fluids.WATER, 1000).writeToNBT(new CompoundTag()));
        var loaded = RocketCargoMutation.apply(before, delta, true);
        var unloaded = RocketCargoMutation.apply(loaded, delta, false);
        assertEquals(before.structureDigest(), loaded.structureDigest());
        assertNotEquals(before.contentHash(), loaded.contentHash());
        assertEquals(before.contentHash(), unloaded.contentHash());
        var sent = new AtomicInteger();
        RocketVisualSynchronizer.publishIfChanged(before, loaded, sent::incrementAndGet);
        assertEquals(1, sent.get());
        RocketVisualSynchronizer.publishIfChanged(loaded, unloaded, sent::incrementAndGet);
        assertEquals(2, sent.get(), "Returning to an earlier hash is still a new publication");
    }

    @Test void identicalAndReconstructedUnchangedSnapshotsDoNotPublish() {
        var before = snapshot(FluidStack.EMPTY);
        var decoded = RocketSnapshotNbtCodec.decode(RocketSnapshotNbtCodec.encode(before)).snapshot().orElseThrow();
        assertNotSame(before, decoded);
        Runnable unexpected = () -> fail("An unchanged snapshot must not send a visual packet");
        RocketVisualSynchronizer.publishIfChanged(before, before, unexpected);
        RocketVisualSynchronizer.publishIfChanged(before, decoded, unexpected);
    }

}
