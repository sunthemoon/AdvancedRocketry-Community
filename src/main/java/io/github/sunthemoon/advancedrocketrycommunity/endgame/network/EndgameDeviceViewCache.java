package io.github.sunthemoon.advancedrocketrycommunity.endgame.network;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client display cache for the ADR-054 section 4 device view: the last view received. A screen shows it only when
 * it names that screen's container; the screen clears it when it closes.
 */
public final class EndgameDeviceViewCache {
    private static final AtomicReference<Optional<EndgameDeviceView>> VIEW = new AtomicReference<>(Optional.empty());

    private EndgameDeviceViewCache() {
    }

    public static void accept(EndgameDeviceView view) {
        VIEW.set(Optional.of(view));
    }

    public static Optional<EndgameDeviceView> view(int containerId) {
        return VIEW.get().filter(view -> view.containerId() == containerId);
    }

    public static void clear() {
        VIEW.set(Optional.empty());
    }
}
