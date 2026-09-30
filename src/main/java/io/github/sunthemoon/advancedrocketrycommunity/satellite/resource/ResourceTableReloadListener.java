package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Reloads {@code asteroid_types} and {@code gas_harvest} together with the celestial catalog (ADR-052 section 2).
 * Raw bytes are read off-thread so each version hashes the exact file. An invalid reload keeps the last complete
 * tables and logs one aggregated error; an invalid initial load fails loading.
 */
public final class ResourceTableReloadListener implements PreparableReloadListener {
    private final Manager manager;
    private final CelestialCatalogManager celestialCatalogs;

    public ResourceTableReloadListener(Manager manager, CelestialCatalogManager celestialCatalogs) {
        this.manager = manager;
        this.celestialCatalogs = celestialCatalogs;
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources, ProfilerFiller preparation,
                                          ProfilerFiller application, Executor background, Executor game) {
        return CompletableFuture.supplyAsync(() -> read(resources), background)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(this::apply, game);
    }

    private static RawTables read(ResourceManager resources) {
        List<String> errors = new ArrayList<>();
        Map<ResourceLocation, byte[]> types = read(resources, ResourceTableCodec.ASTEROID_DIRECTORY,
                ResourceTableCodec.MAX_ASTEROID_FILE_BYTES, ResourceTableCodec.MAX_ASTEROID_TYPES, errors);
        Map<ResourceLocation, byte[]> gas = read(resources, ResourceTableCodec.GAS_DIRECTORY,
                ResourceTableCodec.MAX_GAS_FILE_BYTES, ResourceTableCodec.MAX_GAS_TABLES, errors);
        return new RawTables(types, gas, errors);
    }

    private static Map<ResourceLocation, byte[]> read(ResourceManager resources, String directory, int maxBytes,
                                                      int maxFiles, List<String> errors) {
        Map<ResourceLocation, byte[]> files = new TreeMap<>(java.util.Comparator.comparing(ResourceLocation::toString));
        Map<ResourceLocation, Resource> found = resources.listResources(directory, path -> path.getPath().endsWith(".json"));
        if (found.size() > maxFiles) {
            errors.add(directory + " holds more than " + maxFiles + " files");
            return files;
        }
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(),
                    path.substring(directory.length() + 1, path.length() - ".json".length()));
            try (InputStream input = entry.getValue().open()) {
                byte[] bytes = input.readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes || id == null) {
                    errors.add(entry.getKey() + " exceeds " + maxBytes + " bytes or has no valid ID");
                } else {
                    files.put(id, bytes);
                }
            } catch (IOException exception) {
                errors.add(entry.getKey() + ": " + exception.getMessage());
            }
        }
        return files;
    }

    private void apply(RawTables raw) {
        boolean hadTables = manager.current().isPresent();
        List<String> errors = new ArrayList<>(raw.errors());
        Predicate<ResourceLocation> items = id -> ForgeRegistries.ITEMS.containsKey(id)
                && ForgeRegistries.ITEMS.getValue(id) != Items.AIR;
        List<AsteroidType> types = new ArrayList<>();
        raw.types().forEach((id, bytes) -> {
            try {
                types.add(ResourceTableCodec.decodeAsteroidType(id, bytes, items));
            } catch (RuntimeException exception) {
                errors.add(ResourceTableCodec.ASTEROID_DIRECTORY + " " + id + ": " + exception.getMessage());
            }
        });
        List<GasTable> gas = new ArrayList<>();
        raw.gas().forEach((id, bytes) -> {
            try {
                gas.add(ResourceTableCodec.decodeGasTable(id, bytes, items));
            } catch (RuntimeException exception) {
                errors.add(ResourceTableCodec.GAS_DIRECTORY + " " + id + ": " + exception.getMessage());
            }
        });
        CelestialCatalog celestial = celestialCatalogs.current().orElse(null);
        DataResult<ResourceTables> candidate = !errors.isEmpty()
                ? DataResult.error(() -> String.join("; ", errors.subList(0, Math.min(32, errors.size()))))
                : celestial == null ? DataResult.error(() -> "No valid celestial catalog is active")
                : ResourceTables.create(types, gas, celestial);
        if (candidate.result().isPresent()) {
            manager.accept(candidate.result().get());
            AdvancedRocketryCommunity.LOGGER.info("Accepted resource tables generation {}: {} asteroid types, {} gas tables",
                    manager.generation(), types.size(), gas.size());
            return;
        }
        String message = candidate.error().orElseThrow().message();
        AdvancedRocketryCommunity.LOGGER.error("Rejected resource tables; the last complete tables remain active: {}", message);
        if (!hadTables) {
            throw new IllegalStateException("Initial resource tables are invalid: " + message);
        }
    }

    private record RawTables(Map<ResourceLocation, byte[]> types, Map<ResourceLocation, byte[]> gas, List<String> errors) {
    }

    /** Lifecycle owner of the active tables; cleared with the server. */
    public static final class Manager {
        private final AtomicReference<ResourceTables> current = new AtomicReference<>();
        private volatile long generation;

        public Optional<ResourceTables> current() {
            return Optional.ofNullable(current.get());
        }

        public long generation() {
            return generation;
        }

        public synchronized void accept(ResourceTables tables) {
            current.set(tables);
            generation++;
        }

        public void clear() {
            current.set(null);
        }
    }
}
