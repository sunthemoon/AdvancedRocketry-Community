package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ProcessingFlagCodec;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native recipe progression and the exact nearby-client projection, with no custom channel. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessingSoundGameTests {
    private ProcessingSoundGameTests() { }

    @GameTest(template = "empty", batch = "w42_lathe", timeoutTicks = 30)
    public static void latheRecipeSetsAndClearsProcessingSound(GameTestHelper helper) {
        try (var f = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, false)) {
            assertProjection(helper, f.controller, false, ClassicSound.LATHE);
            f.seed(1, 40); f.tick(2);
            assertProjection(helper, f.controller, true, ClassicSound.LATHE);
            f.tick(3);
            helper.assertTrue(f.controller.reason() == ClassicWait.ENERGY, "Lathe did not exhaust test energy");
            assertProjection(helper, f.controller, false, ClassicSound.LATHE);
            f.power.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow().receiveEnergy(6_000, false);
            f.tick(1); assertProjection(helper, f.controller, true, ClassicSound.LATHE);
            f.fillOutput(); f.tick(2);
            helper.assertTrue(f.controller.reason() == ClassicWait.OUTPUT, "Lathe output did not block progression");
            assertProjection(helper, f.controller, false, ClassicSound.LATHE);
            for (int slot = 0; slot < 4; slot++) { f.output().extractItem(slot, 64, false); }
            f.tick(1); assertProjection(helper, f.controller, true, ClassicSound.LATHE);
            helper.getLevel().setBlock(f.motor, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            f.manager.changed(f.motor); f.tick(1);
            helper.assertTrue(!f.controller.formed(), "Invalid lathe remained formed");
            assertProjection(helper, f.controller, false, ClassicSound.LATHE);
        }
        try (var f = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, false)) {
            f.seed(1, 6_000); f.tick(2);
            assertProjection(helper, f.controller, true, ClassicSound.LATHE);
            f.tick(299);
            helper.assertTrue(f.controller.checkpoint().batchOrdinal() == 1 && f.output().getStackInSlot(0).getCount() == 2,
                    "Real lathe recipe did not complete exactly once");
            assertProjection(helper, f.controller, false, ClassicSound.LATHE);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w42_etcher", timeoutTicks = 30)
    public static void laserEtcherRecipeSetsAndClearsLatheSound(GameTestHelper helper) {
        exercise(helper, "precision_laser_etcher", "laser_etching_basic_circuit_plate", ClassicSound.LATHE);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w42_chemical", timeoutTicks = 30)
    public static void chemicalRecipeSetsAndClearsRollingSound(GameTestHelper helper) {
        exercise(helper, "chemical_reactor", "chemical_reacting_rocket_fuel", ClassicSound.ROLLING_MACHINE);
        helper.succeed();
    }

    private static void exercise(GameTestHelper helper, String kind, String recipeId, ClassicSound sound) {
        try (var f = new C16cMachineFixture(helper, kind, Direction.NORTH)) {
            assertProjection(helper, f.controller, false, sound);
            var recipe = f.recipe(recipeId); f.seed(recipe); f.tick(3);
            assertProjection(helper, f.controller, true, sound);
            var saved = f.controller.saveWithFullMetadata();
            helper.assertTrue(!saved.contains("active"), "Transient processing flag entered the save schema");
            f.controller.load(saved); f.manager.observe(f.controller);
            assertProjection(helper, f.controller, false, sound);
            f.tick(2); assertProjection(helper, f.controller, true, sound);
            int progress = f.controller.progressTicks();
            f.energyLeft = 0;
            for (var plug : f.hatches("energy_input")) { plug.debit(plug.energyStored()); }
            f.tick(1);
            helper.assertTrue(f.controller.reason() == ClassicWait.ENERGY && f.controller.progressTicks() == progress,
                    "Unpowered machine progressed");
            assertProjection(helper, f.controller, false, sound);
            f.energyLeft = (recipe.rows().processingTicks() - progress) * recipe.rows().energyPerTick();
            f.tick(1); assertProjection(helper, f.controller, true, sound);
            f.fillOutput(); f.tick(1);
            helper.assertTrue(f.controller.reason() == ClassicWait.OUTPUT, "Full output did not block machine");
            assertProjection(helper, f.controller, false, sound);
            f.clearOutput(); f.tick(1); assertProjection(helper, f.controller, true, sound);
            var part = f.world(f.pattern.cells().entrySet().stream()
                    .filter(cell -> cell.getValue() instanceof io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher.BlockTag tag
                            && tag.tagId().endsWith(":motors"))
                    .findFirst().orElseThrow().getKey());
            var state = helper.getLevel().getBlockState(part);
            helper.getLevel().setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            f.manager.changed(part); f.tick(1);
            helper.assertTrue(!f.controller.formed(), "Broken processing machine remained formed");
            assertProjection(helper, f.controller, false, sound);
            helper.getLevel().setBlock(part, state, Block.UPDATE_ALL);
            f.manager.changed(part); f.tick(2);
            assertProjection(helper, f.controller, true, sound);
            f.tick(recipe.rows().processingTicks() - f.controller.progressTicks());
            helper.assertTrue(f.controller.checkpoint().batchOrdinal() == 1 && f.controller.checkpoint().job().isEmpty(),
                    "Real machine recipe did not complete exactly once");
            assertProjection(helper, f.controller, false, sound);
        }
    }

    private static void assertProjection(GameTestHelper helper, ClassicControllerBlockEntity controller,
            boolean active, ClassicSound sound) {
        helper.assertTrue(controller.processingActive() == active && ProcessingFlagCodec.decode(controller.getUpdateTag()) == active,
                "Controller and update tag differ from expected processing state: " + controller.reason());
        helper.assertTrue(controller.activeProcessingSound() == (active ? sound : null), "Processing sound mapping differs");
        helper.assertTrue(controller.getUpdateTag().getAllKeys().equals(Set.of("active")), "Update tag exposed saved roots");
        var packet = controller.getUpdatePacket();
        helper.assertTrue(packet.getPos().equals(controller.getBlockPos()) && packet.getTag().equals(controller.getUpdateTag()),
                "Vanilla block-entity packet did not carry the projection");
        var replica = new ClassicControllerBlockEntity(controller.getType(), controller.getBlockPos(),
                controller.getBlockState(), controller.definition());
        var saved = replica.saveWithFullMetadata();
        replica.onDataPacket(null, packet);
        helper.assertTrue(replica.processingActive() == active && replica.saveWithFullMetadata().equals(saved),
                "Client packet changed persistent machine state");
        CompoundTag malformed = new CompoundTag(); malformed.putInt("active", 1);
        replica.handleUpdateTag(malformed);
        helper.assertTrue(!replica.processingActive(), "Malformed flag remained active");
        replica.handleUpdateTag(controller.getUpdateTag()); replica.handleUpdateTag(new CompoundTag());
        helper.assertTrue(!replica.processingActive(), "Missing flag remained active");
    }
}
