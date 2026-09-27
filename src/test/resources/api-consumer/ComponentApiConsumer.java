package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketComponentsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketComponentDefinition;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class ComponentApiConsumer {
    public static void register(RegisterRocketComponentsEvent event) {
        event.register(ResourceLocation.tryParse("consumer:engine"),
                Set.of(ResourceLocation.tryParse("minecraft:diamond_block")),
                new RocketComponentDefinition(120, 2400, 0, true, false, false));
    }
}
