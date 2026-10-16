package fr.reborn.hud.faction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.reborn.hud.faction.FactionArt.*;

/**
 * Le volet Faction (touche <b>J</b>, FACTION.md v3 §10, maquettes v3 01 à 05 validées le 16/10) : un panneau étroit
 * (202 GUI) collé à droite, <b>par-dessus le jeu qui continue</b>, sur le gabarit du Poste de garde v3 (nuit, filet
 * d'or, mots soulignés, aucun kanji). En-tête : emblème réel du village, nom en capitales pixel, tag. Onglets selon le
 * profil, filtrés par le serveur : <b>Ma section</b> · <b>Village</b> · <b>Sections</b> (gradés) · <b>Annonces</b> ·
 * <b>Gestion</b> (chef de faction, charges). Les pages sont dans {@link FactionPages} ; ici le cadre, les primitives,
 * les menus (fiche d'un membre, choix d'un grade) et le clavier. Rien n'est décidé ici : chaque bouton envoie une
 * demande que le serveur revérifie.
 */
public class FactionScreen extends Screen {

    static final int PW = 202, MARGE = 8, H_TETE = 48, H_PIED = 19;

    record Hit(int x, int y, int w, int h, Runnable action, String refus) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** Ligne d'un menu (null = séparateur) : libellé, à droite, éteinte, danger, action. */
    record Item(String label, String droite, boolean eteint, boolean danger, Runnable action) { }

    /** Menu posé à gauche du volet ; {@code tete} = en-tête de fiche (visage, nom, ligne) ou null. */
    record Menu(int x, int y, int w, List<Item> items, JsonObject tete, String pied) {
        int h() {
            int h = tete != null ? 32 : 4;
            for (Item it : items) h += it == null ? 5 : 17;
            return h + (pied != null ? 15 : 3);
        }
    }

    final List<Hit> hits = new ArrayList<>();
    final Pinceau pz = new Pinceau();
    final FactionPages pages = new FactionPages(this);
    String tab;
    /** Sous-vue de l'onglet (null = la page) : inviter, grades, lieux, journal, tenue, creer, charges, charge:<id>, section:<id>, form. */
    String sous;
    int scroll, contentH, ticks, mouseX, mouseY;
    long openedAt;
    boolean ouvert, dansCorps;
    Menu menu;
    String tipTexte;
    final List<EditBox> champs = new ArrayList<>();
    final Map<EditBox, String> indices = new HashMap<>();
    private final Map<String, String> fitCache = new HashMap<>();

    /** Banc : position de souris imposée (survols des captures). */
    java.util.function.Supplier<int[]> debugSouris;

    public FactionScreen() {
        super(Component.literal("Faction"));
    }

    /* =================================================================== cycle de vie */

    @Override
    protected void init() {
        pz.police(font);
        champs.clear();
        fitCache.clear();
        pages.init();
        if (!ouvert) {
            ouvert = true;
            openedAt = System.currentTimeMillis();
            RebornSounds.playReborn("sacoche.open", 1.1f, 0.4f);
        }
    }

    /** Champ de saisie sans rendu vanilla (le pinceau redessine la saisie en Inter) ; garde la valeur au redimensionnement. */
    EditBox box(EditBox ancien, String hint, int max) {
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

    @Override
    public void onClose() {
        RebornSounds.playReborn("sacoche.close", 1.1f, 0.4f);
        super.onClose();
    }

    @Override
    public void removed() {
        FactionClient.send("close");
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        pages.tick();
    }

    /* =================================================================== données */

    JsonObject vue() { return FactionClient.vue(); }

    List<String> onglets() {
        List<String> l = new ArrayList<>();
        JsonObject v = vue();
        for (JsonElement e : arr(v, "onglets")) l.add(e.getAsString());
        return l;
    }

    String tabCourant() {
        List<String> o = onglets();
        if (tab == null || !o.contains(tab)) tab = o.contains("section") ? "section" : "village";
        return tab;
    }

    /* =================================================================== géométrie */

    protected int glisse() {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 160f);
        return Math.round((1f - ease(t)) * 24);
    }

    int px0() { return width - MARGE - PW + glisse(); }

    int px1() { return px0() + PW; }

    int py0() { return MARGE; }

