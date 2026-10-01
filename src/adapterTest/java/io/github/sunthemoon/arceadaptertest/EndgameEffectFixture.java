package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 1.8 consumer: a claim-mod-like listener that vetoes endgame effects of devices whose owner is not trusted on a
 * claimed area. It sees only the public event and may only cancel it.
 */
final class EndgameEffectFixture {
    /** Owners whose effects this fixture vetoes. */
    static final Set<UUID> VETOED_OWNERS = ConcurrentHashMap.newKeySet();
    static volatile EndgameEffectEvent lastSeen;

    private EndgameEffectFixture() {
    }

    static void onEffect(EndgameEffectEvent event) {
        lastSeen = event;
        if (VETOED_OWNERS.contains(event.ownerId())) {
            event.setCanceled(true);
        }
    }
}
