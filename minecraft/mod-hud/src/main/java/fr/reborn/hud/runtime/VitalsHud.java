package fr.reborn.hud.runtime;

import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.tablist.TabEntry;
import fr.reborn.hud.menu.tablist.TablistData;
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
 * <p>Data lue côté client : vie/chakra = valeurs RP serveur-authoritative du
 * bloc {@code self} du tablist ({@link fr.reborn.hud.menu.tablist.TablistData}),
 * fallback vanilla hors serveur ; stamina = faim ; niveau RP via {@link #level}
 * (hook ShinobiCore) ; état <b>voix</b> (mute/parle/assourdi) en direct de
 * {@link fr.reborn.hud.voice.VoiceState} (PlasmoVoice).
 */
public final class VitalsHud {

    public static final int WIDTH = 210;
    public static final int HEIGHT = 56;

    // Hook niveau (posé plus tard par ShinobiCore ; l'état voix vient désormais
    // en direct de VoiceState / PlasmoVoice).
    public static volatile int level = 1;

    private static final int GOLD     = 0xFFE8C34A;
    private static final int HP_COLOR = 0xFFDC3B3B;
    private static final int CK_COLOR = 0xFF3E8FE0;
    private static final int ST_COLOR = 0xFFD9A95E;
    private static final int BAR_BG   = 0xE0140D0A;
    private static final int WHITE    = 0xFFF2F2F2;

    private static final int HEAD = 46;

    // Caches de libellés/Components : les vitals RP sont poussés par le réseau
    // (pas modifiés par frame) → on ne reconstruit chaînes/Components que quand la
    // valeur sous-jacente change, évitant concats + Component.literal à chaque frame.
    private static String cNameStr; private static Component cNameComp;
    private static int cNvLevel = Integer.MIN_VALUE; private static String cNvLabel;
    private static int cHpCur = Integer.MIN_VALUE, cHpMax; private static String cHpLabel;
    private static int cCkCur = Integer.MIN_VALUE, cCkMax; private static String cCkLabel;
    private static int cStPct = Integer.MIN_VALUE; private static String cStLabel;

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

        // Nom RP + niveau : lus de l'entrée tablist « SOI » (posée par ShinobiCore
        // via reborn:tablist). Fallback pseudo MC / niveau-hook hors serveur.
        String rpName = mc.getUser().getName();
        int rpLevel = level;
        try {
            for (TabEntry e : TablistData.entries(rpName)) {
                if (e.relation() == TabEntry.Relation.SOI) {
                    if (e.name() != null && !e.name().isBlank()) rpName = e.name();
                    if (e.level() > 0) rpLevel = e.level();
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // pas de data tablist (hors serveur) → fallback pseudo/level hook.
        }

        // ─── Tête + bordure dorée + badge Nv ───
        int hx = 3, hy = 3;
        DrawHelpers.roundedRectFull(ctx, hx - 2, hy - 2, HEAD + 4, HEAD + 4, 6, GOLD);
        DrawHelpers.roundedRectFull(ctx, hx - 1, hy - 1, HEAD + 2, HEAD + 2, 5, 0xFF181008);
        // Tête = région 8×8 du skin (face à (8,8), calque chapeau à (40,8)),
        // agrandie à HEAD×HEAD. Blit 12-args (…, destW, destH, regionW, regionH,
        // texW, texH) : sinon le blit 10-args prend (HEAD,HEAD) comme région source
        // → échantillonne un bloc HEAD×HEAD du skin (corps/jambes) au lieu de la tête.
        Identifier skin = p.getSkin().body().texturePath();
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, hx, hy, 8f, 8f, HEAD, HEAD, 8, 8, 64, 64);
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, hx, hy, 40f, 8f, HEAD, HEAD, 8, 8, 64, 64);
        // Badge « Nv X » chevauchant le bas de la tête (libellé caché tant que le niveau ne change pas).
        if (rpLevel != cNvLevel) { cNvLabel = "Nv " + rpLevel; cNvLevel = rpLevel; }
        String nv = cNvLabel;
        int nvw = tr.width(nv) + 8;
        int nvx = hx + (HEAD - nvw) / 2, nvy = hy + HEAD - 4;
        DrawHelpers.roundedRectFull(ctx, nvx, nvy, nvw, 10, 3, GOLD);
        ctx.text(tr, Component.literal(nv), nvx + 4, nvy + 1, 0xFF241A06, false);

        // ─── Colonne de droite : nom + barres ───
        int cx = hx + HEAD + 7;
        int cw = WIDTH - cx - 3;

        // Nom RP — plus grand que les labels de barre (cf. hudvitalsrp.png).
        float nameScale = 1.35f;
        // Component du nom caché : ne le reconstruit que si le nom RP change.
        if (!rpName.equals(cNameStr)) { cNameStr = rpName; cNameComp = Component.literal(rpName); }
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, 0f);
        ctx.pose().scale(nameScale, nameScale);
        ctx.text(tr, cNameComp, 0, 0, WHITE, true);
        ctx.pose().popMatrix();

        int barH = 11;
        int y1 = 14, y2 = y1 + barH + 3, y3 = y2 + barH + 3;

        // Vitals RP serveur-authoritative (bloc « self » du tablist, réémis à la
        // reco → survit au déco/reco). Fallback vanilla hors serveur / sans perso.
        TablistData.SelfVitals sv = TablistData.selfVitals();

        // Vie : vraie vie RP (ShinobiCore), PAS les cœurs MC vanilla.
        int hpCur, hpMax;
        if (sv != null) {
            hpCur = sv.hp();
            hpMax = Math.max(1, sv.maxHp());
        } else {
            hpCur = (int) Math.ceil(p.getHealth());
            hpMax = (int) Math.max(1, p.getMaxHealth());
        }
        // Libellé « cur/max » caché : recomposé uniquement quand la vie RP change.
        if (hpCur != cHpCur || hpMax != cHpMax) { cHpLabel = hpCur + "/" + hpMax; cHpCur = hpCur; cHpMax = hpMax; }
        bar(ctx, tr, cx, y1, cw, barH, HP_COLOR, hpCur / (float) hpMax, cHpLabel);

        // Chakra : valeur RP du serveur (plus les champs XP détournés qui se
        // désynchronisaient à la reco).
        int ckCur, ckMax;
        if (sv != null) {
            ckCur = sv.chakra();
            ckMax = Math.max(1, sv.maxChakra());
        } else {
            ckCur = p.experienceLevel;
            float pr = Math.max(0f, Math.min(1f, p.experienceProgress));
            ckMax = pr > 0.001f ? Math.round(ckCur / pr) : Math.max(ckCur, 1);
        }
        float ckRatio = Math.max(0f, Math.min(1f, ckCur / (float) ckMax));
        int voiceW = 24;
        // Libellé chakra caché : recomposé uniquement quand la valeur RP change.
        if (ckCur != cCkCur || ckMax != cCkMax) { cCkLabel = ckCur + "/" + ckMax; cCkCur = ckCur; cCkMax = ckMax; }
        bar(ctx, tr, cx, y2, cw - voiceW, barH, CK_COLOR, ckRatio, cCkLabel);
        voiceIcons(ctx, cx + cw - voiceW + 4, y2 + (barH - 10) / 2);

        // Stamina (faim). Libellé « % » caché : recomposé quand le pourcentage change.
        float st = p.getFoodData().getFoodLevel() / 20f;
        int stPct = Math.round(st * 100);
        if (stPct != cStPct) { cStLabel = stPct + "%"; cStPct = stPct; }
        bar(ctx, tr, cx, y3, cw, barH, ST_COLOR, st, cStLabel);

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

    /** Micro + casque (textures 16×16 mises à l'échelle 10px) selon l'état voix
     *  PlasmoVoice : mic off si mute, casque off si assourdi, halo vert quand on
     *  parle. États lus en direct via {@link fr.reborn.hud.voice.VoiceState}
     *  (valeurs neutres si PlasmoVoice absent). */
    private static void voiceIcons(GuiGraphicsExtractor ctx, int x, int y) {
        int s = 10;
        boolean muted = fr.reborn.hud.voice.VoiceState.selfMuted();
        boolean deaf = fr.reborn.hud.voice.VoiceState.selfDeafened();
        boolean speaking = !muted && fr.reborn.hud.voice.VoiceState.selfSpeaking();
        // Le micro lui-même devient vert quand on parle (teinte via blit couleur).
        int micTint = speaking ? 0xFF3FE05A : 0xFFFFFFFF;
        Identifier mic = muted ? MIC_OFF : MIC_ON;
        Identifier cas = deaf ? CASQUE_OFF : CASQUE_ON;
        ctx.blit(RenderPipelines.GUI_TEXTURED, mic, x, y, 0f, 0f, s, s, 16, 16, 16, 16, micTint);
        ctx.blit(RenderPipelines.GUI_TEXTURED, cas, x + s + 2, y, 0f, 0f, s, s, 16, 16, 16, 16);
    }
}
