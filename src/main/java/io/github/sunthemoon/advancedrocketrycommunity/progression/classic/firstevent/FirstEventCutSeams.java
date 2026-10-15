package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;

/**
 * W44 section 6.1: every gated release-test seam of the world-first cuts. With the cut property absent, or
 * {@code advancedrocketrycommunity.releaseTestHooks} false, every seam returns its production value. With both set,
 * each seam first requires the empty dedicated release-test server ({@link EscrowTestEndpoints#requireEnabled}), so a
 * connected player makes it fail closed. An unknown ID fails at startup. V and X rows use no seam. Writer-thread seams
 * are resolved on the server thread when their policy is built and handed to the writer, as the journal failure policy
 * is today.
 */
public final class FirstEventCutSeams {
    public static final String PROPERTY = "arce.releaseTest.firstEventCut";
    /** Participant-only recovery fixture; never enables a halt or IO failure. */
    public static final String PARTICIPANTS_PROPERTY = "arce.releaseTest.firstEventParticipants";
    /** The release-test halt status of {@code ReleaseTestCommands}. */
    public static final int HALT_STATUS = 75;
    /** The fixed sorted participants of the synthetic M and WARP fixtures, and the later observer who is never one. */
    public static final UUID P1 = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    public static final UUID P2 = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    public static final UUID OBSERVER = UUID.fromString("00000000-0000-4000-8000-0000000000f0");

    /** Server-thread boundaries named by the delivery service, the sources and the Moon completion. */
    public static final String RECEIPT_CONFIRMED = "receipt_confirmed";
    public static final String AWARDS_DISPATCHED = "awards_dispatched";
    public static final String DELIVERED_PUBLISHED = "delivered_published";
    public static final String RECEIPT_BARRIER_FAILED = "receipt_barrier_failed";
    public static final String JOURNAL_UNKNOWN = "journal_unknown";
    public static final String JOURNAL_NOT_COMMITTED = "journal_not_committed";
    public static final String RELOCATION_UNKNOWN = "relocation_unknown";
    public static final String ACKNOWLEDGEMENT_UNKNOWN = "acknowledgement_unknown";
    public static final String WARP_REFUSED = "warp_refused";
    public static final String HELD = "held";
    public static final String FIRST_STEP = "first_step";

    private static final AtomicBoolean ELECTION_MOVED = new AtomicBoolean();
    private static final AtomicBoolean UNRELATED_FAILED = new AtomicBoolean();
    private static final AtomicBoolean RELEASED = new AtomicBoolean();
    private static final AtomicBoolean LATCH_USED = new AtomicBoolean();
    private static FirstEventCutLatch latch;

    private FirstEventCutSeams() {
    }

    /** The headless (H) rows of W44 sections 6.2 to 6.4, with the boundary at which each halts, if any. */
    public enum Cut {
        FM1(null), FM2A(null), FM2B(null), FM3(RECEIPT_CONFIRMED), FM4A(AWARDS_DISPATCHED), FM4B(AWARDS_DISPATCHED),
        FM5(DELIVERED_PUBLISHED), FM6A(JOURNAL_UNKNOWN), FM6B(JOURNAL_UNKNOWN), FM7(null), FM8A(RECEIPT_BARRIER_FAILED),
        FM8B(RECEIPT_BARRIER_FAILED), FM9(null), FM12A(null), FM12B(null),
        FW1(null), FW2(null), FW3(RECEIPT_CONFIRMED), FW4A(AWARDS_DISPATCHED), FW4B(AWARDS_DISPATCHED),
        FW5(DELIVERED_PUBLISHED), FW6A(RELOCATION_UNKNOWN), FW6B(RELOCATION_UNKNOWN), FW7(ACKNOWLEDGEMENT_UNKNOWN),
        FW8A(WARP_REFUSED), FW8B(WARP_REFUSED), FW9(null), FW11(null),
        FP2(HELD), FP3(HELD), FP4(HELD), FP5(null), FP6(null), FP7A(FIRST_STEP), FP7B(FIRST_STEP), FP8(RELOCATION_UNKNOWN),
        FP9(FIRST_STEP), FP10(FIRST_STEP), FP11(FIRST_STEP);

