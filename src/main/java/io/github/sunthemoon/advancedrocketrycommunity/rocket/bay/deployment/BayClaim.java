package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One coordinator claim on logical rocket L and its transfer record T (W27A-DESIGN sections 4.1 and 8.1; R49 Q1). It
 * is HELD from the D0 enqueue, or from the restart classification, until every member is resolved, and then reports
 * exactly one handoff. RAM only: claims are rebuilt every session and never accepted from a caller.
 */
public final class BayClaim {
    public static final int BAYS_PER_ROCKET = 8;
    public enum Origin { CHECKPOINT, RESTART }
    public enum Hold { OWNED_WAIT, REPAIR }
    /** READY_SOURCE needs this session's source authority; ORDINARY hands T to ordinary recovery. */
    public enum Handoff { READY_SOURCE, ORDINARY }
    /** The D0 group checks, then one step per bay, then the wait for every outcome, then the reported end. */
    public enum Stage { GROUP, BAYS, OUTCOMES, ENDED }

    private final UUID logical;
    private final UUID transfer;
    @Nullable private final UUID source;
    private final Origin origin;
    private final List<BayMember> members;
    private Stage stage;
    private int groupStep;
    private boolean checkpointRequested;
    private Hold hold = Hold.OWNED_WAIT;
    @Nullable private String repairReason;
    @Nullable private Handoff handoff;

    private BayClaim(UUID logical, UUID transfer, @Nullable UUID source, Origin origin, List<BayMember> members, Stage stage) {
        this.logical = Objects.requireNonNull(logical, "logical");
        this.transfer = Objects.requireNonNull(transfer, "transfer");
        this.source = source; this.origin = origin; this.members = List.copyOf(members); this.stage = stage;
        if (this.members.isEmpty()) { throw new IllegalArgumentException("A bay claim needs at least one member"); }
    }

    /**
     * The D0 enqueue at the ascent-complete checkpoint, with the in-session live source. Bays are in position order;
     * those after the first {@link #BAYS_PER_ROCKET} are refused at once with ROCKET_DEPLOYMENT_LIMIT.
     */
    public static BayClaim checkpoint(UUID logical, UUID transfer, UUID source, List<BayMember> bays) {
        Objects.requireNonNull(source, "source");
        for (int i = BAYS_PER_ROCKET; i < bays.size(); i++) { bays.get(i).refuse("ROCKET_DEPLOYMENT_LIMIT"); }
        return new BayClaim(logical, transfer, source, Origin.CHECKPOINT, bays, Stage.GROUP);
    }

    /** The restart classification of L's durable receipts; it never has source authority. */
    public static BayClaim restart(UUID logical, UUID transfer, List<BayMember> receipts) {
        return new BayClaim(logical, transfer, null, Origin.RESTART, receipts, Stage.OUTCOMES);
    }

    public UUID logical() { return logical; }
    public UUID transfer() { return transfer; }
    public Optional<UUID> source() { return Optional.ofNullable(source); }
    public Origin origin() { return origin; }
    public List<BayMember> members() { return members; }
    public Stage stage() { return stage; }
    public int groupStep() { return groupStep; }
    public boolean checkpointRequested() { return checkpointRequested; }
    public Optional<Handoff> handoff() { return Optional.ofNullable(handoff); }

    /** Group checks advance one at a time; after the last one the per-bay steps start. */
    public void groupPassed(int steps) {
        require(stage == Stage.GROUP, "pass a group check");
        if (++groupStep >= steps) { stage = Stage.BAYS; }
    }

    /** B14 under ADR-074 item 11: the group's own request was sent, so the next UNPROVED answer rejects. */
    public void markCheckpointRequested() {
        require(stage == Stage.GROUP, "request a checkpoint");
        checkpointRequested = true;
    }

    /** A group check refused: every bay not yet refused is refused with the same runtime reason. */
    public void rejectAll(String reason) {
        require(stage == Stage.GROUP, "reject every bay");
        members.stream().filter(member -> member.state() == BayMember.State.CANDIDATE).forEach(member -> member.refuse(reason));
        stage = Stage.OUTCOMES;
    }

    /** The next bay in position order still to check, or empty once every bay has had its step. */
    public Optional<BayMember> nextCandidate() {
        require(stage == Stage.BAYS, "take a bay step");
        var next = members.stream().filter(member -> member.state() == BayMember.State.CANDIDATE).findFirst();
        if (next.isEmpty()) { stage = Stage.OUTCOMES; }
        return next;
    }

    /** No member is INTENT, awaiting an acknowledgement or held: the claim may end (R49 Q1). */
    public boolean finished() {
        return stage == Stage.OUTCOMES && members.stream().allMatch(BayMember::resolved);
    }

    public void end(Handoff next) {
        require(finished(), "end");
        if (next == Handoff.READY_SOURCE && source == null) { throw new IllegalArgumentException("READY_SOURCE needs source authority"); }
        handoff = Objects.requireNonNull(next); stage = Stage.ENDED;
    }

    /**
     * A REPAIR receipt, a conflict found in this session or a blocked registry (treated like REPAIR for the wait): the
     * claim stays held until offline repair. The first reason is kept.
     */
    public void repair(String reason) {
        Objects.requireNonNull(reason, "reason");
        hold = Hold.REPAIR;
        if (repairReason == null) { repairReason = reason; }
    }

    public Hold hold() { return hold; }

    /** The claim's diagnostic reason: a repair or held member's reason, else what the claim is waiting for. */
    public String reason() {
        if (repairReason != null) { return repairReason; }
        var held = members.stream().filter(member -> member.state() == BayMember.State.HELD).findFirst();
        if (held.isPresent()) { return held.get().reason(); }
        return switch (stage) {
            case GROUP -> checkpointRequested ? "ASSEMBLY_JOURNAL_CHECKPOINT" : "BAY_VALIDATION";
            case BAYS -> "BAY_VALIDATION";
            case OUTCOMES -> origin == Origin.RESTART ? "BAY_RESTART" : "BAY_RECEIPT_WRITE";
            case ENDED -> handoff == Handoff.READY_SOURCE ? "READY_SOURCE" : "RELEASED_TO_ORDINARY";
        };
    }

    private void require(boolean valid, String action) {
        if (!valid) { throw new IllegalStateException("Bay claim cannot " + action + " in stage " + stage); }
    }
}
