package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * A biome source of irregular patches (ADR-063 section 5, Venus): each cell of {@code cell_size} quarts (4 blocks)
 * has a jittered centre, and a position takes the biome of the nearest centre, chosen per cell by a fixed hash. It
 * reads no climate parameter, so a Level keeps its noise router unchanged while it gains several biomes. The layout
 * is the same in every world, as legacy planets' biome layers did not follow the world seed either.
 */
public final class PatchBiomeSource extends BiomeSource {
    public static final Codec<PatchBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Biome.CODEC.listOf().fieldOf("biomes").forGetter(source -> source.biomes),
            Codec.intRange(4, 256).fieldOf("cell_size").forGetter(source -> source.cellSize),
            Codec.LONG.fieldOf("salt").forGetter(source -> source.salt)
    ).apply(instance, PatchBiomeSource::new));

    private final List<Holder<Biome>> biomes;
    private final int cellSize;
    private final long salt;

    public PatchBiomeSource(List<Holder<Biome>> biomes, int cellSize, long salt) {
        if (biomes.isEmpty() || biomes.size() > 16) {
            throw new IllegalArgumentException("a patch biome source needs 1..16 biomes");
        }
        this.biomes = List.copyOf(biomes);
        this.cellSize = cellSize;
        this.salt = salt;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return biomes.get(index(quartX, quartZ));
    }

    /** The biome index at a quart position (pure arithmetic, for tests). */
    public int index(int quartX, int quartZ) {
        int cellX = Math.floorDiv(quartX, cellSize);
        int cellZ = Math.floorDiv(quartZ, cellSize);
        long best = Long.MAX_VALUE;
        int chosen = 0;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                long hash = mix(cellX + ox, cellZ + oz);
                long centreX = (long) (cellX + ox) * cellSize + Math.floorMod(hash, cellSize);
                long centreZ = (long) (cellZ + oz) * cellSize + Math.floorMod(hash >>> 20, cellSize);
                long dx = quartX - centreX;
                long dz = quartZ - centreZ;
                long distance = dx * dx + dz * dz;
                if (distance < best) {
                    best = distance;
                    chosen = (int) Math.floorMod(hash >>> 40, (long) biomes.size());
                }
            }
        }
        return chosen;
    }

    private long mix(int x, int z) {
        long value = salt ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
