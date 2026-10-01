package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
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
 * Reloads {@code laser_drill_tables} with the celestial catalog and item registry (ADR-055 section 2, by the rules
 * of ADR-052 sections 1–2). Raw bytes are read off-thread so each version hashes the exact file. An invalid reload
 * keeps the last complete set and logs one aggregated error of at most 32 entries; an invalid initial load fails
 * loading.
 */
public final class LaserDrillTableReloadListener implements PreparableReloadListener {
    private static final int MAX_ERRORS = 32;

    private final Manager manager;
    private final CelestialCatalogManager celestialCatalogs;

    public LaserDrillTableReloadListener(Manager manager, CelestialCatalogManager celestialCatalogs) {
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
        Map<ResourceLocation, byte[]> files = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        String directory = LaserDrillTableCodec.DIRECTORY;
        Map<ResourceLocation, Resource> found = resources.listResources(directory, path -> path.getPath().endsWith(".json"));
        if (found.size() > LaserDrillTableCodec.MAX_TABLES) {
            errors.add(directory + " holds more than " + LaserDrillTableCodec.MAX_TABLES + " files");
            return new RawTables(files, errors);
        }
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(),
                    path.substring(directory.length() + 1, path.length() - ".json".length()));
            try (InputStream input = entry.getValue().open()) {
                byte[] bytes = input.readNBytes(LaserDrillTableCodec.MAX_FILE_BYTES + 1);
                if (bytes.length > LaserDrillTableCodec.MAX_FILE_BYTES || id == null) {
                    errors.add(entry.getKey() + " exceeds " + LaserDrillTableCodec.MAX_FILE_BYTES
                            + " bytes or has no valid ID");
                } else {
                    files.put(id, bytes);
                }
            } catch (IOException exception) {
                errors.add(entry.getKey() + ": " + exception.getMessage());
            }
        }
        return new RawTables(files, errors);
    }

    private void apply(RawTables raw) {
        Predicate<ResourceLocation> items = id -> ForgeRegistries.ITEMS.containsKey(id)
                && ForgeRegistries.ITEMS.getValue(id) != Items.AIR;
        boolean hadTables = manager.current().isPresent();
        DataResult<LaserDrillTables> candidate = build(raw.files(), raw.errors(), items,
                celestialCatalogs.current().orElse(null));
        if (candidate.result().isPresent()) {
            manager.accept(candidate.result().get());
            AdvancedRocketryCommunity.LOGGER.info("Accepted laser drill tables generation {}: {} files",
                    manager.generation(), raw.files().size());
            return;
        }
        String message = candidate.error().orElseThrow().message();
        AdvancedRocketryCommunity.LOGGER.error("Rejected laser drill tables; the last complete set remains active: {}",
                message);
        if (!hadTables) {
            throw new IllegalStateException("Initial laser drill tables are invalid: " + message);
        }
    }

    /** Decodes every file and cross-checks the set; any error rejects the whole set. */
    static DataResult<LaserDrillTables> build(Map<ResourceLocation, byte[]> files, List<String> readErrors,
                                              Predicate<ResourceLocation> items, CelestialCatalog celestial) {
        List<String> errors = new ArrayList<>(readErrors);
        List<LaserDrillTable> tables = new ArrayList<>();
        files.forEach((id, bytes) -> {
            try {
                tables.add(LaserDrillTableCodec.decode(id, bytes, items));
            } catch (RuntimeException exception) {
                errors.add(LaserDrillTableCodec.DIRECTORY + " " + id + ": " + exception.getMessage());
            }
        });
        if (!errors.isEmpty()) {
            return DataResult.error(() -> String.join("; ", errors.subList(0, Math.min(MAX_ERRORS, errors.size()))));
        }
        if (celestial == null) {
            return DataResult.error(() -> "No valid celestial catalog is active");
        }
        return LaserDrillTables.create(tables, celestial);
    }

    private record RawTables(Map<ResourceLocation, byte[]> files, List<String> errors) {
    }

    /** Lifecycle owner of the active tables; cleared with the server. */
    public static final class Manager {
        private final AtomicReference<LaserDrillTables> current = new AtomicReference<>();
        private volatile long generation;

        public Optional<LaserDrillTables> current() {
            return Optional.ofNullable(current.get());
        }

        public long generation() {
            return generation;
        }

        public synchronized void accept(LaserDrillTables tables) {
            current.set(tables);
            generation++;
        }

        public void clear() {
            current.set(null);
        }
    }
}
