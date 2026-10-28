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
    /** W27A section 4's D3 rule; the registry writer's own backoff after a non-COMMITTED outcome is also 200 ticks. */
    public static final int REGISTRY_RETRY_TICKS = 200, REGISTRY_UNPROVED_FAILURES = 3;
    public enum Origin { CHECKPOINT, RESTART }
    public enum Hold { OWNED_WAIT, REPAIR }
    /** READY_SOURCE needs this session's source authority; ORDINARY hands T to ordinary recovery. */
    public enum Handoff { READY_SOURCE, ORDINARY }
    /**
     * The D0 group checks, then one step per bay, then the wait for every outcome (D1 to D3); W170's frozen group then
     * publishes REGISTERED (D4), resumes its source after a restart (R1, R2), consumes (D5), proves the source (D6) and
     * retires (D7); then the reported end.
     */
    public enum Stage { GROUP, BAYS, OUTCOMES, PUBLISH, RESUME, CONSUME, PROVE, RETIRE, ENDED }

    private final UUID logical;
    private final UUID transfer;
    @Nullable private UUID source;
    private final Origin origin;
    private final List<BayMember> members;
    private Stage stage;
    private int groupStep;
    private boolean checkpointRequested;
    private Hold hold = Hold.OWNED_WAIT;
    @Nullable private String repairReason;
    @Nullable private String waitReason;
    @Nullable private Handoff handoff;
    @Nullable private BayGroup group;
    private int registryFailures;
    private long registryRetryAt = Long.MIN_VALUE;

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

    /** No member is INTENT, awaiting an acknowledgement or held: the claim may end (R49 Q1), or its group retired. */
    public boolean finished() {
        return (stage == Stage.OUTCOMES || stage == Stage.RETIRE) && members.stream().allMatch(BayMember::resolved);
    }

    public void end(Handoff next) {
        require(finished(), "end");
        if (next == Handoff.READY_SOURCE && source == null) { throw new IllegalArgumentException("READY_SOURCE needs source authority"); }
        if (group != null && next != Handoff.READY_SOURCE) { throw new IllegalArgumentException("A consumed group ends with its source"); }
        handoff = Objects.requireNonNull(next); stage = Stage.ENDED;
    }

    /**
     * W170 D4: every member is resolved or proved and at least one is proved; the group freezes and its publication
     * starts. A restart claim whose receipts are all REGISTERED freezes straight into RESUME.
     */
    public void freeze(BayGroup frozen) {
        require(stage == Stage.OUTCOMES && group == null, "freeze a group");
        Objects.requireNonNull(frozen, "group");
        if (!frozen.logical().equals(logical) || !frozen.transfer().equals(transfer)
                || !members.containsAll(frozen.members()) || !members.stream().allMatch(m -> m.resolved() || m.consumable())) {
            throw new IllegalArgumentException("A frozen group is the claim's proved members of (L, T)");
        }
        group = frozen;
        stage = frozen.members().stream().allMatch(m -> m.state() == BayMember.State.REGISTERED) ? Stage.RESUME : Stage.PUBLISH;
    }

    public Optional<BayGroup> group() { return Optional.ofNullable(group); }

    /**
     * W170: the group's stages move forward in order; D6 repeats from RETIRE (B16). An in-session claim, which holds its
     * live source, skips RESUME; a restart claim enters CONSUME only once R2 established its source.
     */
    public void advance(Stage next) {
        boolean allowed = switch (next) {
            case RESUME -> stage == Stage.PUBLISH && source == null;
            case CONSUME -> (stage == Stage.PUBLISH || stage == Stage.RESUME) && source != null;
            case PROVE -> stage == Stage.CONSUME || stage == Stage.RETIRE;
            case RETIRE -> stage == Stage.PROVE;
            default -> false;
        };
        require(allowed && group != null, "advance to " + next);
        stage = next;
    }

    /** W170 R2: the restart established the exact source (present, or rebuilt with T's source UUID). */
    public void establishSource(UUID established) {
        require(stage == Stage.RESUME && source == null, "establish its source");
        source = Objects.requireNonNull(established);
    }

    /**
     * W170 D3 at server tick {@code now}: one more FAILED, UNKNOWN or discarded registry checkpoint. The next request waits
     * {@link #REGISTRY_RETRY_TICKS}; from the {@link #REGISTRY_UNPROVED_FAILURES}th failure the claim waits with
     * REGISTRY_UNPROVED until a proof clears it. Nothing is rejected.
     */
    public void registryFailed(long now) {
        registryFailures++; registryRetryAt = now + REGISTRY_RETRY_TICKS;
        if (registryFailures >= REGISTRY_UNPROVED_FAILURES) { waitReason = "REGISTRY_UNPROVED"; }
    }
    public int registryFailures() { return registryFailures; }
    /** Whether D3 may request a registry checkpoint at server tick {@code now}: at once, or 200 ticks after a failure. */
    public boolean registryRequestDue(long now) { return now >= registryRetryAt; }

    /** A runtime reason the claim is waiting for (DUPLICATE_PENDING, REGISTRY_UNPROVED); null clears it. */
    public void waiting(@Nullable String reason) { waitReason = reason; }

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
        if (waitReason != null && stage != Stage.ENDED) { return waitReason; }
        return switch (stage) {
            case GROUP -> checkpointRequested ? "ASSEMBLY_JOURNAL_CHECKPOINT" : "BAY_VALIDATION";
            case BAYS -> "BAY_VALIDATION";
            case OUTCOMES -> members.stream().anyMatch(m -> m.state() == BayMember.State.REGISTERING) ? "BAY_REGISTRY_PROOF"
                    : origin == Origin.RESTART ? "BAY_RESTART" : "BAY_RECEIPT_WRITE";
            case PUBLISH, RETIRE -> "BAY_RECEIPT_WRITE";
            case RESUME -> "BAY_RESTART";
            case CONSUME -> "BAY_CONSUMPTION";
            case PROVE -> "BAY_SOURCE_PROOF";
            case ENDED -> handoff == Handoff.READY_SOURCE ? "READY_SOURCE" : "RELEASED_TO_ORDINARY";
        };
    }

    private void require(boolean valid, String action) {
        if (!valid) { throw new IllegalStateException("Bay claim cannot " + action + " in stage " + stage); }
    }
}
