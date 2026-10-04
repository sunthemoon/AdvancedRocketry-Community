package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.RecipeMenuReason;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessJournalStore;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineLogic;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceAvailability;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessSimulationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTickInput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTickResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionExecutor;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessNbtResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStateData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Process owner that migrates the accepted v0.2 state into the shared v1.2 kernel. */
final class ElectrolyzerProcessController implements ProcessJournalStore {
    static final String LEGACY_UNVERIFIED_SIGNATURE = "0".repeat(64);

    private final Runnable changed;

    private ProcessMachineState state = ProcessMachineState.IDLE;
    private ProcessFailure failure = ProcessFailure.NONE;
    private ElectrolyzerStatus displayStatus = ElectrolyzerStatus.IDLE;
    private long resourceRevision;
    private Optional<UUID> lastAppliedTransactionId = Optional.empty();
    @Nullable
    private ProcessProgress progress;
    @Nullable
    private String recipeSignature;
    @Nullable
    private ProcessTransactionJournal journal;
    private ProcessNbtStatus legacyStatus = ProcessNbtStatus.SUPPORTED;
    private ProcessNbtStatus processStatus = ProcessNbtStatus.SUPPORTED;
    private ProcessNbtStatus journalStatus = ProcessNbtStatus.SUPPORTED;
    @Nullable
    private Tag preservedProcessRoot;
    @Nullable
    private Tag preservedJournalRoot;
    private boolean writeProcessRoot = true;
    private boolean migrationPerformed;
    private final ElectrolyzerRecipeResolver recipes = new ElectrolyzerRecipeResolver();
    private final RecipeSignatureMigration signatures = new RecipeSignatureMigration();

