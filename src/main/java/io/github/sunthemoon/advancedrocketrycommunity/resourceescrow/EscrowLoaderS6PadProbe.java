package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketNamedPadReleaseFixture;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.*;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** W221 native real-loader flight through actual named-pad selection and governed cleanup. */
public final class EscrowLoaderS6PadProbe {
    private static final Set<String> CASES = Set.of("LF_S6_PAD_PREPARED", "LF_S6_PAD_REFUSED", "LF_S6_PAD_STOPPED",
            "LF_S6_PAD_BEFORE", "LF_S6_PAD_MOVED", "LF_S6_PAD_UNKNOWN", "LF_S6_PAD_RELEASED");
    private static final BlockPos SOURCE = new BlockPos(64, 100, 64), DEVICE = SOURCE.offset(2, 0, 0);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private StationState station;
    private LandingPadEntry pad;
    private BlockPos padPos;
    private RocketEntity source;
    private FluidFuelLoaderBlockEntity loader;
    private CompoundTag baseline;
    private ListTag retiredLoaderMarkers = new ListTag();
    private UUID transfer, previousClaim;
    private boolean launched, discarded, done, started, acknowledged, pruned, prepared;
    private int releases, sequence, ticks;
    private long began = -1;

    public static boolean selected(boolean hooks, String cut) { return hooks && cut != null && CASES.contains(cut); }

