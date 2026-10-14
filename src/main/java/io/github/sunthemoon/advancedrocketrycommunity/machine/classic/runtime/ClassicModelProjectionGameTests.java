package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import java.util.Set;
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
        try (var f = new C16cMachineFixture(helper, "crystallizer", Direction.NORTH)) {
            helper.assertTrue(f.controller.getUpdateTag().isEmpty() && !f.controller.modelFormed(),
                    "An uncatalogued controller projected a model flag");
            BlockPos origin = f.controller.getBlockPos();
            helper.assertTrue(f.controller.getRenderBoundingBox().equals(new AABB(origin)),
                    "An uncatalogued controller changed its render volume");
        }
        helper.succeed();
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
        helper.assertTrue(tag.getAllKeys().equals(formed ? Set.of(ClassicModelProjection.FORMED) : Set.of()),
                "Model projection exposed other fields: " + tag.getAllKeys());
        var packet = controller.getUpdatePacket();
        helper.assertTrue(packet.getPos().equals(controller.getBlockPos()) && packet.getTag().equals(tag),
                "Vanilla block-entity packet did not carry the projection");
        var replica = new ClassicControllerBlockEntity(controller.getType(), controller.getBlockPos(),
                controller.getBlockState(), controller.definition());
        var saved = replica.saveWithFullMetadata();
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
