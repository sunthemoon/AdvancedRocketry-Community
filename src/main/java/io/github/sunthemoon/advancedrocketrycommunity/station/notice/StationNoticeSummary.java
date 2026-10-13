package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One station's offline summary (ADR-065 section 5.3): the latest stored outcome and its revision, with revision 0 and
 * no outcome for a new station, and the revision each owner or member last acknowledged. The owner's acknowledgement
 * has its own field, because members exclude the owner; members have at most
 * {@link StationNoticeLimits#MAX_MEMBER_ACKNOWLEDGEMENTS}. Immutable: every change returns another summary.
 */
public final class StationNoticeSummary {
    public static final StationNoticeSummary EMPTY = new StationNoticeSummary(0L, Optional.empty(), Optional.empty(),
            Map.of());

    private final long revision;
    private final Optional<StationNotice> outcome;
    private final Optional<Acknowledgement> owner;
    private final Map<UUID, Long> members;

    public StationNoticeSummary(long revision, Optional<StationNotice> outcome, Optional<Acknowledgement> owner,
                                Map<UUID, Long> members) {
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.owner = Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(members, "members");
        if (revision < 0L || outcome.isPresent() != revision > 0L) {
            throw new IllegalArgumentException("A station notice summary has an outcome exactly when its revision is"
                    + " positive");
        }
        if (outcome.filter(notice -> !notice.kind().stored()
                || StationNoticeCodec.envelopeBytes(notice) > StationNoticeLimits.MAX_ENVELOPE_BYTES).isPresent()) {
            throw new IllegalArgumentException("A station notice summary keeps only an admitted outcome");
        }
        if (owner.filter(acknowledgement -> acknowledgement.revision() > revision).isPresent()) {
            throw new IllegalArgumentException("The owner acknowledged a later revision");
        }
        if (members.size() > StationNoticeLimits.MAX_MEMBER_ACKNOWLEDGEMENTS) {
            throw new IllegalArgumentException("Station notice member acknowledgements exceed the fixed bound");
        }
        TreeMap<UUID, Long> checked = new TreeMap<>();
        members.forEach((player, acknowledged) -> {
            Objects.requireNonNull(player, "player");
            if (acknowledged == null || acknowledged < 1L || acknowledged > revision) {
                throw new IllegalArgumentException("A member acknowledged no stored revision");
            }
            if (owner.filter(acknowledgement -> acknowledgement.player().equals(player)).isPresent()) {
                throw new IllegalArgumentException("The owner's acknowledgement is also a member's");
            }
            checked.put(player, acknowledged);
        });
        this.revision = revision;
        this.members = Collections.unmodifiableMap(checked);
    }

    public long revision() {
        return revision;
    }

    public Optional<StationNotice> outcome() {
        return outcome;
    }

    public Optional<Acknowledgement> owner() {
        return owner;
    }

    /** Member acknowledgements in UUID order. */
    public Map<UUID, Long> members() {
        return members;
    }

    public OptionalLong acknowledged(UUID player) {
        if (owner.filter(acknowledgement -> acknowledgement.player().equals(player)).isPresent()) {
            return OptionalLong.of(owner.orElseThrow().revision());
        }
        Long member = members.get(player);
        return member == null ? OptionalLong.empty() : OptionalLong.of(member);
    }

    /** Every player whose acknowledgement is stored. */
    public Set<UUID> players() {
        Set<UUID> players = new LinkedHashSet<>();
        owner.ifPresent(acknowledgement -> players.add(acknowledgement.player()));
        players.addAll(members.keySet());
        return Collections.unmodifiableSet(players);
    }

    /** The outcome replaced in place at the next revision; acknowledgements stay and now name older revisions. */
    StationNoticeSummary replacedBy(StationNotice next) {
        return new StationNoticeSummary(Math.addExact(revision, 1L), Optional.of(next.summary()), owner, members);
    }

    /**
     * Only the acknowledgements of the current owner and members, each in its role's field; an ownership transfer
     * moves an acknowledgement from one field to the other. Former owners and members lose theirs.
     */
    StationNoticeSummary reconciled(UUID ownerId, Set<UUID> memberIds) {
        Optional<Acknowledgement> keptOwner = Optional.empty();
        OptionalLong ownerRevision = acknowledged(ownerId);
        if (ownerRevision.isPresent()) {
            keptOwner = Optional.of(new Acknowledgement(ownerId, ownerRevision.getAsLong()));
        }
        TreeMap<UUID, Long> keptMembers = new TreeMap<>();
        for (UUID member : memberIds) {
            OptionalLong memberRevision = acknowledged(member);
            if (memberRevision.isPresent() && !member.equals(ownerId)) {
                keptMembers.put(member, memberRevision.getAsLong());
            }
        }
        if (keptOwner.equals(owner) && keptMembers.equals(members)) {
            return this;
        }
        return new StationNoticeSummary(revision, outcome, keptOwner, keptMembers);
    }

    /** {@code player}'s acknowledgement of the current outcome; unchanged unless the player is the owner or a member. */
    StationNoticeSummary acknowledgedBy(UUID player, UUID ownerId, Set<UUID> memberIds) {
        if (revision == 0L || acknowledged(player).orElse(0L) == revision) {
            return this;
        }
        if (player.equals(ownerId)) {
            return new StationNoticeSummary(revision, outcome, Optional.of(new Acknowledgement(player, revision)),
                    members);
        }
        if (!memberIds.contains(player)) {
            return this;
        }
        TreeMap<UUID, Long> updated = new TreeMap<>(members);
        updated.put(player, revision);
        return new StationNoticeSummary(revision, outcome, owner, updated);
    }

    /** The outcome {@code player} has not acknowledged, if the player is the owner or a member. */
    Optional<StationNotice> pendingFor(UUID player, UUID ownerId, Set<UUID> memberIds) {
        if (!player.equals(ownerId) && !memberIds.contains(player)) {
            return Optional.empty();
        }
        return acknowledged(player).orElse(0L) < revision ? outcome : Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof StationNoticeSummary summary && revision == summary.revision
                && outcome.equals(summary.outcome) && owner.equals(summary.owner) && members.equals(summary.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(revision, outcome, owner, members);
    }

    @Override
    public String toString() {
        return "StationNoticeSummary[revision=" + revision + ", outcome=" + outcome + ", owner=" + owner
                + ", members=" + members + "]";
    }

    /** The revision one player last acknowledged. */
    public record Acknowledgement(UUID player, long revision) {
        public Acknowledgement {
            Objects.requireNonNull(player, "player");
            if (revision < 1L) {
                throw new IllegalArgumentException("An acknowledgement names a stored revision");
            }
        }
    }
}
