package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Every catalogued model of one resource reload, resolved and held to the budgets as one cached set (ADR-066
 * section 7.1). Malformed, oversized or badly referenced geometry fails the whole set. A texture that is missing
 * resolves to the model's explicit fallback instead, so no mesh is drawn with the missing texture.
 */
public final class ObjModelSet {
    /** Reads one resource, failing beyond {@code limit} bytes; empty when the resource does not exist. */
    @FunctionalInterface
    public interface Source {
        Optional<byte[]> read(ResourceLocation location, int limit) throws ObjModelException;
    }

    /**
     * One checked model.
     *
     * @param parserInput the checked OBJ bytes Forge parses, mirrored when the fixture says so
     * @param textures every texture the material library names, mapped to itself or to the fallback
     */
    public record Model(ObjModelFixture fixture, byte[] parserInput, ObjModelCheck.ObjSummary summary,
            Map<ResourceLocation, ResourceLocation> textures) { }

    /** The checked models, the catalogued models whose OBJ is absent, and the set totals. */
    public record Result(Map<String, Model> models, Set<String> absent, int vertices, int bytes) { }

    private ObjModelSet() { }

    public static Result check(List<ObjModelFixture> catalog, Source source) throws ObjModelException {
        var models = new LinkedHashMap<String, Model>();
        var absent = new TreeSet<String>();
        var budget = new Budget();
        for (var fixture : catalog) {
            String label = fixture.model().toString();
            var obj = read(source, fixture.model(), budget);
            if (obj.isEmpty()) { absent.add(fixture.id()); continue; }
            var summary = ObjModelCheck.checkObj(obj.get(), label);
            budget.vertices += summary.budgetVertices();
            if (budget.vertices > ObjModelCheck.MAX_SET_VERTICES) {
                throw new ObjModelException("the model set has more than " + ObjModelCheck.MAX_SET_VERTICES + " vertices");
            }
            if (summary.bounds() == null || !summary.bounds().within(fixture.modelBounds(), 1.0E-4)) {
                throw new ObjModelException(label + ": positions " + summary.bounds() + " leave the fixture's bounding volume");
            }
            String library = summary.materialLibrary();
            if (library == null) { throw new ObjModelException(label + ": names no material library"); }
            var libraryLocation = ObjModelCheck.libraryLocation(fixture.model(), library);
            var mtl = read(source, libraryLocation, budget)
                    .orElseThrow(() -> new ObjModelException(label + ": material library " + libraryLocation + " is missing"));
            var materials = ObjModelCheck.checkMtl(mtl, libraryLocation.toString()).materials();
            var textures = new LinkedHashMap<ResourceLocation, ResourceLocation>();
            boolean fallbackUsed = false;
            for (String material : summary.materials()) {
                var maps = materials.get(material);
                if (maps == null) {
                    throw new ObjModelException(label + ": material " + material + " is not defined in " + libraryLocation);
                }
                String diffuse = maps.get("map_Kd");
                if (diffuse == null) { fallbackUsed = true; continue; }
                var texture = ObjModelCheck.texturePath(diffuse);
                if (textures.containsKey(texture)) { continue; }
                var image = read(source, texture, budget);
                if (image.isPresent()) { ObjModelCheck.checkPng(image.get(), texture.toString()); }
                textures.put(texture, image.isPresent() ? texture : fixture.fallbackTexture());
                fallbackUsed |= image.isEmpty();
            }
            if (fallbackUsed) {
                var fallback = read(source, fixture.fallbackTexture(), budget).orElseThrow(
                        () -> new ObjModelException(label + ": fallback texture " + fixture.fallbackTexture() + " is missing"));
                ObjModelCheck.checkPng(fallback, fixture.fallbackTexture().toString());
            }
            byte[] input = fixture.mirrorX() ? ObjModelCheck.mirrorX(obj.get(), label) : obj.get();
            models.put(fixture.id(), new Model(fixture, input, summary, Collections.unmodifiableMap(textures)));
        }
        return new Result(Collections.unmodifiableMap(models), Collections.unmodifiableSet(absent), budget.vertices, budget.bytes);
    }

    /** What one set has used of its budgets so far. */
    private static final class Budget {
        int bytes;
        int vertices;
    }

    /** Reads within what is left of the set's byte budget, so the set never buffers more than the budget. */
    private static Optional<byte[]> read(Source source, ResourceLocation location, Budget budget) throws ObjModelException {
        int left = ObjModelCheck.MAX_SET_BYTES - budget.bytes;
        var data = source.read(location, left);
        if (data.isPresent() && data.get().length > left) {
            throw new ObjModelException("the model set has more than " + ObjModelCheck.MAX_SET_BYTES + " bytes at " + location);
        }
        data.ifPresent(bytes -> budget.bytes += bytes.length);
        return data;
    }

    /**
     * The textures of models kept from an earlier reload, resolved again against this reload's resources. A texture
     * that is gone or no longer usable falls back, so a retained model is never drawn with the missing texture.
     */
    public static Map<ResourceLocation, ResourceLocation> retexture(ObjModelFixture fixture,
            Map<ResourceLocation, ResourceLocation> textures, Source source) {
        var result = new LinkedHashMap<ResourceLocation, ResourceLocation>();
        for (var texture : textures.keySet()) {
            ResourceLocation resolved = fixture.fallbackTexture();
            try {
                var image = source.read(texture, ObjModelCheck.MAX_SET_BYTES);
                if (image.isPresent()) { ObjModelCheck.checkPng(image.get(), texture.toString()); resolved = texture; }
            } catch (ObjModelException unusable) {
                resolved = fixture.fallbackTexture();
            }
            result.put(texture, resolved);
        }
        return Collections.unmodifiableMap(result);
    }
}
