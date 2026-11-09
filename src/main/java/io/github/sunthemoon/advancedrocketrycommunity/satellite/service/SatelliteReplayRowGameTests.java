package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteReplayRowFixture.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.SatelliteRegistrySettle;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import java.util.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W333 F2.8/F4/F5.6: service-to-terminal parity, with controls showing each fresh-launch dependency is unavailable. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteReplayRowGameTests {
    private SatelliteReplayRowGameTests() { }

    @GameTest(template = "empty", batch = "w333_replay_rows", timeoutTicks = 400)
    public static void replayBypassesChangedDependenciesAtTerminalAndService(GameTestHelper h) throws Exception {
        try (var f = new SatelliteReplayRowFixture(h)) {
            for (SatelliteKind kind : SatelliteKind.values()) {
                for (String change : List.of("definition", "primary", "component", "stats", "research", "target", "body")) {
                    f.install(SatelliteMissionSavedData.create(0));
                    var identity = f.identity(kind); f.seed(identity); f.catalogs(change);
                    for (var target : Arrays.asList(null, MOON, ModIdentity.id("removed_w333_target"))) { replay(h, f, identity, target); }
                    f.catalogs("normal");
                }
            }
        }
        h.succeed();
    }

    private static void replay(GameTestHelper h, SatelliteReplayRowFixture f, SatelliteIdentity identity, net.minecraft.resources.ResourceLocation target) {
        CompoundTag before = f.data.save(new CompoundTag());
        var validation = f.service.validate(f.server, identity.ownerId(), identity, target);
        h.assertTrue(validation.result().code() == SatelliteOperationCode.IDEMPOTENT
                && validation.request().orElseThrow().branch() == SatelliteLaunchService.Branch.REPLAY, "Matching identity performed fresh admission");
        h.assertTrue(f.service.register(f.server, validation.request().orElseThrow(), 999).code() == SatelliteOperationCode.IDEMPOTENT,
                "Shared replay registration changed branch");
        h.assertTrue(f.data.save(new CompoundTag()).equals(before), "Shared replay mutated mission/account/clock/registry");
        f.terminal(identity);
        f.terminal.handleButton(f.player, SatelliteTerminalMenu.BUTTON_LAUNCH);
        SatelliteRegistrySettle.settle(f.server);
        h.assertTrue(f.result() == SatelliteOperationCode.IDEMPOTENT && f.pkg().isEmpty(), "Terminal did not extract matching replay: " + f.result());
        // Persistence may advance save_epoch, but no domain record may change.
        var after = f.data.save(new CompoundTag()); after.putLong("save_epoch", before.getLong("save_epoch"));
        h.assertTrue(after.equals(before), "Terminal replay changed the satellite, mission, account or logical clock");
    }

    @GameTest(template = "empty", batch = "w333_replay_rows", timeoutTicks = 400)
    public static void replayBypassesFullQuotasAtTerminalAndService(GameTestHelper h) throws Exception {
        try (var f = new SatelliteReplayRowFixture(h)) {
            for (SatelliteKind kind : SatelliteKind.values()) {
                for (int quota = 0; quota < 7; quota++) {
                    f.install(SatelliteMissionSavedData.create(0));
                    var identity = f.identity(kind); f.seed(identity);
                    if (kind != SatelliteKind.DATA) {
                        var extra = new SatelliteIdentity(UUID.randomUUID(), identity.ownerId(), ModIdentity.id("data_satellite")); f.seed(extra);
                    }
                    if (quota < 5) {
                        var d = RegistryLimits.DEFAULTS; int count = f.data.satellites().size();
                        f.data.applyLimits(new RegistryLimits(quota == 0 ? 1 : d.unfinishedGlobal(), quota == 1 ? 1 : d.unfinishedPerOwner(),
                                d.finishedPerOwner(), quota == 2 ? 1 : d.missionsTotal(), quota == 3 ? count : d.satellitesGlobal(),
                                quota == 4 ? count : d.satellitesPerOwner(), d.instancesGlobal(), d.instancesPerOwner(), d.intentTicks(), d.selectionTicks()));
                    } else { pressure(h, f, quota == 5); }
                    var fresh = new SatelliteIdentity(UUID.randomUUID(), quota == 5 ? UUID.randomUUID() : identity.ownerId(), ModIdentity.id("data_satellite"));
                    var expected = quota == 1 || quota == 4 ? SatelliteOperationCode.OWNER_LIMIT : quota == 6
                            ? SatelliteOperationCode.STORAGE_BUDGET : SatelliteOperationCode.CAPACITY_REACHED;
                    h.assertTrue(f.service.validate(f.server, fresh.ownerId(), fresh, EARTH).result().code() == expected,
                            "Quota " + quota + " fresh control did not fill its named budget");
                    replay(h, f, identity, null);
                }
            }
        }
        h.succeed();
    }

    private static void pressure(GameTestHelper h, SatelliteReplayRowFixture f, boolean accountsFull) {
        CompoundTag root = f.data.save(new CompoundTag());
        var accounts = root.getList("research_accounts", Tag.TAG_COMPOUND);
        CompoundTag account = accounts.getCompound(0).copy();
        if (accountsFull) {
            for (int i = accounts.size(); i < SatelliteLimits.MAX_RESEARCH_ACCOUNTS; i++) {
                CompoundTag copy = account.copy(); copy.putUUID("owner_id", new UUID(33340, i)); accounts.add(copy);
            }
        } else {
            var large = SatelliteMissionSavedData.create(0);
            var components = java.util.stream.IntStream.range(0, 8).mapToObj(i -> ModIdentity.id("x".repeat(96) + i)).toList();
            var result = large.launchIdle(time -> SatelliteState.launchIdle(UUID.randomUUID(), ModIdentity.id("solar_satellite"), UUID.randomUUID(), time,
                    EARTH, new SatelliteBlueprint(components, false, new SatelliteStats(4, 10720, 1000, 1, 10)),
                    new SatelliteKindState.Solar(100, Optional.empty())), 0);
            int count = (4 * 1024 * 1024) / CodecRecordSizer.INSTANCE.satelliteBytes(result.satellite().orElseThrow()) + 1;
            h.assertTrue(count < SatelliteLimits.MAX_SATELLITES - 2, "Byte fixture hit count limit instead");
            var template = large.save(new CompoundTag()).getList("satellites", Tag.TAG_COMPOUND).getCompound(0);
            for (int i = 0; i < 32; i++) {
                CompoundTag copy = account.copy(); copy.putUUID("owner_id", new UUID(33341, i)); accounts.add(copy);
            }
            var records = root.getList("satellites", Tag.TAG_COMPOUND);
            for (int i = 0; i < count; i++) {
                CompoundTag copy = template.copy(); copy.putUUID("satellite_id", new UUID(33342, i));
                copy.putUUID("owner_id", new UUID(33341, i % 32)); records.add(copy);
            }
            large.close();
        }
        f.install(SatelliteMissionSavedData.load(root));
        h.assertTrue(f.data.operational() && !f.data.isDirty(), "Pressure fixture did not reload without repair");
    }

    @GameTest(template = "empty", batch = "w333_replay_rows", timeoutTicks = 200)
    public static void identityConflictsPrecedeReplay(GameTestHelper h) throws Exception {
        try (var f = new SatelliteReplayRowFixture(h)) {
            for (SatelliteKind kind : SatelliteKind.values()) {
                f.install(SatelliteMissionSavedData.create(0));
                var identity = f.identity(kind); f.seed(identity);
                var changedKind = kind == SatelliteKind.DATA ? SatelliteKind.SOLAR : SatelliteKind.DATA;
                for (var conflict : List.of(new SatelliteIdentity(identity.satelliteId(), UUID.randomUUID(), identity.definitionId(), kind, identity.components()),
                        new SatelliteIdentity(identity.satelliteId(), identity.ownerId(), ModIdentity.id("changed_w333"), kind, identity.components()),
                        new SatelliteIdentity(identity.satelliteId(), identity.ownerId(), identity.definitionId(), changedKind,
                                changedKind == SatelliteKind.DATA ? List.of() : List.of(ModIdentity.id("a"), ModIdentity.id("b"))))) {
                    refuse(h, f, conflict, conflict.ownerId(), SatelliteOperationCode.IDENTITY_CONFLICT, true);
                }
                refuse(h, f, identity, UUID.randomUUID(), SatelliteOperationCode.UNAUTHORIZED, true);
                var blocked = f.data.save(new CompoundTag()); blocked.putInt("schema_version", 999);
                f.install(SatelliteMissionSavedData.load(blocked));
                refuse(h, f, identity, identity.ownerId(), SatelliteOperationCode.REGISTRY_BLOCKED, true);
            }
        }
        h.succeed();
    }

    private static void refuse(GameTestHelper h, SatelliteReplayRowFixture f, SatelliteIdentity identity, UUID caller,
                               SatelliteOperationCode expected, boolean terminal) {
        var before = f.data.save(new CompoundTag());
        h.assertTrue(f.service.validate(f.server, caller, identity, EARTH).result().code() == expected, "Shared refusal differs from " + expected);
        if (terminal) {
            f.terminal(identity);
            var pkg = f.pkg().copy();
            var actor = caller.equals(identity.ownerId()) ? f.player : new net.minecraftforge.common.util.FakePlayer(h.getLevel(),
                    new com.mojang.authlib.GameProfile(caller, "W333Foreign"));
            actor.setPos(f.player.position());
            f.terminal.handleButton(actor, SatelliteTerminalMenu.BUTTON_LAUNCH);
            h.assertTrue(f.result() == expected && ItemStack.matches(pkg, f.pkg()), "Terminal refusal changed package or code: " + f.result());
        }
        h.assertTrue(f.data.save(new CompoundTag()).equals(before), "Refused replay mutated registry");
    }

    @GameTest(template = "empty", batch = "w333_replay_rows", timeoutTicks = 200)
    public static void undiscoveredAllowedBodyAdmitsDataButNotNonData(GameTestHelper h) throws Exception {
        try (var f = new SatelliteReplayRowFixture(h)) {
            h.assertTrue(f.bodies.get(MARS).filter(d -> d.discoveryRequired() && d.capabilities().orbitable()).isPresent(), "Mars fixture is not gated orbitable");
            f.catalogs("discovery");
            var data = f.identity(SatelliteKind.DATA);
            var validation = f.service.validate(f.server, data.ownerId(), data, MARS);
            h.assertTrue(validation.result().code() == SatelliteOperationCode.SUCCESS, "Undiscovered allowed DATA target refused");
            var result = f.service.register(f.server, validation.request().orElseThrow(), 0);
            h.assertTrue(result.mission().orElseThrow().discoveryRequired() && result.mission().orElseThrow().targetBodyId().equals(MARS),
                    "DATA did not start discovery of captured target");
            for (SatelliteKind kind : SatelliteKind.values()) {
                if (kind == SatelliteKind.DATA) { continue; }
                var nonData = f.identity(kind);
                var before = f.data.save(new CompoundTag());
                h.assertTrue(f.service.validate(f.server, nonData.ownerId(), nonData, MARS).result().code() == SatelliteOperationCode.TARGET_NOT_ALLOWED,
                        "Undiscovered non-DATA target bypassed discovery gate: " + kind);
                h.assertTrue(before.equals(f.data.save(new CompoundTag())), "Refused discovery mutated registry");
            }
            io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData.get(f.server).discover(MARS, 0);
            for (SatelliteKind kind : SatelliteKind.values()) {
                if (kind == SatelliteKind.DATA) { continue; }
                var control = f.identity(kind);
                h.assertTrue(f.service.validate(f.server, control.ownerId(), control, MARS).result().code() == SatelliteOperationCode.RESEARCH_LOCKED,
                        "Discovered control did not reach research after physical/blueprint admission: " + kind);
            }
        }
        h.succeed();
    }
}
