package arce.w247;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/**
 * W247 diagnostic only, for focused local GameTest runs; never part of a distributed mod or of CI.
 * Writes one JSON line for every server tick whose period (tick start to next tick start) is at least
 * 100 ms, with the chunk events counted in that period, so a stall can be attributed to chunk generation,
 * loading from disk, unloading or saving. Nothing here changes game or test behaviour.
 */
@Mod("arce_w247_probe")
public final class TickProbe {
    private static final long REPORT_NS = 100_000_000L;
    private final AtomicInteger generated = new AtomicInteger();
    private final AtomicInteger loaded = new AtomicInteger();
    private final AtomicInteger unloaded = new AtomicInteger();
    private final AtomicInteger saved = new AtomicInteger();
    private final AtomicInteger levelSaves = new AtomicInteger();
    private BufferedWriter writer;
    private MinecraftServer server;
    private long tick;
    private long startNs;
    private long startMs;
    private long workNs;
    private long totalTicks;
    private long totalGenerated;
    private long totalLoaded;
    private long totalUnloaded;
    private long totalSaved;
    private long longestNs;

    public TickProbe() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::open);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::first);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::last);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::close);
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (!event.getLevel().isClientSide()) { (event.isNewChunk() ? generated : loaded).incrementAndGet(); }
        });
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            if (!event.getLevel().isClientSide()) { unloaded.incrementAndGet(); }
        });
        MinecraftForge.EVENT_BUS.addListener((ChunkDataEvent.Save event) -> saved.incrementAndGet());
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Save event) -> levelSaves.incrementAndGet());
    }

    private void open(ServerAboutToStartEvent event) {
        if (!(event.getServer() instanceof GameTestServer)) {
            throw new IllegalStateException("The W247 probe is only for the development GameTest server");
        }
        server = event.getServer();
        try {
            Path output = Path.of(System.getProperty("arce.w247.output"));
            Files.createDirectories(output.getParent());
            writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void first(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || writer == null) { return; }
        long now = System.nanoTime();
        if (startNs != 0L) {
            long period = now - startNs;
            longestNs = Math.max(longestNs, period);
            int g = generated.getAndSet(0), l = loaded.getAndSet(0), u = unloaded.getAndSet(0), s = saved.getAndSet(0);
            int w = levelSaves.getAndSet(0);
            totalTicks++; totalGenerated += g; totalLoaded += l; totalUnloaded += u; totalSaved += s;
            if (period >= REPORT_NS) {
                var overworld = server.overworld();
                write(String.format(java.util.Locale.ROOT,
                        "{\"tick\":%d,\"start_ms\":%d,\"period_ms\":%.1f,\"work_ms\":%.1f,\"generated\":%d,\"loaded\":%d,"
                                + "\"unloaded\":%d,\"saved\":%d,\"level_saves\":%d,\"overworld_loaded_after\":%d}",
                        tick, startMs, period / 1e6, workNs / 1e6, g, l, u, s, w,
                        overworld.getChunkSource().getLoadedChunksCount()));
            }
        }
        tick++;
        startNs = now;
        startMs = System.currentTimeMillis();
        workNs = 0L;
    }

    private void last(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && startNs != 0L) { workNs = System.nanoTime() - startNs; }
    }

    private void close(ServerStoppingEvent event) {
        if (writer == null) { return; }
        write(String.format(java.util.Locale.ROOT,
                "{\"summary\":true,\"ticks\":%d,\"generated\":%d,\"loaded\":%d,\"unloaded\":%d,\"saved\":%d,\"longest_ms\":%.1f}",
                totalTicks, totalGenerated, totalLoaded, totalUnloaded, totalSaved, longestNs / 1e6));
        try { writer.close(); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        writer = null;
    }

    private void write(String line) {
        try { writer.write(line); writer.newLine(); writer.flush(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
