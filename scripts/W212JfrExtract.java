import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import jdk.jfr.consumer.RecordingFile;

/** Streaming elapsed class-load/GC/blocking events, not execution-sample estimates. */
public class W212JfrExtract {
    public static void main(String[] args) throws Exception {
        var types = Set.of("jdk.ClassLoad", "jdk.GCPhasePause", "jdk.ThreadPark", "jdk.JavaMonitorEnter", "jdk.JavaMonitorWait");
        try (var file = new RecordingFile(Path.of(args[0]))) {
            while (file.hasMoreEvents()) {
                var event = file.readEvent(); var type = event.getEventType().getName();
                if (!types.contains(type)) { continue; }
                var thread = event.getThread();
                var detail = type.equals("jdk.ClassLoad") ? event.getClass("loadedClass").getName() : "";
                System.out.println(type + "\t" + nanos(event.getStartTime()) + "\t" + nanos(event.getEndTime())
                        + "\t" + (thread == null ? "" : thread.getJavaName()) + "\t" + detail);
            }
        }
    }
    private static long nanos(Instant time) { return time.getEpochSecond() * 1_000_000_000L + time.getNano(); }
}
