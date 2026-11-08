package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * Pont du banc de l'étape B du lot 4 ({@code audit/Lot4bDebug}, {@code REBORN_LOT4_DEBUG=b}) vers l'outil « Plaques »
 * du Poste de garde : instantané simulé, serveur des actions {@code plaque*} simulé, vues, sélecteur, souris.
 * <b>Inactif en production</b> : rien ne l'appelle hors du banc.
 */
public final class GardePlaquesDebug {

    private GardePlaquesDebug() {}

    /** Les actions {@code plaque*} partent vers {@code serveur} (null : réseau). */
    public static void serveur(Consumer<JsonObject> serveur) { StaffClient.debugPlaques = serveur; }

    /** Un message du canal {@code reborn:staff} simulé (instantané, liste {@code plaques}, toast). */
    public static void recevoir(String raw) { StaffClient.debugReceive(raw); }

    /** Instantané puis volet ouvert sur Outils › Plaques. */
    public static void ouvrir(Minecraft mc, String snap) {
        StaffClient.debugEffacerToasts();
        StaffClient.debugReceive(snap);
        GardeScreen g = mc.gui.screen() instanceof GardeScreen cur ? cur : new GardeScreen("alerts");
        if (mc.gui.screen() != g) mc.setScreenAndShow(g);
        g.debugSouris = () -> new int[]{-100, -100};
        g.debugTab("outils");
        g.outilsVue.ouvrir("plaques");
    }

    private static GardePlaques vue(Minecraft mc) {
        return mc.gui.screen() instanceof GardeScreen g ? g.outilsVue.plaquesVue : null;
    }

    public static void liste(Minecraft mc) {
        GardePlaques v = vue(mc);
        if (v != null) v.debugVue("liste", null, false);
    }

    public static void fiche(Minecraft mc, String id, boolean apercu) {
        GardePlaques v = vue(mc);
        if (v == null) return;
        v.debugVue("fiche", id, false);
        v.debugApercuFiche(apercu);
    }

    /** Éditeur d'une nouvelle plaque, rempli ; {@code ancre} null : pas encore d'ancre. */
    public static void editeur(Minecraft mc, String balise, String titre, int portee, JsonObject ancre, String lib, double[] boite) {
        GardePlaques v = vue(mc);
        if (v != null) v.debugEditeur(balise, titre, portee, ancre, lib, boite, true);
    }

    /** Halo de l'éditeur (planche c6) : préréglage {@code code}, ou « Personnalisée » {@code couleur} (sélecteur ouvert ?). */
    public static void halo(Minecraft mc, int code, boolean perso, int couleur, boolean ouvert) {
        GardePlaques v = vue(mc);
        if (v != null) v.debugHalo(code, perso, couleur, ouvert);
    }

    /** « Modifier le texte » d'une plaque de halo {@code c}, puis clic sur « Personnalisée » (le vrai chemin). */
    public static void haloModifier(Minecraft mc, int c) {
        GardePlaques v = vue(mc);
        if (v != null) v.debugHaloVolet().debugModifierPuisOuvrir(c);
    }

    /** Un clic sur la barre de teinte. */
    public static void haloTeinte(Minecraft mc, float t) {
        GardePlaques v = vue(mc);
        if (v != null) v.debugHaloVolet().debugTeinte(t);
    }

    public static String haloEtat(Minecraft mc) {
        GardePlaques v = vue(mc);
        return v == null ? "?" : v.debugHaloVolet().debugEtat();
    }

    public static int haloCouleur(Minecraft mc) {
        GardePlaques v = vue(mc);
        return v == null ? -1 : v.debugHaloVolet().debugCouleur();
    }

    /** Banc : récentes du sélecteur (préférence locale). */
    public static void recentes(int... c) { fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE.haloRecentes = c; }

    /** Défilement du corps du volet (GUI). */
    public static void defiler(Minecraft mc, int y) {
        if (mc.gui.screen() instanceof GardeScreen g) g.scroll = y;
    }

    public static void selecteur(Minecraft mc) {
        GardePlaques v = vue(mc);
        if (v != null) v.ouvrirSelecteur("creer");
    }

    /** Rouvre le sélecteur depuis l'éditeur (le choix est gardé, comme « Changer »). */
    public static void rouvrir(Minecraft mc) {
        GardePlaques v = vue(mc);
        if (v != null) v.ouvrirSelecteur("creer");
    }

    /** Souris posée en ({@code x}, {@code y}) GUI ; le survol est relu au tick suivant. */
    public static void souris(Minecraft mc, float x, float y) {
        if (mc.gui.screen() instanceof GardeScreen g) g.debugSouris = () -> new int[]{Math.round(x), Math.round(y)};
    }

    public static void clicMonde(Minecraft mc, float x, float y, boolean maj) {
        GardePlaques v = vue(mc);
        if (v != null) v.clicMonde(x, y, maj);
    }

    /** Maj tenue pour enfoncée (aperçu de la boîte de deux coins). */
    public static void maj(boolean on) { GardePlaques.debugMaj = on; }

    /** Souris dans le monde à droite de l'écran, sous le bandeau replié du volet (colonne du volet). */
    public static void sousBandeau(Minecraft mc) {
        if (mc.gui.screen() instanceof GardeScreen g) {
            int x = g.px0() + 40, y = Math.min(g.height - 30, g.py1() + 60);
            g.debugSouris = () -> new int[]{x, y};
        }
    }

    public static void pageSuivante(Minecraft mc) {
        GardePlaques v = vue(mc);
        if (v != null) v.debugPageSuivante();
    }

    public static String apercu(Minecraft mc) {
        GardePlaques v = vue(mc);
        return v == null ? "volet fermé" : v.debugApercu();
    }

    public static boolean valider(Minecraft mc) {
        GardePlaques v = vue(mc);
        return v != null && v.entree(false);
    }

    /** Banc : ce que voit le sélecteur (« visé 1, choisi 2, boîte 3×2×1 »). */
    public static String bilan(Minecraft mc) {
        GardePlaques v = vue(mc);
        if (v == null) return "volet fermé";
        SelecteurPlaque s = v.sel;
        return "vue " + v.vue + ", sélecteur " + s.actif + ", visé " + s.viseK + (s.viseLoin ? " (loin)" : "") + ", choisi " + s.choixK
            + " « " + s.libelleChoix() + " », repères " + MondePlaques.nombre() + ", ancre " + (v.ancre == null ? "aucune" : v.ancre.toString());
    }

    public static void fermer(Minecraft mc) {
        StaffClient.debugPlaques = null;
        // l'instantané simulé avait mis le mode staff : on le retire (indicateur « Mode staff · hors RP », rappel F7)
        StaffClient.debugReceive("{\"t\":\"mode\",\"staff\":false,\"anim\":false}");
        if (mc.gui.screen() instanceof GardeScreen) mc.setScreenAndShow(null);
    }
}