        private final String boundary;

        Cut(String boundary) {
            this.boundary = boundary;
        }

        /** The design's spelling, for example {@code FM2a}. */
        public String id() {
            String name = name();
            char last = name.charAt(name.length() - 1);
            return Character.isLetter(last) && name.length() > 3 && Character.isDigit(name.charAt(name.length() - 2))
                    ? name.substring(0, name.length() - 1) + Character.toLowerCase(last) : name;
        }

        static Optional<Cut> byId(String id) {
            return Arrays.stream(values()).filter(cut -> cut.id().equals(id)).findFirst();
        }

        /** The managed root whose runtime load the load-fault seam fails, if any. */
        Optional<ManagedSavedDataType> loadFault() {
            return switch (this) {
                case FP7A, FP7B, FP9 -> Optional.of(ManagedSavedDataType.CLASSIC_PROGRESSION);
                case FP10 -> Optional.of(ManagedSavedDataType.ROCKET_TRANSFERS);
                case FP11 -> Optional.of(ManagedSavedDataType.STATIONS);
                default -> Optional.empty();
            };
        }

        /** The kind whose boundary halts this cut; FP rows halt on either kind. */
        Optional<FirstEventKind> kind() {
            return name().startsWith("FM") ? Optional.of(FirstEventKind.FIRST_MOON_LANDING)
                    : name().startsWith("FW") ? Optional.of(FirstEventKind.FIRST_STATION_WARP) : Optional.empty();
        }

        /** The "save-all completed" rows run the normal save routine on the server thread before halting. */
        boolean saveBeforeHalt() {
            return this == FM4A || this == FW4A || this == FW6A || this == FW7 || this == FP8;
        }
    }

    /**
     * The configured cut; empty in production. A set property with hooks disabled is ignored, as every release-test
     * property is; a set property with hooks enabled and an unknown ID throws.
     */
    public static Optional<Cut> configured() {
        String id = System.getProperty(PROPERTY, "");
        if (id.isEmpty() || !Boolean.getBoolean(EscrowTestEndpoints.HOOKS)) {
            return Optional.empty();
        }
        return Optional.of(Cut.byId(id).orElseThrow(() -> new IllegalStateException(
                "Unknown first-event release-test cut " + id)));
    }

    /** Startup validation of the property; an unknown ID refuses the session. */
    public static void validateAtStartup(MinecraftServer server) {
        ELECTION_MOVED.set(false);
        UNRELATED_FAILED.set(false);
        RELEASED.set(false);
        LATCH_USED.set(false);
        if (latch != null) { latch.release(); }
        latch = null;
        configured().ifPresent(cut -> {
            EscrowTestEndpoints.requireEnabled(server);
            if (cut == Cut.FM9 || cut == Cut.FP5) { latch = new FirstEventCutLatch(); }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_cut id={} armed", cut.id());
        });
    }

    /** The active cut after the gate; empty in production. */
    static Optional<Cut> active(MinecraftServer server) {
        Optional<Cut> cut = configured();
        cut.ifPresent(ignored -> EscrowTestEndpoints.requireEnabled(server));
        return cut;
    }

    private static boolean is(MinecraftServer server, Cut... cuts) {
        Optional<Cut> cut = configured();
        if (cut.isEmpty() || Arrays.stream(cuts).noneMatch(cut.orElseThrow()::equals)) {
            return false;
        }
        EscrowTestEndpoints.requireEnabled(server);
        return true;
    }

    /**
     * Participant seam: the real capture in production; the fixed sorted [P1, P2] after the gate in a cut run, which
     * has zero connected players (W44 section 6.1, rows M and WARP).
     */
    public static Optional<List<UUID>> participants(MinecraftServer server, FirstEventKind kind) {
        if (active(server).isPresent()) { return Optional.of(List.of(P1, P2)); }
        if (Boolean.getBoolean(EscrowTestEndpoints.HOOKS) && Boolean.getBoolean(PARTICIPANTS_PROPERTY)) {
            EscrowTestEndpoints.requireEnabled(server);
            return Optional.of(List.of(P1, P2));
        }
        return Optional.empty();
    }