    ElectrolyzerProcessController(Runnable changed) {
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    void load(CompoundTag parent, ElectrolyzerPersistence.DecodeResult legacy) {
        reset();
        if (!RecipeSignatureMigration.bounded(parent)) {
            signatures.load(parent, true, null);
            state = ProcessMachineState.UNSUPPORTED_DATA;
            displayStatus = ElectrolyzerStatus.UNSUPPORTED_DATA;
            return;
        }
        legacyStatus = legacyStatus(legacy);
        ProcessNbtResult<ProcessStateData> processResult = ProcessStatePersistence.decode(parent);
        processStatus = normalizeEmpty(processResult.status());
        if (processResult.status() == ProcessNbtStatus.SUPPORTED) {
            applyLoaded(processResult.value().orElseThrow());
            if (!legacyMatchesProcess(legacy)) {
                rejectLoadedProcess(parent, ProcessNbtStatus.INVALID_DATA);
            }
        } else if (processResult.status() == ProcessNbtStatus.EMPTY) {
            if (legacyStatus == ProcessNbtStatus.SUPPORTED) {
                migrateLegacy(legacy);
            } else {
                writeProcessRoot = false;
            }
        } else {
            preservedProcessRoot = processResult.preservedRoot().orElseThrow();
            state = processResult.status() == ProcessNbtStatus.UNSUPPORTED_SCHEMA
                    ? ProcessMachineState.UNSUPPORTED_DATA
                    : ProcessMachineState.RECOVERY_REQUIRED;
        }

        ProcessNbtResult<ProcessTransactionJournal> journalResult = ProcessJournalPersistence.decode(parent);
        journalStatus = normalizeEmpty(journalResult.status());
        if (journalResult.status() == ProcessNbtStatus.SUPPORTED) {
            journal = journalResult.value().orElseThrow();
            state = ProcessMachineState.RECOVERY_REQUIRED;
        } else if (journalResult.status() != ProcessNbtStatus.EMPTY) {
            preservedJournalRoot = journalResult.preservedRoot().orElseThrow();
            state = journalResult.status() == ProcessNbtStatus.UNSUPPORTED_SCHEMA
                    ? ProcessMachineState.UNSUPPORTED_DATA
                    : ProcessMachineState.RECOVERY_REQUIRED;
        }
        if (legacyStatus == ProcessNbtStatus.UNSUPPORTED_SCHEMA
                || processStatus == ProcessNbtStatus.UNSUPPORTED_SCHEMA
                || journalStatus == ProcessNbtStatus.UNSUPPORTED_SCHEMA) {
            state = ProcessMachineState.UNSUPPORTED_DATA;
            displayStatus = ElectrolyzerStatus.UNSUPPORTED_DATA;
        } else if (!acceptsResourceAccess()) {
            state = ProcessMachineState.RECOVERY_REQUIRED;
            displayStatus = ElectrolyzerStatus.INVALID_RECIPE;
        } else {
            displayStatus = statusFor(state, failure);
        }
        signatures.load(parent, progress != null || journal != null || !acceptsResourceAccess(), retainedRecipeId());
        if (signatures.unsupported()) {
            state = ProcessMachineState.UNSUPPORTED_DATA;
            displayStatus = ElectrolyzerStatus.UNSUPPORTED_DATA;
        } else if (acceptsResourceAccess() && !signatures.current() && progress != null) {
            state = ProcessMachineState.RECOVERY_REQUIRED;
            failure = failure(ProcessFailureCode.RECOVERY_DIVERGED, "signature_migration_pending");
            displayStatus = ElectrolyzerStatus.INVALID_RECIPE;
        }
    }

    void save(CompoundTag parent) {
        if (signatures.oversized()) { signatures.save(parent, null); return; }
        if (preservedProcessRoot != null) {
            parent.put(ProcessStatePersistence.ROOT, preservedProcessRoot.copy());
        } else if (writeProcessRoot) {
            parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(data()));
        } else {
            parent.remove(ProcessStatePersistence.ROOT);
        }
        if (preservedJournalRoot != null) {
            parent.put(ProcessJournalPersistence.ROOT, preservedJournalRoot.copy());
        } else if (journal != null) {
            parent.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(journal));
        } else {
            parent.remove(ProcessJournalPersistence.ROOT);
        }
        signatures.save(parent, retainedRecipeId());
    }

    private String retainedRecipeId() {
        if (progress != null && journal != null && !progress.definitionId().equals(journal.definitionId())) { return null; }
        return progress != null ? progress.definitionId() : journal != null ? journal.definitionId() : null;
    }

    boolean preservesRecipeInput() {
        return !signatures.current() || !acceptsResourceAccess() || journal != null
                || state == ProcessMachineState.RECOVERY_REQUIRED
                || state == ProcessMachineState.UNSUPPORTED_DATA;
    }

    void tick(ServerLevel level, ElectrolyzerBlockEntity machine, boolean enabled) {
        if (!prepareSignature() || !acceptsResourceAccess()) {
            return;
        }
        if (journal != null) {
            recover(level, machine);
            return;
        }
        if (state == ProcessMachineState.RECOVERY_REQUIRED
                || state == ProcessMachineState.UNSUPPORTED_DATA) {
            return;
        }
        if (progress == null && machine.chargeFromRedstoneInternal()) { recordResourceMutation(); }
        if (level.getGameTime() % 20L == 0L) {
            recipes.requestRefresh();
        }

        ItemStack input = machine.storedItemCopy(ElectrolyzerBlockEntity.SLOT_INPUT);
        if (input.isEmpty()) {
            boolean lostActiveRecipe = progress != null;
            updateState(
                    lostActiveRecipe ? ProcessMachineState.WAITING_INPUT : ProcessMachineState.IDLE,
                    lostActiveRecipe
                            ? failure(ProcessFailureCode.MISSING_ITEM_INPUT, "missing_active_input")
                            : ProcessFailure.NONE,
                    lostActiveRecipe ? ElectrolyzerStatus.INVALID_RECIPE : ElectrolyzerStatus.IDLE
            );
            return;
        }

        boolean wasActive = progress != null;
        ElectrolyzerRecipe recipe = recipes.resolve(
                level,
                machine.getBlockPos(),
                input,
                progress,
                recipeSignature
        );
        if (recipe == null) {
            updateState(
                    ProcessMachineState.INVALID_RECIPE,
                    failure(ProcessFailureCode.INVALID_RECIPE, wasActive ? recipes.failureReason() : "no_recipe"),
                    wasActive || recipes.ambiguous()
                            ? ElectrolyzerStatus.INVALID_RECIPE
                            : ElectrolyzerStatus.NO_RECIPE
            );
            return;
        }
        if (!recipe.available()) {
            updateState(ProcessMachineState.INVALID_RECIPE,
                    failure(ProcessFailureCode.INVALID_RECIPE, "recipe_tags_invalid"), ElectrolyzerStatus.INVALID_RECIPE);
            return;
        }
        ProcessProgress active = progress == null
                ? ProcessProgress.notStarted(recipe.processDefinition())
                : progress;
        if (!validProgress(recipe, active)) {
            updateState(
                    ProcessMachineState.INVALID_RECIPE,
                    failure(ProcessFailureCode.INVALID_RECIPE, active.definitionId()),
                    ElectrolyzerStatus.INVALID_RECIPE
            );
            return;
        }
        if (progress != null && active.progressTicks() == recipe.processDefinition().durationTicks()) {
            updateState(
                    ProcessMachineState.RECOVERY_REQUIRED,
                    failure(ProcessFailureCode.RECOVERY_DIVERGED, active.definitionId()),
                    ElectrolyzerStatus.INVALID_RECIPE
            );
            return;
        }
        if (!recipe.matches(new net.minecraft.world.SimpleContainer(input.copy()), level)) {
            updateState(ProcessMachineState.WAITING_INPUT,
                    failure(ProcessFailureCode.MISSING_ITEM_INPUT, recipe.getId().toString()), ElectrolyzerStatus.NO_RECIPE);
            return;
        }
        if (progress != null && machine.chargeFromRedstoneInternal()) { recordResourceMutation(); }

        ElectrolyzerResourceStore resources = new ElectrolyzerResourceStore(machine, this, recipe);
        ProcessSimulationResult simulation = ProcessMachineLogic.simulate(
                recipe.processDefinition(),
                resources.snapshot()
        );
        ProcessResourceAvailability availability = availability(simulation);
        if (availability == null) {
            updateState(
                    ProcessMachineState.INVALID_RECIPE,
                    simulation.failure(),
                    ElectrolyzerStatus.INVALID_RECIPE
            );
            return;
        }
        ProcessTickResult tick = ProcessMachineLogic.tick(
                recipe.processDefinition(),
                active,
                new ProcessTickInput(
                        true,
                        enabled,
                        availability,
                        machine.energyStored()
                )
        );
        if (tick.energyConsumed() > 0
                && !machine.consumeEnergyInternal(Math.toIntExact(tick.energyConsumed()))) {
            updateState(
                    ProcessMachineState.WAITING_ENERGY,
                    failure(ProcessFailureCode.INSUFFICIENT_ENERGY, recipe.getId().toString()),
                    ElectrolyzerStatus.NEEDS_ENERGY
            );
            return;
        }
        applyTick(recipe, tick);
        if (tick.completionDue()) {
            complete(level, machine, recipe, resources);
        }
    }

    boolean inputLocked() {
        return progress != null || journal != null || !acceptsResourceAccess();
    }

    boolean acceptsResourceAccess() {
        return !signatures.unsupported() && legacyStatus == ProcessNbtStatus.SUPPORTED
                && processStatus == ProcessNbtStatus.SUPPORTED
                && journalStatus == ProcessNbtStatus.SUPPORTED;
    }

    boolean permitsExternalResourceOperations() {
        return signatures.current() && acceptsResourceAccess()
                && journal == null
                && state != ProcessMachineState.RECOVERY_REQUIRED
                && state != ProcessMachineState.UNSUPPORTED_DATA;
    }

    void recordExternalMutation() {
        if (acceptsResourceAccess()) {
            recordResourceMutation();
        }
    }

    void inputChanged() {
        recipes.inputChanged();
    }

    int progressTicks() {
        return progress == null ? 0 : progress.progressTicks();
    }

    int totalProcessingTicks() {
        return recipes.totalProcessingTicks();
    }

    ElectrolyzerStatus displayStatus() {
        return displayStatus;
    }

    RecipeMenuReason recipeMenuReason() {
        return state == ProcessMachineState.UNSUPPORTED_DATA
                ? RecipeMenuReason.NONE : RecipeMenuReason.fromFailure(failure);
    }

    long recipeLookupCount() {
        return recipes.lookupCount();
    }

    long resourceRevision() {
        return resourceRevision;
    }

    @Nullable
    ResourceLocation activeRecipeId() {
        return progress == null ? null : ResourceLocation.tryParse(progress.definitionId());
    }

    Optional<UUID> lastAppliedTransactionId() {
        return lastAppliedTransactionId;
    }

    boolean migrationPerformed() {
        return migrationPerformed;
    }

    void replaceResourceRevision(long expected, long replacement) {
        if (resourceRevision != expected || replacement != Math.addExact(expected, 1)) {
            throw new IllegalStateException("Electrolyzer resource revision changed during commit");
        }
        resourceRevision = replacement;
        changed.run();
    }

    void restoreResourceRevision(long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("resource revision cannot be negative");
        }
        resourceRevision = revision;
        changed.run();
    }

    void markApplied(UUID transactionId) {
        lastAppliedTransactionId = Optional.of(Objects.requireNonNull(transactionId, "transactionId"));
        changed.run();
    }

    @Override
    public Optional<ProcessTransactionJournal> load() {
        return Optional.ofNullable(journal);
    }

    @Override
    public void save(ProcessTransactionJournal replacement) {
        ProcessTransactionJournal checked = Objects.requireNonNull(replacement, "replacement");
        if (!acceptsResourceAccess()) {
            throw new IllegalStateException("Electrolyzer process data is not writable");
        }
        journal = checked;
        changed.run();
    }

    @Override
    public void clear(UUID transactionId) {
        if (journal == null || !journal.transactionId().equals(transactionId)) {
            throw new IllegalStateException("Electrolyzer cannot clear a different journal");
        }
        journal = null;
        changed.run();
    }

    private void recover(ServerLevel level, ElectrolyzerBlockEntity machine) {
        ProcessTransactionJournal activeJournal = journal;
        UUID expectedMachineId = machineId(level, machine.getBlockPos());
        if (!activeJournal.machineId().equals(expectedMachineId)) {
            updateState(
                    ProcessMachineState.RECOVERY_REQUIRED,
                    failure(ProcessFailureCode.RECOVERY_DIVERGED, activeJournal.definitionId()),
                    ElectrolyzerStatus.INVALID_RECIPE
            );
            return;
        }
        ElectrolyzerRecipe recipe = recipes.byId(level, activeJournal.definitionId());
        if (recipe == null || !recipe.signature().equals(recipeSignature)) {
            updateState(ProcessMachineState.INVALID_RECIPE,
                    failure(ProcessFailureCode.INVALID_RECIPE, recipe == null ? "recipe_missing" : "recipe_changed"),
                    ElectrolyzerStatus.INVALID_RECIPE);
            return;
        }
        if (progress == null
                || !progress.definitionId().equals(activeJournal.definitionId())
                || progress.consumedEnergy() != (long) progress.progressTicks() * recipe.spec().energyPerTick()
                || progress.progressTicks() != recipe.spec().processingTicks()) {
            updateState(
                    ProcessMachineState.RECOVERY_REQUIRED,
                    failure(ProcessFailureCode.RECOVERY_DIVERGED, "journal_progress_invalid"),
                    ElectrolyzerStatus.INVALID_RECIPE
            );
            return;
        }
        try { recipe = recipe.retainedPlan(activeJournal.before()); }
        catch (IllegalArgumentException | com.google.gson.JsonParseException exception) {
            updateState(ProcessMachineState.RECOVERY_REQUIRED,
                    failure(ProcessFailureCode.RECOVERY_DIVERGED, "retained_plan_invalid"), ElectrolyzerStatus.INVALID_RECIPE);
            return;
        }
        ProcessSimulationResult expected = ProcessMachineLogic.simulate(
                recipe.processDefinition(),
                activeJournal.before()
        );
        if (expected.plan().isEmpty()
                || !expected.plan().orElseThrow().after().equals(activeJournal.after())) {
            updateState(
                    ProcessMachineState.RECOVERY_REQUIRED,
                    failure(ProcessFailureCode.RECOVERY_DIVERGED, activeJournal.definitionId()),
                    ElectrolyzerStatus.INVALID_RECIPE
            );
            return;
        }
        ProcessTransactionResult recovered = ProcessTransactionExecutor.recover(
                new ElectrolyzerResourceStore(machine, this, recipe),
                this
        );
        handleTransactionResult(recovered);
    }

    private void complete(
            ServerLevel level,
            ElectrolyzerBlockEntity machine,
            ElectrolyzerRecipe recipe,
            ElectrolyzerResourceStore resources
    ) {
        ProcessSimulationResult simulation = ProcessMachineLogic.simulate(
                recipe.processDefinition(),
                resources.snapshot()
        );
        if (simulation.plan().isEmpty()) {
            ProcessMachineState replacement = stateFor(simulation.failure());
            updateState(replacement, simulation.failure(), statusFor(replacement, simulation.failure()));
            return;
        }
        ProcessTransactionResult result = ProcessTransactionExecutor.commit(
                UUID.randomUUID(),
                machineId(level, machine.getBlockPos()),
                simulation.plan().orElseThrow(),
                resources,
                this
        );
        handleTransactionResult(result);
    }

    private void handleTransactionResult(ProcessTransactionResult result) {
        if (result.failure().code() == ProcessFailureCode.NONE) {
            progress = null;
            recipeSignature = null;
            state = ProcessMachineState.IDLE;
            failure = ProcessFailure.NONE;
            displayStatus = ElectrolyzerStatus.RUNNING;
            recipes.requestRefresh();
            changed.run();
            return;
        }
        ProcessMachineState replacement = result.status() == ProcessTransactionStatus.STALE_TRANSACTION
                ? ProcessMachineState.RUNNING
                : ProcessMachineState.RECOVERY_REQUIRED;
        updateState(replacement, result.failure(), statusFor(replacement, result.failure()));
    }

    private boolean validProgress(ElectrolyzerRecipe recipe, ProcessProgress active) {
        if (!active.definitionId().equals(recipe.getId().toString())
                || active.progressTicks() > recipe.processDefinition().durationTicks()) {
            return false;
        }
        if (active.progressTicks() == 0) {
            return progress == null && recipeSignature == null && active.consumedEnergy() == 0;
        }
        if (!recipe.signature().equals(recipeSignature)) {
            return false;
        }
        return active.consumedEnergy() == Math.multiplyExact(
                (long) active.progressTicks(),
                recipe.processDefinition().energyPerTick()
        );
    }

    private boolean prepareSignature() {
        if (!acceptsResourceAccess()) { return false; }
        if (signatures.current()) {
            if (journal != null && progress == null) {
                updateState(ProcessMachineState.RECOVERY_REQUIRED,
                        failure(ProcessFailureCode.RECOVERY_DIVERGED, "journal_progress_missing"), ElectrolyzerStatus.INVALID_RECIPE);
                return false;
            }
            return true;
        }
        // Old ID/resolved hashes do not witness authored JSON, including same-ID overrides.
        updateState(ProcessMachineState.RECOVERY_REQUIRED,
                failure(ProcessFailureCode.RECOVERY_DIVERGED, "signature_migration_unproven"), ElectrolyzerStatus.INVALID_RECIPE);
        return false;
    }

    private void applyTick(ElectrolyzerRecipe recipe, ProcessTickResult tick) {
        boolean changedState = !Objects.equals(progress, tick.progress())
                || !Objects.equals(recipeSignature, recipe.signature())
                || state != tick.state()
                || !failure.equals(tick.failure());
        progress = tick.progress().progressTicks() == 0 ? null : tick.progress();
        recipeSignature = progress == null ? null : recipe.signature();
        state = tick.state();
        failure = tick.failure();
        displayStatus = statusFor(state, failure);
        if (changedState) {
            changed.run();
        }
    }

    private void resetProcess() {
        if (progress != null || recipeSignature != null) {
            progress = null;
            recipeSignature = null;
            changed.run();
        }
        recipes.processReset();
    }

    private void recordResourceMutation() {
        resourceRevision = Math.addExact(resourceRevision, 1);
        recipes.requestRefresh();
        changed.run();
    }

    private ProcessStateData data() {
        return new ProcessStateData(
                state,
                resourceRevision,
                Optional.ofNullable(progress),
                Optional.ofNullable(recipeSignature),
                lastAppliedTransactionId,
                failure
        );
    }

    private void applyLoaded(ProcessStateData data) {
        state = data.state();
        resourceRevision = data.resourceRevision();
        progress = data.progress().orElse(null);
        recipeSignature = data.recipeSignature().orElse(null);
        lastAppliedTransactionId = data.lastAppliedTransactionId();
        failure = data.failure();
    }

    private void migrateLegacy(ElectrolyzerPersistence.DecodeResult legacy) {
        migrationPerformed = legacy.present();
        if (legacy.progress() > 0 && legacy.activeRecipeId() != null) {
            progress = new ProcessProgress(
                    legacy.activeRecipeId().toString(),
                    legacy.progress(),
                    0
            );
            recipeSignature = LEGACY_UNVERIFIED_SIGNATURE;
            state = ProcessMachineState.RUNNING;
        }
    }

    private boolean legacyMatchesProcess(ElectrolyzerPersistence.DecodeResult legacy) {
        if (!legacy.present() || legacy.future() || legacy.invalid()) {
            return false;
        }
        if (progress == null) {
            return legacy.progress() == 0 && legacy.activeRecipeId() == null;
        }
        return legacy.progress() == progress.progressTicks()
                && legacy.activeRecipeId() != null
                && legacy.activeRecipeId().toString().equals(progress.definitionId());
    }

    private void rejectLoadedProcess(CompoundTag parent, ProcessNbtStatus status) {
        processStatus = status;
        preservedProcessRoot = parent.get(ProcessStatePersistence.ROOT).copy();
        progress = null;
        recipeSignature = null;
        state = ProcessMachineState.RECOVERY_REQUIRED;
        failure = failure(ProcessFailureCode.RECOVERY_DIVERGED, "legacy_process_mismatch");
    }

    private void updateState(
            ProcessMachineState replacement,
            ProcessFailure replacementFailure,
            ElectrolyzerStatus replacementDisplay
    ) {
        if (state != replacement
                || !failure.equals(replacementFailure)
                || displayStatus != replacementDisplay) {
            state = replacement;
            failure = replacementFailure;
            displayStatus = replacementDisplay;
            changed.run();
        }
    }

    private void reset() {
        signatures.reset();
        state = ProcessMachineState.IDLE;
        failure = ProcessFailure.NONE;
        displayStatus = ElectrolyzerStatus.IDLE;
        resourceRevision = 0;
        lastAppliedTransactionId = Optional.empty();
        progress = null;
        recipeSignature = null;
        journal = null;
        legacyStatus = ProcessNbtStatus.SUPPORTED;
        processStatus = ProcessNbtStatus.SUPPORTED;
        journalStatus = ProcessNbtStatus.SUPPORTED;
        preservedProcessRoot = null;
        preservedJournalRoot = null;
        writeProcessRoot = true;
        migrationPerformed = false;
        recipes.reset();
    }

    private static ProcessNbtStatus legacyStatus(ElectrolyzerPersistence.DecodeResult legacy) {
        if (legacy.future()) {
            return ProcessNbtStatus.UNSUPPORTED_SCHEMA;
        }
        return legacy.blockingInvalid() ? ProcessNbtStatus.INVALID_DATA : ProcessNbtStatus.SUPPORTED;
    }

    private static ProcessNbtStatus normalizeEmpty(ProcessNbtStatus status) {
        return status == ProcessNbtStatus.EMPTY ? ProcessNbtStatus.SUPPORTED : status;
    }

    @Nullable
    private static ProcessResourceAvailability availability(ProcessSimulationResult simulation) {
        if (simulation.successful()) {
            return ProcessResourceAvailability.READY;
        }
        return switch (simulation.failure().code()) {
            case MISSING_ITEM_INPUT -> ProcessResourceAvailability.MISSING_ITEM_INPUT;
            case MISSING_FLUID_INPUT -> ProcessResourceAvailability.MISSING_FLUID_INPUT;
            case OUTPUT_BLOCKED -> ProcessResourceAvailability.OUTPUT_BLOCKED;
            default -> null;
        };
    }

    private static ProcessMachineState stateFor(ProcessFailure failure) {
        return switch (failure.code()) {
            case MISSING_ITEM_INPUT, MISSING_FLUID_INPUT -> ProcessMachineState.WAITING_INPUT;
            case OUTPUT_BLOCKED -> ProcessMachineState.WAITING_OUTPUT;
            default -> ProcessMachineState.RECOVERY_REQUIRED;
        };
    }

    private static ElectrolyzerStatus statusFor(ProcessMachineState state, ProcessFailure failure) {
        return switch (state) {
            case IDLE -> ElectrolyzerStatus.IDLE;
            case RUNNING -> ElectrolyzerStatus.RUNNING;
            case WAITING_INPUT -> failure.code() == ProcessFailureCode.MISSING_FLUID_INPUT
                    ? ElectrolyzerStatus.NEEDS_WATER
                    : ElectrolyzerStatus.NO_RECIPE;
            case WAITING_OUTPUT -> ElectrolyzerStatus.OUTPUT_BLOCKED;
            case WAITING_ENERGY -> ElectrolyzerStatus.NEEDS_ENERGY;
            case REDSTONE_DISABLED -> ElectrolyzerStatus.REDSTONE_DISABLED;
            case INVALID_RECIPE, RECOVERY_REQUIRED -> ElectrolyzerStatus.INVALID_RECIPE;
            case UNSUPPORTED_DATA -> ElectrolyzerStatus.UNSUPPORTED_DATA;
        };
    }

    private static ProcessFailure failure(ProcessFailureCode code, String subject) {
        return new ProcessFailure(code, subject);
    }

    static UUID machineId(ServerLevel level, net.minecraft.core.BlockPos position) {
        String identity = AdvancedRocketryCommunity.MOD_ID
                + ":electrolyzer|"
                + level.dimension().location()
                + "|"
                + position.asLong();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }
}
