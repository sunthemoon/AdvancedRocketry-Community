package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads the catalogued machine models on every client resource reload (ADR-066 section 7.1). Preparation reads,
 * checks and compiles off the render thread; nothing reaches the compiler unless the whole set passes. Apply
 * publishes a good set and releases the textures only the old set used, or keeps the last good set.
 *
 * @param <M> the compiled model
 */
public final class ObjModelReloadListener<M> extends SimplePreparableReloadListener<ObjModelReloadListener.Prepared<M>> {
    /** Turns one checked model into the compiled form; on the physical client, Forge's OBJ parser and baker. */
    @FunctionalInterface
    public interface Compiler<M> {
        M compile(ObjModelFixture fixture, byte[] parserInput) throws Exception;
    }

    /** The outcome of one preparation. */
    public sealed interface Prepared<M> permits Accepted, Rejected { }

    public record Accepted<M>(Map<String, ObjModelCache.Entry<M>> entries, Set<String> absent) implements Prepared<M> { }

    public record Rejected<M>(String message, Map<String, Map<ResourceLocation, ResourceLocation>> textures) implements Prepared<M> { }

    private static final int MAX_DIAGNOSTIC = 512;
    private final List<ObjModelFixture> catalog;
    private final ObjModelCache<M> cache;
    private final Compiler<M> compiler;
    private final Consumer<Set<ResourceLocation>> release;
    private final Consumer<String> diagnostic;
    private boolean lastAccepted;

    public ObjModelReloadListener(List<ObjModelFixture> catalog, ObjModelCache<M> cache, Compiler<M> compiler,
            Consumer<Set<ResourceLocation>> release, Consumer<String> diagnostic) {
        this.catalog = List.copyOf(catalog);
        this.cache = cache;
        this.compiler = compiler;
        this.release = release;
        this.diagnostic = diagnostic;
    }

    public boolean lastAccepted() { return lastAccepted; }

    @Override
    protected Prepared<M> prepare(ResourceManager resources, ProfilerFiller profiler) {
        ObjModelSet.Source source = (location, limit) -> {
            var resource = resources.getResource(location);
            if (resource.isEmpty()) { return Optional.empty(); }
            try (var input = resource.get().open()) {
                return Optional.of(ObjModelCheck.readBounded(input, limit, location.toString()));
            } catch (IOException failure) {
                throw new ObjModelException(location + ": cannot be read");
            }
        };
        try {
            var set = ObjModelSet.check(catalog, source);
            var entries = new LinkedHashMap<String, ObjModelCache.Entry<M>>();
            for (var model : set.models().values()) {
                try {
                    entries.put(model.fixture().id(), new ObjModelCache.Entry<>(model.fixture(),
                            compiler.compile(model.fixture(), model.parserInput()), model.textures()));
                } catch (Exception failure) {
                    throw new ObjModelException(model.fixture().model() + ": Forge's OBJ loader refused it: " + failure);
                }
            }
            return new Accepted<>(Map.copyOf(entries), set.absent());
        } catch (ObjModelException | RuntimeException failure) {
            var textures = new LinkedHashMap<String, Map<ResourceLocation, ResourceLocation>>();
            cache.entries().forEach((id, entry) -> textures.put(id, ObjModelSet.retexture(entry.fixture(), entry.textures(), source)));
            String raw = String.valueOf(failure.getMessage()).replace('\n', ' ').replace('\r', ' ');
            return new Rejected<>(raw.substring(0, Math.min(MAX_DIAGNOSTIC, raw.length())), Map.copyOf(textures));
        }
    }

    @Override
    protected void apply(Prepared<M> prepared, ResourceManager resources, ProfilerFiller profiler) {
        if (prepared instanceof Accepted<M> accepted) {
            lastAccepted = true;
            var released = cache.replace(accepted.entries());
            if (!released.isEmpty()) { release.accept(released); }
        } else if (prepared instanceof Rejected<M> rejected) {
            lastAccepted = false;
            cache.retexture(rejected.textures());
            diagnostic.accept("Rejected OBJ machine models; the last good set is kept: " + rejected.message());
        }
    }
}
