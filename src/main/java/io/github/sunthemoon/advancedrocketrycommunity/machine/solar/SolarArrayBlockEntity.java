package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternObservation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.PatternRoleResolver;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.ServerLevelPatternWorldView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/** Controller-owned FE; formation, bindings, feedback and output quota are ephemeral. */
public final class SolarArrayBlockEntity extends BlockEntity implements MenuProvider {
    private final Supplier<MenuType<SolarArrayMenu>> menuType;
    private final BooleanSupplier enabled;
    private final IntSupplier multiplier;
    private final Supplier<SolarExposure> exposure;
    private final Set<ChunkPos> unloading = new HashSet<>();
    private Set<BlockPos> countedPanels = Set.of();
    private List<SolarArrayOutputBlockEntity> outputs = List.of();
    private Object token = new Object();
    private SolarArrayRules.Structure structure = SolarArrayRules.Structure.PENDING;
    private int collectors;
    private int energy;
    private Tag refusedRoot;
    private boolean nonAdmissible;
    private boolean operationLock;
    private long outputTick = Long.MIN_VALUE;
    private int outputThisTick;
    private SolarGeneration.Step feedback = SolarArrayRules.step(0, structure, 0, 1, true, false, null);

    public SolarArrayBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state,
            Supplier<MenuType<SolarArrayMenu>> menuType, BooleanSupplier enabled,
            IntSupplier multiplier, Supplier<SolarExposure> exposure) {
        super(type, position, state);
        this.menuType = menuType; this.enabled = enabled; this.multiplier = multiplier; this.exposure = exposure;
    }

    public int energyStored() { return energy; }
    public int actualCredit() { return feedback.credit(); }
    public int collectors() { return collectors; }
    public SolarArrayRules.Structure structure() { return structure; }
    public SolarGeneration.Reason reason() { return repairRequired() ? SolarGeneration.Reason.REPAIR_REQUIRED : feedback.reason(); }
    public SolarGeneration.Environment environment() { return feedback.environment(); }
    public boolean repairRequired() { return refusedRoot != null; }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    boolean enabled() { return enabled.getAsBoolean(); }

    public boolean physical() {
        if (isRemoved() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()
                || server.getServer().getLevel(server.dimension()) != server || !server.isInWorldBounds(worldPosition)) { return false; }
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(worldPosition) == this;
    }
    public boolean available() {
        if (repairRequired() || operationLock || !physical()) { return false; }
        SolarExposure handle = exposure.get();
        return handle != null && handle.owns(((ServerLevel) level).getServer());
    }

    private PatternTransform transform() {
        Direction front = getBlockState().getValue(SolarArrayBlock.FACING);
        return new PatternTransform(switch (front) {
            case NORTH -> PatternRotation.ZERO;
            case EAST -> PatternRotation.CLOCKWISE_90;
            case SOUTH -> PatternRotation.CLOCKWISE_180;
            case WEST -> PatternRotation.CLOCKWISE_270;
            default -> throw new IllegalStateException("Solar array facing is not horizontal");
        }, false);
    }
    private PatternPosition anchor() { return new PatternPosition(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()); }
    private static BlockPos blockPos(PatternPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
    Set<BlockPos> footprint() {
        Set<BlockPos> result = new HashSet<>();
        SolarArrayLayout.footprint(transform(), anchor()).forEach(position -> result.add(blockPos(position)));
        unloading.removeIf(chunk -> result.stream().noneMatch(position -> new ChunkPos(position).equals(chunk)));
        return Set.copyOf(result);
    }
    void chunkState(int x, int z, boolean unavailable) {
        if (unavailable) { unloading.add(new ChunkPos(x, z)); } else { unloading.remove(new ChunkPos(x, z)); }
    }

    void invalidateStructure(SolarArrayRules.Structure status) {
        var previous = outputs;
        var previousPanels = countedPanels;
        outputs = List.of(); countedPanels = Set.of(); token = new Object(); collectors = 0; structure = status;
        feedback = SolarArrayRules.step(energy, status, 0, multiplier.getAsInt(), enabled(), repairRequired(), null);
        for (var output : previous) {
            output.release(this);
            if (level instanceof ServerLevel server) { SolarArrayRuntime.manager(server).released(output.getBlockPos()); }
        }
        if (level instanceof ServerLevel server) { previousPanels.forEach(SolarArrayRuntime.manager(server)::released); }
    }

    boolean counts(BlockPos panel) { return structure == SolarArrayRules.Structure.FORMED && countedPanels.contains(panel); }

    void revalidate(ServerLevel server) {
        if (repairRequired()) { invalidateStructure(SolarArrayRules.Structure.UNAVAILABLE); return; }
        if (!unloading.isEmpty()) { invalidateStructure(SolarArrayRules.Structure.UNLOADED); return; }
        var view = new ServerLevelPatternWorldView(server, new PatternRoleResolver() {
            @Override public boolean isController(BlockPos pos, BlockState state) {
                return pos.equals(worldPosition) && state.getBlock() instanceof SolarArrayBlock;
            }
            @Override public Optional<String> portChannel(BlockPos pos, BlockState state) {
                return state.getBlock() instanceof SolarArrayOutputBlock ? Optional.of(SolarArrayLayout.OUTPUT_CHANNEL) : Optional.empty();
            }
        });
        var result = SolarArrayLayout.evaluate(transform(), anchor(), position -> {
            BlockPos pos = blockPos(position);
            if (!server.isInWorldBounds(pos) || !server.getWorldBorder().isWithinBounds(pos)) {
                return PatternObservation.unloaded();
            }
            return server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                    ? PatternObservation.unloaded() : view.observe(position);
        });
        if (!result.formed()) { invalidateStructure(SolarArrayRules.Structure.of(result.status())); return; }
        Set<BlockPos> panels = new HashSet<>();
        var manager = SolarArrayRuntime.manager(server);
        for (var panel : result.panels()) {
            BlockPos pos = blockPos(panel);
            if (manager.panelClaimedByOther(this, pos)) { invalidateStructure(SolarArrayRules.Structure.CONFLICT); return; }
            panels.add(pos);
        }
        List<SolarArrayOutputBlockEntity> plugs = new ArrayList<>(2);
        for (var output : result.outputs()) {
            BlockPos pos = blockPos(output);
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            var entity = chunk == null ? null : chunk.getBlockEntities().get(pos);
            if (!(entity instanceof SolarArrayOutputBlockEntity plug) || !plug.physical()) {
                invalidateStructure(SolarArrayRules.Structure.UNAVAILABLE); return;
            }
            if (plug.claimedByOther(this)) { invalidateStructure(SolarArrayRules.Structure.CONFLICT); return; }
            plugs.add(plug);
        }
        countedPanels = Set.copyOf(panels); collectors = result.collectors(); structure = SolarArrayRules.Structure.FORMED;
        outputs = List.copyOf(plugs);
        outputs.forEach(plug -> plug.bind(this, token));
    }

    boolean bindingValid(SolarArrayOutputBlockEntity plug, Object observed) {
        return observed == token && structure == SolarArrayRules.Structure.FORMED && outputs.contains(plug)
                && physical() && plug.physical() && plug.boundTo(this, observed);
    }
    boolean owns(SolarArrayOutputBlockEntity plug, Object observed) {
        return bindingValid(plug, observed) && available();
    }
    int extract(SolarArrayOutputBlockEntity plug, Object observed, int maximum, boolean simulate) {
        if (maximum <= 0 || !enabled() || !owns(plug, observed)) { return 0; }
        int amount = Math.min(maximum, Math.min(energy, remainingOutput()));
        if (!simulate && amount > 0) { debit(amount); }
        return amount;
    }
    private int remainingOutput() {
        return outputTick == level.getGameTime() ? SolarArrayRules.OUTPUT_PER_TICK - outputThisTick : SolarArrayRules.OUTPUT_PER_TICK;
    }
    private void debit(int amount) {
        long tick = level.getGameTime();
        if (outputTick != tick) { outputTick = tick; outputThisTick = 0; }
        energy -= amount; outputThisTick += amount; setChanged();
    }

    public static void serverTick(ServerLevel server, BlockPos position, BlockState state, SolarArrayBlockEntity array) {
        if (array.operationLock || !array.physical() || !position.equals(array.worldPosition)) { return; }
        // Two facade identity checks, not a footprint scan. A revoked plug stops collection before its next validation.
        if (array.structure == SolarArrayRules.Structure.FORMED
                && array.outputs.stream().anyMatch(plug -> !array.bindingValid(plug, array.token))) {
            SolarArrayRuntime.manager(server).observe(array);
        }
        boolean enabled = array.enabled();
        SolarGeneration.Environment environment = SolarGeneration.Environment.unavailable();
        if (enabled && !array.repairRequired() && array.structure == SolarArrayRules.Structure.FORMED) {
            SolarExposure handle = array.exposure.get();
            if (handle != null) { environment = handle.read(server, position, array); }
        }
        array.feedback = SolarArrayRules.step(array.energy, array.structure, array.collectors, array.multiplier.getAsInt(),
                enabled, array.repairRequired(), environment);
        if (array.energy != array.feedback.energy()) { array.energy = array.feedback.energy(); array.setChanged(); }
        if (enabled && array.structure == SolarArrayRules.Structure.FORMED && array.available()) { array.push(server); }
    }
    private void push(ServerLevel server) {
        for (var plug : outputs) {
            for (Direction side : Direction.values()) {
                int offer = Math.min(energy, remainingOutput());
                if (offer <= 0 || !enabled() || !owns(plug, token)) { return; }
                BlockPos pos = plug.getBlockPos().relative(side);
                if (!server.isInWorldBounds(pos)) { continue; }
                var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                BlockEntity receiver = chunk == null ? null : chunk.getBlockEntities().get(pos);
                if (receiver == null || receiver.isRemoved() || receiver instanceof SolarArrayOutputBlockEntity) { continue; }
                Object observed = token;
                operationLock = true;
                try {
                    IEnergyStorage port = receiver.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).resolve().orElse(null);
                    if (port != null && port.canReceive() && enabled() && !repairRequired() && bindingValid(plug, observed)
                            && !receiver.isRemoved() && chunk.getBlockEntities().get(pos) == receiver) {
                        int accepted = Math.max(0, Math.min(offer, port.receiveEnergy(offer, false)));
                        if (accepted > 0) { debit(accepted); }
                    }
                } finally { operationLock = false; }
            }
        }
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) { protectUnsafeRoot(server); SolarArrayRuntime.manager(server).observe(this); }
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) { SolarArrayRuntime.manager(server).forget(this); }
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        if (level instanceof ServerLevel server) { SolarArrayRuntime.manager(server).forget(this); }
        super.onChunkUnloaded();
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps(); invalidateStructure(SolarArrayRules.Structure.PENDING);
    }
    @Override public void reviveCaps() {
        super.reviveCaps();
        if (physical()) { SolarArrayRuntime.manager((ServerLevel) level).observe(this); }
    }
    private void protectUnsafeRoot(ServerLevel server) {
        if (nonAdmissible) {
            io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves.recordObservedDenial(
                    server, new ChunkPos(worldPosition), "Refusing chunk save with non-admissible solar input; back up and repair first");
        }
    }
    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        outer.put(SolarSave.ARRAY_ROOT, refusedRoot == null ? SolarSave.encode(energy, SolarArrayRules.CAPACITY)
                : nonAdmissible ? refusedRoot : refusedRoot.copy());
    }
    @Override public void load(CompoundTag outer) {
        if (operationLock) { throw new IllegalStateException("Reentrant solar array load"); }
        super.load(outer);
        invalidateStructure(SolarArrayRules.Structure.PENDING);
        energy = 0; refusedRoot = null; nonAdmissible = false;
        if (outer.contains(SolarSave.ARRAY_ROOT)) {
            Tag raw = outer.get(SolarSave.ARRAY_ROOT);
            nonAdmissible = !SolarSave.bounded(raw);
            if (nonAdmissible) { refusedRoot = raw; }
            else {
                try { energy = SolarSave.decode(raw, SolarArrayRules.CAPACITY); }
                catch (IllegalArgumentException refused) { refusedRoot = raw.copy(); }
            }
        }
        feedback = SolarArrayRules.step(energy, structure, 0, 1, true, repairRequired(), null);
        if (physical()) { protectUnsafeRoot((ServerLevel) level); SolarArrayRuntime.manager((ServerLevel) level).observe(this); }
    }
    @Override public Component getDisplayName() { return Component.translatable("block.advancedrocketrycommunity.solar_array"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return available() && SolarArrayMenu.admitted(player, this) ? new SolarArrayMenu(menuType.get(), id, this) : null;
    }
}
