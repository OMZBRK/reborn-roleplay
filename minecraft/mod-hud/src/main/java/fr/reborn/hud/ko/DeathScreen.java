package fr.reborn.hud.ko;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
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
    private static final float BARS = 0.12f;

    private final String name;
    private final long since;
    private final long choiceAt;
    private long watchSince = -1;
    private boolean asked;
    private int hover = -1;
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
            lids(g, w, h, Math.round(h / 2f * lid));
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
        vignette(g, w, h, open);
        int half = Math.round(h / 2f * (1f - (1f - BARS) * open));
        lids(g, w, h, half);
        if (open >= 1f) {
            int bar = Math.round(h * BARS);
            Component n = RebornFont.body(name);
            g.text(font, n, w / 2 - font.width(n) / 2, (bar - 8) / 2, 0x808E8780, false);
            Component hint = RebornFont.body("Échap pour partir");
            g.text(font, hint, w - 10 - font.width(hint), h - bar + (bar - 8) / 2, 0x608E8780, false);
        }
    }

    private void question(GuiGraphicsExtractor g, Font font, int w, int h, int mx, int my, float a) {
        Component q = RebornFont.display("ASSISTER À VOTRE RÉELLE FIN ?");
        int cy = h / 2 - 20;
        g.pose().pushMatrix();
        g.pose().translate(w / 2f, cy);
        g.pose().scale(1.4f, 1.4f);
        g.text(font, q, -font.width(q) / 2, 0, alpha(0xFFD8CFC4, a), false);
        g.pose().popMatrix();
        Component sub = RebornFont.body(name.isEmpty() ? "Ton histoire s'arrête ici." : name + " — ton histoire s'arrête ici.");
        g.text(font, sub, w / 2 - font.width(sub) / 2, cy + 18, alpha(0xFF8E8780, a), false);
        int bw = 90, bh = 18, gap = 18, by = cy + 42;
        yes = new int[]{w / 2 - gap / 2 - bw, by, bw, bh};
        no = new int[]{w / 2 + gap / 2, by, bw, bh};
        hover = in(yes, mx, my) ? 0 : in(no, mx, my) ? 1 : -1;
        button(g, font, yes, "OUI", hover == 0, a);
        button(g, font, no, "NON", hover == 1, a);
    }

    private static void button(GuiGraphicsExtractor g, Font font, int[] r, String label, boolean hot, float a) {
        int x = r[0], y = r[1], bw = r[2], bh = r[3];
        g.fill(x, y, x + bw, y + bh, alpha(hot ? 0xFF2A1416 : 0xFF0E0E10, a));
        int edge = alpha(hot ? Colors.GOLD : 0xFF4A4440, a);
        g.fill(x, y, x + bw, y + 1, edge);
        g.fill(x, y + bh - 1, x + bw, y + bh, edge);
        g.fill(x, y, x + 1, y + bh, edge);
        g.fill(x + bw - 1, y, x + bw, y + bh, edge);
        Component c = RebornFont.display(label);
        g.text(font, c, x + bw / 2 - font.width(c) / 2, y + (bh - 8) / 2, alpha(hot ? 0xFFF6E7B0 : 0xFFB8B0A8, a), false);
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
    private static void lids(GuiGraphicsExtractor g, int w, int h, int half) {
        if (half <= 0) return;
        g.fill(0, 0, w, half, 0xFF000000);
        g.fill(0, h - half, w, h, 0xFF000000);
        int soft = 16;
        for (int i = 0; i < soft; i++) {
            int a = 255 - Math.round(255f * (i + 1) / soft);
            if (half + i < h - half - i) {
                g.fill(0, half + i, w, half + i + 1, a << 24);
                g.fill(0, h - half - i - 1, w, h - half - i, a << 24);
            }
        }
    }

    private static void vignette(GuiGraphicsExtractor g, int w, int h, float f) {
        int steps = 16, thick = Math.max(2, Math.min(w, h) / 64);
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            int c = Math.round(70 * f * k * k) << 24;
            int o = i * thick;
            g.fill(o, o, w - o, o + thick, c);
            g.fill(o, h - o - thick, w - o, h - o, c);
            g.fill(o, o + thick, o + thick, h - o - thick, c);
            g.fill(w - o - thick, o + thick, w - o, h - o - thick, c);
        }
    }

    private static float ease(float x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    private static int alpha(int argb, float f) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return al << 24 | (argb & 0xFFFFFF);
    }
}
