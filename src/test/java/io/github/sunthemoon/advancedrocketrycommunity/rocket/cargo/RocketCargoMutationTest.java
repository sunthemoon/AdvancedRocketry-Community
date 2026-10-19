package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RocketCargoMutationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(ints = {1, 749, 1000})
    void bothDirectionsPreserveIdentityGeometryPropulsionAndOtherBlocks(int amount) {
        for (boolean load : new boolean[] {true, false}) {
            var before = snapshot(new FluidStack(Fluids.WATER, 2000), new FluidStack(Fluids.LAVA, 8));
            var encodedBefore = RocketSnapshotNbtCodec.encode(before);
            var delta = delta(before, 0, new FluidStack(Fluids.WATER, amount));
            var after = RocketCargoMutation.apply(before, delta, load);
            assertEquals(2000 + (load ? amount : -amount), tank(after, 0).getAmount());
            assertEquals(before.snapshotId(), after.snapshotId());
            assertEquals(before.sourceDimension(), after.sourceDimension());
            assertEquals(before.sourceOrigin(), after.sourceOrigin());
            assertEquals(before.createdAtGameTime(), after.createdAtGameTime());
            assertEquals(before.bounds(), after.bounds()); assertEquals(before.stats(), after.stats());
            assertEquals(before.passengerAnchors(), after.passengerAnchors()); assertEquals(before.propulsion(), after.propulsion());
            assertEquals(before.structureDigest(), after.structureDigest()); assertNotEquals(before.contentHash(), after.contentHash());
            for (var block : before.blocks()) {
                if (block.position().x() != 0 || block.position().y() != 0) {
                    assertSame(block, after.blocks().stream().filter(b -> b.position().equals(block.position())).findFirst().orElseThrow());
                }
            }
            assertEquals(RocketSnapshotNbtCodec.encode(after), RocketSnapshotNbtCodec.encode(RocketCargoMutation.apply(before, delta, load)));
            assertEquals(encodedBefore, RocketSnapshotNbtCodec.encode(before));
        }
    }

    @Test void frozenReplayDoesNotReinterpretCapacityButRejectsUnderflowOverflowAndIncompatibleFluid() {
        var before = snapshot(new FluidStack(Fluids.WATER, 256_000));
        assertEquals(257_000, tank(RocketCargoMutation.apply(before, delta(before, 0, new FluidStack(Fluids.WATER, 1000)), true), 0).getAmount());
        assertThrows(IllegalArgumentException.class, () -> RocketCargoMutation.apply(before, delta(before, 0, new FluidStack(Fluids.LAVA, 1)), true));
        var small = snapshot(new FluidStack(Fluids.WATER, 1));
        assertThrows(IllegalArgumentException.class, () -> RocketCargoMutation.apply(small, delta(small, 0, new FluidStack(Fluids.WATER, 2)), false));
        var full = snapshot(new FluidStack(Fluids.WATER, Integer.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> RocketCargoMutation.apply(full, delta(full, 0, new FluidStack(Fluids.WATER, 1)), true));
    }

    @Test void cursorAndUnknownFluidNeverRetargetOrBecomeEmpty() {
        var before = snapshot(new FluidStack(Fluids.WATER, 1));
        var moved = new FluidStack(Fluids.WATER, 1).writeToNBT(new CompoundTag());
        var wrong = new EscrowCargoPayload(new EscrowCargoPayload.Cursor(UUID.randomUUID(), 0, 0, 0,
                PressurizedTankRocketAdapter.ID.toString()), moved);
        assertThrows(IllegalArgumentException.class, () -> RocketCargoMutation.apply(before, wrong, true));
        moved.putString("FluidName", "missing:fluid");
        assertThrows(IllegalArgumentException.class, () -> new EscrowCargoPayload(delta(before, 0, new FluidStack(Fluids.WATER, 1)).cursor(), moved));
        var blocks = new ArrayList<>(before.blocks()); var b = blocks.get(0); var data = b.blockEntityPayload().orElseThrow().data();
        data.getCompound("tank").getCompound("fluid").putString("FluidName", "missing:fluid");
        blocks.set(0, new RocketBlock(b.position(), b.state(), new RocketBlockEntityPayload(PressurizedTankRocketAdapter.ID, data)));
        var unknown = snapshot(blocks);
        assertThrows(IllegalArgumentException.class, () -> RocketCargoMutation.apply(unknown, delta(unknown, 0, new FluidStack(Fluids.WATER, 1)), true));
    }

    @Test void wholeSnapshotOverheadIsNotThePayloadAggregate() {
        var base = snapshot(FluidStack.EMPTY); var blocks = new ArrayList<>(base.blocks());
        for (int i = 1; i <= 4; i++) {
            var data = new CompoundTag(); data.putByteArray("blob", new byte[262_100]);
            blocks.add(new RocketBlock(new RocketPosition(i, 0, 0),
                    new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:chest"), Map.of()),
                    new RocketBlockEntityPayload(net.minecraft.resources.ResourceLocation.tryParse("fixture:host"), data)));
        }
        var before = snapshot(blocks);
        assertTrue(before.blocks().stream().flatMap(b -> b.blockEntityPayload().stream()).mapToInt(RocketBlockEntityPayload::uncompressedBytes).sum() <= 1_048_576);
        assertThrows(IllegalArgumentException.class, () -> RocketCargoMutation.apply(before, delta(before, 0, new FluidStack(Fluids.WATER, 1)), true));
    }
}
