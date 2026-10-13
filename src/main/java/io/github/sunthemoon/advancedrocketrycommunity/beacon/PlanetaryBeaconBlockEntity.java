package io.github.sunthemoon.advancedrocketrycommunity.beacon;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconIndex;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconLimits;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence.BeaconIndexSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence.BeaconStateCodec;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The planetary beacon controller (ADR-065 section 7). It owns the beacon's identity, owner, name and enabled switch,
 * and mirrors them into the server's index on lifecycle events: the first validation after loading, each structure
 * validation, and each change by its owner or an operator. Its index record is active only while the full loaded
 * structure is formed, the beacon is enabled and it has an owner; unforming or disabling marks it inactive, and
 * unloading leaves the last known record. Removal deletes its own record only.
 */
public final class PlanetaryBeaconBlockEntity extends BlockEntity {
    /** The outcome of the last index reconciliation. */
    enum Registration {
        PENDING, REGISTERED, NO_OWNER, INDEX_UNAVAILABLE, DUPLICATE, BODY_UNAVAILABLE, LIMIT
    }

    private BeaconStateCodec.State state = BeaconStateCodec.State.fresh();
    @Nullable
    private Tag retained;
    private final BeaconStructure structure;
    private Registration registration = Registration.PENDING;
    private boolean reconcile = true;

    public PlanetaryBeaconBlockEntity(BlockPos position, BlockState blockState) {
        super(ModBlockEntities.PLANETARY_BEACON.get(), position, blockState);
        structure = new BeaconStructure(blockState.getBlock());
    }

    public static void serverTick(Level level, BlockPos position, BlockState blockState,
                                  PlanetaryBeaconBlockEntity beacon) {
        if (level instanceof ServerLevel server) {
            beacon.tick(server, blockState);
        }
    }

    private void tick(ServerLevel level, BlockState blockState) {
        if (retained != null) {
            return;
        }
        Optional<BeaconServices> services = BeaconRuntime.services();
        if (services.isEmpty()) {
            return;
        }
        boolean wasFormed = formed();
        structure.tick(level, worldPosition, blockState.getValue(PlanetaryBeaconBlock.FACING), state.beaconId(),
                services.get(), level.getServer().getTickCount());
        boolean validated = structure.consumeValidated();
        if (formed() && !wasFormed) {
            state = state.nextGeneration();
            setChanged();
        }
        if ((validated || reconcile) && structure.status() != BeaconStructure.Status.PENDING) {
            reconcile = false;
            reconcile(level, services.get());
        }
    }

