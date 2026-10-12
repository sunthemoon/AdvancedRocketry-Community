package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconStatus;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.PlanetaryBeaconBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.finder.BeaconFinderService;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence.BeaconStateCodec;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.BeaconMarkerSnapshotPacket;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-065 section 7 on the real server: a beacon placed through its item is owned by its placer, forms from the full
 * loaded pattern and registers an active record; only its owner or an operator switches or renames it; unforming and
 * removal update the index. A held or correctly worn finder receives only its own player's active beacons on the
 * same body and Level, an unequipped finder receives nothing, another owner's beacon is never sent, and each player
 * receives at most one snapshot per 20 ticks, through the registered {@code classic_controls} codec.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetaryBeaconGameTests {
    private static final String MESSAGE = "message." + ModIdentity.MOD_ID + ".beacon.";
    private static final String MOON = "advancedrocketrycommunity:moon";

    private PlanetaryBeaconGameTests() {
    }

    private record Sent(ServerPlayer player, long tick, BeaconMarkerSnapshotPacket snapshot) {
    }

    private static ItemStack finder() {
        return new ItemStack(ModItems.BEACON_FINDER.get());
    }

    /** A built-in helmet with the finder installed through the authoritative equipment edit. */
    private static ItemStack finderHelmet(GameTestHelper helper) {
        ArmorEquipment.Edit edit = ArmorEquipment.install(new ItemStack(ModItems.SPACE_SUIT_HELMET.get()), finder());
        helper.assertTrue(edit != null, "The helmet refused the finder module");
        return edit.armor();
    }

    private static Set<UUID> ids(BeaconMarkerSnapshotPacket snapshot) {
        Set<UUID> ids = new HashSet<>();
        snapshot.markers().forEach(marker -> ids.add(marker.beaconId()));
        return ids;
    }

    @GameTest(template = "empty", batch = "beacon_placement", timeoutTicks = 200)
    public static void aPlacedBeaconIsOwnedFormsAndOnlyItsOwnerOrAnOperatorControlsIt(GameTestHelper helper) {
        BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper);
        try {
            BlockPos at = fixture.controllerAt(210);
            BlockPos standing = fixture.standingBefore(at);
            ServerPlayer owner = fixture.join("beaconOwner", standing);
            ServerPlayer stranger = fixture.join("beaconStranger", standing);
            ServerPlayer operator = fixture.joinOperator("beaconOperator", standing);
            fixture.buildStructure(at);
            PlanetaryBeaconBlockEntity beacon = fixture.place(owner, at, Optional.of("Home"));
            helper.assertTrue(beacon.owner().equals(Optional.of(owner.getUUID())) && beacon.name().equals("Home"),
                    "The placer does not own the beacon or its name was lost");
            UUID id = beacon.beaconId().orElseThrow();
            String body = fixture.body(at);
            fixture.await(40, "the beacon to form", () -> beacon.status() == BeaconStatus.ACTIVE, () -> {
                BeaconRecord record = fixture.index().get(id).orElseThrow();
                long formed = beacon.generation();
                helper.assertTrue(record.owner().equals(owner.getUUID()) && record.active() && record.name().equals("Home")
                        && record.body().equals(body) && record.at("minecraft:overworld", at.getX(), at.getY(), at.getZ())
                        && record.generation() == formed && formed >= 1, "The active record is wrong: " + record);

                // A stranger can neither switch nor rename it and learns nothing but that it is someone else's.
                fixture.replies.values().forEach(List::clear);
                fixture.use(stranger, at, ItemStack.EMPTY);
                ItemStack strangerTag = new ItemStack(Items.NAME_TAG).setHoverName(Component.literal("Mine"));
                fixture.use(stranger, at, strangerTag);
                helper.assertTrue(fixture.replies(stranger).equals(List.of(
                        BeaconGameTestFixture.actionBar(MESSAGE + "not_owner"),
                        BeaconGameTestFixture.actionBar(MESSAGE + "not_owner"))), "Stranger replies: "
                        + fixture.replies(stranger));
                helper.assertTrue(beacon.enabled() && beacon.name().equals("Home"), "A stranger changed the beacon");

                // Sneaking only reports; the owner's switch turns it off; an operator turns it back on.
                owner.setShiftKeyDown(true);
                fixture.use(owner, at, ItemStack.EMPTY);
                owner.setShiftKeyDown(false);
                helper.assertTrue(beacon.enabled() && fixture.lastReply(owner).equals(
                        BeaconGameTestFixture.actionBar(BeaconStatus.ACTIVE.translationKey())), "Sneaking changed it");
                fixture.use(owner, at, ItemStack.EMPTY);
                helper.assertTrue(beacon.status() == BeaconStatus.DISABLED
                        && !fixture.index().get(id).orElseThrow().active(), "The owner's switch did not deactivate it");
                fixture.use(operator, at, ItemStack.EMPTY);
                helper.assertTrue(beacon.status() == BeaconStatus.ACTIVE
                        && fixture.index().get(id).orElseThrow().active(), "An operator could not switch it back on");

                // The owner renames it with a named tag, which is kept; an overlong name is refused.
                ItemStack tag = new ItemStack(Items.NAME_TAG).setHoverName(Component.literal("Base"));
                fixture.use(owner, at, tag);
                helper.assertTrue(fixture.index().get(id).orElseThrow().name().equals("Base") && tag.getCount() == 1,
                        "The rename did not reach the index");
                fixture.use(owner, at, new ItemStack(Items.NAME_TAG).setHoverName(Component.literal("x".repeat(40))));
                helper.assertTrue(beacon.name().equals("Base") && fixture.lastReply(owner).equals(
                        BeaconGameTestFixture.actionBar(MESSAGE + "name_invalid", 32)), "An overlong name was taken");

                // Unforming marks it inactive at once; reforming starts a new generation.
                fixture.set(BeaconGameTestFixture.capOf(at), Blocks.AIR.defaultBlockState());
                fixture.await(40, "the beacon to unform", () -> beacon.status() == BeaconStatus.UNFORMED
                        && !fixture.index().get(id).orElseThrow().active(), () -> {
                    fixture.set(BeaconGameTestFixture.capOf(at), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    fixture.await(40, "the beacon to reform", () -> beacon.status() == BeaconStatus.ACTIVE, () -> {
                        BeaconRecord reformed = fixture.index().get(id).orElseThrow();
                        helper.assertTrue(reformed.active() && reformed.generation() == formed + 1
                                && beacon.generation() == formed + 1, "Reforming did not advance the generation");
                        // Removal deletes exactly its own record.
                        fixture.removeController(at);
                        helper.assertTrue(fixture.index().get(id).isEmpty(), "Removal left the record");
                        fixture.succeed();
                    });
                });
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    /** A beacon item carrying a {@code BlockEntityTag} that names another identity, owner, name, switch and generation. */
    private static ItemStack forgedBeacon(CompoundTag forged) {
        ItemStack stack = new ItemStack(ModItems.PLANETARY_BEACON.get());
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.put(BeaconStateCodec.KEY, forged.copy());
        stack.getOrCreateTag().put(BlockItem.BLOCK_ENTITY_TAG, blockEntity);
        return stack;
    }

    /** A new identity, no name, switched on and never formed: nothing of the forged root was loaded. */
    private static boolean fresh(PlanetaryBeaconBlockEntity beacon, UUID forgedId) {
        return beacon.beaconId().filter(id -> !id.equals(forgedId)).isPresent() && beacon.name().isEmpty()
                && beacon.enabled() && beacon.generation() == 0;
    }

    @GameTest(template = "empty", batch = "beacon_item_nbt", timeoutTicks = 100)
    public static void itemNbtWithoutOperatorRightsCannotForgeTheIdentityOrOwner(GameTestHelper helper) {
        BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper);
        try {
            BlockPos at = fixture.controllerAt(210);
            BlockPos standing = fixture.standingBefore(at);
            ServerPlayer builder = fixture.join("nbtBuilder", standing);
            ServerPlayer operator = fixture.joinOperator("nbtOperator", standing);
            UUID victim = UUID.randomUUID();
            UUID forgedId = UUID.randomUUID();
            CompoundTag forged = BeaconStateCodec.encode(new BeaconStateCodec.State(forgedId, Optional.of(victim),
                    "Free diamonds", false, 7));
            helper.assertTrue(BeaconStateCodec.decode(forged).owner().equals(Optional.of(victim)),
                    "The forged root is not a valid beacon root");

            // A creative player without operator rights: the placer owns a fresh beacon.
            builder.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            helper.assertTrue(builder.getAbilities().instabuild && !builder.hasPermissions(2),
                    "The builder is not a creative non-operator");
            PlanetaryBeaconBlockEntity built = fixture.placeStack(builder, at, forgedBeacon(forged));
            helper.assertTrue(built.onlyOpCanSetNbt(), "The beacon accepts non-operator item NBT");
            helper.assertTrue(fresh(built, forgedId) && built.owner().equals(Optional.of(builder.getUUID())),
                    "A non-operator's item NBT reached the beacon: " + built.saveWithoutMetadata());

            // A creative fake player and a placement without any player: a fresh beacon with no owner.
            FakePlayer fake = new FakePlayer(fixture.level, new GameProfile(UUID.randomUUID(), "nbtFakePlacer"));
            fake.getAbilities().instabuild = true;
            PlanetaryBeaconBlockEntity faked = fixture.placeStack(fake, at.east(3), forgedBeacon(forged));
            PlanetaryBeaconBlockEntity dispensed = fixture.placeStack(null, at.east(6), forgedBeacon(forged));
            for (PlanetaryBeaconBlockEntity beacon : List.of(faked, dispensed)) {
                helper.assertTrue(fresh(beacon, forgedId) && beacon.owner().isEmpty(),
                        "An unowned placement loaded the forged root: " + beacon.saveWithoutMetadata());
            }

            // The same item placed by a creative operator does load it (the owner is still the placer).
            operator.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            PlanetaryBeaconBlockEntity loaded = fixture.placeStack(operator, at.east(9), forgedBeacon(forged));
            helper.assertTrue(loaded.beaconId().equals(Optional.of(forgedId)) && !loaded.enabled()
                    && loaded.generation() == 7 && loaded.owner().equals(Optional.of(operator.getUUID())),
                    "A creative operator's item NBT was not loaded: " + loaded.saveWithoutMetadata());

            fixture.await(40, "the unowned beacons to report no owner", () -> faked.status() == BeaconStatus.NO_OWNER
                    && dispensed.status() == BeaconStatus.NO_OWNER, () -> {
                helper.assertTrue(fixture.index().ownedBy(victim).isEmpty(), "The forged owner holds a record");
                helper.assertTrue(fixture.index().get(faked.beaconId().orElseThrow()).isEmpty()
                        && fixture.index().get(dispensed.beaconId().orElseThrow()).isEmpty(),
                        "An unowned beacon registered");
                fixture.succeed();
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "beacon_finder", timeoutTicks = 200)
    public static void heldAndWornFindersReceiveOnlyTheirOwnActiveBeacons(GameTestHelper helper) {
        BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper);
        try {
            BlockPos at = fixture.controllerAt(210);
            BlockPos standing = fixture.standingBefore(at);
            ServerPlayer alpha = fixture.join("finderAlpha", standing);
            ServerPlayer beta = fixture.join("finderBeta", standing);
            ServerPlayer gamma = fixture.join("finderGamma", standing);
            String body = fixture.body(standing);
            fixture.buildStructure(at);
            PlanetaryBeaconBlockEntity alphaBeacon = fixture.place(alpha, at, Optional.of("Alpha"));
            UUID alphaId = alphaBeacon.beaconId().orElseThrow();
            // Beta's beacon stands nearer to Alpha than Alpha's own; privacy is not a matter of distance.
            BeaconRecord betaRecord = fixture.record(beta, standing.below(2), body, true, "Beta");
            BeaconRecord alphaInactive = fixture.record(alpha, standing.east(4), body, false, "Off");
            BeaconRecord alphaMoon = fixture.record(alpha, standing.west(4), MOON, true, "Moon");
            BeaconRecord gammaRecord = fixture.record(gamma, standing.south(1), body, true, "Gamma");
            fixture.await(40, "Alpha's beacon to form", () -> alphaBeacon.live(), () -> {
                List<Sent> sent = new ArrayList<>();
                BeaconFinderService service = new BeaconFinderService((player, snapshot) -> sent.add(
                        new Sent(player, -1, snapshot)), fixture.services());
                long now = 1_000_000L;

                // Held: Alpha sees exactly its own active beacon on this body, live.
                alpha.setItemInHand(InteractionHand.MAIN_HAND, finder());
                Optional<BeaconMarkerSnapshotPacket> alphaFirst = service.evaluate(alpha, now);
                helper.assertTrue(alphaFirst.isPresent() && ids(alphaFirst.get()).equals(Set.of(alphaId))
                        && alphaFirst.get().markers().get(0).live()
                        && alphaFirst.get().markers().get(0).name().equals("Alpha"), "Alpha's held snapshot: " + alphaFirst);
                // The off hand is a separate holding position; the stream is unchanged, so nothing is resent.
                alpha.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                alpha.setItemInHand(InteractionHand.OFF_HAND, finder());
                helper.assertTrue(BeaconFinderService.eligible(alpha) && service.evaluate(alpha, now + 20).isEmpty(),
                        "The off-hand finder changed the stream");

                // Worn: Beta's helmet module gives Beta only Beta's beacon, last known because no block is loaded.
                beta.setItemSlot(EquipmentSlot.HEAD, finderHelmet(helper));
                helper.assertTrue(ArmorEquipment.summarize(beta).beaconFinderEnabled(), "The worn summary is off");
                Optional<BeaconMarkerSnapshotPacket> betaFirst = service.evaluate(beta, now);
                helper.assertTrue(betaFirst.isPresent() && ids(betaFirst.get()).equals(Set.of(betaRecord.beaconId()))
                        && !betaFirst.get().markers().get(0).live(), "Beta's worn snapshot: " + betaFirst);
                // Worn and held at once is still one stream.
                beta.setItemInHand(InteractionHand.MAIN_HAND, finder());
                helper.assertTrue(service.evaluate(beta, now + 20).isEmpty(), "Worn and held sent a second stream");

                // Unequipped: a finder in the inventory, a finder helmet carried in the hand or worn on the chest.
                gamma.getInventory().setItem(9, finder());
                gamma.setItemInHand(InteractionHand.MAIN_HAND, finderHelmet(helper));
                gamma.setItemSlot(EquipmentSlot.CHEST, finderHelmet(helper));
                helper.assertTrue(!BeaconFinderService.eligible(gamma) && service.evaluate(gamma, now).isEmpty()
                        && service.evaluate(gamma, now + 20).isEmpty(), "An unequipped finder received markers");

                // Removing the helmet while a finder is still held keeps the stream; dropping that too clears it once.
                beta.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                helper.assertTrue(service.evaluate(beta, now + 40).isEmpty(), "A held finder lost its markers");
                beta.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                Optional<BeaconMarkerSnapshotPacket> cleared = service.evaluate(beta, now + 60);
                helper.assertTrue(cleared.equals(Optional.of(BeaconMarkerSnapshotPacket.EMPTY))
                        && service.evaluate(beta, now + 80).isEmpty(), "Unequipping did not clear exactly once");
                // Replacing the helmet with one without the module clears too.
                beta.setItemSlot(EquipmentSlot.HEAD, finderHelmet(helper));
                helper.assertTrue(service.evaluate(beta, now + 100).isPresent(), "Re-equipping sent nothing");
                beta.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACE_SUIT_HELMET.get()));
                helper.assertTrue(service.evaluate(beta, now + 120).equals(Optional.of(BeaconMarkerSnapshotPacket.EMPTY)),
                        "Replacing the helmet did not clear the markers");

                // No snapshot ever named another owner's beacon, an inactive one or one on another body.
                for (Sent entry : sent) {
                    Set<UUID> allowed = entry.player() == alpha ? Set.of(alphaId) : Set.of(betaRecord.beaconId());
                    helper.assertTrue(allowed.containsAll(ids(entry.snapshot())) && entry.player() != gamma,
                            entry.player().getGameProfile().getName() + " was sent " + entry.snapshot());
                }
                helper.assertTrue(sent.stream().noneMatch(entry -> ids(entry.snapshot()).contains(alphaInactive.beaconId())
                        || ids(entry.snapshot()).contains(alphaMoon.beaconId())
                        || ids(entry.snapshot()).contains(gammaRecord.beaconId())), "A filtered record was sent");
                fixture.succeed();
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "beacon_finder_rate", timeoutTicks = 40)
    public static void eachPlayerReceivesAtMostOneSnapshotPerTwentyTicks(GameTestHelper helper) {
        try (BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper)) {
            BlockPos standing = fixture.standingBefore(fixture.controllerAt(210));
            ServerPlayer viewer = fixture.join("finderRate", standing);
            String body = fixture.body(standing);
            fixture.record(viewer, standing.east(5), body, true, "One");
            fixture.record(viewer, standing.west(9), body, true, "Two");
            List<Long> ticks = new ArrayList<>();
            List<BeaconMarkerSnapshotPacket> snapshots = new ArrayList<>();
            long[] clock = {0L};
            BeaconFinderService service = new BeaconFinderService((player, snapshot) -> {
                ticks.add(clock[0]);
                snapshots.add(snapshot);
            }, fixture.services());
            long start = 5_000L;
            // The finder is picked up and put away more often than the interval: on and off every 20 ticks, then
            // every single tick.
            for (long tick = 0; tick < 200; tick++) {
                boolean holding = tick < 100 ? tick % 40 < 20 : tick % 2 == 0;
                viewer.setItemInHand(InteractionHand.MAIN_HAND, holding ? finder() : ItemStack.EMPTY);
                clock[0] = start + tick;
                service.evaluate(viewer, clock[0]);
            }
            helper.assertTrue(ticks.subList(0, 5).equals(List.of(start, start + 20, start + 40, start + 60, start + 80)),
                    "The first 100 ticks sent at " + ticks);
            helper.assertTrue(snapshots.get(0).markers().size() == 2 && snapshots.get(1).equals(BeaconMarkerSnapshotPacket.EMPTY)
                    && snapshots.get(2).equals(snapshots.get(0)), "The alternating snapshots differ: " + snapshots);
            helper.assertTrue(ticks.size() <= 10, "200 ticks sent " + ticks.size() + " snapshots");
            for (int i = 1; i < ticks.size(); i++) {
                helper.assertTrue(ticks.get(i) - ticks.get(i - 1) >= 20, "Two snapshots within 20 ticks: " + ticks);
            }
            // Holding steadily sends nothing more while nothing changes.
            int before = ticks.size();
            viewer.setItemInHand(InteractionHand.MAIN_HAND, finder());
            for (long tick = 200; tick < 300; tick++) {
                clock[0] = start + tick;
                service.evaluate(viewer, clock[0]);
            }
            helper.assertTrue(ticks.size() - before <= 1, "A steady finder resent: " + ticks);
            service.forget(viewer.getUUID());
            helper.assertTrue(service.tracked() == 0, "Logout did not forget the player");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "beacon_finder_channel", timeoutTicks = 100)
    public static void theRunningServerSendsTheSnapshotOnlyToTheHolderThroughIndexTwo(GameTestHelper helper) {
        BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper);
        try {
            BlockPos standing = fixture.standingBefore(fixture.controllerAt(210));
            ServerPlayer holder = fixture.join("channelHolder", standing);
            ServerPlayer bystander = fixture.join("channelBystander", standing);
            String body = fixture.body(standing);
            BeaconRecord record = fixture.record(holder, standing.east(6), body, true, "Relay");
            fixture.record(bystander, standing.west(6), body, true, "Other");
            drain(holder);
            drain(bystander);
            holder.setItemInHand(InteractionHand.MAIN_HAND, finder());
            helper.runAfterDelay(45, () -> {
                try {
                    List<BeaconMarkerSnapshotPacket> received = drain(holder);
                    helper.assertTrue(!received.isEmpty() && received.size() <= 3,
                            "The holder received " + received.size() + " snapshots in 45 ticks");
                    BeaconMarkerSnapshotPacket last = received.get(received.size() - 1);
                    helper.assertTrue(ids(last).equals(Set.of(record.beaconId())) && !last.markers().get(0).live()
                            && last.markers().get(0).name().equals("Relay"), "The holder received " + last);
                    helper.assertTrue(drain(bystander).isEmpty(), "A player without a finder received a snapshot");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    /** Every {@code classic_controls} index-2 frame queued for the player, decoded by the registered codec. */
    private static List<BeaconMarkerSnapshotPacket> drain(ServerPlayer player) {
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.connection.channel();
        channel.runPendingTasks();
        List<BeaconMarkerSnapshotPacket> snapshots = new ArrayList<>();
        for (int count = 0; count < 4_096; count++) {
            Object message = channel.readOutbound();
            if (message == null) {
                return snapshots;
            }
            try {
                if (message instanceof ClientboundCustomPayloadPacket payload
                        && payload.getIdentifier().equals(ModIdentity.id("classic_controls"))) {
                    FriendlyByteBuf data = payload.getData();
                    try {
                        if (data.readUnsignedByte() == 2) {
                            snapshots.add(BeaconMarkerSnapshotPacket.decode(data));
                        }
                    } finally {
                        data.release();
                    }
                }
            } finally {
                io.netty.util.ReferenceCountUtil.release(message);
            }
        }
        throw new IllegalStateException("The outbound drain reached its bound");
    }
}
