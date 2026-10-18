package fr.reborn.hud.yeux;

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
 * Chirurgie des yeux, en deux gestes jouables, habillée au niveau de la fiche shinobi (DA §4.1) : en-tête suspendu,
 * étapes en plaques numérotées (1, 2, 3 ; plus de kanji depuis le 18/10), planche PATIENT et planche FIOLE / TROUSSE pendues aux perches, l'œil en gros plan sur
 * son plateau laqué ; le jeu reste visible sous un voile. Décor en textures ({@link ChirurgieArt},
 * {@code tools/gen_chirurgie_textures.py}) ; le code ne dessine que ce qui bouge (lame, marques, sceau, anneaux,
 * jauges, texte).
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
    private int lastPhase;             // phase où l'échec est survenu
    private final long openedAt = System.currentTimeMillis();
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
    private final boolean[] stitchOk = new boolean[6];

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
            lastPhase = phase;
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
            onClose();
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
            if (onPoint && ring < 0.22f) { stitchHits++; stitchOk[stitch] = true; play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.4f); }
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
                lastPhase = phase;
                failed = true;
                phase = 3;
                finish();
            } else onClose();
        }
        return true;
    }

    /* ================================================================ dessin */

    /**
     * Mise en page (GUI, référence 960×540 assurée par MenuScale) : en-tête suspendu et étapes en haut, planche
     * PATIENT à gauche et FIOLE / TROUSSE à droite pendues aux perches (y 84), l'œil sur son plateau au centre,
     * aides en bas à droite. Compact (W &lt; 840 ou H &lt; 470) : planches collées aux bords, œil plus petit.
     */
    private record Layout(int w, int h, int cx, boolean compact, int panG, int panD, int ex, int ey, int r,
                          int tray, int barX, int barTop, int barH, int cardX, int cardY, int btnX, int btnY,
                          int btnW, int aidesY, int[] tx) {}

    private static final int PAN_Y = 84;

    /** Demi-largeur de l'œil en unités GUI, alignée sur la grille des texels. */
    private static double rg(Layout L) { return EyeArt.snap(L.r * Px.gs()) / Px.gs(); }

    private Layout layoutCache;
    private int layoutW = -1, layoutH = -1;

    private Layout layout() {
        if (layoutCache != null && layoutW == width && layoutH == height) return layoutCache;
        int w = width, h = height, cx = w / 2;
        boolean compact = w < 840 || h < 470;
        int panG = compact ? 8 : 24, panD = w - (compact ? 8 : 24) - ChirurgieArt.PW;
        int r = compact ? 42 : 56;
        double gs = Px.gs();
        int tray = (int) Math.round(56 * EyeArt.scale(r * gs) / gs);
        int ex = cx, ey = (PAN_Y + 4 + h - 40) / 2;
        int barH = Math.round(r * 2.2f), barTop = ey - barH / 2, barX = ex + tray + 14;
        int cardX = cx - 124, cardY = ey - 56;
        int btnW = 90;
        int[] tx = {cx - 84, cx, cx + 84};
        layoutCache = new Layout(w, h, cx, compact, panG, panD, ex, ey, r, tray, barX, barTop, barH, cardX, cardY,
                cx - 46, cardY + 81, btnW, h - 31, tx);
        layoutW = w;
        layoutH = h;
        return layoutCache;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        tickLogic();
        Font f = this.font;
        long n = now();
        Layout L = layout();
        // le jeu reste visible sous un voile (DA §2)
        g.fill(0, 0, width, height, ChirurgieArt.argb(0x0A0608, 0.70f));
        ChirurgieArt.etire(g, ChirurgieArt.VIGNETTE, 0, 0, width, height, 64, 36, ChirurgieArt.argb(0xFFFFFF, 0.55f));

        ChirurgieArt.perches(g, L.panG + ChirurgieArt.PW + 10, L.panD - 10, L.w);
        patient(g, f, L);
        if (mode == Mode.PRELEVEMENT) panneauFiole(g, f, L, n);
        else panneauTrousse(g, f, L);

        // plateau laqué, peau, œil : textures à échelle entière de pixels écran
        Px.begin(g);
        double k = Px.gs();
        double ex = L.ex * k, ey = L.ey * k, r = EyeArt.snap(L.r * k);
        EyeArt.plateau(g, ex, ey, r);
        EyeArt.skin(g, ex, ey, r);
        if (mode == Mode.PRELEVEMENT) drawPrelevement(g, L, k, n);
        else drawGreffe(g, L, k, n);
        Px.end(g);
        if (mode == Mode.PRELEVEMENT && phase == 1) tensionGauge(g, L);
        if (mode == Mode.GREFFE && phase == 1) chakraGauge(g, L, n);

        if (n - flashAt < 900) {
            float a = 1f - (n - flashAt) / 900f;
            ChirurgieArt.arcadeX1(g, f, ChirurgieArt.arcade(flashText), L.ex, L.ey + L.tray + 8,
                    ChirurgieArt.argb(ChirurgieArt.ROUGE_VIF, a), 1, true);
        }
        entete(g, f, L, n);
        aides(g, f, L);
        if (phase >= 3) result(g, f, L, mx, my);
    }

    /* ---------------------------------------------------------- en-tête et étapes */

    private static final Component[] NUM = {ChirurgieArt.arcade("1"), ChirurgieArt.arcade("2"), ChirurgieArt.arcade("3")};
    private static final String[] LIB_PRELEVEMENT = {"INCISER", "DETACHER", "SCELLER"},
            LIB_GREFFE = {"INCISION", "CHAKRA", "SUTURES"};

    private void entete(GuiGraphicsExtractor g, Font f, Layout L, long n) {
        float t = Math.min(1f, (n - openedAt) / 220f);
        int dy = frozen >= 0 || t >= 1f ? 0 : Math.round(-36 * (1 - (1 - (1 - t) * (1 - t))));
        g.pose().pushMatrix();
        g.pose().translate(0, dy);
        ChirurgieArt.blit(g, mode == Mode.GREFFE ? ChirurgieArt.ENTETE_GREFFE : ChirurgieArt.ENTETE_PRELEVEMENT,
                L.cx - 160, 0, 320, 48);
        String[] lib = mode == Mode.GREFFE ? LIB_GREFFE : LIB_PRELEVEMENT;
        int failedAt = failed ? Math.min(lastPhase, 2) : -1;
        for (int i = 0; i < 3; i++) {
            int tx = L.tx[i];
            int cell = i == failedAt ? 3 : i < phase ? 2 : i == phase ? 1 : 0;
            ChirurgieArt.blit(g, ChirurgieArt.ETAPES, tx - 13, 36, cell * 26, 0, 26, 36, 104, 36);
            int ty = cell == 1 ? 46 : 44;
            int col = switch (cell) {
                case 1 -> ChirurgieArt.OR_CLAIR;
                case 2 -> ChirurgieArt.R_OR[7];
                case 3 -> 0xFFB04040;
                default -> ChirurgieArt.R_OR[4];
            };
            // numéro d'étape (ArcadePix) au centre de la plaque, comme les étapes de la création de personnage
            ChirurgieArt.arcadeX1(g, f, NUM[i], tx, ty + 8, col, 1, cell == 1 || cell == 2);
            ChirurgieArt.arcFin(g, f, ChirurgieArt.arcade(lib[i]), tx, ty + 22 + 4,
                    cell == 1 ? ChirurgieArt.CREME : cell == 2 ? ChirurgieArt.CREME_VAL : ChirurgieArt.REPOS, 1, cell == 1);
        }
        g.pose().popMatrix();
    }

    /* ---------------------------------------------------------- planche PATIENT */

    private Component nomPatient, ligneOeil;

    private void patient(GuiGraphicsExtractor g, Font f, Layout L) {
        int x = L.panG, y = PAN_Y;
        ChirurgieArt.planche(g, ChirurgieArt.P_PATIENT, x, ChirurgieArt.H_PATIENT);
        if (nomPatient == null) {
            nomPatient = ChirurgieArt.arcade(patient.isBlank() ? "Inconnu" : patient);
            String side = left ? "Œil gauche" : "Œil droit";
            ligneOeil = ChirurgieArt.inter(kind == EyeArt.Kind.NORMAL ? side : side + " · " + EyeArt.label(kind));
        }
        ChirurgieArt.arcadeX1(g, f, nomPatient, x + 12, y + 36, ChirurgieArt.CREME, 0, true);
        ChirurgieArt.inter7(g, f, ligneOeil, x + 12, y + 52, ChirurgieArt.DOUX, 0);
        g.fill(x + 12, y + 61, x + ChirurgieArt.PW - 12, y + 62, ChirurgieArt.SEP);
        int xr = x + ChirurgieArt.PW - 12;
        if (mode == Mode.PRELEVEMENT) {
            ChirurgieArt.etiq(g, f, "Intégrité de l'œil", x + 12, y + 74, ChirurgieArt.OR_ETIQ);
            ChirurgieArt.inter9(g, f, ChirurgieArt.inter(integrity + " %"), xr, y + 74,
                    integrity < 40 ? ChirurgieArt.ROUGE_VIF : ChirurgieArt.CREME_VAL, 2);
            ChirurgieArt.blit(g, ChirurgieArt.JAUGE_H, x + 12, y + 81, 176, 9);
            int fw = Math.round(172 * integrity / 100f);
            if (fw > 0) {
                g.fill(x + 14, y + 83, x + 14 + fw, y + 88, integrity < 40 ? 0xFFA0182B : 0xFFB08442);
                g.fill(x + 14, y + 83, x + 14 + fw, y + 84, integrity < 40 ? 0xFFCC3A4C : 0xFFF2D59A);
            }
            ChirurgieArt.etiq(g, f, "Coupes", x + 12, y + 106, ChirurgieArt.OR_ETIQ);
            for (int i = 0; i < 3; i++) {
                ChirurgieArt.pion(g, cut[i] ? ChirurgieArt.PION_PERLE : ChirurgieArt.PION_COUPE, xr - 12 - (2 - i) * 14, y + 100);
            }
            ChirurgieArt.etiq(g, f, "Tractions", x + 12, y + 124, ChirurgieArt.OR_ETIQ);
            for (int i = 0; i < 2; i++) {
                ChirurgieArt.pion(g, i < pulls ? ChirurgieArt.PION_PERLE : ChirurgieArt.PION_PERLE_VIDE, xr - 12 - (1 - i) * 14, y + 118);
            }
            ChirurgieArt.etiq(g, f, "Scellé", x + 12, y + 142, ChirurgieArt.OR_ETIQ);
            ChirurgieArt.pion(g, phase >= 3 && !failed ? ChirurgieArt.PION_PERLE : ChirurgieArt.PION_PERLE_VIDE, xr - 12, y + 136);
        } else {
            ChirurgieArt.etiq(g, f, "Erreurs", x + 12, y + 74, ChirurgieArt.OR_ETIQ);
            ChirurgieArt.inter9(g, f, ChirurgieArt.inter(errors + " / " + MAX_ERRORS), xr, y + 74,
                    errors >= MAX_ERRORS - 1 ? ChirurgieArt.ROUGE_VIF : ChirurgieArt.CREME_VAL, 2);
            for (int i = 0; i < MAX_ERRORS; i++) {
                ChirurgieArt.pion(g, i < errors ? ChirurgieArt.PION_ERREUR : ChirurgieArt.PION_ERREUR_VIDE, x + 12 + i * 15, y + 80);
            }
            ChirurgieArt.etiq(g, f, "Incision", x + 12, y + 106, ChirurgieArt.OR_ETIQ);
            int inc = phase > 0 ? 100 : Math.round(Math.max(0f, lap) * 100);
            ChirurgieArt.inter9(g, f, ChirurgieArt.inter(inc + " %"), xr, y + 106, ChirurgieArt.CREME_VAL, 2);
            ChirurgieArt.etiq(g, f, "Fil de chakra", x + 12, y + 124, ChirurgieArt.OR_ETIQ);
            float s = phase > 1 ? 4f : phase == 1 ? Math.min(4f, inZone) : 0f;
            ChirurgieArt.inter9(g, f, ChirurgieArt.inter(String.format(java.util.Locale.ROOT, "%.1f / 4 s", s)), xr, y + 124,
                    ChirurgieArt.CREME_VAL, 2);
            ChirurgieArt.etiq(g, f, "Sutures", x + 12, y + 142, ChirurgieArt.OR_ETIQ);
            for (int i = 0; i < 6; i++) {
                int cell = i < stitch ? (stitchOk[i] ? ChirurgieArt.PION_PERLE : ChirurgieArt.PION_ERREUR) : ChirurgieArt.PION_PERLE_VIDE;
                ChirurgieArt.pion(g, cell, xr - 12 - (5 - i) * 13, y + 136);
            }
        }
    }

    /* ---------------------------------------------------------- planche FIOLE (prélèvement) */

    private void panneauFiole(GuiGraphicsExtractor g, Font f, Layout L, long n) {
        int x = L.panD, y = PAN_Y;
        ChirurgieArt.planche(g, ChirurgieArt.P_FIOLE, x, ChirurgieArt.H_DROITE);
        int fx = x + ChirurgieArt.PW / 2, fy = y + ChirurgieArt.H_DROITE - 8 - ChirurgieArt.FIOLE_H;
        ChirurgieArt.blit(g, ChirurgieArt.FIOLE_FOND, fx - 32, fy, ChirurgieArt.FIOLE_W, ChirurgieArt.FIOLE_H);
        double k = Px.gs();
        boolean sealed = phase >= 3 && !failed;
        if (sealed) {
            Px.begin(g);
            EyeArt.globe(g, fx * k, (fy + 76 + Math.sin(n / 600.0)) * k, 16.5 * k, kind, n);
            Px.end(g);
        }
        ChirurgieArt.blit(g, ChirurgieArt.FIOLE_FACE, fx - 32, fy, ChirurgieArt.FIOLE_W, ChirurgieArt.FIOLE_H);
        if (phase == 2) {
            // l'œil flotte au-dessus de la fiole ; le sceau oscille sur la ligne, cliquer quand il est au centre
            int ly = fy - 6;
            Px.begin(g);
            EyeArt.globe(g, fx * k, (y + 50 + Math.sin(n / 300.0) * 1.5) * k, 16.5 * k, kind, n);
            Px.end(g);
            int w = 34;
            g.fill(fx - w, ly, fx + w + 1, ly + 1, 0xA0D9A95E);
            g.fill(fx - 3, ly - 3, fx + 4, ly + 4, 0x50F2D08A);
            g.fill(fx - 1, ly - 5, fx + 2, ly + 6, 0xC0F2D08A);
            int sx = fx + Math.round(sealPos * w);
            Px.begin(g);
            Px.disc(g, sx * k + k / 2, (ly + 0.5) * k, 4.5 * k, 0xFF7A1322);
            Px.disc(g, sx * k + k / 2, (ly + 0.5) * k, 3.5 * k, 0xFFA0182B);
            Px.ring(g, sx * k + k / 2, (ly + 0.5) * k, 4.5 * k, Math.max(1, k * 0.6), 0xFFD9A95E);
            Px.end(g);
        }
        String l = sealed ? "Œil scellé" : phase == 2 ? "Prêt à sceller" : "Fiole vide";
        ChirurgieArt.inter7(g, f, ChirurgieArt.inter(l), fx, y + ChirurgieArt.H_DROITE + 16, ChirurgieArt.argb(ChirurgieArt.CREME, 0.6f), 1);
    }

    /* ---------------------------------------------------------- planche TROUSSE (greffe) */

    private void panneauTrousse(GuiGraphicsExtractor g, Font f, Layout L) {
        int x = L.panD, y = PAN_Y;
        ChirurgieArt.planche(g, ChirurgieArt.P_TROUSSE, x, ChirurgieArt.H_DROITE);
        int tx = x + ChirurgieArt.PW / 2 - ChirurgieArt.TROUSSE_W / 2;
        int ty = y + 30 + (ChirurgieArt.H_DROITE - 38 - ChirurgieArt.TROUSSE_H) / 2;
        ChirurgieArt.blit(g, ChirurgieArt.TROUSSE, tx, ty, ChirurgieArt.TROUSSE_W, ChirurgieArt.TROUSSE_H);
        int outil = phase == 0 ? 0 : phase == 2 ? 1 : phase == 1 ? 3 : -1;
        if (outil >= 0) ChirurgieArt.blit(g, ChirurgieArt.TROUSSE_SEL, tx + 33 + 34 * outil - 14, ty + 10, 28, 112);
        String l = switch (phase) {
            case 0 -> "Scalpel";
            case 1 -> "Fil de chakra";
            case 2 -> "Aiguille et fil";
            default -> failed ? "Instruments rangés" : "Greffe terminée";
        };
        ChirurgieArt.inter7(g, f, ChirurgieArt.inter(l), x + ChirurgieArt.PW / 2f, y + ChirurgieArt.H_DROITE + 16,
                ChirurgieArt.argb(ChirurgieArt.CREME, 0.6f), 1);
    }

    /* ---------------------------------------------------------- prélèvement (centre) */

    private void drawPrelevement(GuiGraphicsExtractor g, Layout L, double k, long n) {
        double ex = L.ex * k, ey = L.ey * k, r = EyeArt.snap(L.r * k);
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
    }

    /** Jauge de traction (prélèvement, phase 1) : cadre en texture, bande d'or et remplissage par le code. */
    private void tensionGauge(GuiGraphicsExtractor g, Layout L) {
        int x = L.barX, top = L.barTop, h = L.barH;
        ChirurgieArt.jaugeV(g, x - 3, top - 3, h + 6);
        int lo = top + Math.round(h * (1 - bandHi)), hi = top + Math.round(h * (1 - bandLo));
        g.fill(x, lo, x + 7, hi, 0xB0D9A95E);
        g.fill(x, lo, x + 7, lo + 1, 0xFFF2D59A);
        g.fill(x, hi - 1, x + 7, hi, 0xFF8E6A30);
        float t = Math.min(1f, tension());
        int fy = top + Math.round(h * (1 - t));
        if (t > 0) {
            g.fill(x + 1, fy, x + 6, top + h, t > bandHi ? 0xFFCC3A4C : 0xFFA0182B);
            g.fill(x - 3, fy - 1, x + 10, fy + 1, 0xFFFAEED6);
        }
        for (int i = 0; i < 2; i++) {
            ChirurgieArt.pion(g, i < pulls ? ChirurgieArt.PION_PERLE : ChirurgieArt.PION_PERLE_VIDE, x - 3 + 1, top + h + 6 + i * 12);
        }
    }

    /** Jauge du fil de chakra (greffe, phase 1) : zone d'or qui dérive, curseur à la hauteur de la souris. */
    private void chakraGauge(GuiGraphicsExtractor g, Layout L, long n) {
        int x = L.barX, top = L.barTop, h = L.barH;
        ChirurgieArt.jaugeV(g, x - 3, top - 3, h + 6);
        double zone = zoneCenter(n);
        int z0 = Math.max(top, top + (int) Math.round(h * (zone - 0.11))), z1 = Math.min(top + h, top + (int) Math.round(h * (zone + 0.11)));
        g.fill(x, z0, x + 7, z1, 0xB0D9A95E);
        g.fill(x, z0, x + 7, z0 + 1, 0xFFF2D59A);
        int my = (int) Math.round(Math.max(top, Math.min(top + h, mouseY)));
        g.fill(x - 4, my - 1, x + 11, my + 1, 0xFF8FD8FF);
        // remplissage : temps tenu dans la zone
        float p = Math.min(1f, inZone / 4f);
        int fh = Math.round(h * p);
        if (fh > 0) g.fill(x + 1, top + h - fh, x + 2, top + h, 0xFF9FE4FF);
    }

    /* ---------------------------------------------------------- greffe (centre) */

    private void drawGreffe(GuiGraphicsExtractor g, Layout L, double k, long n) {
        double ex = L.ex * k, ey = L.ey * k, r = EyeArt.snap(L.r * k);
        boolean placed = phase >= 1 && !(failed && phase >= 3 && errors >= MAX_ERRORS);
        EyeArt.socket(g, ex, ey, r, 0.3f);
        if (placed) EyeArt.eye(g, ex, ey, r, kind, n);
        if (phase == 0) {
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
            // le fil de chakra, de l'orbite à la jauge, plus vif à mesure qu'il tient
            double x = L.barX * k;
            double my = Math.max(L.barTop * k, Math.min((L.barTop + L.barH) * k, mouseY * k));
            float p = Math.min(1f, inZone / 4f);
            int c = Px.mix(0x504A90C8, 0xFF9FE4FF, p);
            Px.line(g, ex + r * 0.3, ey, x - k * 4, my, Math.max(1.5, k * (0.8 + p)), c);
            Px.disc(g, ex, ey, r * 0.18 * (0.5 + p), Px.alpha(0xFF9FE4FF, 0.25f + 0.5f * p));
        } else if (phase >= 2) {
            for (int i = 0; i < 6; i++) {
                double[] pt = stitchPoint(L, i);
                double px = pt[0] * k, py = pt[1] * k;
                if (i < stitch) {
                    int col = stitchOk[i] ? 0xFF2A1A10 : 0xFF7A1322;
                    Px.line(g, px - k * 3, py - k * 2, px + k * 3, py + k * 2, Math.max(1, k * 0.8), col);
                    Px.line(g, px - k * 3, py + k * 2, px + k * 3, py - k * 2, Math.max(1, k * 0.8), col);
                } else {
                    Px.disc(g, px, py, Math.max(2, k * 2), i == stitch ? 0xFFF6CC78 : 0x60F6CC78);
                    if (i == stitch && phase == 2) {
                        float ring = stitchRing();
                        Px.ring(g, px, py, k * (3 + 14 * ring), Math.max(1, k * 0.8), ring < 0.22f ? 0xFFF6CC78 : 0xC0FAEED6);
                    }
                }
            }
        }
    }

    /* ---------------------------------------------------------- aides et résultat */

    private int aidesKey = -1;
    private Component[] aidesT = new Component[0], aidesA = new Component[0];
    private float aidesTotal;

    private String[][] aidesItems() {
        if (phase >= 3) return new String[][]{{"CLIC", "fermer"}};
        String[] a = mode == Mode.PRELEVEMENT ? switch (phase) {
            case 0 -> new String[]{"CLIC", "couper quand la lame passe sur une marque"};
            case 1 -> new String[]{"MAINTENIR", "tirer, relâcher dans la bande d'or"};
            default -> new String[]{"CLIC", "sceller quand le sceau passe au centre"};
        } : switch (phase) {
            case 0 -> new String[]{"MAINTENIR", "suivre la pointe du scalpel"};
            case 1 -> new String[]{"SOURIS", "garder le fil dans la zone d'or"};
            default -> new String[]{"CLIC", "quand l'anneau touche le point"};
        };
        return new String[][]{a, {"ECHAP", "renoncer"}};
    }

    private void aides(GuiGraphicsExtractor g, Font f, Layout L) {
        int key = (mode.ordinal() << 4) | Math.min(phase, 3);
        if (key != aidesKey) {
            aidesKey = key;
            String[][] it = aidesItems();
            aidesT = new Component[it.length];
            aidesA = new Component[it.length];
            aidesTotal = 10f * (it.length - 1);
            for (int i = 0; i < it.length; i++) {
                aidesT[i] = ChirurgieArt.arcade(it[i][0]);
                aidesA[i] = ChirurgieArt.inter(it[i][1]);
                aidesTotal += ChirurgieArt.largeurCabochon(f, aidesT[i]) + 4 + f.width(aidesA[i]) * ChirurgieArt.S8;
            }
        }
        float y = L.aidesY;
        float x = L.w - (L.compact ? 10 : 24) - aidesTotal;
        for (int i = 0; i < aidesT.length; i++) {
            x += ChirurgieArt.cabochon(g, f, aidesT[i], Math.round(x), y);
            x += 4 + ChirurgieArt.inter8(g, f, aidesA[i], x + 4, y, ChirurgieArt.argb(ChirurgieArt.CREME, 0.6f));
            if (i < aidesT.length - 1) {
                int X = Math.round(x + 5);
                g.fill(X, Math.round(y), X + 1, Math.round(y) + 1, ChirurgieArt.OR_ETEINT);
                x += 10;
            }
        }
    }

    private void result(GuiGraphicsExtractor g, Font f, Layout L, int mx, int my) {
        g.fill(0, 0, width, height, 0x700A0608);
        int x = L.cardX, y = L.cardY;
        ChirurgieArt.blit(g, ChirurgieArt.RESULTAT, x, y, 248, 112);
        ChirurgieArt.blit(g, ChirurgieArt.RESULTAT_SCEAUX, x + 248 - 40, y + 9, failed ? 26 : 0, 0, 26, 26, 52, 26);
        String side = left ? "gauche" : "droit";
        String title, line;
        if (mode == Mode.PRELEVEMENT) {
            title = failed ? "L'oeil est perdu" : "Oeil " + side + " preleve";
            line = failed ? "Il n'a pas survécu au prélèvement." : "Intégrité " + integrity + " %  ·  scellé dans une fiole.";
        } else {
            title = failed ? "La greffe a echoue" : "Greffe reussie";
            line = failed ? "L'œil est perdu ; l'orbite reste vide." : "Œil " + side + " en place  ·  " + errors + " erreur(s).";
        }
        ChirurgieArt.arcadeX1(g, f, ChirurgieArt.arcade(title), L.cx, y + 17, failed ? 0xFFE06060 : ChirurgieArt.OR_CLAIR, 1, true);
        ChirurgieArt.inter9(g, f, ChirurgieArt.inter(line), L.cx, y + 54, ChirurgieArt.CREME_VAL, 1);
        ChirurgieArt.inter7(g, f, ChirurgieArt.inter("Le serveur confirme l'issue."), L.cx, y + 68,
                ChirurgieArt.argb(ChirurgieArt.DOUX, 0.8f), 1);
        boolean hot = mx >= L.btnX && mx < L.btnX + 92 && my >= L.btnY && my < L.btnY + 20;
        ChirurgieArt.blit(g, ChirurgieArt.BOUTON, L.btnX, L.btnY, hot ? 92 : 0, 0, 92, 20, 184, 20);
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
        this.lastPhase = 2;
        this.layoutCache = null;
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
            if (phase == 2) { sealPos = 0.35f; pulls = 2; }
            if (phase >= 3) pulls = 2;
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
            if (phase == 2) { stitch = 3; stitchStart = frozen - 700; stitchOk[0] = true; stitchOk[2] = true; }
            if (phase >= 3) { stitch = 6; java.util.Arrays.fill(stitchOk, errors < MAX_ERRORS); }
        }
    }
}
