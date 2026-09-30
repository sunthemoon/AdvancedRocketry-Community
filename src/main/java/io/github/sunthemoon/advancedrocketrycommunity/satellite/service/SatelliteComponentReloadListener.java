package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentCatalog;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Loads {@code satellite_components}; runs before the definition listener so that schema-2 definitions resolve
 * their primary component. An invalid reload keeps the last complete catalog; an invalid first load fails.
 */
public final class SatelliteComponentReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "satellite_components";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Manager manager;

    public SatelliteComponentReloadListener(Manager manager) {
        super(GSON, DIRECTORY);
        this.manager = manager;
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        boolean hadValidCatalog = manager.current().isPresent();
        DataResult<SatelliteComponentCatalog> candidate = SatelliteComponentCatalog.decode(
                resources,
                id -> ForgeRegistries.ITEMS.containsKey(id) && ForgeRegistries.ITEMS.getValue(id) != Items.AIR
        );
        if (candidate.result().isPresent()) {
            manager.accept(candidate.result().orElseThrow());
            AdvancedRocketryCommunity.LOGGER.info(
                    "Accepted satellite component catalog with {} components", manager.current().orElseThrow().size());
            return;
        }
        String message = candidate.error().orElseThrow().message();
        AdvancedRocketryCommunity.LOGGER.error(
                "Rejected satellite component catalog; last valid catalog remains active: {}",
                message.length() <= 2_048 ? message : message.substring(0, 2_045) + "..."
        );
        if (!hadValidCatalog) {
            throw new IllegalStateException("Initial satellite component catalog is invalid: " + message);
        }
    }

    /** Holds the last complete component catalog; server lifetime, cleared on stop. */
    public static final class Manager {
        private final AtomicReference<SatelliteComponentCatalog> catalog = new AtomicReference<>();

        public Optional<SatelliteComponentCatalog> current() {
            return Optional.ofNullable(catalog.get());
        }

        void accept(SatelliteComponentCatalog value) {
            catalog.set(value);
        }

        public void clear() {
            catalog.set(null);
        }
    }
}
