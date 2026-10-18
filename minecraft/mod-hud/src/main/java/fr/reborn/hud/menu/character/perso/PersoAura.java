package fr.reborn.hud.menu.character.perso;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.meditation.Aura;
import fr.reborn.hud.meditation.MeditationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;

import static fr.reborn.hud.menu.character.perso.PersoArt.*;

/**
 * <b>Couleur de l'aura</b> (18/10) — Réglages Reborn › Interface › Méditation. Le joueur choisit la couleur du halo
 * qui entoure son personnage quand il médite (disque sombre, couronne de lumière, étincelles : {@link Aura}) ; elle
 * est enregistrée sur le personnage par le serveur et vue de tous.
 *
 * <p>À gauche, le sélecteur de couleur de la création de personnage ({@link PersoCouleur}, réutilisé tel quel) ; à
 * droite, l'aperçu du halo vu de dessus, calculé avec les mêmes formules que le rendu en jeu, sur l'herbe, la pierre et
 * l'herbe de nuit, avec ses étincelles ; « Par défaut » et cinq auras toutes faites en dessous. Si le joueur médite, son halo prend la couleur essayée en direct derrière
 * l'écran (le monde reste visible). Valider l'enregistre ; Échap annule ; « Par défaut » revient au menthe de la
 * référence.
 */
public final class PersoAura extends Screen {

    private static final int AW = 236, AH = PersoCouleur.H, TUILE = 64, IMG = 128;
    /** L'aperçu couvre 2⅔ blocs de côté (48 px par bloc, 3 px par texel d'herbe). */
    private static final float BLOCS = 8f / 3f;
    private static final Identifier[] TEX = {
        Identifier.fromNamespaceAndPath("reborn", "perso/aura_herbe"),
        Identifier.fromNamespaceAndPath("reborn", "perso/aura_pierre"),
        Identifier.fromNamespaceAndPath("reborn", "perso/aura_nuit")};
    private static final String[] NOMS = {"Herbe", "Pierre", "Nuit"};
    /** Quelques auras toutes faites, à côté de « Par défaut » (comme les couleurs canon de la teinture). */
    private static final int[] PRESETS = {0x6FB8FF, 0xB57CFF, 0xFF4B4B, 0xFFC94A, 0xF2F4FF};
    /** Teinte de l'herbe (jungle clairsemée : l'herbe vive de la vidéo de référence). */
    private static final int HERBE = 0x64C73F;

    private final Screen parent;
    private final int depart;
    private PersoCouleur picker;
    private int couleur, couleurImg = -1;
    private final DynamicTexture[] tex = new DynamicTexture[3];
    private final NativeImage[] img = new NativeImage[3];
    private int[] herbe, pierre;                       // 16×16 ARGB des textures du jeu (null : aplats)
    private boolean demandee;
    private String message;
    private int px, py;                                // coin du panneau d'aperçu

    public PersoAura(Screen parent) {
        super(Component.literal("Couleur de l'aura"));
        this.parent = parent;
        this.depart = MeditationClient.maCouleur();
        this.couleur = depart;
    }

