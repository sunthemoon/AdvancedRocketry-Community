package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The coordinator's claims, keyed by logical rocket L. A claim answers HELD from its start until its handoff has been
 * reported once; then it is forgotten (W27A-DESIGN section 8.1). At most {@link #LIMIT} claims, so a checkpoint past the
 * limit admits nothing. Pure Java, owner-thread only.
 */
public final class BayClaimBook {
    /** Four times the 64 pending operations the shared admission authority allows. */
    public static final int LIMIT = 256;
    private final LinkedHashMap<UUID, BayClaim> claims = new LinkedHashMap<>();

    /** False, adding nothing, when L already has a claim or the book is full. */
    public boolean begin(BayClaim claim) {
        Objects.requireNonNull(claim, "claim");
        if (claims.size() >= LIMIT || claims.containsKey(claim.logical())) { return false; }
        claims.put(claim.logical(), claim);
        return true;
    }

    public Optional<BayClaim> get(UUID logical) { return Optional.ofNullable(claims.get(logical)); }
    /** Every claim holds its rocket until its handoff is reported, including an ended one. */
    public boolean holds(UUID logical) { return claims.containsKey(logical); }

    /** The ended claim's handoff for its own transfer, exactly once; the claim is then forgotten. */
    public Optional<BayClaim.Handoff> report(UUID logical, UUID transfer) {
        BayClaim claim = claims.get(logical);
        if (claim == null || claim.stage() != BayClaim.Stage.ENDED || !claim.transfer().equals(transfer)) { return Optional.empty(); }
        claims.remove(logical);
        return claim.handoff();
    }

    /** Gated test cleanup only: drops L's claim, ended or not, without a handoff. */
    public void remove(UUID logical) { claims.remove(logical); }

    public Collection<BayClaim> claims() { return List.copyOf(claims.values()); }
    public int size() { return claims.size(); }
    public void clear() { claims.clear(); }
}
