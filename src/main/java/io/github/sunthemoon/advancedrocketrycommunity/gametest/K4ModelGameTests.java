package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorAnchorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.FormedModelController;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.K4ModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Isolated loaded fixtures; one callback registered at setup, with terminal cleanup on all outcomes. */
@GameTestHolder(ModIdentity.MOD_ID) @PrefixGameTestTemplate(false)
public final class K4ModelGameTests {
    private K4ModelGameTests() { }
    @GameTest(template="empty",batch="w241_black_hole_model",timeoutTicks=240)
    public static void blackHoleProjectsItsExistingFormation(GameTestHelper h) { exercise(h,"black_hole_generator",0,false); }
    @GameTest(template="empty",batch="w241_orbital_model",timeoutTicks=240)
    public static void orbitalDrillProjectsItsExistingFormation(GameTestHelper h) { exercise(h,"orbital_laser_drill",1,false); }
    @GameTest(template="empty",batch="w241_precision_model",timeoutTicks=240)
    public static void precisionAssemblerProjectsItsExistingFormation(GameTestHelper h) { exercise(h,"precision_assembler",2,false); }
    @GameTest(template="empty",batch="w241_railgun_model",timeoutTicks=240)
    public static void railgunProjectsItsExistingFormation(GameTestHelper h) { exercise(h,"railgun",3,false); }
    @GameTest(template="empty",batch="w241_rolling_model",timeoutTicks=240)
    public static void rollingMachineProjectsItsExistingFormation(GameTestHelper h) { exercise(h,"rolling_machine",4,false); }
    @GameTest(template="empty",batch="w241_elevator_model",timeoutTicks=240)
    public static void surfaceElevatorProjectsItsExistingFormation(GameTestHelper h) { exercise(h,"elevator_anchor",5,false); }
    @GameTest(template="empty",batch="w241_precision_mirrored",timeoutTicks=240)
    public static void mirroredPrecisionKeepsFormationButUsesJsonFallback(GameTestHelper h) { exercise(h,"precision_assembler",6,true); }

    @GameTest(template="empty",batch="w241_rolling_rotation",timeoutTicks=240)
    public static void rollingRotationWithholdsHullAfterRevalidation(GameTestHelper h) { exerciseRotation(h,"rolling_machine",7); }
    @GameTest(template="empty",batch="w241_precision_rotation",timeoutTicks=240)
    public static void precisionRotationWithholdsHullAfterRevalidation(GameTestHelper h) { exerciseRotation(h,"precision_assembler",8); }
    @GameTest(template="empty",batch="w241_black_hole_rotation",timeoutTicks=240)
    public static void blackHoleRotationWithholdsCachedHull(GameTestHelper h) { exerciseRotation(h,"black_hole_generator",9); }
    @GameTest(template="empty",batch="w241_orbital_rotation",timeoutTicks=240)
    public static void orbitalRotationWithholdsCachedHull(GameTestHelper h) { exerciseRotation(h,"orbital_laser_drill",10); }
    @GameTest(template="empty",batch="w241_railgun_rotation",timeoutTicks=240)
    public static void railgunRotationWithholdsCachedHull(GameTestHelper h) { exerciseRotation(h,"railgun",11); }

