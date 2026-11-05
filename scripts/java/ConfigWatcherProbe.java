import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded native watcher diagnostic, run with Java 17 source-file mode and NightConfig core/toml on the classpath.
 * The sole argument is an existing scratch parent; the probe deletes only its own temporary files and directory.
 */
class ConfigWatcherProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a scratch parent directory");
        }
        Path directory = Files.createTempDirectory(Path.of(args[0]), "w295-watcher-").toAbsolutePath();
        Path target = directory.resolve("common.toml");
        Path gate = directory.resolve("gate.toml");
        Path fence = directory.resolve("fence.toml");
        Files.writeString(target, "[endgame.laserDrill]\nphysicalMining = false\n");
        Files.writeString(gate, "0");
        Files.writeString(fence, "0");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        FileWatcher watcher = new FileWatcher();
        try (var raw = directory.getFileSystem().newWatchService();
             var loaded = CommentedFileConfig.builder(target).sync().build()) {
            directory.register(raw, StandardWatchEventKinds.ENTRY_MODIFY);
            loaded.load();
            watcher.addWatch(target, () -> {
                loaded.load();
                callbacks.incrementAndGet();
            });
            watcher.addWatch(gate, () -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Gate release timed out");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            });
            watcher.addWatch(fence, drained::countDown);
            System.out.println("os=" + System.getProperty("os.name") + " java=" + System.getProperty("java.version"));
            System.out.println("watched_and_edited_path=" + target);
            overwriteByte(gate, '1');
            require(entered.await(5, TimeUnit.SECONDS), "Watcher did not enter gate callback");

            // Hold the real callback thread so native events coalesce, as they may while another config reload runs.
            Files.writeString(target, "[endgame.laserDrill]\nphysicalMining = true\n");
            overwriteByte(fence, '1');
            var key = raw.poll(5, TimeUnit.SECONDS);
            require(key != null, "No raw filesystem events");
            int targetCount = 0;
            for (var event : key.pollEvents()) {
                System.out.println("raw_event=" + event.kind() + " path=" + event.context() + " count=" + event.count());
                if (event.context().equals(target.getFileName())) {
                    targetCount += event.count();
                }
            }
            require(targetCount > 1, "Native events did not coalesce on this host");
            release.countDown();
            require(drained.await(5, TimeUnit.SECONDS), "Watcher did not process fence callback");
            System.out.println("target_callbacks=" + callbacks.get());
            System.out.println("loaded_physicalMining=" + loaded.get("endgame.laserDrill.physicalMining"));
            try (var restarted = CommentedFileConfig.of(target)) {
                restarted.load();
                System.out.println("fresh_load_physicalMining=" + restarted.get("endgame.laserDrill.physicalMining"));
                require(Boolean.TRUE.equals(restarted.get("endgame.laserDrill.physicalMining")), "Fresh load missed edit");
            }
            require(callbacks.get() == 0, "This runtime delivered the coalesced target notification");
            require(Boolean.FALSE.equals(loaded.get("endgame.laserDrill.physicalMining")), "Loaded value changed");
            System.out.println("PASS: missed native notification reproduced; fresh load applies the edit");
        } finally {
            release.countDown();
            watcher.stop();
            Files.deleteIfExists(fence);
            Files.deleteIfExists(gate);
            Files.deleteIfExists(target);
            Files.delete(directory);
        }
    }

    private static void overwriteByte(Path path, char value) throws Exception {
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{(byte) value}), 0);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