    /** Saves the complete terminal DESCENT fixture on S, before any election write can block W. */
    public static void beforeMoonElection(MinecraftServer server, Runnable save) {
        if (is(server, Cut.FM1, Cut.FM2A, Cut.FM6B, Cut.FM7, Cut.FM9)) {
            save.run();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture descent_saved");
        }
    }

    /** Removes a persistent test failure online, without altering roots or production retry timing. */
    static void release(MinecraftServer server) {
        active(server).orElseThrow(() -> new IllegalStateException("No first-event cut is armed"));
        RELEASED.set(true);
        if (latch != null) { latch.release(); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture released");
    }

    /** Advances only the test latch's hard limit; no-op in production. */
    static void fixtureTick(MinecraftServer server) {
        if (latch != null) {
            active(server).orElseThrow();
            latch.tick(server.getTickCount());
        }
    }

    /** Stop cannot wait for an unreleased test latch while draining the journal. */
    static void stopFixture() {
        if (latch != null) { latch.release(); }
    }

    /** Warp visitor count seam: the real saturating count in production; 4,097 for FW8b. */
    public static Optional<Integer> warpVisitorCount(MinecraftServer server) {
        return is(server, Cut.FW8B) ? Optional.of(FirstEventKind.FIRST_STATION_WARP.maxParticipants() + 1)
                : Optional.empty();
    }

    /**
     * Load fault (FP7a, FP7b, FP9, FP10, FP11): throws inside vanilla's read, which logs {@code Error loading saved
     * data} and returns null exactly as for an IO failure; the supplier then builds the UNREADABLE root. The static
     * {@code load} used by the pre-start validator is unchanged, so validation of the same bytes still passes.
     */
    public static void beforeLoad(MinecraftServer server, ManagedSavedDataType type) {
        Optional<Cut> cut = configured();
        if (cut.isEmpty() || cut.orElseThrow().loadFault().filter(type::equals).isEmpty()) {
            return;
        }
        EscrowTestEndpoints.requireEnabled(server);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_cut id={} boundary=load_fault type={}",
                cut.orElseThrow().id(), type.dataName());
        throw new IllegalStateException("Release-test load fault for " + type.dataName());
    }

    /**
     * Boundary halt at a named server-thread point: a no-op in production. The matching cut optionally runs the normal
     * save routine first, then halts with the release-test status.
     */
    public static void boundary(MinecraftServer server, String name, FirstEventKind kind, FirstEvent event) {
        Optional<Cut> configured = configured();
        if (configured.filter(Cut.FW11::equals).isPresent() && kind == FirstEventKind.FIRST_STATION_WARP) {
            EscrowTestEndpoints.requireEnabled(server);
            if (name.equals(AWARDS_DISPATCHED) && event instanceof FirstEvent.StationWarp warp) {
                var data = io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData.get(server);
                data.delete(warp.stationId()).orElseThrow();
                data.flush(server);
                AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture winner_deleted station={}", warp.stationId());
            } else if (name.equals(DELIVERED_PUBLISHED)) {
                halt(Cut.FW11, name, event.eventId());
            }
            return;
        }
        if (configured.isEmpty() || !name.equals(configured.orElseThrow().boundary)
                || configured.orElseThrow().kind().filter(expected -> expected != kind).isPresent()) {
            return;
        }
        Cut cut = configured.orElseThrow();
        EscrowTestEndpoints.requireEnabled(server);
        if (cut.saveBeforeHalt()) {
            server.saveEverything(false, true, true);
        }
        halt(cut, name, event == null ? null : event.eventId());
    }

