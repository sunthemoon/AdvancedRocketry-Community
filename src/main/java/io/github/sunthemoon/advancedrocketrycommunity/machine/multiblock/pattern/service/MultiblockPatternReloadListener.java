package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalogDecoder;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Reads original pattern bytes and atomically publishes only a complete valid catalog. */
public final class MultiblockPatternReloadListener
        extends SimplePreparableReloadListener<MultiblockPatternReloadListener.PreparedCandidate> {
    public static final String DIRECTORY = "machine_patterns";
    private static final String PREFIX = DIRECTORY + "/";
    private static final String SUFFIX = ".json";

    private final MultiblockPatternCatalogManager manager;

    public MultiblockPatternReloadListener(MultiblockPatternCatalogManager manager) {
        this.manager = manager;
    }

    @Override
    protected PreparedCandidate prepare(ResourceManager resources, ProfilerFiller profiler) {
        try {
            Map<ResourceLocation, Resource> discovered = resources.listResources(
                    DIRECTORY,
                    location -> location.getPath().endsWith(SUFFIX)
            );
            if (discovered.size() > MultiblockPatternCatalog.MAX_DEFINITIONS) {
                return PreparedCandidate.failure("pattern catalog: exceeds 256 definitions");
            }
            Map<String, byte[]> encoded = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : discovered.entrySet()) {
                String id = fileDerivedId(entry.getKey());
                byte[] bytes = readBounded(entry.getValue());
                if (encoded.put(id, bytes) != null) {
                    return PreparedCandidate.failure("pattern catalog: duplicate file-derived id " + id);
                }
            }
            return PreparedCandidate.success(MultiblockPatternCatalogDecoder.decode(encoded));
        } catch (IOException | IllegalArgumentException exception) {
            return PreparedCandidate.failure(boundedMessage(exception));
        }
    }

    @Override
    protected void apply(
            PreparedCandidate candidate,
            ResourceManager resources,
            ProfilerFiller profiler
    ) {
        boolean hadValidCatalog = manager.current().isPresent();
        if (candidate.catalog() != null) {
            manager.accept(candidate.catalog());
            MultiblockPatternCatalogManager.ReloadStatus status = manager.status();
            AdvancedRocketryCommunity.LOGGER.info(
                    "Accepted multiblock pattern catalog generation {} with {} definitions",
                    status.generation(),
                    status.definitionCount()
            );
            return;
        }

        manager.reject(candidate.error());
        AdvancedRocketryCommunity.LOGGER.error(
                "Rejected multiblock pattern catalog; last valid generation remains active: {}",
                manager.status().message()
        );
        if (!hadValidCatalog) {
            throw new IllegalStateException(
                    "Initial multiblock pattern catalog is invalid: " + manager.status().message()
            );
        }
    }

    private static byte[] readBounded(Resource resource) throws IOException {
        try (InputStream input = resource.open()) {
            byte[] bytes = input.readNBytes(MultiblockPatternDefinition.MAX_DEFINITION_BYTES + 1);
            if (bytes.length > MultiblockPatternDefinition.MAX_DEFINITION_BYTES) {
                throw new IOException("pattern definition exceeds 65536 bytes");
            }
            return bytes;
        }
    }

    private static String fileDerivedId(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(PREFIX) || !path.endsWith(SUFFIX)) {
            throw new IllegalArgumentException("pattern resource has an invalid path");
        }
        String relative = path.substring(PREFIX.length(), path.length() - SUFFIX.length());
        if (relative.isEmpty()) {
            throw new IllegalArgumentException("pattern resource path is empty");
        }
        return location.getNamespace() + ":" + relative;
    }

    private static String boundedMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "pattern reload failed";
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    record PreparedCandidate(MultiblockPatternCatalog catalog, String error) {
        private static PreparedCandidate success(MultiblockPatternCatalog catalog) {
            return new PreparedCandidate(catalog, "");
        }

        private static PreparedCandidate failure(String message) {
            return new PreparedCandidate(null, message);
        }
    }
}
