package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconStatus;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconWorld;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.PlanetaryBeaconBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.finder.BeaconFinderService;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence.BeaconStateCodec;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native placement and local controls, copied identities, and conservative stale-position takeover. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeaconAuthorityGameTests {
    private BeaconAuthorityGameTests() {
    }

    @GameTest(template = "empty", batch = "beacon_unowned_placement", timeoutTicks = 100)
    public static void setblockAndFakePlacementStayUnownedAndFakeControlsAreRefused(GameTestHelper helper) {
        BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper);
        try {
            BlockPos at = fixture.controllerAt(210);
            fixture.buildStructure(at);
            String command = "setblock " + at.getX() + " " + at.getY() + " " + at.getZ()
                    + " advancedrocketrycommunity:planetary_beacon[facing=north]";
            fixture.server.getCommands().performPrefixedCommand(fixture.server.createCommandSourceStack(), command);
            PlanetaryBeaconBlockEntity unowned = (PlanetaryBeaconBlockEntity) fixture.level.getBlockEntity(at);
            helper.assertTrue(unowned != null && unowned.owner().isEmpty(), "/setblock assigned an owner");
            // Track the command's controller for cleanup without replacing it.
            fixture.set(at, unowned.getBlockState());
            FakePlayer fake = new FakePlayer(fixture.level, new GameProfile(UUID.randomUUID(), "beaconFake"));
            fake.getAbilities().instabuild = true;
            BlockPos fakeAt = at.east(4);
            fixture.buildStructure(fakeAt);
            PlanetaryBeaconBlockEntity faked = fixture.placeStack(fake, fakeAt,
                    new ItemStack(ModItems.PLANETARY_BEACON.get()));
            ServerPlayer owner = fixture.join("fakeControlOwner", fixture.standingBefore(at.east(8)));
            BlockPos ownedAt = at.east(8);
            fixture.buildStructure(ownedAt);
            PlanetaryBeaconBlockEntity owned = fixture.place(owner, ownedAt, Optional.of("Owned"));
            // Even a fake player with the exact owner's UUID cannot switch or rename the beacon.
            FakePlayer impersonator = new FakePlayer(fixture.level, owner.getGameProfile());
            impersonator.getAbilities().instabuild = true;
            fixture.await(40, "unowned registrations and the owned beacon", () -> unowned.status() == BeaconStatus.NO_OWNER
                    && faked.status() == BeaconStatus.NO_OWNER && owned.live(), () -> {
                BeaconRecord before = fixture.index().get(owned.beaconId().orElseThrow()).orElseThrow();
                fixture.use(impersonator, ownedAt, ItemStack.EMPTY);
                fixture.use(impersonator, ownedAt, new ItemStack(Items.NAME_TAG).setHoverName(Component.literal("Fake")));
                helper.assertTrue(owned.enabled() && owned.name().equals("Owned")
                        && fixture.index().get(before.beaconId()).equals(Optional.of(before)), "A fake player controlled the beacon");
                helper.assertTrue(unowned.owner().isEmpty() && faked.owner().isEmpty()
                        && fixture.index().get(unowned.beaconId().orElseThrow()).isEmpty()
                        && fixture.index().get(faked.beaconId().orElseThrow()).isEmpty(), "An unowned placement registered");
                helper.assertTrue(!BeaconFinderService.eligible(impersonator), "A fake player is finder-eligible");
                fixture.succeed();
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "beacon_copy_takeover", timeoutTicks = 150)
    public static void aCopyCannotRewriteOrRemoveTheOriginalButCanTakeOverAProvenStalePosition(GameTestHelper helper) {
        BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper);
        try {
            BlockPos at = fixture.controllerAt(210);
            BlockPos copyAt = at.east(5);
            ServerPlayer owner = fixture.join("beaconCopyOwner", fixture.standingBefore(at));
            fixture.buildStructure(at);
            PlanetaryBeaconBlockEntity original = fixture.place(owner, at, Optional.of("Original"));
            fixture.await(40, "the original to register", original::live, () -> {
                UUID id = original.beaconId().orElseThrow();
                BeaconRecord before = fixture.index().get(id).orElseThrow();
                fixture.buildStructure(copyAt);
                PlanetaryBeaconBlockEntity copy = fixture.place(owner, copyAt, Optional.empty());
                copy.load(original.saveWithoutMetadata());
                fixture.await(40, "the copy to be refused", () -> copy.status() == BeaconStatus.DUPLICATE, () -> {
                    helper.assertTrue(!BeaconWorld.stale(fixture.server, before)
                            && fixture.index().get(id).equals(Optional.of(before)), "A copy rewrote a live original");
                    fixture.removeController(copyAt);
                    helper.assertTrue(fixture.index().get(id).equals(Optional.of(before)), "Removing a copy deleted the original record");
                    PlanetaryBeaconBlockEntity replacement = fixture.place(owner, copyAt, Optional.empty());
                    replacement.load(original.saveWithoutMetadata());
                    fixture.await(40, "the replacement copy to be refused", () -> replacement.status() == BeaconStatus.DUPLICATE, () -> {
                        fixture.removeController(at);
                        fixture.restore(before); // The index's last checkpoint still names the now-removed original.
                        helper.assertTrue(BeaconWorld.stale(fixture.server, before), "The loaded empty original position is not stale");
                        fixture.use(owner, copyAt, ItemStack.EMPTY);
                        fixture.use(owner, copyAt, ItemStack.EMPTY);
                        BeaconRecord taken = fixture.index().get(id).orElseThrow();
                        helper.assertTrue(replacement.live() && taken.owner().equals(owner.getUUID()) && taken.active()
                                && taken.at(before.level(), copyAt.getX(), copyAt.getY(), copyAt.getZ()), "A same-owner stale takeover failed");
                        fixture.succeed();
                    });
                });
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "beacon_copy_boundaries", timeoutTicks = 100)
    public static void unloadedOriginalsBlockTakeoverAndAStaleRecordStillRequiresTheSameOwner(GameTestHelper helper) {
        BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper);
        try {
            BlockPos at = fixture.controllerAt(210);
            ServerPlayer owner = fixture.join("copyBoundaryOwner", fixture.standingBefore(at));
            fixture.buildStructure(at);
            PlanetaryBeaconBlockEntity copy = fixture.place(owner, at, Optional.empty());
            UUID id = copy.beaconId().orElseThrow();
            BlockPos far = fixture.unloaded(0);
            BeaconRecord inaccessible = new BeaconRecord(id, owner.getUUID(), fixture.body(at), "minecraft:overworld",
                    far.getX(), far.getY(), far.getZ(), 7, true, "Unloaded original");
            fixture.restore(inaccessible);
            fixture.await(40, "the unloaded original to block takeover", () -> copy.status() == BeaconStatus.DUPLICATE, () -> {
                helper.assertTrue(!BeaconWorld.stale(fixture.server, inaccessible) && !fixture.level.hasChunkAt(far)
                        && fixture.index().get(id).equals(Optional.of(inaccessible)), "An unloaded original was treated as stale");
                BeaconRecord foreign = new BeaconRecord(id, UUID.randomUUID(), inaccessible.body(), inaccessible.level(),
                        at.getX(), at.getY() - 2, at.getZ(), 7, true, "Foreign stale");
                fixture.index().remove(id);
                fixture.restore(foreign);
                helper.assertTrue(BeaconWorld.stale(fixture.server, foreign), "The foreign fixture position is not stale");
                fixture.use(owner, at, ItemStack.EMPTY);
                helper.assertTrue(copy.status() == BeaconStatus.DUPLICATE
                        && fixture.index().get(id).equals(Optional.of(foreign)), "A different owner took over a stale record");
                fixture.succeed();
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }
}