    /**
     * Station committer and readback reader of the extracted station writer: the atomic policy in production. FW1
     * halts before the move, FW2 moves and halts before publish, FW6a, FW6b and FW7 move then throw with an
     * unreadable readback (FW7 only for the acknowledgement), and FP8 moves and then changes the live winning station
     * through the ordinary mutator so that the publish check fails.
     */
    public static CheckedSavedDataPolicy stationPolicy(MinecraftServer server, Runnable afterMove) {
        ManagedSavedDataType type = ManagedSavedDataType.STATIONS;
        Optional<Cut> cut = active(server);
        if (cut.isEmpty()) {
            return CheckedSavedDataPolicy.atomic(type);
        }
        return switch (cut.orElseThrow()) {
            case FW1 -> new CheckedSavedDataPolicy(type, (staged, target) -> {
                if (stationTransition(staged, target, "ELECTED")) { halt(Cut.FW1, "before_station_move", null); }
                CheckedSavedDataFile.atomicMove(staged, target);
            });
            case FW2 -> new CheckedSavedDataPolicy(type, (staged, target) -> {
                boolean election = stationTransition(staged, target, "ELECTED");
                CheckedSavedDataFile.atomicMove(staged, target);
                if (election) { halt(Cut.FW2, "after_station_move", null); }
            });
            case FW6A, FW6B -> unreadableAfterMove(type, (staged, target) -> stationTransition(staged, target, "ELECTED"));
            case FW7 -> unreadableAfterMove(type, (staged, target) -> stationTransition(staged, target, "DELIVERED"));
            case FP8 -> new CheckedSavedDataPolicy(type, (staged, target) -> {
                boolean election = stationTransition(staged, target, "ELECTED");
                CheckedSavedDataFile.atomicMove(staged, target);
                if (election) { afterMove.run(); }
            });
            default -> CheckedSavedDataPolicy.atomic(type);
        };
    }

    /** Progression committer of the receipt barrier: refuses before the move for FM8a, FM12a and FM12b. */
    public static CheckedSavedDataFile.Committer progressionCommitter(MinecraftServer server) {
        if (is(server, Cut.FM8A, Cut.FM12A, Cut.FM12B)) {
            return (staged, target) -> {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_cut receipt_move_refused");
                throw new IOException("Release-test receipt committer refusal before the move");
            };
        }
        return CheckedSavedDataFile::atomicMove;
    }

    /** Directory force of the station writer and the progression barrier: FM8b throws, in place of the Windows no-op. */
    public static CheckedSavedDataPolicy.DirectoryForce directoryForce(MinecraftServer server) {
        if (is(server, Cut.FM8B)) {
            return directory -> { throw new IOException("Release-test directory force failure"); };
        }
        return CheckedSavedDataPolicy.DirectoryForce.PLATFORM;
    }

    /**
     * The journal checkpoint's directory force, resolved on the server thread for the writer thread: FM2a and FM2b
     * force, then halt on the writer thread after the election generation's move.
     */
    public static CheckedSavedDataPolicy.DirectoryForce journalDirectoryForce(MinecraftServer server) {
        if (server != null && is(server, Cut.FM2A, Cut.FM2B)) {
            Cut cut = configured().orElseThrow();
            return directory -> {
                CheckedSavedDataPolicy.forceDirectory(directory);
                if (ELECTION_MOVED.get()) { halt(cut, "after_election_force", null); }
            };
        }
        return CheckedSavedDataPolicy.DirectoryForce.PLATFORM;
    }

