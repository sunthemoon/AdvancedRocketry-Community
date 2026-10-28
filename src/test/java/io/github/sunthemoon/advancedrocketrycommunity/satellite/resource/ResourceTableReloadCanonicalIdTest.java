package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResourceTableReloadCanonicalIdTest {
    private static final ResourceLocation FILE = ResourceLocation.tryParse("g".repeat(167));
    private static final PreparableReloadListener.PreparationBarrier IMMEDIATE =
            new PreparableReloadListener.PreparationBarrier() {
                @Override
                public <T> CompletableFuture<T> wait(T value) {
                    return CompletableFuture.completedFuture(value);
                }
            };
    @TempDir Path root;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void anOversizedGasFileKeepsTheLastGenerationWithoutAnExceptionalReload() {
        var manager = new ResourceTableReloadListener.Manager();
        manager.accept(ResourceTables.EMPTY);
        var listener = new ResourceTableReloadListener(manager, new CelestialCatalogManager());
        assertDoesNotThrow(() -> reload(listener).join());
        assertEquals(1, manager.generation());
        assertSame(ResourceTables.EMPTY, manager.current().orElseThrow());
    }

    @Test
    void anInitialOversizedGasFileUsesTheFileIdentifyingDiagnostic() {
        var manager = new ResourceTableReloadListener.Manager();
        var listener = new ResourceTableReloadListener(manager, new CelestialCatalogManager());
        var failure = assertThrows(CompletionException.class, () -> reload(listener).join());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("Initial resource tables are invalid: gas_harvest " + FILE
                + ": id must be an ID of at most 176 characters", failure.getCause().getMessage());
        assertEquals(0, manager.generation());
        assertTrue(manager.current().isEmpty());
    }

    private CompletableFuture<Void> reload(ResourceTableReloadListener listener) {
        byte[] raw = ("{\"schema_version\":1,\"body\":\"advancedrocketrycommunity:gas_giant\","
                + "\"products\":[{\"item\":\"minecraft:iron_ingot\",\"amount_per_1000_ticks\":8}]}")
                .getBytes(StandardCharsets.UTF_8);
        var pack = new PathPackResources("canonical-id-test", root, false);
        var resource = new Resource(pack, () -> new ByteArrayInputStream(raw));
        var resourceId = ResourceLocation.tryBuild(FILE.getNamespace(), "gas_harvest/" + FILE.getPath() + ".json");
        // In-memory listing avoids platform filesystem limits on the deliberately long file name.
        var resources = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[] {ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("listResources")) {
                        return args[0].equals(ResourceTableCodec.GAS_DIRECTORY)
                                ? Map.of(resourceId, resource) : Map.of();
                    }
                    throw new AssertionError("Unexpected resource operation: " + method.getName());
                });
        return listener.reload(IMMEDIATE, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE,
                Runnable::run, Runnable::run);
    }
}
