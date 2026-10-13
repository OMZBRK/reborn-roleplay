package fr.reborn.hud.menu.character.perso;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Ambiance sonore des écrans perso.
 * <ul>
 *   <li><b>Musique</b> : une piste « apaisant » de l'OST Reborn par village si le mod {@code reborn-ost} est présent
 *   (appel par réflexion, sans dépendance de compilation) ; sinon l'ambiance du menu Échap du mod en boucle, très
 *   bas, catégorie Musique. La musique vanilla est coupée le temps de l'écran. Le titre affiché suit le village.</li>
 *   <li><b>Voix off</b> : {@code reborn:voix.creation_<étape>} (catégorie Voix) à l'arrivée sur chaque étape ;
 *   coupable depuis l'indicateur « Voix off ».</li>
 * </ul>
 */
final class PersoSons {

    private PersoSons() {}

    static final String[] TITRES = {"Nuit sur la Feuille", "Vent des dunes", "Brume sur l'eau", "Orage lointain",
        "Roche endormie", "Pluie d'acier"};
    private static final String[] PISTES_OST = {"apaisant/track-01", "apaisant/track-02", "apaisant/track-03",
        "apaisant/track-04", "apaisant/track-05", "apaisant/track-06"};
    /** Durée des voix (ms), pour allumer la question au rythme de la parole. */
    static final int[] DUREE_VOIX = {2341, 1930, 1155, 1264, 1436, 1520, 2041, 1527};
    static final int DUREE_INTRO = 4557;
    private static final String[] CLES_VOIX = {"village", "clan", "sexe", "nom", "age", "taille", "apparence", "recap"};

    private static Boucle boucle;
    private static Object ostPiste;
    private static int villageJoue = -1;
    private static SoundInstance voix;
    static boolean voixCoupee;

    /* ---------------------------------------------------------------- musique */

    static boolean joue() {
        return villageJoue >= 0;
    }

    /** Démarre (ou change) la musique pour le village v. */
    static void musique(int v) {
        Minecraft mc = Minecraft.getInstance();
        try { mc.getMusicManager().stopPlaying(); } catch (RuntimeException ignored) { }
        if (v == villageJoue && ((boucle != null && !boucle.isStopped() && boucle.cible > 0) || ostPiste != null)) return;
        villageJoue = v;
        if (ost(v)) {
            if (boucle != null) boucle.cible = 0f;
            return;
        }
        if (boucle == null || boucle.isStopped()) {
            boucle = new Boucle();
            mc.getSoundManager().play(boucle);
        } else {
            boucle.cible = 1f;
        }
    }

    static void arreterMusique() {
        villageJoue = -1;
        if (boucle != null) boucle.cible = 0f;
        if (ostPiste != null) {
            try {
                Object moteur = Class.forName("fr.reborn.ost.RebornOstClient").getMethod("audioEngine").invoke(null);
                Optional<?> cur = (Optional<?>) moteur.getClass().getMethod("currentTrack").invoke(moteur);
                if (cur.isPresent() && cur.get().equals(ostPiste)) moteur.getClass().getMethod("stop").invoke(moteur);
            } catch (Throwable ignored) { }
            ostPiste = null;
        }
    }

    /** Piste de l'OST (réflexion) ; faux si le mod n'est pas là ou la piste absente. */
    private static boolean ost(int v) {
        try {
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("reborn-ost")) return false;
            Class<?> c = Class.forName("fr.reborn.ost.RebornOstClient");
            Object lib = c.getMethod("library").invoke(null);
            Object moteur = c.getMethod("audioEngine").invoke(null);
            Optional<?> piste = (Optional<?>) lib.getClass().getMethod("resolve", String.class)
                .invoke(lib, PISTES_OST[Math.max(0, Math.min(5, v))]);
            if (piste.isEmpty()) return false;
            Method play = null;
            for (Method m : moteur.getClass().getMethods()) if (m.getName().equals("play") && m.getParameterCount() == 5) play = m;
            if (play == null) return false;
            play.invoke(moteur, piste.get(), 0.55f, null, 0f, 0f);
            ostPiste = piste.get();
            return true;
        } catch (Throwable e) {
            ostPiste = null;
            return false;
        }
    }

    /** Boucle d'ambiance du mod, en fondu, catégorie Musique. */
    private static final class Boucle extends AbstractTickableSoundInstance {
        float niveau, cible = 1f;

        Boucle() {
            super(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("reborn", "esc.ambience")),
                SoundSource.MUSIC, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0001f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            niveau += (cible - niveau) * 0.03f;
            volume = Math.max(0.0001f, 0.45f * niveau);
            if (cible == 0f && niveau < 0.01f) stop();
        }
    }

    /* ---------------------------------------------------------------- voix */

    static void voix(int etape) {
        jouerVoix("voix.creation_" + CLES_VOIX[Math.max(0, Math.min(7, etape))]);
    }

    static void voixIntro() { jouerVoix("voix.creation_intro"); }

    private static void jouerVoix(String cle) {
        Minecraft mc = Minecraft.getInstance();
        couperVoix();
        if (voixCoupee) return;
        voix = new SimpleSoundInstance(Identifier.fromNamespaceAndPath("reborn", cle), SoundSource.VOICE, 1f, 1f,
            RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        mc.getSoundManager().play(voix);
    }

    static void couperVoix() {
        if (voix != null) Minecraft.getInstance().getSoundManager().stop(voix);
        voix = null;
    }

    static boolean voixEnCours() {
        return voix != null && Minecraft.getInstance().getSoundManager().isActive(voix);
    }
}
