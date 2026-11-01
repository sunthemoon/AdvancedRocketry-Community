package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelSet;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W244-R4: the capsule's model and texture are checked once per reload, and anything unusable keeps the line cage. */
class CapsuleResourcesTest {
    private static final Path ASSETS = Path.of("src/imported/v1.8/resources/assets/advancedrocketrycommunity");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static Map<ResourceLocation, byte[]> good() throws Exception {
        var files = new HashMap<ResourceLocation, byte[]>();
        files.put(CapsuleResources.MODEL, Files.readAllBytes(ASSETS.resolve("models/spaceelevator.obj")));
        files.put(CapsuleResources.TEXTURE, Files.readAllBytes(ASSETS.resolve("textures/models/spaceelevatorcapsule.png")));
        return files;
    }

    private static ObjModelSet.Source source(Map<ResourceLocation, byte[]> files, AtomicInteger reads) {
        return (location, limit) -> {
            reads.incrementAndGet();
            return Optional.ofNullable(files.get(location));
        };
    }

    @Test void aGoodModelAndTextureArePublishedAndRenderingReadsNothing() throws Exception {
        var diagnostics = new ArrayList<String>();
        var resources = new CapsuleResources(diagnostics::add);
        assertTrue(resources.mesh().isEmpty(), "the line cage until the first reload");
        var reads = new AtomicInteger();
        var prepared = resources.prepare(source(good(), reads));
        assertTrue(prepared.isPresent());
        assertEquals(2, reads.get(), "one read of the model and one of the texture per reload");
        resources.apply(prepared);
        for (int frame = 0; frame < 1_000; frame++) { assertEquals(416, resources.mesh().orElseThrow().vertices()); }
        assertEquals(2, reads.get(), "frames read only the published mesh");
        assertEquals(List.of(), diagnostics);
    }

    @Test void aMissingOrUnreadableTextureKeepsTheLineCage() throws Exception {
        byte[] png = good().get(CapsuleResources.TEXTURE);
        byte[] oversized = png.clone();
        ByteBuffer.wrap(oversized, 16, 4).putInt(2_048);
        byte[] corrupt = png.clone();
        for (int i = 60; i < corrupt.length - 20; i++) { corrupt[i] = (byte) 0x5A; }
        var cases = new HashMap<String, byte[]>();
        cases.put("missing", null);
        cases.put("not a PNG", "not a png".getBytes(StandardCharsets.US_ASCII));
        cases.put("header only", Arrays.copyOf(png, 33));
        cases.put("truncated", Arrays.copyOf(png, png.length / 2));
        cases.put("corrupt image data", corrupt);
        cases.put("over 1,024 pixels a side", oversized);
        for (var entry : cases.entrySet()) {
            var diagnostics = new ArrayList<String>();
            var resources = new CapsuleResources(diagnostics::add);
            resources.apply(resources.prepare(source(good(), new AtomicInteger())));
            assertTrue(resources.mesh().isPresent());
            var files = good();
            if (entry.getValue() == null) { files.remove(CapsuleResources.TEXTURE); } else { files.put(CapsuleResources.TEXTURE, entry.getValue()); }
            var prepared = resources.prepare(source(files, new AtomicInteger()));
            assertTrue(prepared.isEmpty(), entry.getKey());
            resources.apply(prepared);
            assertTrue(resources.mesh().isEmpty(), entry.getKey() + ": a reload never keeps a stale mesh");
            assertEquals(1, diagnostics.size(), entry.getKey());
            assertTrue(diagnostics.get(0).contains("spaceelevatorcapsule.png"), diagnostics.get(0));
        }
    }

    @Test void aMissingOrUnusableModelKeepsTheLineCage() throws Exception {
        String obj = new String(good().get(CapsuleResources.MODEL), StandardCharsets.UTF_8);
        var cases = new HashMap<String, byte[]>();
        cases.put("missing", null);
        cases.put("no Capsule group", obj.replace("o Capsule", "o Pod").getBytes(StandardCharsets.UTF_8));
        cases.put("not an OBJ", "\u0000garbage".getBytes(StandardCharsets.UTF_8));
        for (var entry : cases.entrySet()) {
            var files = good();
            if (entry.getValue() == null) { files.remove(CapsuleResources.MODEL); } else { files.put(CapsuleResources.MODEL, entry.getValue()); }
            var diagnostics = new ArrayList<String>();
            var resources = new CapsuleResources(diagnostics::add);
            var prepared = resources.prepare(source(files, new AtomicInteger()));
            assertTrue(prepared.isEmpty(), entry.getKey());
            resources.apply(prepared);
            assertTrue(resources.mesh().isEmpty(), entry.getKey());
            assertEquals(1, diagnostics.size(), entry.getKey());
            assertTrue(diagnostics.get(0).contains("spaceelevator.obj"), diagnostics.get(0));
        }
    }
}
