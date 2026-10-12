package io.github.sunthemoon.advancedrocketrycommunity.client.sound;

import io.github.sunthemoon.advancedrocketrycommunity.client.sound.ClassicLoopSelection.Key;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModSounds;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;

/**
 * One playing classic loop. Each sound-engine tick it asks {@link ClassicSoundClient} whether its source is still
 * loaded, still active in its synchronized state and within the stop distance, and stops itself at once otherwise;
 * it never relies on a removal callback arriving. It holds a key, not the block entity or entity.
 */
final class ClassicLoopSound extends AbstractTickableSoundInstance {
    private final ClassicSound sound;
    private final Key key;

    ClassicLoopSound(ClassicSound sound, Key key, Vec3 position) {
        super(ModSounds.classic(sound), sound.source(), SoundInstance.createUnseededRandom());
        this.sound = sound;
        this.key = key;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        move(position);
    }

    ClassicSound sound() { return sound; }
    Key key() { return key; }

    @Override
    public void tick() {
        Vec3 position = ClassicSoundClient.validPosition(this);
        if (position == null) {
            stop();
        } else {
            move(position);
        }
    }

    /** Stops the loop; the sound engine releases it on its next update. */
    void end() {
        stop();
    }

    private void move(Vec3 position) {
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }
}
