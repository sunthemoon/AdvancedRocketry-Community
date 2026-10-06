package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicGuardedResourceAccessTest {
    private static final UUID OWNER = new UUID(1, 2);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void nativeCallChecksBeforeAndImmediatelyAfterTheOperationAndDoesNotRetainIt() {
        AtomicInteger checks = new AtomicInteger(); AtomicInteger operations = new AtomicInteger();
        assertEquals("value", ClassicGuardedResourceAccess.nativeCall(checks::incrementAndGet, () -> {
            assertEquals(1, checks.get()); operations.incrementAndGet(); return "value";
        }));
        assertEquals(2, checks.get()); assertEquals(1, operations.get());
        assertThrows(ClassicGuardedResourceAccess.WitnessFailure.class, () -> ClassicGuardedResourceAccess.nativeCall(() -> {
            if (operations.get() == 2) { throw new IllegalStateException("retired"); }
        }, () -> { operations.incrementAndGet(); return null; }));
        assertEquals(2, operations.get());
    }

    @Test void guardedItemAndFluidFactoriesPreserveLegacyMetadataAndCallbackOutcome() {
        var itemKey = new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 1, 2, 3);
        ItemStack item = new ItemStack(Items.PAPER, 3); item.getOrCreateTag().putString("batch", "original");
        var slots = List.of(item, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
        assertTrue(ClassicResourceBank.items(itemKey, slots).samePayload(ClassicResourceBank.items(itemKey, slots, () -> { })));
        var fluidKey = new ClassicBankKey(ClassicBankKind.FLUID_OUTPUT, 1, 2, 3);
        FluidStack fluid = new FluidStack(Fluids.WATER, 1_000); fluid.getOrCreateTag().putString("batch", "original");
        assertTrue(ClassicResourceBank.fluid(fluidKey, fluid).samePayload(ClassicResourceBank.fluid(fluidKey, fluid, () -> { })));
    }

    @Test void guardedWholeDecodeMatchesLegacyAndEveryCheckFailureEscapesInvalidDataCatch() {
        var key = new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 1, 2, 3);
        var value = ClassicResources.empty(OWNER).replace(List.of(ClassicResourceBank.items(key,
                List.of(new ItemStack(Items.PAPER, 3), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY))), false);
        CompoundTag raw = ClassicResourcesCodec.encode(value); AtomicInteger checks = new AtomicInteger();
        var decoded = ClassicGuardedResourceAccess.decode(raw, OWNER, checks::incrementAndGet);
        assertEquals(raw, ClassicGuardedResourceAccess.encode(decoded.value().orElseThrow(), () -> { }));
        assertEquals(ClassicResourcesCodec.decode(raw, OWNER).status(), decoded.status());
        int total = checks.get(); assertTrue(total > 10 && total < 512);
        for (int target = 1; target <= total; target++) {
            int failAt = target; AtomicInteger counter = new AtomicInteger();
            assertThrows(ClassicGuardedResourceAccess.WitnessFailure.class, () -> ClassicGuardedResourceAccess.decode(raw, OWNER, () -> {
                if (counter.incrementAndGet() == failAt) { throw new IllegalStateException("retired"); }
            }));
            assertEquals(target, counter.get());
        }
        assertEquals(raw, ClassicResourcesCodec.encode(value));
    }

    @Test void allBankEnvelopesPreflightBeforeNativeDecodingAndLegacyRefusalsRemain() {
        CompoundTag raw = ClassicResourcesCodec.encode(ClassicResources.empty(OWNER));
        raw.putString("unknown", "field");
        assertEquals(ClassicResourcesCodec.decode(raw, OWNER).status(), ClassicGuardedResourceAccess.decode(raw, OWNER, () -> { }).status());
        assertThrows(NullPointerException.class, () -> ClassicGuardedResourceAccess.decode(raw, OWNER, null));
        assertThrows(ClassicGuardedResourceAccess.WitnessFailure.class, () -> ClassicGuardedResourceAccess.encode(ClassicResources.empty(OWNER), () -> {
            throw new IllegalStateException("retired");
        }));
    }

    @Test void malformedLaterBankRefusesBeforeTheFirstNativeStackDecode() {
        CompoundTag raw = mixedRoot();
        banks(raw).getCompound(1).putInt("unknown", 1);
        AtomicInteger checks = new AtomicInteger();
        var decoded = ClassicGuardedResourceAccess.decode(raw, OWNER, checks::incrementAndGet);
        assertEquals(ClassicResourcesDecode.Status.INVALID_DATA, decoded.status());
        assertTrue(decoded.value().isEmpty()); assertEquals(raw, decoded.encodeForSave());
        // Two entry checks, two checks for the first Item registry preflight,
        // the refusal check and outer return check. No stack decode callback ran.
        assertEquals(6, checks.get());
        assertEquals(ClassicResourcesCodec.decode(raw, OWNER).status(), decoded.status());
    }

    @Test void guardedCountAmountTypeAndEnvelopeNegativesPreserveLegacyWholeRefusals() {
        for (int count : new int[] {0, -1, 65, 127}) {
            CompoundTag raw = mixedRoot(); item(raw).putByte("Count", (byte) count); assertInvalidLikeLegacy(raw);
        }
        CompoundTag nativeLimit = mixedRoot(); item(nativeLimit).putString("id", "minecraft:ender_pearl");
        item(nativeLimit).putByte("Count", (byte) 17); assertInvalidLikeLegacy(nativeLimit);
        CompoundTag countType = mixedRoot(); item(countType).putInt("Count", 3); assertInvalidLikeLegacy(countType);
        for (String key : List.of("ForgeCaps", "unknown")) {
            CompoundTag raw = mixedRoot(); item(raw).put(key, new CompoundTag()); assertInvalidLikeLegacy(raw);
        }
        CompoundTag itemTag = mixedRoot(); item(itemTag).putString("tag", "wrong_type"); assertInvalidLikeLegacy(itemTag);
        for (int amount : new int[] {Integer.MIN_VALUE, -1, 0, 16_001, Integer.MAX_VALUE}) {
            CompoundTag raw = mixedRoot(); fluid(raw).putInt("Amount", amount); assertInvalidLikeLegacy(raw);
        }
        CompoundTag amountType = mixedRoot(); fluid(amountType).putLong("Amount", 1_000); assertInvalidLikeLegacy(amountType);
        CompoundTag fluidTag = mixedRoot(); fluid(fluidTag).putString("Tag", "wrong_type"); assertInvalidLikeLegacy(fluidTag);
        CompoundTag fluidCaps = mixedRoot(); fluid(fluidCaps).put("ForgeCaps", new CompoundTag()); assertInvalidLikeLegacy(fluidCaps);
        CompoundTag slots = mixedRoot(); ((ListTag) banks(slots).getCompound(0).get("items")).remove(3); assertInvalidLikeLegacy(slots);
        CompoundTag duplicate = mixedRoot(); banks(duplicate).add(banks(duplicate).getCompound(0).copy()); assertInvalidLikeLegacy(duplicate);
    }

    @Test void aggregateBytesDepthAndNodesRefuseBeforeNativeCallbacksAndRetainEntireInput() {
        CompoundTag bytes = mixedRoot(); item(bytes).getCompound("tag").putByteArray("excess", new byte[ClassicResourcesCodec.MAX_BYTES]);
        CompoundTag depth = mixedRoot(); CompoundTag cursor = fluid(depth).getCompound("Tag");
        for (int i = 0; i < ClassicResourcesCodec.MAX_DEPTH; i++) {
            CompoundTag child = new CompoundTag(); cursor.put("child", child); cursor = child;
        }
        CompoundTag nodes = mixedRoot(); ListTag entries = new ListTag();
        for (int i = 0; i < ClassicResourcesCodec.MAX_NODES; i++) { entries.add(ByteTag.valueOf((byte) 1)); }
        item(nodes).getCompound("tag").put("entries", entries);
        for (CompoundTag raw : List.of(bytes, depth, nodes)) {
            AtomicInteger checks = new AtomicInteger();
            var decoded = ClassicGuardedResourceAccess.decode(raw, OWNER, checks::incrementAndGet);
            assertEquals(ClassicResourcesDecode.Status.UNBOUNDED, decoded.status());
            assertTrue(decoded.retainsUnboundedIdentity(raw)); assertTrue(decoded.value().isEmpty());
            assertTrue(decoded.requiresSaveRefusal()); assertThrows(IllegalStateException.class, decoded::encodeForSave);
            assertEquals(3, checks.get());
            assertEquals(ClassicResourcesCodec.decode(raw, OWNER).status(), decoded.status());
        }
    }

    @Test void everyFluidWitnessFailureEscapesInvalidDataCatchWithoutChangingInput() {
        var key = new ClassicBankKey(ClassicBankKind.FLUID_INPUT, 2, 3, 4);
        FluidStack stack = new FluidStack(Fluids.WATER, 1_000); stack.getOrCreateTag().putString("batch", "fluid_original");
        CompoundTag raw = ClassicResourcesCodec.encode(ClassicResources.empty(OWNER).replace(
                List.of(ClassicResourceBank.fluid(key, stack)), false));
        CompoundTag before = raw.copy(); AtomicInteger checks = new AtomicInteger();
        var decoded = ClassicGuardedResourceAccess.decode(raw, OWNER, checks::incrementAndGet);
        assertEquals(ClassicResourcesDecode.Status.SUPPORTED, decoded.status());
        assertEquals(raw, ClassicGuardedResourceAccess.encode(decoded.value().orElseThrow(), () -> { }));
        int total = checks.get(); assertTrue(total > 10 && total < 512);
        for (int target = 1; target <= total; target++) {
            int failAt = target; AtomicInteger counter = new AtomicInteger();
            assertThrows(ClassicGuardedResourceAccess.WitnessFailure.class, () -> ClassicGuardedResourceAccess.decode(raw, OWNER, () -> {
                if (counter.incrementAndGet() == failAt) { throw new IllegalStateException("retired"); }
            }));
            assertEquals(target, counter.get()); assertEquals(before, raw);
        }
        assertEquals(before, ClassicResourcesCodec.decode(raw, OWNER).encodeForSave());
    }

    private static CompoundTag mixedRoot() {
        ItemStack item = new ItemStack(Items.PAPER, 3); item.getOrCreateTag().putString("batch", "item_original");
        FluidStack fluid = new FluidStack(Fluids.WATER, 1_000); fluid.getOrCreateTag().putString("batch", "fluid_original");
        return ClassicResourcesCodec.encode(ClassicResources.empty(OWNER).replace(List.of(
                ClassicResourceBank.items(new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 1, 2, 3),
                        List.of(item, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)),
                ClassicResourceBank.fluid(new ClassicBankKey(ClassicBankKind.FLUID_OUTPUT, 2, 3, 4), fluid)), false));
    }

    private static ListTag banks(CompoundTag raw) { return (ListTag) raw.get("banks"); }
    private static CompoundTag item(CompoundTag raw) { return ((ListTag) banks(raw).getCompound(0).get("items")).getCompound(0); }
    private static CompoundTag fluid(CompoundTag raw) { return banks(raw).getCompound(1).getCompound("fluid"); }

    private static void assertInvalidLikeLegacy(CompoundTag raw) {
        CompoundTag before = raw.copy(); var decoded = ClassicGuardedResourceAccess.decode(raw, OWNER, () -> { });
        assertEquals(ClassicResourcesDecode.Status.INVALID_DATA, decoded.status());
        assertEquals(ClassicResourcesCodec.decode(raw, OWNER).status(), decoded.status());
        assertTrue(decoded.value().isEmpty()); assertFalse(decoded.requiresSaveRefusal());
        assertEquals(before, decoded.encodeForSave()); assertEquals(before, raw);
    }
}
