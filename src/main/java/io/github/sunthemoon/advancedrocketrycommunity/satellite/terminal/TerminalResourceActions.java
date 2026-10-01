package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.ResourceMissions;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket.InstanceView;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket.MissionSummary;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket.Selection;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.GasTable;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.DeliveryTerminal;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.ResourceMissionRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.ResourceMissionService;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * ADR-051 terminal side of resource missions for one Satellite Terminal: reconciliation passes (≤ 64 missions
 * per tick), survey/asteroid/gas starts, claims into the reward buffer, cancels, withdrawal and the server-side
 * instance/product selection. Server thread only; selections are runtime state.
 */
final class TerminalResourceActions {
    static final int PERIOD_TICKS = 20;
    static final int MISSIONS_PER_PAGE = SatelliteTerminalViewPacket.MAX_MISSIONS_PER_PAGE;

    private final SatelliteTerminalBlockEntity terminal;
    private UUID selectedInstance;
    private ResourceLocation selectedProduct;
    private int missionPage;
    private UUID registeredId;
    private boolean loadObservationPending = true;
    /** The first pass after a load has completed; only it gates actions (C9-H1). */
    private boolean initialPassDone;
    private boolean failureReported;

    TerminalResourceActions(SatelliteTerminalBlockEntity terminal) {
        this.terminal = terminal;
    }

    /** A new root was read: its observation, directory entry and a reconciliation pass are due again. */
    void reloaded() {
        loadObservationPending = true;
        initialPassDone = false;
        failureReported = false;
    }

    void tick(ServerLevel level) {
        ResourceMissionService service = ResourceMissionRuntime.service().orElse(null);
        if (service == null) {
            return;
        }
        TerminalDelivery delivery = terminal.delivery();
        MinecraftServer server = level.getServer();
        prepare(level, service);
        if (level.getGameTime() % PERIOD_TICKS == 0L) {
            if (delivery.unpersisted()) {
                // ADR-051 section 5: keep the chunk dirty so the incremental save picks the terminal up.
                terminal.setChanged();
            }
            if (!delivery.passActive()) {
                delivery.startPass(service.awaitingDelivery(server, delivery.terminalId()));
            }
        }
        step(server, service);
        if (!delivery.passActive()) {
            initialPassDone = true;
        }
    }

    /**
     * Registers the terminal under its current ID, consumes its chunk-load observation and starts the load pass:
     * every receipt's mission and every claim this terminal paid without an acknowledgement (C9-H1).
     */
    private void prepare(ServerLevel level, ResourceMissionService service) {
        TerminalDelivery delivery = terminal.delivery();
        if (!delivery.terminalId().equals(registeredId)) {
            unregister(level);
            service.directory().register(delivery.terminalId(), level.dimension(), terminal.getBlockPos());
            registeredId = delivery.terminalId();
        }
        if (loadObservationPending) {
            loadObservationPending = false;
            service.observations().consume(delivery.terminalId(), terminal.getBlockPos())
                    .ifPresent(observation -> delivery.observed(delivery.terminalId(), observation.receipts()));
            delivery.startPass(service.awaitingDelivery(level.getServer(), delivery.terminalId()));
        }
    }

    void unregister(Level level) {
        if (registeredId != null) {
            ResourceMissionRuntime.service().ifPresent(service -> service.directory().unregister(registeredId,
                    level.dimension(), terminal.getBlockPos()));
            registeredId = null;
        }
    }

    SatelliteOperationCode start(ServerPlayer player, SatelliteIdentity chip) {
        ResourceMissionService service = ResourceMissionRuntime.service().orElse(null);
        if (service == null) {
            return SatelliteOperationCode.SERVER_ERROR;
        }
        SatelliteOperationCode gate = gate(player.getServer(), service, chip.kind() != SatelliteKind.SURVEY, null);
        if (gate != null) {
            return gate;
        }
        if (chip.kind() == SatelliteKind.SURVEY) {
            return service.startSurvey(player, chip).code();
        }
        // C9-L9: a terminal that delivers rewards has an owner, so no other player can withdraw them.
        terminal.setOwner(player.getUUID());
        SatelliteState satellite = SatelliteRuntime.satellite(player.getServer(), chip.satelliteId()).orElse(null);
        if (satellite == null) {
            return SatelliteOperationCode.SATELLITE_NOT_FOUND;
        }
        return switch (chip.kind()) {
            case ASTEROID_MINER -> service.startAsteroid(player, chip, instance(service, player.getServer(), satellite)
                    .map(AsteroidInstance::instanceId).orElse(null), here(player)).code();
            case GAS_HARVESTER -> product(service, satellite).map(chosen -> service.startGas(player, chip,
                    chosen.item(), here(player)).code()).orElse(SatelliteOperationCode.DEFINITION_NOT_FOUND);
            default -> SatelliteOperationCode.DEFINITION_NOT_FOUND;
        };
    }

