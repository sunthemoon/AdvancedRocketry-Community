package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceTags;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/**
 * ADR-055 section 3: the {@code laser_target} endpoint the owner places on the surface it will dig. Its root holds a
 * 27-slot drop buffer (extractable, never insertable), the shaft cursor {@code next_layer}, the link
 * {@code {controller_id, link_id, ops_done}} of the one controller link it serves, and the freeze of a retired ID
 * (ADR-054 section 9). It registers once a chunk tag shows its ID persisted; until then it cannot be selected. Its
 * {@code generation} only grows: every reset adds one, and a controller records the generation of its first contact.
 * An unlinked marker adopts only a link that never touched it or one recorded at its current generation, so a link
 * dropped by any earlier reset sees {@code LINK_LOST} for good (review C11R-M1), while a marker a crash returned
 * unlinked (its adoption lost with the chunk) still adopts its link and settles it as credit.
 */
public final class LaserTargetBlockEntity extends EndgameDeviceBlockEntity implements MenuProvider {
    public static final ResourceLocation KIND = ModIdentity.id("laser_target");
    public static final int ROOT_SCHEMA = 1;
    public static final int BUFFER_SLOTS = 27;
    private static final int STATUS_CHECK_TICKS = 20;
    private static final int MAX_PARTICLES_PER_TICK = 8;
    private static final Set<String> STATE_KEYS = Set.of(EndgameDeviceTags.FROZEN, "next_layer", "buffer", "link",
            "generation");

