package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.RegisterSatellitePayloadsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.SatelliteMissionDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.SatellitePayloadRegistrar;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class SatelliteApiConsumer {
    public static void register(SatellitePayloadRegistrar registrar, ResourceLocation definition,
                                ResourceLocation item, ResourceLocation body) {
        new RegisterSatellitePayloadsEvent(registrar).register(definition, item,
                new SatelliteMissionDefinition(200, 120, 100, List.of(body)));
    }
}
