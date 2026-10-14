package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelTestFiles.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reload keeps the last good set on failure and clears the old resources on success (ADR-066 section 7.1). */
class ObjModelReloadTest {
    private static final ResourceLocation TEXTURE = ModIdentity.id("textures/models/widget.png");
    private static final ResourceLocation OTHER = ModIdentity.id("textures/models/other.png");
    private static final PreparableReloadListener.PreparationBarrier IMMEDIATE = new PreparableReloadListener.PreparationBarrier() {
        @Override public <T> CompletableFuture<T> wait(T value) { return CompletableFuture.completedFuture(value); }
    };
    @TempDir Path root;
    private final ObjModelCache<String> cache = new ObjModelCache<>();
    private final Set<ResourceLocation> released = new HashSet<>();
    private final List<String> diagnostics = new ArrayList<>();
    private final List<String> compiled = new ArrayList<>();
    private boolean compilerFails;
    private final ObjModelReloadListener<String> listener = new ObjModelReloadListener<>(List.of(WIDGET), cache,
            (fixture, input) -> {
                if (compilerFails) { throw new IllegalStateException("Forge refused the fixture"); }
                String model = fixture.id() + "#" + compiled.size();
                compiled.add(new String(input, StandardCharsets.UTF_8));
                return model;
            }, released::addAll, diagnostics::add);

    @Test void successPublishesAndAFailedReloadKeepsTheLastGoodSet() throws Exception {
        files(OBJ, MTL);
        reload();
        assertTrue(listener.lastAccepted());
        var good = cache.get("widget").orElseThrow();
        assertEquals("widget#0", good.model());
        assertEquals(TEXTURE, good.texture(TEXTURE));
        assertEquals(WIDGET.fallbackTexture(), good.texture(ModIdentity.id("textures/missing.png")));
        assertEquals(new String(ObjModelCheck.mirrorX(bytes(OBJ), "widget.obj"), StandardCharsets.UTF_8), compiled.get(0));
        for (String broken : List.of(OBJ.replace("usemtl Body", "usemtl Other"), OBJ.replace("v 0 0 1", "v 0 0 x"),
                quads(ObjModelCheck.MAX_BAKED_OBJECT_VERTICES / 4 + 1), OBJ.replace("mtllib widget.mtl", "mtllib /tmp/widget.mtl"))) {
            int count = diagnostics.size();
            write("models/widget.obj", bytes(broken));
            reload();
            assertFalse(listener.lastAccepted());
            assertSame(good.model(), cache.get("widget").orElseThrow().model());
            assertEquals(count + 1, diagnostics.size());
            assertTrue(diagnostics.get(count).length() < 640, diagnostics.get(count));
        }
        assertEquals(1, compiled.size(), "a refused set reached the compiler");
        assertTrue(released.isEmpty());
    }

    @Test void aFailedReloadStillResolvesTheRetainedTexturesAgain() throws Exception {
        files(OBJ, MTL);
        reload();
        Files.delete(file("textures/models/widget.png"));
        write("models/widget.obj", bytes(OBJ.replace("v 0 0 1", "v 0 0 x")));
        reload();
        assertFalse(listener.lastAccepted());
        assertEquals(WIDGET.fallbackTexture(), cache.get("widget").orElseThrow().texture(TEXTURE));
    }

    @Test void successClearsTheOldSetAndReleasesItsTextures() throws Exception {
        files(OBJ, MTL);
        reload();
        write("models/widget.mtl", bytes(MTL.replace("models/widget", "models/other")));
        write("textures/models/other.png", png(8, 8));
        reload();
        assertTrue(listener.lastAccepted());
        assertEquals("widget#1", cache.get("widget").orElseThrow().model());
        assertEquals(OTHER, cache.get("widget").orElseThrow().texture(OTHER));
        assertEquals(Set.of(TEXTURE), released);
        Files.delete(file("models/widget.obj"));
        reload();
        assertTrue(listener.lastAccepted());
        assertTrue(cache.get("widget").isEmpty());
        assertEquals(Set.of(TEXTURE, OTHER), released);
        assertFalse(released.contains(WIDGET.fallbackTexture()));
    }

    @Test void aCompilerFailureKeepsTheLastGoodSetAndAFirstFailureLeavesNone() throws Exception {
        files(OBJ, MTL);
        compilerFails = true;
        reload();
        assertFalse(listener.lastAccepted());
        assertTrue(cache.entries().isEmpty());
        assertTrue(diagnostics.get(0).contains("Forge's OBJ loader refused it"), diagnostics.get(0));
        compilerFails = false;
        reload();
        var good = cache.get("widget").orElseThrow();
        compilerFails = true;
        reload();
        assertSame(good.model(), cache.get("widget").orElseThrow().model());
        assertEquals(Map.of(TEXTURE, TEXTURE), cache.get("widget").orElseThrow().textures());
    }

    private void files(String obj, String mtl) throws IOException {
        write("models/widget.obj", bytes(obj));
        write("models/widget.mtl", bytes(mtl));
        write("textures/models/widget.png", png(16, 16));
        write("textures/block/widget_fallback.png", png(16, 16));
    }

    private Path file(String path) { return root.resolve("assets").resolve(ModIdentity.MOD_ID).resolve(path); }

    private void write(String path, byte[] data) throws IOException {
        Files.createDirectories(file(path).getParent());
        Files.write(file(path), data);
    }

    private void reload() {
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(new PathPackResources("fixture", root, false)))) {
            listener.reload(IMMEDIATE, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Runnable::run, Runnable::run).join();
        }
    }
}
