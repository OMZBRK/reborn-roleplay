package fr.reborn.hud.combat;

import fr.reborn.hud.anchor.TeteAncre;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

import java.util.List;

/**
 * HUD combat taïjutsu (rendu 2D par-dessus le HUD), dans la DA Reborn (encre, laque, or), alimenté par
 * {@link CombatState} :
 * <ul>
 *   <li><b>Dégâts infligés</b> : petits chiffres projetés au-dessus des cibles, trois paliers discrets (ivoire,
 *       or, laque), empilés en quinconce, avec léger pop et montée.</li>
 *   <li><b>Endurance</b> : un ensō (cercle zen au pinceau) autour du viseur en combat ; or = restant, laque =
 *       juste dépensé, bleu acier en garde, pulsation rouge à vide.</li>
 *   <li><b>Combo</b> : petit total cumulé à droite du viseur.</li>
 * </ul>
 * Projection monde→écran par {@link TeteAncre} (tête rendue, projection identique au monde ; aucun rendu 3D).
 */
public final class CombatHud {

    private CombatHud() {}

    // Petit anneau discret AUTOUR du viseur Reborn (le vrai curseur reste géré par
    // CrosshairManager / l'éditeur de viseur). Volontairement fin et compact.
    private static final int RING_RADIUS = 7;
    private static final int ENSO_RADIUS = 9;

    private static final int INK = 0xFF140A06, IVORY = 0xFFF5E9D0, GOLD = 0xFFD9A95E, GOLD_HI = 0xFFF2D49A;
    private static final int LACQUER = 0xFFA0182B, LACQUER_HI = 0xFFC01E35, LACQUER_LO = 0xFF7A1322;
    private static final int STEEL = 0xFF9FC3E6;


    /** Banc d'essai : force la garde (pas d'entrée serveur en solo). */
    static boolean debugGuard = false;

    /**
     * Chiffres de dégâts : marge au-dessus du sommet de la tête rendue (blocs). 0,413 redonne l'ancien « œil + 0,7 »
     * d'un joueur debout ; la tête est désormais suivie à terre, allongée et en émote.
     */
    private static final float MARGE_DEGATS = 0.413f;
    /** Ancre réutilisée (thread de rendu) : aucune allocation par indicateur. */
    private static final Vector3f ANCRE = new Vector3f();
    /**
     * Textes composés une fois (MOD_HUD §6, aucune allocation par image) : chiffres de dégâts de 0 à 999 (au premier
     * usage de chaque valeur), total du combo (recomposé quand il change) et le mot « COMBO ».
     */
    private static final Component[] DEGATS = new Component[1000];
    private static Component cCombo, cComboTotal;
    private static int comboTotal;

