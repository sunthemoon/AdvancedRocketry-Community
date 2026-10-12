package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorContent;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ArcCuttingData;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180C16cMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** Real registered controllers and hatches using the production Level manager and generated patterns. */
final class C16cMachineFixture implements AutoCloseable {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("arce_w10", Comparator.comparingLong(ChunkPos::toLong));
    final GameTestHelper helper;
    final String kind;
    final ClassicMachineManager manager;
    final ClassicControllerBlockEntity controller;
    final MultiblockPatternDefinition pattern;
    final PatternTransform transform;
    final BlockPos origin;
    final List<BlockPos> positions = new ArrayList<>();
    final List<ChunkPos> tickets = new ArrayList<>();
    int energyLeft, injected;

    C16cMachineFixture(GameTestHelper helper, String kind, Direction facing) {
        this.helper = helper; this.kind = kind; origin = helper.absolutePos(new BlockPos(4, 4, 4));
        pattern = MultiblockPatternJsonCodec.decode(V180C16cMachineData.pattern(kind).toString().getBytes(StandardCharsets.UTF_8));
        transform = new PatternTransform(switch (facing) {
            case NORTH -> PatternRotation.ZERO; case EAST -> PatternRotation.CLOCKWISE_90;
            case SOUTH -> PatternRotation.CLOCKWISE_180; case WEST -> PatternRotation.CLOCKWISE_270;
            default -> throw new IllegalArgumentException();
        }, false);
        pattern.cells().keySet().forEach(local -> positions.add(world(local)));
        positions.stream().map(ChunkPos::new).distinct().forEach(chunk -> {
            tickets.add(chunk); helper.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk); helper.getLevel().getChunk(chunk.x, chunk.z);
        });
        for (var cell : pattern.cells().entrySet()) {
            var matcher = cell.getValue(); BlockState state;
            if (matcher instanceof PatternMatcher.Controller) {
                state = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse("advancedrocketrycommunity:" + kind))
                        .defaultBlockState().setValue(ClassicControllerBlock.FACING, facing);
            } else if (matcher instanceof PatternMatcher.ExactBlock exact) {
                state = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(exact.blockId())).defaultBlockState();
            } else if (matcher instanceof PatternMatcher.BlockTag tag) {
                state = tag.tagId().equals(V180ArcCuttingData.CLEARANCE_TAG) ? Blocks.AIR.defaultBlockState()
                        : tag.tagId().equals("minecraft:slabs") ? Blocks.OAK_SLAB.defaultBlockState()
                        : MotorContent.block(MotorDefinition.MOTOR).get().defaultBlockState();
            } else if (matcher instanceof PatternMatcher.Port port) {
                state = switch (port.channel()) {
                    case "item_input" -> ModBlocks.ITEM_INPUT_HATCH.get().defaultBlockState(); case "item_output" -> ModBlocks.ITEM_OUTPUT_HATCH.get().defaultBlockState();
                    case "fluid_input" -> ModBlocks.FLUID_INPUT_HATCH.get().defaultBlockState(); case "fluid_output" -> ModBlocks.FLUID_OUTPUT_HATCH.get().defaultBlockState();
                    case "energy_input" -> ModBlocks.POWER_INPUT_PLUG.get().defaultBlockState(); default -> throw new IllegalArgumentException();
                };
            } else { state = Blocks.AIR.defaultBlockState(); }
            helper.getLevel().setBlock(world(cell.getKey()), state, Block.UPDATE_ALL);
        }
        controller = (ClassicControllerBlockEntity) helper.getLevel().getBlockEntity(origin);
        manager = ClassicRuntime.manager(helper.getLevel()); manager.observe(controller); manager.tick();
        helper.assertTrue(controller.formed(), "W10 " + kind + " did not form: " + controller.reason());
    }
    BlockPos world(PatternPosition local) {
        var position = transform.localToWorld(local, pattern.controllerAnchor(), new PatternPosition(origin.getX(), origin.getY(), origin.getZ()));
        return new BlockPos(position.x(), position.y(), position.z());
    }
    List<ClassicHatchBlockEntity> hatches(String channel) {
        return pattern.cells().entrySet().stream().filter(entry -> entry.getValue() instanceof PatternMatcher.Port port && port.channel().equals(channel))
                .map(entry -> (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(world(entry.getKey())))
                .sorted(Comparator.comparingLong(hatch -> hatch.getBlockPos().asLong())).toList();
    }
    IItemHandler input() { return items(hatches("item_input").get(0)); }
    IItemHandler output() { return items(hatches("item_output").get(0)); }
    private IItemHandler items(ClassicHatchBlockEntity hatch) {
        return hatch.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("Missing Item hatch facade"));
    }
    IFluidHandler fluids(ClassicHatchBlockEntity hatch) {
        return hatch.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(() -> new IllegalStateException("Missing Fluid hatch facade"));
    }
    ClassicMachineRecipe recipe(String id) {
        return (ClassicMachineRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.tryParse("advancedrocketrycommunity:" + id)).orElseThrow();
    }
    void seed(ClassicMachineRecipe recipe) {
        List<List<String>> alternatives = recipe.resolveInputs();
        for (int i = 0; i < alternatives.size(); i++) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(alternatives.get(i).get(0))), recipe.rows().itemInputs().get(i).count());
            helper.assertTrue(input().insertItem(i, stack, false).isEmpty(), "Item input refused");
        }
        for (int i = 0; i < recipe.rows().fluidInputs().size(); i++) {
            var row = recipe.rows().fluidInputs().get(i);
            helper.assertTrue(fluids(hatches("fluid_input").get(i)).fill(new FluidStack(BuiltInRegistries.FLUID.get(row.fluidId()), row.amount()),
                    IFluidHandler.FluidAction.EXECUTE) == row.amount(), "Fluid input refused");
        }
        energyLeft = recipe.rows().processingTicks() * recipe.rows().energyPerTick(); feed(); manager.wake(origin);
    }
    void feed() {
        for (var power : hatches("energy_input")) {
            if (energyLeft == 0) { break; }
            int accepted = power.getCapability(ForgeCapabilities.ENERGY).orElseThrow(() -> new IllegalStateException("Missing plug"))
                    .receiveEnergy(energyLeft, false); energyLeft -= accepted; injected += accepted;
        }
    }
    void tick(int ticks) { for (int i = 0; i < ticks; i++) { feed(); manager.tick(); } }
    CompoundTag resources() { return ClassicResourcesCodec.encode(controller.checkpoint().resources()); }
    void replaceBanks(List<ClassicResourceBank> banks) {
        var resources = controller.checkpoint().resources().replace(banks, false);
        CompoundTag saved = controller.saveWithFullMetadata(); saved.put(ClassicResourcesCodec.ROOT, ClassicResourcesCodec.encode(resources));
        controller.load(saved); manager.observe(controller); manager.tick();
    }
    void fillOutput() {
        var banks = new ArrayList<ClassicResourceBank>();
        if (kind.equals("chemical_reactor")) {
            for (var hatch : hatches("fluid_output")) {
                banks.add(ClassicResourceBank.fluid(hatch.bankKey(), new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 16_000)));
            }
        } else {
            for (var hatch : hatches("item_output")) {
                banks.add(ClassicResourceBank.items(hatch.bankKey(), List.of(new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64),
                        new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64))));
            }
        }
        replaceBanks(banks);
    }
    void clearOutput() {
        for (var hatch : hatches("item_output")) { for (int slot = 0; slot < 4; slot++) { items(hatch).extractItem(slot, 64, false); } }
        for (var hatch : hatches("fluid_output")) { fluids(hatch).drain(16_000, IFluidHandler.FluidAction.EXECUTE); }
    }
    void assertCompleted(ClassicMachineRecipe recipe) {
        helper.assertTrue(controller.checkpoint().job().isEmpty() && controller.checkpoint().batchOrdinal() == 1
                && controller.checkpoint().lastTransaction().isPresent(), "Batch did not complete exactly once: " + controller.reason());
        helper.assertTrue(injected == recipe.rows().processingTicks() * recipe.rows().energyPerTick()
                && energyLeft == 0 && controller.storedEnergy() == 0, "Exact FE accounting differs");
        if (!recipe.rows().itemInputs().isEmpty()) {
            for (int slot = 0; slot < 4; slot++) {
                ItemStack remaining = input().getStackInSlot(slot);
                boolean lens = recipe.retainsLens() && slot == 3;
                helper.assertTrue(lens ? remaining.getCount() == 1 && remaining.is(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("advancedrocketrycommunity:laser_lens")))
                        : remaining.isEmpty(), "Exact Item input accounting differs");
            }
        }
        for (var hatch : hatches("fluid_input")) { helper.assertTrue(fluids(hatch).getFluidInTank(0).isEmpty(), "Fluid input not consumed"); }
        for (int i = 0; i < recipe.rows().itemOutputs().size(); i++) {
            helper.assertTrue(ItemStack.matches(output().getStackInSlot(i), recipe.output(i)), "Item output/count differs");
        }
        for (var row : recipe.rows().fluidOutputs()) {
            int total = hatches("fluid_output").stream().map(this::fluids).map(handler -> handler.getFluidInTank(0))
                    .filter(stack -> BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(row.fluidId())).mapToInt(FluidStack::getAmount).sum();
            helper.assertTrue(total == row.amount(), "Fluid output/count differs");
        }
    }
    @Override public void close() {
        helper.getLevel().setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); manager.forget(controller);
        for (BlockPos position : positions) { helper.getLevel().setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(7)).forEach(ItemEntity::discard);
        tickets.forEach(chunk -> helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, chunk));
    }
}
