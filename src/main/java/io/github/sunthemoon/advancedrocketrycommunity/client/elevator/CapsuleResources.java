package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelCheck;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelException;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelResources;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelSet;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The capsule's model and texture, checked once per client resource reload (W244-R4), off the render thread. A good
 * pair publishes the mesh; a missing or unusable model, group or texture publishes nothing, and the countdown keeps the
 * line cage. Rendering only reads the published mesh.
 */
final class CapsuleResources extends SimplePreparableReloadListener<Optional<CapsuleMesh>> {
    static final ResourceLocation MODEL = ModIdentity.id("models/spaceelevator.obj");
    static final ResourceLocation TEXTURE = ModIdentity.id("textures/models/spaceelevatorcapsule.png");

    private final Consumer<String> diagnostic;
    @Nullable private volatile CapsuleMesh mesh;

    CapsuleResources(Consumer<String> diagnostic) {
        this.diagnostic = diagnostic;
    }

    /** The mesh of the last reload, or empty for the line cage. */
    Optional<CapsuleMesh> mesh() {
        return Optional.ofNullable(mesh);
    }

    /** Reads each resource once, within the model set's byte limit, and checks both before anything is published. */
    static CapsuleMesh load(ObjModelSet.Source source) throws ObjModelException {
        var obj = source.read(MODEL, ObjModelCheck.MAX_SET_BYTES)
                .orElseThrow(() -> new ObjModelException(MODEL + " is missing"));
        if (obj.length > ObjModelCheck.MAX_SET_BYTES) { throw new ObjModelException(MODEL + ": more than " + ObjModelCheck.MAX_SET_BYTES + " bytes"); }
        var mesh = CapsuleMesh.read(obj, MODEL.toString());
        var png = source.read(TEXTURE, ObjModelCheck.MAX_SET_BYTES)
                .orElseThrow(() -> new ObjModelException(TEXTURE + " is missing"));
        if (png.length > ObjModelCheck.MAX_SET_BYTES) { throw new ObjModelException(TEXTURE + ": more than " + ObjModelCheck.MAX_SET_BYTES + " bytes"); }
        // W244-R4: an unreadable texture keeps the line cage too, not only a missing one.
        ObjModelCheck.checkPng(png, TEXTURE.toString());
        return mesh;
    }

    @Override
    protected Optional<CapsuleMesh> prepare(ResourceManager resources, ProfilerFiller profiler) {
        return prepare(ObjModelResources.source(resources));
    }

    Optional<CapsuleMesh> prepare(ObjModelSet.Source source) {
        try {
            return Optional.of(load(source));
        } catch (ObjModelException | RuntimeException failure) {
            diagnostic.accept("The elevator capsule draws its line cage: " + ObjModelResources.diagnostic(failure));
            return Optional.empty();
        }
    }

    @Override
    protected void apply(Optional<CapsuleMesh> prepared, ResourceManager resources, ProfilerFiller profiler) {
        apply(prepared);
    }

    void apply(Optional<CapsuleMesh> prepared) {
        mesh = prepared.orElse(null);
    }
}
