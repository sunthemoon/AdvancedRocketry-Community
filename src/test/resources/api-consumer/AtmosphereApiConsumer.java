package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundaryProvider;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundaryRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.RegisterAtmosphereBoundariesEvent;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class AtmosphereApiConsumer {
    public static void register(RegisterAtmosphereBoundariesEvent event, ResourceLocation id) {
        AtmosphereBoundaryRegistrar registrar = event::register;
        AtmosphereBoundaryProvider provider = state -> AtmosphereBoundary.SEALED;
        registrar.register(id, Set.of(id), provider);
    }
}
