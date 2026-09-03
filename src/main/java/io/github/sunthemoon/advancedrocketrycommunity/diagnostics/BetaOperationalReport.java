package io.github.sunthemoon.advancedrocketrycommunity.diagnostics;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.network.LifeSupportNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightIntentPacket;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

/** Fixed-field, path-free operator summary suitable for logs and bug reports. */
public record BetaOperationalReport(
        RuntimeIdentity runtime,
        RootSummary roots,
        ConfigSummary config,
        ProtocolSummary protocols,
        int players,
        int maximumPlayers
) {
    private static final int MAX_FORMATTED_CHARS = 1_024;

    public BetaOperationalReport {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(protocols, "protocols");
        requireCount("players", players);
        requireCount("maximumPlayers", maximumPlayers);
        if (players > maximumPlayers) {
            throw new IllegalArgumentException("Online players cannot exceed the server maximum");
        }
    }

    public static BetaOperationalReport collect(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        CelestialSavedData celestial = CelestialSavedData.get(server);
        RocketTransactionSavedData transactions = RocketTransactionSavedData.get(server);
        RocketTransferSavedData transfers = RocketTransferSavedData.get(server);
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        SatelliteMissionSavedData missions = SatelliteMissionSavedData.get(server);
        return new BetaOperationalReport(
                new RuntimeIdentity(
                        modVersion(AdvancedRocketryCommunity.MOD_ID),
                        modVersion("forge"),
                        ModList.get().isLoaded("jei") ? modVersion("jei") : "absent"
                ),
                new RootSummary(
                        celestial.isWritableSchema(),
                        transactions.operational(),
                        transfers.operational(),
                        stations.operational(),
                        missions.operational(),
                        celestial.entries().size(),
                        transactions.entries().size(),
                        transfers.entries().size(),
                        stations.stations().size(),
                        missions.missions().size()
                ),
                new ConfigSummary(
                        CommonConfig.MAX_ATMOSPHERE_VOLUME.get(),
                        CommonConfig.MAX_ATMOSPHERE_INSPECTIONS_PER_TICK.get()
                ),
                ProtocolSummary.current(),
                server.getPlayerCount(),
                server.getMaxPlayers()
        );
    }

    public String format() {
        String report = BetaDiagnosticId.OPERATOR_REPORT.code()
                + " build=" + runtime.build()
                + " forge=" + runtime.forge()
                + " jei=" + runtime.jei()
                + " root_schema=" + SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION
                + " operational=" + roots.operational()
                + " roots=" + roots.flags()
                + " bodies=" + roots.bodies()
                + " transactions=" + roots.transactions()
                + " transfers=" + roots.transfers()
                + " stations=" + roots.stations()
                + " missions=" + roots.missions()
                + " players=" + players + "/" + maximumPlayers
                + " atmosphere_volume=" + config.maximumAtmosphereVolume()
                + " atmosphere_tick=" + config.maximumAtmosphereInspectionsPerTick()
                + " protocols=" + protocols.format()
                + " flight_frame_max=" + RocketFlightIntentPacket.maximumEncodedBytes()
                + " ticket_policy=transient_transfer_only";
        if (report.length() > MAX_FORMATTED_CHARS) {
            throw new IllegalStateException("Beta operator report exceeded its fixed output bound");
        }
        return report;
    }

    private static String modVersion(String modId) {
        String raw = ModList.get()
                .getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        return RuntimeIdentity.safeVersion(raw);
    }

    private static void requireCount(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    public record RuntimeIdentity(String build, String forge, String jei) {
        private static final Pattern SAFE_VERSION = Pattern.compile("[A-Za-z0-9._+\\-]{1,64}");

        public RuntimeIdentity {
            build = safeVersion(build);
            forge = safeVersion(forge);
            jei = safeVersion(jei);
        }

        private static String safeVersion(String value) {
            return value != null && SAFE_VERSION.matcher(value).matches() ? value : "unknown";
        }
    }

    public record RootSummary(
            boolean celestialOperational,
            boolean transactionsOperational,
            boolean transfersOperational,
            boolean stationsOperational,
            boolean missionsOperational,
            int bodies,
            int transactions,
            int transfers,
            int stations,
            int missions
    ) {
        public RootSummary {
            requireCount("bodies", bodies);
            requireCount("transactions", transactions);
            requireCount("transfers", transfers);
            requireCount("stations", stations);
            requireCount("missions", missions);
        }

        public boolean operational() {
            return celestialOperational
                    && transactionsOperational
                    && transfersOperational
                    && stationsOperational
                    && missionsOperational;
        }

        public String flags() {
            return bit(celestialOperational)
                    + bit(transactionsOperational)
                    + bit(transfersOperational)
                    + bit(stationsOperational)
                    + bit(missionsOperational);
        }

        private static String bit(boolean value) {
            return value ? "1" : "0";
        }
    }

    public record ConfigSummary(
            int maximumAtmosphereVolume,
            int maximumAtmosphereInspectionsPerTick
    ) {
        public ConfigSummary {
            if (maximumAtmosphereVolume < 1
                    || maximumAtmosphereVolume > io.github.sunthemoon.advancedrocketrycommunity
                            .atmosphere.AtmosphereLimits.MAX_VOLUME_CELLS) {
                throw new IllegalArgumentException("Atmosphere volume configuration is outside its hard bound");
            }
            if (maximumAtmosphereInspectionsPerTick < 1
                    || maximumAtmosphereInspectionsPerTick > io.github.sunthemoon.advancedrocketrycommunity
                            .atmosphere.AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK) {
                throw new IllegalArgumentException("Atmosphere tick configuration is outside its hard bound");
            }
        }
    }

    public record ProtocolSummary(
            String lifeSupport,
            String celestial,
            String rocketFlight,
            String rocketVisual
    ) {
        public ProtocolSummary {
            lifeSupport = RuntimeIdentity.safeVersion(lifeSupport);
            celestial = RuntimeIdentity.safeVersion(celestial);
            rocketFlight = RuntimeIdentity.safeVersion(rocketFlight);
            rocketVisual = RuntimeIdentity.safeVersion(rocketVisual);
        }

        public static ProtocolSummary current() {
            return new ProtocolSummary(
                    LifeSupportNetwork.protocolVersion(),
                    CelestialNetwork.protocolVersion(),
                    RocketFlightNetwork.protocolVersion(),
                    RocketVisualNetwork.protocolVersion()
            );
        }

        public String format() {
            return "life:" + lifeSupport
                    + ",celestial:" + celestial
                    + ",flight:" + rocketFlight
                    + ",visual:" + rocketVisual;
        }
    }
}
