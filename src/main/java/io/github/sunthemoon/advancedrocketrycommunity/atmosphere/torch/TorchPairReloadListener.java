package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * Reloads {@code torch_pairs} with bounded reads off-thread and registry checks on the server thread. An invalid set
 * keeps the last complete catalog (the built-in pair after a failed first load) and logs one ERROR; loading never
 * fails.
 */
final class TorchPairReloadListener implements PreparableReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                                          ProfilerFiller preparation, ProfilerFiller application,
                                          Executor background, Executor game) {
        return CompletableFuture.supplyAsync(() -> read(resources), background)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(TorchPairReloadListener::apply, game);
    }

    private static Raw read(ResourceManager resources) {
        List<String> errors = new ArrayList<>();
        Map<ResourceLocation, byte[]> files = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        String directory = TorchPairCatalog.DIRECTORY;
        Map<ResourceLocation, Resource> found = resources.listResources(directory, path -> path.getPath().endsWith(".json"));
        if (found.size() > TorchPairCatalog.MAX_PAIRS - 1) {
            errors.add(directory + " holds more than " + (TorchPairCatalog.MAX_PAIRS - 1) + " data pairs");
            return new Raw(files, errors);
        }
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(),
                    path.substring(directory.length() + 1, path.length() - ".json".length()));
            try (InputStream input = entry.getValue().open()) {
                byte[] bytes = input.readNBytes(TorchPairCatalog.MAX_FILE_BYTES + 1);
                if (id == null || bytes.length > TorchPairCatalog.MAX_FILE_BYTES) {
                    errors.add(entry.getKey() + " exceeds " + TorchPairCatalog.MAX_FILE_BYTES + " bytes or has no valid ID");
                } else {
                    files.put(id, bytes);
                }
            } catch (IOException failure) {
                errors.add(entry.getKey() + ": " + failure.getMessage());
            }
        }
        return new Raw(files, errors);
    }

    private static void apply(Raw raw) {
        TorchPairCatalog.Result result = TorchPairCatalog.build(raw.files(), raw.errors(), TorchShapes::registered);
        if (result.catalog().isPresent()) {
            CombustionTorches.accept(result.catalog().get());
            return;
        }
        LOGGER.error("Rejected torch pairs; the last complete catalog of {} pairs remains active: {}",
                CombustionTorches.catalog().size(), String.join("; ", result.errors()));
    }

    private record Raw(Map<ResourceLocation, byte[]> files, List<String> errors) {
    }
}
