package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketFuelsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketFuelDefinition;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class FuelApiConsumer {
    public static void register(RegisterRocketFuelsEvent event) {
        event.register(ResourceLocation.tryParse("consumer:item_fuel"),
                Set.of(ResourceLocation.tryParse("minecraft:charcoal")),
                new RocketFuelDefinition(73, Optional.of(ResourceLocation.tryParse("minecraft:stick"))));
    }
}
