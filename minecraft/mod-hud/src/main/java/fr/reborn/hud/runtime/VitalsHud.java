package fr.reborn.hud.runtime;

import fr.reborn.hud.menu.DrawHelpers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Panneau RP « vitals » (façon Orizon/Zenkai, cf {@code hudvitalsrp.png}) en
 * haut-gauche : <b>tête</b> du joueur (bordure dorée + badge « Nv X »), <b>nom</b>,
 * et 3 barres arrondies — <b>vie</b> (rouge, cur/max), <b>chakra</b> (bleu,
 * cur/max) + icônes voix, <b>stamina</b> (dorée, %).
 *
 * <p>Data lue côté client : vie = {@code getHealth()/getMaxHealth()} (max RP posé
 * par ShinobiLife via attribut) ; chakra = champs XP détournés par ShinobiCore
 * ({@code experienceLevel} = montant, {@code experienceProgress} = ratio → max
 * dérivé) ; stamina = faim. Le <b>niveau</b> RP et l'état <b>voix</b> arrivent
 * plus tard via ShinobiCore/PlasmoVoice → {@link #level}/{@link #voiceMuted}/
 * {@link #voiceDeafened} (hooks, défaut neutre).
 */
public final class VitalsHud {

    public static final int WIDTH = 204;
    public static final int HEIGHT = 52;

    // Hooks data (posés plus tard par ShinobiCore / PlasmoVoice).
    public static volatile int level = 1;
    public static volatile boolean voiceMuted = false;
    public static volatile boolean voiceDeafened = false;

    private static final int GOLD     = 0xFFE8C34A;
    private static final int HP_COLOR = 0xFFDC3B3B;
    private static final int CK_COLOR = 0xFF3E8FE0;
    private static final int ST_COLOR = 0xFFD9A95E;
    private static final int BAR_BG   = 0xE0140D0A;
    private static final int WHITE    = 0xFFF2F2F2;

    private static final int HEAD = 38;

    // Icônes voix (16×16) faites par l'équipe — mic/casque on/off.
    private static final Identifier MIC_ON     = Identifier.fromNamespaceAndPath("reborn", "textures/hud/mic_on.png");
    private static final Identifier MIC_OFF    = Identifier.fromNamespaceAndPath("reborn", "textures/hud/mic_off.png");
    private static final Identifier CASQUE_ON  = Identifier.fromNamespaceAndPath("reborn", "textures/hud/casque_on.png");
    private static final Identifier CASQUE_OFF = Identifier.fromNamespaceAndPath("reborn", "textures/hud/casque_off.png");

    private VitalsHud() {}

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        Font tr = mc.font;

        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        if (scale != 1.0f) ctx.pose().scale(scale, scale);

        // ─── Tête + bordure dorée + badge Nv ───
        int hx = 3, hy = 2;
        DrawHelpers.roundedRectFull(ctx, hx - 2, hy - 2, HEAD + 4, HEAD + 4, 6, GOLD);
        DrawHelpers.roundedRectFull(ctx, hx - 1, hy - 1, HEAD + 2, HEAD + 2, 5, 0xFF181008);
        Identifier skin = p.getSkin().body().texturePath();
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, hx, hy, 8f, 8f, HEAD, HEAD, 64, 64);
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, hx, hy, 40f, 8f, HEAD, HEAD, 64, 64);
        // Badge « Nv X » chevauchant le bas de la tête.
        String nv = "Nv " + level;
        int nvw = tr.width(nv) + 8;
        int nvx = hx + (HEAD - nvw) / 2, nvy = hy + HEAD - 5;
        DrawHelpers.roundedRectFull(ctx, nvx, nvy, nvw, 10, 3, GOLD);
        ctx.text(tr, Component.literal(nv), nvx + 4, nvy + 1, 0xFF241A06, false);

        // ─── Colonne de droite : nom + barres ───
        int cx = hx + HEAD + 6;
        int cw = WIDTH - cx - 3;

        ctx.text(tr, Component.literal(p.getGameProfile().name()), cx, 0, WHITE, true);

        // Vie.
        int hpCur = (int) Math.ceil(p.getHealth());
        int hpMax = (int) Math.max(1, p.getMaxHealth());
        bar(ctx, tr, cx, 11, cw, 10, HP_COLOR, hpCur / (float) hpMax, hpCur + "/" + hpMax);

        // Chakra (barre plus courte : place aux icônes voix).
        int ckCur = p.experienceLevel;
        float ckRatio = Math.max(0f, Math.min(1f, p.experienceProgress));
        int ckMax = ckRatio > 0.001f ? Math.round(ckCur / ckRatio) : Math.max(ckCur, 1);
        int voiceW = 28;
        bar(ctx, tr, cx, 23, cw - voiceW, 10, CK_COLOR, ckRatio, ckCur + "/" + ckMax);
        voiceIcons(ctx, cx + cw - voiceW + 3, 21);

        // Stamina (faim).
        float st = p.getFoodData().getFoodLevel() / 20f;
        bar(ctx, tr, cx, 35, cw, 10, ST_COLOR, st, Math.round(st * 100) + "%");

        ctx.pose().popMatrix();
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
            ctx.text(tr, Component.literal(label), x + (w - lw) / 2, y + (h - 8) / 2, WHITE, true);
        }
    }

    /** Micro + casque (textures 16×16 mises à l'échelle 13px) selon l'état voix. */
    private static void voiceIcons(GuiGraphicsExtractor ctx, int x, int y) {
        int s = 13;
        Identifier mic = voiceMuted ? MIC_OFF : MIC_ON;
        Identifier cas = voiceDeafened ? CASQUE_OFF : CASQUE_ON;
        ctx.blit(RenderPipelines.GUI_TEXTURED, mic, x, y, 0f, 0f, s, s, 16, 16, 16, 16);
        ctx.blit(RenderPipelines.GUI_TEXTURED, cas, x + s + 1, y, 0f, 0f, s, s, 16, 16, 16, 16);
    }
}
