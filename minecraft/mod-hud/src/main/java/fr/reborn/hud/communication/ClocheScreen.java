package fr.reborn.hud.communication;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La cloche du village : rédiger une annonce (maquette v3c 01, CLOCHE_ET_MISSIVES.md §2.3). Écran <b>centré</b> du kit
 * (comme le guichet), signature : <b>la cloche</b> au filet d'or. À gauche la zone d'écriture encastrée (200
 * caractères, gras / italique, signature imposée en pied) et les grades en puces ; à droite le type (normale /
 * importante / danger) et « pour qui » : tout le village ou des sections (recherche, liste qui défile, cochées en tête ;
 * jamais une section cachée). En pied : personnes dans la zone et concernées (comptées par le serveur), Sonner · Entrée.
 */
public final class ClocheScreen extends Screen {

    static final String[] TYPES = {"normale", "importante", "danger"};
    static final String[] TYPES_LIB = {"Normale", "Importante", "Danger"};
    static final String[] SONS = {"un coup de cloche", "deux coups", "le tocsin"};
    private static final int W = 440, H = 364, VISIBLES = 5;

    private record Sec(String id, String nom, String insigne, boolean nouvelle) { }

    private record Zone(float x, float y, float w, float h, Runnable act) {
        boolean dans(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private JsonObject o;
    private final CommArt.Plume pz = new CommArt.Plume();
    private final List<Zone> zones = new ArrayList<>();
    private final List<Sec> sections = new ArrayList<>();
    private final List<Sec> vue = new ArrayList<>();
    private final Set<String> cochees = new LinkedHashSet<>();
    private final List<String> grades = new ArrayList<>();
    private final Set<String> gradesOn = new LinkedHashSet<>();
    private boolean tout = true, village = true;
    private String fixe;
    private int defil, zone = -1, concernes = -1;
    private long ouvert = System.currentTimeMillis(), demandeCompte;
    private boolean compteAFaire;
    private EditBox recherche;
    private boolean focusTexte = true;
    private int x0, y0;
    private float edX, edY0;
    /** Banc d'essai : position de souris imposée. */
    int[] debugSouris;

    ClocheScreen(JsonObject o) {
        super(Component.literal("Cloche du village"));
        maj(o);
        CommClient.annonce.max = Math.max(20, (int) CommClient.num(o, "max"));
        for (String g : grades) gradesOn.add(g);
    }

    String id() { return CommClient.str(o, "id"); }

    void maj(JsonObject n) {
        this.o = n;
        ouvert = System.currentTimeMillis();
        village = CommClient.bool(n, "village");
        fixe = n.has("fixe") ? CommClient.str(n, "fixe") : null;
        sections.clear();
        for (JsonElement e : CommClient.arr(n, "sections")) {
            JsonObject s = e.getAsJsonObject();
            sections.add(new Sec(CommClient.str(s, "id"), CommClient.str(s, "nom"), CommClient.str(s, "insigne"), CommClient.bool(s, "nouvelle")));
        }
        grades.clear();
        for (JsonElement e : CommClient.arr(n, "grades")) grades.add(e.getAsString());
        if (fixe != null) { tout = false; cochees.clear(); cochees.add(fixe); }
        cochees.removeIf(id -> sections.stream().noneMatch(s -> s.id().equals(id)));
        zone = (int) CommClient.num(n, "zone");
        concernes = (int) CommClient.num(n, "concernes");
        trier();
    }

    void compte(int z, int c) { zone = z; concernes = c; }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        x0 = (this.width - W) / 2;
        y0 = Math.max(2, Math.min((this.height - H) / 2 - 19, this.height - H - 24));
        EditBox ancien = recherche;
        recherche = new EditBox(this.font, 0, 0, 100, 10, Component.literal(""));
        recherche.setBordered(false);
        recherche.setMaxLength(32);
        recherche.setTextColor(CommArt.CREME);
        if (ancien != null) recherche.setValue(ancien.getValue());
        recherche.setResponder(v -> { defil = 0; trier(); });
        recherche.setX(x0 + 289 + 16);
        recherche.setY(y0 + 205 + 3);
        recherche.setWidth(129 - 20);
        recherche.visible = village;
        addRenderableWidget(recherche);
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        super.removed();
    }

