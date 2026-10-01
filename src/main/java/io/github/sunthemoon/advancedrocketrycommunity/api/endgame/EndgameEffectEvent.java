package io.github.sunthemoon.advancedrocketrycommunity.api.endgame;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * Cancelable FORGE-bus event posted on the server thread before an endgame device affects the world (API 1.8). It
 * describes one batch: at most one tick of work for one device, inside the box {@code min..max} of one Level.
 * Listeners, such as claim or protection mods, may only cancel it; a cancelled batch changes nothing and the device
 * reports {@code TARGET_PROTECTED}. It exposes no device object and offers no way to change the effect.
 */
@Cancelable
public final class EndgameEffectEvent extends Event {
    private final ResourceLocation systemId;
    private final EndgameEffect effect;
    private final UUID ownerId;
    private final UUID actorId;
    private final ResourceKey<Level> level;
    private final BlockPos min;
    private final BlockPos max;

    public EndgameEffectEvent(ResourceLocation systemId, EndgameEffect effect, UUID ownerId, Optional<UUID> actorId,
                              ResourceKey<Level> level, BlockPos min, BlockPos max) {
        this.systemId = Objects.requireNonNull(systemId, "systemId");
        this.effect = Objects.requireNonNull(effect, "effect");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.actorId = Objects.requireNonNull(actorId, "actorId").orElse(null);
        this.level = Objects.requireNonNull(level, "level");
        this.min = Objects.requireNonNull(min, "min").immutable();
        this.max = Objects.requireNonNull(max, "max").immutable();
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            throw new IllegalArgumentException("The batch box is inverted");
        }
    }

    /** The endgame system, for example {@code advancedrocketrycommunity:laser_drill}. */
    public ResourceLocation systemId() {
        return systemId;
    }

    public EndgameEffect effect() {
        return effect;
    }

    /** The owner of the device that causes the effect; for a teleport, the departing endpoint's owner. */
    public UUID ownerId() {
        return ownerId;
    }

    /** The player whose intent caused the effect, such as a ride's rider, or empty for autonomous work. */
    public Optional<UUID> actorId() {
        return Optional.ofNullable(actorId);
    }

    public ResourceKey<Level> level() {
        return level;
    }

    /** The lowest corner of the batch box, inclusive. */
    public BlockPos min() {
        return min;
    }

    /** The highest corner of the batch box, inclusive. */
    public BlockPos max() {
        return max;
    }
}
