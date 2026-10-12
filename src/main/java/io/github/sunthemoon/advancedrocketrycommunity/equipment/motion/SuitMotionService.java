package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EquipmentNbt;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.EquipmentSummary;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ADR-066 section 5.2 server authority for the jetpack. Sessions and rate windows are transient and bounded by the
 * players online; nothing here is saved. Each correction is checked, planned, paid with one hydrogen unit and only
 * then applied.
 */
public final class SuitMotionService {
    /** The outcome of one correction attempt, for the server tick and GameTests. */
    public enum Result {
        PAID, NO_SESSION, NO_INTENT, ALREADY_CORRECTED, NOT_LIVE, EQUIPMENT_DISABLED, NO_JETPACK, NO_HOVER_UPGRADE,
        ELYTRA, VEHICLE, ELEVATOR, NO_HYDROGEN, NOT_NEEDED, COMMIT_REFUSED
    }

    /** The answer to one intent; the status ones are also shown on the player's action bar. */
    public enum Reply {
        RATE_LIMITED, IGNORED, THRUST, ENABLED_NORMAL, ENABLED_HOVER, DISABLED, MODE_NORMAL, MODE_HOVER, NO_JETPACK,
        NO_HOVER_UPGRADE, EQUIPMENT_DISABLED;

        public String translationKey() {
            return "message." + ModIdentity.MOD_ID + ".jetpack." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final Map<UUID, JetpackSession> sessions = new HashMap<>();
    private final MotionRateLimiter limiter = new MotionRateLimiter();
    private final BiConsumer<ServerPlayer, JetpackPhysics.Impulse> sender;

    public SuitMotionService(BiConsumer<ServerPlayer, JetpackPhysics.Impulse> sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public Reply receive(ServerPlayer player, JetpackIntentPacket intent, long now) {
        Objects.requireNonNull(intent, "intent");
        if (!limiter.allow(player.getUUID(), now)) { return Reply.RATE_LIMITED; }
        if (!live(player)) { return Reply.IGNORED; }
        JetpackSession session = sessions.get(player.getUUID());
        switch (intent.action()) {
            case THRUST -> {
                if (session == null || !session.enabled()) { return Reply.IGNORED; }
                session.thrust(intent.held(), now);
                return Reply.THRUST;
            }
            case TOGGLE -> {
                if (session != null && session.enabled()) {
                    session.disable();
                    return status(player, Reply.DISABLED);
                }
                if (!CommonConfig.classicEquipmentEnabled()) { return status(player, Reply.EQUIPMENT_DISABLED); }
                EquipmentSummary worn = ArmorEquipment.summarize(player);
                if (!worn.installed().contains(ModuleKind.JETPACK)) { return status(player, Reply.NO_JETPACK); }
                JetpackSession enabled = sessions.computeIfAbsent(player.getUUID(), ignored -> new JetpackSession());
                if (enabled.mode() == JetpackMode.HOVER && !worn.installed().contains(ModuleKind.HOVER)) { enabled.cycle(false); }
                enabled.enable(worn, levelId(player));
                return status(player, enabled.mode() == JetpackMode.HOVER ? Reply.ENABLED_HOVER : Reply.ENABLED_NORMAL);
            }
            case CYCLE_MODE -> {
                JetpackSession cycled = sessions.computeIfAbsent(player.getUUID(), ignored -> new JetpackSession());
                if (!cycled.cycle(ArmorEquipment.summarize(player).installed().contains(ModuleKind.HOVER))) {
                    return status(player, Reply.NO_HOVER_UPGRADE);
                }
                return status(player, cycled.mode() == JetpackMode.HOVER ? Reply.MODE_HOVER : Reply.MODE_NORMAL);
            }
            default -> throw new IllegalStateException("Unknown jetpack intent " + intent.action());
        }
    }

    /** Server tick: one bounded pass over online sessions; offline players' sessions are dropped. */
    public void tick(MinecraftServer server, long now) {
        for (UUID id : List.copyOf(sessions.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) { forget(id); } else { step(player, now); }
        }
    }

    public Result step(ServerPlayer player, long now) {
        JetpackSession session = sessions.get(player.getUUID());
        if (session == null || !session.enabled()) { return Result.NO_SESSION; }
        EquipmentSummary worn = ArmorEquipment.summarize(player);
        if (session.observe(worn, levelId(player))) {
            if (!worn.installed().contains(ModuleKind.JETPACK)) {
                session.disable();
                status(player, Reply.NO_JETPACK);
                return Result.NO_JETPACK;
            }
            if (session.mode() == JetpackMode.HOVER && !worn.installed().contains(ModuleKind.HOVER)) {
                session.disable();
                status(player, Reply.NO_HOVER_UPGRADE);
                return Result.NO_HOVER_UPGRADE;
            }
        }
        session.expire(now);
        if (!session.active(now)) { return Result.NO_INTENT; }
        if (!session.claim(now)) { return Result.ALREADY_CORRECTED; }
        if (!live(player)) { return Result.NOT_LIVE; }
        if (!CommonConfig.classicEquipmentEnabled()) { return Result.EQUIPMENT_DISABLED; }
        if (player.isFallFlying()) { return Result.ELYTRA; }
        // Any vehicle, which includes every rocket seat.
        if (player.isPassenger()) { return Result.VEHICLE; }
        if (elevatorRide(player)) { return Result.ELEVATOR; }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ArmorEquipment.Reserve hydrogen = ArmorEquipment.hydrogen(chest);
        if (hydrogen.stored() < 1) { return Result.NO_HYDROGEN; }
        // The estimate is the last accepted displacement; no client packet supplies a velocity.
        var estimate = new JetpackPhysics.Motion(player.getX() - player.xo, player.getY() - player.yo,
                player.getZ() - player.zo);
        double below = session.mode() == JetpackMode.HOVER ? session.reference(player.getY()) - player.getY() : 0;
        JetpackPhysics.Impulse impulse = JetpackPhysics.plan(session.mode(), estimate, CommonConfig.jetPackForce(),
                below, worn.installed().contains(ModuleKind.FLIGHT_SPEED));
        if (impulse.isZero()) { return Result.NOT_NEEDED; }
        // Pay first: one unit from a detached chest, published only onto the same worn stack.
        ItemStack detached = chest.copy();
        if (!ArmorEquipment.setHydrogen(detached, hydrogen.stored() - 1) || !EquipmentNbt.safe(detached)
                || player.getItemBySlot(EquipmentSlot.CHEST) != chest) {
            return Result.COMMIT_REFUSED;
        }
        chest.setTag(detached.getTag());
        Vec3 motion = player.getDeltaMovement();
        var next = JetpackPhysics.apply(new JetpackPhysics.Motion(motion.x, motion.y, motion.z), impulse);
        player.setDeltaMovement(next.x(), next.y(), next.z());
        if (impulse.y() > 0) { player.resetFallDistance(); }
        sender.accept(player, impulse);
        return Result.PAID;
    }

    public Optional<JetpackSession> session(UUID player) { return Optional.ofNullable(sessions.get(player)); }

    /** Level change or respawn: the intent and HOVER reference end; a respawned player starts switched off. */
    public void changedLevel(ServerPlayer player) {
        JetpackSession session = sessions.get(player.getUUID());
        if (session != null) { session.observe(session.summary(), levelId(player)); }
    }

    public void respawned(UUID player) { sessions.remove(player); }

    public void forget(UUID player) {
        sessions.remove(player);
        limiter.forget(player);
    }

    public void clear() {
        sessions.clear();
        limiter.clear();
    }

    public int tracked() { return Math.max(sessions.size(), limiter.tracked()); }

    /** A connected, alive, non-spectator real player that is the player list's current object in its own Level. */
    static boolean live(ServerPlayer player) {
        return !(player instanceof FakePlayer) && player.isAlive() && !player.isSpectator()
                && player.connection != null && player.connection.connection.isConnected()
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.server.getLevel(player.level().dimension()) == player.level();
    }

    private static boolean elevatorRide(ServerPlayer player) {
        return EndgameRuntime.devices().map(devices -> devices.elevatorRides().pending(player.getUUID()).isPresent())
                .orElse(false);
    }

    private static String levelId(ServerPlayer player) { return player.level().dimension().location().toString(); }

    private static Reply status(ServerPlayer player, Reply reply) {
        player.displayClientMessage(Component.translatable(reply.translationKey()), true);
        return reply;
    }
}
