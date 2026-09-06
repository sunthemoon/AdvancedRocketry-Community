import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.nio.file.*;
import java.util.*;

/** Controlled readiness-boundary injection in an owned loopback JVM; no class/world edits. */
public final class ArceReconnectProbe {
    static final String BASE = "io.github.sunthemoon.advancedrocketrycommunity.";
    static final String RECOVERY = BASE + "rocket.server.RocketTransferRecoveryService";
    static final String OWNER = "62bbb9cb-b2fa-39aa-9a6b-430b71f5493f";
    static VirtualMachine vm;
    static Path work;
    static int sequence, lastSize = -1, holdChecks;
    static final Map<String, Integer> delayed = new HashMap<>(), moves = new HashMap<>();

    static void log(String text) {
        System.out.println("EVENT seq=" + (++sequence) + " ms=" + System.currentTimeMillis() + " " + text);
        System.out.flush();
    }

    static Value field(ObjectReference value, String name) {
        Field field = value.referenceType().fieldByName(name);
        if (field == null) throw new IllegalStateException("Missing field " + name);
        return value.getValue(field);
    }

    static String uuid(ObjectReference value) {
        if (!value.referenceType().name().equals("java.util.UUID")) {
            Field id = value.referenceType().allFields().stream().filter(f ->
                f.declaringType().name().equals("net.minecraft.world.entity.Entity")
                    && f.typeName().equals("java.util.UUID")).findFirst().orElseThrow();
            value = (ObjectReference) value.getValue(id);
        }
        return new UUID(((LongValue) field(value, "mostSigBits")).value(),
            ((LongValue) field(value, "leastSigBits")).value()).toString();
    }

    static String vectors(ObjectReference player) {
        var out = new StringBuilder();
        for (Field f : player.referenceType().allFields()) {
            if (!f.isStatic() && f.declaringType().name().equals("net.minecraft.world.entity.Entity")
                    && f.typeName().equals("net.minecraft.world.phys.Vec3")) {
                ObjectReference vector = (ObjectReference) player.getValue(f);
                out.append(f.name()).append(':');
                if (vector != null) for (Field axis : vector.referenceType().fields()) {
                    if (!axis.isStatic() && axis.typeName().equals("double"))
                        out.append(axis.name()).append('=').append(vector.getValue(axis)).append(',');
                }
                out.append(';');
            }
        }
        return out.toString();
    }

    static void breakpoint(Method method, String kind) {
        var request = vm.eventRequestManager().createBreakpointRequest(method.location());
        request.putProperty("kind", kind);
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        request.enable();
        log("INSTALL kind=" + kind + " method=" + method.declaringType().name() + "." + method.name() + method.signature());
    }

    static ReferenceType type(String name) {
        var found = vm.classesByName(name);
        if (found.size() != 1) throw new IllegalStateException("Expected loaded type " + name);
        return found.get(0);
    }

    static void install(String name, String method, String kind) {
        var methods = type(name).methodsByName(method);
        if (methods.size() != 1) throw new IllegalStateException("Ambiguous method " + name + "." + method);
        breakpoint(methods.get(0), kind);
    }

    static void observe(BreakpointEvent event) throws Exception {
        ThreadReference thread = event.thread();
        if (!thread.name().equals("Server thread")) return;
        StackFrame frame = thread.frame(0);
        String kind = (String) event.request().getProperty("kind");
        if (kind.equals("readiness")) {
            if (thread.frameCount() < 2) return;
            StackFrame caller = thread.frame(1);
            if (!caller.location().declaringType().name().equals(RECOVERY)
                    || !caller.location().method().name().equals("tryReconnect")) return;
            ObjectReference player = (ObjectReference) caller.getArgumentValues().get(0);
            String id = uuid(player);
            String mode = Files.exists(work.resolve("gate.txt")) ? Files.readString(work.resolve("gate.txt")).trim() : "delay";
            int calls = delayed.getOrDefault(id, 0);
            boolean held = mode.equals("hold-owner") && id.equals(OWNER);
            boolean force = held || (mode.equals("delay") && calls < 4);
            delayed.put(id, calls + 1);
            log("READINESS uuid=" + id + " forced_false=" + force + " mode=" + mode
                + " call=" + (calls + 1) + " method=" + event.location().method().name()
                + " vectors=" + vectors(player));
            if (force) {
                thread.forceEarlyReturn(vm.mirrorOf(false));
                if (held && ++holdChecks == 3)
                    Files.writeString(work.resolve("hold-observed.txt"), "three readiness checks held\n");
            }
        } else if (kind.equals("next")) {
            ObjectReference map = (ObjectReference) field(frame.thisObject(), "waiting");
            int size = ((IntegerValue) field(map, "size")).value();
            if (size > 0 || size != lastSize) log("QUEUE_NEXT size=" + size + " tick=" + frame.getArgumentValues().get(0));
            lastSize = size;
        } else {
            var args = frame.getArgumentValues();
            String id = uuid((ObjectReference) args.get(0));
            String caller = thread.frameCount() > 1 ? thread.frame(1).location().method().name() : "none";
            String detail = "";
            if (kind.equals("offer")) detail = " tick=" + args.get(1);
            if (kind.equals("move")) {
                int count = moves.merge(id, 1, Integer::sum);
                detail = " move_count=" + count + " rocket=" + uuid((ObjectReference) args.get(1))
                    + " vectors_before=" + vectors((ObjectReference) args.get(0));
            }
            log(kind.toUpperCase(Locale.ROOT) + " uuid=" + id + " caller=" + caller + detail);
        }
    }

    public static void main(String[] args) throws Exception {
        work = Path.of(args[1]).toAbsolutePath();
        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
            .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var options = connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1");
        options.get("port").setValue(args[0]);
        options.get("timeout").setValue("10000");
        vm = connector.attach(options);
        try {
            if (!vm.canForceEarlyReturn()) throw new IllegalStateException("Early-return injection unsupported");
            var methods = type("net.minecraft.server.level.ServerLevel").methods().stream()
                .filter(m -> m.signature().equals("(J)Z") && !m.isNative() && !m.isAbstract()).toList();
            if (methods.isEmpty()) throw new IllegalStateException("No entity-readiness candidates");
            for (Method method : methods) breakpoint(method, "readiness");
            String queue = BASE + "rocket.flight.RocketPassengerReconnectQueue";
            install(queue, "offer", "offer");
            install(queue, "next", "next");
            install(queue, "complete", "complete");
            install(RECOVERY, "onPlayerLoggedOut", "logout");
            install(BASE + "rocket.server.RocketTransferEntities", "movePassenger", "move");
            log("PROBE_READY controlled_boundary=true");
            long deadline = System.currentTimeMillis() + 480000;
            while (System.currentTimeMillis() < deadline && !Files.exists(work.resolve("probe-stop.txt"))) {
                EventSet events = vm.eventQueue().remove(250);
                if (events == null) continue;
                try {
                    for (Event event : events) {
                        if (event instanceof BreakpointEvent hit) observe(hit);
                        if (event instanceof VMDisconnectEvent || event instanceof VMDeathEvent) return;
                    }
                } finally { events.resume(); }
            }
            if (!Files.exists(work.resolve("probe-stop.txt"))) throw new IllegalStateException("Probe deadline elapsed");
            log("PROBE_STOP requested=true");
        } finally {
            try { vm.dispose(); } catch (VMDisconnectedException ignored) { }
        }
    }
}
