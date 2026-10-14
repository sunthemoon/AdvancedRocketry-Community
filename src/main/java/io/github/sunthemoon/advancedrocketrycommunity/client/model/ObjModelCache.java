package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The last good set of compiled models (ADR-066 section 7.1). A successful reload replaces the whole set and names the
 * textures only the old set used, for release; a failed reload keeps the models and only re-resolves their textures.
 * Written on the render thread, read by renderers.
 *
 * @param <M> the compiled model; Forge's {@code CompositeRenderable} on the physical client
 */
public final class ObjModelCache<M> {
    /** One compiled model and the texture each of its meshes binds. */
    public record Entry<M>(ObjModelFixture fixture, M model, Map<ResourceLocation, ResourceLocation> textures) {
        /** The texture to bind for a mesh; anything the check did not resolve draws the fixture's fallback. */
        public ResourceLocation texture(ResourceLocation requested) {
            return textures.getOrDefault(requested, fixture.fallbackTexture());
        }
    }

    private volatile Map<String, Entry<M>> entries = Map.of();

    public Optional<Entry<M>> get(String id) { return Optional.ofNullable(entries.get(id)); }

    public Map<String, Entry<M>> entries() { return entries; }

    /** Publishes a successful set; returns the textures the old set bound and the new one does not. */
    public Set<ResourceLocation> replace(Map<String, Entry<M>> next) {
        var released = new HashSet<ResourceLocation>();
        entries.values().forEach(entry -> released.addAll(entry.textures().values()));
        next.values().forEach(entry -> released.removeAll(entry.textures().values()));
        entries.values().forEach(entry -> released.remove(entry.fixture().fallbackTexture()));
        next.values().forEach(entry -> released.remove(entry.fixture().fallbackTexture()));
        entries = Map.copyOf(next);
        return Set.copyOf(released);
    }

    /** Keeps every model after a failed reload, with its textures resolved again against the new resources. */
    public void retexture(Map<String, Map<ResourceLocation, ResourceLocation>> textures) {
        var next = new LinkedHashMap<String, Entry<M>>();
        entries.forEach((id, entry) -> next.put(id, new Entry<>(entry.fixture(), entry.model(),
                textures.getOrDefault(id, entry.textures()))));
        entries = Map.copyOf(next);
    }
}
