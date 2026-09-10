package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RollingMachinePortPersistenceTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void everyTypedPortRoundTripsOnlyItsResource() {
        assertPortData(
                RollingMachinePortType.ITEM_INPUT,
                new ItemStack(Items.IRON_INGOT, 3),
                FluidStack.EMPTY,
                0
        );
        assertPortData(
                RollingMachinePortType.ITEM_OUTPUT,
                new ItemStack(Items.IRON_BARS, 8),
                FluidStack.EMPTY,
                0
        );
        assertPortData(
                RollingMachinePortType.FLUID_INPUT,
                ItemStack.EMPTY,
                new FluidStack(Fluids.WATER, 750),
                0
        );
        assertPortData(
                RollingMachinePortType.ENERGY_INPUT,
                ItemStack.EMPTY,
                FluidStack.EMPTY,
                12_345
        );
    }

    @Test
    void futureAndPrimitiveRootsArePreservedWithoutInterpretation() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", RollingMachinePortPersistence.SCHEMA_VERSION + 1);
        future.putString("future_payload", "keep-exactly");
        CompoundTag parent = new CompoundTag();
        parent.put(RollingMachinePortPersistence.ROOT, future);

        RollingMachinePortPersistence.DecodeResult futureResult =
                RollingMachinePortPersistence.decode(parent, RollingMachinePortType.ITEM_INPUT);
        assertEquals(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, futureResult.status());
        assertEquals(future, futureResult.preservedRoot().orElseThrow());
        assertNotSame(future, futureResult.preservedRoot().orElseThrow());

        parent.put(RollingMachinePortPersistence.ROOT, IntTag.valueOf(42));
        RollingMachinePortPersistence.DecodeResult primitiveResult =
                RollingMachinePortPersistence.decode(parent, RollingMachinePortType.ITEM_INPUT);
        assertEquals(MultiblockNbtStatus.INVALID_DATA, primitiveResult.status());
        assertEquals(IntTag.valueOf(42), primitiveResult.preservedRoot().orElseThrow());
    }

    @Test
    void mismatchedTypesUnexpectedFieldsAndOversizedRootsFailClosed() {
        CompoundTag parent = parentWith(RollingMachinePortPersistence.encode(
                RollingMachinePortType.ITEM_INPUT,
                new ItemStack(Items.IRON_INGOT),
                FluidStack.EMPTY,
                0
        ));
        assertEquals(
                MultiblockNbtStatus.INVALID_DATA,
                RollingMachinePortPersistence.decode(parent, RollingMachinePortType.ITEM_OUTPUT).status()
        );

        CompoundTag unexpected = parent.getCompound(RollingMachinePortPersistence.ROOT).copy();
        unexpected.putInt("unexpected", 1);
        assertEquals(
                MultiblockNbtStatus.INVALID_DATA,
                RollingMachinePortPersistence.decode(
                        parentWith(unexpected),
                        RollingMachinePortType.ITEM_INPUT
                ).status()
        );

        CompoundTag oversized = new CompoundTag();
        oversized.putInt("schema_version", RollingMachinePortPersistence.SCHEMA_VERSION + 1);
        oversized.put("payload", new ByteArrayTag(new byte[RollingMachinePortPersistence.MAX_ROOT_BYTES]));
        RollingMachinePortPersistence.DecodeResult oversizedResult =
                RollingMachinePortPersistence.decode(
                        parentWith(oversized),
                        RollingMachinePortType.ITEM_INPUT
                );
        assertEquals(MultiblockNbtStatus.INVALID_DATA, oversizedResult.status());
        assertEquals(oversized, oversizedResult.preservedRoot().orElseThrow());
    }

    @Test
    void resourceBoundsRejectTaggedItemsWrongFluidsAndExcessEnergy() {
        ItemStack tagged = new ItemStack(Items.IRON_INGOT);
        tagged.getOrCreateTag().putString("custom", "blocked");
        assertThrows(
                IllegalStateException.class,
                () -> RollingMachinePortPersistence.encode(
                        RollingMachinePortType.ITEM_INPUT,
                        tagged,
                        FluidStack.EMPTY,
                        0
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> RollingMachinePortPersistence.encode(
                        RollingMachinePortType.FLUID_INPUT,
                        ItemStack.EMPTY,
                        new FluidStack(Fluids.LAVA, 100),
                        0
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> RollingMachinePortPersistence.encode(
                        RollingMachinePortType.ENERGY_INPUT,
                        ItemStack.EMPTY,
                        FluidStack.EMPTY,
                        RollingMachinePortBlockEntity.ENERGY_CAPACITY + 1
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> RollingMachinePortPersistence.encode(
                        RollingMachinePortType.ITEM_INPUT,
                        ItemStack.EMPTY,
                        new FluidStack(Fluids.WATER, 100),
                        0
                )
        );
    }

    @Test
    void absentRootIsAnEmptySupportedStartingPointForTheAdapter() {
        RollingMachinePortPersistence.DecodeResult decoded = RollingMachinePortPersistence.decode(
                new CompoundTag(),
                RollingMachinePortType.ITEM_INPUT
        );

        assertEquals(MultiblockNbtStatus.EMPTY, decoded.status());
        assertTrue(decoded.value().isEmpty());
        assertTrue(decoded.preservedRoot().isEmpty());
    }

    private static void assertPortData(
            RollingMachinePortType type,
            ItemStack item,
            FluidStack fluid,
            int energy
    ) {
        CompoundTag encoded = RollingMachinePortPersistence.encode(type, item, fluid, energy);
        RollingMachinePortPersistence.DecodeResult decoded = RollingMachinePortPersistence.decode(
                parentWith(encoded),
                type
        );

        assertEquals(MultiblockNbtStatus.SUPPORTED, decoded.status());
        RollingMachinePortPersistence.PortData data = decoded.value().orElseThrow();
        assertTrue(ItemStack.matches(item, data.item()));
        assertTrue(fluid.isFluidStackIdentical(data.fluid()));
        assertEquals(energy, data.energy());
    }

    private static CompoundTag parentWith(net.minecraft.nbt.Tag root) {
        CompoundTag parent = new CompoundTag();
        parent.put(RollingMachinePortPersistence.ROOT, root);
        return parent;
    }
}
