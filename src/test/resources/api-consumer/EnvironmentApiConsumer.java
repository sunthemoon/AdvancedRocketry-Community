package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentQueries;
import io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.api.environment.ServerEnvironmentReadyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class EnvironmentApiConsumer {
    private EnvironmentQueries queries;

    public void ready(ServerEnvironmentReadyEvent event) {
        queries = event.queries();
    }

    public boolean earthHasConfiguredAir() {
        return queries.at(Level.OVERWORLD, BlockPos.ZERO)
                .filter(value -> value.locus() == EnvironmentSnapshot.Locus.SURFACE)
                .flatMap(EnvironmentSnapshot::atmosphere).map(value -> value.breathable()).orElse(false);
    }
}
