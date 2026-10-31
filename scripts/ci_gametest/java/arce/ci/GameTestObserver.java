package arce.ci;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** CI-only registry/result observer; never included in a distributed mod. */
@Mod("arce_ci_gametest")
public final class GameTestObserver {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SELECTOR = "advancedrocketrycommunity.gameTestBatches";
    private static final int SHARDS = 4;

    public GameTestObserver() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::beforeTests);
    }

    private void beforeTests(ServerAboutToStartEvent event) {
        if (!(event.getServer() instanceof GameTestServer)) {
            throw new IllegalStateException("The CI observer is only for the development GameTest server");
        }
        int shard = Integer.parseInt(System.getProperty("arce.ci.shard"));
        if (shard < 0 || shard >= SHARDS || !System.getProperty(SELECTOR, "").isEmpty()) {
            throw new IllegalStateException("Invalid or overridden CI shard selector");
        }
        List<TestFunction> functions = new ArrayList<>(GameTestRegistry.getAllTestFunctions());
        functions.sort(Comparator.comparing(TestFunction::getBatchName).thenComparing(TestFunction::getTestName)
                .thenComparing(TestFunction::getStructureName));
        List<Map<String, Object>> inventory = functions.stream().map(GameTestObserver::identity).toList();
        TreeSet<String> batches = new TreeSet<>();
        for (TestFunction function : functions) {
            if (shardFor(function.getBatchName()) == shard) {
                batches.add(function.getBatchName());
            }
        }
        if (batches.isEmpty()) {
            throw new IllegalStateException("CI shard has no tests");
        }
        // The existing NORMAL-priority GameTestBatchFilter reads this property.
        // It retains vanilla's ordering, hooks, batch boundaries and assertions.
        System.setProperty(SELECTOR, String.join(",", batches));
        Path output = Path.of(System.getProperty("arce.ci.output"));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema", 1);
        report.put("shard", shard);
        report.put("inventory", inventory);
        report.put("bundle_sha256", digest(Path.of(System.getProperty("arce.ci.bundle"))));
        List<Map<String, Object>> executed = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        report.put("executed", executed);
        report.put("failed", failed);
        report.put("complete", false);
        write(output.resolve("inventory.json"), report);
        GlobalTestReporter.replaceWith(new TestReporter() {
            private final LogTestReporter vanilla = new LogTestReporter();

            @Override
            public void onTestFailed(GameTestInfo test) {
                vanilla.onTestFailed(test);
                failed.add(identity(test.getTestFunction()));
                executed.add(identity(test.getTestFunction()));
            }

            @Override
            public void onTestSuccess(GameTestInfo test) {
                vanilla.onTestSuccess(test);
                executed.add(identity(test.getTestFunction()));
            }

            @Override
            public void finish() {
                vanilla.finish();
                report.put("complete", true);
                write(output.resolve("result.json"), report);
            }
        });
    }

    private static Map<String, Object> identity(TestFunction test) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", test.getBatchName());
        result.put("name", test.getTestName());
        result.put("structure", test.getStructureName());
        result.put("required", test.isRequired());
        result.put("timeout_ticks", test.getMaxTicks());
        result.put("setup_ticks", test.getSetupTicks());
        result.put("max_attempts", test.getMaxAttempts());
        result.put("required_successes", test.getRequiredSuccesses());
        return result;
    }

    static int shardFor(String batch) {
        if (!batch.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Unsafe CI batch name: " + batch);
        }
        byte[] hash = digest(batch.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long first = ((hash[0] & 255L) << 24) | ((hash[1] & 255L) << 16)
                | ((hash[2] & 255L) << 8) | (hash[3] & 255L);
        return (int) (first % SHARDS);
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String digest(Path path) {
        try {
            return HexFormat.of().formatHex(digest(Files.readAllBytes(path)));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read CI bundle identity", exception);
        }
    }

    private static void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, JSON.toJson(value) + "\n");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write CI GameTest evidence", exception);
        }
    }
}
