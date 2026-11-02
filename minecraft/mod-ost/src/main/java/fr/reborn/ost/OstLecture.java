package fr.reborn.ost;

import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstTrack;
import fr.reborn.ost.audio.OstTrackMeta;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

/**
 * État de la lecture publié pour le HUD (lot 2 « Reprises Ketto », brief §7.3) : le lecteur en haut à droite est
 * dessiné par {@code reborn-hud} (élément « Musique », {@code ost/OstHud}), qui sait déjà gérer le KO, l'éditeur de
 * HUD, l'écran noir du staff et ses polices. mod-ost ne fait plus que publier, <b>sans dépendance de compilation</b>
 * entre les deux mods, par l'{@code ObjectShare} de Fabric Loader.
 *
 * <p>Clé {@value #CLE} = {@code Object[]{String[] textes, long[] etat}}, deux tableaux posés une fois au démarrage et
 * <b>partagés par référence</b> : mis à jour en place à chaque tick client (fin de tick), lus par le HUD à chaque image
 * sans allocation. Écrivain et lecteur sont sur le même fil (le tick et le rendu tournent sur le fil principal).
 * <ul>
 *   <li>{@code textes[0]} = trackId (« mystere/aube »), {@code textes[1]} = titre du HUD
 *       ({@link OstTrackMeta#titreHud} : la catégorie tant que les pistes n'ont pas de titre) ; {@code ""} sans piste ;</li>
 *   <li>{@code etat[0]} = 0 rien, 1 lecture, 2 pause ({@link OstAudioEngine#ETAT_RIEN} …) ;</li>
 *   <li>{@code etat[1]} = 1 si la piste s'entend (gain effectif non nul : hors de la zone d'une diffusion, la source
 *       tourne à gain 0 et le HUD doit se taire) ;</li>
 *   <li>{@code etat[2]} = {@code Util.getMillis()} du dernier changement (piste, état ou audibilité).</li>
 * </ul>
 *
 * <p>En retour, {@code reborn-hud} (≥ lot 2) dépose {@value #CLE_RENDU_HUD} pendant son initialisation : au
 * {@code CLIENT_STARTED}, mod-ost ne dessine son ancien bandeau ({@code ui/OstHudOverlay}, secours pour un
 * {@code reborn-hud} plus ancien) que si cette clé manque. Jamais les deux à la fois, dans n'importe quel ordre de mise
 * à jour des deux mods.
 */
public final class OstLecture {

    /** Clé de l'état publié par mod-ost. */
    public static final String CLE = "reborn-ost:lecture";
    /** Clé déposée par reborn-hud quand il dessine lui-même le lecteur. */
    public static final String CLE_RENDU_HUD = "reborn-hud:ost-rendu";

    private static final String[] TEXTES = {"", ""};
    private static final long[] ETAT = {OstAudioEngine.ETAT_RIEN, 0L, 0L};

    private OstLecture() {}

    /** Dépôt des deux tableaux (une fois, à l'initialisation du mod). */
    static void publier() {
        FabricLoader.getInstance().getObjectShare().put(CLE, new Object[]{TEXTES, ETAT});
    }

    /** Relevé de fin de tick (après {@code OstPlayback.tick} et {@code tickPositional}) : rien n'est alloué. */
    static void maj(OstAudioEngine engine) {
        int etat = engine.etat();
        OstTrack piste = etat == OstAudioEngine.ETAT_RIEN ? null : engine.pisteOuNull();
        boolean change = false;
        String id = piste == null ? "" : piste.trackId();
        if (!id.equals(TEXTES[0])) {
            TEXTES[0] = id;
            TEXTES[1] = piste == null ? "" : OstTrackMeta.titreHud(piste);
            change = true;
        }
        long audible = piste != null && engine.audible() ? 1L : 0L;
        if (change || ETAT[0] != etat || ETAT[1] != audible) {
            ETAT[0] = etat;
            ETAT[1] = audible;
            ETAT[2] = Util.getMillis();
        }
    }

    /** trackId en cours ({@code ""} sans piste). */
    public static String piste() { return TEXTES[0]; }

    /** Titre du HUD de la piste en cours ({@code ""} sans piste). */
    public static String titre() { return TEXTES[1]; }

    /** 0 rien, 1 lecture, 2 pause. */
    public static int etat() { return (int) ETAT[0]; }

    /** La piste s'entend. */
    public static boolean audible() { return ETAT[1] != 0L; }
}
