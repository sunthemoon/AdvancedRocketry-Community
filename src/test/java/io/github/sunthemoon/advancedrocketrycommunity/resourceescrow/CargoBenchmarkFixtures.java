package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** W144 synthetic microbenchmark inputs, not world capture or native admission evidence. */
final class CargoBenchmarkFixtures {
    static final int BLOCKS = 2048, PAYLOADS = 128, AGGREGATE = 1_048_576;
    record Candidate(EscrowOperation op, RocketStructureSnapshot before, RocketStructureSnapshot after,
                     CompoundTag rocketBefore, CompoundTag rocketAfter, CompoundTag deviceBefore, CompoundTag deviceAfter) {
        CargoTicketTestFixture ticket() {
            return op.kind().itemCargo()
                    ? new CargoTicketTestFixture(op.guard().rocket(), before, EscrowItemCargoPayload.decode(op.payload()), op.kind().cargoLoad())
                    : new CargoTicketTestFixture(op.guard().rocket(), before, EscrowCargoPayload.decode(op.payload()), op.kind().cargoLoad());
        }
        EscrowCargoEligibility.Result check() {
            return EscrowCargoEligibility.check(EscrowTestFixtures.ledger(), List.of(), op,
                    rocketBefore, rocketAfter, deviceBefore, deviceAfter, 64_000);
        }
        List<CompoundTag> inputs() { return List.of(rocketBefore, rocketAfter, deviceBefore, deviceAfter); }
    }
    private CargoBenchmarkFixtures() { }

    static Candidate small(boolean item, boolean load, int blob) {
        if (item) {
            var c = EscrowItemCargoEligibilityTest.sample(load, 64, EscrowItemCargoEligibilityTest.stone(blob));
            return candidate(c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter());
        }
        var c = EscrowCargoEligibilityTest.sample(load, 1000, blob);
        return candidate(c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter());
    }
    private static Candidate candidate(EscrowOperation op, CompoundTag before, CompoundTag after, CompoundTag db, CompoundTag da) {
        return new Candidate(op, RocketSnapshotNbtCodec.decode(before.getCompound("snapshot")).snapshot().orElseThrow(),
                RocketSnapshotNbtCodec.decode(after.getCompound("snapshot")).snapshot().orElseThrow(), before, after, db, da);
    }

    /** 127 real vanilla chest payloads plus the addressed tank/chest, at an exact before-payload aggregate. */
    static RocketStructureSnapshot snapshot(Candidate seed, int aggregate) {
        var blocks = new ArrayList<RocketBlock>();
        var target = seed.before.blocks().stream().filter(b -> b.position().equals(new RocketPosition(0, 0, 0))).findFirst().orElseThrow();
        blocks.add(target);
        int remaining = aggregate - target.blockEntityPayload().orElseThrow().uncompressedBytes();
        for (int i = 1; i < BLOCKS; i++) {
            var pos = new RocketPosition(i % 16, i / 256, i / 16 % 16);
            if (i < PAYLOADS) {
                int bytes = remaining / (PAYLOADS - i);
                var payload = chest(bytes); remaining -= bytes;
                blocks.add(new RocketBlock(pos, ItemCargoTestFixtures.state(ItemCargoTestFixtures.CHEST), payload));
            } else {
                blocks.add(new RocketBlock(pos, new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
            }
        }
        var result = CargoTestFixtures.snapshot(blocks);
        require(aggregate(result) == aggregate, "Fixture aggregate differs");
        return result;
    }
    private static RocketBlockEntityPayload chest(int bytes) {
        int overhead = chestBlobs(0).uncompressedBytes();
        require(bytes >= overhead, "Aggregate below chest framing");
        var result = chestBlobs(bytes - overhead);
        require(result.uncompressedBytes() == bytes, "Chest framing differs");
        return result;
    }
    private static RocketBlockEntityPayload chestBlobs(int bytes) {
        var entries = new CompoundTag[3];
        for (int slot = 0; slot < 3; slot++) {
            var stack = ItemCargoTestFixtures.stack(Items.STONE, 1); var tag = new CompoundTag();
            tag.putByteArray("blob", new byte[bytes / 3 + (slot < bytes % 3 ? 1 : 0)]); stack.put("tag", tag);
            EscrowItemStackCodec.validateNative(stack);
            entries[slot] = ItemCargoTestFixtures.entry(slot + 1, stack);
        }
        return ItemCargoTestFixtures.container(null, null, entries);
    }
    static Candidate expand(Candidate seed, int aggregate) {
        var before = snapshot(seed, aggregate);
        var op = seed.op;
        var after = op.kind().itemCargo()
                ? RocketItemCargoMutation.apply(before, EscrowItemCargoPayload.decode(op.payload()), op.kind().cargoLoad())
                : RocketCargoMutation.apply(before, EscrowCargoPayload.decode(op.payload()), op.kind().cargoLoad());
        var fuel = RocketFlightNbtCodec.decode(seed.rocketBefore.getCompound("flight")).data().orElseThrow().fuel();
        var end = op.rocket();
        var rocket = new EscrowEndpoint(end.kind(), end.level(), end.identity(), end.logicalRocket(), end.type(), end.x(), end.y(), end.z(),
                end.unit(), end.beforeEpoch(), EscrowSignatureKind.composite(end.logicalRocket(), before.contentHash(), fuel.resourceSignature()),
                EscrowSignatureKind.composite(end.logicalRocket(), after.contentHash(), fuel.resourceSignature()));
        var changed = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(),
                op.payloadHash(), op.kind().cargoLoad() ? op.device() : rocket, op.kind().cargoLoad() ? rocket : op.device(), op.guard(),
                op.kind(), op.settlement());
        var b = seed.rocketBefore.copy(); var a = seed.rocketAfter.copy();
        b.put("snapshot", RocketSnapshotNbtCodec.encode(before)); b.putString("content_hash", before.contentHash());
        a.put("snapshot", RocketSnapshotNbtCodec.encode(after)); a.putString("content_hash", after.contentHash());
        return new Candidate(changed, before, after, b, a, seed.deviceBefore.copy(), seed.deviceAfter.copy());
    }
    static int aggregate(RocketStructureSnapshot snapshot) {
        return snapshot.blocks().stream().flatMap(b -> b.blockEntityPayload().stream()).mapToInt(RocketBlockEntityPayload::uncompressedBytes).sum();
    }
    static boolean fits(Candidate seed, int aggregate) {
        try {
            var status = expand(seed, aggregate).check().status();
            require(status == RocketCargoSelection.Status.ELIGIBLE || status == RocketCargoSelection.Status.CARGO_TOO_LARGE,
                    "Unexpected fixture refusal: " + status);
            return status == RocketCargoSelection.Status.ELIGIBLE;
        }
        catch (RocketSnapshotException refused) {
            if (refused.code() != io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE
                    && refused.code() != io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.BLOCK_ENTITY_DATA_TOO_LARGE) {
                throw refused;
            }
            return false;
        }
    }
    static int largest(Candidate seed) {
        int low = 128 * 512, high = AGGREGATE;
        require(fits(seed, low), "Minimum fixture not admitted");
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (fits(seed, middle)) { low = middle; } else { high = middle - 1; }
        }
        require(!fits(seed, low + 1), "No proven adjacent refusal");
        return low;
    }
    static void require(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }
}
