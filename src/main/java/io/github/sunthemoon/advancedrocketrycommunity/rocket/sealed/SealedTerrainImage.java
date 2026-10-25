package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import com.mojang.serialization.Codec;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.AnvilRecordReader;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

/** Detached full-chunk capacity projection and native readback checks. Never writes a world or an Anvil file. */
public final class SealedTerrainImage {
    private static final Codec<PalettedContainer<BlockState>> STATES = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
    private SealedTerrainImage() { }

    public static Optional<CompoundTag> project(CompoundTag source, List<SealedRocketMember> members, BlockState carrier) {
        try { return project(source, members, carrier, members.stream().map(SealedTerrainImage::frame).toList()); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    /** Recovery may keep exact owned C and fill only air; a native/foreign member still refuses the whole projection. */
    public static Optional<CompoundTag> projectRecovery(CompoundTag source, List<SealedRocketMember> members, BlockState carrier) {
        try { return project(source, members, carrier, members.stream().map(SealedTerrainImage::frame).toList(), true); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    /** Frames may include native/Forge-owned fields of the already prepared, detached carriers. */
    public static Optional<CompoundTag> project(CompoundTag source, List<SealedRocketMember> members, BlockState carrier,
                                               List<CompoundTag> frames) {
        return project(source, members, carrier, frames, false);
    }

    public static Optional<CompoundTag> project(CompoundTag source, List<SealedRocketMember> members, BlockState carrier,
                                               List<CompoundTag> frames, boolean recovery) {
        if (!bounded(source) || frames.size() != members.size()) { return Optional.empty(); }
        try {
            var image = new Image(source.copy());
            if (!image.projectable(members, carrier, recovery)) { return Optional.empty(); }
            for (int index = 0; index < members.size(); index++) {
                var member = members.get(index); var frame = frames.get(index); var at = member.position();
                if (!owned(frame, member) || !BoundedNbt.fits(frame, SealedRocketMember.MAX_BYTES,
                        SealedRocketMember.MAX_DEPTH, SealedRocketMember.MAX_NODES)) { return Optional.empty(); }
                if (image.at.getOrDefault(at, List.of()).isEmpty()) {
                    image.section(at.y() >> 4).set(at.x() & 15, at.y() & 15, at.z() & 15, carrier);
                    image.entities.add(frame.copy());
                }
            }
            image.writeStates();
            return fits(image.root) ? Optional.of(image.root) : Optional.empty();
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    /** Native data-only validation; live matching/placement uses the shared world-inspection cursor separately. */
    public static boolean matches(CompoundTag root, List<SealedRocketMember> members, BlockState carrier, boolean sealed) {
        if (!bounded(root)) { return false; }
        try { return new Image(root).matches(members, carrier, sealed); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    public static CompoundTag frame(SealedRocketMember member) {
        var root = new CompoundTag(); root.putString("id", SealedRocketMember.TYPE.toString());
        root.putInt("x", member.position().x()); root.putInt("y", member.position().y()); root.putInt("z", member.position().z());
        root.put(SealedRocketMember.ROOT, member.encode()); return root;
    }

    private static boolean owned(CompoundTag root, SealedRocketMember member) {
        return root.getString("id").equals(SealedRocketMember.TYPE.toString())
                && member.position().equals(position(root)) && member.encode().equals(root.get(SealedRocketMember.ROOT));
    }
    private static RocketPosition position(CompoundTag root) {
        require(root.contains("x", Tag.TAG_INT) && root.contains("y", Tag.TAG_INT) && root.contains("z", Tag.TAG_INT));
        return new RocketPosition(root.getInt("x"), root.getInt("y"), root.getInt("z"));
    }
    private static boolean bounded(CompoundTag tag) {
        return BoundedNbt.fits(tag, AnvilRecordReader.DECODED_BYTES, 64, 262_144);
    }
    private static boolean fits(CompoundTag tag) {
        if (!bounded(tag)) { return false; }
        // The native NBT writer and region-file zlib compression; this counts bytes without retaining another full buffer.
        try (var stream = new DataOutputStream(new DeflaterOutputStream(new OutputStream() {
            private int bytes;
            @Override public void write(int value) throws IOException { add(1); }
            @Override public void write(byte[] data, int offset, int length) throws IOException { add(length); }
            private void add(int count) throws IOException {
                if (count > AnvilRecordReader.COMPRESSED_BYTES - bytes) { throw new IOException("Expanded chunk exceeds native proof bound"); }
                bytes += count;
            }
        }))) {
            NbtIo.write(tag, stream);
            return true;
        } catch (IOException tooLarge) { return false; }
    }

    private static final class Image {
        final CompoundTag root;
        final ListTag sections, entities;
        final Map<Integer, CompoundTag> encoded = new HashMap<>();
        final Map<Integer, PalettedContainer<BlockState>> decoded = new HashMap<>();
        final Map<RocketPosition, List<CompoundTag>> at = new HashMap<>();
        Image(CompoundTag root) {
            this.root = root;
            require(root.contains("xPos", Tag.TAG_INT) && root.contains("zPos", Tag.TAG_INT));
            sections = list(root, "sections"); entities = list(root, "block_entities");
            for (Tag raw : sections) {
                var section = (CompoundTag) raw;
                require(section.contains("Y", Tag.TAG_BYTE));
                require(encoded.put((int) section.getByte("Y"), section) == null);
            }
            for (Tag raw : entities) {
                var entity = (CompoundTag) raw;
                at.computeIfAbsent(position(entity), key -> new java.util.ArrayList<>()).add(entity);
            }
        }
        boolean matches(List<SealedRocketMember> members, BlockState carrier, boolean sealed) {
            if (members.isEmpty() || members.size() > RocketLimits.MAX_BLOCKS) { return false; }
            var seen = new HashSet<RocketPosition>();
            for (var member : members) {
                var pos = member.position();
                if (!seen.add(pos) || (pos.x() >> 4) != root.getInt("xPos") || (pos.z() >> 4) != root.getInt("zPos")) { return false; }
                var state = section(pos.y() >> 4).get(pos.x() & 15, pos.y() & 15, pos.z() & 15);
                var blockEntities = at.getOrDefault(pos, List.of());
                if (sealed) {
                    if (!state.equals(carrier) || blockEntities.size() != 1 || !owned(blockEntities.get(0), member)) { return false; }
                } else if (!state.isAir() || !blockEntities.isEmpty()) { return false; }
            }
            return true;
        }
        boolean projectable(List<SealedRocketMember> members, BlockState carrier, boolean recovery) {
            if (!recovery) { return matches(members, carrier, false); }
            if (members.isEmpty() || members.size() > RocketLimits.MAX_BLOCKS) { return false; }
            var seen = new HashSet<RocketPosition>();
            for (var member : members) {
                if (!seen.add(member.position()) || !matches(List.of(member), carrier, false)
                        && !matches(List.of(member), carrier, true)) { return false; }
            }
            return true;
        }
        PalettedContainer<BlockState> section(int y) {
            return decoded.computeIfAbsent(y, index -> {
                var encodedSection = encoded.get(index);
                if (encodedSection == null || !encodedSection.contains("block_states")) {
                    return new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(),
                            PalettedContainer.Strategy.SECTION_STATES);
                }
                return STATES.parse(NbtOps.INSTANCE, encodedSection.get("block_states")).result()
                        .orElseThrow(() -> new IllegalArgumentException("Invalid native block states"));
            });
        }
        void writeStates() {
            decoded.forEach((y, states) -> {
                require(y >= Byte.MIN_VALUE && y <= Byte.MAX_VALUE);
                var section = encoded.get(y);
                if (section == null) { section = new CompoundTag(); section.putByte("Y", y.byteValue()); sections.add(section); }
                section.put("block_states", STATES.encodeStart(NbtOps.INSTANCE, states).result()
                        .orElseThrow(() -> new IllegalArgumentException("Cannot encode native block states")));
            });
        }
        private static ListTag list(CompoundTag root, String key) {
            require(root.get(key) instanceof ListTag);
            var list = (ListTag) root.get(key); require(list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND); return list;
        }
    }
    private static void require(boolean value) { if (!value) { throw new IllegalArgumentException("Invalid native terrain image"); } }
}
