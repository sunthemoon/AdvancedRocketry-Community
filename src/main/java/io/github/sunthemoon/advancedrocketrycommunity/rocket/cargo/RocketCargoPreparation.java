package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotPreparation.Work;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotWitness;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

/** One transient reconstruction, never a persisted operation or admission authority. */
public final class RocketCargoPreparation {
    public enum State { PREPARING, READY, DISCARDED }
    private final UUID logical;
    private final Thread owner = Thread.currentThread();
    private RocketStructureSnapshot before;
    private EscrowCargoPayload delta;
    /** Exactly one of {@code delta} (Fluid) and {@code item} is set until close. */
    private EscrowItemCargoPayload item;
    private final boolean load;
    private RocketSnapshotPreparation snapshot;
    private CompoundTag encoded;
    private int encodedSize;
    private State state = State.PREPARING;
    private int stage;
    private long bytes;
    private long visits;
    private RocketCargoSelection.Status failure;

    RocketCargoPreparation(UUID logical, RocketStructureSnapshot before, EscrowCargoPayload delta, boolean load) {
        this.logical = java.util.Objects.requireNonNull(logical);
        this.before = java.util.Objects.requireNonNull(before);
        this.delta = java.util.Objects.requireNonNull(delta); this.load = load;
    }
    RocketCargoPreparation(UUID logical, RocketStructureSnapshot before, EscrowItemCargoPayload item, boolean load) {
        this.logical = java.util.Objects.requireNonNull(logical);
        this.before = java.util.Objects.requireNonNull(before);
        this.item = java.util.Objects.requireNonNull(item); this.load = load;
    }
    public UUID logical() { return logical; }
    public UUID snapshotId() { return before().snapshotId(); }
    public String beforeHash() { return before().contentHash(); }
    public RocketStructureSnapshot before() { requireLive(); return before; }
    public int blockIndex() { return snapshot == null ? 0 : snapshot.blockIndex(); }
    public long chargedBytes() { return bytes; }
    public long chargedVisits() { return visits; }
    public State state() { return state; }
    public java.util.Optional<RocketCargoSelection.Status> failure() { return java.util.Optional.ofNullable(failure); }
    /** Excludes the pre-existing immutable snapshot; never includes a whole canonical hash buffer. */
    public long retainedNativeBytes() {
        return (snapshot == null ? 0 : snapshot.additionalPayloadBytes())
                + (encoded == null ? 0 : encodedSize);
    }
    public boolean matches(EscrowCargoPayload expected, boolean loading) {
        requireLive(); return load == loading && expected.equals(delta);
    }
    public boolean matches(EscrowItemCargoPayload expected, boolean loading) {
        requireLive(); return load == loading && expected.equals(item);
    }
    public RocketStructureSnapshot after() {
        if (state != State.READY) { throw new IllegalStateException("Cargo preparation is not ready"); }
        return snapshot.result();
    }

    /** A caller cannot accumulate tick credit: every operation is performed inside its actual tick reservation. */
    boolean advance(long tick, RocketCargoWorkBudget budget) {
        requireLive();
        try { return advanceSteps(tick, budget); }
        catch (RocketItemCargoMutation.Refusal refused) { failure = refused.status(); }
        catch (RocketSnapshotException invalid) {
            failure = switch (invalid.code()) {
                case SNAPSHOT_DATA_TOO_LARGE, BLOCK_ENTITY_DATA_TOO_LARGE -> RocketCargoSelection.Status.CARGO_TOO_LARGE;
                default -> RocketCargoSelection.Status.INVALID_DATA;
            };
        } catch (IllegalArgumentException | ArithmeticException | java.util.NoSuchElementException invalid) {
            failure = RocketCargoSelection.Status.INVALID_DATA;
        }
        close(); return true;
    }

