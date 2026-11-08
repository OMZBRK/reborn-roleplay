package fr.reborn.hud.staff;

import fr.reborn.hud.concentration.HaloPlaque;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.skin.SkinSpec;
import fr.reborn.hud.ui.CouleurTextures;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Locale;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Rangée <b>« Halo »</b> de l'éditeur de plaque et sa ligne dans la fiche (lot 4, planche c6, décision du 07/11) : la
 * couleur du halo de <b>chaque plaque</b>, au choix du staff.
 *
 * <ul>
 *   <li>Huit pastilles « lumière » (une petite lueur sur fond de nuit : cœur clair, lueur, voile — ce que verra le joueur,
 *       pas un aplat), dans l'ordre de {@link HaloPlaque#PRESETS} : ivoire-or (défaut, rien de stocké), blanc lunaire,
 *       bleu chakra, violet, rouge sang, vert jade, ambre, aucun halo ; la choisie cerclée d'or, le nom en info-bulle.
 *       Dessous, le nom et le code de la couleur ; à droite, le lien « Personnalisée ».</li>
 *   <li><b>Personnalisée</b> ouvre un sélecteur en ligne sous la rangée : carré saturation × luminosité (de 100 % à
 *       55 % : une lumière additive sombre ne se verrait pas), barre de teinte, aperçu de la lueur seule sur fond de nuit
 *       (le vrai halo, petit), champ hexadécimal (une couleur trop sombre est relevée à 55 % et le champ montre la valeur
 *       relevée), les 4 dernières couleurs personnalisées de ce poste ({@link RebornPrefs#haloRecentes}). Un clic sur un
 *       préréglage ou sur « Personnalisée » le referme. À l'ouverture, le volet défile juste assez pour le montrer.</li>
 *   <li>L'aperçu devant l'objet (dans le monde) prend la couleur tout de suite ({@code GardePlaques.monde}).</li>
 * </ul>
 * Textures du sélecteur : {@link CouleurTextures}, partagées avec la couleur des traces de pas ; les pastilles sont des
 * disques du pinceau (aucune texture).
 */
final class GardeHalo {

    static final String ASTUCE = "Astuce : choisis un halo d'une autre famille que les mots colorés du texte (rouge sur rouge"
        + " sang, or sur ivoire-or ou ambre perdent leur couleur propre).";
    private static final int PERSO_DEFAUT = 0xF29CC2;

    private final StaffScreen s;
    /** Préréglage choisi ({@link HaloPlaque#DEFAUT}, {@link HaloPlaque#AUCUN} ou une lueur des préréglages). */
    int code = HaloPlaque.DEFAUT;
    /** Valeur à l'ouverture de l'éditeur (une plaque modifiée n'envoie le halo que s'il a changé). */
    int initial = HaloPlaque.DEFAUT;
    /** « Personnalisée » choisie : la couleur est {@link #persoCouleur}. */
    boolean perso, ouvert;
    int persoCouleur = PERSO_DEFAUT;
    private float h, sat, val;
    EditBox hexBox;
    private String hexVu = "";
    /** Glissé en cours : 1 carré, 2 teinte. */
    private int pick;
    private float sqX0, sqY0, sqW = 1, sqH = 1;
    /** À l'ouverture : faire défiler le volet pour montrer tout le sélecteur. */
    private boolean defiler;

    GardeHalo(StaffScreen s) { this.s = s; }

    void init() {
        hexBox = s.box(hexBox, "#F29CC2", 7);
    }

    /* ================================================================ état */

    /** Couleur effective à envoyer ou dessiner ({@link HaloPlaque#code}). */
    int code() { return perso ? persoCouleur : code; }

    /** Valeur du protocole : {@code ""} (ivoire-or), {@code "aucun"}, {@code "RRGGBB"}. */
    String valeur() { return HaloPlaque.valeur(code()); }

    boolean change() { return code() != initial; }

    /** Nouvelle plaque : ivoire-or, sélecteur fermé. */
    void effacer() { poser(HaloPlaque.DEFAUT); }

    /** Plaque modifiée : son halo ({@link HaloPlaque#code}) ; une couleur hors des préréglages = « Personnalisée ». */
    void poser(int c) {
        initial = c;
        ouvert = false;
        pick = 0;
        if (c >= 0 && !estPreset(c)) {
            perso = true;
            persoCouleur = c;
            code = HaloPlaque.DEFAUT;
            // le sélecteur se cale sur la couleur de CETTE plaque (rien n'a changé tant que le staff n'y touche pas)
            caler(c);
            hexVu = "#" + hex(c);
            if (hexBox != null) hexBox.setValue(hexVu);
        } else {
            perso = false;
            code = c;
        }
        if (hexBox != null) hexBox.setFocused(false);
    }

    private static boolean estPreset(int c) {
        for (int p : HaloPlaque.PRESETS) if (p == c) return true;
        return false;
    }

    /** Plaque posée ou enregistrée : une couleur personnalisée rejoint les « récentes » de ce poste (4 au plus). */
    void retenir() {
        if (!perso) return;
        RebornPrefs pr = RebornPrefs.INSTANCE;
        pr.ensureLoaded();
        int[] old = pr.haloRecentes == null ? new int[0] : pr.haloRecentes;
        int[] neu = new int[Math.min(4, old.length + 1)];
        neu[0] = persoCouleur;
        int n = 1;
        for (int c : old) if (c != persoCouleur && n < neu.length) neu[n++] = c;
        pr.haloRecentes = n == neu.length ? neu : java.util.Arrays.copyOf(neu, n);
        pr.save();
    }

    private static int[] recentes() {
        RebornPrefs pr = RebornPrefs.INSTANCE;
        pr.ensureLoaded();
        return pr.haloRecentes == null ? new int[0] : pr.haloRecentes;
    }

    private void choisir(int c) {
        code = c;
        perso = false;
        ouvert = false;
        pick = 0;
        if (hexBox != null) hexBox.setFocused(false);
        RebornSounds.uiClick();
    }

    private void basculerPerso() {
        if (!perso) {
            perso = true;
            ouvert = true;
            // départ : la dernière couleur personnalisée, ou la lueur du préréglage choisi (hors ivoire-or et aucun)
            int[] r = recentes();
            if (code >= 0) persoCouleur = code;
            else if (persoCouleur == PERSO_DEFAUT && r.length > 0) persoCouleur = r[0];
            depuisCouleur(persoCouleur);
            defiler = true;
        } else {
            ouvert = !ouvert;
            defiler = ouvert;
            if (ouvert && hexBox != null && !hexVu.equals(hexBox.getValue())) hexBox.setValue(hexVu);
        }
        if (!ouvert && hexBox != null) hexBox.setFocused(false);
        RebornSounds.uiClick();
    }

    /** Teinte, saturation et luminosité du sélecteur : celles de {@code c} (le carré s'arrête à 55 %). */
    private void caler(int c) {
        float[] hsv = SkinSpec.argbToHsv(c);
        h = hsv[0];
        sat = hsv[1];
        val = Math.max(HaloPlaque.Palette.V_MIN, hsv[2]);
    }

    private void depuisCouleur(int c) {
        caler(c);
        persoCouleur = HaloPlaque.Palette.relever(c);
        hexVu = "#" + hex(persoCouleur);
        if (hexBox != null) hexBox.setValue(hexVu);
    }

    private void appliquer() {
        persoCouleur = SkinSpec.hsvToArgb(h, sat, val) & 0xFFFFFF;
        hexVu = "#" + hex(persoCouleur);
        if (hexBox != null) hexBox.setValue(hexVu);
    }

    static String hex(int c) { return String.format(Locale.ROOT, "%06X", c & 0xFFFFFF); }

    /** Saisie du champ hexadécimal (au tick) : six chiffres valides = la couleur, relevée à 55 % si elle est trop sombre. */
    void tick() {
        if (!ouvert || hexBox == null) return;
        String v = hexBox.getValue().trim();
        if (v.equals(hexVu)) return;
        String t = v.startsWith("#") ? v.substring(1) : v;
        if (!t.matches("[0-9A-Fa-f]{6}")) return;
        int c = Integer.parseInt(t, 16);
        caler(c);
        persoCouleur = HaloPlaque.Palette.relever(c);
        hexVu = "#" + hex(persoCouleur);
        // le champ montre la valeur relevée (et la forme « #RRGGBB »)
        if (!hexVu.equals(v)) hexBox.setValue(hexVu);
    }

    /* ================================================================ rangée de l'éditeur */

    /** Rangée « Halo » à la hauteur {@code y} (corps du volet) ; sélecteur dessous s'il est ouvert. Renvoie le bas. */
    int rangee(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        pz.texte("Halo", ix, y + 4, 12, GRIS, false);
        s.tip(ix, y - 2, 30, 12, ASTUCE);
        float x0 = ix + 46;
        for (int i = 0; i < HaloPlaque.PRESETS.length; i++) {
            final int c = HaloPlaque.PRESETS[i];
            float cx = x0 + 3 + i * 11;
            int X = Math.round(cx - 5), Y = y - 1;
            boolean hov = s.hover(X, Y, 10, 10) && s.cliquable(Y, 10);
            pastille(pz, cx, y + 4, 3f, c, !perso && c == code, hov);
            s.tip(X, Y, 10, 10, HaloPlaque.PRESETS_NOMS[i] + (c == HaloPlaque.DEFAUT ? " · par défaut" : ""));
            if (s.cliquable(Y, 10)) s.hits.add(new StaffScreen.Hit(X, Y, 10, 10, () -> choisir(c), null, null));
        }
        y += 11;
        // nom et code de la couleur ; « Personnalisée » à droite
        String lib = "Personnalisée";
        float lw = pz.largeur(lib, 13, perso);
        float lx = ix + iw - lw;
        String nom, det;
        if (perso) {
            nom = "Personnalisée";
            det = " · #" + hex(persoCouleur);
        } else if (code == HaloPlaque.AUCUN) {
            nom = "Aucun halo";
            det = " · le texte seul";
        } else {
            nom = nomPreset(code);
            det = " · #" + hex(HaloPlaque.lueurDe(code)) + (code == HaloPlaque.DEFAUT ? " · par défaut" : "");
        }
        if (!perso) {
            float w = pz.texte(nom, x0, y + 4, 12, CREME, false);
            s.txt(det, x0 + w, y + 4, lx - 12 - x0 - w, 11, GRIS_2, false);
        } else {
            float w = pz.texte(nom, x0, y + 4, 12, CREME, false);
            pz.texte(det, x0 + w, y + 4, 11, GRIS_2, false);
        }
        int LX = Math.round(lx - 11), LY = y - 2, LW = Math.round(lw) + 12;
        boolean hovL = s.hover(LX, LY, LW, 12) && s.cliquable(LY, 12);
        if (perso) {
            pz.texte(lib, lx, y + 4, 13, CREME, true);
            pz.filetH(lx, lx + lw, y + 9.5f, OR);
            pastille(pz, lx - 6, y + 4, 2.5f, persoCouleur, false, hovL);
        } else {
            pz.texte(lib, lx, y + 4, 13, hovL ? CREME : OR_CLAIR, false);
            if (hovL) pz.filetH(lx, lx + lw, y + 9.5f, CREME);
            roue(pz, lx - 6, y + 4, 2.75f);
        }
        if (s.cliquable(LY, 12)) s.hits.add(new StaffScreen.Hit(LX, LY, LW, 12, this::basculerPerso, null, null));
        y += 14;
        if (ouvert && perso) {
            int bas = selecteur(y + 2);
            if (defiler) {
                defiler = false;
                int deborde = bas + 6 - s.vy1();
                if (deborde > 0) s.scroll += deborde;
            }
            y = bas + 2;
        }
        return y;
    }

    static String nomPreset(int c) {
        for (int i = 0; i < HaloPlaque.PRESETS.length; i++) if (HaloPlaque.PRESETS[i] == c) return HaloPlaque.PRESETS_NOMS[i];
        return "Personnalisée";
    }

    /** Sélecteur en ligne (haut {@code y0}) : carré, teinte, aperçu, hex, récentes. Renvoie le bas. */
    private int selecteur(int y0) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        int x0 = ix + 46, xp0 = ix + iw - 44, x1 = xp0 - 6;
        int ya = y0, hh = 38, yb = ya + hh;
        int yh0 = yb + 4, yh1 = yh0 + 5;
        int w = x1 - x0;
        sqX0 = x0;
        sqY0 = ya;
        sqW = w;
        sqH = hh;
        pz.texte("Couleur", ix, ya + 5, 12, GRIS, false);
        pz.texte("saturation", ix, ya + 13, 11, GRIS_2, false);
        pz.texte("× luminosité", ix, ya + 20, 11, GRIS_2, false);
        pz.texte("Teinte", ix, (yh0 + yh1) / 2f, 12, GRIS, false);
        pz.fond(xp0, ya, ix + iw, yh1, NUIT);
        pz.vider(s.ctxCourant);
        // carré et barre de teinte (textures), aperçu de la lueur (le vrai halo, petit, dans sa boîte)
        GuiGraphicsExtractor ctx = s.ctxCourant;
        if (CouleurTextures.preparer(h)) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, CouleurTextures.SV, x0, ya, 0f, 0f, w, hh, CouleurTextures.SV_W, CouleurTextures.SV_H,
                CouleurTextures.SV_W, CouleurTextures.SV_H);
            ctx.blit(RenderPipelines.GUI_TEXTURED, CouleurTextures.TEINTE, x0, yh0, 0f, 0f, w, yh1 - yh0, CouleurTextures.TEINTE_W, 1,
                CouleurTextures.TEINTE_W, 1);
        }
        int e = echelle();
        ctx.enableScissor(xp0 + 1, ya + 1, ix + iw - 1, yh1 - 1);
        DaKit.debutPx(ctx);
        HaloPlaque.dessiner(ctx, (xp0 + ix + iw) / 2.0 * e, ((ya + yh1) / 2.0 - 4) * e, 0.62 * e / 2.0, 1f,
            net.minecraft.util.Util.getMillis(), persoCouleur);
        DaKit.finPx(ctx);
        ctx.disableScissor();
        pz.cadre(x0 - 1, ya - 1, w + 2, hh + 2, FILET_2);
        pz.cadre(x0 - 1, yh0 - 1, w + 2, yh1 - yh0 + 2, FILET_2);
        pz.cadre(xp0, ya, ix + iw - xp0, yh1 - ya, FILET_2);
        pz.texteC("aperçu", (xp0 + ix + iw) / 2f, yh1 - 5, 11, GRIS_2, false);
        // curseurs : anneau sur le carré, poignée sur la teinte
        float cx = x0 + sat * (w - 1), cy = ya + (1 - (val - HaloPlaque.Palette.V_MIN) / (1 - HaloPlaque.Palette.V_MIN)) * (hh - 1);
        pz.pastille(cx, cy, 2.8f, 0xFF110B0D);
        pz.pastille(cx, cy, 2.2f, 0xFFFFFFFF);
        pz.pastille(cx, cy, 1.6f, 0xFF000000 | persoCouleur);
        float hx = x0 + h * (w - 1);
        pz.px(hx - 1.5f, yh0 - 1.5f, hx + 2f, yh1 + 1.5f, 0xFF110B0D);
        pz.px(hx - 1f, yh0 - 1f, hx + 1.5f, yh1 + 1f, 0xFFFFFFFF);
        pz.px(hx - 0.5f, yh0 - 0.5f, hx + 1f, yh1 + 0.5f, 0xFF000000 | SkinSpec.hsvToArgb(h, 1f, 1f));
        if (s.cliquable(ya, hh)) s.hits.add(new StaffScreen.Hit(x0, ya, w, hh, () -> { pick = 1; majPick(s.mouseX, s.mouseY); }, null, null));
        if (s.cliquable(yh0 - 2, yh1 - yh0 + 4))
            s.hits.add(new StaffScreen.Hit(x0, yh0 - 2, w, yh1 - yh0 + 4, () -> { pick = 2; majPick(s.mouseX, s.mouseY); }, null, null));
        // hex et récentes
        int yr = yh1 + 6;
        pz.texte("Hex", ix, yr + 8.5f, 12, GRIS, false);
        s.champ(hexBox, x0, yr, 46, null);
        float xr = x0 + 54;
        int[] rec = recentes();
        if (rec.length > 0) {
            xr += pz.texte("récentes", xr, yr + 8.5f, 11, GRIS_2, false) + 6;
            for (int i = 0; i < rec.length && i < 4; i++) {
                final int c = rec[i];
                float rx = xr + 3 + i * 10;
                int X = Math.round(rx - 4), Y = yr + 4;
                boolean hov = s.hover(X, Y, 9, 9) && s.cliquable(Y, 9);
                pastille(pz, rx, yr + 8.5f, 2.75f, c, false, hov);
                s.tip(X, Y, 9, 9, "#" + hex(c));
                if (s.cliquable(Y, 9)) s.hits.add(new StaffScreen.Hit(X, Y, 9, 9, () -> { depuisCouleur(c); RebornSounds.uiClick(); }, null, null));
            }
        }
        int yn = yr + 23;
        pz.texte("Une lueur ne peut pas être sombre : le carré s'arrête à 55 %.", ix, yn, 11, GRIS_2, false);
        return yn + 8;
    }

    private void majPick(double mx, double my) {
        if (pick == 1) {
            sat = clamp01((float) ((mx - sqX0) / Math.max(1, sqW - 1)));
            float t = clamp01((float) ((my - sqY0) / Math.max(1, sqH - 1)));
            val = 1f - t * (1f - HaloPlaque.Palette.V_MIN);
        } else if (pick == 2) {
            h = Math.min(0.9999f, clamp01((float) ((mx - sqX0) / Math.max(1, sqW - 1))));
        } else return;
        appliquer();
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    /** Glissé de la souris (bouton tenu) sur le carré ou la teinte. */
    boolean glisser(double x, double y) {
        if (pick == 0) return false;
        majPick(x, y);
        return true;
    }

    void relacher() { pick = 0; }

    /** Échap : referme le sélecteur s'il est ouvert. */
    boolean echap() {
        if (!ouvert) return false;
        ouvert = false;
        pick = 0;
        if (hexBox != null) hexBox.setFocused(false);
        return true;
    }

    /* ================================================================ fiche */

    /** Ligne « Halo » de la fiche : pastille, nom (ou « Personnalisée »), code ; « Aucun halo » en toutes lettres. */
    int ligneFiche(int y, int c) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        pz.texte("Halo", ix, y + 4, 12, GRIS, false);
        pastille(pz, ix + 48.5f, y + 4, 2.5f, c, false, false);
        float x = ix + 55;
        String nom = c == HaloPlaque.AUCUN ? "Aucun halo" : nomPreset(c);
        float w = pz.texte(nom, x, y + 4, 12, CREME, false);
        if (c != HaloPlaque.AUCUN)
            s.txt(" · #" + hex(HaloPlaque.lueurDe(c)) + (c == HaloPlaque.DEFAUT ? " · par défaut" : ""), x + w, y + 4,
                ix + iw - x - w, 11, GRIS_2, false);
        return y + 10;
    }

    /* ================================================================ pastilles */

    private static final int CACHE = 12;
    private static final int[] PAL_CODE = new int[CACHE];
    private static final HaloPlaque.Palette[] PAL = new HaloPlaque.Palette[CACHE];
    private static int palN;

    private static HaloPlaque.Palette palette(int c) {
        if (c == HaloPlaque.DEFAUT) return HaloPlaque.palette;
        for (int i = 0; i < CACHE; i++) if (PAL[i] != null && PAL_CODE[i] == c) return PAL[i];
        int j = palN++ % CACHE;
        PAL_CODE[j] = c;
        PAL[j] = HaloPlaque.Palette.depuis(c);
        return PAL[j];
    }

    /** Rayons (fractions de r) des disques empilés d'une pastille « lumière », du bord vers le cœur. */
    private static final float[] ANNEAUX = {1.0f, 0.82f, 0.64f, 0.46f, 0.30f};

    /**
     * Pastille « lumière » de rayon {@code r} (GUI) : disque de nuit cerclé d'un filet (or doux au survol), et dedans le
     * profil du halo réduit (planche c6, {@code pastille_halo}) : nuit + cœur × 0,85 (σ 0,30 r) + lueur × 1,25 (σ 0,62 r) +
     * voile × 0,55 (σ 1,15 r), rendu en cinq disques concentriques ; « aucun » : barre grise en travers ; la choisie :
     * anneau d'or.
     */
    static void pastille(GardeArt.Pinceau pz, float cx, float cy, float r, int c, boolean choisi, boolean survol) {
        if (choisi) {
            pz.pastille(cx, cy, r + 1.75f, OR);
            pz.pastille(cx, cy, r + 1.1f, NUIT);
        }
        pz.pastille(cx, cy, r + 0.5f, survol ? OR_DOUX : FILET_2);
        pz.pastille(cx, cy, r, NUIT);
        if (c == HaloPlaque.AUCUN) {
            // barre en travers, en petits disques (le pinceau pose les disques après les filets)
            for (float t = -r * 0.6f; t <= r * 0.6f + 1e-3f; t += r * 0.2f) pz.pastille(cx + t, cy - t, 0.42f, GRIS_2);
            return;
        }
        HaloPlaque.Palette p = palette(c);
        for (float k : ANNEAUX) pz.pastille(cx, cy, r * k, profil(p, k * 0.8f));
    }

    /** Couleur du profil de la pastille à la distance {@code d} (fraction du rayon) du centre, sur la nuit. */
    private static int profil(HaloPlaque.Palette p, float d) {
        double gc = 0.85 * Math.exp(-(d / 0.30) * (d / 0.30)), gl = 1.25 * Math.exp(-(d / 0.62) * (d / 0.62)),
            gv = 0.55 * Math.exp(-(d / 1.15) * (d / 1.15));
        int col = 0xFF000000;
        for (int sh = 16; sh >= 0; sh -= 8) {
            double v = ((NUIT >> sh) & 255) + ((p.coeur() >> sh) & 255) * gc + ((p.lueur() >> sh) & 255) * gl
                + ((p.voile() >> sh) & 255) * gv;
            col |= (int) Math.min(255, Math.round(v)) << sh;
        }
        return col;
    }

    /** Petit anneau de teintes : l'icône de « Personnalisée ». */
    static void roue(GardeArt.Pinceau pz, float cx, float cy, float r) {
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12;
            pz.pastille(cx + (float) Math.cos(a) * r * 0.78f, cy + (float) Math.sin(a) * r * 0.78f, r * 0.30f,
                SkinSpec.hsvToArgb(i / 12f, 0.55f, 1f));
        }
    }

    /* ================================================================ banc */

    /** Banc : le vrai chemin « Modifier le texte » d'une plaque de halo {@code c}, puis clic sur « Personnalisée ». */
    void debugModifierPuisOuvrir(int c) {
        poser(c);
        basculerPerso();
    }

    /** Banc : un clic sur la barre de teinte, à la teinte {@code t}. */
    void debugTeinte(float t) {
        h = t;
        appliquer();
    }

    String debugEtat() {
        return String.format(Locale.ROOT, "h=%.3f sat=%.3f val=%.3f hex=%s couleur=%06X ouvert=%b change=%b", h, sat, val,
            hexBox == null ? "?" : hexBox.getValue(), persoCouleur, ouvert, change());
    }

    int debugCouleur() { return persoCouleur; }

    void debug(int c, boolean perso, int couleur, boolean ouvert) {
        poser(HaloPlaque.DEFAUT);
        if (perso) {
            this.perso = true;
            depuisCouleur(couleur);
            this.ouvert = ouvert;
            defiler = ouvert;
        } else code = c;
    }
}
