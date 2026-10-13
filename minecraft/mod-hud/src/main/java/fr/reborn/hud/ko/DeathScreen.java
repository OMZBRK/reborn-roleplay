package fr.reborn.hud.ko;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Scène de mort (KO-7). Un écran : le mort ne peut <b>absolument rien faire</b> (ni bouger, ni parler, ni ouvrir
 * quoi que ce soit).
 * <ol>
 *   <li>Les yeux battent puis se ferment.</li>
 *   <li>Écran noir. Au bout du délai (10 s depuis la mort, réglé par le serveur) : « Assister à votre réelle fin ? »
 *       Oui / Non.</li>
 *   <li>Non → il part ({@code /corps quitter}) : le serveur laisse le corps et ouvre la sélection de personnage.</li>
 *   <li>Oui → les yeux se rouvrent sur une vue grisée, sans HUD, en bandes de cinéma ; il regarde la suite (la
 *       souris tourne la vue autour du corps) et part quand il veut (Échap).</li>
 * </ol>
 */
public final class DeathScreen extends Screen {

    private static final long CLOSE = 1700, OPEN = 1800;
    /** Hauteur de chaque bande de cinéma (part de l'écran). */
    private static final float BARS = 0.105f;

    private final String name;
    private final long since;
    private final long choiceAt;
    private long watchSince = -1;
    private boolean asked;
    private int hover = -1, lastHover = -1;
    private int[] yes = new int[4], no = new int[4];
    private CameraType savedCamera;

    public DeathScreen(String name, int minSeconds) {
        super(Component.literal("Mort"));
        this.name = name;
        this.since = System.currentTimeMillis();
        this.choiceAt = since + Math.max(0, minSeconds) * 1000L;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (savedCamera == null) savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        hideHud(true);
    }

    /** Fermeture (fin de scène) : HUD et caméra rendus. */
    public void restore() {
        Minecraft mc = Minecraft.getInstance();
        hideHud(false);
        if (savedCamera != null) mc.options.setCameraType(savedCamera);
    }

