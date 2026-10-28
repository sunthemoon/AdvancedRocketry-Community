package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** Empty-server, property-gated native F7/F8 cuts. No extra fixture save precedes a cut. */
public final class EscrowTerminalProbe {
    private static final List<String> CASES = List.of("FT_HANDOFF_BEFORE", "FT_HANDOFF_REPLACED", "FT_HANDOFF_ACK",
            "FT_JOURNAL_BEFORE", "FT_JOURNAL_REPLACED", "FT_JOURNAL_ACK",
            "FT_PRUNE_BEFORE", "FT_PRUNE_REPLACED", "FT_PRUNE_ACK");
    private static final List<BlockPos> POSITIONS = List.of(new BlockPos(64, 100, 64), new BlockPos(80, 100, 64));
    private final MinecraftServer server;
    private final String cut;
    private final String stageCut;
    private final boolean cancel;
    private final boolean recovery;
    private final java.nio.file.Path ledgerFile;
    private volatile boolean started;
    private boolean discarded;
    private boolean done;
    private boolean accepted, requested;

    EscrowTerminalProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        cancel = cut.startsWith("FT_CANCEL_");
        stageCut = cancel ? cut.replace("FT_CANCEL_", "FT_") : cut;
        EscrowTags.require(CASES.contains(stageCut), "Unknown terminal cut");
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        ledgerFile = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        for (var position : POSITIONS) {
            var chunk = new ChunkPos(position);
            server.overworld().getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            server.overworld().getChunk(chunk.x, chunk.z);
        }
    }

    void checkpoint(String point) {
        if (recovery || !started) { return; }
        if (stageCut.equals("FT_HANDOFF_ACK") && point.equals("F7_ACK")
                || stageCut.equals("FT_JOURNAL_ACK") && point.equals("F8_JOURNAL_ACK")
                || stageCut.equals("FT_PRUNE_ACK") && point.equals("F8_PRUNE_ACK")) { pause(cut); }
        boolean before = point.equals("K1") || point.equals("K8");
        boolean after = point.equals("K2") || point.equals("K9");
        if (!(before && cut.endsWith("_BEFORE") || after && cut.endsWith("_REPLACED"))
                || stageCut.startsWith("FT_JOURNAL")) { return; }
        var type = ManagedSavedDataType.RESOURCE_ESCROW;
        var file = before ? ledgerFile.resolveSibling(type.fileName() + ".arce-pending") : ledgerFile;
        var root = EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, type).orElseThrow());
        if (root.issuedLaunchSerial() != 1) { return; }
        boolean terminal = root.armedFlights().size() == 1 && root.armedFlights().get(0).outcome()
                == (cancel ? EscrowFlightTables.Outcome.CANCELLED : EscrowFlightTables.Outcome.HANDED_OFF);
        if (stageCut.startsWith("FT_HANDOFF") && terminal || stageCut.startsWith("FT_PRUNE") && root.armedFlights().isEmpty()) { pause(cut); }
    }

    /** The owner selects this seam; its IO closure reads only checked files and immutable configuration. */
    public static CheckedSavedDataPolicy journalPolicy(MinecraftServer server, CheckedSavedDataPolicy policy) {
        String cut = System.getProperty("advancedrocketrycommunity.escrowCase", "");
        String stageCut = cut.replace("FT_CANCEL_", "FT_");
        if (server == null || !Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                || !"start".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"))
                || !(stageCut.equals("FT_JOURNAL_BEFORE") || stageCut.equals("FT_JOURNAL_REPLACED"))) { return policy; }
        EscrowTestEndpoints.requireEnabled(server);
        return policy.withCommitter((staged, target) -> {
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            var candidate = CheckedSavedDataFile.readPayload(staged, type).orElseThrow();
            var previous = CheckedSavedDataFile.readPayload(target, type);
            boolean removal = candidate.getList("transfers", 10).isEmpty()
                    && previous.filter(tag -> tag.getList("transfers", 10).size() == 1).isPresent();
            if (removal && stageCut.equals("FT_JOURNAL_BEFORE")) { pause(cut); }
            policy.committer().commit(staged, target);
            if (removal && stageCut.equals("FT_JOURNAL_REPLACED")) { pause(cut); }
        });
    }

    void tick() {
        if (done || !loaded()) { return; }
        var level = server.overworld(); var data = ResourceEscrowSavedData.forTests(server); data.bind(server);
        var journal = RocketTransferSavedData.get(server); if (!journal.retryChecked(server)) { return; }
        if (!recovery && !started) {
            EscrowTags.require(data.ledger().issuedLaunchSerial() == 0 && journal.entries().isEmpty(), "Terminal fixture is not fresh");
            var source = EscrowRocketAuditProbe.createRocket(level, UUID.randomUUID(), true);
            if (cancel) {
                var base = source.flightData().orElseThrow();
                var filled = base.fuel().withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1_000);
                source.updateFlightData(base.withFuel(RocketFuelState.restorePropulsion(filled.propulsion().orElseThrow(),
                        List.of(UUID.randomUUID())), level.getGameTime()));
            }
            var tag = source.saveWithoutId(new CompoundTag());
            tag.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), cancel ? 7 : 0, List.of()).encode());
            source.load(tag); EscrowTags.require(level.addFreshEntity(source), "Terminal source publication failed");
            var base = source.flightData().orElseThrow(); long now = level.getGameTime();
            var fuel = cancel ? base.fuel() : base.fuel().withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1_000);
            if (!cancel) { source.updateFlightData(base.withFuel(fuel, now)); }
            EscrowTags.require(source.flightData().orElseThrow().passengers().assignments().isEmpty(), "Native terminal fixture must be uncrewed");
            var from = source.snapshot().orElseThrow();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(80, 100, 64), now);
            var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("F7 F8 native fixture")));
            started = true;
            EscrowTags.require(EscrowGovernedPreparation.begin(source, source.flightData().orElseThrow().withPlan(plan).startCountdown(now),
                    to, record -> loaded(), record -> accepted = true, () -> discarded = true), "Terminal F1 preparation failed");
            return;
        }
        EscrowTags.require(!discarded, "Terminal preparation lost its exact callback");
        if (!recovery && cancel && accepted && !requested && journal.actionAuthorityAvailable()) {
            var record = journal.entries().stream().findFirst().orElseThrow();
            var source = (RocketEntity) level.getEntity(record.sourceEntityId());
            EscrowTags.require(source != null && source.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN,
                    "Terminal cancellation lost its live countdown");
            // W182: the server serves a flight intent only from a connected player's open flight menu.
            var player = io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightConsoleFixtures.standIn(level,
                    new com.mojang.authlib.GameProfile(source.ownerId().orElseThrow(), "TerminalOwner"));
            player.setPos(source.position());
            io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightConsoleFixtures.send(player, source, () ->
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.requestFlightIntent(player,
                            source.getId(), RocketFlightAction.CANCEL,
                            source.flightData().orElseThrow().plan().orElseThrow().destinationTarget(), UUID.randomUUID()));
            EscrowTags.require(EscrowRuntime.of(server).returns().handles(record, source), "Terminal cancellation was refused");
            requested = true;
        }
        if (!recovery || !journal.entries().isEmpty() || !journal.actionAuthorityAvailable() || !data.ledger().armedFlights().isEmpty()) { return; }
        var candidates = level.getEntitiesOfClass(RocketEntity.class, new AABB(63, 0, 63, 82, 200, 66));
        if (candidates.isEmpty()) { return; }
        EscrowTags.require(candidates.size() == 1, "Terminal restart duplicated its survivor");
        var survivor = candidates.get(0); var flight = survivor.flightData().orElseThrow();
        EscrowTags.require(survivor.getX() == (cancel ? 64.5 : 80.5) && survivor.getY() == 100 && survivor.getZ() == 64.5
                && flight.state() == (cancel ? RocketFlightState.FUELED : RocketFlightState.LANDED)
                && flight.fuel().totalAmount() == (cancel ? 1_000 : 500)
                && flight.fuel().committedDebits().size() == 1 && flight.passengers().assignments().isEmpty()
                && survivor.escrowSection().ledger().equals(data.ledger().id()) && survivor.escrowSection().epoch() == (cancel ? 7 : 0)
                && survivor.escrowSection().markers().isEmpty() && RocketEscrowGate.idle(survivor)
                && data.ledger().issuedLaunchSerial() == 1, "Terminal restart changed its survivor or replayed its flight");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TERMINAL_RESULT cut={} journal=idle armed=0 fuel={} epoch={}",
                cut, flight.fuel().totalAmount(), survivor.escrowSection().epoch());
        done = true;
    }

    private boolean loaded() {
        var level = server.overworld();
        return POSITIONS.stream().allMatch(pos -> level.areEntitiesLoaded(new ChunkPos(pos).toLong()) && level.isPositionEntityTicking(pos));
    }
    private static void pause(String cut) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TERMINAL_PAUSED cut={}", cut);
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException(stopped); }
        throw new IllegalStateException("Native terminal forced stop did not arrive within 30 seconds");
    }
}