    @Override
    protected void init() {
        if (!demandee) { MeditationClient.demanderCouleur(); demandee = true; }
        if (picker == null) picker = new PersoCouleur("COULEUR DE L'AURA", 0xFF000000 | couleur, this::essayer);
        int total = PersoCouleur.W + 12 + AW;
        picker.x = (width - total) / 2;
        // au-dessus du milieu : en vue à la troisième personne, le halo du joueur reste visible dessous
        picker.y = Math.max(8, height / 2 - AH - 24);
        px = picker.x + PersoCouleur.W + 12;
        py = picker.y;
        if (herbe == null) {
            herbe = lire("textures/block/grass_block_top.png");
            pierre = lire("textures/block/stone.png");
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean shouldCloseOnEsc() { return false; }

    private void essayer(int rgb) {
        couleur = rgb & 0xFFFFFF;
        MeditationClient.essayer(couleur);
    }

    private void valider() {
        if (!MeditationClient.serveurPret()) {
            message = "Serveur pas à jour : couleur non enregistrée.";
            return;
        }
        MeditationClient.choisirCouleur(couleur == Aura.DEFAUT ? -1 : couleur);
        fermer();
    }

    private void annuler() {
        MeditationClient.essayer(depart);
        fermer();
    }

    private void fermer() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() { annuler(); }

    @Override
    public void removed() {
        for (int i = 0; i < 3; i++) {
            if (tex[i] != null) { tex[i].close(); tex[i] = null; img[i] = null; }
        }
        couleurImg = -1;
    }

    /* ------------------------------------------------------------------ saisie */

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) { annuler(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { valider(); return true; }
        if (k == GLFW.GLFW_KEY_BACKSPACE) { picker.effacer(); return true; }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (!e.isAllowedChatCharacter()) return false;
        return picker.car(e.codepointAsString().charAt(0));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0) return super.mouseClicked(e, dbl);
        double mx = e.x(), my = e.y();
        int[] d = rectDefaut();
        if (dans(mx, my, d[0], d[1], d[2], d[3])) {
            poser(Aura.DEFAUT);
            return true;
        }
        for (int i = 0; i < PRESETS.length; i++) {
            int[] r = rectPreset(i);
            if (dans(mx, my, r[0], r[1], r[2], r[3])) { poser(PRESETS[i]); return true; }
        }
        boolean[] ok = {false};
        if (picker.clic(mx, my, ok)) {
            if (ok[0]) valider();
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (picker.glisser(e.x(), e.y())) return true;
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (picker.relacher()) return true;
        return super.mouseReleased(e);
    }

    /* ------------------------------------------------------------------ dessin */

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0x55000000);                     // le monde reste visible derrière
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        Font f = font;
        long now = System.currentTimeMillis();
        // la couleur du serveur arrive après l'ouverture : on s'y cale tant que le joueur n'a rien touché
        if (MeditationClient.maCouleurConnue() && couleur == depart && MeditationClient.maCouleur() != depart
                && !picker.glisse()) {
            poser(MeditationClient.maCouleur());
        }
        picker.dessiner(g, f, mx, my, now);
        panneau(g, px, py, AW, AH);
        etiq(g, f, "APERCU DU HALO", px + 12, py + 16, OR_CLAIR);
        g.fill(px + 12, py + 23, px + AW - 12, py + 24, alpha(R_OR[3], 0.8f));
        majTuiles();
        int tx = px + 12, ty = py + 32, pas = (AW - 24 - 3 * TUILE) / 2 + TUILE;
        for (int i = 0; i < 3; i++) {
            int x = tx + i * pas;
            g.fill(x - 1, ty - 1, x + TUILE + 1, ty + TUILE + 1, R_OR[3]);
            if (tex[i] != null) g.blit(RenderPipelines.GUI_TEXTURED, TEX[i], x, ty, 0, 0, TUILE, TUILE, IMG, IMG, IMG, IMG);
            etincelles(g, x, ty, now, i);
            inter(g, f, inter(NOMS[i]), x + TUILE / 2f, ty + TUILE + 8, 7, DOUX, 1);
        }
        String note = message != null ? message
            : MeditationClient.maCouleurChoisie() || couleur != Aura.DEFAUT ? "Visible de tous quand tu médites."
            : "Par défaut : le vert menthe de la méditation.";
        inter(g, f, inter(note), px + 12, ty + TUILE + 24, 7, message != null ? 0xFFE0866A : DOUX, 0);
        int[] d = rectDefaut();
        boolean sur = dans(mx, my, d[0], d[1], d[2], d[3]);
        g.fill(d[0] - 1, d[1] - 1, d[0] + d[2] + 1, d[1] + d[3] + 1, sur ? R_OR[5] : R_OR[3]);
        g.fill(d[0], d[1], d[0] + d[2], d[1] + d[3], sur ? 0xFF2A1A1E : 0xFF140B0D);
        g.fill(d[0] + 3, d[1] + 5, d[0] + 9, d[1] + d[3] - 5, opaque(Aura.DEFAUT));
        inter(g, f, inter("Par défaut"), d[0] + 14, d[1] + d[3] / 2f, 7, sur ? CREME : DOUX, 0);
        for (int i = 0; i < PRESETS.length; i++) {
            int[] r = rectPreset(i);
            boolean choisi = (couleur & 0xFFFFFF) == PRESETS[i], s2 = dans(mx, my, r[0], r[1], r[2], r[3]);
            g.fill(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1, choisi ? R_OR[6] : s2 ? R_OR[5] : R_OR[3]);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], opaque(PRESETS[i]));
            g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, alpha(0xFFFFFFFF, 0.25f));
        }
    }

    private int[] rectPreset(int i) { return new int[]{px + 12 + 74 + 12 + i * 23, py + AH - 27, 18, 18}; }

    /** Pose une couleur dans le sélecteur par son champ hexadécimal (vidé d'abord). */
    private void poser(int rgb) {
        for (int i = 0; i < 6; i++) picker.effacer();
        for (char c : String.format("%06X", rgb & 0xFFFFFF).toCharArray()) picker.car(c);
    }

    private int[] rectDefaut() { return new int[]{px + 12, py + AH - 28, 74, 20}; }

    /** Panneau laqué au filet d'or (le même que le sélecteur). */
    private static void panneau(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xC0000000);
        g.fill(x, y, x + w, y + h, R_OR[3]);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF140B0D);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, R_OR[1]);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF1A0F12);
        losange(g, x + 2, y + 2);
        losange(g, x + w - 5, y + 2);
        losange(g, x + 2, y + h - 5);
        losange(g, x + w - 5, y + h - 5);
    }

    /** Étincelles de l'aperçu : quelques points qui scintillent autour de la tête (déterministes, sans allocation). */
    private void etincelles(GuiGraphicsExtractor g, int x, int y, long now, int tuile) {
        int teinte = Aura.etincelle(couleur);
        double t = now / 1000.0;
        float k = TUILE / BLOCS;                                       // GUI par bloc
        for (int j = 0; j < 7; j++) {
            double vie = 1.1 + 0.07 * j, cycle = (t + j * 0.37 + tuile * 0.21) / vie;
            long n = (long) Math.floor(cycle);
            double u = cycle - n;
            long h = (n * 7919 + j * 104729 + tuile * 31) * 2654435761L;
            double a = ((h >>> 8) & 1023) / 1024.0 * 2 * Math.PI, r = 0.2 + 0.3 * ((h >>> 20) & 1023) / 1024.0;
            float sx = x + TUILE / 2f + (float) (Math.cos(a) * r * k), sy = y + TUILE / 2f + (float) (Math.sin(a) * r * k);
            double sc = 0.5 + 0.5 * Math.sin(j * 1.7 + t * (9 + j));
            float al = (float) (Math.min(1, u / 0.15) * Math.min(1, (1 - u) / 0.3) * (0.35 + 0.65 * sc * sc));
            if (al < 0.05f) continue;
            int ix = Math.round(sx), iy = Math.round(sy);
            g.fill(ix - 1, iy - 1, ix + 1, iy + 1, alpha(0xFF000000 | teinte, al * 0.7f));
            g.fill(ix, iy, ix + 1, iy + 1, alpha(0xFFFFFFFF, al * 0.8f));
        }
    }

    /* ------------------------------------------------------------------ tuiles d'aperçu */

    /** Recalcule les trois tuiles quand la couleur change (128² chacune, même formules que le rendu en jeu). */
    private void majTuiles() {
        if (couleurImg == couleur && tex[0] != null) return;
        couleurImg = couleur;
        var tm = Minecraft.getInstance().getTextureManager();
        int lum = Aura.lumiere(couleur), voile = Aura.voile(couleur);
        int cr = (lum >> 16) & 255, cg = (lum >> 8) & 255, cb = lum & 255;
        float lr = cr * Aura.LUMIERE, lg = cg * Aura.LUMIERE, lb = cb * Aura.LUMIERE;
        int vr = (voile >> 16) & 255, vg = (voile >> 8) & 255, vb = voile & 255;
        float pas = BLOCS / IMG;
        for (int t = 0; t < 3; t++) {
            if (img[t] == null) {
                img[t] = new NativeImage(IMG, IMG, false);
                tex[t] = new DynamicTexture(() -> "reborn aura apercu", img[t]);
                tm.register(TEX[t], tex[t]);
            }
            float nuit = t == 2 ? 0.16f : 1f;
            for (int j = 0; j < IMG; j++) {
                for (int i = 0; i < IMG; i++) {
                    float x = (i + 0.5f) * pas - BLOCS / 2, z = (j + 0.5f) * pas - BLOCS / 2;
                    int tx = Math.floorMod((int) Math.floor((x + 8) * 16), 16), tz = Math.floorMod((int) Math.floor((z + 8) * 16), 16);
                    int sol = sol(t == 1 ? pierre : herbe, tx, tz, t != 1);
                    float r = ((sol >> 16) & 255) * nuit, g = ((sol >> 8) & 255) * nuit, b = (sol & 255) * nuit;
                    if (t == 2) b += 6;                                           // nuit un peu bleutée
                    float d = (float) Math.sqrt(x * x + z * z);
                    float c = Aura.disque(d, pas) * Aura.VOILE;
                    r += (vr * nuit - r) * c; g += (vg * nuit - g) * c; b += (vb * nuit - b) * c;
                    float p = Aura.couronne(Math.max(d, Aura.R_DISQUE)) * (1 - Aura.disque(d, pas));
                    float te = Aura.TEINTE * p;
                    r += (cr - r) * te; g += (cg - g) * te; b += (cb - b) * te;
                    r += lr * p; g += lg * p; b += lb * p;
                    // la tête du méditant vue de dessus (8 texels), pour l'échelle
                    if (Math.abs(x) < 0.25f && Math.abs(z) < 0.25f) {
                        boolean bord = Math.abs(x) > 0.2f || Math.abs(z) > 0.2f;
                        int hr = bord ? 0x2A : 0x3D, hg = bord ? 0x1E : 0x2B, hb = bord ? 0x1A : 0x24;
                        r = hr * (t == 2 ? 0.35f : 1f); g = hg * (t == 2 ? 0.35f : 1f); b = hb * (t == 2 ? 0.35f : 1f);
                    }
                    img[t].setPixel(i, j, 0xFF000000 | Aura.rgbClamp(r, g, b));
                }
            }
            tex[t].upload();
        }
    }

    /** Texel du sol (texture du jeu, l'herbe teintée), ou un aplat si la texture manque. */
    private static int sol(int[] tex, int x, int z, boolean teinter) {
        if (tex == null) return teinter ? 0x3D7D27 : 0x7D7D7D;
        int p = tex[z * 16 + x];
        if (!teinter) return p & 0xFFFFFF;
        int r = ((p >> 16) & 255) * ((HERBE >> 16) & 255) / 255, g = ((p >> 8) & 255) * ((HERBE >> 8) & 255) / 255,
            b = (p & 255) * (HERBE & 255) / 255;
        return r << 16 | g << 8 | b;
    }

    /** Lit une texture 16×16 du jeu en ARGB, ou {@code null}. */
    private static int[] lire(String chemin) {
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace(chemin));
            if (res.isEmpty()) return null;
            try (InputStream in = res.get().open(); NativeImage im = NativeImage.read(in)) {
                int[] t = new int[256];
                for (int j = 0; j < 16; j++) for (int i = 0; i < 16; i++) t[j * 16 + i] = im.getPixel(i % im.getWidth(), j % im.getHeight());
                return t;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
