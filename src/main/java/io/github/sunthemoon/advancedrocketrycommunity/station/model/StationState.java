package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable, independently schema-versioned station authority state. */
public final class StationState {
    private final int schemaVersion;
    private final UUID stationId;
    private final UUID ownerId;
    private final String name;
    private final StationGridCell cell;
    private final StationRegion region;
    private final StationPosition landingPad;
    private final ResourceLocation orbitBody;
    private final long createdAtGameTime;
    private final StationEnvironmentProfile environment;
    private final Set<UUID> members;

    public StationState(
            int schemaVersion,
            UUID stationId,
            UUID ownerId,
            String name,
            StationGridCell cell,
            StationRegion region,
            StationPosition landingPad,
            ResourceLocation orbitBody,
            long createdAtGameTime,
            StationEnvironmentProfile environment,
            Collection<UUID> members
    ) {
        if (schemaVersion != StationLimits.STATE_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported station state schema");
        }
        this.schemaVersion = schemaVersion;
        this.stationId = Objects.requireNonNull(stationId, "stationId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.name = requireName(name);
        this.cell = Objects.requireNonNull(cell, "cell");
        this.region = Objects.requireNonNull(region, "region");
        this.landingPad = Objects.requireNonNull(landingPad, "landingPad");
        this.orbitBody = Objects.requireNonNull(orbitBody, "orbitBody");
        if (!cell.region().equals(region) || !cell.landingPad().equals(landingPad)) {
            throw new IllegalArgumentException("Station geometry does not match its allocated cell");
        }
        if (orbitBody.toString().length() > 128) {
            throw new IllegalArgumentException("Station orbit body identifier is too long");
        }
        if (createdAtGameTime < 0L) {
            throw new IllegalArgumentException("Station creation time cannot be negative");
        }
        this.createdAtGameTime = createdAtGameTime;
        this.environment = Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(members, "members");
        LinkedHashSet<UUID> checked = new LinkedHashSet<>();
        members.stream().sorted(Comparator.naturalOrder()).forEach(member -> {
            Objects.requireNonNull(member, "member");
            if (member.equals(ownerId)) {
                throw new IllegalArgumentException("Station owner cannot also be a member");
            }
            if (!checked.add(member)) {
                throw new IllegalArgumentException("Station member list contains a duplicate");
            }
        });
        if (checked.size() > StationLimits.MAX_MEMBERS) {
            throw new IllegalArgumentException("Station member list exceeds the fixed bound");
        }
        this.members = Set.copyOf(checked);
    }

    public static StationState fromReservation(StationReservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        return new StationState(
                StationLimits.STATE_SCHEMA_VERSION,
                reservation.stationId(),
                reservation.ownerId(),
                reservation.name(),
                reservation.cell(),
                reservation.region(),
                reservation.landingPad(),
                reservation.orbitBody(),
                reservation.createdAtGameTime(),
                StationEnvironmentProfile.BASIC_SPACE,
                List.of()
        );
    }

    static String requireName(String value) {
        Objects.requireNonNull(value, "name");
        String checked = value.strip();
        if (checked.isEmpty() || checked.length() > StationLimits.MAX_NAME_LENGTH
                || checked.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new IllegalArgumentException("Station name is outside the fixed bound");
        }
        return checked;
    }

    public StationState withMember(UUID memberId) {
        Objects.requireNonNull(memberId, "memberId");
        if (memberId.equals(ownerId) || members.contains(memberId)) {
            return this;
        }
        if (members.size() >= StationLimits.MAX_MEMBERS) {
            throw new IllegalStateException("Station member list is full");
        }
        LinkedHashSet<UUID> updated = new LinkedHashSet<>(members);
        updated.add(memberId);
        return copy(ownerId, updated);
    }

    public StationState withoutMember(UUID memberId) {
        Objects.requireNonNull(memberId, "memberId");
        if (!members.contains(memberId)) {
            return this;
        }
        LinkedHashSet<UUID> updated = new LinkedHashSet<>(members);
        updated.remove(memberId);
        return copy(ownerId, updated);
    }

    public StationState transferOwnership(UUID newOwnerId) {
        Objects.requireNonNull(newOwnerId, "newOwnerId");
        if (newOwnerId.equals(ownerId)) {
            return this;
        }
        LinkedHashSet<UUID> updated = new LinkedHashSet<>(members);
        updated.remove(newOwnerId);
        if (updated.size() < StationLimits.MAX_MEMBERS) {
            updated.add(ownerId);
        }
        return copy(newOwnerId, updated);
    }

    private StationState copy(UUID updatedOwner, Collection<UUID> updatedMembers) {
        return new StationState(
                schemaVersion,
                stationId,
                updatedOwner,
                name,
                cell,
                region,
                landingPad,
                orbitBody,
                createdAtGameTime,
                environment,
                updatedMembers
        );
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public UUID stationId() {
        return stationId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String name() {
        return name;
    }

    public StationGridCell cell() {
        return cell;
    }

    public StationRegion region() {
        return region;
    }

    public StationPosition landingPad() {
        return landingPad;
    }

    public ResourceLocation orbitBody() {
        return orbitBody;
    }

    public long createdAtGameTime() {
        return createdAtGameTime;
    }

    public StationEnvironmentProfile environment() {
        return environment;
    }

    public Set<UUID> members() {
        return members;
    }

    public List<UUID> sortedMembers() {
        return members.stream().sorted().toList();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StationState state)) {
            return false;
        }
        return schemaVersion == state.schemaVersion
                && createdAtGameTime == state.createdAtGameTime
                && stationId.equals(state.stationId)
                && ownerId.equals(state.ownerId)
                && name.equals(state.name)
                && cell.equals(state.cell)
                && region.equals(state.region)
                && landingPad.equals(state.landingPad)
                && orbitBody.equals(state.orbitBody)
                && environment.equals(state.environment)
                && members.equals(state.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, stationId, ownerId, name, cell, region,
                landingPad, orbitBody, createdAtGameTime, environment, members);
    }
}

