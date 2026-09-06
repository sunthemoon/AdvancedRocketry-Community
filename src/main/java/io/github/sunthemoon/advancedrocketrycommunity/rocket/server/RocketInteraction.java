package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Shared bounded interaction and feedback rules for rocket server services. */
final class RocketInteraction {
    static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0D;

    private RocketInteraction() {
    }

    static RocketAssemblerBlockEntity assembler(ServerLevel level, BlockPos position) {
        return level.getBlockEntity(position) instanceof RocketAssemblerBlockEntity assembler
                ? assembler
                : null;
    }

    static boolean withinRange(ServerPlayer player, BlockPos position) {
        return withinRange(
                player,
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D
        );
    }

    static boolean withinRange(ServerPlayer player, double x, double y, double z) {
        return player.distanceToSqr(x, y, z) <= MAX_INTERACTION_DISTANCE_SQUARED;
    }

    static void update(
            RocketAssemblerBlockEntity assembler,
            RocketValidationCode code,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats stats,
            String detail,
            ServerLevel level
    ) {
        assembler.setReport(new RocketAssemblerReport(code, stats, detail, level.getGameTime()));
    }

    static void notify(ServerPlayer player, RocketValidationCode code, String detail) {
        player.displayClientMessage(
                Component.translatable(code.translationKey())
                        .append(Component.literal(": " + detail)),
                true
        );
    }

    static void notifyStats(ServerPlayer player, RocketStructureSnapshot snapshot, String action) {
        var stats = snapshot.stats();
        player.displayClientMessage(
                Component.literal(
                        action + ": blocks=" + stats.blockCount()
                                + ", mass=" + stats.mass()
                                + ", thrust=" + stats.thrust()
                                + ", fuel=" + stats.fuelCapacity()
                                + ", seats=" + stats.seatCount()
                ),
                true
        );
    }

    static String issueDetail(RocketValidationIssue issue) {
        String position = issue.position()
                .map(value -> " at " + value.x() + "," + value.y() + "," + value.z())
                .orElse("");
        String parameters = issue.parameters().isEmpty() ? "" : " " + issue.parameters();
        return issue.code().name().toLowerCase(Locale.ROOT) + position + parameters;
    }
}
