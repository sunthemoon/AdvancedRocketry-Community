package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.SuitOxygenProvider;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerProtectionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SuitEquipmentGameTests {
    private static final String ROOT = "arce_suit_provider";
    private static final BiFunction<CompoundTag, Integer, CompoundTag> WRITE = (data, units) -> {
        data.putInt("oxygen", units); return data;
    };

    private SuitEquipmentGameTests() { }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void mixedAndWrongSlotEquipmentUsesTheServerCadence(GameTestHelper helper) {
        try (Harness test = new Harness(moon(helper), WRITE)) {
            test.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACE_SUIT_HELMET.get()));
            test.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
            helper.assertTrue(test.refill(), "External chest did not refill");
            test.ticks(20);
            helper.assertTrue(test.units() == 999 && test.player.getHealth() == 20,
                    "Mixed suit did not consume exactly one oxygen and protect");
            test.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_HELMET));
            test.ticks(20);
            helper.assertTrue(test.units() == 999 && test.player.getHealth() == 18,
                    "Wrong-slot piece granted protection or consumed oxygen");
            test.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS, 2));
            helper.assertTrue(test.equipment.countPieces(test.player) == 3, "Count-two armor was accepted");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void wholeRefillConservesCanistersAndUnrelatedItemData(GameTestHelper helper) {
        try (Harness test = new Harness(moon(helper), WRITE)) {
            test.chest().getOrCreateTag().putString("marker", "unchanged");
            test.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get(), 3));
            helper.assertTrue(test.refill() && test.refill(), "Two whole refills were rejected");
            CompoundTag before = test.chest().getTag().copy();
            helper.assertTrue(!test.refill() && test.units() == 2000, "Full chest accepted another canister");
            helper.assertTrue(before.equals(test.chest().getTag())
                    && test.player.getMainHandItem().getCount() == 1 && test.emptyCount() == 2,
                    "Rejected refill changed equipment or canister authority");
            helper.assertTrue("unchanged".equals(test.chest().getTag().getString("marker")), "Outer tag changed");
            test.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            helper.assertTrue(!test.refill(), "Non-canister hand supplied oxygen");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void failedDebitPreservesPayloadAndDoesNotGrantFreeProtection(GameTestHelper helper) {
        try (Harness test = new Harness(moon(helper), (data, units) -> {
            if (units < data.getInt("oxygen")) { data.putInt("oxygen", 0); throw new IllegalStateException("fixture"); }
            return WRITE.apply(data, units);
        })) {
            test.seed(50);
            CompoundTag before = test.chest().getTag().copy();
            test.ticks(20);
            helper.assertTrue(test.player.getHealth() == 18 && before.equals(test.chest().getTag()),
                    "Failed debit protected the interval or changed original data");
            helper.assertTrue(test.last.oxygenUnits() == 0 && test.last.equippedSuitPieces() == 0,
                    "Disabled provider still contributed oxygen or armor");
            test.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
            helper.assertTrue(!test.refill() && test.emptyCount() == 0 && test.player.getMainHandItem().getCount() == 1,
                    "Disabled provider spent a refill canister");
            test.life.clear();
            helper.assertTrue(test.equipment.countPieces(test.player) == 4, "Session cleanup retained disabled mappings");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void malformedBodyAndFutureEnvelopeRemainItemLocal(GameTestHelper helper) {
        try (Harness test = new Harness(moon(helper), WRITE)) {
            for (boolean future : new boolean[]{false, true}) {
                test.seed(50);
                if (future) { test.chest().getTag().getCompound(ROOT).putInt("schema_version", 2); }
                else { test.chest().getTag().getCompound(ROOT).getCompound("data").putString("oxygen", "bad"); }
                CompoundTag before = test.chest().getTag().copy();
                test.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
                helper.assertTrue(!test.refill() && before.equals(test.chest().getTag()), "Invalid item data was overwritten");
                helper.assertTrue(test.player.getMainHandItem().getCount() == 1 && test.emptyCount() == 0,
                        "Invalid item spent or returned a canister");
            }
            test.seed(50);
            helper.assertTrue(test.equipment.readOxygen(test.player).oxygenUnits() == 50,
                    "Malformed item disabled valid provider data");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void creativeSpectatorAndBreathablePlayersDoNotDebit(GameTestHelper helper) {
        try (Harness test = new Harness(moon(helper), WRITE)) {
            test.seed(50);
            for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
                test.player.setGameMode(mode);
                test.ticks(20);
                helper.assertTrue(test.units() == 50 && test.last.status() == PlayerProtectionStatus.EXEMPT,
                        "Exempt player consumed oxygen");
            }
            test.player.setGameMode(GameType.CREATIVE);
            test.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
            helper.assertTrue(test.refill() && test.units() == 1050 && test.emptyCount() == 0
                    && test.player.getMainHandItem().getCount() == 1, "Creative refill semantics changed");
        }
        try (Harness test = new Harness(helper.getLevel(), WRITE)) {
            test.seed(50);
            test.ticks(20);
            helper.assertTrue(test.units() == 50 && test.last.status() == PlayerProtectionStatus.BREATHABLE_ENVIRONMENT,
                    "Breathable environment consumed oxygen");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void staleChestAndChangedHeldCanisterRejectCommit(GameTestHelper helper) {
        try (Harness test = new Harness(moon(helper), WRITE)) {
            test.seed(50);
            var read = test.equipment.readOxygen(test.player);
            ItemStack old = test.chest();
            test.player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            helper.assertTrue(!test.equipment.setOxygen(test.player, read, 49)
                    && old.getTag().getCompound(ROOT).getCompound("data").getInt("oxygen") == 50,
                    "Stale read committed to a replaced chest");
        }
        AtomicReference<FakePlayer> holder = new AtomicReference<>();
        try (Harness test = new Harness(moon(helper), (data, units) -> {
            // Deliberate contract violation to check host hand revalidation, not side-effect rollback.
            holder.get().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            return WRITE.apply(data, units);
        })) {
            holder.set(test.player);
            test.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
            helper.assertTrue(!test.refill() && !test.chest().getOrCreateTag().contains(ROOT) && test.emptyCount() == 0,
                    "Changed canister identity still committed oxygen");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void recursiveEquipmentUseIsContained(GameTestHelper helper) {
        AtomicReference<Harness> holder = new AtomicReference<>();
        try (Harness test = new Harness(moon(helper), (data, units) -> {
            holder.get().refill();
            return WRITE.apply(data, units);
        })) {
            holder.set(test);
            test.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
            helper.assertTrue(!test.refill() && !test.chest().getOrCreateTag().contains(ROOT) && test.player.getMainHandItem().getCount() == 1
                    && test.emptyCount() == 0, "Reentrant refill mutated authority");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void builtInChestCanUseExternalArmorWithoutChangingLegacyData(GameTestHelper helper) {
        try (Harness test = new Harness(moon(helper), WRITE)) {
            ItemStack chest = new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get());
            test.player.setItemSlot(EquipmentSlot.CHEST, chest);
            test.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
            helper.assertTrue(test.refill(), "Mixed built-in chest failed refill");
            test.ticks(20);
            helper.assertTrue(SpaceSuitOxygen.read(chest).oxygenUnits() == 999
                    && !chest.getTag().contains(ROOT) && test.player.getHealth() == 20,
                    "External armor changed legacy chest format or protection");
        }
        helper.succeed();
    }

    private static ServerLevel moon(GameTestHelper helper) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon is unavailable");
        return moon;
    }

    private static final class Harness implements AutoCloseable {
        private final FakePlayer player;
        private final SuitEquipmentService equipment;
        private final AtmosphereManager atmosphere;
        private final PlayerLifeSupportService life;
        private PlayerLifeSupportSnapshot last;

        private Harness(ServerLevel level, BiFunction<CompoundTag, Integer, CompoundTag> write) {
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "SuitEquipmentTest")) {
                @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
            };
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(20);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            try (var registry = new SuitEquipmentRegistry(id -> ForgeRegistries.ITEMS.getValue(id))) {
                registry.forOwner("fixture").register(ResourceLocation.tryParse("fixture:suit"), Map.of(
                        ResourceLocation.tryParse("minecraft:leather_helmet"), EquipmentSlot.HEAD,
                        ResourceLocation.tryParse("minecraft:leather_chestplate"), EquipmentSlot.CHEST,
                        ResourceLocation.tryParse("minecraft:leather_leggings"), EquipmentSlot.LEGS,
                        ResourceLocation.tryParse("minecraft:leather_boots"), EquipmentSlot.FEET), 1, new SuitOxygenProvider() {
                    public OptionalInt readOxygen(CompoundTag data) {
                        return data.contains("oxygen") && !data.contains("oxygen", 3)
                                ? OptionalInt.empty() : OptionalInt.of(data.getInt("oxygen"));
                    }
                    public CompoundTag writeOxygen(CompoundTag data, int units) { return write.apply(data, units); }
                });
                equipment = new SuitEquipmentService(registry.freeze());
            }
            atmosphere = new AtmosphereManager(new CelestialEnvironmentService(new CelestialCatalogManager()));
            life = new PlayerLifeSupportService(atmosphere, (ignored, snapshot) -> last = snapshot, equipment);
        }

        private ItemStack chest() { return player.getItemBySlot(EquipmentSlot.CHEST); }
        private int units() { return equipment.readOxygen(player).oxygenUnits(); }
        private boolean refill() { return equipment.fillOneCanister(player, InteractionHand.MAIN_HAND).accepted(); }
        private int emptyCount() { return player.getInventory().countItem(ModItems.EMPTY_CANISTER.get()); }
        private void ticks(int count) { for (int tick = 0; tick < count; tick++) { life.tickPlayer(player); } }
        private void seed(int units) {
            CompoundTag data = new CompoundTag();
            data.putInt("schema_version", 1);
            data.putString("provider", "fixture:suit");
            data.putInt("payload_version", 1);
            CompoundTag body = new CompoundTag(); body.putInt("oxygen", units);
            data.put("data", body);
            chest().getOrCreateTag().put(ROOT, data);
        }
        @Override public void close() { life.clear(); atmosphere.clear(); }
    }
}
