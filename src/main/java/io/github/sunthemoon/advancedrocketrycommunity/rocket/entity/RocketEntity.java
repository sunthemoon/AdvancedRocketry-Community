package io.github.sunthemoon.advancedrocketrycommunity.rocket.entity;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Thin persistent rocket entity. Transactions and rendering remain separate services. */
public final class RocketEntity extends Entity implements MenuProvider {
    private static final String DATA_KEY = "RocketEntityData";
    private static final String FLIGHT_DATA_KEY = "flight_data";
    private static final int ENTITY_SCHEMA_VERSION = 2;
    private static final EntityDataAccessor<String> DISPLAYED_FLIGHT_STATE =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> DISPLAYED_FUEL_AMOUNT =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.LONG);

    private RocketStructureSnapshot snapshot;
    private UUID assemblyTransactionId;
    private UUID ownerId;
    private RocketFlightData flightData;
    private CompoundTag preservedBlockedData;
    private boolean activeStateLogged;

    public RocketEntity(EntityType<? extends RocketEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void initialize(
            RocketStructureSnapshot snapshot,
            UUID assemblyTransactionId,
            UUID ownerId
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        ResourceLocation dimension = level().dimension().location();
        RocketFlightData initialFlightData = RocketFlightData.initial(
                Objects.requireNonNull(assemblyTransactionId, "assemblyTransactionId"),
                snapshot.stats().fuelCapacity(),
                boundedSeats(snapshot),
                bodyForDimension(dimension),
                dimension,
                snapshot.sourceOrigin(),
                level().getGameTime()
        );
        initializeTransferred(snapshot, assemblyTransactionId, ownerId, initialFlightData);
    }

    public void initializeTransferred(
            RocketStructureSnapshot snapshot,
            UUID assemblyTransactionId,
            UUID ownerId,
            RocketFlightData flightData
    ) {
        if (level().isClientSide) {
            throw new IllegalStateException("Only the server may initialize a RocketEntity");
        }
        if (this.snapshot != null || this.flightData != null || preservedBlockedData != null) {
            throw new IllegalStateException("RocketEntity is already initialized");
        }
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.assemblyTransactionId = Objects.requireNonNull(assemblyTransactionId, "assemblyTransactionId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.flightData = Objects.requireNonNull(flightData, "flightData");
        validateBindings();
        RocketPosition origin = flightData.currentOrigin();
        setPos(origin.x() + 0.5D, origin.y(), origin.z() + 0.5D);
        refreshSyncedData();
    }

    public Optional<RocketStructureSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    public Optional<UUID> assemblyTransactionId() {
        return Optional.ofNullable(assemblyTransactionId);
    }

    public Optional<UUID> ownerId() {
        return Optional.ofNullable(ownerId);
    }

    public Optional<RocketFlightData> flightData() {
        return Optional.ofNullable(flightData);
    }

    public RocketFlightState displayedFlightState() {
        try {
            return RocketFlightState.valueOf(entityData.get(DISPLAYED_FLIGHT_STATE));
        } catch (IllegalArgumentException exception) {
            return RocketFlightState.FAILED_RECOVERABLE;
        }
    }

    public long displayedFuelAmount() {
        return entityData.get(DISPLAYED_FUEL_AMOUNT);
    }

    public void updateFlightData(RocketFlightData updatedFlightData) {
        if (level().isClientSide) {
            throw new IllegalStateException("Only the server may update rocket flight data");
        }
        RocketFlightData previous = flightData;
        flightData = Objects.requireNonNull(updatedFlightData, "updatedFlightData");
        try {
            validateBindings();
        } catch (RuntimeException exception) {
            flightData = previous;
            throw exception;
        }
        refreshSyncedData();
    }

    public boolean operational() {
        return snapshot != null
                && assemblyTransactionId != null
                && ownerId != null
                && flightData != null
                && preservedBlockedData == null;
    }

    public Optional<CompoundTag> preservedBlockedData() {
        return preservedBlockedData == null
                ? Optional.empty()
                : Optional.of(preservedBlockedData.copy());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DISPLAYED_FLIGHT_STATE, RocketFlightState.ASSEMBLED.name());
        entityData.define(DISPLAYED_FUEL_AMOUNT, 0L);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag parent) {
        clearDecodedState();
        if (!parent.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            preservedBlockedData = invalidSentinel("missing entity payload");
            return;
        }
        CompoundTag data = parent.getCompound(DATA_KEY);
        if (!data.contains("schema_version", Tag.TAG_INT)) {
            preservedBlockedData = data.copy();
            return;
        }
        int schema = data.getInt("schema_version");
        if ((schema != 1 && schema != ENTITY_SCHEMA_VERSION)
                || !data.hasUUID("assembly_transaction_id")
                || !data.hasUUID("owner_id")
                || !data.contains("snapshot", Tag.TAG_COMPOUND)) {
            preservedBlockedData = data.copy();
            return;
        }
        RocketSnapshotDecodeResult decodedSnapshot = RocketSnapshotNbtCodec.decode(data.getCompound("snapshot"));
        if (decodedSnapshot.status() != RocketSnapshotDecodeResult.Status.VALID) {
            preservedBlockedData = data.copy();
            return;
        }
        RocketStructureSnapshot candidateSnapshot = decodedSnapshot.snapshot().orElseThrow();
        UUID candidateTransactionId = data.getUUID("assembly_transaction_id");
        RocketFlightData candidateFlightData;
        if (schema == 1) {
            ResourceLocation dimension = level().dimension().location();
            candidateFlightData = RocketFlightData.initial(
                    candidateTransactionId,
                    candidateSnapshot.stats().fuelCapacity(),
                    boundedSeats(candidateSnapshot),
                    bodyForDimension(dimension),
                    dimension,
                    candidateSnapshot.sourceOrigin(),
                    level().getGameTime()
            );
            setPos(
                    candidateSnapshot.sourceOrigin().x() + 0.5D,
                    candidateSnapshot.sourceOrigin().y(),
                    candidateSnapshot.sourceOrigin().z() + 0.5D
            );
        } else {
            if (!data.contains(FLIGHT_DATA_KEY, Tag.TAG_COMPOUND)) {
                preservedBlockedData = data.copy();
                return;
            }
            RocketFlightDecodeResult decodedFlight = RocketFlightNbtCodec.decode(data.getCompound(FLIGHT_DATA_KEY));
            if (decodedFlight.status() != RocketFlightDecodeResult.Status.VALID) {
                preservedBlockedData = data.copy();
                return;
            }
            candidateFlightData = decodedFlight.data().orElseThrow();
        }
        snapshot = candidateSnapshot;
        assemblyTransactionId = candidateTransactionId;
        ownerId = data.getUUID("owner_id");
        flightData = candidateFlightData;
        try {
            validateBindings();
        } catch (RuntimeException exception) {
            clearDecodedState();
            preservedBlockedData = data.copy();
            return;
        }
        refreshSyncedData();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag parent) {
        if (preservedBlockedData != null) {
            parent.put(DATA_KEY, preservedBlockedData.copy());
            return;
        }
        if (!operational()) {
            parent.put(DATA_KEY, invalidSentinel("uninitialized entity"));
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt("schema_version", ENTITY_SCHEMA_VERSION);
        data.putUUID("assembly_transaction_id", assemblyTransactionId);
        data.putUUID("owner_id", ownerId);
        data.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot));
        data.put(FLIGHT_DATA_KEY, RocketFlightNbtCodec.encode(flightData));
        parent.put(DATA_KEY, data);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide && !activeStateLogged) {
            activeStateLogged = true;
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_ROCKET_ENTITY_ACTIVE entity={} operational={} snapshot={} flight_state={} fuel={}",
                    getUUID(),
                    operational(),
                    snapshot().map(RocketStructureSnapshot::contentHash).orElse("none"),
                    flightData().map(RocketFlightData::state).orElse(RocketFlightState.FAILED_RECOVERABLE),
                    flightData().map(data -> data.fuel().amount()).orElse(0L)
            );
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_ROCKET_ENTITY_REMOVED entity={} reason={} operational={}",
                    getUUID(),
                    reason,
                    operational()
            );
        }
        super.remove(reason);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                RocketRuntime.requestDisassembly(serverPlayer, this);
            } else {
                RocketRuntime.openFlightMenu(serverPlayer, this);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return operational()
                && passenger instanceof Player
                && flightData.passengers().assignment(passenger.getUUID()).isPresent()
                && getPassengers().size() < flightData.passengers().seatCapacity();
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger) || flightData == null) {
            return;
        }
        int seat = flightData.passengers().assignment(passenger.getUUID())
                .map(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerSeat::seatIndex)
                .orElse(0);
        double angle = seat * (Math.PI * 2.0D / Math.max(1, flightData.passengers().seatCapacity()));
        double radius = seat == 0 ? 0.0D : 0.35D;
        move.accept(
                passenger,
                getX() + Math.cos(angle) * radius,
                getY() + 1.15D,
                getZ() + Math.sin(angle) * radius
        );
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return position().add(1.5D, 0.0D, 0.0D);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.advancedrocketrycommunity.rocket_flight");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RocketFlightMenu(containerId, inventory, this);
    }

    private void validateBindings() {
        if (snapshot == null || assemblyTransactionId == null || ownerId == null || flightData == null) {
            throw new IllegalStateException("Rocket entity binding is incomplete");
        }
        if (!assemblyTransactionId.equals(flightData.logicalRocketId())) {
            throw new IllegalArgumentException("Flight data logical rocket does not match the assembly transaction");
        }
        if (snapshot.stats().fuelCapacity() != flightData.fuel().capacity()) {
            throw new IllegalArgumentException("Flight fuel capacity does not match snapshot statistics");
        }
        if (boundedSeats(snapshot) != flightData.passengers().seatCapacity()) {
            throw new IllegalArgumentException("Flight passenger capacity does not match snapshot statistics");
        }
        if (!level().dimension().location().equals(flightData.currentDimension())) {
            throw new IllegalArgumentException("Flight current dimension does not match the entity level");
        }
    }

    private void refreshSyncedData() {
        if (flightData == null) {
            entityData.set(DISPLAYED_FLIGHT_STATE, RocketFlightState.FAILED_RECOVERABLE.name());
            entityData.set(DISPLAYED_FUEL_AMOUNT, 0L);
            return;
        }
        entityData.set(DISPLAYED_FLIGHT_STATE, flightData.state().name());
        entityData.set(DISPLAYED_FUEL_AMOUNT, flightData.fuel().amount());
    }

    private void clearDecodedState() {
        snapshot = null;
        assemblyTransactionId = null;
        ownerId = null;
        flightData = null;
        preservedBlockedData = null;
        refreshSyncedData();
    }

    private static int boundedSeats(RocketStructureSnapshot snapshot) {
        return Math.min(snapshot.stats().seatCount(), RocketFlightLimits.MAX_PASSENGERS);
    }

    private static ResourceLocation bodyForDimension(ResourceLocation dimension) {
        if (RocketFlightPlanner.EARTH.dimensionId().equals(dimension)) {
            return RocketFlightPlanner.EARTH.bodyId();
        }
        if (RocketFlightPlanner.MOON.dimensionId().equals(dimension)) {
            return RocketFlightPlanner.MOON.bodyId();
        }
        return dimension;
    }

    private static CompoundTag invalidSentinel(String reason) {
        CompoundTag data = new CompoundTag();
        data.putInt("schema_version", ENTITY_SCHEMA_VERSION);
        data.putString("invalid_reason", reason);
        return data;
    }
}
