package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * ADR-049 section 9 solar links. A receiver claims the link of a chip it holds when the satellite is unlinked;
 * the existing holder keeps it until its own check no longer finds the chip, or it is removed. Only link
 * changes write to the registry; output is a rate, never banked.
 */
public final class SolarLinks {
    public static final int MAX_OUTPUT_PER_TICK = 10_000;

    /** What one receiver slot contributes. */
    public enum LinkStatus { EMPTY, HOLDING, ELSEWHERE, UNAVAILABLE }

    /** One receiver check: a status per slot and the output in FE per tick. */
    public record ReceiverCheck(List<LinkStatus> slots, int output) {
        public ReceiverCheck {
            slots = List.copyOf(slots);
            if (output < 0 || output > MAX_OUTPUT_PER_TICK) {
                throw new IllegalArgumentException("Receiver output is outside its bound");
            }
        }
    }

    private final CelestialCatalogManager celestialCatalogs;
    private final ReceiverDirectory receivers;

    SolarLinks(CelestialCatalogManager celestialCatalogs, ReceiverDirectory receivers) {
        this.celestialCatalogs = Objects.requireNonNull(celestialCatalogs, "celestialCatalogs");
        this.receivers = Objects.requireNonNull(receivers, "receivers");
    }

    /** The 20-tick receiver check: claim free links, keep held ones, release links whose chip is gone. */
    ReceiverCheck check(MinecraftServer server, UUID receiverId, List<Optional<SatelliteIdentity>> chips) {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        Map<UUID, SatelliteState> holding = new LinkedHashMap<>();
        List<LinkStatus> statuses = new ArrayList<>(chips.size());
        for (Optional<SatelliteIdentity> chip : chips) {
            if (chip.isEmpty()) {
                statuses.add(LinkStatus.EMPTY);
                continue;
            }
            SatelliteIdentity identity = chip.get();
            SatelliteState satellite = data.satellite(identity.satelliteId()).orElse(null);
            if (satellite == null || !satellite.ownerId().equals(identity.ownerId())
                    || !(satellite.kindState() instanceof SatelliteKindState.Solar solar)) {
                statuses.add(LinkStatus.UNAVAILABLE);
                continue;
            }
            if (holding.containsKey(satellite.satelliteId())) {
                statuses.add(LinkStatus.HOLDING);
                continue;
            }
            if (solar.receiver().isEmpty()) {
                SatelliteOperationResult claimed = data.updateKindState(satellite.satelliteId(),
                        new SatelliteKindState.Solar(solar.outputMultiplierPercent(), Optional.of(receiverId)));
                satellite = claimed.satellite().orElse(satellite);
            } else if (!solar.receiver().get().equals(receiverId)) {
                statuses.add(LinkStatus.ELSEWHERE);
                continue;
            }
            holding.put(satellite.satelliteId(), satellite);
            statuses.add(LinkStatus.HOLDING);
        }
        for (UUID linked : data.linkedTo(receiverId)) {
            if (!holding.containsKey(linked)) {
                clearLink(data, linked);
            }
        }
        return new ReceiverCheck(statuses, output(holding.values()));
    }

    /** {@code Σ power × solar_intensity(orbit body) × multiplier / 100}, floored and capped; 0 for a lost body. */
    int output(Iterable<SatelliteState> satellites) {
        double total = 0.0D;
        for (SatelliteState satellite : satellites) {
            if (!(satellite.kindState() instanceof SatelliteKindState.Solar solar) || satellite.orbitBody().isEmpty()) {
                continue;
            }
            double intensity = celestialCatalogs.current()
                    .flatMap(catalog -> catalog.get(satellite.orbitBody().orElseThrow()))
                    .map(body -> body.solarIntensity())
                    .orElse(0.0D);
            total += satellite.blueprint().stats().power() * intensity * solar.outputMultiplierPercent() / 100.0D;
        }
        return (int) Math.min(MAX_OUTPUT_PER_TICK, Math.floor(total));
    }

    /** A removed receiver clears every link it holds. */
    void release(MinecraftServer server, UUID receiverId) {
        receivers.remove(receiverId);
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        for (UUID linked : data.linkedTo(receiverId)) {
            clearLink(data, linked);
        }
    }

    /** Whether the satellite's linked receiver is confirmed missing (ADR-049 sections 7 and 9). */
    boolean receiverMissing(MinecraftServer server, SatelliteState satellite) {
        return satellite.kindState() instanceof SatelliteKindState.Solar solar && solar.receiver().isPresent()
                && receivers.presence(server, solar.receiver().get()) == ReceiverDirectory.Presence.MISSING;
    }

    /** The owner, with the chip at a terminal, unlinks a satellite whose receiver is confirmed missing. */
    SatelliteOperationResult unlink(ServerPlayer player, SatelliteIdentity identity, boolean operator) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return SatelliteManager.failure(SatelliteOperationCode.SERVER_ERROR);
        }
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteState satellite = data.satellite(identity.satelliteId()).orElse(null);
        if (satellite == null) {
            return SatelliteManager.failure(SatelliteOperationCode.SATELLITE_NOT_FOUND);
        }
        if (!operator && !satellite.ownerId().equals(player.getUUID())) {
            return SatelliteManager.failure(SatelliteOperationCode.UNAUTHORIZED);
        }
        if (!(satellite.kindState() instanceof SatelliteKindState.Solar solar) || solar.receiver().isEmpty()) {
            return new SatelliteOperationResult(SatelliteOperationCode.IDEMPOTENT, false, Optional.of(satellite),
                    Optional.empty(), 0);
        }
        if (!receiverMissing(server, satellite)) {
            return SatelliteManager.failure(SatelliteOperationCode.MISSION_BUSY);
        }
        SatelliteOperationResult result = clearLink(data, satellite.satelliteId());
        AdvancedRocketryCommunity.LOGGER.info("ARCE_SATELLITE_UNLINK satellite={} receiver={} by={} reason=missing",
                satellite.satelliteId(), solar.receiver().get(), player.getUUID());
        return result;
    }

    /** Operator unlink by satellite ID, whatever the receiver's state. */
    SatelliteOperationResult unlinkAdmin(MinecraftServer server, UUID satelliteId, UUID actor) {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteState satellite = data.satellite(satelliteId).orElse(null);
        if (satellite == null) {
            return SatelliteManager.failure(SatelliteOperationCode.SATELLITE_NOT_FOUND);
        }
        if (!(satellite.kindState() instanceof SatelliteKindState.Solar solar) || solar.receiver().isEmpty()) {
            return new SatelliteOperationResult(SatelliteOperationCode.IDEMPOTENT, false, Optional.of(satellite),
                    Optional.empty(), 0);
        }
        SatelliteOperationResult result = clearLink(data, satelliteId);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_SATELLITE_UNLINK satellite={} receiver={} by={} reason=operator",
                satelliteId, solar.receiver().get(), actor);
        return result;
    }

    private static SatelliteOperationResult clearLink(SatelliteMissionSavedData data, UUID satelliteId) {
        SatelliteState satellite = data.satellite(satelliteId).orElse(null);
        if (satellite == null || !(satellite.kindState() instanceof SatelliteKindState.Solar solar)) {
            return SatelliteManager.failure(SatelliteOperationCode.SATELLITE_NOT_FOUND);
        }
        return data.updateKindState(satelliteId, new SatelliteKindState.Solar(solar.outputMultiplierPercent(),
                Optional.empty()));
    }
}
