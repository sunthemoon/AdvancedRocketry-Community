package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Kind-specific mission fields (ADR-050 §3). */
public sealed interface MissionPayload {
    Pattern HEX16 = Pattern.compile("[0-9a-f]{16}");

    MissionKind kind();

    /** Research credited to the owner's account on claim (ADR-010/037). */
    record Data(int researchYield, int discoveryCost, boolean discoveryRequired) implements MissionPayload {
        public Data {
            if (researchYield <= 0 || researchYield > SatelliteLimits.MAX_RESEARCH_PER_MISSION) {
                throw new IllegalArgumentException("Mission research yield is outside fixed bounds");
            }
            if (discoveryCost <= 0 || discoveryCost > researchYield) {
                throw new IllegalArgumentException("Mission discovery cost is invalid");
            }
        }

        @Override
        public MissionKind kind() {
            return MissionKind.DATA;
        }
    }

    /** Instances created at survey start and the fingerprint of the candidate set (ADR-052 §4). */
    record Survey(List<UUID> instances, String candidateFingerprint) implements MissionPayload {
        public Survey {
            Objects.requireNonNull(instances, "instances");
            Objects.requireNonNull(candidateFingerprint, "candidateFingerprint");
            instances = List.copyOf(instances);
            if (instances.isEmpty() || instances.size() > SatelliteLimits.MAX_INSTANCES_PER_SURVEY
                    || new HashSet<>(instances).size() != instances.size()) {
                throw new IllegalArgumentException("Survey instance list is invalid");
            }
            if (!HEX16.matcher(candidateFingerprint).matches()) {
                throw new IllegalArgumentException("Survey candidate fingerprint is invalid");
            }
        }

        @Override
        public MissionKind kind() {
            return MissionKind.SURVEY;
        }
    }

    /** Asteroid or gas reward delivered through a bound terminal (ADR-051 §5–§9). */
    record Resource(
            MissionKind kind,
            List<RewardEntry> reward,
            UUID boundTerminal,
            Optional<TerminalLocation> boundTerminalDisplay,
            boolean rebound,
            Optional<UUID> paidTerminal,
            boolean acknowledged,
            OptionalLong ackEpoch
    ) implements MissionPayload {
        public Resource {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(boundTerminal, "boundTerminal");
            Objects.requireNonNull(boundTerminalDisplay, "boundTerminalDisplay");
            Objects.requireNonNull(paidTerminal, "paidTerminal");
            Objects.requireNonNull(ackEpoch, "ackEpoch");
            if (!kind.resource()) {
                throw new IllegalArgumentException("Resource payload needs a resource kind");
            }
            reward = RewardEntry.validated(reward, SatelliteLimits.MAX_REWARD_ENTRIES, SatelliteLimits.MAX_REWARD_ITEMS);
            if (acknowledged != ackEpoch.isPresent() || (acknowledged && paidTerminal.isEmpty())) {
                throw new IllegalArgumentException("Acknowledgement needs a paying terminal and an epoch");
            }
            if (ackEpoch.isPresent() && ackEpoch.getAsLong() < 0L) {
                throw new IllegalArgumentException("Acknowledgement epoch cannot be negative");
            }
        }

        /** Paid at this terminal and not yet acknowledged (ADR-051 section 6). */
        public Resource paidAt(UUID terminal) {
            return new Resource(kind, reward, boundTerminal, boundTerminalDisplay, rebound, Optional.of(terminal), false,
                    OptionalLong.empty());
        }

        /** The paying terminal saw its receipt persisted at this epoch (ADR-051 section 7). */
        public Resource acknowledgedAt(long epoch) {
            return new Resource(kind, reward, boundTerminal, boundTerminalDisplay, rebound, paidTerminal, true,
                    OptionalLong.of(epoch));
        }

        /** Bound to another terminal (an operator rebind, or the bind-back of section 7). */
        public Resource boundTo(UUID terminal, Optional<TerminalLocation> display, boolean reboundNow) {
            return new Resource(kind, reward, terminal, display, reboundNow, paidTerminal, acknowledged, ackEpoch);
        }
    }

    /** Display-only position of a bound terminal; the level ID is bounded like every stored ID (C9-M3). */
    record TerminalLocation(ResourceLocation level, BlockPos pos) {
        public static final int MAX_LEVEL_CHARS = 128;

        public TerminalLocation {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(pos, "pos");
            if (level.toString().length() > MAX_LEVEL_CHARS) {
                throw new IllegalArgumentException("Terminal level ID exceeds " + MAX_LEVEL_CHARS + " characters");
            }
        }

        /** The location, or empty when the level ID is too long to store (it is display-only). */
        public static Optional<TerminalLocation> of(ResourceLocation level, BlockPos pos) {
            return level.toString().length() > MAX_LEVEL_CHARS ? Optional.empty()
                    : Optional.of(new TerminalLocation(level, pos));
        }
    }
}
