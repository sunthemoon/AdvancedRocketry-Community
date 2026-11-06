package io.github.sunthemoon.advancedrocketrycommunity.rocket.entity;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

/**
 * W311: vanilla's entity search visits only the entity sections from 4 blocks below the searched box upward
 * (EntitySectionStorage), and a rocket's section is that of its base. A rider seated high above the base can therefore
 * miss its rocket even with its eyes inside the rocket's box. Then the use key targets the rider's own rocket, exactly as
 * vanilla does when its search finds it. The server still checks reach and every console rule.
 */
public final class RocketSeatedPick {
    private RocketSeatedPick() { }

    /** The rider's own rocket when vanilla picked no entity and the rider's eyes are inside the rocket's box. */
    public static Optional<RocketEntity> target(Entity rider, HitResult picked) {
        if (rider == null || !(rider.getVehicle() instanceof RocketEntity rocket) || rocket.isRemoved()
                || picked != null && picked.getType() == HitResult.Type.ENTITY) {
            return Optional.empty();
        }
        return rocket.getBoundingBox().contains(rider.getEyePosition()) ? Optional.of(rocket) : Optional.empty();
    }
}
