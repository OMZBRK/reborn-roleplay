package fr.reborn.hud.menu.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.sound.SoundCategory;

/**
 * Onglet Audio — 4 volumes câblés sur les {@link SoundCategory} vanilla + un
 * toggle sous-titres. Le « mute si fenêtre inactive » (mort, sans consommateur)
 * a été retiré.
 */
public class AudioTab extends SectionedTab {

    @Override
    protected void build() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        GameOptions o = mc.options;

        section("Volumes");

        row("Volume principal", null,
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pct(o, SoundCategory.MASTER), 0, 100, "%",
                v -> applyVolume(SoundCategory.MASTER, v)));

        row("Musique", "Contrôle aussi le lecteur du menu principal",
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pct(o, SoundCategory.MUSIC), 0, 100, "%",
                v -> applyVolume(SoundCategory.MUSIC, v)));

        row("Effets sonores", null,
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pct(o, SoundCategory.BLOCKS), 0, 100, "%",
                v -> applySfx(v)));

        row("Voix", "Chat vocal RP de proximité",
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pct(o, SoundCategory.VOICE), 0, 100, "%",
                v -> applyVolume(SoundCategory.VOICE, v)));

        section("Divers");

        row("Sous-titres", "Affiche les bruitages sous forme de texte",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.getShowSubtitles().getValue(),
                v -> { o.getShowSubtitles().setValue(v); o.write(); }));

        spacer(4);
    }

    private static int pct(GameOptions o, SoundCategory cat) {
        return (int) Math.round(o.getSoundVolumeOption(cat).getValue() * 100);
    }

    private static void applyVolume(SoundCategory category, int percent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        mc.options.getSoundVolumeOption(category).setValue(percent / 100.0);
        mc.options.write();
    }

    /** SFX = somme des catégories d'effets sonores du jeu. */
    private static void applySfx(int percent) {
        applyVolume(SoundCategory.BLOCKS, percent);
        applyVolume(SoundCategory.HOSTILE, percent);
        applyVolume(SoundCategory.NEUTRAL, percent);
        applyVolume(SoundCategory.PLAYERS, percent);
        applyVolume(SoundCategory.AMBIENT, percent);
        applyVolume(SoundCategory.WEATHER, percent);
        applyVolume(SoundCategory.RECORDS, percent);
    }
}
