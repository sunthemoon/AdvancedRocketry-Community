package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One station notice of ADR-065 section 5.3: its kind and its arguments as canonical strings, each valid for the kind's
 * role. No argument is a player name, a UUID or a coordinate. A stored outcome is the localized argument envelope of an
 * offline summary; {@link StationNoticeCodec#envelopeBytes} is its exact NBT payload, admitted only up to
 * {@link StationNoticeLimits#MAX_ENVELOPE_BYTES}.
 */
public record StationNotice(StationNoticeKind kind, List<String> arguments) {
    public StationNotice {
        Objects.requireNonNull(kind, "kind");
        arguments = List.copyOf(arguments);
        if (arguments.size() != kind.roles().size()) {
            throw new IllegalArgumentException("Station notice " + kind + " takes " + kind.roles().size() + " arguments");
        }
        for (int index = 0; index < arguments.size(); index++) {
            if (!kind.roles().get(index).accepts(kind, arguments.get(index))) {
                throw new IllegalArgumentException("Station notice " + kind + " argument " + index + " is invalid");
            }
        }
    }

    /** The countdown start, sent live only. */
    public static StationNotice started(String station, ResourceLocation from, ResourceLocation to, int seconds,
                                        String costClass, int cost) {
        return new StationNotice(StationNoticeKind.WARP_STARTED, List.of(station,
                StationManagementCode.WARP_STARTED.name(), from.toString(), to.toString(), Integer.toString(seconds),
                costClass, Integer.toString(cost)));
    }

    /** One countdown second, sent live on the action bar only. */
    public static StationNotice countdown(String station, ResourceLocation to, int seconds) {
        return new StationNotice(StationNoticeKind.WARP_COUNTDOWN, List.of(station, to.toString(),
                Integer.toString(seconds)));
    }

    public static StationNotice committed(String station, ResourceLocation from, ResourceLocation to, String costClass,
                                          int cost, int balance) {
        return new StationNotice(StationNoticeKind.WARP_COMMITTED, List.of(station,
                StationManagementCode.WARP_COMMITTED.name(), from.toString(), to.toString(), costClass,
                Integer.toString(cost), Integer.toString(balance)));
    }

    /** A countdown that ended without a warp: cancelled ({@code WARP_CANCELLED}) or refused at its commit. */
    public static StationNotice aborted(String station, StationManagementCode reason, ResourceLocation to) {
        return new StationNotice(StationNoticeKind.WARP_ABORTED, List.of(station, reason.name(), to.toString()));
    }

    public String station() {
        return arguments.get(0);
    }

    /**
     * The notice an offline summary keeps (byte-budget admission): this outcome when its envelope fits
     * {@link StationNoticeLimits#MAX_ENVELOPE_BYTES}, otherwise its brief outcome, which always fits.
     */
    public StationNotice summary() {
        if (!kind.stored()) {
            throw new IllegalStateException("Station notice " + kind + " is not an outcome");
        }
        return StationNoticeCodec.envelopeBytes(this) <= StationNoticeLimits.MAX_ENVELOPE_BYTES ? this : brief();
    }

    /** The station name and the outcome code alone; at most 250 bytes, whatever the bodies' length. */
    public StationNotice brief() {
        return new StationNotice(StationNoticeKind.WARP_OUTCOME, List.of(arguments.get(0), arguments.get(1)));
    }

    /**
     * What a visitor without VISIT access is sent (W22 review F2): a start or commit as the station, the code and the
     * target, without the warp cost or the energy balance. A countdown or an abort carries neither and is unchanged.
     */
    public StationNotice forVisitors() {
        return switch (kind) {
            case WARP_STARTED, WARP_COMMITTED -> new StationNotice(StationNoticeKind.WARP_VISITOR,
                    List.of(arguments.get(0), arguments.get(1), arguments.get(3)));
            case WARP_COUNTDOWN, WARP_ABORTED, WARP_OUTCOME, WARP_VISITOR -> this;
        };
    }
}
