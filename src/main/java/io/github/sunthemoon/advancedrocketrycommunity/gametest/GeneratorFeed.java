package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionBurn;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraftforge.common.ForgeHooks;

/**
 * W68: the mod's own combustion generator as a power source, fuelled by a vanilla hopper above it (and optionally a
 * dropper). It pushes into its loaded neighbours as in play. {@link #generated()} is the FE its fuel has produced:
 * {@value CombustionBurn#RATE} FE per burn tick of every fuel item it has started, minus the ticks still due.
 */
final class GeneratorFeed {
    final CombustionGeneratorBlockEntity generator;
    private final ServerLevel level;
    private final Item fuel;
    private final int burnTicks;
    private final List<Container> stock = new ArrayList<>();
    private int supplied;

    GeneratorFeed(AutomationRig rig, BlockPos position, Item fuel, int count) {
        level = rig.level; this.fuel = fuel; burnTicks = ForgeHooks.getBurnTime(new ItemStack(fuel), RecipeType.SMELTING);
        rig.place(position, ModBlocks.COMBUSTION_GENERATOR.get().defaultBlockState());
        generator = (CombustionGeneratorBlockEntity) level.getBlockEntity(position);
        stock.add(rig.hopper(position.above(), Direction.DOWN, List.of(new ItemStack(fuel, count)))); supplied = count;
    }
    /** A second fuel path: a dropper on {@code side} of the generator. */
    DispenserBlockEntity dropper(AutomationRig rig, Direction side, int count) {
        var dropper = rig.dropper(generator.getBlockPos().relative(side), side.getOpposite(), List.of(new ItemStack(fuel, count)));
        stock.add(dropper); supplied += count; return dropper;
    }
    /** One generator tick, the static ticker the server runs once per game tick. */
    void tick() { CombustionGeneratorBlockEntity.serverTick(level, generator.getBlockPos(), generator.getBlockState(), generator); }
    int burnTicks() { return burnTicks; }
    int supplied() { return supplied; }
    int unburnt() {
        int total = generator.fuelStack().is(fuel) ? generator.fuelStack().getCount() : 0;
        for (Container container : stock) { total += AutomationRig.count(container, fuel); }
        return total;
    }
    long generated() { return (long) CombustionBurn.RATE * ((long) (supplied - unburnt()) * burnTicks - generator.burnState().remaining()); }
    int buffered() { return generator.burnState().energy(); }
    boolean spent() { return unburnt() == 0 && generator.burnState().remaining() == 0; }
    /** The generator's own save and load round trip; the ledger must hold across it. */
    void reload() { generator.load(generator.saveWithFullMetadata()); }
}
