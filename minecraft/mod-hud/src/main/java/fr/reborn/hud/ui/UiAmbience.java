package fr.reborn.hud.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Boucle d'ambiance d'un écran Reborn (fiche, carte, boutique…) : démarre en fondu,
 * s'arrête en fondu, une seule à la fois. Source AMBIENT (suit le réglage « Ambiance »).
 */
public final class UiAmbience {

    private UiAmbience() {}

    private static Loop current;

    /** Lance la boucle {@code reborn:<key>} (remplace la précédente). */
    public static void start(String key, float volume) {
        stop();
        current = new Loop(key, volume);
        Minecraft.getInstance().getSoundManager().play(current);
    }

    /** Fondu de sortie puis arrêt (non bloquant). */
    public static void stop() {
        if (current != null) current.fading = true;
        current = null;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final float target;
        private boolean fading;

        Loop(String key, float target) {
            super(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("reborn", key)),
                SoundSource.AMBIENT, RandomSource.create());
            this.target = target;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            if (fading) {
                volume -= 0.04f;
                if (volume <= 0.01f) stop();
            } else if (volume < target) {
                volume = Math.min(target, volume + 0.02f);
            }
        }
    }
}
