package fr.reborn.hud.menu.cosmetic;

import fr.reborn.hud.cosmetic.CosmeticFeatureRenderer;
import fr.reborn.hud.cosmetic.CosmeticTransform;
import fr.reborn.hud.cosmetic.RepositionMode;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.character.perso.PersoCouleur;
import fr.reborn.hud.menu.inventory.CosmeticSlot;
import fr.reborn.hud.menu.inventory.InventoryData;
import fr.reborn.hud.menu.inventory.InventoryPayload;
import fr.reborn.hud.menu.inventory.InventoryScreen;
import fr.reborn.hud.menu.inventory.SlotItem;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * <b>Teinture d'un cosmétique porté</b> (18/10 : le bandeau) — sacoche › clic droit sur le bandeau porté ›
 * « Teinture… ». Le joueur reste debout dans le vrai monde, caméra en orbite de face (même mode que « Repositionner » :
 * {@link RepositionMode}) ; le tissu prend la couleur choisie <b>en direct</b> sur le personnage
 * ({@link CosmeticFeatureRenderer#APERCU_TEINTE}, local, jamais diffusé), la plaque gravée garde la sienne.
 *
 * <p>Panneau laqué sobre à gauche : couleurs canon (nuit d'origine, bleu marine, gris, blanc, rouge, vert, violet,
 * sable), couleur libre au sélecteur de la création de personnage ({@link PersoCouleur}, réutilisé tel quel),
 * « Origine », « Annuler », « Appliquer ». Appliquer envoie {@code cos:dye:<SLOT>:<RRGGBB|origine>} sur
 * {@code reborn:inventory} : ShinobiCore réécrit le composant {@code dyed_color} de l'objet, le persiste et le rediffuse
 * à tous ({@code reborn:cosmetics}). Libre : aucun coût ni teinture à posséder.
 */
public class TeintureScreen extends Screen {

    /**
     * Couleurs canon : la première est la couleur d'origine du bandeau de l'utilisateur ({@code bandeau_ninja},
     * {@code color: "#302C32"} du YAML Nexo, valeur donnée par {@code tools/teinture_bandeau_nexo.py} : sans teinte
     * choisie, le tissu presque noir rend exactement comme avant). Garder les deux identiques.
     */
    public static final int[] CANON = {0x302C32, 0x3A5288, 0x7A7E88, 0xE8E4DA, 0x9E2A2E, 0x3E6E46, 0x5A447E, 0xC29A5E};
    public static final String[] CANON_NOMS = {"Nuit", "Bleu marine", "Gris", "Blanc", "Rouge", "Vert", "Violet", "Sable"};

    private static final int PW = 212, PH = 196, SW = 18, PAS = 23;

    private final CosmeticSlot slot;
    private final SlotItem item;
    private final String nom;
    /** Couleur au départ (RGB, ou {@link CosmeticFeatureRenderer#ORIGINE}). */
    private final int initial;
    /** Couleur montrée (RGB, ou {@link CosmeticFeatureRenderer#ORIGINE}). */
    private int choix;
    /** Dernière couleur libre (pipette), −1 = aucune. */
    private int libre = -1;
    private PersoCouleur picker;
    private boolean orbite;
    private int px, py;
    private final int[][] boutons = new int[3][4];
    private static final String[] B_LIB = {"ORIGINE", "ANNULER", "APPLIQUER"};

    public TeintureScreen(CosmeticSlot slot, SlotItem item) {
        super(Component.literal("Teinture"));
        this.slot = slot;
        this.item = item;
        this.nom = item != null && item.name != null ? item.name : (slot != null ? slot.label : "Cosmétique");
        this.initial = item != null && item.dye >= 0 ? item.dye : CosmeticFeatureRenderer.ORIGINE;
        this.choix = initial;
        if (initial >= 0 && canon(initial) < 0) libre = initial;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!RepositionMode.INSTANCE.isActive()) {
            String id = CosmeticFeatureRenderer.cosmeticId(slot, item);
            RepositionMode.INSTANCE.begin(mc, id, nom, CosmeticTransform.Anchor.HEAD);
            RepositionMode.INSTANCE.zoom(4);                     // plus près : la tête
        }
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        apercu();
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        CosmeticFeatureRenderer.APERCU_TEINTE.remove(slot);
        RepositionMode.INSTANCE.end(mc);
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /* ================================================================ état */

    private static int canon(int rgb) {
        for (int i = 0; i < CANON.length; i++) if (CANON[i] == rgb) return i;
        return -1;
    }

    /** La couleur réellement vue (origine = la première couleur canon). */
    private int vue() { return choix == CosmeticFeatureRenderer.ORIGINE ? CANON[0] : choix; }

    private void choisir(int rgb) {
        choix = rgb;
        apercu();
    }

    private void apercu() {
        if (slot != null) CosmeticFeatureRenderer.APERCU_TEINTE.put(slot, choix);
    }

    private void appliquer() {
        Minecraft mc = Minecraft.getInstance();
        if (choix != initial && slot != null && item != null) {
            String v = choix == CosmeticFeatureRenderer.ORIGINE ? "origine" : String.format(Locale.ROOT, "%06X", choix);
            if (ClientPlayNetworking.canSend(InventoryPayload.ID)) {
                ClientPlayNetworking.send(new InventoryPayload("cos:dye:" + slot.name() + ":" + v));
            }
            if (debugEnvoi != null) debugEnvoi.accept("cos:dye:" + slot.name() + ":" + v);
            // MAJ optimiste (le serveur repousse la sacoche juste après)
            InventoryData.remplacerPorte(slot, item.avecTeinte(choix == CosmeticFeatureRenderer.ORIGINE ? -1 : choix));
            RebornSounds.playReborn("sacoche.place", 1.0f, 0.6f);
        }
        retour(mc);
    }

    private void retour(Minecraft mc) {
        CosmeticFeatureRenderer.APERCU_TEINTE.remove(slot);
        mc.setScreenAndShow(new InventoryScreen());
    }

    /** Banc d'essai : reçoit la commande qui serait envoyée au serveur (null en jeu). */
    public static java.util.function.Consumer<String> debugEnvoi;

    /* ================================================================ rendu */

    private void layout() {
        px = 20;
        py = Math.max(12, (this.height - PH) / 2 - 10);
        int bw = (PW - 24 - 10) / 3, by = py + PH - 30;
        for (int i = 0; i < 3; i++) boutons[i] = new int[]{px + 12 + i * (bw + 5), by, bw, 16};
        if (picker != null) {
            picker.x = Math.min(this.width - PersoCouleur.W - 8, px + PW + 12);
            picker.y = Math.max(8, Math.min(this.height - PersoCouleur.H - 8, py + 96));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // monde net derrière, léger voile à gauche pour lire le panneau
        ctx.fillGradient(0, 0, this.width, this.height, DaKit.argb(0x0A0709, 0.06f), DaKit.argb(0x0A0709, 0.18f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        layout();
        Font f = this.font;
        panneau(ctx, px, py, PW, PH);
        DaKit.arcX1(ctx, f, DaKit.arcade("TEINTURE"), px + 12, py + 11, DaKit.CREME, 0, true);
        DaKit.inter9o(ctx, f, DaKit.inter(nom), px + 12, py + 30, DaKit.CREME_VAL, 0);
        DaKit.inter8(ctx, f, DaKit.inter("Le tissu prend la couleur, la plaque reste gravée."), px + 12, py + 42,
            DaKit.argb(0xE8DCC0, 0.62f), 0);
        ctx.fill(px + 12, py + 50, px + PW - 12, py + 51, DaKit.SEP);

        // couleurs canon
        DaKit.etiq(ctx, f, DaKit.etiq(f, "Couleurs"), px + 12, py + 60, DaKit.OR_ETIQ);
        int survol = -1;
        LotGui l = LotGui.ouvrir(ctx);
        for (int i = 0; i < CANON.length; i++) {
            int x = px + 13 + i * PAS, y = py + 68;
            boolean sel = choix == CANON[i] || (i == 0 && choix == CosmeticFeatureRenderer.ORIGINE);
            boolean sur = DaKit.dans(mx, my, x - 2, y - 2, SW + 4, SW + 4);
            if (sur) survol = i;
            pastille(l, x, y, CANON[i], sel, sur);
        }
        l.fermer();
        int ic = canon(vue());
        String nomCouleur = survol >= 0 ? CANON_NOMS[survol] + (survol == 0 ? " · d'origine" : "")
            : ic >= 0 ? CANON_NOMS[ic] + (ic == 0 ? " · d'origine" : "") : "Couleur libre";
        DaKit.inter8(ctx, f, DaKit.inter(nomCouleur), px + 12, py + 99, DaKit.CREME, 0);
        DaKit.inter8(ctx, f, DaKit.inter(String.format(Locale.ROOT, "#%06X", survol >= 0 ? CANON[survol] : vue())),
            px + PW - 12, py + 99, DaKit.argb(0xE8DCC0, 0.55f), 2);

        // couleur libre (sélecteur de la création de personnage)
        ctx.fill(px + 12, py + 108, px + PW - 12, py + 109, DaKit.SEP);
        DaKit.etiq(ctx, f, DaKit.etiq(f, "Couleur libre"), px + 12, py + 118, DaKit.OR_ETIQ);
        int lx = px + 13, ly = py + 126;
        boolean libreSel = canon(vue()) < 0;
        boolean libreSur = DaKit.dans(mx, my, lx - 2, ly - 2, SW + 4, SW + 4);
        LotGui p = LotGui.ouvrir(ctx);
        pipette(p, lx, ly, libre, libreSel, libreSur || picker != null);
        p.fermer();
        DaKit.inter8(ctx, f, DaKit.inter(picker != null ? "Choisis la teinte à droite" : "Pipette : n'importe quelle teinte"),
            lx + SW + 9, ly + SW / 2f, libreSur ? DaKit.CREME : DaKit.argb(0xE8DCC0, 0.7f), 0);

        // boutons
        for (int i = 0; i < 3; i++) {
            int[] b = boutons[i];
            boolean sur = DaKit.dans(mx, my, b[0], b[1], b[2], b[3]);
            boolean actif = i != 0 || choix != CosmeticFeatureRenderer.ORIGINE;
            bouton(ctx, f, b, B_LIB[i], i == 2, sur && actif, actif);
        }

        if (picker != null) picker.dessiner(ctx, f, mx, my, System.currentTimeMillis());

        DaKit.aides(ctx, f, fr.reborn.hud.menu.settings.ReglagesArt.PIECES, 0, 0, 120, 40, new String[][]{
            {"Glisser", "tourner autour"}, {"Molette", "zoom"}, {"Entrée", "appliquer"}, {"Échap", "annuler"}},
            this.width - 16, this.height - 18, 1f);
    }

    /** Panneau laqué (même kit que le sélecteur de couleur) : voile, double filet or, losanges. */
    private static void panneau(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        LotGui l = LotGui.ouvrir(g);
        l.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xB0000000);
        l.fill(x, y, x + w, y + h, DaKit.R_OR[3]);
        l.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xF0140B0D);
        l.fill(x + 3, y + 3, x + w - 3, y + h - 3, DaKit.R_OR[1]);
        l.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xF01A0F12);
        for (int[] c : new int[][]{{x + 2, y + 2}, {x + w - 5, y + 2}, {x + 2, y + h - 5}, {x + w - 5, y + h - 5}}) {
            l.fill(c[0] + 1, c[1], c[0] + 2, c[1] + 3, DaKit.R_OR[5]);
            l.fill(c[0], c[1] + 1, c[0] + 3, c[1] + 2, DaKit.R_OR[5]);
        }
        l.fermer();
    }

    private static int opaque(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Pastille carrée (cadre or, reflet) ; choisie = double cadre or vif + pointe dessous. */
    private static void pastille(LotGui g, int x, int y, int c, boolean sel, boolean sur) {
        if (sel) {
            g.fill(x - 3, y - 3, x + SW + 3, y + SW + 3, DaKit.R_OR[6]);
            g.fill(x - 2, y - 2, x + SW + 2, y + SW + 2, 0xFF120A0C);
            g.fill(x + SW / 2 - 2, y + SW + 4, x + SW / 2 + 3, y + SW + 5, DaKit.R_OR[6]);
            g.fill(x + SW / 2 - 1, y + SW + 5, x + SW / 2 + 2, y + SW + 6, DaKit.R_OR[6]);
            g.fill(x + SW / 2, y + SW + 6, x + SW / 2 + 1, y + SW + 7, DaKit.R_OR[6]);
        }
        g.fill(x - 1, y - 1, x + SW + 1, y + SW + 1, sel || sur ? DaKit.R_OR[5] : DaKit.R_OR[3]);
        g.fill(x, y, x + SW, y + SW, opaque(c));
        g.fill(x, y, x + SW, y + 1, opaque(mix(c, 0xFFFFFF, 0.35f)));
        g.fill(x, y + SW - 1, x + SW, y + SW, opaque(mix(c, 0x000000, 0.3f)));
    }

    /** Pastille « pipette » : la couleur libre choisie, sinon un petit nuancier en quartiers, marquée d'un « + ». */
    private static void pipette(LotGui g, int x, int y, int c, boolean sel, boolean sur) {
        if (sel) {
            g.fill(x - 3, y - 3, x + SW + 3, y + SW + 3, DaKit.R_OR[6]);
            g.fill(x - 2, y - 2, x + SW + 2, y + SW + 2, 0xFF120A0C);
        }
        g.fill(x - 1, y - 1, x + SW + 1, y + SW + 1, sel || sur ? DaKit.R_OR[5] : DaKit.R_OR[3]);
        if (c >= 0) {
            g.fill(x, y, x + SW, y + SW, opaque(c));
        } else {
            int m = SW / 2;
            g.fill(x, y, x + m, y + m, 0xFFC23B3B);
            g.fill(x + m, y, x + SW, y + m, 0xFFE8D27A);
            g.fill(x, y + m, x + m, y + SW, 0xFF3A6EA5);
            g.fill(x + m, y + m, x + SW, y + SW, 0xFF4E8A4A);
        }
        int cx = x + SW / 2, cy = y + SW / 2;
        g.fill(cx - 3, cy - 1, cx + 4, cy + 2, 0xD0120A0C);
        g.fill(cx - 1, cy - 3, cx + 2, cy + 4, 0xD0120A0C);
        g.fill(cx - 2, cy, cx + 3, cy + 1, 0xFFF5E9D0);
        g.fill(cx, cy - 2, cx + 1, cy + 3, 0xFFF5E9D0);
    }

    /** Bouton laqué : fond, filet or (or vif au survol ; laque rouge pour l'action principale), libellé ArcadePix fin. */
    private static void bouton(GuiGraphicsExtractor g, Font f, int[] b, String lib, boolean principal, boolean sur,
                               boolean actif) {
        int x = b[0], y = b[1], w = b[2], h = b[3];
        int cadre = !actif ? DaKit.R_OR[2] : sur ? DaKit.R_OR[6] : DaKit.R_OR[4];
        int fond = principal ? (sur ? 0xFF8E1628 : 0xFF6A1020) : (sur ? 0xFF2A1A1E : 0xFF1E1215);
        LotGui l = LotGui.ouvrir(g);
        l.fill(x, y, x + w, y + h, cadre);
        l.fill(x + 1, y + 1, x + w - 1, y + h - 1, fond);
        l.fill(x + 1, y + 1, x + w - 1, y + 2, DaKit.argb(0xFFFFFF, actif ? 0.10f : 0.04f));
        l.fermer();
        DaKit.arcFin(g, f, DaKit.arcade(lib), x + w / 2f, y + 5.25f,
            !actif ? DaKit.INACTIF : sur ? DaKit.CREME : 0xFFD8CCB0, 1, true);
    }

    /* ================================================================ saisie */

    private boolean surPanneau(double mx, double my) {
        return DaKit.dans(mx, my, px - 2, py - 2, PW + 4, PH + 4)
            || (picker != null && DaKit.dans(mx, my, picker.x - 2, picker.y - 2, PersoCouleur.W + 4, PersoCouleur.H + 4));
    }

    private void ouvrirLibre() {
        int depart = libre >= 0 ? libre : vue();
        picker = new PersoCouleur("COULEUR LIBRE - BANDEAU", 0xFF000000 | depart, rgb -> {
            libre = rgb & 0xFFFFFF;
            choisir(libre);
        });
        layout();
    }

    private void fermerLibre(boolean garder) {
        if (picker == null) return;
        if (!garder) picker.annuler();
        picker = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        layout();
        double mx = e.x(), my = e.y();
        if (e.button() != 0) return super.mouseClicked(e, dbl);
        if (picker != null) {
            boolean[] ok = {false};
            if (picker.clic(mx, my, ok)) {
                if (ok[0]) fermerLibre(true);
                return true;
            }
            if (!DaKit.dans(mx, my, px, py, PW, PH)) { fermerLibre(true); return true; }
        }
        for (int i = 0; i < CANON.length; i++) {
            int x = px + 13 + i * PAS, y = py + 68;
            if (DaKit.dans(mx, my, x - 2, y - 2, SW + 4, SW + 4)) {
                fermerLibre(true);
                choisir(i == 0 && initial == CosmeticFeatureRenderer.ORIGINE ? CosmeticFeatureRenderer.ORIGINE : CANON[i]);
                RebornSounds.playReborn("sacoche.pick", 1.2f, 0.4f);
                return true;
            }
        }
        if (DaKit.dans(mx, my, px + 11, py + 124, SW + 4 + 150, SW + 4)) {
            if (picker == null) ouvrirLibre(); else fermerLibre(true);
            RebornSounds.playReborn("sacoche.pick", 1.2f, 0.4f);
            return true;
        }
        for (int i = 0; i < 3; i++) {
            int[] b = boutons[i];
            if (!DaKit.dans(mx, my, b[0], b[1], b[2], b[3])) continue;
            Minecraft mc = Minecraft.getInstance();
            switch (i) {
                case 0 -> { if (choix != CosmeticFeatureRenderer.ORIGINE) { fermerLibre(true); choisir(CosmeticFeatureRenderer.ORIGINE); } }
                case 1 -> retour(mc);
                default -> appliquer();
            }
            return true;
        }
        if (!surPanneau(mx, my)) { orbite = true; return true; }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (picker != null && picker.glisser(e.x(), e.y())) return true;
        if (orbite) { RepositionMode.INSTANCE.orbit(dx, dy); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (picker != null && picker.relacher()) return true;
        if (orbite) { orbite = false; return true; }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (!surPanneau(mx, my)) { RepositionMode.INSTANCE.zoom(v); return true; }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (picker != null) {
            if (k == GLFW.GLFW_KEY_ESCAPE) fermerLibre(false);
            else if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) fermerLibre(true);
            else if (k == GLFW.GLFW_KEY_BACKSPACE) picker.effacer();
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) { retour(Minecraft.getInstance()); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { appliquer(); return true; }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (picker != null && e.isAllowedChatCharacter()) {
            String c = e.codepointAsString();
            if (!c.isEmpty()) picker.car(c.charAt(0));
            return true;
        }
        return super.charTyped(e);
    }

    /* ================================================================ banc */

    /** Banc : choisit une couleur (RGB ou {@link CosmeticFeatureRenderer#ORIGINE}) comme un clic. */
    public void bancChoisir(int rgb) { fermerLibre(true); choisir(rgb); }

    /** Banc : ouvre le sélecteur de couleur libre, avec une teinte tapée ({@code RRGGBB}) si non nulle. */
    public void bancLibre(String hex) {
        ouvrirLibre();
        if (hex != null) for (char c : hex.toCharArray()) picker.car(c);
    }

    /** Banc : applique (comme le bouton). */
    public void bancAppliquer() { appliquer(); }
}