    /** Cochées en tête, puis l'ordre du serveur ; filtre de la recherche. */
    private void trier() {
        vue.clear();
        String q = recherche == null ? "" : sansAccents(recherche.getValue().trim());
        for (int pass = 0; pass < 2; pass++)
            for (Sec s : sections) {
                boolean on = cochees.contains(s.id());
                if ((pass == 0) != on) continue;
                if (!q.isEmpty() && !sansAccents(s.nom()).contains(q)) continue;
                vue.add(s);
            }
        defil = Math.max(0, Math.min(defil, vue.size() - VISIBLES));
    }

    private static String sansAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
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
        if (compteAFaire && System.currentTimeMillis() - demandeCompte > 250) { compteAFaire = false; demanderCompte(); }
        zones.clear();
        pz.police(this.font);
        CommArt.panneau(ctx, x0, y0, W, H);
        float cx = x0 + W / 2f;
        // en-tête : la cloche, le titre, le lieu et le titre du sonneur
        CommArt.sprite(ctx, CommArt.MOTIF_CLOCHE, 27, 17, cx - 27, y0 + 12, 54, -1);
        pz.texteC("Cloche du village", cx, y0 + 57, 24, CommArt.CREME, true);
        pz.texteC(CommClient.str(o, "lieu") + " · tu sonnes en " + CommClient.str(o, "titre"), cx, y0 + 71, 14, CommArt.CREME_DOUX, false);
        pz.h(x0 + 20, x0 + W - 23, y0 + 88, CommArt.FILET);
        int yc = y0 + 97;
        zoneAnnonce(x0 + 22, yc + 2, 250, 150, mx, my);
        colonneDroite(ctx, x0 + 289, yc + 6, mx, my);
        gradesPuces(x0 + 22, yc + 166, mx, my);
        pied(cx, mx, my);
        pz.vider(ctx);
        // dessinés tout de suite : marque de la zone, insignes et coches de la liste
        String type = CommClient.annonceType;
        CommArt.sprite(ctx, CommArt.cloche(type), CommArt.largeurCloche(type), 9, x0 + 22 + 11, yc + 2 + 10 - 0.5f,
                CommArt.largeurCloche(type), -1);
        listeObjets(ctx);
        if (recherche.visible) recherche.extractRenderState(ctx, mx, my, delta);
    }

    /* ---------------------------------------------------------------- la zone d'écriture */

    private void zoneAnnonce(float x, float y, float w, float h, int mx, int my) {
        Editeur ed = CommClient.annonce;
        CommArt.zone(pz, x, y, w, h, focusTexte);
        String type = CommClient.annonceType;
        int col = CommArt.couleurType(type);
        pz.arcade("ANNONCE DU VILLAGE", x + 14 + CommArt.largeurCloche(type), y + 11 - 0.5f, col, DaKit.facteurPetit());
        formatGI(ed, x + w - 5, y + 14, mx, my);
        pz.h(x + 9, x + w - 9, y + 25, CommArt.FILET_ZONE);
        // filets d'écriture presque invisibles
        for (int i = 0; i < 4; i++) pz.h(x + 11, x + w - 11, y + 52 + i * 17, DaKit.argb(0xD9A95E, 0.07f));
        edX = x + 12;
        edY0 = y + 45;
        ed.mettreEnPage(this.font, edX, edY0, w - 24, 18, 17);
        if (ed.longueur() == 0 && !focusTexte) pz.texte("Écris l'annonce…", edX, edY0, 18, CommArt.GRIS_FONCE, false);
        ed.peindre(pz, 18, CommArt.CREME, focusTexte);
        zones.add(new Zone(x, y + 26, w, h - 54, null));
        pz.h(x + 9, x + w - 9, y + h - 28, CommArt.FILET_ZONE);
        String sg = "— " + CommClient.str(o, "nom") + ", " + CommClient.str(o, "titre");
        pz.italique(sg, x + w - 12 - pz.largeurItalique(sg, 16), y + h - 14, 16, CommArt.CREME_DOUX);
        pz.texte(ed.longueur() + " / " + ed.max, x + 12, y + h - 14, 12, CommArt.GRIS_FONCE, false);
    }

