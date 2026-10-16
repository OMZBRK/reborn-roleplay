package fr.reborn.hud.communication;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.monnaie.BourseClient;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * La volière (maquettes v3c 03 et 04, CLOCHE_ET_MISSIVES.md §3.2 à §3.4) : écran <b>centré</b> du kit, signature : <b>le
 * corbeau perché</b>, deux mots en tête — « Écrire » et « Missives ici · N ».
 * <ul>
 *   <li><b>Écrire</b> : la lettre encastrée (À : puces retirables, « + Ajouter » parmi les personnes connues ou une
 *       adresse écrite à la main ; corps 600 caractères, gras / italique ; signature et cachet de cire en pied), la
 *       volière d'arrivée (délai, coût), le sceau (signée / anonyme), le coût total et l'heure d'arrivée, Envoyer par
 *       corbeau · Entrée. Le brouillon est gardé si l'on ferme. Avec une missive en main, « Transférer » la renvoie.</li>
 *   <li><b>Missives ici</b> : les missives présentes (les siennes en tête, en or, « pour toi » ; une ligne par copie),
 *       Lire / Prendre au survol, filtres Toutes · Pour toi · Scellées.</li>
 * </ul>
 */
public final class VoliereScreen extends Screen {

    private record Zone(float x, float y, float w, float h, Runnable act) {
        boolean dans(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record Contact(String ref, String nom, String sous, UUID mc) { }

    private JsonObject o;
    private final CommArt.Plume pz = new CommArt.Plume();
    private final List<Zone> zones = new ArrayList<>();
    private final List<Contact> contacts = new ArrayList<>();
    int onglet;
    private int filtre, defil, defilDest;
    private boolean transfert;
    private long ouvert = System.currentTimeMillis(), recu = System.currentTimeMillis();
    private int x0, y0, w, h;
    private boolean focusTexte = true;
    // « + Ajouter »
    private boolean ajout, ajoutAdresse;
    private int ajoutSel;
    private EditBox ajoutBox;
    private float ajX, ajY;
    /** Banc d'essai : position de souris imposée. */
    int[] debugSouris;

    VoliereScreen(JsonObject o) {
        super(Component.literal("Volière"));
        maj(o);
    }

    String id() { return CommClient.str(o, "id"); }

    void maj(JsonObject n) {
        boolean premier = this.o == null;
        this.o = n;
        recu = System.currentTimeMillis();
        if (premier || n.has("ouvrir")) onglet = (int) CommClient.num(n, "onglet");
        CommClient.lettre.max = Math.max(40, (int) CommClient.num(n, "max"));
        contacts.clear();
        for (JsonElement e : CommClient.arr(n, "contacts")) {
            JsonObject c = e.getAsJsonObject();
            UUID mc = null;
            try { if (c.has("mc")) mc = UUID.fromString(CommClient.str(c, "mc")); } catch (IllegalArgumentException ignored) { }
            contacts.add(new Contact(CommClient.str(c, "ref"), CommClient.str(c, "nom"), CommClient.str(c, "sous"), mc));
        }
        contacts.sort((a, b) -> a.nom().compareToIgnoreCase(b.nom()));
        // volière d'arrivée : celle du brouillon si elle est joignable, sinon « ici »
        JsonArray ds = CommClient.arr(n, "destinations");
        boolean ok = false;
        for (JsonElement e : ds) if (CommClient.str(e.getAsJsonObject(), "id").equals(CommClient.vers)) ok = true;
        if (!ok && !ds.isEmpty()) CommClient.vers = CommClient.str(ds.get(0).getAsJsonObject(), "id");
        if (transfert && !n.has("enMain")) transfert = false;
        if (w > 0) layout();
    }

    void envoye(boolean tr) { if (tr) transfert = false; }

    @Override
    public boolean isPauseScreen() { return false; }

    private void layout() {
        w = onglet == 0 ? 440 : 410;
        h = onglet == 0 ? 356 : 352;
        x0 = (this.width - w) / 2;
        y0 = Math.max(2, Math.min((this.height - h) / 2 - (onglet == 0 ? 12 : 7), this.height - h - 24));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        EditBox ancien = ajoutBox;
        ajoutBox = new EditBox(this.font, 0, 0, 120, 10, Component.literal(""));
        ajoutBox.setBordered(false);
        ajoutBox.setMaxLength(40);
        ajoutBox.setTextColor(CommArt.CREME);
        if (ancien != null) ajoutBox.setValue(ancien.getValue());
        ajoutBox.setResponder(v -> ajoutSel = 0);
        ajoutBox.visible = ajout;
        addRenderableWidget(ajoutBox);
        layout();
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        super.removed();
    }

    /* ================================================================ rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        float a = Math.min(1f, (System.currentTimeMillis() - ouvert) / 180f);
        ctx.fill(0, 0, this.width, this.height, DaKit.argb(0x0A0608, 0.58f * a));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (debugSouris != null) { mx = debugSouris[0]; my = debugSouris[1]; }
        int amx = mx, amy = my;
        if (ajout) { mx = -1000; my = -1000; }   // le menu ouvert prend la souris
        zones.clear();
        pz.police(this.font);
        CommArt.panneau(ctx, x0, y0, w, h);
        float cx = x0 + w / 2f;
        CommArt.sprite(ctx, CommArt.MOTIF_CORBEAU, 29, 16, cx - 29, y0 + 12, 58, -1);
        pz.texteC(CommClient.str(o, "nom"), cx, y0 + 55, 24, CommArt.CREME, true);
        int n = CommClient.arr(o, "missives").size();
        String sous = onglet == 0 ? CommClient.str(o, "type") + " · tu écris en " + CommClient.str(o, "moi")
                : CommClient.str(o, "type") + " · " + n + (n > 1 ? " missives" : " missive") + " · gardées " + CommClient.num(o, "garde") + " jours";
        pz.texteC(sous, cx, y0 + 69, 14, CommArt.CREME_DOUX, false);
        onglets(cx, y0 + 86, n, mx, my);
        pz.h(x0 + 20, x0 + w - 23, y0 + 99, CommArt.FILET);
        List<Runnable> apres = new ArrayList<>();
        if (onglet == 0) ecrire(ctx, mx, my, apres);
        else missives(ctx, mx, my, apres);
        String t = CommClient.toastActif();
        if (t != null) pz.texteC(t, cx, Math.min(y0 + h + 16, this.height - 6), 13, CommClient.toastRefus ? CommArt.ROUGE : CommArt.AMBRE, false);
        else pz.texteC(onglet == 0 ? "Échap  fermer   ·   le brouillon est gardé si tu fermes" : "Échap  fermer", cx, Math.min(y0 + h + 16, this.height - 6), 13, CommArt.CREME_DOUX, false);
        pz.vider(ctx);
        for (Runnable r : apres) r.run();
        if (ajout) menuAjouter(ctx, amx, amy, delta);
    }

    private void onglets(float cx, float ym, int n, int mx, int my) {
        String[] l = {"Écrire", "Missives ici · " + n};
        float gap = 15, tot = -gap;
        for (int i = 0; i < 2; i++) tot += pz.largeur(l[i], 16, i == onglet) + gap;
        float x = cx - tot / 2f;
        for (int i = 0; i < 2; i++) {
            boolean act = i == onglet;
            float lw = pz.largeur(l[i], 16, act);
            boolean hov = DaKit.dans(mx, my, Math.round(x - 2), Math.round(ym - 7), Math.round(lw + 4), 14);
            pz.texte(l[i], x, ym, 16, act || hov ? CommArt.CREME : CommArt.CREME_DOUX, act);
            if (act) pz.px(x, ym + 6.5f, x + lw, ym + 7f, CommArt.OR);
            final int k = i;
            zones.add(new Zone(x - 2, ym - 7, lw + 4, 14, () -> changerOnglet(k)));
            x += lw + gap;
        }
    }

    void changerOnglet(int k) {
        if (k == onglet) return;
        onglet = k;
        ajout = false;
        ajoutBox.visible = false;
        defil = 0;
        layout();
        RebornSounds.uiClick();
    }

    /* ================================================================ Écrire */

    private JsonObject destination() {
        for (JsonElement e : CommClient.arr(o, "destinations")) if (CommClient.str(e.getAsJsonObject(), "id").equals(CommClient.vers)) return e.getAsJsonObject();
        return null;
    }

    private void ecrire(GuiGraphicsExtractor ctx, int mx, int my, List<Runnable> apres) {
        float L = x0 + 22, R = x0 + 418;
        float zy = y0 + 110, zw = 250, zh = 181;
        lettre(ctx, L, zy, zw, zh, mx, my, apres);
        // volière d'arrivée
        float rx = x0 + 289, yy = y0 + 114;
        pz.rubrique("Volière d'arrivée", rx, yy, CommArt.CREME_DOUX);
        yy += 15;
        JsonArray ds = CommClient.arr(o, "destinations");
        int visibles = 4;
        defilDest = Math.max(0, Math.min(defilDest, ds.size() - visibles));
        int nd = Math.max(1, CommClient.dests.size());
        for (int i = 0; i < visibles && defilDest + i < ds.size(); i++) {
            JsonObject d = ds.get(defilDest + i).getAsJsonObject();
            String id = CommClient.str(d, "id");
            boolean on = id.equals(CommClient.vers);
            boolean refus = d.has("refus");
            boolean hov = !refus && DaKit.dans(mx, my, Math.round(rx - 5), Math.round(yy - 8), Math.round(R - rx + 8), 23);
            if (on) { pz.fond(rx - 5, yy - 8, R + 3, yy + 15, 0xFF28161C); pz.px(rx - 5, yy - 8, rx - 4.5f, yy + 15, CommArt.OR); }
            else if (hov) pz.fond(rx - 5, yy - 8, R + 3, yy + 15, 0xFF1C1014);
            CommArt.radio(pz, rx + 3.5f, yy, on);
            pz.texte(pz.ellipse(CommClient.str(d, "nom"), R - rx - 14, 14, on), rx + 12, yy, 14,
                    refus ? CommArt.GRIS_FONCE : on ? CommArt.CREME : CommArt.CREME_DOUX, on);
            long cout = CommClient.num(d, "cout");
            String info = duree(CommClient.num(d, "delai")) + " · " + cout + " ryo" + (on && nd > 1 ? " chacun" : "")
                    + (d.has("cond") ? " · " + CommClient.str(d, "cond") : "");
            pz.texte(refus ? CommClient.str(d, "refus") : info, rx + 12, yy + 9.5f, 12, refus ? CommArt.GRIS_FONCE : on ? CommArt.OR_CLAIR : CommArt.GRIS, false);
            if (!refus) zones.add(new Zone(rx - 5, yy - 8, R - rx + 8, 23, () -> { CommClient.vers = id; RebornSounds.uiClick(); }));
            yy += 26;
        }
        if (ds.isEmpty()) { pz.texte("Aucune volière joignable.", rx, yy, 13, CommArt.GRIS, false); yy += 26; }
        if (ds.size() > visibles) {
            float top = y0 + 121, hh = 26 * visibles;
            pz.px(R + 5, top, R + 5.5f, top + hh, 0xFF2A1C20);
            float th = hh * visibles / ds.size(), ty = top + hh * defilDest / ds.size();
            pz.px(R + 4.5f, ty, R + 6, ty + th, CommArt.OR_SOMBRE);
        }
        yy = Math.max(yy, y0 + 233) + 2;
        pz.rubrique("Sceau", rx, yy, CommArt.CREME_DOUX);
        yy += 13;
        if (transfert) {
            pz.texte("Celui de la missive", rx, yy, 15, CommArt.CREME, true);
            pz.texte("Un transfert garde son anonymat", rx, yy + 12, 12, CommArt.GRIS, false);
            pz.texte("et l'état de son cachet.", rx, yy + 21, 12, CommArt.GRIS, false);
        } else {
            String[] m = {"Signée", "Anonyme"};
            float x = rx;
            for (int i = 0; i < 2; i++) {
                boolean act = (i == 0) == CommClient.signee;
                float lw = pz.largeur(m[i], 15, act);
                boolean hov = DaKit.dans(mx, my, Math.round(x - 2), Math.round(yy - 7), Math.round(lw + 4), 14);
                pz.texte(m[i], x, yy, 15, act || hov ? CommArt.CREME : CommArt.CREME_DOUX, act);
                if (act) pz.px(x, yy + 6.5f, x + lw, yy + 7f, CommArt.OR);
                final boolean s = i == 0;
                zones.add(new Zone(x - 2, yy - 7, lw + 4, 14, () -> { CommClient.signee = s; RebornSounds.uiClick(); }));
                x += lw + 11;
            }
            pz.texte("Toujours fermée à la cire : on", rx, yy + 12, 12, CommArt.GRIS, false);
            pz.texte("verra si quelqu'un l'a ouverte.", rx, yy + 21, 12, CommArt.GRIS, false);
        }
        // pied : coût, arrivée, Envoyer
        float yf = zy + zh + 13;
        pz.h(x0 + 20, x0 + w - 23, yf, CommArt.FILET);
        float py = yf + 18;
        JsonObject d = destination();
        long cout = d == null ? 0 : CommClient.num(d, "cout");
        int copies = transfert ? 1 : CommClient.dests.size();
        String l1;
        if (d == null) l1 = "Choisis la volière d'arrivée";
        else {
            String total = copies <= 1 ? cout + " ryo" : (cout * copies) + " ryo (" + copies + " × " + cout + ")";
            LocalTime t = LocalTime.now().plusSeconds(CommClient.num(d, "delai"));
            l1 = (copies == 0 ? cout + " ryo par copie" : total) + " · arrivée vers " + t.getHour() + " h "
                    + String.format(Locale.ROOT, "%02d", t.getMinute()) + ", " + minuscule(CommClient.str(d, "lieu"));
        }
        pz.texte("Bourse : " + BourseClient.fmt(CommClient.num(o, "bourse")) + " ryo · " + (copies > 1 ? "chacun sera prévenu à l'arrivée" : "prévenu à l'arrivée"),
                L + 16, py + 6, 12, CommArt.GRIS, false);
        apres.add(() -> CommArt.sprite(ctx, CommArt.PIECE, 12, 12, L, py - 10.5f, 12, -1));
        String lab = transfert ? "Transférer par corbeau · Entrée" : "Envoyer par corbeau · Entrée";
        float bw = pz.largeur(lab, 15, true) + 22, bc = R - 125, bx = bc - bw / 2f, by = py - 9;
        pz.texte(pz.ellipse(l1, bx - (L + 16) - 8, 14, false), L + 16, py - 4.5f, 14, CommArt.CREME, false);
        boolean hov = DaKit.dans(mx, my, Math.round(bx), Math.round(by), Math.round(bw), 16);
        pz.fond(bx, by, bx + bw, by + 16, hov ? 0xFF5A1622 : 0xFF3C0E16);
        pz.cadre(bx, by, bw, 16, CommArt.OR);
        pz.px(bx + 0.5f, by + 0.5f, bx + bw - 0.5f, by + 1, 0xFF962834);
        pz.texteC(lab, bc, by + 8, 15, CommArt.CREME, true);
        zones.add(new Zone(bx, by, bw, 16, this::envoyer));
        float ax = bc + bw / 2f + 9, aw = pz.largeur("Annuler", 15, false);
        boolean ha = DaKit.dans(mx, my, Math.round(ax - 2), Math.round(by), Math.round(aw + 4), 16);
        pz.texte("Annuler", ax, by + 8, 15, ha ? CommArt.CREME : CommArt.CREME_DOUX, false);
        zones.add(new Zone(ax - 2, by, aw + 4, 16, () -> { if (transfert) { transfert = false; RebornSounds.uiClick(); } else onClose(); }));
    }

    /** La lettre encastrée : À (puces), nombre de copies et G / I, corps, signature et cachet. */
    private void lettre(GuiGraphicsExtractor ctx, float x, float y, float zw, float zh, int mx, int my, List<Runnable> apres) {
        Editeur ed = CommClient.lettre;
        CommArt.zone(pz, x, y, zw, zh, focusTexte && !transfert);
        pz.texte("À", x + 11, y + 14, 14, CommArt.GRIS, false);
        float xx = x + 22;
        List<CommClient.Dest> ds = CommClient.dests;
        int maxDest = transfert ? 1 : (int) Math.max(1, CommClient.num(o, "destMax"));
        for (int i = 0; i < ds.size(); i++) {
            CommClient.Dest d = ds.get(i);
            String nom = pz.ellipse(d.nom(), 90, 14, false);
            float pw = pz.largeur(nom, 14, false) + 20;
            if (xx + pw > x + zw - 50) { pz.texte("+" + (ds.size() - i), xx + 2, y + 14, 14, CommArt.CREME_DOUX, true); xx += 16; break; }
            pz.fond(xx, y + 7.5f, xx + pw, y + 20.5f, 0xFF26161C);
            pz.cadre(xx, y + 7.5f, pw, 13, 0xFF5A4030);
            pz.texte(nom, xx + 5.5f, y + 14, 14, d.adresse() ? CommArt.CREME_DOUX : CommArt.CREME, false);
            float c = xx + pw - 7;
            boolean hx = DaKit.dans(mx, my, Math.round(c - 4), Math.round(y + 9), 8, 10);
            pz.texteC("×", c, y + 13.5f, 15, hx ? CommArt.CREME : CommArt.GRIS, false);
            final int k = i;
            zones.add(new Zone(c - 4, y + 9, 8, 10, () -> { CommClient.dests.remove(k); RebornSounds.uiClick(); }));
            xx += pw + 4;
        }
        if (ds.size() < maxDest) {
            float aw = pz.largeur("+ Ajouter", 14, true);
            boolean ha = DaKit.dans(mx, my, Math.round(xx), Math.round(y + 7), Math.round(aw + 4), 14);
            pz.texte("+ Ajouter", xx + 2, y + 14, 14, ha ? CommArt.OR_CLAIR : CommArt.OR, true);
            final float ax = xx;
            zones.add(new Zone(xx, y + 7, aw + 4, 14, () -> ouvrirAjout(ax - 4, y + 20)));
        }
        pz.h(x + 9, x + zw - 9, y + 27, CommArt.FILET_ZONE);
        int n = ds.size();
        pz.texte(transfert ? "transfert · un seul destinataire" : n == 0 ? "aucun destinataire" : n == 1 ? "1 destinataire"
                : n + " destinataires · une copie chacun", x + 12, y + 39, 12, CommArt.GRIS_FONCE, false);
        pz.h(x + 9, x + zw - 9, y + 49, CommArt.FILET_ZONE);
        float pied = y + zh - 29, yb = y + zh - 14.5f;
        if (transfert) {
            JsonObject m = o.getAsJsonObject("enMain");
            boolean sc = CommClient.bool(m, "scellee");
            pz.texte("La missive que tu tiens part telle quelle :", x + 12, y + 62, 14, CommArt.CREME_DOUX, false);
            pz.texte("« Pour " + CommClient.str(m, "pour") + " », " + (sc ? "toujours scellée." : "cachet brisé."), x + 12, y + 78, 14, CommArt.CREME, false);
            pz.texte("Son texte et sa signature ne changent pas ;", x + 12, y + 98, 12, CommArt.GRIS, false);
            pz.texte("seule l'adresse est réécrite.", x + 12, y + 108, 12, CommArt.GRIS, false);
            apres.add(() -> CommArt.sprite(ctx, sc ? CommArt.MISSIVE : CommArt.MISSIVE_OUVERTE, 20, 20, x + zw - 36, y + 64, 20, -1));
        } else {
            formatGI(ed, x + zw - 5, y + 39, mx, my);
            for (int i = 0; i < 6; i++) {
                float ry = y + 66 + i * 16;
                if (ry < pied - 4) pz.h(x + 11, x + zw - 11, ry, DaKit.argb(0xD9A95E, 0.07f));
            }
            float edX = x + 12, edY = y + 59;
            ed.mettreEnPage(this.font, edX, edY, zw - 24, 17, 16);
            if (ed.longueur() == 0 && !focusTexte) pz.texte("Écris ta missive…", edX, edY, 17, CommArt.GRIS_FONCE, false);
            ed.peindre(pz, 17, CommArt.CREME, focusTexte && !ajout);
            zones.add(new Zone(x, y + 50, zw, zh - 80, null));
            pz.texte(ed.longueur() + " / " + ed.max, x + 12, yb, 12, CommArt.GRIS_FONCE, false);
        }
        pz.h(x + 9, x + zw - 9, pied, CommArt.FILET_ZONE);
        float cw = 22.5f;
        String sg = transfert ? "— signature d'origine" : CommClient.signee ? "— " + CommClient.str(o, "moi") : "— sans signature";
        pz.italique(sg, x + zw - 17 - cw - pz.largeurItalique(sg, 16), yb, 16, CommArt.CREME_DOUX);
        apres.add(() -> CommArt.sprite(ctx, CommArt.CACHET, 15, 15, x + zw - 10 - cw, yb - cw / 2f, cw, -1));
    }

    private void formatGI(Editeur ed, float xr, float cy, int mx, int my) {
        for (int i = 0; i < 2; i++) {
            boolean ital = i == 0;
            float x = xr - 12 - i * 14;
            boolean on = ed.actif(ital ? Editeur.I : Editeur.G);
            boolean hov = DaKit.dans(mx, my, Math.round(x - 2), Math.round(cy - 6), 12, 12);
            if (on) { pz.fond(x - 1.5f, cy - 5.5f, x + 9, cy + 5, 0xFF2A181E); pz.cadre(x - 1.5f, cy - 5.5f, 10.5f, 10.5f, CommArt.OR_SOMBRE); }
            int col = on || hov ? CommArt.CREME : CommArt.GRIS_FONCE;
            if (ital) pz.italique("I", x + 2, cy, 14, col);
            else pz.texte("G", x + 2, cy, 14, col, true);
            final int bit = ital ? Editeur.I : Editeur.G;
            zones.add(new Zone(x - 2, cy - 6, 12, 12, () -> { ed.basculer(bit); focusTexte = true; RebornSounds.uiClick(); }));
        }
    }

    /* ---------------------------------------------------------------- + Ajouter */

    private void ouvrirAjout(float x, float y) {
        ajout = true;
        ajoutAdresse = false;
        ajoutSel = 0;
        ajX = x;
        ajY = y;
        ajoutBox.setValue("");
        ajoutBox.visible = true;
        ajoutBox.setFocused(true);
        setFocused(ajoutBox);
        focusTexte = false;
        RebornSounds.uiClick();
    }

    private void fermerAjout() {
        ajout = false;
        ajoutBox.visible = false;
        ajoutBox.setFocused(false);
        setFocused(null);
        focusTexte = true;
    }

    private List<Contact> trouves() {
        String q = sansAccents(ajoutBox.getValue().trim());
        List<Contact> l = new ArrayList<>();
        for (Contact c : contacts) {
            boolean deja = CommClient.dests.stream().anyMatch(d -> c.ref().equals(d.ref()));
            if (deja) continue;
            if (q.isEmpty() || sansAccents(c.nom()).contains(q)) l.add(c);
        }
        return l;
    }

    private static String sansAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private final List<Zone> zonesAjout = new ArrayList<>();

    /** Le menu « + Ajouter » : recherche parmi ceux qu'on connaît, ou une adresse écrite à la main (lisible de tous). */
    private void menuAjouter(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        zonesAjout.clear();
        float x = Math.min(ajX, x0 + w - 150), y = ajY, mw = 140;
        List<Contact> l = ajoutAdresse ? List.of() : trouves();
        int rows = Math.min(3, l.size());
        float mh = ajoutAdresse ? 46 : 27 + Math.max(1, rows) * 19 + 30;
        pz.fond(x + 1.5f, y + 1.5f, x + mw + 1.5f, y + mh + 1.5f, 0x78000000);
        pz.fond(x, y, x + mw, y + mh, 0xFA0E080B);
        pz.cadre(x, y, mw, mh, CommArt.OR_SOMBRE);
        pz.fond(x + 5, y + 5, x + mw - 5, y + 19, CommArt.NUIT_ZONE);
        pz.cadre(x + 5, y + 5, mw - 10, 14, CommArt.OR_FONCE);
        ajoutBox.setX(Math.round(x + 10));
        ajoutBox.setY(Math.round(y + 8.5f));
        ajoutBox.setWidth(Math.round(mw - 20));
        ajoutBox.setMaxLength(ajoutAdresse ? (int) Math.max(10, CommClient.num(o, "adresseMax")) : 32);
        if (ajoutBox.getValue().isEmpty())
            pz.texte(ajoutAdresse ? "« Au chef de la Police »" : "Chercher parmi ceux que tu connais", x + 10, y + 12, 12, CommArt.GRIS_FONCE, false);
        List<Runnable> faces = new ArrayList<>();
        if (ajoutAdresse) {
            pz.texte("Adresse écrite à la main · Entrée", x + 7, y + 28, 12, CommArt.OR, false);
            pz.texte("lisible de tous, ne prévient personne", x + 7, y + 38, 11, CommArt.GRIS, false);
        } else {
            for (int i = 0; i < rows; i++) {
                Contact c = l.get(i);
                float yy = y + 30 + i * 19;
                boolean hov = DaKit.dans(mx, my, Math.round(x + 2), Math.round(yy - 8), Math.round(mw - 4), 17);
                if (hov) ajoutSel = i;
                if (i == ajoutSel) pz.fond(x + 2, yy - 8, x + mw - 2, yy + 9, 0xFF28161C);
                pz.texte(pz.ellipse(c.nom(), mw - 30, 14, i == ajoutSel), x + 23, yy - 2, 14, CommArt.CREME, i == ajoutSel);
                pz.texte(c.sous(), x + 23, yy + 6, 11, CommArt.GRIS, false);
                faces.add(() -> CommArt.visage(ctx, c.mc(), x + 7, yy - 5.5f, 11));
                zonesAjout.add(new Zone(x + 2, yy - 8, mw - 4, 17, () -> ajouterContact(c)));
            }
            if (l.isEmpty()) pz.texte(contacts.isEmpty() ? "Tu ne connais encore personne." : "Personne de ce nom.", x + 7, y + 30, 12, CommArt.GRIS, false);
            float ys = y + 27 + Math.max(1, rows) * 19 + 2;
            pz.h(x + 6, x + mw - 6, ys, CommArt.FILET_ZONE);
            boolean ha = DaKit.dans(mx, my, Math.round(x + 2), Math.round(ys + 2), Math.round(mw - 4), 24);
            pz.texte("Écrire une adresse à la main…", x + 7, ys + 10, 13, ha ? CommArt.OR_CLAIR : CommArt.OR, false);
            pz.texte("lisible de tous : « Au chef de la Police »", x + 7, ys + 20, 11, CommArt.GRIS, false);
            zonesAjout.add(new Zone(x + 2, ys + 2, mw - 4, 24, () -> { ajoutAdresse = true; ajoutBox.setValue(""); RebornSounds.uiClick(); }));
        }
        zonesAjout.add(new Zone(x, y, mw, mh, () -> { }));
        pz.vider(ctx);
        for (Runnable r : faces) r.run();
        ajoutBox.extractRenderState(ctx, mx, my, delta);
    }

    private void ajouterContact(Contact c) {
        if (CommClient.dests.stream().noneMatch(d -> c.ref().equals(d.ref()))) CommClient.dests.add(new CommClient.Dest(c.ref(), c.nom()));
        fermerAjout();
        RebornSounds.uiClick();
    }

    private void validerAjout() {
        if (ajoutAdresse) {
            String a = ajoutBox.getValue().trim();
            if (a.isEmpty()) return;
            CommClient.dests.add(new CommClient.Dest("", a));
            fermerAjout();
            RebornSounds.uiClick();
            return;
        }
        List<Contact> l = trouves();
        if (!l.isEmpty()) ajouterContact(l.get(Math.min(ajoutSel, l.size() - 1)));
    }

    /* ================================================================ Missives ici */

    private List<JsonObject> liste() {
        List<JsonObject> l = new ArrayList<>();
        for (JsonElement e : CommClient.arr(o, "missives")) {
            JsonObject m = e.getAsJsonObject();
            if (filtre == 1 && !CommClient.bool(m, "toi")) continue;
            if (filtre == 2 && !CommClient.bool(m, "scellee")) continue;
            l.add(m);
        }
        return l;
    }

    private void missives(GuiGraphicsExtractor ctx, int mx, int my, List<Runnable> apres) {
        float L = x0 + 22, R = x0 + 388;
        float y = y0 + 108;
        JsonArray all = CommClient.arr(o, "missives");
        int toi = 0, sc = 0;
        for (JsonElement e : all) { if (CommClient.bool(e.getAsJsonObject(), "toi")) toi++; if (CommClient.bool(e.getAsJsonObject(), "scellee")) sc++; }
        String[] f = {"Toutes", "Pour toi · " + toi, "Scellées · " + sc};
        float x = L;
        for (int i = 0; i < 3; i++) {
            boolean act = i == filtre;
            float lw = pz.largeur(f[i], 14, act);
            boolean hov = DaKit.dans(mx, my, Math.round(x - 2), Math.round(y - 2), Math.round(lw + 4), 12);
            pz.texte(f[i], x, y + 4, 14, act || hov ? CommArt.CREME : CommArt.CREME_DOUX, act);
            if (act) pz.px(x, y + 10, x + lw, y + 10.5f, CommArt.OR);
            final int k = i;
            zones.add(new Zone(x - 2, y - 2, lw + 4, 12, () -> { filtre = k; defil = 0; RebornSounds.uiClick(); }));
            x += lw + 11;
        }
        List<JsonObject> l = liste();
        int visibles = 5;
        defil = Math.max(0, Math.min(defil, l.size() - visibles));
        float yy = y + 16;
        long now = CommClient.num(o, "now") + (System.currentTimeMillis() - recu);
        boolean prendre = CommClient.bool(o, "peutPrendre");
        int survol = -1;
        for (int i = 0; i < visibles && defil + i < l.size(); i++)
            if (DaKit.dans(mx, my, Math.round(L - 5), Math.round(yy + i * 32), Math.round(R - L + 8), 30)) survol = i;
        for (int i = 0; i < visibles && defil + i < l.size(); i++) {
            JsonObject m = l.get(defil + i);
            boolean hov = i == survol;
            boolean pourToi = CommClient.bool(m, "toi"), scellee = CommClient.bool(m, "scellee");
            if (hov) { pz.fond(L - 5, yy, R + 3, yy + 30, 0xFF28161C); pz.px(L - 5, yy, L - 4.5f, yy + 30, CommArt.OR); }
            final float sy = yy;
            apres.add(() -> CommArt.sprite(ctx, scellee ? CommArt.MISSIVE : CommArt.MISSIVE_OUVERTE, 20, 20, L, sy + 5, 20, -1));
            float px = L + 27;
            px += pz.texte("Pour  ", px, yy + 10, 13, CommArt.GRIS, false);
            px += pz.texte(pz.ellipse(CommClient.str(m, "pour"), 130, 16, true), px, yy + 10, 16, pourToi ? CommArt.OR_CLAIR : CommArt.CREME, true);
            JsonArray autres = CommClient.arr(m, "autres");
            if (!autres.isEmpty()) px += 3 + pz.texte("et " + autres.size() + (autres.size() > 1 ? " autres" : " autre"), px + 3, yy + 10, 13, CommArt.CREME_DOUX, false);
            if (pourToi) {
                float bw = pz.largeur("pour toi", 11, true) + 8;
                pz.fond(px + 5, yy + 5.5f, px + 5 + bw, yy + 14.5f, 0xFF281A12);
                pz.cadre(px + 5, yy + 5.5f, bw, 9, CommArt.OR_SOMBRE);
                pz.texteC("pour toi", px + 5 + bw / 2f, yy + 10, 11, CommArt.OR_CLAIR, true);
            }
            List<String> parts = new ArrayList<>();
            boolean signe = m.has("signe");
            parts.add(signe ? "signée " + CommClient.str(m, "signe") : "sans signature");
            if (!autres.isEmpty()) {
                List<String> p = new ArrayList<>();
                for (JsonElement a : autres) p.add(a.getAsString().split(" ")[0]);
                parts.add("aussi pour " + String.join(" et ", p));
            }
            parts.add("de la " + CommClient.str(m, "de"));
            pz.texte(pz.ellipse(String.join(" · ", parts), R - px + 20 - 40, 13, false), L + 27, yy + 21, 13, signe ? CommArt.CREME_DOUX : CommArt.GRIS, false);
            String id = CommClient.str(m, "id");
            if (hov && prendre) {
                float bw = 52, bc = R - 26, bx = bc - bw / 2f, by = yy + 6.5f;
                boolean hb = DaKit.dans(mx, my, Math.round(bx), Math.round(by), Math.round(bw), 16);
                pz.fond(bx, by, bx + bw, by + 16, hb ? 0xFF5A1622 : 0xFF3C0E16);
                pz.cadre(bx, by, bw, 16, CommArt.OR);
                pz.px(bx + 0.5f, by + 0.5f, bx + bw - 0.5f, by + 1, 0xFF962834);
                pz.texteC("Prendre", bc, by + 8, 14, CommArt.CREME, true);
                zones.add(new Zone(bx, by, bw, 16, () -> prendre(id, false)));
                float lw2 = pz.largeur("Lire", 14, false);
                float lx = bx - 9 - lw2;
                boolean hl = DaKit.dans(mx, my, Math.round(lx - 3), Math.round(by), Math.round(lw2 + 6), 16);
                pz.texte("Lire", lx, by + 8, 14, hl ? CommArt.OR_CLAIR : CommArt.CREME, false);
                zones.add(new Zone(lx - 3, by, lw2 + 6, 16, () -> prendre(id, true)));
            } else {
                pz.texteD(depuis(now - CommClient.num(m, "le")), R, yy + 10, 13, CommArt.GRIS, false);
                pz.texteD(scellee ? "scellée" : "cachet brisé", R, yy + 21, 13, scellee ? CommArt.CREME_DOUX : CommArt.AMBRE, false);
                if (hov && !prendre) zones.add(new Zone(L - 5, yy, R - L + 8, 30, () -> CommClient.toast("Prendre à une volière : à partir de " + CommClient.str(o, "seuilPrendre") + ".", true)));
            }
            yy += 32;
            if (defil + i < l.size() - 1 && i < visibles - 1 && i != survol && i + 1 != survol) pz.h(L + 27, R, yy - 1.5f, 0xFF2A1C20);
        }
        if (l.isEmpty()) {
            pz.texteC(filtre == 1 ? "Aucune missive pour toi ici." : filtre == 2 ? "Aucune missive scellée." : "Aucune missive à cette volière.",
                    x0 + w / 2f, yy + 40, 14, CommArt.GRIS, false);
        }
        if (l.size() > visibles) {
            float top = y + 16, hh = 32 * visibles - 2;
            pz.px(R + 6, top, R + 6.5f, top + hh, 0xFF2A1C20);
            float th = hh * visibles / l.size(), ty = top + hh * defil / l.size();
            pz.px(R + 5.5f, ty, R + 7, ty + th, CommArt.OR_SOMBRE);
        }
        float yf = y + 16 + 32 * visibles + 6;
        pz.h(x0 + 20, x0 + w - 23, yf, CommArt.FILET);
        float cx = x0 + w / 2f - 1.5f;
        pz.texteC("Tu peux prendre n'importe quelle missive (" + CommClient.str(o, "seuilPrendre") + " et plus).", cx, yf + 15, 13, CommArt.CREME_DOUX, false);
        pz.texteC("La lire brise son cachet pour toujours ; elle reste ici.", cx, yf + 26, 13, CommArt.GRIS, false);
        String tr = "Transférer une missive ›";
        float tw = pz.largeur(tr, 14, true);
        boolean ht = DaKit.dans(mx, my, Math.round(cx - tw / 2 - 2), Math.round(yf + 35), Math.round(tw + 4), 14);
        pz.texteC(tr, cx, yf + 42, 14, ht ? CommArt.OR_CLAIR : CommArt.OR, true);
        zones.add(new Zone(cx - tw / 2 - 2, yf + 35, tw + 4, 14, this::transferer));
    }

    static String depuis(long ms) {
        long m = Math.max(0, ms) / 60_000;
        if (m < 1) return "à l'instant";
        if (m < 60) return "il y a " + m + " min";
        long hh = m / 60;
        if (hh < 24) return "il y a " + hh + " h";
        long j = hh / 24;
        return j == 1 ? "hier" : "il y a " + j + " jours";
    }

    static String duree(long s) {
        if (s < 60) return s + " s";
        long m = Math.round(s / 60.0);
        return m + " min";
    }

    static String minuscule(String s) { return s == null || s.isEmpty() ? "" : Character.toLowerCase(s.charAt(0)) + s.substring(1); }

    /* ================================================================ actions */

    private void prendre(String id, boolean lire) {
        JsonObject q = CommClient.a("prendre");
        q.addProperty("id", id);
        if (lire) q.addProperty("lire", true);
        CommClient.send(q);
        RebornSounds.playReborn("sacoche.place", 1f, 0.6f);
    }

    private void transferer() {
        if (!o.has("enMain")) { CommClient.toast("Prends en main la missive à transférer.", true); return; }
        transfert = true;
        CommClient.dests.clear();
        changerOnglet(0);
    }

    private void envoyer() {
        if (CommClient.dests.isEmpty()) { CommClient.toast("À qui ? « + Ajouter » un destinataire.", true); return; }
        if (!transfert && CommClient.lettre.vide()) { CommClient.toast("La missive est vide.", true); return; }
        JsonObject q = CommClient.a(transfert ? "transferer" : "envoyer");
        q.addProperty("voliere", id());
        q.addProperty("vers", CommClient.vers);
        JsonArray ds = new JsonArray();
        for (CommClient.Dest d : CommClient.dests) {
            JsonObject x = new JsonObject();
            if (d.adresse()) x.addProperty("adresse", d.nom()); else x.addProperty("ref", d.ref());
            ds.add(x);
        }
        q.add("dests", ds);
        if (!transfert) {
            q.add("texte", CommArt.json(CommClient.lettre.segs()));
            q.addProperty("signee", CommClient.signee);
        }
        CommClient.send(q);
        RebornSounds.confirm();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y();
        if (ajout) {
            for (Zone z : new ArrayList<>(zonesAjout)) if (z.dans(mx, my)) { z.act().run(); return true; }
            fermerAjout();
            return true;
        }
        for (Zone z : new ArrayList<>(zones)) {
            if (!z.dans(mx, my)) continue;
            if (z.act() == null) { focusTexte = true; CommClient.lettre.cliquer(mx, my, e.hasShiftDown()); }
            else z.act().run();
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hA, double v) {
        int d = -(int) Math.signum(v);
        if (onglet == 1) { defil = Math.max(0, defil + d); return true; }
        if (mx > x0 + 280) { defilDest = Math.max(0, defilDest + d); return true; }
        return super.mouseScrolled(mx, my, hA, v);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (ajout) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { fermerAjout(); return true; }
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { validerAjout(); return true; }
            if (k == GLFW.GLFW_KEY_DOWN) { ajoutSel++; return true; }
            if (k == GLFW.GLFW_KEY_UP) { ajoutSel = Math.max(0, ajoutSel - 1); return true; }
            if (ajoutBox.keyPressed(e)) return true;
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            if (onglet == 0 && focusTexte && e.hasShiftDown() && !transfert) { CommClient.lettre.inserer("\n"); return true; }
            if (onglet == 0) envoyer();
            return true;
        }
        if (onglet == 0 && !transfert && focusTexte && CommClient.lettre.touche(k, e.hasShiftDown(), e.hasControlDown())) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent ev) {
        if (ajout) return ajoutBox.charTyped(ev);
        if (onglet == 0 && !transfert && focusTexte) {
            if (ev.isAllowedChatCharacter()) CommClient.lettre.inserer(ev.codepointAsString());
            return true;
        }
        return super.charTyped(ev);
    }

    /* ================================================================ banc */

    void debugAjout(String saisie) {
        ouvrirAjout(x0 + 22 + 140, y0 + 110 + 20);
        ajoutBox.setValue(saisie);
    }

    void debugOnglet(int k) { onglet = k; layout(); }

    void debugTransfert() { transferer(); }
}