    EscrowLoaderS6PadProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "Named-pad fixture requires its exact gates");
        this.server = server; this.cut = cut; this.recovery = recovery;
        retain(server.overworld(), SOURCE);
    }

    void startTick() {
        if (started) { return; }
        started = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PAD_BEGIN cut={} recovery={}", cut, recovery);
        if (!recovery) { return; }
        var decoded = LandingPadIndexCodec.decode(LandingPadIndex.get(server).save(new CompoundTag()));
        EscrowTags.require(decoded.entries().size() == 1, "Named-pad restart lost its index");
        pad = decoded.entries().get(0); previousClaim = pad.claim(); transfer = pad.claim();
        station = StationRegistrySavedData.get(server).find(pad.stationId()).orElseThrow(); padPos = pad.position(station);
        retain(space(), padPos.above());
        if (transfer == null) {
            var arms = ResourceEscrowSavedData.existing(server).ledger().armedFlights();
            if (!arms.isEmpty()) { transfer = arms.get(0).transfer(); }
        }
        observe("RESTART", false);
    }

    void checkpoint(String point) {
        if (done) { return; }
        if (point.equals("C2_DESTINATION_AFTER") && loader != null) {
            retiredLoaderMarkers = loader.saveWithFullMetadata().getCompound("arce_fluid_fuel_loader")
                    .getCompound("resource_escrow").getList("markers", 10).copy();
        }
        if (!recovery && point.equals("F2_COUNTDOWN")) {
            prepared = true; previousClaim = transfer;
            var index = LandingPadIndex.get(server);
            EscrowTags.require(transfer.equals(entry().claim()), "Product preparation lost its named-pad claim");
            index.save(server.getWorldPath(LevelResource.ROOT).resolve("data/" + LandingPadIndex.DATA_NAME + ".dat").toFile());
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PAD_STORE cut={} owner=FIXTURE scope=PAD_INDEX", cut);
            observe("PREPARED", false);
            if (cut.endsWith("PREPARED")) { finish("PREPARED"); }
            if (cut.endsWith("REFUSED") || cut.endsWith("STOPPED")) {
                var journal = journal();
                var before = snapshot();
                if (cut.endsWith("STOPPED")) { journal.stop(); }
                else { journal.reserve(source.flightData().orElseThrow().logicalRocketId()).orElseThrow(); }
                var result = RocketNamedPadReleaseFixture.cancel(source);
                var after = snapshot();
                EscrowTags.require(result.code() == RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED
                        && sameRefusal(before, after), "Named-pad refused cancellation changed source, journal or claim");
                observe(cut.endsWith("REFUSED") ? "REFUSED" : "STOPPED", true); finish("REFUSAL");
            }
        }
        if (!recovery && point.equals("F7_ACK") && cut.endsWith("UNKNOWN")) {
            // Existing R52 committer/readback failure, selected only after this flight's terminal write.
            System.setProperty("advancedrocketrycommunity.escrowCase", "LF_M_R52_UNKNOWN");
        }
        if (point.equals("F8_JOURNAL_ACK")) {
            acknowledged = true;
            EscrowTags.require(transfer != null && journal().transferAbsenceAcknowledged(transfer), "Cleanup ACK lacks exact absence");
            observe("ACK", false);
        }
        if (point.equals("F8_PRUNE_ACK")) {
            pruned = true; checkClaim();
            if (!recovery && cut.endsWith("RELEASED")) { observe("RELEASED", false); finish("RELEASED"); }
        }
        if (!recovery && (point.equals("PAD_BEFORE") || point.equals("PAD_MOVED"))) {
            checkClaim(); observe(point.substring(4), false); finish(point);
        }
    }

    static boolean sameRefusal(CompoundTag before, CompoundTag after) {
        return List.of("source", "journal", "pads").stream().allMatch(key -> before.get(key).equals(after.get(key)));
    }

    void tick() {
        if (done) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "Named-pad fixture exceeded existing S6 bound");
        if (pad != null) { checkClaim(); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PAD_TICK cut={} index={} claim={} absence={} releases={}",
                cut, ticks++, pad == null ? "NONE" : entry().claim(), absence(), releases);
        if (!recovery && cut.endsWith("UNKNOWN") && journal().checkedWriteResult().outcome().name().equals("UNKNOWN")) {
            observe("UNKNOWN", false); finish("UNKNOWN"); return;
        }
        if (!journal().retryChecked(server) || !ready(server.overworld(), SOURCE)) { return; }
        if (recovery) { recover(); return; }
        EscrowTags.require(!discarded, "Named-pad governed launch discarded its callback");
        if (source == null) { create(); return; }
        if (launched || !ready(space(), padPos.above())) { return; }
        var runtime = EscrowRuntime.of(server);
        if (bank(source, false) == 0) {
            runtime.loaderAdmissionForTests(server, device(loader), true);
            try { loader.admitFromQueue(); } finally { runtime.loaderAdmissionForTests(server, device(loader), false); }
            return;
        }
        var root = ResourceEscrowSavedData.existing(server);
        if (!root.bind(server).writerIdle() || !root.ledger().records().isEmpty()
                || !section(loader).markers().isEmpty() || !source.escrowSection().markers().isEmpty()) { return; }
        EscrowTags.require(amount(loader) == 4_000 && bank(source, false) == 1_000 && section(loader).epoch() == 1
                && source.escrowSection().epoch() == 1 && RocketEscrowGate.idle(source), "Named-pad real fill did not retire");
        baseline = tag(source);
        var base = source.flightData().orElseThrow(); var fuel = base.fuel(); long now = server.overworld().getGameTime();
        transfer = UUID.randomUUID();
        var plan = new RocketFlightPlan(3, transfer, CelestialIds.EARTH_ID, station.orbitBody(),
                source.level().dimension().location(), CelestialIds.SPACE_LEVEL.location(), station.stationId(), 500, now,
                new TravelTarget.Station(station.stationId())).withLandingPad(pad.reference())
                .withPropulsionDebit(new RocketPropulsionDebit(500, 0, fuel.propulsion().orElseThrow().revision(),
                        fuel.resourceSignature(), RocketNativeResources.hash("W221 real-loader named-pad flight")));
        launched = true;
        var result = RocketNamedPadReleaseFixture.launch(source, EscrowLoaderS6FlightProbe.launchCountdown(base, plan, now), () -> discarded = true);
        EscrowTags.require(result.code() == RocketFlightRequestCode.JOURNAL_PENDING, "Named-pad product launch refused: " + result.code());
        EscrowTags.require(transfer.equals(entry().claim()), "Product launch did not claim the named pad");
        previousClaim = transfer;
    }

    private void create() {
        var owner = UUID.randomUUID();
        station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                .create(server, owner, "W221", CelestialIds.EARTH_ID, false).station().orElseThrow();
        padPos = new BlockPos(station.landingPad().x() + 32, station.landingPad().y() - 1, station.landingPad().z());
        retain(space(), padPos.above());
        for (int x = -1; x <= 1; x++) { for (int z = -1; z <= 1; z++) {
            space().setBlockAndUpdate(padPos.offset(x, 0, z), Blocks.GRAY_CONCRETE.defaultBlockState());
        } }
        space().setBlockAndUpdate(padPos, ModBlocks.LANDING_PAD.get().defaultBlockState());
        pad = LandingPadService.register(space(), padPos, owner, false, "W221 Dock");
        source = rocket(server.overworld(), SOURCE, owner, mono(2_000));
        EscrowTags.require(server.overworld().addFreshEntity(source), "Named-pad source publication failed");
        loader = loader(server.overworld(), DEVICE, owner, tank(ROCKET_FUEL, 5_000), binding(source));
        EscrowTags.require(server.saveEverything(false, true, true), "Named-pad pre-fill baseline save failed");
    }

    private void recover() {
        if (!ready(space(), padPos.above()) || server.getTickCount() - began < 100) { return; }
        var root = ResourceEscrowSavedData.existing(server);
        if (!root.bind(server).writerIdle() || !journal().actionAuthorityAvailable() || !journal().entries().isEmpty()
                || !root.ledger().records().isEmpty() || !root.ledger().armedFlights().isEmpty()) { return; }
        loader = (FluidFuelLoaderBlockEntity) server.overworld().getBlockEntity(DEVICE);
        var arrivals = arrivals();
        EscrowTags.require(arrivals.size() == 1 && sources().isEmpty() && entry().claim() == null
                && amount(loader) == 4_000 && section(loader).epoch() == 1 && section(loader).markers().isEmpty(),
                "Named-pad restart did not retain exactly one unclaimed arrival and real loader");
        var arrival = arrivals.get(0); var flight = arrival.flightData().orElseThrow();
        EscrowTags.require(flight.state() == RocketFlightState.LANDED && bank(arrival, false) == 500
                && flight.fuel().committedDebits().size() == 1 && arrival.escrowSection().epoch() == 0
                && arrival.escrowSection().markers().isEmpty() && RocketEscrowGate.idle(arrival), "Named-pad restart changed typed survivor");
        observe("RESULT", false); finish("RESULT");
    }

    private void checkClaim() {
        var claim = entry().claim();
        if (previousClaim != null && claim == null) {
            EscrowTags.require(acknowledged && absence(), "Named-pad claim released before acknowledged absence");
            releases++;
        }
        EscrowTags.require(releases <= 1 && (claim == null || claim.equals(transfer)), "Named-pad claim identity/release count changed");
        previousClaim = claim;
    }

    private boolean absence() { return transfer != null && journal().transferAbsenceAcknowledged(transfer); }
    private RocketTransferSavedData journal() { return RocketTransferSavedData.get(server); }
    private ServerLevel space() { return server.getLevel(CelestialIds.SPACE_LEVEL); }
    private LandingPadEntry entry() { return LandingPadIndex.get(server).find(pad.stationId(), pad.reference()).orElseThrow(); }
    private List<RocketEntity> arrivals() { return space().getEntitiesOfClass(RocketEntity.class, new AABB(padPos.above()).inflate(4, 200, 4)); }
    private List<RocketEntity> sources() { return server.overworld().getEntitiesOfClass(RocketEntity.class, new AABB(SOURCE).inflate(4, 200, 4)); }
    private static CompoundTag tag(RocketEntity rocket) {
        var tag = rocket.saveWithoutId(new CompoundTag()); tag.putString("id", "advancedrocketrycommunity:rocket"); return tag;
    }

    private CompoundTag snapshot() {
        var tag = new CompoundTag();
        if (source != null && !source.isRemoved()) { tag.put("source", tag(source)); }
        else { var found = sources(); tag.put("source", found.size() == 1 ? tag(found.get(0)) : new CompoundTag()); }
        var arrivals = arrivals(); tag.put("arrival", arrivals.size() == 1 ? tag(arrivals.get(0)) : new CompoundTag());
        tag.put("pads", LandingPadIndex.get(server).save(new CompoundTag()));
        tag.put("journal", journal().save(new CompoundTag()));
        tag.put("ledger", EscrowCodec.encode(ResourceEscrowSavedData.existing(server).ledger()));
        if (loader != null) { tag.put("loader", loader.saveWithFullMetadata()); }
        if (baseline != null) { tag.put("baseline", baseline); }
        tag.put("retired_loader_markers", retiredLoaderMarkers.copy());
        tag.putBoolean("absence", absence()); tag.putInt("releases", releases);
        tag.putString("outcome", journal().checkedWriteResult().outcome().name());
        return tag;
    }

    private void observe(String label, boolean refused) {
        var tag = snapshot(); tag.putString("label", label); tag.putInt("seq", sequence++); tag.putBoolean("refused", refused);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PAD_EVENT cut={} nbt={}", cut, EscrowLoaderS6TerminalProbe.encode(tag));
    }

    private void finish(String hook) {
        done = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PAD_END cut={} count={}", cut, ticks);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PAD_{} cut={} hook={}", recovery ? "RESULT" : "PAUSED", cut, hook);
        if (recovery || cut.endsWith("RELEASED")) { return; }
        EscrowLoaderS6JournalProbe.pause(cut, hook);
    }

    private static void retain(ServerLevel level, BlockPos origin) {
        for (int x = (origin.getX() - 1) >> 4; x <= (origin.getX() + 2) >> 4; x++) {
            for (int z = (origin.getZ() - 1) >> 4; z <= (origin.getZ() + 2) >> 4; z++) {
                var chunk = new ChunkPos(x, z); level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
                level.getChunk(x, z);
            }
        }
    }
    private static boolean ready(ServerLevel level, BlockPos origin) {
        for (int x = (origin.getX() - 1) >> 4; x <= (origin.getX() + 2) >> 4; x++) {
            for (int z = (origin.getZ() - 1) >> 4; z <= (origin.getZ() + 2) >> 4; z++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(x, z)) || !level.isPositionEntityTicking(new BlockPos(x * 16, origin.getY(), z * 16))) { return false; }
            }
        }
        return true;
    }
}
