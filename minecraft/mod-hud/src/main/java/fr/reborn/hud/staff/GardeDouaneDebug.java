package fr.reborn.hud.staff;

import net.minecraft.client.Minecraft;

/**
 * Pont du banc de la douane ({@code douane/DouaneDebug}, {@code REBORN_DOUANE_DEBUG}) vers le Poste de garde :
 * ouvre l'onglet Douane dans un état donné, pose un survol, remplit la décision. <b>Inactif en production</b> : rien
 * ne l'appelle hors du banc.
 */
public final class GardeDouaneDebug {

    private GardeDouaneDebug() {}

    /** Instantané du Poste de garde (canal {@code reborn:staff}) puis volet ouvert sur l'onglet Douane. */
    public static void ouvrir(Minecraft mc, String snapStaff) {
        StaffClient.debugEffacerToasts();
        StaffClient.debugReceive(snapStaff);
        GardeScreen g = mc.gui.screen() instanceof GardeScreen cur ? cur : new GardeScreen("alerts");
        if (mc.gui.screen() != g) mc.setScreenAndShow(g);
        g.debugSouris = null;
        g.debugTab("douane");
        if (g.douaneVue.dossierU != null) g.douaneVue.retourFile();
    }

    private static GardeScreen ecran(Minecraft mc) {
        return mc.gui.screen() instanceof GardeScreen g ? g : null;
    }

    /** Survole le bouton {@code k} (0 = Appeler, 1 = Dossier, 2 = « … ») de la ligne {@code i} de la file. */
    public static void survolLigne(Minecraft mc, int i, int k) {
        GardeScreen g = ecran(mc);
        if (g == null) return;
        g.debugSouris = () -> {
            Integer y = i < g.douaneVue.lignesY.size() ? g.douaneVue.lignesY.get(i) : null;
            if (y == null) return new int[]{-100, -100};
            return new int[]{g.ix() + g.iw() - 14 * (3 - k) + 6, y + 11};
        };
    }

    /** Survole le bouton d'envoi de la décision. */
    public static void survolEnvoi(Minecraft mc) {
        GardeScreen g = ecran(mc);
        if (g == null) return;
        g.debugSouris = () -> {
            int[] b = g.douaneVue.boutonEnvoi;
            return b == null ? new int[]{-100, -100} : new int[]{b[0] + b[2] - 14, b[1] + 10};
        };
    }

    public static void sansSurvol(Minecraft mc) {
        GardeScreen g = ecran(mc);
        if (g != null) g.debugSouris = () -> new int[]{-100, -100};
    }

    public static void dossier(Minecraft mc, String u, String vue) {
        GardeScreen g = ecran(mc);
        if (g == null) return;
        g.douaneVue.ouvrirDossier(u, false);
        g.douaneVue.vue = vue;
        g.debugScroll(0);
    }

    public static void decision(Minecraft mc, String resultat, String motif, String notes) {
        GardeScreen g = ecran(mc);
        if (g != null) g.douaneVue.debugChoix(resultat, motif, notes);
    }

    public static void retrait(Minecraft mc, String u, String motif) {
        GardeScreen g = ecran(mc);
        if (g != null) g.douaneVue.debugRetrait(u, motif);
    }

    public static void toast(String m) { StaffClient.toast(m); }

    public static void effacerToasts() { StaffClient.debugEffacerToasts(); }
}
