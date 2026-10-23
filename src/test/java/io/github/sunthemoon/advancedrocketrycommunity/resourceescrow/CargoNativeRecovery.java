package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.google.gson.*;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Read-only restart comparison for all twelve cargo/fuel/refusal pairs and the complete ledger. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class CargoNativeRecovery {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BASELINE = "cargo-recovery-baseline.json";
    private final MinecraftServer server;
    private final int index;
    private final long started = System.nanoTime();
    private final int startTick;
    private boolean done;

    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.cargoBenchmark")
                || !"recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"))) { return; }
        MinecraftForge.EVENT_BUS.register(new CargoNativeRecovery(event.getServer()));
    }
    private CargoNativeRecovery(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; startTick = server.getTickCount();
        index = Integer.getInteger("advancedrocketrycommunity.cargoRecoveryIndex", 0);
        EscrowTags.require(index == 1 || index == 2, "Recovery index must be 1 or 2");
        var moon = Objects.requireNonNull(server.getLevel(CelestialIds.MOON_LEVEL));
        for (int i = 0; i < 12; i++) {
            var pos = new ChunkPos(CargoNativeBenchmark.at(i));
            moon.getChunkSource().addRegionTicket(TicketType.START, pos, 2, net.minecraft.util.Unit.INSTANCE);
            moon.getChunk(pos.x, pos.z); // The same fixed test setup as start, never an arbitrary endpoint.
        }
    }

    static void prepareBaseline(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        MinecraftForge.EVENT_BUS.register(new Baseline(server));
    }
    public static final class Baseline {
        private final MinecraftServer server;
        Baseline(MinecraftServer server) { this.server = server; }
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
            if (event.getServer() != server || event.phase != TickEvent.Phase.END) { return; }
            var reference = CargoNativeBenchmark.field(EscrowRuntime.of(server), "flightBenchmark");
            if (reference == null || !(boolean) CargoNativeBenchmark.field(reference, "done")) { return; }
            baseline(server); MinecraftForge.EVENT_BUS.unregister(this);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_BENCH_DONE status=NATIVE_MEASURED");
        }
    }
    private static void baseline(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        var report = new LinkedHashMap<String, Object>(); report.put("schema_version", 1);
        report.put("source_commit", System.getProperty("advancedrocketrycommunity.benchmarkCommit"));
        report.put("state", capture(server));
        write(server, BASELINE, report);
    }

    private static Map<String, String> capture(MinecraftServer server) {
        var data = Objects.requireNonNull(ResourceEscrowSavedData.existing(server));
        EscrowTags.require(data.bind(server).operational() && data.bind(server).writerIdle()
                && data.ledger().records().isEmpty(), "Recovery requires an idle operational ledger without pending records");
        var state = new TreeMap<String, String>();
        state.put("ledger", CanonicalNbtDigest.of(EscrowCodec.encode(data.ledger()), EscrowLimits.ROOT_BYTES, 24));
        var moon = Objects.requireNonNull(server.getLevel(CelestialIds.MOON_LEVEL));
        for (int i = 0; i < 12; i++) {
            var pos = CargoNativeBenchmark.at(i);
            EscrowTags.require(moon.hasChunkAt(pos) && moon.isPositionEntityTicking(pos), "Recovery fixture is not ticking");
            var device = Objects.requireNonNull(moon.getBlockEntity(pos), "Recovery device missing");
            var rockets = new ArrayList<RocketEntity>();
            moon.getEntities().getAll().forEach(entity -> {
                if (entity instanceof RocketEntity rocket && rocket.blockPosition().equals(pos.offset(4, 0, 0))) { rockets.add(rocket); }
            });
            EscrowTags.require(rockets.size() == 1, "Missing or duplicate recovery rocket at " + pos);
            state.put("device_" + i, CanonicalNbtDigest.of(device.saveWithFullMetadata(), EscrowLimits.ROOT_BYTES, 24));
            state.put("rocket_" + i, EscrowNativeEnvelope.digest(EscrowNativeEnvelope.rocket(rockets.get(0))));
        }
        return state;
    }

    static boolean matches(JsonObject baseline, String source, Map<String, String> state) {
        return baseline.has("schema_version") && baseline.get("schema_version").getAsInt() == 1
                && baseline.has("source_commit") && source.equals(baseline.get("source_commit").getAsString())
                && state.size() == 25 && baseline.has("state") && baseline.get("state").equals(GSON.toJsonTree(state));
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (done || event.getServer() != server || event.phase != TickEvent.Phase.END) { return; }
        var reference = CargoNativeBenchmark.field(EscrowRuntime.of(server), "flightBenchmark");
        if (reference == null || !(boolean) CargoNativeBenchmark.field(reference, "done")) { return; }
        var report = new LinkedHashMap<String, Object>(); report.put("restart", index);
        report.put("source_commit", System.getProperty("advancedrocketrycommunity.benchmarkCommit"));
        try {
            var path = server.getWorldPath(LevelResource.ROOT).resolve(BASELINE);
            EscrowTags.require(Files.size(path) <= 32768, "Recovery baseline too large");
            var baseline = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var state = capture(server); report.put("state", state);
            EscrowTags.require(matches(baseline, (String) report.get("source_commit"), state), "Recovery state differs from completed reference load");
            report.put("status", "MATCHED");
        } catch (java.io.IOException | RuntimeException failure) {
            report.put("status", "MISMATCH"); report.put("error", failure.toString());
        }
        report.put("recovery_ns", System.nanoTime() - started); report.put("recovery_ticks", server.getTickCount() - startTick);
        report.put("governed_flights_verified", true); done = true;
        write(server, "cargo-recovery-" + index + ".json", report);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_RECOVERY_DONE restart={} status={}", index, report.get("status"));
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    private static void write(MinecraftServer server, String name, Object report) {
        try { Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve(name), GSON.toJson(report)); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Cargo recovery export failed", failure); }
    }
}
