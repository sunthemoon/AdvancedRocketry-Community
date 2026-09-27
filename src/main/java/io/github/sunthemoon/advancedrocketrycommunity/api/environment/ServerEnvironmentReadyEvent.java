package io.github.sunthemoon.advancedrocketrycommunity.api.environment;

import java.util.Objects;
import net.minecraftforge.eventbus.api.Event;

/**
 * Non-cancelable FORGE-bus notification emitted during logical server startup on its thread.
 * Register the listener during mod construction; replace retained handles on every new server.
 * No client-only resource load emits this event. The supplied handle expires at server stopping.
 */
public final class ServerEnvironmentReadyEvent extends Event {
    private final EnvironmentQueries queries;

    public ServerEnvironmentReadyEvent(EnvironmentQueries queries) {
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    public EnvironmentQueries queries() {
        return queries;
    }
}
