package fr.reborn.hud.menu.character.perso;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Ambiance sonore des écrans perso : <b>pas de musique</b>, une nuit au bord de l'étang faite de sons de Minecraft
 * joués en petites touches discrètes et variées (catégorie Ambiance) : l'eau qui clapote, les insectes des buissons
 * à lucioles, le vent dans les herbes, une grenouille de temps en temps ; une couleur par village (sable à Suna,
 * pluie à Ame, orage lointain à Kumo…). La musique du jeu est coupée le temps de l'écran.
 *
 * <p>Voix off ({@code reborn:voix.creation_<étape>}) : <b>désactivée</b> (la voix générée a été jugée laide) ; le
 * mécanisme reste pour un vrai enregistrement, à réactiver avec {@code -Dreborn.persoVoix=true}.
 */
final class PersoSons {

    private PersoSons() {}

    /** Voix off des étapes (désactivée par défaut). */
    static final boolean VOIX = Boolean.getBoolean("reborn.persoVoix");

    /** Durée des voix (ms), pour allumer la question au rythme de la parole. */
    static final int[] DUREE_VOIX = {2341, 1930, 1155, 1264, 1436, 1520, 2041, 1527};
    private static final String[] CLES_VOIX = {"village", "clan", "sexe", "nom", "age", "taille", "apparence", "recap"};

    private static SoundInstance voix;
    static boolean voixCoupee = !VOIX;

    /* ---------------------------------------------------------------- ambiance */

    /** Une couche : son vanilla, intervalle (ticks) min-max, volume min-max, hauteur min-max. */
    private record Couche(String son, int iMin, int iMax, float vMin, float vMax, float pMin, float pMax) {}

    private static final Couche EAU = new Couche("block.water.ambient", 50, 110, 0.07f, 0.13f, 0.55f, 0.8f);
    private static final Couche INSECTES = new Couche("block.firefly_bush.idle", 40, 120, 0.035f, 0.06f, 0.85f, 1.15f);
    private static final Couche VENT = new Couche("block.dry_grass.ambient", 120, 260, 0.05f, 0.09f, 0.75f, 0.95f);
    private static final Couche GRENOUILLE = new Couche("entity.frog.ambient", 220, 520, 0.035f, 0.06f, 0.8f, 1.05f);
    private static final Couche SABLE = new Couche("block.sand.idle", 80, 180, 0.02f, 0.035f, 0.8f, 1.0f);
    private static final Couche PLUIE = new Couche("weather.rain", 30, 45, 0.05f, 0.08f, 0.9f, 1.0f);
    private static final Couche ORAGE = new Couche("entity.lightning_bolt.thunder", 600, 1400, 0.025f, 0.04f, 0.5f, 0.6f);

    /** Couches par village : Konoha, Suna, Kiri, Kumo, Iwa, Ame. */
    private static final Couche[][] VILLAGES = {
        {EAU, INSECTES, VENT, GRENOUILLE},
        {EAU, INSECTES, SABLE, VENT},
        {EAU, EAU, INSECTES, GRENOUILLE},
        {EAU, INSECTES, VENT, ORAGE},
        {EAU, INSECTES, VENT},
        {EAU, PLUIE, INSECTES},
    };

    private static final RandomSource ALEA = RandomSource.create();
    private static int village = -1;
    private static int[] prochain = new int[0];
    private static int horloge;

    static boolean joue() { return village >= 0; }

    /** Démarre (ou change) l'ambiance pour le village v. */
    static void ambiance(int v) {
        v = Math.max(0, Math.min(5, v));
        try { Minecraft.getInstance().getMusicManager().stopPlaying(); } catch (RuntimeException ignored) { }
        if (v == village) return;
        village = v;
        Couche[] cs = VILLAGES[v];
        prochain = new int[cs.length];
        // les premières touches arrivent vite et décalées : l'étang « s'éveille » en une à trois secondes
        for (int i = 0; i < cs.length; i++) prochain[i] = horloge + 4 + i * 9 + ALEA.nextInt(16);
    }

    /** À appeler à chaque tick de l'écran. */
    static void tick() {
        if (village < 0) return;
        horloge++;
        Minecraft mc = Minecraft.getInstance();
        if (horloge % 20 == 0) {
            try { mc.getMusicManager().stopPlaying(); } catch (RuntimeException ignored) { }
        }
        Couche[] cs = VILLAGES[village];
        for (int i = 0; i < cs.length && i < prochain.length; i++) {
            if (horloge < prochain[i]) continue;
            Couche c = cs[i];
            prochain[i] = horloge + c.iMin() + ALEA.nextInt(Math.max(1, c.iMax() - c.iMin()));
            float vol = c.vMin() + ALEA.nextFloat() * (c.vMax() - c.vMin());
            float pitch = c.pMin() + ALEA.nextFloat() * (c.pMax() - c.pMin());
            try {
                mc.getSoundManager().play(new SimpleSoundInstance(Identifier.withDefaultNamespace(c.son()),
                    SoundSource.AMBIENT, vol, pitch, ALEA, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
            } catch (RuntimeException ignored) { }
        }
    }

    static void arreter() {
        village = -1;
    }

    /* ---------------------------------------------------------------- voix (désactivée par défaut) */

    static void voix(int etape) {
        jouerVoix("voix.creation_" + CLES_VOIX[Math.max(0, Math.min(7, etape))]);
    }

    static void voixIntro() { jouerVoix("voix.creation_intro"); }

    private static void jouerVoix(String cle) {
        couperVoix();
        if (!VOIX || voixCoupee) return;
        voix = new SimpleSoundInstance(Identifier.fromNamespaceAndPath("reborn", cle), SoundSource.VOICE, 1f, 1f,
            RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        Minecraft.getInstance().getSoundManager().play(voix);
    }

    static void couperVoix() {
        if (voix != null) Minecraft.getInstance().getSoundManager().stop(voix);
        voix = null;
    }

    static boolean voixEnCours() {
        return voix != null && Minecraft.getInstance().getSoundManager().isActive(voix);
    }
}
