package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Strict independent schema for process progress, revision and replay marker. */
final class RollingMachineProcessPersistence {
    static final String ROOT = "arce_process";
    static final int SCHEMA_VERSION = 1;
    static final int MAX_ROOT_BYTES = 65_536;

    private static final Set<String> FIELDS = Set.of(
            "schema_version",
            "state",
            "resource_revision",
            "definition_id",
            "recipe_signature",
            "progress_ticks",
            "consumed_energy",
            "last_applied_transaction",
            "failure_code",
            "failure_subject"
    );
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");

    private RollingMachineProcessPersistence() {
    }

    static CompoundTag encode(ProcessData data) {
        Objects.requireNonNull(data, "data");
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", SCHEMA_VERSION);
        root.putString("state", data.state().name().toLowerCase(Locale.ROOT));
        root.putLong("resource_revision", data.resourceRevision());
        root.putString(
                "definition_id",
                data.progress().map(ProcessProgress::definitionId).orElse("")
        );
        root.putString("recipe_signature", data.recipeSignature().orElse(""));
        root.putInt("progress_ticks", data.progress().map(ProcessProgress::progressTicks).orElse(0));
        root.putLong("consumed_energy", data.progress().map(ProcessProgress::consumedEnergy).orElse(0L));
        root.putString(
                "last_applied_transaction",
                data.lastAppliedTransactionId().map(UUID::toString).orElse("")
        );
        root.putString("failure_code", data.failure().code().code());
        root.putString("failure_subject", data.failure().subject());
        if (root.sizeInBytes() > MAX_ROOT_BYTES) {
            throw new IllegalStateException("Rolling Machine process data exceeded its 64 KiB bound");
        }
        return root;
    }

    static RollingMachineNbtResult<ProcessData> decode(CompoundTag parent) {
        Objects.requireNonNull(parent, "parent");
        if (!parent.contains(ROOT)) {
            return RollingMachineNbtResult.empty();
        }
        Tag raw = parent.get(ROOT);
        if (!(raw instanceof CompoundTag root)) {
            return RollingMachineNbtResult.rejected(MultiblockNbtStatus.INVALID_DATA, raw);
        }
        if (root.sizeInBytes() > MAX_ROOT_BYTES
                || !root.contains("schema_version", Tag.TAG_INT)) {
            return RollingMachineNbtResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
        if (root.getInt("schema_version") != SCHEMA_VERSION) {
            return RollingMachineNbtResult.rejected(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, root);
        }
        try {
            requireExactFieldsAndTypes(root);
            String definitionId = root.getString("definition_id");
            String recipeSignature = root.getString("recipe_signature");
            int progressTicks = root.getInt("progress_ticks");
            long consumedEnergy = root.getLong("consumed_energy");
            Optional<ProcessProgress> progress;
            Optional<String> signature;
            if (definitionId.isEmpty()) {
                if (!recipeSignature.isEmpty() || progressTicks != 0 || consumedEnergy != 0L) {
                    throw new IllegalArgumentException("inactive process has active progress fields");
                }
                progress = Optional.empty();
                signature = Optional.empty();
            } else {
                new ProcessResourceKey(ProcessResourceKind.ITEM, "recipe", definitionId);
                if (!SIGNATURE.matcher(recipeSignature).matches()
                        || progressTicks < 1
                        || progressTicks > ProcessDefinition.MAX_DURATION_TICKS
                        || consumedEnergy < 0
                        || consumedEnergy > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("active process fields are outside their bounds");
                }
                progress = Optional.of(new ProcessProgress(definitionId, progressTicks, consumedEnergy));
                signature = Optional.of(recipeSignature);
            }
            long revision = root.getLong("resource_revision");
            if (revision < 0) {
                throw new IllegalArgumentException("resource revision cannot be negative");
            }
            ProcessMachineState state = parseState(root.getString("state"));
            if (state == ProcessMachineState.RUNNING && progress.isEmpty()) {
                throw new IllegalArgumentException("running state requires active progress");
            }
            ProcessFailure failure = new ProcessFailure(
                    parseFailureCode(root.getString("failure_code")),
                    root.getString("failure_subject")
            );
            Optional<UUID> lastApplied = parseOptionalUuid(root.getString("last_applied_transaction"));
            return RollingMachineNbtResult.supported(new ProcessData(
                    state,
                    revision,
                    progress,
                    signature,
                    lastApplied,
                    failure
            ));
        } catch (RuntimeException exception) {
            return RollingMachineNbtResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
    }

    private static void requireExactFieldsAndTypes(CompoundTag root) {
        if (!FIELDS.equals(root.getAllKeys())
                || !root.contains("state", Tag.TAG_STRING)
                || !root.contains("resource_revision", Tag.TAG_LONG)
                || !root.contains("definition_id", Tag.TAG_STRING)
                || !root.contains("recipe_signature", Tag.TAG_STRING)
                || !root.contains("progress_ticks", Tag.TAG_INT)
                || !root.contains("consumed_energy", Tag.TAG_LONG)
                || !root.contains("last_applied_transaction", Tag.TAG_STRING)
                || !root.contains("failure_code", Tag.TAG_STRING)
                || !root.contains("failure_subject", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("process root has missing, unexpected or mistyped fields");
        }
    }

    private static ProcessMachineState parseState(String value) {
        for (ProcessMachineState state : ProcessMachineState.values()) {
            if (state.name().toLowerCase(Locale.ROOT).equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("unknown process state");
    }

    private static ProcessFailureCode parseFailureCode(String value) {
        for (ProcessFailureCode code : ProcessFailureCode.values()) {
            if (code.code().equals(value)) {
                return code;
            }
        }
        throw new IllegalArgumentException("unknown process failure code");
    }

    private static Optional<UUID> parseOptionalUuid(String value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("transaction UUID is not canonical");
        }
        return Optional.of(parsed);
    }

    record ProcessData(
            ProcessMachineState state,
            long resourceRevision,
            Optional<ProcessProgress> progress,
            Optional<String> recipeSignature,
            Optional<UUID> lastAppliedTransactionId,
            ProcessFailure failure
    ) {
        ProcessData {
            Objects.requireNonNull(state, "state");
            if (resourceRevision < 0) {
                throw new IllegalArgumentException("resource revision cannot be negative");
            }
            progress = Objects.requireNonNull(progress, "progress");
            recipeSignature = Objects.requireNonNull(recipeSignature, "recipeSignature");
            lastAppliedTransactionId = Objects.requireNonNull(
                    lastAppliedTransactionId,
                    "lastAppliedTransactionId"
            );
            Objects.requireNonNull(failure, "failure");
            if (progress.isPresent() != recipeSignature.isPresent()) {
                throw new IllegalArgumentException("progress and recipe signature must be present together");
            }
            recipeSignature.ifPresent(value -> {
                if (!SIGNATURE.matcher(value).matches()) {
                    throw new IllegalArgumentException("recipe signature must be lowercase SHA-256");
                }
            });
        }

        static ProcessData empty() {
            return new ProcessData(
                    ProcessMachineState.IDLE,
                    0,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    ProcessFailure.NONE
            );
        }
    }
}
