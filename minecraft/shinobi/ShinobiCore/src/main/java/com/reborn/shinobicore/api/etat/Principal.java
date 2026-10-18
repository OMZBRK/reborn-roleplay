package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

/**
 * L'<b>état principal</b> (§4.3) : une seule valeur que tout le monde lit (plaques staff, journal, HUD, Poste de garde),
 * <b>résolue</b> à partir des trois couches et du modificateur « en combat » — jamais stockée. Première ligne vraie de
 * haut en bas (ordre de l'énumération).
 */
@Stable
public enum Principal {
    MODE_STAFF("Mode staff"),
    HORS_JEU("Hors jeu"),
    MORT("Mort RP"),
    PORTE("Porté"),
    CONTROLE("Contrôlé"),
    INCONSCIENT("Inconscient"),
    A_TERRE("À terre"),
    ENTRAVE("Entravé"),
    ETOURDI("Étourdi"),
    DOULEUR("Douleur"),
    GARDE("Garde"),
    PRISON("Prison"),
    COMBAT("Combat"),
    LIBRE("Libre");

    private final String libelle;

    Principal(String libelle) { this.libelle = libelle; }

    /** Libellé affiché (fiche du Poste de garde, /etats, journal). */
    public String libelle() { return libelle; }

    /** Règle de résolution (fonction pure, §4.3). */
    public static Principal resoudre(Vital v, Controle c, Cadre k, boolean enCombat) {
        if (k == Cadre.STAFF) return MODE_STAFF;
        if (k == Cadre.HORS_JEU) return HORS_JEU;
        if (v == Vital.MORT) return MORT;
        if (c == Controle.PORTE) return PORTE;
        if (c == Controle.CONTROLE) return CONTROLE;
        if (v == Vital.INCONSCIENT) return INCONSCIENT;
        if (v == Vital.A_TERRE) return A_TERRE;
        if (c == Controle.ENTRAVE || c == Controle.CORPS_VIDE) return ENTRAVE;
        if (c == Controle.ETOURDI) return ETOURDI;
        if (v == Vital.DOULEUR) return DOULEUR;
        if (c == Controle.GARDE) return GARDE;
        if (k == Cadre.PRISON) return PRISON;
        if (enCombat || k == Cadre.DUEL) return COMBAT;
        return LIBRE;
    }
}
