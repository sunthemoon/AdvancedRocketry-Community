package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/** W221's exact, observation-only journal cut. */
public final class EscrowLoaderS6PadJournalProbe {
    private EscrowLoaderS6PadJournalProbe() { }

    static CheckedSavedDataPolicy journalPolicy(MinecraftServer server, CheckedSavedDataPolicy base) {
        if (server == null) { return base; }
        return policy(base, Boolean.getBoolean(EscrowTestEndpoints.HOOKS),
                System.getProperty("advancedrocketrycommunity.escrowScenario", ""),
                System.getProperty("advancedrocketrycommunity.escrowCase", ""),
                () -> EscrowTestEndpoints.enabled(server), () -> EscrowTestEndpoints.requireEnabled(server),
                cut -> server.submit(() -> EscrowRuntime.of(server).checkpoint(cut.endsWith("BEFORE") ? "PAD_BEFORE" : "PAD_MOVED")).join());
    }

    static CheckedSavedDataPolicy policy(CheckedSavedDataPolicy base, boolean hooks, String scenario, String cut,
            BooleanSupplier enabled, Runnable requireEnabled, Consumer<String> observe) {
        if (!hooks || !"start".equals(scenario) || !("LF_S6_PAD_BEFORE".equals(cut) || "LF_S6_PAD_MOVED".equals(cut))
                || !enabled.getAsBoolean()) { return base; }
        requireEnabled.run();
        return base.withCommitter((staged, target) -> {
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            var previous = CheckedSavedDataFile.readPayload(target, type).orElse(null);
            var candidate = CheckedSavedDataFile.readPayload(staged, type).orElseThrow();
            boolean selected = EscrowLoaderS6JournalProbe.transition("LF_S6_F8_JOURNAL_REPLACED", previous, candidate);
            if (selected && cut.endsWith("BEFORE")) { observe.accept(cut); }
            base.committer().commit(staged, target);
            if (selected && cut.endsWith("MOVED")) { observe.accept(cut); }
        });
    }
}
