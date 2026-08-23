package fr.reborn.hud.menu.tirage;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * <b>Test de la feuille</b> — le tirage de nature de chakra, façon gacha Zenkai.
 *
 * <p>Boucle addictive en 6 temps : canalisation du chakra dans la feuille
 * (build-up + suspense croissant), flash, révélation animée de la carte de
 * nature (assets Zenkai authentiques), palier de rareté (aura/rayons/écran),
 * jauge de pitié (garantie légendaire), puis boutons <b>Relancer</b> /
 * <b>Confirmer</b>. Purement client — aucun état serveur : c'est un prototype
 * de FEEL à valider en jeu (ouvert par la touche configurable, défaut F).
 *
 * <p>La rareté n'altère PAS la nature obtenue (les 5 natures restent
 * équiprobables, fidèle au lore) : c'est la <i>puissance de manifestation</i>
 * de l'affinité qui est tirée, pour le frisson gacha sans casser l'identité RP.
 */
public class TirageScreen extends Screen {

    // ─────────── Réglages (tunables) ───────────
    private static final long CHANNEL_MS = 1700;   // durée de la canalisation auto
    private static final long SUSPENSE_MS = 560;   // pull-in avant le flash
    private static final long FLASH_MS = 260;      // flash blanc
    private static final long REVEAL_MS = 720;     // entrée de la carte

    private static final int PITY_EPIC = 30;       // épique garanti après N sans épique+
    private static final int PITY_LEGEND = 70;     // légendaire garanti après N sans légendaire

    // ─────────── Persistance inter-tirages (statique) ───────────
    private static int pullsSinceEpic = 0;
    private static int pullsSinceLegend = 0;
    private static int totalPulls = 0;

    // ─────────── État courant ───────────
    private enum Phase { IDLE, CHANNELING, SUSPENSE, FLASH, REVEAL, RESULT }

    private Phase phase = Phase.IDLE;
    private long phaseStart = now();
    private Nature rolledNature;
    private Rarity rolledRarity;
    private boolean crossedHalf; // son d'escalade joué une fois

    private final Random rng = new Random();

    private boolean prevHudHidden;
    private boolean hudCaptured = false;

    public TirageScreen() {
        super(Component.literal("Test de la feuille"));
    }

    private static long now() { return System.currentTimeMillis(); }

    private void setPhase(Phase p) {
        phase = p;
        phaseStart = now();
    }

    /** t normalisé (0..1) dans la phase courante pour une durée donnée. */
    private float pt(long durMs) {
        return Math.min(1f, (now() - phaseStart) / (float) durMs);
    }

    private float elapsed() { return (now() - phaseStart) / 1000f; }

