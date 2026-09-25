package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElectrolyzerPersistenceTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void currentSchemaEmptyStateDecodesWithinItsBounds() {
        CompoundTag parent = parentWith(validEmptyRoot());

        ElectrolyzerPersistence.DecodeResult decoded = ElectrolyzerPersistence.decode(parent);

        assertTrue(decoded.present());
        assertFalse(decoded.future());
        assertFalse(decoded.invalid());
        assertFalse(decoded.blockingInvalid());
        assertNull(decoded.preservedData());
        assertEquals(ElectrolyzerBlockEntity.SLOT_COUNT, decoded.inventory().length);
        assertTrue(java.util.Arrays.stream(decoded.inventory()).allMatch(stack -> stack.isEmpty()));
        assertTrue(decoded.water().isEmpty());
        assertEquals(0, decoded.energy());
        assertEquals(0, decoded.progress());
        assertNull(decoded.activeRecipeId());

        CompoundTag canonicalForgeEmpty = validEmptyRoot();
        canonicalForgeEmpty.getCompound("fluid").putString("FluidName", "minecraft:empty");
        canonicalForgeEmpty.getCompound("fluid").putInt("Amount", 0);
        assertFalse(ElectrolyzerPersistence.decode(parentWith(canonicalForgeEmpty)).invalid());
    }

    @Test
    void futurePrimitiveAndMalformedCurrentRootsRemainExactCopies() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", ElectrolyzerRecipeSpec.CURRENT_SCHEMA_VERSION + 1);
        future.putString("future_payload", "keep-exactly");
        assertPreserved(future, true);

        assertPreserved(IntTag.valueOf(42), false);

        CompoundTag malformed = validEmptyRoot();
        malformed.putInt("energy", ElectrolyzerBlockEntity.ENERGY_CAPACITY + 1);
        assertPreserved(malformed, false);
    }

    @Test
    void rootLargerThanSixtyFourKibIsRejectedBeforeSchemaInterpretation() {
        CompoundTag oversized = new CompoundTag();
        oversized.putInt("schema_version", ElectrolyzerRecipeSpec.CURRENT_SCHEMA_VERSION + 1);
        oversized.put("payload", new ByteArrayTag(new byte[65_536]));

        ElectrolyzerPersistence.DecodeResult decoded = ElectrolyzerPersistence.decode(parentWith(oversized));

        assertTrue(decoded.invalid());
        assertTrue(decoded.blockingInvalid());
        assertFalse(decoded.future());
        assertEquals(oversized, decoded.preservedData());
    }

    @Test
    void completedBoundaryIsRecoverableButInactiveRecipeIdentityIsRejected() {
        CompoundTag recoverable = validEmptyRoot();
        recoverable.putInt("progress", ElectrolyzerRecipeSpec.MAX_PROCESSING_TICKS);
        recoverable.putString("active_recipe", "advancedrocketrycommunity:electrolyzer_water");
        ElectrolyzerPersistence.DecodeResult completed = ElectrolyzerPersistence.decode(parentWith(recoverable));
        assertFalse(completed.invalid());
        assertEquals(ElectrolyzerRecipeSpec.MAX_PROCESSING_TICKS, completed.progress());

        CompoundTag inactive = validEmptyRoot();
        inactive.putString("active_recipe", "advancedrocketrycommunity:electrolyzer_water");
        ElectrolyzerPersistence.DecodeResult rejected = ElectrolyzerPersistence.decode(parentWith(inactive));
        assertTrue(rejected.blockingInvalid());
        assertEquals(inactive, rejected.preservedData());
        assertNull(rejected.activeRecipeId());
    }

    @Test
    void unexpectedOrMistypedNestedFieldsArePreservedAndBlocked() {
        CompoundTag unexpected = validEmptyRoot();
        unexpected.putString("unversioned_extension", "must-not-be-discarded");
        assertPreserved(unexpected, false);

        CompoundTag mistypedInventory = validEmptyRoot();
        mistypedInventory.getCompound("inventory").putInt("Items", 1);
        assertPreserved(mistypedInventory, false);

        CompoundTag mistypedFluid = validEmptyRoot();
        mistypedFluid.getCompound("fluid").putString("Amount", "1000");
        assertPreserved(mistypedFluid, false);
    }

    private static void assertPreserved(Tag root, boolean future) {
        ElectrolyzerPersistence.DecodeResult decoded = ElectrolyzerPersistence.decode(parentWith(root));

        assertEquals(future, decoded.future());
        assertEquals(!future, decoded.invalid());
        assertEquals(!future, decoded.blockingInvalid());
        assertEquals(root, decoded.preservedData());
        if (root instanceof CompoundTag) {
            assertNotSame(root, decoded.preservedData());
        }
    }

    private static CompoundTag validEmptyRoot() {
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", ElectrolyzerBlockEntity.SLOT_COUNT);
        inventory.put("Items", new ListTag());

        CompoundTag machine = new CompoundTag();
        machine.putInt("schema_version", ElectrolyzerRecipeSpec.CURRENT_SCHEMA_VERSION);
        machine.put("inventory", inventory);
        machine.put("fluid", new CompoundTag());
        machine.putInt("energy", 0);
        machine.putInt("progress", 0);
        return machine;
    }

    private static CompoundTag parentWith(Tag root) {
        CompoundTag parent = new CompoundTag();
        parent.put(ElectrolyzerPersistence.DATA_KEY, root.copy());
        return parent;
    }
}
