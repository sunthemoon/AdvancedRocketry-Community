package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorContent;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** Registered server adapters and actual banks; fixture managers use the production lifecycle notifications. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicLatheGameTests {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("w01_classic_fixture", Comparator.comparingLong(ChunkPos::toLong), 60);
    @GameTest(template = "empty", batch = "w01_loaded_only", timeoutTicks = 30)
    public static void removalPolicyDoesNotLoadAnUnrelatedHatchChunk(GameTestHelper helper) {
        var position = new BlockPos(24_000_000, 80, -24_000_000); var source = helper.getLevel().getChunkSource();
        helper.assertTrue(source.getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null, "Remote fixture chunk was already loaded");
        helper.assertTrue(ClassicRemovalPolicy.blocks(helper.getLevel(), position), "Unloaded removal was not refused");
        helper.assertTrue(source.getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null, "Removal policy loaded an unrelated chunk");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_rotations", timeoutTicks = 30)
    public static void allFourRotationsFormAndWrongMotorRefuses(GameTestHelper helper) {
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            run(helper, facing, false, fixture -> {
                helper.assertTrue(fixture.controller.formed(), "Lathe did not form facing " + facing);
                helper.getLevel().setBlock(fixture.motor, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                fixture.manager.changed(fixture.motor); fixture.manager.tick();
                helper.assertTrue(!fixture.controller.formed() && fixture.controller.reason() == ClassicWait.UNFORMED, "Wrong motor formed a lathe");
            });
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_recipe", timeoutTicks = 30)
    public static void registeredLatheRecipeMovesExactlyOneBatchAnd6000Fe(GameTestHelper helper) {
        run(helper, Direction.NORTH, false, fixture -> {
            fixture.seed(1, 6_000); fixture.tick(301);
            helper.assertTrue(fixture.input().getStackInSlot(0).isEmpty(), "Lathe input was not consumed");
            helper.assertTrue(ItemStack.matches(fixture.output().getStackInSlot(0), new ItemStack(MaterialContent.item("iron_rod"), 2)), "Lathe output accounting");
            helper.assertTrue(fixture.power.energyStored() == 0 && fixture.controller.checkpoint().batchOrdinal() == 1
                    && fixture.controller.checkpoint().job().isEmpty(), "Lathe energy/batch accounting");
            helper.assertTrue(fixture.output().extractItem(0, 64, false).getCount() == 2, "Output facade did not reach controller bank");
            fixture.tick(10); helper.assertTrue(fixture.output().getStackInSlot(0).isEmpty(), "Batch duplicated");
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_redstone", timeoutTicks = 30)
    public static void redstonePausesWithoutConsumingResourcesOrEnergy(GameTestHelper helper) {
        run(helper, Direction.NORTH, false, fixture -> {
            fixture.seed(1, 6_000); fixture.tick(11); int progress = fixture.controller.progressTicks();
            var resources = ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources()); int energy = fixture.power.energyStored();
            helper.getLevel().setBlock(fixture.signal, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            fixture.tick(12);
            helper.assertTrue(fixture.controller.progressTicks() == progress && fixture.power.energyStored() == energy
                    && resources.equals(ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources()))
                    && fixture.controller.reason() == ClassicWait.REDSTONE, "Redstone consumed resources or progress");
            helper.getLevel().setBlock(fixture.signal, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); fixture.tick(301);
            helper.assertTrue(fixture.output().getStackInSlot(0).getCount() == 2 && fixture.power.energyStored() == 0, "Redstone resume accounting");
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_full_output", timeoutTicks = 30)
    public static void fullOutputPausesAndThenResumesWithoutLoss(GameTestHelper helper) {
        run(helper, Direction.NORTH, false, fixture -> {
            fixture.fillOutput(); fixture.seed(1, 6_000); fixture.tick(12);
            helper.assertTrue(fixture.controller.reason() == ClassicWait.OUTPUT && fixture.controller.progressTicks() == 0
                    && fixture.power.energyStored() == 6_000 && fixture.input().getStackInSlot(0).getCount() == 1, "Full output consumed resources");
            helper.assertTrue(fixture.output().extractItem(0, 64, false).getCount() == 64, "Full output bank inaccessible");
            fixture.tick(300);
            helper.assertTrue(fixture.power.energyStored() == 0 && fixture.input().getStackInSlot(0).isEmpty()
                    && fixture.output().getStackInSlot(0).getCount() == 2, "Unblocked output did not finish exactly once");
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_item_break", timeoutTicks = 30)
    public static void itemHatchBreakDropsTheAssignedBankOnce(GameTestHelper helper) {
        run(helper, Direction.NORTH, false, fixture -> {
            fixture.seed(7, 0); var oldInput = fixture.input();
            helper.getLevel().destroyBlock(fixture.inputPosition, false); fixture.inputHatch.removedByBlock();
            helper.assertTrue(fixture.dropped(Items.IRON_INGOT) == 7, "Item hatch did not drop its bank once");
            helper.assertTrue(oldInput.getStackInSlot(0).isEmpty() && oldInput.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                    "Old hatch facade revived after removal");
            helper.getLevel().setBlock(fixture.inputPosition, ModBlocks.ITEM_INPUT_HATCH.get().defaultBlockState(), Block.UPDATE_ALL);
            fixture.manager.changed(fixture.inputPosition); fixture.manager.tick();
            helper.assertTrue(fixture.input().getStackInSlot(0).isEmpty() && fixture.dropped(Items.IRON_INGOT) == 7, "Rebuilt Item hatch duplicated its bank");
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_fluid_break", timeoutTicks = 30)
    public static void fluidHatchBreakRetainsItsControllerBankAndRebuildReachesIt(GameTestHelper helper) {
        run(helper, Direction.NORTH, true, fixture -> {
            var hatch = (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(fixture.fluidInputPosition);
            var oldCap = hatch.getCapability(ForgeCapabilities.FLUID_HANDLER); var old = oldCap.orElseThrow(() -> new IllegalStateException("Fluid facade"));
            helper.assertTrue(old.fill(new FluidStack(Fluids.WATER, 9_000), IFluidHandler.FluidAction.EXECUTE) == 9_000, "Fluid bank fill");
            long generation = fixture.controller.controllerState().generation();
            helper.getLevel().destroyBlock(fixture.fluidInputPosition, false); hatch.removedByBlock();
            fixture.manager.changed(fixture.fluidInputPosition); fixture.manager.tick();
            helper.assertTrue(!oldCap.isPresent() && old.getFluidInTank(0).isEmpty(), "Removed Fluid facade survived");
            helper.assertTrue(fixture.controller.checkpoint().resources().bank(hatch.bankKey()).orElseThrow().fluid().getAmount() == 9_000, "Fluid bank lost on hatch break");
            helper.getLevel().setBlock(fixture.fluidInputPosition, ModBlocks.FLUID_INPUT_HATCH.get().defaultBlockState(), Block.UPDATE_ALL);
            fixture.manager.changed(fixture.fluidInputPosition); fixture.manager.tick();
            var rebuilt = (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(fixture.fluidInputPosition);
            var fresh = rebuilt.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(() -> new IllegalStateException("Rebuilt Fluid facade"));
            helper.assertTrue(fresh.getFluidInTank(0).getAmount() == 9_000 && fresh != old
                    && fixture.controller.controllerState().generation() > generation, "Rebuild did not create an exact fresh binding to retained Fluid");
            helper.assertTrue(!rebuilt.saveWithFullMetadata().getCompound(ClassicHatchBlockEntity.ROOT).contains("fluid"), "Hatch persisted a second Fluid copy");
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_controller_break", timeoutTicks = 30)
    public static void controllerBreakDropsAllItemsOnceAndDrainsFluids(GameTestHelper helper) {
        run(helper, Direction.NORTH, true, fixture -> {
            fixture.seed(5, 0); fixture.fillOutput();
            var fluid = (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(fixture.fluidInputPosition);
            fluid.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(() -> new IllegalStateException("Fluid input"))
                    .fill(new FluidStack(Fluids.WATER, 8_000), IFluidHandler.FluidAction.EXECUTE);
            helper.getLevel().destroyBlock(fixture.origin, false); fixture.controller.removeController();
            helper.assertTrue(fixture.dropped(Items.IRON_INGOT) == 5 && fixture.dropped(Items.DIRT) == 256, "Controller did not drop all Items once");
            helper.assertTrue(fixture.controller.checkpoint().resources().banks().stream().allMatch(bank -> bank.key().kind().isItem()
                    ? bank.items().stream().allMatch(ItemStack::isEmpty) : bank.fluid().isEmpty()), "Controller resources retained a removed copy");
            helper.assertTrue(fluid.ownerClaim().isEmpty() && !fluid.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(), "Controller break left a live Fluid claim");
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_unavailable", timeoutTicks = 30)
    public static void unavailableControllerRefusesPlayerExplosionAndEntityRemoval(GameTestHelper helper) {
        run(helper, Direction.NORTH, false, fixture -> {
            fixture.seed(3, 1_000); var old = fixture.input(); var roots = ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources());
            ChunkPos chunk = new ChunkPos(fixture.origin); fixture.manager.onChunkUnloading(chunk.x, chunk.z);
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "W01_Removal"));
            var broken = new BlockEvent.BreakEvent(helper.getLevel(), fixture.inputPosition, helper.getLevel().getBlockState(fixture.inputPosition), player);
            MinecraftForge.EVENT_BUS.post(broken); helper.assertTrue(broken.isCanceled(), "Unavailable controller allowed player break");
            var explosion = new Explosion(helper.getLevel(), null, fixture.origin.getX(), fixture.origin.getY(), fixture.origin.getZ(),
                    4, false, Explosion.BlockInteraction.DESTROY);
            explosion.getToBlow().add(fixture.inputPosition); MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(helper.getLevel(), explosion, new ArrayList<>()));
            helper.assertTrue(!explosion.getToBlow().contains(fixture.inputPosition), "Unavailable controller allowed explosion break");
            var wither = new WitherBoss(EntityType.WITHER, helper.getLevel());
            helper.assertTrue(!helper.getLevel().getBlockState(fixture.inputPosition).canEntityDestroy(helper.getLevel(), fixture.inputPosition, wither), "Unavailable controller allowed entity destruction");
            helper.assertTrue(old.getStackInSlot(0).isEmpty() && roots.equals(ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources())), "Unavailable owner exposed or lost resources");
            fixture.manager.onChunkChanged(chunk.x, chunk.z); fixture.manager.tick();
            helper.assertTrue(fixture.controller.formed() && fixture.input().getStackInSlot(0).getCount() == 3, "Owner load notification did not restore resources");
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_unformed_claims", timeoutTicks = 30)
    public static void unformedSaveLoadRetainsPowerOwnershipUntilControllerRemoval(GameTestHelper helper) {
        run(helper, Direction.NORTH, false, fixture -> {
            fixture.seed(7, 1_000);
            helper.getLevel().setBlock(fixture.motor, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            fixture.manager.changed(fixture.motor); fixture.manager.tick();
            helper.assertTrue(!fixture.controller.formed() && fixture.controller.controllerState().partPositions().isEmpty()
                    && fixture.power.ownerClaim().isPresent(), "Unformed fixture did not retain its power claim");
            var saved = fixture.controller.saveWithFullMetadata(); fixture.controller.load(saved.copy());
            fixture.manager.observe(fixture.controller); fixture.manager.tick();
            helper.assertTrue(fixture.controller.checkpoint().claimedParts().contains(fixture.power.getBlockPos())
                    && !ClassicRemovalPolicy.blocks(helper.getLevel(), fixture.origin), "Unformed checkpoint lost removable power ownership");
            helper.getLevel().destroyBlock(fixture.origin, false); fixture.controller.removeController();
            helper.assertTrue(fixture.dropped(Items.IRON_INGOT) == 7 && fixture.power.ownerClaim().isEmpty()
                    && fixture.inputHatch.ownerClaim().isEmpty() && fixture.power.energyStored() == 1_000
                    && !ClassicRemovalPolicy.blocks(helper.getLevel(), fixture.power.getBlockPos()), "Unformed controller removal left an orphan or duplicate");
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_progress", timeoutTicks = 30)
    public static void saveLoadKeepsProgressAndResumesWithRemainingEnergy(GameTestHelper helper) {
        run(helper, Direction.NORTH, false, fixture -> {
            fixture.seed(1, 6_000); fixture.tick(48); int progress = fixture.controller.progressTicks(); long consumed = fixture.controller.consumedEnergy();
            helper.assertTrue(progress > 0 && progress < 300 && consumed == progress * 20L, "Nonzero process checkpoint");
            var controller = fixture.controller.saveWithFullMetadata(); var hatches = new ArrayList<CompoundTag>();
            fixture.hatches().forEach(hatch -> hatches.add(hatch.saveWithFullMetadata()));
            fixture.controller.load(controller.copy());
            var physical = fixture.hatches(); for (int index = 0; index < physical.size(); index++) { physical.get(index).load(hatches.get(index).copy()); }
            fixture.manager.observe(fixture.controller); fixture.manager.tick();
            helper.assertTrue(fixture.controller.progressTicks() == progress + 1 && fixture.controller.consumedEnergy() == consumed + 20, "Save/load reset or changed progress");
            fixture.tick(300 - progress - 1);
            helper.assertTrue(fixture.controller.checkpoint().job().isEmpty() && fixture.power.energyStored() == 0
                    && fixture.output().getStackInSlot(0).getCount() == 2 && fixture.input().getStackInSlot(0).isEmpty(), "Restart continuation accounting");
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_missing_hatch", timeoutTicks = 30)
    public static void missingHatchPausesAJobAndKeepsTheCheckpoint(GameTestHelper helper) {
        run(helper, Direction.NORTH, false, fixture -> {
            fixture.seed(1, 6_000); fixture.tick(40); int progress = fixture.controller.progressTicks(), energy = fixture.power.energyStored();
            helper.getLevel().destroyBlock(fixture.outputPosition, false); fixture.manager.changed(fixture.outputPosition); fixture.tick(12);
            helper.assertTrue(fixture.controller.progressTicks() == progress && fixture.power.energyStored() == energy
                    && fixture.controller.checkpoint().resources().bank(fixture.inputHatch.bankKey()).orElseThrow().item(0).getCount() == 1, "Missing hatch consumed input/progress");
            helper.getLevel().setBlock(fixture.outputPosition, ModBlocks.ITEM_OUTPUT_HATCH.get().defaultBlockState(), Block.UPDATE_ALL);
            fixture.manager.changed(fixture.outputPosition); fixture.tick(301);
            helper.assertTrue(fixture.output().getStackInSlot(0).getCount() == 2 && fixture.power.energyStored() == 0, "Rebuilt output did not resume the old job");
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_conflict", timeoutTicks = 30)
    public static void hatchClaimedByAnotherControllerRefusesFormation(GameTestHelper helper) {
        run(helper, Direction.NORTH, false, fixture -> {
            fixture.inputHatch.clearClaim();
            var foreign = new MultiblockPartBinding(1, helper.getLevel().dimension(), fixture.origin.offset(10, 0, 0), UUID.randomUUID(), 1);
            fixture.inputHatch.setMultiblockBinding(java.util.Optional.of(foreign));
            fixture.manager.changed(fixture.inputPosition); fixture.manager.tick();
            helper.assertTrue(!fixture.controller.formed() && fixture.controller.reason() == ClassicWait.CONFLICT
                    && fixture.inputHatch.multiblockBinding().orElseThrow().equals(foreign), "Conflicting hatch was rebound");
            fixture.inputHatch.clearClaim();
        }); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01_unsupported", timeoutTicks = 30)
    public static void unsupportedOwnerRootsStayVerbatimAndBlockHatchRemoval(GameTestHelper helper) {
        run(helper, Direction.NORTH, false, fixture -> {
            fixture.seed(4, 0); CompoundTag saved = fixture.controller.saveWithFullMetadata();
            saved.getCompound(ClassicCheckpointCodec.ROOT).putInt("schema_version", 99); fixture.controller.load(saved.copy());
            helper.assertTrue(fixture.controller.repairRequired() && ClassicRemovalPolicy.blocks(helper.getLevel(), fixture.inputPosition)
                    && !fixture.inputHatch.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Unsupported owner admitted resources/removal");
            var after = fixture.controller.saveWithFullMetadata();
            helper.assertTrue(saved.get(ClassicCheckpointCodec.ROOT).equals(after.get(ClassicCheckpointCodec.ROOT))
                    && saved.get(ClassicResourcesCodec.ROOT).equals(after.get(ClassicResourcesCodec.ROOT)), "Unsupported roots were overwritten");
            saved.getCompound(ClassicCheckpointCodec.ROOT).putInt("schema_version", 1); fixture.controller.load(saved); fixture.manager.observe(fixture.controller); fixture.manager.tick();
        }); helper.succeed();
    }

    @GameTest(template = "empty", batch = "w01_part_unload", timeoutTicks = 30)
    public static void aSeparateHatchChunkUnloadPausesAndRetiresCapabilities(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, Direction.NORTH, false, true)) {
            fixture.seed(1, 6_000); fixture.tick(24);
            int progress = fixture.controller.progressTicks(), energy = fixture.power.energyStored();
            var old = fixture.input(); var remote = new ChunkPos(fixture.inputPosition);
            helper.assertTrue(!remote.equals(new ChunkPos(fixture.origin)), "Part unload fixture did not cross a chunk boundary");
            fixture.manager.onChunkUnloading(remote.x, remote.z); fixture.tick(12);
            helper.assertTrue(fixture.controller.controllerState().formationState() == MultiblockFormationState.WAITING_UNLOADED
                    && fixture.controller.progressTicks() == progress && fixture.power.energyStored() == energy
                    && old.getStackInSlot(0).isEmpty(), "Hatch-chunk unload advanced or exposed the process");
            fixture.manager.onChunkChanged(remote.x, remote.z); fixture.tick(301);
            helper.assertTrue(fixture.output().getStackInSlot(0).getCount() == 2 && fixture.power.energyStored() == 0, "Hatch chunk reload did not resume exact accounting");
        }
        helper.succeed();
    }
    private static void run(GameTestHelper helper, Direction facing, boolean fluids, Consumer<Fixture> test) {
        try (var fixture = new Fixture(helper, facing, fluids)) { test.accept(fixture); }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper; final ClassicMachineManager manager; final ClassicControllerBlockEntity controller;
        final BlockPos origin, inputPosition, outputPosition, motor, signal, fluidInputPosition, fluidOutputPosition;
        final ClassicHatchBlockEntity inputHatch, power;
        final List<BlockPos> positions = new ArrayList<>(); final List<ChunkPos> tickets = new ArrayList<>();
        Fixture(GameTestHelper helper, Direction facing, boolean fluids) {
            this(helper, facing, fluids, false);
        }
        Fixture(GameTestHelper helper, Direction facing, boolean fluids, boolean crossChunk) {
            this.helper = helper;
            BlockPos base = helper.absolutePos(new BlockPos(4, 4, 4));
            origin = crossChunk ? new BlockPos((base.getX() >> 4) * 16 + 14, base.getY(), (base.getZ() >> 4) * 16 + 8) : base;
            signal = origin.above();
            var json = V180ClassicMachineData.lathePattern();
            if (fluids) {
                json.getAsJsonArray("cells").get(5).getAsJsonObject().add("matcher", com.google.gson.JsonParser.parseString("{\"port\":\"fluid_input\"}"));
                json.getAsJsonArray("cells").get(6).getAsJsonObject().add("matcher", com.google.gson.JsonParser.parseString("{\"port\":\"fluid_output\"}"));
            }
            var pattern = MultiblockPatternJsonCodec.decode(json.toString().getBytes(StandardCharsets.UTF_8));
            var transform = new PatternTransform(switch (facing) {
                case NORTH -> PatternRotation.ZERO; case EAST -> PatternRotation.CLOCKWISE_90; case SOUTH -> PatternRotation.CLOCKWISE_180;
                case WEST -> PatternRotation.CLOCKWISE_270; default -> throw new IllegalArgumentException();
            }, false);
            var controllerPos = new PatternPosition(origin.getX(), origin.getY(), origin.getZ());
            java.util.function.Function<PatternPosition, BlockPos> world = local -> {
                var p = transform.localToWorld(local, pattern.controllerAnchor(), controllerPos); return new BlockPos(p.x(), p.y(), p.z());
            };
            inputPosition = world.apply(new PatternPosition(3, 1, 0)); outputPosition = world.apply(new PatternPosition(3, 0, 0));
            motor = world.apply(new PatternPosition(1, 1, 0)); fluidInputPosition = world.apply(new PatternPosition(1, 0, 0));
            fluidOutputPosition = world.apply(new PatternPosition(2, 0, 0));
            for (var local : pattern.cells().keySet()) { positions.add(world.apply(local)); } positions.add(signal);
            positions.stream().map(ChunkPos::new).distinct().forEach(chunk -> {
                tickets.add(chunk); helper.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk); helper.getLevel().getChunk(chunk.x, chunk.z);
            });
            for (var entry : pattern.cells().entrySet()) {
                BlockState state; var matcher = entry.getValue();
                if (matcher instanceof PatternMatcher.Controller) { state = ModBlocks.LATHE.get().defaultBlockState().setValue(ClassicControllerBlock.FACING, facing); }
                else if (matcher instanceof PatternMatcher.BlockTag) { state = MotorContent.block(MotorDefinition.MOTOR).get().defaultBlockState(); }
                else if (matcher instanceof PatternMatcher.ExactBlock) { state = ModBlocks.MACHINE_CASING.get().defaultBlockState(); }
                else if (matcher instanceof PatternMatcher.Port port) {
                    state = switch (port.channel()) {
                        case "item_input" -> ModBlocks.ITEM_INPUT_HATCH.get().defaultBlockState(); case "item_output" -> ModBlocks.ITEM_OUTPUT_HATCH.get().defaultBlockState();
                        case "energy_input" -> ModBlocks.POWER_INPUT_PLUG.get().defaultBlockState(); case "fluid_input" -> ModBlocks.FLUID_INPUT_HATCH.get().defaultBlockState();
                        case "fluid_output" -> ModBlocks.FLUID_OUTPUT_HATCH.get().defaultBlockState(); default -> throw new IllegalArgumentException();
                    };
                } else { state = Blocks.AIR.defaultBlockState(); }
                helper.getLevel().setBlock(world.apply(entry.getKey()), state, Block.UPDATE_ALL);
            }
            helper.getLevel().setBlock(signal, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            controller = (ClassicControllerBlockEntity) helper.getLevel().getBlockEntity(origin);
            inputHatch = (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(inputPosition);
            power = (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(world.apply(new PatternPosition(0, 0, 0)));
            if (fluids) {
                var patterns = new MultiblockPatternCatalogManager(); patterns.accept(MultiblockPatternCatalog.create(List.of(pattern)));
                manager = new ClassicMachineManager(helper.getLevel(), patterns);
            } else { manager = ClassicRuntime.manager(helper.getLevel()); }
            manager.observe(controller); manager.tick();
            helper.assertTrue(controller.formed(), "Fixture did not form: " + controller.reason());
        }
        IItemHandler input() { return ((ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(inputPosition)).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("Item input facade")); }
        IItemHandler output() { return ((ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(outputPosition)).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("Item output facade")); }
        void seed(int count, int energy) {
            helper.assertTrue(input().insertItem(0, new ItemStack(Items.IRON_INGOT, count), false).isEmpty(), "Fixture input insertion");
            if (energy > 0) { helper.assertTrue(power.getCapability(ForgeCapabilities.ENERGY).orElseThrow(() -> new IllegalStateException("Power plug")).receiveEnergy(energy, false) == energy, "Fixture power insertion"); }
            manager.wake(origin);
        }
        void tick(int count) { for (int i = 0; i < count; i++) { manager.tick(); } }
        void fillOutput() {
            var outputHatch = (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(outputPosition);
            var resources = controller.checkpoint().resources().replace(List.of(ClassicResourceBank.items(outputHatch.bankKey(), List.of(
                    new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64)))), false);
            CompoundTag saved = controller.saveWithFullMetadata(); saved.put(ClassicResourcesCodec.ROOT, ClassicResourcesCodec.encode(resources));
            controller.load(saved); manager.observe(controller); manager.tick();
        }
        List<ClassicHatchBlockEntity> hatches() {
            return positions.stream().map(position -> helper.getLevel().getBlockEntity(position)).filter(ClassicHatchBlockEntity.class::isInstance)
                    .map(ClassicHatchBlockEntity.class::cast).toList();
        }
        int dropped(net.minecraft.world.item.Item item) {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(6)).stream()
                    .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
        }
        @Override public void close() {
            // Remove the controller first so every still-loaded claim is released before fixture hatch cleanup.
            helper.getLevel().setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); manager.forget(controller);
            for (BlockPos position : positions) { helper.getLevel().setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(6)).forEach(ItemEntity::discard);
            tickets.forEach(chunk -> helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, chunk));
            if (manager != ClassicRuntime.manager(helper.getLevel())) { manager.close(); }
        }
    }
    private ClassicLatheGameTests() { }
}
