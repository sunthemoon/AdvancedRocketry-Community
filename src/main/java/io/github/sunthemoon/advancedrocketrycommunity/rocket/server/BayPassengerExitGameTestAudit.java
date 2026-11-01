package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Bounded fixture diagnostics; no production hooks or observer changes. */
final class BayPassengerExitGameTestAudit {
    private BayPassengerExitGameTestAudit() { }
    static String difference(Object before, Object after) {
        var left = (Map<?, ?>) before; var right = (Map<?, ?>) after;
        var changed = new TreeMap<String, String>();
        left.forEach((key, value) -> { if (!value.equals(right.get(key))) { changed.put(key.toString(), value + " -> " + right.get(key)); } });
        right.forEach((key, value) -> { if (!left.containsKey(key)) { changed.put(key.toString(), "absent -> " + value); } });
        return changed.toString();
    }
    static final class Player extends ServerPlayer {
        boolean tracing;
        Player(MinecraftServer server, ServerLevel level, GameProfile profile) { super(server, level, profile); }
        @Override public void absMoveTo(double x, double y, double z, float yaw, float pitch) {
            if (tracing) {
                io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_BAY_EXIT_REPOSITION tick={} target={},{},{} frames={}", getServer().getTickCount(), x, y, z,
                        StackWalker.getInstance().walk(stream -> stream.limit(20).map(frame -> frame.getClassName() + "." + frame.getMethodName()).toList()));
            }
            super.absMoveTo(x, y, z, yaw, pitch);
        }
    }
}
