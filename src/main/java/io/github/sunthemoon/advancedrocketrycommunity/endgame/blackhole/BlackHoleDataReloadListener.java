package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
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
 * Reloads {@code singularities} and {@code black_hole_fuels} together (ADR-057 sections 1 and 2, by the rules of
 * ADR-052 section 2). Raw bytes are read off-thread so each version hashes the exact file; an invalid reload keeps the
 * last complete set and logs one aggregated error of at most 32 entries; an invalid initial load fails loading.
 */
public final class BlackHoleDataReloadListener implements PreparableReloadListener {
    private static final int MAX_ERRORS = 32;

    private final Manager manager;

    public BlackHoleDataReloadListener(Manager manager) {
        this.manager = manager;
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources, ProfilerFiller preparation,
                                          ProfilerFiller application, Executor background, Executor game) {
        return CompletableFuture.supplyAsync(() -> read(resources), background)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(this::apply, game);
    }

    private static Raw read(ResourceManager resources) {
        List<String> errors = new ArrayList<>();
        Map<ResourceLocation, byte[]> singularities = read(resources, BlackHoleCodec.SINGULARITY_DIRECTORY,
                BlackHoleCodec.MAX_SINGULARITY_BYTES, errors);
        Map<ResourceLocation, byte[]> fuels = read(resources, BlackHoleCodec.FUEL_DIRECTORY,
                BlackHoleCodec.MAX_FUEL_BYTES, errors);
        return new Raw(singularities, fuels, errors);
    }

    private static Map<ResourceLocation, byte[]> read(ResourceManager resources, String directory, int maxBytes,
                                                      List<String> errors) {
        Map<ResourceLocation, byte[]> files = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        Map<ResourceLocation, Resource> found = resources.listResources(directory, path -> path.getPath().endsWith(".json"));
        if (found.size() > BlackHoleCodec.MAX_FILES) {
            errors.add(directory + " holds more than " + BlackHoleCodec.MAX_FILES + " files");
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

    private void apply(Raw raw) {
        Predicate<ResourceLocation> items = id -> ForgeRegistries.ITEMS.containsKey(id)
                && ForgeRegistries.ITEMS.getValue(id) != Items.AIR;
        boolean hadData = manager.current().isPresent();
        DataResult<BlackHoleData> candidate = build(raw.singularities(), raw.fuels(), raw.errors(), items);
        if (candidate.result().isPresent()) {
            manager.accept(candidate.result().get());
            AdvancedRocketryCommunity.LOGGER.info("Accepted black-hole data: {} singularities, {} fuel tables",
                    raw.singularities().size(), raw.fuels().size());
            return;
        }
        String message = candidate.error().orElseThrow().message();
        AdvancedRocketryCommunity.LOGGER.error("Rejected black-hole data; the last complete set remains active: {}",
                message);
        if (!hadData) {
            throw new IllegalStateException("Initial black-hole data is invalid: " + message);
        }
    }

    /** Decodes every file and cross-checks the set; any error rejects the whole set. */
    static DataResult<BlackHoleData> build(Map<ResourceLocation, byte[]> singularities, Map<ResourceLocation, byte[]> fuels,
                                           List<String> readErrors, Predicate<ResourceLocation> items) {
        List<String> errors = new ArrayList<>(readErrors);
        List<SingularityProfile> profiles = new ArrayList<>();
        singularities.forEach((id, bytes) -> {
            try {
                profiles.add(BlackHoleCodec.decodeSingularity(id, bytes));
            } catch (RuntimeException exception) {
                errors.add(BlackHoleCodec.SINGULARITY_DIRECTORY + " " + id + ": " + exception.getMessage());
            }
        });
        List<BlackHoleFuelTable> tables = new ArrayList<>();
        fuels.forEach((id, bytes) -> {
            try {
                tables.add(BlackHoleCodec.decodeFuelTable(id, bytes, items));
            } catch (RuntimeException exception) {
                errors.add(BlackHoleCodec.FUEL_DIRECTORY + " " + id + ": " + exception.getMessage());
            }
        });
        if (!errors.isEmpty()) {
            return DataResult.error(() -> String.join("; ", errors.subList(0, Math.min(MAX_ERRORS, errors.size()))));
        }
        return BlackHoleData.create(profiles, tables);
    }

    private record Raw(Map<ResourceLocation, byte[]> singularities, Map<ResourceLocation, byte[]> fuels,
                       List<String> errors) {
    }

    /** Lifecycle owner of the active data; cleared with the server. */
    public static final class Manager {
        private final AtomicReference<BlackHoleData> current = new AtomicReference<>();

        public Optional<BlackHoleData> current() {
            return Optional.ofNullable(current.get());
        }

        public void accept(BlackHoleData data) {
            current.set(data);
        }

        public void clear() {
            current.set(null);
        }
    }
}
