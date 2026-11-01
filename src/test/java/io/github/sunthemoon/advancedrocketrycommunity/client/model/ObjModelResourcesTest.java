package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pins the adapter and diagnostic behaviour shared by both resource reload listeners. */
class ObjModelResourcesTest {
    private static final ResourceLocation LOCATION = ModIdentity.id("models/resource.obj");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static ResourceManager manager(Resource resource, AtomicInteger lookups) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[] {ResourceManager.class}, (proxy, method, args) -> {
                    assertEquals("getResource", method.getName(), "no other resource-manager calls");
                    assertArrayEquals(new Object[] {LOCATION}, args);
                    lookups.incrementAndGet();
                    return Optional.ofNullable(resource);
                });
    }

    @Test void anAbsentResourceIsEmptyAndLookedUpOnlyOnce() throws Exception {
        var lookups = new AtomicInteger();
        assertTrue(ObjModelResources.source(manager(null, lookups)).read(LOCATION, 4).isEmpty());
        assertEquals(1, lookups.get());
    }

    @Test void exactLimitReadsCloseTheStreamAndOversizedReadsStillFailAndClose() throws Exception {
        for (int length : new int[] {4, 5}) {
            var lookups = new AtomicInteger();
            var opens = new AtomicInteger();
            var closes = new AtomicInteger();
            byte[] bytes = new byte[length];
            var resource = new Resource(null, () -> {
                opens.incrementAndGet();
                return new ByteArrayInputStream(bytes) {
                    @Override public void close() { closes.incrementAndGet(); }
                };
            });
            var source = ObjModelResources.source(manager(resource, lookups));
            if (length == 4) {
                assertArrayEquals(bytes, source.read(LOCATION, 4).orElseThrow());
            } else {
                var failure = assertThrows(ObjModelException.class, () -> source.read(LOCATION, 4));
                assertEquals(LOCATION + ": more than 4 bytes", failure.getMessage());
            }
            assertEquals(1, lookups.get());
            assertEquals(1, opens.get());
            assertEquals(1, closes.get());
        }
    }

    @Test void openReadAndCloseIoFailuresKeepTheSameDiagnosticAndCloseOpenedStreams() {
        for (String stage : new String[] {"open", "read", "close"}) {
            var lookups = new AtomicInteger();
            var closes = new AtomicInteger();
            var resource = new Resource(null, () -> {
                if (stage.equals("open")) { throw new IOException("open failed"); }
                return new InputStream() {
                    @Override public int read() throws IOException {
                        if (stage.equals("read")) { throw new IOException("read failed"); }
                        return -1;
                    }
                    @Override public void close() throws IOException {
                        closes.incrementAndGet();
                        if (stage.equals("close")) { throw new IOException("close failed"); }
                    }
                };
            });
            var source = ObjModelResources.source(manager(resource, lookups));
            var failure = assertThrows(ObjModelException.class, () -> source.read(LOCATION, 4));
            assertEquals(LOCATION + ": cannot be read", failure.getMessage());
            assertEquals(1, lookups.get());
            assertEquals(stage.equals("open") ? 0 : 1, closes.get());
        }
    }

    @Test void diagnosticsKeepNullMessagesSanitizeLineBreaksAndTruncateAtExactly512Characters() {
        assertEquals("null", ObjModelResources.diagnostic(new IOException()));
        assertEquals("", ObjModelResources.diagnostic(new IOException("")));
        assertEquals("a  b c", ObjModelResources.diagnostic(new IOException("a\r\nb\nc")));
        for (int length : new int[] {511, 512, 513, 1_024}) {
            assertEquals("x".repeat(Math.min(512, length)), ObjModelResources.diagnostic(new IOException("x".repeat(length))));
        }
    }
}
