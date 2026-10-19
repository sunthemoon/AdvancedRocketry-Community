package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** W27c1 receipts: a real full-field receipt at its maximum encoding, and simple receipts for counting. */
final class BayReceiptFixtures {
    static final UUID STORE = new UUID(27, 1);
    private BayReceiptFixtures() { }

    static UUID id(long value) { return new UUID(2_701, value); }
    static String hash(long value) { return RocketNativeResources.hash("w27c1-" + value); }
    /** The canonical ID of exactly {@code length} characters. */
    static String id(String namespace, int length) {
        return namespace + ":" + "p".repeat(length - namespace.length() - 1);
    }
    /** {@code package_sig} is the merged NC1 signature of the raw package tag. */
    static String packageSignature(long value) {
        CompoundTag pkg = new CompoundTag(); pkg.putString("id", "advancedrocketrycommunity:satellite_package");
        pkg.putByte("Count", (byte) 1); pkg.putLong("w27c1", value);
        return RocketNativeResources.signature(pkg);
    }

    /** Every optional field present and every string at its maximum: the 938-byte receipt of W27A section 2. */
    static BayReceipt maximal(long n) {
        return new BayReceipt(id(n), n, id(100_000 + n), new RocketPosition(-2_000_000 - (int) n, -64, 2_000_000),
                id(200_000 + n), id(300_000), id("advancedrocketrycommunity", 128), SatelliteKind.ASTEROID_MINER,
                id("advancedrocketrycommunity", 128), packageSignature(n), id(400_000 + n), hash(1), hash(2), hash(3), hash(4),
                BayReceipt.Phase.REGISTERED, "R".repeat(40), true, true, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                BayReceipt.MAX_ATTEMPTS);
    }

    static BayReceipt receipt(long n, UUID owner, UUID rocket, BayReceipt.Phase phase) {
        boolean registered = phase == BayReceipt.Phase.REGISTERED;
        return new BayReceipt(id(n), n, rocket, new RocketPosition((int) n, 1, 0), id(200_000 + n), owner,
                "advancedrocketrycommunity:w27c1_data", SatelliteKind.DATA, "advancedrocketrycommunity:earth",
                packageSignature(n), id(400_000 + n), hash(1), registered ? hash(2) : null, hash(3), registered ? hash(4) : null,
                phase, phase == BayReceipt.Phase.REJECTED ? "INTERRUPTED" : "NONE", false, false, 0, 1, 1, 0);
    }

    static List<BayReceipt> maximalList(int count) {
        var receipts = new ArrayList<BayReceipt>();
        for (long i = 1; i <= count; i++) { receipts.add(maximal(i)); }
        return receipts;
    }

    static BayReceiptRoot root(List<BayReceipt> receipts) {
        long issued = receipts.stream().mapToLong(BayReceipt::seq).max().orElse(0);
        return new BayReceiptRoot(STORE, issued, 1, receipts);
    }
}
