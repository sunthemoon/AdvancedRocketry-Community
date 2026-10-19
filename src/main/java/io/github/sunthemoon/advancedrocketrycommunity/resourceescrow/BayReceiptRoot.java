package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable receipt root: what the server thread captures for a write (no NBT, at most 4,096 references) and what
 * the codec decodes. Construction is O(1) apart from the list copy; {@link #validate} checks the root invariants and
 * runs where the root is encoded or decoded, never in the capture.
 */
public record BayReceiptRoot(UUID storeId, long issuedThrough, long writerGeneration, List<BayReceipt> receipts) {
    public static final int RECEIPTS = 4_096;

    public BayReceiptRoot {
        Objects.requireNonNull(storeId); receipts = List.copyOf(receipts);
        EscrowTags.require(issuedThrough >= 0 && writerGeneration >= 0, "Negative bay receipt root counter");
        EscrowTags.require(receipts.size() <= RECEIPTS, "Too many bay receipts");
    }

    public static BayReceiptRoot empty(UUID storeId) { return new BayReceiptRoot(storeId, 0, 0, List.of()); }

    /** The codec invariants: unique {@code op}, unique key, at most one receipt per satellite, sequences issued. */
    public BayReceiptRoot validate() {
        var ops = new HashSet<UUID>(); var keys = new HashSet<BayReceipt.Key>(); var satellites = new HashSet<UUID>();
        for (BayReceipt receipt : receipts) {
            EscrowTags.require(ops.add(receipt.op()), "Duplicate bay receipt operation");
            EscrowTags.require(keys.add(receipt.key()), "Duplicate bay receipt key");
            EscrowTags.require(satellites.add(receipt.satellite()), "Second bay receipt for one satellite");
            EscrowTags.require(receipt.seq() <= issuedThrough, "Bay receipt sequence was never issued");
        }
        return this;
    }
}
