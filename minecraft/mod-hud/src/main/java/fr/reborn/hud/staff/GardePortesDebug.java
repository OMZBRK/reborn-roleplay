package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Pont du banc de l'outil « Portes » ({@code audit/Lot6PortesDebug}, {@code REBORN_LOT6_DEBUG=portes}) vers
 * {@link GardePortes} : instantané simulé, serveur des actions {@code porte*} simulé, vues, sélecteur, souris, et clics
 * sur les <b>vraies zones cliquables</b> du volet (retrouvées par leur gabarit dans la dernière image dessinée).
 * <b>Inactif en production</b> : rien ne l'appelle hors du banc. Aucune méthode ne rend la suite d'une porte en clair.
 */
public final class GardePortesDebug {

    private GardePortesDebug() {}

    /** Les actions {@code porte*} partent vers {@code serveur} (null : réseau). */
    public static void serveur(Consumer<JsonObject> serveur) { StaffClient.debugPortes = serveur; }

    /** Un message du canal {@code reborn:staff} simulé (instantané, liste {@code portes}, {@code porte_verif}, toast). */
    public static void recevoir(String raw) {
        if (raw != null) StaffClient.debugReceive(raw);
    }

    /** Horloge de l'écart entre deux vérifications de dalle (null : l'horloge murale). */
    public static void horloge(LongSupplier h) { GardePortes.horloge = h == null ? System::currentTimeMillis : h; }

    /** Instantané puis volet ouvert sur Outils › Portes. */
    public static void ouvrir(Minecraft mc, String snap) {
        StaffClient.debugEffacerToasts();
        StaffClient.debugReceive(snap);
        GardeScreen g = mc.gui.screen() instanceof GardeScreen cur ? cur : new GardeScreen("alerts");
        if (mc.gui.screen() != g) mc.setScreenAndShow(g);
        g.debugSouris = () -> new int[]{-100, -100};
        g.debugTab("outils");
        g.outilsVue.ouvrir("portes");
    }

    /** Retour à la liste des outils (la ligne « Portes scellées » et son compteur). */
    public static void outils(Minecraft mc) {
        GardeScreen g = ecran(mc);
        if (g == null) return;
        g.outil = null;
        g.scroll = 0;
    }

    private static GardeScreen ecran(Minecraft mc) {
        return mc.gui.screen() instanceof GardeScreen g ? g : null;
    }

    private static GardePortes vue(Minecraft mc) {
        GardeScreen g = ecran(mc);
        return g == null ? null : g.outilsVue.portesVue;
    }

    public static void liste(Minecraft mc) {
        GardePortes v = vue(mc);
        if (v != null) v.debugVue("liste", v.ficheId);
    }

    public static void fiche(Minecraft mc, String id) {
        GardePortes v = vue(mc);
        if (v != null) v.debugVue("fiche", id);
    }

    public static void effacerToasts() { StaffClient.debugEffacerToasts(); }

    /** Texte du dernier toast affiché (vide s'il n'y en a pas). */
    public static String dernierToast() {
        StaffClient.Toast[] t = StaffClient.toasts();
        return t.length == 0 ? "" : t[t.length - 1].text();
    }

    /* ---------------------------------------------------------------- souris, sélecteur */

    /** Souris posée en ({@code x}, {@code y}) GUI ; le survol est relu au tick suivant. */
    public static void souris(Minecraft mc, float x, float y) {
        GardeScreen g = ecran(mc);
        if (g != null) g.debugSouris = () -> new int[]{Math.round(x), Math.round(y)};
    }

    public static void clicMonde(Minecraft mc, float x, float y, boolean maj) {
        GardePortes v = vue(mc);
        if (v != null) v.clicMonde(x, y, maj);
    }

    /** Entrée (ou Ctrl + Entrée) passée à l'outil, comme {@code StaffScreen.keyPressed}. */
    public static boolean entree(Minecraft mc, boolean ctrl) {
        GardePortes v = vue(mc);
        return v != null && v.touche(GLFW.GLFW_KEY_ENTER, ctrl);
    }

    public static boolean retourArriere(Minecraft mc) {
        GardePortes v = vue(mc);
        return v != null && v.touche(GLFW.GLFW_KEY_BACKSPACE, false);
    }

    public static boolean echap(Minecraft mc) {
        GardePortes v = vue(mc);
        return v != null && v.echap();
    }

