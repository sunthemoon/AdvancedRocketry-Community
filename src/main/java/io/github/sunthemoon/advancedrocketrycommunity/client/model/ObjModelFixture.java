package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-066 section 7.1: how one machine model is placed and drawn. The fixture freezes the orientation (a mirror in x,
 * then the controller rotation), the scale, the rendering and the bounding volume; it never changes collision or the
 * multiblock match. A test-resource copy under {@code obj-model-fixtures/} pins every value.
 *
 * @param kind the controller kind whose formed flag shows the model
 * @param model the OBJ, a compiled resource location in the mod namespace; never a file path or URL
 * @param mirrorX whether the checked OBJ is mirrored in x before Forge parses it
 * @param offsetX the model origin relative to the controller block's corner, after the mirror, facing north
 * @param modelBounds the box the OBJ's positions must lie in, in model units before the mirror
 * @param fallbackTexture the NEW or project-owned texture drawn instead of a missing or unusable one
 * @param renderType the render type of every mesh; only {@code entity_cutout} exists today
 */
public record ObjModelFixture(String id, ResourceLocation kind, ResourceLocation model, boolean mirrorX, boolean flipV,
        double scale, double offsetX, double offsetY, double offsetZ, ObjModelCheck.Bounds modelBounds,
        ResourceLocation fallbackTexture, String renderType) {
    public static final String ENTITY_CUTOUT = "entity_cutout";
    /** Forge OBJ settings shared by every catalogued model; the quads take the given light, not the material's. */
    public static final boolean AUTOMATIC_CULLING = false, SHADE_QUADS = true, EMISSIVE_AMBIENT = false;
    /** The light cell, counted up from the controller; every catalogued pattern keeps it empty. */
    public static final int LIGHT_CELL_ABOVE = 1;

    /**
     * The 1.12 centrifuge model on the modern 3 x 4 x 3 pattern. The C16c pattern is the mirror image of the 1.12
     * structure (local x runs east for a north-facing controller; the 1.12 renderer drew the hull to the west), so the
     * model is mirrored to cover the pattern. Its fallback is the centrifuge's NEW controller face.
     */
    public static final ObjModelFixture CENTRIFUGE = new ObjModelFixture("centrifuge", ModIdentity.id("centrifuge"),
            ModIdentity.id("models/centrifuge.obj"), true, true, 1, 1, -1, 2,
            new ObjModelCheck.Bounds(-2, 0, -2, 1, 4, 1), ModIdentity.id("textures/block/centrifuge.png"), ENTITY_CUTOUT);

    public static final List<ObjModelFixture> CATALOG = List.of(CENTRIFUGE);

    /** The rotation around +Y that matches the controller's pattern transform (clockwise viewed from above). */
    public static float yRotationDegrees(Direction facing) {
        return switch (facing) {
            case NORTH -> 0; case EAST -> -90; case SOUTH -> 180; case WEST -> 90;
            default -> throw new IllegalArgumentException("Controller horizontal facing");
        };
    }

    /** A source-model point relative to the controller block's corner: mirror, scale, offset, then rotation. */
    public double[] toController(Direction facing, double x, double y, double z) {
        double px = (mirrorX ? -x : x) * scale + offsetX - 0.5, py = y * scale + offsetY, pz = z * scale + offsetZ - 0.5;
        double radians = Math.toRadians(yRotationDegrees(facing));
        double cos = Math.cos(radians), sin = Math.sin(radians);
        return new double[] {cos * px + sin * pz + 0.5, py, -sin * px + cos * pz + 0.5};
    }
}
