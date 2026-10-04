package io.github.sunthemoon.advancedrocketrycommunity.machine.menu;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Required, message-empty admission of the native menu layout. Holds no world state. */
public final class MachineMenuNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel channel;

    private MachineMenuNetwork() { }

    /** Called once on both physical sides during common setup, before network registry lock. */
    public static void register() {
        if (channel != null) {
            throw new IllegalStateException("Machine menu channel is already registered");
        }
        channel = NetworkRegistry.ChannelBuilder
                .named(ModIdentity.id("machine_menu"))
                .networkProtocolVersion(MachineMenuNetwork::protocolVersion)
                .clientAcceptedVersions(MachineMenuNetwork::acceptsProtocol)
                .serverAcceptedVersions(MachineMenuNetwork::acceptsProtocol)
                .simpleChannel();
    }

    public static String protocolVersion() {
        return PROTOCOL_VERSION;
    }

    public static boolean acceptsProtocol(String version) {
        return PROTOCOL_VERSION.equals(version);
    }
}