    /** LUT d'angles par géométrie (rOut,rIn) pour l'anneau : atan2 + test de bande
     *  constants à géométrie fixe (NaN = hors bande) → 1 calcul/géométrie, pas par frame. */
    private static final java.util.Map<Long, float[]> RING_ANGLE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options == null || mc.gui.hud.isHidden()) return;
        if (mc.gui.screen() != null) return;

        Font font = mc.font;
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        int cx = gw / 2, cy = gh / 2;
        long now = System.currentTimeMillis();
        CombatState st = CombatState.INSTANCE;

        // ── Damage indicators (projetés au-dessus des cibles) ──
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam != null && cam.isInitialized()) {
            List<CombatState.DamageIndicator> live = st.liveIndicators(now);
            for (int i = 0, n = live.size(); i < n; i++) {
                CombatState.DamageIndicator ind = live.get(i);
                Entity e = mc.level.getEntity(ind.entityId);
                if (e == null) continue;
                float age = (now - ind.spawnMs) / (float) CombatState.DMG_LIFE_MS; // 0..1
                // Tête rendue et interpolée (TeteAncre : à terre, allongé, émotes ; hauteur des yeux pour un mob),
                // projetée comme le monde, calée au pixel physique : plus de saccades au tick.
                if (!TeteAncre.ecran(e, MARGE_DEGATS, ANCRE)) continue;
                float sx = ANCRE.x + ind.dx;
                float rise = (1f - (1f - age) * (1f - age)) * 12f;           // ease-out
                float sy = ANCRE.y + ind.dy - rise;
                float pop = age < 0.12f ? 1f + (1f - age / 0.12f) * (1f - age / 0.12f) * 0.55f : 1f;
                float alpha = age < 0.7f ? 1f : Math.max(0f, 1f - (age - 0.7f) / 0.3f);
                drawDamage(ctx, font, sx, sy, (int) Math.round(ind.amount), pop, alpha, age);
            }
        }

        // ── Endurance (ensō) + combo, seulement en combat ou en garde ──
        boolean guard = debugGuard || CombatInput.INSTANCE.isBlocking();
        float rA = Math.max(st.combatModeAlpha(now), guard ? 1f : 0f);
        if (rA > 0.01f) {
            float frac = st.staminaFraction();
            float trail = st.trailFraction(now);
            int fill = guard ? STEEL : GOLD;
            if (frac < 0.2f) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(now / 90.0);
                fill = lerp(GOLD, 0xFFFF5A48, pulse);
            }
            float ea = rA * 0.8f;
            BrushRing.enso(ctx, cx, cy, ENSO_RADIUS, frac, trail,
                applyAlpha(fill, ea), applyAlpha(LACQUER, ea * 0.85f), applyAlpha(IVORY, ea * 0.14f));

            float comboA = st.comboAlpha(now) * rA;
            if (comboA > 0.01f) drawCombo(ctx, font, cx + ENSO_RADIUS + 6, cy - 5, (int) Math.round(st.comboTotal()), comboA * 0.85f);
        }

        // ── Flash de parade timée (deflect) ──
        float pf = st.parryFlashAlpha(now);
        if (pf > 0.01f) {
            if (st.parryRole() == 0) {
                // Deflect réussi : anneau ivoire qui s'étend en s'estompant.
                int r = ENSO_RADIUS + 3 + Math.round((1f - pf) * 12f);
                ringBand(ctx, cx, cy, r, 2, 0f, 360f, applyAlpha(0xFFF5E9D0, pf));
            } else {
                // Fait parer (ouverture subie) : voile laque léger plein écran.
                ctx.fill(0, 0, gw, gh, applyAlpha(0x407A1322, pf * 0.8f));
            }
        }

        // La barre d'endurance est un ÉLÉMENT HUD DÉPLAÇABLE (cf. renderEnduranceBar
        // + registre "combat-endurance" dans RebornHudClient) → plus rendue ici au centre.
    }

    private static final int BAR_W = 90, BAR_H = 5;

    /**
     * Barre d'endurance de combat rendue à une position HUD (élément déplaçable via
     * l'éditeur). Visible seulement en combat ou pendant la garde. {@code (x,y)} =
     * coin haut-gauche ; {@code scale} depuis l'état HUD.
     */
    public static void renderEnduranceBar(GuiGraphicsExtractor ctx, int x, int y, float scale) {
        CombatState st = CombatState.INSTANCE;
        boolean blocking = CombatInput.INSTANCE.isBlocking();
        float barA = Math.max(st.combatModeAlpha(System.currentTimeMillis()), blocking ? 1f : 0f);
        if (barA <= 0.01f) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        if (scale != 1f) ctx.pose().scale(scale, scale);
        drawEnduranceBar(ctx, 0, 0, st.staminaFraction(), barA, blocking);
        ctx.pose().popMatrix();
    }

    /** Barre d'endurance DA : réglette laquée cerclée de bois, remplissage or (acier en garde), dépense en laque. */
    private static void drawEnduranceBar(GuiGraphicsExtractor ctx, int x, int y,
                                         float frac, float alpha, boolean blocking) {
        long now = System.currentTimeMillis();
        float trail = CombatState.INSTANCE.trailFraction(now);
        ctx.fill(x - 2, y - 2, x + BAR_W + 2, y + BAR_H + 2, applyAlpha(0xFF2E1C11, alpha));
        ctx.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, applyAlpha(0xFF5A3A22, alpha));
        ctx.fill(x, y, x + BAR_W, y + BAR_H, applyAlpha(0xFF120A08, alpha));
        int tw = Math.round(BAR_W * Math.max(0f, Math.min(1f, trail)));
        if (tw > 0) ctx.fill(x, y, x + tw, y + BAR_H, applyAlpha(LACQUER_HI, alpha));
        int fw = Math.round(BAR_W * Math.max(0f, Math.min(1f, frac)));
        int c = blocking ? STEEL : frac < 0.2f ? LACQUER_HI : GOLD;
        if (fw > 0) {
            ctx.fill(x, y, x + fw, y + BAR_H, applyAlpha(c, alpha));
            ctx.fill(x, y, x + fw, y + 1, applyAlpha(0x60FFFFFF, alpha));
        }
        for (int k = 1; k < 4; k++) ctx.fill(x + BAR_W * k / 4, y, x + BAR_W * k / 4 + 1, y + BAR_H, applyAlpha(0x802E1C11, alpha));
    }

    /** Total du combo : petit chiffre or, « combo » en ivoire dessous, simple ombre d'encre. */
    private static void drawCombo(GuiGraphicsExtractor ctx, Font font, int x, int y, int total, float a) {
        if (cComboTotal == null || total != comboTotal) {
            comboTotal = total;
            cComboTotal = RebornFont.bold(String.valueOf(total));
        }
        if (cCombo == null) cCombo = RebornFont.body("COMBO");
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(0.85f, 0.85f);
        shadowed(ctx, font, cComboTotal, 0, 0, applyAlpha(GOLD_HI, a), applyAlpha(INK, a * 0.7f));
        ctx.pose().popMatrix();
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y + 8);
        ctx.pose().scale(0.5f, 0.5f);
        shadowed(ctx, font, cCombo, 0, 0, applyAlpha(IVORY, a * 0.8f), applyAlpha(INK, a * 0.6f));
        ctx.pose().popMatrix();
    }

    /** Chiffre de dégâts composé une fois par valeur (0 à 999 ; au-delà, composé à la demande). */
    private static Component degats(int v) {
        if (v < 0 || v >= DEGATS.length) return RebornFont.bold(String.valueOf(v));
        Component c = DEGATS[v];
        if (c == null) DEGATS[v] = c = RebornFont.bold(String.valueOf(v));
        return c;
    }

    /** Texte avec une ombre d'encre décalée d'un pixel (discret, sans contour). */
    private static void shadowed(GuiGraphicsExtractor ctx, Font font, Component t, int x, int y, int fill, int shadow) {
        ctx.text(font, t, x + 1, y + 1, shadow, false);
        ctx.text(font, t, x, y, fill, false);
    }

    /**
     * Chiffre de dégâts, discret : ivoire (coup léger), or (moyen), laque (lourd), à peine plus grand d'un palier à
     * l'autre. Ombre d'encre, léger pop à l'impact.
     */
    private static void drawDamage(GuiGraphicsExtractor ctx, Font font, float cx, float cy,
                                   int amount, float scale, float alpha, float age) {
        if (alpha <= 0f) return;
        int tier = amount >= 15 ? 2 : amount >= 8 ? 1 : 0;
        float s = (1f + (scale - 1f) * 0.5f) * (tier == 2 ? 1.05f : tier == 1 ? 0.9f : 0.75f);
        float a = alpha * 0.9f;
        Component t = degats(amount);
        int w = font.width(t);
        int fill = tier == 2 ? 0xFFE0414F : tier == 1 ? GOLD_HI : IVORY;
        ctx.pose().pushMatrix();
        ctx.pose().translate(DaKit.cale(cx - (w * s) / 2f), DaKit.cale(cy));   // calé au pixel physique
        ctx.pose().scale(s, s);
        shadowed(ctx, font, t, 0, 0, applyAlpha(fill, a), applyAlpha(INK, a * 0.75f));
        ctx.pose().popMatrix();
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
            | Math.round(ab + (bb - ab) * t);
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Arc d'anneau [startDeg, startDeg+sweepDeg], horaire depuis le HAUT. */
    private static void ringBand(GuiGraphicsExtractor ctx, int cx, int cy, int radius,
                                 int thickness, float startDeg, float sweepDeg, int color) {
        fr.reborn.hud.ui.LotGui lot = fr.reborn.hud.ui.LotGui.ouvrir(ctx);   // 1 élément GUI (cf. LotGui)
        if (sweepDeg <= 0f) return;
        int rOut = radius, rIn = Math.max(0, radius - thickness);
        float[] ang = ringAngleLut(rOut, rIn);   // angles précalculés (NaN hors bande)
        int span = 2 * rOut + 1;
        // Batch : une span horizontale par run contigu au lieu d'un fill 1×1/pixel.
        for (int dy = -rOut; dy <= rOut; dy++) {
            int base = (dy + rOut) * span;
            int runStart = Integer.MIN_VALUE;
            for (int dx = -rOut; dx <= rOut; dx++) {
                float a = ang[base + dx + rOut];
                boolean on;
                if (Float.isNaN(a)) {
                    on = false;
                } else {
                    float rel = a - startDeg;
                    if (rel < 0) rel += 360f;
                    on = rel <= sweepDeg;
                }
                if (on) {
                    if (runStart == Integer.MIN_VALUE) runStart = dx;
                } else if (runStart != Integer.MIN_VALUE) {
                    lot.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, color);
                    runStart = Integer.MIN_VALUE;
                }
            }
            if (runStart != Integer.MIN_VALUE) {
                lot.fill(cx + runStart, cy + dy, cx + rOut + 1, cy + dy + 1, color);
            }
        }
        lot.fermer();
    }

    /** Angles (deg, 0 en haut, horaire) de l'anneau [rIn,rOut] ; NaN hors bande. 1 calcul/géométrie. */
    private static float[] ringAngleLut(int rOut, int rIn) {
        long key = ((long) rOut << 32) | (rIn & 0xffffffffL);
        return RING_ANGLE.computeIfAbsent(key, k -> {
            int span = 2 * rOut + 1;
            float[] a = new float[span * span];
            int rOutSq = rOut * rOut, rInSq = rIn * rIn;
            for (int dy = -rOut; dy <= rOut; dy++) {
                for (int dx = -rOut; dx <= rOut; dx++) {
                    int idx = (dy + rOut) * span + (dx + rOut);
                    int d2 = dx * dx + dy * dy;
                    if (d2 > rOutSq || d2 < rInSq) {
                        a[idx] = Float.NaN;
                    } else {
                        float angle = (float) Math.toDegrees(Math.atan2(dx, -dy));
                        if (angle < 0) angle += 360f;
                        a[idx] = angle;
                    }
                }
            }
            return a;
        });
    }
}
