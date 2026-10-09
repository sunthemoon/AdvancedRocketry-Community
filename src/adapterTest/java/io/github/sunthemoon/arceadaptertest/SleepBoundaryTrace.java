package io.github.sunthemoon.arceadaptertest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Bounded, invocation-owned evidence. Metadata is diagnostic, never a gameplay predicate. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SleepBoundaryTrace {
    static final int FRAME_CAP = 64, CASE_EVENT_CAP = 32, EVENT_CAP = 128;
    static final int CASE_BYTES = 128 * 1024, TOTAL_BYTES = 512 * 1024, ID_CAP = 512;
    private static final StackWalker WALKER = StackWalker.getInstance(Set.of(
            StackWalker.Option.RETAIN_CLASS_REFERENCE, StackWalker.Option.SHOW_HIDDEN_FRAMES,
            StackWalker.Option.SHOW_REFLECT_FRAMES));
    private final IdentityHashMap<Object, Integer> identities = new IdentityHashMap<>();
    private final List<Frame> retainedMetadata = new ArrayList<>();
    private final JsonObject root = object("format", "ARCE_SLEEP_BOUNDARY_OBSERVATION_1",
            "evidenceClass", "native-Java simulated transport", "traceStopped", false);
    private final JsonArray rows = new JsonArray();
    private final ServerPlayer[] actors = new ServerPlayer[2];
    private final Connection[] connections = new Connection[2];
    private ServerLevel level;
    private JsonObject current;
    private boolean enabled, stopped, failOnce;
    private int events, caseEvents;
    int depth;
    String context = "native_action";
    private String route = "none", control = "none";

    SleepBoundaryTrace(List<String> names) {
        root.add("cases", rows); root.add("lifecycle", new JsonArray());
        for (int i = 0; i < names.size(); i++) {
            JsonObject row = object("number", i + 1, "name", names.get(i), "status", "UNEXECUTED");
            for (String key : List.of("preparation", "actions", "events", "outputs", "cleanup")) {
                row.add(key, new JsonArray());
            }
            rows.add(row);
        }
    }

    void bind(ServerLevel ownedLevel, int index, ServerPlayer actor, Connection connection) {
        level = ownedLevel; actors[index] = actor; connections[index] = connection;
        lifecycle(object("stage", "native_loaded_spawn", "actor", token(actor), "uuid", actor.getUUID().toString(),
                "spawnGetterProjection", spawn(actor)));
    }

    int token(Object value) {
        if (value == null) { return 0; }
        Integer old = identities.get(value);
        if (old != null) { return old; }
        if (identities.size() >= ID_CAP) { throw new IllegalStateException("identity token cap"); }
        int next = identities.size() + 1; identities.put(value, next); return next;
    }

    void begin(int index, boolean observe) {
        current = rows.get(index).getAsJsonObject(); enabled = observe; caseEvents = 0;
        current.addProperty("observerEnabled", observe);
        current.addProperty("observerReceiver", token(this));
    }

    void route(String declaredRoute, String declaredControl) {
        route = declaredRoute; control = declaredControl;
        current.addProperty("declaredRoute", route); current.addProperty("declaredControl", control);
    }
    void end() { enabled = false; failOnce = false; depth = 0; context = "native_action"; route = "none"; control = "none"; current = null; }
    void failureIntervention() { failOnce = true; }
    void status(String status) { current.addProperty("status", status); }
    void note(String key, JsonElement value) { append(current, "preparation", object("kind", key, "value", value)); }
    void output(JsonObject value) { append(current, "outputs", value); }
    void cleanup(JsonObject value) { append(current, "cleanup", value); }
    void lifecycle(JsonObject value) { append(root, "lifecycle", value); }
    void declared(JsonObject value) { append(current, "preparation", value); }

    boolean append(JsonObject target, String collection, JsonObject value) {
        JsonArray array = target.getAsJsonArray(collection); array.add(value);
        if (bytes(root) > TOTAL_BYTES - 4096 || (current != null && bytes(current) > CASE_BYTES - 512)) {
            array.remove(array.size() - 1); incomplete("structured byte cap"); return false;
        }
        return true;
    }

    private static int bytes(JsonElement value) { return value.toString().getBytes(StandardCharsets.UTF_8).length; }
    void incomplete(String reason) {
        stopped = true; root.addProperty("traceStopped", true);
        if (!root.has("incomplete")) { root.addProperty("incomplete", brief(reason)); }
        if (current != null) { current.addProperty("incomplete", true); }
    }

    private boolean reserveEvent() {
        if (stopped) { return false; }
        if (caseEvents >= CASE_EVENT_CAP || events >= EVENT_CAP) { incomplete("event cap"); return false; }
        caseEvents++; events++; return true;
    }

    /** Passive LOWEST is not a final writer. The explicit failure intervention is separate. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void observe(PlayerSetSpawnEvent event) {
        if (!enabled || current == null) { return; }
        if (reserveEvent()) {
            long start = System.nanoTime(), allocation = allocated();
            try {
                verify();
                if (event.getEntity() != actors[0] && event.getEntity() != actors[1]) {
                    throw new IllegalStateException("unowned event actor");
                }
                List<Frame> frames = frames();
                JsonArray stack = new JsonArray();
                for (Frame frame : frames) {
                    if (frame.overflow()) { stack.add(object("overflow", true)); }
                    else { stack.add(object("class", frame.owner().getName(), "classToken", token(frame.owner()),
                            "descriptor", frame.type().descriptorString(), "member", frame.member())); }
                }
                long afterAllocation = allocated();
                JsonObject record = object("event", token(event), "eventClass", event.getClass().getName(),
                        "actor", token(event.getEntity()), "level", token(event.getEntity().level()),
                        "spawnLevel", event.getSpawnLevel().location().toString(), "newSpawn", position(event.getNewSpawn()),
                        "forced", event.isForced(), "canceledAtObserver", event.isCanceled(),
                        "declaredRoute", route, "declaredControl", control,
                        "declaredContext", context, "nestedDepth", depth, "receiver", token(this),
                        "nativeAtCallback", snapshot(false), "frames", stack, "durationNanos", System.nanoTime() - start,
                        "allocatedBytes", allocation < 0 || afterAllocation < 0 ? null : afterAllocation - allocation);
                if (append(current, "events", record)) { retainedMetadata.addAll(frames); }
                if (frames.get(frames.size() - 1).overflow()) { incomplete("frame cap overflow sentinel"); }
            } catch (RuntimeException | LinkageError failure) {
                incomplete("observer capture: " + failure.getClass().getName() + ": " + failure.getMessage());
            }
        }
        if (failOnce) { failOnce = false; throw new ObserverInterventionFailure(); }
    }

    static final class ObserverInterventionFailure extends RuntimeException {
        ObserverInterventionFailure() { super("declared one-shot observer failure intervention"); }
    }

    private record Frame(Class<?> owner, MethodType type, String member, boolean overflow) { }
    private static List<Frame> frames() {
        List<Frame> result = new ArrayList<>(WALKER.walk(stream -> stream.limit(FRAME_CAP + 1L).map(frame ->
                new Frame(frame.getDeclaringClass(), frame.getMethodType(), frame.getMethodName(), false)).toList()));
        if (result.size() > FRAME_CAP) { result.set(FRAME_CAP, new Frame(null, null, null, true)); }
        return result;
    }

    private static long allocated() {
        try {
            var bean = ManagementFactory.getThreadMXBean();
            if (bean instanceof com.sun.management.ThreadMXBean allocation
                    && allocation.isThreadAllocatedMemorySupported() && allocation.isThreadAllocatedMemoryEnabled()) {
                return allocation.getThreadAllocatedBytes(Thread.currentThread().getId());
            }
        } catch (RuntimeException | LinkageError unavailable) { /* Measurement remains unmeasured. */ }
        return -1;
    }

    void action(String label, boolean preparation, Runnable action) {
        JsonObject record = object("label", label, "declaredContext", context, "nestedDepth", depth,
                "before", safeSnapshot(preparation));
        try { action.run(); record.addProperty("outcome", "RETURNED"); }
        catch (RuntimeException | Error failure) {
            record.add("failure", error(failure)); record.addProperty("outcome", "THREW"); throw failure;
        } finally {
            record.add("after", safeSnapshot(preparation));
            if (current == null) { lifecycle(record); }
            else { append(current, preparation ? "preparation" : "actions", record); }
        }
    }

    private JsonElement safeSnapshot(boolean small) {
        try { return snapshot(small); }
        catch (RuntimeException | LinkageError failure) { incomplete("native snapshot capture: " + failure); return error(failure); }
    }

    void verify() {
        if (level == null || !level.getServer().isSameThread()) { throw new IllegalStateException("not owning server thread"); }
        for (int i = 0; i < actors.length; i++) {
            ServerPlayer actor = actors[i]; Connection connection = connections[i];
            if (actor == null || actor.level() != level || actor.serverLevel() != level || actor.connection == null
                    || actor.connection.player != actor || actor.connection.connection != connection
                    || connection.getPacketListener() != actor.connection || !connection.isConnected()
                    || level.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor
                    || level.getServer().getPlayerList().getPlayers().stream().noneMatch(player -> player == actor)
                    || level.players().stream().noneMatch(player -> player == actor)) {
                throw new IllegalStateException("owned actor/Level/connection/list identity differs");
            }
        }
        if (level.getServer().getPlayerList().getPlayers().size() != 2) { throw new IllegalStateException("ordinary player present"); }
    }

    JsonObject snapshot(boolean small) {
        JsonArray players = new JsonArray();
        for (int i = 0; i < actors.length; i++) {
            ServerPlayer actor = actors[i]; if (actor == null) { continue; }
            JsonObject player = object("actor", token(actor), "spawnGetterProjection", spawn(actor));
            if (!small) {
                player.add("native", object("uuid", actor.getUUID().toString(), "level", token(actor.level()),
                        "levelId", actor.level().dimension().location().toString(), "connection", token(connections[i]),
                        "listener", token(actor.connection), "connected", connections[i].isConnected(),
                        "playerListIdentity", level.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor,
                        "levelListIdentity", level.players().stream().anyMatch(value -> value == actor),
                        "listenerActorIdentity", actor.connection != null && actor.connection.player == actor,
                        "pose", actor.getPose().name(), "sleeping", actor.isSleeping(),
                        "sleepingPos", position(actor.getSleepingPos().orElse(null)), "position", vector(actor.position()),
                        "eye", vector(actor.getEyePosition()), "yawBits", Float.floatToRawIntBits(actor.getYRot()),
                        "mainEmpty", actor.getMainHandItem().isEmpty(), "offEmpty", actor.getOffhandItem().isEmpty(),
                        "gameMode", actor.gameMode.getGameModeForPlayer().getName(),
                        "mayInteractHead", level.mayInteract(actor, SleepBoundaryObservationFixture.HEAD),
                        "reachHead", actor.canReach(SleepBoundaryObservationFixture.HEAD, 1.5D)));
            }
            players.add(player);
        }
        JsonObject result = object("actors", players);
        if (!small && level != null) {
            result.add("world", object("gameTime", level.getGameTime(), "dayTime", level.getDayTime(), "isDay", level.isDay(),
                    "head", bed(SleepBoundaryObservationFixture.HEAD), "foot", bed(SleepBoundaryObservationFixture.FOOT)));
        }
        return result;
    }

    private JsonObject bed(BlockPos position) {
        if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) {
            throw new IllegalStateException("snapshot bed chunk unavailable");
        }
        var state = level.getBlockState(position);
        return object("pos", position(position), "state", state.toString(), "loadedBlockToken", token(state.getBlock()),
                "ordinaryWhiteBedIdentity", state.getBlock() == Blocks.WHITE_BED,
                "receiverQualification", "loaded block identity is not a callback receiver proof");
    }

    static JsonObject spawn(ServerPlayer actor) {
        return object("dimension", actor.getRespawnDimension().location().toString(), "position", position(actor.getRespawnPosition()),
                "angleBits", Float.floatToRawIntBits(actor.getRespawnAngle()), "forced", actor.isRespawnForced(),
                "source", "native getters, not serialized player NBT");
    }

    boolean isIncomplete() { return stopped; }
    void finish(String outcome) {
        root.addProperty("outcome", outcome); root.addProperty("eventCaptureAttempts", events);
        int persisted = 0;
        for (JsonElement row : rows) { persisted += row.getAsJsonObject().getAsJsonArray("events").size(); }
        root.addProperty("persistedEventRecords", persisted);
    }
    String json() { return root.toString(); }
    void clear() { end(); identities.clear(); retainedMetadata.clear(); root.entrySet().clear(); level = null;
        java.util.Arrays.fill(actors, null); java.util.Arrays.fill(connections, null); }

    static JsonObject error(Throwable failure) {
        JsonArray suppressed = new JsonArray();
        for (int i = 0; i < Math.min(failure.getSuppressed().length, 8); i++) {
            suppressed.add(brief(failure.getSuppressed()[i].getClass().getName() + ": " + failure.getSuppressed()[i].getMessage()));
        }
        JsonArray causes = new JsonArray(); Throwable cause = failure.getCause();
        for (int i = 0; cause != null && cause != failure && i < 8; i++, cause = cause.getCause()) {
            causes.add(object("type", cause.getClass().getName(), "message", brief(cause.getMessage())));
        }
        return object("type", failure.getClass().getName(), "message", brief(failure.getMessage()), "causes", causes, "suppressed", suppressed);
    }
    static boolean observerIntervention(Throwable failure) {
        for (int i = 0; failure != null && i < 8; i++, failure = failure.getCause()) {
            if (failure instanceof ObserverInterventionFailure) { return true; }
        }
        return false;
    }
    private static String brief(String value) { return value == null ? "" : value.substring(0, Math.min(value.length(), 256)); }
    static JsonElement position(BlockPos value) {
        if (value == null) { return JsonNull.INSTANCE; }
        JsonArray result = new JsonArray(); result.add(value.getX()); result.add(value.getY()); result.add(value.getZ()); return result;
    }
    static JsonArray vector(Vec3 value) {
        JsonArray result = new JsonArray(); result.add(value.x); result.add(value.y); result.add(value.z); return result;
    }
    static JsonObject object(Object... fields) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) {
            String key = (String) fields[i]; Object value = fields[i + 1];
            if (value == null) { result.add(key, JsonNull.INSTANCE); }
            else if (value instanceof JsonElement element) { result.add(key, element); }
            else if (value instanceof String text) { result.addProperty(key, text); }
            else if (value instanceof Number number) { result.addProperty(key, number); }
            else if (value instanceof Boolean flag) { result.addProperty(key, flag); }
            else { throw new IllegalArgumentException("unsupported evidence value"); }
        }
        return result;
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "sleep_trace", timeoutTicks = 20)
    public static void tokensUseIdentityAndHaveAHardCap(GameTestHelper helper) {
        var trace = new SleepBoundaryTrace(List.of("identity-unit"));
        try {
            Object a = new String("equal"), b = new String("equal");
            helper.assertTrue(trace.token(a) == trace.token(a) && trace.token(a) != trace.token(b), "Identity tokens differ");
            while (trace.identities.size() < ID_CAP) { trace.token(new Object()); }
            boolean refused = false; try { trace.token(new Object()); } catch (IllegalStateException capped) { refused = true; }
            helper.assertTrue(refused && trace.identities.size() == ID_CAP, "Token cap was exceeded");
        } finally { trace.clear(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "sleep_trace", timeoutTicks = 20)
    public static void structuredAndEventCapsStopFurtherCapture(GameTestHelper helper) {
        var trace = new SleepBoundaryTrace(SleepBoundaryObservationFixture.NAMES);
        try {
            trace.begin(0, true);
            for (int i = 0; i < CASE_EVENT_CAP; i++) { helper.assertTrue(trace.reserveEvent(), "Premature case cap"); }
            helper.assertTrue(!trace.reserveEvent() && trace.stopped && trace.events == CASE_EVENT_CAP, "Case event cap differs");
            trace.end();
        } finally { trace.clear(); }
        trace = new SleepBoundaryTrace(SleepBoundaryObservationFixture.NAMES);
        try {
            for (int row = 0; row < 4; row++) { trace.begin(row, true); for (int i = 0; i < CASE_EVENT_CAP; i++) { trace.reserveEvent(); } trace.end(); }
            trace.begin(4, true);
            helper.assertTrue(!trace.reserveEvent() && trace.events == EVENT_CAP, "Invocation event cap differs");
        } finally { trace.clear(); }
        trace = new SleepBoundaryTrace(List.of("byte-unit"));
        try {
            trace.begin(0, false);
            helper.assertTrue(!trace.append(trace.current, "preparation", object("unit", "x".repeat(CASE_BYTES)))
                    && bytes(trace.root) <= TOTAL_BYTES && bytes(trace.current) <= CASE_BYTES && trace.stopped, "Byte cap differs");
        } finally { trace.clear(); }
        trace = new SleepBoundaryTrace(List.of("total-byte-unit"));
        try {
            helper.assertTrue(!trace.append(trace.root, "lifecycle", object("unit", "x".repeat(TOTAL_BYTES)))
                    && bytes(trace.root) <= TOTAL_BYTES && trace.stopped, "Invocation byte cap differs");
        } finally { trace.clear(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "sleep_trace", timeoutTicks = 20)
    public static void deepPublicWalkerRetainsOnlyBoundedFramesAndSentinel(GameTestHelper helper) {
        List<Frame> frames = deepFrames(70);
        helper.assertTrue(frames.size() == FRAME_CAP + 1 && frames.get(FRAME_CAP).overflow()
                && frames.subList(0, FRAME_CAP).stream().allMatch(frame -> frame.owner() != null && frame.type() != null
                && frame.type().descriptorString() != null && frame.member() != null), "Frame metadata/cap differs");
        helper.succeed();
    }
    private static List<Frame> deepFrames(int remaining) { return remaining == 0 ? frames() : deepFrames(remaining - 1); }
}
