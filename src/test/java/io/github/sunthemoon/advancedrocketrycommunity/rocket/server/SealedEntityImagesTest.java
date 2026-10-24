package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealedEntityImagesTest {
    private final UUID logical = UUID.randomUUID(), snapshot = UUID.randomUUID(), entity = UUID.randomUUID();
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactFrozenEntityIncludesOwnerFuelCargoAndPhysicalIdentity() throws Exception {
        var frozen = entity(); assertTrue(present(slot(frozen), frozen));
        for (var field : new String[] {"owner", "fuel", "cargo"}) {
            var altered = frozen.copy(); altered.getCompound("RocketEntityData").putInt(field, 99);
            assertFalse(present(slot(altered), frozen));
        }
        var moved = frozen.copy(); var position = new ListTag(); position.add(DoubleTag.valueOf(1)); moved.put("Pos", position);
        assertFalse(present(slot(moved), frozen));
        var wrongType = frozen.copy(); wrongType.putString("id", "minecraft:pig");
        assertFalse(present(slot(wrongType), frozen));
    }

    @Test void absenceIncludesSnapshotIdentityWhenRestartHasNoLogicalId() throws Exception {
        var copy = entity(); copy.putUUID("UUID", UUID.randomUUID());
        copy.getCompound("RocketEntityData").putUUID("assembly_transaction_id", UUID.randomUUID());
        assertFalse(absent(slot(copy), null));
        var unrelated = entity(); unrelated.putUUID("UUID", UUID.randomUUID());
        var data = unrelated.getCompound("RocketEntityData"); data.putUUID("assembly_transaction_id", UUID.randomUUID());
        data.getCompound("snapshot").putUUID("snapshot_id", UUID.randomUUID());
        assertTrue(absent(slot(unrelated), logical), "Same cargo hash alone cannot identify another rocket");
    }

    @Test void nestedCopiesAndLogicalCopiesAreNotMissed() throws Exception {
        var frozen = entity(); var duplicate = entity(); duplicate.putUUID("UUID", UUID.randomUUID());
        assertFalse(present(slot(frozen, duplicate), frozen));
        var parent = new CompoundTag(); parent.putString("id", "minecraft:boat");
        parent.putUUID("UUID", UUID.randomUUID()); var passengers = new ListTag(); passengers.add(duplicate); parent.put("Passengers", passengers);
        assertFalse(absent(slot(parent), logical));
        duplicate.getCompound("RocketEntityData").getCompound("snapshot").putUUID("snapshot_id", UUID.randomUUID());
        assertFalse(absent(slot(duplicate), logical));
    }

    @Test void malformedListsCannotBeCoercedIntoAnEmptyNativeCensus() throws Exception {
        var root = new CompoundTag(); root.putString("Entities", "not-a-list"); assertFalse(absent(root, logical));
        var wrong = new ListTag(); wrong.add(DoubleTag.valueOf(1)); root.put("Entities", wrong); assertFalse(absent(root, logical));
        var parent = new CompoundTag(); parent.putString("id", "minecraft:boat"); parent.putString("Passengers", "bad");
        assertFalse(absent(slot(parent), logical));
        var malformed = entity(); malformed.getCompound("RocketEntityData").getCompound("snapshot").putIntArray("snapshot_id", new int[] {1});
        assertFalse(absent(slot(malformed), null));
    }

    @Test void emptyNativeEntitySlotIsAbsenceButNotPresence() throws Exception {
        assertTrue(absent(slot(), logical)); assertFalse(present(slot(), entity()));
        var raw = new CompoundTag(); raw.putUUID("UUID", entity); raw.putString("id", "minecraft:pig");
        assertFalse(absent(slot(raw), logical));
    }

    private boolean present(CompoundTag root, CompoundTag frozen) throws Exception {
        return (boolean) type().getDeclaredMethod("present", CompoundTag.class, UUID.class, UUID.class, UUID.class, CompoundTag.class)
                .invoke(null, root, logical, snapshot, entity, frozen);
    }
    private boolean absent(CompoundTag root, UUID knownLogical) throws Exception {
        return (boolean) type().getDeclaredMethod("absent", CompoundTag.class, UUID.class, UUID.class, UUID.class)
                .invoke(null, root, knownLogical, snapshot, entity);
    }
    private static Class<?> type() throws ClassNotFoundException { return Class.forName(SealedEntityImagesTest.class.getPackageName() + ".SealedEntityImages"); }
    private CompoundTag entity() {
        var tag = new CompoundTag(); tag.putString("id", "advancedrocketrycommunity:rocket"); tag.putUUID("UUID", entity);
        var pos = new ListTag(); pos.add(DoubleTag.valueOf(0.5)); pos.add(DoubleTag.valueOf(64)); pos.add(DoubleTag.valueOf(0.5)); tag.put("Pos", pos);
        var data = new CompoundTag(); data.putUUID("assembly_transaction_id", logical); data.putInt("owner", 1); data.putInt("fuel", 2); data.putInt("cargo", 3);
        var structure = new CompoundTag(); structure.putUUID("snapshot_id", snapshot); structure.putString("content_hash", "a".repeat(64)); data.put("snapshot", structure);
        tag.put("RocketEntityData", data); return tag;
    }
    private static CompoundTag slot(CompoundTag... entities) {
        var root = new CompoundTag(); var list = new ListTag(); for (var entity : entities) { list.add(entity); } root.put("Entities", list); return root;
    }
}
