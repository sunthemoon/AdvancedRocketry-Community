package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorContent;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ArcCuttingData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** Real registered controllers, recipes, hatches and production Level manager; synchronous ticks isolate accounting. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArcCuttingGameTests {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("w05_classic_fixture", Comparator.comparingLong(ChunkPos::toLong), 60);
    @GameTest(template = "empty", batch = "w05_arc_rotations", timeoutTicks = 30)
    public static void arcFormsInAllRotationsAndRefusesWrongCoil(GameTestHelper helper) { rotations(helper, true); }
    @GameTest(template = "empty", batch = "w05_cutting_rotations", timeoutTicks = 30)
    public static void cuttingFormsInAllRotationsAndRefusesWrongMotor(GameTestHelper helper) { rotations(helper, false); }
    private static void rotations(GameTestHelper helper, boolean arc) {
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            try (var fixture = new Fixture(helper, arc, facing)) {
                helper.assertTrue(fixture.controller.formed(), "Formation facing " + facing);
                helper.getLevel().setBlock(fixture.wrongPart, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                fixture.manager.changed(fixture.wrongPart); fixture.manager.tick();
                helper.assertTrue(!fixture.controller.formed() && fixture.controller.reason() == ClassicWait.UNFORMED, "Wrong part formed machine");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w05_steel", timeoutTicks = 30)
    public static void steelUsesExactlyOneIronOneCharcoalAnd6000Fe(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, true, Direction.NORTH)) {
            fixture.seed(6_000, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.CHARCOAL)); fixture.tick(6_001);
            fixture.completed(MaterialContent.item("steel_ingot"), 1, 6_000);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w05_no_coal_steel", timeoutTicks = 30)
    public static void coalCannotReplaceCharcoalInSteel(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, true, Direction.NORTH)) {
            fixture.seed(6_000, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COAL)); fixture.tick(20);
            helper.assertTrue(fixture.controller.checkpoint().job().isEmpty() && fixture.controller.checkpoint().batchOrdinal() == 0
                    && fixture.power.energyStored() == 6_000 && fixture.input().getStackInSlot(0).getCount() == 1
                    && fixture.input().getStackInSlot(1).is(Items.COAL), "Coal ran a steel recipe or lost resources");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w05_titanium", timeoutTicks = 30)
    public static void minedRutileProducesTitaniumWith1000Fe(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, true, Direction.NORTH)) {
            fixture.seed(1_000, new ItemStack(MaterialContent.item("raw_rutile"))); fixture.tick(101);
            fixture.completed(MaterialContent.item("titanium_ingot"), 1, 1_000);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w05_wafer", timeoutTicks = 30)
    public static void siliconBouleCutsIntoFourWafersWith30000Fe(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, false, Direction.NORTH)) {
            fixture.seed(30_000, new ItemStack(MaterialContent.item("silicon_boule"))); fixture.tick(301);
            fixture.completed(ModItems.SILICON_WAFER.get(), 4, 30_000);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w05_circuits", timeoutTicks = 30)
    public static void bothCircuitPlatesCutIntoFourCircuitsWith30000Fe(GameTestHelper helper) {
        for (boolean advanced : List.of(false, true)) {
            try (var fixture = new Fixture(helper, false, Direction.NORTH)) {
                fixture.seed(30_000, new ItemStack(advanced ? ModItems.ADVANCED_CIRCUIT_PLATE.get() : ModItems.BASIC_CIRCUIT_PLATE.get()));
                fixture.tick(301); fixture.completed(advanced ? ModItems.ADVANCED_CIRCUIT.get() : ModItems.BASIC_CIRCUIT.get(), 4, 30_000);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w05_planks", timeoutTicks = 30)
    public static void everyLogAndStemCutsIntoSixCorrectPlanksWith800Fe(GameTestHelper helper) {
        for (var wood : V180ArcCuttingData.WOODS.entrySet()) {
            try (var fixture = new Fixture(helper, false, Direction.NORTH)) {
                var input = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(wood.getKey()));
                var output = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(wood.getValue()));
                fixture.seed(800, new ItemStack(input)); fixture.tick(81); fixture.completed(output, 6, 800);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w05_arc_output", timeoutTicks = 30)
    public static void fullArcOutputPausesAnActiveSteelJobWithoutLoss(GameTestHelper helper) { fullOutput(helper, true); }
    @GameTest(template = "empty", batch = "w05_cutting_output", timeoutTicks = 30)
    public static void fullCuttingOutputPausesAnActiveWaferJobWithoutLoss(GameTestHelper helper) { fullOutput(helper, false); }
    private static void fullOutput(GameTestHelper helper, boolean arc) {
        try (var fixture = new Fixture(helper, arc, Direction.NORTH)) {
            fixture.seed(arc ? 6_000 : 30_000, arc ? new ItemStack[]{new ItemStack(Items.IRON_INGOT), new ItemStack(Items.CHARCOAL)}
                    : new ItemStack[]{new ItemStack(MaterialContent.item("silicon_boule"))});
            fixture.tick(11); fixture.fillOutput();
            int progress = fixture.controller.progressTicks(), energy = fixture.power.energyStored();
            long consumed = fixture.controller.consumedEnergy(); var resources = ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources());
            fixture.tick(12);
            helper.assertTrue(fixture.controller.reason() == ClassicWait.OUTPUT && fixture.controller.progressTicks() == progress
                    && fixture.controller.consumedEnergy() == consumed && fixture.power.energyStored() == energy
                    && resources.equals(ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources())), "Full output lost resources, FE or progress");
            helper.assertTrue(fixture.output().extractItem(0, 64, false).getCount() == 64, "Full output facade inaccessible");
            fixture.tick((arc ? 6_000 : 300) - progress);
            fixture.completed(arc ? MaterialContent.item("steel_ingot") : ModItems.SILICON_WAFER.get(), arc ? 1 : 4, arc ? 6_000 : 30_000);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w05_sawmill_switch", timeoutTicks = 30)
    public static void sawmillOffRefusesNewWoodJobsAndPausesAndResumesAnActiveJob(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, false, Direction.NORTH)) {
            SwitchOverrides.set(CommonConfig.SAWMILL_VANILLA_WOOD, false);
            fixture.seed(800, new ItemStack(Items.OAK_LOG)); fixture.tick(20);
            helper.assertTrue(fixture.controller.checkpoint().job().isEmpty() && fixture.power.energyStored() == 800
                    && fixture.input().getStackInSlot(0).is(Items.OAK_LOG), "Disabled sawmill started or lost input");
            SwitchOverrides.set(CommonConfig.SAWMILL_VANILLA_WOOD, true); fixture.tick(11);
            int progress = fixture.controller.progressTicks(); long consumed = fixture.controller.consumedEnergy();
            var resources = ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources()); int energy = fixture.power.energyStored();
            SwitchOverrides.set(CommonConfig.SAWMILL_VANILLA_WOOD, false); fixture.tick(20);
            helper.assertTrue(progress > 0 && fixture.controller.reason() == ClassicWait.CONFIG_DISABLED && fixture.controller.progressTicks() == progress
                    && fixture.controller.consumedEnergy() == consumed && fixture.power.energyStored() == energy
                    && resources.equals(ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources())), "Sawmill disable lost active job/resources");
            SwitchOverrides.set(CommonConfig.SAWMILL_VANILLA_WOOD, true); fixture.tick(80 - progress);
            fixture.completed(Items.OAK_PLANKS, 6, 800);
        } finally { SwitchOverrides.clear(CommonConfig.SAWMILL_VANILLA_WOOD); }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w05_null_cells", timeoutTicks = 30)
    public static void conservativeClearanceRefusesAnUnrelatedHatchWithoutClaimingIt(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, true, Direction.NORTH)) {
            BlockPos unrelated = fixture.world(new PatternPosition(0, 4, 0));
            helper.getLevel().setBlock(unrelated, ModBlocks.FLUID_INPUT_HATCH.get().defaultBlockState(), Block.UPDATE_ALL);
            fixture.manager.changed(unrelated); fixture.manager.tick();
            var hatch = (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(unrelated);
            helper.assertTrue(!fixture.controller.formed() && hatch.ownerClaim().isEmpty() && hatch.multiblockBinding().isEmpty()
                    && fixture.controller.checkpoint().resources().bank(hatch.bankKey()).isEmpty(), "Occupied clearance formed or claimed an unrelated hatch");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w05_save_load", timeoutTicks = 30)
    public static void bothMachineKindsKeepResourcesAndProgressThroughCurrentFormatLoad(GameTestHelper helper) {
        for (boolean arc : List.of(true, false)) {
            try (var fixture = new Fixture(helper, arc, Direction.NORTH)) {
                fixture.seed(arc ? 6_000 : 30_000, arc ? new ItemStack[]{new ItemStack(Items.IRON_INGOT), new ItemStack(Items.CHARCOAL)}
                        : new ItemStack[]{new ItemStack(MaterialContent.item("silicon_boule"))});
                fixture.tick(11); int progress = fixture.controller.progressTicks(); long energy = fixture.controller.consumedEnergy();
                var saved = fixture.controller.saveWithFullMetadata(); var resources = ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources());
                fixture.controller.load(saved.copy());
                helper.assertTrue(!fixture.controller.repairRequired() && fixture.controller.progressTicks() == progress
                        && fixture.controller.consumedEnergy() == energy
                        && resources.equals(ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources())), "Machine kind refused its saved root or lost its job");
                fixture.manager.observe(fixture.controller); fixture.tick((arc ? 6_000 : 300) - progress);
                fixture.completed(arc ? MaterialContent.item("steel_ingot") : ModItems.SILICON_WAFER.get(), arc ? 1 : 4, arc ? 6_000 : 30_000);
            }
        }
        helper.succeed();
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ClassicMachineManager manager;
        final ClassicControllerBlockEntity controller;
        final ClassicHatchBlockEntity power;
        final MultiblockPatternDefinition pattern;
        final PatternTransform transform;
        final BlockPos origin, inputPos, outputPos, wrongPart;
        final List<BlockPos> positions = new ArrayList<>(); final List<ChunkPos> tickets = new ArrayList<>();
        int energyLeft, injected;
        Fixture(GameTestHelper helper, boolean arc, Direction facing) {
            this.helper = helper; origin = helper.absolutePos(new BlockPos(4, 4, 4));
            pattern = MultiblockPatternJsonCodec.decode((arc ? V180ArcCuttingData.arcPattern() : V180ArcCuttingData.cuttingPattern()).toString().getBytes(StandardCharsets.UTF_8));
            transform = new PatternTransform(switch (facing) {
                case NORTH -> PatternRotation.ZERO; case EAST -> PatternRotation.CLOCKWISE_90; case SOUTH -> PatternRotation.CLOCKWISE_180;
                case WEST -> PatternRotation.CLOCKWISE_270; default -> throw new IllegalArgumentException();
            }, false);
            inputPos = world(arc ? new PatternPosition(1, 1, 0) : new PatternPosition(0, 0, 0));
            outputPos = world(arc ? new PatternPosition(3, 1, 0) : new PatternPosition(2, 0, 0));
            wrongPart = world(arc ? new PatternPosition(1, 3, 1) : new PatternPosition(0, 0, 1));
            pattern.cells().keySet().forEach(local -> positions.add(world(local)));
            positions.stream().map(ChunkPos::new).distinct().forEach(chunk -> {
                tickets.add(chunk); helper.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk); helper.getLevel().getChunk(chunk.x, chunk.z);
            });
            for (var cell : pattern.cells().entrySet()) {
                var matcher = cell.getValue(); BlockState state;
                if (matcher instanceof PatternMatcher.Controller) { state = (arc ? ModBlocks.ARC_FURNACE : ModBlocks.CUTTING_MACHINE).get().defaultBlockState().setValue(ClassicControllerBlock.FACING, facing); }
                else if (matcher instanceof PatternMatcher.ExactBlock exact) { state = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.tryParse(exact.blockId())).defaultBlockState(); }
                else if (matcher instanceof PatternMatcher.BlockTag tag) {
                    state = tag.tagId().endsWith(":coils") ? MaterialContent.block("copper_coil").defaultBlockState()
                            : tag.tagId().endsWith(":motors") ? MotorContent.block(MotorDefinition.MOTOR).get().defaultBlockState() : ModBlocks.BLAST_BRICK.get().defaultBlockState();
                } else if (matcher instanceof PatternMatcher.Port port) {
                    state = switch (port.channel()) {
                        case "item_input" -> ModBlocks.ITEM_INPUT_HATCH.get().defaultBlockState(); case "item_output" -> ModBlocks.ITEM_OUTPUT_HATCH.get().defaultBlockState();
                        case "energy_input" -> ModBlocks.POWER_INPUT_PLUG.get().defaultBlockState(); default -> throw new IllegalArgumentException();
                    };
                } else { state = Blocks.AIR.defaultBlockState(); }
                helper.getLevel().setBlock(world(cell.getKey()), state, Block.UPDATE_ALL);
            }
            helper.getLevel().setBlock(inputPos, ModBlocks.ITEM_INPUT_HATCH.get().defaultBlockState(), Block.UPDATE_ALL);
            helper.getLevel().setBlock(outputPos, ModBlocks.ITEM_OUTPUT_HATCH.get().defaultBlockState(), Block.UPDATE_ALL);
            controller = (ClassicControllerBlockEntity) helper.getLevel().getBlockEntity(origin);
            power = (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(world(arc ? new PatternPosition(1, 4, 1) : new PatternPosition(2, 0, 1)));
            manager = ClassicRuntime.manager(helper.getLevel()); manager.observe(controller); manager.tick();
            helper.assertTrue(controller.formed(), "W05 fixture did not form: " + controller.reason());
        }
        BlockPos world(PatternPosition local) {
            var position = transform.localToWorld(local, pattern.controllerAnchor(), new PatternPosition(origin.getX(), origin.getY(), origin.getZ()));
            return new BlockPos(position.x(), position.y(), position.z());
        }
        IItemHandler input() { return items(inputPos); }
        IItemHandler output() { return items(outputPos); }
        private IItemHandler items(BlockPos position) { return helper.getLevel().getBlockEntity(position).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("Missing Item hatch facade")); }
        void seed(int energy, ItemStack... inputs) {
            for (int i = 0; i < inputs.length; i++) { helper.assertTrue(input().insertItem(i, inputs[i], false).isEmpty(), "Input insertion refused"); }
            energyLeft = energy; feed(); manager.wake(origin);
        }
        void feed() {
            if (energyLeft <= 0 || power.energyStored() >= 100) { return; }
            int accepted = power.getCapability(ForgeCapabilities.ENERGY).orElseThrow(() -> new IllegalStateException("Missing power plug"))
                    .receiveEnergy(energyLeft, false); energyLeft -= accepted; injected += accepted;
        }
        void tick(int ticks) { for (int i = 0; i < ticks; i++) { feed(); manager.tick(); } }
        void completed(Item item, int count, int energy) {
            helper.assertTrue(controller.checkpoint().job().isEmpty() && controller.checkpoint().batchOrdinal() == 1, "Batch did not complete exactly once");
            for (int slot = 0; slot < input().getSlots(); slot++) { helper.assertTrue(input().getStackInSlot(slot).isEmpty(), "Input count was not exact"); }
            helper.assertTrue(ItemStack.matches(output().getStackInSlot(0), new ItemStack(item, count)), "Output Item/count differed");
            helper.assertTrue(injected == energy && energyLeft == 0 && controller.storedEnergy() == 0, "FE accounting differed");
            helper.assertTrue(output().extractItem(0, 64, false).getCount() == count, "Output hatch could not extract result");
            tick(5); helper.assertTrue(output().getStackInSlot(0).isEmpty() && controller.checkpoint().batchOrdinal() == 1, "Output duplicated");
        }
        void fillOutput() {
            var hatch = (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(outputPos);
            var resources = controller.checkpoint().resources().replace(List.of(ClassicResourceBank.items(hatch.bankKey(), List.of(
                    new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64)))), false);
            CompoundTag saved = controller.saveWithFullMetadata(); saved.put(ClassicResourcesCodec.ROOT, ClassicResourcesCodec.encode(resources));
            controller.load(saved); manager.observe(controller); manager.tick();
        }
        @Override public void close() {
            helper.getLevel().setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); manager.forget(controller);
            for (BlockPos position : positions) { helper.getLevel().setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(7)).forEach(ItemEntity::discard);
            tickets.forEach(chunk -> helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, chunk));
        }
    }
    private ArcCuttingGameTests() { }
}