    /**
     * Mirrors this beacon into the index. Refusals are reported to the owner and leave the index unchanged, except
     * that a refused replacement of this beacon's own record still takes its activity and generation.
     */
    private void reconcile(ServerLevel level, BeaconServices services) {
        if (structure.status() == BeaconStructure.Status.PENDING) {
            // Loading (or controls used before validation) must not overwrite the last known activity.
            reconcile = true;
            return;
        }
        if (state.owner().isEmpty()) {
            registration = Registration.NO_OWNER;
            return;
        }
        MinecraftServer server = level.getServer();
        BeaconIndexSavedData index = BeaconIndexSavedData.get(server);
        if (!index.operational()) {
            registration = Registration.INDEX_UNAVAILABLE;
            return;
        }
        String levelId = level.dimension().location().toString();
        Optional<BeaconRecord> existing = index.get(state.beaconId());
        boolean here = existing.filter(record -> record.at(levelId, worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ())).isPresent();
        if (existing.isPresent() && !here && !BeaconWorld.stale(server, existing.get())) {
            // A copy of a beacon that still stands elsewhere, or whose position cannot be checked now.
            registration = Registration.DUPLICATE;
            return;
        }
        // A missing catalog or body pauses resolution: a registered beacon keeps its last body.
        Optional<String> body = services.body(server, level.dimension(), worldPosition)
                .or(() -> here ? existing.map(BeaconRecord::body) : Optional.empty());
        if (body.isEmpty()) {
            registration = Registration.BODY_UNAVAILABLE;
            return;
        }
        BeaconRecord desired = new BeaconRecord(state.beaconId(), state.owner().get(), body.get(), levelId,
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), state.generation(),
                state.enabled() && formed(), state.name());
        // A refused rename or body change still records whether this beacon is active now.
        Optional<BeaconIndex.Admission> admission = index.update(desired);
        if (admission.isEmpty()) {
            registration = Registration.INDEX_UNAVAILABLE;
        } else if (admission.get() == BeaconIndex.Admission.OWNER_MISMATCH) {
            registration = Registration.DUPLICATE;
        } else {
            registration = admission.get().stored() ? Registration.REGISTERED : Registration.LIMIT;
        }
    }

    /** Placement by a connected player sets the owner and the item's name, if it is a valid beacon name. */
    void placedBy(UUID owner, String name) {
        if (retained != null) {
            return;
        }
        state = state.withOwner(owner).withName(BeaconLimits.validName(name) ? name : "");
        reconcile = true;
        setChanged();
    }

    /** The owner's or an operator's switch; reconciles at once so the reply is current. */
    BeaconStatus toggle(ServerLevel level) {
        state = state.withEnabled(!state.enabled());
        setChanged();
        BeaconRuntime.services().ifPresent(services -> reconcile(level, services));
        return status();
    }

    BeaconStatus rename(ServerLevel level, String name) {
        state = state.withName(name);
        setChanged();
        BeaconRuntime.services().ifPresent(services -> reconcile(level, services));
        return status();
    }

    /** Called once when the block is removed (not on chunk unload): deletes this beacon's own record. */
    void removedFromWorld(ServerLevel level) {
        BeaconRuntime.services().ifPresent(structure::untrack);
        if (retained != null) {
            return;
        }
        BeaconIndexSavedData index = BeaconIndexSavedData.get(level.getServer());
        String levelId = level.dimension().location().toString();
        if (index.get(state.beaconId()).filter(record -> record.at(levelId, worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ())).isPresent()) {
            index.remove(state.beaconId());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        BeaconRuntime.services().ifPresent(structure::untrack);
    }

    public BeaconStatus status() {
        if (retained != null) {
            return BeaconStatus.INVALID_DATA;
        }
        switch (registration) {
            case NO_OWNER -> {
                return BeaconStatus.NO_OWNER;
            }
            case INDEX_UNAVAILABLE -> {
                return BeaconStatus.INDEX_UNAVAILABLE;
            }
            case DUPLICATE -> {
                return BeaconStatus.DUPLICATE;
            }
            case BODY_UNAVAILABLE -> {
                return BeaconStatus.BODY_UNAVAILABLE;
            }
            case LIMIT -> {
                return BeaconStatus.LIMIT;
            }
            default -> {
            }
        }
        if (!state.enabled()) {
            return BeaconStatus.DISABLED;
        }
        return switch (structure.status()) {
            case PENDING -> BeaconStatus.PENDING;
            case UNFORMED -> BeaconStatus.UNFORMED;
            case UNLOADED -> BeaconStatus.UNLOADED;
            case NO_PATTERN -> BeaconStatus.NO_PATTERN;
            case FORMED -> registration == Registration.REGISTERED ? BeaconStatus.ACTIVE : BeaconStatus.PENDING;
        };
    }

    /** Formed, enabled, owned and registered: what the finder may show as a live marker. */
    public boolean live() {
        return status() == BeaconStatus.ACTIVE;
    }

    public boolean formed() {
        return structure.status() == BeaconStructure.Status.FORMED;
    }

    /** Empty while the saved data is kept unreadable. */
    public Optional<UUID> beaconId() {
        return retained == null ? Optional.of(state.beaconId()) : Optional.empty();
    }

    public Optional<UUID> owner() {
        return retained == null ? state.owner() : Optional.empty();
    }

    public String name() {
        return state.name();
    }

    public boolean enabled() {
        return state.enabled();
    }

    public long generation() {
        return state.generation();
    }

    /** Item NBT cannot forge the identity or owner: only a creative operator's {@code BlockEntityTag} is loaded. */
    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        retained = null;
        if (tag.contains(BeaconStateCodec.KEY)) {
            Tag raw = tag.get(BeaconStateCodec.KEY);
            try {
                state = BeaconStateCodec.decode(raw);
            } catch (IllegalArgumentException unreadable) {
                retained = raw;
            }
        }
        reconcile = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(BeaconStateCodec.KEY, retained != null ? retained.copy() : BeaconStateCodec.encode(state));
    }
}
