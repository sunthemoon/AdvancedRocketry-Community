package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;

/** A claim mod's listener: it may only read the batch and cancel it. */
public final class EndgameApiConsumer {
    public static void protect(EndgameEffectEvent event, java.util.UUID claimOwner) {
        if (event.effect() == EndgameEffect.BLOCK_BREAK && !event.ownerId().equals(claimOwner)
                && event.min().getY() >= event.level().location().getPath().length()) {
            event.setCanceled(true);
        }
        event.actorId().ifPresent(actor -> event.systemId().getNamespace());
        event.max();
    }
}
