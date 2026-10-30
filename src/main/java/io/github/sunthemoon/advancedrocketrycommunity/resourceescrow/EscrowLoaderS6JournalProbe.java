package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** R200's release-gated, observation-only journal replacement seam. */
public final class EscrowLoaderS6JournalProbe {
    private static final Set<String> CUTS = Set.of("LF_S6_F2_BEFORE", "LF_S6_F2_REPLACED",
            "LF_S6_F8_JOURNAL_BEFORE", "LF_S6_F8_JOURNAL_REPLACED");
    private EscrowLoaderS6JournalProbe() { }

    public static CheckedSavedDataPolicy journalPolicy(MinecraftServer server, CheckedSavedDataPolicy policy) {
        if (server == null) { return policy; }
        return policy(policy, Boolean.getBoolean(EscrowTestEndpoints.HOOKS),
                System.getProperty("advancedrocketrycommunity.escrowScenario", ""),
                System.getProperty("advancedrocketrycommunity.escrowCase", ""),
                () -> EscrowTestEndpoints.enabled(server), () -> EscrowTestEndpoints.requireEnabled(server),
                cut -> pause(cut, "JOURNAL_COMMIT"));
    }

    static CheckedSavedDataPolicy policy(CheckedSavedDataPolicy policy, boolean hooks, String scenario, String cut,
            BooleanSupplier enabled, Runnable requireEnabled, Consumer<String> pause) {
        if (!hooks || !"start".equals(scenario) || cut == null || !CUTS.contains(cut) || !enabled.getAsBoolean()) { return policy; }
        requireEnabled.run();
        return policy.withCommitter((staged, target) -> {
            var candidate = CheckedSavedDataFile.readPayload(staged, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow();
            var previous = CheckedSavedDataFile.readPayload(target, ManagedSavedDataType.ROCKET_TRANSFERS).orElse(null);
            boolean matches = transition(cut, previous, candidate);
            if (matches && cut.endsWith("_BEFORE")) { pause.accept(cut); }
            policy.committer().commit(staged, target);
            if (matches && cut.endsWith("_REPLACED")) { pause.accept(cut); }
        });
    }

    static boolean transition(String cut, CompoundTag previous, CompoundTag candidate) {
        if (previous == null || !previous.contains("transfers", Tag.TAG_LIST)
                || !candidate.contains("transfers", Tag.TAG_LIST)) { return false; }
        var before = previous.getList("transfers", Tag.TAG_COMPOUND);
        var after = candidate.getList("transfers", Tag.TAG_COMPOUND);
        boolean preparing = cut.startsWith("LF_S6_F2_");
        if (preparing ? !before.isEmpty() || after.size() != 1 : before.size() != 1 || !after.isEmpty()) { return false; }
        var record = (preparing ? after : before).getCompound(0);
        return record.getInt("schema_version") == 3 && record.hasUUID("transfer_id")
                && record.contains("native_transfer", Tag.TAG_COMPOUND)
                && record.getString("phase").equals(preparing ? "PREPARED" : "COMMITTED");
    }

    static void pause(String cut, String hook) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_PAUSED cut={} hook={}", cut, hook);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException(stopped); }
        throw new IllegalStateException("S6 flight forced stop did not arrive within 30 seconds");
    }
}
