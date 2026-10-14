package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * ADR-066 section 7.1: the transient formed flag that a physical-client machine model needs, and the volume it
 * draws in. Only controllers with a catalogued model project it; every other controller's update tag is unchanged.
 * The flag never enters saved data and the server never reads it back.
 */
public final class ClassicModelProjection {
    public static final String FORMED = "formed";

    /** The generated pattern's size and controller anchor, which the model covers exactly. */
    public record Footprint(int sizeX, int sizeY, int sizeZ, PatternPosition anchor) { }

    private static final Map<ResourceLocation, Footprint> FOOTPRINTS = Map.of(
            ModIdentity.id("centrifuge"), new Footprint(3, 4, 3, new PatternPosition(0, 1, 0)));

    private ClassicModelProjection() { }

    public static boolean projects(ResourceLocation kind) { return FOOTPRINTS.containsKey(kind); }

    public static Optional<Footprint> footprint(ResourceLocation kind) { return Optional.ofNullable(FOOTPRINTS.get(kind)); }

    public static void encode(CompoundTag tag, boolean formed) {
        if (formed) { tag.putBoolean(FORMED, true); }
    }

    /** NBT has no boolean type. Only the canonical boolean byte 1 means formed. */
    public static boolean decode(@Nullable CompoundTag tag) {
        return tag != null && tag.contains(FORMED, Tag.TAG_BYTE) && tag.getByte(FORMED) == 1;
    }

    /** The world box of every footprint cell for a controller at {@code controller} under {@code transform}. */
    public static AABB bounds(Footprint footprint, PatternTransform transform, BlockPos controller) {
        var origin = new PatternPosition(controller.getX(), controller.getY(), controller.getZ());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x : new int[] {0, footprint.sizeX() - 1}) {
            for (int y : new int[] {0, footprint.sizeY() - 1}) {
                for (int z : new int[] {0, footprint.sizeZ() - 1}) {
                    var cell = transform.localToWorld(new PatternPosition(x, y, z), footprint.anchor(), origin);
                    minX = Math.min(minX, cell.x()); minY = Math.min(minY, cell.y()); minZ = Math.min(minZ, cell.z());
                    maxX = Math.max(maxX, cell.x()); maxY = Math.max(maxY, cell.y()); maxZ = Math.max(maxZ, cell.z());
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
