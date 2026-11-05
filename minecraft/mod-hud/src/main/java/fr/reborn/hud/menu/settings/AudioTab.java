package fr.reborn.hud.menu.settings;

import fr.reborn.hud.keybind.HudKeybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;

/**
 * Onglet AUDIO (gabarit o2 du lot 3) : Général, Voix, Musique. Volumes câblés sur les {@link SoundSource} de Minecraft ;
 * la page Voix reprend les réglages utiles de Plasmo Voice ({@link VoixPlasmo}) quand il est là (maquette o2-c), les
 * touches de la voix restant dans Commandes › Voix et escouade.
 */
public final class AudioTab {

    private AudioTab() {}

    public static OngletPages onglet() {
        return new OngletPages(
            new OngletPages.Page("Général", new PageReglages(AudioTab::general), AudioTab::resetGeneral),
            new OngletPages.Page("Voix", new PageReglages(AudioTab::voix), AudioTab::resetVoix),
            new OngletPages.Page("Musique", new PageReglages(AudioTab::musique), AudioTab::resetMusique));
    }

    private static Options options() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.options;
    }

    // ─── Général ─────────────────────────────────────────
    private static void general(PageReglages p) {
        Options o = options();
        if (o == null) return;
        p.curseur("Volume principal", null, pct(o, SoundSource.MASTER), 0, 100, "%",
            v -> applyVolume(SoundSource.MASTER, v));
        p.curseur("Effets sonores", "Pas, blocs, créatures, météo, ambiance.", pct(o, SoundSource.BLOCKS), 0, 100, "%",
            AudioTab::applySfx);
        p.bascule("Sous-titres", "Les bruitages écrits à l'écran.", o.showSubtitles().get(),
            v -> { o.showSubtitles().set(v); o.save(); });
        p.bascule("Son directionnel", "Son 3D pour un casque (HRTF).", o.directionalAudio().get(),
            v -> { o.directionalAudio().set(v); o.save(); });
    }

    private static void resetGeneral() {
        Options o = options();
        if (o == null) return;
        setVolume(o, SoundSource.MASTER, 100);
        for (SoundSource s : SFX) setVolume(o, s, 100);
        o.showSubtitles().set(false);
        o.directionalAudio().set(false);
        o.save();
    }

    // ─── Voix ────────────────────────────────────────────
    private static void voix(PageReglages p) {
        if (!VoixPlasmo.present()) {
            Options o = options();
            if (o == null) return;
            p.curseur("Voix", "Chat vocal de proximité.", pct(o, SoundSource.VOICE), 0, 100, "%",
                v -> applyVolume(SoundSource.VOICE, v));
            p.valeur("Plasmo Voice", "Absent du jeu : la voix n'est pas disponible.", "—");
            return;
        }
        p.curseur("Voix des autres joueurs", null, plasmoPct(VoixPlasmo.VOLUME), 0, plasmoMax(VoixPlasmo.VOLUME), "%",
            v -> VoixPlasmo.ecrire(VoixPlasmo.VOLUME, v / 100.0));
        p.curseur("Volume du micro", "Ce que les autres entendent de toi.", plasmoPct(VoixPlasmo.MICRO), 0,
            plasmoMax(VoixPlasmo.MICRO), "%", v -> VoixPlasmo.ecrire(VoixPlasmo.MICRO, v / 100.0));
        int act = VoixPlasmo.activation();
        if (act != VoixPlasmo.ACTIVATION_INCONNUE) {
            // « Appuyer » sans touche liée : personne ne t'entend ; l'aide le dit en vermillon (relu à chaque image)
            KeyMapping parler = HudKeybinds.PARLER;
            boolean sansTouche = act == VoixPlasmo.ACTIVATION_APPUYER && (parler == null || parler.isUnbound());
            p.aideAlerte = sansTouche;
            p.choix("Activation", sansTouche
                    ? "Aucune touche : lie « Appuyer pour parler » dans Commandes › Voix et escouade."
                    : "Détection de la voix, ou appuyer pour parler (Commandes › Voix et escouade).",
                new SegmentedControl.Option[] {
                    new SegmentedControl.Option("voix", "Voix"),
                    new SegmentedControl.Option("appuyer", "Appuyer"),
                },
                act == VoixPlasmo.ACTIVATION_APPUYER ? "appuyer" : "voix",
                v -> VoixPlasmo.activation("appuyer".equals(v) ? VoixPlasmo.ACTIVATION_APPUYER : VoixPlasmo.ACTIVATION_VOIX));
        }
        p.curseur("Seuil de détection", "Plus haut : ton souffle et ton clavier ne passent plus.",
            (int) Math.round(VoixPlasmo.lire(VoixPlasmo.SEUIL)), (int) Math.round(VoixPlasmo.min(VoixPlasmo.SEUIL)),
            (int) Math.round(VoixPlasmo.max(VoixPlasmo.SEUIL)), " dB", v -> VoixPlasmo.ecrire(VoixPlasmo.SEUIL, v));
        p.action("Micro", VoixPlasmo.micro(), "Choisir", VoixPlasmo::ouvrirMenu);
        p.bascule("Réduction du bruit", null, VoixPlasmo.reductionBruit(), VoixPlasmo::reductionBruit);
        KeyMapping menu = KeyMapping.get("key.plasmovoice.settings");
        String touche = menu != null && !menu.isUnbound() ? menu.getTranslatedKeyMessage().getString() : null;
        p.action("Réglages avancés de Plasmo Voice", touche != null ? "Le menu de Plasmo, touche " + touche + "."
            : "Le menu de Plasmo.", "Ouvrir", VoixPlasmo::ouvrirMenu);
    }

    private static void resetVoix() {
        if (!VoixPlasmo.present()) {
            applyVolume(SoundSource.VOICE, 100);
            return;
        }
        VoixPlasmo.ecrire(VoixPlasmo.VOLUME, 1.0);
        VoixPlasmo.ecrire(VoixPlasmo.MICRO, 1.0);
        VoixPlasmo.reductionBruit(false);
    }

    private static int plasmoPct(int cle) {
        return (int) Math.round(VoixPlasmo.lire(cle) * 100);
    }

    private static int plasmoMax(int cle) {
        return Math.max(100, (int) Math.round(VoixPlasmo.max(cle) * 100));
    }

    // ─── Musique ─────────────────────────────────────────
    private static void musique(PageReglages p) {
        Options o = options();
        if (o == null) return;
        p.curseur("Musique", "Musiques du serveur et lecteur du menu principal.", pct(o, SoundSource.MUSIC), 0, 100, "%",
            v -> applyVolume(SoundSource.MUSIC, v));
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        p.bascule("Lecteur effacé après 10 s", "Sinon, le lecteur en haut à droite reste visible, atténué.",
            prefs.ostMasquerApres10s, v -> { prefs.ostMasquerApres10s = v; prefs.save(); });
    }

    private static void resetMusique() {
        applyVolume(SoundSource.MUSIC, 100);
        RebornPrefs.INSTANCE.ostMasquerApres10s = false;
        RebornPrefs.INSTANCE.save();
    }

    // ─── Outils ──────────────────────────────────────────
    private static int pct(Options o, SoundSource cat) {
        return (int) Math.round(o.getSoundSourceOptionInstance(cat).get() * 100);
    }

    /** Effets sonores = toutes les catégories de bruitages du jeu. */
    private static final SoundSource[] SFX = {SoundSource.BLOCKS, SoundSource.HOSTILE, SoundSource.NEUTRAL,
        SoundSource.PLAYERS, SoundSource.AMBIENT, SoundSource.WEATHER, SoundSource.RECORDS};

    private static void setVolume(Options o, SoundSource category, int percent) {
        o.getSoundSourceOptionInstance(category).set(percent / 100.0);
    }

    /** Volume appliqué tout de suite, {@code options.txt} écrit au relâché du curseur ({@link Enregistrement}). */
    private static void applyVolume(SoundSource category, int percent) {
        Options o = options();
        if (o == null) return;
        setVolume(o, category, percent);
        Enregistrement.options();
    }

    private static void applySfx(int percent) {
        Options o = options();
        if (o == null) return;
        for (SoundSource s : SFX) setVolume(o, s, percent);
        Enregistrement.options();
    }
}
