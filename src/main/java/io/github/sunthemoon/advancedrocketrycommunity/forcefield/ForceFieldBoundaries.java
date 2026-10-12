package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.RegisterAtmosphereBoundariesEvent;
import java.util.Set;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForceFieldBoundaries {
    @SubscribeEvent public static void register(RegisterAtmosphereBoundariesEvent event) {
        event.register(ModIdentity.id("force_field_boundary"), Set.of(ModIdentity.id("force_field")),
                state -> AtmosphereBoundary.SEALED);
    }
    private ForceFieldBoundaries() { }
}
