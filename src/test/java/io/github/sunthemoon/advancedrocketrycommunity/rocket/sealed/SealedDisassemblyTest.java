package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SealedDisassemblyTest {
    @Test void aChangedSurvivorAfterTerrainProofCannotElectTheSealedSnapshot() throws Exception {
        var world = new World(2); var run = live(world);
        run.ticks(3); assertEquals(2, world.staged); assertFalse(run.flag("electionSubmitted"));
        world.entity = "OTHER"; run.ticks(8);
        assertFalse(run.flag("electionSubmitted")); assertEquals("LOCKED", world.ram); assertTrue(world.u);
        assertFalse(world.mutations.contains("discard"));
    }
    @Test void completionRetainsUUntilSealedTerrainAndNativeEntityAbsenceAreBothProved() throws Exception {
        var world = new World(3); var run = live(world);
        run.finish(40);
        assertEquals("SEALED", run.result());
        assertEquals(List.of("retain", "stage:0", "stage:1", "stage:2", "elect", "discard", "remove-u"), world.mutations);
        assertFalse(world.u); assertEquals("ABSENT", world.entity);
        assertTrue(Arrays.stream(world.cells).allMatch("OWNED"::equals));
        assertTrue(world.proofs.containsAll(Set.of("PREPARE", "SEALED", "COMPLETE")));
    }

    @Test void neitherRamRecordNorMissingPredecessorProofPermitsStaging() throws Exception {
        var world = new World(2); world.autoAck = false; var run = live(world);
        run.ticks(5); assertTrue(world.u); assertEquals(0, world.staged);
        world.ack = "LOCKED"; world.predecessor = false;
        run.ticks(5); assertEquals(0, world.staged); assertEquals("MATCHING", world.entity);
        world.predecessor = true; world.autoAck = true;
        run.finish(250); assertEquals("SEALED", run.result());
    }

    @Test void failedPartialPlacementErasesOnlyOwnedCellsAndRequiresCertifiedRollback() throws Exception {
        var world = new World(3); world.failStage = 1; var run = live(world);
        run.finish(300);
        assertEquals("ROLLBACK_PROVED", run.result());
        assertEquals(List.of("retain", "stage:0", "erase:0", "remove-u"), world.mutations);
        assertEquals("MATCHING", world.entity); assertFalse(world.u);
        assertTrue(world.proofs.contains("ROLLBACK"));
    }

    @Test void aForeignCellIsNeverErasedOrReplacedDuringRollback() throws Exception {
        var world = new World(2); world.cells[0] = "OWNED"; world.cells[1] = "OTHER";
        world.u = true; world.ram = "LOCKED"; var run = recovery(world, false);
        run.ticks(240);
        assertNull(run.result()); assertTrue(world.u); assertEquals("OTHER", world.cells[1]);
        assertFalse(world.mutations.contains("erase:1")); assertFalse(world.mutations.contains("remove-u"));
        assertEquals("MATCHING", world.entity);
    }

    @Test void restoredSubmissionIsOneWayEvenWhenTheAcknowledgementIsUnknown() throws Exception {
        var world = new World(2); world.unknownElection = true; var run = live(world);
        run.ticks(12); assertTrue(run.flag("electionSubmitted"));
        world.request = false; run.ticks(240);
        assertTrue(world.u); assertEquals("MATCHING", world.entity);
        assertEquals(0, world.erased); assertFalse(world.mutations.contains("discard"));
        world.autoAck = true; world.unknownElection = false;
        run.finish(40); assertEquals("SEALED", run.result());
    }

    @Test void absenceSubmissionKeepsAuthorityUntilItsExactAckAndCurrentNativeProofs() throws Exception {
        var world = new World(2); world.unknownRemoval = true; var run = live(world);
        run.ticks(15); assertFalse(world.u); assertNull(run.result());
        world.proofs.remove("COMPLETE"); world.autoProof = false;
        world.ack = "ABSENT"; run.ticks(5); assertNull(run.result());
        assertEquals(2, world.staged); assertEquals(0, world.erased);
        world.proofs.add("COMPLETE"); run.finish(30); assertEquals("SEALED", run.result());
    }

    @Test void recoverySelectsDOnlyBeforeElectionAndCompletesMissingMembersWithoutD() throws Exception {
        for (boolean elected : new boolean[] {false, true}) {
            for (boolean entityPresent : new boolean[] {false, true}) {
                var world = new World(3); world.u = true; world.ram = elected ? "RESTORED" : "LOCKED";
                world.entity = entityPresent ? "MATCHING" : "ABSENT";
                world.cells[0] = "OWNED"; world.cells[2] = "OWNED";
                var run = recovery(world, elected); run.finish(80);
                boolean rollback = !elected && entityPresent;
                assertEquals(rollback ? "ROLLBACK_PROVED" : "SEALED", run.result());
                assertTrue(Arrays.stream(world.cells).allMatch((rollback ? "EMPTY" : "OWNED")::equals));
                assertEquals(rollback ? "MATCHING" : "ABSENT", world.entity);
                assertFalse(world.u);
            }
        }
    }

    @Test void missingDOnAPlacementFailureCompletesSealedTerrainInsteadOfDiscardingTheSnapshot() throws Exception {
        var world = new World(2); world.failStage = 1; world.loseEntityOnFailure = true; var run = live(world);
        run.ticks(8); world.failStage = -1;
        run.finish(300); assertEquals("SEALED", run.result()); assertEquals(0, world.erased);
    }

    @Test void requestAgeNeverDeletesRetainedAuthorityAndNeverStartsLateRestoration() throws Exception {
        var world = new World(2); world.autoAck = false; var run = live(world);
        run.ticks(4); assertTrue(world.u);
        world.tick = 201; run.ticks(4);
        assertTrue(world.u); assertEquals(0, world.staged); assertNull(run.result());
        world.autoAck = true; run.finish(60); assertEquals("ROLLBACK_PROVED", run.result());
    }

    @Test void aSharedBudgetBoundsLargeFootprintsAcrossSeparateTransactions() throws Exception {
        var first = new World(2048); var second = new World(2048);
        var a = live(first); var b = live(second); var budget = new SealedWorkBudget(); budget.beginTick(0);
        for (int i = 0; i < 300; i++) { a.step(budget); b.step(budget); }
        assertEquals(256, first.inspected + second.inspected);
        assertEquals(0, first.staged + second.staged);
        budget.beginTick(0); a.step(budget); assertEquals(256, first.inspected + second.inspected);
    }

    @Test void refusedRetentionStillNeedsCheckedAbsenceAndNativeRollbackBeforeReleasingD() throws Exception {
        var world = new World(2); world.refuseRetain = true; world.autoAck = false; var run = live(world);
        run.ticks(5); assertFalse(world.u); assertEquals(0, world.staged);
        world.tick = 201; run.ticks(5); assertNull(run.result());
        world.autoAck = true; run.finish(40);
        assertEquals("ROLLBACK_PROVED", run.result()); assertEquals("MATCHING", world.entity);
        assertTrue(world.proofs.contains("ROLLBACK")); assertEquals(0, world.staged);
    }

    @Test void anExpiredPrepareProofCannotAuthorizeTheFirstMutationAfterTheJournalWait() throws Exception {
        var world = new World(2); world.autoAck = false; var run = live(world);
        run.ticks(4); assertTrue(world.u); assertEquals(0, world.staged);
        world.proofs.remove("PREPARE"); world.autoProof = false; world.ack = "LOCKED";
        run.ticks(4); assertEquals(0, world.staged); assertFalse(run.flag("restorationStarted"));
        world.proofs.add("PREPARE"); world.autoAck = true; world.autoProof = true;
        run.finish(40); assertEquals("SEALED", run.result());
    }

    @Test void saveOffDoesNotExtendAnUnretainedRequestsOriginalDeadline() throws Exception {
        var world = new World(2); world.gate = "SAVE_OFF"; var run = live(world);
        run.ticks(1); assertFalse(world.u);
        world.tick = 201; run.ticks(1);
        assertEquals("REJECTED", run.result()); assertFalse(world.u); assertEquals(0, world.staged);
    }

    private static Run live(World world) throws Exception { return new Run(world, null); }
    private static Run recovery(World world, boolean elected) throws Exception { return new Run(world, elected); }

    private static final class Run {
        final World world; final Object transaction;
        Run(World world, Boolean elected) throws Exception {
            this.world = world;
            var type = Class.forName(SealedDisassemblyTest.class.getPackageName() + ".SealedDisassembly");
            var port = Class.forName(type.getName() + "$Port");
            var proxy = Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[] {port}, (object, method, args) -> {
                Object value = world.call(method.getName(), args);
                if (method.getReturnType().isEnum()) {
                    for (Object constant : method.getReturnType().getEnumConstants()) {
                        if (constant.toString().equals(value)) { return constant; }
                    }
                    throw new AssertionError(value);
                }
                return value;
            });
            transaction = elected == null ? type.getConstructor(int.class, port).newInstance(world.cells.length, proxy)
                    : type.getMethod("recover", int.class, port, boolean.class).invoke(null, world.cells.length, proxy, elected);
        }
        boolean step(SealedWorkBudget budget) throws Exception {
            return (boolean) transaction.getClass().getMethod("step", SealedWorkBudget.class).invoke(transaction, budget);
        }
        String result() throws Exception {
            var value = transaction.getClass().getMethod("result").invoke(transaction); return value == null ? null : value.toString();
        }
        boolean flag(String name) throws Exception { return (boolean) transaction.getClass().getMethod(name).invoke(transaction); }
        void ticks(int count) throws Exception {
            var budget = new SealedWorkBudget();
            for (int i = 0; i < count && result() == null; i++) {
                budget.beginTick(world.tick);
                int calls = 0;
                while (step(budget) && calls++ < 256) { }
                if (world.checkpoint && world.autoAck) { world.ack = world.u ? world.ram : "ABSENT"; }
                world.checkpoint = false; world.tick++;
            }
        }
        void finish(int count) throws Exception { ticks(count); assertNotNull(result(), world.mutations.toString()); }
    }

    private static final class World {
        final String[] cells; final List<String> mutations = new ArrayList<>(); final Set<String> proofs = new HashSet<>();
        long tick; int inspected, staged, erased; int failStage = -1;
        boolean u, checkpoint, unknownElection, unknownRemoval, loseEntityOnFailure, refuseRetain;
        boolean autoAck = true, autoProof = true, request = true, predecessor = true;
        String entity = "MATCHING", gate = "READY", ram, ack;
        World(int members) { cells = new String[members]; Arrays.fill(cells, "EMPTY"); }
        Object call(String method, Object[] args) {
            return switch (method) {
                case "now" -> tick;
                case "gate" -> gate;
                case "requestCurrent" -> request;
                case "predecessorAbsent" -> predecessor;
                case "inspect" -> { inspected++; yield cells[(int) args[0]]; }
                case "entity" -> entity;
                case "stage" -> {
                    int index = (int) args[0];
                    assertTrue(proofs.contains("PREPARE"), "Staged without expanded native capacity preflight");
                    assertTrue(u && ram != null && ram.equals(ack), "Staged before exact U presence");
                    assertTrue(predecessor, "Staged before T absence"); assertEquals("EMPTY", cells[index]);
                    if (index == failStage) { if (loseEntityOnFailure) { entity = "ABSENT"; } yield false; }
                    cells[index] = "OWNED"; staged++; mutations.add("stage:" + index); yield true;
                }
                case "erase" -> {
                    int index = (int) args[0]; assertEquals("MATCHING", entity); assertNotEquals("RESTORED", ram);
                    assertEquals("OWNED", cells[index]); cells[index] = "EMPTY"; erased++; mutations.add("erase:" + index); yield true;
                }
                case "discard" -> {
                    assertEquals("RESTORED", ack); assertTrue(proofs.contains("SEALED"));
                    entity = "ABSENT"; mutations.add("discard"); yield true;
                }
                case "retain" -> { if (!u && !refuseRetain) { u = true; ram = "LOCKED"; mutations.add("retain"); } yield null; }
                case "retainedAcknowledged" -> u && "LOCKED".equals(ram) && ram.equals(ack);
                case "elect" -> {
                    if (!"RESTORED".equals(ram)) {
                        assertTrue(proofs.contains("SEALED")); ram = "RESTORED"; mutations.add("elect");
                        if (unknownElection) { autoAck = false; }
                    }
                    yield null;
                }
                case "electedAcknowledged" -> u && "RESTORED".equals(ram) && ram.equals(ack);
                case "removeRecord" -> {
                    assertTrue(proofs.contains("MATCHING".equals(entity) ? "ROLLBACK" : "COMPLETE"));
                    if (u) { u = false; mutations.add("remove-u"); if (unknownRemoval) { autoAck = false; } }
                    yield null;
                }
                case "absentAcknowledged" -> !u && "ABSENT".equals(ack);
                case "checkpoint" -> { checkpoint = true; yield null; }
                case "proofCurrent" -> proofs.contains(args[0].toString());
                case "requestProof" -> { if (autoProof) { proofs.add(args[0].toString()); } yield null; }
                case "held" -> null;
                default -> throw new AssertionError(method);
            };
        }
    }
}
