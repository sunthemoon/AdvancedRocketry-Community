package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PumpSaveTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID OWNER = new UUID(1, 27);
    @Test void overLimitFluidIdsRefuseOnEncodeAndDecodeWithoutNormalization() {
        String overLimit = "test:" + "a".repeat(124);
        assertEquals(129, overLimit.length());
        CompoundTag root = PumpSave.encode(null, 0, 0, new FluidStack(Fluids.WATER, 1));
        root.getCompound("fluid").putString("FluidName", overLimit);
        assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(root));
        FluidStack noncanonical = new FluidStack(Fluids.WATER, 1) {
            @Override public CompoundTag writeToNBT(CompoundTag encoded) {
                super.writeToNBT(encoded);
                encoded.putString("FluidName", overLimit);
                return encoded;
            }
        };
        assertFalse(PumpSave.fits(null, 0, 0, noncanonical));
        assertThrows(IllegalArgumentException.class, () -> PumpSave.encode(null, 0, 0, noncanonical));
    }
    @Test void ownerResourcesCooldownAndGenericTaggedFluidRoundTripExactly() {
        for (FluidStack fluid : new FluidStack[]{FluidStack.EMPTY, new FluidStack(Fluids.WATER, 16_000), new FluidStack(Fluids.LAVA, 37)}) {
            if (!fluid.isEmpty()) { fluid.getOrCreateTag().putString("batch", "kept"); }
            CompoundTag root = PumpSave.encode(OWNER, 10_000, 5, fluid);
            var saved = PumpSave.decode(root);
            assertEquals(OWNER, saved.owner()); assertEquals(10_000, saved.energy()); assertEquals(5, saved.cooldown());
            assertTrue(fluid.isFluidStackIdentical(saved.fluid()));
            assertEquals(root, PumpSave.encode(saved.owner(), saved.energy(), saved.cooldown(), saved.fluid()));
            if (!saved.fluid().isEmpty()) { saved.fluid().getOrCreateTag().putString("batch", "changed"); assertNotEquals(saved.fluid().getTag(), root.getCompound("fluid").getCompound("Tag")); }
        }
        assertNull(PumpSave.decode(PumpSave.encode(null, 0, 0, FluidStack.EMPTY)).owner());
    }
    @Test void missingFutureUnknownAndMistypedFieldsAreRefusedNotDefaulted() {
        CompoundTag root = PumpSave.encode(OWNER, 100, 0, FluidStack.EMPTY);
        for (String key : new String[]{"schema", "energy", "cooldown", "fluid"}) {
            CompoundTag missing = root.copy(); missing.remove(key);
            assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(missing));
            CompoundTag wrong = root.copy(); wrong.put(key, StringTag.valueOf("kept"));
            assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(wrong));
        }
        CompoundTag future = root.copy(); future.putInt("schema", 2);
        assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(future));
        CompoundTag extra = root.copy(); extra.putInt("frontier", 4);
        assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(extra));
        root.putInt("owner", 0); assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(root));
        assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(IntTag.valueOf(9)));
    }
    @Test void impossibleResourceCountsAndUnknownFluidAreRefused() {
        for (String key : new String[]{"energy", "cooldown"}) {
            for (int bad : new int[]{-1, Integer.MAX_VALUE}) {
                CompoundTag root = PumpSave.encode(null, 0, 0, new FluidStack(Fluids.WATER, 1)); root.putInt(key, bad);
                assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(root));
            }
        }
        for (int bad : new int[]{-1, 0, 16_001, Integer.MAX_VALUE}) {
            CompoundTag root = PumpSave.encode(null, 0, 0, new FluidStack(Fluids.WATER, 1));
            root.getCompound("fluid").putInt("Amount", bad);
            assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(root));
        }
        CompoundTag unknown = PumpSave.encode(null, 0, 0, new FluidStack(Fluids.WATER, 1));
        unknown.getCompound("fluid").putString("FluidName", "missing:future_fluid");
        assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(unknown));
    }
    @Test void aggregateBytesAndDepthArePreflightedBeforeCopies() {
        CompoundTag huge = new CompoundTag(); huge.putByteArray("payload", new byte[8192]);
        assertFalse(PumpSave.bounded(huge)); assertThrows(IllegalArgumentException.class, () -> PumpSave.decode(huge));
        FluidStack deep = new FluidStack(Fluids.WATER, 1);
        CompoundTag cursor = deep.getOrCreateTag();
        for (int i = 0; i < 40; i++) { CompoundTag child = new CompoundTag(); cursor.put("child", child); cursor = child; }
        assertFalse(PumpSave.fits(OWNER, 0, 0, deep));
        FluidStack framed = new FluidStack(Fluids.WATER, 1); framed.getOrCreateTag().putByteArray("payload", new byte[8100]);
        assertTrue(PumpSave.bounded(framed.getTag())); assertFalse(PumpSave.fits(OWNER, 0, 0, framed));
    }
}
