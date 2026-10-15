package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTestTags.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoaderTankTest {
    private static final ResourceLocation WATER = ResourceLocation.tryParse("minecraft:water");
    private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-000000000081");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static CompoundTag batch(String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("batch", value);
        return tag;
    }

    @Test void emptyOneAndFullTanksRoundTripInForgesOwnEnvelope() {
        assertEquals(new CompoundTag(), LoaderTank.EMPTY.encode());
        assertEquals(LoaderTank.EMPTY, LoaderTank.decode(new CompoundTag()));
        for (int amount : new int[] {1, 749, 1_000, LoaderTank.CAPACITY}) {
            for (CompoundTag tag : new CompoundTag[] {null, new CompoundTag(), batch("retained")}) {
                LoaderTank tank = LoaderTank.of(WATER, tag, amount);
                CompoundTag encoded = tank.encode();
                assertEquals(new FluidStack(Fluids.WATER, amount, tag).writeToNBT(new CompoundTag()), encoded);
                assertEquals(tank, LoaderTank.decode(encoded));
                assertEquals(encoded, LoaderTank.decode(encoded).encode());
            }
        }
        // An empty native tag is a different native Fluid from no tag at all, as Forge compares them.
        assertNotEquals(LoaderTank.of(WATER, null, 10), LoaderTank.of(WATER, new CompoundTag(), 10));
    }

    @Test void amountsOutsideZeroToSixteenThousandAreRefused() {
        assertEquals(LoaderTank.EMPTY, LoaderTank.of(WATER, batch("dropped with the Fluid"), 0));
        for (int amount : new int[] {-1, LoaderTank.CAPACITY + 1, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            invalid(() -> LoaderTank.of(WATER, null, amount));
        }
        CompoundTag valid = LoaderTank.of(WATER, null, 1_000).encode();
        for (int amount : new int[] {0, -1, LoaderTank.CAPACITY + 1, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            CompoundTag tag = valid.copy();
            tag.putInt("Amount", amount);
            invalid(() -> LoaderTank.decode(tag));
        }
    }

    @Test void unknownMistypedAndNoncanonicalFieldsAreInvalid() {
        CompoundTag valid = LoaderTank.of(WATER, batch("a"), 1_000).encode();
        CompoundTag unknown = valid.copy(); unknown.putInt("extra", 1);
        invalid(() -> LoaderTank.decode(unknown));
        CompoundTag nameType = valid.copy(); nameType.putInt("FluidName", 1);
        invalid(() -> LoaderTank.decode(nameType));
        CompoundTag amountType = valid.copy(); amountType.putLong("Amount", 1_000);
        invalid(() -> LoaderTank.decode(amountType));
        CompoundTag tagType = valid.copy(); tagType.put("Tag", StringTag.valueOf("x"));
        invalid(() -> LoaderTank.decode(tagType));
        for (String key : new String[] {"FluidName", "Amount"}) {
            CompoundTag missing = valid.copy(); missing.remove(key);
            invalid(() -> LoaderTank.decode(missing));
        }
        for (String id : new String[] {"minecraft:empty", "water", "Minecraft:water", "minecraft:water ", "",
                "x:" + "a".repeat(127)}) {
            CompoundTag tag = valid.copy(); tag.putString("FluidName", id);
            invalid(() -> LoaderTank.decode(tag));
        }
        invalid(() -> LoaderTank.of(ResourceLocation.tryParse("minecraft:empty"), null, 1));
        invalid(() -> LoaderTank.of(ResourceLocation.tryParse("x:" + "a".repeat(127)), null, 1));
        invalid(() -> LoaderTank.decode(StringTag.valueOf("tank")));
        invalid(() -> LoaderTank.decode(null));
    }

    @Test void aFluidThatIsNotRegisteredIsPreservedAsUnregisteredNotInvalid() {
        CompoundTag absent = LoaderTank.of(ResourceLocation.tryParse("absentmod:fuel"), batch("kept"), 1_000).encode();
        assertEquals(LoaderDataException.Reason.UNREGISTERED_FLUID, refusal(() -> LoaderTank.decode(absent)));
        // Pure construction and encoding need no registry; only native data and conversions check registration.
        assertEquals(absent, LoaderTank.of(ResourceLocation.tryParse("absentmod:fuel"), batch("kept"), 1_000).encode());
        invalid(() -> LoaderTank.of(ResourceLocation.tryParse("absentmod:fuel"), null, 1).fluidStack());
    }

    @Test void envelopeBytesDepthAndNodesAreExactForValidNativeTags() {
        CompoundTag base = LoaderTank.of(WATER, new CompoundTag(), 1_000).encode();
        bounds(base, LoaderTank::bounded, LoaderTank.MAX_BYTES, LoaderTank.MAX_DEPTH, LoaderTank.MAX_NODES);
        // The same limits hold for a valid tank whose native tag fills the envelope.
        CompoundTag fullBytes = base.copy();
        // The nested tag drops its 3-byte root header and replaces the empty tag's 1-byte body.
        fullBytes.put("Tag", padTo(new CompoundTag(), "p", LoaderTank.MAX_BYTES - (int) size(base).bytes() + 4));
        assertEquals(LoaderTank.MAX_BYTES, size(fullBytes).bytes());
        assertEquals(fullBytes, LoaderTank.decode(fullBytes).encode());
        CompoundTag overBytes = base.copy();
        overBytes.put("Tag", padTo(new CompoundTag(), "p", LoaderTank.MAX_BYTES - (int) size(base).bytes() + 5));
        invalid(() -> LoaderTank.decode(overBytes));
        invalid(() -> LoaderTank.of(WATER, overBytes.getCompound("Tag"), 1_000));
        CompoundTag deepest = base.copy();
        deepest.put("Tag", chain(LoaderTank.MAX_DEPTH - 1));
        assertEquals(deepest, LoaderTank.decode(deepest).encode());
        CompoundTag tooDeep = base.copy();
        tooDeep.put("Tag", chain(LoaderTank.MAX_DEPTH));
        invalid(() -> LoaderTank.decode(tooDeep));
        CompoundTag most = base.copy();
        most.put("Tag", nodesTo(new CompoundTag(), "n", LoaderTank.MAX_NODES - 3));
        assertEquals(LoaderTank.MAX_NODES, size(most).nodes());
        assertEquals(most, LoaderTank.decode(most).encode());
        CompoundTag tooMany = base.copy();
        tooMany.put("Tag", nodesTo(new CompoundTag(), "n", LoaderTank.MAX_NODES - 2));
        invalid(() -> LoaderTank.decode(tooMany));
    }

    @Test void everyValidTankFitsACarrierAndAFullRoot() {
        CompoundTag base = LoaderTank.of(WATER, new CompoundTag(), LoaderTank.CAPACITY).encode();
        CompoundTag bytes = padTo(new CompoundTag(), "p", LoaderTank.MAX_BYTES - (int) size(base).bytes() + 4);
        CompoundTag nodes = nodesTo(new CompoundTag(), "n", LoaderTank.MAX_NODES - 3);
        CompoundTag depth = chain(LoaderTank.MAX_DEPTH - 1);
        UUID owner = UUID.randomUUID();
        var binding = new LoaderBinding(UUID.randomUUID(), ResourceLocation.tryParse("x:" + "a".repeat(126)), UUID.randomUUID(),
                new BlockPos(-30_000_000, -2_048, 29_999_999), UUID.randomUUID());
        var locked = new LoaderEscrowSection(UUID.randomUUID(), Long.MAX_VALUE,
                List.of(new EscrowPrefix.Marker(UUID.randomUUID(), Long.MAX_VALUE, "SOURCE", Long.MAX_VALUE)));
        for (CompoundTag tag : new CompoundTag[] {bytes, nodes, depth}) {
            LoaderTank tank = LoaderTank.of(WATER, tag, LoaderTank.CAPACITY);
            assertTrue(size(tank.encode()).bytes() == LoaderTank.MAX_BYTES || size(tank.encode()).nodes() == LoaderTank.MAX_NODES
                    || tag == depth);
            var carrier = new LoaderCarrier(owner, tank);
            assertEquals(carrier, LoaderCarrier.decode(carrier.encode()));
            var root = new LoaderRoot(owner, UUID.randomUUID(), binding, tank, locked);
            assertEquals(root, LoaderRoot.decode(root.encode()));
        }
    }

    @Test void signatureIsPinnedAndCoversDeviceFluidAmountAndTag() {
        // Independently computed from the NC1 framing: SHA-256 of the prefix, the device and the native signature.
        assertEquals("245676a48fb1a6489c63b25655272c329bd219ed88560295ff6bca8ea444a9b6",
                LoaderTank.of(WATER, null, 1_000).signature(DEVICE));
        assertEquals("47688946033c7dddf27cd1293d26e77dc9e598bf3ae914157faa13c7bf0bbc8f", LoaderTank.EMPTY.signature(DEVICE));
        assertEquals("arce-escrow-fluid-tank-1:", LoaderTank.SIGNATURE_PREFIX);
        String base = LoaderTank.of(WATER, null, 1_000).signature(DEVICE);
        assertEquals(base, LoaderTank.decode(LoaderTank.of(WATER, null, 1_000).encode()).signature(DEVICE));
        assertNotEquals(base, LoaderTank.of(WATER, null, 1_000).signature(UUID.randomUUID()));
        assertNotEquals(base, LoaderTank.of(WATER, null, 999).signature(DEVICE));
        assertNotEquals(base, LoaderTank.of(WATER, new CompoundTag(), 1_000).signature(DEVICE));
        assertNotEquals(base, LoaderTank.of(ResourceLocation.tryParse("minecraft:lava"), null, 1_000).signature(DEVICE));
        assertThrows(NullPointerException.class, () -> LoaderTank.EMPTY.signature(null));
    }

    @Test void conversionsAndAccessorsShareNoNativeTag() {
        FluidStack stack = new FluidStack(Fluids.WATER, 1_251, batch("original"));
        LoaderTank tank = LoaderTank.of(stack);
        stack.getTag().putString("batch", "caller");
        assertEquals(batch("original"), tank.tag());
        tank.tag().putString("batch", "accessor");
        FluidStack out = tank.fluidStack();
        assertTrue(out.isFluidStackIdentical(new FluidStack(Fluids.WATER, 1_251, batch("original"))));
        out.getTag().putString("batch", "capability");
        assertEquals(batch("original"), tank.tag());
        assertEquals(LoaderTank.EMPTY, LoaderTank.of(FluidStack.EMPTY));
        assertTrue(LoaderTank.EMPTY.fluidStack().isEmpty());
        assertEquals(LoaderTank.CAPACITY - 1_251, tank.room());
    }

    @Test void withAmountKeepsTheFluidAndTagAndZeroEmpties() {
        LoaderTank tank = LoaderTank.of(WATER, batch("kept"), 1_000);
        assertEquals(LoaderTank.of(WATER, batch("kept"), 251), tank.withAmount(251));
        assertEquals(LoaderTank.EMPTY, tank.withAmount(0));
        assertEquals(LoaderTank.EMPTY, LoaderTank.EMPTY.withAmount(0));
        invalid(() -> LoaderTank.EMPTY.withAmount(1));
        invalid(() -> tank.withAmount(LoaderTank.CAPACITY + 1));
    }
}
