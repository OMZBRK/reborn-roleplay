package fr.reborn.hud.menu.settings;

import fr.reborn.hud.concentration.HaloPlaque;
import fr.reborn.hud.concentration.Traces;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.skin.SkinSpec;
import fr.reborn.hud.ui.CouleurTextures;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.Locale;

/**
 * Le réglage <b>« Couleur des traces »</b> (Paramètres › Interface › Concentration, lot 4, décision du 08/11) : chaque joueur
 * choisit la couleur des traces de pas <b>sur son écran</b> ; « Automatique » par défaut (blanc sur un sol sombre, sombre sur un
 * sol clair, {@link Traces#solClair}), sinon la même pour toutes (l'anonymat reste entier), rien n'est envoyé au serveur.
 *
 * <p>« Automatique », une rangée de semelles (les préréglages, {@link Traces#PRESETS}) et le lien « Personnalisée », qui ouvre sous la ligne le
 * <b>même sélecteur que le halo des plaques</b> ({@code GardeHalo}, {@link CouleurTextures}) : carré saturation × luminosité
 * limité à 55 %, barre de teinte, aperçu, champ hexadécimal, les 4 dernières couleurs personnalisées de ce poste.
 *
 * <p>L'état vit ici en statique : les Paramètres refont leurs widgets à chaque changement de mise en page.
 */
final class CouleurTracesReglage {

    static final int LIGNE_H = 28, SELECTEUR_H = 86;
    private static final int CELLULE = 14, SEMELLE_L = 9, SEMELLE_H = 15, LARGEUR_LIEN = 66;
    /** Largeur de « Automatique » (pastille et nom), devant les préréglages. */
    private static final int LARGEUR_AUTO = 62;
    private static final String AIDE_AUTO = "Blanc sur un sol sombre, sombre sur un sol clair.";
    /** La moitié claire de la pastille « Automatique » : un sol clair. */
    private static final int SOL_CLAIR_PASTILLE = 0xFFE9DFC6;
    private static final int NUIT = 0xFF110B0D, CADRE = 0xFF3A2A22, GRIS = 0xFF8A8070;

    private static float h, s, v;
    private static boolean ouvert;
    /** Glissé en cours : 1 carré, 2 teinte. */
    private static int pick;
    private static String hexVu = "";
    /** Depuis « Automatique », tant qu'aucun geste n'a choisi : la couleur de départ, celle du champ, de l'aperçu et des curseurs. */
    private static int depart = 0x7FD0C8;
    private static EditBox hex;
    /** Les widgets de la dernière mise en page (banc). */
    private static Ligne ligneVue;
    private static Selecteur selecteurVu;

    private CouleurTracesReglage() {}

    private static int couleur() {
        RebornPrefs pr = RebornPrefs.INSTANCE;
        pr.ensureLoaded();
        return pr.tracesCouleur & 0xFFFFFF;
    }

    private static boolean auto() {
        RebornPrefs pr = RebornPrefs.INSTANCE;
        pr.ensureLoaded();
        return pr.tracesAuto;
    }

    private static boolean estPreset(int c) {
        for (int p : Traces.PRESETS) if (p == c) return true;
        return false;
    }

    private static String nom(int c) {
        for (int i = 0; i < Traces.PRESETS.length; i++) if (Traces.PRESETS[i] == c) return Traces.PRESETS_NOMS[i];
        return "Personnalisée";
    }

    static String hexa(int c) { return String.format(Locale.ROOT, "%06X", c & 0xFFFFFF); }

    private static void poser(int c, boolean ecrire) {
        RebornPrefs pr = RebornPrefs.INSTANCE;
        pr.tracesAuto = false;
        pr.tracesCouleur = c & 0xFFFFFF;
        if (ecrire) pr.save();
    }

    private static void poserAuto(boolean ecrire) {
        RebornPrefs pr = RebornPrefs.INSTANCE;
        pr.tracesAuto = true;
        if (ecrire) pr.save();
    }

    static boolean ouvert() { return ouvert; }

    /** « Réinitialiser » de la page : « Automatique », sélecteur fermé. */
    static void reinitialiser() {
        ouvert = false;
        pick = 0;
        RebornPrefs.INSTANCE.tracesCouleur = Traces.IVOIRE;
        poserAuto(true);
    }

