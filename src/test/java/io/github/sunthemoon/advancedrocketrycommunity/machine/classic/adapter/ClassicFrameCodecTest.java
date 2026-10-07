package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassicFrameCodecTest {
    @Test void outgoingControllerProjectionHasExactRootInventoryAndBounds() {
        CompoundTag root = projection(); assertTrue(ClassicFrameCodec.preflightController(root));
        root.putString("vanilla", "not managed"); assertFalse(ClassicFrameCodec.preflightController(root));
        root.remove("vanilla"); root.remove(ClassicRootBundle.MARKER); assertFalse(ClassicFrameCodec.preflightController(root));
        root = projection(); root.put(ClassicRootBundle.MACHINE, IntTag.valueOf(1)); assertFalse(ClassicFrameCodec.preflightController(root));
        root = projection(); root.getCompound(ClassicRootBundle.MACHINE).putByteArray("bytes", new byte[65_536]);
        assertFalse(ClassicFrameCodec.preflightController(root));
    }

    @Test void hatchProjectionRefusesForeignTypeAndDeepOrOversizedShapes() {
        assertFalse(ClassicFrameCodec.preflightHatch(IntTag.valueOf(1)));
        CompoundTag root = new CompoundTag(); assertTrue(ClassicFrameCodec.preflightHatch(root));
        root.putByteArray("bytes", new byte[4_096]); assertFalse(ClassicFrameCodec.preflightHatch(root));
        root = new CompoundTag(); CompoundTag child = root;
        for (int n = 0; n < 14; n++) { CompoundTag next = new CompoundTag(); child.put("child", next); child = next; }
        assertFalse(ClassicFrameCodec.preflightHatch(root));
    }

    @Test void rawRetentionShapeIsNotASupportedControllerOrHatchAdmission() {
        assertThrows(NullPointerException.class, () -> ClassicFrameCodec.encodeController(null, null));
        assertThrows(NullPointerException.class, () -> ClassicFrameCodec.encodeHatch(null, null));
        assertThrows(NullPointerException.class, () -> ClassicFrameCodec.decodeController(projection(), null, null, null, null));
        assertThrows(NullPointerException.class, () -> ClassicFrameCodec.decodeHatch(new CompoundTag(), null, null, null));
    }

    @Test void machinePreflightInspectsAllScalarFieldsWithoutNativeOwnerAdmission() {
        CompoundTag raw = machine(); CompoundTag before = raw.copy();
        var value = ClassicMachineCodec.preflight(raw);
        assertEquals(new UUID(1, 2), value.machineId()); assertTrue(value.nativePlan().isEmpty());
        assertTrue(value.assignments().isEmpty()); assertEquals(before, raw);
        for (java.util.function.Consumer<CompoundTag> change : List.<java.util.function.Consumer<CompoundTag>>of(
                root -> root.putIntArray("machine_uuid", new int[] {1}),
                root -> root.putInt("generation", 0), root -> root.putLong("generation", -1),
                root -> root.putInt("rotation", 45), root -> root.putString("extra", "value"),
                root -> root.getCompound("process").putString("extra", "value"),
                root -> root.putString("last_applied_transaction", new UUID(1, 2).toString()),
                root -> root.put("drop_quarantine", new CompoundTag()))) {
            CompoundTag wrong = machine(); change.accept(wrong); CompoundTag retained = wrong.copy();
            assertThrows(IllegalArgumentException.class, () -> ClassicMachineCodec.preflight(wrong));
            assertEquals(retained, wrong);
        }
    }

    @Test void journalPreflightRejectsUnknownCoercedAndIncompleteQuantityFieldsWithoutNativeAdmission() {
        assertTrue(ClassicFrameCodec.preflightJournal(new CompoundTag()).isEmpty());
        CompoundTag parent = journal(); CompoundTag before = parent.copy();
        assertTrue(ClassicFrameCodec.preflightJournal(parent).isPresent()); assertEquals(before, parent);
        for (java.util.function.Consumer<CompoundTag> change : List.<java.util.function.Consumer<CompoundTag>>of(
                root -> root.putString("transaction_id", "1-2-3-4-5"),
                root -> root.putString("definition_id", "lathe"), root -> root.putInt("port_revision", 0),
                root -> root.putString("before_fingerprint", "0".repeat(64)), root -> root.putString("phase", "unknown"),
                root -> root.putString("extra", "value"), root -> root.getCompound("before").putInt("revision", 0),
                root -> root.getCompound("before").putString("extra", "value"),
                root -> root.getCompound("before").remove("entries"),
                root -> root.getCompound("after").putLong("revision", 2))) {
            CompoundTag wrong = journal(); change.accept(wrong.getCompound(ClassicRootBundle.JOURNAL));
            CompoundTag retained = wrong.copy();
            assertThrows(IllegalArgumentException.class, () -> ClassicFrameCodec.preflightJournal(wrong));
            assertEquals(retained, wrong);
        }
    }

    private static CompoundTag journal() {
        var before = new io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot(0, java.util.Map.of());
        var after = new io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot(1, java.util.Map.of());
        var value = new io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal(
                1, new UUID(3, 4), new UUID(1, 2), "advancedrocketrycommunity:lathe", 0, before.fingerprint(), before, after,
                io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessJournalPhase.PREPARED);
        CompoundTag parent = new CompoundTag();
        parent.put(ClassicRootBundle.JOURNAL,
                io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence.encode(value));
        return parent;
    }

    private static CompoundTag machine() {
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1);
        root.putString("machine_kind", "advancedrocketrycommunity:lathe"); root.putUUID("machine_uuid", new UUID(1, 2));
        root.putString("owner_level", "minecraft:overworld");
        CompoundTag position = new CompoundTag(); position.putInt("x", 0); position.putInt("y", 0); position.putInt("z", 0);
        root.put("owner_position", position); root.putLong("generation", 0); root.putInt("rotation", 0);
        root.putString("formation_state", "UNFORMED"); root.put("assignments", new ListTag()); root.putLong("batch_ordinal", 0);
        CompoundTag refusal = new CompoundTag(); refusal.putString("code", "not_formed"); refusal.putString("subject", "");
        root.put("refusal", refusal); CompoundTag process = new CompoundTag(); process.putString("phase", "idle");
        root.put("process", process); return root;
    }

    private static CompoundTag projection() {
        CompoundTag root = new CompoundTag();
        for (String key : new String[] {ClassicRootBundle.MACHINE, ClassicRootBundle.RESOURCES, ClassicRootBundle.MARKER}) {
            CompoundTag value = new CompoundTag(); value.putInt("schema_version", 1); root.put(key, value);
        }
        return root; // a preflight shape, intentionally not a valid typed frame
    }
}
