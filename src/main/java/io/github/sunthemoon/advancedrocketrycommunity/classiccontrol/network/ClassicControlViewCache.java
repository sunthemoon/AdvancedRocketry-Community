package io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client display cache for {@link ClassicControlViewPacket}: the last view received. A screen shows it only when it
 * names that screen's container and kind; closing the screen and logging out clear it.
 */
public final class ClassicControlViewCache {
    private static final AtomicReference<Optional<ClassicControlViewPacket>> VIEW =
            new AtomicReference<>(Optional.empty());

    private ClassicControlViewCache() {
    }

    public static void accept(ClassicControlViewPacket view) {
        VIEW.set(Optional.of(view));
    }

    public static Optional<ClassicControlViewPacket> view(int containerId, ClassicControlKind kind) {
        return VIEW.get().filter(view -> view.containerId() == containerId && view.kind() == kind);
    }

    /** Clears the view of one container, so a late close cannot drop the next menu's view. */
    public static void clear(int containerId) {
        VIEW.updateAndGet(current -> current.filter(view -> view.containerId() != containerId));
    }

    public static void clear() {
        VIEW.set(Optional.empty());
    }
}