    private static void depuisCouleur(int c) {
        float[] hsv = SkinSpec.argbToHsv(c);
        h = hsv[0];
        s = hsv[1];
        v = Math.max(HaloPlaque.Palette.V_MIN, hsv[2]);
        hexVu = "#" + hexa(HaloPlaque.Palette.relever(c));
        if (hex != null) hex.setValue(hexVu);
    }

    /** Retient une couleur personnalisée dans les 4 dernières de ce poste. */
    private static void retenir(int c) {
        if (estPreset(c)) return;
        RebornPrefs pr = RebornPrefs.INSTANCE;
        int[] old = pr.tracesRecentes == null ? new int[0] : pr.tracesRecentes;
        int[] neu = new int[Math.min(4, old.length + 1)];
        neu[0] = c;
        int n = 1;
        for (int r : old) if (r != c && n < neu.length) neu[n++] = r;
        pr.tracesRecentes = n == neu.length ? neu : java.util.Arrays.copyOf(neu, n);
    }

    private static void semelle(GuiGraphicsExtractor ctx, int x, int y, int w, int hh, int rgb) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, Traces.TEX, x, y, 1f, 1f, w, hh, Traces.SEMELLE_W, Traces.SEMELLE_H,
            Traces.TEX_W, Traces.TEX_H, 0xFF000000 | rgb);
    }

    private static void cadre(GuiGraphicsExtractor ctx, int x, int y, int w, int hh, int col) {
        ctx.fill(x, y, x + w, y + 1, col);
        ctx.fill(x, y + hh - 1, x + w, y + hh, col);
        ctx.fill(x, y, x + 1, y + hh, col);
        ctx.fill(x + w - 1, y, x + w, y + hh, col);
    }

    /** La pastille « Automatique » : à gauche une semelle blanche sur un sol sombre, à droite une sombre sur un sol clair. */
    private static void pastilleAuto(GuiGraphicsExtractor ctx, int cx, int yc, boolean choisi, boolean survole) {
        int mil = cx + (CELLULE - 1) / 2;
        ctx.fill(cx, yc, mil, yc + 17, NUIT);
        ctx.fill(mil, yc, cx + CELLULE - 1, yc + 17, SOL_CLAIR_PASTILLE);
        int xs = mil - 5;
        // 3 texels de chaque côté de la semelle, 5 pixels chacun
        ctx.blit(RenderPipelines.GUI_TEXTURED, Traces.TEX, xs, yc + 1, 1f, 1f, 5, SEMELLE_H, 3, Traces.SEMELLE_H,
            Traces.TEX_W, Traces.TEX_H, 0xFF000000 | Traces.IVOIRE);
        ctx.blit(RenderPipelines.GUI_TEXTURED, Traces.TEX, mil, yc + 1, 4f, 1f, 5, SEMELLE_H, 3, Traces.SEMELLE_H,
            Traces.TEX_W, Traces.TEX_H, 0xFF000000 | Traces.SEMELLE_SOMBRE);
        cadre(ctx, cx, yc, CELLULE - 1, 17, choisi ? DaKit.OR : survole ? DaKit.OR_ETEINT : CADRE);
    }

    /* ================================================================ ligne : « Automatique », semelles et « Personnalisée » */

    /** Le réglage de la ligne, calé à droite de {@code droite}, centré sur {@code y}. */
    static AbstractWidget ligne(int droite, int y) {
        int w = LARGEUR_AUTO + Traces.PRESETS.length * CELLULE + LARGEUR_LIEN;
        return new Ligne(droite - w, y, w, LIGNE_H);
    }

    private static final class Ligne extends AbstractWidget {
        Ligne(int x, int y, int w, int hh) {
            super(x, y, w, hh, Component.literal("Couleur des traces"));
            ligneVue = this;
        }

        private int x0Presets() { return getX() + LARGEUR_AUTO; }

        private boolean surAuto(double mx, double my) {
            return mx >= getX() && mx < x0Presets() - 2 && my >= getY() && my < getY() + 19;
        }

        private int survol(int mx, int my) {
            if (my < getY() || my >= getY() + 19 || mx < x0Presets()) return -1;
            int i = (mx - x0Presets()) / CELLULE;
            return i < Traces.PRESETS.length ? i : -1;
        }

        private boolean surLien(double mx, double my) {
            int x0 = x0Presets() + Traces.PRESETS.length * CELLULE + 4;
            return mx >= x0 && mx < getX() + getWidth() && my >= getY() && my < getY() + 19;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
            var font = Minecraft.getInstance().font;
            int c = couleur();
            boolean auto = auto(), surA = isHovered() && surAuto(mx, my);
            int sur = isHovered() ? survol(mx, my) : -1;
            int yc = getY() + 1;
            pastilleAuto(ctx, getX(), yc, auto, surA);
            float wa = DaKit.inter7(ctx, font, DaKit.inter("Automatique"), getX() + CELLULE + 2, yc + 9f,
                auto || surA ? DaKit.CREME : DaKit.OR_CLAIR, 0);
            if (auto || surA) ctx.fill(getX() + CELLULE + 2, yc + 13, getX() + CELLULE + 2 + Math.round(wa), yc + 14, auto ? DaKit.OR : DaKit.CREME);
            for (int i = 0; i < Traces.PRESETS.length; i++) {
                int cx = x0Presets() + i * CELLULE;
                ctx.fill(cx, yc, cx + CELLULE - 1, yc + 17, NUIT);
                if (!auto && Traces.PRESETS[i] == c) cadre(ctx, cx, yc, CELLULE - 1, 17, DaKit.OR);
                else if (i == sur) cadre(ctx, cx, yc, CELLULE - 1, 17, DaKit.OR_ETEINT);
                else cadre(ctx, cx, yc, CELLULE - 1, 17, CADRE);
                semelle(ctx, cx + 2, yc + 1, SEMELLE_L, SEMELLE_H, Traces.PRESETS[i]);
            }
            // « Personnalisée » : petite barre de teinte (comme la roue du halo) et le lien
            int lx = x0Presets() + Traces.PRESETS.length * CELLULE + 4;
            boolean perso = !auto && !estPreset(c), surL = isHovered() && surLien(mx, my);
            if (CouleurTextures.preparer(h)) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, CouleurTextures.TEINTE, lx, yc + 7, 0f, 0f, 6, 4, CouleurTextures.TEINTE_W, 1,
                    CouleurTextures.TEINTE_W, 1);
            }
            float w = DaKit.inter7(ctx, font, DaKit.inter("Personnalisée"), lx + 9, yc + 9f,
                perso ? DaKit.CREME : surL ? DaKit.CREME : DaKit.OR_CLAIR, 0);
            if (perso || surL) ctx.fill(lx + 9, yc + 13, lx + 9 + Math.round(w), yc + 14, perso ? DaKit.OR : DaKit.CREME);
            // sous les semelles : le nom de ce qu'on survole, sinon celui de la couleur choisie
            String detail = sur >= 0 ? Traces.PRESETS_NOMS[sur] + " · #" + hexa(Traces.PRESETS[sur])
                : surA || auto ? AIDE_AUTO : nom(c) + " · #" + hexa(c);
            DaKit.inter7(ctx, font, DaKit.inter(detail), getX(), getY() + 24f, DaKit.DOUX, 0);
        }

        @Override
        public void onClick(MouseButtonEvent e, boolean dbl) {
            if (surAuto(e.x(), e.y())) {
                poserAuto(true);
                if (ouvert) {
                    ouvert = false;
                    SectionedTab.demanderRafraichir();
                }
                RebornSounds.uiClick();
                return;
            }
            int i = survol((int) e.x(), (int) e.y());
            if (i >= 0) {
                poser(Traces.PRESETS[i], true);
                if (ouvert) {
                    ouvert = false;
                    SectionedTab.demanderRafraichir();
                }
                RebornSounds.uiClick();
                return;
            }
            if (surLien(e.x(), e.y())) {
                ouvert = !ouvert;
                pick = 0;
                if (ouvert) {
                    int c = couleur();
                    // départ : la couleur du moment (celle des préréglages aussi : on la nuance). L'ivoire n'a presque pas de
                    // teinte : le sélecteur se cale sur la dernière couleur personnalisée. Ouvrir ne change rien : la couleur
                    // ne bouge qu'au premier geste, et le champ montre celle qui est en place. « Automatique » n'en a pas :
                    // curseurs, champ et aperçu partent tous de la dernière personnalisée (il reste choisi jusqu'au premier geste)
                    int[] r = RebornPrefs.INSTANCE.tracesRecentes;
                    if (auto()) {
                        depart = HaloPlaque.Palette.relever(r != null && r.length > 0 ? r[0] : 0x7FD0C8);
                        depuisCouleur(depart);
                    } else if (c == Traces.IVOIRE) {
                        depuisCouleur(r != null && r.length > 0 ? r[0] : 0x7FD0C8);
                        hexVu = "#" + hexa(c);
                        if (hex != null) hex.setValue(hexVu);
                    } else {
                        depuisCouleur(c);
                    }
                }
                SectionedTab.demanderRafraichir();
                RebornSounds.uiClick();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput n) { }
    }

    /* ================================================================ sélecteur en ligne */

    /** Le champ hexadécimal : la saisie seulement, le sélecteur le dessine en Inter. */
    static EditBox champ(int x, int y) {
        EditBox b = new EditBox(Minecraft.getInstance().font, x, y, 48, 14, Component.literal("Hex")) {
            @Override
            public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d) { }

            /** Un clic sélectionne tout : on retape un code d'un coup. */
            @Override
            public void onClick(MouseButtonEvent e, boolean dbl) {
                moveCursorToEnd(false);
                setHighlightPos(0);
            }
        };
        b.setMaxLength(7);
        b.setBordered(false);
        b.setHint(Component.empty());
        b.setValue(hexVu);
        hex = b;
        return b;
    }

    static AbstractWidget selecteur(int x, int y, int w) { return new Selecteur(x, y, w, SELECTEUR_H); }

    /** Géométrie : dans la colonne de droite, carré et teinte, aperçu à droite, hex et récentes dessous. */
    private static final class Selecteur extends AbstractWidget {
        private static final int HAUT_SV = 44, HAUT_TEINTE = 5, L_APERCU = 44;

        Selecteur(int x, int y, int w, int hh) {
            super(x, y, w, hh, Component.literal("Sélecteur de couleur"));
            selecteurVu = this;
        }

        private int x0() { return getX() + 56; }

        private int largeur() { return getWidth() - 56 - L_APERCU - 6; }

        private int yTeinte() { return getY() + HAUT_SV + 4; }

        private int yHex() { return yTeinte() + HAUT_TEINTE + 6; }

        private void syncHex() {
            if (hex == null) return;
            String t = hex.getValue().trim();
            if (t.equals(hexVu)) return;
            String nu = t.startsWith("#") ? t.substring(1) : t;
            if (!nu.matches("[0-9A-Fa-f]{6}")) return;
            int c = HaloPlaque.Palette.relever(Integer.parseInt(nu, 16));
            depuisCouleur(c);
            poser(c, true);
            retenir(c);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
            syncHex();
            var font = Minecraft.getInstance().font;
            int x0 = x0(), w = largeur(), ya = getY(), yt = yTeinte(), yh = yHex();
            int c = auto() ? depart : couleur();
            DaKit.inter7(ctx, font, DaKit.inter("Couleur"), getX(), ya + 6f, DaKit.DOUX, 0);
            DaKit.inter7(ctx, font, DaKit.inter("saturation"), getX(), ya + 15f, GRIS, 0);
            DaKit.inter7(ctx, font, DaKit.inter("× luminosité"), getX(), ya + 22f, GRIS, 0);
            DaKit.inter7(ctx, font, DaKit.inter("Teinte"), getX(), yt + 2.5f, DaKit.DOUX, 0);
            DaKit.inter7(ctx, font, DaKit.inter("Hex"), getX(), yh + 7f, DaKit.DOUX, 0);
            if (CouleurTextures.preparer(h)) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, CouleurTextures.SV, x0, ya, 0f, 0f, w, HAUT_SV, CouleurTextures.SV_W,
                    CouleurTextures.SV_H, CouleurTextures.SV_W, CouleurTextures.SV_H);
                ctx.blit(RenderPipelines.GUI_TEXTURED, CouleurTextures.TEINTE, x0, yt, 0f, 0f, w, HAUT_TEINTE, CouleurTextures.TEINTE_W,
                    1, CouleurTextures.TEINTE_W, 1);
            }
            cadre(ctx, x0 - 1, ya - 1, w + 2, HAUT_SV + 2, CADRE);
            cadre(ctx, x0 - 1, yt - 1, w + 2, HAUT_TEINTE + 2, CADRE);
            // curseurs
            int cx = x0 + Math.round(s * (w - 1));
            int cy = ya + Math.round((1 - (v - HaloPlaque.Palette.V_MIN) / (1 - HaloPlaque.Palette.V_MIN)) * (HAUT_SV - 1));
            ctx.fill(cx - 2, cy - 2, cx + 3, cy + 3, NUIT);
            ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
            ctx.fill(cx, cy, cx + 1, cy + 1, 0xFF000000 | c);
            int hx = x0 + Math.round(h * (w - 1));
            ctx.fill(hx - 1, yt - 2, hx + 2, yt + HAUT_TEINTE + 2, NUIT);
            ctx.fill(hx, yt - 1, hx + 1, yt + HAUT_TEINTE + 1, 0xFFFFFFFF);
            // aperçu : deux semelles sur fond de nuit, comme sur le sol en concentration
            int xp = x0 + w + 6;
            ctx.fill(xp, ya - 1, xp + L_APERCU, yt + HAUT_TEINTE + 1, NUIT);
            cadre(ctx, xp, ya - 1, L_APERCU, HAUT_SV + HAUT_TEINTE + 6, CADRE);
            ctx.blit(RenderPipelines.GUI_TEXTURED, Traces.TEX, xp + 8, ya + 22, 1f, 1f, 12, 20, Traces.SEMELLE_W, Traces.SEMELLE_H,
                Traces.TEX_W, Traces.TEX_H, 0xFF000000 | c);
            // pied droit : le même, en miroir (u de droite à gauche)
            ctx.blit(RenderPipelines.GUI_TEXTURED, Traces.TEX, xp + 24, ya + 8, 7f, 1f, 12, 20, -Traces.SEMELLE_W, Traces.SEMELLE_H,
                Traces.TEX_W, Traces.TEX_H, 0xFF000000 | c);
            // champ hexadécimal
            boolean focus = hex != null && hex.isFocused();
            ctx.fill(x0, yh, x0 + 48, yh + 14, NUIT);
            cadre(ctx, x0, yh, 48, 14, focus ? DaKit.OR_ETEINT : CADRE);
            String val = hex != null ? hex.getValue() : "";
            boolean curseur = focus && Util.getMillis() / 500L % 2L == 0L;
            if (focus && !hex.getHighlighted().isEmpty()) {
                ctx.fill(x0 + 3, yh + 2, x0 + 5 + Math.round(font.width(DaKit.inter(val)) * DaKit.S7), yh + 12, DaKit.argb(0xD9A95E, 0.35f));
            }
            float tw = DaKit.inter7(ctx, font, DaKit.inter(val), x0 + 4, yh + 7f, DaKit.CREME, 0);
            if (curseur) ctx.fill(x0 + 4 + Math.round(tw) + 1, yh + 3, x0 + 4 + Math.round(tw) + 2, yh + 11, DaKit.CREME);
            // récentes
            int[] rec = RebornPrefs.INSTANCE.tracesRecentes;
            if (rec != null && rec.length > 0) {
                float x = x0 + 56;
                x += DaKit.inter7(ctx, font, DaKit.inter("récentes"), x, yh + 7f, GRIS, 0) + 5;
                for (int i = 0; i < rec.length && i < 4; i++) {
                    int rx = Math.round(x) + i * 11;
                    ctx.fill(rx, yh + 1, rx + 10, yh + 13, NUIT);
                    cadre(ctx, rx, yh + 1, 10, 12, rec[i] == c ? DaKit.OR : CADRE);
                    semelle(ctx, rx + 2, yh + 2, 6, 10, rec[i]);
                }
            }
            DaKit.inter7(ctx, font, DaKit.inter("Une couleur choisie ne peut pas être sombre : le carré s'arrête à 55 %."), getX(), yh + 20f, GRIS, 0);
        }

        private int recente(double mx, double my) {
            int[] rec = RebornPrefs.INSTANCE.tracesRecentes;
            if (rec == null || rec.length == 0) return -1;
            int yh = yHex();
            if (my < yh || my >= yh + 14) return -1;
            var font = Minecraft.getInstance().font;
            float x = x0() + 56 + font.width(DaKit.inter("récentes")) * DaKit.S7 + 5;
            int i = (int) Math.floor((mx - Math.round(x)) / 11.0);
            return i >= 0 && i < rec.length && i < 4 ? i : -1;
        }

        @Override
        public void onClick(MouseButtonEvent e, boolean dbl) {
            double mx = e.x(), my = e.y();
            int x0 = x0(), w = largeur(), ya = getY(), yt = yTeinte();
            if (mx >= x0 && mx < x0 + w && my >= ya && my < ya + HAUT_SV) pick = 1;
            else if (mx >= x0 && mx < x0 + w && my >= yt - 2 && my < yt + HAUT_TEINTE + 2) pick = 2;
            else {
                int r = recente(mx, my);
                if (r >= 0) {
                    int c = RebornPrefs.INSTANCE.tracesRecentes[r];
                    depuisCouleur(c);
                    poser(c, true);
                    RebornSounds.uiClick();
                }
                return;
            }
            maj(mx, my);
        }

        @Override
        protected void onDrag(MouseButtonEvent e, double dx, double dy) { maj(e.x(), e.y()); }

        @Override
        public void onRelease(MouseButtonEvent e) {
            if (pick != 0) {
                pick = 0;
                int c = couleur();
                retenir(c);
                poser(c, true);
            }
        }

        private void maj(double mx, double my) {
            int w = largeur();
            if (pick == 1) {
                s = clamp01((float) ((mx - x0()) / Math.max(1, w - 1)));
                float t = clamp01((float) ((my - getY()) / (HAUT_SV - 1f)));
                v = 1f - t * (1f - HaloPlaque.Palette.V_MIN);
            } else if (pick == 2) {
                h = Math.min(0.9999f, clamp01((float) ((mx - x0()) / Math.max(1, w - 1))));
            } else return;
            int c = SkinSpec.hsvToArgb(h, s, v) & 0xFFFFFF;
            poser(c, false);
            hexVu = "#" + hexa(c);
            if (hex != null) hex.setValue(hexVu);
        }

        private static float clamp01(float f) { return Math.max(0f, Math.min(1f, f)); }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput n) { }
    }

    /* ================================================================ banc */

    /** Point du clic (GUI) : -1 = « Automatique », pastille {@code i}, 7 = lien « Personnalisée », 8 = milieu du carré, 9 = champ hexadécimal. */
    static int[] point(int i) {
        if (i < 0) return new int[] {ligneVue.getX() + 6, ligneVue.getY() + 9};
        if (i < Traces.PRESETS.length) return new int[] {ligneVue.x0Presets() + i * CELLULE + 6, ligneVue.getY() + 9};
        if (i == 7) return new int[] {ligneVue.x0Presets() + Traces.PRESETS.length * CELLULE + 20, ligneVue.getY() + 9};
        if (i == 8) return new int[] {selecteurVu.x0() + selecteurVu.largeur() / 2, selecteurVu.getY() + 20};
        return new int[] {hex.getX() + 10, hex.getY() + 6};
    }

    /** Banc : ce que le sélecteur montre : {champ hexadécimal (RVB), aperçu, couleur des curseurs}. */
    static int[] montre() {
        String t = hex != null ? hex.getValue() : hexVu;
        return new int[] {Integer.parseInt(t.substring(1), 16), auto() ? depart : couleur(), SkinSpec.hsvToArgb(h, s, v) & 0xFFFFFF};
    }

    /** Banc : « Automatique », sélecteur fermé, sans écrire. */
    static void debugAuto() {
        poserAuto(false);
        ouvert = false;
        pick = 0;
        SectionedTab.demanderRafraichir();
    }

    /** Banc : impose la couleur et l'état du sélecteur (la mise en page suit au prochain tick). */
    public static void debug(int c, boolean ouvrir) {
        poser(c, false);
        ouvert = ouvrir;
        pick = 0;
        depuisCouleur(c);
        SectionedTab.demanderRafraichir();
    }
}
