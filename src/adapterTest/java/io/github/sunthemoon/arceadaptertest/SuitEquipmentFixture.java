package io.github.sunthemoon.arceadaptertest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Finite opt-in test commands; invoke production events/item use, never host internals. */
final class SuitEquipmentFixture {
    static final String HOST = "advancedrocketrycommunity";
    static final BlockPos CHEST = new BlockPos(264, 220, 264);
    static final String ROOT = "arce_suit_provider";

    private SuitEquipmentFixture() { }

    static void registerCommands(RegisterCommandsEvent event) {
        var command = Commands.literal("arce_fixture_suit")
                .requires(source -> source.hasPermission(4) && source.getEntity() == null);
        for (String phase : new String[]{"setup", "restart", "skipped", "restored"}) {
            command.then(Commands.literal(phase).executes(context -> {
                ServerLevel moon = context.getSource().getServer().getLevel(ResourceKey.create(
                        Registries.DIMENSION, ResourceLocation.tryParse(HOST + ":moon")));
                require(moon != null && moon.hasChunkAt(CHEST), "Fixture chest chunk is not loaded");
                require(moon.getBlockEntity(CHEST) instanceof ChestBlockEntity, "Fixture chest is missing");
                runPhase(moon, (ChestBlockEntity) moon.getBlockEntity(CHEST), phase);
                context.getSource().sendSuccess(() -> Component.literal("V130_SUIT_PHASE_PASS " + phase), false);
                return 1;
            }));
        }
        event.getDispatcher().register(command);
    }

    static void runPhase(ServerLevel level, ChestBlockEntity container, String phase) {
        boolean setup = phase.equals("setup");
        boolean skipped = phase.equals("skipped");
        require(setup || phase.equals("restart") || skipped || phase.equals("restored"), "Unknown fixture phase");
        require(skipped == Boolean.getBoolean("arce_adapter_test.skipSuitEquipment"), "Phase/registration mismatch");
        FakePlayer player = player(level);
        try {
            ItemStack armor;
            if (setup) {
                require(container.isEmpty(), "Setup cannot replace saved equipment");
                armor = new ItemStack(Items.LEATHER_CHESTPLATE);
                armor.getOrCreateTag().putString("fixture_marker", "persist-exactly");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("oxygen_canister"), 2));
            } else {
                armor = container.removeItemNoUpdate(0);
                require(armor.is(Items.LEATHER_CHESTPLATE) && armor.getCount() == 1, "Saved armor is missing");
                player.setItemInHand(InteractionHand.MAIN_HAND, container.removeItemNoUpdate(1));
                player.getInventory().add(container.removeItemNoUpdate(2));
            }
            player.setItemSlot(EquipmentSlot.CHEST, armor);
            int before = setup ? 0 : units(armor);
            if (setup) {
                require(refill(player), "Production item use did not refill equipment");
                require(units(armor) == 1000 && canisters(player) == 1 && empties(player) == 1,
                        "Initial whole-canister transfer failed");
            }
            CompoundTag unchanged = armor.save(new CompoundTag());
            ticks(player, 20);
            if (skipped) {
                require(player.getHealth() == 18 && armor.save(new CompoundTag()).equals(unchanged),
                        "Skipped provider supplied protection or changed data");
                require(!refill(player) && canisters(player) == 1 && empties(player) == 1,
                        "Absent equipment provider accepted refill");
            } else {
                require(player.getHealth() == 20 && units(armor) == (setup ? 999 : before - 1),
                        "Production vacuum cadence did not debit once and protect");
                if (phase.equals("restored")) {
                    require(refill(player) && units(armor) == before - 1 + 1000
                            && canisters(player) == 0 && empties(player) == 2, "Restored provider did not resume refill");
                }
            }
            require("persist-exactly".equals(armor.getTag().getString("fixture_marker")), "Unrelated armor data changed");
            int full = canisters(player);
            int empty = empties(player);
            // Transfer the same authoritative chest stack back; discard no live duplicate.
            player.getInventory().clearContent();
            container.setItem(0, armor);
            container.setItem(1, full == 0 ? ItemStack.EMPTY : new ItemStack(item("oxygen_canister"), full));
            container.setItem(2, empty == 0 ? ItemStack.EMPTY : new ItemStack(item("empty_canister"), empty));
            container.setChanged();
        } finally {
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
            player.getInventory().clearContent();
        }
    }

    static FakePlayer player(ServerLevel level) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ExternalSuitTest")) {
            @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(20);
        player.setPos(CHEST.getX() + 0.5, CHEST.getY() + 3, CHEST.getZ() + 0.5);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        return player;
    }

    static void ticks(FakePlayer player, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
        }
    }

    static boolean refill(FakePlayer player) {
        return item("oxygen_canister").use(player.level(), player, InteractionHand.MAIN_HAND).getResult().consumesAction();
    }

    static int units(ItemStack stack) { return stack.getTag().getCompound(ROOT).getCompound("data").getInt("oxygen"); }
    static int canisters(FakePlayer player) { return player.getInventory().countItem(item("oxygen_canister")); }
    static int empties(FakePlayer player) { return player.getInventory().countItem(item("empty_canister")); }
    static Item item(String path) { return ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(HOST + ":" + path)); }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
