package arce.trace;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GameTestBatch;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;
import org.slf4j.LoggerFactory;

@Mod("arce_journal_trace")
public final class JournalTrace {
    private final Set<UUID> seen = new HashSet<>();
    private final Set<UUID> drops = new HashSet<>();
    private long mutation = -1;
    private boolean clean;
    public JournalTrace() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, this::select);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::order);
    }
    private void select(ServerAboutToStartEvent event) {
        var selected = System.getProperty("arce.trace.order", "");
        if (!selected.isEmpty()) { System.setProperty("advancedrocketrycommunity.gameTestBatches", selected); }
    }
    private void order(ServerAboutToStartEvent event) {
        var selected = System.getProperty("arce.trace.order", "");
        if (selected.isEmpty()) { return; }
        var names = Arrays.asList(selected.split(","));
        List<GameTestBatch> batches = ObfuscationReflectionHelper.getPrivateValue(GameTestServer.class,
            (GameTestServer) event.getServer(), "f_177587_");
        if (batches.size() != names.size()) { throw new IllegalStateException("Targeted batch selection mismatch"); }
        batches.sort(Comparator.comparingInt(batch -> names.indexOf(batch.getName().replaceFirst(":\\d+$", ""))));
        LoggerFactory.getLogger("arce_journal_trace").info("TRACE_ORDER {}", batches.stream().map(GameTestBatch::getName).toList());
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        var journal = RocketTransferSavedData.get(event.getServer());
        if (mutation != journal.mutationSequence() || clean != journal.clean()) {
            mutation = journal.mutationSequence(); clean = journal.clean();
            LoggerFactory.getLogger("arce_journal_trace").info("TRACE_JOURNAL_STATE tick={} sequence={} clean={} records={} effects={}",
                event.getServer().getTickCount(), mutation, clean, journal.entries().size(), journal.outstandingEffectCount());
        }
        try {
            var field = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowGameTests.class
                .getDeclaredField("RUNNING");
            field.setAccessible(true);
            for (var scenario : (Iterable<?>) field.get(null)) {
                if (!scenario.getClass().getSimpleName().equals("FiveAndSixth")) { continue; }
                var phase = scenario.getClass().getSuperclass().getDeclaredField("step");
                phase.setAccessible(true);
                if (phase.getInt(scenario) == 1 && !journal.clean()) {
                    LoggerFactory.getLogger("arce_journal_trace").info("TRACE_LOADER_PRECONDITION tick={} clean=false records={} effects={}",
                        event.getServer().getTickCount(), journal.entries().stream().map(r -> r.transferId().toString()).toList(),
                        journal.outstandingEffectCount());
                }
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        for (var level : event.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && drops.add(item.getUUID())) {
                    LoggerFactory.getLogger("arce_journal_trace").info("TRACE_DROP tick={} item={} at={}",
                        event.getServer().getTickCount(), item.getItem(), item.position());
                }
            }
        }
        for (var record : RocketTransferSavedData.get(event.getServer()).entries()) {
            if (seen.add(record.transferId())) {
                LoggerFactory.getLogger("arce_journal_trace").info("TRACE_JOURNAL_NEW transfer={} logical={} phase={} source={} origin={} passengers={}",
                    record.transferId(), record.logicalRocketId(), record.phase(), record.sourceEntityId(),
                    record.sourceSnapshot().sourceOrigin(), record.sourceFlightData().passengers());
            }
        }
    }
}
