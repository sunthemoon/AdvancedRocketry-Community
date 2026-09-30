package io.github.sunthemoon.advancedrocketrycommunity.satellite.network;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/**
 * ADR-049 section 10: the server-selected terminal view. Every selection carries its position and list
 * size, so the client never sends an index; the client only displays it for the open menu it names.
 */
public record SatelliteTerminalViewPacket(
        int containerId,
        Optional<SatelliteKind> kind,
        Selection definition,
        Selection target,
        Optional<ResourceLocation> orbitBody,
        Selection instance,
        Optional<InstanceView> instanceView,
        Selection product,
        int missionPage,
        int missionPages,
        List<MissionSummary> missions,
        List<RewardEntry> buffer
) {
    /** ADR-049 section 10 bound; the worst case with 128-character IDs is about 8.6 KiB. */
    public static final int MAX_BYTES = 12 * 1024;
    public static final int MAX_ID_CHARS = 128;
    public static final int MAX_SELECTION_SIZE = SatelliteLimits.MAX_INSTANCES;
    public static final int MAX_MISSIONS_PER_PAGE = 8;
    public static final int MAX_BUFFER_ENTRIES = 32;
    public static final int MAX_BUFFER_ITEMS = 3_456;

    public SatelliteTerminalViewPacket {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(orbitBody, "orbitBody");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(instanceView, "instanceView");
        Objects.requireNonNull(product, "product");
        missions = List.copyOf(missions);
        buffer = List.copyOf(buffer);
        orbitBody.ifPresent(id -> requireId(id, "orbit body"));
        if (missionPages < 0 || missionPages > MAX_SELECTION_SIZE
                || missionPage < 0 || missionPage >= Math.max(1, missionPages)
                || missions.size() > MAX_MISSIONS_PER_PAGE
                || instanceView.isPresent() != instance.id().isPresent()) {
            throw new IllegalArgumentException("Satellite terminal view is outside its bounds");
        }
        if (!buffer.isEmpty()) {
            RewardEntry.validated(buffer, MAX_BUFFER_ENTRIES, MAX_BUFFER_ITEMS);
            buffer.forEach(entry -> requireId(entry.item(), "buffer item"));
        }
    }

    /** A view with no selection, used before the chip or catalog provides one. */
    public static SatelliteTerminalViewPacket empty(int containerId) {
        return new SatelliteTerminalViewPacket(containerId, Optional.empty(), Selection.NONE, Selection.NONE,
                Optional.empty(), Selection.NONE, Optional.empty(), Selection.NONE, 0, 0, List.of(), List.of());
    }

    public static void encode(SatelliteTerminalViewPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId());
        buffer.writeByte(packet.kind().map(value -> value.ordinal() + 1).orElse(0));
        packet.definition().write(buffer);
        packet.target().write(buffer);
        writeOptionalId(buffer, packet.orbitBody());
        packet.instance().write(buffer);
        packet.instanceView().ifPresent(view -> view.write(buffer));
        packet.product().write(buffer);
        buffer.writeVarInt(packet.missionPage());
        buffer.writeVarInt(packet.missionPages());
        buffer.writeVarInt(packet.missions().size());
        packet.missions().forEach(summary -> summary.write(buffer));
        writeEntries(buffer, packet.buffer());
    }

    public static SatelliteTerminalViewPacket decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) {
            throw new DecoderException("Satellite terminal view exceeds " + MAX_BYTES + " bytes");
        }
        try {
            int containerId = buffer.readVarInt();
            int kindValue = buffer.readUnsignedByte();
            if (kindValue > SatelliteKind.values().length) {
                throw new DecoderException("Unknown satellite kind in the terminal view");
            }
            Optional<SatelliteKind> kind = kindValue == 0 ? Optional.empty()
                    : Optional.of(SatelliteKind.values()[kindValue - 1]);
            Selection definition = Selection.read(buffer);
            Selection target = Selection.read(buffer);
            Optional<ResourceLocation> orbitBody = readOptionalId(buffer);
            Selection instance = Selection.read(buffer);
            Optional<InstanceView> instanceView = instance.id().isPresent()
                    ? Optional.of(InstanceView.read(buffer)) : Optional.empty();
            Selection product = Selection.read(buffer);
            int page = buffer.readVarInt();
            int pages = buffer.readVarInt();
            int count = count(buffer, MAX_MISSIONS_PER_PAGE);
            List<MissionSummary> missions = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                missions.add(MissionSummary.read(buffer));
            }
            List<RewardEntry> entries = readEntries(buffer, MAX_BUFFER_ENTRIES);
            if (buffer.isReadable()) {
                throw new DecoderException("Trailing bytes after the satellite terminal view");
            }
            return new SatelliteTerminalViewPacket(containerId, kind, definition, target, orbitBody, instance,
                    instanceView, product, page, pages, missions, entries);
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            throw new DecoderException("Malformed satellite terminal view", exception);
        }
    }

    public static void handle(SatelliteTerminalViewPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            SatelliteTerminalViewCache.accept(packet);
        }
        context.setPacketHandled(true);
    }

    static void requireId(ResourceLocation id, String name) {
        if (id.toString().length() > MAX_ID_CHARS) {
            throw new IllegalArgumentException("Satellite terminal view " + name + " exceeds " + MAX_ID_CHARS + " characters");
        }
    }

    static void writeId(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), MAX_ID_CHARS);
    }

    static ResourceLocation readId(FriendlyByteBuf buffer) {
        ResourceLocation id = ResourceLocation.tryParse(buffer.readUtf(MAX_ID_CHARS));
        if (id == null) {
            throw new DecoderException("Invalid identifier in the satellite terminal view");
        }
        return id;
    }

    static void writeOptionalId(FriendlyByteBuf buffer, Optional<ResourceLocation> id) {
        buffer.writeBoolean(id.isPresent());
        id.ifPresent(value -> writeId(buffer, value));
    }

    static Optional<ResourceLocation> readOptionalId(FriendlyByteBuf buffer) {
        byte present = buffer.readByte();
        if (present != 0 && present != 1) {
            throw new DecoderException("Satellite terminal view presence must be 0 or 1");
        }
        return present == 1 ? Optional.of(readId(buffer)) : Optional.empty();
    }

    static int count(FriendlyByteBuf buffer, int maximum) {
        int value = buffer.readVarInt();
        if (value < 0 || value > maximum) {
            throw new DecoderException("Satellite terminal view count exceeds its bound");
        }
        return value;
    }

    static void writeEntries(FriendlyByteBuf buffer, List<RewardEntry> entries) {
        buffer.writeVarInt(entries.size());
        for (RewardEntry entry : entries) {
            writeId(buffer, entry.item());
            buffer.writeVarInt(entry.count());
        }
    }

    static List<RewardEntry> readEntries(FriendlyByteBuf buffer, int maximum) {
        int count = count(buffer, maximum);
        List<RewardEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new RewardEntry(readId(buffer), buffer.readVarInt()));
        }
        return entries;
    }

    /** A server-side selection: the chosen ID (if any), its position and the size of its list. */
    public record Selection(Optional<ResourceLocation> id, int position, int size) {
        public static final Selection NONE = new Selection(Optional.empty(), 0, 0);

        public Selection {
            Objects.requireNonNull(id, "id");
            id.ifPresent(value -> requireId(value, "selection"));
            if (size < 0 || size > MAX_SELECTION_SIZE || position < 0 || position >= Math.max(1, size)
                    || id.isPresent() && size == 0) {
                throw new IllegalArgumentException("Satellite terminal selection is outside its bounds");
            }
        }

        public static Selection of(List<ResourceLocation> values, int position) {
            if (values.isEmpty()) {
                return NONE;
            }
            int index = Math.floorMod(position, values.size());
            return new Selection(Optional.of(values.get(index)), index, values.size());
        }

        void write(FriendlyByteBuf buffer) {
            writeOptionalId(buffer, id);
            buffer.writeVarInt(position);
            buffer.writeVarInt(size);
        }

        static Selection read(FriendlyByteBuf buffer) {
            Optional<ResourceLocation> id = readOptionalId(buffer);
            return new Selection(id, buffer.readVarInt(), buffer.readVarInt());
        }
    }

    /** The selected logical asteroid with its full yield (ADR-051 section 2). */
    public record InstanceView(UUID instanceId, int expiresInSeconds, List<RewardEntry> yield) {
        public InstanceView {
            Objects.requireNonNull(instanceId, "instanceId");
            yield = RewardEntry.validated(yield, SatelliteLimits.MAX_REWARD_ENTRIES, SatelliteLimits.MAX_YIELD_ITEMS);
            yield.forEach(entry -> requireId(entry.item(), "yield item"));
            if (expiresInSeconds < 0) {
                throw new IllegalArgumentException("Instance expiry must not be negative");
            }
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(instanceId);
            buffer.writeVarInt(expiresInSeconds);
            writeEntries(buffer, yield);
        }

        static InstanceView read(FriendlyByteBuf buffer) {
            return new InstanceView(buffer.readUUID(), buffer.readVarInt(),
                    readEntries(buffer, SatelliteLimits.MAX_REWARD_ENTRIES));
        }
    }

    /** One bound-mission summary on the current page. */
    public record MissionSummary(UUID missionId, MissionKind kind, MissionStatus status, ResourceLocation target,
                                 int remainingSeconds, int rewardItems) {
        public MissionSummary {
            Objects.requireNonNull(missionId, "missionId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(target, "target");
            requireId(target, "mission target");
            if (remainingSeconds < 0 || rewardItems < 0) {
                throw new IllegalArgumentException("Mission summary values must not be negative");
            }
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(missionId);
            buffer.writeByte(kind.ordinal());
            buffer.writeByte(status.ordinal());
            writeId(buffer, target);
            buffer.writeVarInt(remainingSeconds);
            buffer.writeVarInt(rewardItems);
        }

        static MissionSummary read(FriendlyByteBuf buffer) {
            UUID missionId = buffer.readUUID();
            int kind = buffer.readUnsignedByte();
            int status = buffer.readUnsignedByte();
            if (kind >= MissionKind.values().length || status >= MissionStatus.values().length) {
                throw new DecoderException("Unknown mission kind or status in the terminal view");
            }
            return new MissionSummary(missionId, MissionKind.values()[kind], MissionStatus.values()[status],
                    readId(buffer), buffer.readVarInt(), buffer.readVarInt());
        }
    }
}
