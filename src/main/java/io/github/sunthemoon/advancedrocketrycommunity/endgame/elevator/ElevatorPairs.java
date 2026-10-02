package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStations;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.Tombstone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointValidator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * ADR-059 sections 3, 4 and 7 on a live server: the ADR-045 rules over a snapshot of live state, validity at use,
 * ship and ride access, and the bind and unbind writes (each a barrier flush, audited). It reads the station registry,
 * the catalog and the endgame root only; it never loads a chunk or touches the far end.
 */
public final class ElevatorPairs {
    private static final StationAccessService ACCESS = new StationAccessService();

    private ElevatorPairs() {
    }

    /** ADR-045 rules 1 to 5 for (station, body, x, z) from live state; an operator skips rule 2. */
    static ElevatorEndpointValidator.Result rules(MinecraftServer server, EndgameDevices devices, UUID stationId,
                                                 ResourceLocation body, int x, int z, UUID actor, boolean operator) {
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        ElevatorEndpointValidator.Snapshot snapshot = new ElevatorEndpointValidator.Snapshot(data.updatesAvailable(),
                data.find(stationId).map(station -> new ElevatorEndpointValidator.StationView(station.stationId(),
                        station.ownerId(), station.orbitBody(), station.landingPad().x(), station.landingPad().z())),
                actor, operator, devices.celestial(), level -> server.getLevel(level) != null,
                (level, column, row) -> {
                    ServerLevel target = server.getLevel(level);
                    // The border is plain Level state; reading it never touches a chunk.
                    return target != null && target.getWorldBorder().isWithinBounds(new BlockPos(column, 0, row));
                });
        return ElevatorEndpointValidator.validate(new ElevatorEndpointValidator.Request(stationId, body, x, z),
                snapshot);
    }

    /**
     * Validity at use (section 3), re-derived every time: ADR-045 rules 1, 3, 4 and 5, the stored Level key still the
     * body's Level, and both endpoints ACTIVE.
     */
    public static ElevatorRules.Check validity(MinecraftServer server, EndgameRoot root, EndgameDevices devices,
                                               ElevatorPair pair) {
        ElevatorEndpointValidator.Result result = rules(server, devices, pair.stationId(), pair.bodyId(), pair.x(),
                pair.z(), pair.boundBy(), true);
        boolean levelCurrent = result.level().map(key -> key.location().equals(pair.levelKey())).orElse(true);
        return ElevatorRules.validity(result.code(), levelCurrent, active(root, pair.terminalId()),
                active(root, pair.anchorId()));
    }

    private static boolean active(EndgameRoot root, UUID id) {
        return root.endpoint(id).filter(record -> record.state() == EndpointRecord.State.ACTIVE).isPresent();
    }

    /**
     * Ship and ride access (section 7): station {@code VISIT}, and the anchor's owner is the station's owner or a
     * member; operators are exempt.
     */
    public static EndgameCode access(MinecraftServer server, EndgameRoot root, ElevatorPair pair, UUID actor,
                                     boolean operator) {
        Optional<StationState> station = StationRegistrySavedData.get(server).find(pair.stationId());
        if (station.isEmpty()) {
            return operator ? EndgameCode.OK : EndgameCode.UNAUTHORIZED;
        }
        boolean visit = ACCESS.allowed(station.get(), actor, false, StationAccessAction.VISIT);
        Optional<UUID> anchorOwner = owner(root, pair.anchorId());
        boolean anchorOwnerInStation = anchorOwner.filter(owner -> station.get().ownerId().equals(owner)
                || station.get().members().contains(owner)).isPresent();
        return ElevatorRules.access(operator, visit, anchorOwnerInStation);
    }

    /** An endpoint's owner, from its index record or its tombstone. */
    static Optional<UUID> owner(EndgameRoot root, UUID id) {
        Optional<EndpointRecord> record = root.endpoint(id);
        return record.isPresent() ? record.map(EndpointRecord::owner) : root.tombstone(id).map(Tombstone::owner);
    }

