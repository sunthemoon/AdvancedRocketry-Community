package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Device;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Reuses the physical kernel but never starts a recipe. Registry records own all research state. */
public final class ResearchControllerBlockEntity extends ClassicControllerBlockEntity {
    public static final String OBSERVATORY_ROOT = "arce_observatory", PROCESSOR_ROOT = "arce_astrobody_data_processor";
    private final boolean processor;
    private ResearchIdentity identity;
    private Tag rejected;
    private ResearchStatus status = ResearchStatus.UNFORMED;
    private UUID selection;
    private int answeredUntil;

    public ResearchControllerBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state, boolean processor) {
        super(type, position, state, processor ? ClassicMachineDefinition.ASTROBODY_DATA_PROCESSOR : ClassicMachineDefinition.OBSERVATORY);
        this.processor = processor;
    }
    public boolean processor() { return processor; }
    /** Only the two research types get a ticker; the other classic controllers keep none. */
    static boolean ticks(BlockEntityType<?> type) {
        return type == ModBlockEntities.OBSERVATORY.get() || type == ModBlockEntities.ASTROBODY_DATA_PROCESSOR.get();
    }
    public String rootKey() { return processor ? PROCESSOR_ROOT : OBSERVATORY_ROOT; }
    public Optional<ResearchIdentity> identity() { return Optional.ofNullable(identity); }
    public Optional<UUID> researchOwner() { return identity().flatMap(ResearchIdentity::owner); }
    public Device device() {
        if (identity == null || level == null) { throw new IllegalStateException("Unplaced research device"); }
        return new Device(identity.device(), level.dimension().location(), worldPosition.asLong());
    }
    void placed(UUID owner) {
        if (identity == null && rejected == null && level instanceof ServerLevel server) {
            identity = new ResearchIdentity(controllerState().machineInstanceId(), Optional.of(owner),
                    server.dimension().location(), worldPosition.asLong(), false);
            setChanged(); ResearchRuntime.service(server).observe(this);
        }
    }
    void quarantineCopy() {
        if (identity != null && !identity.collision()) { identity = identity.quarantined(); setChanged(); }
        status = ResearchStatus.DEVICE_COLLISION;
    }
    public ResearchStatus researchStatus() { return status; }
    void status(ResearchStatus status) { this.status = status; }
    /** An intent's answer stays visible for two seconds unless work or a pause replaces it. */
    void answer(ResearchStatus status, int tick) { this.status = status; answeredUntil = tick + 40; }
    /** The per-tick idle status; it does not hide a recent answer. */
    void idle(int tick) { if (tick - answeredUntil >= 0) { status = ResearchStatus.IDLE; } }
    UUID selection() { return selection; }
    void selection(UUID id) { selection = id; }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    @Override public boolean repairRequired() {
        return rejected != null || checkpoint().job().isPresent() || !checkpoint().resources().banks().isEmpty() || super.repairRequired();
    }
    @Override public boolean formed() { return !repairRequired() && (identity == null || !identity.collision()) && super.formed(); }
    @Override boolean ownerUnavailable() {
        return repairRequired() || identity != null && identity.collision() || super.ownerUnavailable();
    }
    void loadedTick() {
        if (level instanceof ServerLevel server && physical()) { ResearchRuntime.service(server).enqueue(this); }
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            observeUnsafeIdentity(server); ResearchRuntime.service(server).observe(this);
        }
    }
    @Override public void onChunkUnloaded() {
        if (level instanceof ServerLevel server) { ResearchRuntime.service(server).forget(this); }
        super.onChunkUnloaded();
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) { ResearchRuntime.service(server).forget(this); }
        super.setRemoved();
    }
    @Override public void load(CompoundTag outer) {
        if (level instanceof ServerLevel server) { ResearchRuntime.service(server).forget(this); }
        super.load(outer); identity = null; rejected = null; selection = null;
        if (outer.contains(rootKey())) {
            Tag raw = outer.get(rootKey());
            try { identity = ResearchIdentity.decode(raw); }
            catch (RuntimeException unsupported) { rejected = ResearchIdentity.bounded(raw) ? raw.copy() : raw; }
        } else if (outer.contains(ClassicCheckpointCodec.ROOT)) {
            // An existing kernel root with no research identity cannot acquire a new owner/identity.
            rejected = new CompoundTag();
        }
        if (level instanceof ServerLevel server) {
            observeUnsafeIdentity(server); ResearchRuntime.service(server).observe(this);
        }
    }
    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (rejected != null) { outer.put(rootKey(), ResearchIdentity.bounded(rejected) ? rejected.copy() : rejected); }
        else if (identity != null) { outer.put(rootKey(), identity.encode()); }
    }
    private void observeUnsafeIdentity(ServerLevel server) {
        if (rejected != null && !ResearchIdentity.bounded(rejected)) {
            io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves.recordObservedDenial(
                    server, new net.minecraft.world.level.ChunkPos(worldPosition), "Research device root requires backup and offline repair");
        }
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ResearchControllerMenu(id, this, player);
    }
}
