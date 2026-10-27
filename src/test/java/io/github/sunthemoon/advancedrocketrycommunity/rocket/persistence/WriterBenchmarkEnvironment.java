package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

/** W129a B2/B3a: the host, JVM and collector, sampled heap, GC pauses and the Windows working set of this process. */
final class WriterBenchmarkEnvironment implements AutoCloseable {
    /** docs/17 section 4, unchanged: P95 and P99 of server-thread tick work. */
    static final double P95_MS = 50, P99_MS = 100;
    record Pause(String collector, String action, long startMs, long durationMs) { }

    private final AtomicLong sampledPeak = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread sampler;
    private final List<Pause> pauses = new CopyOnWriteArrayList<>();
    private final List<Runnable> unsubscribe = new ArrayList<>();

    WriterBenchmarkEnvironment() {
        sampler = new Thread(() -> {
            var memory = ManagementFactory.getMemoryMXBean();
            while (running.get()) {
                sampledPeak.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(), Math::max);
                try { Thread.sleep(1); } catch (InterruptedException stop) { return; }
            }
        }, "W129a-heap-sampler");
        sampler.setDaemon(true); sampler.start();
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean instanceof NotificationEmitter emitter) {
                NotificationListener listener = (notification, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        var info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                        pauses.add(new Pause(info.getGcName(), info.getGcAction(), info.getGcInfo().getStartTime(),
                                info.getGcInfo().getDuration()));
                    }
                };
                emitter.addNotificationListener(listener, null, null);
                unsubscribe.add(() -> {
                    try { emitter.removeNotificationListener(listener); } catch (Exception ignored) { /* already gone */ }
                });
            }
        }
    }

    static Map<String, Object> describe() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        var report = new LinkedHashMap<String, Object>();
        report.put("pid", ProcessHandle.current().pid());
        report.put("java", System.getProperty("java.vm.vendor") + " " + System.getProperty("java.vm.name") + " "
                + System.getProperty("java.runtime.version"));
        report.put("jvm_arguments", runtime.getInputArguments());
        report.put("collectors", ManagementFactory.getGarbageCollectorMXBeans().stream().map(GarbageCollectorMXBean::getName).toList());
        report.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("processor", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unknown"));
        report.put("physical_memory_bytes", os.getTotalMemorySize());
        report.put("free_physical_memory_bytes_at_start", os.getFreeMemorySize());
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        return report;
    }

    void resetPoolPeaks() {
        sampledPeak.set(0);
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) { pool.resetPeakUsage(); }
    }

    Map<String, Object> heap() {
        var report = new LinkedHashMap<String, Object>();
        report.put("sampled_peak_used_bytes", sampledPeak.get());
        long pools = 0;
        var byPool = new LinkedHashMap<String, Long>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) { byPool.put(pool.getName(), pool.getPeakUsage().getUsed()); pools += pool.getPeakUsage().getUsed(); }
        }
        report.put("pool_peak_used_bytes", byPool);
        report.put("sum_of_pool_peaks_bytes", pools);
        return report;
    }

    int pauseCount() { return pauses.size(); }

    Map<String, Object> pauses(int from) {
        var window = List.copyOf(pauses.subList(from, pauses.size()));
        var report = new LinkedHashMap<String, Object>();
        report.put("count", window.size());
        report.put("maximum_ms", window.stream().mapToLong(Pause::durationMs).max().orElse(0));
        report.put("cumulative_ms", window.stream().mapToLong(Pause::durationMs).sum());
        var byAction = new LinkedHashMap<String, Long>();
        for (var pause : window) { byAction.merge(pause.collector() + " / " + pause.action(), 1L, Long::sum); }
        report.put("by_collector_action", byAction);
        report.put("pauses", window);
        return report;
    }

    long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    /** Heap still used after a full collection: the resident and pinned graphs, not transient write trees. */
    static long retainedAfterGc() throws InterruptedException {
        for (int i = 0; i < 3; i++) { System.gc(); Thread.sleep(200); }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    /** Windows WorkingSet64 and PeakWorkingSet64 of this JVM, read once through PowerShell; empty elsewhere. */
    static Map<String, Object> workingSet() {
        var report = new LinkedHashMap<String, Object>();
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
            report.put("status", "UNAVAILABLE_NOT_WINDOWS"); return report;
        }
        long pid = ProcessHandle.current().pid();
        try {
            // No embedded quotes: Windows argument quoting would otherwise split the command.
            var process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "$p = Get-Process -Id " + pid + "; $p.WorkingSet64; $p.PeakWorkingSet64")
                    .redirectErrorStream(true).start();
            process.getOutputStream().close();
            String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); report.put("status", "TIMEOUT"); return report; }
            var numbers = text.lines().map(String::trim).filter(line -> line.matches("[0-9]+")).toList();
            if (numbers.size() != 2) { report.put("status", "FAILED"); report.put("output", text); return report; }
            report.put("working_set_bytes", Long.parseLong(numbers.get(0)));
            report.put("peak_working_set_bytes", Long.parseLong(numbers.get(1)));
            report.put("status", "MEASURED");
        } catch (Exception failure) {
            report.put("status", "FAILED"); report.put("error", failure.toString());
        }
        return report;
    }

    /** Thread CPU and allocated bytes of the live threads with this exact name. */
    static Map<String, Object> thread(String name) {
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        var report = new LinkedHashMap<String, Object>();
        long cpu = 0, allocated = 0; int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals(name)) {
                count++; cpu += threads.getThreadCpuTime(thread.getId()); allocated += threads.getThreadAllocatedBytes(thread.getId());
            }
        }
        report.put("live_threads", count); report.put("cpu_ms", cpu / 1e6); report.put("allocated_bytes", allocated);
        return report;
    }

    @Override public void close() throws InterruptedException {
        running.set(false); sampler.interrupt(); sampler.join();
        unsubscribe.forEach(Runnable::run);
    }
}
