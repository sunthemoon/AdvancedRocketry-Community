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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
        var level = helper.getLevel(); var chunks = level.getChunkSource();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO), position = null;
        // Find an already-loaded frontier without acquiring chunks or changing tickets.
        for (int x = -32; x <= 32 && position == null; x++) {
            int cx = (origin.getX() >> 4) + x, cz = origin.getZ() >> 4;
            if (chunks.getChunkNow(cx, cz) != null && chunks.getChunkNow(cx + 1, cz) == null) {
                position = new BlockPos((cx << 4) + 15, 220, (cz << 4) + 8);
            }
        }
        helper.assertTrue(position != null, "No loaded frontier available for the unloaded guard fixture");
        BlockPos ventPos = position, facadePos = position.east(); BlockState before = level.getBlockState(ventPos);
        helper.assertTrue(level.getBlockEntity(ventPos) == null, "Frontier fixture must be BE-free");
        // Native neighbour-shape propagation would load the very chunk this fixture must leave absent.
        int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState(), quiet);
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

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final BlockPos ventPos;
        private final BlockPos facadePos;
        private final BlockState beforeVent;
        private final BlockState beforeFacade;
        private final OxygenVentBlockEntity vent;
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
