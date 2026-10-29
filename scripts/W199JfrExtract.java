import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/** Streaming, JDK-only export. No recording or server modification is performed here. */
public class W199JfrExtract {
    private static final Set<String> EVENTS = Set.of("jdk.ExecutionSample", "jdk.NativeMethodSample",
            "jdk.ThreadPark", "jdk.JavaMonitorWait", "jdk.JavaMonitorEnter", "jdk.FileRead", "jdk.FileWrite",
            "jdk.GCPhasePause", "jdk.SafepointBegin", "jdk.SafepointStateSynchronization");

    public static void main(String[] args) throws Exception {
        try (var file = new RecordingFile(Path.of(args[0]))) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                String type = event.getEventType().getName();
                if (!EVENTS.contains(type)) { continue; }
                RecordedThread thread = event.hasField("sampledThread") ? event.getThread("sampledThread") : event.getThread();
                String frames = event.getStackTrace() == null ? "" : event.getStackTrace().getFrames().stream()
                        .map(frame -> frame.getMethod().getType().getName() + "." + frame.getMethod().getName()
                                + ":" + frame.getLineNumber()).collect(Collectors.joining(";"));
                System.out.println(type + "\t" + nanos(event.getStartTime()) + "\t" + nanos(event.getEndTime())
                        + "\t" + (thread == null ? "" : thread.getJavaName()) + "\t" + frames);
            }
        }
    }

    private static long nanos(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }
}
