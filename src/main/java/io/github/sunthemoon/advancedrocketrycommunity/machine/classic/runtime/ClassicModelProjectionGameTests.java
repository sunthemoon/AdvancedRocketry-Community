package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;
import io.github.sunthemoon.advancedrocketrycommunity.sound.MachineSoundProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-066 section 7.1: the formed flag of a catalogued client model and its render volume, with no custom channel. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicModelProjectionGameTests {
    private ClassicModelProjectionGameTests() { }

    @GameTest(template = "empty", batch = "w51_centrifuge_model", timeoutTicks = 30)
    public static void centrifugeProjectsFormedFlagAndPatternFootprint(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            try (var f = new C16cMachineFixture(helper, "centrifuge", facing)) {
                assertProjection(helper, f.controller, true);
                helper.assertTrue(f.controller.getRenderBoundingBox().equals(footprint(f)),
                        "Centrifuge render volume differs from its pattern footprint facing " + facing);
                var motor = f.world(f.pattern.cells().entrySet().stream()
                        .filter(cell -> cell.getValue() instanceof PatternMatcher.BlockTag tag && tag.tagId().endsWith(":motors"))
                        .findFirst().orElseThrow().getKey());
                var state = helper.getLevel().getBlockState(motor);
                helper.getLevel().setBlock(motor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                f.manager.changed(motor); f.tick(1);
                helper.assertTrue(!f.controller.formed(), "Broken centrifuge remained formed");
                assertProjection(helper, f.controller, false);
                helper.assertTrue(f.controller.getRenderBoundingBox().equals(footprint(f)),
                        "An unformed centrifuge changed its render volume");
                helper.getLevel().setBlock(motor, state, Block.UPDATE_ALL);
                f.manager.changed(motor); f.tick(2);
                assertProjection(helper, f.controller, true);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w51_uncatalogued_model", timeoutTicks = 30)
    public static void uncataloguedControllerKeepsItsUpdateTagAndVolume(GameTestHelper helper) {
        try (var f = new C16cMachineFixture(helper, "precision_laser_etcher", Direction.NORTH)) {
            helper.assertTrue(f.controller.getUpdateTag().getAllKeys().equals(Set.of("active")) && !f.controller.modelFormed(),
                    "An uncatalogued controller projected a model flag");
            BlockPos origin = f.controller.getBlockPos();
            helper.assertTrue(f.controller.getRenderBoundingBox().equals(new AABB(origin)),
                    "An uncatalogued controller changed its render volume");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w233_chemical_model", timeoutTicks = 30)
    public static void chemicalReactorProjectsFormedModel(GameTestHelper helper) { exerciseC16c(helper, "chemical_reactor"); }

    @GameTest(template = "empty", batch = "w233_crystallizer_model", timeoutTicks = 30)
    public static void crystallizerProjectsFormedModel(GameTestHelper helper) { exerciseC16c(helper, "crystallizer"); }

    private static void exerciseC16c(GameTestHelper helper, String kind) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            try (var f = new C16cMachineFixture(helper, kind, facing)) {
                var part = f.world(f.pattern.cells().entrySet().stream()
                        .filter(cell -> cell.getValue() instanceof PatternMatcher.ExactBlock
                                || cell.getValue() instanceof PatternMatcher.BlockTag tag && tag.tagId().endsWith(":motors"))
                        .findFirst().orElseThrow().getKey());
                exerciseTransition(helper, f.controller, f.manager, part, f::tick, footprint(f));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w233_cutting_model", timeoutTicks = 30)
    public static void cuttingMachineProjectsFormedModelAndSawVolume(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            try (var f = new ArcCuttingGameTests.Fixture(helper, false, facing)) {
                var cells = new AABB(f.positions.get(0));
                for (var position : f.positions) { cells = cells.minmax(new AABB(position)); }
                var visual = new AABB(cells.minX, cells.minY, cells.minZ, cells.maxX, cells.maxY + 0.505879, cells.maxZ);
                exerciseTransition(helper, f.controller, f.manager, f.wrongPart, f::tick, visual);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w233_lathe_model", timeoutTicks = 30)
    public static void latheProjectsFormedModel(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            try (var f = new ClassicLatheGameTests.Fixture(helper, facing, false)) {
                // The fixture's extra redstone signal cell is not part of the generated pattern.
                var cells = new AABB(f.origin);
                for (var position : f.positions) { if (!position.equals(f.signal)) { cells = cells.minmax(new AABB(position)); } }
                exerciseTransition(helper, f.controller, f.manager, f.motor, f::tick, cells);
            }
        }
        helper.succeed();
    }

    private static void exerciseTransition(GameTestHelper helper, ClassicControllerBlockEntity controller,
            ClassicMachineManager manager, BlockPos part, IntConsumer tick, AABB volume) {
        assertProjection(helper, controller, true);
        helper.assertTrue(controller.getRenderBoundingBox().equals(volume), "Formed model render volume differs");
        var state = helper.getLevel().getBlockState(part);
        helper.getLevel().setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        manager.changed(part); tick.accept(1);
        assertProjection(helper, controller, false);
        helper.assertTrue(controller.getRenderBoundingBox().equals(volume), "Unformed model render volume differs");
        helper.getLevel().setBlock(part, state, Block.UPDATE_ALL);
        manager.changed(part); tick.accept(2);
        assertProjection(helper, controller, true);
    }

    private static AABB footprint(C16cMachineFixture f) {
        var box = new AABB(f.positions.get(0));
        for (BlockPos position : f.positions) { box = box.minmax(new AABB(position)); }
        return box;
    }

    private static void assertProjection(GameTestHelper helper, ClassicControllerBlockEntity controller, boolean formed) {
        CompoundTag tag = controller.getUpdateTag();
        helper.assertTrue(controller.formed() == formed && controller.modelFormed() == formed
                && ClassicModelProjection.decode(tag) == formed, "Controller and model projection differ: " + controller.reason());
        boolean sound = MachineSoundProfile.loop(controller.definition().kind().getPath(), true) != null;
        Set<String> expected = sound ? (formed ? Set.of("active", "formed") : Set.of("active"))
                : (formed ? Set.of("formed") : Set.of());
        helper.assertTrue(tag.getAllKeys().equals(expected),
                "Model projection exposed other fields: " + tag.getAllKeys());
        var packet = controller.getUpdatePacket();
        // Vanilla sends an empty update tag as a null payload; the client must read that as not formed.
        helper.assertTrue(packet.getPos().equals(controller.getBlockPos())
                && Objects.equals(packet.getTag(), tag.isEmpty() ? null : tag), "Vanilla block-entity packet did not carry the projection");
        var replica = new ClassicControllerBlockEntity(controller.getType(), controller.getBlockPos(),
                controller.getBlockState(), controller.definition());
        var saved = replica.saveWithFullMetadata();
        CompoundTag previous = new CompoundTag(); ClassicModelProjection.encode(previous, true);
        replica.handleUpdateTag(previous);
        replica.onDataPacket(null, packet);
        helper.assertTrue(replica.modelFormed() == formed && replica.saveWithFullMetadata().equals(saved),
                "Client packet changed persistent machine state");
        CompoundTag malformed = new CompoundTag(); malformed.putInt(ClassicModelProjection.FORMED, 1);
        replica.handleUpdateTag(malformed);
        helper.assertTrue(!replica.modelFormed(), "Malformed formed flag was accepted");
        replica.handleUpdateTag(tag); replica.handleUpdateTag(new CompoundTag());
        helper.assertTrue(!replica.modelFormed(), "Missing formed flag remained set");
    }
}
