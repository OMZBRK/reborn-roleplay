package fr.reborn.hud.menu.settings;

/** Banc {@code REBORN_LOT4_DEBUG=c7} : accès au réglage de la couleur des traces depuis {@code audit/Lot4c7Debug}. */
public final class CouleurTracesBanc {

    private CouleurTracesBanc() {}

    /** Impose la couleur et l'état du sélecteur, sans écrire sur le disque. */
    public static void poser(int couleur, boolean ouvert) { CouleurTracesReglage.debug(couleur, ouvert); }

    public static boolean ouvert() { return CouleurTracesReglage.ouvert(); }

    /** « Automatique », sans écrire sur le disque. */
    public static void auto() { CouleurTracesReglage.debugAuto(); }

    /** Le « Réinitialiser » de la page. */
    public static void reinitialiser() { CouleurTracesReglage.reinitialiser(); }

    /** Ce que le sélecteur montre : {champ hexadécimal (RVB), aperçu, couleur des curseurs}. */
    public static int[] montre() { return CouleurTracesReglage.montre(); }

    /** Point du clic (GUI) : -1 = « Automatique », pastille {@code i}, 7 = lien « Personnalisée », 8 = milieu du carré, 9 = champ hexadécimal. */
    public static int[] point(int i) { return CouleurTracesReglage.point(i); }
}
