package fr.reborn.hud.runtime;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.tablist.TabEntry;
import fr.reborn.hud.menu.tablist.TablistData;
import fr.reborn.hud.voice.VoiceState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.io.InputStream;

/**
 * Panneau RP « vitals » (façon Orizon, cf {@code hudvitalsrp.png}) en
 * haut-gauche : <b>tête</b> du joueur (bordure dorée + badge « Nv X »), <b>nom</b>,
 * et 3 barres arrondies — <b>vie</b> (rouge, cur/max), <b>chakra</b> (bleu,
 * cur/max) + icônes voix, <b>stamina</b> (dorée, %).
 *
 * <p>Data lue côté client : vie/chakra = valeurs RP serveur-authoritative en
 * DIRECT via {@link fr.reborn.hud.runtime.VitalsFeed} (canal {@code reborn:vitals},
 * poussé ~5×/s par ShinobiCore), fallback sur le bloc {@code self} du tablist
 * ({@link fr.reborn.hud.menu.tablist.TablistData}) puis vanilla hors serveur ;
 * stamina = faim (déjà en direct) ; état <b>voix</b> (portée = couleur du micro,
 * parle/mute/assourdi) relu à chaque tick par {@link fr.reborn.hud.voice.VoiceState}
 * (PlasmoVoice).
 *
 * <p><b>Compact</b> (Game Plan n° 18, 05/11) : hors combat, la tête seule dans le cadre du bloc réduit de moitié, un
 * filet de vie de 4 px au-dessus, un de chakra dessous, le micro à droite ; le bloc complet revient en combat, avec un
 * morph (états et minuteurs dans {@link VitalsMode}). La boîte de l'élément reste celle du bloc complet.
 */
public final class VitalsHud {

    public static final int WIDTH = 210;
    public static final int HEIGHT = 56;

    private static final int GOLD     = 0xFFE8C34A;
    private static final int HP_COLOR = 0xFFDC3B3B;
    private static final int CK_COLOR = 0xFF3E8FE0;
    private static final int ST_COLOR = 0xFFD9A95E;
    private static final int BAR_BG   = 0xE0140D0A;
    private static final int WHITE    = 0xFFF2F2F2;

    private static final int HEAD = 46;

    /**
     * Couleur du micro = portée de la voix (décision du 31/10), la palette des barres : vert = la plus petite portée
     * (chuchoter), or = intermédiaire (parler), rouge = la plus grande (crier). Gris = indisponible (aucune liaison
     * vocale : Plasmo Voice absent, serveur sans voix, UDP bloqué).
     */
    public static final int MIC_VERT = 0xFF3FE05A, MIC_JAUNE = GOLD, MIC_ROUGE = HP_COLOR, MIC_GRIS = 0xFF9A948C;
    /**
     * « Je parle » : la pulsation tire vers une variante plus lumineuse de la <b>même</b> teinte (plus vers le blanc, qui
     * délavait l'or en beige et se lisait « atténué ») — la portée reste lisible pendant qu'on parle.
     */
    private static final int MIC_VERT_VIF = 0xFF7CFF8E, MIC_JAUNE_VIF = 0xFFFFE07A, MIC_ROUGE_VIF = 0xFFFF6B6B;
    /** Micro coupé, rendu muet, assourdi : les icônes de l'équipe, éteintes (légèrement grisées). */
    private static final int ICONE_ETEINTE = 0xFFCFCAC2;
    /** Le micro dans le panneau (unités du panneau, avant son échelle) : bout droit de la ligne du chakra. */
    public static final int MIC_TAILLE = 10;
    public static final int MIC_X = (3 + HEAD + 7) + (WIDTH - (3 + HEAD + 7) - 3) - 24 + 4;
    public static final int MIC_Y = 14 + 11 + 3 + (11 - MIC_TAILLE) / 2;

    // Caches de libellés : les vitals RP sont poussés par le réseau (pas modifiés par frame) → on ne reconstruit les
    // chaînes que quand la valeur sous-jacente change.
    private static String cNameStr;
    private static int cHpCur = Integer.MIN_VALUE, cHpMax; private static String cHpLabel;
    private static int cCkCur = Integer.MIN_VALUE, cCkMax; private static String cCkLabel;
    private static int cStPct = Integer.MIN_VALUE; private static String cStLabel;

    // ─── Valeurs courantes ({@link #lire}) ───
    private static int hpCur, hpMax = 1, ckCur, ckMax = 1, source;
    private static float stVal;

