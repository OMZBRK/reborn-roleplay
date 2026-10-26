package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Poste de garde v3 — direction A, « le volet » (maquettes validées le 14/10, {@code Maquettes/gameplan/04_staff/v3/
 * A_*}). Un panneau étroit (~202 GUI, 404 px en 1080p) collé à droite, <b>par-dessus le jeu qui continue</b> : le
 * monde et le HUD restent rendus et visibles, sans voile ni flou (le staff voit la scène pendant qu'il agit).
 *
 * <p>En-tête « POSTE DE GARDE » (ArcadePix or), puce du mode staff / animation, fermeture ; quatre onglets à icône au
 * trait : <b>Alertes</b> (en attente / traitées / les miennes, actions en mots), <b>Joueurs</b> (liste, 4 icônes
 * d'action au survol, clic droit = toutes les actions, fiche compacte — {@link GardeFiche} ; recherche universelle
 * façon B dans le champ — {@link GardeRecherche}), <b>Journal</b> (jetons de filtre, groupé par jour —
 * {@link GardeJournal}), <b>Outils</b> (annonces, séisme, chat staff, commandes, Infirmerie, modes —
 * {@link GardeOutils}). Aucun kanji (DA §3). Une action à paramètre s'ouvre dans le volet même
 * ({@link GardeFormulaire}).
 *
 * <p>Performance (DA §6) : chaque région est peinte par un {@link GardeArt.Pinceau} (aplats, filets, icônes et textes
 * en lots : quelques éléments GUI), la mise en page tient en quelques constantes, les textes coupés sont mémorisés.
 * Toutes les données viennent du serveur ({@link StaffClient}) ; chaque bouton envoie une demande que le serveur
 * revérifie. Protocole : {@code docs/technique/PLUGINS_REFERENCE.md} §4 bis.
 */
public class StaffScreen extends Screen {

    /** Largeur du volet (GUI) et marge à l'écran. */
    static final int PW = 202, MARGE = 8, H_TETE = 48, H_PIED = 19;

    protected record Hit(int x, int y, int w, int h, Runnable action, Runnable droit, String refus) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** Élément d'un menu contextuel (null = séparateur). */
    record Item(String icone, String label, char touche, String verrou, boolean danger, Runnable action) { }

    record Menu(int x, int y, int w, List<Item> items, Object source) { }

    protected final List<Hit> hits = new ArrayList<>();
    protected final GardeArt.Pinceau pz = new GardeArt.Pinceau();
    protected String tab = "alerts";
    protected String profileUuid;
    /** Sous-vue de l'onglet Outils (null = la liste des outils). */
    protected String outil;
    String alerteVue = "attente";
    protected int scroll, contentH;
    protected int ticks, mouseX, mouseY;
    protected long openedAt;
    protected boolean ouvert, dansCorps;
    protected Menu menu;
    protected String tipTexte;
    /** Raison donnée au clic sur le prochain bouton éteint. */
    protected String refusVerrou;
    protected EditBox searchBox;
    protected final List<EditBox> champs = new ArrayList<>();
    protected final Map<EditBox, String> indices = new HashMap<>();
    private final Map<String, String> fitCache = new HashMap<>();
    /** Filtre rapide de la liste des joueurs et tri. */
    String filtreJoueurs = "tous", tri = "pid";
    /** Ligne survolée (pour les icônes d'action). */
    String survolUuid;
    /** Alerte choisie pour « Valider… » / « Répondre » (formulaire dans le volet). */
    JsonObject alerteForm;

    protected final GardeFiche fiche = new GardeFiche(this);
    protected final GardeJournal journalVue = new GardeJournal(this);
    protected final GardeAnnonces annoncesVue = new GardeAnnonces(this);
    protected final GardeOutils outilsVue = new GardeOutils(this);
    protected final GardeFormulaire formulaire = new GardeFormulaire(this);
    protected final GardeRecherche recherche = new GardeRecherche(this);
    /** Onglet Douane (lot E, 25/10) : file, bureaux, dossier, décision — {@link GardeDouane}. */
    protected final GardeDouane douaneVue = new GardeDouane(this);

    public StaffScreen() {
        super(Component.literal("Poste de garde"));
    }

    /* =================================================================== cycle de vie */

    @Override
    protected void init() {
        pz.police(font);
        champs.clear();
        fitCache.clear();
        searchBox = box(searchBox, "Nom, pseudo ou #N°", 40);
        searchBox.setResponder(v -> { scroll = 0; recherche.modifie(v); });
        fiche.init();
        journalVue.init();
        annoncesVue.init();
        outilsVue.init();
        formulaire.init();
        douaneVue.init();
        if (!ouvert) {
            ouvert = true;
            openedAt = System.currentTimeMillis();
            StaffClient.send("refresh");
            fr.reborn.hud.douane.DouaneClient.demanderEtat();   // ignoré par le serveur sans droits d'examinateur
            RebornSounds.playReborn("sacoche.open", 1.15f, 0.4f);
        }
    }

    /** Champ de saisie sans bordure (le cadre est dessiné à part) ; garde la valeur après un redimensionnement. */
    protected EditBox box(EditBox ancien, String hint, int max) {
        // rendu vanilla coupé (texte, curseur, indice) : le pinceau redessine la saisie en Inter (champ(...))
        EditBox b = new EditBox(this.font, 0, 0, 100, 12, Component.literal(hint)) {
            @Override
            public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d) { }
        };
        b.setMaxLength(max);
        b.setHint(Component.empty());
        indices.put(b, hint == null ? "" : hint);
        b.setBordered(false);
        b.setTextShadow(false);
        b.setTextColor(CREME);
        b.setVisible(false);
        if (ancien != null) {
            b.setValue(ancien.getValue());
            b.setFocused(ancien.isFocused());
        }
        this.addRenderableWidget(b);
        champs.add(b);
        return b;
    }

    protected void indice(EditBox b, String hint) { indices.put(b, hint == null ? "" : hint); }

    net.minecraft.client.gui.Font police() { return font; }

    @Override
    public void onClose() {
        RebornSounds.playReborn("sacoche.close", 1.15f, 0.4f);
        super.onClose();
    }

    @Override
    public void removed() {
        StaffClient.clearProfile();
        douaneVue.fermer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 40 == 0) {
            StaffClient.send("refresh");
            if (profileUuid != null) requestProfile(profileUuid);
        }
        journalVue.tick();
        recherche.tick();
        douaneVue.tick();
    }

    /* =================================================================== géométrie */

    /** Volet élargi (carte des zones, réglages du KO, dossier de la douane) ? */
    protected boolean large() { return "douane".equals(tab) && douaneVue.large(); }

    protected int pw() { return large() ? Math.max(PW, Math.min(width - 2 * MARGE, 640)) : PW; }

    /** Glissement d'ouverture depuis la droite (GUI). */
    protected int glisse() {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 160f);
        return Math.round((1f - ease(t)) * 24);
    }

    protected int px0() { return width - MARGE - pw() + glisse(); }
    protected int px1() { return px0() + pw(); }
    protected int py0() { return MARGE; }
    protected int ix() { return px0() + 8; }
    protected int iw() { return pw() - 16; }
    protected int top() { return py0() + H_TETE; }
    protected int ph;
    /** Contexte de l'image en cours (dessins immédiats : carte de l'Infirmerie). */
    protected GuiGraphicsExtractor ctxCourant;
    protected int py1() { return py0() + ph; }
    /** Corps qui défile. */
    protected int vy0() { return top() + toolsH(); }
    protected int vy1() {
        if ("outils".equals(tab) && "chat".equals(outil) && !formulaire.actif()) return py1() - 30;
        return py1() - (pied() == null ? 6 : H_PIED + 2);
    }
    protected int vx0() { return ix(); }
    protected int vx1() { return ix() + iw(); }

    static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** Hauteur de la zone d'outils fixe de la vue. */
    protected int toolsH() {
        return switch (tab) {
            case "players" -> profileUuid != null ? 0 : recherche.active() ? 23 : 38;
            case "journal" -> journalVue.hauteurOutils();
            case "outils" -> outilsVue.hauteurOutils();
            case "douane" -> 0;
            default -> 16;
        };
    }

    /** Texte d'aide du pied (null = pas de pied). */
    protected String pied() {
        if (formulaire.actif()) return "Échap : annuler";
        return switch (tab) {
            case "players" -> profileUuid != null ? (fiche.persos.detailOuvert() ? "Échap : retour aux personnages" : "Échap : retour à la liste")
                    : recherche.active() ? "Flèches : choisir  ·  Entrée : ouvrir  ·  Échap : effacer"
                    : "Clic : fiche  ·  Clic droit : actions  ·  F7 : fermer";
            case "journal" -> "Ctrl + clic sur un N° : filtrer ce joueur";
            case "outils" -> outilsVue.pied();
            case "douane" -> douaneVue.pied();
            default -> "Une alerte = un clic : « Y aller » vous téléporte";
        };
    }

    /** Le volet prend-il toute la hauteur (listes longues) ou s'ajuste-t-il à son contenu ? */
    protected boolean pleineHauteur() {
        return switch (tab) {
            case "players" -> profileUuid == null;
            case "journal" -> true;
            case "outils" -> outilsVue.pleineHauteur();
            case "douane" -> douaneVue.pleineHauteur();
            default -> false;
        };
    }

    /* =================================================================== rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        // Rien : le jeu reste visible tel quel derrière le volet (pas de voile ni de flou plein écran, DA §6).
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (debugSouris != null) { int[] p = debugSouris.get(); mx = p[0]; my = p[1]; }
        mouseX = mx;
        mouseY = my;
        hits.clear();
        tipTexte = null;
        dansCorps = false;
        survolUuid = null;
        for (EditBox b : champs) b.setVisible(false);
        dessiner(ctx);
        super.extractRenderState(ctx, mx, my, delta);   // champs de saisie
        surcouches(ctx);
    }

    /** Tout le dessin du volet. */
    protected void dessiner(GuiGraphicsExtractor ctx) {
        ctxCourant = ctx;
        JsonObject snap = StaffClient.snapshot();
        // douane coupée, ou droits d'examinateur perdus : l'onglet disparaît
        if ("douane".equals(tab) && !fr.reborn.hud.douane.DouaneClient.ongletVisible()) { tab = "alerts"; douaneVue.retourFile(); }
        int full = height - 2 * MARGE;
        if (pleineHauteur()) ph = full;
        else ph = Math.max(140, Math.min(full, H_TETE + toolsH() + contentH + (pied() == null ? 6 : H_PIED + 2) + 4));

        int x0 = px0(), x1 = px1(), y0 = py0(), y1 = py1();
        // ombre en paliers (pas de flou), fond nuit, filet, filet d'or d'1 px en haut
        pz.fond(x1, y0 + 2, x1 + 2, y1 + 2, 0x6E000000);
        pz.fond(x0 + 2, y1, x1, y1 + 2, 0x6E000000);
        pz.fond(x1 + 2, y0 + 4, x1 + 3, y1 + 3, 0x37000000);
        pz.fond(x0 + 4, y1 + 2, x1 + 2, y1 + 3, 0x37000000);
        pz.fond(x0, y0, x1, y1, VOILE);
        pz.cadre(x0, y0, pw(), ph, FILET);
        pz.filetH(x0, x1, y0, OR_DOUX);
        entete(snap);
        onglets(snap);
        if (snap != null) outils(snap);
        pz.vider(ctx);

        // corps qui défile
        int a = vy0(), b = Math.max(vy0(), vy1());
        ctx.enableScissor(x0 + 1, a, x1 - 1, b);
        dansCorps = true;
        int y = a + 2 - scroll;
        int end;
        if (snap == null) end = vide(y, "Connexion au poste de garde…", null);
        else end = corps(snap, y);
        pz.vider(ctx);
        dansCorps = false;
        ctx.disableScissor();
        contentH = end - y + 2;
        int vue = b - a;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - vue)));
        barre(a, b, contentH);
        outilsVue.piedChat();
        String p = pied();
        if (p != null) {
            pz.filetH(ix(), ix() + iw(), y1 - H_PIED, FILET);
            pz.texteC(fit(p, iw(), 12, false), (x0 + x1) / 2f, y1 - H_PIED / 2f, 12, GRIS_2, false);
        }
        pz.vider(ctx);
    }

    /** Contenu du corps de la vue courante ; renvoie le bas du contenu. */
    protected int corps(JsonObject snap, int y) {
        if (formulaire.actif() && !"players".equals(tab)) return formulaire.dessiner(pz, ix(), y + 4, iw()) + 4;
        return switch (tab) {
            case "players" -> profileUuid != null ? fiche.corps(snap, y) : recherche.active() ? recherche.corps(snap, y) : joueurs(snap, y);
            case "journal" -> journalVue.corps(snap, y);
            case "outils" -> outilsVue.corps(snap, y);
            case "douane" -> douaneVue.corps(snap, y);
            default -> alertes(snap, y);
        };
    }

    /** Surcouches : menu contextuel, notifications, info-bulle. */
    protected void surcouches(GuiGraphicsExtractor ctx) {
        boolean rien = menu == null && tipTexte == null && StaffClient.toasts().length == 0;
        if (rien) return;
        ctx.nextStratum();
        if (menu != null) dessinerMenu();
        toasts();
        bulle();
        pz.vider(ctx);
    }

    /* ------------------------------------------------------------------- en-tête et onglets */

    protected void entete(JsonObject snap) {
        int x0 = px0(), y0 = py0();
        pz.arcade("POSTE DE GARDE", ix(), y0 + 6, OR);
        int rx = px1() - 8;
        boolean hovX = hover(rx - 10, y0 + 5, 12, 12);
        pz.icone("croix", rx - 8, y0 + 7, 8, hovX ? CREME : GRIS);
        hits.add(new Hit(rx - 10, y0 + 5, 12, 12, this::onClose, null, null));
        tip(rx - 10, y0 + 5, 12, 12, "Fermer (F7)");
        rx -= 15;
        if (snap == null) return;
        if (fr.reborn.hud.douane.DouaneClient.modeExaminateur()) {
            // mode examinateur (exclusif avec le mode staff et le mode animation) : puce or
            String le = "Examinateur";
            float we = pz.largeur(le, 12, false) + 12;
            float ex = rx - we;
            boolean hovE = hover(Math.round(ex), y0 + 6, Math.round(we), 11);
            pz.fond(Math.round(ex), y0 + 6, rx, y0 + 17, hovE ? 0xFF33271A : 0xFF281F12);
            pz.cadre(ex, y0 + 6, we, 11, 0xFF68522E);
            pz.pastille(ex + 5, y0 + 11.5f, 1.25f, OR);
            pz.texte(le, ex + 8.5f, y0 + 11.5f, 12, OR, false);
            hits.add(new Hit(Math.round(ex), y0 + 6, Math.round(we), 11, () -> {
                fr.reborn.hud.douane.DouaneClient.envoyer("mode");
                RebornSounds.uiClick();
            }, null, null));
            tip(Math.round(ex), y0 + 6, Math.round(we), 11,
                    "Mode examinateur actif : visible des candidats, plaque « Examinateur », aucune mécanique RP, tout est journalisé. Clique pour le couper.");
            return;
        }
        boolean staff = bool(snap, "modeStaff"), anim = bool(snap, "modeAnim");
        String lab = anim ? "Animation" : "Mode staff";
        float w = pz.largeur(lab, 12, false) + 12;
        int col = anim ? ANIM : staff ? OK : GRIS_2;
        float cx = rx - w;
        boolean hov = hover(Math.round(cx), y0 + 6, Math.round(w), 11);
        pz.fond(Math.round(cx), y0 + 6, rx, y0 + 17, anim ? 0xFF2A1E34 : staff ? 0xFF18261E : hov ? NUIT_3 : NUIT_2);
        pz.cadre(cx, y0 + 6, w, 11, anim ? 0xFF5C4478 : staff ? 0xFF34563E : FILET_2);
        pz.pastille(cx + 5, y0 + 11.5f, 1.5f, col);
        pz.texte(lab, cx + 8.5f, y0 + 11.5f, 12, col, false);
        hits.add(new Hit(Math.round(cx), y0 + 6, Math.round(w), 11, () -> {
            StaffClient.send(anim ? "modeanim" : "modestaff");
            RebornSounds.uiClick();
        }, null, null));
        tip(Math.round(cx), y0 + 6, Math.round(w), 11, anim
                ? "Mode animation actif : tu joues pour un event, mécaniques RP permises, tout est journalisé. Clique pour le couper."
                : staff ? "Mode staff actif : invisible aux joueurs, aucune mécanique RP, plaques au-dessus des têtes. Clique pour le couper."
                : "Mode staff inactif. Clique pour l'activer (invisible, aucune mécanique RP, plaques).");
    }

    private static final String[][] ONGLETS_BASE = {{"alerts", "cloche", "Alertes"}, {"players", "joueurs", "Joueurs"},
            {"journal", "journal", "Journal"}, {"outils", "outils", "Outils"}};
    private static final String[][] ONGLETS_DOUANE = {{"alerts", "cloche", "Alertes"}, {"players", "joueurs", "Joueurs"},
            {"journal", "journal", "Journal"}, {"outils", "outils", "Outils"}, {"douane", "barriere", "Douane"}};

    /** Onglets du volet : le 5ᵉ, Douane, seulement pour un examinateur quand la douane est active. */
    protected String[][] onglets() {
        return fr.reborn.hud.douane.DouaneClient.ongletVisible() ? ONGLETS_DOUANE : ONGLETS_BASE;
    }

    protected void onglets(JsonObject snap) {
        String[][] ONGLETS = onglets();
        int ty = py0() + 26;
        float[] ws = new float[ONGLETS.length];
        float[] lw = new float[ONGLETS.length];
        int[] badges = new int[ONGLETS.length];
        float total = 0;
        for (int i = 0; i < ONGLETS.length; i++) {
            boolean on = ONGLETS[i][0].equals(tab);
            lw[i] = pz.largeur(ONGLETS[i][2], 13, on);
            badges[i] = compteOnglet(ONGLETS[i][0], snap);
            ws[i] = 11 + lw[i] + (badges[i] > 0 ? 11 : 0);
            total += ws[i];
        }
        // cinq onglets dans le volet étroit : les icônes tombent faute de place (maquette J6)
        boolean icones = total + (ONGLETS.length - 1) * 8 <= iw();
        if (!icones) {
            total = 0;
            for (int i = 0; i < ONGLETS.length; i++) { ws[i] -= 11; total += ws[i]; }
        }
        float gap = Math.min(16, (iw() - total) / (ONGLETS.length - 1));
        float ox = ix();
        for (int i = 0; i < ONGLETS.length; i++) {
            String id = ONGLETS[i][0];
            boolean on = id.equals(tab);
            boolean hov = !on && hover(Math.round(ox) - 2, ty - 1, Math.round(ws[i]) + 4, 16);
            float lx = icones ? ox + 11 : ox;
            if (icones) pz.icone(ONGLETS[i][1], ox, ty + 1, 8, on ? OR : hov ? GRIS : GRIS_2);
            pz.texte(ONGLETS[i][2], lx, ty + 5, 13, on ? CREME : hov ? CREME : GRIS, on);
            if (badges[i] > 0) {
                float bx = lx + lw[i] + 3;
                String n = badges[i] > 99 ? "99" : String.valueOf(badges[i]);
                pz.fond(Math.round(bx), ty + 1, Math.round(bx) + 8, ty + 9, LAQUE);
                pz.texteC(n, bx + 4, ty + 4.75f, 10, CREME, true);
            }
            if (on) pz.fond(Math.round(ox) - 2, ty + 14, Math.round(ox + ws[i]) + 2, ty + 15, OR);
            hits.add(new Hit(Math.round(ox) - 2, ty - 1, Math.round(ws[i]) + 4, 16, () -> switchTab(id), null, null));
            ox += ws[i] + gap;
        }
        pz.filetH(ix(), ix() + iw(), ty + 15.5f, FILET);
    }

    protected int compteOnglet(String id, JsonObject snap) {
        if ("douane".equals(id)) return fr.reborn.hud.douane.DouaneClient.enAttente();
        return "alerts".equals(id) && snap != null ? arr(snap, "alerts").size() : 0;
    }

    protected void switchTab(String id) {
        if (!id.equals(tab)) RebornSounds.uiClick();
        else if ("players".equals(id) && profileUuid != null) { retourListe(); return; }
        else if ("outils".equals(id) && outil != null) { outil = null; scroll = 0; return; }
        else if ("douane".equals(id) && douaneVue.dossierU != null) { douaneVue.retourFile(); return; }
        tab = id;
        scroll = 0;
        menu = null;
        formulaire.fermer();
        if (!"players".equals(id)) { profileUuid = null; StaffClient.clearProfile(); }
        if ("journal".equals(id)) journalVue.ouvrir();
        if ("players".equals(id) && profileUuid == null) setFocused(searchBox);
        if ("douane".equals(id)) douaneVue.ouvrir();
    }

    /** Zone d'outils fixe (sous les onglets). */
    protected void outils(JsonObject snap) {
        int y = top();
        switch (tab) {
            case "players" -> {
                if (profileUuid != null) return;
                champ(searchBox, ix(), y, iw(), "chercher");
                if (recherche.active()) return;
                filtresJoueurs(snap, y + 23);
                pz.filetH(ix(), ix() + iw(), y + 37.5f, FILET);
            }
            case "journal" -> journalVue.outils(snap, y);
            case "outils" -> outilsVue.outils(snap, y);
            case "douane" -> { }   // tout l'onglet défile dans le corps
            default -> {
                float x = ix();
                String[][] vues = {{"attente", "En attente " + arr(snap, "alerts").size()}, {"traitees", "Traitées"}, {"miennes", "Les miennes"}};
                for (String[] v : vues) x += mot(v[1], x, y + 5, 13, alerteVue.equals(v[0]), () -> { alerteVue = v[0]; scroll = 0; }) + 9;
                boolean son = StaffClient.cloche;
                int bx = ix() + iw() - 9;
                pz.icone("cloche", bx, y + 1, 8, son ? OR : hover(bx - 2, y - 1, 12, 12) ? GRIS : GRIS_2);
                hits.add(new Hit(bx - 2, y - 1, 12, 12, () -> { StaffClient.cloche = !StaffClient.cloche; RebornSounds.uiClick(); }, null, null));
                tip(bx - 2, y - 1, 12, 12, son ? "Son des nouvelles alertes : activé" : "Son des nouvelles alertes : coupé");
                pz.filetH(ix(), ix() + iw(), y + 15.5f, FILET);
            }
        }
    }

    /* =================================================================== alertes */

    private static String[] typeAlerte(String kind) {
        return switch (kind) {
            case "mort" -> new String[]{"crane", "Mise à mort RP"};
            case "agression" -> new String[]{"epee", "Agression"};
            case "aide" -> new String[]{"aide", "Appel à l'aide"};
            case "signalement" -> new String[]{"drapeau", "Signalement"};
            case "yeux" -> new String[]{"observer", "Prélèvement d'œil"};
            case "vol" -> new String[]{"sac", "Vol de parchemin"};
            case "douane" -> new String[]{"barriere", "Douane"};
            default -> new String[]{"alerte", kind.isEmpty() ? "Alerte" : kind};
        };
    }

    static int couleurAlerte(String kind) {
        return switch (kind) {
            case "mort" -> ROUGE;
            case "agression" -> TERRE;
            case "aide" -> GLACE;
            case "signalement" -> NOIR_V;
            case "douane" -> OR;
            default -> STAFF;
        };
    }

    protected int alertes(JsonObject snap, int y) {
        if (!"attente".equals(alerteVue)) return traitees(snap, y, "miennes".equals(alerteVue));
        JsonArray al = arr(snap, "alerts");
        if (al.isEmpty()) {
            y = vide(y, "Aucune alerte. Le village est calme.", "Combats hors zone, appels à l'aide, signalements et mises à mort arrivent ici.");
        }
        int g = grade(snap);
        for (JsonElement e : al) {
            JsonObject a = e.getAsJsonObject();
            String kind = str(a, "kind"), id = str(a, "id");
            int col = couleurAlerte(kind);
            int h = 52;
            boolean hov = hover(px0() + 1, y, pw() - 2, h) && cliquable(y, h);
            if (hov) pz.fond(px0() + 1, y, px1() - 1, y + h, NUIT_3);
            pz.fond(px0() + 1, y + 5, px0() + 2, y + h - 5, col);
            String[] ty = typeAlerte(kind);
            pz.icone(ty[0], ix(), y + 3, 7, col);
            pz.capitales(ty[1], ix() + 10, y + 6.5f, 11, col);
            JsonArray ctx = arr(a, "ctx");
            String quand = ctx.isEmpty() ? "" : ctx.get(ctx.size() - 1).getAsString();
            pz.texteD(quand, ix() + iw(), y + 6.5f, 12, GRIS_2, false);
            String qui = (str(a, "pid").isEmpty() ? "" : str(a, "pid") + " ") + name(a);
            pz.texte(fit(qui, iw(), 15, false), ix(), y + 19, 15, CREME, false);
            List<String> det = new ArrayList<>();
            if (!str(a, "motif").isEmpty()) det.add(str(a, "motif"));
            for (int i = 0; i < ctx.size() - 1; i++) det.add(ctx.get(i).getAsString());
            if (num(a, "score") > 0) det.add("score " + num(a, "score"));
            String d = String.join(" · ", det);
            txt(d, ix(), y + 29.5f, iw(), 12, GRIS, false);
            JsonObject cible = a.deepCopy();
            cible.addProperty("_alerte", id);
            float x = ix();
            float cy = y + 42;
            if (kind.equals("douane")) {
                // candidat arrivé au hall : l'onglet Douane, pas de téléportation
                boolean dv = fr.reborn.hud.douane.DouaneClient.ongletVisible();
                motAction(dv ? "Onglet Douane" : "Fiche", x, cy, true, dv ? () -> switchTab("douane") : () -> openProfile(str(a, "uuid")));
                float iwD = pz.largeur("Ignorer", 13, false);
                motCouleur("Ignorer", ix() + iw() - iwD, cy, 13, GRIS_2, () -> dismiss(id));
                y += h;
                pz.filetH(ix(), ix() + iw(), y - 0.5f, FILET);
                continue;
            }
            x += motAction(kind.equals("signalement") ? "Voir le chat" : "Y aller", x, cy, true, kind.equals("signalement")
                    ? () -> { openProfile(str(a, "uuid")); fiche.ouvrirSection("sanctions"); }
                    : () -> GardeActions.envoyer(this, "tp", cible, Map.of())) + 10;
            switch (kind) {
                case "mort" -> {
                    refusVerrou = g < 2 ? "Réservé aux modérateurs et plus." : null;
                    x += motVerrou("Valider…", x, cy, g < 2, () -> validerRpk(a)) + 10;
                    motVerrou("Refuser", x, cy, g < 2, () -> killVerdict(id, false, ""));
                    refusVerrou = null;
                }
                case "aide" -> {
                    x += motAction("Répondre", x, cy, false, () -> repondre(a)) + 10;
                    motAction("Fiche", x, cy, false, () -> openProfile(str(a, "uuid")));
                }
                case "signalement" -> {
                    refusVerrou = g < 2 ? "Réservé aux modérateurs et plus." : null;
                    x += motVerrou("Observer", x, cy, g < 2, () -> GardeActions.envoyer(this, "watch", cible, Map.of())) + 10;
                    refusVerrou = null;
                    motAction("Fiche", x, cy, false, () -> openProfile(str(a, "uuid")));
                }
                default -> {
                    refusVerrou = g < 2 ? "Réservé aux modérateurs et plus." : null;
                    x += motVerrou("Observer", x, cy, g < 2, () -> GardeActions.envoyer(this, "watch", cible, Map.of())) + 10;
                    refusVerrou = null;
                    motAction("Fiche", x, cy, false, () -> openProfile(str(a, "uuid")));
                }
            }
            float iw2 = pz.largeur("Ignorer", 13, false);
            motCouleur("Ignorer", ix() + iw() - iw2, cy, 13, GRIS_2, () -> dismiss(id));
            y += h;
            pz.filetH(ix(), ix() + iw(), y - 0.5f, FILET);
        }
        // traitées récemment (aperçu)
        JsonArray tr = arr(snap, "traitees");
        if (!tr.isEmpty()) {
            y += 8;
            pz.capitales("Traitées récemment", ix(), y + 3, 11, GRIS_2);
            y += 11;
            for (int i = 0; i < Math.min(3, tr.size()); i++) {
                txt(ligneTraitee(tr.get(i).getAsJsonObject()), ix(), y + 3, iw(), 12, GRIS_3, false);
                y += 10;
            }
        }
        return y + 4;
    }

    private int traitees(JsonObject snap, int y, boolean miennes) {
        JsonArray tr = arr(snap, "traitees");
        int n = 0;
        for (JsonElement e : tr) {
            JsonObject t = e.getAsJsonObject();
            if (miennes && !bool(t, "moi")) continue;
            n++;
            int col = couleurAlerte(str(t, "kind"));
            pz.pastille(ix() + 2, y + 6, 1.5f, col);
            txt(ligneTraitee(t), ix() + 7, y + 6, iw() - 7, 12, GRIS, false);
            y += 13;
            pz.filetH(ix() + 7, ix() + iw(), y - 0.5f, SEP);
        }
        if (n == 0) y = vide(y, miennes ? "Tu n'as traité aucune alerte récemment." : "Aucune alerte traitée récemment.",
                "« Y aller », « Ignorer » et les verdicts de mise à mort apparaissent ici.");
        return y + 4;
    }

    private static String ligneTraitee(JsonObject t) {
        String[] ty = typeAlerte(str(t, "kind"));
        String qui = (str(t, "pid").isEmpty() ? "" : str(t, "pid") + " ") + str(t, "perso");
        return ty[1] + " · " + qui + " — " + str(t, "par") + " " + str(t, "quoi") + " · " + str(t, "time");
    }

    private void validerRpk(JsonObject a) {
        alerteForm = a;
        String id = str(a, "id");
        formulaire.ouvrir("crane", "Valider la mise à mort ?", (str(a, "motif").isEmpty() ? "" : str(a, "motif") + ". ")
                        + "La victime ne meurt pas tout de suite : le demandeur reçoit le droit d'achever.",
                "Valider", true, null, List.of(GardeFormulaire.Champ.texte("note", "Raison (obligatoire)", "Scène validée avec les deux joueurs…", true)),
                List.of("Inscrit au journal, visible des autres staffs."), v -> killVerdict(id, true, v.get("note")));
    }

    private void repondre(JsonObject a) {
        JsonObject cible = a.deepCopy();
        cible.addProperty("_alerte", str(a, "id"));
        formulaire.ouvrir("annonce", "Répondre à " + name(a), "Une ligne discrète en haut de son écran.", "Envoyer", false, null,
                List.of(GardeFormulaire.Champ.texte("m", "Message", "Un staff arrive, ne bouge pas.", true)),
                List.of(), v -> GardeActions.envoyer(this, "announce", cible, v));
    }

    /* =================================================================== joueurs */

    private static final String[][] FILTRES = {{"tous", "Tous"}, {"terre", "À terre"}, {"geles", "Gelés"}, {"notes", "Notés"}};

    private boolean passeFiltre(JsonObject p, String f) {
        return switch (f) {
            case "terre" -> { String st = str(p, "state"); yield st.contains("terre") || st.contains("Inconscient"); }
            case "geles" -> bool(p, "gele");
            case "notes" -> num(p, "notes") > 0;
            default -> true;
        };
    }

    private void filtresJoueurs(JsonObject snap, int y) {
        float x = ix();
        JsonArray all = arr(snap, "players");
        for (String[] f : FILTRES) {
            int n = 0;
            for (JsonElement e : all) if (passeFiltre(e.getAsJsonObject(), f[0])) n++;
            boolean on = f[0].equals(filtreJoueurs);
            x += mot(f[1] + " " + n, x, y + 4.5f, 13, on, () -> { filtreJoueurs = f[0]; scroll = 0; }) + 9;
        }
        String t = switch (tri) { case "nom" -> "Nom"; case "etat" -> "État"; default -> "N°"; };
        float tw = pz.largeur(t, 12, false);
        int tx = Math.round(ix() + iw() - tw - 8);
        boolean hov = hover(tx - 2, y - 1, Math.round(tw) + 12, 12);
        pz.texte(t, tx, y + 4.5f, 12, hov ? CREME : GRIS_2, false);
        pz.icone("tri", tx + tw + 2, y + 1.5f, 6, hov ? CREME : GRIS_2);
        hits.add(new Hit(tx - 2, y - 1, Math.round(tw) + 12, 12, () -> {
            tri = switch (tri) { case "pid" -> "nom"; case "nom" -> "etat"; default -> "pid"; };
            RebornSounds.uiClick();
        }, null, null));
        tip(tx - 2, y - 1, Math.round(tw) + 12, 12, "Trier par N°, nom ou état");
    }

    /** Joueurs de la liste, filtrés et triés. */
    List<JsonObject> listeJoueurs(JsonObject snap) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : arr(snap, "players")) if (passeFiltre(e.getAsJsonObject(), filtreJoueurs)) out.add(e.getAsJsonObject());
        switch (tri) {
            case "nom" -> out.sort((a, b) -> name(a).compareToIgnoreCase(name(b)));
            case "etat" -> out.sort((a, b) -> Integer.compare(poidsEtat(b), poidsEtat(a)));
            default -> out.sort((a, b) -> Integer.compare(pidNum(a), pidNum(b)));
        }
        return out;
    }

    private static int pidNum(JsonObject p) {
        try { return Integer.parseInt(str(p, "pid")); } catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }

    private static int poidsEtat(JsonObject p) {
        String st = str(p, "state");
        if (st.contains("terre") || st.contains("Inconscient")) return 5;
        if (st.contains("ATA")) return 4;
        if (bool(p, "gele")) return 3;
        if (bool(p, "noir")) return 2;
        return st.isEmpty() ? 0 : 1;
    }

    /** État court affiché à droite d'une ligne et sa couleur. */
    static Object[] etatCourt(JsonObject p) {
        String st = str(p, "state");
        String premier = st.isEmpty() ? "" : st.split(" · ")[0];
        if (bool(p, "modeAnim")) return new Object[]{"Animation", ANIM};
        if (bool(p, "modeStaff")) return new Object[]{"Mode staff", STAFF};
        if (premier.contains("terre") || premier.contains("Inconscient")) return new Object[]{premier, TERRE};
        if (premier.startsWith("ATA")) return new Object[]{premier.replace(" · ", " "), ROUGE};
        if (bool(p, "noir")) return new Object[]{"Écran noir", NOIR_V};
        if (bool(p, "gele")) return new Object[]{"Gelé", GLACE};
        if (premier.contains("Duel")) return new Object[]{"En duel", TERRE};
        if (!premier.isEmpty()) return new Object[]{premier, GRIS};
        return new Object[]{"En forme", OK};
    }

    /** Marqueurs de la seconde ligne : étiquettes, notes, gel. */
    static Object[] marques(JsonObject p) {
        List<String> m = new ArrayList<>();
        for (JsonElement e : arr(p, "etiquettes")) m.add(e.getAsString());
        int n = num(p, "notes");
        if (n > 0) m.add(n + " note" + (n > 1 ? "s" : ""));
        if (bool(p, "gele") && !etatCourt(p)[0].equals("Gelé")) m.add("gelé");
        String s = String.join(" · ", m);
        int col = s.contains("surveill") ? TERRE : s.contains("avertissement") ? ROUGE : s.equals("gelé") ? GLACE : GRIS;
        return new Object[]{s, col};
    }

    protected int joueurs(JsonObject snap, int y) {
        List<JsonObject> l = listeJoueurs(snap);
        if (arr(snap, "players").isEmpty()) return vide(y, "Personne en ligne pour l'instant.", null);
        if (l.isEmpty()) return vide(y, "Aucun joueur pour ce filtre.", "Clique sur « Tous » pour revoir la liste.");
        for (JsonObject p : l) y = ligneJoueur(p, y, false, null);
        return y + 2;
    }

    /**
     * Une ligne de joueur (23 GUI) : visage, nom RP, « N° · pseudo · grade », état à droite (pastille), marqueurs ;
     * au survol, quatre icônes d'action (aller, ramener, geler, plus). {@code surligne} : terme de recherche à
     * surligner dans le nom ; {@code choisie} : ligne choisie au clavier (recherche).
     */
    protected int ligneJoueur(JsonObject p, int y, boolean choisie, String surligne) {
        int h = 23;
        String uuid = str(p, "uuid");
        boolean horsLigne = p.has("online") && !bool(p, "online");
        boolean hov = menu == null && hover(px0() + 1, y, pw() - 2, h) && cliquable(y, h);
        if (hov) survolUuid = uuid;
        if (hov || choisie) {
            pz.fond(px0() + 1, y, px1() - 1, y + h, NUIT_3);
            pz.fond(px0() + 1, y, px0() + 2, y + h, OR);
        }
        pz.visage(uuid, ix(), y + 5.5f, 12);
        float tx = ix() + 18, droite = ix() + iw();
        float wmax;
        if (hov && !horsLigne) {
            // actions éclair : 1 clic, sans ouvrir la fiche
            float bx = droite;
            String[] ics = {"plus", "geler", "ramener", "aller"};
            GardeActions.Action[] acts = {null, GardeActions.GELER, GardeActions.RAMENER, GardeActions.ALLER};
            int g = grade(StaffClient.snapshot());
            for (int k = 0; k < 4; k++) {
                bx -= 14;
                int X = Math.round(bx), Y = y + 5;
                boolean on = hover(X, Y, 13, 13);
                GardeActions.Action a = acts[k];
                String refus = a == null ? null : GardeActions.refus(a, g, p);
                pz.fond(X, Y, X + 13, Y + 13, on ? NUIT_3 : NUIT_2);
                pz.cadre(X, Y, 13, 13, on ? OR_DOUX : "aller".equals(ics[k]) ? OR_DOUX : FILET_2);
                String ic = a == GardeActions.GELER && GardeActions.gele(p) ? "geler" : ics[k];
                pz.icone(ic, X + 2.5f, Y + 2.5f, 8, refus != null ? GRIS_3 : "aller".equals(ics[k]) ? OR : CREME);
                if (a == null) {
                    final int mx = X, my = Y + 13;
                    hits.add(new Hit(X, Y, 13, 13, () -> ouvrirMenuJoueur(p, mx, my), null, null));
                    if (on) tipGauche(Math.round(droite - 56), Y, "Toutes les actions");
                } else {
                    hits.add(new Hit(X, Y, 13, 13, () -> GardeActions.lancer(this, a, p), null, null));
                    if (on) tipGauche(Math.round(droite - 56), Y, a.libelle(p).replace("…", "") + "  " + a.touche());
                }
            }
            wmax = bx - tx - 4;
        } else {
            Object[] et = etatCourt(p);
            String e = horsLigne ? "hors ligne" + (str(p, "vu").isEmpty() ? "" : " · vu " + str(p, "vu")) : (String) et[0];
            int col = horsLigne ? GRIS_2 : (int) et[1];
            float ew = pz.texteD(e, droite, y + 7.5f, 12, col, false);
            if (!horsLigne) pz.pastille(droite - ew - 4.5f, y + 7.5f, 1.5f, col);
            wmax = droite - ew - 9 - tx;
            Object[] mq = marques(p);
            if (!((String) mq[0]).isEmpty() && !horsLigne) pz.texteD(fit((String) mq[0], 70, 12, false), droite, y + 16.5f, 12, (int) mq[1], false);
        }
        String nom = name(p);
        if (surligne != null && !surligne.isEmpty()) surligner(nom, surligne, tx, y + 7.5f, wmax, 15, horsLigne ? GRIS : CREME);
        else pz.texte(fit(nom, wmax, 15, false), tx, y + 7.5f, 15, horsLigne ? GRIS : CREME, false);
        String sous = (str(p, "pid").isEmpty() ? "—" : str(p, "pid")) + " · " + str(p, "name") + (str(p, "rank").isEmpty() ? "" : " · " + str(p, "rank"));
        float sousMax = hov ? wmax : Math.min(wmax + 30, iw() - 18 - 74);
        if (surligne != null && !surligne.isEmpty() && !GardeActions.sans(nom).contains(GardeActions.sans(surligne)))
            surligner(sous, surligne, tx, y + 16.5f, sousMax, 12, GRIS_2);
        else pz.texte(fit(sous, sousMax, 12, false), tx, y + 16.5f, 12, GRIS_2, false);
        if (cliquable(y, h)) hits.add(new Hit(px0() + 1, y, pw() - 2, h, () -> openProfile(uuid), () -> ouvrirMenuJoueur(p, mouseX, mouseY), null));
        pz.filetH(tx, droite, y + h - 0.5f, SEP);
        return y + h;
    }

    /** Texte avec la partie qui correspond à {@code terme} en or (recherche). */
    protected void surligner(String s, String terme, float x, float cy, float maxW, int taille, int col) {
        String f = fit(s, maxW, taille, false);
        int i = GardeActions.sans(f).indexOf(GardeActions.sans(terme));
        if (i < 0 || terme.isBlank()) { pz.texte(f, x, cy, taille, col, false); return; }
        int j = Math.min(f.length(), i + terme.trim().length());
        float xx = x;
        xx += pz.texte(f.substring(0, i), xx, cy, taille, col, false);
        xx += pz.texte(f.substring(i, j), xx, cy, taille, OR_CLAIR, false);
        pz.texte(f.substring(j), xx, cy, taille, col, false);
    }

    /* =================================================================== menu contextuel */

    void ouvrirMenuJoueur(JsonObject p, int x, int y) {
        List<Item> items = new ArrayList<>();
        int g = grade(StaffClient.snapshot());
        for (GardeActions.Action a : GardeActions.menuLigne()) items.add(a == null ? null : item(a, p, g));
        ouvrirMenu(items, x, y, p);
    }

    /** Élément de menu pour une action (grisé avec cadenas et « Admin » si le grade ne suffit pas). */
    Item item(GardeActions.Action a, JsonObject p, int g) {
        String verrou = g < a.grade() ? gradeName(a.grade()) : null;
        String refus = verrou == null ? GardeActions.refus(a, g, p) : null;
        Runnable r = refus != null ? () -> { StaffClient.toast(refus); RebornSounds.deny(); } : () -> GardeActions.lancer(this, a, p);
        boolean danger = a == GardeActions.RESET || a == GardeActions.SUPPR;
        return new Item(a.icone(), a.libelle(p), a.touche(), verrou, danger, refus != null ? r : r);
    }

    /** Ouvre un menu à gauche du volet, à hauteur de y (recalé dans l'écran). */
    void ouvrirMenu(List<Item> items, int x, int y, Object source) {
        int w = 150;
        int h = 5;
        for (Item it : items) h += it == null ? 5 : 17;
        int mx = Math.min(x, px0() - w - 4);
        if (mx < 4) mx = Math.max(4, x - w);
        int my = Math.max(4, Math.min(y - 20, height - h - 4));
        menu = new Menu(mx, my, w, items, source);
        RebornSounds.uiClick();
    }

    private void dessinerMenu() {
        Menu m = menu;
        int h = 5;
        for (Item it : m.items()) h += it == null ? 5 : 17;
        int x = m.x(), y = m.y(), w = m.w();
        pz.fond(x + w, y + 2, x + w + 2, y + h + 2, 0x78000000);
        pz.fond(x + 2, y + h, x + w, y + h + 2, 0x78000000);
        pz.fond(x, y, x + w, y + h, 0xF8110B0D);
        pz.cadre(x, y, w, h, FILET_2);
        pz.filetH(x, x + w, y, OR_DOUX);
        int yy = y + 2;
        for (Item it : m.items()) {
            if (it == null) { pz.filetH(x + 6, x + w - 6, yy + 2.5f, FILET); yy += 5; continue; }
            boolean verrou = it.verrou() != null;
            boolean hov = hover(x + 2, yy, w - 4, 17);
            if (hov && !verrou) {
                pz.fond(x + 2, yy, x + w - 2, yy + 17, LAQUE_SOMBRE);
                pz.fond(x + 2, yy, x + 3, yy + 17, OR);
            }
            int col = verrou ? GRIS_3 : it.danger() ? ROUGE : CREME;
            pz.icone(it.icone(), x + 8, yy + 4.5f, 8, verrou ? GRIS_3 : it.danger() ? ROUGE : GRIS);
            pz.texte(fit(it.label(), w - 50, 14, false), x + 21, yy + 8.5f, 14, col, false);
            if (verrou) {
                pz.icone("verrou", x + w - 15, yy + 5, 7, GRIS_3);
                pz.texteD(it.verrou(), x + w - 18, yy + 8.5f, 11, GRIS_3, false);
            } else if (it.touche() != 0) touche(String.valueOf(it.touche()), x + w - 6, yy + 4);
            final Item fi = it;
            hits.add(new Hit(x + 2, yy, w - 4, 17, () -> {
                menu = null;
                if (fi.verrou() != null) { StaffClient.toast("Réservé au grade " + fi.verrou() + " et plus."); RebornSounds.deny(); }
                else fi.action().run();
            }, null, null));
            yy += 17;
        }
    }

    /** Petite touche de clavier (raccourci) calée à droite en xd, haut en y ; renvoie sa largeur. */
    float touche(String t, float xd, float y) {
        float w = Math.max(9, pz.largeur(t, 11, true) + 5);
        float x = xd - w;
        pz.fond(Math.round(x), Math.round(y), Math.round(xd), Math.round(y) + 9, NUIT_3);
        pz.cadre(x, y, w, 9, FILET_2);
        pz.texteC(t, x + w / 2f, y + 4.5f, 11, GRIS, true);
        return w;
    }

    /* =================================================================== surcouches : notifications, info-bulle */

    protected void toasts() {
        StaffClient.Toast[] ts = StaffClient.toasts();
        if (ts.length == 0) return;
        int y = py1() - H_PIED - 18;
        long now = System.currentTimeMillis();
        for (int i = ts.length - 1; i >= 0; i--) {
            StaffClient.Toast t = ts[i];
            String s = fit(t.text(), iw() - 12, 12, false);
            float w = pz.largeur(s, 12, false) + 12;
            float x = px0() + pw() / 2f - w / 2f;
            int dy = Math.round((1f - ease((now - t.at()) / 160f)) * 6);
            int X = Math.round(x), Y = y + dy;
            pz.fond(X, Y, X + Math.round(w), Y + 14, 0xF5080506);
            pz.cadre(X, Y, Math.round(w), 14, FILET_2);
            pz.fond(X, Y, X + 1, Y + 14, OR);
            pz.texte(s, x + 6, Y + 7, 12, CREME, false);
            y -= 17;
        }
    }

    protected void bulle() {
        if (tipTexte == null || tipTexte.isBlank()) return;
        List<String> lignes = wrap(tipTexte, 150, 12);
        float w = 0;
        for (String l : lignes) w = Math.max(w, pz.largeur(l, 12, false));
        int bw = Math.round(w) + 10, bh = lignes.size() * 8 + 6;
        int x = Math.min(width - bw - 4, mouseX + 8), y = mouseY + 12;
        if (x + bw > px0() && mouseX < px0()) x = Math.max(4, mouseX - bw - 6);
        if (y + bh > height - 4) y = mouseY - bh - 4;
        pz.fond(x, y, x + bw, y + bh, 0xF5080506);
        pz.cadre(x, y, bw, bh, FILET_2);
        for (int i = 0; i < lignes.size(); i++) pz.texte(lignes.get(i), x + 5, y + 7 + i * 8, 12, CREME, false);
    }

    /** Info-bulle si la souris est sur (x, y, w, h) (et, dans le corps, dans sa partie visible). */
    protected void tip(int x, int y, int w, int h, String texte) {
        if (texte == null || menu != null) return;
        if (dansCorps && (mouseY < vy0() || mouseY >= vy1())) return;
        if (hover(x, y, w, h)) tipTexte = texte;
    }

    /** Bulle courte posée à gauche d'un groupe d'icônes (au survol), comme sur la maquette A_01. */
    private void tipGauche(int xd, int y, String t) {
        float w = pz.largeur(t, 12, false) + 9;
        int X = Math.round(xd - 4 - w);
        pz.fond(X, y, X + Math.round(w), y + 12, 0xF5080506);
        pz.cadre(X, y, Math.round(w), 12, FILET_2);
        pz.texteC(t, X + w / 2f, y + 6, 12, CREME, false);
    }

    /* =================================================================== primitives */

    /** Mot de filtre : gras crème souligné d'or quand actif, gris sinon (souligné au survol). */
    float mot(String s, float x, float cy, int taille, boolean actif, Runnable r) {
        float w = pz.largeur(s, taille, actif);
        int X = Math.round(x), Y = Math.round(cy - 6);
        boolean hov = hover(X - 1, Y, Math.round(w) + 2, 12) && cliquable(Y, 12);
        pz.texte(s, x, cy, taille, actif || hov ? CREME : GRIS, actif);
        if (actif) pz.filetH(x, x + w, cy + 5.5f, OR);
        else if (hov) pz.filetH(x, x + w, cy + 5.5f, GRIS);
        if (r != null && cliquable(Y, 12)) hits.add(new Hit(X - 1, Y, Math.round(w) + 2, 12, () -> { r.run(); RebornSounds.uiClick(); }, null, null));
        return w;
    }

    /** Mot d'action (13) : primaire en or gras, sinon crème ; souligné au survol. */
    float motAction(String s, float x, float cy, boolean primaire, Runnable r) {
        float w = pz.largeur(s, 13, primaire);
        int X = Math.round(x), Y = Math.round(cy - 6);
        boolean hov = hover(X - 1, Y, Math.round(w) + 2, 12) && cliquable(Y, 12);
        int col = primaire ? (hov ? OR_CLAIR : OR) : CREME;
        pz.texte(s, x, cy, 13, col, primaire);
        if (hov) pz.filetH(x, x + w, cy + 5.5f, col);
        if (r != null && cliquable(Y, 12)) hits.add(new Hit(X - 1, Y, Math.round(w) + 2, 12, () -> { r.run(); RebornSounds.uiClick(); }, null, null));
        return w;
    }

    /** Mot d'une couleur donnée (souligné au survol). */
    float motCouleur(String s, float x, float cy, int taille, int col, Runnable r) {
        float w = pz.largeur(s, taille, false);
        int X = Math.round(x), Y = Math.round(cy - 6);
        boolean hov = hover(X - 1, Y, Math.round(w) + 2, 12) && cliquable(Y, 12);
        pz.texte(s, x, cy, taille, hov ? CREME : col, false);
        if (hov) pz.filetH(x, x + w, cy + 5.5f, CREME);
        if (r != null && cliquable(Y, 12)) hits.add(new Hit(X - 1, Y, Math.round(w) + 2, 12, r, null, null));
        return w;
    }

    /** Mot d'action, éteint (gris, cadenas au clic) si {@code verrou}. */
    float motVerrou(String s, float x, float cy, boolean verrou, Runnable r) {
        if (!verrou) return motAction(s, x, cy, false, r);
        float w = pz.largeur(s, 13, false);
        pz.texte(s, x, cy, 13, GRIS_3, false);
        int X = Math.round(x), Y = Math.round(cy - 6);
        String raison = refusVerrou;
        if (cliquable(Y, 12)) hits.add(new Hit(X - 1, Y, Math.round(w) + 2, 12, null, null, raison));
        return w;
    }

    /** Bouton plein (laque) ou contour, h 17 ; éteint = gris + cadenas. Renvoie sa largeur. */
    int bouton(int x, int y, int w, String label, boolean plein, Runnable r, boolean eteint) {
        int h = 17;
        boolean hov = !eteint && hover(x, y, w, h) && cliquable(y, h);
        if (eteint) {
            pz.fond(x, y, x + w, y + h, NUIT_3);
            pz.cadre(x, y, w, h, FILET);
            pz.texteC(fit(label, w - 14, 13, true), x + w / 2f - 3, y + 8.5f, 13, GRIS_3, true);
            pz.icone("verrou", x + w - 10, y + 5, 6, GRIS_3);
        } else if (plein) {
            pz.fond(x, y, x + w, y + h, hov ? 0xFFB41E33 : LAQUE);
            pz.filetH(x, x + w, y, LAQUE_HAUT);
            pz.texteC(fit(label, w - 6, 13, true), x + w / 2f, y + 8.5f, 13, CREME, true);
        } else {
            pz.fond(x, y, x + w, y + h, hov ? NUIT_3 : NUIT_2);
            pz.cadre(x, y, w, h, hov ? OR_DOUX : FILET_2);
            pz.texteC(fit(label, w - 6, 13, false), x + w / 2f, y + 8.5f, 13, CREME, false);
        }
        if (cliquable(y, h)) hits.add(new Hit(x, y, w, h, eteint ? null : () -> { if (r != null) r.run(); }, null, refusVerrou));
        return w;
    }

    int largeurBouton(String label) { return Math.max(56, Math.round(pz.largeur(label, 13, true)) + 20); }

    /** Champ de saisie (h 17) : fond, filet (or si actif), icône facultative, indice gris quand vide. */
    void champ(EditBox b, int x, int y, int w, String icone) {
        boolean focus = b.isFocused();
        pz.fond(x, y, x + w, y + 17, NUIT_2);
        pz.cadre(x, y, w, 17, focus ? OR_DOUX : FILET_2);
        int tx = x + 5;
        if (icone != null) {
            pz.icone(icone, x + 5, y + 4.5f, 8, focus ? OR : GRIS);
            tx = x + 17;
        }
        boolean visible = !dansCorps || y >= vy0() && y + 17 <= vy1();
        b.setVisible(visible);
        b.setX(tx);
        b.setY(y + 5);
        b.setWidth(Math.max(10, x + w - 5 - tx));
        String h = indices.get(b);
        if (visible && b.getValue().isEmpty() && h != null && !h.isEmpty()) pz.texte(fit(h, x + w - 5 - tx, 14, false), tx, y + 8.5f, 14, GRIS_2, false);
        if (visible) {
            // saisie en Inter 14 : défile pour garder le curseur visible
            String v = b.getValue();
            float avail = x + w - 5 - tx;
            int cur = Math.max(0, Math.min(b.getCursorPosition(), v.length()));
            int start = 0;
            while (start < cur && pz.largeur(v.substring(start, cur), 14, false) > avail) start++;
            String vis = v.substring(start);
            while (!vis.isEmpty() && pz.largeur(vis, 14, false) > avail) vis = vis.substring(0, vis.length() - 1);
            if (!vis.isEmpty()) pz.texte(vis, tx, y + 8.5f, 14, CREME, false);
            if (focus && (System.currentTimeMillis() / 500) % 2 == 0) {
                float cx = tx + pz.largeur(v.substring(start, cur), 14, false);
                pz.px(cx, y + 4, cx + 1f / GardeArt.echelle(), y + 13, OR);
            }
        }
        if (visible) hits.add(new Hit(x, y, w, 17, () -> { setFocused(b); b.setFocused(true); }, null, null));
    }

    /** Barre de défilement fine, à droite du volet. */
    protected void barre(int y0, int y1, int total) {
        int vue = y1 - y0;
        if (total <= vue + 2 || vue < 20) return;
        int x = px1() - 4;
        int th = Math.max(14, vue * vue / total);
        int ty = y0 + Math.round((vue - th) * (scroll / (float) Math.max(1, total - vue)));
        pz.fond(x, y0, x + 1, y1, 0xFF241A1E);
        pz.fond(x, ty, x + 1, ty + th, FILET_2);
    }

    /** État vide : une phrase, une aide plus petite. */
    protected int vide(int y, String phrase, String aide) {
        float cx = ix() + iw() / 2f;
        pz.texteC(fit(phrase, iw(), 14, false), cx, y + 18, 14, GRIS, false);
        if (aide != null) {
            List<String> l = wrap(aide, iw() - 10, 12);
            for (int i = 0; i < l.size(); i++) pz.texteC(l.get(i), cx, y + 30 + i * 8, 12, GRIS_2, false);
            return y + 36 + l.size() * 8;
        }
        return y + 34;
    }

    /** Texte coupé par « … » avec info-bulle du texte complet. */
    float txt(String s, float x, float cy, float maxW, int taille, int col, boolean gras) {
        if (s == null || s.isEmpty()) return 0;
        String f = fit(s, maxW, taille, gras);
        float w = pz.texte(f, x, cy, taille, col, gras);
        if (!f.equals(s)) tip(Math.round(x), Math.round(cy - 5), Math.round(maxW), 10, s);
        return w;
    }

    /** Coupe s pour tenir dans maxW (GUI) à cette taille (mémorisé). */
    String fit(String s, float maxW, int taille, boolean gras) {
        if (s == null) return "";
        String cle = taille + (gras ? "g" : "m") + ":" + Math.round(maxW * 4) + ":" + s;
        String r = fitCache.get(cle);
        if (r != null) return r;
        if (fitCache.size() > 4000) fitCache.clear();
        r = s;
        if (pz.largeur(r, taille, gras) > maxW) {
            String t = s;
            while (t.length() > 1 && pz.largeur(t + "…", taille, gras) > maxW) t = t.substring(0, t.length() - 1);
            r = t.stripTrailing() + "…";
        }
        fitCache.put(cle, r);
        return r;
    }

    List<String> wrap(String s, float maxW, int taille) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (pz.largeur(next, taille, false) > maxW && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else line = new StringBuilder(next);
        }
        if (!line.isEmpty()) out.add(line.toString());
        for (int i = 0; i < out.size(); i++) out.set(i, fit(out.get(i), maxW, taille, false));
        return out;
    }

    boolean hover(int x, int y, int w, int h) {
        if (menu != null && mouseX >= menu.x() && mouseX < menu.x() + menu.w() && mouseY >= menu.y()) {
            int mh = 5;
            for (Item it : menu.items()) mh += it == null ? 5 : 17;
            if (mouseY < menu.y() + mh) {
                // seules les lignes du menu réagissent sous le menu
                return x >= menu.x() && x < menu.x() + menu.w() && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            }
        }
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Un élément du corps est-il dans la partie visible (pas masqué par le défilement) ? */
    boolean cliquable(int y, int h) {
        return !dansCorps || y + h > vy0() && y < vy1();
    }

    /* =================================================================== navigation et actions */

    protected void retourListe() {
        profileUuid = null;
        scroll = 0;
        formulaire.fermer();
        StaffClient.clearProfile();
        setFocused(searchBox);
        RebornSounds.uiClick();
    }

    protected void openProfile(String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        tab = "players";
        profileUuid = uuid;
        scroll = 0;
        menu = null;
        formulaire.fermer();
        fiche.ouvrir();
        setFocused(null);
        requestProfile(uuid);
        RebornSounds.uiClick();
    }

    /** Ouvre la fiche de {@code p} (si besoin) puis un formulaire dans le volet. */
    void ficheFormulaire(JsonObject p, java.util.function.Consumer<GardeFormulaire> ouvrir) {
        String uuid = str(p, "uuid");
        if (!"players".equals(tab) || !uuid.equals(profileUuid)) openProfile(uuid);
        fiche.cible = p;
        ouvrir.accept(formulaire);
        scroll = 0;
    }

    /** Ouvre la fiche sur la section Sanctions dépliée. */
    void ficheSanctions(String uuid) {
        if (!"players".equals(tab) || !uuid.equals(profileUuid)) openProfile(uuid);
        fiche.ouvrirSection("sanctions");
    }

    protected void requestProfile(String uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "profile");
        o.addProperty("t", uuid);
        StaffClient.send(o);
    }

    protected void killVerdict(String id, boolean ok, String note) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "kill");
        o.addProperty("id", id);
        o.addProperty("v", ok ? "ok" : "no");
        if (note != null && !note.isBlank()) o.addProperty("note", note);
        StaffClient.send(o);
        if (ok) RebornSounds.confirm(); else RebornSounds.uiClick();
    }

    protected void dismiss(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "dismiss");
        o.addProperty("id", id);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    /* =================================================================== banc d'essai (StaffDebug) */

    /** Onglets du banc (anciens ids compris : annonces, chat, cmds → Outils). */
    void debugTab(String id) {
        switch (id) {
            case "annonces", "chat", "cmds", "seisme", "modes" -> { switchTab("outils"); outil = id; }
            default -> switchTab(id);
        }
    }

    void debugProfile(String uuid) { openProfile(uuid); }

    void debugSearch(String q) { tab = "players"; profileUuid = null; searchBox.setValue(q); setFocused(searchBox); searchBox.setFocused(true); scroll = 0; }

    void debugScroll(int px) { scroll = px; }

    void debugMenuPlus() { fiche.ouvrirPlus(); }

    void debugValiderRpk() {
        JsonArray al = arr(StaffClient.snapshot(), "alerts");
        if (!al.isEmpty()) validerRpk(al.get(0).getAsJsonObject());
    }

    void debugMenuFiltre() { journalVue.debugMenu(); }

    /** Banc : position de souris imposée (survols des captures). */
    java.util.function.Supplier<int[]> debugSouris;

    /** Banc : survole l'icône {@code k} (0 = plus, 1 = geler…) de la ligne {@code i} de la liste. */
    void debugSurvolLigne(int i, int k) {
        debugSouris = () -> new int[]{ix() + iw() - 14 * (k + 1) + 6, vy0() + 2 + 23 * i + 11};
    }

    /* =================================================================== entrées */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        double x = e.x(), y = e.y();
        if (e.button() == 0 || e.button() == 1) {
            boolean dansMenu = false;
            if (menu != null) {
                int mh = 5;
                for (Item it : menu.items()) mh += it == null ? 5 : 17;
                dansMenu = x >= menu.x() && x < menu.x() + menu.w() && y >= menu.y() && y < menu.y() + mh;
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (!h.in(x, y)) continue;
                if (menu != null && !dansMenu) { menu = null; return true; }
                if (e.button() == 1) {
                    if (h.droit() != null) { h.droit().run(); return true; }
                    continue;
                }
                if (h.action() != null) {
                    if (e.hasControlDown() && journalVue.ctrlClic(x, y)) return true;
                    h.action().run();
                } else {
                    StaffClient.toast(h.refus() != null ? h.refus() : "Ton grade ne permet pas cette action.");
                    RebornSounds.deny();
                }
                // un clic hors d'un champ lui retire le focus
                if (getFocused() instanceof EditBox b && !(b.isMouseOver(x, y))) { b.setFocused(false); }
                return true;
            }
            if (menu != null) { menu = null; return true; }
        }
        boolean r = super.mouseClicked(e, dbl);
        return r || x >= px0();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (menu != null) return true;
        scroll = Math.max(0, scroll - (int) Math.round(vAmount * 18));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        boolean saisie = getFocused() instanceof EditBox b && b.isFocused() && b.isVisible();
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null) { menu = null; return true; }
            if (formulaire.actif()) { formulaire.fermer(); return true; }
            if ("players".equals(tab) && profileUuid == null && !searchBox.getValue().isEmpty()) { searchBox.setValue(""); return true; }
            if ("players".equals(tab) && profileUuid != null && fiche.echap()) return true;
            if ("players".equals(tab) && profileUuid != null) { retourListe(); return true; }
            if ("journal".equals(tab) && journalVue.echap()) return true;
            if ("outils".equals(tab) && outil != null) { outil = null; scroll = 0; return true; }
            if ("douane".equals(tab) && douaneVue.echap()) return true;
            onClose();
            return true;
        }
        if (k == GLFW.GLFW_KEY_F7 && !saisie) { onClose(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            if (formulaire.actif() && formulaire.entree()) return true;
            if ("douane".equals(tab) && !formulaire.actif() && douaneVue.entree(e.hasControlDown())) return true;
            if ("players".equals(tab) && profileUuid == null && recherche.active() && recherche.entree()) return true;
            if (outilsVue.entree()) return true;
            if (journalVue.entree()) return true;
            if (fiche.entree()) return true;
        }
        if ("players".equals(tab) && profileUuid == null && recherche.active()) {
            if (k == GLFW.GLFW_KEY_DOWN) { recherche.deplacer(1); return true; }
            if (k == GLFW.GLFW_KEY_UP) { recherche.deplacer(-1); return true; }
        }
        if (!saisie && "players".equals(tab) && profileUuid != null && !formulaire.actif() && menu == null) {
            if (k == GLFW.GLFW_KEY_TAB) { fiche.ouvrirPlus(); return true; }
            if (fiche.raccourci(k)) return true;
        }
        return super.keyPressed(e);
    }

    /* =================================================================== JSON + grades */

    static String str(JsonObject o, String k) {
        try { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
        catch (RuntimeException e) { return ""; }
    }

    static int num(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsInt() : 0; } catch (RuntimeException e) { return 0; }
    }

    static long lng(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsLong() : 0L; } catch (RuntimeException e) { return 0L; }
    }

    static boolean bool(JsonObject o, String k) {
        try { return o != null && o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }

    static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
    }

    static String name(JsonObject p) {
        return str(p, "perso").isEmpty() ? str(p, "name") : str(p, "perso");
    }

    static int grade(JsonObject snap) { return num(snap, "grade"); }

    static String gradeName(int g) {
        return switch (g) { case 1 -> "Helper"; case 2 -> "Modo"; case 3 -> "Admin"; case 4 -> "Owner"; default -> "Joueur"; };
    }

    static int gradeColor(int g) {
        return switch (g) { case 1 -> 0xFF78B4C0; case 2 -> TERRE; case 3 -> ROUGE; case 4 -> OR; default -> GRIS; };
    }
}
