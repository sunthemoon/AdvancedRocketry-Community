package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * One immutable world-first event (ADR-066 section 6.2; W44 sections 1.1 and 2.1): the source transaction, the winning
 * participants, strictly ascending by {@link UUID#compareTo}, and the fingerprint that binds them. Construction
 * validates every field and recomputes the fingerprint; nothing here reads the world.
 */
public sealed interface FirstEvent permits FirstEvent.MoonLanding, FirstEvent.StationWarp {
    FirstEventKind kind();

    UUID eventId();

    /** The immutable winning set; never current members, later joiners or a live passenger list. */
    List<UUID> participants();

    String fingerprint();

    /** O(log n) lookup in the immutable participant array (W44 section 3.5). */
    default boolean participant(UUID player) {
        return player != null && Collections.binarySearch(participants(), player) >= 0;
    }

    /** The first Moon landing; taken from the COMMITTED record at the checked completion (W44 section 2.3). */
    record MoonLanding(UUID eventId, UUID transferId, UUID logicalRocketId, UUID ownerId, UUID terminalRocketId,
                       String recordChecksum, ResourceLocation level, int originX, int originY, int originZ,
                       long completedAtGameTime, List<UUID> participants, String fingerprint) implements FirstEvent {
        public MoonLanding {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(transferId, "transferId");
            Objects.requireNonNull(logicalRocketId, "logicalRocketId");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(terminalRocketId, "terminalRocketId");
            if (recordChecksum == null || !recordChecksum.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("A first Moon landing needs the record's 64-hex checksum");
            }
            if (!CelestialIds.MOON_LEVEL.location().equals(level)) {
                throw new IllegalArgumentException("A first Moon landing must complete in the Moon Level");
            }
            if (completedAtGameTime < 0L) {
                throw new IllegalArgumentException("A first Moon landing cannot complete before game time 0");
            }
            participants = ascendingParticipants(participants, FirstEventKind.FIRST_MOON_LANDING);
            requireFingerprint(FirstEventKind.FIRST_MOON_LANDING, eventId, participants, fingerprint);
        }

        public static MoonLanding create(UUID eventId, UUID transferId, UUID logicalRocketId, UUID ownerId,
                                         UUID terminalRocketId, String recordChecksum, int originX, int originY,
                                         int originZ, long completedAtGameTime, Collection<UUID> participants) {
            List<UUID> sorted = sortedParticipants(participants, FirstEventKind.FIRST_MOON_LANDING);
            return new MoonLanding(eventId, transferId, logicalRocketId, ownerId, terminalRocketId, recordChecksum,
                    CelestialIds.MOON_LEVEL.location(), originX, originY, originZ, completedAtGameTime, sorted,
                    fingerprintOf(FirstEventKind.FIRST_MOON_LANDING, eventId, sorted));
        }

        @Override
        public FirstEventKind kind() { return FirstEventKind.FIRST_MOON_LANDING; }
    }

    /** The first station warp; minted with the checked relocation candidate (W44 section 1.2). */
    record StationWarp(UUID eventId, UUID stationId, UUID actorId, ResourceLocation fromBody, ResourceLocation toBody,
                       int cost, long orbitalTick, List<UUID> participants, String fingerprint) implements FirstEvent {
        /** The station record's existing orbit-body bound. */
        public static final int MAX_BODY_LENGTH = 128;

        public StationWarp {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(stationId, "stationId");
            Objects.requireNonNull(actorId, "actorId");
            requireBody(fromBody);
            requireBody(toBody);
            if (cost < 1 || cost > StationLimits.MAX_WARP_ENERGY) {
                throw new IllegalArgumentException("A first station warp cost is outside its bound");
            }
            if (orbitalTick < 0L) {
                throw new IllegalArgumentException("A first station warp clock cannot be negative");
            }
            participants = ascendingParticipants(participants, FirstEventKind.FIRST_STATION_WARP);
            requireFingerprint(FirstEventKind.FIRST_STATION_WARP, eventId, participants, fingerprint);
        }

        public static StationWarp create(UUID eventId, UUID stationId, UUID actorId, ResourceLocation fromBody,
                                         ResourceLocation toBody, int cost, long orbitalTick,
                                         Collection<UUID> participants) {
            List<UUID> sorted = sortedParticipants(participants, FirstEventKind.FIRST_STATION_WARP);
            return new StationWarp(eventId, stationId, actorId, fromBody, toBody, cost, orbitalTick, sorted,
                    fingerprintOf(FirstEventKind.FIRST_STATION_WARP, eventId, sorted));
        }

        @Override
        public FirstEventKind kind() { return FirstEventKind.FIRST_STATION_WARP; }

        private static void requireBody(ResourceLocation body) {
            if (body == null || body.toString().length() > MAX_BODY_LENGTH) {
                throw new IllegalArgumentException("A first station warp body is missing or outside its bound");
            }
        }
    }

    /**
     * SHA-256 over the UTF-8 kind, a 0 byte, the 16 event ID bytes, n as a 4-byte big-endian int and 16 bytes per
     * participant; 64 lowercase hex characters (W44 section 1.1).
     */
    static String fingerprintOf(FirstEventKind kind, UUID eventId, List<UUID> participants) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(kind.serializedName().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 16 * participants.size());
            buffer.putLong(eventId.getMostSignificantBits()).putLong(eventId.getLeastSignificantBits());
            buffer.putInt(participants.size());
            for (UUID participant : participants) {
                buffer.putLong(participant.getMostSignificantBits()).putLong(participant.getLeastSignificantBits());
            }
            digest.update(buffer.array());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is unavailable", missing);
        }
    }

    /** A sorted copy for a new event; a duplicate or more than the kind's bound is refused, never truncated. */
    private static List<UUID> sortedParticipants(Collection<UUID> participants, FirstEventKind kind) {
        Objects.requireNonNull(participants, "participants");
        if (participants.size() > kind.maxParticipants()) {
            throw new IllegalArgumentException("First event participants exceed the " + kind.serializedName()
                    + " bound");
        }
        return ascendingParticipants(participants.stream().map(id -> Objects.requireNonNull(id, "participant"))
                .sorted().toList(), kind);
    }

    /** An immutable copy that must already be strictly ascending by {@link UUID#compareTo} and within the bound. */
    private static List<UUID> ascendingParticipants(List<UUID> participants, FirstEventKind kind) {
        Objects.requireNonNull(participants, "participants");
        if (participants.size() > kind.maxParticipants()) {
            throw new IllegalArgumentException("First event participants exceed the " + kind.serializedName()
                    + " bound");
        }
        List<UUID> copy = List.copyOf(participants);
        for (int index = 1; index < copy.size(); index++) {
            if (copy.get(index - 1).compareTo(copy.get(index)) >= 0) {
                throw new IllegalArgumentException("First event participants must be strictly ascending");
            }
        }
        return copy;
    }

    private static void requireFingerprint(FirstEventKind kind, UUID eventId, List<UUID> participants,
                                           String fingerprint) {
        if (fingerprint == null || !fingerprint.equals(fingerprintOf(kind, eventId, participants))) {
            throw new IllegalArgumentException("First event fingerprint does not match its participants");
        }
    }
}