    // ─── Compact (Game Plan n° 18, 05/11) ───
    /** Cadre nuit (le même que le bloc), piste vide, liseré or éteint à 55 % (greffe C), vie basse (greffe A). */
    private static final int CADRE_NUIT = 0xFF181008;
    private static final int RIM = 0x8C8E6A30, RIM_ST = 0xA68E6A30;
    private static final int CORE_DANGER = 0xE0581414, RIM_DANGER = 0xD0B83434;
    private static final int HP_VIF = 0xFFFF6B6B;
    private static final int REFLET = 0x30FFFFFF;
    /**
     * Cotes de la maquette en px 1080p (échelle GUI 2, élément à 100 %), depuis l'ancre de l'élément. Le compact est
     * recalculé en pixels écran pour l'échelle courante (tête au multiple de 8 le plus proche, traits d'au moins 1 px) ;
     * le bloc, lui, est le bloc d'avant (unités du panneau × 2).
     */
    private static final float F_CADRE = 100f, F_CX = 2f, F_CY = 2f, F_R = 12f, F_BD = 2f;
    private static final float F_VIE_X = 112f, F_VIE_Y = 28f, F_VIE_W = 302f, F_BAR_H = 22f;
    private static final float F_CK_Y = 56f, F_CK_W = 254f, F_ST_Y = 84f;
    private static final float F_MIC_X = 374f, F_MIC_Y = 56f, F_MIC_S = 20f;

    /** Gabarit du compact en pixels écran, recalculé quand l'échelle change. */
    private static float gabF = -1f;
    private static int cB, cH, cS, cR, cFh, cSh, cFx, cVy, cFy, cCy, cSy, cMs, cMx, cMy;

    // Icônes voix (16×16) faites par l'équipe — mic/casque on/off.
    private static final Identifier MIC_ON     = Identifier.fromNamespaceAndPath("reborn", "textures/hud/mic_on.png");
    private static final Identifier MIC_OFF    = Identifier.fromNamespaceAndPath("reborn", "textures/hud/mic_off.png");
    private static final Identifier CASQUE_ON  = Identifier.fromNamespaceAndPath("reborn", "textures/hud/casque_on.png");
    private static final Identifier CASQUE_OFF = Identifier.fromNamespaceAndPath("reborn", "textures/hud/casque_off.png");
    /** Copie éclaircie de {@link #MIC_ON} (texture dynamique, créée une fois) : c'est elle qu'on teinte. */
    private static final Identifier MIC_CLAIR = Identifier.fromNamespaceAndPath("reborn-hud", "dynamic/mic_clair");
    /** 0 = à créer, 1 = prête, -1 = échec (repli : l'icône d'origine). */
    private static int micClairEtat;
    /** « Je parle », lissé (0 → 1) : l'activation à la voix clignote, la pulsation monte et retombe en douceur. */
    private static float parleNiv;
    private static long parleMs;

    private VitalsHud() {}

    /**
     * Lit vie, chakra et satiété (RP serveur-authoritative). Priorité : flux LIVE reborn:vitals (VitalsFeed, ~5×/s) →
     * bloc « self » du tablist (2 s, fallback) → vanilla (hors serveur / sans perso). Appelé au rendu et au tick.
     */
    public static void lire(LocalPlayer p) {
        VitalsFeed.Vitals vf = VitalsFeed.get();
        TablistData.SelfVitals sv = vf == null ? TablistData.selfVitals() : null;
        if (vf != null) {
            hpCur = vf.hp(); hpMax = Math.max(1, vf.maxHp());
            ckCur = vf.chakra(); ckMax = Math.max(1, vf.maxChakra());
            source = 2;
        } else if (sv != null) {
            hpCur = sv.hp(); hpMax = Math.max(1, sv.maxHp());
            ckCur = sv.chakra(); ckMax = Math.max(1, sv.maxChakra());
            source = 1;
        } else {
            // Vie : vraie vie RP (ShinobiCore) en jeu ; vanilla seulement hors serveur.
            hpCur = (int) Math.ceil(p.getHealth());
            hpMax = (int) Math.max(1, p.getMaxHealth());
            // Chakra hors serveur : les champs XP détournés (ancien affichage).
            ckCur = p.experienceLevel;
            float pr = Math.max(0f, Math.min(1f, p.experienceProgress));
            ckMax = pr > 0.001f ? Math.round(ckCur / pr) : Math.max(ckCur, 1);
            source = 0;
        }
        VitalsFeed.Satiete sat = VitalsFeed.satiete();
        stVal = sat != null ? sat.valeur() : p.getFoodData().getFoodLevel() / 20f;
    }

