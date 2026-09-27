package io.github.sunthemoon.arceadaptertest;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.RegisterSatellitePayloadsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.SatelliteMissionDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersions;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** API/platform-only fixture. Native smoke commands are opt-in, bounded and permission-level 2. */
final class SatellitePayloadFixture {
    static final ResourceLocation ID = AdapterTestMod.id("research_payload");
    static final UUID OWNER = UUID.fromString("c6fcdf97-8f10-450e-ad36-67a644d23c2b");
    static int events;
    static RegisterSatellitePayloadsEvent retained;
    static final String HOST = "advancedrocketrycommunity";

    private SatellitePayloadFixture() { }

    static void register(RegisterSatellitePayloadsEvent event) {
        events++; retained = event;
        if (Boolean.getBoolean("arce_adapter_test.skipSatellitePayload")) {
            LogUtils.getLogger().info("Skipped satellite payload (event {})", events);
            return;
        }
        event.register(ID, ResourceLocation.tryParse("minecraft:amethyst_shard"), new SatelliteMissionDefinition(
                400, 137, 11, List.of(host("earth"), host("moon"))));
        LogUtils.getLogger().info("Registered satellite payload {} (API {}.{}, event {})", ID,
                ApiVersions.current().major(), ApiVersions.current().minor(), events);
    }

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce_sat_fixture").requires(source -> source.hasPermission(2))
                .then(Commands.argument("action", StringArgumentType.word())
                        .then(Commands.argument("position", BlockPosArgument.blockPos()).executes(context -> {
                            String action = StringArgumentType.getString(context, "action");
                            BlockPos position = BlockPosArgument.getLoadedBlockPos(context, "position");
                            var level = context.getSource().getLevel();
                            if (!List.of("create", "launch", "claim", "probe").contains(action)) {
                                throw new IllegalArgumentException("Unknown satellite fixture action");
                            }
                            BlockEntity terminal;
                            if (action.equals("create")) {
                                if (!level.isEmptyBlock(position)) { throw new IllegalStateException("Fixture requires empty position"); }
                                terminal = create(level, position, OWNER);
                                act(terminal, OWNER, 2);
                                act(terminal, OWNER, 3);
                            } else {
                                terminal = level.getBlockEntity(position);
                                if (terminal == null || !terminal.getBlockState().is(ForgeRegistries.BLOCKS.getValue(host("satellite_terminal")))) {
                                    throw new IllegalStateException("Satellite terminal absent");
                                }
                                if (action.equals("launch")) { act(terminal, OWNER, 3); }
                                if (action.equals("claim")) { act(terminal, OWNER, 4); }
                            }
                            log(terminal, action);
                            return 1;
                        }))));
    }

    static BlockEntity create(ServerLevel level, BlockPos position, UUID owner) {
        level.setBlockAndUpdate(position, ForgeRegistries.BLOCKS.getValue(host("satellite_terminal")).defaultBlockState());
        BlockEntity terminal = level.getBlockEntity(position);
        var inventory = terminal.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = switch (slot) {
                case 0 -> new ItemStack(ForgeRegistries.ITEMS.getValue(host("satellite_chassis")));
                case 1 -> new ItemStack(ForgeRegistries.ITEMS.getValue(host("satellite_solar_module")));
                case 2 -> new ItemStack(Items.AMETHYST_SHARD, 2);
                default -> new ItemStack(ForgeRegistries.ITEMS.getValue(host("satellite_control_chip")));
            };
            if (!inventory.insertItem(slot, stack, false).isEmpty()) { throw new IllegalStateException("Fixture input rejected: " + slot); }
        }
        terminal.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow().receiveEnergy(10000, false);
        return terminal;
    }

    static FakePlayer player(BlockEntity terminal, UUID owner) {
        var player = new FakePlayer((ServerLevel) terminal.getLevel(), new GameProfile(owner, "SatelliteFixture"));
        player.setPos(terminal.getBlockPos().getX() + 0.5, terminal.getBlockPos().getY() + 1, terminal.getBlockPos().getZ() + 0.5);
        return player;
    }

    static void act(BlockEntity terminal, UUID owner, int button) {
        var player = player(terminal, owner);
        var menu = ((MenuProvider) terminal).createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;
        if (!menu.clickMenuButton(player, button)) { throw new IllegalStateException("Fixture menu action rejected: " + button); }
    }

    static CompoundTag root(BlockEntity terminal) { return terminal.saveWithoutMetadata().getCompound("SatelliteTerminal"); }

    private static void log(BlockEntity terminal, String action) {
        CompoundTag root = root(terminal);
        var inventory = terminal.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        var chip = inventory.getStackInSlot(3);
        JsonObject result = new JsonObject(); result.addProperty("action", action);
        result.addProperty("energy", root.getInt("energy")); result.addProperty("result", root.getInt("last_result"));
        result.addProperty("payload_count", inventory.getStackInSlot(2).getCount());
        result.addProperty("package_empty", inventory.getStackInSlot(4).isEmpty());
        if (chip.hasTag() && chip.getTag().contains("SatelliteIdentity")) {
            var identity = chip.getTag().getCompound("SatelliteIdentity");
            result.addProperty("satellite", identity.getUUID("satellite_id").toString());
            result.addProperty("owner", identity.getUUID("owner_id").toString());
            result.addProperty("definition", identity.getString("definition_id"));
        }
        LogUtils.getLogger().info("ARCE_SAT_PAYLOAD {}", result);
    }

    static ResourceLocation host(String path) { return ResourceLocation.tryParse(HOST + ":" + path); }
}
