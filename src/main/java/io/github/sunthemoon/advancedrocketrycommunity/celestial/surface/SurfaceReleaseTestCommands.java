package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * C15b packaged-server hooks for ADR-063 section 9 (S1), registered only with
 * {@code -Dadvancedrocketrycommunity.releaseTestHooks=true}. {@code generate <dimension> <chunkX> <chunkZ> <radius>}
 * generates a square of chunks (radius at most 8) on the server thread and reports the time, the highest surface
 * and the structure starts found; {@code sample <dimension> <x> <z>} reports one column's top block. Every line
 * starts with {@code ARCE_RELEASE_TEST}.
 */
public final class SurfaceReleaseTestCommands {
    private static final int MAX_RADIUS = 8;

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) {
            return;
        }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("surface")
                .then(Commands.literal("release-test").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("generate")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                        .then(Commands.argument("radius",
                                                                IntegerArgumentType.integer(0, MAX_RADIUS))
                                                                .executes(this::generate))))))
                        .then(Commands.literal("sample")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(this::sample))))))));
    }

    private int generate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = DimensionArgument.getDimension(context, "dimension");
        int centreX = IntegerArgumentType.getInteger(context, "chunkX");
        int centreZ = IntegerArgumentType.getInteger(context, "chunkZ");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int chunks = 0;
        int highest = level.getMinBuildHeight();
        long slowest = 0;
        Map<String, Integer> starts = new TreeMap<>();
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        long started = System.nanoTime();
        for (int x = centreX - radius; x <= centreX + radius; x++) {
            for (int z = centreZ - radius; z <= centreZ + radius; z++) {
                long before = System.nanoTime();
                ChunkAccess chunk = level.getChunk(x, z, ChunkStatus.FULL, true);
                slowest = Math.max(slowest, System.nanoTime() - before);
                chunks++;
                ChunkPos position = chunk.getPos();
                for (int dx = 0; dx < 16; dx += 4) {
                    for (int dz = 0; dz < 16; dz += 4) {
                        highest = Math.max(highest, chunk.getHeight(Heightmap.Types.WORLD_SURFACE,
                                position.getMinBlockX() + dx, position.getMinBlockZ() + dz));
                    }
                }
                for (Map.Entry<Structure, StructureStart> start : chunk.getAllStarts().entrySet()) {
                    if (start.getValue().isValid()) {
                        starts.merge(String.valueOf(structures.getKey(start.getKey())), 1, Integer::sum);
                    }
                }
            }
        }
        long millis = (System.nanoTime() - started) / 1_000_000L;
        String line = "ARCE_RELEASE_TEST surface generate dim=" + level.dimension().location() + " chunks=" + chunks
                + " millis=" + millis + " slowest_ms=" + slowest / 1_000_000L + " highest=" + highest + " starts="
                + starts;
        AdvancedRocketryCommunity.LOGGER.info(line);
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return chunks;
    }

    private int sample(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = DimensionArgument.getDimension(context, "dimension");
        int x = IntegerArgumentType.getInteger(context, "x");
        int z = IntegerArgumentType.getInteger(context, "z");
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos position = new BlockPos(x, top, z);
        String line = "ARCE_RELEASE_TEST surface sample dim=" + level.dimension().location() + " x=" + x + " z=" + z
                + " top=" + top + " block=" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock())
                + " biome=" + level.getBiome(position).unwrapKey().map(key -> key.location().toString()).orElse("?");
        AdvancedRocketryCommunity.LOGGER.info(line);
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return top;
    }
}
