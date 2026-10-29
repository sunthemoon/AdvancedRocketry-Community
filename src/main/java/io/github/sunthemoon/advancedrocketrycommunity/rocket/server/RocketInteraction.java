package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketFeedback;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue;
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
        boolean failure = code != RocketValidationCode.SUCCESS && code != RocketValidationCode.SCAN_IN_PROGRESS;
        assembler.setReport(new RocketAssemblerReport(code, stats,
                failure ? RocketFeedback.reason(code).getString() : detail, level.getGameTime()));
        if (failure) {
            StringBuilder diagnostic = new StringBuilder();
            appendDiagnostic(diagnostic, detail);
            assembler.setOperatorDetail(diagnostic.toString());
        }
    }

    static void notify(ServerPlayer player, RocketValidationCode code, String detail) {
        // The diagnostic is for logs/operators, never a literal suffix in a player's localized message.
        player.displayClientMessage(Component.translatable(code.translationKey()), true);
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
        // R153: schema-1 still stores a bounded string, now resolved in the server's language.
        return RocketFeedback.reason(issue.code()).getString();
    }

    /** Bounded, single-line diagnostics without first allocating an unbounded Map.toString(). */
    static String diagnosticDetail(RocketValidationIssue issue) {
        StringBuilder text = new StringBuilder();
        appendDiagnostic(text, issue.code().name());
        issue.position().ifPresent(value -> appendDiagnostic(text, " at " + value.x() + "," + value.y() + "," + value.z()));
        for (var entry : issue.parameters().entrySet()) {
            if (text.length() >= RocketAssemblerBlockEntity.MAX_OPERATOR_DETAIL_LENGTH) { break; }
            appendDiagnostic(text, " ");
            appendDiagnostic(text, entry.getKey());
            appendDiagnostic(text, "=");
            appendDiagnostic(text, entry.getValue());
        }
        return text.toString();
    }

    private static void appendDiagnostic(StringBuilder target, String value) {
        for (int index = 0; index < value.length()
                && target.length() < RocketAssemblerBlockEntity.MAX_OPERATOR_DETAIL_LENGTH; index++) {
            char character = value.charAt(index);
            target.append(Character.isISOControl(character) || character == '\u2028' || character == '\u2029'
                    ? ' ' : character);
        }
    }
}
