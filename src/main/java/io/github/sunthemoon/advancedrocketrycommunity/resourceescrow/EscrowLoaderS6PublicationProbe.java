package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** W220's release-only add-on-style spawn veto; no executor decisions or native stores. */
final class EscrowLoaderS6PublicationProbe {
    static final String RETURN = "LF_S6_PUBLICATION_RETURN", HELD = "LF_S6_PUBLICATION_HELD";
    static final String ROUND = "advancedrocketrycommunity.escrowS6PublicationRound";
    private final MinecraftServer server;
    private final String cut;
    private final int round;
    private final Consumer<EntityJoinLevelEvent> listener = this::join;
    private RocketTransferRecord vetoed;
    private CompoundTag vetoSource;
    private int vetoes;
    private volatile boolean returnReadback;
    private boolean done;

    static boolean selected(String cut) { return RETURN.equals(cut) || HELD.equals(cut); }
    static boolean vetoRound(String cut, int round) { return RETURN.equals(cut) && round == 0 || HELD.equals(cut) && round == 1; }
    static boolean target(boolean hooks, boolean enabled, String cut, int round, boolean fromDisk,
                          UUID candidate, UUID destination, RocketTransferPhase phase) {
        return hooks && enabled && vetoRound(cut, round) && !fromDisk && destination != null && destination.equals(candidate)
                && phase == (RETURN.equals(cut) ? RocketTransferPhase.DESTINATION_SPAWNED : RocketTransferPhase.PASSENGERS_TRANSFERRED);
    }

