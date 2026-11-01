package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** Transient presentation only: no validator, persistence, ownership or operation state. */
public final class K4ModelProjection {
    private boolean formed;

    public boolean formed() { return formed; }
    public void read(CompoundTag tag) { formed = ClassicModelProjection.decode(tag); }
    public void publish(BlockEntity controller, boolean next) {
        if (formed == next) { return; }
        formed = next;
        if (controller.getLevel() instanceof ServerLevel server && !controller.isRemoved()) {
            server.sendBlockUpdated(controller.getBlockPos(), controller.getBlockState(), controller.getBlockState(), 3);
        }
    }

    public static boolean projects(ResourceLocation kind) {
        return kind.getNamespace().equals(ModIdentity.MOD_ID) && switch (kind.getPath()) {
            case "black_hole_generator", "orbital_laser_drill", "precision_assembler", "railgun", "rolling_machine", "elevator_anchor" -> true;
            default -> false;
        };
    }

    /** Fixed render-only union of the legacy idle hull and modern pattern, not collision or match bounds. */
    public static AABB bounds(String kind, Direction facing, BlockPos controller) {
        AABB north = switch (kind) {
            case "black_hole_generator" -> new AABB(-1, -4, 0, 2, 3, 3);
            case "orbital_laser_drill" -> new AABB(-2, -1, -2.00005, 10, 3.25, 7.00005);
            case "precision_assembler" -> new AABB(-1, 0, 0, 4, 3, 4);
            case "railgun" -> new AABB(-4, 0, -1, 5, 11, 8);
            case "rolling_machine" -> new AABB(-2, 0, 0, 4, 3, 3);
            case "elevator_anchor" -> new AABB(-4, -1, -2, 5, 1.5, 10);
            default -> throw new IllegalArgumentException("Unknown K4 model " + kind);
        };
        AABB rotated = switch (facing) {
            case NORTH -> north;
            case EAST -> new AABB(1 - north.maxZ, north.minY, north.minX, 1 - north.minZ, north.maxY, north.maxX);
            case SOUTH -> new AABB(1 - north.maxX, north.minY, 1 - north.maxZ, 1 - north.minX, north.maxY, 1 - north.minZ);
            case WEST -> new AABB(north.minZ, north.minY, 1 - north.maxX, north.maxZ, north.maxY, 1 - north.minX);
            default -> throw new IllegalArgumentException("Horizontal model facing required");
        };
        return rotated.move(controller);
    }
}