    /* ---------------------------------------------------------------- zones cliquables du volet */

    private static final int TOUT = -1;

    /** Zones du corps du volet au gabarit ({@code w}, {@code h}) ({@link #TOUT} : toute valeur), dans l'ordre du dessin. */
    private static List<StaffScreen.Hit> zones(GardeScreen g, int w, int h) {
        List<StaffScreen.Hit> l = new ArrayList<>();
        for (StaffScreen.Hit z : g.hits)
            if (z.y() >= g.vy0() && z.x() >= g.px0() && (w == TOUT || z.w() == w) && (h == TOUT || z.h() == h)) l.add(z);
        return l;
    }

    /** Clic gauche sur une zone, comme {@code StaffScreen.mouseClicked} : « ok », « refus : raison » ou « absente ». */
    private static String cliquer(StaffScreen.Hit z) {
        if (z == null) return "absente";
        if (z.action() == null) return "refus : " + z.refus();
        z.action().run();
        return "ok";
    }

    private static String cliquer(Minecraft mc, int w, int h, int n) {
        GardeScreen g = ecran(mc);
        if (g == null) return "absente";
        List<StaffScreen.Hit> l = zones(g, w, h);
        return cliquer(n < l.size() ? l.get(n) : null);
    }

    /** Rangée {@code n} de la liste des portes. */
    public static String rangee(Minecraft mc, int n) {
        GardeScreen g = ecran(mc);
        return g == null ? "absente" : cliquer(mc, g.pw() - 2, 26, n);
    }

    /** Action {@code n} de la fiche (0 : ouvrir ou fermer … 6 : retirer le sceau). */
    public static String action(Minecraft mc, int n) {
        GardeScreen g = ecran(mc);
        return g == null ? "absente" : cliquer(mc, g.pw() - 2, 16, n);
    }

    /** Case {@code signe} (0 à 11) de la grille des signes. */
    public static String caseSigne(Minecraft mc, int signe) { return cliquer(mc, 28, 29, signe); }

    /** Bouton « − » ou « + » de la levée (absent quand la borne est atteinte). */
    public static String levee(Minecraft mc, boolean plus) {
        GardeScreen g = ecran(mc);
        if (g == null) return "absente";
        for (StaffScreen.Hit z : zones(g, 12, 12)) if (z.x() == g.ix() + (plus ? 84 : 46)) return cliquer(z);
        return "absente";
    }

    /** La plaque laquée de la vue (« Nouvelle porte », « Sceller la porte », « Valider la dalle », « Enregistrer »…). */
    public static String plaque(Minecraft mc) {
        GardeScreen g = ecran(mc);
        if (g == null) return "absente";
        StaffScreen.Hit p = null;
        for (StaffScreen.Hit z : zones(g, TOUT, 17)) if (z.w() < g.iw() && z.x() + z.w() == g.ix() + g.iw()) p = z;
        return cliquer(p);
    }

    /** La plaque laquée de la vue est-elle entièrement dans la partie visible du corps (sans défiler) ? */
    public static boolean plaqueVisible(Minecraft mc) {
        GardeScreen g = ecran(mc);
        if (g == null) return false;
        for (StaffScreen.Hit z : zones(g, TOUT, 17))
            if (z.w() < g.iw() && z.x() + z.w() == g.ix() + g.iw()) return z.y() + z.h() <= g.vy1() && g.scroll == 0;
        return false;
    }

    /** Mot « Allumé » ({@code eteint} faux) ou « Éteint » de la ligne Système de la liste. */
    public static String systeme(Minecraft mc, boolean eteint) {
        GardeScreen g = ecran(mc);
        if (g == null) return "absente";
        List<StaffScreen.Hit> mots = new ArrayList<>();
        for (StaffScreen.Hit z : zones(g, TOUT, 12)) {
            if (!mots.isEmpty() && z.y() != mots.get(0).y()) break;   // la première rangée de mots du corps
            mots.add(z);
        }
        return cliquer(mots.size() == 2 ? mots.get(eteint ? 1 : 0) : null);
    }

    /** « Lever » du premier blocage de la fiche (mot calé à droite). */
    public static String lever(Minecraft mc) {
        GardeScreen g = ecran(mc);
        if (g == null) return "absente";
        for (StaffScreen.Hit z : zones(g, TOUT, 12)) if (z.x() + z.w() >= g.ix() + g.iw()) return cliquer(z);
        return "absente";
    }

