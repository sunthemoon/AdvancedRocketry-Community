package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResourcesCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockControllerNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Independent strict machine schema 1. Unsupported roots are retained by the lifecycle adapter. */
final class ClassicCheckpointCodec {
    static final String ROOT = "arce_classic_machine";
    static final int MAX_BYTES = 65_536;
    private static final Set<String> FIELDS = Set.of("schema_version", "kind", "level", "position", "structure",
            "batch_ordinal", "last_transaction", "job", "claims");
    private static final Set<String> JOB_FIELDS = Set.of("recipe", "signature", "duration", "energy_per_tick", "ticks", "energy");

    static boolean bounded(Tag raw) { return BoundedNbt.fits(raw, MAX_BYTES, 16, 8_192); }
    /** Save admission checks present roots; required-field validation belongs to decode. */
    static boolean rootsWritable(CompoundTag entity) {
        if (entity.contains(ROOT) && !bounded(entity.get(ROOT))
                || entity.contains(ClassicResourcesCodec.ROOT) && !ClassicResourcesCodec.bounded(entity.get(ClassicResourcesCodec.ROOT))
                || !io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration.bounded(entity)) {
            return false;
        }
        for (String key : java.util.List.of("arce_process_journal", "arce_process", "arce_machine")) {
            if (entity.contains(key) && !bounded(entity.get(key))) { return false; }
        }
        return true;
    }
    static CompoundTag encode(ClassicCheckpoint checkpoint, ResourceLocation kind, ResourceLocation level, BlockPos position) {
        var root = new CompoundTag(); root.putInt("schema_version", 1); root.putString("kind", kind.toString());
        root.putString("level", level.toString()); root.putLong("position", position.asLong());
        root.put("structure", MultiblockControllerNbtCodec.encode(checkpoint.structure()));
        var claims = new ListTag();
        checkpoint.claimedParts().forEach(positionValue -> {
            var claim = new CompoundTag(); claim.putInt("x", positionValue.getX());
            claim.putInt("y", positionValue.getY()); claim.putInt("z", positionValue.getZ()); claims.add(claim);
        });
        root.put("claims", claims);
        root.putLong("batch_ordinal", checkpoint.batchOrdinal());
        root.putString("last_transaction", checkpoint.lastTransaction().map(UUID::toString).orElse(""));
        var job = new CompoundTag();
        checkpoint.job().ifPresent(value -> {
            job.putString("recipe", value.recipe().toString()); job.putString("signature", value.signature());
            job.putInt("duration", value.duration()); job.putInt("energy_per_tick", value.energyPerTick());
            job.putInt("ticks", value.progress().progressTicks()); job.putLong("energy", value.progress().consumedEnergy());
            if (value.ordinal() > 0) { job.putLong("ordinal", value.ordinal()); }
            value.outcome().ifPresent(outcome -> {
                var chosen = new CompoundTag(); chosen.putString("transaction", outcome.transaction().toString());
                var outputs = new ListTag();
                for (var row : outcome.outputs()) {
                    var entry = new CompoundTag(); entry.putString("item", row.itemId().toString());
                    entry.putInt("count", row.count()); outputs.add(entry);
                }
                chosen.put("outputs", outputs); job.put("outcome", chosen);
            });
        });
        root.put("job", job);
        if (!bounded(root)) { throw new IllegalArgumentException("Machine checkpoint exceeds bounds"); }
        return root;
    }

