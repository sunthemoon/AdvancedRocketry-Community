package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ForgeProtectionView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** ADR-054 steps 1..6 for an item, whose stable ID is not an EndgameSystem device enum. */
final class LaserGunProtection {
    private LaserGunProtection() { }

    static boolean areaAllowed(ServerPlayer player, BlockPos min, BlockPos max) {
        var root = EndgameRuntime.operational().flatMap(service -> service.root());
        if (root.isEmpty()) { return false; }
        var view = new ForgeProtectionView(player.serverLevel(), root.orElseThrow().zones());
        if (!view.chunksFull(min, max) || !view.insideWorld(min, max)) { return false; }
        for (var zone : view.zones()) {
            if (zone.intersects(player.level().dimension().location(), min.getX(), min.getZ(), max.getX(), max.getZ())
                    && !zone.allows(player.getUUID())) { return false; }
        }
        if (view.spaceLevel() && !view.stationsAllow(player.getUUID(), min, max)) { return false; }
        return view.spawnSquare().filter(square -> square.intersects(min, max)).isEmpty();
    }

    static boolean breakingAllowed(ServerPlayer player, BlockPos pos) {
        if (!areaAllowed(player, pos, pos)) { return false; }
        var view = new ForgeProtectionView(player.serverLevel(), java.util.List.of());
        return !view.cancelled(new EndgameEffectEvent(ModIdentity.id("basic_laser_gun"), EndgameEffect.BLOCK_BREAK,
                player.getUUID(), Optional.of(player.getUUID()), player.level().dimension(), pos, pos));
    }
}
