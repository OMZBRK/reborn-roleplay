package fr.reborn.hud.yeux;

import fr.reborn.hud.ui.Da;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Chirurgie des yeux, en deux gestes jouables dans le style Reborn (laque, or, washi, tracés lissés au pixel réel,
 * le jeu reste visible sous un voile).
 *
 * <p><b>Prélèvement</b> (signature : la fiole de scellement, à droite) :
 * <ol>
 *   <li><b>Inciser</b> — une lame dorée tourne autour de l'œil ; cliquer quand elle passe sur chacune des trois
 *       marques sombres.</li>
 *   <li><b>Détacher</b> — maintenir le clic pour tirer, relâcher quand la tension est dans la bande dorée (deux
 *       fois). Trop tôt : rien ne vient ; trop tard : déchirure.</li>
 *   <li><b>Sceller</b> — l'œil flotte au-dessus de la fiole ; cliquer quand le sceau passe au centre.</li>
 * </ol>
 * Chaque erreur coûte de l'<b>intégrité</b> à l'œil (100 % au départ) ; à 0, l'œil est perdu.
 *
 * <p><b>Greffe</b> (signature : la trousse d'instruments, à droite) :
 * <ol>
 *   <li><b>Incision</b> — garder le curseur, clic maintenu, sur la pointe du scalpel qui fait le tour de
 *       l'orbite.</li>
 *   <li><b>Connexion du chakra</b> — garder le fil de chakra (hauteur de la souris) dans la zone dorée qui dérive,
 *       4 s au total.</li>
 *   <li><b>Sutures</b> — six points ; cliquer chacun quand l'anneau qui se resserre le touche.</li>
 * </ol>
 * Cinq erreurs (sceaux rouges) : la greffe échoue et l'œil est perdu.
 *
 * <p>Le résultat est envoyé au serveur ({@code /yeux resultat …}), qui décide (droits, objets, chances).
 */
public final class ChirurgieScreen extends Screen {

    public enum Mode { PRELEVEMENT, GREFFE }

    private static final int MAX_ERRORS = 5;

    private final Mode mode;
    private final String patient;
    private final boolean left;
    private final EyeArt.Kind kind;
    private final String token;
    private final Random rnd = new Random();

    private int phase;                 // 0, 1, 2 ; 3 = résultat
    private long phaseStart;
    private int errors;
    private int integrity = 100;
    private long flashAt = -10_000;
    private String flashText = "";
    private boolean failed;
    private boolean sent;

    // prélèvement
    private final double[] cuts = {0.35, 2.45, 4.35};
    private final boolean[] cut = new boolean[3];
    private boolean pulling;
    private long pullStart;
    private float bandLo = 0.62f, bandHi = 0.78f;
    private int pulls;
    private float sealPos;

    // greffe
    private boolean holding;
    private double mouseX, mouseY;
    private long awaySince = -1;
    private float lap;
    private long lastTick;
    private float inZone;
    private long outSince = -1;
    private int stitch;
    private long stitchStart;
    private int stitchHits;

    // banc d'essai
    private long frozen = -1;

    public ChirurgieScreen(Mode mode, String patient, boolean left, String eyeKind, String token) {
        super(Component.literal("Chirurgie"));
        this.mode = mode;
        this.patient = patient == null ? "" : patient;
        this.left = left;
        this.kind = EyeArt.kind(eyeKind);
        this.token = token == null ? "" : token;
        this.phaseStart = System.currentTimeMillis();
        this.lastTick = phaseStart;
    }

    private long now() { return frozen >= 0 ? frozen : System.currentTimeMillis(); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) { }

    /* ================================================================ logique */

    private void next() {
        phase++;
        phaseStart = now();
        lastTick = phaseStart;
        if (phase == 1 && mode == Mode.PRELEVEMENT) newBand();
        if (phase == 2 && mode == Mode.GREFFE) { stitch = 0; stitchStart = now() + 500; }
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f);
        if (phase == 3) finish();
    }

    private void newBand() {
        float c = 0.55f + rnd.nextFloat() * 0.25f;
        bandLo = c - 0.08f;
        bandHi = c + 0.08f;
    }

    private void error(String what, int cost) {
        errors++;
        integrity = Math.max(0, integrity - cost);
        flashAt = now();
        flashText = what;
        play(SoundEvents.PLAYER_HURT, 0.7f);
        boolean lost = mode == Mode.GREFFE ? errors >= MAX_ERRORS : integrity <= 0;
        if (lost) {
            failed = true;
            phase = 3;
            phaseStart = now();
            finish();
        }
    }

    private void finish() {
        if (sent || frozen >= 0) return;
        sent = true;
        var c = Minecraft.getInstance().getConnection();
        if (c == null) return;
        int score = mode == Mode.GREFFE ? Math.max(0, 100 - errors * 15) : integrity;
        c.sendCommand("yeux resultat " + (mode == Mode.GREFFE ? "greffe" : "prelevement") + " "
                + (failed ? "echec" : "ok") + " " + score + (token.isEmpty() ? "" : " " + token));
    }

    private void play(SoundEvent s, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, 0.7f));
    }

    private float tension() {
        if (!pulling) return 0;
        float t = (now() - pullStart) / 1000f;
        return Math.min(1.1f, 0.35f * t + 0.30f * t * t);
    }

    private double bladeAngle() { return (now() - phaseStart) / 1600.0 * Math.PI * 2 - Math.PI / 2; }

    private void tickLogic() {
        long n = now();
        float dt = Math.min(0.1f, (n - lastTick) / 1000f);
        lastTick = n;
        if (phase >= 3 || frozen >= 0) return;
        if (mode == Mode.PRELEVEMENT) {
            if (phase == 1 && pulling && tension() >= 1.05f) {
                pulling = false;
                error("Déchirure", 15);
                newBand();
            }
            if (phase == 2) sealPos = (float) Math.sin((n - phaseStart) / 380.0);
            return;
        }
        Layout L = layout();
        if (phase == 0) {
            lap = Math.min(1f, (n - phaseStart - 900) / 6000f);
            if (n - phaseStart < 900) return;
            double a = lap * Math.PI * 2 - Math.PI / 2;
            double tx = L.ex + Math.cos(a) * rg(L) * 1.12, ty = L.ey + Math.sin(a) * rg(L) * 0.80;
            boolean on = holding && Math.hypot(mouseX - tx, mouseY - ty) < 11;
            if (on) awaySince = -1;
            else if (awaySince < 0) awaySince = n;
            else if (n - awaySince > 450) { error("Incision déviée", 0); awaySince = n; }
            if (lap >= 1f && !failed) next();
        } else if (phase == 1) {
            double zone = zoneCenter(n);
            double y = (mouseY - L.barTop) / (double) L.barH;
            boolean in = Math.abs(y - zone) < 0.11;
            if (in) { inZone += dt; outSince = -1; }
            else if (outSince < 0) outSince = n;
            else if (n - outSince > 800) { error("Fil de chakra rompu", 0); outSince = n; }
            if (inZone >= 4f && !failed) next();
        } else if (phase == 2) {
            if (stitch < 6 && n - stitchStart > 1400) {
                error("Suture manquée", 0);
                stitch++;
                stitchStart = n + 250;
                if (stitch >= 6 && !failed) next();
            }
        }
    }

    private double zoneCenter(long n) {
        double t = (n - phaseStart) / 1000.0;
        return 0.5 + 0.30 * Math.sin(t * 1.1) + 0.08 * Math.sin(t * 3.7);
    }

    private double[] stitchPoint(Layout L, int k) {
        double a = -Math.PI * 0.85 + k * (Math.PI * 1.7 / 5);
        return new double[]{L.ex + Math.cos(a) * rg(L) * 1.05, L.ey + Math.sin(a) * rg(L) * 0.70 + (k % 2 == 0 ? 0 : 4)};
    }

    /** 0..1 : rayon de l'anneau qui se resserre sur la suture (1 = 3× le point, 0 = sur le point). */
    private float stitchRing() {
        return 1f - Math.max(0f, Math.min(1f, (now() - stitchStart) / 1100f));
    }

    /* ================================================================ entrées */

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0) return true;
        mouseX = e.x(); mouseY = e.y();
        Layout L = layout();
        if (phase >= 3) {
            if (Da.in(e.x(), e.y(), L.btnX, L.btnY, L.btnW, 16)) onClose();
            return true;
        }
        if (mode == Mode.PRELEVEMENT) {
            if (phase == 0) {
                double a = norm(bladeAngle());
                for (int k = 0; k < 3; k++) {
                    if (cut[k]) continue;
                    if (Math.abs(angDiff(a, cuts[k])) < 0.26) {
                        cut[k] = true;
                        play(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f);
                        if (cut[0] && cut[1] && cut[2]) next();
                        return true;
                    }
                }
                error("Coup à côté", 10);
            } else if (phase == 1) {
                pulling = true;
                pullStart = now();
            } else if (phase == 2) {
                if (Math.abs(sealPos) < 0.22) { play(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f); next(); }
                else error("Sceau mal posé", 8);
            }
            return true;
        }
        holding = true;
        if (phase == 2 && stitch < 6) {
            double[] p = stitchPoint(L, stitch);
            float ring = stitchRing();
            boolean onPoint = Math.hypot(e.x() - p[0], e.y() - p[1]) < 9;
            if (onPoint && ring < 0.22f) { stitchHits++; play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.4f); }
            else error(onPoint ? "Suture trop tôt" : "Suture à côté", 0);
            stitch++;
            stitchStart = now() + 250;
            if (stitch >= 6 && !failed) next();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        holding = false;
        if (mode == Mode.PRELEVEMENT && phase == 1 && pulling) {
            float t = tension();
            pulling = false;
            if (t >= bandLo && t <= bandHi) {
                pulls++;
                play(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8f);
                if (pulls >= 2) next(); else newBand();
            } else if (t < bandLo) error("Pas assez tiré", 6);
            else error("Déchirure", 15);
        }
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) { mouseX = x; mouseY = y; }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { mouseX = e.x(); mouseY = e.y(); return true; }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (phase < 3) {           // abandon = échec
                failed = true;
                phase = 3;
                finish();
            } else onClose();
        }
        return true;
    }

    /* ================================================================ dessin */

    private record Layout(int px, int py, int pw, int ph, int ex, int ey, int r, int sx, int sy, int barTop, int barH,
                          int barX, int btnX, int btnY, int btnW) {}

    /** Demi-largeur de l'œil en unités GUI, alignée sur la grille des texels. */
    private static double rg(Layout L) { return EyeArt.snap(L.r * Px.gs()) / Px.gs(); }

    private Layout layout() {
        int pw = Math.min(440, width - 40), ph = Math.min(256, height - 70);
        int px = (width - pw) / 2, py = 46;
        int r = Math.min(56, ph / 4);
        int ex = px + Math.round(pw * 0.42f), ey = py + Math.round(ph * 0.50f);
        int sx = px + Math.round(pw * 0.80f), sy = py + Math.round(ph * 0.52f);
        int barH = Math.round(r * 2.2f), barTop = ey - barH / 2, barX = ex + Math.round(r * 1.75f);
        int btnW = 90;
        return new Layout(px, py, pw, ph, ex, ey, r, sx, sy, barTop, barH, barX, px + (pw - btnW) / 2, py + ph - 28, btnW);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        tickLogic();
        Font f = this.font;
        long n = now();
        Layout L = layout();
        g.fill(0, 0, width, height, 0xA0080506);
        Da.panel(g, L.px, L.py, L.pw, L.ph);
        String side = left ? "œil gauche" : "œil droit";
        String title = mode == Mode.GREFFE ? "Greffe — " + side : "Prélèvement — " + side;
        String sub = (kind == EyeArt.Kind.NORMAL ? "" : EyeArt.label(kind) + " · ") + patient;
        Da.header(g, f, width / 2, 10, title, sub.isBlank() ? null : sub);
        steps(g, f, L);

        // plateau laqué rond sous la tête du patient
        Px.begin(g);
        double k = Px.gs();
        double ex = L.ex * k, ey = L.ey * k, r = EyeArt.snap(L.r * k);
        EyeArt.plateau(g, ex, ey, r);                      // texture : un seul blit par image
        EyeArt.skin(g, ex, ey, r);
        if (mode == Mode.PRELEVEMENT) drawPrelevement(g, L, k, n);
        else drawGreffe(g, L, k, n);
        Px.end(g);

        bottom(g, f, L);
        if (n - flashAt < 900) {
            float a = 1f - (n - flashAt) / 900f;
            Da.text(g, f, flashText, L.ex, L.ey + L.r * 1.25f + 6, Da.small(), Px.alpha(0xFFE04848, a), 1);
        }
        if (phase >= 3) result(g, f, L, mx, my);
        Da.hint(g, f, width, height, hint());
    }

    private String hint() {
        if (phase >= 3) return "Clic : fermer";
        if (mode == Mode.PRELEVEMENT) return switch (phase) {
            case 0 -> "Clic quand la lame passe sur une marque  -  Echap : renoncer";
            case 1 -> "Maintiens le clic pour tirer, relache dans la bande doree";
            default -> "Clic quand le sceau passe au centre de la fiole";
        };
        return switch (phase) {
            case 0 -> "Clic maintenu : suis la pointe du scalpel autour de l'orbite";
            case 1 -> "Garde le fil de chakra dans la zone doree (hauteur de la souris)";
            default -> "Clic sur le point quand l'anneau le touche";
        };
    }

    private void steps(GuiGraphicsExtractor g, Font f, Layout L) {
        String[] names = mode == Mode.GREFFE
                ? new String[]{"Incision", "Connexion du chakra", "Sutures"}
                : new String[]{"Inciser", "Détacher", "Sceller"};
        int bw = 96, gap = 8, total = 3 * bw + 2 * gap, x = L.px + (L.pw - total) / 2, y = L.py + 10;
        for (int i = 0; i < 3; i++) {
            boolean cur = i == phase, done = i < phase && !(failed && i == Math.min(phase, 2));
            Da.plate(g, f, x, y, bw, 14, (i + 1) + "  " + names[i], cur, false, true);
            if (done) Da.outline(g, x, y, bw, 14, Da.GOLD);
            x += bw + gap;
        }
    }

    /* ---------------------------------------------------------- prélèvement */

    private void drawPrelevement(GuiGraphicsExtractor g, Layout L, double k, long n) {
        double ex = L.ex * k, ey = L.ey * k, r = EyeArt.snap(L.r * k);
        double sx = L.sx * k, sy = L.sy * k;
        boolean removed = phase >= 2 || (phase >= 3 && !failed);
        // l'œil dans l'orbite, ou l'orbite vide
        if (phase <= 1) {
            double lift = phase == 1 ? Math.min(1f, tension()) * r * 0.10 + pulls * r * 0.06 : 0;
            if (phase == 1) EyeArt.socket(g, ex, ey, r, 0.6f);
            EyeArt.eye(g, ex, ey - lift, r, kind, n);
        } else {
            EyeArt.socket(g, ex, ey, r, 1f);
        }
        // incisions : orbite de la lame, marques, coupes faites
        if (phase == 0) {
            double or = r * 1.22;
            Px.ring(g, ex, ey, or + k * 0.5, Math.max(1, k * 0.6), 0x40F6CC78);
            for (int i = 0; i < 3; i++) {
                if (cut[i]) Px.arc(g, ex, ey, or, Math.max(2, k * 1.2), cuts[i] - 0.22, cuts[i] + 0.22, 0xFFF6CC78);
                else Px.arc(g, ex, ey, or + k, Math.max(3, k * 2.4), cuts[i] - 0.26, cuts[i] + 0.26, 0xFF5A0A0E);
            }
            double a = bladeAngle();
            double bx = ex + Math.cos(a) * or, by = ey + Math.sin(a) * or;
            Px.line(g, bx - Math.cos(a) * k * 7, by - Math.sin(a) * k * 7, bx + Math.cos(a) * k * 5, by + Math.sin(a) * k * 5,
                    Math.max(2, k * 1.6), 0xFFE8E4DC);
            Px.disc(g, bx, by, Math.max(2, k * 1.6), 0xFFF6CC78);
        } else {
            for (int i = 0; i < 3; i++) Px.arc(g, ex, ey, r * 1.22, Math.max(1, k * 0.8), cuts[i] - 0.22, cuts[i] + 0.22, 0x90A01818);
        }
        // jauge de traction (phase 1)
        if (phase == 1) tensionGauge(g, L, k);
        // la fiole (signature)
        fiole(g, sx, sy, k, phase >= 3 && !failed ? 1f : 0f, n);
        if (phase == 2) {
            double gx = sx, gy = sy - k * 74 + Math.sin(n / 300.0) * k * 2;
            EyeArt.globe(g, gx, gy, r * 0.42, kind, n);
            // sceau qui oscille sous l'œil
            double w = k * 30, mxp = sx + sealPos * w, ly = sy - k * 50;
            Px.line(g, sx - w, ly, sx + w, ly, Math.max(1, k * 0.8), 0x80F6CC78);
            Px.disc(g, sx, ly, Math.max(2, k * 2.2), 0x60F6CC78);
            Px.disc(g, mxp, ly, Math.max(3, k * 3.6), 0xFFA0182B);
            Px.ring(g, mxp, ly, Math.max(3, k * 3.6), Math.max(1, k * 0.7), 0xFFF6CC78);
        }
    }

    private void tensionGauge(GuiGraphicsExtractor g, Layout L, double k) {
        double x = L.barX * k, top = L.barTop * k, h = L.barH * k, w = k * 7;
        g.fill((int) (x - 1), (int) (top - 1), (int) (x + w + 1), (int) (top + h + 1), 0xFFAA8034);
        g.fill((int) x, (int) top, (int) (x + w), (int) (top + h), 0xFF1A0A0C);
        int lo = (int) (top + h * (1 - bandHi)), hi = (int) (top + h * (1 - bandLo));
        g.fill((int) x, lo, (int) (x + w), hi, 0xC0D9A95E);
        float t = Math.min(1f, tension());
        int fy = (int) (top + h * (1 - t));
        g.fill((int) x + 1, fy, (int) (x + w) - 1, (int) (top + h), t > bandHi ? 0xFFC0242C : 0xFFA0182B);
        g.fill((int) (x - k * 2), fy - 1, (int) (x + w + k * 2), fy + 1, 0xFFFAEED6);
        for (int i = 0; i < 2; i++) {
            Px.disc(g, x + w / 2, top + h + k * (6 + i * 6), Math.max(2, k * 2), i < pulls ? 0xFFF6CC78 : 0x50F6CC78);
        }
    }

    /** Fiole de scellement : verre, liquide de conservation, bouchon de cire rouge frappé du kanji 封. */
    private void fiole(GuiGraphicsExtractor g, double cx, double cy, double k, float filled, long n) {
        double w = k * 24, h = k * 74, top = cy - h / 2;
        Px.disc(g, cx + k * 3, cy + h / 2 + k * 2, w * 1.2, 0x50000000);
        // verre
        g.fill((int) (cx - w), (int) (top + k * 8), (int) (cx + w), (int) (cy + h / 2 - w), 0x40C8E0E8);
        Px.disc(g, cx, cy + h / 2 - w, w, 0x40C8E0E8);
        // liquide
        double liq = top + h * 0.38;
        g.fill((int) (cx - w + k), (int) liq, (int) (cx + w - k), (int) (cy + h / 2 - w), 0xA0C49A3C);
        Px.disc(g, cx, cy + h / 2 - w, w - k, 0xA0C49A3C);
        g.fill((int) (cx - w + k), (int) liq, (int) (cx + w - k), (int) (liq + k), 0xC0F2D49A);
        if (filled > 0) {
            EyeArt.globe(g, cx, cy + h * 0.12 + Math.sin(n / 500.0) * k, w * 0.78, kind, n);
            g.fill((int) (cx - w + k), (int) liq, (int) (cx + w - k), (int) (cy + h / 2 - w), 0x40C49A3C);
        }
        // reflets
        g.fill((int) (cx - w + k * 2), (int) (top + k * 10), (int) (cx - w + k * 3.5), (int) (cy + h / 2 - w * 1.4), 0x60FFFFFF);
        // contour
        g.fill((int) (cx - w - 1), (int) (top + k * 8), (int) (cx - w), (int) (cy + h / 2 - w), 0xB0E8F4F8);
        g.fill((int) (cx + w), (int) (top + k * 8), (int) (cx + w + 1), (int) (cy + h / 2 - w), 0xB0E8F4F8);
        // col et bouchon de cire
        g.fill((int) (cx - w * 0.75), (int) (top + k * 2), (int) (cx + w * 0.75), (int) (top + k * 9), 0xFF8A1018);
        g.fill((int) (cx - w * 0.75), (int) (top + k * 2), (int) (cx + w * 0.75), (int) (top + k * 3), 0xFFB82830);
        Px.disc(g, cx, top + k * 2, w * 0.55, 0xFFA0182B);
        Px.ring(g, cx, top + k * 2, w * 0.55, Math.max(1, k * 0.6), 0xFFD9A95E);
        // cordelette dorée
        g.fill((int) (cx - w * 0.8), (int) (top + k * 9), (int) (cx + w * 0.8), (int) (top + k * 10.2), 0xFFC8A05A);
    }

    /* ---------------------------------------------------------- greffe */

    private void drawGreffe(GuiGraphicsExtractor g, Layout L, double k, long n) {
        double ex = L.ex * k, ey = L.ey * k, r = EyeArt.snap(L.r * k);
        boolean placed = phase >= 1 && !(failed && phase >= 3 && errors >= MAX_ERRORS);
        EyeArt.socket(g, ex, ey, r, 0.3f);
        if (placed) EyeArt.eye(g, ex, ey, r, kind, n);
        if (phase == 0) {
            // tracé pointillé lissé de l'incision + scalpel
            int dots = 40;
            for (int i = 0; i < dots; i++) {
                double a = i * Math.PI * 2 / dots - Math.PI / 2;
                boolean done = i / (double) dots < lap;
                Px.disc(g, ex + Math.cos(a) * r * 1.12, ey + Math.sin(a) * r * 0.80, Math.max(1.2, k * 0.9),
                        done ? 0xFFA01818 : 0x70F6CC78);
            }
            double a = lap * Math.PI * 2 - Math.PI / 2;
            double tx = ex + Math.cos(a) * r * 1.12, ty = ey + Math.sin(a) * r * 0.80;
            boolean on = holding && Math.hypot(mouseX * k - tx, mouseY * k - ty) < 11 * k;
            Px.ring(g, tx, ty, k * 7, Math.max(1, k * 0.8), on ? 0xFFF6CC78 : 0x90F6CC78);
            Px.disc(g, tx, ty, Math.max(2, k * 1.8), 0xFFE8E4DC);
        } else if (phase == 1) {
            double zone = zoneCenter(n);
            double x = L.barX * k, top = L.barTop * k, h = L.barH * k, w = k * 7;
            g.fill((int) (x - 1), (int) (top - 1), (int) (x + w + 1), (int) (top + h + 1), 0xFFAA8034);
            g.fill((int) x, (int) top, (int) (x + w), (int) (top + h), 0xFF0E0A14);
            int z0 = (int) (top + h * (zone - 0.11)), z1 = (int) (top + h * (zone + 0.11));
            g.fill((int) x, Math.max((int) top, z0), (int) (x + w), Math.min((int) (top + h), z1), 0xC0D9A95E);
            double my = Math.max(top, Math.min(top + h, mouseY * k));
            g.fill((int) (x - k * 3), (int) my - 1, (int) (x + w + k * 3), (int) my + 1, 0xFF8FD8FF);
            // le fil de chakra, de l'orbite à la jauge, plus vif à mesure qu'il tient
            float p = Math.min(1f, inZone / 4f);
            int c = Px.mix(0x504A90C8, 0xFF9FE4FF, p);
            Px.line(g, ex + r * 0.3, ey, x - k * 3, my, Math.max(1.5, k * (0.8 + p)), c);
            Px.disc(g, ex, ey, r * 0.18 * (0.5 + p), Px.alpha(0xFF9FE4FF, 0.25f + 0.5f * p));
        } else if (phase >= 2) {
            for (int i = 0; i < 6; i++) {
                double[] pt = stitchPoint(L, i);
                double px = pt[0] * k, py = pt[1] * k;
                if (i < stitch) {
                    Px.line(g, px - k * 3, py - k * 2, px + k * 3, py + k * 2, Math.max(1, k * 0.8), 0xFF2A1A10);
                    Px.line(g, px - k * 3, py + k * 2, px + k * 3, py - k * 2, Math.max(1, k * 0.8), 0xFF2A1A10);
                } else {
                    Px.disc(g, px, py, Math.max(2, k * 2), i == stitch ? 0xFFF6CC78 : 0x60F6CC78);
                    if (i == stitch && phase == 2) {
                        float ring = stitchRing();
                        Px.ring(g, px, py, k * (3 + 14 * ring), Math.max(1, k * 0.8), ring < 0.22f ? 0xFFF6CC78 : 0xC0FAEED6);
                    }
                }
            }
        }
        trousse(g, L.sx * k, L.sy * k, k);
    }

    /** Trousse d'instruments déroulée sur un linge (signature de la greffe). */
    private void trousse(GuiGraphicsExtractor g, double cx, double cy, double k) {
        double w = k * 38, h = k * 70;
        g.fill((int) (cx - w + k * 3), (int) (cy - h / 2 + k * 3), (int) (cx + w + k * 3), (int) (cy + h / 2 + k * 3), 0x50000000);
        g.fill((int) (cx - w), (int) (cy - h / 2), (int) (cx + w), (int) (cy + h / 2), 0xFFEADCB6);
        g.fill((int) (cx - w), (int) (cy - h / 2), (int) (cx + w), (int) (cy - h / 2 + k * 3), 0xFFA0182B);
        g.fill((int) (cx - w), (int) (cy + h / 2 - k * 3), (int) (cx + w), (int) (cy + h / 2), 0xFFA0182B);
        for (int i = 0; i < 4; i++) {
            double x = cx - w + k * 13 + i * k * 17.5;
            g.fill((int) (x - k * 2), (int) (cy - h / 2 + k * 6), (int) (x + k * 2), (int) (cy + h / 2 - k * 6), 0x30603E24);
        }
        double t = Math.max(1, k * 0.9);
        double x0 = cx - w + k * 13, s = k * 17.5;
        Px.line(g, x0, cy - h * 0.20, x0, cy + h * 0.32, t * 1.6, 0xFF5A3A22);           // scalpel : manche
        Px.line(g, x0, cy - h * 0.34, x0, cy - h * 0.20, t, 0xFFC8C8D0);                 // lame
        Px.line(g, x0, cy - h * 0.34, x0 + k * 2.5, cy - h * 0.26, t, 0xFFE8E8F0);
        Px.line(g, x0 + s, cy - h * 0.34, x0 + s, cy + h * 0.32, t * 0.7, 0xFFD8D8E0);   // aiguille
        Px.ring(g, x0 + s, cy - h * 0.34, k * 2, Math.max(1, k * 0.5), 0xFFD8D8E0);
        Px.line(g, x0 + s * 2, cy - h * 0.30, x0 + s * 2 - k * 2.5, cy + h * 0.30, t, 0xFF8C8C94);   // pince
        Px.line(g, x0 + s * 2, cy - h * 0.30, x0 + s * 2 + k * 2.5, cy + h * 0.30, t, 0xFF8C8C94);
        Px.line(g, x0 + s * 3, cy - h * 0.18, x0 + s * 3, cy + h * 0.30, t * 1.6, 0xFF4E9A62);   // fil de suture
        Px.disc(g, x0 + s * 3, cy - h * 0.24, k * 4, 0xFF3E8F62);
        Px.ring(g, x0 + s * 3, cy - h * 0.24, k * 4, Math.max(1, k * 0.6), 0xFF2A6A44);
    }

    /* ---------------------------------------------------------- bas et résultat */

    private void bottom(GuiGraphicsExtractor g, Font f, Layout L) {
        int y = L.py + L.ph - 24;
        if (mode == Mode.PRELEVEMENT) {
            Da.ruler(g, f, L.px + 30, y, 150, integrity / 100f, "Intégrité de l'œil", integrity + " %", false, true);
        } else {
            Da.text(g, f, "Erreurs", L.px + 30, y, Da.small(), Da.MUTED, 0);
            for (int i = 0; i < MAX_ERRORS; i++) {
                int x = L.px + 30 + i * 14, yy = y + 9;
                g.fill(x, yy, x + 10, yy + 10, i < errors ? 0xFFA0182B : 0x402A1A1C);
                Da.outline(g, x, yy, 10, 10, i < errors ? 0xFFF0C8B0 : 0x60AA8034);
            }
        }
    }

    private void result(GuiGraphicsExtractor g, Font f, Layout L, int mx, int my) {
        g.fill(L.px + 2, L.py + 2, L.px + L.pw - 2, L.py + L.ph - 2, 0xB0080506);
        String side = left ? "gauche" : "droit";
        String title, line;
        if (mode == Mode.PRELEVEMENT) {
            title = failed ? "L'œil est perdu" : "Œil " + side + " prélevé";
            line = failed ? "Il n'a pas survécu au prélèvement." : "Intégrité " + integrity + " %  -  scellé dans une fiole.";
        } else {
            title = failed ? "La greffe a échoué" : "Greffe réussie";
            line = failed ? "L'œil est perdu ; l'orbite reste vide." : "Œil " + side + " en place  -  " + errors + " erreur(s).";
        }
        int cy = L.py + L.ph / 2 - 26;
        Da.text(g, f, title, width / 2f, cy, Da.title(), failed ? 0xFFE06060 : Da.GOLD, 1);
        g.text(f, Component.literal(line), width / 2 - f.width(line) / 2, cy + 18, 0xFFD8CFC4, false);
        boolean hot = Da.in(mx, my, L.btnX, L.btnY, L.btnW, 16);
        Da.plate(g, f, L.btnX, L.btnY, L.btnW, 16, "Fermer", true, hot, true);
    }

    private static double norm(double a) {
        a %= Math.PI * 2;
        return a < 0 ? a + Math.PI * 2 : a;
    }

    private static double angDiff(double a, double b) {
        double d = norm(a) - norm(b);
        if (d > Math.PI) d -= Math.PI * 2;
        if (d < -Math.PI) d += Math.PI * 2;
        return d;
    }

    /* ================================================================ banc d'essai */

    /** Fige l'écran dans un état représentatif d'une phase (captures). */
    public void debugState(int phase, long elapsedMs, int errors, int integrity, double mouseGuiX, double mouseGuiY) {
        this.phase = phase;
        this.errors = errors;
        this.integrity = integrity;
        this.failed = phase >= 3 && (mode == Mode.GREFFE ? errors >= MAX_ERRORS : integrity <= 0);
        long base = 1_000_000_000L;
        this.phaseStart = base;
        this.frozen = base + elapsedMs;
        this.mouseX = mouseGuiX;
        this.mouseY = mouseGuiY;
        this.holding = true;
        if (mode == Mode.PRELEVEMENT) {
            cut[0] = phase > 0 || elapsedMs > 600;
            cut[1] = phase > 0;
            cut[2] = phase > 0;
            if (phase == 1) { pulling = true; pullStart = frozen - 900; pulls = 1; bandLo = 0.58f; bandHi = 0.74f; }
            if (phase == 2) sealPos = 0.35f;
        } else {
            lap = phase == 0 ? Math.min(1f, (elapsedMs - 900) / 6000f) : 1f;
            inZone = phase == 1 ? 2.6f : 4f;
            Layout L = layout();
            if (phase == 0) {                                   // curseur sur la pointe du scalpel
                double a = lap * Math.PI * 2 - Math.PI / 2;
                this.mouseX = L.ex + Math.cos(a) * rg(L) * 1.12;
                this.mouseY = L.ey + Math.sin(a) * rg(L) * 0.80;
            } else if (phase == 1) {                            // fil tenu dans la zone
                this.mouseY = L.barTop + L.barH * zoneCenter(frozen);
            }
            if (phase == 2) { stitch = 3; stitchStart = frozen - 700; }
        }
    }
}
