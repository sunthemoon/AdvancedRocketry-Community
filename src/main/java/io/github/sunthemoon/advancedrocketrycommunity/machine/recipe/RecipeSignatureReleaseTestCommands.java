package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import java.util.List;
import java.nio.charset.StandardCharsets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Fixed, opt-in console observations for a copied release fixture; never loads chunks. */
public final class RecipeSignatureReleaseTestCommands {
    static final List<String> CASES = List.of("legacy_partial", "legacy_pending", "current_partial",
            "current_pending", "future_process", "future_journal", "opaque_marker");
    static final List<String> MACHINES = List.of("rolling", "precision", "electrolyzer");
    private static final String MARKER = "arce_recipe_signature";

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) { return; }
        var fixture = Commands.literal("release-test").requires(source -> consolePermission(
                source.hasPermission(4), source.getEntity() == null, source.getTextName()));
        for (int machine = 0; machine < MACHINES.size(); machine++) {
            int selected = machine;
            fixture.then(Commands.literal(MACHINES.get(machine))
                    .then(Commands.literal("report").executes(context -> report(context.getSource(), selected)))
                    .then(Commands.literal("power-current").executes(context -> power(context.getSource(), selected))));
        }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("signature").then(fixture)));
    }

    static boolean consolePermission(boolean permission, boolean noEntity, String name) {
        return permission && noEntity && "Server".equals(name);
    }

    static BlockPos position(int row, int machine) {
        if (row < 0 || row >= CASES.size() || machine < 0 || machine >= MACHINES.size()) {
            throw new IllegalArgumentException("Unknown Signature fixture cell");
        }
        return new BlockPos(new int[] {243, 250, 254}[machine], 128 + 16 * row, 242);
    }

    private static ServerLevel level(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        if (level.getChunkSource().getChunkNow(15, 15) == null) {
            throw new IllegalStateException("Explicitly FULL-load fixed Signature chunk15,15 first");
        }
        return level;
    }

    private static BlockEntity at(ServerLevel level, BlockPos position) {
        if ((position.getX() >> 4) != 15 || (position.getZ() >> 4) != 15
                || !level.isInWorldBounds(position) || !level.getWorldBorder().isWithinBounds(position)) {
            throw new IllegalStateException("Signature fixture outside fixed loaded cells");
        }
        BlockEntity entity = level.getChunkSource().getChunkNow(15, 15).getBlockEntity(position);
        if (entity == null) { throw new IllegalStateException("Missing Signature fixture cell " + position); }
        return entity;
    }

    private static BlockEntity controller(ServerLevel level, int row, int machine) {
        BlockEntity entity = at(level, position(row, machine));
        if (!(machine == 0 && entity instanceof RollingMachineBlockEntity)
                && !(machine == 1 && entity instanceof PrecisionAssemblerBlockEntity)
                && !(machine == 2 && entity instanceof ElectrolyzerBlockEntity)) {
            throw new IllegalStateException("Wrong Signature fixture controller type");
        }
        return entity;
    }

    private static BlockPos energyPosition(int row, int machine) {
        BlockPos origin = position(row, machine);
        return machine == 0 ? origin.offset(1, 0, 0)
                : machine == 1 ? origin.offset(1, 0, 3) : origin;
    }

    private static BlockPos inputPosition(int row, int machine) {
        BlockPos origin = position(row, machine);
        return machine == 0 ? origin.offset(-2, 0, 0)
                : machine == 1 ? origin.offset(-1, 0, 0) : origin;
    }

    private static int report(CommandSourceStack source, int machine) {
        ServerLevel level = level(source);
        for (int row = 0; row < CASES.size(); row++) {
                BlockEntity entity = controller(level, row, machine);
                BlockEntity energy = at(level, energyPosition(row, machine));
                BlockEntity input = at(level, inputPosition(row, machine));
                BlockEntity fluid = machine == 0 ? at(level, position(row, machine).offset(-1, 0, 0)) : entity;
                CompoundTag before = entity.saveWithFullMetadata();
                CompoundTag energyBefore = energy.saveWithFullMetadata();
                CompoundTag inputBefore = input.saveWithFullMetadata();
                CompoundTag fluidBefore = fluid.saveWithFullMetadata();
                int acceptedEnergy = energy.getCapability(ForgeCapabilities.ENERGY).map(value -> value.receiveEnergy(1, true)).orElse(0);
                ItemStack probe = machine == 2 ? new ItemStack(io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.EMPTY_CANISTER.get())
                        : new ItemStack(Items.IRON_INGOT);
                int acceptedItem = input.getCapability(ForgeCapabilities.ITEM_HANDLER)
                        .map(value -> 1 - value.insertItem(0, probe, true).getCount()).orElse(0);
                int acceptedFluid = machine == 1 ? -1 : fluid.getCapability(ForgeCapabilities.FLUID_HANDLER)
                        .map(value -> value.fill(new FluidStack(Fluids.WATER, 1), IFluidHandler.FluidAction.SIMULATE)).orElse(0);
                if (!before.equals(entity.saveWithFullMetadata()) || !energyBefore.equals(energy.saveWithFullMetadata())
                        || !inputBefore.equals(input.saveWithFullMetadata()) || !fluidBefore.equals(fluid.saveWithFullMetadata())) {
                    throw new IllegalStateException("Signature SIMULATE observation changed saved resources/root");
                }
                JsonObject result = new JsonObject();
                result.addProperty("cell", CASES.get(row)); result.addProperty("machine", MACHINES.get(machine));
                result.addProperty("marker", before.contains(MARKER));
                result.addProperty("energy_simulated", acceptedEnergy); result.addProperty("item_simulated", acceptedItem);
                result.addProperty("fluid_simulated", acceptedFluid);
                result.addProperty("tag_generation", RecipeTagGeneration.current());
                JsonArray alternatives = new JsonArray();
                if (row == 2 || row == 3) {
                    for (List<String> group : facts(level, machine).alternatives()) {
                        if (group.isEmpty() || group.size() > 32) { throw new IllegalStateException("Unbounded fixture ingredients"); }
                        JsonArray entries = new JsonArray(); group.forEach(entries::add); alternatives.add(entries);
                    }
                }
                result.add("alternatives", alternatives);
                result.addProperty("signature", row == 2 || row == 3 ? facts(level, machine).signature() : "NOT_QUERIED");
                if (entity instanceof RollingMachineBlockEntity rolling) {
                    result.addProperty("status", rolling.processState().name());
                    result.addProperty("failure", rolling.processFailure().code().name());
                    result.addProperty("subject", rolling.processFailure().subject());
                    result.addProperty("progress", rolling.processProgress().map(value -> value.progressTicks()).orElse(0));
                    result.addProperty("protected", rolling.preservesRecipeInput());
                } else if (entity instanceof PrecisionAssemblerBlockEntity precision) {
                    result.addProperty("status", precision.processState().name());
                    result.addProperty("failure", precision.processFailure().code().name());
                    result.addProperty("subject", precision.processFailure().subject());
                    result.addProperty("progress", precision.processProgress().map(value -> value.progressTicks()).orElse(0));
                    result.addProperty("protected", precision.preservesRecipeInput());
                } else {
                    ElectrolyzerBlockEntity electro = (ElectrolyzerBlockEntity) entity;
                    result.addProperty("status", electro.status().name());
                    result.addProperty("failure", "NOT_EXPOSED"); result.addProperty("subject", "NOT_EXPOSED");
                    result.addProperty("progress", electro.progress()); result.addProperty("protected", electro.preservesRecipeInput());
                }
                if (result.toString().getBytes(StandardCharsets.UTF_8).length > 8_192) { throw new IllegalStateException("Signature fixture report exceeds byte budget"); }
                source.sendSuccess(() -> Component.literal("ARCE_SIGNATURE_REPORT " + result), false);
        }
        source.sendSuccess(() -> Component.literal("ARCE_SIGNATURE_REPORT_END machine=" + MACHINES.get(machine)), false);
        return CASES.size();
    }

    private static String recipeId(int machine) {
        return "advancedrocketrycommunity:" + new String[] {"rolling_iron_bars", "precision_control_circuit", "electrolyzer_water"}[machine];
    }

    private static Facts facts(ServerLevel level, int machine) {
        var recipe = level.getRecipeManager().byKey(new ResourceLocation(recipeId(machine)))
                .orElseThrow(() -> new IllegalStateException("Missing current fixture recipe"));
        if (machine == 0 && recipe instanceof RollingMachineRecipe rolling) {
            return new Facts(rolling.signature(), List.of(rolling.ingredientAlternatives()), rolling.processingTicks(), rolling.energyPerTick());
        }
        if (machine == 1 && recipe instanceof PrecisionAssemblerRecipe precision) {
            return new Facts(precision.signature(), precision.ingredientAlternatives(), precision.processingTicks(), precision.energyPerTick());
        }
        if (machine == 2 && recipe instanceof ElectrolyzerRecipe electro) {
            return new Facts(electro.signature(), List.of(electro.ingredientAlternatives()), electro.spec().processingTicks(), electro.spec().energyPerTick());
        }
        throw new IllegalStateException("Wrong current fixture recipe type");
    }

    private record Facts(String signature, List<List<String>> alternatives, int ticks, int energy) { }

    static int remainingEnergy(CompoundTag saved, String recipe, int duration, int perTick) {
        if (!saved.contains(MARKER, Tag.TAG_COMPOUND) || saved.contains(ProcessJournalPersistence.ROOT)
                || !saved.contains(ProcessStatePersistence.ROOT, Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Power action only accepts supported current partial work");
        }
        CompoundTag marker = saved.getCompound(MARKER);
        CompoundTag process = saved.getCompound(ProcessStatePersistence.ROOT);
        int ticks = process.getInt("progress_ticks");
        if (marker.size() != 4 || !marker.contains("schema_version", Tag.TAG_INT) || marker.getInt("schema_version") != 1
                || !marker.contains("format", Tag.TAG_STRING) || !marker.getString("format").equals("json_v1")
                || !marker.contains("converted", Tag.TAG_BYTE) || marker.getByte("converted") != 0
                || !marker.contains("recipe_id", Tag.TAG_STRING) || !marker.getString("recipe_id").equals(recipe)
                || !process.getString("definition_id").equals(recipe) || !process.contains("schema_version", Tag.TAG_INT)
                || process.getInt("schema_version") != 1 || !process.contains("progress_ticks", Tag.TAG_INT)
                || ticks != 5 || !process.contains("consumed_energy", Tag.TAG_LONG)
                || process.getLong("consumed_energy") != (long) ticks * perTick
                || !process.getString("recipe_signature").matches("[0-9a-f]{64}")
                || duration <= ticks || duration > 72_000 || perTick <= 0) {
            throw new IllegalStateException("Current fixture marker/clock/identity changed");
        }
        long remaining = (long) (duration - ticks) * perTick;
        if (remaining > 20_000) { throw new IllegalStateException("Current fixture exceeds fixed FE capacity"); }
        return (int) remaining;
    }

    static void receiveBounded(IEnergyStorage energy, int amount) {
        if (amount <= 0 || amount > 20_000 || energy.getEnergyStored() != 0 || !energy.canReceive()
                || energy.getMaxEnergyStored() < amount || energy.receiveEnergy(amount, true) <= 0) {
            throw new IllegalStateException("Current fixture cannot receive exact remaining FE");
        }
        int remaining = amount;
        for (int call = 0; call < 128 && remaining > 0; call++) {
            int accepted = energy.receiveEnergy(remaining, false);
            if (accepted <= 0 || accepted > remaining) { throw new IllegalStateException("Current fixture FE transfer refused"); }
            remaining -= accepted;
        }
        if (remaining != 0 || energy.getEnergyStored() != amount) {
            throw new IllegalStateException("Current fixture bounded FE transfer did not finish");
        }
    }

    private static int power(CommandSourceStack source, int machine) {
        ServerLevel level = level(source);
        BlockEntity entity = controller(level, 2, machine);
        if (!(entity instanceof RecipeSignatureProtected protection) || protection.preservesRecipeInput()) {
            throw new IllegalStateException("Current fixture is in repair/recovery mode");
        }
        Facts facts = facts(level, machine);
        CompoundTag saved = entity.saveWithFullMetadata();
        if (!saved.getCompound(ProcessStatePersistence.ROOT).getString("recipe_signature").equals(facts.signature())) {
            throw new IllegalStateException("Current fixture signature changed");
        }
        int amount = remainingEnergy(saved, recipeId(machine), facts.ticks(), facts.energy());
        IEnergyStorage store = at(level, energyPosition(2, machine)).getCapability(ForgeCapabilities.ENERGY).resolve()
                .orElseThrow(() -> new IllegalStateException("Current fixture FE interface unavailable"));
        if (store.getEnergyStored() != 0 || store.receiveEnergy(amount, true) <= 0) {
            throw new IllegalStateException("Current fixture FE preflight refused");
        }
        receiveBounded(store, amount);
        source.sendSuccess(() -> Component.literal("ARCE_SIGNATURE_POWER_CURRENT_END machine=" + MACHINES.get(machine)), false);
        return 1;
    }
}