    /** Formulaire de confirmation ouvert dans le volet ? */
    public static boolean confirmation(Minecraft mc) {
        GardeScreen g = ecran(mc);
        return g != null && g.formulaire.actif();
    }

    /** Entrée dans le formulaire de confirmation. */
    public static boolean confirmer(Minecraft mc) {
        GardeScreen g = ecran(mc);
        return g != null && g.formulaire.entree();
    }

    /* ---------------------------------------------------------------- état lu par le banc */

    public static void nom(Minecraft mc, String n) {
        GardePortes v = vue(mc);
        if (v != null) v.debugNom(n);
    }

    public static String vueCourante(Minecraft mc) {
        GardePortes v = vue(mc);
        return v == null ? "volet fermé" : v.sel.actif ? "selecteur" : v.vue;
    }

    public static String ficheId(Minecraft mc) {
        GardePortes v = vue(mc);
        return v == null || v.ficheId == null ? "" : v.ficheId;
    }

    /** État de la dalle du sélecteur : 0 rien, 1 refus local, 2 en attente, 3 valable, 4 refus du serveur. */
    public static int etatDalle(Minecraft mc) {
        GardePortes v = vue(mc);
        return v == null ? -1 : v.verifEtat;
    }

    public static String raisonDalle(Minecraft mc) {
        GardePortes v = vue(mc);
        return v == null ? "" : v.verifRaison;
    }

    public static int tailleSuite(Minecraft mc) {
        GardePortes v = vue(mc);
        return v == null ? -1 : v.suite.size();
    }

    public static int levee(Minecraft mc) {
        GardePortes v = vue(mc);
        return v == null ? -1 : v.leve;
    }

    public static String raisonSceller(Minecraft mc) {
        GardePortes v = vue(mc);
        return v == null ? "volet fermé" : v.raisonSceller();
    }

    /** Volet replié en bandeau (sélecteur) ? */
    public static boolean replie(Minecraft mc) {
        GardeScreen g = ecran(mc);
        return g != null && !g.pleineHauteur();
    }

    /** La ligne « Suite » de la fiche de {@code id} montre-t-elle exactement ces signes (noms dans l'ordre) ? */
    public static boolean ficheSuiteVaut(Minecraft mc, String id, int... signes) {
        GardePortes v = vue(mc);
        JsonObject p = v == null ? null : v.porte(id);
        if (p == null) return false;
        StringBuilder b = new StringBuilder();
        for (int i : signes) b.append(b.isEmpty() ? "" : "  ›  ").append(fr.reborn.hud.mudra.MudraClient.NOMS[i]);
        return String.join("", v.suiteAffichee(p, StaffScreen.bool(StaffClient.portes(), "peutGerer"), 4000)).contentEquals(b);
    }

    /**
     * La ligne « Suite » de la fiche de {@code id} telle qu'elle s'affiche, seulement si elle ne nomme aucun signe (sinon
     * « [suite] » : le banc n'écrit jamais une suite au journal).
     */
    public static String ficheSuiteSansSigne(Minecraft mc, String id) {
        GardePortes v = vue(mc);
        JsonObject p = v == null ? null : v.porte(id);
        if (p == null) return "porte absente";
        String t = String.join(" ", v.suiteAffichee(p, StaffScreen.bool(StaffClient.portes(), "peutGerer"), 4000));
        for (String n : fr.reborn.hud.mudra.MudraClient.NOMS) if (t.contains(n)) return "[suite]";
        return t;
    }

    /** L'état de l'outil en une ligne (jamais la suite : son nombre de signes seulement). */
    public static String bilan(Minecraft mc) {
        GardePortes v = vue(mc);
        return v == null ? "volet fermé" : v.debugBilan();
    }

    public static void fermer(Minecraft mc) {
        StaffClient.debugPortes = null;
        GardePortes.horloge = System::currentTimeMillis;
        // l'instantané simulé avait mis le mode staff : on le retire (indicateur « Mode staff · hors RP », rappel F7)
        StaffClient.debugReceive("{\"t\":\"mode\",\"staff\":false,\"anim\":false}");
        if (mc.gui.screen() instanceof GardeScreen) mc.setScreenAndShow(null);
    }
}
