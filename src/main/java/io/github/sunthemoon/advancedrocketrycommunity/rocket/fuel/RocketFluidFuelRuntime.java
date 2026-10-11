package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Lifecycle-scoped immutable metadata only, not a static world/resource collection. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RocketFluidFuelRuntime {
    private static volatile RocketFluidFuelCatalog catalog = RocketFluidFuelCatalog.defaults();
    private RocketFluidFuelRuntime() { }
    public static RocketFluidFuelCatalog capture() { return catalog; }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { catalog = RocketFluidFuelCatalog.defaults(); }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(new Loader()); }
    private static final class Loader extends SimplePreparableReloadListener<List<String>> {
        @Override protected List<String> prepare(ResourceManager manager, ProfilerFiller profiler) {
            try {
                var resources = manager.listResources("rocket_fluid_fuels", id -> id.getPath().endsWith(".json"));
                if (resources.size() > 64) { throw new IllegalArgumentException("Too many rocket Fluid definitions"); }
                var documents = new ArrayList<String>();
                for (var resource : resources.values()) {
                    try (var input = resource.open()) {
                        byte[] bytes = input.readNBytes(8_193);
                        if (bytes.length > 8_192) { throw new IllegalArgumentException("Rocket Fluid definition too large"); }
                        documents.add(new String(bytes, StandardCharsets.UTF_8));
                    }
                }
                return List.copyOf(documents);
            } catch (IOException | RuntimeException invalid) {
                AdvancedRocketryCommunity.LOGGER.warn("ARCE_ROCKET_FLUID_CATALOG_REJECTED reason={}", invalid.getMessage());
                return null;
            }
        }
        @Override protected void apply(List<String> documents, ResourceManager manager, ProfilerFiller profiler) {
            if (documents == null) { return; }
            try {
                var allowed = new EnumMap<RocketPropulsion.Role, Collection<String>>(RocketPropulsion.Role.class);
                for (var role : RocketPropulsion.Role.values()) { allowed.put(role, List.copyOf(CommonConfig.ROCKET.allowed(role))); }
                var next = RocketFluidFuelCatalog.parse(documents, Loader::knownSource, allowed);
                catalog = next;
            } catch (RuntimeException invalid) {
                AdvancedRocketryCommunity.LOGGER.warn("ARCE_ROCKET_FLUID_CATALOG_REJECTED reason={}", invalid.getMessage());
            }
        }
        private static boolean knownSource(ResourceLocation id) {
            var fluid = ForgeRegistries.FLUIDS.getValue(id);
            return ForgeRegistries.FLUIDS.containsKey(id) && fluid != null && fluid != Fluids.EMPTY
                    && fluid.defaultFluidState().isSource();
        }
    }
}
