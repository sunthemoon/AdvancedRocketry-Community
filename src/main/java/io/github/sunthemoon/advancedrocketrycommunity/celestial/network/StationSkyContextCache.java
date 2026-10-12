package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls.Axis;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;

/**
 * Client display cache for ADR-047 and ADR-065 section 5.2.1: the last station sky context received and the
 * local tick it arrived at. Cleared on NONE, logout and a Level change; the server re-sends the current
 * context within its next pass. Interpolation never writes server phase or elapsed time.
 */
public final class StationSkyContextCache {
    /** The client extrapolates at most this many local ticks past a context, then holds the last phase. */
    public static final int MAX_EXTRAPOLATION_TICKS = 40;
    private static final AtomicReference<Received> LAST = new AtomicReference<>();
    private static final AtomicLong LOCAL_TICKS = new AtomicLong();

    private StationSkyContextCache() {
    }

    public static void accept(StationSkyContextPacket packet) {
        LAST.set(packet.context().map(context -> new Received(context, LOCAL_TICKS.get())).orElse(null));
    }

    /** One client tick; the only local time the extrapolation uses. */
    public static void clientTick() {
        LOCAL_TICKS.incrementAndGet();
    }

    public static Optional<StationSkyContextPacket.Context> context() {
        return Optional.ofNullable(LAST.get()).map(Received::context);
    }

    public static Optional<ResourceLocation> orbitBody() {
        return context().flatMap(StationSkyContextPacket.Context::body);
    }

    /** The current presentation phases in degrees (EAST, UP, NORTH), if a context is cached. */
    public static Optional<double[]> phaseDegrees(float partialTick) {
        Received received = LAST.get();
        return received == null ? Optional.empty()
                : Optional.of(phaseDegrees(received.context(), LOCAL_TICKS.get() - received.localTick(), partialTick));
    }

    /**
     * The phases at the packet sample advanced by at most {@value #MAX_EXTRAPOLATION_TICKS} local ticks (the
     * sample plus the local ticks saturates at {@link Long#MAX_VALUE}); past that horizon the last bounded
     * phase is held until a fresh heartbeat. Only the integer part uses the server arithmetic; the partial
     * tick only smooths within the horizon.
     */
    public static double[] phaseDegrees(StationSkyContextPacket.Context context, long localTicks, float partialTick) {
        long elapsed = Math.max(0L, Math.min(localTicks, MAX_EXTRAPOLATION_TICKS));
        double fraction = elapsed < MAX_EXTRAPOLATION_TICKS ? Math.max(0.0F, Math.min(1.0F, partialTick)) : 0.0D;
        long tick = context.sample() > Long.MAX_VALUE - elapsed ? Long.MAX_VALUE : context.sample() + elapsed;
        if (tick == Long.MAX_VALUE) {
            fraction = 0.0D;
        }
        StationOrbitalControls controls = context.controls();
        double[] degrees = new double[Axis.values().length];
        for (Axis axis : Axis.values()) {
            double milli = controls.phaseAt(axis, tick)
                    + fraction * controls.rate(axis) * StationOrbitalControls.PHASE_STEP_PER_RATE;
            degrees[axis.ordinal()] = Math.floorMod((long) Math.floor(milli * 1_000.0D),
                    (long) StationOrbitalControls.PHASES_PER_TURN * 1_000L) / 1_000_000.0D;
        }
        return degrees;
    }

    public static void clear() {
        LAST.set(null);
    }

    private record Received(StationSkyContextPacket.Context context, long localTick) {
    }
}
