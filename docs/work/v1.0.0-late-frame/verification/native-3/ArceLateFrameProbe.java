import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.nio.file.*;
import java.util.*;

/** External loopback debugger probe; never transforms classes or writes world state. */
public final class ArceLateFrameProbe {
    static final String BASE = "io.github.sunthemoon.advancedrocketrycommunity.";
    static VirtualMachine vm;
    static ObjectReference held, oldMenu;
    static final List<ObjectReference> retained = new ArrayList<>();
    static ClassType type(String name) {
        var matches = vm.classesByName(name);
        if (matches.size() != 1) throw new IllegalStateException("Expected loaded class: " + name);
        return (ClassType) matches.get(0);
    }
    static Method method(ReferenceType type, String name) {
        var matches = type.methodsByName(name).stream().filter(m -> m.declaringType().equals(type)).toList();
        if (matches.size() != 1) throw new IllegalStateException("Ambiguous method: " + type.name() + "." + name + " " + matches);
        return matches.get(0);
    }
    static Value field(ObjectReference object, String name) {
        Field field = object.referenceType().fieldByName(name);
        if (field == null) throw new IllegalStateException("Missing field: " + name);
        return object.getValue(field);
    }
    static ObjectReference keep(ObjectReference object) {
        object.disableCollection(); retained.add(object); return object;
    }
    static ObjectReference minecraft() {
        ClassType type = type("net.minecraft.client.Minecraft");
        for (Field field : type.fields()) {
            if (field.isStatic() && field.typeName().equals(type.name())) {
                Value value = type.getValue(field);
                if (value != null) return (ObjectReference)value;
            }
        }
        throw new IllegalStateException("No Minecraft singleton");
    }
    static ObjectReference player() { return (ObjectReference)field(minecraft(), "f_91074_"); }
    static ObjectReference menu() { return (ObjectReference)field(player(), "f_36096_"); }
    static int integer(ObjectReference object, String name) { return ((IntegerValue)field(object, name)).value(); }
    static List<Value> state(ObjectReference menu) {
        return List.of(field(menu, "receivedPlan"), field(menu, "receivedQuotes"), field(menu, "planReceived"));
    }
    static boolean flight(ObjectReference menu) {
        return menu.referenceType().name().equals(BASE + "rocket.menu.RocketFlightMenu");
    }
    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static BreakpointRequest breakpoint(Method method) {
        BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(method.location());
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        request.enable(); return request;
    }
    interface Suspended { void run(ThreadReference thread) throws Exception; }
    static void onTick(Suspended action) throws Exception {
        BreakpointRequest request = breakpoint(method(type("net.minecraft.client.Minecraft"), "m_91383_"));
        long deadline = System.currentTimeMillis() + 10000;
        try {
            while (System.currentTimeMillis() < deadline) {
                EventSet events = vm.eventQueue().remove(1000);
                if (events == null) continue;
                try {
                    for (Event event : events) {
                        if (event instanceof BreakpointEvent hit && hit.request().equals(request)) {
                            request.disable();
                            require(hit.thread().name().equals("Render thread"), "Wrong delivery thread");
                            action.run(hit.thread()); return;
                        }
                    }
                } finally { events.resume(); }
            }
            throw new IllegalStateException("Client tick observation timed out");
        } finally { vm.eventRequestManager().deleteEventRequest(request); }
    }
    static void capture() throws Exception {
        String name = BASE + "client.RocketFlightPlanHandler";
        ClassPrepareRequest prepare = vm.eventRequestManager().createClassPrepareRequest();
        prepare.addClassFilter(name); prepare.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); prepare.enable();
        BreakpointRequest request = vm.classesByName(name).isEmpty() ? null : breakpoint(method(type(name), "handle"));
        long deadline = System.currentTimeMillis() + 120000;
        System.out.println("CAPTURE_ARMED"); System.out.flush();
        try {
            while (System.currentTimeMillis() < deadline) {
                EventSet events = vm.eventQueue().remove(1000);
                if (events == null) continue;
                try {
                    for (Event event : events) {
                        if (event instanceof ClassPrepareEvent ready) {
                            request = breakpoint(method(ready.referenceType(), "handle"));
                        } else if (event instanceof BreakpointEvent hit && hit.request().equals(request)) {
                            request.disable();
                            require(hit.thread().name().equals("Render thread"), "Network dispatch not on render thread");
                            held = keep((ObjectReference)hit.thread().frame(0).getArgumentValues().get(0));
                            oldMenu = keep(menu());
                            require(flight(oldMenu), "First frame has no flight menu");
                            require(integer(held, "containerId") == integer(oldMenu, "f_38840_"), "Container mismatch");
                            require(integer(held, "rocketEntityId") == integer(oldMenu, "rocketEntityId"), "Rocket mismatch");
                            System.out.println("CAPTURE packet=" + held.uniqueID() + " menu=" + oldMenu.uniqueID()
                                + " container=" + integer(held,"containerId") + " rocket=" + integer(held,"rocketEntityId"));
                            return;
                        }
                    }
                } finally { events.resume(); }
            }
            throw new IllegalStateException("Native frame capture timed out");
        } finally {
            vm.eventRequestManager().deleteEventRequest(prepare);
            if (request != null) vm.eventRequestManager().deleteEventRequest(request);
        }
    }
    static void deliver(ThreadReference thread, ObjectReference packet) throws Exception {
        ClassType handler = type(BASE + "client.RocketFlightPlanHandler");
        handler.invokeMethod(thread, method(handler,"handle"), List.of(packet), ObjectReference.INVOKE_SINGLE_THREADED);
    }
    static ObjectReference construct(ThreadReference thread, String name, List<Value> args) throws Exception {
        ClassType type = type(BASE + name);
        return keep(type.newInstance(thread, method(type,"<init>"), args, ObjectReference.INVOKE_SINGLE_THREADED));
    }
    static ObjectReference packet(ThreadReference thread, int container, int rocket, Value plan, Value quotes) throws Exception {
        return construct(thread,"rocket.network.RocketFlightPlanPacket",
            List.of(vm.mirrorOf(container),vm.mirrorOf(rocket),plan,quotes));
    }
    static ObjectReference changedPacket(ThreadReference thread, int container, int rocket) throws Exception {
        List<Value> quotes = new ArrayList<>();
        for (int fuel : new int[]{111,222,333}) quotes.add(construct(thread,
            "rocket.menu.RocketFlightQuotes$Quote",List.of(vm.mirrorOf(fuel),vm.mirrorOf(true))));
        ObjectReference changedQuotes = construct(thread,"rocket.menu.RocketFlightQuotes",quotes);
        ClassType destination = type(BASE + "rocket.flight.RocketDestination");
        ObjectReference changedPlan = construct(thread,"rocket.menu.RocketFlightPlanSnapshot",
            Arrays.asList(destination.getValue(destination.fieldByName("MOON")),null));
        return packet(thread,container,rocket,changedPlan,changedQuotes);
    }
    static void check(String operation) throws Exception {
        onTick(thread -> {
            List<Value> oldBefore = state(oldMenu);
            if (operation.equals("reject-no-player")) {
                require(player() == null,"Player still present");
                deliver(thread,held);
                require(player() == null && oldBefore.equals(state(oldMenu)),"Disconnected delivery mutated state");
            } else {
                ObjectReference current = menu();
                if (operation.equals("reject-closed")) {
                    require(!flight(current),"Menu still open");
                    deliver(thread,held);
                    require(menu().equals(current) && oldBefore.equals(state(oldMenu)),"Closed delivery mutated state");
                } else {
                    require(flight(current) && !current.equals(oldMenu),"Need replacement flight menu");
                    List<Value> before = state(current);
                    int container = integer(current,"f_38840_"), rocket = integer(current,"rocketEntityId");
                    if (operation.equals("reject-reopened")) {
                        require(container != integer(held,"containerId") && rocket == integer(held,"rocketEntityId"),"Not same-rocket reopening");
                        deliver(thread,held);
                        require(before.equals(state(current)),"Old container frame applied");
                    } else if (operation.equals("reject-wrong-rocket")) {
                        ObjectReference wrong = packet(thread,container,rocket + 1,field(held,"plan"),field(held,"quotes"));
                        deliver(thread,wrong);
                        require(before.equals(state(current)),"Wrong rocket frame applied");
                    } else if (operation.equals("positive")) {
                        ObjectReference valid = changedPacket(thread,container,rocket);
                        try {
                            deliver(thread,valid);
                            require(field(current,"receivedPlan").equals(field(valid,"plan"))
                                && field(current,"receivedQuotes").equals(field(valid,"quotes")),"Matching frame not applied");
                        } finally {
                            deliver(thread,packet(thread,container,rocket,before.get(0),before.get(1)));
                        }
                        require(before.equals(state(current)),"Positive-control restore failed");
                    } else throw new IllegalArgumentException(operation);
                    require(menu().equals(current) && oldBefore.equals(state(oldMenu)),"Wrong menu identity or old menu mutation");
                    System.out.println("STATE menu=" + current.uniqueID() + " container=" + container + " rocket=" + rocket
                        + " plan=" + field(current,"receivedPlan") + " quotes=" + field(current,"receivedQuotes"));
                }
            }
            System.out.println("PASS " + operation + " thread=" + thread.name());
        });
    }
    public static void main(String[] args) throws Exception {
        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
            .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var options = connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1"); options.get("port").setValue(args[0]);
        options.get("timeout").setValue("10000"); vm = connector.attach(options);
        try {
            capture();
            onTick(thread -> {
                require(((BooleanValue)field(oldMenu,"planReceived")).value(),"Native frame not applied before probe");
                held = changedPacket(thread,integer(held,"containerId"),integer(held,"rocketEntityId"));
                System.out.println("HELD_SENTINEL captured-envelope=true destination=MOON quotes=111,222,333 packet="+held.uniqueID());
            });
            System.out.println("READY_COMMANDS"); System.out.flush();
            try (Scanner scanner = new Scanner(System.in)) {
                for (int count = 0; count < 12 && scanner.hasNextLine(); count++) {
                    String command = scanner.nextLine();
                    if (command.equals("stop")) return;
                    check(command); System.out.flush();
                }
            }
        } finally {
            for (ObjectReference object : retained) object.enableCollection();
            vm.dispose();
        }
    }
}