    /**
     * Bind (section 3), from the terminal's menu: the first failure in the section's order, or {@code OK} once the
     * pair is written by a barrier flush. Concurrent binds are serialized on the server thread: the cardinality is
     * checked again inside the write.
     */
    public static ElevatorRules.Check bind(MinecraftServer server, EndgameService service, EndgameDevices devices,
                                           ElevatorTerminalBlockEntity terminal, UUID anchorId, UUID actor,
                                           boolean operator, long now) {
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return ElevatorRules.Check.of(EndgameCode.ROOT_UNAVAILABLE);
        }
        EndgameRoot root = view.get();
        Optional<UUID> stationId = terminal.station();
        Optional<EndpointRecord> anchor = root.endpoint(anchorId)
                .filter(record -> record.kind().equals(ElevatorAnchorBlockEntity.KIND));
        if (stationId.isEmpty()) {
            return ElevatorRules.Check.of(EndgameCode.TERMINAL_UNAVAILABLE);
        }
        if (anchor.isEmpty()) {
            return ElevatorRules.Check.of(EndgameCode.ANCHOR_UNAVAILABLE);
        }
        Optional<EndgameStations.Body> body = devices.body(server, anchor.get().level(), anchor.get().pos());
        if (body.isEmpty()) {
            return ElevatorRules.Check.rule(ElevatorEndpointCode.NO_SURFACE);
        }
        BlockPos at = BlockPos.of(anchor.get().pos());
        UUID terminalId = terminal.endpointId();
        ElevatorPair.Column column = new ElevatorPair.Column(anchor.get().level(), at.getX(), at.getZ());
        ElevatorEndpointValidator.Result rule = rules(server, devices, stationId.get(), body.get().body(), at.getX(),
                at.getZ(), actor, operator);
        ElevatorRules.Check check = ElevatorRules.bind(new ElevatorRules.BindFacts(rule.code(),
                terminal.endpointActive() && terminal.placement() == EndgameCode.OK,
                anchor.get().state() == EndpointRecord.State.ACTIVE, anchor.get().owner().equals(actor), operator,
                root.pairs().conflict(stationId.get(), anchorId, terminalId, column),
                StationWarpRuntime.service().map(warp -> warp.warpPending(server, stationId.get())).orElse(false),
                admission(root)));
        if (!check.ok()) {
            audit(service, now, "bind", check, terminal.deviceId().orElse(null), actor, "anchor=" + anchorId);
            return check;
        }
        ElevatorPair pair = new ElevatorPair(UUID.randomUUID(), stationId.get(), terminalId, anchorId,
                body.get().body(), anchor.get().level(), at.getX(), at.getZ(), at.getY(), now, actor);
        EndgameCode written = service.barrier(r -> {
            EndgameCode conflict = r.pairs().conflict(pair.stationId(), anchorId, terminalId, column);
            if (conflict == EndgameCode.OK) {
                r.pairs().add(pair);
            }
            return conflict;
        });
        if (written != EndgameCode.OK) {
            return ElevatorRules.Check.of(written);
        }
        if (service.writePending()) {
            // The pair is kept and written by the next flush; the bind is not reported until it is durable.
            return ElevatorRules.Check.of(EndgameCode.ROOT_BUSY);
        }
        audit(service, now, "bind", ElevatorRules.Check.OK, terminalId, actor, describe(pair));
        return ElevatorRules.Check.OK;
    }

    /** Section 1 admission for one more pair: at most 1,024, and the root's growth bound. */
    static EndgameCode admission(EndgameRoot root) {
        if (root.pairs().size() >= EndgameLimits.MAX_PAIRS) {
            return EndgameCode.PAIR_LIMIT;
        }
        return root.accountedBytes() + EndgameLimits.PAIR_RECORD_BYTES > EndgameLimits.GROWTH_ADMISSION_BYTES
                ? EndgameCode.ROOT_FULL : EndgameCode.OK;
    }

    /**
     * Unbind (section 4): always possible for the station owner, the anchor owner or an operator, whatever the pair's
     * validity; never loads the far end; cancels the pair's pending rides; a barrier flush.
     */
    public static ElevatorRules.Check unbind(MinecraftServer server, EndgameService service, EndgameDevices devices,
                                             ElevatorPair pair, UUID actor, boolean operator, long now) {
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return ElevatorRules.Check.of(EndgameCode.ROOT_UNAVAILABLE);
        }
        boolean stationOwner = StationRegistrySavedData.get(server).find(pair.stationId())
                .filter(station -> station.ownerId().equals(actor)).isPresent();
        boolean anchorOwner = owner(view.get(), pair.anchorId()).filter(actor::equals).isPresent();
        if (!ElevatorRules.mayUnbind(operator, stationOwner, anchorOwner)) {
            return ElevatorRules.Check.of(EndgameCode.UNAUTHORIZED);
        }
        service.barrier(root -> root.pairs().remove(pair.pairId()));
        devices.elevatorRides().cancelPair(server, pair.pairId());
        audit(service, now, "unbind", ElevatorRules.Check.OK, pair.terminalId(), actor, describe(pair));
        return ElevatorRules.Check.OK;
    }

    static String describe(ElevatorPair pair) {
        return "pair=" + pair.pairId() + " station=" + pair.stationId() + " terminal=" + pair.terminalId() + " anchor="
                + pair.anchorId() + " body=" + pair.bodyId() + " level=" + pair.levelKey() + " column=" + pair.x() + ","
                + pair.z();
    }

    static void audit(EndgameService service, long now, String action, ElevatorRules.Check check,
                      @Nullable UUID device, @Nullable UUID actor, String fields) {
        service.audit().line(now, EndgameSystem.SPACE_ELEVATOR.id(), action, check.code().name(), device, null, actor,
                check.rule().map(rule -> "rule=" + rule.name() + " ").orElse("") + fields);
    }
}
