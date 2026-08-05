package fr.reborn.hud.menu.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;

/**
 * Onglet Audio — 4 volumes câblés sur les {@link SoundSource} vanilla + un
 * toggle sous-titres. Le « mute si fenêtre inactive » (mort, sans consommateur)
 * a été retiré.
 */
public class AudioTab extends SectionedTab {

    @Override
    protected void build() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;

        section("Volumes");

        row("Volume principal", null,
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pct(o, SoundSource.MASTER), 0, 100, "%",
                v -> applyVolume(SoundSource.MASTER, v)));

        row("Musique", "Contrôle aussi le lecteur du menu principal",
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pct(o, SoundSource.MUSIC), 0, 100, "%",
                v -> applyVolume(SoundSource.MUSIC, v)));

        row("Effets sonores", null,
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pct(o, SoundSource.BLOCKS), 0, 100, "%",
                v -> applySfx(v)));

        row("Voix", "Chat vocal RP de proximité",
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pct(o, SoundSource.VOICE), 0, 100, "%",
                v -> applyVolume(SoundSource.VOICE, v)));

        section("Divers");

        row("Sous-titres", "Affiche les bruitages sous forme de texte",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.showSubtitles().get(),
                v -> { o.showSubtitles().set(v); o.save(); }));

        spacer(4);
    }

    private static int pct(Options o, SoundSource cat) {
        return (int) Math.round(o.getSoundVolumeOption(cat).getValue() * 100);
    }

    private static void applyVolume(SoundSource category, int percent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        mc.options.getSoundVolumeOption(category).setValue(percent / 100.0);
        mc.options.save();
    }

    /** SFX = somme des catégories d'effets sonores du jeu. */
    private static void applySfx(int percent) {
        applyVolume(SoundSource.BLOCKS, percent);
        applyVolume(SoundSource.HOSTILE, percent);
        applyVolume(SoundSource.NEUTRAL, percent);
        applyVolume(SoundSource.PLAYERS, percent);
        applyVolume(SoundSource.AMBIENT, percent);
        applyVolume(SoundSource.WEATHER, percent);
        applyVolume(SoundSource.RECORDS, percent);
    }
}
