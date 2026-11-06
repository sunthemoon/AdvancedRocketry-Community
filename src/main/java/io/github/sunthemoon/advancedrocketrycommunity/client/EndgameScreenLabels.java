package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;

/** Historical refusals must not contradict a current successful server view. */
final class EndgameScreenLabels {
    static boolean showLastStop(EndgameCode current, EndgameCode last) {
        return current != EndgameCode.OK && last != EndgameCode.OK && last != current
                && last != EndgameCode.CONFIRM_REQUIRED;
    }

    private EndgameScreenLabels() { }
}
