package fr.reborn.hud.menu.character.scene;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Sons des scènes de sélection / création, tous très discrets : un souffle grave en boucle (le vide), un souffle
 * d'air + un tintement de cristal quand on change de perso ou de question, un petit déclic à chaque lettre tapée.
 */
public final class SceneSounds {

    private static Hum hum;

    private SceneSounds() {}

    private static void ui(SoundEvent e, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, volume));
    }

    /** Changement de perso (sélection) : souffle d'air + tintement. */
    public static void focus() {
        ui(SoundEvents.PLAYER_ATTACK_SWEEP, 1.75f, 0.12f);
        ui(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f + (float) Math.random() * 0.2f, 0.55f);
    }

    /** Nouvelle question / nouvelle ligne : souffle d'air seul, plus doux. */
    public static void whoosh() { ui(SoundEvents.PLAYER_ATTACK_SWEEP, 1.9f, 0.08f); }

    /** Choix dans une liste (village, clan, apparence…). */
    public static void tick() { ui(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35f + (float) Math.random() * 0.15f, 0.28f); }

    /** Une lettre tapée. */
    public static void key() {
        ui(SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON, 1.7f + (float) Math.random() * 0.25f, 0.13f);
    }

    /** Validation forte (entrer en jeu, créer). */
    public static void resonate() { ui(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f); }

    /** Démarre le souffle du vide (fondu d'entrée). */
    public static void startHum() {
        if (hum != null && !hum.isStopped()) { hum.target = 1f; return; }
        hum = new Hum();
        Minecraft.getInstance().getSoundManager().play(hum);
    }

    /** Arrête le souffle (fondu de sortie). */
    public static void stopHum() { if (hum != null) hum.target = 0f; }

    /** Boucle grave, volume très bas, en fondu. */
    private static final class Hum extends AbstractTickableSoundInstance {
        float level, target = 1f;

        Hum() {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.MASTER, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0001f;
            this.pitch = 0.5f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            level += (target - level) * 0.04f;
            volume = Math.max(0.0001f, 0.16f * level);
            if (target == 0f && level < 0.01f) stop();
        }
    }
}
