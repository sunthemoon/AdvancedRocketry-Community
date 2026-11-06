package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Client-only refusal recognition and meaningful view changes; notice storage remains in ScreenActionMessages. */
final class EndgameScreenFeedback {
    private static final String VIEW = "advancedrocketrycommunity.endgame.view.";
    private static final Set<EndgameCode> NON_REFUSALS = Set.of(EndgameCode.OK, EndgameCode.STOPPED,
            EndgameCode.COMPLETE, EndgameCode.GENERATING, EndgameCode.CONFIRM_REQUIRED,
            EndgameCode.AWAITING_WORLD_SAVE, EndgameCode.RIDE_COUNTDOWN);
    private static final Pattern RAILGUN_QUOTE = Pattern.compile("(?:LOCAL|ORBITAL) ([0-9]{1,10}) FE [0-9]+ t");
    private static final Pattern COUNTS = Pattern.compile("([0-9]{1,10}) / ([0-9]{1,10})");

    private EndgameScreenFeedback() { }

    static Optional<EndgameCode> refusal(Component message) {
        if (message == null || !(message.getContents() instanceof TranslatableContents text)
                || !EndgameIntentGuard.STATUS_LINE_KEY.equals(text.getKey()) || text.getArgs().length != 2
                || !(text.getArgs()[1] instanceof String name)) {
            return Optional.empty();
        }
        return EndgameCode.byName(name).filter(code -> !NON_REFUSALS.contains(code));
    }

    static boolean inventoryRelevant(EndgameCode code) {
        return switch (code) {
            case NO_PAYLOAD, PAYLOAD_TOO_LARGE, NO_FUEL, NO_LENS, OUTPUT_FULL, TARGET_BUFFER_FULL -> true;
            default -> false;
        };
    }

    static boolean answers(EndgameCode refusal, EndgameDeviceView before, EndgameDeviceView after,
                           int oldEnergy, int energy) {
        if (before.containerId() != after.containerId() || before.system() != after.system()) { return false; }
        // Recording the refusal itself can change status/lastStop. It must not immediately dismiss its notice.
        if (after.status() == refusal) { return false; }
        if (before.status() == refusal && after.status() != refusal) { return true; }
        if (before.lastStop() == refusal && after.lastStop() == EndgameCode.OK) { return true; }
        if (before.detail() != after.detail()) { return true; }
        Set<String> fields = switch (after.system()) {
            case RAILGUN -> Set.of("structure", "destination", "destination_at", "route", "auto", "redstone", "min_stack");
            case SPACE_ELEVATOR -> Set.of("structure", "pair", "far_end", "far_end_at", "anchor", "auto", "redstone");
            case LASER_DRILL -> Set.of("structure", "running", "mode", "redstone", "lens", "body", "table",
                    "target", "target_at", "link", "warning", "footprint", "floor");
            case BLACK_HOLE_GENERATOR -> Set.of("structure", "body", "fuel_table");
            default -> Set.of();
        };
        for (String field : fields) {
            var changed = field(after, field);
            boolean recordsRefusal = changed.filter(line -> line.value().equals(refusal.translationKey())
                    || line.value().equals(refusal.name())).isPresent();
            if (!recordsRefusal && !field(before, field).equals(changed)) { return true; }
        }
        if (refusal == EndgameCode.OUTBOX_FULL
                && !before.line(VIEW + "outbox").equals(after.line(VIEW + "outbox"))) {
            var counts = after.line(VIEW + "outbox").filter(line -> !line.translated())
                    .map(line -> COUNTS.matcher(line.value())).filter(java.util.regex.Matcher::matches);
            if (counts.isPresent() && Long.parseLong(counts.get().group(1)) < Long.parseLong(counts.get().group(2))) {
                return true;
            }
        }
        // The existing railgun view carries the server's configured cost; partial charging does not answer it.
        // Other systems do not send their energy quote, so do not guess one from local configuration.
        if (refusal == EndgameCode.INSUFFICIENT_ENERGY && after.system() == EndgameSystem.RAILGUN) {
            var quote = after.line(VIEW + "route").filter(line -> !line.translated())
                    .map(line -> RAILGUN_QUOTE.matcher(line.value())).filter(java.util.regex.Matcher::matches);
            if (quote.isPresent()) {
                long cost = Long.parseLong(quote.get().group(1));
                return oldEnergy < cost && energy >= cost;
            }
        }
        return false;
    }

    private static Optional<EndgameDeviceView.Line> field(EndgameDeviceView view, String field) {
        return view.line(VIEW + field).map(line -> {
            // Physical-mode paid credit changes during work; only a different linked marker answers a refusal.
            int paid = line.value().indexOf(" paid=");
            return field.equals("link") && !line.translated() && paid >= 0
                    ? EndgameDeviceView.Line.text(line.label(), line.value().substring(0, paid)) : line;
        });
    }
}
