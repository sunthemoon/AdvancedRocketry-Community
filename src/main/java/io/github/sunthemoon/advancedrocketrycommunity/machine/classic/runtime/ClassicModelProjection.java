package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
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

    /** The unchanged pattern dimensions/anchor and the visual height, including any detail above its cells. */
    public record Footprint(int sizeX, int sizeY, int sizeZ, PatternPosition anchor, double renderHeight, double renderBelow) {
        public Footprint(int sizeX, int sizeY, int sizeZ, PatternPosition anchor) { this(sizeX, sizeY, sizeZ, anchor, sizeY, 0); }
        public Footprint(int sizeX, int sizeY, int sizeZ, PatternPosition anchor, double renderHeight) {
            this(sizeX, sizeY, sizeZ, anchor, renderHeight, 0);
        }
    }

    private static final Map<ResourceLocation, Footprint> FOOTPRINTS = Map.of(
            ModIdentity.id("centrifuge"), new Footprint(3, 4, 3, new PatternPosition(0, 1, 0)),
            ModIdentity.id("chemical_reactor"), new Footprint(3, 2, 2, new PatternPosition(1, 1, 0)),
            ModIdentity.id("crystallizer"), new Footprint(3, 2, 2, new PatternPosition(1, 0, 0)),
            ModIdentity.id("cutting_machine"), new Footprint(3, 1, 2, new PatternPosition(1, 0, 0), 1.505879),
            ModIdentity.id("lathe"), new Footprint(4, 2, 1, new PatternPosition(0, 1, 0)),
            ModIdentity.id("astrobody_data_processor"), new Footprint(3, 2, 2, new PatternPosition(1, 1, 0)),
            ModIdentity.id("observatory"), new Footprint(5, 5, 5, new PatternPosition(2, 1, 0), 5, 0.005001),
            ModIdentity.id("planetary_beacon"), new Footprint(3, 5, 3, new PatternPosition(1, 0, 0)),
            ModIdentity.id("solar_array"), new Footprint(3, 1, 22, new PatternPosition(1, 0, 0)));

    private ClassicModelProjection() { }

    public static boolean projects(ResourceLocation kind) { return FOOTPRINTS.containsKey(kind); }

    public static Optional<Footprint> footprint(ResourceLocation kind) { return Optional.ofNullable(FOOTPRINTS.get(kind)); }

    public static PatternTransform transform(Direction facing) {
        return new PatternTransform(switch (facing) {
            case NORTH -> PatternRotation.ZERO; case EAST -> PatternRotation.CLOCKWISE_90;
            case SOUTH -> PatternRotation.CLOCKWISE_180; case WEST -> PatternRotation.CLOCKWISE_270;
            default -> throw new IllegalArgumentException("Model horizontal facing");
        }, false);
    }

    public static AABB bounds(ResourceLocation kind, Direction facing, BlockPos controller) {
        return bounds(footprint(kind).orElseThrow(), transform(facing), controller);
    }

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
        return new AABB(minX, minY - footprint.renderBelow(), minZ,
                maxX + 1, maxY + 1 + Math.max(0, footprint.renderHeight() - footprint.sizeY()), maxZ + 1);
    }
}