    SatelliteOperationCode claim(ServerPlayer player, SatelliteIdentity chip) {
        ResourceMissionService service = ResourceMissionRuntime.service().orElse(null);
        if (service == null) {
            return SatelliteOperationCode.SERVER_ERROR;
        }
        SatelliteOperationCode gate = gate(player.getServer(), service, chip.kind() != SatelliteKind.SURVEY,
                currentMission(player, chip));
        if (gate != null) {
            return gate;
        }
        if (chip.kind() == SatelliteKind.SURVEY) {
            return service.claimSurvey(player, chip).code();
        }
        TerminalDelivery delivery = terminal.delivery();
        SatelliteOperationResult result = service.claimResource(player, chip, here(player), delivery::room);
        // C9-L1: only a claim that this call made pays; a replay or an already-claimed mission never does.
        if (result.code() == SatelliteOperationCode.SUCCESS && result.changed()) {
            MissionState claimed = result.mission().orElseThrow();
            // ADR-051 section 6: the reward and an unpersisted receipt land in the same server tick.
            delivery.pay(claimed.missionId(), ((MissionPayload.Resource) claimed.payload()).reward());
            terminal.setChanged();
        }
        return result.code();
    }

    SatelliteOperationCode cancel(ServerPlayer player, SatelliteIdentity chip) {
        ResourceMissionService service = ResourceMissionRuntime.service().orElse(null);
        if (service == null) {
            return SatelliteOperationCode.SERVER_ERROR;
        }
        SatelliteOperationCode gate = gate(player.getServer(), service, true, currentMission(player, chip));
        if (gate != null) {
            return gate;
        }
        boolean operator = !chip.ownerId().equals(player.getUUID()) && player.hasPermissions(2);
        return service.cancel(player, chip, operator, here(player)).code();
    }

    /** ADR-051 section 5: moves up to one stack into the player's inventory; the rest stays buffered. */
    SatelliteOperationCode withdraw(ServerPlayer player) {
        TerminalDelivery delivery = terminal.delivery();
        for (RewardEntry entry : delivery.buffer()) {
            Item item = ForgeRegistries.ITEMS.getValue(entry.item());
            if (item == null || item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item, Math.min(entry.count(), new ItemStack(item).getMaxStackSize()));
            int offered = stack.getCount();
            player.getInventory().add(stack);
            int moved = offered - stack.getCount();
            if (moved <= 0) {
                return SatelliteOperationCode.OUTPUT_BLOCKED;
            }
            delivery.take(entry.item(), moved);
            terminal.setChanged();
            return SatelliteOperationCode.SUCCESS;
        }
        return delivery.buffer().isEmpty() ? SatelliteOperationCode.IDEMPOTENT : SatelliteOperationCode.UNSUPPORTED_DATA;
    }

    /** Previous/next among the craft's instances (asteroid miner) or products (gas harvester). */
    SatelliteOperationCode select(ServerPlayer player, SatelliteIdentity chip, int delta) {
        ResourceMissionService service = ResourceMissionRuntime.service().orElse(null);
        SatelliteState satellite = SatelliteRuntime.satellite(player.getServer(), chip.satelliteId()).orElse(null);
        if (service == null || satellite == null) {
            return SatelliteOperationCode.SATELLITE_NOT_FOUND;
        }
        if (chip.kind() == SatelliteKind.ASTEROID_MINER) {
            List<AsteroidInstance> instances = service.instancesFor(player.getServer(), satellite);
            if (instances.isEmpty()) {
                return SatelliteOperationCode.INSTANCE_NOT_FOUND;
            }
            int index = Math.floorMod(indexOf(instances.stream().map(AsteroidInstance::instanceId).toList(),
                    selectedInstance) + delta, instances.size());
            selectedInstance = instances.get(index).instanceId();
            return SatelliteOperationCode.SUCCESS;
        }
        if (chip.kind() == SatelliteKind.GAS_HARVESTER) {
            List<GasTable.Product> products = service.productsFor(satellite);
            if (products.isEmpty()) {
                return SatelliteOperationCode.DEFINITION_NOT_FOUND;
            }
            int index = Math.floorMod(indexOf(products.stream().map(GasTable.Product::item).toList(),
                    selectedProduct) + delta, products.size());
            selectedProduct = products.get(index).item();
            return SatelliteOperationCode.SUCCESS;
        }
        return SatelliteOperationCode.DEFINITION_NOT_FOUND;
    }

