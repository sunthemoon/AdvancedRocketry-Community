package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * ADR-065 section 5.1: how a station-management actor reaches the station. A command is the player's own
 * source, located at their own position. A controller screen is the exact controller block the server opened
 * the menu on: its Level, position and block are server-side menu state, never a client ray or coordinate.
 */
public record StationActorContext(boolean issuedByPlayer, Optional<Controller> controller) {
    public StationActorContext {
        Objects.requireNonNull(controller, "controller");
    }

    /** A command from the player's own source; {@code issuedByPlayer} is the existing source check. */
    public static StationActorContext command(boolean issuedByPlayer) {
        return new StationActorContext(issuedByPlayer, Optional.empty());
    }

    /** The screen of the controller block at {@code position} in {@code level}. */
    public static StationActorContext controller(ResourceKey<Level> level, BlockPos position, Block block) {
        return new StationActorContext(true, Optional.of(new Controller(level, position.immutable(), block)));
    }

    public boolean viaController() {
        return controller.isPresent();
    }

    /** The server-side identity of one controller block. */
    public record Controller(ResourceKey<Level> level, BlockPos position, Block block) {
        public Controller {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(block, "block");
        }
    }
}