    int py1() { return height - MARGE; }

    int ix() { return px0() + 8; }

    int iw() { return PW - 16; }

    int top() { return py0() + H_TETE; }

    int vy0() { return top(); }

    int vy1() { return py1() - (pied() == null ? 6 : H_PIED + 2); }

    static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    String pied() { return pages.pied(); }

    /* =================================================================== rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        // Rien : le jeu reste visible derrière le volet (pas de voile ni de flou plein écran, DA §6).
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (debugSouris != null) { int[] p = debugSouris.get(); mx = p[0]; my = p[1]; }
        mouseX = mx;
        mouseY = my;
        hits.clear();
        tipTexte = null;
        dansCorps = false;
        for (EditBox b : champs) b.setVisible(false);
        dessiner(ctx);
        super.extractRenderState(ctx, mx, my, delta);
        surcouches(ctx);
    }

    private void dessiner(GuiGraphicsExtractor ctx) {
        JsonObject v = vue();
        int x0 = px0(), x1 = px1(), y0 = py0(), y1 = py1();
        pz.fond(x1, y0 + 2, x1 + 2, y1 + 2, 0x6E000000);
        pz.fond(x0 + 2, y1, x1, y1 + 2, 0x6E000000);
        pz.fond(x1 + 2, y0 + 4, x1 + 3, y1 + 3, 0x37000000);
        pz.fond(x0 + 4, y1 + 2, x1 + 2, y1 + 3, 0x37000000);
        pz.fond(x0, y0, x1, y1, VOILE);
        pz.cadre(x0, y0, PW, y1 - y0, FILET);
        pz.filetH(x0, x1, y0, OR_DOUX);
        entete(v);
        if (v != null && bool(v, "ok")) onglets(v);
        else pz.filetH(ix(), ix() + iw(), y0 + 22, FILET);
        pz.vider(ctx);

        int a = vy0(), b = Math.max(vy0(), vy1());
        ctx.enableScissor(x0 + 1, a, x1 - 1, b);
        dansCorps = true;
        int y = a + 2 - scroll;
        int end;
        if (v == null) end = vide(y, "Ouverture du volet…", null);
        else if (!bool(v, "ok")) end = vide(y, str(v, "raison"), "Le volet Faction montre ton village, ta section et les annonces.");
        else end = pages.corps(v, tabCourant(), y);
        pz.vider(ctx);
        dansCorps = false;
        ctx.disableScissor();
        contentH = end - y + 2;
        int vue = b - a;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - vue)));
        barre(a, b, contentH);
        String p = pied();
        if (p != null) {
            pz.filetH(ix(), ix() + iw(), y1 - H_PIED, FILET);
            pz.texteC(fit(p, iw(), 12, false), (x0 + x1) / 2f, y1 - H_PIED / 2f, 12, GRIS_2, false);
        }
        pz.vider(ctx);
    }

    private void surcouches(GuiGraphicsExtractor ctx) {
        boolean rien = menu == null && tipTexte == null && FactionClient.toasts().length == 0;
        if (rien) return;
        ctx.nextStratum();
        if (menu != null) dessinerMenu();
        toasts();
        bulle();
        pz.vider(ctx);
    }

    /* ------------------------------------------------------------------- en-tête et onglets */

    private void entete(JsonObject v) {
        int y0 = py0();
        JsonObject f = obj(v, "faction");
        float tx = ix();
        if (f != null) {
            String emb = str(f, "embleme");
            if (!emb.isEmpty()) {
                pz.insigne(emb, ix(), y0 + 7, 11, OR);
                tx += 16;
            }
            String nom = DaKit.sansAccents(str(f, "nom")).toUpperCase(java.util.Locale.ROOT);
            float w = pz.arcade(nom, tx, y0 + 8, OR);
            String tag = str(f, "tag");
            if (!tag.isEmpty()) {
                float bx = tx + w + 5, bw = pz.largeur(tag, 11, false) + 8;
                pz.cadre(bx, y0 + 7.5f, bw, 9, FILET_2);
                pz.texteC(tag, bx + bw / 2f, y0 + 12, 11, GRIS, false);
            }
        } else pz.arcade("FACTION", tx, y0 + 8, OR);
        int rx = px1() - 8;
        boolean hovX = hover(rx - 10, y0 + 5, 12, 12);
        pz.icone("croix", rx - 8, y0 + 8, 8, hovX ? CREME : GRIS);
        hits.add(new Hit(rx - 10, y0 + 5, 12, 12, this::onClose, null));
        tip(rx - 10, y0 + 5, 12, 12, "Fermer (" + HudKeybinds.nom(HudKeybinds.FACTION, "J") + ")");
        touche(HudKeybinds.nom(HudKeybinds.FACTION, "J"), rx - 13, y0 + 7.5f);
    }

