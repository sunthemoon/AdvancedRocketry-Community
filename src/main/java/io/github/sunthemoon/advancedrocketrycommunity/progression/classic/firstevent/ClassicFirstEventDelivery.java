package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/**
 * W44 section 3.3: the world-first delivery service. The sources ask it for the election disposition immediately
 * before each potentially first action, and notify it after a source change.
 */
public final class ClassicFirstEventDelivery {
    private ClassicFirstEventDelivery() {
    }

    /** Refreshed on the server thread before each election attempt; nothing unavailable ever yields ALLOW. */
    public static FirstEventDisposition disposition(MinecraftServer server, FirstEventKind kind,
                                                    FirstEventSourceView source, FirstEventOutbox ram) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            return FirstEventDisposition.DEFER;
        }
        return FirstEventElection.disposition(kind, source, ram, ClassicProgressionSavedData.get(server));
    }

    /** A source committed or published a change of {@code kind}; the next END tick re-reads its durable view. */
    public static void sourceChanged(MinecraftServer server, FirstEventKind kind) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(kind, "kind");
    }
}
