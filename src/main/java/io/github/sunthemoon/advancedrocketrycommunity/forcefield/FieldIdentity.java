package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Exact physical claim identity; source availability is not part of airtightness. */
public record FieldIdentity(UUID projector, UUID owner, long generation, ResourceKey<Level> level,
                            BlockPos source, Direction direction) {
    static final UUID UNBOUND_OWNER = new UUID(0, 0);
    public FieldIdentity {
        Objects.requireNonNull(projector);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(level);
        Objects.requireNonNull(direction);
        source = Objects.requireNonNull(source).immutable();
        if (generation < 1 || level.location().toString().length() > 255
                || Math.abs((long) source.getX()) > 30_000_000 || Math.abs((long) source.getZ()) > 30_000_000
                || source.getY() < -2048 || source.getY() > 2047) {
            throw new IllegalArgumentException("Invalid force-field identity");
        }
    }

    public Key key() { return new Key(projector, generation); }
    public BlockPos target(int offset) { return source.relative(direction, checkedOffset(offset)); }

    public int offset(BlockPos position) {
        int x = position.getX() - source.getX();
        int y = position.getY() - source.getY();
        int z = position.getZ() - source.getZ();
        int distance = x * direction.getStepX() + y * direction.getStepY() + z * direction.getStepZ();
        return distance >= 1 && distance <= 32 && x == distance * direction.getStepX()
                && y == distance * direction.getStepY() && z == distance * direction.getStepZ() ? distance : 0;
    }

    public static int bit(int offset) { return 1 << (checkedOffset(offset) - 1); }
    private static int checkedOffset(int offset) {
        if (offset < 1 || offset > 32) { throw new IllegalArgumentException("Claim offset outside 1..32"); }
        return offset;
    }
    public record Key(UUID projector, long generation) { }
}
