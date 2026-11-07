package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeKind;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Recognizes the existing structured notices, without parsing localized text or predicting server time. */
final class WarpScreenNotice {
    private WarpScreenNotice() { }

    static boolean replacesCountdown(Component current, Component incoming) {
        if (current == null || incoming == null
                || !(current.getContents() instanceof TranslatableContents countdown)
                || !(incoming.getContents() instanceof TranslatableContents outcome)
                || !countdown.getKey().equals(StationNoticeKind.WARP_COUNTDOWN.key())
                || countdown.getArgs().length != 3) { return false; }
        int target;
        if (outcome.getKey().equals(StationNoticeKind.WARP_ABORTED.key()) && outcome.getArgs().length == 3) {
            target = 2;
        } else if (outcome.getKey().equals(StationNoticeKind.WARP_COMMITTED.key()) && outcome.getArgs().length == 7) {
            target = 3;
        } else { return false; }
        return Objects.equals(countdown.getArgs()[0], outcome.getArgs()[0])
                && Objects.equals(countdown.getArgs()[1], outcome.getArgs()[target]);
    }

    static boolean isNotice(Component message) {
        if (!(message.getContents() instanceof TranslatableContents text)) { return false; }
        return text.getKey().equals(StationNoticeKind.WARP_COUNTDOWN.key())
                || text.getKey().equals(StationNoticeKind.WARP_ABORTED.key())
                || text.getKey().equals(StationNoticeKind.WARP_COMMITTED.key());
    }
}
