package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelTestFiles.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** One cached model set: references, fallbacks and the set budgets (ADR-066 section 7.1). */
class ObjModelSetTest {
    private static final ResourceLocation TEXTURE = ModIdentity.id("textures/models/widget.png");
    private final Map<ResourceLocation, byte[]> files = new HashMap<>();
    private final List<Integer> limits = new ArrayList<>();
    private final ObjModelSet.Source source = (location, limit) -> {
        limits.add(limit);
        byte[] data = files.get(location);
        try {
            return data == null ? Optional.empty()
                    : Optional.of(ObjModelCheck.readBounded(new ByteArrayInputStream(data), limit, location.toString()));
        } catch (IOException failure) {
            throw new ObjModelException(failure.getMessage());
        }
    };

    private void widget(String obj, String mtl) {
        files.put(WIDGET.model(), bytes(obj));
        files.put(ModIdentity.id("models/widget.mtl"), bytes(mtl));
        files.put(TEXTURE, png(16, 16));
        files.put(WIDGET.fallbackTexture(), png(16, 16));
    }

    private ObjModelSet.Result check() throws ObjModelException { return ObjModelSet.check(List.of(WIDGET), source); }

    private void assertRefused(String fragment) {
        var failure = assertThrows(ObjModelException.class, this::check);
        assertTrue(failure.getMessage().contains(fragment), failure.getMessage() + " should name " + fragment);
    }

    @Test void resolvesEveryReferenceInsideTheModResources() throws Exception {
        widget(OBJ, MTL);
        var result = check();
        var model = result.models().get("widget");
        assertEquals(Map.of(TEXTURE, TEXTURE), model.textures());
        assertArrayEquals(ObjModelCheck.mirrorX(bytes(OBJ), "widget.obj"), model.parserInput());
        assertEquals(12, result.vertices());
        assertEquals(bytes(OBJ).length + bytes(MTL).length + files.get(TEXTURE).length, result.bytes());
        assertTrue(result.absent().isEmpty());
        var unmirrored = new ObjModelFixture("widget", WIDGET.kind(), WIDGET.model(), false, true, 1, 0, 0, 0,
                WIDGET.modelBounds(), WIDGET.fallbackTexture(), ObjModelFixture.ENTITY_CUTOUT);
        assertArrayEquals(bytes(OBJ), ObjModelSet.check(List.of(unmirrored), source).models().get("widget").parserInput());
    }

    @Test void anAbsentModelIsNotAFailure() throws Exception {
        var result = check();
        assertTrue(result.models().isEmpty());
        assertEquals(Set.of("widget"), result.absent());
    }

    @Test void aMissingOrUnmappedTextureFallsBackExplicitly() throws Exception {
        widget(OBJ, MTL);
        files.remove(TEXTURE);
        assertEquals(Map.of(TEXTURE, WIDGET.fallbackTexture()), check().models().get("widget").textures());
        widget(OBJ, MTL.replace("map_Kd advancedrocketrycommunity:models/widget\n", ""));
        assertEquals(Map.of(), check().models().get("widget").textures());
        files.remove(WIDGET.fallbackTexture());
        assertRefused("fallback texture");
    }

    @Test void refusesBrokenReferencesGeometryAndTextures() {
        widget(OBJ, MTL);
        files.remove(ModIdentity.id("models/widget.mtl"));
        assertRefused("material library advancedrocketrycommunity:models/widget.mtl is missing");
        widget(OBJ.replace("usemtl Body", "usemtl Other"), MTL);
        assertRefused("material Other is not defined");
        widget(OBJ.replace("mtllib widget.mtl", "mtllib minecraft:models/widget.mtl"), MTL);
        assertRefused("external namespace");
        widget(OBJ, MTL.replace("advancedrocketrycommunity:models/widget", "https://example.com/widget"));
        assertRefused("absolute, external or variable");
        widget(OBJ.replace("v 1 1 1", "v 3 1 1"), MTL);
        assertRefused("leave the fixture's bounding volume");
        widget(OBJ, MTL);
        files.put(TEXTURE, png(ObjModelCheck.MAX_TEXTURE_SIDE + 1, 1));
        assertRefused("pixels a side");
    }

    @Test void holdsTheSetVertexBudget() throws Exception {
        int quads = ObjModelCheck.MAX_BAKED_OBJECT_VERTICES / ObjModelCheck.QUAD_CORNERS;
        var catalog = new ArrayList<ObjModelFixture>();
        for (int i = 0; i < 5; i++) {
            var fixture = fixture("widget" + i);
            catalog.add(fixture);
            files.put(fixture.model(), bytes(quads(quads)));
        }
        files.put(ModIdentity.id("models/widget.mtl"), bytes(MTL));
        files.put(TEXTURE, png(16, 16));
        var four = ObjModelSet.check(catalog.subList(0, 4), source);
        assertEquals(ObjModelCheck.MAX_SET_VERTICES, four.vertices());
        var five = assertThrows(ObjModelException.class, () -> ObjModelSet.check(catalog, source));
        assertTrue(five.getMessage().contains("more than 262144 vertices"), five.getMessage());
    }

    @Test void neverReadsBeyondTheSetByteBudget() throws Exception {
        var padding = "#".repeat(1023) + "\n";
        var big = new StringBuilder(OBJ);
        while (big.length() < 9 * 1024 * 1024) { big.append(padding); }
        var first = fixture("first");
        var second = fixture("second");
        files.put(first.model(), bytes(big.toString()));
        files.put(second.model(), bytes(big.toString()));
        files.put(ModIdentity.id("models/widget.mtl"), bytes(MTL));
        files.put(TEXTURE, png(16, 16));
        assertEquals(1, ObjModelSet.check(List.of(first), source).models().size());
        limits.clear();
        var failure = assertThrows(ObjModelException.class, () -> ObjModelSet.check(List.of(first, second), source));
        assertTrue(failure.getMessage().contains("bytes"), failure.getMessage());
        for (int i = 1; i < limits.size(); i++) { assertTrue(limits.get(i) <= limits.get(i - 1), "the remaining budget grew"); }
        assertTrue(limits.get(limits.size() - 1) < ObjModelCheck.MAX_SET_BYTES - big.length(), "the second model was offered the whole budget");
    }

    @Test void retainedTexturesAreResolvedAgainAgainstNewResources() {
        widget(OBJ, MTL);
        var textures = Map.of(TEXTURE, TEXTURE);
        assertEquals(textures, ObjModelSet.retexture(WIDGET, textures, source));
        files.put(TEXTURE, bytes("not a png"));
        assertEquals(Map.of(TEXTURE, WIDGET.fallbackTexture()), ObjModelSet.retexture(WIDGET, textures, source));
        files.remove(TEXTURE);
        assertEquals(Map.of(TEXTURE, WIDGET.fallbackTexture()), ObjModelSet.retexture(WIDGET, textures, source));
    }
}
