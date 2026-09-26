package io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere;

import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;

/**
 * Logical-server-only functions over detached, owned oxygen data. No world, item,
 * capability, file, network or external mutable state may be read or changed.
 * Capacity is 2,000 units; one unit supplies twenty vacuum ticks. The host commits
 * validated changes, not the provider. Calls must return within 5 ms; this is a
 * returned-time check, not preemption or a sandbox for arbitrary mod code.
 */
public interface SuitOxygenProvider {
    /**
     * Pure read, without changing or retaining data. Empty means unsupported/invalid
     * item data and prevents use/refill. A new empty compound must read as present
     * zero. Null or present values outside 0..2000 are provider faults.
     */
    OptionalInt readOxygen(CompoundTag data);

    /**
     * Returns data representing exactly oxygenUnits, preserving other owned fields.
     * May mutate this detached argument. The host bounds and independently reads the
     * result before copying/committing it. Null, unsupported or mismatched results
     * fail without updating the item. No input/output references may be retained.
     */
    CompoundTag writeOxygen(CompoundTag data, int oxygenUnits);
}
