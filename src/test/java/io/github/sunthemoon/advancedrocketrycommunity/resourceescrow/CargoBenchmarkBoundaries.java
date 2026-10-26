package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Effective representation maxima for explicitly specified monotone byte/depth/node families. */
final class CargoBenchmarkBoundaries {
    private CargoBenchmarkBoundaries() { }
    static CompoundTag tag(String family, int n) {
        var tag = new CompoundTag();
        switch (family) {
            case "bytes" -> tag.putByteArray("blob", new byte[n]);
            case "depth" -> { var at = tag; for (int i = 0; i < n; i++) { var child = new CompoundTag(); at.put("c", child); at = child; } }
            case "nodes" -> { for (int i = 0; i < n; i++) { tag.putByte("n" + i, (byte) 1); } }
            default -> throw new IllegalArgumentException("Unknown boundary family");
        }
        return tag;
    }
    static CargoBenchmarkFixtures.Candidate candidate(boolean item, boolean load, String family, int n) {
        var extra = tag(family, n);
        if (item) {
            var stack = EscrowItemCargoEligibilityTest.stone(-1); stack.put("tag", extra);
            var c = EscrowItemCargoEligibilityTest.sample(load, 64, stack);
            return new CargoBenchmarkFixtures.Candidate(c.op(),
                    RocketSnapshotNbtCodec.decode(c.before().getCompound("snapshot")).snapshot().orElseThrow(),
                    RocketSnapshotNbtCodec.decode(c.after().getCompound("snapshot")).snapshot().orElseThrow(),
                    c.before(), c.after(), c.deviceBefore(), c.deviceAfter());
        }
        var seed = CargoBenchmarkFixtures.small(false, load, -1); var old = seed.op();
        var contents = new FluidStack(Fluids.WATER, 2000); contents.setTag(extra);
        var moved = contents.copy(); moved.setAmount(1000);
        // Establish the typed inner/framed bound before the tank's independent save envelope.
        new EscrowCargoPayload(EscrowCargoPayload.decode(old.payload()).cursor(), moved.writeToNBT(new CompoundTag()));
        var before = CargoTestFixtures.snapshot(load ? FluidStack.EMPTY : contents);
        var delta = CargoTestFixtures.delta(before, 0, moved); var after = RocketCargoMutation.apply(before, delta, load);
        var oldTank = load ? contents : FluidStack.EMPTY; var nextTank = RocketCargoMutation.change(oldTank, moved, !load);
        var b = seed.rocketBefore().copy(); var a = seed.rocketAfter().copy();
        var fuel = RocketFlightNbtCodec.decode(b.getCompound("flight")).data().orElseThrow().fuel();
        var end = old.rocket(); var logical = end.logicalRocket();
        var rocket = new EscrowEndpoint(end.kind(), end.level(), end.identity(), logical, end.type(), end.x(), end.y(), end.z(), end.unit(), 0,
                EscrowSignatureKind.composite(logical, before.contentHash(), fuel.resourceSignature()),
                EscrowSignatureKind.composite(logical, after.contentHash(), fuel.resourceSignature()));
        var de = old.device();
        var device = new EscrowEndpoint(de.kind(), de.level(), de.identity(), null, de.type(), de.x(), de.y(), de.z(), de.unit(), 0,
                LoaderTank.of(oldTank).signature(de.identity()), LoaderTank.of(nextTank).signature(de.identity()));
        var payload = delta.encode();
        var op = new EscrowOperation(old.id(), old.sequence(), old.owner(), old.createdTick(), old.phase(), old.reason(), payload,
                RocketNativeResources.signature(payload), load ? device : rocket, load ? rocket : device, old.guard(), old.kind(), old.settlement());
        b.putUUID("assembly_snapshot", before.snapshotId()); b.put("snapshot", RocketSnapshotNbtCodec.encode(before)); b.putString("content_hash", before.contentHash());
        a.putUUID("assembly_snapshot", after.snapshotId()); a.put("snapshot", RocketSnapshotNbtCodec.encode(after)); a.putString("content_hash", after.contentHash());
        a.put("wrapper", new RocketEscrowSection(EscrowTestFixtures.ledger().id(), 1,
                List.of(new EscrowPrefix.Link(op, rocket, load ? "DESTINATION" : "SOURCE").marker())).encode());
        var db = seed.deviceBefore().copy(); var da = seed.deviceAfter().copy();
        db.getCompound(EscrowCargoEligibility.DEVICE_ROOT).put("tank", LoaderTank.of(oldTank).encode());
        da.getCompound(EscrowCargoEligibility.DEVICE_ROOT).put("tank", LoaderTank.of(nextTank).encode());
        return new CargoBenchmarkFixtures.Candidate(op, before, after, b, a, db, da);
    }
    static Map<String, Object> observe(boolean item, boolean load, String family, int n) {
        var row = new LinkedHashMap<String, Object>(); row.put("parameter", n);
        long start = System.nanoTime();
        try {
            var c = candidate(item, load, family, n); var originals = c.inputs().stream().map(CompoundTag::copy).toList();
            var ledger = EscrowTestFixtures.ledger(); var saved = EscrowCodec.encode(ledger);
            var result = EscrowCargoEligibility.check(ledger, List.of(), c.op(), c.rocketBefore(), c.rocketAfter(), c.deviceBefore(), c.deviceAfter(), 64000);
            CargoBenchmarkFixtures.require(originals.equals(c.inputs()) && saved.equals(EscrowCodec.encode(ledger)), "Boundary check mutated an endpoint/ledger");
            CargoBenchmarkFixtures.require(result.status() == RocketCargoSelection.Status.ELIGIBLE
                    || result.status() == RocketCargoSelection.Status.CARGO_TOO_LARGE, "Unexpected effective boundary result: " + result.status());
            row.put("status", result.status().name()); row.put("stage", "complete_capture_and_O1_reservation");
            row.put("unmutated", true); row.put("framed_bytes", RocketNbtSize.uncompressedBytes(c.op().payload()));
            row.put("inner_bytes", RocketNbtSize.uncompressedBytes(c.op().payload().getCompound(item ? "stack" : "fluid")));
            row.put("before_snapshot_bytes", RocketNbtSize.uncompressedBytes(c.rocketBefore().getCompound("snapshot")));
            row.put("after_snapshot_bytes", RocketNbtSize.uncompressedBytes(c.rocketAfter().getCompound("snapshot")));
        } catch (EscrowTags.TooLarge | EscrowItemStackCodec.Refusal | RocketSnapshotException refused) {
            if (refused instanceof EscrowItemStackCodec.Refusal value && value.status() != EscrowItemStackCodec.Status.CARGO_TOO_LARGE) { throw value; }
            if (refused instanceof RocketSnapshotException value && value.code() != io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.BLOCK_ENTITY_DATA_TOO_LARGE
                    && value.code() != io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE) { throw value; }
            row.put("status", "CARGO_TOO_LARGE"); row.put("stage", "construction_before_any_admission"); row.put("unmutated", true);
            row.put("refusal", refused.toString());
        }
        row.put("wall_ns", System.nanoTime() - start); return row;
    }
    static List<Map<String, Object>> measure() {
        var result = new ArrayList<Map<String, Object>>();
        for (boolean item : List.of(false, true)) { for (boolean load : List.of(true, false)) {
            for (String family : List.of("bytes", "depth", "nodes")) {
                int low = 0, high = family.equals("bytes") ? 9000 : family.equals("depth") ? 24 : 520;
                CargoBenchmarkFixtures.require(eligible(observe(item, load, family, low)) && !eligible(observe(item, load, family, high)), "Boundary is not bracketed");
                while (low < high) { int middle = (low + high + 1) >>> 1; if (eligible(observe(item, load, family, middle))) { low = middle; } else { high = middle - 1; } }
                var admitted = observe(item, load, family, low); var rejected = observe(item, load, family, low + 1);
                CargoBenchmarkFixtures.require(eligible(admitted) && !eligible(rejected), "Missing adjacent effective boundary");
                result.add(Map.of("device", (item ? "item_" : "fluid_") + (load ? "loader" : "unloader"), "family", family,
                        "admitted", admitted, "rejected", rejected));
            }
        } }
        return result;
    }
    private static boolean eligible(Map<String, Object> row) { return row.get("status").equals("ELIGIBLE"); }
}
