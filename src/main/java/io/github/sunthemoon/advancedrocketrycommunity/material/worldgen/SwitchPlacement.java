package io.github.sunthemoon.advancedrocketrycommunity.material.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.config.WorldgenSwitches;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * A placement modifier that passes positions only while a named server switch is on (ADR-061 section 3.5): turning
 * a switch off stops a feature in new chunks without a data pack. Unknown switch names fail to decode.
 */
public final class SwitchPlacement extends PlacementModifier {
    public static final String OVERWORLD_ORES = WorldgenSwitches.OVERWORLD_ORES;

    /** A map codec, so the placement JSON stays flat: {@code {"type": ..., "switch": "overworld_ores"}}. */
    public static final Codec<SwitchPlacement> CODEC = Codec.STRING.fieldOf("switch").<SwitchPlacement>flatXmap(
            name -> WorldgenSwitches.known(name) ? DataResult.success(new SwitchPlacement(name))
                    : DataResult.error(() -> "Unknown server switch " + name),
            placement -> DataResult.success(placement.name)).codec();

    private final String name;

    private SwitchPlacement(String name) {
        this.name = name;
    }

    public static SwitchPlacement of(String name) {
        if (!WorldgenSwitches.known(name)) {
            throw new IllegalArgumentException("Unknown server switch " + name);
        }
        return new SwitchPlacement(name);
    }

    public String name() {
        return name;
    }

    public boolean enabled() {
        return WorldgenSwitches.enabled(name);
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos position) {
        return enabled() ? Stream.of(position) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return MaterialContent.SWITCH_PLACEMENT.get();
    }
}
