package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import net.minecraft.data.PackOutput;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.SoundDefinitionsProvider;

/**
 * The v1.8 {@code sounds.json} (ADR-066 section 7.1), in the v1.8 resource namespace so the immutable v0.1 file is
 * untouched. Each classic event names its NEW file from {@link V180SoundArt}, its subtitle and its audible range.
 */
public final class V180SoundDefinitions extends SoundDefinitionsProvider {
    private final ExistingFileHelper files;

    public V180SoundDefinitions(PackOutput output, ExistingFileHelper files) {
        super(output, ClassicSound.DEFINITION_NAMESPACE, files);
        this.files = files;
    }

    @Override
    public void registerSounds() {
        for (ClassicSound sound : ClassicSound.values()) {
            // The NEW file is written by V180SoundArt in this same run.
            files.trackGenerated(sound.file(), PackType.CLIENT_RESOURCES, ".ogg", "sounds");
            add(sound.id(), definition()
                    .subtitle(sound.subtitle())
                    .with(sound(sound.file()).attenuationDistance(sound.range()).stream(false)));
        }
    }
}
