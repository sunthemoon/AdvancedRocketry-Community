package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketCargoNestedBoundsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void nativeFluidFitsButFramedCargoFailsBytesDepthAndNodesWithoutTruncation() {
        for (String kind : new String[] {"bytes", "depth", "nodes"}) {
            var fluid = new FluidStack(Fluids.WATER, 1000); var tag = fluid.getOrCreateTag();
            switch (kind) {
                case "bytes" -> tag.putByteArray("blob", new byte[8060]);
                case "depth" -> {
                    var cursor = tag;
                    for (int i = 0; i < 10; i++) { var child = new CompoundTag(); cursor.put("c", child); cursor = child; }
                    cursor.putInt("leaf", 1);
                }
                case "nodes" -> { for (int i = 0; i < 380; i++) { tag.putByte("n" + i, (byte) 1); } }
                default -> throw new AssertionError();
            }
            assertTrue(TankSave.fits(fluid), kind + " must fit the tank, not only its bare tag");
            var before = snapshot(FluidStack.EMPTY); var nativeFluid = fluid.writeToNBT(new CompoundTag());
            assertTrue(BoundedNbt.fits(nativeFluid, 8192, 16, 1024), kind);
            var cursor = new EscrowCargoPayload.Cursor(before.snapshotId(), 0, 0, 0,
                    "advancedrocketrycommunity:pressurized_tank_v1");
            assertThrows(IllegalArgumentException.class, () -> new EscrowCargoPayload(cursor, nativeFluid), kind);
            var original = nativeFluid.copy(); var called = new java.util.concurrent.atomic.AtomicBoolean();
            var result = RocketCargoSelection.select(before, fluid, true, 64_000, d -> {
                called.set(true); return RocketCargoSelection.Status.ELIGIBLE;
            });
            assertEquals(RocketCargoSelection.Status.CARGO_TOO_LARGE, result.status(), kind);
            assertFalse(called.get(), "A framed refusal must precede complete capture or reservation");
            assertEquals(original, fluid.writeToNBT(new CompoundTag()));
            assertTrue(tank(before, 0).isEmpty());
        }
    }

    @Test void nativeFluidAtItsByteCeilingDoesNotImplyTankRootFits() {
        boolean found = false;
        for (int blob = 8050; blob <= 8192; blob++) {
            var fluid = new FluidStack(Fluids.WATER, 1); fluid.getOrCreateTag().putByteArray("b", new byte[blob]);
            var nativeRoot = fluid.writeToNBT(new CompoundTag());
            if (BoundedNbt.fits(nativeRoot, 8192, 16, 1024) && !TankSave.fits(fluid)) {
                found = true; assertThrows(IllegalArgumentException.class, () -> TankSave.encode(fluid)); break;
            }
        }
        assertTrue(found);
    }

    @Test void individuallyBoundedPayloadsCanExceedWholeSnapshotDepthAndNodes() {
        for (boolean depth : new boolean[] {true, false}) {
            var data = new CompoundTag();
            if (depth) {
                var cursor = data;
                for (int i = 0; i < 31; i++) { var child = new CompoundTag(); cursor.put("c", child); cursor = child; }
            } else {
                var nodes = new net.minecraft.nbt.ListTag();
                for (int i = 0; i < 40_000; i++) { nodes.add(net.minecraft.nbt.ByteTag.valueOf((byte) 1)); }
                data.put("nodes", nodes);
            }
            assertTrue(BoundedNbt.fits(data, 262_144, 32, 65_536));
            var blocks = new java.util.ArrayList<>(snapshot(FluidStack.EMPTY).blocks());
            for (int i = 1; i <= 2; i++) {
                blocks.add(new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock(
                        new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(i, 0, 0),
                        new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState(
                                net.minecraft.resources.ResourceLocation.tryParse("fixture:host"), java.util.Map.of()),
                        new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload(
                                net.minecraft.resources.ResourceLocation.tryParse("fixture:host"), data)));
            }
            var before = snapshot(blocks);
            var encoded = io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec.encode(before);
            assertFalse(BoundedNbt.fits(encoded, 1_048_576, 32, 65_536));
            var error = assertThrows(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException.class,
                    () -> RocketCargoMutation.apply(before, delta(before, 0, new FluidStack(Fluids.WATER, 1)), true));
            assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE,
                    error.code());
        }
    }
}
