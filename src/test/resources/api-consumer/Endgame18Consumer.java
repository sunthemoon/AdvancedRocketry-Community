package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersion;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersions;

/** Compiled only against the three-constant 1.8 classifier surface, then loaded on the newer host. */
public final class Endgame18Consumer {
    public static int compiledMinor() { return 8; }
    public static int hostMinor() { return ApiVersions.current().minor(); }
    public static void protect(EndgameEffectEvent event) {
        switch (event.effect()) {
            case BLOCK_BREAK: event.setCanceled(true); break;
            case ENTITY_GRAVITY: break;
            case TELEPORT: break;
            default: event.setCanceled(true);
        }
        event.systemId(); event.ownerId(); event.actorId(); event.level(); event.min(); event.max();
    }
    public static String exhaustive(EndgameEffectEvent event) {
        return switch (event.effect()) {
            case BLOCK_BREAK -> "break";
            case ENTITY_GRAVITY -> "gravity";
            case TELEPORT -> "teleport";
        };
    }
}
