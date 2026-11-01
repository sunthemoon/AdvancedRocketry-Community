package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CapsuleCageGeometryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void approvedTextureAndStationaryMeshBudgetArePinned() throws Exception {
        assertEquals("advancedrocketrycommunity:textures/models/spaceelevatorcapsule.png", ElevatorCapsuleClient.TEXTURE.toString());
        var image = Files.readAllBytes(Path.of("src/imported/v1.8/resources/assets/advancedrocketrycommunity/textures/models/spaceelevatorcapsule.png"));
        assertEquals("12c6ccc1aaed6d7135321d6299cb292d9da4ecd09cde462f33797b931b5dfcaa",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image)));
        assertEquals(112, CapsuleCageGeometry.VERTICES);
        for (double height : new double[] {0.5, 2.05, 3}) {
            var strips = CapsuleCageGeometry.strips(height);
            assertEquals(28, strips.size());
            for (var strip : strips) {
                assertEquals(1, strip.nx()*strip.nx() + strip.nz()*strip.nz(), 1.0E-6);
                for (var point : List.of(strip.a(),strip.b(),strip.c(),strip.d())) {
                    assertTrue(Math.abs(point.x()) <= 0.67 && Math.abs(point.z()) <= 0.67);
                    assertTrue(point.y() >= -0.05 - 1.0E-6 && point.y() <= height + 1.0E-6);
                    assertTrue(point.u() >= 0 && point.u() <= 1 && point.v() >= 0 && point.v() <= 1);
                }
            }
            assertEquals(strips, CapsuleCageGeometry.strips(height));
        }
    }

    @Test void invalidHeightCannotCreateUnboundedGeometry() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 0.49, 3.01}) {
            assertThrows(IllegalArgumentException.class, () -> CapsuleCageGeometry.strips(value));
        }
    }
}
