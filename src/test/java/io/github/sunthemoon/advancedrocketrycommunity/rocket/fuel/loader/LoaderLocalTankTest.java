package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoaderLocalTankTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void acceptsOnlyAvailableRoomWithoutChangingTheOffer() {
        var offered = new FluidStack(Fluids.WATER, Integer.MAX_VALUE);
        var before = LoaderTank.of(new FluidStack(Fluids.WATER, 15_999));
        assertEquals(16_000, LoaderLocalTank.accepting(before, offered).amount());
        assertEquals(Integer.MAX_VALUE, offered.getAmount());
        assertEquals(15_999, before.amount());
        assertNull(LoaderLocalTank.accepting(before.withAmount(16_000), offered));
    }

    @Test void emptyWrongFluidAndDifferentNativeTagAreRefused() {
        var tank = LoaderTank.of(new FluidStack(Fluids.WATER, 100));
        assertNull(LoaderLocalTank.accepting(tank, null));
        assertNull(LoaderLocalTank.accepting(tank, FluidStack.EMPTY));
        assertNull(LoaderLocalTank.accepting(tank, new FluidStack(Fluids.LAVA, 1)));
        assertNull(LoaderLocalTank.accepting(tank, new FluidStack(Fluids.WATER, 1, new CompoundTag())));
    }

    @Test void nativeTagsAreDetachedAndUnsupportedEnvelopesAreRefused() {
        var tag = new CompoundTag(); tag.putString("batch", "kept");
        var offered = new FluidStack(Fluids.WATER, 749, tag);
        var accepted = LoaderLocalTank.accepting(LoaderTank.EMPTY, offered);
        assertEquals(749, accepted.amount());
        offered.getTag().putString("batch", "changed");
        assertEquals("kept", accepted.tag().getString("batch"));
        var huge = new CompoundTag(); huge.putByteArray("opaque", new byte[8_193]);
        assertNull(LoaderLocalTank.intake(LoaderTank.EMPTY, new FluidStack(Fluids.WATER, 1, huge)));
    }
}
