package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightConsoleFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** Dedicated real-loader successor fixture; no extra save or binding shortcut. */
final class EscrowLoaderS6RefillProbe {
    private static final BlockPos SOURCE = new BlockPos(74, 100, 64), DESTINATION = new BlockPos(82, 100, 64);
    private static final List<BlockPos> DEVICES = List.of(new BlockPos(78, 100, 64), new BlockPos(78, 100, 66));
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery, bi;
    private final List<FluidFuelLoaderBlockEntity> loaders = new ArrayList<>();
    private final List<UUID> operations = new ArrayList<>();
    private RocketEntity source, arrival;
    private boolean launching, discarded, pruned, done;
    private long began = -1;

    static boolean selected(boolean hooks, String cut) {
        return hooks && ("LF_S6_REFILL".equals(cut) || "LF_S6_REFILL_BI".equals(cut));
    }
    static int generation(int fills, boolean flight) { return 1 + 2 * fills + (flight ? 3 : 0); }
    static boolean refusalBoundary(String point) { return "F7_ACK".equals(point) || "F8_JOURNAL_ACK".equals(point); }

    EscrowLoaderS6RefillProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "S6 refill requires exact per-device test gates");
        this.server = server; this.cut = cut; this.recovery = recovery; bi = cut.endsWith("_BI");
        for (var position : List.of(SOURCE, DESTINATION)) {
            var chunk = new ChunkPos(position);
            server.overworld().getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            server.overworld().getChunk(chunk.x, chunk.z);
        }
    }

    void checkpoint(String point) {
        if (recovery || done) { return; }
        if (point.equals("C2_DESTINATION_AFTER")) {
            var rows = ResourceEscrowSavedData.existing(server).ledger().records();
            EscrowTags.require(rows.size() == 1, "S6 refill admitted overlapping operations");
            var op = rows.get(0);
            EscrowTags.require(operations.size() + 1 == op.sequence() && !operations.contains(op.id()), "S6 refill repeated a fill");
            operations.add(op.id());
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_REFILL_OPERATION cut={} sequence={} operation={} device={} destination={} bank={}",
                    cut, op.sequence(), op.id(), op.source().identity(), op.destination().identity(), op.destination().unit());
        }
        if (!launching) { return; }
        if (point.equals("F2_PREPARED_ACK")) {
            var path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            var payload = CheckedSavedDataFile.readPayload(path, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_REFILL_TRANSFER cut={} digest={} nbt={}", cut,
                    EscrowLoaderS6JournalProbe.transferDigest(payload), EscrowLoaderS6TerminalProbe.capture(payload));
        }
        if (point.equals("F6_LANDED")) {
            var row = RocketTransferSavedData.get(server).entries().stream().findFirst().orElseThrow();
            arrival = (RocketEntity) server.overworld().getEntity(row.destinationEntityId().orElseThrow());
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_REFILL_LANDED cut={} nbt={}", cut,
                    EscrowLoaderS6TerminalProbe.encode(EscrowGovernedEnvelope.capture(arrival)));
        }
        if (point.equals("F7_ACK")) {
            EscrowTags.require(arrival != null, "S6 refill lost its arrival");
            for (var loader : loaders) { bind(loader); }
        }
        if (refusalBoundary(point)) {
            var before = capture(arrival);
            for (var loader : loaders) {
                EscrowTags.require(!admit(loader) && loader.lastAttempt() == FluidFuelLoaderStatus.ESCROW_PENDING,
                        "S6 successor did not refuse the terminal flight");
            }
            EscrowTags.require(before.equals(capture(arrival)), "S6 refused successor mutated authority or resources");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_REFILL_REFUSED cut={} hook={} digest={}", cut, point,
                    EscrowNativeEnvelope.digest(before));
        }
        if (point.equals("F8_PRUNE_ACK")) {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_REFILL_PRUNED cut={} nbt={}", cut,
                    EscrowLoaderS6TerminalProbe.encode(EscrowCodec.encode(ResourceEscrowSavedData.existing(server).ledger())));
            pruned = true;
        }
    }

    private void bind(FluidFuelLoaderBlockEntity loader) {
        var player = RocketFlightConsoleFixtures.standIn(server.overworld(), new GameProfile(arrival.ownerId().orElseThrow(), "S6RefillOwner"));
        player.setPos(79.5, 100, 64.5);
        var stack = new ItemStack(ModItems.LINKER.get());
        stack.getOrCreateTag().put(LinkerSelection.KEY, new LinkerSelection(device(loader), server.overworld().dimension().location(), loader.getBlockPos()).encode());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        EscrowTags.require(LinkerItem.bindRocket(player, InteractionHand.MAIN_HAND, arrival) == InteractionResult.CONSUME
                && loader.root().orElseThrow().binding().equals(binding(arrival)), "S6 refill linker refused its owner or exact arrival");
    }

    void tick() {
        if (done || !loaded()) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "S6 refill exceeded the existing fixture bound");
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server)) { return; }
        if (recovery) { recover(journal); return; }
        EscrowTags.require(!discarded, "S6 refill launch discarded its callback");
        if (source == null) {
            source = rocket(server.overworld(), SOURCE, UUID.randomUUID(), bi ? bipropellant(2_000, 2_000) : mono(2_000));
            EscrowTags.require(server.overworld().addFreshEntity(source), "S6 refill source publication failed");
            loaders.add(loader(server.overworld(), DEVICES.get(0), source.ownerId().orElseThrow(), tank(bi ? HYDROGEN : ROCKET_FUEL, 5_000), binding(source)));
            if (bi) { loaders.add(loader(server.overworld(), DEVICES.get(1), source.ownerId().orElseThrow(), tank(OXYGEN, 5_000), binding(source))); }
            EscrowTags.require(server.saveEverything(false, true, true), "S6 refill pre-fill baseline save failed");
            return;
        }
        if (!quiet()) { return; }
        int banks = bi ? 2 : 1;
        if (!launching) {
            for (int index = 0; index < banks; index++) {
                if (bank(source, index == 1) == 0) { admit(loaders.get(index)); return; }
            }
            var root = ResourceEscrowSavedData.existing(server).ledger();
            EscrowTags.require(operations.size() == banks && root.issuedThrough() == banks && root.writerGeneration() == generation(banks, false)
                    && root.issuedLaunchSerial() == 0 && RocketEscrowGate.idle(source), "S6 refill baseline has extra authority");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_REFILL_BASELINE cut={} nbt={}", cut, EscrowLoaderS6TerminalProbe.encode(capture(source)));
            var base = source.flightData().orElseThrow(); var from = source.snapshot().orElseThrow(); long now = server.overworld().getGameTime();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(82, 100, 64), now);
            var fuel = base.fuel();
            var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, bi ? 500 : 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("W211 S6 successor refill")));
            launching = true;
            EscrowTags.require(EscrowGovernedPreparation.begin(source, EscrowLoaderS6FlightProbe.launchCountdown(base, plan, now), to,
                    row -> loaded(), row -> { }, () -> discarded = true), "S6 refill governed launch refused");
        } else if (pruned) {
            for (int index = 0; index < banks; index++) {
                if (bank(arrival, index == 1) == 500) { admit(loaders.get(index)); return; }
            }
            requireFinal(arrival, journal);
            EscrowTags.require(operations.size() == 2 * banks, "S6 refill lost an actual successor operation");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_REFILL_FINAL cut={} digest={}", cut,
                    EscrowGovernedEnvelope.digest(EscrowGovernedEnvelope.capture(arrival)));
            EscrowLoaderS6JournalProbe.pause(cut, "SUCCESSOR_RETIRED");
        }
    }

    private boolean admit(FluidFuelLoaderBlockEntity loader) {
        var runtime = EscrowRuntime.of(server); runtime.loaderAdmissionForTests(server, device(loader), true);
        try { return loader.admitFromQueue(); }
        finally { runtime.loaderAdmissionForTests(server, device(loader), false); }
    }

    private CompoundTag capture(RocketEntity rocket) {
        var payload = new CompoundTag(); var devices = new ListTag();
        loaders.forEach(loader -> devices.add(loader.saveWithFullMetadata()));
        payload.put("loaders", devices); payload.put("rocket", EscrowGovernedEnvelope.capture(rocket));
        payload.put("ledger", EscrowCodec.encode(ResourceEscrowSavedData.existing(server).ledger()));
        return payload;
    }

    private boolean quiet() {
        var data = ResourceEscrowSavedData.existing(server);
        return data == null || data.bind(server).writerIdle() && data.ledger().records().isEmpty()
                && loaders.stream().allMatch(loader -> section(loader).markers().isEmpty())
                && (arrival == null ? source : arrival).escrowSection().markers().isEmpty();
    }

    private void recover(RocketTransferSavedData journal) {
        if (loaders.isEmpty()) {
            for (int index = 0; index < (bi ? 2 : 1); index++) {
                if (!(server.overworld().getBlockEntity(DEVICES.get(index)) instanceof FluidFuelLoaderBlockEntity)) { return; }
            }
            for (int index = 0; index < (bi ? 2 : 1); index++) { loaders.add((FluidFuelLoaderBlockEntity) server.overworld().getBlockEntity(DEVICES.get(index))); }
        }
        var rockets = server.overworld().getEntitiesOfClass(RocketEntity.class, new AABB(72, 0, 62, 86, 300, 69));
        if (rockets.isEmpty()) { return; }
        EscrowTags.require(rockets.size() == 1, "S6 native image is not explained by its durable record: extra refill survivor");
        arrival = rockets.get(0);
        if (server.getTickCount() - began < 100 || !quiet() || !journal.actionAuthorityAvailable()) { return; }
        requireFinal(arrival, journal);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_REFILL_FINAL cut={} digest={}", cut,
                EscrowGovernedEnvelope.digest(EscrowGovernedEnvelope.capture(arrival)));
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_RESULT cut={} banks={} tank=3000 bank=1500", cut, bi ? 2 : 1);
        done = true;
    }

    private void requireFinal(RocketEntity rocket, RocketTransferSavedData journal) {
        var root = ResourceEscrowSavedData.existing(server).ledger(); int banks = bi ? 2 : 1;
        EscrowTags.require(journal.entries().isEmpty() && root.records().isEmpty() && root.armedFlights().isEmpty()
                && root.issuedThrough() == 2 * banks && root.writerGeneration() == generation(2 * banks, true)
                && root.issuedLaunchSerial() == 1 && rocket.escrowSection().epoch() == banks && RocketEscrowGate.idle(rocket)
                && bank(rocket, false) == 1_500 && bank(rocket, true) == (bi ? 1_500 : 0)
                && rocket.flightData().orElseThrow().fuel().committedDebits().size() == 1
                && loaders.stream().allMatch(loader -> amount(loader) == 3_000 && section(loader).epoch() == 2
                    && loader.root().orElseThrow().binding().equals(binding(rocket))), "S6 refill changed its final resources or authority");
    }

    private boolean loaded() {
        return List.of(SOURCE, DESTINATION).stream().allMatch(pos -> server.overworld().areEntitiesLoaded(new ChunkPos(pos).toLong())
                && server.overworld().isPositionEntityTicking(pos));
    }
}
