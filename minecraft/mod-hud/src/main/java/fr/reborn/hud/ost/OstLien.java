package fr.reborn.hud.ost;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;

/**
 * Lien avec {@code reborn-ost} (≥ 0.2.6) <b>sans dépendance de compilation</b> : l'{@link ObjectShare} de Fabric Loader
 * (lot 2 « Reprises Ketto », brief §7.3).
 *
 * <ul>
 *   <li>mod-ost publie {@value #CLE} = {@code Object[]{String[] textes, long[] etat}} : deux tableaux posés une fois et
 *       <b>partagés par référence</b>, mis à jour en place à chaque fin de tick client. On garde les deux références et
 *       on les relit à chaque image : rien n'est alloué. Écrivain et lecteur tournent sur le même fil (le tick et
 *       l'extraction du HUD sont sur le fil principal).
 *       <ul>
 *         <li>{@code textes[0]} = trackId (« mystere/aube »), {@code textes[1]} = titre du HUD (la catégorie de la
 *             piste, décision du 01/11) ; {@code ""} sans piste ;</li>
 *         <li>{@code etat[0]} = 0 rien, 1 lecture, 2 pause ; {@code etat[1]} = 1 si la piste s'entend (hors de la zone
 *             d'une diffusion, la source tourne à gain 0) ; {@code etat[2]} = {@code Util.getMillis()} du dernier
 *             changement.</li>
 *       </ul></li>
 *   <li>En retour, on dépose {@value #CLE_RENDU} pendant l'initialisation du client : au {@code CLIENT_STARTED}, mod-ost
 *       ne dessine son ancien bandeau de secours que si cette clé manque. Jamais les deux lecteurs à la fois :
 *       <ul>
 *         <li>reborn-hud récent + reborn-ost 0.2.6 : le lecteur est dessiné ici ({@link OstHud}), le bandeau est coupé ;</li>
 *         <li>reborn-hud 0.4.194 (ne dépose pas la clé) + reborn-ost 0.2.6 : le bandeau de secours de mod-ost ;</li>
 *         <li>reborn-hud récent + reborn-ost 0.2.5 (ne publie rien) : {@link #present()} reste faux, rien n'est dessiné
 *             ici et l'ancien bandeau de 0.2.5 reste le seul.</li>
 *       </ul></li>
 * </ul>
 */
public final class OstLien {

    /** Clé publiée par mod-ost. */
    public static final String CLE = "reborn-ost:lecture";
    /** Clé déposée ici : reborn-hud dessine lui-même le lecteur. */
    public static final String CLE_RENDU = "reborn-hud:ost-rendu";

    public static final int RIEN = 0, LECTURE = 1, PAUSE = 2;

    private static String[] textes;
    private static long[] etat;

    private OstLien() {}

    /** Initialisation du client (avant {@code CLIENT_STARTED}) : dépôt de la clé de rendu, attente de l'état publié. */
    static void init() {
        ObjectShare os = FabricLoader.getInstance().getObjectShare();
        os.put(CLE_RENDU, Boolean.TRUE);
        os.whenAvailable(CLE, (cle, v) -> lier(v));
    }

    /** Garde les deux tableaux publiés (format vérifié ; un format inconnu laisse le lecteur muet). */
    static void lier(Object v) {
        if (v instanceof Object[] a && a.length >= 2 && a[0] instanceof String[] t && t.length >= 2
            && a[1] instanceof long[] e && e.length >= 3) {
            textes = t;
            etat = e;
        }
    }

    /** reborn-ost ≥ 0.2.6 a publié son état. */
    public static boolean present() { return textes != null; }

    /** trackId en cours ({@code ""} sans piste). */
    public static String piste() {
        String[] t = textes;
        return t == null || t[0] == null ? "" : t[0];
    }

    /** Titre du HUD de la piste en cours ({@code ""} sans piste). */
    public static String titre() {
        String[] t = textes;
        return t == null || t[1] == null ? "" : t[1];
    }

    /** {@link #RIEN}, {@link #LECTURE} ou {@link #PAUSE}. */
    public static int etat() {
        long[] e = etat;
        return e == null ? RIEN : (int) e[0];
    }

    /** La piste s'entend (gain effectif non nul). */
    public static boolean audible() {
        long[] e = etat;
        return e != null && e[1] != 0L;
    }

    /** Horodatage ({@code Util.getMillis()}) du dernier changement publié, 0 sans état. */
    public static long changeA() {
        long[] e = etat;
        return e == null ? 0L : e[2];
    }
}
