package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicHatchKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Forge lifetime adapter. Immutable controller checkpoints own every Item/Fluid and active job. */
public class ClassicControllerBlockEntity extends BlockEntity implements MenuProvider {
    private final ClassicMachineDefinition definition;
    private ClassicCheckpoint checkpoint;
    private CompoundTag retained;
    private boolean repair;
    private boolean ready;
    private boolean transaction;
    private boolean lookupNeeded = true;
    private final Set<ChunkPos> unavailable = new HashSet<>();
    private ClassicWait reason = ClassicWait.UNFORMED;

    public ClassicControllerBlockEntity(BlockPos position, BlockState state) {
        this(ModBlockEntities.LATHE.get(), position, state, ClassicMachineDefinition.LATHE);
    }
    public ClassicControllerBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state,
                                           ClassicMachineDefinition definition) {
        super(type, position, state); this.definition = definition;
        checkpoint = freshCheckpoint(state);
    }
    private static ClassicCheckpoint freshCheckpoint(BlockState state) {
        UUID id = UUID.randomUUID();
        return new ClassicCheckpoint(MultiblockControllerState.initial(id, transform(state)),
                Set.of(), ClassicResources.empty(id), Optional.empty(), 0, Optional.empty());
    }
    ClassicMachineDefinition definition() { return definition; }
    ClassicCheckpoint checkpoint() { return checkpoint; }
    public MultiblockControllerState controllerState() { return checkpoint.structure(); }
    public ClassicWait reason() { return repair ? ClassicWait.REPAIR : reason; }
    public int progressTicks() { return checkpoint.job().map(job -> job.progress().progressTicks()).orElse(0); }
    public int durationTicks() { return checkpoint.job().map(ClassicCheckpoint.Job::duration).orElse(0); }
    public long consumedEnergy() { return checkpoint.job().map(job -> job.progress().consumedEnergy()).orElse(0L); }
    public boolean formed() { return ready && !repair && checkpoint.structure().formationState() == MultiblockFormationState.FORMED; }
    public boolean repairRequired() { return repair; }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    boolean transaction() { return transaction; }
    boolean physical() {
        if (isRemoved() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()) { return false; }
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }
    boolean ownerUnavailable() { return repair || !physical() || unavailable.contains(new ChunkPos(worldPosition)); }
    boolean ownsClaim(ClassicHatchBlockEntity hatch) {
        var claim = hatch.ownerClaim().orElse(null);
        return claim != null && claim.machineInstanceId().equals(checkpoint.structure().machineInstanceId())
                && level != null && claim.controllerLevel().equals(level.dimension())
                && claim.controllerPosition().equals(worldPosition);
    }
    boolean acceptsBinding(ClassicHatchBlockEntity hatch) {
        var binding = hatch.multiblockBinding().orElse(null); var state = checkpoint.structure();
        return formed() && !ownerUnavailable() && hatch.physical() && ownsClaim(hatch) && binding != null
                && binding.generation() == state.generation() && state.partPositions().contains(hatch.getBlockPos());
    }
    boolean acceptsFacade(ClassicHatchBlockEntity hatch) { return !transaction && acceptsBinding(hatch) && allPartsLoaded(); }
    private boolean allPartsLoaded() {
        if (!unavailable.isEmpty()) { return false; }
        for (BlockPos position : checkpoint.structure().partPositions()) {
            var hatch = loadedHatch(position);
            if (hatch == null || !acceptsBinding(hatch)) { return false; }
        }
        return true;
    }
    ClassicHatchBlockEntity loadedHatch(BlockPos position) {
        if (!(level instanceof ServerLevel server)) { return null; }
        var chunk = server.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(position) instanceof ClassicHatchBlockEntity hatch ? hatch : null;
    }
    Set<BlockPos> footprint(MultiblockPatternDefinition pattern) {
        Set<BlockPos> cells = new HashSet<>(checkpoint.claimedParts());
        var origin = new PatternPosition(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        for (var local : pattern.cells().keySet()) {
            var transformed = checkpoint.structure().selectedTransform().localToWorld(local, pattern.controllerAnchor(), origin);
            cells.add(new BlockPos(transformed.x(), transformed.y(), transformed.z()));
        }
        return Set.copyOf(cells);
    }
    void invalidate() {
        ready = false; lookupNeeded = true;
        checkpoint.claimedParts().forEach(position -> { var hatch = loadedHatch(position); if (hatch != null) { hatch.refreshFacades(); } });
    }
    void chunkUnavailable(ChunkPos chunk) {
        unavailable.add(chunk); invalidate(); reason = ClassicWait.UNLOADED;
        var state = checkpoint.structure();
        if (!repair && state.generation() > 0 && !state.partPositions().isEmpty()) {
            checkpoint = checkpoint.structure(state.withState(MultiblockFormationState.WAITING_UNLOADED, state.partPositions())); setChanged();
        }
    }
    void chunkAvailable(ChunkPos chunk) { unavailable.remove(chunk); }

    void revalidate(ServerLevel server, MultiblockPatternDefinition pattern) {
        if (repair || transaction || !unavailable.isEmpty()) { return; }
        ready = false;
        var roles = new PatternRoleResolver() {
            @Override public boolean isController(BlockPos position, BlockState state) {
                return position.equals(worldPosition) && state.getBlock() instanceof ClassicControllerBlock;
            }
            @Override public Optional<String> portChannel(BlockPos position, BlockState state) {
                return state.getBlock() instanceof ClassicHatchBlock block ? Optional.of(block.kind().patternRole()) : Optional.empty();
            }
        };
        var world = new ServerLevelPatternWorldView(server, roles);
        var validation = MultiblockPatternValidator.validate(pattern, checkpoint.structure().selectedTransform(),
                new PatternPosition(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()), world);
        ClassicResources resources = checkpoint.resources();
        Set<BlockPos> claims = new HashSet<>(checkpoint.claimedParts());
        if (validation.formed()) {
            var additions = new ArrayList<ClassicResourceBank>();
            int hatchCount = 0;
            for (BlockPos position : footprint(pattern)) {
                var hatch = loadedHatch(position);
                if (hatch == null) { continue; }
                if (++hatchCount > 64) { reason = ClassicWait.LIMIT; return; }
                claims.add(position);
                if (claims.size() > 64) { reason = ClassicWait.LIMIT; return; }
                if (hatch.repairRequired() || hatch.ownerClaim().isPresent() && !ownsClaim(hatch)) { reason = ClassicWait.CONFLICT; return; }
                hatch.kind().bankKind().ifPresent(kind -> {
                    if (checkpoint.resources().bank(hatch.bankKey()).isEmpty()) { additions.add(ClassicResourceBank.empty(hatch.bankKey())); }
                });
            }
            try { resources = resources.replace(additions, false); }
            catch (IllegalArgumentException | IllegalStateException limit) { reason = ClassicWait.LIMIT; return; }
            // Reconcile independently saved part generations only after identity and bank preflight.
            // ready is false throughout this pass, so reconciliation cannot expose resource facades.
            for (BlockPos position : claims) {
                var hatch = loadedHatch(position); if (hatch != null) { hatch.reconcileGeneration(this); }
            }
        }
        var access = new LoadedPartBindingAccess() {
            @Override public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey() { return server.dimension(); }
            @Override public boolean isLoaded(BlockPos position) { return server.hasChunkAt(position); }
            @Override public Optional<MultiblockPartBindingTarget> loadedTarget(BlockPos position) {
                var hatch = loadedHatch(position);
                return hatch != null && !hatch.repairRequired() && (hatch.ownerClaim().isEmpty() || ownsClaim(hatch))
                        ? Optional.of(hatch) : Optional.empty();
            }
        };
        var result = MultiblockLifecycleCoordinator.revalidate(checkpoint.structure(), pattern, server.dimension(),
                worldPosition, world, new LoadedPartBindingGateway(access));
        if (result.controllerState().formationState() == MultiblockFormationState.FORMED) {
            long generation = checkpoint.structure().generation();
            checkpoint = new ClassicCheckpoint(result.controllerState(), claims, resources, checkpoint.job(),
                    checkpoint.batchOrdinal(), checkpoint.lastTransaction());
            ready = true; lookupNeeded = true; reason = ClassicWait.INPUT;
            // ADR-066 §6.2: a new binding generation is an actual formation; resuming the same one is not.
            if (result.controllerState().generation() > generation) {
                io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicAdvancementEvents
                        .multiblockFormed(server, worldPosition);
            }
        } else {
            // A rolled-back live binding can still have established a durable owner claim.
            claims.removeIf(position -> !checkpoint.claimedParts().contains(position)
                    && (loadedHatch(position) == null || !ownsClaim(loadedHatch(position))));
            checkpoint = new ClassicCheckpoint(result.controllerState(), claims, checkpoint.resources(), checkpoint.job(),
                    checkpoint.batchOrdinal(), checkpoint.lastTransaction());
            reason = result.controllerState().formationState() == MultiblockFormationState.WAITING_UNLOADED ? ClassicWait.UNLOADED
                    : result.controllerState().formationState() == MultiblockFormationState.BINDING_CONFLICT ? ClassicWait.CONFLICT : ClassicWait.UNFORMED;
        }
        setChanged();
    }

    record Edit<T>(ClassicResources resources, T result) { }
    <T> T access(ClassicHatchBlockEntity hatch, long epoch, Function<ClassicResources, Edit<T>> action, T fallback) {
        if (!acceptsFacade(hatch)) { return fallback; }
        ClassicCheckpoint before = checkpoint; transaction = true;
        try {
            var edit = action.apply(before.resources());
            if (checkpoint != before || !hatch.current(epoch, this) || !allPartsLoaded()) { return fallback; }
            if (edit.resources() != before.resources()) {
                checkpoint = before.resources(edit.resources()); setChanged();
                if (hatch.kind().bankKind().orElseThrow().isInput()) { inputChanged(); }
            }
            return edit.result();
        } catch (IllegalArgumentException | IllegalStateException refused) { return fallback; }
        finally { transaction = false; }
    }
    private void inputChanged() {
        lookupNeeded = true;
        if (level instanceof ServerLevel server) { ClassicRuntime.manager(server).resourceChanged(this); }
    }
    boolean needsLookup() { return lookupNeeded && formed() && checkpoint.job().isEmpty() && !transaction; }
    void lookupStarted() { lookupNeeded = false; reason = ClassicWait.LOOKUP; }
    boolean candidateMatches(ClassicMachineRecipe recipe, ProcessDefinition process) {
        if (transaction || !formed() || !allPartsLoaded() || !recipe.enabled()) { return false; }
        ClassicCheckpoint before = checkpoint; transaction = true;
        try {
            var result = ClassicBatch.prepare(before.resources(), activeBanks(), recipe, process);
            return checkpoint == before && formed() && allPartsLoaded()
                    && (result.plan().isPresent() || result.reason() == ClassicWait.OUTPUT);
        } catch (IllegalArgumentException | IllegalStateException limit) { reason = ClassicWait.LIMIT; return false; }
        finally { transaction = false; }
    }
    void lookupCompleted(Optional<ClassicMachineRecipe> selected) {
        if (!formed() || checkpoint.job().isPresent()) { return; }
        if (selected.isEmpty()) { if (reason != ClassicWait.LIMIT) { reason = ClassicWait.INPUT; } return; }
        if (checkpoint.batchOrdinal() == Long.MAX_VALUE) { reason = ClassicWait.LIMIT; return; }
        var recipe = selected.orElseThrow();
        checkpoint = checkpoint.job(Optional.of(new ClassicCheckpoint.Job(recipe.getId(), recipe.rows().jsonSignature(),
                recipe.rows().processingTicks(), recipe.rows().energyPerTick(), new ProcessProgress(recipe.getId().toString(), 0, 0))
                .start(checkpoint.batchOrdinal() + 1)));
        setChanged();
    }
    Set<ClassicBankKey> activeBanks() {
        var result = new HashSet<ClassicBankKey>();
        checkpoint.structure().partPositions().forEach(position -> {
            var hatch = loadedHatch(position); if (hatch != null && hatch.kind().bankKind().isPresent()) { result.add(hatch.bankKey()); }
        });
        return Set.copyOf(result);
    }
    public int storedEnergy() {
        int result = 0;
        for (BlockPos position : checkpoint.structure().partPositions()) {
            var hatch = loadedHatch(position); if (hatch != null && hatch.kind() == ClassicHatchKind.POWER_INPUT) { result += hatch.energyStored(); }
        }
        return result;
    }
    void catalogUnavailable() { reason = ClassicWait.LIMIT; }
    void tickProcess(ClassicMachineRecipe recipe, ProcessDefinition process) {
        if (repair || transaction || !formed() || checkpoint.job().isEmpty()) { return; }
        var job = checkpoint.job().orElseThrow();
        if (!allPartsLoaded()) { invalidate(); reason = ClassicWait.UNLOADED; return; }
        if (recipe == null) { reason = ClassicWait.RECIPE_MISSING; return; }
        if (!job.signature().equals(recipe.rows().jsonSignature())) { reason = ClassicWait.RECIPE_CHANGED; return; }
        if (!recipe.enabled()) { reason = ClassicWait.CONFIG_DISABLED; return; }
        if (process == null) { reason = ClassicWait.RECIPE_TAGS_INVALID; return; }
        if (level.hasNeighborSignal(worldPosition)) { reason = ClassicWait.REDSTONE; return; }
        if (definition == ClassicMachineDefinition.CRYSTALLIZER
                && !ClassicRuntime.manager((ServerLevel) level).gravityAllows(worldPosition)) { reason = ClassicWait.GRAVITY; return; }
        if (!recipe.rows().chanceOutputs().isEmpty() && job.progress().progressTicks() >= job.duration() - 1 && job.outcome().isEmpty()) {
            ClassicCheckpoint selecting = checkpoint; transaction = true;
            try {
                long ordinal = job.ordinal() > 0 ? job.ordinal() : Math.addExact(checkpoint.batchOrdinal(), 1);
                var outputs = ClassicChanceSelection.choose(recipe.rows().chanceOutputs(),
                        ClassicChanceSelection.seed(checkpoint.resources().machineId(), job.signature(), ordinal),
                        id -> ClassicMachineRecipe.stackLimit(id, 1));
                var selected = job.choose(new ClassicCheckpoint.Outcome(UUID.randomUUID(), outputs));
                if (checkpoint != selecting || !formed() || !allPartsLoaded()) { return; }
                ClassicCheckpoint chosen = selecting.job(Optional.of(selected));
                ClassicCheckpointCodec.encode(chosen, definition.kind(), level.dimension().location(), worldPosition);
                checkpoint = chosen; job = selected; setChanged();
            } catch (IllegalArgumentException | ArithmeticException limit) { reason = ClassicWait.LIMIT; return; }
            finally { transaction = false; }
        }
        ClassicCheckpoint before = checkpoint; transaction = true;
        try {
            var result = ClassicBatch.prepare(before.resources(), activeBanks(), recipe, process,
                    job.outcome().map(ClassicCheckpoint.Outcome::outputs).orElse(List.of()));
            if (result.plan().isEmpty()) { reason = result.reason(); return; }
            if (checkpoint != before || !formed() || !allPartsLoaded()) { return; }
            var plan = result.plan().orElseThrow();
            var progress = job.progress(); boolean completes = progress.progressTicks() == job.duration();
            boolean willComplete = progress.progressTicks() >= job.duration() - 1;
            UUID transactionId = willComplete ? job.outcome().map(ClassicCheckpoint.Outcome::transaction).orElseGet(UUID::randomUUID) : null;
            if (willComplete && (before.batchOrdinal() == Long.MAX_VALUE
                    || !ClassicBatch.verifyCommit(plan, before.resources().machineId(), transactionId))) {
                reason = ClassicWait.LIMIT; return;
            }
            if (willComplete) {
                ClassicResourcesCodec.encode(plan.after());
                ClassicCheckpointCodec.encode(before, definition.kind(), level.dimension().location(), worldPosition);
            }
            if (!completes) {
                var tick = ProcessMachineLogic.tick(process, progress,
                        new ProcessTickInput(true, true, ProcessResourceAvailability.READY, storedEnergy()));
                if (tick.energyConsumed() == 0) { reason = ClassicWait.ENERGY; return; }
                int remaining = Math.toIntExact(tick.energyConsumed());
                for (BlockPos position : before.structure().partPositions()) {
                    var hatch = loadedHatch(position);
                    if (hatch != null && hatch.kind() == ClassicHatchKind.POWER_INPUT) { remaining -= hatch.debit(remaining); }
                }
                if (remaining != 0) { reason = ClassicWait.REPAIR; return; }
                progress = tick.progress(); completes = tick.completionDue();
            }
            if (completes) {
                // No native callback lies between the resource/progress/ordinal publications: one reference.
                checkpoint = new ClassicCheckpoint(before.structure(), before.claimedParts(), plan.after(), Optional.empty(),
                        before.batchOrdinal() + 1, Optional.of(transactionId)); lookupNeeded = true;
            } else { checkpoint = before.job(Optional.of(job.advance(progress))); }
            reason = ClassicWait.RUNNING; setChanged();
        } catch (IllegalArgumentException | IllegalStateException limit) { reason = ClassicWait.LIMIT; }
        finally { transaction = false; }
    }
    boolean blocksRemoval() {
        if (ownerUnavailable() || transaction) { return true; }
        if (!(level instanceof ServerLevel server)) { return true; }
        for (var bank : checkpoint.resources().banks()) {
            var key = bank.key(); var chunk = new ChunkPos(key.x() >> 4, key.z() >> 4);
            if (unavailable.contains(chunk) || server.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) { return true; }
        }
        return checkpoint.claimedParts().stream().anyMatch(position -> unavailable.contains(new ChunkPos(position))
                || loadedHatch(position) == null);
    }
    void removeHatch(ClassicHatchBlockEntity hatch) {
        if (!ownsClaim(hatch) || transaction || repair) { return; }
        transaction = true;
        try {
            var claims = new HashSet<>(checkpoint.claimedParts()); claims.remove(hatch.getBlockPos());
            checkpoint = new ClassicCheckpoint(checkpoint.structure().withState(MultiblockFormationState.UNFORMED, Set.of()),
                    claims, checkpoint.resources(), checkpoint.job(), checkpoint.batchOrdinal(), checkpoint.lastTransaction());
            setChanged();
            if (hatch.kind().bankKind().filter(ClassicBankKind::isItem).isPresent()) {
                var bank = checkpoint.resources().bank(hatch.bankKey()).orElse(null);
                if (bank != null) {
                    var drops = bank.items(); checkpoint = checkpoint.resources(checkpoint.resources().remove(hatch.bankKey()));
                    setChanged(); drops.forEach(stack -> drop(worldPosition, stack));
                }
            }
            invalidate();
        } finally { transaction = false; }
        if (level instanceof ServerLevel server) { ClassicRuntime.manager(server).changed(hatch.getBlockPos()); }
    }
    void removeController() {
        if (repair || transaction || !physical()) { return; }
        transaction = true;
        try {
            var banks = checkpoint.resources().banks(); var drops = new ArrayList<ItemStack>();
            var emptied = new ArrayList<ClassicResourceBank>();
            for (var bank : banks) {
                if (bank.key().kind().isItem()) { drops.addAll(bank.items()); }
                emptied.add(ClassicResourceBank.empty(bank.key()));
            }
            checkpoint = checkpoint.resources(checkpoint.resources().replace(emptied, false)); ready = false; setChanged();
            Set<BlockPos> positions = new HashSet<>(checkpoint.claimedParts());
            banks.forEach(bank -> positions.add(new BlockPos(bank.key().x(), bank.key().y(), bank.key().z())));
            positions.forEach(position -> { var hatch = loadedHatch(position); if (hatch != null && ownsClaim(hatch)) { hatch.clearClaim(); } });
            drops.forEach(stack -> drop(worldPosition, stack));
        } finally { transaction = false; }
    }
    private void drop(BlockPos position, ItemStack stack) {
        if (!stack.isEmpty()) { Containers.dropItemStack(level, position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5, stack); }
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            observeUnsafeRoot(server);
            if (retained != null && !repair && !retained.getCompound(ClassicCheckpointCodec.ROOT).getString("level").equals(server.dimension().location().toString())) { repair = true; }
            ClassicRuntime.manager(server).observe(this);
        }
    }
    @Override public void onChunkUnloaded() {
        invalidate(); if (level instanceof ServerLevel server) { ClassicRuntime.manager(server).forget(this); }
        super.onChunkUnloaded();
    }
    @Override public void setRemoved() { invalidate(); super.setRemoved(); }
    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (repair) { if (retained != null) { retained.getAllKeys().forEach(key -> outer.put(key, retained.get(key))); } return; }
        ClassicCheckpoint saved = checkpoint;
        ResourceLocation levelId = level == null ? new ResourceLocation("minecraft:overworld") : level.dimension().location();
        outer.put(ClassicCheckpointCodec.ROOT, ClassicCheckpointCodec.encode(saved, definition.kind(), levelId, worldPosition));
        outer.put(ClassicResourcesCodec.ROOT, ClassicResourcesCodec.encode(saved.resources()));
        new io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration()
                .save(outer, saved.job().map(job -> job.recipe().toString()).orElse(null));
    }
    @Override public void load(CompoundTag outer) {
        if (transaction) { throw new IllegalStateException("Reentrant controller load"); }
        super.load(outer); invalidate(); repair = false; retained = new CompoundTag();
        for (String key : List.of(ClassicCheckpointCodec.ROOT, ClassicResourcesCodec.ROOT, "arce_recipe_signature",
                "arce_process_journal", "arce_process", "arce_machine")) {
            if (outer.contains(key)) {
                Tag raw = outer.get(key); retained.put(key, ClassicCheckpointCodec.bounded(raw) ? raw.copy() : raw);
            }
        }
        // Only an entity without any controller root has nothing to preserve. A partial set is decoded,
        // refused and retained verbatim below; absent roots are writable, so it does not refuse a save.
        if (retained.isEmpty()) {
            checkpoint = freshCheckpoint(getBlockState()); retained = null; unavailable.clear();
            lookupNeeded = true; reason = ClassicWait.UNFORMED; setChanged(); return;
        }
        try {
            if (outer.contains("arce_process_journal") || outer.contains("arce_process") || outer.contains("arce_machine")) {
                throw new IllegalArgumentException("Unproved prior process roots");
            }
            checkpoint = ClassicCheckpointCodec.decode(outer, definition.kind(), worldPosition);
            var marker = new io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration();
            marker.load(outer, checkpoint.job().isPresent(), checkpoint.job().map(job -> job.recipe().toString()).orElse(null));
            if (!marker.current() || marker.unsupported()) { throw new IllegalArgumentException("Unproved JSON signature marker"); }
            unavailable.clear(); lookupNeeded = true;
        } catch (RuntimeException refused) { repair = true; reason = ClassicWait.REPAIR; }
        if (level instanceof ServerLevel server) { observeUnsafeRoot(server); }
    }
    private void observeUnsafeRoot(ServerLevel server) {
        if (repair && retained != null && !ClassicRuntime.controllerRootsBounded(retained)) {
            ClassicRuntime.observedUnsafeRoot(server, worldPosition);
        }
    }
    @Override public Component getDisplayName() { return Component.translatable("block." + definition.kind().getNamespace() + "." + definition.kind().getPath()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new ClassicControllerMenu(id, this); }
    private static PatternTransform transform(BlockState state) {
        Direction facing = state.getValue(ClassicControllerBlock.FACING);
        return new PatternTransform(switch (facing) {
            case NORTH -> PatternRotation.ZERO; case EAST -> PatternRotation.CLOCKWISE_90;
            case SOUTH -> PatternRotation.CLOCKWISE_180; case WEST -> PatternRotation.CLOCKWISE_270;
            default -> throw new IllegalArgumentException("Controller horizontal facing");
        }, false);
    }
}
