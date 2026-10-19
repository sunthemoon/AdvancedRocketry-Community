package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.SatelliteBayFixtures.TEMPLATE;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowAdmissionAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayPackage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * W27c1 A1 on the real server: the receipt index loaded at ServerStartingEvent, the bay's insertion refusal through
 * every surface, and the N5 command through the dispatcher. Their own batch keeps journal and admission state free of
 * concurrently running tests; receipts come only from the gated test hooks and are retired before each test ends.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteBayReceiptGameTests {
    private static final String BATCH = "satellite_bay_receipts";
    private static final String MESSAGE = "message.advancedrocketrycommunity.bay_receipt.";
    private SatelliteBayReceiptGameTests() {
    }

    private static BayReceipt receipt(MinecraftServer server, UUID owner, UUID rocket, int bay, UUID satellite, String signature,
                                      BayReceipt.Phase phase) {
        String hash = RocketNativeResources.hash("w27c1-gametest");
        long tick = server.getTickCount();
        return new BayReceipt(UUID.randomUUID(), BayReceiptTestHooks.nextSequence(server), rocket, new RocketPosition(0, bay, 0), satellite,
                owner, ModIdentity.id("w27b_solar").toString(), SatelliteKind.SOLAR, ModIdentity.id("earth").toString(), signature,
                UUID.randomUUID(), hash, null, hash, null, phase, phase == BayReceipt.Phase.REJECTED ? "INTERRUPTED" : "NONE",
                false, false, 0, tick, tick, 0);
    }
    private static void put(GameTestHelper helper, BayReceipt receipt) {
        BayReceiptStore.Admission admission = BayReceiptTestHooks.put(helper.getLevel().getServer(), receipt, outcome -> { });
        helper.assertTrue(admission.queued(), "Receipt fixture refused: " + admission);
    }
    private static boolean settled(MinecraftServer server) {
        return BayReceiptTestHooks.store(server).map(store -> !store.holdsWork()).orElse(true);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void theReceiptIndexWasLoadedAtServerStartingBeforeTheFirstTick(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var receipts = EscrowRuntime.of(server).bayReceipts(server);
        helper.assertTrue(receipts.firstTickState() != null
                        && receipts.firstTickState() != EscrowAdmissionAuthority.Delegate.State.NOT_LOADED,
                "The receipt index was not loaded before the first tick: " + receipts.firstTickState());
        helper.assertTrue(receipts.state() != EscrowAdmissionAuthority.Delegate.State.NOT_LOADED, "The receipt index is not loaded");
        var authority = BayReceiptTestHooks.authority(server);
        for (var consumer : EscrowAdmissionAuthority.Consumer.values()) {
            helper.assertTrue(authority.checkReserve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), consumer)
                    != EscrowAdmissionAuthority.Result.UNAVAILABLE, "Admission is unavailable for " + consumer);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void insertionIsRefusedThroughEverySurfaceWhileABlockingReceiptExists(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos bayRelative = new BlockPos(8, 4, 8);
        BlockPos above = bayRelative.above();
        BlockPos side = bayRelative.west();
        UUID ownerId = UUID.randomUUID();
        SatelliteBayBlockEntity bay = SatelliteBayFixtures.place(helper, bayRelative, SatelliteBayFixtures.root(ownerId, null));
        IItemHandler handler = bay.itemHandler();
        ItemStack pkg = SatelliteBayFixtures.solarPackage(ownerId);
        var captured = SatelliteBayPackage.capture(pkg).orElseThrow();
        UUID satellite = SatelliteBayPackage.decode(captured).identity().orElseThrow().satelliteId();
        BayReceipt intent = receipt(server, ownerId, UUID.randomUUID(), 1, satellite, RocketNativeResources.signature(captured),
                BayReceipt.Phase.INTENT);
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            put(helper, intent);
            ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w27c1Owner", level,
                    helper.absolutePos(bayRelative).north(2), new ArrayList<>());
            joined.add(owner);
            SatelliteBayMenu menu = (SatelliteBayMenu) bay.createMenu(1, owner.getInventory(), owner);
            helper.assertTrue(!handler.isItemValid(0, pkg) && !menu.getSlot(0).mayPlace(pkg),
                    "The handler or the menu slot accepted a package that a receipt blocks");
            helper.assertTrue(ItemStack.matches(handler.insertItem(0, pkg, true), pkg)
                    && ItemStack.matches(handler.insertItem(0, pkg, false), pkg) && bay.packageState().isEmpty(),
                    "A simulated or actual insertion passed the receipt refusal");
            menu.setCarried(pkg.copy());
            menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(ItemStack.matches(menu.getCarried(), pkg) && bay.packageState().isEmpty(), "A menu click inserted");
            menu.setCarried(ItemStack.EMPTY);
            helper.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
            HopperBlockEntity hopper = (HopperBlockEntity) level.getBlockEntity(helper.absolutePos(above));
            hopper.setItem(0, pkg.copy());
            HopperBlockEntity.pushItemsTick(level, helper.absolutePos(above), level.getBlockState(helper.absolutePos(above)), hopper);
            helper.assertTrue(ItemStack.matches(hopper.getItem(0), pkg) && bay.packageState().isEmpty(), "A hopper inserted");
            hopper.setItem(0, ItemStack.EMPTY);
            helper.setBlock(side, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
            BlockPos dropperAt = helper.absolutePos(side);
            DispenserBlockEntity dropper = (DispenserBlockEntity) level.getBlockEntity(dropperAt);
            dropper.setItem(0, pkg.copy());
            level.getBlockState(dropperAt).tick(level, dropperAt, level.random);
            helper.assertTrue(ItemStack.matches(dropper.getItem(0), pkg) && bay.packageState().isEmpty(), "A dropper inserted");
            dropper.setItem(0, ItemStack.EMPTY);
        } catch (RuntimeException failure) {
            cleanup(level, helper, joined, bayRelative, above, side);
            throw failure;
        }
        joined.forEach(player -> server.getPlayerList().remove(player));
        joined.clear();
        // Each step waits for the store's exact acknowledgements, whatever other tests of the batch are writing.
        int[] step = {0};
        helper.onEachTick(() -> {
            if (step[0] > 2 || !settled(server)) {
                return;
            }
            try {
                if (step[0] == 0) {
                    helper.assertTrue(!handler.isItemValid(0, pkg), "The durable INTENT receipt stopped blocking");
                    put(helper, intent.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", server.getTickCount()));
                    helper.assertTrue(!handler.isItemValid(0, pkg), "The unacknowledged refusal unblocked the package");
                    step[0] = 1;
                } else if (step[0] == 1) {
                    helper.assertTrue(handler.isItemValid(0, pkg) && handler.insertItem(0, pkg, false).isEmpty()
                            && bay.packageState().isPresent(), "An acknowledged REJECTED receipt still blocked the package");
                    helper.assertTrue(BayReceiptTestHooks.remove(server, intent.op(), outcome -> { }).queued(), "Fixture cleanup refused");
                    step[0] = 2;
                } else {
                    helper.assertTrue(BayReceiptTestHooks.store(server).orElseThrow().find(satellite).isEmpty(), "The fixture receipt remained");
                    step[0] = 3;
                    cleanup(level, helper, joined, bayRelative, above, side);
                    helper.succeed();
                }
            } catch (RuntimeException failure) {
                step[0] = 3;
                cleanup(level, helper, joined, bayRelative, above, side);
                throw failure;
            }
        });
    }

    private static void cleanup(ServerLevel level, GameTestHelper helper, List<ServerPlayer> joined, BlockPos... positions) {
        joined.forEach(player -> level.getServer().getPlayerList().remove(player));
        joined.clear();
        for (BlockPos relative : positions) {
            level.removeBlockEntity(helper.absolutePos(relative));
            level.setBlock(helper.absolutePos(relative), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** The N5 command through the dispatcher: permission, each in-game refusal, then two audited retirements. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 600)
    public static void theRetireCommandRefusesUntilItsRocketIsClearAndRetiresOnTheExactAcknowledgement(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Component> messages = new ArrayList<>();
        CommandSource sink = new CommandSource() {
            @Override public void sendSystemMessage(Component message) { messages.add(message); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        CommandSourceStack operator = new CommandSourceStack(sink, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)), Vec2.ZERO,
                helper.getLevel(), 2, "w27c1Operator", Component.literal("w27c1Operator"), server, null);
        UUID owner = UUID.randomUUID(), rocket = UUID.randomUUID(), orphanSatellite = UUID.randomUUID(), heldSatellite = UUID.randomUUID();
        String signature = RocketNativeResources.hash("w27c1-command-package");
        BayReceipt orphan = receipt(server, owner, rocket, 1, orphanSatellite, signature, BayReceipt.Phase.REJECTED);
        BayReceipt held = receipt(server, owner, rocket, 2, heldSatellite, signature, BayReceipt.Phase.INTENT);
        put(helper, orphan);
        put(helper, held);
        int[] step = {0};
        helper.onEachTick(() -> {
            if (step[0] > 3 || !settled(server)) {
                return;
            }
            switch (step[0]) {
                case 0 -> {
                    int denied = server.getCommands().performPrefixedCommand(operator.withPermission(0),
                            "arce bay receipt retire-rejected " + orphanSatellite);
                    helper.assertTrue(denied == 0, "A non-operator ran the command");
                    expect(helper, server, operator, messages, orphanSatellite, 0, "refused.rocket_pending");
                    expect(helper, server, operator, messages, heldSatellite, 0, "refused.not_rejected");
                    expect(helper, server, operator, messages, UUID.randomUUID(), 0, "refused.not_found");
                    put(helper, held.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", server.getTickCount()));
                    expect(helper, server, operator, messages, heldSatellite, 0, "refused.unacknowledged");
                    step[0] = 1;
                }
                case 1 -> {
                    expect(helper, server, operator, messages, orphanSatellite, 1, "requested");
                    step[0] = 2;
                }
                case 2 -> {
                    helper.assertTrue(key(messages.get(messages.size() - 1)).equals(MESSAGE + "retired"), "No retirement report");
                    helper.assertTrue(BayReceiptTestHooks.store(server).orElseThrow().find(orphanSatellite).isEmpty(), "The orphan remained");
                    expect(helper, server, operator, messages, orphanSatellite, 0, "refused.not_found");
                    expect(helper, server, operator, messages, heldSatellite, 1, "requested");
                    step[0] = 3;
                }
                default -> {
                    helper.assertTrue(BayReceiptTestHooks.store(server).orElseThrow().find(heldSatellite).isEmpty(), "The second receipt remained");
                    step[0] = 4;
                    helper.succeed();
                }
            }
        });
    }

    private static void expect(GameTestHelper helper, MinecraftServer server, CommandSourceStack operator, List<Component> messages,
                               UUID satellite, int result, String message) {
        int size = messages.size();
        int actual = server.getCommands().performPrefixedCommand(operator, "arce bay receipt retire-rejected " + satellite);
        helper.assertTrue(actual == result && messages.size() == size + 1 && key(messages.get(size)).equals(MESSAGE + message),
                "Expected " + message + " (" + result + ") but got " + actual + " "
                        + (messages.size() > size ? key(messages.get(size)) : "no message"));
    }

    private static String key(Component message) {
        return message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : message.getString();
    }
}
