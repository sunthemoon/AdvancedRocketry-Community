package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * ADR-055 sections 3 and 5: what the drill's buttons change, after the ADR-054 section 4 guard allowed them. Logical
 * mode starts directly; physical mode needs a {@code confirm} from the same player within 200 ticks of {@code start}.
 * Changing the mode or the link stops the drill, so physical work always restarts through the confirmation. A link
 * changes only after a contact with no debt and no credit, or when its marker can no longer be contacted; the
 * unsettled difference is then abandoned with a {@code LINK_ABANDONED} line.
 */
final class LaserDrillIntents {
    static final int CONFIRM_WINDOW_TICKS = 200;

    private LaserDrillIntents() {
    }

    static EndgameCode start(OrbitalLaserDrillBlockEntity drill, EndgameDevices devices, UUID actor, long now) {
        if (drill.running()) {
            return EndgameCode.OK;
        }
        if (drill.ownerId().isEmpty()) {
            return EndgameCode.UNOWNED;
        }
        if (drill.mode() == LaserDrillMode.PHYSICAL) {
            EndgameCode ready = physicalReady(drill, devices);
            if (ready != EndgameCode.OK) {
                return ready;
            }
            drill.pending(new OrbitalLaserDrillBlockEntity.Pending(actor, now));
            drill.audit("start", EndgameCode.CONFIRM_REQUIRED, actor, "mode=PHYSICAL");
            return EndgameCode.CONFIRM_REQUIRED;
        }
        return run(drill, devices, actor, "mode=LOGICAL");
    }

    /** Only the player who pressed start, within 200 ticks, sets a physical drill running. */
    static EndgameCode confirm(OrbitalLaserDrillBlockEntity drill, EndgameDevices devices, UUID actor, long now) {
        Optional<OrbitalLaserDrillBlockEntity.Pending> pending = drill.pending();
        if (drill.running()) {
            return EndgameCode.OK;
        }
        if (drill.mode() != LaserDrillMode.PHYSICAL || pending.isEmpty() || !pending.get().actor().equals(actor)
                || now - pending.get().tick() > CONFIRM_WINDOW_TICKS) {
            return EndgameCode.CONFIRM_REQUIRED;
        }
        drill.pending(null);
        EndgameCode ready = physicalReady(drill, devices);
        return ready != EndgameCode.OK ? ready : run(drill, devices, actor, "mode=PHYSICAL confirmed=true");
    }

    private static EndgameCode physicalReady(OrbitalLaserDrillBlockEntity drill, EndgameDevices devices) {
        if (!devices.settings().laserPhysicalMining()) {
            return EndgameCode.PHYSICAL_DISABLED;
        }
        return drill.link().isPresent() ? EndgameCode.OK : EndgameCode.NO_TARGET;
    }

    /** Active admission, first come first served; beyond a limit {@code ACTIVE_LIMIT} (ADR-054 section 7). */
    private static EndgameCode run(OrbitalLaserDrillBlockEntity drill, EndgameDevices devices, UUID actor,
                                   String fields) {
        LaserDrillSettings settings = devices.laserSettings();
        if (!devices.active().admit(EndgameSystem.LASER_DRILL, drill.deviceId().orElseThrow(),
                drill.ownerId().orElseThrow(), settings.activePerOwner(), settings.activeGlobal())) {
            drill.audit("start", EndgameCode.ACTIVE_LIMIT, actor, fields);
            return EndgameCode.ACTIVE_LIMIT;
        }
        drill.running(true, devices);
        drill.audit("start", EndgameCode.OK, actor, fields);
        return EndgameCode.OK;
    }

    static EndgameCode stop(OrbitalLaserDrillBlockEntity drill, EndgameDevices devices, UUID actor) {
        drill.pending(null);
        if (drill.running()) {
            drill.running(false, devices);
            drill.audit("stop", EndgameCode.OK, actor, "");
        }
        return EndgameCode.OK;
    }

    static EndgameCode cycleRedstone(OrbitalLaserDrillBlockEntity drill, UUID actor) {
        drill.redstoneMode(drill.redstoneMode().next());
        drill.audit("redstone", EndgameCode.OK, actor, "mode=" + drill.redstoneMode().name());
        return EndgameCode.OK;
    }

