package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Repeated live native-incompatibility experiments; never an online repair or a persisted-invalid-root claim. */
final class EscrowLoaderS6GuardProbe {
    static final String VETO = "LF_S6_G1_VETO", SATURATION = "LF_S6_G1_SATURATION";
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64);
    private static final BlockPos PROTECTED = new BlockPos(67, 100, 64), CONTROL = new BlockPos(338, 100, 64);
    private final MinecraftServer server;
    private final String cut;
    private final int round, count;
    private final Area first, control;
    private final List<Area> areas = new ArrayList<>();
    private final List<FluidFuelLoaderBlockEntity> loaders = new ArrayList<>();
    private final List<CompoundTag> injected = new ArrayList<>();
    private RocketEntity rocket;
    private int phase, cursor, ticks;
    private boolean done;

    static boolean selected(boolean hooks, String cut) { return hooks && (VETO.equals(cut) || SATURATION.equals(cut)); }
    static BlockPos position(int index) {
        EscrowTags.require(index >= 0 && index < 257, "Guard fixture coordinate exceeds its fixed bound");
        return new BlockPos((4 + index % 16) * 16 + 2, 100, (4 + index / 16) * 16);
    }
    static CompoundTag incompatible(CompoundTag original) {
        var raw = original.copy();
        raw.putInt("schema_version", 2);
        raw.put("w211_nan", FloatTag.valueOf(Float.intBitsToFloat(0x7fc00001)));
        return raw;
    }

    EscrowLoaderS6GuardProbe(MinecraftServer server, String cut) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                && server.isDedicatedServer() && server.getPlayerList().getPlayerCount() == 0
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "Guard experiment requires its exact isolated release gate");
        this.server = server; this.cut = cut;
        round = Integer.parseInt(System.getProperty("advancedrocketrycommunity.escrowS6G1Round"));
        EscrowTags.require(round >= 0 && round <= 2, "Invalid guard restart ordinal");
        count = SATURATION.equals(cut) ? 257 : 1;
        first = new Area(server.overworld(), 4, 4); first.hold();
        control = new Area(server.overworld(), 21, 4); control.hold();
    }

    void tick() {
        if (done) { return; }
        EscrowTags.require(++ticks <= 2_400, "S6 guard exceeded the existing fixture bound");
        var level = server.overworld();
        if (!first.ticking() || !control.ticking()) { return; }
        EscrowTags.require(ResourceEscrowSavedData.existing(server) == null, "Guard fixture admitted escrow");
        if (rocket == null) {
            var existing = level.getEntitiesOfClass(RocketEntity.class, new AABB(ORIGIN).inflate(8));
            if (round == 0) {
                EscrowTags.require(existing.isEmpty(), "Guard fixture needs a fresh world");
                rocket = rocket(level, ORIGIN, UUID.randomUUID(), mono(2_000));
                EscrowTags.require(level.addFreshEntity(rocket), "Guard control rocket publication failed");
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.setDayTime(0);
                level.setBlockAndUpdate(PROTECTED, Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(CONTROL, Blocks.STONE.defaultBlockState());
            } else {
                EscrowTags.require(existing.size() == 1, "Guard restart lost or duplicated its control rocket"); rocket = existing.get(0);
                EscrowTags.require(level.getDayTime() == round * 10_000L
                        && rocket.getName().getString().equals("W211_G1_" + round), "Independent stores did not persist the previous counter");
            }
        }
        if (phase == 0) {
            var at = position(cursor);
            if (areas.size() == cursor) {
                var area = new Area(level, at.getX() >> 4, at.getZ() >> 4); area.hold(); areas.add(area); return;
            }
            if (!areas.get(cursor).ticking()) { return; }
            var loader = round == 0 ? loader(level, at, rocket.ownerId().orElseThrow(), tank(ROCKET_FUEL, 5_000), binding(rocket))
                    : (FluidFuelLoaderBlockEntity) level.getBlockEntity(at);
            EscrowTags.require(loader != null && loader.root().isPresent() && amount(loader) == 5_000
                    && section(loader).epoch() == 0 && section(loader).markers().isEmpty()
                    && loader.root().orElseThrow().binding().equals(binding(rocket)), "Guard restart changed its baseline loader");
            loaders.add(loader);
            if (++cursor < count) { return; }
            if (round == 0) { EscrowTags.require(server.saveEverything(false, true, true), "Guard setup baseline refused"); }
            phase = 1; cursor = 0; return;
        }
        var guard = level.getCapability(GuardedChunkSaves.CAPABILITY).resolve().orElseThrow();
        if (phase == 1) {
            var loader = loaders.get(cursor);
            var raw = incompatible(loader.root().orElseThrow().encode());
            injected.add(raw.copy());
            var outer = new CompoundTag(); outer.put(LoaderRoot.KEY, raw); loader.load(outer);
            EscrowTags.require(loader.unkeepable() && raw.equals(loader.saveWithoutMetadata().get(LoaderRoot.KEY))
                    && Float.floatToRawIntBits(loader.saveWithoutMetadata().getCompound(LoaderRoot.KEY).getFloat("w211_nan")) == 0x7fc00001,
                    "Guard changed its unsupported live root");
            cursor++;
            EscrowTags.require(guard.size() == Math.min(cursor, 256), "Guard denial count differs from distinct real loaders");
            if (cursor == 256) {
                EscrowTags.require(guard.reason(new ChunkPos(CONTROL).toLong()).isEmpty(), "Guard saturated before 257th denial");
                loaders.get(0).onLoad();
                EscrowTags.require(guard.size() == 256 && guard.reason(new ChunkPos(CONTROL).toLong()).isEmpty(), "Duplicate consumed capacity");
                AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_G1_CAPACITY entries=256 duplicate_stable=true unrelated_allowed=true");
            }
            if (cursor < count) { return; }
            phase = 2; return;
        }
        EscrowTags.require(guard.reason(new ChunkPos(PROTECTED).toLong()).orElseThrow().equals(FluidFuelLoaderBlockEntity.SAVE_REFUSAL)
                && guard.reason(new ChunkPos(CONTROL).toLong()).isPresent() == SATURATION.equals(cut), "Guard scope changed");
        EscrowTags.require(bank(rocket, false) == 0 && bank(rocket, true) == 0 && rocket.escrowSection().markers().isEmpty(),
                "Guard fixture changed rocket resources");
        var changed = round % 2 == 0 ? Blocks.GOLD_BLOCK : Blocks.DIAMOND_BLOCK;
        level.setBlockAndUpdate(PROTECTED, changed.defaultBlockState());
        level.setBlockAndUpdate(CONTROL, changed.defaultBlockState());
        rocket.setCustomName(Component.literal("W211_G1_" + (round + 1)));
        level.setDayTime((round + 1) * 10_000L);
        // This flushed save is the experiment's subject. Its terrain refusals must not be called a successful save.
        server.saveEverything(false, true, true);
        for (int index = 0; index < count; index++) {
            var raw = loaders.get(index).saveWithoutMetadata().getCompound(LoaderRoot.KEY);
            EscrowTags.require(injected.get(index).equals(raw) && Float.floatToRawIntBits(raw.getFloat("w211_nan")) == 0x7fc00001,
                    "Native save changed an unsupported live root");
        }
        EscrowTags.require(level.getChunkAt(PROTECTED).isUnsaved()
                && (!SATURATION.equals(cut) || level.getChunkAt(CONTROL).isUnsaved()), "Refusal did not retain dirty terrain");
        done = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_G1_RESULT cut={} round={} loaders={} denials={} scope={} retained_bits=7fc00001 save_attempted=true",
                cut, round, count, guard.size(), SATURATION.equals(cut) ? "LEVEL" : "CHUNK");
    }
}
