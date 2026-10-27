package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * W129bc B3 (design section 6.1): W128's 16 MiB registry, resident and written through its own off-thread writer on a
 * live-like server, beside the transaction and transfer writers. Each round makes one ordinary state change on the
 * owner thread (an idle satellite registered, then removed in the next round), so every write, including a definite
 * failure, carries content that differs from the file.
 */
public final class SatelliteRegistryOverlapAccess {
    public static final String THREAD = SatelliteRegistryWriter.THREAD;
    private static final UUID OWNER = new UUID(129, 0xB3);
    private static final UUID TOGGLED = new UUID(129, 0xB30);

    private final SatelliteMissionSavedData data;
    private boolean present;
    private long serial = 1;

    private SatelliteRegistryOverlapAccess(SatelliteMissionSavedData data, MinecraftServer server) {
        this.data = data;
        data.bindServerForTests(server);
    }

    /** The 5,226-mission root at 16,692,097 bytes of its 16 MiB bound. */
    public static SatelliteRegistryOverlapAccess resident(MinecraftServer server) {
        return new SatelliteRegistryOverlapAccess(SatelliteRegistryResidentFixture.resident(), server);
    }

    /** A fresh registry, for smoke runs only. */
    public static SatelliteRegistryOverlapAccess small(MinecraftServer server) {
        return new SatelliteRegistryOverlapAccess(SatelliteMissionSavedData.create(0), server);
    }

    public void mutate() {
        var result = present ? data.decommission(TOGGLED, OWNER, true, false)
                : data.launchIdle(time -> idle(time), ++serial);
        if (result.code() != SatelliteOperationCode.SUCCESS) {
            throw new IllegalStateException("The registry round's state change was refused: " + result.code());
        }
        present = !present;
    }

    public boolean request(Path file, CheckedSavedDataPolicy policy) { return data.requestWrite(file, policy); }
    public void poll() { data.pollWriter(); }
    public long completedWrites() { return data.writerForTests().completedWrites(); }
    public double lastWriterMillis() { return data.writerForTests().lastWriterNanos() / 1e6; }
    public double lastWriterCpuMillis() { return data.writerForTests().lastWriterCpuNanos() / 1e6; }
    public String lastOutcome() { return data.writerForTests().lastOutcome().map(Enum::name).orElse("NONE"); }
    public boolean inFlight() { return data.writerInFlight(); }
    public boolean operational() { return data.operational(); }
    public int missions() { return data.missions().size(); }
    public int rootBytes() { return SatelliteNbtSize.uncompressedBytes(data.save(new net.minecraft.nbt.CompoundTag())); }

    /** Published and covering every change, with nothing in flight or pending. */
    public boolean acknowledgedCurrent() {
        return !data.writerBusy() && data.acknowledgement().filter(ack -> ack.sequence() == data.mutationSequence()).isPresent();
    }

    /** The object that holds the registry's {@code CheckedRootWriter} in its {@code writer} field. */
    public Object writerHolder() { return data.writerForTests(); }

    /** Drains and joins the writer before its file goes. */
    public void close() { data.close(); }

    private static SatelliteState idle(long time) {
        var blueprint = new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"), ModIdentity.id("asteroid_drill_module"),
                ModIdentity.id("satellite_cargo_hold")), false, new SatelliteStats(4, 10_720, 1_000, 9, 10));
        return SatelliteState.launchIdle(TOGGLED, ModIdentity.id("asteroid_miner"), OWNER, time, ModIdentity.id("earth"),
                blueprint, new SatelliteKindState.Plain(SatelliteKind.ASTEROID_MINER));
    }
}
