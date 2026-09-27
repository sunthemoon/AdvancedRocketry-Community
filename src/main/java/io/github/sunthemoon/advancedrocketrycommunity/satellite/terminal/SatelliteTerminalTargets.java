package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Detached menu catalog. Target interning keeps maximum inputs below Forge's open-screen limit. */
public record SatelliteTerminalTargets(long generation, List<Entry> definitions) {
    private static final int FORMAT_MARKER = -1;
    private static final int FORMAT_VERSION = 1;

    public SatelliteTerminalTargets {
        definitions = List.copyOf(definitions);
        if (generation < 0 || definitions.size() > 16
                || definitions.stream().map(Entry::id).distinct().count() != definitions.size()
                || definitions.stream().flatMap(entry -> entry.targets().stream()).distinct().count() > 128) {
            throw new IllegalArgumentException("Satellite menu catalog exceeds bounds or contains duplicate IDs");
        }
    }

    public static SatelliteTerminalTargets current() {
        return new SatelliteTerminalTargets(SatelliteRuntime.catalogGeneration(), SatelliteRuntime.catalog()
                .map(catalog -> catalog.definitions().stream().map(Entry::from).toList()).orElse(List.of()));
    }

    public int indexOf(ResourceLocation id) {
        for (int index = 0; index < definitions.size(); index++) {
            if (definitions.get(index).id().equals(id)) { return index; }
        }
        return -1;
    }

    public List<ResourceLocation> targets(int definitionIndex) {
        return definitionIndex < 0 || definitionIndex >= definitions.size()
                ? List.of() : definitions.get(definitionIndex).targets();
    }

    public void write(FriendlyByteBuf buffer) {
        List<ResourceLocation> targets = definitions.stream().flatMap(entry -> entry.targets().stream()).distinct().toList();
        buffer.writeVarInt(FORMAT_MARKER);
        buffer.writeVarInt(FORMAT_VERSION);
        buffer.writeLong(generation);
        buffer.writeVarInt(targets.size());
        targets.forEach(id -> writeId(buffer, id));
        buffer.writeVarInt(definitions.size());
        for (Entry entry : definitions) {
            writeId(buffer, entry.id());
            buffer.writeVarInt(entry.targets().size());
            entry.targets().forEach(target -> buffer.writeVarInt(targets.indexOf(target)));
        }
    }

    public static SatelliteTerminalTargets read(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT_MARKER || buffer.readVarInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported satellite menu format; update both host and client");
        }
        long generation = buffer.readLong();
        int targetCount = count(buffer, 128);
        List<ResourceLocation> targets = new ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) { targets.add(readId(buffer)); }
        if (new HashSet<>(targets).size() != targets.size()) {
            throw new IllegalArgumentException("Duplicate satellite menu target ID");
        }
        int definitionCount = count(buffer, 16);
        List<Entry> definitions = new ArrayList<>(definitionCount);
        for (int i = 0; i < definitionCount; i++) {
            ResourceLocation id = readId(buffer);
            int size = count(buffer, 16);
            List<ResourceLocation> allowed = new ArrayList<>(size);
            for (int j = 0; j < size; j++) {
                int index = buffer.readVarInt();
                if (index < 0 || index >= targets.size()) { throw new IllegalArgumentException("Invalid satellite target index"); }
                allowed.add(targets.get(index));
            }
            definitions.add(new Entry(id, allowed));
        }
        return new SatelliteTerminalTargets(generation, definitions);
    }

    private static int count(FriendlyByteBuf buffer, int maximum) {
        int value = buffer.readVarInt();
        if (value < 0 || value > maximum) { throw new IllegalArgumentException("Satellite menu count exceeds bounds"); }
        return value;
    }

    private static void writeId(FriendlyByteBuf buffer, ResourceLocation id) { buffer.writeUtf(id.toString(), 128); }
    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        return Objects.requireNonNull(ResourceLocation.tryParse(buffer.readUtf(128)), "Invalid satellite menu ID");
    }

    public record Entry(ResourceLocation id, List<ResourceLocation> targets) {
        public Entry {
            Objects.requireNonNull(id, "id");
            targets = List.copyOf(targets);
            if (id.toString().length() > 128 || targets.isEmpty() || targets.size() > 16
                    || new HashSet<>(targets).size() != targets.size()
                    || targets.stream().anyMatch(target -> target.toString().length() > 128)) {
                throw new IllegalArgumentException("Invalid satellite menu definition");
            }
        }
        private static Entry from(SatelliteDefinition definition) { return new Entry(definition.id(), definition.allowedTargets()); }
    }
}
