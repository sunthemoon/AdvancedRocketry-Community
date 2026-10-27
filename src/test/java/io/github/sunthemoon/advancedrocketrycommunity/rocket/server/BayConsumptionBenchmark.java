package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferConsumption;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryBenchmark;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * W170's D5 benchmark (W127-PLAN's W27d2 A1; W27A-DESIGN section 9): the consumption's server-thread work for a rocket
 * at the snapshot bounds, 2,048 blocks with 128 block-entity payloads near the 1 MiB aggregate, eight of them bays that
 * the group consumes. Measured per server tick, as the coordinator runs it: each budgeted step of the S' and D' job
 * (ADR-065 4.1.1's 16 MiB and 16,384 visits per tick, one reconstruction), the T' record method, and the journal rewrite
 * on a detached journal. docs/17 section 4: a tick's work P95 at most 50 ms and P99 at most 100 ms. A standalone
 * diagnostic on the development host, not reference-node evidence; no unit test runs it.
 */
public final class BayConsumptionBenchmark {
    static final int WARMUP = 5, SAMPLES = 50, BAYS = 8, PAYLOADS = RocketLimits.MAX_BLOCK_ENTITIES;
    private BayConsumptionBenchmark() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        var report = new LinkedHashMap<String, Object>();
        report.put("schema_version", 1); report.put("source_commit", args[1]); report.put("status", "IN_PROGRESS");
        report.put("environment", "A0_MICROBENCHMARK_DEVELOPMENT_HOST");
        report.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        report.put("jvm_flags", ManagementFactory.getRuntimeMXBean().getInputArguments());
        report.put("java_version", System.getProperty("java.runtime.version"));
        report.put("warmup_operations", WARMUP); report.put("measured_operations", SAMPLES);
        MinecraftBootstrap.initialize();
        var record = record();
        long payloadBytes = record.sourceSnapshot().blocks().stream().flatMap(b -> b.blockEntityPayload().stream())
                .mapToLong(RocketBlockEntityPayload::uncompressedBytes).sum();
        report.put("fixture", Map.of("blocks", record.sourceSnapshot().blocks().size(), "payloads", PAYLOADS, "consumed_bays", BAYS,
                "payload_bytes", payloadBytes, "aggregate_limit_bytes", RocketLimits.MAX_TOTAL_NBT_BYTES));
        var consumed = new LinkedHashMap<RocketPosition, BayReceipt>();
        for (int i = 0; i < BAYS; i++) { consumed.put(position(i), receipt(record, i)); }
        var jobTicks = new ArrayList<Double>(); var recordMethod = new ArrayList<Double>(); var rewrite = new ArrayList<Double>();
        var ticksPerSample = new ArrayList<Integer>();
        for (int sample = 0; sample < WARMUP + SAMPLES; sample++) {
            boolean measured = sample >= WARMUP;
            var job = BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), consumed).orElseThrow();
            var budget = new RocketCargoWorkBudget();
            int ticks = 0;
            for (long tick = 0; ; tick++) {
                long started = System.nanoTime();
                boolean done = job.step(budget, tick);
                if (measured) { jobTicks.add((System.nanoTime() - started) / 1e6); }
                ticks++;
                if (done) { break; }
                if (ticks > 1_000) { throw new IllegalStateException("The job did not finish in 1,000 ticks"); }
            }
            if (measured) { ticksPerSample.add(ticks); }
            var result = job.result().orElseThrow();
            long started = System.nanoTime();
            var after = record.withConsumedSnapshots(result.source(), result.destination());
            if (measured) { recordMethod.add((System.nanoTime() - started) / 1e6); }
            var journal = new RocketTransferSavedData(); journal.put(record);
            started = System.nanoTime();
            var outcome = RocketTransferConsumption.apply(journal, record, after, record.sourceSnapshot().contentHash(),
                    record.destinationSnapshot().contentHash(), after.sourceSnapshot().contentHash(), after.destinationSnapshot().contentHash());
            if (measured) { rewrite.add((System.nanoTime() - started) / 1e6); }
            if (outcome != RocketTransferConsumption.Result.CONSUMED) { throw new IllegalStateException("Rewrite refused: " + outcome); }
        }
        var phases = new LinkedHashMap<String, Object>();
        phases.put("job_tick", summary(jobTicks)); phases.put("record_method", summary(recordMethod)); phases.put("journal_rewrite", summary(rewrite));
        phases.put("job_ticks_per_sample", Map.of("min", ticksPerSample.stream().mapToInt(Integer::intValue).min().orElse(0),
                "max", ticksPerSample.stream().mapToInt(Integer::intValue).max().orElse(0)));
        report.put("phases", phases);
        boolean within = List.of(jobTicks, recordMethod, rewrite).stream().allMatch(BayConsumptionBenchmark::within);
        report.put("budget_ms", Map.of("p95", 50, "p99", 100));
        report.put("status", within ? "WITHIN_BUDGET" : "STOP_RULE");
        SatelliteRegistryBenchmark.persist(output, report);
        System.out.println("BAY_CONSUMPTION_BENCHMARK " + report.get("status") + " " + phases);
    }

    private static Map<String, Object> summary(List<Double> values) {
        var sorted = values.stream().sorted().toList();
        var map = new LinkedHashMap<String, Object>();
        map.put("samples", sorted.size());
        map.put("p50_ms", SatelliteRegistryBenchmark.percentile(sorted, 0.50));
        map.put("p95_ms", SatelliteRegistryBenchmark.percentile(sorted, 0.95));
        map.put("p99_ms", SatelliteRegistryBenchmark.percentile(sorted, 0.99));
        map.put("max_ms", sorted.get(sorted.size() - 1));
        return map;
    }

    private static boolean within(List<Double> values) {
        var sorted = values.stream().sorted().toList();
        return SatelliteRegistryBenchmark.percentile(sorted, 0.95) <= 50 && SatelliteRegistryBenchmark.percentile(sorted, 0.99) <= 100;
    }

    /** 2,048 blocks in a 16 x 16 x 8 box; the first 128 carry payloads: eight bays, then 120 large opaque payloads. */
    static RocketStructureSnapshot source() {
        ResourceLocation iron = ResourceLocation.tryParse("minecraft:iron_block");
        ResourceLocation bay = ModIdentity.id("rocket_satellite_bay"), chest = ResourceLocation.tryParse("minecraft:chest");
        var blocks = new ArrayList<RocketBlock>();
        int filler = (int) ((RocketLimits.MAX_TOTAL_NBT_BYTES - BAYS * 600L) / (PAYLOADS - BAYS)) - 96;
        for (int index = 0; index < RocketLimits.MAX_BLOCKS; index++) {
            var at = new RocketPosition(index % 16, index / 256, (index / 16) % 16);
            if (index < BAYS) {
                blocks.add(new RocketBlock(at, new RocketBlockState(bay, Map.of()), new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID,
                        SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(BayConsumptionFixtures.OWNER, BayConsumptionFixtures.pkg(index), null)))));
            } else if (index < PAYLOADS) {
                var data = new CompoundTag(); data.putByteArray("opaque", new byte[filler]); data.putInt("n", index);
                blocks.add(new RocketBlock(at, new RocketBlockState(chest, Map.of()), new RocketBlockEntityPayload(chest, data)));
            } else {
                blocks.add(new RocketBlock(at, new RocketBlockState(iron, Map.of())));
            }
        }
        return RocketStructureSnapshot.create(new UUID(170, 20), RocketFlightPlanner.EARTH.dimensionId(), BayConsumptionFixtures.EARTH, blocks,
                List.of(new RocketPosition(15, 7, 15)), new RocketStats(blocks.size(), 200L, 1_000L, 1_000L, 1, 1, 1, PAYLOADS), 0L);
    }

    static RocketPosition position(int bay) { return new RocketPosition(bay % 16, 0, 0); }

    static RocketTransferRecord record() {
        var source = source();
        var destination = source.relocated(new UUID(170, 21), RocketFlightPlanner.MOON.dimensionId(), BayConsumptionFixtures.MOON, 160L);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        var plan = RocketFlightPlanner.plan(source.stats(), fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON,
                BayConsumptionFixtures.TRANSFER, 0L).plan();
        var transit = RocketFlightData.initial(BayConsumptionFixtures.LOGICAL, source.stats().fuelCapacity(), source.stats().seatCount(),
                RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(), BayConsumptionFixtures.EARTH, 0L)
                .withFuel(fuel, 0L).withPlan(plan).startCountdown(0L).completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(BayConsumptionFixtures.TRANSFER, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        var arrival = transit.arriveAtDestination(transit.fuel().debit(plan).state(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), BayConsumptionFixtures.MOON, RocketFlightLimits.COUNTDOWN_TICKS
                        + RocketFlightLimits.ASCENT_TICKS + RocketFlightLimits.TRANSIT_TICKS);
        return RocketTransferRecord.create(BayConsumptionFixtures.TRANSFER, BayConsumptionFixtures.LOGICAL, BayConsumptionFixtures.OWNER,
                BayConsumptionFixtures.SOURCE, source, destination, transit, arrival, plan.requiredFuel(), 0L);
    }

    static BayReceipt receipt(RocketTransferRecord record, int i) {
        return new BayReceipt(new UUID(173, i), i + 1, BayConsumptionFixtures.LOGICAL, position(i), new UUID(174, i),
                BayConsumptionFixtures.OWNER, ModIdentity.id("data_satellite").toString(), SatelliteKind.DATA,
                ModIdentity.id("earth").toString(), RocketNativeResources.signature(BayConsumptionFixtures.pkg(i)), record.transferId(),
                record.sourceSnapshot().contentHash(), null, record.destinationSnapshot().contentHash(), null, BayReceipt.Phase.INTENT,
                "NONE", false, false, 0, 1, 1, 0);
    }

}
