package io.github.sunthemoon.advancedrocketrycommunity.endgame.protection;

import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * ADR-054 section 5 steps 1 to 6, evaluated in order for every batch on the server thread; the first failure stops
 * the batch with its code and nothing changes. Step 7 (a standard break event per block) belongs to the system that
 * breaks blocks (ADR-055) and runs after this chain. The world is read only through {@link View}, so the order is
 * testable without a running server.
 */
public final class EndgameProtection {
    private EndgameProtection() {
    }

    public static EndgameCode check(Batch batch, View view) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(view, "view");
        BlockPos min = batch.min();
        BlockPos max = batch.max();
        // 1. Loaded: every touched chunk is FULL already; nothing loads a chunk (section 12).
        if (batch.requireLoaded() && !view.chunksFull(min, max)) {
            return EndgameCode.TARGET_UNLOADED;
        }
        // 2. Bounds: world border and build height.
        if (!view.insideWorld(min, max)) {
            return EndgameCode.TARGET_OUT_OF_BOUNDS;
        }
        // 3. Protected zones, unless the device owner is on the zone's allow list.
        for (ProtectedZone zone : view.zones()) {
            if (zone.intersects(batch.level().location(), min.getX(), min.getZ(), max.getX(), max.getZ())
                    && !zone.allows(batch.owner())) {
                return EndgameCode.TARGET_PROTECTED;
            }
        }
        // 4. Stations: in the Space Level, inside one committed region where the owner has BUILD access.
        if (view.spaceLevel() && !view.stationsAllow(batch.owner(), min, max)) {
            return EndgameCode.TARGET_PROTECTED;
        }
        // 5. Spawn protection on a dedicated server; never bypassed for operators, never tied to the op list.
        if (view.spawnSquare().filter(square -> square.intersects(min, max)).isPresent()) {
            return EndgameCode.TARGET_PROTECTED;
        }
        // 6. The public, cancelable API event.
        EndgameEffectEvent event = new EndgameEffectEvent(batch.system().key(), batch.effect(), batch.owner(),
                batch.actor(), batch.level(), min, max);
        if (view.cancelled(event)) {
            return EndgameCode.TARGET_PROTECTED;
        }
        return EndgameCode.OK;
    }

    /**
     * One batch: at most one tick of work for one device, inside the inclusive box {@code min..max}.
     *
     * @param requireLoaded false only for effects that modify no chunk, such as a gravity field (ADR-058 section 3)
     */
    public record Batch(EndgameSystem system, EndgameEffect effect, UUID owner, Optional<UUID> actor,
                        ResourceKey<Level> level, BlockPos min, BlockPos max, boolean requireLoaded) {
        public Batch {
            Objects.requireNonNull(system, "system");
            Objects.requireNonNull(effect, "effect");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(level, "level");
            min = Objects.requireNonNull(min, "min").immutable();
            max = Objects.requireNonNull(max, "max").immutable();
            if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
                throw new IllegalArgumentException("The batch box is inverted");
            }
        }
    }

    /** The vanilla spawn protection square: {@code |x - cx| <= radius} and {@code |z - cz| <= radius}. */
    public record SpawnSquare(int centerX, int centerZ, int radius) {
        public SpawnSquare {
            if (radius < 1) {
                throw new IllegalArgumentException("A spawn square has a positive radius");
            }
        }

        public boolean intersects(BlockPos min, BlockPos max) {
            return (long) min.getX() <= (long) centerX + radius && (long) max.getX() >= (long) centerX - radius
                    && (long) min.getZ() <= (long) centerZ + radius && (long) max.getZ() >= (long) centerZ - radius;
        }
    }

    /** What the chain reads; the Forge implementation never loads a chunk. */
    public interface View {
        boolean chunksFull(BlockPos min, BlockPos max);

        boolean insideWorld(BlockPos min, BlockPos max);

        Collection<ProtectedZone> zones();

        boolean spaceLevel();

        boolean stationsAllow(UUID owner, BlockPos min, BlockPos max);

        /** The protected square, only on a dedicated server in the Overworld; integrated and LAN servers have none. */
        Optional<SpawnSquare> spawnSquare();

        /** Posts the event and reports whether a listener cancelled it. */
        boolean cancelled(EndgameEffectEvent event);
    }
}