    static EndgameCode toggleMode(OrbitalLaserDrillBlockEntity drill, EndgameDevices devices, UUID actor) {
        LaserDrillMode next = drill.mode() == LaserDrillMode.LOGICAL ? LaserDrillMode.PHYSICAL : LaserDrillMode.LOGICAL;
        if (next == LaserDrillMode.PHYSICAL && !devices.settings().laserPhysicalMining()) {
            return EndgameCode.PHYSICAL_DISABLED;
        }
        stop(drill, devices, actor);
        drill.mode(next);
        drill.audit("mode", EndgameCode.OK, actor, "mode=" + next.name());
        return EndgameCode.OK;
    }

    /**
     * Links the selected marker: an {@code ACTIVE} laser target, the drill owner's unless an operator links it, with
     * its footprint inside its chunk. Relinking the current marker changes nothing.
     */
    static EndgameCode link(OrbitalLaserDrillBlockEntity drill, EndgameDevices devices, EndgameRoot root, UUID actor,
                            boolean operator, UUID marker) {
        Optional<EndpointRecord> record = root.endpoint(marker).filter(found -> found.state()
                == EndpointRecord.State.ACTIVE && found.kind().equals(LaserTargetBlockEntity.KIND));
        if (record.isEmpty()) {
            return EndgameCode.ENDPOINT_NOT_FOUND;
        }
        UUID owner = drill.ownerId().orElse(null);
        boolean foreign = !record.get().owner().equals(owner);
        if (foreign && !operator) {
            return EndgameCode.TARGET_FOREIGN;
        }
        if (!LaserShaft.footprintInsideChunk(BlockPos.of(record.get().pos()))) {
            return EndgameCode.FOOTPRINT_AT_CHUNK_EDGE;
        }
        if (drill.link().filter(link -> link.marker().equals(marker)).isPresent()) {
            return EndgameCode.OK;
        }
        EndgameCode change = changeAllowed(drill, root);
        if (change != EndgameCode.OK) {
            return change;
        }
        abandonIfUnsettled(drill, root, actor);
        stop(drill, devices, actor);
        drill.link(new LaserLink(marker, UUID.randomUUID(), 0L, foreign));
        drill.audit("link", EndgameCode.OK, actor, "marker=" + marker + (foreign ? " operator_link=true" : ""));
        return EndgameCode.OK;
    }

    static EndgameCode unlink(OrbitalLaserDrillBlockEntity drill, EndgameDevices devices, EndgameRoot root,
                              UUID actor) {
        if (drill.link().isEmpty()) {
            return EndgameCode.OK;
        }
        EndgameCode change = changeAllowed(drill, root);
        if (change != EndgameCode.OK) {
            return change;
        }
        UUID marker = drill.link().get().marker();
        abandonIfUnsettled(drill, root, actor);
        stop(drill, devices, actor);
        drill.link(null);
        drill.audit("unlink", EndgameCode.OK, actor, "marker=" + marker);
        return EndgameCode.OK;
    }

    /** After a settled contact, or when the marker is gone, MISSING or answered LINK_LOST. */
    static EndgameCode changeAllowed(OrbitalLaserDrillBlockEntity drill, EndgameRoot root) {
        Optional<LaserLink> link = drill.link();
        if (link.isEmpty() || drill.linkSettled() || drill.linkLost() || unreachable(root, link.get().marker())) {
            return EndgameCode.OK;
        }
        return EndgameCode.LINK_UNSETTLED;
    }

    private static boolean unreachable(EndgameRoot root, UUID marker) {
        return root.endpoint(marker).filter(record -> record.state() == EndpointRecord.State.ACTIVE).isEmpty();
    }

    /** The difference is bounded by the crash gap; it is abandoned with one audit line (ADR-055 section 3). */
    private static void abandonIfUnsettled(OrbitalLaserDrillBlockEntity drill, EndgameRoot root, UUID actor) {
        Optional<LaserLink> current = drill.link();
        if (current.isEmpty()) {
            return;
        }
        LaserLink link = current.get();
        if (!drill.linkSettled()) {
            drill.audit("LINK_ABANDONED", EndgameCode.OK, actor, "marker=" + link.marker() + " ops_paid="
                    + link.opsPaid() + " reason=" + (unreachable(root, link.marker()) ? "marker_gone" : "link_lost"));
        }
    }
}
