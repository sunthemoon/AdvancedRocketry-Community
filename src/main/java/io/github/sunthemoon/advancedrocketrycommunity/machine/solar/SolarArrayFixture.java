package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/** Disposable native-world cells and injected read-only exposure. Not a production API or restart evidence. */
final class SolarArrayFixture implements AutoCloseable {
    final ServerLevel level;
    final BlockPos position;
    final SolarExposure exposure = new SolarExposure();
    final CelestialCatalogManager catalogs = new CelestialCatalogManager();
    final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
    SolarArrayBlockEntity array;
    boolean enabled = true;
    int multiplier = 1;
    private boolean closed;

    SolarArrayFixture(ServerLevel level, BlockPos position) {
        this.level = level; this.position = position;
        catalogs.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions()));
        exposure.start(level.getServer(), catalogs);
    }
    void remember(BlockPos pos) {
        if (before.containsKey(pos)) { return; }
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntities().containsKey(pos) || level.getBlockState(pos).hasBlockEntity()) {
            throw new IllegalStateException("Solar fixture requires already loaded cells without native data");
        }
        before.put(pos, level.getBlockState(pos));
    }
    void set(BlockPos pos, BlockState state) { remember(pos); level.setBlock(pos, state, Block.UPDATE_ALL); }
    SolarArrayBlockEntity install(BlockPos controller, int panels) {
        return install(controller, panels, Direction.NORTH);
    }
    SolarArrayBlockEntity install(BlockPos controller, int panels, Direction facing) {
        var state = ModBlocks.SOLAR_ARRAY.get().defaultBlockState().setValue(SolarArrayBlock.FACING, facing);
        var transform = new PatternTransform(switch (facing) {
            case NORTH -> PatternRotation.ZERO;
            case EAST -> PatternRotation.CLOCKWISE_90;
            case SOUTH -> PatternRotation.CLOCKWISE_180;
            case WEST -> PatternRotation.CLOCKWISE_270;
            default -> throw new IllegalArgumentException("Fixture facing is not horizontal");
        }, false);
        var anchor = new PatternPosition(controller.getX(), controller.getY(), controller.getZ());
        int collector = 0;
        for (var cell : SolarArrayLayout.cells()) {
            var world = transform.localToWorld(cell.local(), SolarArrayLayout.ANCHOR, anchor);
            var pos = new BlockPos(world.x(), world.y(), world.z());
            if (cell.collector()) { set(pos, collector++ < panels ? ModBlocks.SOLAR_ARRAY_PANEL.get().defaultBlockState() : Blocks.AIR.defaultBlockState()); }
            else { set(pos, cell.local().equals(SolarArrayLayout.ANCHOR) ? state : ModBlocks.POWER_OUTPUT_PLUG.get().defaultBlockState()); }
        }
        var injected = new SolarArrayBlockEntity(ModBlockEntities.SOLAR_ARRAY.get(), controller, state,
                ModMenuTypes.SOLAR_ARRAY::get, () -> enabled, () -> multiplier, () -> exposure);
        level.setBlockEntity(injected);
        SolarArrayRuntime.manager(level).observe(injected);
        SolarArrayRuntime.manager(level).tick();
        if (controller.equals(position)) { array = injected; }
        return injected;
    }
    void form() { SolarArrayRuntime.manager(level).tick(); }
    void tick() { SolarArrayBlockEntity.serverTick(level, position, array.getBlockState(), array); }
    void energy(int energy) {
        CompoundTag outer = new CompoundTag(); outer.put(SolarSave.ARRAY_ROOT, SolarSave.encode(energy, SolarArrayRules.CAPACITY));
        array.load(outer); form();
    }
    IEnergyStorage port(int x) {
        var entity = level.getBlockEntity(position.offset(x, 0, 0));
        return entity.getCapability(ForgeCapabilities.ENERGY).orElseThrow(() -> new IllegalStateException("Array output capability absent"));
    }
    @Override public void close() {
        if (closed) { return; } closed = true;
        try { before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL)); }
        finally { exposure.close(level.getServer()); }
    }
}
