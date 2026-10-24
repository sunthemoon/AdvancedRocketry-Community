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
        data.getCompound("snapshot").putString("snapshot_id", UUID.randomUUID().toString());
        assertTrue(absent(slot(unrelated), logical), "Same cargo hash alone cannot identify another rocket");
    }

    @Test void nestedCopiesAndLogicalCopiesAreNotMissed() throws Exception {
        var frozen = entity(); var duplicate = entity(); duplicate.putUUID("UUID", UUID.randomUUID());
        assertFalse(present(slot(frozen, duplicate), frozen));
        var parent = new CompoundTag(); parent.putString("id", "minecraft:boat");
        parent.putUUID("UUID", UUID.randomUUID()); var passengers = new ListTag(); passengers.add(duplicate); parent.put("Passengers", passengers);
        assertFalse(absent(slot(parent), logical));
        duplicate.getCompound("RocketEntityData").getCompound("snapshot").putString("snapshot_id", UUID.randomUUID().toString());
        assertFalse(absent(slot(duplicate), logical));
    }

    @Test void aNestedOnlyCopyCannotProveAnUnmountedNativeSurvivor() throws Exception {
        var frozen = entity(); var parent = new CompoundTag(); parent.putString("id", "minecraft:boat");
        parent.putUUID("UUID", UUID.randomUUID()); var passengers = new ListTag(); passengers.add(frozen);
        parent.put("Passengers", passengers);
        assertFalse(present(slot(parent), frozen), "A nested image is not the frozen unmounted survivor");
    }

    @Test void recoveryCopyCensusRequiresEveryExactPhysicalImageWithoutExtraOrMutatedCopies() throws Exception {
        var frozen = entity(); var otherId = UUID.randomUUID(); var copy = frozen.copy(); copy.putUUID("UUID", otherId);
        var ids = java.util.List.of(entity, otherId);
        assertTrue(copies(slot(frozen, copy), frozen, ids));
        assertFalse(copies(slot(frozen), frozen, ids));
        assertFalse(copies(slot(frozen, copy), frozen, java.util.List.of(entity)));
        assertFalse(copies(slot(frozen, frozen.copy()), frozen, ids));
        var mutated = copy.copy(); mutated.getCompound("RocketEntityData").putInt("fuel", 999);
        assertFalse(copies(slot(frozen, mutated), frozen, ids));
        var parent = new CompoundTag(); parent.putString("id", "minecraft:boat");
        var passengers = new ListTag(); passengers.add(copy); parent.put("Passengers", passengers);
        assertFalse(copies(slot(frozen, parent), frozen, ids));
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
    private boolean copies(CompoundTag root, CompoundTag frozen, java.util.List<UUID> ids) throws Exception {
        return (boolean) type().getDeclaredMethod("presentCopies", CompoundTag.class, UUID.class, UUID.class, UUID.class,
                CompoundTag.class, java.util.List.class).invoke(null, root, logical, snapshot, entity, frozen, ids);
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
        var structure = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot.create(snapshot,
                net.minecraft.resources.ResourceLocation.tryParse("minecraft:overworld"),
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(0, 64, 0),
                java.util.List.of(new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock(
                        new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(0, 0, 0),
                        new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState(
                                net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), java.util.Map.of()))),
                java.util.List.of(), new io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats(1, 10, 0, 0, 0, 0, 0, 0), 0);
        data.put("snapshot", io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec.encode(structure));
        tag.put("RocketEntityData", data); return tag;
    }
    private static CompoundTag slot(CompoundTag... entities) {
        var root = new CompoundTag(); var list = new ListTag(); for (var entity : entities) { list.add(entity); } root.put("Entities", list); return root;
    }
}
