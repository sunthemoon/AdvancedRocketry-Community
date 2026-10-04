package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalViews;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * ADR-051 A1 in the resource batch (a fresh, batch-owned registry whose logical clock the tests advance): survey →
 * instances → asteroid mission → claim → withdraw → acknowledgement; a gas mission on the discovered gas giant;
 * cancel and recycle; wrong-terminal, buffer-full and receipts-full refusals; more than 256 claims at one terminal.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResourceMissionGameTests {
    private static final String KEY = "SatelliteTerminal";
    private static final ResourceLocation MINER = ModIdentity.id("asteroid_miner");
    private static final ResourceLocation HARVESTER = ModIdentity.id("gas_harvester");
    private static final ResourceLocation SURVEYOR = ModIdentity.id("survey_satellite");

    private ResourceMissionGameTests() {
    }

    @GameTest(template = "empty", batch = SatelliteRegistryFixture.RESOURCE_BATCH, timeoutTicks = 400)
    public static void surveyAsteroidClaimWithdrawAndAcknowledge(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        // GameTest structures force their own chunks; resource missions must not add any (ADR-051).
        java.util.Set<Long> forced = java.util.Set.copyOf(helper.getLevel().getForcedChunks());
        SatelliteTerminalBlockEntity terminal = place(helper, new BlockPos(1, 2, 1));
        FakePlayer owner = player(helper, terminal);
        IItemHandler slots = terminal.menuInventory();
        UUID survey = craft(data, owner.getUUID(), SatelliteKind.SURVEY, SURVEYOR, CelestialIds.EARTH_ID, server);
        chip(helper, slots, identity(survey, owner.getUUID(), SatelliteKind.SURVEY, SURVEYOR));

        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        MissionState surveying = current(data, survey);
        List<UUID> found = ((MissionPayload.Survey) surveying.payload()).instances();
        helper.assertTrue(surveying.kind() == MissionKind.SURVEY && found.size() == 2
                        && found.stream().allMatch(id -> data.instance(id).orElseThrow().state() == InstanceState.PENDING),
                "A survey start did not create two hidden instances");
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.NOT_READY);
        advance(data, 6_000L);
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.SUCCESS);
        helper.assertTrue(found.stream().allMatch(id -> data.instance(id).orElseThrow().state() == InstanceState.AVAILABLE),
                "A survey claim did not make its instances AVAILABLE");
        // ADR-052 A1: the live survey (server seed, current tables) recomputes exactly under survey-v1.
        helper.assertTrue(server.getCommands().performPrefixedCommand(owner.createCommandSourceStack().withPermission(2)
                .withSuppressedOutput(), "arce satellite admin mission verify " + surveying.missionId()) == 1,
                "mission verify did not report MATCH for the survey");

        UUID miner = craft(data, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER, CelestialIds.EARTH_ID, server);
        chip(helper, slots, identity(miner, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER));
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.AWAITING_WORLD_SAVE);
        // ADR-051 section 5: the real chunk save path marks the terminal ID persisted.
        helper.getLevel().getChunkSource().save(true);
        helper.assertTrue(terminal.terminalIdPersisted(), "A chunk save did not mark the terminal ID persisted");
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_OPTION_NEXT, SatelliteOperationCode.SUCCESS);
        SatelliteTerminalViewPacket view = SatelliteTerminalViews.compose(terminal, 5, server);
        helper.assertTrue(view.instance().size() == 2 && view.instance().position() == 1
                        && view.instanceView().isPresent(), "The view does not show the second selected instance");
        UUID chosen = view.instanceView().orElseThrow().instanceId();

        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        MissionState mining = current(data, miner);
        helper.assertTrue(mining.instanceId().orElseThrow().equals(chosen)
                        && data.instance(chosen).orElseThrow().state() == InstanceState.ALLOCATED
                        && ((MissionPayload.Resource) mining.payload()).boundTerminal().equals(terminal.terminalId()),
                "The asteroid mission did not bind this terminal and allocate the selected instance");
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.NOT_READY);
        advance(data, 72_000L);
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.AWAITING_WORLD_SAVE);
        data.flush(server);
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.SUCCESS);
        int reward = ((MissionPayload.Resource) data.mission(mining.missionId()).orElseThrow().payload()).reward()
                .stream().mapToInt(entry -> entry.count()).sum();
        helper.assertTrue(bufferItems(terminal) == reward && receipts(terminal).equals(List.of(mining.missionId()))
                        && data.instance(chosen).orElseThrow().state() == InstanceState.DEPLETED,
                "A claim did not deliver the whole reward with one receipt and deplete the instance");
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.MISSION_NOT_FOUND);

        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_WITHDRAW, SatelliteOperationCode.SUCCESS);
        int withdrawn = owner.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(withdrawn > 0 && withdrawn <= 64 && bufferItems(terminal) == reward - withdrawn,
                "A withdrawal did not move one stack out of the buffer");
        view = SatelliteTerminalViews.compose(terminal, 5, server);
        helper.assertTrue(view.missions().size() == 1 && view.missions().get(0).status() == MissionStatus.CLAIMED
                        && view.buffer().stream().mapToInt(entry -> entry.count()).sum() == reward - withdrawn,
                "The view does not show the bound mission and the buffer");

        // Receipt life: a chunk save persists it, the next pass acknowledges it, a flush makes that durable, and
        // the pass after that drops it. The terminal and the coalesced flush run on their own schedule.
        helper.getLevel().getChunkSource().save(true);
        helper.succeedWhen(() -> {
            MissionPayload.Resource resource = (MissionPayload.Resource) data.mission(mining.missionId()).orElseThrow()
                    .payload();
            helper.assertTrue(resource.acknowledged(), "The persisted receipt was not acknowledged yet");
            helper.assertTrue(receipts(terminal).isEmpty(), "The durable acknowledgement did not drop the receipt");
            helper.assertTrue(forced.equals(java.util.Set.copyOf(helper.getLevel().getForcedChunks())),
                    "A resource mission forced a chunk");
        });
    }

    @GameTest(template = "empty", batch = SatelliteRegistryFixture.RESOURCE_BATCH, timeoutTicks = 60)
    public static void gasHarvestNeedsTheDiscoveredGasGiant(GameTestHelper helper) {
        gasHarvest(helper, false);
    }

    @GameTest(template = "empty", batch = SatelliteRegistryFixture.RESOURCE_BATCH, timeoutTicks = 60)
    public static void nitrogenUsesTheExistingDiscoveredGasGiantMissionRules(GameTestHelper helper) {
        gasHarvest(helper, true);
    }

    private static void gasHarvest(GameTestHelper helper, boolean nitrogenSelected) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteTerminalBlockEntity terminal = place(helper, new BlockPos(1, 2, 1));
        FakePlayer owner = player(helper, terminal);
        UUID harvester = craft(data, owner.getUUID(), SatelliteKind.GAS_HARVESTER, HARVESTER, PlanetaryContent.GAS_GIANT,
                server);
        chip(helper, terminal.menuInventory(), identity(harvester, owner.getUUID(), SatelliteKind.GAS_HARVESTER, HARVESTER));
        persist(helper, terminal);
        if (CelestialSavedData.get(server).get(PlanetaryContent.GAS_GIANT).isEmpty()) {
            act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.TARGET_NOT_ALLOWED);
            CelestialSavedData.get(server).discover(PlanetaryContent.GAS_GIANT, server.overworld().getGameTime());
        }
        SatelliteTerminalViewPacket view = SatelliteTerminalViews.compose(terminal, 5, server);
        ResourceLocation hydrogen = ForgeRegistries.ITEMS.getKey(ModItems.HYDROGEN_CANISTER.get());
        helper.assertTrue(view.product().id().equals(java.util.Optional.of(hydrogen)) && view.product().size() == 2,
                "The view does not preserve hydrogen as first of the two gas giant products");
        ResourceLocation selected = hydrogen;
        if (nitrogenSelected) {
            act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_OPTION_NEXT, SatelliteOperationCode.SUCCESS);
            selected = ForgeRegistries.ITEMS.getKey(io.github.sunthemoon.advancedrocketrycommunity.fluid
                    .ClassicFluids.NITROGEN_CANISTER.get());
            view = SatelliteTerminalViews.compose(terminal, 5, server);
            helper.assertTrue(view.product().id().equals(java.util.Optional.of(selected)) && view.product().size() == 2,
                    "Nitrogen is not selectable through the existing gas-product control");
        }
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        MissionState harvesting = current(data, harvester);
        MissionPayload.Resource resource = (MissionPayload.Resource) harvesting.payload();
        // gas-v1 with 8 per 1,000 ticks, rating 10 and cargo 9: 576 canisters over 72,000 ticks (examples.json).
        helper.assertTrue(harvesting.kind() == MissionKind.GAS && harvesting.targetBodyId().equals(PlanetaryContent.GAS_GIANT)
                        && resource.reward().equals(List.of(new io.github.sunthemoon.advancedrocketrycommunity.satellite
                          .mission.RewardEntry(selected, 576)))
                        && harvesting.completesAtLogicalTime() - harvesting.startedAtLogicalTime() == 72_000L
                        && harvesting.rewardVersion().startsWith("gas-v1/"),
                "The gas mission does not match gas-v1");
        advance(data, 72_000L);
        data.flush(server);
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.SUCCESS);
        helper.assertTrue(bufferItems(terminal) == 576, "The gas reward was not delivered whole");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = SatelliteRegistryFixture.RESOURCE_BATCH, timeoutTicks = 60)
    public static void cancelRecyclesAtTheBoundTerminalOnly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteTerminalBlockEntity bound = place(helper, new BlockPos(1, 2, 1));
        SatelliteTerminalBlockEntity other = place(helper, new BlockPos(3, 2, 1));
        FakePlayer owner = player(helper, bound);
        UUID miner = craft(data, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER, CelestialIds.EARTH_ID, server);
        SatelliteIdentity minerChip = identity(miner, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER);
        surveyed(helper, data, owner, bound, server);
        chip(helper, bound.menuInventory(), minerChip);
        chip(helper, other.menuInventory(), minerChip);
        persist(helper, bound);
        persist(helper, other);

        act(helper, bound, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        UUID first = current(data, miner).missionId();
        AsteroidInstance instance = data.instance(current(data, miner).instanceId().orElseThrow()).orElseThrow();
        act(helper, other, owner, SatelliteTerminalMenu.BUTTON_CANCEL, SatelliteOperationCode.WRONG_TERMINAL);
        advance(data, 72_000L);
        data.flush(server);
        act(helper, other, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.WRONG_TERMINAL);
        helper.assertTrue(bufferItems(other) == 0, "A wrong-terminal claim delivered items");
        act(helper, bound, owner, SatelliteTerminalMenu.BUTTON_CANCEL, SatelliteOperationCode.SUCCESS);
        helper.assertTrue(data.mission(first).orElseThrow().status() == MissionStatus.CANCELLED
                        && data.instance(instance.instanceId()).orElseThrow().state() == InstanceState.AVAILABLE
                        && bufferItems(bound) == 0,
                "An owner cancel at the bound terminal did not return the instance without a reward");

        // Select the returned instance (the server keeps the selection) and mine it again.
        for (int step = 0; step < 2 && !SatelliteTerminalViews.compose(bound, 5, server).instanceView().orElseThrow()
                .instanceId().equals(instance.instanceId()); step++) {
            press(helper, bound, owner, SatelliteTerminalMenu.BUTTON_OPTION_NEXT, SatelliteOperationCode.SUCCESS);
        }
        act(helper, bound, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        UUID second = current(data, miner).missionId();
        helper.assertTrue(!second.equals(first) && current(data, miner).instanceId().orElseThrow()
                        .equals(instance.instanceId())
                        && data.instance(instance.instanceId()).orElseThrow().state() == InstanceState.ALLOCATED,
                "The returned instance could not be mined again");
        int code = server.getCommands().performPrefixedCommand(owner.createCommandSourceStack().withPermission(2)
                .withSuppressedOutput(), "arce satellite admin mission cancel " + second);
        helper.assertTrue(code == 1 && data.instance(instance.instanceId()).orElseThrow().state()
                == InstanceState.QUARANTINED, "An operator cancel did not hold the instance");
        code = server.getCommands().performPrefixedCommand(owner.createCommandSourceStack().withPermission(2)
                .withSuppressedOutput(), "arce satellite admin instance release " + instance.instanceId());
        helper.assertTrue(code == 1 && data.instance(instance.instanceId()).orElseThrow().state()
                == InstanceState.AVAILABLE, "instance release did not return the held instance");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = SatelliteRegistryFixture.RESOURCE_BATCH, timeoutTicks = 60)
    public static void aFullBufferRefusesTheWholeClaim(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteTerminalBlockEntity terminal = place(helper, new BlockPos(1, 2, 1));
        FakePlayer owner = player(helper, terminal);
        UUID miner = craft(data, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER, CelestialIds.EARTH_ID, server);
        surveyed(helper, data, owner, terminal, server);
        chip(helper, terminal.menuInventory(), identity(miner, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER));
        persist(helper, terminal);
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        UUID mission = current(data, miner).missionId();
        advance(data, 72_000L);
        data.flush(server);

        // 32 distinct entries: a reward with any new item is refused whole.
        CompoundTag root = terminal.saveWithoutMetadata();
        ListTag full = new ListTag();
        for (int index = 0; index < 32; index++) {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", "advancedrocketrycommunity:filler_" + index);
            entry.putInt("count", 1);
            full.add(entry);
        }
        root.getCompound(KEY).put("reward_buffer", full);
        terminal.load(root);
        persist(helper, terminal);
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.DELIVERY_BUFFER_FULL);
        helper.assertTrue(data.mission(mission).orElseThrow().status() == MissionStatus.READY && bufferItems(terminal) == 32
                && receipts(terminal).isEmpty(), "A refused claim changed the mission or the terminal");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = SatelliteRegistryFixture.RESOURCE_BATCH, timeoutTicks = 400)
    public static void moreThan256ClaimsAtOneTerminalReleaseReceiptsByAcknowledgement(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteTerminalBlockEntity terminal = place(helper, new BlockPos(1, 2, 1));
        FakePlayer owner = player(helper, terminal);
        persist(helper, terminal);
        List<UUID> surveys = new java.util.ArrayList<>();
        List<UUID> miners = new java.util.ArrayList<>();
        for (int index = 0; index < 16; index++) {
            if (index < 8) {
                surveys.add(craft(data, owner.getUUID(), SatelliteKind.SURVEY, SURVEYOR, CelestialIds.EARTH_ID, server));
            }
            miners.add(craft(data, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER, CelestialIds.EARTH_ID, server));
        }
        int[] claims = {0};
        for (int round = 0; round < 17; round++) {
            // Eight surveys make the owner's sixteen live instances; sixteen miners take them.
            for (UUID survey : surveys) {
                chip(helper, terminal.menuInventory(), identity(survey, owner.getUUID(), SatelliteKind.SURVEY, SURVEYOR));
                act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
            }
            advance(data, 6_000L);
            for (UUID survey : surveys) {
                chip(helper, terminal.menuInventory(), identity(survey, owner.getUUID(), SatelliteKind.SURVEY, SURVEYOR));
                act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.SUCCESS);
            }
            for (UUID miner : miners) {
                chip(helper, terminal.menuInventory(), identity(miner, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER));
                act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
            }
            advance(data, 72_000L);
            data.flush(server);
            if (round == 16) {
                break;
            }
            for (int index = 0; index < miners.size(); index++) {
                chip(helper, terminal.menuInventory(), identity(miners.get(index), owner.getUUID(),
                        SatelliteKind.ASTEROID_MINER, MINER));
                if (round == 5 && index == 0) {
                    // C9-H1: with 80 receipts pending (more than one 64-mission pass), a claim through the menu, with
                    // its real intent limiter, succeeds at the first press.
                    menuPress(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.SUCCESS);
                } else {
                    act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.SUCCESS);
                }
                drain(helper, terminal, owner);
                claims[0]++;
            }
        }
        // 256 receipts wait for a chunk save: the next claim is refused whole.
        helper.assertTrue(receipts(terminal).size() == 256, "The terminal does not hold 256 receipts");
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.TERMINAL_RECEIPTS_FULL);
        // The receipts are released by durable acknowledgement, not by pruning, on the production schedule: the
        // terminal's periodic passes acknowledge them, the coalesced flush makes that durable, the next passes drop them.
        observeChunkSave(helper, terminal);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(receipts(terminal).isEmpty(), "Receipts not yet released"))
                .thenExecute(() -> {
                    for (UUID miner : miners) {
                        chip(helper, terminal.menuInventory(), identity(miner, owner.getUUID(),
                                SatelliteKind.ASTEROID_MINER, MINER));
                        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.SUCCESS);
                        drain(helper, terminal, owner);
                        claims[0]++;
                    }
                    long finished = data.missions().stream().filter(mission -> !mission.status().unfinished()).count();
                    helper.assertTrue(claims[0] == 272 && receipts(terminal).size() == 16 && finished < 1_536,
                            "More than 256 claims at one terminal did not complete below the pruning threshold");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = SatelliteRegistryFixture.RESOURCE_BATCH, timeoutTicks = 60)
    public static void aBrokenTerminalCarriesItsDeliveryToItsNewPlace(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteTerminalBlockEntity terminal = place(helper, new BlockPos(1, 2, 1));
        FakePlayer owner = player(helper, terminal);
        terminal.setOwner(owner.getUUID());
        UUID paid = craft(data, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER, CelestialIds.EARTH_ID, server);
        UUID waiting = craft(data, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER, CelestialIds.EARTH_ID, server);
        surveyed(helper, data, owner, terminal, server);
        persist(helper, terminal);
        for (UUID miner : List.of(paid, waiting)) {
            chip(helper, terminal.menuInventory(), identity(miner, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER));
            act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        }
        UUID waitingMission = current(data, waiting).missionId();
        advance(data, 72_000L);
        data.flush(server);
        chip(helper, terminal.menuInventory(), identity(paid, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER));
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.SUCCESS);
        UUID terminalId = terminal.terminalId();
        int buffered = bufferItems(terminal);
        List<UUID> held = receipts(terminal);

        // C9-H2 (ADR-051 section 5): breaking the terminal carries its ID, buffer, receipts and owner; the slots drop.
        BlockPos from = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().destroyBlock(from, true);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(from).inflate(2.0D));
        ItemStack carried = drops.stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(ModBlocks.SATELLITE_TERMINAL.get().asItem())).findFirst().orElseThrow();
        helper.assertTrue(drops.stream().anyMatch(entity -> entity.getItem().is(ModItems.SATELLITE_CONTROL_CHIP.get())),
                "The chip in the slot did not drop as an item");
        BlockPos next = new BlockPos(4, 2, 1);
        helper.setBlock(next, ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState()
                .setValue(SatelliteTerminalBlock.FACING, Direction.NORTH).setValue(SatelliteTerminalBlock.LIT, false));
        ModBlocks.SATELLITE_TERMINAL.get().setPlacedBy(helper.getLevel(), helper.absolutePos(next),
                helper.getLevel().getBlockState(helper.absolutePos(next)), owner, carried.copy());
        SatelliteTerminalBlockEntity placed = (SatelliteTerminalBlockEntity) helper.getBlockEntity(next);
        helper.assertTrue(placed.terminalId().equals(terminalId) && bufferItems(placed) == buffered
                        && receipts(placed).equals(held) && !placed.terminalIdPersisted()
                        && placed.menuInventory().getStackInSlot(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP).isEmpty(),
                "The placed terminal did not carry its delivery section (unpersisted) without copying its slots");
        // The mission still bound to this ID is claimed at the terminal's new place.
        persist(helper, placed);
        chip(helper, placed.menuInventory(), identity(waiting, owner.getUUID(), SatelliteKind.ASTEROID_MINER, MINER));
        act(helper, placed, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.SUCCESS);
        helper.assertTrue(receipts(placed).contains(waitingMission) && bufferItems(placed) > buffered,
                "The carried terminal could not deliver its bound mission");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = SatelliteRegistryFixture.RESOURCE_BATCH, timeoutTicks = 20)
    public static void terminalRootsUpgradeToSchemaTwoAndQuarantineABadDeliverySection(GameTestHelper helper) {
        SatelliteTerminalBlockEntity terminal = place(helper, new BlockPos(1, 2, 1));
        CompoundTag root = terminal.saveWithoutMetadata();
        CompoundTag legacy = root.getCompound(KEY);
        legacy.putInt("schema_version", 1);
        legacy.remove("terminal_id");
        legacy.remove("reward_buffer");
        legacy.remove("receipts");
        terminal.load(root);
        CompoundTag upgraded = terminal.saveWithoutMetadata().getCompound(KEY);
        helper.assertTrue(upgraded.getInt("schema_version") == 2 && upgraded.hasUUID("terminal_id")
                        && upgraded.getList("reward_buffer", Tag.TAG_COMPOUND).isEmpty()
                        && upgraded.getList("receipts", Tag.TAG_INT_ARRAY).isEmpty() && !terminal.terminalIdPersisted(),
                "A schema-1 root did not load with a new, unpersisted ID and empty sections");

        CompoundTag bad = upgraded.copy();
        ListTag receipts = new ListTag();
        for (int index = 0; index <= 256; index++) {
            receipts.add(NbtUtils.createUUID(new UUID(9L, index)));
        }
        bad.put("receipts", receipts);
        CompoundTag badRoot = root.copy();
        badRoot.put(KEY, bad);
        terminal.load(badRoot);
        CompoundTag preserved = terminal.saveWithoutMetadata().getCompound(KEY);
        helper.assertTrue(preserved.equals(bad), "A quarantined terminal root was not preserved exactly");
        helper.assertTrue(terminal.menuInventory().insertItem(SatelliteTerminalBlockEntity.SLOT_CHARGE,
                new ItemStack(net.minecraft.world.item.Items.REDSTONE), true).getCount() == 1,
                "A quarantined terminal accepted items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = SatelliteRegistryFixture.BLOCKED_BATCH, timeoutTicks = 40)
    public static void aBlockedRegistryRefusesResourceActionsAndKeepsReceipts(GameTestHelper helper) {
        SatelliteTerminalBlockEntity terminal = place(helper, new BlockPos(1, 2, 1));
        FakePlayer owner = player(helper, terminal);
        chip(helper, terminal.menuInventory(), identity(UUID.randomUUID(), owner.getUUID(), SatelliteKind.ASTEROID_MINER,
                MINER));
        CompoundTag root = terminal.saveWithoutMetadata();
        ListTag held = new ListTag();
        held.add(NbtUtils.createUUID(new UUID(11L, 11L)));
        root.getCompound(KEY).put("receipts", held);
        terminal.load(root);
        persist(helper, terminal);
        for (int button : new int[]{SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteTerminalMenu.BUTTON_CLAIM,
                SatelliteTerminalMenu.BUTTON_CANCEL}) {
            act(helper, terminal, owner, button, SatelliteOperationCode.UNSUPPORTED_DATA);
        }
        // The terminal keeps reconciling on its own schedule; a blocked registry never drops a receipt.
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(receipts(terminal).equals(List.of(new UUID(11L, 11L))),
                    "A blocked registry released a receipt");
            helper.succeed();
        });
    }

    private static List<AsteroidInstance> surveyed(GameTestHelper helper, SatelliteMissionSavedData data, FakePlayer owner,
                                                   SatelliteTerminalBlockEntity terminal, MinecraftServer server) {
        UUID survey = craft(data, owner.getUUID(), SatelliteKind.SURVEY, SURVEYOR, CelestialIds.EARTH_ID, server);
        chip(helper, terminal.menuInventory(), identity(survey, owner.getUUID(), SatelliteKind.SURVEY, SURVEYOR));
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        List<UUID> found = ((MissionPayload.Survey) current(data, survey).payload()).instances();
        advance(data, 6_000L);
        act(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_CLAIM, SatelliteOperationCode.SUCCESS);
        return found.stream().map(id -> data.instance(id).orElseThrow()).toList();
    }

    /** The chunk save signal without the file write: {@code ChunkSerializer.write}, then the Forge event. */
    private static void observeChunkSave(GameTestHelper helper, SatelliteTerminalBlockEntity terminal) {
        ServerLevel level = helper.getLevel();
        LevelChunk chunk = level.getChunkAt(terminal.getBlockPos());
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, level, ChunkSerializer.write(level, chunk)));
    }

    private static void persist(GameTestHelper helper, SatelliteTerminalBlockEntity terminal) {
        observeChunkSave(helper, terminal);
        helper.assertTrue(terminal.terminalIdPersisted(), "The chunk save signal did not persist the terminal ID");
    }

    /** Advances the batch-owned registry's logical clock by at least {@code ticks} (ADR-010 monotonic clock). */
    private static void advance(SatelliteMissionSavedData data, long ticks) {
        data.completeDue(0L);
        data.completeDue(ticks);
    }

    private static void drain(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, FakePlayer owner) {
        for (int guard = 0; guard < 128 && bufferItems(terminal) > 0; guard++) {
            terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_WITHDRAW);
            owner.getInventory().clearContent();
        }
        helper.assertTrue(bufferItems(terminal) == 0, "The reward buffer could not be withdrawn");
    }

    /** One press through the real menu: its validity check and the per-player intent limiter (C9-L8(a)). */
    private static void menuPress(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, FakePlayer player,
                                  int button, SatelliteOperationCode expected) {
        SatelliteTerminalMenu menu = new SatelliteTerminalMenu(7, player.getInventory(), terminal,
                io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalTargets.current());
        helper.assertTrue(menu.stillValid(player), "The terminal menu is not valid for the player");
        menu.clickMenuButton(player, button);
        int actual = terminal.saveWithoutMetadata().getCompound(KEY).getInt("last_result");
        helper.assertTrue(actual == expected.ordinal(), "Menu button " + button + ": expected " + expected + " but got "
                + SatelliteOperationCode.values()[actual]);
    }

    /**
     * Presses a resource action directly (bypassing the menu limiter), repeating while the terminal completes its
     * first reconciliation pass after load (ADR-051 section 7).
     */
    private static void act(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, FakePlayer player, int button,
                            SatelliteOperationCode expected) {
        int actual = SatelliteOperationCode.RECONCILING.ordinal();
        for (int attempt = 0; attempt < 16 && actual == SatelliteOperationCode.RECONCILING.ordinal(); attempt++) {
            terminal.handleButton(player, button);
            actual = terminal.saveWithoutMetadata().getCompound(KEY).getInt("last_result");
        }
        helper.assertTrue(actual == expected.ordinal(), "Button " + button + ": expected " + expected + " but got "
                + SatelliteOperationCode.values()[actual]);
    }

    private static MissionState current(SatelliteMissionSavedData data, UUID satellite) {
        return data.mission(data.satellite(satellite).orElseThrow().currentMissionId().orElseThrow()).orElseThrow();
    }

    private static int bufferItems(SatelliteTerminalBlockEntity terminal) {
        ListTag buffer = terminal.saveWithoutMetadata().getCompound(KEY).getList("reward_buffer", Tag.TAG_COMPOUND);
        int total = 0;
        for (int index = 0; index < buffer.size(); index++) {
            total += buffer.getCompound(index).getInt("count");
        }
        return total;
    }

    private static List<UUID> receipts(SatelliteTerminalBlockEntity terminal) {
        return terminal.saveWithoutMetadata().getCompound(KEY).getList("receipts", Tag.TAG_INT_ARRAY).stream()
                .map(NbtUtils::loadUUID).toList();
    }

    private static UUID craft(SatelliteMissionSavedData data, UUID owner, SatelliteKind kind, ResourceLocation definition,
                              ResourceLocation orbit, MinecraftServer server) {
        UUID satelliteId = UUID.randomUUID();
        SatelliteKindState state = kind == SatelliteKind.SURVEY
                ? new SatelliteKindState.Survey(0L, 0L, 1_000, 32, 8) : new SatelliteKindState.Plain(kind);
        SatelliteBlueprint blueprint = new SatelliteBlueprint(components(kind), false,
                new SatelliteStats(4, 10_720, 1_000, 9, 10));
        data.launchIdle(time -> SatelliteState.launchIdle(satelliteId, definition, owner, time, orbit, blueprint, state),
                server.overworld().getGameTime());
        return satelliteId;
    }

    private static SatelliteIdentity identity(UUID satellite, UUID owner, SatelliteKind kind, ResourceLocation definition) {
        return new SatelliteIdentity(satellite, owner, definition, kind, components(kind));
    }

    private static List<ResourceLocation> components(SatelliteKind kind) {
        Item primary = switch (kind) {
            case SURVEY -> ModItems.SURVEY_SCANNER_MODULE.get();
            case ASTEROID_MINER -> ModItems.ASTEROID_DRILL_MODULE.get();
            default -> ModItems.GAS_INTAKE_MODULE.get();
        };
        return List.of(key(ModItems.SATELLITE_CHASSIS.get()), key(primary), key(ModItems.SATELLITE_CARGO_HOLD.get()));
    }

    private static void chip(GameTestHelper helper, IItemHandler slots, SatelliteIdentity identity) {
        slots.extractItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, 64, false);
        ItemStack chip = new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get());
        SatelliteItemData.write(chip, identity);
        helper.assertTrue(slots.insertItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, chip, false).isEmpty(),
                "Could not insert the control chip");
    }

    private static SatelliteTerminalBlockEntity place(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState()
                .setValue(SatelliteTerminalBlock.FACING, Direction.NORTH)
                .setValue(SatelliteTerminalBlock.LIT, false));
        SatelliteTerminalBlockEntity terminal = (SatelliteTerminalBlockEntity) helper.getBlockEntity(position);
        terminal.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow()
                .receiveEnergy(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, false);
        return terminal;
    }

    private static FakePlayer player(GameTestHelper helper, SatelliteTerminalBlockEntity terminal) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ResourceMissions"));
        BlockPos position = terminal.getBlockPos();
        player.setPos(position.getX() + 0.5D, position.getY() + 1.0D, position.getZ() + 0.5D);
        return player;
    }

    private static void press(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, FakePlayer player,
                              int button, SatelliteOperationCode expected) {
        terminal.handleButton(player, button);
        int actual = terminal.saveWithoutMetadata().getCompound(KEY).getInt("last_result");
        helper.assertTrue(actual == expected.ordinal(), "Button " + button + ": expected "
                + expected + " but got " + SatelliteOperationCode.values()[actual]);
    }

    private static ResourceLocation key(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }
}