    static final Map<String, String[]> ONGLETS = Map.of(
            "section", new String[]{"drapeau", "Ma section"}, "village", new String[]{"joueurs", "Village"},
            "sections", new String[]{"drapeau", "Sections"}, "annonces", new String[]{"cloche", "Annonces"},
            "gestion", new String[]{"outils", "Gestion"});

    private void onglets(JsonObject v) {
        List<String> ids = onglets();
        if (ids.isEmpty()) return;
        String cur = tabCourant();
        int ty = py0() + 26;
        float[] lw = new float[ids.size()];
        float total = 0;
        for (int i = 0; i < ids.size(); i++) {
            lw[i] = pz.largeur(ONGLETS.get(ids.get(i))[1], 13, true);
            total += 11 + lw[i];
        }
        boolean avecIc = total + 6 * (ids.size() - 1) <= iw();
        float[] ws = new float[ids.size()];
        float som = 0;
        for (int i = 0; i < ids.size(); i++) { ws[i] = (avecIc ? 11 : 0) + lw[i]; som += ws[i]; }
        float gap = ids.size() > 1 ? Math.min(14, (iw() - som) / (ids.size() - 1)) : 0;
        float ox = ix();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            String[] o = ONGLETS.get(id);
            boolean on = id.equals(cur);
            boolean hov = !on && hover(Math.round(ox) - 2, ty - 1, Math.round(ws[i]) + 4, 16);
            if (avecIc) pz.icone(o[0], ox, ty + 1, 8, on ? OR : hov ? GRIS : GRIS_2);
            pz.texte(o[1], ox + (avecIc ? 11 : 0), ty + 5, 13, on || hov ? CREME : GRIS, on);
            if (on) pz.fond(Math.round(ox) - 2, ty + 14, Math.round(ox + ws[i]) + 2, ty + 15, OR);
            hits.add(new Hit(Math.round(ox) - 2, ty - 1, Math.round(ws[i]) + 4, 16, () -> changerOnglet(id), null));
            ox += ws[i] + gap;
        }
        pz.filetH(ix(), ix() + iw(), ty + 15.5f, FILET);
    }

    void changerOnglet(String id) {
        if (!id.equals(tab)) RebornSounds.uiClick();
        else if (sous != null) { retour(); return; }
        tab = id;
        sous = null;
        scroll = 0;
        menu = null;
        pages.ouvertOnglet(id);
    }

    /** Ouvre une sous-vue de l'onglet courant. */
    void ouvrirSous(String s) {
        sous = s;
        scroll = 0;
        menu = null;
        setFocused(null);
        pages.ouvertSous(s);
        RebornSounds.uiClick();
    }

    void retour() {
        sous = null;
        scroll = 0;
        menu = null;
        setFocused(null);
        RebornSounds.uiClick();
    }

    /* =================================================================== menus */

    void ouvrirMenu(List<Item> items, int y, JsonObject tete, String piedMenu, int w) {
        Menu m0 = new Menu(0, 0, w, items, tete, piedMenu);
        int h = m0.h();
        int mx = Math.max(4, px0() - w - 6);
        int my = Math.max(4, Math.min(y - 8, height - h - 4));
        menu = new Menu(mx, my, w, items, tete, piedMenu);
        RebornSounds.uiClick();
    }

    private boolean dansMenu(double x, double y) {
        return menu != null && x >= menu.x() && x < menu.x() + menu.w() && y >= menu.y() && y < menu.y() + menu.h();
    }

    private void dessinerMenu() {
        Menu m = menu;
        int x = m.x(), y = m.y(), w = m.w(), h = m.h();
        pz.fond(x + w, y + 2, x + w + 2, y + h + 2, 0x78000000);
        pz.fond(x + 2, y + h, x + w, y + h + 2, 0x78000000);
        pz.fond(x, y, x + w, y + h, 0xF8110B0D);
        pz.cadre(x, y, w, h, FILET);
        pz.filetH(x, x + w, y, OR_DOUX);
        int yy = y + 2;
        if (m.tete() != null) {
            JsonObject t = m.tete();
            if (bool(t, "connu") && !str(t, "u").isEmpty()) pz.visage(uuid(str(t, "u")), x + 7, y + 7, 16, false);
            else if (bool(t, "connu")) pz.visage(null, x + 7, y + 7, 16, true);
            else pz.inconnu(x + 7, y + 7, 16);
            pz.texte(fit(str(t, "nom"), w - 46, 15, true), x + 29, y + 11, 15, CREME, true);
            pz.texte(fit(str(t, "ligne"), w - 36, 12, false), x + 29, y + 20, 12, GRIS, false);
            if (bool(t, "connu")) pz.pastille(x + w - 9, y + 11, 1.5f, bool(t, "online") ? OK : GRIS_3);
            yy = y + 30;
        }
        for (Item it : m.items()) {
            if (it == null) { pz.filetH(x + 6, x + w - 6, yy + 2.5f, FILET); yy += 5; continue; }
            boolean hov = !it.eteint() && hover(x + 2, yy, w - 4, 17);
            if (hov) {
                pz.fond(x + 2, yy, x + w - 2, yy + 17, LAQUE_SOMBRE);
                pz.fond(x + 2, yy, x + 3, yy + 17, OR);
            }
            int col = it.eteint() ? GRIS_3 : it.danger() ? ROUGE : CREME;
            pz.texte(fit(it.label(), w - 60, 14, false), x + 8, yy + 8.5f, 14, col, false);
            if (it.droite() != null) pz.texteD(fit(it.droite(), 56, 12, false), x + w - 7, yy + 8.5f, 12, it.eteint() ? GRIS_3 : GRIS, false);
            final Item fi = it;
            hits.add(new Hit(x + 2, yy, w - 4, 17, () -> {
                if (fi.eteint()) { RebornSounds.deny(); return; }
                menu = null;
                if (fi.action() != null) fi.action().run();
            }, null));
            yy += 17;
        }
        if (m.pied() != null) {
            pz.filetH(x + 6, x + w - 6, yy + 2, FILET);
            pz.texte(fit(m.pied(), w - 16, 11, false), x + 8, yy + 9, 11, GRIS_2, false);
        }
    }

    /* =================================================================== surcouches */

    private void toasts() {
        FactionClient.Toast[] ts = FactionClient.toasts();
        if (ts.length == 0) return;
        int y = py1() - H_PIED - 18;
        long now = System.currentTimeMillis();
        for (int i = ts.length - 1; i >= 0; i--) {
            FactionClient.Toast t = ts[i];
            String s = fit(t.text(), iw() - 12, 12, false);
            float w = pz.largeur(s, 12, false) + 12;
            float x = px0() + PW / 2f - w / 2f;
            int dy = Math.round((1f - ease((now - t.at()) / 160f)) * 6);
            int X = Math.round(x), Y = y + dy;
            pz.fond(X, Y, X + Math.round(w), Y + 14, 0xF5080506);
            pz.cadre(X, Y, Math.round(w), 14, FILET_2);
            pz.fond(X, Y, X + 1, Y + 14, t.refus() ? ROUGE : OR);
            pz.texte(s, x + 6, Y + 7, 12, CREME, false);
            y -= 17;
        }
    }

    private void bulle() {
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

    void tip(int x, int y, int w, int h, String texte) {
        if (texte == null || menu != null) return;
        if (dansCorps && (mouseY < vy0() || mouseY >= vy1())) return;
        if (hover(x, y, w, h)) tipTexte = texte;
    }

    /* =================================================================== primitives */

    /** Petite touche de clavier calée à droite en xd, haut en y ; renvoie sa largeur. */
    float touche(String t, float xd, float y) {
        float w = Math.max(9, pz.largeur(t, 11, true) + 5);
        float x = xd - w;
        pz.fond(Math.round(x), Math.round(y), Math.round(xd), Math.round(y) + 9, NUIT_3);
        pz.cadre(x, y, w, 9, FILET_2);
        pz.texteC(t, x + w / 2f, y + 4.5f, 11, GRIS, true);
        return w;
    }

    /** Mot de choix : gras crème souligné d'or quand actif, gris sinon (souligné au survol). */
    float mot(String s, float x, float cy, int taille, boolean actif, Runnable r) {
        float w = pz.largeur(s, taille, actif);
        int X = Math.round(x), Y = Math.round(cy - 6);
        boolean hov = hover(X - 1, Y, Math.round(w) + 2, 12) && cliquable(Y, 12);
        pz.texte(s, x, cy, taille, actif || hov ? CREME : GRIS, actif);
        if (actif) pz.fond(Math.round(x), Math.round(cy + 5), Math.round(x + w), Math.round(cy + 6), OR);
        else if (hov) pz.filetH(x, x + w, cy + 5.5f, GRIS);
        if (r != null && cliquable(Y, 12)) hits.add(new Hit(X - 1, Y, Math.round(w) + 2, 12, () -> { r.run(); RebornSounds.uiClick(); }, null));
        return w;
    }

    /** Lien d'action (or gras par défaut), souligné au survol ; aligné à gauche, ou à droite si {@code droite}. */
    float lien(String s, float x, float cy, int taille, int col, boolean gras, boolean droite, Runnable r) {
        float w = pz.largeur(s, taille, gras);
        float x0 = droite ? x - w : x;
        int X = Math.round(x0), Y = Math.round(cy - 6);
        boolean hov = r != null && hover(X - 1, Y, Math.round(w) + 2, 12) && cliquable(Y, 12);
        int c = hov ? (col == OR ? OR_CLAIR : CREME) : col;
        pz.texte(s, x0, cy, taille, c, gras);
        if (hov) pz.filetH(x0, x0 + w, cy + 5.5f, c);
        if (r != null && cliquable(Y, 12)) hits.add(new Hit(X - 1, Y, Math.round(w) + 2, 12, () -> { r.run(); RebornSounds.uiClick(); }, null));
        return w;
    }

    /** Bouton plaque laquée fine (maquettes : laque sombre, filet or) ; éteint = gris. */
    int bouton(int x, int y, int w, int h, String label, Runnable r, boolean eteint) {
        boolean hov = !eteint && hover(x, y, w, h) && cliquable(y, h);
        pz.fond(x, y, x + w, y + h, eteint ? NUIT_2 : hov ? 0xFF5E1824 : LAQUE_SOMBRE);
        pz.cadre(x, y, w, h, eteint ? FILET_2 : hov ? OR_CLAIR : OR);
        pz.texteC(fit(label, w - 6, 13, true), x + w / 2f, y + h / 2f, 13, eteint ? GRIS_2 : CREME, true);
        if (cliquable(y, h)) hits.add(new Hit(x, y, w, h, eteint ? null : () -> { if (r != null) r.run(); }, null));
        return w;
    }

    /** Champ de saisie : fond, filet (or si actif), icône facultative, indice gris quand vide ; suffixe facultatif. */
    void champ(EditBox b, int x, int y, int w, int h, String icone, String suffixe) {
        boolean focus = b.isFocused();
        pz.fond(x, y, x + w, y + h, NUIT_2);
        pz.cadre(x, y, w, h, focus ? OR_DOUX : FILET_2);
        int tx = x + 5;
        if (icone != null) {
            pz.icone(icone, x + 5, y + h / 2f - 4, 8, focus ? OR : GRIS_2);
            tx = x + 17;
        }
        float cy = y + h / 2f;
        boolean visible = !dansCorps || y >= vy0() && y + h <= vy1();
        b.setVisible(visible);
        b.setX(tx);
        b.setY(Math.round(cy - 4));
        b.setWidth(Math.max(10, x + w - 5 - tx));
        String hint = indices.get(b);
        if (visible && b.getValue().isEmpty() && hint != null && !hint.isEmpty()) pz.texte(fit(hint, x + w - 5 - tx, 13, false), tx, cy, 13, GRIS_2, false);
        if (visible) {
            String v = b.getValue();
            float avail = x + w - 5 - tx;
            int cur = Math.max(0, Math.min(b.getCursorPosition(), v.length()));
            int start = 0;
            while (start < cur && pz.largeur(v.substring(start, cur), 13, false) > avail) start++;
            String vis = v.substring(start);
            while (!vis.isEmpty() && pz.largeur(vis, 13, false) > avail) vis = vis.substring(0, vis.length() - 1);
            float vw = 0;
            if (!vis.isEmpty()) vw = pz.texte(vis, tx, cy, 13, CREME, false);
            if (suffixe != null && !v.isEmpty()) pz.texte(suffixe, tx + vw + 2, cy, 13, CREME, false);
            if (focus && (System.currentTimeMillis() / 500) % 2 == 0) {
                float cx = tx + pz.largeur(v.substring(start, cur), 13, false);
                pz.px(cx, cy - 4.5f, cx + 1f / FactionArt.echelle(), cy + 4.5f, OR);
            }
        }
        if (visible) hits.add(new Hit(x, y, w, h, () -> { setFocused(b); b.setFocused(true); }, null));
    }

    /** Case à cocher 7 GUI (maquettes : fond nuit, filet or, coche d'or) ; renvoie la largeur prise. */
    void caseCocher(float x, float y, boolean coche, boolean eteinte) {
        int k = FactionArt.echelle();
        pz.fond(Math.round(x), Math.round(y), Math.round(x) + 7, Math.round(y) + 7, 0xFF0A0608);
        pz.cadre(x, y, 7, 7, eteinte ? 0xFF4A3C3E : OR_DOUX);
        if (!coche) return;
        int col = eteinte ? GRIS : OR_CLAIR;
        // coche au trait : deux segments, posés au pixel écran
        float s = 1f / k, ep = Math.max(1, Math.round(k * 0.9f)) * s;
        float[][] pts = {{1.6f, 3.6f}, {3f, 5f}, {5.6f, 1.6f}};
        for (int i = 0; i < 2; i++) {
            float ax = pts[i][0], ay = pts[i][1], bx = pts[i + 1][0], by = pts[i + 1][1];
            int n = Math.max(2, Math.round(Math.max(Math.abs(bx - ax), Math.abs(by - ay)) * k));
            for (int j = 0; j <= n; j++) {
                float t = j / (float) n, px = x + ax + (bx - ax) * t, py = y + ay + (by - ay) * t;
                pz.px(px - ep / 2, py - ep / 2, px + ep / 2 + s, py + ep / 2 + s, col);
            }
        }
    }

    /** Insigne de section dans un petit rond nuit cerclé d'or (version volet de la pastille v2). */
    void rondInsigne(float cx, float cy, float r, String insigne, boolean actif) {
        pz.pastille(cx, cy, r, actif ? NUIT_3 : NUIT_2);
        anneau(cx, cy, r, actif ? OR : OR_DOUX);
        int s = Math.round(r * 1.25f);
        pz.insigne(insigne, cx - s / 2f, cy - s / 2f, s, actif ? OR_CLAIR : OR);
    }

    /** Anneau d'1 px écran (rayon r GUI), posé point par point. */
    void anneau(float cx, float cy, float r, int col) {
        int k = FactionArt.echelle();
        int R = Math.round(r * k);
        int CX = Math.round(cx * k), CY = Math.round(cy * k);
        float s = 1f / k;
        int n = Math.max(24, (int) (R * 6.3f));
        int px = Integer.MIN_VALUE, py = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            int X = CX + (int) Math.round(Math.cos(a) * (R - 0.5)), Y = CY + (int) Math.round(Math.sin(a) * (R - 0.5));
            if (X == px && Y == py) continue;
            px = X;
            py = Y;
            pz.px(X * s, Y * s, (X + 1) * s, (Y + 1) * s, col);
        }
    }

    /** « ? » d'une section cachée : rond nuit cerclé de gris. */
    void rondQ(float cx, float cy, float r) {
        pz.pastille(cx, cy, r, NUIT_2);
        anneau(cx, cy, r, GRIS_3);
        pz.texteC("?", cx, cy + 0.5f, Math.round(r * 2.2f), GRIS, true);
    }

    /** Tête d'une personne vue par le lecteur : visage RP si nommée, capuche sombre sinon. */
    void tete(JsonObject p, float x, float y, int taille) {
        if (!bool(p, "connu")) { pz.inconnu(x, y, taille); return; }
        String u = str(p, "u");
        pz.visage(u.isEmpty() ? null : uuid(u), x, y, taille, !bool(p, "online"));
    }

    static java.util.UUID uuid(String s) {
        try { return java.util.UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    void barre(int y0, int y1, int total) {
        int vue = y1 - y0;
        if (total <= vue + 2 || vue < 20) return;
        int x = px1() - 4;
        int th = Math.max(14, vue * vue / total);
        int ty = y0 + Math.round((vue - th) * (scroll / (float) Math.max(1, total - vue)));
        pz.fond(x, y0, x + 1, y1, 0xFF241A1E);
        pz.fond(x, ty, x + 1, ty + th, FILET_2);
    }

    int vide(int y, String phrase, String aide) {
        float cx = ix() + iw() / 2f;
        for (String l : wrap(phrase, iw(), 14)) { pz.texteC(l, cx, y + 18, 14, GRIS, false); y += 9; }
        if (aide != null) {
            List<String> l = wrap(aide, iw() - 10, 12);
            for (int i = 0; i < l.size(); i++) pz.texteC(l.get(i), cx, y + 21 + i * 8, 12, GRIS_2, false);
            return y + 27 + l.size() * 8;
        }
        return y + 25;
    }

    float txt(String s, float x, float cy, float maxW, int taille, int col, boolean gras) {
        if (s == null || s.isEmpty()) return 0;
        String f = fit(s, maxW, taille, gras);
        float w = pz.texte(f, x, cy, taille, col, gras);
        if (!f.equals(s)) tip(Math.round(x), Math.round(cy - 5), Math.round(maxW), 10, s);
        return w;
    }

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
        if (menu != null && dansMenu(mouseX, mouseY)) {
            return x >= menu.x() && x < menu.x() + menu.w() && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    boolean cliquable(int y, int h) {
        return !dansCorps || y + h > vy0() && y < vy1();
    }

    /* =================================================================== entrées */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        double x = e.x(), y = e.y();
        if (e.button() == 0) {
            boolean dm = dansMenu(x, y);
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (!h.in(x, y)) continue;
                if (menu != null && !dm) { menu = null; return true; }
                if (h.action() != null) h.action().run();
                else if (h.refus() != null) { FactionClient.toast(h.refus(), true); }
                if (getFocused() instanceof EditBox b && !b.isMouseOver(x, y)) b.setFocused(false);
                return true;
            }
            if (menu != null) { menu = null; return true; }
            if (getFocused() instanceof EditBox b) { b.setFocused(false); setFocused(null); }
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

    boolean saisie() { return getFocused() instanceof EditBox b && b.isFocused() && b.isVisible(); }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null) { menu = null; return true; }
            if (saisie()) { EditBox b = (EditBox) getFocused(); b.setFocused(false); setFocused(null); return true; }
            if (sous != null) { retour(); return true; }
            onClose();
            return true;
        }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            if (pages.entree()) return true;
        }
        if (!saisie() && HudKeybinds.FACTION != null && HudKeybinds.FACTION.matches(e)) { onClose(); return true; }
        return super.keyPressed(e);
    }

    /* =================================================================== JSON */

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

    /** « 4 250 » (espace fine des milliers). */
    static String ryo(long n) {
        String s = String.format(java.util.Locale.ROOT, "%,d", Math.abs(n)).replace(',', ' ');
        return (n < 0 ? "−" : "") + s;
    }

    /** « il y a 2 h », « il y a 5 min », « 14/10 ». */
    static String depuis(long le) {
        if (le <= 0) return "";
        long d = (System.currentTimeMillis() - le) / 1000;
        if (d < 60) return "à l'instant";
        if (d < 3600) return "il y a " + d / 60 + " min";
        if (d < 86400) return "il y a " + d / 3600 + " h";
        return date(le);
    }

    static String date(long le) {
        return new java.text.SimpleDateFormat("dd/MM").format(new java.util.Date(le));
    }
}
