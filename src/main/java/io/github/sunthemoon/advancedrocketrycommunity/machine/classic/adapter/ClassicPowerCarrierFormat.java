package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Data-only parsing of an exclusively owned stable root; never Item or operation admission. */
final class ClassicPowerCarrierFormat {
    // Existing named-empty-root accounting; never Tag.sizeInBytes().
    private static final ClassicNbtLimits LIMIT = new ClassicNbtLimits(1_024, 4, 16);
    private static final Set<String> READY_KEYS = Set.of("schema_version", "phase", "energy");
    private static final Set<String> PENDING_KEYS = Set.of("schema_version", "phase", "energy", "operation_id", "entity_id");

    enum Phase { READY, PENDING }

    /** Detached format values only; pending energy is not a placeable or spendable reservoir. */
    record FormatValue(Phase phase, int energy, Optional<UUID> operationId, Optional<UUID> entityId) {
        FormatValue {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(entityId, "entityId");
            ClassicValueChecks.require(energy >= 0 && energy <= 10_000, "Carrier energy");
            ClassicValueChecks.require(operationId.isPresent() == (phase == Phase.PENDING)
                    && entityId.isPresent() == (phase == Phase.PENDING), "Carrier phase identity presence");
        }
    }

    static Optional<FormatValue> read(Tag root) {
        try {
            return readBounded(root);
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static Optional<FormatValue> readBounded(Tag root) {
        if (root == null || root.getClass() != CompoundTag.class
                || !ClassicNbtShape.fits(root, LIMIT)
                || !BoundedNbt.fits(root, LIMIT.bytes(), LIMIT.depth(), LIMIT.nodes())) {
            return Optional.empty();
        }
        CompoundTag value = (CompoundTag) root;
        if (!exact(value.get("schema_version"), IntTag.class)
                || !exact(value.get("phase"), StringTag.class)
                || !exact(value.get("energy"), IntTag.class)
                || value.getInt("schema_version") != 1) {
            return Optional.empty();
        }
        int energy = value.getInt("energy");
        if (energy < 0 || energy > 10_000) { return Optional.empty(); }
        String phase = value.getString("phase");
        if (phase.equals("ready") && value.getAllKeys().equals(READY_KEYS)) {
            return Optional.of(new FormatValue(Phase.READY, energy, Optional.empty(), Optional.empty()));
        }
        if (!phase.equals("pending") || !value.getAllKeys().equals(PENDING_KEYS)
                || !exact(value.get("operation_id"), IntArrayTag.class)
                || !exact(value.get("entity_id"), IntArrayTag.class)) {
            return Optional.empty();
        }
        int[] operation = ((IntArrayTag) value.get("operation_id")).getAsIntArray();
        int[] entity = ((IntArrayTag) value.get("entity_id")).getAsIntArray();
        if (operation.length != 4 || entity.length != 4) { return Optional.empty(); }
        return Optional.of(new FormatValue(Phase.PENDING, energy, Optional.of(uuid(operation)), Optional.of(uuid(entity))));
    }

    private static boolean exact(Tag value, Class<? extends Tag> type) {
        return value != null && value.getClass() == type;
    }

    private static UUID uuid(int[] words) {
        return new UUID((long) words[0] << 32 | words[1] & 0xffff_ffffL,
                (long) words[2] << 32 | words[3] & 0xffff_ffffL);
    }

    private ClassicPowerCarrierFormat() { }
}