    // ─────────── Cycle de vie ───────────
    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!hudCaptured && mc.gui != null) {
            prevHudHidden = mc.gui.hud.isHidden();
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
            hudCaptured = true;
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (hudCaptured && mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHudHidden);
            hudCaptured = false;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ─────────── Logique temporelle ───────────
    @Override
    public void tick() {
        switch (phase) {
            case CHANNELING -> {
                float p = pt(CHANNEL_MS);
                if (!crossedHalf && p >= 0.5f) {
                    crossedHalf = true;
                    playSound(SoundEvents.BEACON_POWER_SELECT, 1.35f, 0.6f);
                }
                if (p >= 1f) { setPhase(Phase.SUSPENSE); playSuspenseSound(); }
            }
            case SUSPENSE -> { if (pt(SUSPENSE_MS) >= 1f) { setPhase(Phase.FLASH); onReveal(); } }
            case FLASH -> { if (pt(FLASH_MS) >= 1f) setPhase(Phase.REVEAL); }
            case REVEAL -> { if (pt(REVEAL_MS) >= 1f) setPhase(Phase.RESULT); }
            default -> { }
        }
    }

    /** Démarre une canalisation : roll immédiat (caché), suspense jusqu'au flash. */
    private void startChannel() {
        if (phase != Phase.IDLE && phase != Phase.RESULT) return;
        roll();
        crossedHalf = false;
        setPhase(Phase.CHANNELING);
        playSound(SoundEvents.BEACON_ACTIVATE, 0.7f, 0.7f);
    }

    private void roll() {
        totalPulls++;
        pullsSinceEpic++;
        pullsSinceLegend++;
        rolledNature = Nature.values()[rng.nextInt(Nature.values().length)];

        Rarity r;
        if (pullsSinceLegend >= PITY_LEGEND) {
            r = Rarity.LEGENDAIRE;
        } else {
            double x = rng.nextDouble();
            if (x < 0.015) r = Rarity.LEGENDAIRE;
            else if (x < 0.08) r = Rarity.EPIQUE;
            else if (x < 0.32) r = Rarity.RARE;
            else r = Rarity.COMMUN;
            if (r.ordinal() < Rarity.EPIQUE.ordinal() && pullsSinceEpic >= PITY_EPIC) r = Rarity.EPIQUE;
        }
        if (r == Rarity.LEGENDAIRE) { pullsSinceLegend = 0; pullsSinceEpic = 0; }
        else if (r == Rarity.EPIQUE) { pullsSinceEpic = 0; }
        rolledRarity = r;
    }

    private void onReveal() {
        Rarity r = rolledRarity;
        switch (r) {
            case COMMUN -> playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.8f);
            case RARE -> {
                playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 0.9f);
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.5f);
            }
            case EPIQUE -> playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
            case LEGENDAIRE -> {
                playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.5f, 0.5f);
                playSound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
            }
        }
    }

    private void playSuspenseSound() {
        playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8f, 0.6f);
    }

    private void playSound(SoundEvent ev, float pitch, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && ev != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
        }
    }

    // ─────────── Rendu ───────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Fond plein opaque (masque le monde) + halo radial chaud au centre.
        ctx.fillGradient(0, 0, this.width, this.height, 0xFF0A0507, 0xFF16090C);
        int cx = this.width / 2, cy = (int) (this.height * 0.46f);
        int tint = phase.ordinal() >= Phase.REVEAL.ordinal() && rolledRarity != null
            ? rolledRarity.color : Colors.ACCENT;
        // Halo doux (peu de bandes pour rester léger).
        for (int i = 5; i >= 1; i--) {
            int rad = 120 + i * 55;
            ctx.fill(cx - rad, cy - rad, cx + rad, cy + rad, Colors.withAlpha(tint, 0.04f));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font f = this.font;
        int cx = this.width / 2;
        int cy = (int) (this.height * 0.46f);

        // Tremblement d'écran (canalisation/suspense) — plus fort quand ça monte.
        float shakeMag = 0f;
        if (phase == Phase.CHANNELING) shakeMag = 1.5f + pt(CHANNEL_MS) * 4f;
        else if (phase == Phase.SUSPENSE) shakeMag = 6f + pt(SUSPENSE_MS) * 5f;
        int sx = 0, sy = 0;
        if (shakeMag > 0.1f) {
            double tms = now();
            sx = (int) Math.round(Math.sin(tms * 0.13) * shakeMag);
            sy = (int) Math.round(Math.cos(tms * 0.17) * shakeMag * 0.7);
        }
        ctx.pose().pushMatrix();
        ctx.pose().translate(sx, sy);

        drawMotes(ctx, cx, cy);

        // Titre (haut).
        Component title = RebornFont.arcade("TEST DE LA FEUILLE");
        drawScaled(ctx, f, title, cx, 30, 1.8f, Colors.GOLD, true);
        Component sub = RebornFont.arcade("Revele ta nature de chakra");
        ctx.text(f, sub, cx - f.width(sub) / 2, 58, Colors.FOREGROUND_SUBTLE, false);

        switch (phase) {
            case IDLE -> drawIdle(ctx, f, cx, cy, mouseX, mouseY);
            case CHANNELING -> drawChannel(ctx, cx, cy);
            case SUSPENSE -> drawChannel(ctx, cx, cy);
            case FLASH -> { drawChannel(ctx, cx, cy); }
            case REVEAL, RESULT -> drawReveal(ctx, f, cx, cy, mouseX, mouseY);
        }

        drawPity(ctx, f);

        ctx.pose().popMatrix();

        // Flash blanc au-dessus de tout (jamais tremblé).
        if (phase == Phase.FLASH) {
            float a = 1f - pt(FLASH_MS);
            ctx.fill(0, 0, this.width, this.height, Colors.withAlpha(0xFFFFFFFF, a));
        }
    }

    // ── Feuille + canalisation ──────────────────────────────
    private void drawPaper(GuiGraphicsExtractor ctx, int cx, int cy, float glow, int glowColor) {
        int pw = 92, ph = 122;
        int bob = (int) (Math.sin(now() * 0.003) * 4);
        int px = cx - pw / 2, py = cy - ph / 2 + bob;
        // Lueur derrière la feuille (croît avec la charge).
        if (glow > 0.01f) {
            for (int i = 6; i >= 1; i--) {
                int g = i * 6;
                ctx.fill(px - g, py - g, px + pw + g, py + ph + g,
                    Colors.withAlpha(glowColor, 0.05f * glow));
            }
        }
        // Parchemin (ivoire, léger dégradé).
        DrawHelpers.roundedRect(ctx, px, py, pw, ph, 4, 0xFFEFE4C8);
        ctx.fillGradient(px + 2, py + 2, px + pw - 2, py + ph - 2, 0xFFF6ECD4, 0xFFD9C7A0);
        // Pli central + liseré.
        ctx.fill(cx - 1, py + 8, cx + 1, py + ph - 8, 0x33000000);
        DrawHelpers.outlinedRect(ctx, px, py, pw, ph, 0, Colors.withAlpha(0xFF7A5A2E, 0.7f));
        // Kanji « chakra » stylisé (traits) qui s'imprègne de chakra à la charge.
        int ink = Colors.lerp(0x66403020, glowColor, glow);
        DrawHelpers.thickLine(ctx, cx - 20, py + 34, cx + 20, py + 34, 3, ink);
        DrawHelpers.thickLine(ctx, cx, py + 26, cx, py + ph - 26, 3, ink);
        DrawHelpers.thickLine(ctx, cx - 16, py + 60, cx + 16, py + 84, 3, ink);
        DrawHelpers.thickLine(ctx, cx + 16, py + 60, cx - 16, py + 84, 3, ink);
    }

    private void drawChannel(GuiGraphicsExtractor ctx, int cx, int cy) {
        float charge = phase == Phase.CHANNELING ? pt(CHANNEL_MS) : 1f;
        int chakra = 0xFF4FC3F7; // bleu chakra
        drawPaper(ctx, cx, cy, charge, chakra);

        // Anneau de charge autour de la feuille.
        int ringR = 92;
        DrawHelpers.ring(ctx, cx, cy, ringR, 2, Colors.withAlpha(0xFFFFFFFF, 0.12f));
        arc(ctx, cx, cy, ringR, 3, -90, charge * 360f, chakra);
        // Anneau tournant pointillé (énergie).
        float rot = (now() % 3600) / 3600f * 360f;
        DrawHelpers.dashedRing(ctx, cx, cy, ringR + 8, 2, Colors.withAlpha(chakra, 0.5f), 10, 14, rot);

        // Éclairs de chakra qui flashent autour (nombre ∝ charge).
        int bolts = 3 + (int) (charge * 7);
        for (int i = 0; i < bolts; i++) {
            double ang = i * (Math.PI * 2 / bolts) + now() * 0.004;
            float flick = (float) (0.4 + 0.6 * Math.abs(Math.sin(now() * 0.02 + i)));
            int r2 = ringR + 14 + (int) (Math.sin(now() * 0.01 + i) * 6);
            int bx = cx + (int) (Math.cos(ang) * r2) - 12;
            int by = cy + (int) (Math.sin(ang) * r2) - 3;
            ctx.pose().pushMatrix();
            ctx.pose().translate(bx + 12, by + 3);
            ctx.pose().rotate((float) ang);
            ctx.pose().translate(-12, -3);
            ctx.blit(RenderPipelines.GUI_TEXTURED, CHAKRA_BOLT, 0, 0, 0f, 0f, 24, 6, 132, 30, 132, 30,
                Colors.withAlpha(chakra, flick));
            ctx.pose().popMatrix();
        }

        // Libellé de progression.
        Component pct = RebornFont.arcade("CANALISATION " + (int) (charge * 100) + "%");
        int py2 = cy + 92;
        ctx.text(this.font, pct, cx - this.font.width(pct) / 2, py2, Colors.FOREGROUND, false);
    }

    // ── Révélation ──────────────────────────────────────────
    private void drawReveal(GuiGraphicsExtractor ctx, Font f, int cx, int cy, int mouseX, int mouseY) {
        Rarity r = rolledRarity;
        Nature n = rolledNature;
        if (r == null || n == null) return;

        float in = phase == Phase.REVEAL ? pt(REVEAL_MS) : 1f;
        // Assombrit légèrement le fond pour faire ressortir la carte.
        ctx.fill(0, 0, this.width, this.height, Colors.withAlpha(0xFF000000, 0.35f * in));

        // Rayons de lumière tournants derrière la carte (rareté).
        drawRays(ctx, cx, cy, r, in);

        // Carte de nature (asset Zenkai) avec entrée en overshoot.
        float scale = overshoot(in);
        int drawH = (int) (this.height * 0.62f);
        int drawW = (int) (drawH * (638f / 614f));
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(scale, scale);
        ctx.blit(RenderPipelines.GUI_TEXTURED, n.tex, -drawW / 2, -drawH / 2, 0f, 0f,
            drawW, drawH, 638, 614, 638, 614);
        ctx.pose().popMatrix();

        // Étincelles jaillissantes (rareté).
        drawSparks(ctx, cx, cy, r, in);

        if (phase == Phase.RESULT) {
            // Bandeau de rareté sous la carte.
            Component lbl = RebornFont.arcade(r.label);
            drawScaled(ctx, f, lbl, cx, cy + drawH / 2 - 6, 2.0f, r.color, true);

            // Boutons Relancer / Confirmer.
            drawButtons(ctx, f, mouseX, mouseY);
        }
    }

    private void drawRays(GuiGraphicsExtractor ctx, int cx, int cy, Rarity r, float in) {
        int count = r.rays;
        float rot = (now() % 20000) / 20000f * 360f;
        int len = (int) (Math.max(this.width, this.height) * 0.75f);
        int col = Colors.withAlpha(r.color, 0.18f * in);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().rotate((float) Math.toRadians(rot));
        for (int i = 0; i < count; i++) {
            ctx.pose().rotate((float) (Math.PI * 2 / count));
            // Triangle-ish ray = ligne épaisse depuis le centre.
            DrawHelpers.thickLine(ctx, 0, 0, len, 0, 3, col);
        }
        ctx.pose().popMatrix();
        // Cœur lumineux.
        DrawHelpers.disc(ctx, cx, cy, (int) (26 * in), Colors.withAlpha(r.color, 0.25f * in));
    }

    private void drawSparks(GuiGraphicsExtractor ctx, int cx, int cy, Rarity r, float in) {
        int count = r.sparks;
        long seed = 0x5EED;
        for (int i = 0; i < count; i++) {
            double ang = i * 2.399963 + (seed % 7); // golden angle
            float prog = Math.min(1f, in * 1.4f - (i % 5) * 0.03f);
            if (prog <= 0) continue;
            int dist = (int) (prog * (100 + (i * 37 % 90)));
            int sxp = cx + (int) (Math.cos(ang) * dist);
            int syp = cy + (int) (Math.sin(ang) * dist);
            float a = (1f - prog) * in;
            int sz = 2 + (i % 3);
            DrawHelpers.disc(ctx, sxp, syp, sz, Colors.withAlpha(0xFFFFFFFF, a * 0.9f));
            DrawHelpers.disc(ctx, sxp, syp, sz + 1, Colors.withAlpha(r.color, a * 0.5f));
        }
    }

    // ── Idle ────────────────────────────────────────────────
    private void drawIdle(GuiGraphicsExtractor ctx, Font f, int cx, int cy, int mouseX, int mouseY) {
        drawPaper(ctx, cx, cy, 0.15f + 0.1f * (float) Math.sin(now() * 0.004), 0xFF4FC3F7);
        DrawHelpers.ring(ctx, cx, cy, 92, 2, Colors.withAlpha(0xFF4FC3F7, 0.18f));

        // Prompt pulsant.
        float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(now() * 0.004));
        Component prompt = RebornFont.arcade("[ ESPACE ] CANALISE TON CHAKRA");
        drawScaled(ctx, f, prompt, cx, cy + 92, 1.15f, Colors.withAlpha(Colors.FOREGROUND, pulse), true);
        Component hint = RebornFont.arcade("ou clique sur la feuille");
        ctx.text(f, hint, cx - f.width(hint) / 2, cy + 116, Colors.FOREGROUND_MUTED, false);
    }

    // ── Pity + boutons ──────────────────────────────────────
    private void drawPity(GuiGraphicsExtractor ctx, Font f) {
        int bw = 220, bh = 8;
        int bx = (this.width - bw) / 2;
        int by = this.height - 30;
        float frac = Math.min(1f, pullsSinceLegend / (float) PITY_LEGEND);
        DrawHelpers.roundedRect(ctx, bx, by, bw, bh, 3, Colors.withAlpha(0xFF000000, 0.5f));
        DrawHelpers.roundedRect(ctx, bx, by, (int) (bw * frac), bh, 3, Colors.lerp(Colors.ACCENT, Colors.GOLD, frac));
        DrawHelpers.outlinedRect(ctx, bx, by, bw, bh, 0, Colors.withAlpha(Colors.GOLD, 0.4f));
        Component pl = RebornFont.arcade("GARANTIE LEGENDAIRE  " + pullsSinceLegend + " / " + PITY_LEGEND);
        ctx.text(f, pl, this.width / 2 - f.width(pl) / 2, by - 12, Colors.FOREGROUND_SUBTLE, false);
        Component tp = RebornFont.arcade("TIRAGES : " + totalPulls);
        ctx.text(f, tp, this.width / 2 - f.width(tp) / 2, by + bh + 4, Colors.FOREGROUND_MUTED, false);
    }

    private static final int BTN_W = 150, BTN_H = 32;

    private int relancerX() { return this.width / 2 - BTN_W - 8; }
    private int confirmerX() { return this.width / 2 + 8; }
    private int btnY() { return (int) (this.height * 0.46f) + (int) (this.height * 0.62f) / 2 + 24; }

    private void drawButtons(GuiGraphicsExtractor ctx, Font f, int mouseX, int mouseY) {
        drawBtn(ctx, f, relancerX(), btnY(), "RELANCER", Colors.GOLD, mouseX, mouseY);
        drawBtn(ctx, f, confirmerX(), btnY(), "CONFIRMER", Colors.SUCCESS, mouseX, mouseY);
    }

    private void drawBtn(GuiGraphicsExtractor ctx, Font f, int x, int y, String label, int accent, int mx, int my) {
        boolean hover = mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
        int fill = hover ? Colors.withAlpha(accent, 0.30f) : Colors.withAlpha(0xFF000000, 0.5f);
        int border = hover ? accent : Colors.withAlpha(Colors.FOREGROUND, 0.35f);
        DrawHelpers.roundedOutlinedRect(ctx, x, y, BTN_W, BTN_H, 6, fill, border);
        Component t = RebornFont.arcade(label);
        ctx.text(f, t, x + (BTN_W - f.width(t)) / 2, y + (BTN_H - 8) / 2, Colors.WHITE_PURE, false);
    }

    private void drawMotes(GuiGraphicsExtractor ctx, int cx, int cy) {
        for (int i = 0; i < 22; i++) {
            float t = (now() * 0.00004f + i * 0.137f) % 1f;
            int mx = (int) ((Math.sin(i * 12.9898) * 43758.5) % this.width);
            if (mx < 0) mx += this.width;
            int my = (int) (this.height - t * this.height);
            float a = (float) (0.15 + 0.25 * Math.abs(Math.sin(now() * 0.002 + i)));
            DrawHelpers.disc(ctx, mx, my, 1, Colors.withAlpha(Colors.ACCENT, a));
        }
    }

    // ── Utilitaires de rendu ────────────────────────────────
    /** Arc partiel (progress ring) tracé point à point. */
    private void arc(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int thickness,
                     float startDeg, float sweepDeg, int color) {
        if (sweepDeg <= 0) return;
        int steps = Math.max(1, (int) (sweepDeg / 3));
        for (int i = 0; i <= steps; i++) {
            float ang = (float) Math.toRadians(startDeg + sweepDeg * i / steps);
            int px = cx + (int) (Math.cos(ang) * radius);
            int py = cy + (int) (Math.sin(ang) * radius);
            DrawHelpers.disc(ctx, px, py, thickness, color);
        }
    }

    private void drawScaled(GuiGraphicsExtractor ctx, Font f, Component c, int cx, int y,
                            float scale, int color, boolean shadow) {
        int w = f.width(c);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, y);
        ctx.pose().scale(scale, scale);
        ctx.text(f, c, -w / 2, 0, color, shadow);
        ctx.pose().popMatrix();
    }

    /** Easing overshoot (back-out) pour l'entrée de la carte. */
    private float overshoot(float t) {
        float s = 1.70158f;
        t = t - 1f;
        return 1f + (t * t * ((s + 1f) * t + s));
    }

    // ─────────── Interactions ───────────
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int k = event.key();
        if (k == GLFW.GLFW_KEY_SPACE || k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            if (phase == Phase.IDLE || phase == Phase.RESULT) { startChannel(); return true; }
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            // Ferme uniquement au repos.
            if (phase == Phase.IDLE || phase == Phase.RESULT) { onClose(); return true; }
            return true; // avale l'ESC pendant l'animation
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int mx = (int) event.x(), my = (int) event.y();
            if (phase == Phase.RESULT) {
                if (mx >= relancerX() && mx < relancerX() + BTN_W && my >= btnY() && my < btnY() + BTN_H) {
                    RebornSounds.uiClick();
                    setPhase(Phase.IDLE);
                    return true;
                }
                if (mx >= confirmerX() && mx < confirmerX() + BTN_W && my >= btnY() && my < btnY() + BTN_H) {
                    RebornSounds.confirm();
                    onClose();
                    return true;
                }
            }
            if (phase == Phase.IDLE) { startChannel(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    // ─────────── Données ───────────
    private static final Identifier CHAKRA_BOLT =
        Identifier.fromNamespaceAndPath("reborn", "textures/gacha/chakra_bolt.png");

    private enum Nature {
        KATON("katon"), SUITON("suiton"), RAITON("raiton"), FUTON("futon"), DOTON("doton");
        final Identifier tex;
        Nature(String slug) {
            this.tex = Identifier.fromNamespaceAndPath("reborn", "textures/gacha/nature/" + slug + ".png");
        }
    }

    private enum Rarity {
        // Palette de rareté AUTHENTIQUE Zenkai (RARITY_COLORS de kg_config.lua).
        COMMUN("AFFINITE COMMUNE", 0xFF9D9D9D, 10, 18),
        RARE("AFFINITE RARE", 0xFF0070DD, 16, 34),
        EPIQUE("AFFINITE EPIQUE", 0xFFA335EE, 24, 60),
        LEGENDAIRE("AFFINITE LEGENDAIRE", 0xFFFF8000, 40, 110);
        final String label;
        final int color;
        final int rays;
        final int sparks;
        Rarity(String label, int color, int rays, int sparks) {
            this.label = label; this.color = color; this.rays = rays; this.sparks = sparks;
        }
    }
}