    private static void exerciseRotation(GameTestHelper h, String kind, int row) {
        var fixtures = new ArrayList<Fixture>();
        var states = new ArrayList<BlockState>();
        var roots = new ArrayList<Object>();
        var validations = new ArrayList<Object>();
        int[] stage = {0};
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            fixtures.add(new Fixture(h, kind, new BlockPos(-2_410_000 + fixtures.size()*160,100,-2_410_000-row*1000),facing,false));
        }
        h.onEachTick(() -> {
            fixtures.forEach(Fixture::tick);
            if (stage[0] == 0) {
                if (fixtures.stream().anyMatch(f -> !f.formed() || !((FormedModelController)f.controller).modelFormed())) { return; }
                for (var f : fixtures) {
                    roots.add(f.controllerState()); validations.add(f.validation());
                    states.add(FormedModelRotationProbe.rotate(h, f.controller));
                    if (f.controller instanceof EndgameDeviceBlockEntity) {
                        // Before a budget grant/tick, code() may still describe the previous facing.
                        FormedModelRotationProbe.assertPresentation(h, f.controller, false);
                        FormedModelRotationProbe.set(h, f.controller, states.get(states.size()-1));
                        FormedModelRotationProbe.assertPresentation(h, f.controller, true);
                    } else { f.dirty(); }
                }
                if (fixtures.get(0).controller instanceof EndgameDeviceBlockEntity) {
                    fixtures.forEach(Fixture::close); h.succeed(); return;
                }
                stage[0] = 1;
            } else {
                for (int i=0; i<fixtures.size(); i++) {
                    if (fixtures.get(i).validation() == validations.get(i)) { return; }
                }
                for (int i=0; i<fixtures.size(); i++) {
                    var f = fixtures.get(i);
                    h.assertTrue(f.formed() && roots.get(i).equals(f.controllerState()), "Rotation changed retained formation/bindings");
                    FormedModelRotationProbe.assertPresentation(h, f.controller, stage[0] == 2);
                    if (stage[0] == 1) {
                        validations.set(i, f.validation());
                        FormedModelRotationProbe.set(h, f.controller, states.get(i)); f.dirty();
                    }
                }
                if (stage[0] == 2) { fixtures.forEach(Fixture::close); h.succeed(); } else { stage[0] = 2; }
            }
        });
    }

    private static void exercise(GameTestHelper h,String kind,int row,boolean mirrored) {
        var fixtures = new ArrayList<Fixture>();
        int[] stage={0};
        try {
            int index=0;
            for (Direction facing : kind.equals("elevator_anchor") ? List.of(Direction.NORTH) : Direction.Plane.HORIZONTAL) {
                fixtures.add(new Fixture(h,kind,new BlockPos(-2_410_000 + index++*160,100,-2_410_000-row*1000),facing,mirrored));
            }
            h.onEachTick(() -> {
                for (var f : fixtures) { f.tick(); }
                boolean expected=stage[0]!=1;
                if (fixtures.stream().anyMatch(f -> f.formed()!=expected
                        || stage[0]>=3 && ((FormedModelController)f.controller).modelFormed()!=!mirrored)) { return; }
                for (var f : fixtures) { f.assertProjection(h,expected && !mirrored); }
                if (stage[0]==0) { for(var f:fixtures){f.breakPart();} stage[0]=1; }
                else if (stage[0]==1) { for(var f:fixtures){f.restorePart();} stage[0]=2; }
                else if (stage[0]==2) {
                    for (var f : fixtures) { f.assertUnloadClearsProjection(h); f.dirty(); }
                    stage[0]=3;
                } else if (stage[0]==3) {
                    for (var f : fixtures) { f.reloadAndAssertProjectionCleared(h); f.dirty(); }
                    stage[0]=4;
                } else { fixtures.forEach(Fixture::close); h.succeed(); }
            });
        } catch (RuntimeException | Error failure) { fixtures.forEach(Fixture::close); throw failure; }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final String kind;
        private final BlockPos origin;
        private final Direction facing;
        private final K3ModelTestRegion region;
        private final BlockEntity controller;
        private final BlockPos broken;
        private final BlockState brokenState;

        Fixture(GameTestHelper helper,String kind,BlockPos origin,Direction facing,boolean mirrored) {
            this.helper=helper; this.kind=kind; this.origin=origin; this.facing=facing;
            region=new K3ModelTestRegion(helper,origin);
            try {
                MultiblockPatternDefinition pattern;
                try(var in=K4ModelGameTests.class.getResourceAsStream("/data/advancedrocketrycommunity/machine_patterns/"+kind+".json")) {
                    pattern=MultiblockPatternJsonCodec.decode(Objects.requireNonNull(in).readAllBytes());
                }
                var transform=new PatternTransform(ClassicModelProjection.transform(facing).rotation(),mirrored);
                var world=new PatternPosition(origin.getX(),origin.getY(),origin.getZ());
                for(var cell:pattern.cells().entrySet()) {
                    if(cell.getValue() instanceof PatternMatcher.Controller){continue;}
                    var p=transform.localToWorld(cell.getKey(),pattern.controllerAnchor(),world);
                    region.set(new BlockPos(p.x(),p.y(),p.z()),state(cell.getValue()));
                }
                var block=Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(ModIdentity.id(kind)));
                var state=block.defaultBlockState();
                if(state.hasProperty(ClassicControllerBlock.FACING)){state=state.setValue(ClassicControllerBlock.FACING,facing);}
                region.set(origin,state); controller=Objects.requireNonNull(helper.getLevel().getBlockEntity(origin));
                if(controller instanceof EndgameDeviceBlockEntity endgame){helper.assertTrue(endgame.assignOwner(UUID.randomUUID()),"K4 owner assignment failed");}
                if(controller instanceof PrecisionAssemblerBlockEntity precision){PrecisionAssemblerRuntime.observeController(helper.getLevel(),precision);}
                if(controller instanceof RollingMachineBlockEntity rolling){RollingMachineRuntime.observeController(helper.getLevel(),rolling);}
                var local=pattern.cells().entrySet().stream().filter(e->e.getValue() instanceof PatternMatcher.ExactBlock).findFirst().orElseThrow().getKey();
                var p=transform.localToWorld(local,pattern.controllerAnchor(),world); broken=new BlockPos(p.x(),p.y(),p.z());
                brokenState=helper.getLevel().getBlockState(broken);
            } catch(Exception | Error failure){region.close();throw new IllegalStateException("K4 fixture setup failed",failure);}
        }
        private BlockState state(PatternMatcher matcher) {
            if(matcher instanceof PatternMatcher.ExactBlock exact){return Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.tryParse(exact.blockId()))).defaultBlockState();}
            if(matcher instanceof PatternMatcher.Air){return Blocks.AIR.defaultBlockState();}
            if(matcher instanceof PatternMatcher.OptionalCell optional){return state(optional.matcher());}
            if(matcher instanceof PatternMatcher.Port port){return Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(ModIdentity.id(kind+"_"+port.channel()+"_port"))).defaultBlockState();}
            throw new IllegalArgumentException("Unsupported K4 fixture matcher "+matcher);
        }
        void tick() {
            var level=helper.getLevel(); var state=controller.getBlockState();
            if(controller instanceof BlackHoleGeneratorBlockEntity b){BlackHoleGeneratorBlockEntity.serverTick(level,origin,state,b);}
            if(controller instanceof OrbitalLaserDrillBlockEntity b){OrbitalLaserDrillBlockEntity.serverTick(level,origin,state,b);}
            if(controller instanceof RailgunBlockEntity b){RailgunBlockEntity.serverTick(level,origin,state,b);}
            if(controller instanceof ElevatorEndpointBlockEntity b){ElevatorEndpointBlockEntity.serverTick(level,origin,state,b);}
        }
        boolean formed() {
            if(controller instanceof PrecisionAssemblerBlockEntity b){return b.formationState()==MultiblockFormationState.FORMED;}
            if(controller instanceof RollingMachineBlockEntity b){return b.formationState()==MultiblockFormationState.FORMED;}
            if(controller instanceof BlackHoleGeneratorBlockEntity b){return b.structureCode()==EndgameCode.OK;}
            if(controller instanceof OrbitalLaserDrillBlockEntity b){return b.structureCode()==EndgameCode.OK;}
            if(controller instanceof RailgunBlockEntity b){return b.structureCode()==EndgameCode.OK;}
            return ((ElevatorAnchorBlockEntity)controller).placement()==EndgameCode.OK;
        }
        Object controllerState() {
            if (controller instanceof RollingMachineBlockEntity b) { return b.controllerState(); }
            if (controller instanceof PrecisionAssemblerBlockEntity b) { return b.controllerState(); }
            return null;
        }
        Object validation() {
            if (controller instanceof RollingMachineBlockEntity b) { return b.lastValidation().orElse(null); }
            if (controller instanceof PrecisionAssemblerBlockEntity b) { return b.lastValidation().orElse(null); }
            return null;
        }
        void breakPart(){region.set(broken,Blocks.AIR.defaultBlockState());dirty();}
        void restorePart(){region.set(broken,brokenState);dirty();}
        private void dirty(){
            EndgameRuntime.devices().ifPresent(d->d.structures().changed(helper.getLevel().dimension(),broken));
            PrecisionAssemblerRuntime.markDirty(helper.getLevel(),broken);RollingMachineRuntime.markDirty(helper.getLevel(),broken);
        }
        void assertProjection(GameTestHelper h,boolean formed) {
            var tag=controller.getUpdateTag();var keys=new HashSet<String>();
            if(kind.equals("black_hole_generator")){keys.add("generating");}
            if(kind.equals("orbital_laser_drill")||kind.equals("rolling_machine")){keys.add("active");}
            if(kind.equals("elevator_anchor")){keys.add("tether"); keys.add(io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorCapsuleCodec.KEY);}
            if(formed){keys.add("formed");}
            h.assertTrue(tag.getAllKeys().equals(keys),"K4 update exposed nonvisual state: "+tag);
            h.assertTrue(((FormedModelController)controller).modelFormed()==formed && ClassicModelProjection.decode(tag)==formed,"K4 formed projection differs");
            var packet=(ClientboundBlockEntityDataPacket)controller.getUpdatePacket();
            h.assertTrue(packet!=null && Objects.equals(packet.getTag(),tag.isEmpty()?null:tag),"K4 packet differs");
            var replica=Objects.requireNonNull(controller.getType().create(origin,controller.getBlockState()));var saved=replica.saveWithoutMetadata();
            replica.onDataPacket(null,packet);
            h.assertTrue(((FormedModelController)replica).modelFormed()==formed && saved.equals(replica.saveWithoutMetadata()),"K4 packet mutated save state");
            h.assertTrue(!ClassicModelProjection.decode(replica.getUpdateTag()),"K4 receive became formation authority");
            var good=new CompoundTag();good.putBoolean("formed",true);
            for(Tag bad:List.of(IntTag.valueOf(1),ByteTag.valueOf((byte)2),ByteTag.valueOf((byte)-1),StringTag.valueOf("true"))){
                var invalid=new CompoundTag();invalid.put("formed",bad);replica.handleUpdateTag(invalid);
                h.assertTrue(!((FormedModelController)replica).modelFormed(),"K4 accepted malformed formed byte");
            }
            replica.handleUpdateTag(good);replica.onDataPacket(null,ClientboundBlockEntityDataPacket.create(replica,unused->new CompoundTag()));
            h.assertTrue(!((FormedModelController)replica).modelFormed() && saved.equals(replica.saveWithoutMetadata()),"K4 null packet retained state or changed save");
            h.assertTrue(controller.onlyOpCanSetNbt(),"K4 controller permits nonoperator identity NBT");
            var expected=K4ModelProjection.bounds(kind,facing,origin);var actual=controller.getRenderBoundingBox();
            h.assertTrue(actual.equals(actual.minmax(expected)),"K4 render box clips hull");
        }
        void reloadAndAssertProjectionCleared(GameTestHelper h) {
            var saved = controller.saveWithoutMetadata();
            controller.load(saved);
            h.assertTrue(!((FormedModelController)controller).modelFormed()
                    && !ClassicModelProjection.decode(controller.getUpdateTag()), "K4 load trusted a saved formation for presentation");
            h.assertTrue(!controller.saveWithoutMetadata().contains("formed"), "K4 presentation entered saved state");
        }
        void assertUnloadClearsProjection(GameTestHelper h) {
            controller.onChunkUnloaded();
            h.assertTrue(!((FormedModelController)controller).modelFormed()
                    && !ClassicModelProjection.decode(controller.getUpdateTag()), "K4 unload retained formed projection");
        }
        @Override public void close(){region.close();}
    }
}
