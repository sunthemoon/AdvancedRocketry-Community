package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Kind-specific satellite state, including the kind parameters snapshotted at launch (ADR-049 §7). */
public sealed interface SatelliteKindState {
    SatelliteKind kind();

    /** {@code data}, {@code asteroid_miner} and {@code gas_harvester} carry no extra state. */
    record Plain(SatelliteKind kind) implements SatelliteKindState {
        public Plain {
            Objects.requireNonNull(kind, "kind");
            if (kind == SatelliteKind.SURVEY || kind == SatelliteKind.SOLAR) {
                throw new IllegalArgumentException("Kind " + kind.id() + " needs its own state");
            }
        }
    }

    /** Lazy battery charge plus the scan parameters fixed at launch. */
    record Survey(long charge, long chargeTime, int scanEnergy, int scanRadius, int scanCell)
            implements SatelliteKindState {
        public Survey {
            if (charge < 0L || charge > SatelliteLimits.MAX_BATTERY || chargeTime < 0L) {
                throw new IllegalArgumentException("Survey charge is invalid");
            }
            if (scanEnergy < 1 || scanEnergy > SatelliteLimits.MAX_SCAN_ENERGY) {
                throw new IllegalArgumentException("Survey scan energy is outside its bound");
            }
            if (scanRadius < SatelliteLimits.MIN_SCAN_RADIUS || scanRadius > SatelliteLimits.MAX_SCAN_RADIUS
                    || (scanCell != 4 && scanCell != 8 && scanCell != 16) || scanRadius % scanCell != 0) {
                throw new IllegalArgumentException("Survey scan geometry is invalid");
            }
        }

        @Override
        public SatelliteKind kind() {
            return SatelliteKind.SURVEY;
        }

        /**
         * ADR-049 section 8 lazy battery: {@code min(battery, charge + power × Δ)} for Δ logical ticks since
         * {@link #chargeTime()}, in saturating 64-bit arithmetic.
         */
        public long chargeAt(long logicalTime, int power, int battery) {
            if (power < 0 || battery < 0) {
                throw new IllegalArgumentException("Survey stats must not be negative");
            }
            long elapsed = Math.max(0L, logicalTime - chargeTime);
            long gained = elapsed == 0L || power == 0 ? 0L
                    : elapsed > Long.MAX_VALUE / power ? Long.MAX_VALUE : elapsed * power;
            long total = gained > Long.MAX_VALUE - charge ? Long.MAX_VALUE : charge + gained;
            return Math.min(battery, total);
        }

        /** ADR-049 section 8: the state after paying one scan at {@code logicalTime}, or empty when uncovered. */
        public Optional<Survey> pay(long logicalTime, int power, int battery) {
            long available = chargeAt(logicalTime, power, battery);
            return available < scanEnergy ? Optional.empty()
                    : Optional.of(new Survey(available - scanEnergy, logicalTime, scanEnergy, scanRadius, scanCell));
        }
    }

    /** Output multiplier fixed at launch and the receiver currently holding the link. */
    record Solar(int outputMultiplierPercent, Optional<UUID> receiver) implements SatelliteKindState {
        public Solar {
            Objects.requireNonNull(receiver, "receiver");
            if (outputMultiplierPercent < 1 || outputMultiplierPercent > SatelliteLimits.MAX_SOLAR_MULTIPLIER_PERCENT) {
                throw new IllegalArgumentException("Solar output multiplier is outside its bound");
            }
        }

        @Override
        public SatelliteKind kind() {
            return SatelliteKind.SOLAR;
        }
    }
}
