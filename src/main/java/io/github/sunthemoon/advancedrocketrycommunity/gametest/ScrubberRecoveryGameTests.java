package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.CarbonCartridge;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.DeviceProtection;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.ScrubberBank;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.ScrubberBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.AtmosphereVolume;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeBounds;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScrubberRecoveryGameTests {
    @GameTest(template = "atmosphere_test", batch = "scrubber_recovery", timeoutTicks = 40)
    public static void mixedCrossChunkSnapshotProtectsAndRecoversExactlyOneUsableCartridge(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            CompoundTag oldFacade = f.facade.saveWithoutMetadata();
            f.levelSet(f.facadePos, Blocks.AIR.defaultBlockState());
            f.levelSet(f.facadePos, ModBlocks.CO2_SCRUBBER.get().defaultBlockState());
            f.facade = (ScrubberBlockEntity) helper.getLevel().getBlockEntity(f.facadePos);
            f.observe();
            ItemStack offered = new ItemStack(ModItems.CARBON_SCRUBBER_CARTRIDGE.get());
            offered.getOrCreateTag().putString("marker", "mixed-snapshot");
            helper.assertTrue(f.items().insertItem(0, offered, false).isEmpty(), "Replacement facade did not accept cartridge");
            CompoundTag newerVent = f.vent.saveWithoutMetadata();
            UUID bankIdentity = f.facade.identity();
            // Replay the two independently saved chunks: vent B and still-bound facade A.
            f.vent.load(newerVent); f.facade.load(oldFacade); f.observe();
            helper.assertTrue(!bankIdentity.equals(f.facade.identity()) && f.facade.protectedData(), "Mixed identity was not protected");
            CompoundTag before = f.vent.scrubbers().encode();
            var state = f.facade.getBlockState();
            var event = new BlockEvent.BreakEvent(helper.getLevel(), f.facadePos, state, helper.makeMockSurvivalPlayer());
            DeviceProtection.breaking(event);
            helper.assertTrue(event.isCanceled(), "Forge break admitted unresolved facade");
            helper.assertTrue(!state.getBlock().onDestroyedByPlayer(state, helper.getLevel(), f.facadePos,
                    helper.makeMockSurvivalPlayer(), true, helper.getLevel().getFluidState(f.facadePos)), "Native break admitted unresolved facade");
            helper.assertTrue(!state.getBlock().canEntityDestroy(state, helper.getLevel(), f.facadePos, null), "Entity destruction admitted unresolved facade");
            state.getBlock().onBlockExploded(state, helper.getLevel(), f.facadePos, null);
            f.facade.detach();
            helper.assertTrue(helper.getLevel().getBlockEntity(f.facadePos) == f.facade && before.equals(f.vent.scrubbers().encode()),
                    "Refused removal changed bank or physical facade");
            helper.assertTrue(f.items().extractItem(0, 1, false).isEmpty(), "Conflicting facade claimed bank cartridge");
            f.openPlayer();
            helper.assertTrue(f.useVent() == InteractionResult.CONSUME, "Vent player recovery was refused");
            ItemStack recovered = f.player.getMainHandItem();
            helper.assertTrue(recovered.getCount() == 1 && CarbonCartridge.uses(recovered) == CarbonCartridge.MAX_USES
                    && recovered.getTag().getString("marker").equals("mixed-snapshot"), "Recovery lost cartridge contents");
            helper.assertTrue(f.replies.equals(List.of(Component.translatable(
                    "message.advancedrocketrycommunity.vent.scrubber_cartridge_recovered").getString())), "Recovery message differs");
            helper.assertTrue(f.vent.scrubbers().encode().getList("banks", 10).isEmpty() && !f.facade.protectedData(),
                    "Recovery retained the orphan or stale binding");
            helper.assertTrue(f.vent.scrubbers().recoverOrphan(f.vent).isEmpty(), "Recovery replay duplicated cartridge");
            // The original facade identity may bind normally only after explicit withdrawal.
            UUID retainedIdentity = f.facade.identity(); f.observe();
            helper.assertTrue(f.facade.identity().equals(retainedIdentity) && f.items().insertItem(0, recovered, false).isEmpty(),
                    "Recovered cartridge could not be reused without inventing an identity");
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(f.vent.scrubbers().skipDebit(f.vent)
                    && CarbonCartridge.uses(f.items().getStackInSlot(0)) == CarbonCartridge.MAX_USES - 1,
                    "Recovered cartridge did not spend exactly one use");
            helper.assertTrue(f.useVent() == InteractionResult.FAIL && f.player.getMainHandItem().isEmpty(), "Healthy bank was recovered a second time");
            helper.assertTrue(f.items().extractItem(0, 1, false).getCount() == 1 && f.items().extractItem(0, 1, false).isEmpty(),
                    "Final withdrawal duplicated cartridge");
            helper.succeed();
        }
    }

    @GameTest(template = "atmosphere_test", batch = "scrubber_recovery", timeoutTicks = 40)
    public static void recoveryRetainsMatchingAndUnreadableFacadesButPermitsDisabledWithdrawal(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            helper.assertTrue(f.items().insertItem(0, new ItemStack(ModItems.CARBON_SCRUBBER_CARTRIDGE.get()), false).isEmpty(), "Insertion failed");
            CompoundTag bank = f.vent.scrubbers().encode(); CompoundTag savedFacade = f.facade.saveWithoutMetadata();
            helper.assertTrue(f.vent.scrubbers().recoverOrphan(f.vent).isEmpty() && bank.equals(f.vent.scrubbers().encode()), "Matching facade lost bank");
            helper.getLevel().removeBlockEntity(f.facadePos);
            helper.assertTrue(f.vent.scrubbers().recoverOrphan(f.vent).isEmpty() && bank.equals(f.vent.scrubbers().encode()), "Missing facade entity was treated as proof of an orphan");
            f.facade = new ScrubberBlockEntity(f.facadePos, helper.getLevel().getBlockState(f.facadePos));
            f.facade.load(savedFacade); helper.getLevel().setBlockEntity(f.facade);
            CompoundTag unreadable = new CompoundTag(); unreadable.put(ScrubberBlockEntity.KEY, StringTag.valueOf("retained"));
            f.facade.load(unreadable);
            helper.assertTrue(f.vent.scrubbers().recoverOrphan(f.vent).isEmpty() && bank.equals(f.vent.scrubbers().encode()), "Unreadable facade lost bank");
            f.facade.load(savedFacade);
            savedFacade.getCompound(ScrubberBlockEntity.KEY).putString("identity", UUID.randomUUID().toString());
            f.facade.load(savedFacade);
            SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, false);
            try {
                helper.assertTrue(f.vent.scrubbers().recoverOrphan(f.vent).getCount() == 1
                        && f.vent.scrubbers().recoverOrphan(f.vent).isEmpty(), "Disabled withdrawal duplicated or stranded cartridge");
            } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED); }
            helper.succeed();
        }
    }

    @GameTest(template = "atmosphere_test", batch = "scrubber_recovery", timeoutTicks = 40)
    public static void recoveryDoesNotLoadAnUnavailableFacadeChunk(GameTestHelper helper) {
        try (OwnedLoadedFrontier frontier = new OwnedLoadedFrontier(helper, helper.getLevel(), 220)) {
            recoveryDoesNotLoadAnUnavailableFacadeChunk(helper, frontier);
        }
    }

    private static void recoveryDoesNotLoadAnUnavailableFacadeChunk(GameTestHelper helper, OwnedLoadedFrontier frontier) {
        var level = helper.getLevel(); var chunks = level.getChunkSource();
        BlockPos ventPos = frontier.edge, facadePos = ventPos.east(); BlockState before = level.getBlockState(ventPos);
        helper.assertTrue(level.getBlockEntity(ventPos) == null, "Frontier fixture must be BE-free");
        // Native neighbour-shape propagation would load the very chunk this fixture must leave absent.
        int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        frontier.set(ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
        var vent = (OxygenVentBlockEntity) level.getBlockEntity(ventPos);
        try {
            CompoundTag bank = new ScrubberBank().encode(); ListTag banks = new ListTag(); CompoundTag entry = new CompoundTag();
            entry.putInt("face", Direction.EAST.ordinal()); entry.putString("identity", UUID.randomUUID().toString());
            entry.put("cartridge", CarbonCartridge.prepare(new ItemStack(ModItems.CARBON_SCRUBBER_CARTRIDGE.get())).serializeNBT());
            banks.add(entry); bank.put("banks", banks);
            CompoundTag saved = new CompoundTag(); saved.put(OxygenVentPersistence.DATA_KEY, OxygenVentPersistence.encode(0, 0, 0, 0, 0, bank));
            vent.load(saved);
            helper.assertTrue(!vent.protectedDeviceData(), "Frontier fixture must load a supported bank root");
            helper.assertTrue(chunks.getChunkNow(facadePos.getX() >> 4, facadePos.getZ() >> 4) == null,
                    "Fixture setup loaded the unavailable facade chunk");
            helper.assertTrue(vent.scrubbers().recoverOrphan(vent).isEmpty() && bank.equals(vent.scrubbers().encode())
                    && chunks.getChunkNow(facadePos.getX() >> 4, facadePos.getZ() >> 4) == null,
                    "Recovery loaded an unavailable chunk or withdrew its unresolved cartridge");
            helper.succeed();
        } finally { vent.load(new CompoundTag()); level.setBlock(ventPos, before, quiet); }
    }

    @GameTest(template = "atmosphere_test", batch = "scrubber_stale_binding", timeoutTicks = 40)
    public static void emptyConflictingAssociationIsReleasedAtTheVentWithoutMovingAnItem(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            UUID identity = f.facade.identity();
            f.replayReplacedFacade(null);
            ListTag banks = f.vent.scrubbers().encode().getList("banks", 10); UUID generation = f.vent.scrubbers().generation();
            helper.assertTrue(banks.size() == 1 && banks.getCompound(0).getCompound("cartridge").isEmpty(), "Snapshot must hold one empty association");
            f.openPlayer();
            f.assertRemovalRefused("Empty association");
            CompoundTag before = f.vent.scrubbers().encode();
            helper.assertTrue(f.vent.scrubbers().recoverOrphan(f.vent).isEmpty() && f.items().extractItem(0, 1, false).isEmpty(),
                    "An absent cartridge was withdrawn");
            f.observe();
            helper.assertTrue(f.facade.protectedData() && before.equals(f.vent.scrubbers().encode()), "Observation admitted the conflicting facade");
            f.replies.clear();
            helper.assertTrue(f.useVent() == InteractionResult.CONSUME && f.player.getMainHandItem().isEmpty(), "Vent did not release the empty association");
            helper.assertTrue(f.replies.equals(List.of(message("vent.scrubber_association_released"))), "Release message differs");
            helper.assertTrue(f.vent.scrubbers().encode().getList("banks", 10).isEmpty() && generation.equals(f.vent.scrubbers().generation())
                    && f.facade.identity().equals(identity) && !bound(f.facade), "Release kept the association or binding, or changed an identity");
            helper.assertTrue(f.useVent() == InteractionResult.FAIL && f.replies.equals(List.of(message("vent.scrubber_association_released"),
                    message("vent.scrubber_recovery_refused"))), "A second release was not refused");
            f.assertRemovable("Empty association");
            helper.assertTrue(f.vent.scrubbers().encode().getList("banks", 10).isEmpty() && f.noCartridgeDropped(), "Removal invented an association or a cartridge");
            helper.succeed();
        }
    }

    @GameTest(template = "atmosphere_test", batch = "scrubber_stale_binding_frontier", timeoutTicks = 40)
    public static void emptyAssociationBehindAnUnloadedFacadeChunkIsKept(GameTestHelper helper) {
        try (OwnedLoadedFrontier frontier = new OwnedLoadedFrontier(helper, helper.getLevel(), 224)) {
            emptyAssociationBehindAnUnloadedFacadeChunkIsKept(helper, frontier);
        }
    }

    private static void emptyAssociationBehindAnUnloadedFacadeChunkIsKept(GameTestHelper helper, OwnedLoadedFrontier frontier) {
        var level = helper.getLevel(); var chunks = level.getChunkSource();
        BlockPos ventPos = frontier.edge, facadePos = ventPos.east(); BlockState before = level.getBlockState(ventPos);
        helper.assertTrue(level.getBlockEntity(ventPos) == null, "Frontier fixture must be BE-free");
        frontier.set(ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
        var vent = (OxygenVentBlockEntity) level.getBlockEntity(ventPos);
        try {
            CompoundTag bank = new ScrubberBank().encode(); ListTag banks = new ListTag(); CompoundTag entry = new CompoundTag();
            entry.putInt("face", Direction.EAST.ordinal()); entry.putString("identity", UUID.randomUUID().toString());
            entry.put("cartridge", new CompoundTag()); banks.add(entry); bank.put("banks", banks);
            CompoundTag saved = new CompoundTag(); saved.put(OxygenVentPersistence.DATA_KEY, OxygenVentPersistence.encode(0, 0, 0, 0, 0, bank));
            vent.load(saved);
            helper.assertTrue(!vent.protectedDeviceData() && bank.equals(vent.scrubbers().encode()), "Frontier fixture must load a supported empty association");
            helper.assertTrue(chunks.getChunkNow(facadePos.getX() >> 4, facadePos.getZ() >> 4) == null, "Fixture setup loaded the unavailable facade chunk");
            helper.assertTrue(!vent.scrubbers().releaseEmptyOrphan(vent) && vent.scrubbers().recoverOrphan(vent).isEmpty()
                    && bank.equals(vent.scrubbers().encode()) && chunks.getChunkNow(facadePos.getX() >> 4, facadePos.getZ() >> 4) == null,
                    "Release loaded an unavailable chunk or dropped its unresolved association");
            helper.succeed();
        } finally { vent.load(new CompoundTag()); level.setBlock(ventPos, before, QUIET); }
    }

    @GameTest(template = "atmosphere_test", batch = "scrubber_stale_binding", timeoutTicks = 40)
    public static void facadeBoundToAnotherGenerationIsReleasedAtTheFacadeWithoutTouchingTheBank(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            CompoundTag oldFacade = f.facade.saveWithoutMetadata(); UUID oldGeneration = f.vent.scrubbers().generation();
            // The vent is replaced and A rebinds to its generation; then A's chunk reverts to the old binding.
            f.levelSet(f.ventPos, Blocks.AIR.defaultBlockState());
            f.levelSet(f.ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
            f.vent = (OxygenVentBlockEntity) helper.getLevel().getBlockEntity(f.ventPos); f.observe();
            helper.assertTrue(f.items().insertItem(0, cartridge("other-generation"), false).isEmpty(), "Rebound facade did not accept cartridge");
            f.facade.load(oldFacade); f.observe();
            CompoundTag bank = f.vent.scrubbers().encode();
            helper.assertTrue(!oldGeneration.equals(f.vent.scrubbers().generation()) && bound(f.facade) && bank.getList("banks", 10).size() == 1,
                    "Snapshot must bind A to a generation the vent no longer has");
            f.openPlayer();
            f.assertRemovalRefused("Other generation");
            helper.assertTrue(f.items().extractItem(0, 1, false).isEmpty(), "Stale facade withdrew the bank cartridge");
            f.replies.clear();
            helper.assertTrue(f.useVent() == InteractionResult.FAIL && bank.equals(f.vent.scrubbers().encode())
                    && f.replies.equals(List.of(message("vent.scrubber_recovery_refused"))), "Vent recovery claimed a matching identity");
            f.replies.clear();
            helper.assertTrue(f.useFacade() == InteractionResult.CONSUME && f.player.getMainHandItem().isEmpty()
                    && f.replies.equals(List.of(message("scrubber.binding_released"))), "Facade did not release its stale binding");
            helper.assertTrue(!bound(f.facade) && !f.facade.protectedData() && bank.equals(f.vent.scrubbers().encode()),
                    "Release touched the bank or kept the binding");
            // The released matching identity rebinds normally and withdraws the bank's single cartridge.
            f.observe();
            helper.assertTrue(f.useFacade() == InteractionResult.CONSUME && f.player.getMainHandItem().getTag().getString("marker").equals("other-generation")
                    && CarbonCartridge.uses(f.player.getMainHandItem()) == CarbonCartridge.MAX_USES, "Rebound facade did not withdraw the cartridge");
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(f.useFacade() == InteractionResult.FAIL && f.items().extractItem(0, 1, false).isEmpty(), "Cartridge was withdrawn twice");
            f.assertRemovable("Other generation");
            helper.assertTrue(f.vent.scrubbers().encode().getList("banks", 10).isEmpty() && f.noCartridgeDropped(), "Removal duplicated a cartridge");
            helper.succeed();
        }
    }

    @GameTest(template = "atmosphere_test", batch = "scrubber_stale_binding", timeoutTicks = 40)
    public static void facadeWhoseOwnerCellHoldsNoVentIsReleasedOnlyWithLoadedProof(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var level = helper.getLevel(); CompoundTag oldFacade = f.facade.saveWithoutMetadata();
            // The vent is removed, while A's chunk keeps the old binding.
            f.levelSet(f.ventPos, Blocks.AIR.defaultBlockState()); f.facade.load(oldFacade);
            helper.assertTrue(bound(f.facade), "Replay did not restore the binding");
            // A retained vent root, and a vent block without its entity, prove nothing.
            f.levelSet(f.ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
            f.vent = (OxygenVentBlockEntity) level.getBlockEntity(f.ventPos);
            CompoundTag retained = new CompoundTag(); retained.put(OxygenVentPersistence.DATA_KEY, StringTag.valueOf("retained")); f.vent.load(retained);
            helper.assertTrue(f.vent.protectedDeviceData() && !f.facade.releaseStaleBinding() && oldFacade.equals(f.facade.saveWithoutMetadata()),
                    "A retained vent root was treated as proof");
            f.vent.load(new CompoundTag()); level.removeBlockEntity(f.ventPos);
            var chunk = level.getChunkAt(f.ventPos);
            helper.assertTrue(!f.facade.releaseStaleBinding() && chunk.getBlockEntities().get(f.ventPos) == null
                    && oldFacade.equals(f.facade.saveWithoutMetadata()), "A vent block without its entity was treated as proof");
            f.levelSet(f.ventPos, Blocks.AIR.defaultBlockState());
            f.openPlayer();
            f.assertRemovalRefused("No vent");
            f.replies.clear();
            helper.assertTrue(f.useFacade() == InteractionResult.CONSUME && f.player.getMainHandItem().isEmpty() && !bound(f.facade)
                    && f.replies.equals(List.of(message("scrubber.binding_released"))), "Facade did not release its stale binding");
            helper.assertTrue(f.useFacade() == InteractionResult.FAIL, "A released facade was released again");
            f.assertRemovable("No vent");
            helper.assertTrue(f.noCartridgeDropped(), "Removal dropped a cartridge");
            helper.succeed();
        }
    }

    @GameTest(template = "atmosphere_test", batch = "scrubber_stale_binding", timeoutTicks = 40)
    public static void facadeUnreferencedBySameGenerationBankIsReleasedAndItsCartridgeStaysRecoverable(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.replayReplacedFacade(cartridge("unreferenced"));
            CompoundTag bank = f.vent.scrubbers().encode();
            f.openPlayer();
            f.assertRemovalRefused("Unreferenced facade");
            f.replies.clear();
            helper.assertTrue(f.useFacade() == InteractionResult.CONSUME && f.player.getMainHandItem().isEmpty()
                    && f.replies.equals(List.of(message("scrubber.binding_released"))), "Facade did not release its unreferenced binding");
            helper.assertTrue(!bound(f.facade) && bank.equals(f.vent.scrubbers().encode()), "Release touched the bank or kept the binding");
            f.assertRemovable("Unreferenced facade");
            helper.assertTrue(bank.equals(f.vent.scrubbers().encode()), "Removing the released facade changed the bank");
            helper.assertTrue(f.useVent() == InteractionResult.CONSUME && f.player.getMainHandItem().getTag().getString("marker").equals("unreferenced")
                    && CarbonCartridge.uses(f.player.getMainHandItem()) == CarbonCartridge.MAX_USES, "The bank cartridge was not recoverable");
            helper.assertTrue(f.vent.scrubbers().encode().getList("banks", 10).isEmpty() && f.vent.scrubbers().recoverOrphan(f.vent).isEmpty()
                    && !f.vent.scrubbers().releaseEmptyOrphan(f.vent) && f.noCartridgeDropped(), "Recovery duplicated or retained the cartridge");
            helper.succeed();
        }
    }

    @GameTest(template = "atmosphere_test", batch = "scrubber_stale_binding_frontier", timeoutTicks = 40)
    public static void staleBindingToAnUnloadedOwnerCellStaysProtected(GameTestHelper helper) {
        try (OwnedLoadedFrontier frontier = new OwnedLoadedFrontier(helper, helper.getLevel(), 228)) {
            staleBindingToAnUnloadedOwnerCellStaysProtected(helper, frontier);
        }
    }

    private static void staleBindingToAnUnloadedOwnerCellStaysProtected(GameTestHelper helper, OwnedLoadedFrontier frontier) {
        var level = helper.getLevel(); var chunks = level.getChunkSource();
        BlockPos facadePos = frontier.edge, ownerPos = facadePos.east(); BlockState before = level.getBlockState(facadePos);
        helper.assertTrue(level.getBlockEntity(facadePos) == null, "Frontier fixture must be BE-free");
        frontier.set(facadePos, ModBlocks.CO2_SCRUBBER.get().defaultBlockState());
        var facade = (ScrubberBlockEntity) level.getBlockEntity(facadePos);
        try {
            CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putString("identity", UUID.randomUUID().toString());
            root.putLong("owner_position", ownerPos.asLong()); root.putString("owner_generation", UUID.randomUUID().toString());
            CompoundTag saved = new CompoundTag(); saved.put(ScrubberBlockEntity.KEY, root); facade.load(saved);
            CompoundTag kept = facade.saveWithoutMetadata();
            helper.assertTrue(bound(facade) && facade.available(), "Frontier fixture must load a supported bound facade");
            helper.assertTrue(chunks.getChunkNow(ownerPos.getX() >> 4, ownerPos.getZ() >> 4) == null, "Fixture setup loaded the unavailable owner chunk");
            var items = facade.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
            helper.assertTrue(facade.protectedData() && !facade.releaseStaleBinding() && items.extractItem(0, 1, false).isEmpty()
                    && kept.equals(facade.saveWithoutMetadata()) && chunks.getChunkNow(ownerPos.getX() >> 4, ownerPos.getZ() >> 4) == null,
                    "An unloaded owner cell released the binding or was loaded");
            helper.succeed();
        } finally { facade.load(new CompoundTag()); level.setBlock(facadePos, before, QUIET); }
    }

    /** Native neighbour-shape propagation would load the very chunk a frontier fixture must leave absent. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static ItemStack cartridge(String marker) {
        ItemStack stack = new ItemStack(ModItems.CARBON_SCRUBBER_CARTRIDGE.get()); stack.getOrCreateTag().putString("marker", marker); return stack;
    }
    private static String message(String key) { return Component.translatable("message.advancedrocketrycommunity." + key).getString(); }
    private static boolean bound(ScrubberBlockEntity facade) {
        return facade.saveWithoutMetadata().getCompound(ScrubberBlockEntity.KEY).contains("owner_position");
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final BlockPos ventPos;
        private final BlockPos facadePos;
        private final BlockState beforeVent;
        private final BlockState beforeFacade;
        private OxygenVentBlockEntity vent;
        private final AtmosphereVolume volume;
        private ScrubberBlockEntity facade;
        private ServerPlayer player;
        private EmbeddedChannel channel;
        private final List<String> replies = new ArrayList<>();

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            BlockPos origin = helper.absolutePos(new BlockPos(0, 2, 4));
            ventPos = new BlockPos((origin.getX() >> 4 << 4) + 15, origin.getY(), origin.getZ());
            facadePos = ventPos.east();
            helper.assertTrue((ventPos.getX() >> 4) != (facadePos.getX() >> 4)
                    && helper.getLevel().hasChunkAt(ventPos) && helper.getLevel().hasChunkAt(facadePos), "Fixture must straddle loaded chunks");
            helper.assertTrue(helper.getLevel().getBlockEntity(ventPos) == null && helper.getLevel().getBlockEntity(facadePos) == null,
                    "Fixture cells must be BE-free");
            beforeVent = helper.getLevel().getBlockState(ventPos); beforeFacade = helper.getLevel().getBlockState(facadePos);
            levelSet(ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
            levelSet(facadePos, ModBlocks.CO2_SCRUBBER.get().defaultBlockState());
            vent = (OxygenVentBlockEntity) helper.getLevel().getBlockEntity(ventPos);
            facade = (ScrubberBlockEntity) helper.getLevel().getBlockEntity(facadePos);
            var cell = new VolumePosition(facadePos.getX(), facadePos.getY() + 1, facadePos.getZ());
            var cells = Set.of(cell);
            volume = new AtmosphereVolume(VolumeIdentity.fromCells(cells), cells, VolumeBounds.single(cell));
            observe();
        }
        private void levelSet(BlockPos pos, BlockState state) { helper.getLevel().setBlock(pos, state, Block.UPDATE_ALL); }
        private void observe() { vent.scrubbers().observe(vent, volume); }
        private IItemHandler items() { return facade.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new); }
        private void openPlayer() {
            var level = helper.getLevel();
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "scrubRecovery")) {
                @Override public void sendSystemMessage(Component message, boolean overlay) { replies.add(message.getString()); }
            };
            Connection connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.teleportTo(level, ventPos.getX() + .5, ventPos.getY() + 1, ventPos.getZ() + .5, 0, 0);
            player.setShiftKeyDown(true); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); replies.clear();
        }
        private InteractionResult useVent() {
            var state = vent.getBlockState();
            return state.getBlock().use(state, helper.getLevel(), ventPos, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(ventPos), Direction.UP, ventPos, false));
        }
        private InteractionResult useFacade() {
            var state = facade.getBlockState();
            return state.getBlock().use(state, helper.getLevel(), facadePos, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(facadePos), Direction.UP, facadePos, false));
        }
        /** The review's mixed snapshot: the vent root naming replacement B (charged or not) replayed beside still-bound A. */
        private void replayReplacedFacade(ItemStack cartridge) {
            CompoundTag oldFacade = facade.saveWithoutMetadata();
            levelSet(facadePos, Blocks.AIR.defaultBlockState());
            levelSet(facadePos, ModBlocks.CO2_SCRUBBER.get().defaultBlockState());
            facade = (ScrubberBlockEntity) helper.getLevel().getBlockEntity(facadePos);
            observe();
            helper.assertTrue(cartridge == null || items().insertItem(0, cartridge, false).isEmpty(), "Replacement facade did not accept cartridge");
            CompoundTag newerVent = vent.saveWithoutMetadata(); UUID replacement = facade.identity();
            vent.load(newerVent); facade.load(oldFacade); observe();
            helper.assertTrue(!replacement.equals(facade.identity()) && bound(facade), "Replay did not restore bound facade A");
        }
        /** Every ordinary removal path refuses, and the bank and physical facade stay unchanged. */
        private void assertRemovalRefused(String scenario) {
            var level = helper.getLevel(); var state = facade.getBlockState();
            CompoundTag bank = vent.scrubbers().encode(), root = facade.saveWithoutMetadata();
            var event = new BlockEvent.BreakEvent(level, facadePos, state, helper.makeMockSurvivalPlayer());
            DeviceProtection.breaking(event);
            helper.assertTrue(event.isCanceled() && facade.protectedData(), scenario + ": Forge break admitted the facade");
            helper.assertTrue(!state.getBlock().onDestroyedByPlayer(state, level, facadePos, helper.makeMockSurvivalPlayer(), true,
                    level.getFluidState(facadePos)) && !state.getBlock().canEntityDestroy(state, level, facadePos, null)
                    && state.getDestroyProgress(helper.makeMockSurvivalPlayer(), level, facadePos) == 0, scenario + ": native removal admitted the facade");
            state.getBlock().onBlockExploded(state, level, facadePos, null); facade.detach();
            helper.assertTrue(!player.gameMode.destroyBlock(facadePos), scenario + ": player break admitted the facade");
            helper.assertTrue(level.getBlockEntity(facadePos) == facade && bank.equals(vent.scrubbers().encode())
                    && root.equals(facade.saveWithoutMetadata()), scenario + ": refused removal changed the bank or facade");
        }
        /** The released facade is ordinarily removable; the creative breaker drops nothing. */
        private void assertRemovable(String scenario) {
            var level = helper.getLevel(); var state = facade.getBlockState();
            var event = new BlockEvent.BreakEvent(level, facadePos, state, helper.makeMockSurvivalPlayer());
            DeviceProtection.breaking(event);
            helper.assertTrue(!event.isCanceled() && !facade.protectedData() && state.getBlock().canEntityDestroy(state, level, facadePos, null)
                    && state.getDestroyProgress(helper.makeMockSurvivalPlayer(), level, facadePos) > 0, scenario + ": released facade is still protected");
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(player.gameMode.destroyBlock(facadePos) && !level.getBlockState(facadePos).is(ModBlocks.CO2_SCRUBBER.get()),
                    scenario + ": player could not remove the released facade");
        }
        private boolean noCartridgeDropped() {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(ventPos).inflate(4),
                    item -> item.getItem().is(ModItems.CARBON_SCRUBBER_CARTRIDGE.get())).isEmpty();
        }
        @Override public void close() {
            try {
                if (player != null) { helper.getLevel().getServer().getPlayerList().remove(player); }
            } finally {
                if (channel != null) { channel.finishAndReleaseAll(); }
                // Clear owned banks before restoring fixture cells, so cleanup cannot spawn cartridge drops.
                vent.load(new CompoundTag()); facade.load(new CompoundTag());
                levelSet(facadePos, beforeFacade); levelSet(ventPos, beforeVent);
            }
        }
    }
    private ScrubberRecoveryGameTests() { }
}
