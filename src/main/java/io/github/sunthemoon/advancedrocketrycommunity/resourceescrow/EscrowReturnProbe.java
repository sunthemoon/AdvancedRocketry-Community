package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;

/** Empty-server return storage cuts. No cancellation is reissued on recovery and no F7 is fabricated. */
final class EscrowReturnProbe {
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private final boolean cancel;
    private final int slot;
    private final String point;
    private final BlockPos destination;
    private boolean started;
    private boolean accepted;
    private boolean discarded;
    private boolean requested;
    private boolean done;
    private volatile int returnSlot = -1;

    EscrowReturnProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        String[] parts = cut.split("_", 6);
        EscrowTags.require(parts.length == 6 && parts[0].equals("RT") && List.of("CANCEL", "PAD").contains(parts[1])
                && List.of("SHARED", "SEPARATE").contains(parts[2]) && List.of("0", "1").contains(parts[3])
                && List.of("STORE", "FORCE", "READBACK").contains(parts[4]) && List.of("BEFORE", "AFTER").contains(parts[5]),
                "Unknown return cut");
        cancel = parts[1].equals("CANCEL"); slot = Integer.parseInt(parts[3]); point = "BARRIER_" + parts[4] + "_" + parts[5];
        boolean shared = parts[2].equals("SHARED"); EscrowTags.require(!shared || slot == 0, "Shared return has only one joint slot");
        destination = new BlockPos(shared ? 68 : 80, 100, 64);
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        for (var pos : positions()) {
            var chunk = new ChunkPos(pos); server.overworld().getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            server.overworld().getChunk(chunk.x, chunk.z);
        }
    }

    void checkpoint(String name) {
        if (recovery || !started) { return; }
        if (name.startsWith("RETURN_ABSENCE_") || name.equals("RETURN_APPLIED")) { returnSlot = -1; }
        if (name.startsWith("RETURN_F6_SLOT_")) { returnSlot = Integer.parseInt(name.substring("RETURN_F6_SLOT_".length())); }
        if (returnSlot == slot && name.equals(point)) {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_RETURN_PAUSED cut={}", cut);
            try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
            throw new IllegalStateException("Native return kill did not arrive within 30 seconds");
        }
    }

    void tick() {
        if (done || !loaded()) { return; }
        var level = server.overworld(); var data = ResourceEscrowSavedData.forTests(server); data.bind(server);
        var journal = RocketTransferSavedData.get(server); if (!journal.retryChecked(server)) { return; }
        if (!recovery && !started) { prepare(data); return; }
        EscrowTags.require(!discarded, "Return preparation callback discarded");
        if (!recovery && !accepted || journal.entries().size() != 1 || !journal.actionAuthorityAvailable()) { return; }
        var record = journal.entries().get(0); var arm = record.nativeTransfer().orElseThrow().armed();
        EscrowTags.require(data.ledger().armedFlights().equals(List.of(arm)), "Return released or changed ARMED authority");
        var source = level.getEntity(record.sourceEntityId()) instanceof RocketEntity found ? found : null;
        var arrival = level.getEntity(arm.destination()) instanceof RocketEntity found ? found : null;
        if (!recovery && cancel && !requested) {
            EscrowTags.require(source != null && source.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN,
                    "Cancellation lost its live countdown");
            var player = new FakePlayer(level, new GameProfile(source.ownerId().orElseThrow(), "ReturnOwner"));
            player.setPos(source.position());
            RocketRuntime.requestFlightIntent(player, source.getId(), RocketFlightAction.CANCEL,
                    source.flightData().orElseThrow().plan().orElseThrow().destinationTarget(), UUID.randomUUID());
            EscrowTags.require(EscrowRuntime.of(server).returns().handles(record, source), "Live cancellation was refused");
            requested = true;
        }
        if (!recovery) { return; }
        if (source != null && EscrowGovernedReturnAction.returned(record, EscrowNativeEnvelope.rocket(source))) {
            if (!EscrowRuntime.of(server).returns().checkpoint(server, record)) { return; }
            EscrowTags.require(arrival == null && !RocketEscrowGate.idle(source) && source.escrowSection().epoch() == 7
                    && source.flightData().orElseThrow().fuel().totalAmount() == 1_000
                    && source.flightData().orElseThrow().fuel().committedDebits().size() == 1,
                    "Return restart changed credit or published its destination");
            finish("RETURNED");
        } else if (source == null && arrival != null && arrival.flightData().orElseThrow().state() == RocketFlightState.LANDED) {
            // The first post-return store has not happened at this cut; its volatile request is not a durable cancellation.
            EscrowTags.require(cancel && slot == 0 && point.equals("BARRIER_STORE_BEFORE")
                    && record.phase() == RocketTransferPhase.COMMITTED
                    && EscrowGovernedTerminalDisk.survivor(record, EscrowFlightTables.Outcome.HANDED_OFF, EscrowNativeEnvelope.rocket(arrival))
                    && arrival.escrowSection().epoch() == 0 && arrival.flightData().orElseThrow().fuel().totalAmount() == 500
                    && arrival.flightData().orElseThrow().fuel().committedDebits().size() == 2 && !RocketEscrowGate.idle(arrival),
                    "Return restart fabricated forward authority");
            finish("LANDED");
        }
    }

    private void prepare(ResourceEscrowSavedData data) {
        var level = server.overworld();
        if (!cancel) { level.setBlock(destination, Blocks.STONE.defaultBlockState(), 3); }
        // Persist level clocks and the obstruction before F1, never flush over the cut under observation.
        EscrowTags.require(server.saveEverything(false, true, true), "Return baseline save refused");
        level.getChunkSource().chunkMap.worker.synchronize(true).join();
        var rocket = EscrowRocketAuditProbe.createRocket(level, UUID.randomUUID(), true);
        var base = rocket.flightData().orElseThrow(); long now = level.getGameTime();
        var filled = base.fuel().withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1_000);
        var fuel = RocketFuelState.restorePropulsion(filled.propulsion().orElseThrow(), List.of(UUID.randomUUID()));
        rocket.updateFlightData(base.withFuel(fuel, now));
        var tag = rocket.saveWithoutId(new CompoundTag());
        tag.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 7, List.of()).encode());
        rocket.load(tag); EscrowTags.require(level.addFreshEntity(rocket), "Return source publication refused");
        var from = rocket.snapshot().orElseThrow();
        var to = from.relocated(UUID.randomUUID(), from.sourceDimension(),
                new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
        var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("Return storage fixture")));
        started = EscrowGovernedPreparation.begin(rocket, rocket.flightData().orElseThrow().withPlan(plan).startCountdown(now),
                to, record -> loaded(), record -> accepted = true, () -> discarded = true);
        EscrowTags.require(started, "Return preparation refused");
    }

    private List<BlockPos> positions() { return List.of(new BlockPos(64, 100, 64), destination); }
    private boolean loaded() {
        var level = server.overworld();
        return positions().stream().allMatch(pos -> level.areEntitiesLoaded(new ChunkPos(pos).toLong()) && level.isPositionEntityTicking(pos));
    }
    private void finish(String outcome) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_RETURN_RESULT cut={} outcome={} armed=true", cut, outcome); done = true;
    }
}