    /** G et I en haut à droite de la zone, dans le langage de l'écran. */
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
            zones.add(new Zone(x - 2, cy - 6, 12, 12, () -> { ed.basculer(bit); focusTexte = true; setFocused(null); RebornSounds.uiClick(); }));
        }
    }

    /* ---------------------------------------------------------------- type, pour qui, sections */

    private void colonneDroite(GuiGraphicsExtractor ctx, float rx, float y, int mx, int my) {
        float R = x0 + 418;
        pz.rubrique("Type", rx, y, CommArt.CREME_DOUX);
        float yy = y + 13;
        for (int i = 0; i < 3; i++) {
            String t = TYPES[i];
            boolean on = t.equals(CommClient.annonceType);
            boolean hov = DaKit.dans(mx, my, Math.round(rx - 2), Math.round(yy - 6), Math.round(R - rx + 4), 12);
            pz.disque(rx + 2.5f, yy, 2.25f, CommArt.couleurType(t));
            float lw = pz.texte(TYPES_LIB[i], rx + 9, yy, 15, on || hov ? CommArt.CREME : CommArt.CREME_DOUX, on);
            if (on) pz.px(rx + 9, yy + 5.5f, rx + 9 + lw, yy + 6, CommArt.OR);
            pz.texteD(SONS[i], R, yy, 12, CommArt.GRIS, false);
            zones.add(new Zone(rx - 2, yy - 6, R - rx + 4, 12, () -> { CommClient.annonceType = t; RebornSounds.uiClick(); }));
            yy += 13;
        }
        yy += 6;
        pz.rubrique("Pour qui", rx, yy, CommArt.CREME_DOUX);
        yy += 13;
        // tout le village
        boolean dispoTout = village;
        CommArt.radio(pz, rx + 3.5f, yy, tout && dispoTout);
        pz.texte("Tout le village", rx + 12, yy, 15, !dispoTout ? CommArt.GRIS_FONCE : tout ? CommArt.CREME : CommArt.CREME_DOUX, tout && dispoTout);
        pz.texte(dispoTout ? "visiteurs présents compris" : "réservé aux gradés du village", rx + 12, yy + 9.5f, 12, CommArt.GRIS, false);
        if (dispoTout) zones.add(new Zone(rx - 2, yy - 6, R - rx, 18, () -> { tout = true; compteAFaire(); RebornSounds.uiClick(); }));
        yy += 22;
        CommArt.radio(pz, rx + 3.5f, yy, !tout);
        pz.texte(fixe != null ? "Ta section" : "Des sections", rx + 12, yy, 15, !tout ? CommArt.CREME : CommArt.CREME_DOUX, !tout);
        zones.add(new Zone(rx - 2, yy - 6, R - rx, 12, () -> { tout = false; compteAFaire(); RebornSounds.uiClick(); }));
        yy += 9;
        listeSections(rx, yy, R - rx, mx, my);
    }

    private float lx, ly, lw;

    private void listeSections(float x, float y, float w, int mx, int my) {
        boolean actif = !tout;
        // recherche
        recherche.visible = fixe == null;
        if (fixe == null) {
            pz.fond(x, y, x + w, y + 14, CommArt.NUIT_ZONE);
            pz.cadre(x, y, w, 14, recherche.isFocused() ? CommArt.OR_FONCE : CommArt.FILET);
            if (recherche.getValue().isEmpty() && !recherche.isFocused())
                pz.texte("Chercher une section", x + 16, y + 7, 13, CommArt.GRIS_FONCE, false);
            recherche.setX(Math.round(x + 16));
            recherche.setY(Math.round(y + 3.5f));
            y += 18;
        }
        float hh = VISIBLES * 13 + 4;
        pz.fond(x, y, x + w, y + hh, CommArt.NUIT_ZONE);
        pz.cadre(x, y, w, hh, CommArt.FILET);
        lx = x; ly = y; lw = w;
        for (int i = 0; i < VISIBLES && defil + i < vue.size(); i++) {
            Sec s = vue.get(defil + i);
            boolean on = cochees.contains(s.id());
            float yy = y + 2 + i * 13 + 6.5f;
            boolean hov = actif && fixe == null && DaKit.dans(mx, my, Math.round(x + 1), Math.round(yy - 6.5f), Math.round(w - 8), 13);
            if (hov) pz.fond(x + 1, yy - 6.5f, x + w - 7, yy + 6.5f, CommArt.SURVOL);
            CommArt.caseACocher(pz, x + 5, yy - 3.5f, on, hov);
            pz.texte(pz.ellipse(s.nom(), w - 46 - (s.nouvelle() ? 30 : 0), 14, false), x + 29, yy, 14,
                    !actif ? CommArt.GRIS : on ? CommArt.CREME : CommArt.CREME_DOUX, false);
            if (s.nouvelle()) pz.texteD("nouvelle", x + w - 9, yy, 11, CommArt.OR, false);
            if (actif && fixe == null)
                zones.add(new Zone(x + 1, yy - 6.5f, w - 8, 13, () -> {
                    if (!cochees.remove(s.id())) cochees.add(s.id());
                    compteAFaire();
                    RebornSounds.uiClick();
                }));
        }
        if (vue.isEmpty()) pz.texte(sections.isEmpty() ? "Aucune section" : "Aucune section trouvée", x + 8, y + 10, 13, CommArt.GRIS, false);
        // barre de défilement fine
        int n = Math.max(vue.size(), 1);
        pz.px(x + w - 3, y + 2, x + w - 2.5f, y + hh - 2, 0xFF2A1C20);
        if (vue.size() > VISIBLES) {
            float th = (hh - 4) * VISIBLES / (float) n, ty = y + 2 + (hh - 4) * defil / (float) n;
            pz.px(x + w - 3.5f, ty, x + w - 2, ty + th, CommArt.OR_SOMBRE);
        }
        y += hh + 8;
        pz.texte(cochees.size() + (cochees.size() > 1 ? " cochées sur " : " cochée sur ") + sections.size(), x, y, 12, CommArt.GRIS, false);
        if (fixe == null) {
            String[] m = {"Tout", "Aucune"};
            float tx = x + w - pz.largeur("Tout · Aucune", 12, false);
            for (int i = 0; i < 2; i++) {
                float mw = pz.largeur(m[i], 12, false);
                boolean hov = DaKit.dans(mx, my, Math.round(tx - 1), Math.round(y - 5), Math.round(mw + 2), 10);
                pz.texte(m[i], tx, y, 12, hov ? CommArt.CREME : CommArt.CREME_DOUX, false);
                final boolean tous = i == 0;
                zones.add(new Zone(tx - 1, y - 5, mw + 2, 10, () -> {
                    if (tous) for (Sec s : sections) cochees.add(s.id()); else cochees.clear();
                    tout = false;
                    compteAFaire();
                    RebornSounds.uiClick();
                }));
                tx += mw;
                if (i == 0) tx += pz.texte(" · ", tx, y, 12, CommArt.CREME_DOUX, false);
            }
        }
        pz.texte(fixe != null ? "Un chef de section n'annonce qu'à sa section." : "L'ANBU n'est jamais proposée.", x, y + 10, 12, CommArt.GRIS, false);
    }

    /** Insignes et coches (textures), après la plume. */
    private void listeObjets(GuiGraphicsExtractor ctx) {
        for (int i = 0; i < VISIBLES && defil + i < vue.size(); i++) {
            Sec s = vue.get(defil + i);
            boolean on = cochees.contains(s.id());
            float yy = ly + 2 + i * 13 + 6.5f;
            if (on) CommArt.sprite(ctx, CommArt.COCHE, 7, 7, lx + 5, yy - 3.5f, 7, tout ? 0x80FFFFFF : -1);
            fr.reborn.hud.faction.Insignes.poser(ctx, s.insigne(), lx + 16, yy - 4.5f, 9, on && !tout ? CommArt.OR : 0xFF706462);
        }
        if (fixe == null) CommArt.sprite(ctx, CommArt.LOUPE, 9, 9, lx + 4.5f, ly - 18 + 2.5f, 9, -1);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (DaKit.dans(mx, my, Math.round(lx), Math.round(ly), Math.round(lw), VISIBLES * 13 + 4)) {
            defil = Math.max(0, Math.min(Math.max(0, vue.size() - VISIBLES), defil - (int) Math.signum(v)));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    /* ---------------------------------------------------------------- grades */

    private void gradesPuces(float x, float y, int mx, int my) {
        float droite = x + 250;
        pz.rubrique("Grades concernés", x, y, CommArt.CREME_DOUX);
        pz.texteD(gradesOn.size() + " sur " + grades.size(), droite, y, 12, CommArt.GRIS, false);
        float xx = x, yy = y + 9;
        for (String g : grades) {
            boolean on = gradesOn.contains(g);
            float w = pz.largeur(g, 13, on) + 10;
            if (xx + w > droite) { xx = x; yy += 16; }
            boolean hov = DaKit.dans(mx, my, Math.round(xx), Math.round(yy), Math.round(w), 12);
            pz.fond(xx, yy, xx + w, yy + 12, on ? CommArt.PUCE_ON : CommArt.PUCE_OFF);
            pz.cadre(xx, yy, w, 12, on ? CommArt.OR_SOMBRE : hov ? 0xFF5A4C4C : CommArt.PUCE_BORD);
            pz.texteC(g, xx + w / 2f, yy + 6, 13, on ? CommArt.CREME : hov ? CommArt.CREME_DOUX : CommArt.GRIS, on);
            zones.add(new Zone(xx, yy, w, 12, () -> { if (!gradesOn.remove(g)) gradesOn.add(g); compteAFaire(); RebornSounds.uiClick(); }));
            xx += w + 4;
        }
    }

    /* ---------------------------------------------------------------- pied */

    private void pied(float cx, int mx, int my) {
        float L = x0 + 22, R = x0 + 418;
        float yf = y0 + 322;
        pz.h(x0 + 20, x0 + W - 23, yf, CommArt.FILET);
        float yy = yf + 17;
        pz.disque(L + 2, yy - 4.5f, 1.75f, CommArt.VERT);
        String zl = zone < 0 ? "…" : zone + (zone > 1 ? " personnes dans la zone" : " personne dans la zone") + " · "
                + Math.max(0, concernes) + (concernes > 1 ? " concernées" : " concernée");
        pz.texte(zl, L + 8, yy - 4.5f, 14, CommArt.CREME, false);
        pz.texte("Les autres entendent la cloche, sans le texte.", L + 8, yy + 6, 12, CommArt.GRIS, false);
        String lab = "Sonner · Entrée";
        float bw = pz.largeur(lab, 15, true) + 22, bc = R - 105, bx = bc - bw / 2f, by = yy - 9;
        boolean hov = DaKit.dans(mx, my, Math.round(bx), Math.round(by), Math.round(bw), 16);
        pz.fond(bx, by, bx + bw, by + 16, hov ? 0xFF5A1622 : 0xFF3C0E16);
        pz.cadre(bx, by, bw, 16, CommArt.OR);
        pz.px(bx + 0.5f, by + 0.5f, bx + bw - 0.5f, by + 1, 0xFF962834);
        pz.texteC(lab, bc, by + 8, 15, CommArt.CREME, true);
        zones.add(new Zone(bx, by, bw, 16, this::sonner));
        float ax = bc + bw / 2f + 9, aw = pz.largeur("Annuler", 15, false);
        boolean ha = DaKit.dans(mx, my, Math.round(ax - 2), Math.round(by), Math.round(aw + 4), 16);
        pz.texte("Annuler", ax, by + 8, 15, ha ? CommArt.CREME : CommArt.CREME_DOUX, false);
        zones.add(new Zone(ax - 2, by, aw + 4, 16, this::onClose));
        // sous l'écran : Échap et le délai de la cloche (ou le dernier message du serveur)
        String t = CommClient.toastActif();
        if (t != null) pz.texteC(t, cx, Math.min(y0 + H + 16, this.height - 6), 13, CommClient.toastRefus ? CommArt.ROUGE : CommArt.AMBRE, false);
        else pz.texteC("Échap  fermer   ·   " + delai(), cx, Math.min(y0 + H + 16, this.height - 6), 13, CommArt.CREME_DOUX, false);
    }

    private String delai() {
        long dt = (System.currentTimeMillis() - ouvert) / 1000;
        long tue = CommClient.num(o, "tue") - dt, ac = CommClient.num(o, "attenteCloche") - dt, am = CommClient.num(o, "attenteMoi") - dt;
        if (tue > 0) return "la cloche est tue par le staff (encore " + duree(tue) + ")";
        // seuil « Sonner sans délai » du village (ou Kage, ou staff en animation) : aucun anti-spam
        String sd = CommClient.str(o, "sansDelai");
        if (!sd.isEmpty()) return "la cloche peut sonner, " + ("animation".equals(sd) ? "sans délai en mode animation" : "sans délai pour ton grade");
        if (ac > 0) return "la cloche vient de sonner : encore " + duree(ac);
        if (am > 0) return "tu as sonné il y a peu : encore " + duree(am);
        long libre = CommClient.num(o, "libreDepuis");
        long toi = CommClient.num(o, "delaiMoi") / 60;
        return "la cloche peut sonner (" + (libre < 0 ? "jamais sonnée" : "libre depuis " + duree(libre + dt)) + " ; toi : " + toi + " min)";
    }

    static String duree(long s) {
        if (s < 60) return s + " s";
        if (s < 3600) return (s / 60) + " min";
        return (s / 3600) + " h";
    }

    /* ================================================================ actions */

    private void compteAFaire() { compteAFaire = true; demandeCompte = System.currentTimeMillis(); }

    private JsonObject cible(String action) {
        JsonObject q = CommClient.a(action);
        q.addProperty("id", id());
        q.addProperty("tout", tout);
        JsonArray s = new JsonArray();
        for (String c : cochees) s.add(c);
        q.add("sections", s);
        JsonArray g = new JsonArray();
        for (String x : gradesOn) g.add(x);
        q.add("grades", g);
        return q;
    }

    private void demanderCompte() { CommClient.send(cible("compter")); }

    private void sonner() {
        Editeur ed = CommClient.annonce;
        if (ed.vide()) { CommClient.toast("Écris l'annonce.", true); return; }
        if (gradesOn.isEmpty()) { CommClient.toast("Coche au moins un grade.", true); return; }
        if (!tout && cochees.isEmpty()) { CommClient.toast("Coche au moins une section.", true); return; }
        JsonObject q = cible("sonner");
        q.addProperty("type", CommClient.annonceType);
        q.add("texte", CommArt.json(ed.segs()));
        CommClient.send(q);
        RebornSounds.confirm();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y();
        if (recherche.visible && DaKit.dans(mx, my, recherche.getX() - 14, recherche.getY() - 4, recherche.getWidth() + 16, 14)) {
            focusTexte = false;
            recherche.setFocused(true);
            setFocused(recherche);
            return true;
        }
        for (Zone z : new ArrayList<>(zones)) {
            if (!z.dans(mx, my)) continue;
            if (z.act() == null) {
                focusTexte = true;
                recherche.setFocused(false);
                setFocused(null);
                CommClient.annonce.cliquer(mx, my, e.hasShiftDown());
            } else z.act().run();
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            if (focusTexte && e.hasShiftDown()) { CommClient.annonce.inserer("\n"); return true; }
            sonner();
            return true;
        }
        if (k == GLFW.GLFW_KEY_TAB) {
            focusTexte = !focusTexte || !recherche.visible;
            recherche.setFocused(!focusTexte);
            setFocused(focusTexte ? null : recherche);
            return true;
        }
        if (!focusTexte && recherche.isFocused() && recherche.keyPressed(e)) return true;
        if (focusTexte && CommClient.annonce.touche(k, e.hasShiftDown(), e.hasControlDown())) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent ev) {
        if (!focusTexte && recherche.isFocused()) return recherche.charTyped(ev);
        if (focusTexte) {
            if (ev.isAllowedChatCharacter()) CommClient.annonce.inserer(ev.codepointAsString());
            return true;
        }
        return super.charTyped(ev);
    }

    /* ================================================================ banc */

    void debugCocher(String... ids) {
        tout = false;
        cochees.clear();
        for (String s : ids) cochees.add(s);
        trier();
    }

    void debugGrades(String... g) { gradesOn.clear(); gradesOn.addAll(List.of(g)); }
}
