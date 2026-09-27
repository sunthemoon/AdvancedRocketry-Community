package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfile;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkyProfileReloadTest {
    @TempDir Path root;
    private final AtomicInteger resets = new AtomicInteger();
    private final List<String> diagnostics = new ArrayList<>();
    private final SkyProfileReloadListener listener = new SkyProfileReloadListener(resets::incrementAndGet, diagnostics::add);
    private static final PreparableReloadListener.PreparationBarrier IMMEDIATE = new PreparableReloadListener.PreparationBarrier() {
        @Override public <T> CompletableFuture<T> wait(T value) { return CompletableFuture.completedFuture(value); }
    };

    @Test void winningResourceOverridesInvalidLowerPackAndReloadRemovesOldEntries() throws Exception {
        write(root, "mars", "{");
        Path higher = root.resolve("higher");
        write(higher, "mars", profile());
        try (var resources = resources(List.of(new PathPackResources("base", root, false), new PathPackResources("higher", higher, false)))) {
            reload(resources, IMMEDIATE).join();
        }
        assertTrue(listener.lastAccepted());
        assertEquals(1, listener.profiles().size());
        assertEquals(SkyProfiles.builtins().get(ModIdentity.id("mars")), listener.profiles().get(ModIdentity.id("mars")));
        Files.delete(file(root, "mars"));
        reload();
        assertTrue(listener.profiles().isEmpty());
        assertEquals(2, resets.get());
    }

    @Test void invalidRawInputsKeepCompletePriorMapAndEmitOneBoundedDiagnostic() throws Exception {
        write(root, "mars", profile());
        reload();
        var before = listener.profiles();
        for (var bad : List.of("{", "[]", "{\"schema_version\":1,\"schema_version\":1}",
                " ".repeat(SkyProfile.MAX_BYTES) + "{}", "[".repeat(17) + "0" + "]".repeat(17),
                profile().replace("\"schema_version\":1", "\"schema_version\":2"),
                profile().replace("minecraft:ambient.basalt_deltas.additions", "INVALID ID"),
                "{\"" + "x".repeat(1000) + "\":0,\"" + "x".repeat(1000) + "\":1}")) {
            int count = diagnostics.size();
            write(root, "broken", bad);
            reload();
            assertSame(before, listener.profiles());
            assertFalse(listener.lastAccepted());
            assertEquals(count + 1, diagnostics.size());
            assertTrue(diagnostics.get(count).length() < 640);
        }
        Files.write(file(root, "broken"), new byte[] {(byte) 0xC3, (byte) 0x28});
        reload();
        assertSame(before, listener.profiles());
        assertFalse(listener.lastAccepted());
    }

    @Test void initialFailureUsesDefaultsThenValidRepairCanPublish() throws Exception {
        write(root, "broken", "{}");
        reload();
        assertSame(SkyProfiles.builtins(), listener.profiles());
        assertFalse(listener.lastAccepted());
        write(root, "broken", profile());
        reload();
        assertTrue(listener.lastAccepted());
        assertEquals(1, listener.profiles().size());
    }

    @Test void countAndResourceIdBoundsAreCheckedBeforePublishing() throws Exception {
        for (int index = 0; index < 128; index++) { write(root, "p" + index, profile()); }
        reload();
        assertTrue(listener.lastAccepted());
        assertEquals(128, listener.profiles().size());
        var before = listener.profiles();
        write(root, "overflow", profile());
        reload();
        assertSame(before, listener.profiles());
        assertFalse(listener.lastAccepted());
        Files.delete(file(root, "overflow"));
        Files.delete(file(root, "p0"));
        write(root, "x".repeat(128), profile());
        reload();
        assertSame(before, listener.profiles());
        assertFalse(listener.lastAccepted());
    }

    @Test void preparationDoesNotPublishOrResetUntilBarrierCompletes() throws Exception {
        write(root, "custom", profile());
        var gate = new CompletableFuture<Void>();
        var barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override public <T> CompletableFuture<T> wait(T prepared) { return gate.thenApply(ignored -> prepared); }
        };
        try (var resources = resources(List.of(new PathPackResources("fixture", root, false)))) {
            var task = reload(resources, barrier);
            assertFalse(task.isDone());
            assertSame(SkyProfiles.builtins(), listener.profiles());
            assertEquals(0, resets.get());
            gate.complete(null);
            task.join();
        }
        assertEquals(1, listener.profiles().size());
        assertEquals(1, resets.get());
    }

    @Test void failedResourceIsClosedAndDoesNotDiscardOtherProfiles() throws Exception {
        write(root, "mars", profile());
        reload();
        var before = listener.profiles();
        var closes = new AtomicInteger();
        var broken = new PathPackResources("failure", root, false) {
            @Override public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
                super.listResources(type, namespace, directory, output);
                output.accept(ModIdentity.id(SkyProfile.DIRECTORY + "/broken.json"), () -> new InputStream() {
                    @Override public int read() throws IOException { throw new IOException("injected read failure"); }
                    @Override public void close() { closes.incrementAndGet(); }
                });
            }
        };
        try (var resources = resources(List.of(broken))) { reload(resources, IMMEDIATE).join(); }
        assertSame(before, listener.profiles());
        assertEquals(1, closes.get());
        assertTrue(diagnostics.get(0).contains("injected read failure"));
    }

    private void reload() {
        try (var resources = resources(List.of(new PathPackResources("fixture", root, false)))) {
            reload(resources, IMMEDIATE).join();
        }
    }

    private CompletableFuture<Void> reload(MultiPackResourceManager resources, PreparableReloadListener.PreparationBarrier barrier) {
        return listener.reload(barrier, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Runnable::run, Runnable::run);
    }

    private static MultiPackResourceManager resources(List<net.minecraft.server.packs.PackResources> packs) {
        return new MultiPackResourceManager(PackType.CLIENT_RESOURCES, packs);
    }

    private static String profile() { return SkyProfiles.builtins().get(ModIdentity.id("mars")).encode().toString(); }
    private static Path file(Path base, String name) {
        return base.resolve("assets/advancedrocketrycommunity/" + SkyProfile.DIRECTORY + "/" + name + ".json");
    }
    private static void write(Path base, String name, String content) throws IOException {
        var path = file(base, name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
