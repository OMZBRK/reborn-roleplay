package fr.reborn.hud.crosshair;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.settings.ReglagesArt;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Éditeur de viseur, au niveau de la fiche shinobi (DA §4.1, §6), version épurée. Signature : le <b>mato de kyūdō</b>
 * (cible de papier sur la butte de sable, sous l'auvent du pas de tir) — l'aperçu du viseur est dessiné en direct en
 * son centre. À droite, la planche laquée des réglages avec les contrôles des Paramètres ({@link ReglagesArt}) et la
 * rangée des quinze modèles. Le jeu reste visible derrière, sous un voile.
 *
 * <p>Décor fixe : {@code textures/gui/viseur/*} ({@code tools/gen_viseur_textures.py}). Mise en page calculée à
 * l'init ; chaque réglage est enregistré immédiatement. Sons : flèche fichée à l'ouverture, corde d'arc au choix
 * d'un modèle, clic de menu pour le reste. Ouvert par la touche J et depuis Paramètres › Interface.
 */
public class CrosshairScreen extends Screen {

    private static Identifier tex(String n) { return DaKit.tex("viseur", n); }

    private static final Identifier ENTETE = tex("entete"), MATO = tex("mato"), PLANCHE = tex("planche"),
        CELLULES = tex("cellules"), PIECES = tex("pieces");

    /** Bloc : cible 200 + 10 + planche 420, hauteur 300 ; en-tête suspendu au-dessus. */
    private static final int MW = 200, GAP = 10, PW = 420, H = 300, BLOC_W = MW + GAP + PW;
    private static final int COL_W = 186, ROW = 22, SEG_H = 16, CELL = 22, CELL_PAS = 26;

    private static final int C_WHITE = 0xFFFFFFFF, C_RED = 0xFFEF4444, C_GOLD = 0xFFD9A95E, C_GREEN = 0xFF4ADE80,
        C_CYAN = 0xFF38BDF8;
    private static final int[] COULEURS = {C_WHITE, C_RED, C_GOLD, C_GREEN, C_CYAN};
    private static final String[] STYLES = {"preset", "cross", "dot", "circle"};
    private static final String[] STYLES_LIB = {"Modèles", "Croix", "Point", "Cercle"};
    private static final String[] CURSEURS_LIB = {"Défaut", "Curseur 1", "Curseur 2"};

    private final Screen parent;

    private sealed interface Ligne permits Bascule, Choix, Nuancier, Reglette {}

    private record Bascule(String label, String aide, int x, int ym, Supplier<Boolean> get, Consumer<Boolean> set)
        implements Ligne {}

    /** Libellé sur sa ligne, plaques segmentées sur la ligne du dessous (y = ym + 9). */
    private record Choix(String label, String aide, int x, int ym, String[] libs, IntSupplier get, IntConsumer set)
        implements Ligne {}

    private record Nuancier(String label, String aide, int x, int ym) implements Ligne {}

    private record Reglette(String label, String aide, int x, int ym, int min, int max, String unite, IntSupplier get,
                            IntConsumer set) implements Ligne {}

    private final List<Ligne> lignes = new ArrayList<>();
    private int bx, by, px, py, gridX, gridY;
    private Reglette glisse;
    private String aide = "";

    public CrosshairScreen(Screen parent) {
        super(Component.literal("Éditeur de viseur"));
        this.parent = parent;
        RebornPrefs.INSTANCE.ensureLoaded();
    }

    // ═════════════════════════════════════════════════════════════ mise en page

    @Override
    protected void init() {
        bx = (this.width - BLOC_W) / 2;
        by = Math.max(24, (this.height - H) / 2 + 6);
        px = bx + MW + GAP;
        py = by;
        lignes.clear();
        RebornPrefs p = RebornPrefs.INSTANCE;

        int xa = px + 14, xb = px + 220;
        int y = py + 38;
        lignes.add(new Choix("Style", "Modèles dessinés ou formes tracées", xa, y + 4, STYLES_LIB,
            () -> Math.max(0, java.util.Arrays.asList(STYLES).indexOf(p.crosshairStyle)),
            v -> p.crosshairStyle = STYLES[v]));
        y += 38;
        lignes.add(new Reglette("Échelle", "Taille du viseur", xa, y + 7, 50, 200, " %",
            () -> p.crosshairScale, v -> p.crosshairScale = v));
        y += ROW;
        lignes.add(new Reglette("Espacement", "Écart au centre (croix, cercle)", xa, y + 7, 0, 20, " px",
            () -> p.crosshairGap, v -> p.crosshairGap = v));
        y += ROW;
        lignes.add(new Reglette("Longueur", "Branches ou rayon (formes tracées)", xa, y + 7, 0, 20, " px",
            () -> p.crosshairLength, v -> p.crosshairLength = v));
        y += ROW;
        lignes.add(new Reglette("Épaisseur", "Trait des formes tracées", xa, y + 7, 1, 8, " px",
            () -> p.crosshairThickness, v -> p.crosshairThickness = v));

        y = py + 38;
        lignes.add(new Bascule("Viseur Reborn", "Remplace le viseur vanilla en vue à la première personne", xb, y + 7,
            () -> p.crosshairEnabled, b -> p.crosshairEnabled = b));
        y += ROW;
        lignes.add(new Nuancier("Couleur", "Teinte du viseur", xb, y + 7));
        y += ROW;
        lignes.add(new Bascule("Arc-en-ciel", "Cycle de teinte — ignore la couleur", xb, y + 7,
            () -> p.crosshairRainbow, b -> p.crosshairRainbow = b));
        y += ROW;
        lignes.add(new Bascule("Dynamique", "S'écarte selon la recharge de l'attaque", xb, y + 7,
            () -> p.crosshairDynamic, b -> p.crosshairDynamic = b));
        y += ROW;
        lignes.add(new Bascule("Marque d'impact", "Croix quand tu touches une cible", xb, y + 7,
            () -> p.crosshairHitMarker, b -> p.crosshairHitMarker = b));
        y += ROW;
        var cfg = RebornHudClient.config();
        lignes.add(new Choix("Curseur d'interaction", "Curseur du menu d'interaction (R)", xb, y + 7, CURSEURS_LIB,
            () -> Math.max(0, Math.min(2, cfg.getInteractionCursor())), v -> { cfg.setInteractionCursor(v); cfg.save(); }));

        gridX = px + (PW - (CrosshairManager.PRESET_COUNT * CELL_PAS - 4)) / 2;
        gridY = py + 206;
    }

    // ═════════════════════════════════════════════════════════════ rendu

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (Minecraft.getInstance().level == null) ctx.fill(0, 0, this.width, this.height, 0xFF0A0608);
        else ctx.fill(0, 0, this.width, this.height, DaKit.argb(0x08040A, 0.38f));
        DaKit.etire(ctx, ReglagesArt.VIGNETTE, 0, 0, this.width, this.height, 160, 90, DaKit.blanc(0.8f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Font f = this.font;
        // la cible et l'aperçu en direct
        DaKit.blit(ctx, MATO, bx - 2, by - 2, 0, 0, 208, 308, 208, 308);
        CrosshairManager.drawPreview(ctx, bx + 100, by + 132, 2.0f);
        DaKit.arcFin(ctx, f, DaKit.arcade("Aperçu en direct"), bx + MW / 2f, by + 284, DaKit.OR_ETIQ, 1, false);

        // planche des réglages, cartouche du titre à cheval sur son bord haut
        DaKit.blit(ctx, PLANCHE, px - 2, py - 2, 0, 0, 428, 308, 428, 308);
        int ex = px + PW / 2 - 80, ey = py - 20;
        DaKit.blit(ctx, ENTETE, ex, ey, 0, 0, 160, 44, 160, 44);
        DaKit.arcX1(ctx, f, DaKit.arcade("Viseur"), ex + 80, ey + 16, DaKit.OR_CLAIR, 1, true);
        titre(ctx, f, "Forme", px + 14, py + 20);
        titre(ctx, f, "Couleur et effets", px + 220, py + 20);

        aide = "";
        for (Ligne l : lignes) dessiner(ctx, f, l, mouseX, mouseY);

        // modèles
        titre(ctx, f, "Modèles", px + 14, gridY - 18);
        int sel = RebornPrefs.INSTANCE.crosshairPreset;
        boolean presets = "preset".equals(RebornPrefs.INSTANCE.crosshairStyle);
        LotBlit lc = LotBlit.ouvrir(ctx, CELLULES, 78, 26);
        for (int i = 0; i < CrosshairManager.PRESET_COUNT; i++) {
            int cx = gridX + i * CELL_PAS;
            boolean hov = DaKit.dans(mouseX, mouseY, cx, gridY, CELL, CELL);
            int etat = i == sel ? 2 : hov ? 1 : 0;
            lc.blit(cx - 1, gridY - 1, etat * 26, 0, 26, 26);
            if (hov) aide = "Modèle " + (i + 1) + (presets ? "" : " — choisis le style « Modèles » pour l'utiliser");
        }
        lc.fermer();
        for (int i = 0; i < CrosshairManager.PRESET_COUNT; i++) {
            int cx = gridX + i * CELL_PAS;
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx + CELL / 2f, gridY + CELL / 2f);
            ctx.pose().scale(16f / 33f, 16f / 33f);
            ctx.blit(RenderPipelines.GUI_TEXTURED, CrosshairManager.preset(i), -16, -16, 0f, 0f, 33, 33, 33, 33,
                presets || i == sel ? 0xFFFFFFFF : 0x99FFFFFF);
            ctx.pose().popMatrix();
        }

        // aide contextuelle, boutons
        int ay = py + 246;
        if (!aide.isEmpty()) DaKit.inter7(ctx, f, DaKit.inter(aide), px + PW / 2f, ay, DaKit.argb(0xC2B59A, 0.85f), 1);
        int b1 = resetX(), b2 = fermerX(), bby = boutonsY();
        boolean h1 = DaKit.dans(mouseX, mouseY, b1, bby, BTN_W, 20), h2 = DaKit.dans(mouseX, mouseY, b2, bby, BTN_W, 20);
        ReglagesArt.bouton(ctx, b1, bby, BTN_W, h1 ? 1 : 0);
        ReglagesArt.bouton(ctx, b2, bby, BTN_W, 3);
        DaKit.inter9(ctx, f, DaKit.inter("Réinitialiser"), b1 + BTN_W / 2f, bby + 10, h1 ? DaKit.CREME : DaKit.CREME_VAL, 1);
        DaKit.inter9(ctx, f, DaKit.inter("Fermer"), b2 + BTN_W / 2f, bby + 10, h2 ? DaKit.CREME : DaKit.CREME_VAL, 1);
    }

    private static final int BTN_W = 110;

    private int resetX() { return px + PW / 2 - BTN_W - 6; }

    private int fermerX() { return px + PW / 2 + 6; }

    private int boutonsY() { return py + H - 34; }

    private void titre(GuiGraphicsExtractor ctx, Font f, String t, int x, int y) {
        int w = DaKit.arcX1(ctx, f, DaKit.arcade(t), x, y, DaKit.OR_CLAIR, 0, true);
        ReglagesArt.filet(ctx, x + w + 8, x + COL_W, y + 3);
    }

    private void dessiner(GuiGraphicsExtractor ctx, Font f, Ligne l, int mx, int my) {
        switch (l) {
            case Bascule b -> {
                boolean hov = DaKit.dans(mx, my, b.x, b.ym - 10, COL_W, ROW - 1);
                if (hov) aide = b.aide;
                DaKit.inter9(ctx, f, DaKit.inter(b.label), b.x, b.ym, hov ? DaKit.CREME : DaKit.CREME_VAL, 0);
                ReglagesArt.interrupteur(ctx, b.x + COL_W - 40, b.ym - 7, b.get.get(), hov);
            }
            case Choix c -> {
                int cur = c.get.getAsInt();
                boolean hovL = DaKit.dans(mx, my, c.x, c.ym - 8, COL_W, 36);
                if (hovL) aide = c.aide;
                DaKit.inter9(ctx, f, DaKit.inter(c.label), c.x, c.ym, hovL ? DaKit.CREME : DaKit.CREME_VAL, 0);
                int sw = segW(c);
                for (int i = 0; i < c.libs.length; i++) {
                    int sx = c.x + i * sw, sy = c.ym + 9;
                    boolean actif = i == cur, hov = DaKit.dans(mx, my, sx, sy, sw - 2, SEG_H);
                    ReglagesArt.segment(ctx, sx, sy, sw - 2, actif ? 2 : hov ? 1 : 0);
                    int col = actif ? DaKit.CREME : hov ? DaKit.CREME_VAL : DaKit.NEUTRE_TXT;
                    DaKit.inter8(ctx, f, DaKit.inter(c.libs[i]), sx + (sw - 2) / 2f, sy + 8, col, 1);
                }
            }
            case Nuancier n -> {
                boolean hovL = DaKit.dans(mx, my, n.x, n.ym - 10, COL_W, ROW - 1);
                if (hovL) aide = RebornPrefs.INSTANCE.crosshairRainbow ? "Arc-en-ciel actif : la couleur est ignorée" : n.aide;
                DaKit.inter9(ctx, f, DaKit.inter(n.label), n.x, n.ym, hovL ? DaKit.CREME : DaKit.CREME_VAL, 0);
                int cur = RebornPrefs.INSTANCE.crosshairColor;
                LotBlit lb = LotBlit.ouvrir(ctx, PIECES, 64, 48);
                LotGui lg = LotGui.ouvrir(ctx);
                for (int i = 0; i < COULEURS.length; i++) {
                    int sx = pastilleX(n, i), sy = n.ym - 7;
                    boolean choisie = !RebornPrefs.INSTANCE.crosshairRainbow && COULEURS[i] == cur;
                    boolean hov = DaKit.dans(mx, my, sx, sy, 15, 15);
                    lb.blit(sx, sy, (choisie ? 2 : hov ? 1 : 0) * 16, 0, 15, 15);
                    lg.fill(sx + 3, sy + 3, sx + 12, sy + 12, COULEURS[i]);
                }
                lb.fermer();
                lg.fermer();
            }
            case Reglette r -> {
                int v = r.get.getAsInt();
                float t = (v - r.min) / (float) Math.max(1, r.max - r.min);
                boolean hovL = DaKit.dans(mx, my, r.x, r.ym - 10, COL_W, ROW - 1);
                if (hovL || glisse == r) aide = r.aide;
                boolean chaud = glisse == r || DaKit.dans(mx, my, regX(r) - 2, r.ym - 8, REG_W + 4, 16);
                DaKit.inter9(ctx, f, DaKit.inter(r.label), r.x, r.ym, hovL ? DaKit.CREME : DaKit.CREME_VAL, 0);
                ReglagesArt.reglette(ctx, regX(r), r.ym - 5, REG_W, t, chaud);
                DaKit.inter8(ctx, f, DaKit.inter(v + r.unite), r.x + COL_W, r.ym, DaKit.OR_CLAIR, 2);
            }
        }
    }

    private static final int REG_W = 76;

    private static int regX(Reglette r) { return r.x + 70; }

    private static int segW(Choix c) { return (COL_W + 2) / c.libs.length; }

    private static int pastilleX(Nuancier n, int i) { return n.x + COL_W - 15 - (COULEURS.length - 1 - i) * 18; }

    // ═════════════════════════════════════════════════════════════ entrées

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        RebornPrefs p = RebornPrefs.INSTANCE;
        for (Ligne l : lignes) {
            switch (l) {
                case Bascule b -> {
                    if (DaKit.dans(mx, my, b.x, b.ym - 10, COL_W, ROW - 1)) {
                        b.set.accept(!b.get.get());
                        changer();
                        return true;
                    }
                }
                case Choix c -> {
                    int sw = segW(c);
                    for (int i = 0; i < c.libs.length; i++) {
                        if (DaKit.dans(mx, my, c.x + i * sw, c.ym + 9, sw - 2, SEG_H)) {
                            c.set.accept(i);
                            changer();
                            return true;
                        }
                    }
                }
                case Nuancier n -> {
                    for (int i = 0; i < COULEURS.length; i++) {
                        if (DaKit.dans(mx, my, pastilleX(n, i) - 1, n.ym - 8, 17, 17)) {
                            p.crosshairColor = COULEURS[i];
                            p.crosshairRainbow = false;
                            changer();
                            return true;
                        }
                    }
                }
                case Reglette r -> {
                    if (DaKit.dans(mx, my, regX(r) - 4, r.ym - 8, REG_W + 8, 16)) {
                        glisse = r;
                        regler(r, mx);
                        return true;
                    }
                }
            }
        }
        for (int i = 0; i < CrosshairManager.PRESET_COUNT; i++) {
            if (DaKit.dans(mx, my, gridX + i * CELL_PAS, gridY, CELL, CELL)) {
                p.crosshairPreset = i;
                p.crosshairStyle = "preset";
                p.save();
                son(SoundEvents.ARROW_SHOOT, 1.4f, 0.35f);
                return true;
            }
        }
        if (DaKit.dans(mx, my, resetX(), boutonsY(), BTN_W, 20)) { reinitialiser(); return true; }
        if (DaKit.dans(mx, my, fermerX(), boutonsY(), BTN_W, 20)) { onClose(); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (glisse != null) { regler(glisse, event.x()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (glisse != null) {
            glisse = null;
            RebornPrefs.INSTANCE.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    private void regler(Reglette r, double mx) {
        float t = (float) (mx - regX(r) - 2) / Math.max(1, REG_W - 4);
        int v = r.min + Math.round(Math.max(0f, Math.min(1f, t)) * (r.max - r.min));
        if (v != r.get.getAsInt()) r.set.accept(v);
    }

    private void changer() {
        RebornPrefs.INSTANCE.save();
        RebornSounds.uiClick();
    }

    private void reinitialiser() {
        RebornPrefs p = RebornPrefs.INSTANCE;
        p.crosshairPreset = 0;
        p.crosshairScale = 100;
        p.crosshairColor = C_WHITE;
        p.crosshairRainbow = false;
        p.crosshairDynamic = false;
        p.crosshairHitMarker = true;
        p.save();
        RebornSounds.confirm();
    }

    private static void son(net.minecraft.sounds.SoundEvent e, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    @Override
    public void added() {
        super.added();
        son(SoundEvents.ARROW_HIT, 0.9f, 0.45f);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        RebornPrefs.INSTANCE.save();
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