    /** Vie en pour mille (0 à 1000). */
    static int vieMille() { return Math.round(1000f * Mth.clamp(hpCur / (float) hpMax, 0f, 1f)); }

    /** Chakra en pour mille (0 à 1000). */
    static int chakraMille() { return Math.round(1000f * Mth.clamp(ckCur / (float) ckMax, 0f, 1f)); }

    static int vieMax() { return hpMax; }

    static int chakraCur() { return ckCur; }

    static int chakraMax() { return ckMax; }

    /** 0 = vanilla, 1 = tablist, 2 = flux live. */
    static int source() { return source; }

    static float satieteValeur() { return stVal; }

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        lire(p);
        long now = VitalsMode.maintenant();
        VitalsMode.avancer(mc, now);
        VitalsMode.Etat e = VitalsMode.etat();
        if (e == VitalsMode.Etat.COMPLET) {
            renderComplet(ctx, mc, p, x, y, scale);
            return;
        }
        VitalsMode.phases(now);
        float tb = VitalsMode.barres(), th = VitalsMode.tete(), tt = VitalsMode.textes();
        int gs = Math.max(1, mc.getWindow().getGuiScale());
        gabarit(scale * gs / 2f);
        Identifier skin = p.getSkin().body().texturePath();

        // Formes en pixels écran (nettes à toutes les échelles), depuis l'ancre de l'élément.
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(1f / gs, 1f / gs);
        if (e == VitalsMode.Etat.COMPACT) compact(ctx, skin, now);
        else morph(ctx, skin, gs * scale / 2f, gs, tb, th, tt);
        ctx.pose().popMatrix();

