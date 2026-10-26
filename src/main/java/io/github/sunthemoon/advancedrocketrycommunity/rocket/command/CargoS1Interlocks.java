package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.command.CargoS1Fixture.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.command.ResourceRestartReleaseFixture.require;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;

/** Cut-G2 in a packaged dedicated process, including a real player-list logout/login. No transactions are admitted. */
final class CargoS1Interlocks {
    static void check(ServerLevel level, boolean items, RocketEntity source) {
        var server = level.getServer(); var at = new BlockPos(88, 100, 76);
        var players = new ResourceRestartReleaseFixture();
        RocketEntity rocket = null;
        var vehicle = new ArmorStand(level, 90, 100, 72);
        require(level.addFreshEntity(vehicle), "G2 vehicle admission");
        try {
            for (var role : RocketCargoRole.values()) {
                var owner = players.login(server, level, OWNER);
                owner.teleportTo(level, 88.5, 100, 75.5, 0, 0);
                rocket = ModEntities.ROCKET.get().create(level);
                require(rocket != null, "G2 entity type");
                rocket.initialize(source.snapshot().orElseThrow().relocated(UUID.randomUUID(), level.dimension().location(),
                        new RocketPosition(88, 100, 72), level.getGameTime()), UUID.randomUUID(), OWNER);
                require(level.addFreshEntity(rocket), "G2 rocket admission");
                var expectedRoot = root(items, role, 100 + role.ordinal(), binding(rocket), "valid");
                var device = place(level, at, items, role, expectedRoot);
                var flight = rocket.flightData().orElseThrow(); var snapshot = rocket.snapshot().orElseThrow();
                require(status(device) == RocketCargoStatus.ADMISSION_DISABLED, "G2 neutral control");
                rocket.updateFlightData(flight.withPassengers(flight.passengers().assign(OWNER).orElseThrow()));
                require(status(device) == RocketCargoStatus.ROCKET_BUSY, "G2 assigned passenger");
                require(owner.startRiding(rocket, true) && status(device) == RocketCargoStatus.ROCKET_BUSY, "G2 mount");
                owner.stopRiding(); server.getPlayerList().remove(owner);
                owner = players.login(server, level, OWNER);
                require(rocket.flightData().orElseThrow().passengers().equals(flight.passengers().assign(OWNER).orElseThrow())
                        && status(device) == RocketCargoStatus.ROCKET_BUSY, "G2 reconnect occupancy");
                owner.stopRiding(); rocket.updateFlightData(flight);
                require(rocket.startRiding(vehicle, true) && status(device) == RocketCargoStatus.ROCKET_BUSY, "G2 riding rocket");
                rocket.stopRiding();
                var home = rocket.position(); var commands = server.getCommands(); var commandSource = server.createCommandSourceStack().withSuppressedOutput();
                commands.performPrefixedCommand(commandSource, "ride " + rocket.getUUID() + " mount " + vehicle.getUUID());
                require(rocket.isPassenger() && status(device) == RocketCargoStatus.ROCKET_BUSY, "G2 neutral ride command");
                rocket.stopRiding();
                var saved = rocket.saveWithoutId(new CompoundTag());
                saved.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(LEDGER, 1,
                        List.of(new EscrowPrefix.Marker(OPERATION, 1, role == RocketCargoRole.LOAD ? "DESTINATION" : "SOURCE", 1))).encode());
                rocket.discard(); rocket = ModEntities.ROCKET.get().create(level); rocket.load(saved);
                require(level.addFreshEntity(rocket) && status(device) == RocketCargoStatus.ESCROW_PENDING, "G2 held native load");
                require(!owner.startRiding(rocket, true), "G2 held player mount");
                commands.performPrefixedCommand(commandSource, "ride " + rocket.getUUID() + " mount " + vehicle.getUUID());
                commands.performPrefixedCommand(commandSource, "tp " + rocket.getUUID() + " " + (home.x + 2) + " " + home.y + " " + home.z);
                var travel = new EntityTravelToDimensionEvent(rocket, Level.NETHER); MinecraftForge.EVENT_BUS.post(travel);
                require(!rocket.isPassenger() && rocket.position().equals(home) && travel.isCanceled(), "G2 held movement");
                rocket.setPos(home.add(0.125, 0, 0));
                require(status(device) == RocketCargoStatus.STALE_BINDING, "G2 direct-mod movement not stale");
                require(expectedRoot.equals(device.saveWithFullMetadata().get(key(items)))
                        && io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec.encode(snapshot)
                                .equals(io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec.encode(rocket.snapshot().orElseThrow()))
                        && flight.fuel().equals(rocket.flightData().orElseThrow().fuel()), "G2 changed cargo or fuel");
                rocket.discard(); rocket = null;
                level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
                server.getPlayerList().remove(owner); players.pump();
            }
        } finally {
            if (rocket != null) { rocket.discard(); }
            var online = server.getPlayerList().getPlayer(OWNER);
            if (online != null) { online.stopRiding(); server.getPlayerList().remove(online); }
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState()); vehicle.discard(); players.close();
        }
    }

    private CargoS1Interlocks() { }
}