    private final ItemStackHandler buffer = new ItemStackHandler(BUFFER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler automation = new ExtractOnly();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();
    private boolean frozen;
    private int nextLayer;
    @Nullable
    private UUID linkedController;
    @Nullable
    private UUID linkId;
    private long opsDone;
    private long generation;

    private EndgameCode endpointStatus = EndgameCode.AWAITING_WORLD_SAVE;
    private EndgameCode lastCode = EndgameCode.OK;
    private long nextStatusCheck;
    private long activeUntil;
    private boolean active;

    public LaserTargetBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.LASER_TARGET.get(), position, state);
    }

    @Override
    public EndgameSystem system() {
        return EndgameSystem.LASER_DRILL;
    }

    @Override
    protected int rootSchema() {
        return ROOT_SCHEMA;
    }

    @Override
    protected Set<String> stateKeys() {
        return STATE_KEYS;
    }

    @Override
    protected void resetState() {
        for (int slot = 0; slot < BUFFER_SLOTS; slot++) {
            buffer.setStackInSlot(slot, ItemStack.EMPTY);
        }
        frozen = false;
        nextLayer = LaserShaft.firstLayer(worldPosition);
        linkedController = null;
        linkId = null;
        opsDone = 0L;
        generation = 0L;
    }

    @Override
    protected void readState(CompoundTag root) {
        frozen = EndgameNbt.requireBoolean(root, EndgameDeviceTags.FROZEN);
        nextLayer = EndgameNbt.requireInt(root, "next_layer");
        if (nextLayer > LaserShaft.firstLayer(worldPosition)
                || nextLayer < LaserShaft.firstLayer(worldPosition) - LaserDrillSettings.MAX_DEPTH) {
            throw new IllegalArgumentException("The shaft cursor is outside its range");
        }
        LaserDrillStorage.readItems(EndgameNbt.requireCompound(root, "buffer"), buffer, false);
        if (root.contains("link")) {
            CompoundTag link = EndgameNbt.requireCompound(root, "link");
            EndgameNbt.requireKeys(link, Set.of("controller_id", "link_id", "ops_done"), "Laser target link");
            linkedController = EndgameNbt.requireUuid(link, "controller_id");
            linkId = EndgameNbt.requireUuid(link, "link_id");
            opsDone = EndgameNbt.requireLong(link, "ops_done");
            if (opsDone < 0) {
                throw new IllegalArgumentException("A negative ops_done");
            }
        }
        generation = EndgameNbt.requireLong(root, "generation");
        if (generation < 0) {
            throw new IllegalArgumentException("A negative marker generation");
        }
    }

    @Override
    protected void writeState(CompoundTag root) {
        root.putBoolean(EndgameDeviceTags.FROZEN, frozen);
        root.putInt("next_layer", nextLayer);
        root.put("buffer", buffer.serializeNBT());
        if (linkedController != null) {
            CompoundTag link = new CompoundTag();
            link.put("controller_id", NbtUtils.createUUID(linkedController));
            link.put("link_id", NbtUtils.createUUID(linkId));
            link.putLong("ops_done", opsDone);
            root.put("link", link);
        }
        root.putLong("generation", generation);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, LaserTargetBlockEntity target) {
        if (level instanceof ServerLevel server) {
            target.tick(server);
        }
    }

    public static void clientTick(Level level, BlockPos position, BlockState state, LaserTargetBlockEntity target) {
        if (!target.active) {
            return;
        }
        // At most 8 particles per tick at the current layer; the client's particle setting drops them at minimal.
        int count = 1 + level.random.nextInt(MAX_PARTICLES_PER_TICK);
        for (int i = 0; i < count; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, position.getX() - 0.5D + level.random.nextDouble() * 3.0D,
                    target.nextLayer + 1.05D, position.getZ() - 0.5D + level.random.nextDouble() * 3.0D, 0.0D,
                    0.05D, 0.0D);
        }
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (active && now >= activeUntil) {
            active = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (now < nextStatusCheck || quarantined()) {
            return;
        }
        nextStatusCheck = now + STATUS_CHECK_TICKS;
        Optional<EndgameService> service = EndgameRuntime.operational();
        if (service.isEmpty() || deviceId().isEmpty()) {
            endpointStatus = EndgameCode.ROOT_UNAVAILABLE;
            return;
        }
        if (ownerId().isEmpty()) {
            endpointStatus = EndgameCode.UNOWNED;
            return;
        }
        if (frozen) {
            endpointStatus = EndgameCode.ENDPOINT_RETIRED;
            return;
        }
        endpointStatus = service.get().endpointStatus(deviceId().get(), level.dimension().location(),
                worldPosition.asLong());
        if (endpointStatus == EndgameCode.ENDPOINT_RETIRED) {
            // A retired ID is frozen in the root for good, so it never registers again (review R4-L3).
            frozen = true;
            setChanged();
            service.get().forgetCandidate(deviceId().get());
        } else if (endpointStatus != EndgameCode.OK && endpointStatus != EndgameCode.ENDPOINT_POSITION_CONFLICT) {
            service.get().awaitRegistration(deviceId().get(), KIND, ownerId().get(), level.dimension().location(),
                    worldPosition.asLong());
            // ADR-054 section 2: while unregistered the chunk stays dirty, so the next autosave or the unload save
            // (which vanilla writes before it removes block entities) shows the ID persisted (review C11R-L5).
            setChanged();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        itemCapability = LazyOptional.of(() -> automation);
        nextStatusCheck = 0;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            deviceId().ifPresent(id -> EndgameRuntime.service().ifPresent(service -> service.forgetCandidate(id)));
        }
    }

    // ---- The drill's side of the link (ADR-055 section 3) ----------------------------------------------------

    /** Registered here, owned, not frozen or quarantined: selectable and able to serve a link. */
    public boolean endpointActive() {
        return endpointStatus == EndgameCode.OK && usable() && !frozen;
    }

    public EndgameCode endpointStatus() {
        return endpointStatus;
    }

    /**
     * The marker accepts the exact controller link it serves, or, when it serves none, a link that never touched it
     * ({@code linkGeneration < 0}) or one that touched it at its current generation (no reset since).
     */
    public boolean accepts(UUID controller, UUID link, long linkGeneration) {
        return LaserLinkCounters.accepts(linkedController != null,
                linkedController != null && linkedController.equals(controller) && link.equals(linkId), linkGeneration,
                generation);
    }

    /** Adopts the controller link on first contact with {@code ops_done = 0}. */
    public void adopt(UUID controller, UUID link) {
        if (linkedController == null) {
            linkedController = controller;
            linkId = link;
            opsDone = 0L;
            setChanged();
        }
    }

    public Optional<UUID> linkedController() {
        return Optional.ofNullable(linkedController);
    }

    public long opsDone() {
        return opsDone;
    }

    /** The number of resets so far; it never decreases. */
    public long generation() {
        return generation;
    }

    /** GameTests only: a marker whose chunk kept more or fewer layers than its controller's (a crash cut). */
    public void setOpsDoneForTest(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("ops_done is not negative");
        }
        opsDone = value;
        setChanged();
    }

    public int nextLayer() {
        return nextLayer;
    }

    public boolean frozen() {
        return frozen;
    }

    /** Whether the buffer can take every stack; the buffer is not changed. */
    public boolean fits(List<ItemStack> drops) {
        ItemStackHandler copy = new ItemStackHandler(BUFFER_SLOTS);
        for (int slot = 0; slot < BUFFER_SLOTS; slot++) {
            copy.setStackInSlot(slot, buffer.getStackInSlot(slot).copy());
        }
        for (ItemStack drop : drops) {
            if (!ItemHandlerHelper.insertItemStacked(copy, drop.copy(), false).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** One layer is done: drops in, the cursor down, {@code ops_done + 1}, and the beam shown to clients. */
    public void layerDone(List<ItemStack> drops, long now, int beamTicks) {
        for (ItemStack drop : drops) {
            if (!ItemHandlerHelper.insertItemStacked(buffer, drop.copy(), false).isEmpty()) {
                throw new IllegalStateException("The checked buffer did not take every drop");
            }
        }
        nextLayer--;
        opsDone++;
        lastCode = EndgameCode.OK;
        activeUntil = now + beamTicks;
        setChanged();
        if (!active && level != null) {
            active = true;
        }
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The code of the drill's last stop at this marker, for its owner's menu. */
    public void reportStop(EndgameCode code) {
        lastCode = code;
    }

    public EndgameCode lastCode() {
        return lastCode;
    }

    /**
     * The owner's reset: the marker serves no controller any more and its generation grows, so no earlier link is
     * adopted again. Its {@code ops_done} is audited, because a debt its old controller held is forgiven (ADR-055
     * section 3, a residual of at most a few layers' energy).
     *
     * @param actor the resetting player, or null for an owner change
     */
    public EndgameCode reset(@Nullable UUID actor) {
        generation = Math.addExact(generation, 1L);
        setChanged();
        if (linkedController == null) {
            return EndgameCode.OK;
        }
        UUID previous = linkedController;
        long done = opsDone;
        linkedController = null;
        linkId = null;
        opsDone = 0L;
        setChanged();
        if (level instanceof ServerLevel server) {
            EndgameRuntime.operational().ifPresent(service -> service.audit().line(server.getGameTime(),
                    system().id(), "MARKER_RESET", "OK", deviceId().orElse(null), ownerId().orElse(null), actor,
                    "controller=" + previous + " ops_done=" + done + " generation=" + generation));
        }
        return EndgameCode.OK;
    }

    /**
     * Review C11R-M2: a marker an operator gave to another owner serves none of the old owner's links; the new
     * owner's drill links it again. A marker not registered yet waits again under its new owner in the same tick, so
     * no later save registers it for the old owner, and a request queued from an earlier save no longer matches the
     * waiting candidate (review C11R2-L1).
     */
    @Override
    protected void ownerChanged(UUID previous) {
        reset(null);
        if (level instanceof ServerLevel server && !frozen && deviceId().isPresent() && ownerId().isPresent()) {
            EndgameRuntime.operational().ifPresent(service -> service.awaitRegistration(deviceId().get(), KIND,
                    ownerId().get(), server.dimension().location(), worldPosition.asLong()));
        }
    }

    public ItemStackHandler buffer() {
        return buffer;
    }

    public void dropBuffer(Level world, BlockPos position) {
        for (int slot = 0; slot < BUFFER_SLOTS; slot++) {
            net.minecraft.world.Containers.dropItemStack(world, position.getX(), position.getY(), position.getZ(),
                    buffer.getStackInSlot(slot));
            buffer.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected String describeState() {
        return "endpoint=" + endpointStatus.name() + " frozen=" + frozen + " next_layer=" + nextLayer + " link="
                + (linkedController == null ? "-" : linkedController + " ops_done=" + opsDone) + " last="
                + lastCode.name();
    }

    // ---- Render state (ADR-054 section 4 update tags: active and depth only) -------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", active);
        tag.putInt("depth", nextLayer);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        active = tag.getBoolean("active");
        nextLayer = tag.getInt("depth");
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean activeForRender() {
        return active;
    }

    public int depthForRender() {
        return nextLayer;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition.getX() - 1, nextLayer, worldPosition.getZ() - 1,
                worldPosition.getX() + 2, worldPosition.getY() + LaserBeam.MAX_HEIGHT, worldPosition.getZ() + 2);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.laser_target");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LaserTargetMenu(id, inventory, this);
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (!quarantined() && capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
    }

    /** Automation may extract drops and never insert (ADR-055 section 3). */
    private final class ExtractOnly implements IItemHandler {
        @Override
        public int getSlots() {
            return BUFFER_SLOTS;
        }

        @Override
        public @Nonnull ItemStack getStackInSlot(int slot) {
            return buffer.getStackInSlot(slot);
        }

        @Override
        public @Nonnull ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return level == null || level.isClientSide ? ItemStack.EMPTY : buffer.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return buffer.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return false;
        }
    }
}
