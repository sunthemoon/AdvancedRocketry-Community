package io.github.sunthemoon.advancedrocketrycommunity.beacon;

import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlsNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import java.util.Objects;
import java.util.Optional;
import net.minecraftforge.common.MinecraftForge;

/** Narrow lifecycle bridge from the beacon block entities to the installed beacon services. */
public final class BeaconRuntime {
    private static volatile BeaconServices services;

    private BeaconRuntime() {
    }

    /** Called once from the mod constructor; the services clear their runtime state when each server stops. */
    public static void install(MultiblockPatternCatalogManager patterns, CelestialCatalogManager celestial) {
        BeaconServices installed = new BeaconServices(patterns, celestial, ClassicControlsNetwork::sendBeaconSnapshot);
        services = Objects.requireNonNull(installed, "installed");
        MinecraftForge.EVENT_BUS.addListener(installed::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(installed::onBlockBroken);
        MinecraftForge.EVENT_BUS.addListener(installed::onBlockPlaced);
        MinecraftForge.EVENT_BUS.addListener(installed::onNeighborNotify);
        MinecraftForge.EVENT_BUS.addListener(installed::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(installed::onPlayerChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(installed::onPlayerRespawn);
        MinecraftForge.EVENT_BUS.addListener(installed::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(installed::onServerStopped);
    }

    public static Optional<BeaconServices> services() {
        return Optional.ofNullable(services);
    }
}