    EscrowLoaderS6PublicationProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        this.server = server; this.cut = cut; round = Integer.getInteger(ROUND, -1);
        EscrowTags.require(selected(cut) && round >= 0 && round <= 2 && recovery == (round != 0),
                "Publication fixture requires its exact process round");
        if (vetoRound(cut, round)) {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, listener);
        }
    }

    private void join(EntityJoinLevelEvent event) {
        if (done || event.loadedFromDisk() || event.getLevel() != server.overworld()
                || !(event.getEntity() instanceof RocketEntity created)) { return; }
        var journal = RocketTransferSavedData.get(server);
        var rows = journal.entries();
        if (rows.size() != 1 || rows.get(0).nativeTransfer().isEmpty()) { return; }
        var row = rows.get(0); var arm = row.nativeTransfer().orElseThrow().armed();
        if (!target(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), EscrowTestEndpoints.enabled(server), cut, round,
                event.loadedFromDisk(), created.getUUID(), arm.destination(), row.phase())) { return; }
        var source = (RocketEntity) server.overworld().getEntity(row.sourceEntityId());
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(vetoes == 0 && source != null && journal.acknowledgedCurrent()
                && data.ledger().armedFlights().equals(java.util.List.of(arm))
                && !RocketEscrowGate.resourcesIdle(source)
                && row.nativeTransfer().orElseThrow().destinationEnvelope(row).equals(EscrowGovernedEnvelope.capture(created)),
                "Publication veto lacks its exact checked authority or created envelope");
        vetoed = row; vetoSource = EscrowGovernedEnvelope.capture(source); vetoes++;
        var checked = CheckedSavedDataFile.readPayload(server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()), ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow();
        var stored = checked.getList("transfers", 10);
        EscrowTags.require(stored.size() == 1 && stored.getCompound(0).getUUID("transfer_id").equals(row.transferId())
                && stored.getCompound(0).getString("phase").equals(row.phase().name()), "Veto lost its checked journal phase");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PUBLICATION_VETO cut={} phase={} transfer={} destination={} source_time={} source_digest={} transfer_digest={} count=1",
                cut, row.phase(), row.transferId(), arm.destination(), source.flightData().orElseThrow().stateStartedGameTime(),
                EscrowGovernedEnvelope.digest(vetoSource), EscrowNativeEnvelope.digest(stored.getCompound(0)));
        event.setCanceled(true);
    }

    void checkpoint(String point, RocketEntity source) {
        if (done || round != 0) { return; }
        if (point.equals("F4_PUBLICATION_BEFORE")) { EscrowLoaderS6PhaseProbe.source(cut, source); }
        if (HELD.equals(cut) && point.equals("F5_PASSENGERS_TRANSFERRED_ACK")) {
            EscrowLoaderS6PhaseProbe.checked(server, cut);
            EscrowLoaderS6JournalProbe.pause(cut, point);
        }
        if (!RETURN.equals(cut)) { return; }
        if (point.equals("RETURN_APPLIED")) {
            EscrowTags.require(vetoes == 1 && EscrowGovernedReturnAction.returned(vetoed, EscrowGovernedEnvelope.capture(source)),
                    "Publication failure did not take the exact-source return");
            returned(source);
        }
        if (point.equals("RETURN_F6_SLOT_1")) { returnReadback = true; }
        if (returnReadback && point.equals("BARRIER_READBACK_AFTER")) {
            EscrowLoaderS6PhaseProbe.checked(server, cut);
            EscrowLoaderS6JournalProbe.pause(cut, "RETURN_F6_READBACK_1");
        }
    }

    void recover(RocketTransferSavedData journal) {
        if (done) { return; }
        if (HELD.equals(cut) && round == 1) {
            if (vetoed == null) { return; }
            var source = (RocketEntity) server.overworld().getEntity(vetoed.sourceEntityId());
            var data = ResourceEscrowSavedData.existing(server);
            EscrowTags.require(vetoes == 1 && journal.acknowledgedCurrent() && journal.find(vetoed.transferId()).filter(vetoed::equals).isPresent()
                    && data.ledger().armedFlights().equals(java.util.List.of(vetoed.nativeTransfer().orElseThrow().armed()))
                    && source != null && vetoSource.equals(EscrowGovernedEnvelope.capture(source))
                    && !RocketEscrowGate.resourcesIdle(source) && !EscrowRuntime.of(server).returns().handles(vetoed, source)
                    && server.overworld().getEntity(vetoed.nativeTransfer().orElseThrow().armed().destination()) == null,
                    "S6 native image is not explained by its durable record: publication retry changed its held source");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PUBLICATION_HELD cut={} source_locked=true destination_absent=true return=false", cut);
            EscrowLoaderS6JournalProbe.pause(cut, "PUBLICATION_HELD");
            return;
        }
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null || !data.bind(server).writerIdle() || !journal.actionAuthorityAvailable()
                || !journal.entries().isEmpty() || !data.ledger().armedFlights().isEmpty()) { return; }
        if (!(server.overworld().getBlockEntity(new BlockPos(66, 100, 64)) instanceof FluidFuelLoaderBlockEntity loader)) { return; }
        var rockets = server.overworld().getEntitiesOfClass(RocketEntity.class, new AABB(63, 0, 63, 83, 300, 67));
        if (rockets.isEmpty()) { return; }
        EscrowTags.require(rockets.size() == 1 && data.ledger().writerGeneration() == 6 && data.ledger().records().isEmpty()
                && amount(loader) == 4000 && section(loader).epoch() == 1 && section(loader).markers().isEmpty(),
                "Publication recovery changed its loader or final authority");
        var survivor = rockets.get(0); var state = survivor.flightData().orElseThrow(); boolean returned = RETURN.equals(cut);
        EscrowTags.require(RocketEscrowGate.idle(survivor) && bank(survivor, false) == (returned ? 1000 : 500)
                && bank(survivor, true) == 0 && state.fuel().committedDebits().size() == (returned ? 0 : 1)
                && survivor.escrowSection().epoch() == (returned ? 1 : 0) && survivor.escrowSection().markers().isEmpty()
                && state.state() == (returned ? RocketFlightState.FUELED : RocketFlightState.LANDED)
                && survivor.getX() == (returned ? 64.5 : 80.5) && survivor.getY() == 100 && survivor.getZ() == 64.5,
                "Publication recovery changed its authorized survivor");
        if (returned) { returned(survivor); }
        else {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_SURVIVOR cut={} landed_time={} digest={}", cut,
                    state.stateStartedGameTime(), EscrowGovernedEnvelope.digest(EscrowGovernedEnvelope.capture(survivor)));
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PUBLICATION_RESULT cut={} outcome={}", cut, returned ? "CANCELLED" : "LANDED");
        done = true; MinecraftForge.EVENT_BUS.unregister(listener);
    }

    private void returned(RocketEntity source) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PUBLICATION_RETURNED cut={} time={} digest={}", cut,
                source.flightData().orElseThrow().stateStartedGameTime(), EscrowGovernedEnvelope.digest(EscrowGovernedEnvelope.capture(source)));
    }
}
