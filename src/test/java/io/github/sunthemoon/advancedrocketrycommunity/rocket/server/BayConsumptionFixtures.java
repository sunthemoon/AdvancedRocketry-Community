package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** W170 A0 fixtures: a real snapshot with up to eight bays holding packages, its transfer record and receipts. */
final class BayConsumptionFixtures {
    static final UUID LOGICAL = new UUID(170, 1), OWNER = new UUID(170, 2), SOURCE = new UUID(170, 3), TRANSFER = new UUID(170, 4);
    static final RocketPosition EARTH = new RocketPosition(8, 80, 8), MOON = new RocketPosition(72, 80, 8);
    private BayConsumptionFixtures() { }

    static RocketPosition bay(int i) { return new RocketPosition(2, i, 0); }

    static CompoundTag pkg(int i) {
        var pkg = new CompoundTag();
        pkg.putString("id", "advancedrocketrycommunity:data_satellite_package"); pkg.putByte("Count", (byte) 1);
        var tag = new CompoundTag(); tag.putInt("w170", i); pkg.put("tag", tag);
        return pkg;
    }

    /** Four structural blocks and {@code bays} satellite bays, each holding its own package. */
    static RocketStructureSnapshot source(int bays) {
        ResourceLocation iron = ResourceLocation.tryParse("minecraft:iron_block");
        ResourceLocation bayBlock = ModIdentity.id("rocket_satellite_bay");
        var blocks = new ArrayList<RocketBlock>(List.of(
                new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 2, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(1, 1, 0), new RocketBlockState(iron, Map.of()))));
        for (int i = 0; i < bays; i++) {
            blocks.add(new RocketBlock(bay(i), new RocketBlockState(bayBlock, Map.of()), new RocketBlockEntityPayload(
                    SatelliteBayRocketAdapter.ID, SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(OWNER, pkg(i), null)))));
        }
        return RocketStructureSnapshot.create(new UUID(170, 10), RocketFlightPlanner.EARTH.dimensionId(), EARTH, blocks,
                List.of(new RocketPosition(1, 1, 0)), new RocketStats(4 + bays, 200L, 1_000L, 1_000L, 1, 1, 1, bays), 0L);
    }

    /** PREPARED T for {@link #source}, Earth to Moon, ungoverned. */
    static RocketTransferRecord record(int bays) {
        var source = source(bays);
        var destination = source.relocated(new UUID(170, 11), RocketFlightPlanner.MOON.dimensionId(), MOON, 160L);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        var plan = RocketFlightPlanner.plan(source.stats(), fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, TRANSFER, 0L).plan();
        var transit = RocketFlightData.initial(LOGICAL, source.stats().fuelCapacity(), source.stats().seatCount(),
                RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(), EARTH, 0L)
                .withFuel(fuel, 0L).withPassengers(RocketPassengerManifest.empty(1)).withPlan(plan).startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(TRANSFER, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        var arrival = transit.arriveAtDestination(transit.fuel().debit(plan).state(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), MOON, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS
                        + RocketFlightLimits.TRANSIT_TICKS);
        return RocketTransferRecord.create(TRANSFER, LOGICAL, OWNER, SOURCE, source, destination, transit, arrival, plan.requiredFuel(), 0L);
    }

    /** The INTENT receipt of bay {@code i} of {@code record}. */
    static BayReceipt intent(RocketTransferRecord record, int i) {
        return new BayReceipt(new UUID(171, i), i + 1, LOGICAL, bay(i), new UUID(172, i), OWNER, ModIdentity.id("data_satellite").toString(),
                SatelliteKind.DATA, ModIdentity.id("earth").toString(), RocketNativeResources.signature(pkg(i)), record.transferId(),
                record.sourceSnapshot().contentHash(), null, record.destinationSnapshot().contentHash(), null, BayReceipt.Phase.INTENT,
                "NONE", false, false, 0, 1, 1, 0);
    }

    /** The same receipt REGISTERED with the group's after hashes. */
    static BayReceipt registered(BayReceipt intent, String srcAfter, String dstAfter) {
        return new BayReceipt(intent.op(), intent.seq(), intent.rocket(), intent.bay(), intent.satellite(), intent.owner(),
                intent.definition(), intent.kind(), intent.orbit(), intent.packageSig(), intent.transfer(), intent.srcBefore(), srcAfter,
                intent.dstBefore(), dstAfter, BayReceipt.Phase.REGISTERED, "NONE", false, false, 3, 1, 2, 0);
    }
}
