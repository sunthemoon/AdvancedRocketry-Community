package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorContent;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ArcCuttingData;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180C16cMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicHatchKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicChanceSelection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicHatchBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMachineManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternJsonCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * W68 (W62 S5): one registered classic multiblock runs {@value #BATCHES} batches of one production recipe through its
 * real hatches. A vanilla hopper and a dropper (or, where the layout leaves one free face, a capability call from a
 * second side) feed items; mod pumps push fluids; a counted test source charges the power plugs; a hopper under the
 * output hatch and capability calls from two sides take the products. The controller and every hatch are saved and
 * loaded once mid-run. Every Item, mB and FE is counted on both sides of the machine.
 */
final class ClassicAutomationRun {
    static final int BATCHES = 20;
    private static final int DROPPER_PERIOD = 16, PIPE_PERIOD = 48, SETTLE_SUB_TICKS = 40;
    private static final Direction[] SIDES = Direction.values();
    private final AutomationRig rig;
    private final ServerLevel level;
    private final ClassicMachineRecipe recipe;
    private final ClassicMachineManager manager;
    private final ClassicControllerBlockEntity controller;
    private final BlockPos origin;
    private final Set<BlockPos> cells = new HashSet<>();
    private final Map<ClassicHatchKind, List<ClassicHatchBlockEntity>> hatches = new EnumMap<>(ClassicHatchKind.class);
    private final int subTicksPerTick;
    private final Map<Item, Integer> supplied = new LinkedHashMap<>(), perBatchIn = new LinkedHashMap<>(), retained = new LinkedHashMap<>();
    private final Map<Item, Integer> expectedOut = new LinkedHashMap<>(), collected = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> reservoir = new LinkedHashMap<>(), fluidSupplied = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> fluidCollected = new LinkedHashMap<>();
    private final List<PumpBlockEntity> pumps = new ArrayList<>();
    private final List<ResourceLocation> pumpFluids = new ArrayList<>();
    private final List<ItemStack> pipeStock = new ArrayList<>();
    private HopperBlockEntity inputHopper, outputHopper;
    private DispenserBlockEntity inputDropper;
    private Direction feedSide, pipeSide;
    private final long energyBudget;
    private long injected;
    private int subTick, settle = -1;
    private boolean reloaded;

    ClassicAutomationRun(GameTestHelper helper, String kind, String recipeId, int subTicksPerTick) {
        rig = new AutomationRig(helper); level = helper.getLevel(); this.subTicksPerTick = subTicksPerTick;
        origin = helper.absolutePos(new BlockPos(7, 4, 7));
        try {
            var pattern = MultiblockPatternJsonCodec.decode(pattern(kind).toString().getBytes(StandardCharsets.UTF_8));
            var transform = new PatternTransform(PatternRotation.ZERO, false);
            var anchor = new PatternPosition(origin.getX(), origin.getY(), origin.getZ());
            var world = new LinkedHashMap<PatternPosition, BlockPos>();
            pattern.cells().keySet().forEach(local -> {
                var p = transform.localToWorld(local, pattern.controllerAnchor(), anchor); world.put(local, new BlockPos(p.x(), p.y(), p.z()));
            });
            cells.addAll(world.values());
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos cell : cells) {
                minX = Math.min(minX, cell.getX()); minY = Math.min(minY, cell.getY()); minZ = Math.min(minZ, cell.getZ());
                maxX = Math.max(maxX, cell.getX()); maxY = Math.max(maxY, cell.getY()); maxZ = Math.max(maxZ, cell.getZ());
            }
            rig.room(new BlockPos(minX - 2, minY - 2, minZ - 2), new BlockPos(maxX + 2, maxY + 2, maxZ + 2));
            for (var cell : pattern.cells().entrySet()) { rig.place(world.get(cell.getKey()), state(kind, cell.getValue())); }
            if (kind.equals("arc_furnace")) {
                // As ArcCuttingGameTests: the item hatches stand in two shell-part cells of the front wall.
                rig.place(world.get(new PatternPosition(1, 1, 0)), ModBlocks.ITEM_INPUT_HATCH.get().defaultBlockState());
                rig.place(world.get(new PatternPosition(3, 1, 0)), ModBlocks.ITEM_OUTPUT_HATCH.get().defaultBlockState());
            }
            controller = (ClassicControllerBlockEntity) level.getBlockEntity(origin);
            manager = ClassicRuntime.manager(level);
            rig.beforeClose(() -> { level.setBlock(origin, Blocks.AIR.defaultBlockState(), 3); manager.forget(controller); });
            for (var kindOf : ClassicHatchKind.values()) { hatches.put(kindOf, new ArrayList<>()); }
            world.values().stream().sorted().forEach(position -> {
                if (level.getBlockEntity(position) instanceof ClassicHatchBlockEntity hatch) { hatches.get(hatch.kind()).add(hatch); }
            });
            manager.observe(controller); manager.tick();
            helper.assertTrue(controller.formed(), "W68 " + kind + " did not form: " + controller.reason());
            recipe = (ClassicMachineRecipe) level.getRecipeManager().byKey(ModIdentity.id(recipeId)).orElseThrow();
            var rows = recipe.rows();
            energyBudget = (long) BATCHES * rows.processingTicks() * rows.energyPerTick();
            planItems(); planFluids(); planOutputs();
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    private static com.google.gson.JsonObject pattern(String kind) {
        return switch (kind) {
            case "arc_furnace" -> V180ArcCuttingData.arcPattern();
            case "cutting_machine" -> V180ArcCuttingData.cuttingPattern();
            case "lathe" -> V180ClassicMachineData.lathePattern();
            default -> V180C16cMachineData.pattern(kind);
        };
    }
    private BlockState state(String kind, PatternMatcher matcher) {
        if (matcher instanceof PatternMatcher.Controller) {
            return BuiltInRegistries.BLOCK.get(ModIdentity.id(kind)).defaultBlockState().setValue(ClassicControllerBlock.FACING, Direction.NORTH);
        }
        if (matcher instanceof PatternMatcher.ExactBlock exact) { return BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(exact.blockId())).defaultBlockState(); }
        if (matcher instanceof PatternMatcher.BlockTag tag) {
            if (tag.tagId().equals(V180ArcCuttingData.CLEARANCE_TAG)) { return Blocks.AIR.defaultBlockState(); }
            if (tag.tagId().endsWith(":coils")) { return MaterialContent.block("copper_coil").defaultBlockState(); }
            if (tag.tagId().endsWith(":motors")) { return MotorContent.block(MotorDefinition.MOTOR).get().defaultBlockState(); }
            if (tag.tagId().equals("minecraft:slabs")) { return Blocks.OAK_SLAB.defaultBlockState(); }
            return ModBlocks.BLAST_BRICK.get().defaultBlockState();
        }
        if (matcher instanceof PatternMatcher.Port port) {
            return switch (port.channel()) {
                case "item_input" -> ModBlocks.ITEM_INPUT_HATCH.get().defaultBlockState();
                case "item_output" -> ModBlocks.ITEM_OUTPUT_HATCH.get().defaultBlockState();
                case "fluid_input" -> ModBlocks.FLUID_INPUT_HATCH.get().defaultBlockState();
                case "fluid_output" -> ModBlocks.FLUID_OUTPUT_HATCH.get().defaultBlockState();
                case "energy_input" -> ModBlocks.POWER_INPUT_PLUG.get().defaultBlockState();
                default -> throw new IllegalArgumentException("Unknown port " + port.channel());
            };
        }
        return Blocks.AIR.defaultBlockState();
    }

    /** One hopper (and a dropper or a second capability side) per input hatch; the laser lens enters once. */
    private void planItems() {
        var rows = recipe.rows(); var alternatives = recipe.resolveInputs();
        boolean etching = rows.type().equals("advancedrocketrycommunity:laser_etching");
        var hopperStacks = new ArrayList<ItemStack>(); var dropperStacks = new ArrayList<ItemStack>();
        for (int row = 0; row < rows.itemInputs().size(); row++) {
            var choices = alternatives.get(row);
            String id = choices.stream().filter(choice -> choice.endsWith("raw_rutile")).findFirst().orElse(choices.get(0));
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id)); int count = rows.itemInputs().get(row).count();
            if (etching && id.equals("advancedrocketrycommunity:laser_lens")) {
                retained.merge(item, count, Integer::sum); supplied.merge(item, count, Integer::sum);
                hopperStacks.add(0, new ItemStack(item, count)); continue;
            }
            int total = BATCHES * count; perBatchIn.merge(item, count, Integer::sum); supplied.merge(item, total, Integer::sum);
            int second = Math.max(1, total / 4), first = Math.min(64, total - second); second = total - first;
            hopperStacks.add(new ItemStack(item, first));
            for (int left = second; left > 0; left -= 64) { dropperStacks.add(new ItemStack(item, Math.min(64, left))); }
        }
        if (hopperStacks.isEmpty()) { return; }
        var input = hatches.get(ClassicHatchKind.ITEM_INPUT).get(0).getBlockPos();
        var sides = rig.freeSides(input, cells);
        Direction hopperSide = sides.contains(Direction.UP) ? Direction.UP
                : sides.stream().filter(side -> side.getAxis().isHorizontal()).findFirst().orElseThrow(() -> new IllegalStateException("No hopper face"));
        inputHopper = rig.hopper(input.relative(hopperSide), hopperSide.getOpposite(), hopperStacks);
        feedSide = hopperSide; sides.remove(hopperSide);
        if (!sides.isEmpty()) {
            Direction dropperSide = sides.get(0);
            inputDropper = rig.dropper(input.relative(dropperSide), dropperSide.getOpposite(), dropperStacks);
        } else {
            // The layout leaves one free face: a second side is exercised by capability calls on another face.
            pipeSide = hopperSide.getOpposite(); dropperStacks.forEach(stack -> pipeStock.add(stack.copy()));
        }
    }
    /** One pump beside each used fluid input hatch; the test tops each pump up from a counted reservoir. */
    private void planFluids() {
        var rows = recipe.rows().fluidInputs(); var inputs = hatches.get(ClassicHatchKind.FLUID_INPUT);
        for (int row = 0; row < rows.size(); row++) {
            var hatch = inputs.get(row).getBlockPos(); var sides = rig.freeSides(hatch, cells);
            sides.removeIf(side -> !isolatedPump(hatch.relative(side), hatch));
            Direction side = sides.stream().findFirst().orElseThrow(() -> new IllegalStateException("No pump face"));
            rig.place(hatch.relative(side), PumpContent.BLOCK.get().defaultBlockState());
            pumps.add((PumpBlockEntity) level.getBlockEntity(hatch.relative(side)));
            ResourceLocation fluid = rows.get(row).fluidId(); pumpFluids.add(fluid);
            reservoir.merge(fluid, BATCHES * rows.get(row).amount(), Integer::sum);
        }
    }
    /** A pump pushes into all six neighbours, so it may touch only its own hatch among the machine's cells. */
    private boolean isolatedPump(BlockPos pump, BlockPos hatch) {
        for (Direction side : SIDES) {
            BlockPos next = pump.relative(side);
            if (!next.equals(hatch) && (cells.contains(next) || !level.getBlockState(next).isAir())) { return false; }
        }
        return true;
    }
    private void planOutputs() {
        var rows = recipe.rows();
        rows.itemOutputs().forEach(row -> expectedOut.merge(BuiltInRegistries.ITEM.get(row.itemId()), BATCHES * row.count(), Integer::sum));
        if (!rows.chanceOutputs().isEmpty()) {
            var machine = controller.controllerState().machineInstanceId();
            for (long ordinal = 1; ordinal <= BATCHES; ordinal++) {
                ClassicChanceSelection.choose(rows.chanceOutputs(), ClassicChanceSelection.seed(machine, rows.jsonSignature(), ordinal),
                        id -> new ItemStack(BuiltInRegistries.ITEM.get(id)).getMaxStackSize())
                        .forEach(row -> expectedOut.merge(BuiltInRegistries.ITEM.get(row.itemId()), row.count(), Integer::sum));
            }
        }
        var outputs = hatches.get(ClassicHatchKind.ITEM_OUTPUT);
        if (!outputs.isEmpty() && (!rows.itemOutputs().isEmpty() || !rows.chanceOutputs().isEmpty())) {
            BlockPos below = outputs.get(0).getBlockPos().below();
            if (!cells.contains(below) && rig.free(below)) { outputHopper = rig.hopper(below, Direction.DOWN, List.of()); }
        }
    }

    void start(int timeoutTicks) { rig.drive(timeoutTicks, this::gameTick, this::verify); }

    private boolean gameTick() {
        topUpPumps();
        for (int i = 0; i < subTicksPerTick; i++) {
            subTick++;
            feedEnergy(); rig.tickHoppers();
            if (subTick % DROPPER_PERIOD == 0 && completed() > 0 && controller.formed()) { secondSide(); }
            manager.tick();
            if (subTick % PIPE_PERIOD == 0) { pipeOutputs(SIDES[(subTick / PIPE_PERIOD) % SIDES.length]); }
            if (!reloaded && completed() >= BATCHES / 2 && controller.progressTicks() > 0) { refusalsThenReload(); }
            if (settle < 0 && completed() >= BATCHES && controller.durationTicks() == 0 && reloaded) { settle = 0; }
            if (settle >= 0 && ++settle > SETTLE_SUB_TICKS) { pipeOutputs(Direction.NORTH); pipeOutputs(Direction.EAST); return true; }
        }
        return false;
    }
    private void topUpPumps() {
        for (int index = 0; index < pumps.size(); index++) {
            ResourceLocation fluid = pumpFluids.get(index); int left = reservoir.get(fluid);
            if (left == 0) { continue; }
            var port = pumps.get(index).getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(() -> new IllegalStateException("Pump port"));
            int filled = port.fill(new FluidStack(BuiltInRegistries.FLUID.get(fluid), Math.min(left, 4_000)), IFluidHandler.FluidAction.EXECUTE);
            reservoir.put(fluid, left - filled); fluidSupplied.merge(fluid, filled, Integer::sum);
        }
    }
    /** Charges every plug from the counted budget, through a different face each sub-tick. */
    private void feedEnergy() {
        for (var plug : hatches.get(ClassicHatchKind.POWER_INPUT)) {
            long left = energyBudget - injected;
            if (left <= 0) { return; }
            IEnergyStorage port = plug.getCapability(ForgeCapabilities.ENERGY, SIDES[subTick % SIDES.length]).orElseThrow(() -> new IllegalStateException("Plug"));
            injected += port.receiveEnergy((int) Math.min(left, 10_000), false);
        }
    }
    private void secondSide() {
        if (inputDropper != null) { if (!AutomationRig.empty(inputDropper)) { rig.pulse(inputDropper); } return; }
        if (pipeStock.isEmpty()) { return; }
        var hatch = hatches.get(ClassicHatchKind.ITEM_INPUT).get(0);
        IItemHandler port = hatch.getCapability(ForgeCapabilities.ITEM_HANDLER, pipeSide).orElse(null);
        if (port == null) { return; }
        ItemStack unit = pipeStock.get(0).copyWithCount(1);
        if (ItemHandlerHelper.insertItem(port, unit, false).isEmpty()) {
            pipeStock.get(0).shrink(1); if (pipeStock.get(0).isEmpty()) { pipeStock.remove(0); }
        }
    }
    /** Capability extraction from {@code side}, the second output face besides the hopper below. */
    private void pipeOutputs(Direction side) {
        for (var hatch : hatches.get(ClassicHatchKind.ITEM_OUTPUT)) {
            IItemHandler port = hatch.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            if (port == null) { continue; }
            for (int slot = 0; slot < port.getSlots(); slot++) {
                ItemStack taken = port.extractItem(slot, 64, false);
                if (!taken.isEmpty()) { collected.merge(taken.getItem(), taken.getCount(), Integer::sum); }
            }
        }
        for (var hatch : hatches.get(ClassicHatchKind.FLUID_OUTPUT)) {
            IFluidHandler port = hatch.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
            if (port == null) { continue; }
            FluidStack taken = port.drain(16_000, IFluidHandler.FluidAction.EXECUTE);
            if (!taken.isEmpty()) { fluidCollected.merge(BuiltInRegistries.FLUID.getKey(taken.getFluid()), taken.getAmount(), Integer::sum); }
        }
    }
    private int produced(Item item) {
        int total = collected.getOrDefault(item, 0) + (outputHopper == null ? 0 : AutomationRig.count(outputHopper, item));
        for (var hatch : hatches.get(ClassicHatchKind.ITEM_OUTPUT)) {
            IItemHandler port = hatch.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (port != null) { for (int slot = 0; slot < port.getSlots(); slot++) { if (port.getStackInSlot(slot).is(item)) { total += port.getStackInSlot(slot).getCount(); } } }
        }
        return total;
    }
    private int producedFluid(ResourceLocation fluid) {
        int total = fluidCollected.getOrDefault(fluid, 0);
        for (var hatch : hatches.get(ClassicHatchKind.FLUID_OUTPUT)) {
            IFluidHandler port = hatch.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
            if (port != null && BuiltInRegistries.FLUID.getKey(port.getFluidInTank(0).getFluid()).equals(fluid)) { total += port.getFluidInTank(0).getAmount(); }
        }
        return total;
    }
    private int completed() {
        var rows = recipe.rows();
        if (!rows.itemOutputs().isEmpty()) {
            var row = rows.itemOutputs().get(0); return produced(BuiltInRegistries.ITEM.get(row.itemId())) / row.count();
        }
        var row = rows.fluidOutputs().get(0); return producedFluid(row.fluidId()) / row.amount();
    }

    /** Mid-run: every refused direction first, then the restart round trip of ClassicLatheGameTests. */
    private void refusalsThenReload() {
        reloaded = true;
        var helper = rig.helper; CompoundTag before = controller.saveWithFullMetadata();
        for (Direction side : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (var hatch : hatches.get(ClassicHatchKind.ITEM_INPUT)) {
                IItemHandler port = hatch.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(() -> new IllegalStateException("Input facade"));
                for (int slot = 0; slot < port.getSlots(); slot++) {
                    helper.assertTrue(port.extractItem(slot, 64, false).isEmpty() && port.extractItem(slot, 64, true).isEmpty(),
                            "An item input hatch released an input from side " + side);
                }
            }
            for (var hatch : hatches.get(ClassicHatchKind.FLUID_INPUT)) {
                IFluidHandler port = hatch.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(() -> new IllegalStateException("Fluid facade"));
                helper.assertTrue(port.drain(16_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "A fluid input hatch released fluid from side " + side);
            }
            for (var plug : hatches.get(ClassicHatchKind.POWER_INPUT)) {
                helper.assertTrue(plug.getCapability(ForgeCapabilities.ENERGY, side).orElseThrow(() -> new IllegalStateException("Plug"))
                        .extractEnergy(10_000, false) == 0, "A power plug released FE from side " + side);
            }
            for (var hatch : hatches.get(ClassicHatchKind.ITEM_OUTPUT)) {
                ItemStack offered = new ItemStack(Items.DIRT, 3);
                helper.assertTrue(hatch.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(() -> new IllegalStateException("Output facade"))
                        .insertItem(0, offered, false).getCount() == 3, "An item output hatch accepted an insertion from side " + side);
            }
            for (var hatch : hatches.get(ClassicHatchKind.FLUID_OUTPUT)) {
                helper.assertTrue(hatch.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(() -> new IllegalStateException("Fluid output"))
                        .fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE) == 0, "A fluid output hatch accepted a fill from side " + side);
            }
        }
        helper.assertTrue(before.equals(controller.saveWithFullMetadata()), "A refused automation call changed the controller");
        int progress = controller.progressTicks(), stored = controller.storedEnergy(); long consumed = controller.consumedEnergy();
        var all = new ArrayList<ClassicHatchBlockEntity>(); hatches.values().forEach(all::addAll);
        var saved = new ArrayList<CompoundTag>(); all.forEach(hatch -> saved.add(hatch.saveWithFullMetadata()));
        controller.load(before.copy());
        for (int index = 0; index < all.size(); index++) { all.get(index).load(saved.get(index).copy()); }
        manager.observe(controller);
        helper.assertTrue(!controller.repairRequired() && controller.progressTicks() == progress && controller.consumedEnergy() == consumed
                && controller.storedEnergy() == stored, "Mid-run save and load changed the job, its FE or the plugs");
    }

    private void verify() {
        var helper = rig.helper; String kind = BuiltInRegistries.BLOCK.getKey(controller.getBlockState().getBlock()).getPath();
        helper.assertTrue(reloaded && controller.formed() && !controller.repairRequired() && controller.durationTicks() == 0
                && controller.consumedEnergy() == 0, kind + " did not finish idle after its reload: " + controller.reason());
        IItemHandler input = hatches.get(ClassicHatchKind.ITEM_INPUT).isEmpty() ? null
                : hatches.get(ClassicHatchKind.ITEM_INPUT).get(0).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("Input"));
        for (var entry : supplied.entrySet()) {
            Item item = entry.getKey(); int left = 0;
            if (inputHopper != null) { left += AutomationRig.count(inputHopper, item); }
            if (inputDropper != null) { left += AutomationRig.count(inputDropper, item); }
            left += pipeStock.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
            for (int slot = 0; input != null && slot < input.getSlots(); slot++) { if (input.getStackInSlot(slot).is(item)) { left += input.getStackInSlot(slot).getCount(); } }
            int consumed = BATCHES * perBatchIn.getOrDefault(item, 0), kept = retained.getOrDefault(item, 0);
            helper.assertTrue(entry.getValue() == consumed + kept && left == kept,
                    kind + " " + item + ": supplied " + entry.getValue() + ", left " + left + ", stated use " + consumed + ", retained " + kept);
        }
        for (var entry : expectedOut.entrySet()) {
            int produced = produced(entry.getKey());
            helper.assertTrue(produced == entry.getValue(), kind + " made " + produced + " " + entry.getKey() + ", stated " + entry.getValue());
        }
        for (var row : recipe.rows().fluidInputs()) {
            int left = reservoir.get(row.fluidId()); int inside = 0;
            for (int index = 0; index < pumps.size(); index++) { if (pumpFluids.get(index).equals(row.fluidId())) { inside += pumps.get(index).fluid().getAmount(); } }
            for (var hatch : hatches.get(ClassicHatchKind.FLUID_INPUT)) {
                var stack = hatch.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(() -> new IllegalStateException("Fluid")).getFluidInTank(0);
                if (BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(row.fluidId())) { inside += stack.getAmount(); }
            }
            helper.assertTrue(left == 0 && inside == 0 && fluidSupplied.getOrDefault(row.fluidId(), 0) == BATCHES * row.amount(),
                    kind + " fluid " + row.fluidId() + ": supplied " + fluidSupplied.get(row.fluidId()) + ", unused " + (left + inside));
        }
        for (var row : recipe.rows().fluidOutputs()) {
            int produced = producedFluid(row.fluidId());
            helper.assertTrue(produced == BATCHES * row.amount(), kind + " made " + produced + " mB " + row.fluidId() + ", stated " + BATCHES * row.amount());
        }
        helper.assertTrue(injected == energyBudget && controller.storedEnergy() == 0,
                kind + " FE: injected " + injected + ", stated " + energyBudget + ", left in plugs " + controller.storedEnergy());
        String spilled = rig.spilled(origin, 8);
        helper.assertTrue(spilled.isEmpty(), kind + " spilled items into the world: " + spilled);
    }
}
