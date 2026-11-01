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
import net.minecraft.network.chat.Component;
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

    // Caches de libellés/Components : les vitals RP sont poussés par le réseau
    // (pas modifiés par frame) → on ne reconstruit chaînes/Components que quand la
    // valeur sous-jacente change, évitant concats + Component.literal à chaque frame.
    private static String cNameStr; private static Component cNameComp;
    private static int cHpCur = Integer.MIN_VALUE, cHpMax; private static String cHpLabel;
    private static int cCkCur = Integer.MIN_VALUE, cCkMax; private static String cCkLabel;
    private static int cStPct = Integer.MIN_VALUE; private static String cStLabel;

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

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        Font tr = mc.font;

        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        if (scale != 1.0f) ctx.pose().scale(scale, scale);

        // Nom RP : lu de l'entrée tablist « SOI » (posée par ShinobiCore via
        // reborn:tablist). Fallback pseudo MC hors serveur.
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

        // ─── Tête + bordure dorée ───
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

        // Vitals RP serveur-authoritative. Priorité : flux LIVE reborn:vitals
        // (VitalsFeed, ~5×/s) → bloc « self » du tablist (2 s, fallback) → vanilla
        // (hors serveur / sans perso). Le flux live évite la latence de 2 s du tablist.
        VitalsFeed.Vitals vf = VitalsFeed.get();
        TablistData.SelfVitals sv = TablistData.selfVitals();

        // Vie : vraie vie RP (ShinobiCore), PAS les cœurs MC vanilla.
        int hpCur, hpMax;
        if (vf != null) {
            hpCur = vf.hp();
            hpMax = Math.max(1, vf.maxHp());
        } else if (sv != null) {
            hpCur = sv.hp();
            hpMax = Math.max(1, sv.maxHp());
        } else {
            hpCur = (int) Math.ceil(p.getHealth());
            hpMax = (int) Math.max(1, p.getMaxHealth());
        }
        // Libellé « cur/max » caché : recomposé uniquement quand la vie RP change.
        if (hpCur != cHpCur || hpMax != cHpMax) { cHpLabel = hpCur + "/" + hpMax; cHpCur = hpCur; cHpMax = hpMax; }
        bar(ctx, tr, cx, y1, cw, barH, HP_COLOR, hpCur / (float) hpMax, cHpLabel);
        // Régénération passive en cours (nourriture, 14/10) : une lueur douce qui glisse sur la barre rouge.
        VitalsFeed.Satiete sat = VitalsFeed.satiete();
        if (sat != null && sat.regenere()) lueur(ctx, cx, y1, Math.round(cw * Math.max(0f, Math.min(1f, hpCur / (float) hpMax))), barH);

        // Chakra : valeur RP du serveur (plus les champs XP détournés qui se
        // désynchronisaient à la reco).
        int ckCur, ckMax;
        if (vf != null) {
            ckCur = vf.chakra();
            ckMax = Math.max(1, vf.maxChakra());
        } else if (sv != null) {
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
        voiceIcons(ctx, MIC_X, MIC_Y);   // = (cx + cw - voiceW + 4, y2 + (barH - 10) / 2), partagé avec le micro à terre

        // Satiété (nourriture, 14/10) : poussée par ShinobiCore dans reborn:vitals ; sans elle (ancien serveur, solo),
        // la faim vanilla comme avant. Or pâle = ce qu'apporte le repas en cours ; repère de 2 px à 90 % (régénération).
        float st = sat != null ? sat.valeur() : p.getFoodData().getFoodLevel() / 20f;
        int stPct = Math.round(st * 100);
        if (stPct != cStPct) { cStLabel = stPct + "%"; cStPct = stPct; }
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

    /** Remplissage seul (le fond est déjà posé, avec l'or pâle du repas). */
    private static void barSansFond(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h,
                                    int color, float ratio, String label) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        int fw = Math.round(w * ratio);
        if (fw > 0) DrawHelpers.roundedRectFull(ctx, x, y, Math.max(h, fw), h, h / 2, color);
        ctx.fill(x + h / 2, y + 1, x + w - h / 2, y + 2, 0x30FFFFFF);
        if (label != null && !label.isEmpty()) {
            int lw = tr.width(label);
            ctx.text(tr, Component.literal(label), x + (w - lw) / 2, y + (h - 8) / 2, WHITE, true);
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
            ctx.text(tr, Component.literal(label), x + (w - lw) / 2, y + (h - 8) / 2, WHITE, true);
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
