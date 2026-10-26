package io.github.sunthemoon.advancedrocketrycommunity.satellite.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryWriterCuts;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * W129bc native cuts (design section 6.4): {@code arce satellite release-test registry-writer ...}. Absent unless the
 * release-test JVM property is set; every action also requires an empty dedicated server
 * ({@link SatelliteRegistryWriterCuts#enabled}). The mutations are a data satellite on Earth, a terminal launch of an
 * idle survey satellite at a fixed spawn-chunk terminal, and a data satellite whose Mars research pays a discovery.
 */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class SatelliteRegistryWriterCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000129b01");
    private static final UUID SURVEY = UUID.nameUUIDFromBytes("w129bc-terminal-survey".getBytes(StandardCharsets.UTF_8));
    private static final UUID RESEARCH = UUID.nameUUIDFromBytes("w129bc-mars-research".getBytes(StandardCharsets.UTF_8));
    private static final String PROPERTY = "advancedrocketrycommunity.releaseTestHooks";

    private SatelliteRegistryWriterCommands() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) { return; }
        var writer = Commands.literal("registry-writer").requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> run(context, SatelliteRegistryWriterCommands::status)))
                .then(Commands.literal("add").executes(context -> run(context, SatelliteRegistryWriterCommands::add)))
                .then(Commands.literal("request").executes(context -> run(context, (server, data) ->
                        LOGGER.info("ARCE_RELEASE_SRW_REQUESTED result={} sequence={}", data.request(server),
                                data.mutationSequence()))))
                .then(Commands.literal("release").executes(context -> run(context, (server, data) -> data.cuts().release(server))))
                .then(Commands.literal("at-stop").executes(context -> run(context, (server, data) ->
                        data.cuts().atStopping(server, () -> add(server, data)))))
                .then(Commands.literal("crash").then(Commands.argument("release", BoolArgumentType.bool())
                        .executes(context -> run(context, (server, data) -> data.cuts().crashAtNextTick(server,
                                () -> add(server, data), BoolArgumentType.getBool(context, "release"))))))
                .then(Commands.literal("arm").then(Commands.argument("point", StringArgumentType.word())
                        .then(Commands.argument("mode", StringArgumentType.word()).executes(context -> run(context,
                                (server, data) -> data.cuts().arm(server,
                                        SatelliteRegistryWriterCuts.Point.valueOf(upper(context, "point")),
                                        SatelliteRegistryWriterCuts.Mode.valueOf(upper(context, "mode"))))))))
                .then(Commands.literal("hold-effects").then(Commands.argument("hold", BoolArgumentType.bool())
                        .executes(context -> run(context, (server, data) ->
                                data.cuts().holdEffects(server, BoolArgumentType.getBool(context, "hold"))))))
                .then(Commands.literal("hold-discovery").then(Commands.argument("hold", BoolArgumentType.bool())
                        .executes(context -> run(context, (server, data) ->
                                data.cuts().holdDiscovery(server, BoolArgumentType.getBool(context, "hold"))))))
                .then(Commands.literal("terminal-place").executes(context -> run(context, (server, data) -> place(server))))
                .then(Commands.literal("terminal-launch").executes(context -> run(context, (server, data) -> {
                    var terminal = terminal(server);
                    boolean handled = terminal.handleButton(actor(server), SatelliteTerminalMenu.BUTTON_LAUNCH);
                    // The launch's change is at this sequence; only a published commit covering it proves the registration.
                    LOGGER.info("ARCE_RELEASE_SRW_TERMINAL handled={} sequence={} {}", handled, data.mutationSequence(),
                            describe(server));
                })))
                .then(Commands.literal("discovery-launch").executes(context -> run(context, (server, data) ->
                        LOGGER.info("ARCE_RELEASE_SRW_DISCOVERY step=launch result={}", SatelliteRuntime.launch(actor(server),
                                research(), PlanetaryContent.MARS).code()))))
                .then(Commands.literal("discovery-ready").executes(context -> run(context, (server, data) -> {
                    data.completeDue(server.overworld().getGameTime() + 1_000_000L);
                    LOGGER.info("ARCE_RELEASE_SRW_DISCOVERY step=ready {}", describe(server));
                })))
                .then(Commands.literal("discovery-claim").executes(context -> run(context, (server, data) -> {
                    var code = SatelliteRuntime.claim(actor(server), research()).code();
                    // The paid receipt's change is at this sequence; its commit must cover it before the discovery.
                    LOGGER.info("ARCE_RELEASE_SRW_DISCOVERY step=claim result={} sequence={}", code, data.mutationSequence());
                })));
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("satellite")
                .then(Commands.literal("release-test").then(writer))));
    }

    /** A data satellite with its first mission on Earth, made directly in the registry with no request. */
    private static void add(MinecraftServer server, SatelliteMissionSavedData data) {
        var definition = SatelliteRuntime.catalog().flatMap(catalog -> catalog.get(SatelliteIds.DATA_SATELLITE))
                .orElseThrow(() -> new IllegalStateException("The data satellite definition is unavailable"));
        UUID satellite = UUID.randomUUID();
        var result = data.launch(satellite, SatelliteManager.initialMissionId(satellite), OWNER, definition,
                CelestialIds.EARTH_ID, server.overworld().getGameTime(), false);
        LOGGER.info("ARCE_RELEASE_SRW_MUTATED result={} sequence={} satellites={}", result.code(), data.mutationSequence(),
                data.satellites().size());
    }

    private static void status(MinecraftServer server, SatelliteMissionSavedData data) {
        LOGGER.info("ARCE_RELEASE_SRW_STATUS {} {}", data.cuts().status(server, data), describe(server));
    }

    /** The terminal, its package and stored result, the pending table, and the Mars research and its discovery. */
    private static String describe(MinecraftServer server) {
        var data = SatelliteMissionSavedData.get(server);
        String terminal = "none", packaged = "none";
        String result = "none";
        if (server.overworld().getBlockEntity(terminalPos(server)) instanceof SatelliteTerminalBlockEntity placed) {
            terminal = "present";
            packaged = placed.menuInventory().getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty() ? "empty" : "bound";
            result = io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode.values()[placed
                    .saveWithoutMetadata().getCompound("SatelliteTerminal").getInt("last_result")].name();
        }
        String mission = data.mission(SatelliteManager.initialMissionId(RESEARCH))
                .map(state -> state.status().name()).orElse("NONE");
        return String.format(Locale.ROOT, "terminal=%s package=%s result=%s pending=%d research=%s discovered=%s balance=%d",
                terminal, packaged, result, SatelliteRuntime.terminalActions().map(table -> table.size()).orElse(-1),
                mission, CelestialSavedData.get(server).get(PlanetaryContent.MARS).isPresent(),
                data.account(OWNER).balance());
    }

    /** A powered terminal two blocks from the world spawn, always loaded, with a bound survey chip and package. */
    private static void place(MinecraftServer server) {
        var level = server.overworld();
        BlockPos pos = terminalPos(server);
        level.setBlock(pos, ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState()
                .setValue(SatelliteTerminalBlock.FACING, Direction.NORTH).setValue(SatelliteTerminalBlock.LIT, false), 3);
        var terminal = terminal(server);
        var slots = terminal.menuInventory();
        var survey = new SatelliteIdentity(SURVEY, OWNER, ModIdentity.id("survey_satellite"), SatelliteKind.SURVEY, List.of(
                id(ModItems.SATELLITE_CHASSIS.get()), id(ModItems.SURVEY_SCANNER_MODULE.get()),
                id(ModItems.SATELLITE_SOLAR_MODULE.get()), id(ModItems.DATA_STORAGE_UNIT.get()),
                id(ModItems.SATELLITE_BATTERY.get())));
        if (!slots.insertItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), survey), false).isEmpty()
                || !slots.insertItem(SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), survey), false).isEmpty()) {
            throw new IllegalStateException("The terminal refused the survey chip or package");
        }
        terminal.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow()
                .receiveEnergy(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, false);
        LOGGER.info("ARCE_RELEASE_SRW_TERMINAL placed {}", describe(server));
    }

    private static SatelliteTerminalBlockEntity terminal(MinecraftServer server) {
        if (server.overworld().getBlockEntity(terminalPos(server)) instanceof SatelliteTerminalBlockEntity terminal) {
            return terminal;
        }
        throw new IllegalStateException("No terminal at " + terminalPos(server).toShortString());
    }

    private static BlockPos terminalPos(MinecraftServer server) {
        return server.overworld().getSharedSpawnPos().offset(2, 0, 2);
    }

    /** The owner as a network-free actor next to the terminal. */
    private static FakePlayer actor(MinecraftServer server) {
        var player = new FakePlayer(server.overworld(), new GameProfile(OWNER, "W129bcOwner"));
        BlockPos pos = terminalPos(server);
        player.setPos(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
        return player;
    }

    private static SatelliteIdentity research() {
        return new SatelliteIdentity(RESEARCH, OWNER, SatelliteIds.DATA_SATELLITE);
    }

    private static ItemStack bound(Item item, SatelliteIdentity identity) {
        ItemStack stack = new ItemStack(item);
        SatelliteItemData.write(stack, identity);
        return stack;
    }

    private static net.minecraft.resources.ResourceLocation id(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    private static String upper(CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name).toUpperCase(Locale.ROOT);
    }

    private static int run(CommandContext<CommandSourceStack> context,
                           BiConsumer<MinecraftServer, SatelliteMissionSavedData> action) throws CommandSyntaxException {
        var server = context.getSource().getServer();
        if (!SatelliteRegistryWriterCuts.enabled(server)) {
            throw new SimpleCommandExceptionType(Component.literal("Registry writer cuts need an empty release-test dedicated server")).create();
        }
        try {
            action.accept(server, SatelliteMissionSavedData.get(server));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            throw new SimpleCommandExceptionType(Component.literal(refused.getMessage())).create();
        }
        context.getSource().sendSuccess(() -> Component.literal("Registry writer cut command done"), false);
        return 1;
    }
}