    void nextPage(MinecraftServer server) {
        int pages = pages(bound(server).size());
        missionPage = Math.floorMod(missionPage + 1, pages);
    }

    /** The ADR-049 section 10 resource sections of the terminal view for this chip. */
    Sections sections(MinecraftServer server, Optional<SatelliteIdentity> chip) {
        ResourceMissionService service = ResourceMissionRuntime.service().orElse(null);
        List<RewardEntry> buffer = terminal.delivery().buffer();
        if (service == null || server == null || !SatelliteMissionSavedData.get(server).operational()) {
            return new Sections(Selection.NONE, Optional.empty(), Selection.NONE, 0, 0, List.of(), buffer);
        }
        SatelliteState satellite = chip.flatMap(identity -> SatelliteRuntime.satellite(server,
                identity.satelliteId())).orElse(null);
        long now = SatelliteMissionSavedData.get(server).logicalGameTime();
        Selection instanceSelection = Selection.NONE;
        Optional<InstanceView> instanceView = Optional.empty();
        Selection productSelection = Selection.NONE;
        if (satellite != null && satellite.kind() == SatelliteKind.ASTEROID_MINER) {
            List<AsteroidInstance> instances = service.instancesFor(server, satellite);
            Optional<AsteroidInstance> chosen = instance(service, server, satellite);
            if (chosen.isPresent()) {
                int index = instances.indexOf(chosen.orElseThrow());
                instanceSelection = new Selection(Optional.of(chosen.orElseThrow().asteroidType()), index,
                        instances.size());
                long expiresIn = Math.max(0L, chosen.orElseThrow().expiresAt().orElse(now) - now);
                instanceView = Optional.of(new InstanceView(chosen.orElseThrow().instanceId(),
                        (int) Math.min(Integer.MAX_VALUE, (expiresIn + 19L) / 20L), chosen.orElseThrow().yield()));
            }
        } else if (satellite != null && satellite.kind() == SatelliteKind.GAS_HARVESTER) {
            List<ResourceLocation> products = service.productsFor(satellite).stream().map(GasTable.Product::item)
                    .toList();
            productSelection = product(service, satellite)
                    .map(chosen -> new Selection(Optional.of(chosen.item()), products.indexOf(chosen.item()),
                            products.size())).orElse(Selection.NONE);
        }
        List<MissionState> bound = bound(server);
        int pages = pages(bound.size());
        int page = Math.min(missionPage, pages - 1);
        List<MissionSummary> missions = new ArrayList<>();
        for (MissionState mission : bound.subList(page * MISSIONS_PER_PAGE,
                Math.min(bound.size(), (page + 1) * MISSIONS_PER_PAGE))) {
            long remaining = mission.status() == MissionStatus.ACTIVE
                    ? Math.max(0L, mission.completesAtLogicalTime() - now) : 0L;
            int items = ((MissionPayload.Resource) mission.payload()).reward().stream()
                    .mapToInt(RewardEntry::count).sum();
            missions.add(new MissionSummary(mission.missionId(), mission.kind(), mission.status(),
                    mission.targetBodyId(), (int) Math.min(Integer.MAX_VALUE, (remaining + 19L) / 20L), items));
        }
        return new Sections(instanceSelection, instanceView, productSelection, page, bound.isEmpty() ? 0 : pages,
                missions, buffer);
    }

    record Sections(Selection instance, Optional<InstanceView> instanceView, Selection product, int page, int pages,
                    List<MissionSummary> missions, List<RewardEntry> buffer) {
    }

