package io.github.sunthemoon.advancedrocketrycommunity.endgame.intent;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStations;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ADR-054 section 4 on a live server: builds the {@link EndgameIntentRules} snapshot for a menu button and applies
 * the per-player rate last. Every read of the device position is guarded by {@code getChunkNow}, so no chunk is
 * loaded. A refusal sends one translated status line with its stable code.
 */
public final class EndgameIntentGuard {
    public static final String STATUS_LINE_KEY = "advancedrocketrycommunity.endgame.status_line";

    private EndgameIntentGuard() {
    }

    /** The device a menu was opened for. */
    public record Target(ResourceKey<Level> level, BlockPos position, UUID deviceId) {
        public Target {
            Objects.requireNonNull(level, "level");
            position = position.immutable();
            Objects.requireNonNull(deviceId, "deviceId");
        }
    }

    /** @param stationManaged ADR-058: CONFIGURE and OPERATE inside stations need MANAGE_STATION */
    public record Intent(EndgameAction action, IntentKind kind, boolean startsOperation, boolean stationManaged) {
        public Intent {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** The checked device when every rule and the rate allow the intent. */
    public record Result(EndgameCode code, Optional<EndgameDeviceBlockEntity> device) {
        public boolean allowed() {
            return code == EndgameCode.OK;
        }
    }

    public static Result check(Player player, Target target, Intent intent) {
        boolean realPlayer = player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)
                && serverPlayer.connection != null;
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        boolean sameLevel = player.level().dimension().equals(target.level());
        ServerLevel level = sameLevel && player.level() instanceof ServerLevel server ? server : null;
        boolean chunkLoaded = level != null && level.getChunkSource()
                .getChunkNow(target.position().getX() >> 4, target.position().getZ() >> 4) != null;
        EndgameDeviceBlockEntity device = chunkLoaded
                && level.getBlockEntity(target.position()) instanceof EndgameDeviceBlockEntity found
                && !found.isRemoved() ? found : null;
        boolean sameDevice = device != null && (device.quarantined()
                || device.deviceId().filter(target.deviceId()::equals).isPresent());
        boolean withinReach = player.distanceToSqr(target.position().getCenter())
                <= EndgameLimits.INTENT_DISTANCE_BLOCKS * EndgameLimits.INTENT_DISTANCE_BLOCKS;
        EndgameAuthority.Decision authority = device == null ? new EndgameAuthority.Decision(false, false,
                EndgameCode.DEVICE_CHANGED)
                : EndgameAuthority.decide(new EndgameAuthority.Request(player.getUUID(), realPlayer
                && player.hasPermissions(2), device.ownerId(), EndgameStations.at(level, target.position()).context(),
                intent.action(), intent.stationManaged()));
        EndgameSettings settings = devices.map(EndgameDevices::settings).orElse(EndgameSettings.DEFAULTS);
        EndgameCode code = EndgameIntentRules.check(new EndgameIntentRules.Snapshot(realPlayer, devices.isPresent(),
                sameLevel, chunkLoaded, sameDevice, withinReach, device != null && device.quarantined(), authority,
                intent.startsOperation(), device != null && settings.enabled(device.system())));
        if (code == EndgameCode.OK) {
            int interval = intent.kind() == IntentKind.STATE ? settings.intentIntervalTicks()
                    : settings.selectionIntervalTicks();
            if (!devices.get().rates().allow(player.getUUID(), intent.kind(), level.getGameTime(), interval)) {
                code = EndgameCode.RATE_LIMITED;
            }
        }
        if (code != EndgameCode.OK) {
            statusLine(player, code);
            return new Result(code, Optional.empty());
        }
        return new Result(code, Optional.of(device));
    }

    /** One translated status line with the stable code, never colour alone (section 4). */
    public static void statusLine(Player player, EndgameCode code) {
        player.displayClientMessage(Component.translatable(STATUS_LINE_KEY, Component.translatable(code.translationKey()),
                code.name()), true);
    }
}
