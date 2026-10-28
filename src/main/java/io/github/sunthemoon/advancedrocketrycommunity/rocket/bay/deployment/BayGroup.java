package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * W170 D4: one frozen consumption group for exact (L, T) (W27A-DESIGN sections 4 and 4.1, R3). Its members are the
 * proved registrations only, in bay order, each consumed by its own operation; the four hashes and the owner are the same
 * for every REGISTERED receipt of the group, and the owner binds T for the session (W170-R25). A group never admits a
 * member after it freezes. Its invalidation counter is the
 * D6/D7 token's group part: a save, join, leave or movement of the source bumps it, and a token taken before refuses.
 * RAM only; owner thread, except {@link #token()}'s supplier, which the receipt writer's guard reads on its IO thread.
 */
public final class BayGroup {
    private final UUID logical;
    private final UUID transfer;
    private final UUID owner;
    private final List<BayMember> members;
    private final Map<RocketPosition, UUID> consumed = new LinkedHashMap<>();
    private final String srcBefore, dstBefore, srcAfter, dstAfter;
    private final AtomicLong invalidations = new AtomicLong();

    public BayGroup(UUID logical, UUID transfer, List<BayMember> members, String srcBefore, String dstBefore,
                    String srcAfter, String dstAfter) {
        this.logical = Objects.requireNonNull(logical); this.transfer = Objects.requireNonNull(transfer);
        this.members = List.copyOf(members);
        this.srcBefore = Objects.requireNonNull(srcBefore); this.dstBefore = Objects.requireNonNull(dstBefore);
        this.srcAfter = Objects.requireNonNull(srcAfter); this.dstAfter = Objects.requireNonNull(dstAfter);
        if (this.members.isEmpty() || this.members.size() > BayClaim.BAYS_PER_ROCKET) {
            throw new IllegalArgumentException("A bay group has one to " + BayClaim.BAYS_PER_ROCKET + " members");
        }
        if (srcBefore.equals(srcAfter) || dstBefore.equals(dstAfter)) {
            throw new IllegalArgumentException("A consumption group changes both snapshots");
        }
        owner = this.members.get(0).receipt().map(BayReceipt::owner)
                .orElseThrow(() -> new IllegalArgumentException("A group member has a receipt"));
        for (BayMember member : this.members) {
            BayReceipt receipt = member.receipt().orElseThrow(() -> new IllegalArgumentException("A group member has a receipt"));
            if (!member.consumable() || !receipt.rocket().equals(logical) || !receipt.transfer().equals(transfer)
                    || !receipt.owner().equals(owner) || consumed.put(member.bay(), receipt.op()) != null) {
                throw new IllegalArgumentException("A group member must be a proved or registered bay of (L, T) and its one owner, once");
            }
        }
    }

    /**
     * The group of durable REGISTERED receipts found at a restart: every receipt names L, T and one owner with the same
     * four hashes, or the group is incoherent and the claim holds for repair.
     */
    public static java.util.Optional<BayGroup> resumed(UUID logical, UUID transfer, List<BayMember> registered) {
        if (registered.isEmpty()) { return java.util.Optional.empty(); }
        BayReceipt first = registered.get(0).receipt().orElseThrow();
        for (BayMember member : registered) {
            BayReceipt receipt = member.receipt().orElseThrow();
            if (member.state() != BayMember.State.REGISTERED || !receipt.transfer().equals(transfer)
                    || !receipt.srcBefore().equals(first.srcBefore()) || !receipt.dstBefore().equals(first.dstBefore())
                    || !Objects.equals(receipt.srcAfter(), first.srcAfter()) || !Objects.equals(receipt.dstAfter(), first.dstAfter())) {
                return java.util.Optional.empty();
            }
        }
        try {
            return java.util.Optional.of(new BayGroup(logical, transfer, registered, first.srcBefore(), first.dstBefore(),
                    first.srcAfter(), first.dstAfter()));
        } catch (IllegalArgumentException incoherent) { return java.util.Optional.empty(); }
    }

    public UUID logical() { return logical; }
    public UUID transfer() { return transfer; }
    /** The receipts' one owner; T must name the same owner before any recovery effect (W170-R25). */
    public UUID owner() { return owner; }
    public List<BayMember> members() { return members; }
    /** Bay position to the operation that consumes it, in bay order. */
    public Map<RocketPosition, UUID> consumed() { return java.util.Collections.unmodifiableMap(consumed); }
    public List<UUID> operations() { return List.copyOf(consumed.values()); }
    public String srcBefore() { return srcBefore; }
    public String dstBefore() { return dstBefore; }
    public String srcAfter() { return srcAfter; }
    public String dstAfter() { return dstAfter; }

    /** Which half a recorded content hash is: the source end's before or after hash, or neither. */
    public Half sourceHalf(String hash) { return half(hash, srcBefore, srcAfter); }
    public Half destinationHalf(String hash) { return half(hash, dstBefore, dstAfter); }
    public enum Half { BEFORE, AFTER, OUTSIDE }
    private static Half half(String hash, String before, String after) {
        return before.equals(hash) ? Half.BEFORE : after.equals(hash) ? Half.AFTER : Half.OUTSIDE;
    }

    /** B16: a superseding save, a join or leave, or movement of the source; every earlier token refuses from now on. */
    public void invalidate() { invalidations.incrementAndGet(); }
    /** The group part of the D6/D7 token: true while nothing invalidated the group since this call. */
    public BooleanSupplier token() {
        long at = invalidations.get();
        return () -> invalidations.get() == at;
    }
}
