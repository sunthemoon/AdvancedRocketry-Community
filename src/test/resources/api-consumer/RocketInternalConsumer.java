package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketAdaptersEvent;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapter;

public final class RocketInternalConsumer {
    private RocketBlockEntityAdapter internal;

    public static RegisterRocketAdaptersEvent publicEvent(RegisterRocketAdaptersEvent event) {
        return event;
    }
}
