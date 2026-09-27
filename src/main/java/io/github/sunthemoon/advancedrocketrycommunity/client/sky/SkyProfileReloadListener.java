package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.BoundedDefinitionJson;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfile;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfiles;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Preparation is CPU-only; the supplied apply callback schedules render-thread cleanup. */
public final class SkyProfileReloadListener extends SimplePreparableReloadListener<DataResult<Map<ResourceLocation, SkyProfile>>> {
    private final Runnable onReload;
    private final Consumer<String> diagnostic;
    private volatile Map<ResourceLocation, SkyProfile> profiles = SkyProfiles.builtins();
    private boolean lastAccepted;

    public SkyProfileReloadListener(Runnable onReload, Consumer<String> diagnostic) {
        this.onReload = onReload;
        this.diagnostic = diagnostic;
    }

    public Map<ResourceLocation, SkyProfile> profiles() { return profiles; }
    public boolean lastAccepted() { return lastAccepted; }

    @Override
    protected DataResult<Map<ResourceLocation, SkyProfile>> prepare(ResourceManager resources, ProfilerFiller profiler) {
        try {
            var found = resources.listResources(SkyProfile.DIRECTORY, id -> id.getPath().endsWith(".json"));
            if (found.size() > SkyProfile.MAX_PROFILES) {
                throw new IOException("Sky resource count exceeds " + SkyProfile.MAX_PROFILES);
            }
            var candidate = new LinkedHashMap<ResourceLocation, SkyProfile>();
            for (var entry : found.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                String path = entry.getKey().getPath();
                String prefix = SkyProfile.DIRECTORY + "/";
                if (!path.startsWith(prefix) || path.length() <= prefix.length() + 5) {
                    throw new IOException("Invalid sky profile resource path");
                }
                var id = new ResourceLocation(entry.getKey().getNamespace(), path.substring(prefix.length(), path.length() - 5));
                BoundedCelestialCodecs.requireId(id, "sky profile");
                try (var input = entry.getValue().open()) {
                    var profile = SkyProfile.decode(BoundedDefinitionJson.read(input, SkyProfile.MAX_BYTES));
                    if (candidate.putIfAbsent(id, profile) != null) {
                        throw new IOException("Duplicate sky profile ID");
                    }
                } catch (IOException | IllegalArgumentException exception) {
                    throw new IOException(id + ": " + exception.getMessage(), exception);
                }
            }
            return DataResult.success(Map.copyOf(candidate));
        } catch (IOException | IllegalArgumentException exception) {
            String raw = String.valueOf(exception.getMessage()).replace('\n', ' ').replace('\r', ' ');
            String message = raw.substring(0, Math.min(512, raw.length()));
            return DataResult.error(() -> message);
        }
    }

    @Override
    protected void apply(DataResult<Map<ResourceLocation, SkyProfile>> candidate,
            ResourceManager resources, ProfilerFiller profiler) {
        lastAccepted = candidate.result().isPresent();
        if (lastAccepted) {
            profiles = candidate.result().orElseThrow();
        } else {
            diagnostic.accept("Rejected sky profiles; previous presentation retained: "
                    + candidate.error().orElseThrow().message());
        }
        onReload.run();
    }
}
