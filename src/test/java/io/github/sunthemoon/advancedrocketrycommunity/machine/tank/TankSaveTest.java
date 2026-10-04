package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TankSaveTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void roundTripIncludesTagsAndRetainsEveryNonnegativeRepresentableBalance() {
        assertEquals(TankSave.encode(FluidStack.EMPTY), TankSave.encode(TankSave.decode(TankSave.encode(FluidStack.EMPTY))));
        for (int amount : new int[]{1, 64_000, 256_001, Integer.MAX_VALUE}) {
            FluidStack fluid = new FluidStack(Fluids.WATER, amount);
            fluid.setTag(new CompoundTag());
            fluid.getTag().putString("batch", "retained");
            CompoundTag root = TankSave.encode(fluid);
            assertEquals(root, TankSave.encode(TankSave.decode(root)));
            assertEquals(amount, TankSave.decode(root).getAmount());
        }
    }

    @Test void encodedRootDoesNotShareFluidMetadataInEitherDirection() {
        FluidStack fluid = new FluidStack(Fluids.WATER, 1_000);
        fluid.getOrCreateTag().putString("batch", "original");
        CompoundTag root = TankSave.encode(fluid);
        root.getCompound("fluid").getCompound("Tag").putString("batch", "encoded");
        assertEquals("original", fluid.getTag().getString("batch"));
        fluid.getTag().putString("batch", "owner");
        assertEquals("encoded", root.getCompound("fluid").getCompound("Tag").getString("batch"));
    }

    @Test void decodedFluidDoesNotShareCallerMetadataInEitherDirection() {
        FluidStack fluid = new FluidStack(Fluids.WATER, 1_000);
        fluid.getOrCreateTag().putString("batch", "original");
        CompoundTag root = TankSave.encode(fluid);
        FluidStack decoded = TankSave.decode(root);
        root.getCompound("fluid").getCompound("Tag").putString("batch", "caller");
        assertEquals("original", decoded.getTag().getString("batch"));
        decoded.getTag().putString("batch", "decoded");
        assertEquals("caller", root.getCompound("fluid").getCompound("Tag").getString("batch"));
    }

    @Test void unsupportedRootsAndEnvelopesDoNotDecodeToEmptyDefaults() {
        CompoundTag valid = TankSave.encode(new FluidStack(Fluids.WATER, 1_000));
        for (String key : valid.getAllKeys()) {
            CompoundTag missing = valid.copy(); missing.remove(key);
            assertThrows(IllegalArgumentException.class, () -> TankSave.decode(missing));
            CompoundTag wrong = valid.copy(); wrong.put(key, StringTag.valueOf("retain"));
            assertThrows(IllegalArgumentException.class, () -> TankSave.decode(wrong));
        }
        CompoundTag future = valid.copy(); future.putInt("schema", 2);
        assertThrows(IllegalArgumentException.class, () -> TankSave.decode(future));
        CompoundTag unknown = valid.copy(); unknown.putInt("unknown", 1);
        assertThrows(IllegalArgumentException.class, () -> TankSave.decode(unknown));
        assertThrows(IllegalArgumentException.class, () -> TankSave.decode(IntTag.valueOf(4)));
        for (int amount : new int[]{0, -1, Integer.MIN_VALUE}) {
            CompoundTag bad = valid.copy(); bad.getCompound("fluid").putInt("Amount", amount);
            assertThrows(IllegalArgumentException.class, () -> TankSave.decode(bad));
        }
        for (String id : new String[]{"missing:fluid", "minecraft:empty", "UPPERCASE:bad", "x:" + "a".repeat(130)}) {
            CompoundTag bad = valid.copy(); bad.getCompound("fluid").putString("FluidName", id);
            assertThrows(IllegalArgumentException.class, () -> TankSave.decode(bad));
        }
        CompoundTag extra = valid.copy(); extra.getCompound("fluid").putInt("extra", 7);
        assertThrows(IllegalArgumentException.class, () -> TankSave.decode(extra));
    }

    @Test void byteDepthAndAggregateBudgetsPrecedeCopiesAndCapabilityInsertion() {
        CompoundTag huge = new CompoundTag(); huge.putByteArray("bytes", new byte[TankSave.MAX_BYTES]);
        assertFalse(TankSave.bounded(huge));
        assertThrows(IllegalArgumentException.class, () -> TankSave.decode(huge));
        CompoundTag deep = new CompoundTag(); CompoundTag cursor = deep;
        for (int depth = 0; depth < 40; depth++) {
            CompoundTag child = new CompoundTag(); cursor.put("child", child); cursor = child;
        }
        FluidStack fluid = new FluidStack(Fluids.WATER, 1_000); fluid.setTag(deep);
        assertFalse(TankSave.fits(fluid));
        for (int bytes = 8_075; bytes < 8_192; bytes++) {
            CompoundTag payload = new CompoundTag(); payload.putByteArray("x", new byte[bytes]);
            FluidStack candidate = new FluidStack(Fluids.WATER, 1_000); candidate.setTag(payload);
            if (TankSave.safe(candidate) && !TankSave.fits(candidate)) { return; }
        }
        fail("Aggregate state framing must count toward the tank byte limit");
    }
}