        // Nom, chiffres (et casque) : seulement une fois les barres en place, en fondu, à leur place du bloc.
        if (tt > 0.02f) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(x, y);
            if (scale != 1.0f) ctx.pose().scale(scale, scale);
            textes(ctx, mc, tt);
            ctx.pose().popMatrix();
        }
    }

    /** Gabarit du compact en pixels écran pour {@code f} pixels écran par px de la maquette. */
    private static void gabarit(float f) {
        if (f == gabF) return;
        gabF = f;
        cB = Math.max(1, Math.round(f));                 // or 1 px, nuit 1 px
        cH = fr.reborn.hud.ui.DaKit.cotePx(32f * f, 8);  // tête : 4 px par pixel de skin à 1:1
        cS = cH + 4 * cB;                                 // cadre 36
        cR = Math.max(1, Math.round(4f * f));
        cFh = Math.max(2, Math.round(4f * f));            // filets de 4
        cSh = Math.max(1, Math.round(2f * f));            // satiété de 2
        int g = Math.max(1, Math.round(2f * f));          // 2 px d'écart
        cFx = Math.round(2f * f);
        cVy = Math.round(2f * f);
        cFy = cVy + cFh + g;
        cCy = cFy + cS + g;
        cSy = cCy + cFh + g;
        cMs = fr.reborn.hud.ui.DaKit.cotePx(16f * f, 16); // micro : la texture à 1:1
        cMx = cFx + cS + Math.round(6f * f);
        cMy = cFy + (cS - cMs) / 2;
    }

    /** Compact : tête dans le cadre du bloc réduit de moitié, filet de vie au-dessus, de chakra dessous, micro. */
    private static void compact(GuiGraphicsExtractor ctx, Identifier skin, long now) {
        cadre(ctx, skin, cFx, cFy, cS, cR, cB);
        float vie = Mth.clamp(hpCur / (float) hpMax, 0f, 1f);
        if (VitalsMode.vieBasse()) {
            // Vie ≤ 25 % : le filet bat (1,2 s, sinus), sa piste et son liseré rougissent ; pas de halo.
            float k = VitalsMode.pouls(now);
            filet(ctx, cFx, cVy, cS, cFh, melanger(HP_COLOR, HP_VIF, k), vie,
                melanger(RIM, RIM_DANGER, 0.35f + 0.65f * k), melanger(BAR_BG, CORE_DANGER, k), cB);
        } else {
            filet(ctx, cFx, cVy, cS, cFh, HP_COLOR, vie, RIM, BAR_BG, cB);
        }
        filet(ctx, cFx, cCy, cS, cFh, CK_COLOR, Mth.clamp(ckCur / (float) ckMax, 0f, 1f), RIM, BAR_BG, cB);
        if (VitalsMode.faimBasse()) filet(ctx, cFx, cSy, cS, cSh, ST_COLOR, stVal, RIM_ST, BAR_BG, cB);
        micro(ctx, cMx, cMy, cMs);
        if (VoiceState.selfDeafened()) {   // le casque n'apparaît que si l'on est assourdi
            ctx.blit(RenderPipelines.GUI_TEXTURED, CASQUE_OFF, cMx + cMs + Math.max(1, cMs / 8), cMy, 0f, 0f, cMs, cMs,
                16, 16, 16, 16, ICONE_ETEINTE);
        }
    }

    /**
     * Entre deux : chaque filet devient sa barre (position, largeur, épaisseur), le cadre et la tête grandissent, le
     * micro glisse au bout du chakra. {@code f} : pixels écran par px de la maquette (cotes du bloc). Entre les deux
     * bouts, tout tombe sur la grille GUI ({@code g} pixels écran, LISEZMOI « Timings ») : coins nets, côtés pairs.
     */
    private static void morph(GuiGraphicsExtractor ctx, Identifier skin, float f, int g, float tb, float th, float tt) {
        grille = g;
        int fs = mi(cS, F_CADRE * f, th);
        int bd = Math.max(1, mi(cB, F_BD * f, th));
        cadre(ctx, skin, mi(cFx, F_CX * f, th), mi(cFy, F_CY * f, th), fs, mi(cR, F_R * f, th), bd);
        int rim = alpha(RIM, 1f - tb);
        int bh = mi(cFh, F_BAR_H * f, tb);
        filet(ctx, mi(cFx, F_VIE_X * f, tb), mi(cVy, F_VIE_Y * f, tb), mi(cS, F_VIE_W * f, tb), bh, HP_COLOR,
            Mth.clamp(hpCur / (float) hpMax, 0f, 1f), rim, BAR_BG, cB);
        filet(ctx, mi(cFx, F_VIE_X * f, tb), mi(cCy, F_CK_Y * f, tb), mi(cS, F_CK_W * f, tb), bh, CK_COLOR,
            Mth.clamp(ckCur / (float) ckMax, 0f, 1f), rim, BAR_BG, cB);
        // Satiété : du 3e filet (≤ 25 %) ou de rien, vers sa barre.
        boolean faim = VitalsMode.faimBasse();
        int sw = mi(faim ? cS : 0, F_VIE_W * f, tb);
        if (sw > 2) {
            float k = faim ? 1f : Math.min(1f, tb * 1.6f);
            filet(ctx, mi(cFx, F_VIE_X * f, tb), mi(cSy, F_ST_Y * f, tb), sw, mi(cSh, F_BAR_H * f, tb),
                alpha(ST_COLOR, k), stVal, alpha(rim, k), alpha(BAR_BG, k), cB);
        }
        int ms = mi(cMs, F_MIC_S * f, tb);
        int mx = mi(cMx, F_MIC_X * f, tb), my = mi(cMy, F_MIC_Y * f, tb);
        micro(ctx, mx, my, ms);
        if (VoiceState.selfDeafened() && tt <= 0.02f) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, CASQUE_OFF, mx + ms + Math.max(1, ms / 8), my, 0f, 0f, ms, ms,
                16, 16, 16, 16, ICONE_ETEINTE);
        }
    }

    /** Nom, chiffres et casque du bloc en fondu ({@code a}), en unités du panneau. */
    private static void textes(GuiGraphicsExtractor ctx, Minecraft mc, float a) {
        Font tr = mc.font;
        int blanc = alpha(WHITE, a);
        labels();
        String nom = nomRp(mc);
        ctx.pose().pushMatrix();
        ctx.pose().translate(COL_X, 0f);
        ctx.pose().scale(1.35f, 1.35f);
        ctx.text(tr, nom, 0, 0, blanc, true);
        ctx.pose().popMatrix();
        int cw = WIDTH - COL_X - 3;
        ctx.text(tr, cHpLabel, COL_X + (cw - tr.width(cHpLabel)) / 2, Y1 + (BAR_H - 8) / 2, blanc, true);
        ctx.text(tr, cCkLabel, COL_X + (cw - 24 - tr.width(cCkLabel)) / 2, Y2 + (BAR_H - 8) / 2, blanc, true);
        ctx.text(tr, cStLabel, COL_X + (cw - tr.width(cStLabel)) / 2, Y3 + (BAR_H - 8) / 2, blanc, true);
        int s = MIC_TAILLE;
        boolean sourd = VoiceState.selfDeafened();
        ctx.blit(RenderPipelines.GUI_TEXTURED, sourd ? CASQUE_OFF : CASQUE_ON, MIC_X + s + 2, MIC_Y, 0f, 0f, s, s,
            16, 16, 16, 16, alpha(sourd ? ICONE_ETEINTE : 0xFFFFFFFF, a));
    }

    /** Cadre or puis nuit (épaisseur {@code b}), tête (face + calque chapeau) dedans. */
    private static void cadre(GuiGraphicsExtractor ctx, Identifier skin, int x, int y, int s, int r, int b) {
        DrawHelpers.roundedRectFull(ctx, x, y, s, s, r, GOLD);
        DrawHelpers.roundedRectFull(ctx, x + b, y + b, s - 2 * b, s - 2 * b, Math.max(0, r - b), CADRE_NUIT);
        int h = s - 4 * b;
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x + 2 * b, y + 2 * b, 8f, 8f, h, h, 8, 8, 64, 64);
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x + 2 * b, y + 2 * b, 40f, 8f, h, h, 8, 8, 64, 64);
    }

    /**
     * Filet (greffe C) : piste vide avec un liseré or éteint de {@code b} px (sans liseré si son alpha est nul), la
     * pleine par-dessus, et son reflet d'1 trait (seulement sur la pleine, à partir de 4 px de haut).
     */
    private static void filet(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c, float ratio, int rim,
                              int core, int b) {
        if (w <= 0 || h <= 0) return;
        boolean avecLisere = (rim >>> 24) > 4;
        if (avecLisere && h > 2 * b) {
            DrawHelpers.roundedRectFull(ctx, x, y, w, h, h / 2, rim);
            DrawHelpers.roundedRectFull(ctx, x + b, y + b, w - 2 * b, h - 2 * b, (h - 2 * b) / 2, core);
        } else {
            DrawHelpers.roundedRectFull(ctx, x, y, w, h, h / 2, avecLisere ? rim : core);
        }
        int fw = Math.round(w * Mth.clamp(ratio, 0f, 1f));
        if (fw > 0) DrawHelpers.roundedRectFull(ctx, x, y, Math.max(Math.min(h, w), fw), h, h / 2, c);
        if (h >= 4 * b && fw > 2 * b) ctx.fill(x + b, y, x + fw - b, y + b, REFLET);
    }

    /** Pas de la grille du morph (pixels écran par unité GUI), posé par {@link #morph}. */
    private static int grille = 1;

    /** Interpolation de {@code a} (compact) à {@code b} (bloc) ; aux bouts la valeur exacte, entre les deux la grille. */
    private static int mi(float a, float b, float t) {
        if (t <= 0f) return Math.round(a);
        if (t >= 1f) return Math.round(b);
        return Math.round((a + (b - a) * t) / grille) * grille;
    }

    /** Multiplie l'alpha d'une couleur ARGB. */
    private static int alpha(int argb, float k) {
        int a = Math.round((argb >>> 24) * Mth.clamp(k, 0f, 1f));
        return a << 24 | (argb & 0xFFFFFF);
    }

    // ─── Le bloc complet (l'affichage d'avant, inchangé) ───
    private static final int COL_X = 3 + HEAD + 7, BAR_H = 11, Y1 = 14, Y2 = Y1 + BAR_H + 3, Y3 = Y2 + BAR_H + 3;

    /** Nom RP : lu de l'entrée tablist « SOI » (posée par ShinobiCore via reborn:tablist). Fallback pseudo MC hors serveur. */
    private static String nomRp(Minecraft mc) {
        String rpName = mc.getUser().getName();
        try {
            for (TabEntry e : TablistData.entries(rpName)) {
                if (e.relation() == TabEntry.Relation.SOI) {
                    if (e.name() != null && !e.name().isBlank()) rpName = e.name();
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // pas de data tablist (hors serveur) → fallback pseudo.
        }
        if (!rpName.equals(cNameStr)) cNameStr = rpName;
        return cNameStr;
    }

    /** Libellés « cur/max » et « % » cachés : recomposés uniquement quand la valeur change. */
    private static void labels() {
        if (hpCur != cHpCur || hpMax != cHpMax) { cHpLabel = hpCur + "/" + hpMax; cHpCur = hpCur; cHpMax = hpMax; }
        if (ckCur != cCkCur || ckMax != cCkMax) { cCkLabel = ckCur + "/" + ckMax; cCkCur = ckCur; cCkMax = ckMax; }
        int stPct = Math.round(stVal * 100);
        if (stPct != cStPct) { cStLabel = stPct + "%"; cStPct = stPct; }
    }

    private static void renderComplet(GuiGraphicsExtractor ctx, Minecraft mc, LocalPlayer p, int x, int y, float scale) {
        Font tr = mc.font;

        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        if (scale != 1.0f) ctx.pose().scale(scale, scale);

        String rpName = nomRp(mc);
        labels();

        // ─── Tête + bordure dorée ───
        int hx = 3, hy = 3;
        DrawHelpers.roundedRectFull(ctx, hx - 2, hy - 2, HEAD + 4, HEAD + 4, 6, GOLD);
        DrawHelpers.roundedRectFull(ctx, hx - 1, hy - 1, HEAD + 2, HEAD + 2, 5, CADRE_NUIT);
        // Tête = région 8×8 du skin (face à (8,8), calque chapeau à (40,8)),
        // agrandie à HEAD×HEAD. Blit 12-args (…, destW, destH, regionW, regionH,
        // texW, texH) : sinon le blit 10-args prend (HEAD,HEAD) comme région source
        // → échantillonne un bloc HEAD×HEAD du skin (corps/jambes) au lieu de la tête.
        Identifier skin = p.getSkin().body().texturePath();
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, hx, hy, 8f, 8f, HEAD, HEAD, 8, 8, 64, 64);
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, hx, hy, 40f, 8f, HEAD, HEAD, 8, 8, 64, 64);

        // ─── Colonne de droite : nom + barres ───
        int cx = COL_X;
        int cw = WIDTH - cx - 3;

        // Nom RP — plus grand que les labels de barre (cf. hudvitalsrp.png).
        float nameScale = 1.35f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, 0f);
        ctx.pose().scale(nameScale, nameScale);
        ctx.text(tr, rpName, 0, 0, WHITE, true);
        ctx.pose().popMatrix();

        int barH = BAR_H;
        int y1 = Y1, y2 = Y2, y3 = Y3;

        // Vie : vraie vie RP (ShinobiCore), PAS les cœurs MC vanilla.
        bar(ctx, tr, cx, y1, cw, barH, HP_COLOR, hpCur / (float) hpMax, cHpLabel);
        // Régénération passive en cours (nourriture, 14/10) : une lueur douce qui glisse sur la barre rouge.
        VitalsFeed.Satiete sat = VitalsFeed.satiete();
        if (sat != null && sat.regenere()) lueur(ctx, cx, y1, Math.round(cw * Math.max(0f, Math.min(1f, hpCur / (float) hpMax))), barH);

        // Chakra : valeur RP du serveur (plus les champs XP détournés qui se
        // désynchronisaient à la reco).
        float ckRatio = Math.max(0f, Math.min(1f, ckCur / (float) ckMax));
        int voiceW = 24;
        bar(ctx, tr, cx, y2, cw - voiceW, barH, CK_COLOR, ckRatio, cCkLabel);
        voiceIcons(ctx, MIC_X, MIC_Y);   // = (cx + cw - voiceW + 4, y2 + (barH - 10) / 2), partagé avec le micro à terre

        // Satiété (nourriture, 14/10) : poussée par ShinobiCore dans reborn:vitals ; sans elle (ancien serveur, solo),
        // la faim vanilla comme avant. Or pâle = ce qu'apporte le repas en cours ; repère de 2 px à 90 % (régénération).
        float st = stVal;
        if (sat != null && sat.aVenir() > 0.004f) {
            float fan = Math.min(1f, st + sat.aVenir());
            DrawHelpers.roundedRectFull(ctx, cx, y3, cw, barH, barH / 2, BAR_BG);
            DrawHelpers.roundedRectFull(ctx, cx, y3, Math.max(barH, Math.round(cw * fan)), barH, barH / 2, 0x60F6E2B4);
            barSansFond(ctx, tr, cx, y3, cw, barH, ST_COLOR, st, cStLabel);
        } else {
            bar(ctx, tr, cx, y3, cw, barH, ST_COLOR, st, cStLabel);
        }
        if (sat != null) {
            int rx = cx + Math.round(cw * 0.90f);
            ctx.fill(rx, y3 + 2, rx + 1, y3 + barH - 2, st >= 0.90f ? 0xE69A7234 : 0xE68E6A30);
        }

        ctx.pose().popMatrix();
    }

    /**
     * Position courante du micro dans le panneau (unités du panneau, avant son échelle), compact ou bloc : lue par le
     * micro à terre ({@code ko/MicroATerreHud}). {@code i} : 0 = x, 1 = y, 2 = côté.
     */
    public static float micro(int i, float scale) {
        VitalsMode.phases(VitalsMode.maintenant());
        float tb = VitalsMode.barres();
        float plein = i == 0 ? MIC_X : i == 1 ? MIC_Y : MIC_TAILLE;
        if (tb >= 1f) return plein;
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        gabarit(scale * gs / 2f);
        float compact = (i == 0 ? cMx : i == 1 ? cMy : cMs) / (gs * scale);
        return compact + (plein - compact) * tb;
    }

    /** Remplissage seul (le fond est déjà posé, avec l'or pâle du repas). */
    private static void barSansFond(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h,
                                    int color, float ratio, String label) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        int fw = Math.round(w * ratio);
        if (fw > 0) DrawHelpers.roundedRectFull(ctx, x, y, Math.max(h, fw), h, h / 2, color);
        ctx.fill(x + h / 2, y + 1, x + w - h / 2, y + 2, 0x30FFFFFF);
        if (label != null && !label.isEmpty()) {
            int lw = tr.width(label);
            ctx.text(tr, label, x + (w - lw) / 2, y + (h - 8) / 2, WHITE, true);
        }
    }

    /** Lueur douce qui glisse lentement sur la partie pleine de la barre de vie (régénération). */
    private static void lueur(GuiGraphicsExtractor ctx, int x, int y, int fw, int h) {
        if (fw <= h) return;
        float t = (System.currentTimeMillis() % 2400) / 2400f;
        int c = x + Math.round(fw * t);
        for (int d = -8; d <= 8; d++) {
            int xx = c + d;
            if (xx < x + h / 2 || xx >= x + fw - h / 2) continue;
            int a = Math.round(70 * (1 - Math.abs(d) / 9f));
            ctx.fill(xx, y + 2, xx + 1, y + h - 2, (a << 24) | 0xFFD6D6);
        }
    }

    /** Barre « pilule » (fond arrondi + remplissage) avec texte centré. */
    private static void bar(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h,
                            int color, float ratio, String label) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        DrawHelpers.roundedRectFull(ctx, x, y, w, h, h / 2, BAR_BG);
        int fw = Math.round(w * ratio);
        if (fw > 0) DrawHelpers.roundedRectFull(ctx, x, y, Math.max(h, fw), h, h / 2, color);
        // reflet supérieur.
        ctx.fill(x + h / 2, y + 1, x + w - h / 2, y + 2, 0x30FFFFFF);
        if (label != null && !label.isEmpty()) {
            int lw = tr.width(label);
            ctx.text(tr, label, x + (w - lw) / 2, y + (h - 8) / 2, WHITE, true);
        }
    }

    /** Micro + casque (textures 16×16 mises à l'échelle 10px) selon l'état voix
     *  PlasmoVoice : micro à la couleur de la portée ({@link #micro}), casque off
     *  (éteint) si assourdi. États relus à chaque tick par
     *  {@link fr.reborn.hud.voice.VoiceState} (valeurs neutres si PlasmoVoice absent). */
    private static void voiceIcons(GuiGraphicsExtractor ctx, int x, int y) {
        int s = MIC_TAILLE;
        micro(ctx, x, y, s);
        if (VoiceState.selfDeafened()) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, CASQUE_OFF, x + s + 2, y, 0f, 0f, s, s, 16, 16, 16, 16, ICONE_ETEINTE);
        } else {
            ctx.blit(RenderPipelines.GUI_TEXTURED, CASQUE_ON, x + s + 2, y, 0f, 0f, s, s, 16, 16, 16, 16);
        }
    }

    /** Couleur du micro pour un rang de portée ({@link VoiceState#portee()}) : 0 vert, 1 or, 2 rouge. */
    public static int couleurPortee(int rang) {
        return rang <= VoiceState.CHUCHOTE ? MIC_VERT : rang >= VoiceState.CRIEE ? MIC_ROUGE : MIC_JAUNE;
    }

    /**
     * Le micro seul (vitals, et micro à terre quand ils sont masqués), {@code s} × {@code s} en (x, y) :
     * <ul>
     *   <li>à la couleur de la portée <b>en permanence</b> (vert / or / rouge, {@link #couleurPortee}) ;</li>
     *   <li>je parle = légère pulsation vers une variante plus lumineuse de la même teinte (2,5 Hz), qui monte en 90 ms
     *       et retombe en 220 ms ;</li>
     *   <li>aucune liaison vocale ({@link VoiceState#relie()} faux : personne ne nous entend) : gris, sans pulsation ;</li>
     *   <li>micro coupé, rendu muet par le staff, voix refusée par l'état (mort RP…), assourdi : les icônes de l'équipe,
     *       éteintes (aucune couleur).</li>
     * </ul>
     */
    public static void micro(GuiGraphicsExtractor ctx, int x, int y, int s) {
        boolean relie = VoiceState.relie(), muet = VoiceState.selfMuted(), sourd = VoiceState.selfDeafened();
        long now = System.currentTimeMillis();
        float dt = parleMs == 0L ? 0f : Mth.clamp(now - parleMs, 0L, 100L);
        parleMs = now;
        boolean parle = relie && !muet && !sourd && VoiceState.selfSpeaking();
        parleNiv = parle ? Math.min(1f, parleNiv + dt / 90f) : Math.max(0f, parleNiv - dt / 220f);
        if (!relie) {   // indisponible (comme Plasmo, qui passe la liaison avant l'état du micro)
            ctx.blit(RenderPipelines.GUI_TEXTURED, micClair(), x, y, 0f, 0f, s, s, 16, 16, 16, 16, MIC_GRIS);
            return;
        }
        if (muet || sourd) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, muet ? MIC_OFF : MIC_ON, x, y, 0f, 0f, s, s, 16, 16, 16, 16, ICONE_ETEINTE);
            return;
        }
        int c = couleurPortee(VoiceState.portee());
        if (parleNiv > 0f) {
            float k = 0.5f - 0.5f * Mth.cos((now % 400L) / 400f * Mth.TWO_PI);
            c = melanger(c, vive(c), k * parleNiv);
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, micClair(), x, y, 0f, 0f, s, s, 16, 16, 16, 16, c);
    }

    /** Variante lumineuse de la même teinte (pulsation « je parle ») ; toute autre couleur s'éclaircit d'un tiers. */
    private static int vive(int c) {
        if (c == MIC_VERT) return MIC_VERT_VIF;
        if (c == MIC_JAUNE) return MIC_JAUNE_VIF;
        if (c == MIC_ROUGE) return MIC_ROUGE_VIF;
        return versBlanc(c, 0.33f);
    }

    /** Mélange deux couleurs ARGB opaques ({@code f} de 0 à 1 vers {@code b}), alpha de {@code a} gardé. */
    private static int melanger(int a, int b, float f) {
        int r = (a >> 16 & 0xFF) + Math.round(((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * f);
        int g = (a >> 8 & 0xFF) + Math.round(((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * f);
        int bl = (a & 0xFF) + Math.round(((b & 0xFF) - (a & 0xFF)) * f);
        return (a & 0xFF000000) | r << 16 | g << 8 | bl;
    }

    /** Éclaircit une couleur ARGB vers le blanc ({@code f} de 0 à 1), alpha gardé. */
    private static int versBlanc(int argb, float f) {
        int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        r += Math.round((255 - r) * f);
        g += Math.round((255 - g) * f);
        b += Math.round((255 - b) * f);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /**
     * L'icône micro de l'équipe aux gris remontés, à teinter : l'originale est gris foncé (80 à 142), et une teinte s'y
     * multiplie (le jaune virait au brun, le rouge au bordeaux). Créée une fois, en mémoire (16 × 16) ; repli sur
     * l'originale si la lecture échoue. Thread de rendu.
     */
    public static Identifier micClair() {
        if (micClairEtat == 0) micClairEtat = creerMicClair() ? 1 : -1;
        return micClairEtat > 0 ? MIC_CLAIR : MIC_ON;
    }

    private static boolean creerMicClair() {
        Minecraft mc = Minecraft.getInstance();
        try {
            var res = mc.getResourceManager().getResource(MIC_ON);
            if (res.isEmpty()) return false;
            NativeImage im;
            try (InputStream in = res.get().open()) {
                im = NativeImage.read(NativeImage.Format.RGBA, in);
            }
            for (int yy = 0; yy < im.getHeight(); yy++) {
                for (int xx = 0; xx < im.getWidth(); xx++) {
                    int p = im.getPixel(xx, yy);
                    if ((p >>> 24) == 0) continue;
                    int r = p >> 16 & 0xFF, g = p >> 8 & 0xFF, b = p & 0xFF;
                    if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 40) continue;   // pixel coloré : gardé
                    int v = remonter((r * 299 + g * 587 + b * 114) / 1000);
                    im.setPixel(xx, yy, (p & 0xFF000000) | v << 16 | v << 8 | v);
                }
            }
            mc.getTextureManager().register(MIC_CLAIR, new DynamicTexture(() -> "reborn-hud mic clair", im));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Gris de l'icône (80 / 91 / 142 / 237) → 200 / 206 / 235 / 252 : le modelé de l'équipe reste, la teinte est vive. */
    private static int remonter(int l) {
        if (l <= 80) return Math.round(200f * l / 80f);
        if (l <= 142) return Math.round(200f + (l - 80) * 35f / 62f);
        return Math.min(255, Math.round(235f + (l - 142) * 20f / 113f));
    }
}