    /**
     * The journal committer cut modes of the existing failure policy, for the election generation only: FM1 halts
     * before the move, FM7 refuses it, FM6b throws an Error before it, FM6a and FP6 move and then throw (FP6 once, for
     * an unrelated generation after DELIVERED), and FM2a and FM2b record the move for the directory-force halt.
     */
    public static Optional<CheckedSavedDataPolicy> journalPolicy(MinecraftServer server) {
        ManagedSavedDataType type = ManagedSavedDataType.ROCKET_TRANSFERS;
        if (server == null || configured().isEmpty()) {
            return Optional.empty();
        }
        Cut cut = active(server).orElseThrow();
        return switch (cut) {
            case FM9, FP5 -> {
                FirstEventCutLatch delay = java.util.Objects.requireNonNull(latch, "The first-event latch was not armed");
                yield Optional.of(new CheckedSavedDataPolicy(type, (staged, target) -> {
                    if ((cut == Cut.FP5 || electionGeneration(staged, target)) && LATCH_USED.compareAndSet(false, true)) {
                        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture latch_wait id={} thread={}",
                                cut.id(), Thread.currentThread().getName());
                        delay.awaitMove();
                        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture latch_released id={}", cut.id());
                    }
                    CheckedSavedDataFile.atomicMove(staged, target);
                }));
            }
            case FM1 -> Optional.of(new CheckedSavedDataPolicy(type, (staged, target) -> {
                if (electionGeneration(staged, target)) { halt(cut, "before_election_move", null); }
                CheckedSavedDataFile.atomicMove(staged, target);
            }));
            case FM2A, FM2B -> Optional.of(new CheckedSavedDataPolicy(type, (staged, target) -> {
                boolean election = electionGeneration(staged, target);
                CheckedSavedDataFile.atomicMove(staged, target);
                if (election) { ELECTION_MOVED.set(true); }
            }));
            case FM7 -> Optional.of(new CheckedSavedDataPolicy(type, (staged, target) -> {
                if (!RELEASED.get() && electionGeneration(staged, target)) {
                    AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_cut election_move_refused");
                    throw new IOException("Release-test election refusal before the move");
                }
                CheckedSavedDataFile.atomicMove(staged, target);
            }));
            case FM6B -> Optional.of(new CheckedSavedDataPolicy(type, (staged, target) -> {
                if (electionGeneration(staged, target)) {
                    throw new Error("Release-test unknown election outcome before the move");
                }
                CheckedSavedDataFile.atomicMove(staged, target);
            }));
            case FM6A -> Optional.of(unreadableAfterMove(type, FirstEventCutSeams::electionGeneration));
            case FP6 -> Optional.of(unreadableAfterMove(type, (staged, target) -> outboxState(staged).equals("DELIVERED")
                    && outboxState(target).equals("DELIVERED") && UNRELATED_FAILED.compareAndSet(false, true)));
            default -> Optional.empty();
        };
    }

    /** The generation a cut selects, from the staged candidate and the current target. */
    @FunctionalInterface
    private interface Selection {
        boolean test(Path staged, Path target) throws IOException;
    }

    /** A policy whose committer moves the candidate and then throws, with a readback that reports it unreadable. */
    private static CheckedSavedDataPolicy unreadableAfterMove(ManagedSavedDataType type, Selection selected) {
        AtomicBoolean unreadable = new AtomicBoolean();
        return new CheckedSavedDataPolicy(type, (staged, target) -> {
            boolean cut = selected.test(staged, target);
            CheckedSavedDataFile.atomicMove(staged, target);
            if (cut) {
                unreadable.set(true);
                throw new IOException("Release-test failure after the move");
            }
        }, (file, rootType) -> {
            if (unreadable.getAndSet(false)) {
                throw new UncheckedIOException(new IOException("Release-test unreadable readback"));
            }
            return CheckedSavedDataFile.readPayload(file, rootType);
        });
    }

    private static boolean electionGeneration(Path staged, Path target) throws IOException {
        return outboxState(staged).equals("ELECTED") && !outboxState(target).equals("ELECTED");
    }

    private static String outboxState(Path file) throws IOException {
        try {
            return CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.ROCKET_TRANSFERS)
                    .map(payload -> payload.getCompound(ManagedSavedDataType.FIRST_MOON_LANDING_KEY).getString("state"))
                    .orElse("EMPTY");
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    /** The station write that moves the first-warp slot to {@code state}: the election or the acknowledgement. */
    private static boolean stationTransition(Path staged, Path target, String state) {
        return stationOutbox(staged).equals(state) && !stationOutbox(target).equals(state);
    }

    private static String stationOutbox(Path file) {
        return CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.STATIONS)
                .map(payload -> payload.getCompound(ManagedSavedDataType.FIRST_STATION_WARP_KEY).getString("state"))
                .orElse("EMPTY");
    }

    /** Logs the boundary marker and halts with the release-test status, on whichever thread reached it. */
    static void halt(Cut cut, String boundary, UUID event) {
        String line = "ARCE_RELEASE_TEST first_event_cut id=" + cut.id() + " boundary=" + boundary + " event=" + event
                + " thread=" + Thread.currentThread().getName();
        AdvancedRocketryCommunity.LOGGER.info(line);
        System.out.println(line);
        System.out.flush();
        Runtime.getRuntime().halt(HALT_STATUS);
    }
}