    private boolean advanceSteps(long tick, RocketCargoWorkBudget budget) {
        if (!budget.claim(tick, this)) { return false; }
        while (state == State.PREPARING) {
            long bound = stage == 0 ? 1_048_576 : stage == 1 ? snapshot.nextByteBound()
                    : stage == 2 ? RocketCargoWorkBudget.MAX_BYTES : 8L * 1_048_576;
            int visitBound = stage == 0 ? 2 * before.blocks().size() : stage == 1 ? snapshot.nextVisits()
                    : 8 * before.blocks().size();
            if (!budget.perform(tick, bound, visitBound, false, () -> {
                Work work = step(); bytes += work.bytes(); visits += work.visits(); return work;
            })) { return false; }
        }
        return true;
    }

    private Work step() {
        if (stage == 0) {
            if (item != null) { return itemStep(); }
            delta.cursor().requirePayload(before);
            if (before.propulsion().isEmpty() || !PressurizedTankRocketAdapter.ID.toString().equals(delta.cursor().adapter())) {
                throw new IllegalArgumentException("Cargo requires a modern tank snapshot");
            }
            int index = index(delta.cursor());
            var block = before.blocks().get(index);
            if (!block.state().blockId().equals(PressurizedTankRocketAdapter.TYPE)) {
                throw new IllegalArgumentException("Cargo cursor does not name a tank block");
            }
            var old = block.blockEntityPayload().orElseThrow();
            var fluid = PressurizedTankRocketAdapter.fluid(old.data());
            var moved = FluidStack.loadFluidStackFromNBT(delta.fluid());
            var replacement = PressurizedTankRocketAdapter.payload(RocketCargoMutation.change(fluid, moved, load));
            snapshot = new RocketSnapshotPreparation(before, index, replacement);
            stage++;
            // Native tank validation performs bounded defensive copies, native round trips and signatures.
            // Reserve all of those passes at the actual input/output lengths, including framing headroom.
            return new Work(32L * (old.uncompressedBytes() + replacement.uncompressedBytes()
                    + RocketNbtSize.uncompressedBytes(delta.fluid()) + 256), 2 * before.blocks().size());
        }
        if (stage == 1) {
            var work = snapshot.step();
            if (snapshot.done()) { stage++; }
            return work;
        }
        if (stage == 2) {
            encoded = RocketSnapshotNbtCodec.encode(snapshot.result());
            encodedSize = RocketNbtSize.uncompressedBytes(encoded);
            stage++;
            return new Work(2L * encodedSize + snapshot.totalPayloadBytes(), 4 * before.blocks().size());
        }
        if (!RocketSnapshotWitness.verifies(encoded, snapshot.result())) {
            throw new IllegalArgumentException("Prepared cargo snapshot cannot round-trip");
        }
        encoded = null; state = State.READY;
        // Native root preservation/preflight/compare and payload constructors, with no expanded-palette hashes.
        return new Work(5L * encodedSize + 2L * snapshot.totalPayloadBytes(), 8 * before.blocks().size());
    }
    /** Stage 0 for an item delta; later stages are the fluid ones, unchanged. */
    private Work itemStep() {
        RocketItemCargoMutation.requireTarget(before, item, load);
        int index = index(item.cursor());
        var old = before.blocks().get(index).blockEntityPayload().orElseThrow();
        var replacement = RocketItemCargoMutation.payload(before.blocks().get(index), item, load);
        snapshot = new RocketSnapshotPreparation(before, index, replacement);
        stage++;
        // Container parsing, native stack round trips, merge signatures and copies, as for the tank.
        return new Work(32L * (old.uncompressedBytes() + replacement.uncompressedBytes()
                + RocketNbtSize.uncompressedBytes(item.stack()) + 256), 2 * before.blocks().size());
    }
    private int index(EscrowCargoPayload.Cursor cursor) {
        for (int i = 0; i < before.blocks().size(); i++) {
            if (RocketCargoMutation.matches(before.blocks().get(i), cursor)) { return i; }
        }
        return -1;
    }
    private void requireLive() {
        requireOwner();
        if (state == State.DISCARDED) { throw new IllegalStateException("Cargo preparation was discarded"); }
    }
    private void requireOwner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Cargo preparation requires its owner thread"); }
    }
    void close() {
        requireOwner();
        state = State.DISCARDED; before = null; delta = null; item = null; snapshot = null; encoded = null;
    }
}
