package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import com.mojang.datafixers.util.Either;
import java.lang.invoke.MethodType;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.eventbus.EventBus;

/**
 * D1 outcome 3: preserve native spawn fields around an admitted off-world bed operation, not around arbitrary
 * setters. No spawn event is cancelled and no R-036 persisted root is touched. See W37B for the accepted exceptional
 * integrated-pause, crash-cleanup and third-party direct-save windows.
 */
public final class BedRespawnPreserver {
    private static final StackWalker STACK = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    // Descriptors survive Forge's production method-name remapping. These signatures are unique in ServerPlayer.
    private static final MethodType SLEEP = MethodType.methodType(Either.class, BlockPos.class);
    private static final MethodType SET = MethodType.methodType(void.class, ResourceKey.class, BlockPos.class,
            float.class, boolean.class, boolean.class);
    private final Predicate<ResourceKey<Level>> offWorld;
    // Player identity, not UUID: a replacement must never inherit another object's operation.
    private final Map<ServerPlayer, TreeMap<Integer, Operation>> pending = new IdentityHashMap<>();

    public BedRespawnPreserver(Predicate<ResourceKey<Level>> offWorld) {
        this.offWorld = Objects.requireNonNull(offWorld);
    }

    /** Registered first by the sleep service. HIGHEST precedes lower priorities, not earlier HIGHEST registrations. */
    public void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, this::onTimeCheck);
        bus.addListener(EventPriority.HIGHEST, this::onSleepEntry);
        bus.addListener(EventPriority.HIGHEST, true, this::onSetterEntry);
        bus.addListener(EventPriority.LOWEST, true, this::onSetterExit);
        bus.addListener(EventPriority.LOWEST, this::onSleep);
        bus.addListener(EventPriority.HIGHEST, this::onTick);
        bus.addListener(EventPriority.HIGHEST, this::onLogout);
        bus.addListener(EventPriority.HIGHEST, this::onStopping);
        bus.addListener(EventPriority.HIGHEST, this::onCommand);
        bus.addListener(this::onStopped);
    }

    private void onSleepEntry(PlayerSleepInBedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.server.isSameThread() && pending.containsKey(player)) {
            Call call = call();
            if (call.admission) { retireReturned(player, call.depth); }
        }
    }

    private void retireReturned(ServerPlayer player, int depth) {
        TreeMap<Integer, Operation> operations = pending.get(player);
        if (operations == null) { return; }
        var returned = operations.tailMap(depth, true);
        returned.descendingMap().values().forEach(operation -> restore(player, operation));
        returned.clear();
        if (operations.isEmpty()) { pending.remove(player); }
    }

    private void onSleep(PlayerSleepInBedEvent event) {
        if (event.getResultStatus() != null || !(event.getEntity() instanceof ServerPlayer player)
                || !player.server.isSameThread() || !offWorld.test(player.level().dimension())
                || event.getOptionalPos().isEmpty()) {
            return;
        }
        Call call = call();
        if (!call.admission) { return; } // A synthetic event is not a native operation.
        // A returned early refusal at this depth cannot be reused; deeper returned calls are stale too.
        retireReturned(player, call.depth);
        TreeMap<Integer, Operation> operations = pending.computeIfAbsent(player, ignored -> new TreeMap<>());
        operations.put(call.depth, new Operation(Tuple.read(player), player.level().dimension(),
                event.getOptionalPos().orElseThrow().immutable()));
    }

    private void onSetterEntry(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.server.isSameThread()) { return; }
        Call call = call();
        if (!call.spawnSetter) { return; }
        Operation operation = setterOperation(event);
        if (operation != null) {
            // Vanilla evaluated getYRot immediately before its setter. The event exposes no angle argument.
            operation.written = new Tuple(operation.dimension, operation.bed,
                    Float.floatToRawIntBits(event.getEntity().getYRot()), false);
            operation.setterEvent = event;
        } else if (!call.bedSetter) {
            TreeMap<Integer, Operation> operations = pending.get(player);
            if (operations != null) {
                operations.forEach((depth, pendingOperation) -> {
                    if (call.depth == 0 || depth <= call.timeDepth) {
                        pendingOperation.foreignSetter(call.setterDepth, event);
                    }
                });
            }
        }
    }

    private void onSetterExit(PlayerSetSpawnEvent event) {
        Operation operation = setterOperation(event);
        if (operation != null) {
            // Keep legitimate nested/foreign changes made during admission or setter dispatch, too.
            operation.before = Tuple.read((ServerPlayer) event.getEntity());
        }
    }

    private Operation setterOperation(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.server.isSameThread()) { return null; }
        Call call = call();
        TreeMap<Integer, Operation> operations = pending.get(player);
        Operation operation = operations == null ? null : operations.get(call.depth);
        return call.bedSetter && operation != null && !event.isForced()
                && operation.dimension.equals(event.getSpawnLevel())
                && operation.dimension.equals(player.level().dimension())
                && operation.bed.equals(event.getNewSpawn()) ? operation : null;
    }

    private void onTimeCheck(SleepingTimeCheckEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.server.isSameThread()) { return; }
        TreeMap<Integer, Operation> operations = pending.get(player);
        if (operations == null) { return; }
        Call call = call();
        if (!call.timeCheck) { return; }
        int depth = call.depth;
        Operation operation = operations.remove(depth);
        if (operation != null) { restore(player, operation); }
        if (operations.isEmpty()) { pending.remove(player); }
    }

    private void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) { flush(event.getServer()); }
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.server.isSameThread()) { flush(player); }
    }

    private void onStopping(ServerStoppingEvent event) { flush(event.getServer()); }

    private void onCommand(CommandEvent event) { flush(event.getParseResults().getContext().getSource().getServer()); }

    private void onStopped(ServerStoppedEvent event) {
        pending.keySet().removeIf(player -> player.server == event.getServer());
    }

    private void flush(MinecraftServer server) {
        if (!server.isSameThread()) { return; }
        // Copy before removing; deepest operation first preserves its enclosing operation's current tuple.
        pending.keySet().stream().filter(player -> player.server == server).toList().forEach(this::flush);
    }

    private void flush(ServerPlayer player) {
        Call call = call();
        if (call.depth > 0) {
            // A reentrant command/logout during admission or setter dispatch is before the native write. Do not
            // consume that operation: its eventual primary callback still has to preserve the nested setter.
            // In a native time-check callback the write has finished; restore before a reentrant save, retaining
            // the entry until the primary callback unwinds it normally.
            if (call.timeDepth > 0) {
                TreeMap<Integer, Operation> active = pending.get(player);
                if (active != null) {
                    active.descendingMap().forEach((depth, operation) -> {
                        if (depth <= call.timeDepth) { restore(player, operation); }
                    });
                }
            }
            return;
        }
        TreeMap<Integer, Operation> operations = pending.remove(player);
        if (operations != null) { operations.descendingMap().values().forEach(operation -> restore(player, operation)); }
    }

    private static void restore(ServerPlayer player, Operation operation) {
        if (operation.written != null && operation.setterEvent != null && !operation.setterEvent.isCanceled()
                && !operation.superseded()
                && operation.written.equals(Tuple.read(player))) {
            operation.before.write(player);
        }
    }

    private static Call call() {
        return STACK.walk(stream -> {
            var frames = stream.toList();
            int depth = 0;
            boolean bedSetter = false;
            boolean admission = false;
            boolean timeCheck = false;
            boolean spawnSetter = false;
            int timeDepth = 0;
            int setterDepth = 0;
            for (int index = frames.size() - 1; index >= 0; index--) {
                StackWalker.StackFrame frame = frames.get(index);
                if (nativeSleep(frame)) { depth++; }
                if (frame.getDeclaringClass() == ServerPlayer.class && frame.getMethodType().equals(SET)) { setterDepth++; }
                if (frame.getDeclaringClass() == ForgeEventFactory.class && index + 1 < frames.size()
                        && nativeSleep(frames.get(index + 1)) && frame.getMethodName().equals("fireSleepingTimeCheck")) {
                    timeDepth = Math.max(timeDepth, depth);
                }
            }
            // Inspect the nearest dispatch, not any enclosing factory. A synthetic post inside a native listener
            // must not borrow that outer call's attribution. Forge/EventBus method names are not obfuscated.
            for (int index = 0; index < frames.size(); index++) {
                if (!postFrame(frames.get(index))) { continue; }
                while (index + 1 < frames.size() && postFrame(frames.get(index + 1))) { index++; }
                if (index + 2 < frames.size()) {
                    StackWalker.StackFrame factory = frames.get(index + 1);
                    StackWalker.StackFrame caller = frames.get(index + 2);
                    if (factory.getDeclaringClass() == ForgeEventFactory.class) {
                        admission = factory.getMethodName().equals("onPlayerSleepInBed") && nativeSleep(caller);
                        timeCheck = factory.getMethodName().equals("fireSleepingTimeCheck") && nativeSleep(caller);
                        spawnSetter = factory.getMethodName().equals("onPlayerSpawnSet")
                                && caller.getDeclaringClass() == ServerPlayer.class && caller.getMethodType().equals(SET);
                        bedSetter = spawnSetter && index + 3 < frames.size() && nativeSleep(frames.get(index + 3));
                    }
                }
                break;
            }
            return new Call(depth, bedSetter, admission, timeCheck, timeDepth, setterDepth, spawnSetter);
        });
    }

    private static boolean postFrame(StackWalker.StackFrame frame) {
        return frame.getDeclaringClass() == EventBus.class && frame.getMethodName().equals("post");
    }

    private static boolean nativeSleep(StackWalker.StackFrame frame) {
        return frame.getDeclaringClass() == ServerPlayer.class && frame.getMethodType().equals(SLEEP);
    }

    private record Call(int depth, boolean bedSetter, boolean admission, boolean timeCheck, int timeDepth,
                        int setterDepth, boolean spawnSetter) { }

    private static final class Operation {
        private Tuple before;
        private Tuple written;
        private PlayerSetSpawnEvent setterEvent;
        private final ResourceKey<Level> dimension;
        private final BlockPos bed;
        // At most one event per active native setter depth, not an unbounded history of intervening writes.
        private final TreeMap<Integer, PlayerSetSpawnEvent> foreign = new TreeMap<>();
        private boolean foreignCommitted;

        private Operation(Tuple before, ResourceKey<Level> dimension, BlockPos bed) {
            this.before = before;
            this.dimension = dimension;
            this.bed = bed;
        }

        private void foreignSetter(int depth, PlayerSetSpawnEvent event) {
            var completed = foreign.tailMap(depth, true);
            foreignCommitted |= completed.values().stream().anyMatch(previous -> !previous.isCanceled());
            completed.clear();
            foreign.put(depth, event);
        }

        private boolean superseded() {
            return foreignCommitted || foreign.values().stream().anyMatch(event -> !event.isCanceled());
        }
    }

    record Tuple(ResourceKey<Level> dimension, BlockPos position, int angleBits, boolean forced) {
        static Tuple read(ServerPlayer player) {
            return new Tuple(player.getRespawnDimension(), player.getRespawnPosition(),
                    Float.floatToRawIntBits(player.getRespawnAngle()), player.isRespawnForced());
        }

        void write(ServerPlayer player) {
            player.respawnDimension = dimension;
            player.respawnPosition = position;
            player.respawnAngle = Float.intBitsToFloat(angleBits);
            player.respawnForced = forced;
        }
    }
}