    private static void hideHud(boolean hidden) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(hidden);
    }

    public boolean watching() { return watchSince >= 0; }

    /** Banc d'essai : bouton survolé imposé (-1 = souris). */
    public int debugHover = -1;

    /** Banc d'essai : répond à la question sans clic. */
    public void debugAnswer(boolean watch) { answer(watch); }

    private void answer(boolean watch) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 0.8f));
        if (watch) watchSince = System.currentTimeMillis();
        else if (mc.getConnection() != null) mc.getConnection().sendCommand("corps quitter");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = this.font;
        int w = this.width, h = this.height;
        long now = System.currentTimeMillis();
        long t = now - since;

        if (watchSince < 0) {
            // 1) les yeux se ferment, 2) noir
            float lid = t < CLOSE ? ease(t / (float) CLOSE) : 1f;
            if (t < CLOSE) lid = Math.min(1f, lid + (float) Math.max(0, Math.sin(t / 140.0)) * 0.08f * (1 - lid));
            lids(g, w, h, Math.round(h / 2f * lid), 16);
            if (now >= choiceAt && t >= CLOSE) {
                if (!asked) {
                    asked = true;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.4f, 0.6f));
                }
                question(g, font, w, h, mx, my, Math.min(1f, (now - choiceAt) / 900f));
            }
            return;
        }

        // 3) la suite : vue grisée, sans HUD, bandes de cinéma
        float open = ease(Math.min(1f, (now - watchSince) / (float) OPEN));
        g.fill(0, 0, w, h, Math.round(150 * open) << 24 | 0x141416);
        DaKit.etire(g, VIGNETTE, 0, 0, w, h, 160, 90, DaKit.blanc(0.8f * open));
        int bande = Math.round(h * BARS);
        int half = Math.round(h / 2f - (h / 2f - bande) * open);
        lids(g, w, h, half, Math.round(16 * (1f - open)));
        if (open >= 1f) cinema(g, font, w, h, Math.min(1f, (now - watchSince - OPEN) / 700f));
    }

    /* ---------------------------------------------------------------- textures (tools/gen_mort_textures.py) */

    private static final Identifier PLAQUE = DaKit.tex("mort", "plaque"), BOUTONS = DaKit.tex("mort", "boutons"),
        PIECES = DaKit.tex("mort", "pieces"), VIGNETTE = DaKit.tex("mort", "vignette");
    private static final int PW = 300, BW = 92, BH = 20;

    /** Bandes de cinéma : nom au filet d'or en haut, touches en cabochons en bas. */
    private void cinema(GuiGraphicsExtractor g, Font font, int w, int h, float a) {
        if (a <= 0f) return;
        int bar = Math.round(h * BARS);
        // filets d'or éteint au bord intérieur des bandes
        g.fill(0, bar - 1, w, bar, DaKit.argb(0x4A3518, 0.9f * a));
        g.fill(0, h - bar, w, h - bar + 1, DaKit.argb(0x4A3518, 0.9f * a));
        // nom : ArcadePix ×1 crème éteint, filets à losanges de part et d'autre
        Component n = DaKit.arcade(name.isEmpty() ? "Fin" : name);
        int nw = font.width(n) - 1, cy = bar / 2;
        DaKit.arcX1(g, font, n, w / 2f, cy - 3, DaKit.alpha(0xFFCFC4AE, a), 1, true);
        int fil = Math.min(110, w / 2 - nw / 2 - 40);
        for (int s = -1; s <= 1; s += 2) {
            int x0 = w / 2 + s * (nw / 2 + 12), x1 = x0 + s * fil;
            g.fill(Math.min(x0, x1), cy, Math.max(x0, x1), cy + 1, DaKit.argb(0x6A4E24, a));
            DaKit.blit(g, PIECES, x0 - 1, cy - 1, 0, 0, 3, 3, 64, 12, DaKit.blanc(a));
            DaKit.blit(g, PIECES, x1 - 1, cy - 1, 0, 0, 3, 3, 64, 12, DaKit.blanc(a));
        }
        // touches : GLISSER tourner · ECHAP partir, à droite de la bande basse
        float y = h - bar / 2f;
        float x = w - 18;
        x -= touche(g, font, "partir", "ECHAP", x, y, a) + 14;
        touche(g, font, "tourner la vue", "GLISSER", x, y, a);
    }

    /** Cabochon de touche + action (Inter 8), calé à droite en x ; renvoie la largeur. */
    private static float touche(GuiGraphicsExtractor g, Font f, String action, String touche, float xd, float ymil, float a) {
        Component act = DaKit.inter(action), t = DaKit.arcade(touche);
        float aw = DaKit.largeur8(f, act);
        int tw = (int) Math.ceil(f.width(t) * 0.5f) + 6;
        float x = xd - aw - 4 - tw;
        int yy = Math.round(ymil - 5), xx = Math.round(x);
        DaKit.blit(g, PIECES, xx, yy, 16, 0, 4, 12, 64, 12, DaKit.blanc(a));
        for (int k = xx + 4; k < xx + tw - 4; k += 16) DaKit.blit(g, PIECES, k, yy, 28, 0, Math.min(16, xx + tw - 4 - k), 12, 64, 12, DaKit.blanc(a));
        DaKit.blit(g, PIECES, xx + tw - 4, yy, 52, 0, 6, 12, 64, 12, DaKit.blanc(a));
        DaKit.arcFin(g, f, t, xx + tw / 2f, ymil - 1.75f, DaKit.alpha(DaKit.OR_CLAIR, a), 1, false);
        DaKit.inter8(g, f, act, xx + tw + 4, ymil, DaKit.argb(0xCFC4AE, 0.7f * a), 0);
        return xd - x;
    }

    private void question(GuiGraphicsExtractor g, Font font, int w, int h, int mx, int my, float a) {
        int px = w / 2 - 156, py = h / 2 - 66;
        int ox = px + 6, oy = py + 10;
        DaKit.blit(g, PLAQUE, px, py, 0, 0, 312, 128, 312, 128, DaKit.blanc(a));
        Component sub = DaKit.inter(name.isEmpty() ? "Ton histoire s'arrête ici." : name + " — ton histoire s'arrête ici.");
        DaKit.inter9(g, font, sub, ox + PW / 2f, oy + 51, DaKit.argb(0xC2B59A, 0.9f * a), 1);
        int by = oy + 70;
        yes = new int[]{w / 2 - 8 - BW, by, BW, BH};
        no = new int[]{w / 2 + 8, by, BW, BH};
        hover = debugHover >= 0 ? debugHover : in(yes, mx, my) ? 0 : in(no, mx, my) ? 1 : -1;
        DaKit.blit(g, BOUTONS, yes[0] - 1, by - 1, hover == 0 ? 95 : 0, 0, 95, 24, 190, 48, DaKit.blanc(a));
        DaKit.blit(g, BOUTONS, no[0] - 1, by - 1, hover == 1 ? 95 : 0, 24, 95, 24, 190, 48, DaKit.blanc(a));
        if (hover != lastHover) {
            if (hover >= 0) fr.reborn.hud.menu.RebornSounds.playReborn("esc.hover", 0.8f, 0.10f);
            lastHover = hover;
        }
    }

    private static boolean in(int[] r, double x, double y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (watchSince < 0 && asked && e.button() == 0) {
            if (in(yes, e.x(), e.y())) answer(true);
            else if (in(no, e.x(), e.y())) answer(false);
        }
        return true;
    }

    /** En regardant la suite : la souris tourne la vue (le corps, lui, ne bouge pas). */
    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        var p = Minecraft.getInstance().player;
        if (watchSince >= 0 && p != null) {
            p.setYRot(p.getYRot() + (float) dx * 0.6f);
            p.setXRot(Math.max(-60f, Math.min(60f, p.getXRot() + (float) dy * 0.4f)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (watchSince >= 0 && e.key() == GLFW.GLFW_KEY_ESCAPE) answer(false);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    /* ---------------------------------------------------------------- dessin */

    /** Paupières : noir plein jusqu'au bord, dégradé vers le centre (fermées : tout est noir). */
    private static void lids(GuiGraphicsExtractor g, int w, int h, int half, int soft) {
        if (half <= 0) return;
        g.fill(0, 0, w, half, 0xFF000000);
        g.fill(0, h - half, w, h, 0xFF000000);
        for (int i = 0; i < soft; i++) {
            int a = 255 - Math.round(255f * (i + 1) / soft);
            if (half + i < h - half - i) {
                g.fill(0, half + i, w, half + i + 1, a << 24);
                g.fill(0, h - half - i - 1, w, h - half - i, a << 24);
            }
        }
    }

    private static float ease(float x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

}
