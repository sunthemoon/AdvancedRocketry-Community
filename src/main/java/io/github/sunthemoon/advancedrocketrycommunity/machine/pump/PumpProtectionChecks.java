package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.EndgameProtection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** ADR-054 steps 1-7 for one source. Pump is not an EndgameSystem enum member. */
public final class PumpProtectionChecks {
    public static PumpCode check(UUID owner, ResourceKey<Level> level, BlockPos target,
            EndgameProtection.View view, BooleanSupplier breakCancelled) {
        PumpCode region = region(owner, level, target, view);
        if (region != PumpCode.SOURCE_READY) { return region; }
        if (view.cancelled(new EndgameEffectEvent(ModIdentity.id("pump"), EndgameEffect.BLOCK_BREAK, owner,
                Optional.empty(), level, target, target)) || breakCancelled.getAsBoolean()) {
            return PumpCode.TARGET_PROTECTED;
        }
        return PumpCode.SOURCE_READY;
    }
    /** Repeat the non-callback region checks after the two external event callbacks, without reposting events. */
    public static PumpCode region(UUID owner, ResourceKey<Level> level, BlockPos target, EndgameProtection.View view) {
        if (owner == null) { return PumpCode.NO_OWNER; }
        if (!view.chunksFull(target, target)) { return PumpCode.TARGET_UNLOADED; }
        if (!view.insideWorld(target, target)) { return PumpCode.TARGET_OUT_OF_BOUNDS; }
        for (var zone : view.zones()) {
            if (zone.intersects(level.location(), target.getX(), target.getZ(), target.getX(), target.getZ())
                    && !zone.allows(owner)) { return PumpCode.TARGET_PROTECTED; }
        }
        if (view.spaceLevel() && !view.stationsAllow(owner, target, target)) { return PumpCode.TARGET_PROTECTED; }
        if (view.spawnSquare().filter(square -> square.intersects(target, target)).isPresent()) {
            return PumpCode.TARGET_PROTECTED;
        }
        return PumpCode.SOURCE_READY;
    }
    private PumpProtectionChecks() { }
}
