package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Small synthetic model files for the OBJ path tests; none is copied from any asset. */
final class ObjModelTestFiles {
    static final String OBJ = """
            # synthetic test cube
            mtllib widget.mtl
            o Body
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            v 0 0 1
            v 1 0 1
            v 1 1 1
            v 0 1 1
            vt 0 0
            vt 1 0
            vt 1 1
            vt 0 1
            vn 0 0 -1
            vn 0 0 1
            usemtl Body
            s off
            f 1/1/1 4/4/1 3/3/1 2/2/1
            f 5/1/2 6/2/2 7/3/2 8/4/2
            f 1/1/1 2/2/1 3/3/1
            l 1 2
            """;
    static final String MTL = """
            newmtl Body
            Kd 1 1 1
            map_Kd advancedrocketrycommunity:models/widget
            """;
    static final ObjModelFixture WIDGET = fixture("widget");

    private ObjModelTestFiles() { }

    static ObjModelFixture fixture(String name) {
        return new ObjModelFixture(name, ModIdentity.id(name), ModIdentity.id("models/" + name + ".obj"), true, true, 1, 1, 0, 0,
                new ObjModelCheck.Bounds(-1, -1, -1, 2, 2, 2), ModIdentity.id("textures/block/" + name + "_fallback.png"),
                ObjModelFixture.ENTITY_CUTOUT);
    }

    static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    static byte[] png(int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) { image.setRGB(x, y, 0xFF000000 | (x * 37 + y * 101) & 0xFFFFFF); }
        }
        var output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", output);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return output.toByteArray();
    }

    /** An OBJ of {@code quads} quads over four positions, in one material. */
    static String quads(int quads) {
        var text = new StringBuilder("mtllib widget.mtl\nv 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nusemtl Body\n");
        for (int i = 0; i < quads; i++) { text.append("f 1 2 3 4\n"); }
        return text.toString();
    }
}
