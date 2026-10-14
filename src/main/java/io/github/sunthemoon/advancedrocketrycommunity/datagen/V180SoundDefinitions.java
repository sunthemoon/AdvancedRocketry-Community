package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModSounds;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import net.minecraft.data.PackOutput;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.SoundDefinitionsProvider;

/**
 * The one current mod-namespace {@code sounds.json} (ADR-066 section 7.1), so every sound location equals its
 * registry ID. It folds in the v0.1 {@code ui_select} definition unchanged; the build excludes the immutable v0.1
 * file, which defines only {@code ui_select}. Each classic event names its NEW file from {@link V180SoundArt}, its
 * subtitle and its audible range.
 */
public final class V180SoundDefinitions extends SoundDefinitionsProvider {
    private final ExistingFileHelper files;

    public V180SoundDefinitions(PackOutput output, ExistingFileHelper files) {
        super(output, ModIdentity.MOD_ID, files);
        this.files = files;
    }

    @Override
    public void registerSounds() {
        // Exactly as the v0.1 ModSoundDefinitionsProvider defined it.
        add(ModSounds.UI_SELECT, definition()
                .subtitle("subtitle.advancedrocketrycommunity.ui_select")
                .with(sound("advancedrocketrycommunity:ui_select")));
        for (ClassicSound sound : ClassicSound.values()) {
            // The NEW file is written by V180SoundArt in this same run.
            files.trackGenerated(sound.file(), PackType.CLIENT_RESOURCES, ".ogg", "sounds");
            add(sound.id(), definition()
                    .subtitle(sound.subtitle())
                    .with(sound(sound.file()).attenuationDistance(sound.range()).stream(false)));
        }
    }
}
