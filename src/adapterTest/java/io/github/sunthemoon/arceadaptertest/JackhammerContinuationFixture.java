package io.github.sunthemoon.arceadaptertest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Separate opt-in packaged-server fixture: native login/save and continued use, never a gameplay command. */
@Mod.EventBusSubscriber(modid = AdapterTestMod.MOD_ID)
public final class JackhammerContinuationFixture {
    private static final UUID ID = UUID.fromString("914adf94-5147-49a3-bf78-ff21cd948719");
    private static final BlockPos TARGET = new BlockPos(12, 180, 12);
    private static final String NAME = "Jackhammer restart";
    private static final String NOTE = "native continuation";

    private JackhammerContinuationFixture() { }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("arce.jackhammerSmoke")) { return; }
        event.getDispatcher().register(Commands.literal("arce_jackhammer_test")
                .requires(source -> source.hasPermission(4) && source.getEntity() == null)
                .then(Commands.literal("seed").executes(context -> run(context.getSource().getServer(), true)))
                .then(Commands.literal("continue").executes(context -> run(context.getSource().getServer(), false))));
    }

    private static int run(MinecraftServer server, boolean seed) {
        require(server.isSameThread() && server.getPlayerList().getPlayer(ID) == null,
                "Fixture requires the server thread and its offline test UUID");
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("advancedrocketrycommunity", "jackhammer"));
        require(item != null && item != Items.AIR, "Registered jackhammer unavailable");
        var level = server.overworld();
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            ServerPlayer player = new ServerPlayer(server, level, new GameProfile(ID, "jackContinue"));
            try {
                // Native placeNewPlayer reads this UUID's playerdata. Continuation never manually loads/reseeds NBT.
                server.getPlayerList().placeNewPlayer(connection, player);
                if (seed) {
                    require(player.getInventory().isEmpty(), "Seed requires a new empty native inventory");
                    ItemStack stack = new ItemStack(item);
                    stack.setDamageValue(17); stack.setHoverName(Component.literal(NAME));
                    stack.getOrCreateTag().putString("fixture_note", NOTE);
                    stack.enchant(Enchantments.BLOCK_EFFICIENCY, 2);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                } else {
                    require(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "Native loaded game mode differs");
                }
                ItemStack stack = player.getMainHandItem();
                int damage = seed ? 17 : 18;
                require(stack.getItem() == item && stack.getCount() == 1 && stack.getMaxDamage() == 1024
                        && stack.getDamageValue() == damage && stack.hasTag()
                        && NOTE.equals(stack.getTag().getString("fixture_note"))
                        && stack.getHoverName().getString().equals(NAME)
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, stack) == 2,
                        "Native pre-use held item differs");
                CompoundTag before = stack.save(new CompoundTag());
                mine(server, player, before, damage + 1);
            } finally {
                if (server.getPlayerList().getPlayer(ID) == player) {
                    // Native removal saves the connected player's actual inventory; no explicit fixture NBT writer.
                    server.getPlayerList().remove(player);
                }
            }
            // The scope is synchronous: drain/release its packets, not a retained static player/channel service.
        } finally { channel.finishAndReleaseAll(); }
        System.out.println("ARCE_JACKHAMMER_CONTINUATION stage=" + (seed ? "seed" : "continue")
                + " uuid=" + ID + " before_damage=" + (seed ? 17 : 18) + " after_damage=" + (seed ? 18 : 19));
        return 1;
    }

    private static void mine(MinecraftServer server, ServerPlayer player, CompoundTag before, int afterDamage) {
        var level = server.overworld();
        level.getChunkAt(TARGET); // Fixed opt-in fixture setup, not a client-selected load.
        BlockPos neighbor = TARGET.east();
        require(level.getBlockEntity(TARGET) == null && level.getBlockEntity(neighbor) == null,
                "Owned fixture requires BE-free cells");
        var targetBefore = level.getBlockState(TARGET);
        var neighborBefore = level.getBlockState(neighbor);
        AABB bounds = new AABB(TARGET).inflate(1.0D);
        Set<UUID> previous = new HashSet<>();
        level.getEntitiesOfClass(ItemEntity.class, bounds).forEach(entity -> previous.add(entity.getUUID()));
        require(previous.isEmpty(), "Owned fixture loot bounds are not empty");
        try {
            level.setBlock(TARGET, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(neighbor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            player.teleportTo(level, TARGET.getX() - 0.5D, TARGET.getY(), TARGET.getZ() + 0.5D, 0, 0);
            player.setOnGround(true);
            player.gameMode.handleBlockBreakAction(TARGET, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    Direction.WEST, level.getMaxBuildHeight(), 0);
            var loot = level.getEntitiesOfClass(ItemEntity.class, bounds);
            require(level.getBlockState(TARGET).isAir() && level.getBlockState(neighbor).is(Blocks.STONE)
                    && loot.size() == 1 && loot.get(0).getItem().is(Items.COBBLESTONE)
                    && loot.get(0).getItem().getCount() == 1, "Native single-target break/qualified loot differs");
            CompoundTag expected = before.copy(); expected.getCompound("tag").putInt("Damage", afterDamage);
            require(expected.equals(player.getMainHandItem().save(new CompoundTag())), "Native held data/wear differs");
        } finally {
            level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
                    .filter(entity -> !previous.contains(entity.getUUID())).forEach(ItemEntity::discard);
            level.setBlock(TARGET, targetBefore, Block.UPDATE_ALL);
            level.setBlock(neighbor, neighborBefore, Block.UPDATE_ALL);
            level.destroyBlockProgress(player.getId(), TARGET, -1);
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) { throw new IllegalStateException(reason); }
    }
}
