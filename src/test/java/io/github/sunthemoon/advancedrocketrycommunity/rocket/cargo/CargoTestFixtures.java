package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

public final class CargoTestFixtures {
    private CargoTestFixtures() { }
    public static RocketStructureSnapshot snapshot(FluidStack... tanks) {
        var blocks = new ArrayList<RocketBlock>();
        for (int i = 0; i < tanks.length; i++) {
            blocks.add(new RocketBlock(new RocketPosition(i, 0, 0),
                    new RocketBlockState(PressurizedTankRocketAdapter.TYPE, Map.of()), PressurizedTankRocketAdapter.payload(tanks[i])));
        }
        blocks.add(new RocketBlock(new RocketPosition(0, 1, 0),
                new RocketBlockState(ResourceLocation.tryParse("advancedrocketrycommunity:rocket_seat"), Map.of())));
        return snapshot(blocks);
    }
    public static RocketStructureSnapshot snapshot(List<RocketBlock> blocks) {
        var settings = RocketPropulsion.Settings.DEFAULTS;
        var allowed = new EnumMap<>(settings.allowedFluids()); allowed.put(RocketPropulsion.Role.MONO, List.of("minecraft:water"));
        var propulsion = new RocketPropulsion(RocketPropulsion.Mode.MONO, 20_000, 0, 1, 1, settings.withAllowedFluids(allowed));
        return RocketStructureSnapshot.create(new UUID(0, 106), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(0, 64, 0), blocks, List.of(),
                new RocketStats(blocks.size(), 100, 1000, 20_000, 1, 0, 1,
                        (int) blocks.stream().filter(b -> b.blockEntityPayload().isPresent()).count()), 25, propulsion);
    }
    public static EscrowCargoPayload delta(RocketStructureSnapshot before, int x, FluidStack moved) {
        return new EscrowCargoPayload(new EscrowCargoPayload.Cursor(before.snapshotId(), x, 0, 0,
                PressurizedTankRocketAdapter.ID.toString()), moved.writeToNBT(new CompoundTag()));
    }
    public static FluidStack tank(RocketStructureSnapshot value, int x) {
        return PressurizedTankRocketAdapter.fluid(value.blocks().stream().filter(b -> b.position().equals(new RocketPosition(x, 0, 0)))
                .findFirst().orElseThrow().blockEntityPayload().orElseThrow().data());
    }
}
