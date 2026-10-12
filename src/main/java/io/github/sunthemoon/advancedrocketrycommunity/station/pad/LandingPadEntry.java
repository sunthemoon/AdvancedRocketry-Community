package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Immutable index identity; x/z are relative to the station's invariant cell landing position. */
public record LandingPadEntry(UUID stationId, UUID padId, UUID physicalId, UUID ownerId,
                              String name, StationPosition localPosition, long epoch, UUID claim) {
    public LandingPadEntry {
        Objects.requireNonNull(stationId); Objects.requireNonNull(padId);
        Objects.requireNonNull(physicalId); Objects.requireNonNull(ownerId); Objects.requireNonNull(localPosition);
        name = checkedName(name);
        if (epoch < 0 || Math.abs((long) localPosition.x()) > 768 || Math.abs((long) localPosition.z()) > 768
                || localPosition.y() < -64 || localPosition.y() > 319) {
            throw new IllegalArgumentException("Landing-pad position or epoch is outside its bound");
        }
    }

    public static String checkedName(String name) {
        Objects.requireNonNull(name);
        if (name.isBlank() || name.length() > 32 || !name.equals(name.strip())
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Landing-pad name must contain 1..32 printable characters");
        }
        return name;
    }

    public LandingPadReference reference() { return new LandingPadReference(padId, epoch); }
    public LandingPadEntry withClaim(UUID transfer) {
        return new LandingPadEntry(stationId, padId, physicalId, ownerId, name, localPosition, epoch, transfer);
    }
    public BlockPos position(StationState station) {
        if (!station.stationId().equals(stationId)) { throw new IllegalArgumentException("Wrong pad station"); }
        return new BlockPos(Math.addExact(station.landingPad().x(), localPosition.x()), localPosition.y(),
                Math.addExact(station.landingPad().z(), localPosition.z()));
    }
    public boolean matches(LandingPadReference reference, UUID physical) {
        return reference != null && reference().equals(reference) && physicalId.equals(physical);
    }
}
