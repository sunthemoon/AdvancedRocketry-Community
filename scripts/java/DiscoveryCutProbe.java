import com.sun.jdi.Bootstrap;
import com.sun.jdi.LongValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.EventRequest;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** External read-only debugger: suspend at a pinned write boundary, never invoke target methods. */
public final class DiscoveryCutProbe {
    private static final String BASE = "io.github.sunthemoon.advancedrocketrycommunity.";
    private static final String CLASS = BASE + "persistence.migration.AtomicSavedData";
    private static final String SIGNATURE = "(Ljava/nio/file/Path;L"
            + CLASS.replace('.', '/') + "$Committer;)V";
    private static final List<String> STORES = List.of("SATELLITE_MISSIONS", "CELESTIAL", "SATELLITE_MISSIONS");

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    private static void log(String value) {
        System.out.println(value);
        System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 3, "Expected loopback port, boundary 1..4 and mission UUID");
        int port = Integer.parseInt(args[0]), boundary = Integer.parseInt(args[1]);
        UUID selectedMission = UUID.fromString(args[2]);
        require(port > 0 && port <= 65535 && boundary >= 1 && boundary <= 4, "Invalid probe bounds");
        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(value -> value.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var arguments = connector.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue(Integer.toString(port));
        arguments.get("timeout").setValue("10000");
        VirtualMachine vm = connector.attach(arguments);
        boolean reached = false;
        try {
            var classes = vm.classesByName(CLASS);
            require(classes.size() == 1, "Atomic writer must already be loaded once");
            var methods = classes.get(0).methodsByName("flush", SIGNATURE);
            require(methods.size() == 1, "Expected pinned atomic writer overload");
            var method = methods.get(0);
            int line = boundary == 4 ? 100 : 90;
            var locations = method.locationsOfLine(line);
            require(locations.size() == 1 && locations.get(0).codeIndex() == (boundary == 4 ? 394 : 326),
                    "Artifact write boundary differs from reviewed bytecode");
            var request = vm.eventRequestManager().createBreakpointRequest(locations.get(0));
            request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            request.enable();
            String bytecode = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(method.bytecodes()));
            log("MIG03_ARMED boundary=" + boundary + " line=" + line + " bci=" + locations.get(0).codeIndex()
                    + " bytecode_sha256=" + bytecode);
            int seen = 0, eventsSeen = 0;
            long deadline = System.nanoTime() + 45_000_000_000L;
            while (System.nanoTime() < deadline) {
                var events = vm.eventQueue().remove(1000);
                if (events == null) { continue; }
                require(++eventsSeen <= 32, "Probe event budget exceeded");
                for (var event : events) {
                    if (event instanceof VMDisconnectEvent) {
                        require(reached, "VM disconnected before the selected boundary");
                        log("MIG03_DISCONNECTED_AFTER_CUT");
                        return;
                    }
                    if (!(event instanceof BreakpointEvent hit) || !hit.request().equals(request)) { continue; }
                    require(!reached && events.suspendPolicy() == EventRequest.SUSPEND_ALL,
                            "Selected cut must retain full VM suspension");
                    var thread = hit.thread();
                    require(thread.frameCount() <= 96, "Unbounded stack");
                    var frames = thread.frames();
                    var recovery = frames.stream().filter(frame -> frame.location().declaringType().name()
                            .equals(BASE + "satellite.service.DiscoveryClaimRecovery")
                            && frame.location().method().name().equals("recover")).findFirst();
                    if (recovery.isEmpty() || frames.stream().noneMatch(frame -> frame.location().declaringType().name()
                            .equals(BASE + "satellite.service.SatelliteManager")
                            && frame.location().method().name().equals("claimMission"))) { continue; }
                    var mission = (ObjectReference) recovery.orElseThrow().getArgumentValues().get(2);
                    long most = ((LongValue) mission.getValue(mission.referenceType().fieldByName("mostSigBits"))).value();
                    long least = ((LongValue) mission.getValue(mission.referenceType().fieldByName("leastSigBits"))).value();
                    if (!selectedMission.equals(new UUID(most, least))) { continue; }
                    require(thread.name().equals("Server thread"), "Claim did not run on the server thread");
                    var object = frames.get(0).thisObject();
                    var type = (ObjectReference) object.getValue(classes.get(0).fieldByName("type"));
                    String name = ((StringReference) type.getValue(type.referenceType().fieldByName("name"))).value();
                    require(seen < STORES.size() && name.equals(STORES.get(seen)), "Unexpected authority write order");
                    seen++;
                    log("MIG03_WRITE ordinal=" + seen + " store=" + name + " line=" + hit.location().lineNumber()
                            + " bci=" + hit.location().codeIndex() + " thread=" + thread.uniqueID()
                            + " mission=" + selectedMission);
                    if (seen == Math.min(boundary, 3)) {
                        reached = true;
                        log("MIG03_CUT_REACHED boundary=" + boundary + " store=" + name);
                    }
                }
                // The owning harness kills the suspended process; do not dispose or resume that cut.
                if (!reached) { events.resume(); }
            }
            throw new IllegalStateException("Bounded cut/disconnect observation timed out");
        } catch (VMDisconnectedException disconnected) {
            require(reached, "VM disconnected before reaching its boundary");
            log("MIG03_DISCONNECTED_AFTER_CUT");
        } finally {
            try { vm.dispose(); } catch (VMDisconnectedException ignored) { /* Already killed by its owner. */ }
        }
    }
}
