package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameNbt;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ADR-054 section 2: the root every endgame block entity keeps under {@code endgame}: {@code schema_version},
 * {@code device_id}, an optional {@code owner_id} and the device's own fields.
 * <ul>
 * <li>A placement by a connected, non-fake player creates a new device ID and makes that player the owner; any
 * other placement creates an unowned, inert device. A block entity without a root gets a new ID on its first
 * load. The ID is never carried in an item.</li>
 * <li>A root with an unknown schema, an unknown, missing or malformed field, or a value outside its bounds is
 * quarantined: the device is inert, the root is re-saved unchanged, one {@code ARCE_ENDGAME_DEVICE_QUARANTINED} line
 * is written, its buffers are not dropped and only operators can break it.</li>
 * </ul>
 */
public abstract class EndgameDeviceBlockEntity extends BlockEntity {
    private static final Set<String> IDENTITY_KEYS = Set.of(EndgameDeviceTags.SCHEMA_VERSION,
            EndgameDeviceTags.DEVICE_ID, EndgameDeviceTags.OWNER_ID);

    @Nullable
    private UUID deviceId;
    @Nullable
    private UUID ownerId;
    @Nullable
    private Tag quarantinedRoot;
    @Nullable
    private String quarantineReason;
    private boolean quarantineLogged;

    protected EndgameDeviceBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
    }

    public abstract EndgameSystem system();

    /** The schema of this device's root; a root with another schema is quarantined. */
    protected abstract int rootSchema();

    /** The device's own root fields; any other field quarantines the root. */
    protected abstract Set<String> stateKeys();

    /** Reads the device's own fields strictly; throws {@link IllegalArgumentException} on any defect. */
    protected abstract void readState(CompoundTag root);

    protected abstract void writeState(CompoundTag root);

    /** Puts the device's own fields into their fresh state (a new root, or before a strict read). */
    protected abstract void resetState();

    /** Called once when a new device ID is created (a placement or a block entity without a root). */
    protected void onNewIdentity() {
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        quarantinedRoot = null;
        quarantineReason = null;
        quarantineLogged = false;
        if (!tag.contains(EndgameDeviceTags.ROOT)) {
            deviceId = null;
            ownerId = null;
            resetState();
            return;
        }
        Tag raw = tag.get(EndgameDeviceTags.ROOT);
        try {
            if (!(raw instanceof CompoundTag root)) {
                throw new IllegalArgumentException("The endgame root is not a compound");
            }
            Set<String> allowed = new HashSet<>(IDENTITY_KEYS);
            allowed.addAll(stateKeys());
            EndgameNbt.requireKeys(root, allowed, "Endgame device root");
            if (EndgameNbt.requireInt(root, EndgameDeviceTags.SCHEMA_VERSION) != rootSchema()) {
                throw new IllegalArgumentException("Unknown endgame device schema");
            }
            UUID id = EndgameNbt.requireUuid(root, EndgameDeviceTags.DEVICE_ID);
            UUID owner = root.contains(EndgameDeviceTags.OWNER_ID)
                    ? EndgameNbt.requireUuid(root, EndgameDeviceTags.OWNER_ID) : null;
            resetState();
            readState(root);
            deviceId = id;
            ownerId = owner;
        } catch (RuntimeException exception) {
            deviceId = null;
            ownerId = null;
            resetState();
            quarantinedRoot = raw.copy();
            quarantineReason = exception.getMessage() == null ? exception.getClass().getSimpleName()
                    : exception.getMessage();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (quarantinedRoot != null) {
            tag.put(EndgameDeviceTags.ROOT, quarantinedRoot.copy());
            return;
        }
        ensureIdentity();
        CompoundTag root = new CompoundTag();
        root.putInt(EndgameDeviceTags.SCHEMA_VERSION, rootSchema());
        root.put(EndgameDeviceTags.DEVICE_ID, NbtUtils.createUUID(deviceId));
        if (ownerId != null) {
            root.put(EndgameDeviceTags.OWNER_ID, NbtUtils.createUUID(ownerId));
        }
        writeState(root);
        tag.put(EndgameDeviceTags.ROOT, root);
    }

    /** Render state only; the root never goes to clients (ADR-054 section 4). */
    @Override
    public CompoundTag getUpdateTag() {
        return new CompoundTag();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) {
            return;
        }
        if (quarantinedRoot != null) {
            if (!quarantineLogged) {
                quarantineLogged = true;
                AdvancedRocketryCommunity.LOGGER.warn("ARCE_ENDGAME_DEVICE_QUARANTINED system={} level={} pos={} {} {}"
                                + " reason={}", system().id(), level.dimension().location(), worldPosition.getX(),
                        worldPosition.getY(), worldPosition.getZ(), quarantineReason);
            }
            return;
        }
        if (deviceId == null) {
            ensureIdentity();
            setChanged();
        }
    }

    /**
     * A placement: a new device ID, owned by the placer when it is a connected player that is not a FakePlayer.
     * A quarantined root (only an operator can place one from an item) stays as it is.
     */
    public void placedBy(@Nullable Entity placer) {
        if (quarantinedRoot != null) {
            return;
        }
        deviceId = UUID.randomUUID();
        ownerId = eligibleOwner(placer).orElse(null);
        resetState();
        onNewIdentity();
        setChanged();
    }

    public static Optional<UUID> eligibleOwner(@Nullable Entity placer) {
        if (placer instanceof ServerPlayer player && !(placer instanceof FakePlayer) && player.connection != null
                && player.getServer() != null && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            return Optional.of(player.getUUID());
        }
        return Optional.empty();
    }

    /** Operator assignment (ADR-054 section 13); a quarantined root cannot be given an owner. */
    public boolean assignOwner(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        if (quarantinedRoot != null) {
            return false;
        }
        ensureIdentity();
        ownerId = owner;
        setChanged();
        return true;
    }

    public Optional<UUID> deviceId() {
        return Optional.ofNullable(deviceId);
    }

    public Optional<UUID> ownerId() {
        return Optional.ofNullable(ownerId);
    }

    public boolean quarantined() {
        return quarantinedRoot != null;
    }

    public Optional<String> quarantineReason() {
        return Optional.ofNullable(quarantineReason);
    }

    /** Operator output of {@code /arce endgame device inspect} (ADR-054 section 13). */
    public String describe() {
        return "system=" + system().id() + " device=" + deviceId().map(UUID::toString).orElse("-") + " owner="
                + ownerId().map(UUID::toString).orElse("-") + (quarantined() ? " quarantined reason=" + quarantineReason
                : " " + describeState());
    }

    /** The device's own status fields for {@link #describe()}. */
    protected abstract String describeState();

    /** Neither quarantined nor unowned. */
    public boolean usable() {
        return quarantinedRoot == null && ownerId != null;
    }

    private void ensureIdentity() {
        if (deviceId == null && quarantinedRoot == null) {
            deviceId = UUID.randomUUID();
            resetState();
            onNewIdentity();
        }
    }
}
