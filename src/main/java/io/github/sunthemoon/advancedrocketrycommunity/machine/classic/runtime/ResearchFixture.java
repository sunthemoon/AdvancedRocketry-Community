package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorContent;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/** Explicitly loaded, disposable test blueprint. Production code never requests these tickets/chunks. */
final class ResearchFixture implements AutoCloseable {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("arce_w21c", Comparator.comparingLong(ChunkPos::toLong));
    final ServerLevel level;
    final BlockPos origin;
    final ResearchControllerBlockEntity controller;
    final List<BlockPos> cells = new ArrayList<>();
    final List<ChunkPos> tickets = new ArrayList<>();
    final List<ClassicHatchBlockEntity> power = new ArrayList<>();
    final PatternTransform transform;
    final MultiblockPatternDefinition pattern;
    int injected;
    ResearchFixture(ServerLevel level, BlockPos origin, boolean processor, Direction facing, UUID owner) {
        this.level = level; this.origin = origin; pattern = ResearchPatterns.definition(processor);
        transform = new PatternTransform(switch (facing) {
            case NORTH -> PatternRotation.ZERO; case EAST -> PatternRotation.CLOCKWISE_90;
            case SOUTH -> PatternRotation.CLOCKWISE_180; case WEST -> PatternRotation.CLOCKWISE_270;
            default -> throw new IllegalArgumentException("Research fixture facing");
        }, false);
        for (var local : pattern.cells().keySet()) { cells.add(world(local)); }
        cells.stream().map(ChunkPos::new).distinct().forEach(chunk -> {
            tickets.add(chunk); level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk); level.getChunk(chunk.x, chunk.z);
        });
        for (var entry : pattern.cells().entrySet()) {
            BlockState state; var matcher = entry.getValue();
            if (matcher instanceof PatternMatcher.Controller) { state = (processor ? ModBlocks.ASTROBODY_DATA_PROCESSOR : ModBlocks.OBSERVATORY)
                    .get().defaultBlockState().setValue(ClassicControllerBlock.FACING, facing); }
            else if (matcher instanceof PatternMatcher.ExactBlock block) { state = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(block.blockId())).defaultBlockState(); }
            else if (matcher instanceof PatternMatcher.Port) { state = ModBlocks.POWER_INPUT_PLUG.get().defaultBlockState(); }
            else if (matcher instanceof PatternMatcher.BlockTag tag) {
                state = tag.tagId().endsWith("motors") ? MotorContent.block(MotorDefinition.MOTOR).get().defaultBlockState()
                        : tag.tagId().equals("minecraft:slabs") ? Blocks.STONE_SLAB.defaultBlockState()
                        : tag.tagId().endsWith("observatory_shell_parts") ? ModBlocks.MACHINE_CASING.get().defaultBlockState()
                        : Blocks.AIR.defaultBlockState();
            } else { state = Blocks.AIR.defaultBlockState(); }
            // One wildcard power plug supplies the observatory without changing its frozen layout.
            if (!processor && entry.getKey().equals(new PatternPosition(1, 0, 0))) { state = ModBlocks.POWER_INPUT_PLUG.get().defaultBlockState(); }
            level.setBlock(world(entry.getKey()), state, Block.UPDATE_ALL);
        }
        controller = (ResearchControllerBlockEntity) level.getBlockEntity(origin); controller.placed(owner);
        for (BlockPos cell : cells) { if (level.getBlockEntity(cell) instanceof ClassicHatchBlockEntity hatch) { power.add(hatch); } }
        ClassicRuntime.manager(level).observe(controller); ClassicRuntime.manager(level).tick();
    }
    BlockPos world(PatternPosition local) {
        var position = transform.localToWorld(local, pattern.controllerAnchor(), new PatternPosition(origin.getX(), origin.getY(), origin.getZ()));
        return new BlockPos(position.x(), position.y(), position.z());
    }
    BlockPos lens() { var offset = transform.applyOffset(new PatternPosition(0, 3, 1)); return origin.offset(offset.x(), offset.y(), offset.z()); }
    void feed(int offered) {
        for (var hatch : power) { if (offered > 0) {
            int accepted = hatch.getCapability(ForgeCapabilities.ENERGY).orElseThrow(() -> new IllegalStateException("Research power capability"))
                    .receiveEnergy(offered, false); offered -= accepted; injected += accepted;
        } }
    }
    boolean ticking() { return level.isPositionEntityTicking(origin); }
    void flush() { SatelliteMissionSavedData.get(level.getServer()).flush(level.getServer()); }
    void revalidate() { ClassicRuntime.manager(level).changed(origin); ClassicRuntime.manager(level).tick(); }
    @Override public void close() {
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); ClassicRuntime.manager(level).forget(controller);
        for (BlockPos cell : cells) { level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
        tickets.forEach(chunk -> level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, chunk));
    }
}
