package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForceFieldPowerGameTests {
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void moonHeightEdgesProjectAndRetractWithoutOutsideChunkReads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(level.getHeight() == 256, "Moon height fixture is not 256 blocks high");
        heightEdges(helper, level);
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void overworldHeightEdgesProjectAndRetractWithoutOutsideChunkReads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().overworld();
        helper.assertTrue(level.getMinBuildHeight() == -64 && level.getMaxBuildHeight() == 320,
                "Overworld fixture has unexpected build-height limits");
        heightEdges(helper, level);
        helper.succeed();
    }
    private static void heightEdges(GameTestHelper helper, ServerLevel level) {
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        for (int y : new int[]{level.getMaxBuildHeight() - 1, level.getMaxBuildHeight() - 2,
                level.getMinBuildHeight(), level.getMinBuildHeight() + 1}) {
            BlockPos pos = new BlockPos(base.getX() + 1024, y, base.getZ() + 1024);
            var before = new LinkedHashMap<BlockPos, BlockState>();
            var reads = new ArrayList<BlockPos>();
            var data = new FieldCleanupSavedData(reads::add);
            FieldIdentity identity = null;
            try {
                for (BlockPos cell : FieldCellView.powerPositions(pos)) {
                    if (level.isOutsideBuildHeight(cell)) { continue; }
                    level.getChunk(cell.getX() >> 4, cell.getZ() >> 4);
                    before.put(cell, level.getBlockState(cell));
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                level.setBlock(pos, ModBlocks.FORCE_FIELD_PROJECTOR.get().defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                var source = (ForceFieldProjectorBlockEntity) level.getBlockEntity(pos);
                var previous = source.reservationIdentity();
                var state = source.state().bind(new UUID(404, 2));
                identity = new FieldIdentity(state.id(), state.owner().orElseThrow(), state.generation(),
                        level.dimension(), pos, state.direction());
                helper.assertTrue(FieldCleanupSavedData.get(level.getServer()).bindReservation(previous, identity),
                        "Height fixture could not bind its existing reservation");
                CompoundTag tag = new CompoundTag();
                tag.put(ForceFieldCodecs.PROJECTOR_ROOT, ForceFieldCodecs.projector(state));
                source.load(tag);
                helper.assertTrue(data.reserve(identity, 0), "Height fixture could not reserve cleanup capacity");
                BlockPos power = pos.east(), target = identity.target(1);
                level.setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                helper.assertTrue(new FieldCellView(level, null).powered(pos).equals(Optional.of(level.hasNeighborSignal(pos))),
                        "Height-edge power disagrees with the stock query at y=" + y);
                int growth = ForceFieldService.work(level.getServer(), data, data.view().get(identity.key()));
                helper.assertTrue(source.state().claims() == 1 && level.getBlockState(target).is(ModBlocks.FORCE_FIELD.get())
                        && growth <= FieldCellView.MAX_WORK, "Height-edge projection failed at y=" + y);
                level.setBlock(power, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                helper.assertTrue(new FieldCellView(level, null).powered(pos).equals(Optional.of(false)),
                        "Unpowered height edge did not decide at y=" + y);
                int retraction = ForceFieldService.work(level.getServer(), data, data.view().get(identity.key()));
                helper.assertTrue(source.state().claims() == 0 && level.getBlockState(target).is(Blocks.AIR)
                        && retraction <= FieldCellView.MAX_WORK && reads.stream().noneMatch(level::isOutsideBuildHeight),
                        "Height-edge retraction or read accounting failed at y=" + y);
                BlockPos absent = pos.offset(4096, 0, 4096).atY(level.getMaxBuildHeight());
                int chunkX = absent.getX() >> 4, chunkZ = absent.getZ() >> 4;
                helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null, "Outside-height chunk fixture is loaded");
                var outside = new FieldCellView(level, reads::add);
                int count = reads.size();
                helper.assertTrue(outside.available(absent) && outside.getBlockState(absent).is(Blocks.VOID_AIR)
                        && outside.getBlockEntity(absent) == null && outside.getFluidState(absent).isEmpty()
                        && outside.inspectedAndMutatedCells() == 0 && reads.size() == count
                        && level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                        "Outside-height VOID_AIR consulted or loaded a chunk");
            } finally {
                for (var original : before.entrySet()) {
                    ForceFieldProtection.physical(level, original.getKey(), () -> level.setBlock(original.getKey(), original.getValue(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
                }
                if (identity != null) { FieldCleanupSavedData.get(level.getServer()).forget(identity.key()); }
            }
        }
    }
    private static ForceFieldProjectorBlockEntity claim(GameTestHelper helper,
            ForceFieldPlacementGameTests.Placement f, FieldCleanupSavedData data, int claims) {
        helper.assertTrue(f.place().consumesAction(), "Native power fixture placement failed");
        var source = (ForceFieldProjectorBlockEntity) f.level.getBlockEntity(f.pos);
        var previous = source.reservationIdentity();
        var state = source.state().bind(new UUID(404, 1));
        var bound = new FieldIdentity(state.id(), state.owner().orElseThrow(), state.generation(),
                f.level.dimension(), f.pos, state.direction());
        helper.assertTrue(FieldCleanupSavedData.get(f.level.getServer()).bindReservation(previous, bound),
                "Power fixture failed to bind its native placement reservation");
        CompoundTag tag = new CompoundTag();
        tag.put(ForceFieldCodecs.PROJECTOR_ROOT, ForceFieldCodecs.projector(state.withClaims(claims)));
        source.load(tag);
        helper.assertTrue(data.reserve(source.identity(), claims), "Power fixture admission failed");
        return source;
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void loadedEdgeConductorCannotReadAnAbsentSecondHopChunk(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        BlockPos pos = new BlockPos(3_000_014 + base.getX() * 64, 220, 3_000_008 + base.getZ() * 64);
        try (var f = new ForceFieldPlacementGameTests.Placement(helper, pos)) {
            var reads = new ArrayList<BlockPos>();
            var data = new FieldCleanupSavedData(reads::add);
            var source = claim(helper, f, data, 1);
            helper.assertTrue(source.state().direction() == Direction.NORTH, "Edge fixture must point along its loaded chunk");
            BlockPos fieldPos = source.identity().target(1);
            f.level.setBlock(pos.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            ForceFieldProtection.physical(f.level, fieldPos, () -> f.level.setBlock(fieldPos,
                    ModBlocks.FORCE_FIELD.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
            var field = (ForceFieldBlockEntity) f.level.getBlockEntity(fieldPos);
            field.initialize(source.identity());
            try {
                for (Direction side : Direction.values()) {
                    helper.assertTrue(ForceFieldService.loaded(f.level, pos.relative(side)), "An immediate edge neighbour is not FULL");
                }
                int absentX = (pos.getX() >> 4) + 1, z = pos.getZ() >> 4;
                helper.assertTrue(f.level.getChunkSource().getChunkNow(absentX, z) == null, "Second-hop edge fixture is already loaded");
                var before = data.save(new CompoundTag());
                var sourceBefore = source.saveWithFullMetadata();
                var fieldBefore = field.saveWithFullMetadata();
                data.tick(f.level.getServer());
                helper.assertTrue(f.level.getChunkSource().getChunkNow(absentX, z) == null
                        && reads.stream().noneMatch(p -> (p.getX() >> 4) == absentX)
                        && data.inspectedCells() == reads.size(), "Power sampling loaded/read the absent chunk or mischarged actual reads");
                helper.assertTrue(data.save(new CompoundTag()).equals(before) && source.saveWithFullMetadata().equals(sourceBefore)
                        && field.saveWithFullMetadata().equals(fieldBefore) && f.level.getBlockEntity(fieldPos) == field,
                        "Unavailable redstone sampling changed the source, claims or sealed field");
            } finally {
                ForceFieldProtection.physical(f.level, fieldPos, () -> f.level.setBlock(fieldPos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
                f.level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void sevenNativeProjectorsChargeTheirActualLoadedWorldReadSets(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        var reads = new ArrayList<BlockPos>();
        var data = new FieldCleanupSavedData(reads::add);
        var fixtures = new ArrayList<ForceFieldPlacementGameTests.Placement>();
        var sources = new ArrayList<ForceFieldProjectorBlockEntity>();
        try {
            for (int i = 0; i < 7; i++) {
                BlockPos pos = new BlockPos(4_000_008 + base.getX() * 64 + i * 64, 220, 4_000_008 + base.getZ() * 64);
                var f = new ForceFieldPlacementGameTests.Placement(helper, pos); fixtures.add(f);
                sources.add(claim(helper, f, data, 0));
                for (Direction side : Direction.values()) {
                    f.level.setBlock(pos.relative(side), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            data.tick(helper.getLevel().getServer());
            helper.assertTrue(reads.size() == 50 && new HashSet<>(reads).size() == 50 && data.inspectedCells() == 50
                    && reads.size() <= FieldWorkBudget.MAX_CELLS,
                    "Seven projectors' actual loaded-world reads disagreed with the shared 64-cell accounting");
            int visited = 0;
            for (var source : sources) {
                var stencil = FieldCellView.powerPositions(source.getBlockPos());
                long actual = reads.stream().filter(stencil::contains).count();
                helper.assertTrue(actual == 0 || actual == 25, "A powered-down conductor stencil was undercounted");
                if (actual == 25) { visited++; }
                helper.assertTrue(source.state().claims() == 0, "Unpowered budget fixture grew a field");
            }
            helper.assertTrue(visited == 2, "The scheduler did not defer the five visits that exceed remaining capacity");
        } finally {
            for (var f : fixtures) {
                for (Direction side : Direction.values()) {
                    f.level.setBlock(f.pos.relative(side), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                f.close();
            }
        }
        helper.succeed();
    }
    private ForceFieldPowerGameTests() { }
}