    /**
     * ADR-051 section 7: resource actions are refused while the registry is blocked. A delivery action waits for
     * the first reconciliation pass after the terminal loaded (at most a few ticks), then reconciles the row of
     * the mission it acts on, so a receipt this terminal holds is applied before a claim or cancel (C9-H1).
     * Later periodic passes never refuse an action. Null when the action may proceed.
     */
    private SatelliteOperationCode gate(MinecraftServer server, ResourceMissionService service, boolean delivery,
                                        UUID missionId) {
        if (!SatelliteMissionSavedData.get(server).operational()) {
            return SatelliteOperationCode.UNSUPPORTED_DATA;
        }
        if (!delivery || !(terminal.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        prepare(level, service);
        if (!initialPassDone) {
            step(server, service);
            if (terminal.delivery().passActive()) {
                return SatelliteOperationCode.RECONCILING;
            }
            initialPassDone = true;
        }
        // C9R2-L2: if the acted-on mission's row cannot be reconciled, the action is refused, never taken blind.
        return missionId != null && !apply(server, service, missionId) ? SatelliteOperationCode.SERVER_ERROR : null;
    }

    private static UUID currentMission(ServerPlayer player, SatelliteIdentity chip) {
        return SatelliteRuntime.satellite(player.getServer(), chip.satelliteId())
                .flatMap(SatelliteState::currentMissionId).orElse(null);
    }

    private void step(MinecraftServer server, ResourceMissionService service) {
        TerminalDelivery delivery = terminal.delivery();
        for (UUID missionId : delivery.nextBatch(TerminalDelivery.RECONCILE_PER_TICK)) {
            apply(server, service, missionId);
        }
    }

    /**
     * One reconciliation row: the service applies the registry side, the delivery section the terminal side. A
     * failing row is logged once per load and skipped, so the terminal keeps ticking (C9-L2); false then.
     */
    private boolean apply(MinecraftServer server, ResourceMissionService service, UUID missionId) {
        try {
            TerminalDelivery delivery = terminal.delivery();
            DeliveryTerminal here = here(server);
            ResourceMissions.Reconciled reconciled = service.reconcile(server, here, missionId,
                    delivery.receipt(missionId));
            String event = delivery.apply(reconciled.action(), missionId, reconciled.mission());
            if (event == null) {
                return true;
            }
            if (event.equals("RECEIPT_DROPPED") || event.equals("REMATERIALIZED")) {
                terminal.setChanged();
            }
            if (reconciled.mission().isPresent()) {
                service.audit(server, event, reconciled.mission().orElseThrow(), here.id(), "");
            } else {
                service.auditAbsent(server, event, missionId, here.id(), terminal.ownerOrNil());
            }
            return true;
        } catch (RuntimeException exception) {
            if (!failureReported) {
                failureReported = true;
                io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.error(
                        "Satellite terminal reconciliation failed for mission {} (reported once per load)",
                        missionId, exception);
            }
            return false;
        }
    }

    private List<MissionState> bound(MinecraftServer server) {
        return ResourceMissionRuntime.service().map(service -> service.boundTo(server,
                terminal.delivery().terminalId())).orElse(List.of());
    }

    private Optional<AsteroidInstance> instance(ResourceMissionService service, MinecraftServer server,
                                                SatelliteState satellite) {
        List<AsteroidInstance> instances = service.instancesFor(server, satellite);
        return instances.stream().filter(candidate -> candidate.instanceId().equals(selectedInstance)).findFirst()
                .or(() -> instances.stream().findFirst());
    }

    private Optional<GasTable.Product> product(ResourceMissionService service, SatelliteState satellite) {
        List<GasTable.Product> products = service.productsFor(satellite);
        return products.stream().filter(candidate -> candidate.item().equals(selectedProduct)).findFirst()
                .or(() -> products.stream().findFirst());
    }

    private DeliveryTerminal here(ServerPlayer player) {
        return here(player.getServer());
    }

    private DeliveryTerminal here(MinecraftServer server) {
        ResourceKey<Level> level = terminal.getLevel() == null ? Level.OVERWORLD : terminal.getLevel().dimension();
        return new DeliveryTerminal(terminal.delivery().terminalId(), terminal.delivery().idPersisted(), level,
                terminal.getBlockPos());
    }

    private static <T> int indexOf(List<T> values, T selected) {
        int index = values.indexOf(selected);
        return index < 0 ? 0 : index;
    }

    private static int pages(int missions) {
        return Math.max(1, (missions + MISSIONS_PER_PAGE - 1) / MISSIONS_PER_PAGE);
    }
}
