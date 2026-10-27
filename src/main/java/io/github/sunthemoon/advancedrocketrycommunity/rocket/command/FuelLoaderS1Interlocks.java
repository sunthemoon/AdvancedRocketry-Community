package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.command.ResourceRestartReleaseFixture.require;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.command.FuelLoaderS1Fixture.*;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
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

/** cut-G2 in a packaged process; server-side player-list login is not a V2 real-client claim. */
final class FuelLoaderS1Interlocks {
    static void check(ServerLevel level, RocketEntity source) {
        var server = level.getServer(); var at = new BlockPos(88, 100, 76);
        var players = new ResourceRestartReleaseFixture(); RocketEntity rocket = null;
        var vehicle = new ArmorStand(level, 90, 100, 72);
        require(level.addFreshEntity(vehicle), "G2 vehicle admission");
        try {
            var owner = players.login(server, level, OWNER); owner.teleportTo(level, 88.5, 100, 75.5, 0, 0);
            rocket = ModEntities.ROCKET.get().create(level);
            require(rocket != null, "G2 entity type");
            rocket.initialize(source.snapshot().orElseThrow().relocated(UUID.randomUUID(), level.dimension().location(),
                    new RocketPosition(88, 100, 72), level.getGameTime()), UUID.randomUUID(), OWNER);
            require(level.addFreshEntity(rocket), "G2 rocket admission");
            var expected = root(100, binding(rocket), "valid"); var device = place(level, at, expected);
            var flight = rocket.flightData().orElseThrow(); var snapshot = rocket.snapshot().orElseThrow();
            require(device.attempt() == FluidFuelLoaderStatus.ADMISSION_DISABLED, "G2 neutral control");
            rocket.updateFlightData(flight.withPassengers(flight.passengers().assign(OWNER).orElseThrow()));
            require(device.attempt() == FluidFuelLoaderStatus.ROCKET_BUSY, "G2 assigned passenger");
            require(owner.startRiding(rocket, true) && device.attempt() == FluidFuelLoaderStatus.ROCKET_BUSY, "G2 mount");
            owner.stopRiding(); server.getPlayerList().remove(owner); owner = players.login(server, level, OWNER);
            require(rocket.flightData().orElseThrow().passengers().equals(flight.passengers().assign(OWNER).orElseThrow())
                    && device.attempt() == FluidFuelLoaderStatus.ROCKET_BUSY, "G2 reconnect occupancy");
            owner.stopRiding(); rocket.updateFlightData(flight);
            require(rocket.startRiding(vehicle, true) && device.attempt() == FluidFuelLoaderStatus.ROCKET_BUSY, "G2 riding rocket");
            rocket.stopRiding();
            var home = rocket.position(); var commands = server.getCommands(); var commandSource = server.createCommandSourceStack().withSuppressedOutput();
            commands.performPrefixedCommand(commandSource, "ride " + rocket.getUUID() + " mount " + vehicle.getUUID());
            require(rocket.isPassenger() && device.attempt() == FluidFuelLoaderStatus.ROCKET_BUSY, "G2 neutral ride command");
            rocket.stopRiding();
            var saved = rocket.saveWithoutId(new CompoundTag());
            saved.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(new UUID(176, 2), 1,
                    List.of(new EscrowPrefix.Marker(new UUID(176, 3), 1, "DESTINATION", 1))).encode());
            rocket.discard(); rocket = ModEntities.ROCKET.get().create(level); rocket.load(saved);
            require(level.addFreshEntity(rocket) && device.attempt() == FluidFuelLoaderStatus.ESCROW_PENDING, "G2 held native load");
            require(!owner.startRiding(rocket, true), "G2 held player mount");
            commands.performPrefixedCommand(commandSource, "ride " + rocket.getUUID() + " mount " + vehicle.getUUID());
            commands.performPrefixedCommand(commandSource, "tp " + rocket.getUUID() + " " + (home.x + 2) + " " + home.y + " " + home.z);
            var travel = new EntityTravelToDimensionEvent(rocket, Level.NETHER); MinecraftForge.EVENT_BUS.post(travel);
            require(!rocket.isPassenger() && rocket.position().equals(home) && travel.isCanceled(), "G2 held movement");
            rocket.setPos(home.add(20, 0, 0));
            require(device.attempt() == FluidFuelLoaderStatus.OUT_OF_RANGE, "G2 bypass movement admitted a remote fill");
            require(expected.equals(device.saveWithoutMetadata().get(LoaderRoot.KEY))
                    && snapshot.equals(rocket.snapshot().orElseThrow()) && flight.fuel().equals(rocket.flightData().orElseThrow().fuel()),
                    "G2 changed resources or snapshot");
        } finally {
            if (rocket != null) { rocket.discard(); }
            var owner = server.getPlayerList().getPlayer(OWNER);
            if (owner != null) { owner.stopRiding(); server.getPlayerList().remove(owner); }
            level.removeBlockEntity(at); level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            vehicle.discard(); players.close();
        }
    }
    private FuelLoaderS1Interlocks() { }
}
