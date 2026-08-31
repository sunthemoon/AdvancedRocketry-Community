package io.github.sunthemoon.advancedrocketrycommunity.rocket.entity;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Thin same-dimension entity. Snapshot transactions and rendering remain separate services. */
public final class RocketEntity extends Entity {
    private static final String DATA_KEY = "RocketEntityData";
    private static final int ENTITY_SCHEMA_VERSION = 1;

    private RocketStructureSnapshot snapshot;
    private UUID assemblyTransactionId;
    private UUID ownerId;
    private CompoundTag preservedBlockedData;

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
        if (level().isClientSide) {
            throw new IllegalStateException("Only the server may initialize a RocketEntity");
        }
        if (this.snapshot != null || preservedBlockedData != null) {
            throw new IllegalStateException("RocketEntity is already initialized");
        }
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.assemblyTransactionId = Objects.requireNonNull(assemblyTransactionId, "assemblyTransactionId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        setPos(
                snapshot.sourceOrigin().x() + 0.5D,
                snapshot.sourceOrigin().y(),
                snapshot.sourceOrigin().z() + 0.5D
        );
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

    public boolean operational() {
        return snapshot != null
                && assemblyTransactionId != null
                && ownerId != null
                && preservedBlockedData == null;
    }

    public Optional<CompoundTag> preservedBlockedData() {
        return preservedBlockedData == null
                ? Optional.empty()
                : Optional.of(preservedBlockedData.copy());
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag parent) {
        snapshot = null;
        assemblyTransactionId = null;
        ownerId = null;
        preservedBlockedData = null;
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
        if (schema != ENTITY_SCHEMA_VERSION
                || !data.hasUUID("assembly_transaction_id")
                || !data.hasUUID("owner_id")
                || !data.contains("snapshot", Tag.TAG_COMPOUND)) {
            preservedBlockedData = data.copy();
            return;
        }
        RocketSnapshotDecodeResult decoded = RocketSnapshotNbtCodec.decode(data.getCompound("snapshot"));
        if (decoded.status() != RocketSnapshotDecodeResult.Status.VALID) {
            preservedBlockedData = data.copy();
            return;
        }
        snapshot = decoded.snapshot().orElseThrow();
        assemblyTransactionId = data.getUUID("assembly_transaction_id");
        ownerId = data.getUUID("owner_id");
        setPos(
                snapshot.sourceOrigin().x() + 0.5D,
                snapshot.sourceOrigin().y(),
                snapshot.sourceOrigin().z() + 0.5D
        );
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
        parent.put(DATA_KEY, data);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            RocketRuntime.requestDisassembly(serverPlayer, this);
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
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private static CompoundTag invalidSentinel(String reason) {
        CompoundTag data = new CompoundTag();
        data.putInt("schema_version", ENTITY_SCHEMA_VERSION);
        data.putString("invalid_reason", reason);
        return data;
    }
}
