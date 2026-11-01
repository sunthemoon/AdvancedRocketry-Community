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
 * @param modelYRotationDegrees the model-space rotation, after mirroring and before the offset/facing rotation
 * @param lightCellAboveController the sample height above the controller, outside occupied pattern cells
 */
public record ObjModelFixture(String id, ResourceLocation kind, ResourceLocation model, boolean mirrorX, boolean flipV,
        double scale, double offsetX, double offsetY, double offsetZ, ObjModelCheck.Bounds modelBounds,
        ResourceLocation fallbackTexture, String renderType, double modelYRotationDegrees, int lightCellAboveController) {
    /** Existing fixtures have no model-space rotation. */
    public ObjModelFixture(String id, ResourceLocation kind, ResourceLocation model, boolean mirrorX, boolean flipV,
            double scale, double offsetX, double offsetY, double offsetZ, ObjModelCheck.Bounds modelBounds,
            ResourceLocation fallbackTexture, String renderType) {
        this(id, kind, model, mirrorX, flipV, scale, offsetX, offsetY, offsetZ, modelBounds, fallbackTexture, renderType, 0, LIGHT_CELL_ABOVE);
    }

    public ObjModelFixture(String id, ResourceLocation kind, ResourceLocation model, boolean mirrorX, boolean flipV,
            double scale, double offsetX, double offsetY, double offsetZ, ObjModelCheck.Bounds modelBounds,
            ResourceLocation fallbackTexture, String renderType, double modelYRotationDegrees) {
        this(id, kind, model, mirrorX, flipV, scale, offsetX, offsetY, offsetZ, modelBounds, fallbackTexture, renderType,
                modelYRotationDegrees, LIGHT_CELL_ABOVE);
    }
    public static final String ENTITY_CUTOUT = "entity_cutout";
    /** Forge OBJ settings shared by every catalogued model; the quads take the given light, not the material's. */
    public static final boolean AUTOMATIC_CULLING = false, SHADE_QUADS = true, EMISSIVE_AMBIENT = false;
    /** The default light sample height, counted up from the controller. */
    public static final int LIGHT_CELL_ABOVE = 1;

    /**
     * The 1.12 centrifuge model on the modern 3 x 4 x 3 pattern. The C16c pattern is the mirror image of the 1.12
     * structure (local x runs east for a north-facing controller; the 1.12 renderer drew the hull to the west), so the
     * model is mirrored to cover the pattern. Its fallback is the centrifuge's NEW controller face.
     */
    public static final ObjModelFixture CENTRIFUGE = new ObjModelFixture("centrifuge", ModIdentity.id("centrifuge"),
            ModIdentity.id("models/centrifuge.obj"), true, true, 1, 1, -1, 2,
            new ObjModelCheck.Bounds(-2, 0, -2, 1, 4, 1), ModIdentity.id("textures/block/centrifuge.png"), ENTITY_CUTOUT);

    public static final ObjModelFixture CHEMICAL_REACTOR = new ObjModelFixture("chemical_reactor", ModIdentity.id("chemical_reactor"),
            ModIdentity.id("models/chemicalreactor.obj"), true, true, 1, -1, -1, 0,
            new ObjModelCheck.Bounds(-3, 0, 0, 0, 2, 2), ModIdentity.id("textures/block/chemical_reactor.png"), ENTITY_CUTOUT);
    public static final ObjModelFixture CRYSTALLIZER = new ObjModelFixture("crystallizer", ModIdentity.id("crystallizer"),
            ModIdentity.id("models/crystalliser.obj"), true, true, 1, -1, 0, 0,
            new ObjModelCheck.Bounds(0, 0, 0, 2, 2, 3), ModIdentity.id("textures/block/crystallizer.png"), ENTITY_CUTOUT, 90, 2);
    public static final ObjModelFixture CUTTING_MACHINE = new ObjModelFixture("cutting_machine", ModIdentity.id("cutting_machine"),
            ModIdentity.id("models/cuttingmachine.obj"), true, true, 1, -1, 0, 0,
            new ObjModelCheck.Bounds(0, 0, 0, 2, 1.505879, 3), ModIdentity.id("textures/block/cutting_machine.png"), ENTITY_CUTOUT, 90);
    public static final ObjModelFixture LATHE = new ObjModelFixture("lathe", ModIdentity.id("lathe"),
            ModIdentity.id("models/lathe.obj"), true, true, 1, 1, -1, 0,
            new ObjModelCheck.Bounds(-0.054134, -0.054134, -1, 1, 1.625, 3), ModIdentity.id("textures/block/lathe.png"), ENTITY_CUTOUT, 90);

    // RendererPrecisionLaserEtcher's idle transform, reflected across the modern pattern width.
    public static final ObjModelFixture PRECISION_LASER_ETCHER = new ObjModelFixture("precision_laser_etcher", ModIdentity.id("precision_laser_etcher"),
            ModIdentity.id("models/precisionlaseretcher.obj"), true, true, 1, -1, 0, 0,
            new ObjModelCheck.Bounds(-3, 0, -3, 0, 2.25, 0), ModIdentity.id("textures/block/precision_laser_etcher.png"), ENTITY_CUTOUT, 270, 3);

    /*
     * K3: each model is its 1.12 renderer's placement mirrored across the pattern's width, about the controller, like
     * the K2 machines (Root decision R248, after review findings W236-R1 and W236-R2). K3ObjModelTest pins this per
     * facing against the transcribed legacy transforms.
     */
    public static final ObjModelFixture ASTROBODY_DATA_PROCESSOR = new ObjModelFixture("astrobody_data_processor", ModIdentity.id("astrobody_data_processor"),
            ModIdentity.id("models/astrobodydataprocessor.obj"), true, true, 1, -1, -1, 0,
            new ObjModelCheck.Bounds(0, 0, 0, 2.000001, 1.625, 3), ModIdentity.id("textures/block/astrobody_data_processor.png"), ENTITY_CUTOUT, 90);
    public static final ObjModelFixture OBSERVATORY = new ObjModelFixture("observatory", ModIdentity.id("observatory"),
            ModIdentity.id("models/observatory.obj"), true, true, 1, 0.5, -1, 2.5,
            new ObjModelCheck.Bounds(-2.5, -0.005001, -2.5, 2.5, 4.995, 2.5), ModIdentity.id("textures/block/observatory.png"), ENTITY_CUTOUT, 90, 4);
    public static final ObjModelFixture PLANETARY_BEACON = new ObjModelFixture("planetary_beacon", ModIdentity.id("planetary_beacon"),
            ModIdentity.id("models/beacon.obj"), true, true, 1, 0.5, 0, 0.5,
            new ObjModelCheck.Bounds(-0.875, 0, -1.5, 2.5, 5, 1.5), ModIdentity.id("textures/block/planetary_beacon_front.png"), ENTITY_CUTOUT, 90, 5);
    public static final ObjModelFixture SOLAR_ARRAY = new ObjModelFixture("solar_array", ModIdentity.id("solar_array"),
            ModIdentity.id("models/solar_array.obj"), true, true, 1, 0, 0, 1,
            new ObjModelCheck.Bounds(0, 0, -2, 1, 1, 1), ModIdentity.id("textures/block/solar_array_front.png"), ENTITY_CUTOUT, 270);

    // K4 preserves the transcribed legacy idle orientation; source geometry is never rewritten.
    public static final ObjModelFixture BLACK_HOLE_GENERATOR = new ObjModelFixture("black_hole_generator", ModIdentity.id("black_hole_generator"),
            ModIdentity.id("models/blackholegenerator.obj"), true, true, 1, 0.5, 0.5, 0.5,
            new ObjModelCheck.Bounds(-1.25, -4.5, -0.5, 1.25, 1.5, 2.25), ModIdentity.id("textures/block/railgun_front.png"), ENTITY_CUTOUT, 0, 4);

    public static final ObjModelFixture ORBITAL_LASER_DRILL = new ObjModelFixture("orbital_laser_drill", ModIdentity.id("orbital_laser_drill"),
            ModIdentity.id("models/orbitallaserdrill.obj"), true, true, 1, 5, 0, 3,
            new ObjModelCheck.Bounds(-5.000025, -0.000025, -6.00005, 4.000025, 3.25, 5), ModIdentity.id("textures/block/orbital_laser_front.png"), ENTITY_CUTOUT, 90, 4);

    public static final ObjModelFixture PRECISION_ASSEMBLER = new ObjModelFixture("precision_assembler", ModIdentity.id("precision_assembler"),
            ModIdentity.id("models/precisionassembler.obj"), true, true, 1, 0, 0, 0,
            new ObjModelCheck.Bounds(0, 0, 0, 3, 3, 4), ModIdentity.id("textures/block/precision_assembler_front.png"), ENTITY_CUTOUT, 90, 3);

    public static final ObjModelFixture RAILGUN = new ObjModelFixture("railgun", ModIdentity.id("railgun"),
            ModIdentity.id("models/railgun.obj"), true, true, 1, 0.5, 0, 3.5,
            new ObjModelCheck.Bounds(-4.49987, 0, -4.49987, 4.499871, 11, 4.49987), ModIdentity.id("textures/block/railgun_front.png"), ENTITY_CUTOUT, 90, 12);

    public static final ObjModelFixture ROLLING_MACHINE = new ObjModelFixture("rolling_machine", ModIdentity.id("rolling_machine"),
            ModIdentity.id("models/rollingmachine.obj"), true, true, 1, -1, 0, 0,
            new ObjModelCheck.Bounds(-0.25, -0.25, 0, 3, 1.5, 5), ModIdentity.id("textures/block/lathe.png"), ENTITY_CUTOUT, 90, 3);

    public static final ObjModelFixture ELEVATOR_ANCHOR = new ObjModelFixture("elevator_anchor", ModIdentity.id("elevator_anchor"),
            ModIdentity.id("models/spaceelevator.obj"), true, true, 1, 1, 0, 5,
            new ObjModelCheck.Bounds(-5, -1, -4.999871, 4.999871, 508, 3.999871), ModIdentity.id("textures/block/railgun_front.png"), ENTITY_CUTOUT, 90, 2);

    public static final List<ObjModelFixture> CATALOG = List.of(CENTRIFUGE, CHEMICAL_REACTOR, CRYSTALLIZER, CUTTING_MACHINE, LATHE, PRECISION_LASER_ETCHER,
            ASTROBODY_DATA_PROCESSOR, OBSERVATORY, PLANETARY_BEACON, SOLAR_ARRAY,
            BLACK_HOLE_GENERATOR, ORBITAL_LASER_DRILL, PRECISION_ASSEMBLER, RAILGUN, ROLLING_MACHINE, ELEVATOR_ANCHOR);

    /** The rotation around +Y that matches the controller's pattern transform (clockwise viewed from above). */
    public static float yRotationDegrees(Direction facing) {
        return switch (facing) {
            case NORTH -> 0; case EAST -> -90; case SOUTH -> 180; case WEST -> 90;
            default -> throw new IllegalArgumentException("Controller horizontal facing");
        };
    }

    /** A source-model point relative to the controller block's corner: mirror, scale, offset, then rotation. */
    public double[] toController(Direction facing, double x, double y, double z) {
        double modelRadians = Math.toRadians(modelYRotationDegrees), mx = mirrorX ? -x : x;
        double px = (Math.cos(modelRadians) * mx + Math.sin(modelRadians) * z) * scale + offsetX - 0.5;
        double py = y * scale + offsetY;
        double pz = (-Math.sin(modelRadians) * mx + Math.cos(modelRadians) * z) * scale + offsetZ - 0.5;
        double radians = Math.toRadians(yRotationDegrees(facing));
        double cos = Math.cos(radians), sin = Math.sin(radians);
        return new double[] {cos * px + sin * pz + 0.5, py, -sin * px + cos * pz + 0.5};
    }
}