    static ClassicCheckpoint decode(CompoundTag outer, ResourceLocation kind, BlockPos position) {
        Tag raw = outer.get(ROOT);
        if (!bounded(raw) || !(raw instanceof CompoundTag root) || !root.getAllKeys().equals(FIELDS)) {
            throw new IllegalArgumentException("Machine root structure");
        }
        require(root, "schema_version", Tag.TAG_INT); require(root, "kind", Tag.TAG_STRING);
        require(root, "level", Tag.TAG_STRING); require(root, "position", Tag.TAG_LONG);
        require(root, "structure", Tag.TAG_COMPOUND); require(root, "batch_ordinal", Tag.TAG_LONG);
        require(root, "last_transaction", Tag.TAG_STRING); require(root, "job", Tag.TAG_COMPOUND);
        require(root, "claims", Tag.TAG_LIST);
        if (root.getInt("schema_version") != 1 || !root.getString("kind").equals(kind.toString())
                || root.getLong("position") != position.asLong() || root.getString("level").length() > 128
                || ResourceLocation.tryParse(root.getString("level")) == null
                || !ResourceLocation.tryParse(root.getString("level")).toString().equals(root.getString("level"))) {
            throw new IllegalArgumentException("Machine schema/anchor");
        }
        CompoundTag structureOuter = new CompoundTag(); structureOuter.put(MultiblockControllerNbtCodec.ROOT, root.get("structure"));
        var structure = MultiblockControllerNbtCodec.decode(structureOuter).value().orElseThrow();
        if (structure.selectedTransform().mirroredLocalX() || structure.partPositions().size() > 64) {
            throw new IllegalArgumentException("Family formation limits");
        }
        var claims = (ListTag) root.get("claims"); var positions = new LinkedHashSet<BlockPos>();
        if (claims.size() > 64 || !claims.isEmpty() && claims.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Claim bounds/types");
        }
        for (Tag value : claims) {
            var claim = (CompoundTag) value;
            if (!claim.getAllKeys().equals(Set.of("x", "y", "z"))) { throw new IllegalArgumentException("Claim fields"); }
            require(claim, "x", Tag.TAG_INT); require(claim, "y", Tag.TAG_INT); require(claim, "z", Tag.TAG_INT);
            if (!positions.add(new BlockPos(claim.getInt("x"), claim.getInt("y"), claim.getInt("z")))) {
                throw new IllegalArgumentException("Duplicate claim");
            }
        }
        var resources = ClassicResourcesCodec.decode(outer.get(ClassicResourcesCodec.ROOT), structure.machineInstanceId())
                .value().orElseThrow(() -> new IllegalArgumentException("Missing or unsupported controller resources"));
        CompoundTag job = root.getCompound("job"); Optional<ClassicCheckpoint.Job> decodedJob = Optional.empty();
        if (!job.isEmpty()) {
            var expected = new java.util.HashSet<>(JOB_FIELDS);
            if (job.contains("outcome")) { expected.add("outcome"); }
            if (job.contains("ordinal")) {
                expected.add("ordinal"); require(job, "ordinal", Tag.TAG_LONG);
                if (job.getLong("ordinal") < 1 || root.getLong("batch_ordinal") == Long.MAX_VALUE
                        || job.getLong("ordinal") != root.getLong("batch_ordinal") + 1) {
                    throw new IllegalArgumentException("Job ordinal sequence");
                }
            }
            if (!job.getAllKeys().equals(expected)) { throw new IllegalArgumentException("Active job fields"); }
            require(job, "recipe", Tag.TAG_STRING); require(job, "signature", Tag.TAG_STRING);
            require(job, "duration", Tag.TAG_INT); require(job, "energy_per_tick", Tag.TAG_INT);
            require(job, "ticks", Tag.TAG_INT); require(job, "energy", Tag.TAG_LONG);
            ResourceLocation recipe = ResourceLocation.tryParse(job.getString("recipe"));
            if (recipe == null || !recipe.toString().equals(job.getString("recipe"))) { throw new IllegalArgumentException("Job recipe ID"); }
            decodedJob = Optional.of(new ClassicCheckpoint.Job(recipe, job.getString("signature"), job.getInt("duration"),
                    job.getInt("energy_per_tick"), new ProcessProgress(recipe.toString(), job.getInt("ticks"), job.getLong("energy")),
                    decodeOutcome(job), job.getLong("ordinal")));
        }
        String transaction = root.getString("last_transaction");
        if (transaction.length() > 36) { throw new IllegalArgumentException("Transaction ID length"); }
        Optional<UUID> transactionId = transaction.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(transaction));
        if (transactionId.isPresent() && !transactionId.orElseThrow().toString().equals(transaction)) {
            throw new IllegalArgumentException("Noncanonical transaction ID");
        }
        if (resources.revision() == Long.MAX_VALUE || root.getLong("batch_ordinal") == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Exhausted controller counters require repair");
        }
        return new ClassicCheckpoint(structure, positions, resources, decodedJob, root.getLong("batch_ordinal"), transactionId);
    }
    private static Optional<ClassicCheckpoint.Outcome> decodeOutcome(CompoundTag job) {
        if (!job.contains("outcome")) { return Optional.empty(); }
        require(job, "outcome", Tag.TAG_COMPOUND); var root = job.getCompound("outcome");
        if (!root.getAllKeys().equals(Set.of("transaction", "outputs"))) { throw new IllegalArgumentException("Outcome fields"); }
        require(root, "transaction", Tag.TAG_STRING); require(root, "outputs", Tag.TAG_LIST);
        String id = root.getString("transaction");
        if (id.length() != 36 || !UUID.fromString(id).toString().equals(id)) { throw new IllegalArgumentException("Outcome transaction"); }
        var list = (ListTag) root.get("outputs");
        if (list.size() > 4 || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) { throw new IllegalArgumentException("Outcome bound/type"); }
        var outputs = new java.util.ArrayList<io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicItemOutput>();
        for (Tag raw : list) {
            var row = (CompoundTag) raw;
            if (!row.getAllKeys().equals(Set.of("item", "count"))) { throw new IllegalArgumentException("Outcome entry fields"); }
            require(row, "item", Tag.TAG_STRING); require(row, "count", Tag.TAG_INT);
            var item = ResourceLocation.tryParse(row.getString("item"));
            if (item == null || !item.toString().equals(row.getString("item"))) { throw new IllegalArgumentException("Outcome Item ID"); }
            outputs.add(new io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicItemOutput(item, row.getInt("count")));
        }
        return Optional.of(new ClassicCheckpoint.Outcome(UUID.fromString(id), outputs));
    }
    private static void require(CompoundTag root, String key, int type) {
        if (!root.contains(key, type)) { throw new IllegalArgumentException("Machine field type: " + key); }
    }
    private ClassicCheckpointCodec() { }
}
