package io.github.sunthemoon.advancedrocketrycommunity.rocket.entity;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
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
    private static final int ENTITY_SCHEMA_VERSION = 3;
    private static final String ESCROW_DATA_KEY = "resource_escrow";
    private static final int ENTITY_ENVELOPE_BYTES = 2_048;
    private static final EntityDataAccessor<String> DISPLAYED_FLIGHT_STATE =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> DISPLAYED_FUEL_AMOUNT =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DISPLAYED_SEAT_CAPACITY =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final List<EntityDataAccessor<Optional<UUID>>> DISPLAYED_SEAT_PASSENGERS =
            IntStream.range(0, RocketFlightLimits.MAX_PASSENGERS)
                    .mapToObj(index -> SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.OPTIONAL_UUID))
                    .toList();

    private RocketStructureSnapshot snapshot;
    private UUID assemblyTransactionId;
    private UUID ownerId;
    private RocketFlightData flightData;
    private CompoundTag preservedBlockedData;
    private RocketEscrowSection escrowSection = RocketEscrowSection.neutral();
    private boolean activeStateLogged;
    private long governedObservation;

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
        if (!(level() instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Only the server may initialize a RocketEntity");
        }
        ResourceLocation dimension = level().dimension().location();
        RocketPosition origin = snapshot.sourceOrigin();
        var currentTarget = RocketRuntime.resolveCurrentTarget(
                serverLevel,
                new net.minecraft.core.BlockPos(origin.x(), origin.y(), origin.z())
        ).orElseThrow(() -> new IllegalStateException("Rocket body context is unresolved"));
        RocketFlightData initialFlightData = RocketFlightData.initial(
                Objects.requireNonNull(assemblyTransactionId, "assemblyTransactionId"),
                snapshot.stats().fuelCapacity(),
                boundedSeats(snapshot),
                currentTarget instanceof io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget.BodySurface surface
                        ? surface.bodyId() : currentTarget instanceof io.github.sunthemoon.advancedrocketrycommunity
                        .travel.model.TravelTarget.Station station
                        ? stationOrbit(serverLevel, station.instanceId(), dimension) : bodyForDimension(dimension),
                dimension,
                origin,
                level().getGameTime(),
                currentTarget
        );
        if (snapshot.propulsion().isPresent()) {
            initialFlightData = initialFlightData.withFuel(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.empty(
                            snapshot.propulsion().orElseThrow()), level().getGameTime());
        }
        initializeTransferred(snapshot, assemblyTransactionId, ownerId, initialFlightData);
    }

    /** ADR-044 §5: a rocket built on a station records the station's current orbit body. */
    private static ResourceLocation stationOrbit(ServerLevel level, java.util.UUID stationId,
                                                 ResourceLocation dimension) {
        return io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData
                .get(level.getServer()).find(stationId)
                .map(io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState::orbitBody)
                .orElseGet(() -> bodyForDimension(dimension));
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
        this.flightData = normalizeFlightData(Objects.requireNonNull(flightData, "flightData"));
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

    public RocketEscrowSection escrowSection() { return escrowSection; }
    public long governedObservation() { return governedObservation; }

    /** The ordinary reconciliation permit publishes native resources and their wrapper as one action. */
    public void applyEscrowFill(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReconciliationAction.Permit permit) {
        var change = Objects.requireNonNull(permit).consume(this);
        var previousFlight = flightData; var previousSection = escrowSection;
        flightData = change.flight(); escrowSection = change.wrapper();
        try { validateBindings(); }
        catch (RuntimeException failure) { flightData = previousFlight; escrowSection = previousSection; throw failure; }
        refreshSyncedData();
    }

    /** Only F4's checked, one-use permit can initialize a new bound destination. */
    public void initializeGovernedDestination(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDestinationAction.Permit permit) {
        var record = Objects.requireNonNull(permit).consume(this);
        var envelope = record.nativeTransfer().orElseThrow().destinationEnvelope(record);
        setUUID(record.nativeTransfer().orElseThrow().armed().destination());
        initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), record.ownerId(), record.destinationFlightData());
        escrowSection = RocketEscrowSection.decode(envelope.getCompound("wrapper"));
        var pos = envelope.getList("pos", net.minecraft.nbt.Tag.TAG_DOUBLE);
        var motion = envelope.getList("motion", net.minecraft.nbt.Tag.TAG_DOUBLE);
        setPos(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
        setDeltaMovement(motion.getDouble(0), motion.getDouble(1), motion.getDouble(2));
        setNoGravity(envelope.getBoolean("no_gravity")); noPhysics = envelope.getBoolean("no_physics");
        setOnGround(envelope.getBoolean("on_ground")); setInvulnerable(envelope.getBoolean("invulnerable"));
        setYRot(envelope.getFloat("yaw")); setXRot(envelope.getFloat("pitch"));
    }

    public void moveGovernedSource(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedSourceFlight.MotionPermit permit) {
        var position = Objects.requireNonNull(permit).consume(this);
        setPos(position.x, position.y, position.z);
    }

    /** Descent and landing retain fuel, manifest and wrapper under their own checked action. */
    public void advanceGovernedArrival(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedArrival.Permit permit) {
        var change = Objects.requireNonNull(permit).consume(this);
        RocketFlightData previous = flightData;
        flightData = change.flight();
        try { validateBindings(); }
        catch (RuntimeException failure) { flightData = previous; throw failure; }
        setPos(change.position().x, change.position().y, change.position().z);
        if (flightData != previous) { refreshSyncedData(); }
    }

    public void pruneSettledEscrow() {
        escrowSection = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowSettlementPruning.rocket(this, escrowSection);
    }
    public void pruneSettledEscrow(String digest, java.util.Set<UUID> members) {
        escrowSection = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowSettlementPruning.rocket(this, escrowSection, digest, members);
    }
    public void pruneReconciledEscrow(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReconciliationPruning.Permit permit) {
        escrowSection = Objects.requireNonNull(permit).consume(this);
    }
    public void normalizeSettlementEscrow(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowSettlementNormalization.Permit permit) {
        escrowSection = Objects.requireNonNull(permit).consume(this);
    }

    /** A governed source transition retains its native fuel and exact escrow wrapper. */
    public void advanceGovernedSource(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedSourceAction.Permit permit) {
        RocketFlightData candidate = Objects.requireNonNull(permit).consume(this);
        RocketFlightData previous = flightData;
        flightData = candidate;
        try { validateBindings(); }
        catch (RuntimeException failure) { flightData = previous; throw failure; }
        refreshSyncedData();
    }

    /** Exact present-source resume/return. Outcome and resource release still require checked F6-F8. */
    public void recoverGovernedSource(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedSourceRecovery.Permit permit) {
        var change = Objects.requireNonNull(permit).consume(this);
        RocketFlightData previous = flightData;
        flightData = change.flight();
        try { validateBindings(); }
        catch (RuntimeException failure) { flightData = previous; throw failure; }
        setPos(change.position().x, change.position().y, change.position().z);
        refreshSyncedData();
    }

    /** A post-F4 return requires its own durable absence permit, never a legacy recovery exemption. */
    public void returnGovernedSource(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedReturnAction.Permit permit) {
        var change = Objects.requireNonNull(permit).consume(this);
        RocketFlightData previous = flightData;
        flightData = change.flight();
        try { validateBindings(); }
        catch (RuntimeException failure) { flightData = previous; throw failure; }
        setPos(change.position().x, change.position().y, change.position().z);
        refreshSyncedData();
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
        RocketFlightData candidate = normalizeFlightData(Objects.requireNonNull(updatedFlightData, "updatedFlightData"));
        if (RocketEscrowGate.flightFrozen(this) && !RocketEscrowGate.terminalFuelUpdate(this, candidate)) {
            throw new IllegalStateException("Rocket flight state requires an exact escrow action permit");
        }
        boolean resourceChanged = previous != null && !previous.fuel().sameResources(candidate.fuel());
        if (resourceChanged && !RocketEscrowGate.resourcesIdle(this)) {
            throw new IllegalStateException("Rocket resources are protected by escrow");
        }
        RocketEscrowSection nextSection = resourceChanged ? escrowSection.afterNativeResourceMutation() : escrowSection;
        flightData = candidate;
        try {
            validateBindings();
        } catch (RuntimeException exception) {
            flightData = previous;
            throw exception;
        }
        escrowSection = nextSection;
        refreshSyncedData();
    }

    private RocketFlightData normalizeFlightData(RocketFlightData candidate) {
        if (candidate.schemaVersion() != 1) {
            return candidate;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Legacy flight data requires server context migration");
        }
        return RocketRuntime.migrateLegacyFlightData(serverLevel, candidate)
                .orElseThrow(() -> new IllegalStateException(
                        "Legacy flight location cannot be resolved safely"
                ));
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
                : Optional.of(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources
                        .preserve(preservedBlockedData, io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits.MAX_TOTAL_NBT_BYTES
                                + RocketFlightLimits.MAX_FLIGHT_DATA_NBT_BYTES + ENTITY_ENVELOPE_BYTES));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DISPLAYED_FLIGHT_STATE, RocketFlightState.ASSEMBLED.name());
        entityData.define(DISPLAYED_FUEL_AMOUNT, 0L);
        entityData.define(DISPLAYED_SEAT_CAPACITY, 0);
        DISPLAYED_SEAT_PASSENGERS.forEach(slot -> entityData.define(slot, Optional.empty()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag parent) {
        governedObservation++;
        boolean live = isAddedToWorld();
        RocketEscrowSection retained = escrowSection;
        if (live) { io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowAudit.observe(this); }
        // Vanilla has already applied base fields (including Pos). This protects the mod payload,
        // not command success or relocation; the coordinator must separately detect relocation.
        if (live && !RocketEscrowGate.idle(this)) { return; }
        clearDecodedState();
        if (!parent.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            preservedBlockedData = invalidSentinel("missing entity payload");
            return;
        }
        CompoundTag data = parent.getCompound(DATA_KEY);
        if (!io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.bounded(data,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits.MAX_TOTAL_NBT_BYTES
                        + RocketFlightLimits.MAX_FLIGHT_DATA_NBT_BYTES + ENTITY_ENVELOPE_BYTES)) {
            preservedBlockedData = data;
            return;
        }
        if (!data.contains("schema_version", Tag.TAG_INT)) {
            preservedBlockedData = data.copy();
            return;
        }
        int schema = data.getInt("schema_version");
        var keys = schema == 1 ? java.util.Set.of("schema_version", "assembly_transaction_id", "owner_id", "snapshot")
                : schema == 2 ? java.util.Set.of("schema_version", "assembly_transaction_id", "owner_id", "snapshot", FLIGHT_DATA_KEY)
                : java.util.Set.of("schema_version", "assembly_transaction_id", "owner_id", "snapshot", FLIGHT_DATA_KEY, ESCROW_DATA_KEY);
        if (!data.getAllKeys().equals(keys)) {
            preservedBlockedData = data.copy();
            return;
        }
        if ((schema < 1 || schema > ENTITY_SCHEMA_VERSION)
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
            if (candidateFlightData.schemaVersion() == 1) {
                if (!(level() instanceof ServerLevel serverLevel)) {
                    preservedBlockedData = data.copy();
                    return;
                }
                candidateFlightData = RocketRuntime.migrateLegacyFlightData(serverLevel, candidateFlightData)
                        .orElse(null);
                if (candidateFlightData == null) {
                    preservedBlockedData = data.copy();
                    return;
                }
            }
        }
        snapshot = candidateSnapshot;
        assemblyTransactionId = candidateTransactionId;
        ownerId = data.getUUID("owner_id");
        flightData = candidateFlightData;
        try {
            if (live) {
                escrowSection = retained;
            } else if (schema == ENTITY_SCHEMA_VERSION) {
                if (!data.contains(ESCROW_DATA_KEY, Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("Missing rocket escrow section");
                }
                escrowSection = RocketEscrowSection.decode(data.getCompound(ESCROW_DATA_KEY));
            }
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
        governedObservation++;
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowAudit.observe(this);
        if (preservedBlockedData != null) {
            parent.put(DATA_KEY, io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources
                    .preserve(preservedBlockedData, io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits.MAX_TOTAL_NBT_BYTES
                            + RocketFlightLimits.MAX_FLIGHT_DATA_NBT_BYTES + ENTITY_ENVELOPE_BYTES));
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
        data.put(ESCROW_DATA_KEY, escrowSection.encode());
        parent.put(DATA_KEY, data);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowAudit.observe(this);
        setNoGravity(true);
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
            var exchanged = io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketPropulsionInteraction
                    .exchange(serverPlayer, this, hand);
            if (exchanged != InteractionResult.PASS) { return exchanged; }
            if (player.isShiftKeyDown()) {
                if (hand == InteractionHand.MAIN_HAND) {
                    RocketRuntime.requestDisassembly(serverPlayer, this);
                }
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
    public boolean hasExactlyOnePlayerPassenger() {
        // Vanilla uses this predicate to move vehicle ownership into player NBT
        // and unload it on logout. Rockets belong to the world and flight journal.
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
        if (!hasPassenger(passenger) || (!level().isClientSide && flightData == null)) {
            return;
        }
        int seat = 0;
        for (int index = 0; index < DISPLAYED_SEAT_PASSENGERS.size(); index++) {
            if (entityData.get(DISPLAYED_SEAT_PASSENGERS.get(index)).filter(passenger.getUUID()::equals).isPresent()) {
                seat = index;
                break;
            }
        }
        RocketPassengerPosition offset = RocketPassengerPosition.forSeat(seat, entityData.get(DISPLAYED_SEAT_CAPACITY));
        move.accept(
                passenger,
                getX() + offset.x(),
                getY() + offset.y(),
                getZ() + offset.z()
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
    public void kill() {
        if (level().isClientSide || RocketEscrowGate.idle(this)) { super.kill(); }
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
        if (!snapshot.propulsion().equals(flightData.fuel().propulsion().map(value -> value.descriptor()))) {
            throw new IllegalArgumentException("Flight propulsion descriptor differs from snapshot");
        }
        flightData.fuel().propulsion().ifPresent(value -> {
            for (var bank : List.of(value.primary(), value.oxidizer())) {
                if (bank.fluidId() != null && (!net.minecraftforge.registries.ForgeRegistries.FLUIDS.containsKey(bank.fluidId())
                        || !net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(bank.fluidId()).defaultFluidState().isSource())) {
                    throw new IllegalArgumentException("Unknown native propulsion Fluid");
                }
            }
        });
        if (boundedSeats(snapshot) != flightData.passengers().seatCapacity()) {
            throw new IllegalArgumentException("Flight passenger capacity does not match snapshot statistics");
        }
        if (!level().dimension().location().equals(flightData.currentDimension())) {
            throw new IllegalArgumentException("Flight current dimension does not match the entity level");
        }
    }

    private void refreshSyncedData() {
        entityData.set(DISPLAYED_SEAT_CAPACITY, flightData == null ? 0 : flightData.passengers().seatCapacity());
        List<Optional<UUID>> seats = new java.util.ArrayList<>(
                java.util.Collections.nCopies(RocketFlightLimits.MAX_PASSENGERS, Optional.empty()));
        if (flightData != null) {
            flightData.passengers().assignments().forEach(seat ->
                    seats.set(seat.seatIndex(), Optional.of(seat.passengerId())));
        }
        for (int index = 0; index < DISPLAYED_SEAT_PASSENGERS.size(); index++) {
            entityData.set(DISPLAYED_SEAT_PASSENGERS.get(index), seats.get(index));
        }
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
        escrowSection = RocketEscrowSection.neutral();
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
