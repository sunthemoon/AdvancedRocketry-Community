package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Startup-only connection exception for an explicitly marked disposable bay V2 campaign world. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class BayV2Campaign {
    public static final String PROPERTY = "advancedrocketrycommunity.bayV2CampaignPlayers";
    public static final String MARKER = ".arce-bay-v2-campaign";
    public static final String MARKER_TEXT = "ARCE_BAY_V2_DISPOSABLE_WORLD_V1\n";
    private static final Logger LOGGER = LogUtils.getLogger();
    // Explicit server lifecycle: created before startup, removed on stop, never contains players or Levels.
    private static final Map<MinecraftServer, Selection> SELECTIONS = java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private record Selection(Set<UUID> clients, String names, Path world, boolean marked) { }
    private BayV2Campaign() { }

    @SubscribeEvent public static void starting(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        if (server instanceof GameTestServer || !server.isDedicatedServer()) { return; }
        Set<UUID> clients = parse(System.getProperty(PROPERTY));
        if (clients.isEmpty()) { return; }
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String names = clients.stream().map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining(","));
        var selection = new Selection(clients, names, world, marked(world));
        SELECTIONS.put(server, selection);
        LOGGER.info("ARCE_BAY_V2_CAMPAIGN startup property={} clients={} world={} marker={}",
                PROPERTY, names, world, selection.marked());
    }

    @SubscribeEvent public static void connected(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) { return; }
        Selection selection = SELECTIONS.get(player.getServer());
        if (selection != null && selection.clients().contains(player.getUUID())) {
            LOGGER.info("ARCE_BAY_V2_CAMPAIGN connect property={} clients={} world={} marker={} player={}",
                    PROPERTY, selection.names(), selection.world(), selection.marked(), player.getUUID());
        }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SELECTIONS.remove(event.getServer()); }

    /** Called only after the gate has checked owner thread, hooks, dedicated-server type and RCON. */
    static Set<UUID> allowedClients(MinecraftServer server) {
        Selection selection = SELECTIONS.get(server);
        return selection != null && selection.marked() ? selection.clients() : Set.of();
    }

    static Set<UUID> parse(String value) {
        if (value == null) { return Set.of(); }
        String uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
        if (value.length() != 73 || !value.matches(uuid + "," + uuid)) { throw malformed(); }
        UUID first = UUID.fromString(value.substring(0, 36)), second = UUID.fromString(value.substring(37));
        if (first.equals(second)) { throw malformed(); }
        return Set.of(first, second);
    }

    private static IllegalArgumentException malformed() {
        return new IllegalArgumentException(PROPERTY + " requires exactly two distinct canonical UUIDs separated by one comma, without whitespace");
    }

    private static boolean marked(Path world) {
        Path marker = world.resolve(MARKER);
        byte[] expected = MARKER_TEXT.getBytes(StandardCharsets.US_ASCII);
        try {
            if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) != expected.length) { return false; }
            // Fixed read budget even if another process replaces or grows the marker during this check.
            try (var input = Files.newInputStream(marker, LinkOption.NOFOLLOW_LINKS)) {
                return Arrays.equals(expected, input.readNBytes(expected.length + 1));
            }
        } catch (IOException | SecurityException unavailable) { return false; }
    }
}
