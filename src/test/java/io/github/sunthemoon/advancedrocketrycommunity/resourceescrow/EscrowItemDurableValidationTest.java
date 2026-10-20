package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.WorldDataMigrationService;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowItemDurableValidationTest {
    private static final String CHANGING_ITEM = "w110f:changing_native_item";
    private static ChangingItem ITEM;
    @BeforeAll static void bootstrap() {
        MinecraftBootstrap.initialize();
        ITEM = new ChangingItem();
        Registry.register(BuiltInRegistries.ITEM, CHANGING_ITEM, ITEM);
    }
    @TempDir Path temp;

    @Test void missingRegisteredItemDoesNotRefuseLedgerStartup() throws Exception {
        var stack = saved("w110f:unregistered_item", 1);
        assertTrue(BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.tryParse(stack.getString("id"))).isEmpty());
        startsUnchangedButNativeRefuses(stack);
    }

    @Test void newlyAttachedCapabilitiesDoNotRefuseLedgerStartup() throws Exception {
        var stack = saved(CHANGING_ITEM, 1);
        assertEquals(stack, EscrowItemStackCodec.encode(EscrowItemStackCodec.decode(stack)));
        ITEM.caps = true;
        try {
            var nativeStack = ItemStack.of(stack.copy());
            nativeStack.getMaxStackSize();
            assertTrue(nativeStack.save(new CompoundTag()).contains("ForgeCaps"), "Exercise actual native capability serialization");
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.encode(nativeStack));
            startsUnchangedButNativeRefuses(stack);
        } finally { ITEM.caps = false; }
    }

    @Test void changedNativeMaximumDoesNotRefuseLedgerStartup() throws Exception {
        var stack = saved(CHANGING_ITEM, 64);
        assertEquals(stack, EscrowItemStackCodec.encode(EscrowItemStackCodec.decode(stack)));
        ITEM.maximum = 1;
        try {
            assertEquals(1, ItemStack.of(stack.copy()).getMaxStackSize());
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA,
                    () -> EscrowItemStackCodec.encode(ItemStack.of(stack.copy())));
            startsUnchangedButNativeRefuses(stack);
        } finally { ITEM.maximum = 64; }
    }

    private void startsUnchangedButNativeRefuses(CompoundTag stack) throws Exception {
        for (var kind : EscrowItemFixtures.KINDS) {
            for (boolean settling : java.util.List.of(false, true)) {
                var operation = EscrowItemFixtures.operation(kind, 1, 99);
                var prepared = EscrowCodec.encode(ledger(operation));
                var record = prepared.getList("records", 10).getCompound(0);
                var payload = record.getCompound("payload"); payload.put("stack", stack.copy());
                record.putString("payload_hash", RocketNativeResources.signature(payload));
                // Freeze the actual changed record, rather than corrupting an already frozen digest.
                var encoded = settling ? settle(prepared) : prepared;
                var world = Files.createDirectories(temp.resolve(kind + "-" + settling));
                var file = Files.createDirectories(world.resolve("data")).resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
                var outer = new CompoundTag(); outer.put("data", encoded); NbtIo.writeCompressed(outer, file.toFile());
                byte[] before = Files.readAllBytes(file);
                assertEquals(1, assertDoesNotThrow(() -> new WorldDataMigrationService().migrate(world)).managedFileCount());
                assertArrayEquals(before, Files.readAllBytes(file));
                assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
                var decoded = assertDoesNotThrow(() -> EscrowCodec.decode(encoded));
                assertEquals(encoded, EscrowCodec.encode(decoded));
                var data = assertDoesNotThrow(() -> ResourceEscrowSavedData.load(encoded));
                assertTrue(data.operational()); assertFalse(data.isDirty());
                try (var writer = new EscrowLedgerWriter(temp.resolve(kind + "-writer-" + settling), 0);
                        var coordinator = new EscrowCoordinator(data, writer)) {
                    assertEquals(decoded, coordinator.ledger());
                    assertTrue(writer.capturedOperations().isEmpty());
                }
                EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.decode(stack));
                EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.validateNative(stack));
                assertDoesNotThrow(() -> EscrowItemStackCodec.validateStructure(stack));
                assertEquals(stack, decoded.records().get(0).payload().getCompound("stack"));
            }
        }
    }

    private static CompoundTag settle(CompoundTag prepared) {
        var op = EscrowCodec.decode(prepared).records().get(0).withReason(EscrowReason.OTHER_STATE);
        return EscrowCodec.encode(ledger(op.settle(EscrowItemFixtures.intent(ledger(op), op, 0, 0, false))));
    }

    @Test void structuralValidationKeepsExactFieldsCountsAndLocalBounds() {
        var valid = saved("w110f:unregistered_item", 64);
        assertDoesNotThrow(() -> EscrowItemStackCodec.validateStructure(valid));
        for (String id : java.util.List.of("minecraft:air", "stone", "minecraft:STONE", "minecraft:\u00e9")) {
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA,
                    () -> EscrowItemStackCodec.validateStructure(saved(id, 1)));
        }
        for (int count : java.util.List.of(-1, 0, 65)) {
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA,
                    () -> EscrowItemStackCodec.validateStructure(saved("w110f:unregistered_item", count)));
        }
        var unknown = valid.copy(); unknown.putInt("Slot", 0);
        var wrongCount = valid.copy(); wrongCount.putInt("Count", 1);
        var missing = valid.copy(); missing.remove("id");
        var wrongTag = valid.copy(); wrongTag.putString("tag", "invalid");
        var caps = valid.copy(); caps.put("ForgeCaps", new CompoundTag());
        var nestedCaps = valid.copy(); var child = new CompoundTag(); child.put("ForgeCaps", new CompoundTag());
        var list = new net.minecraft.nbt.ListTag(); list.add(child); var tag = new CompoundTag(); tag.put("nested", list); nestedCaps.put("tag", tag);
        for (var bad : java.util.List.of(unknown, wrongCount, missing, wrongTag, caps, nestedCaps)) {
            var original = bad.copy();
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.validateStructure(bad));
            assertEquals(original, bad);
        }
        for (var edge : java.util.List.of(EscrowItemStackCodecTest.bytes(4_096), EscrowItemStackCodecTest.depth(16), EscrowItemStackCodecTest.nodes(512))) {
            assertDoesNotThrow(() -> EscrowItemStackCodec.validateStructure(edge));
        }
        for (var over : java.util.List.of(saved("w:" + "i".repeat(127), 1), EscrowItemStackCodecTest.bytes(4_097),
                EscrowItemStackCodecTest.depth(17), EscrowItemStackCodecTest.nodes(513))) {
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.CARGO_TOO_LARGE, () -> EscrowItemStackCodec.validateStructure(over));
        }
    }

    private static CompoundTag saved(String id, int count) {
        var stack = new CompoundTag(); stack.putString("id", id); stack.putByte("Count", (byte) count); return stack;
    }
    private static final class ChangingItem extends Item {
        private boolean caps;
        private int maximum = 64;
        private ChangingItem() { super(new Properties()); }
        @Override public int getMaxStackSize(ItemStack stack) {
            if (caps) { attachCaps(stack); }
            return maximum;
        }
        private static void attachCaps(ItemStack stack) {
            // Plain JUnit has no transformed Forge event bus. Install a per-stack dispatcher;
            // ItemStack.save still exercises Forge's real serializeCaps/ForgeCaps path.
            var provider = new ICapabilitySerializable<CompoundTag>() {
                @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction direction) { return LazyOptional.empty(); }
                @Override public CompoundTag serializeNBT() { var value = new CompoundTag(); value.putInt("native", 1); return value; }
                @Override public void deserializeNBT(CompoundTag value) { }
            };
            try {
                var capabilities = CapabilityProvider.class.getDeclaredField("capabilities"); capabilities.setAccessible(true);
                capabilities.set(stack, new CapabilityDispatcher(java.util.Map.of(), java.util.List.of(), provider));
                var initialized = CapabilityProvider.class.getDeclaredField("initialized"); initialized.setAccessible(true);
                initialized.setBoolean(stack, true);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }
}
