package fr.reborn.hud.voice;

import fr.reborn.hud.anchor.TeteAncre;
import fr.reborn.hud.chat.TypingState;
import fr.reborn.hud.nametag.CalqueTete;
import fr.reborn.hud.recit.RecitHud;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Bulle au-dessus de la tête (31/10, la seule : l'icône de parole de Plasmo Voice est retirée), rendu <b>2D HUD</b> :
 * <b>sans fond</b> (décision du 31/10 au soir : plus de capsule ni de pointe), le motif seul, blanc doux, posé au-dessus
 * de la tête comme le nom. Lisible sans fond sur la neige ou le ciel clair : une ombre portée d'1 px écran (celle du
 * texte du /me) et un contour sombre très discret sur les 4 côtés (encre à 40 %, invisible sur fond sombre) :
 * <ul>
 *   <li><b>Voix</b> (Plasmo Voice → {@link VoiceState}) : 3 petites barres verticales qui ondulent doucement
 *       (égaliseur) ; sur soi aussi, en 3e personne seulement.</li>
 *   <li><b>Frappe</b> ({@link TypingState}) : 3 points en vague, nets au repos (85 %). La voix l'emporte si les deux
 *       sont actives.</li>
 * </ul>
 * Ancrée sur la tête réellement rendue ({@link TeteAncre}, interpolée : plus de saccades au tick), dans la pile de
 * {@link CalqueTete} (au-dessus du nametag quand il est là, à sa place sinon). La couche a une hauteur <b>fixe</b>
 * (les barres au plus haut, contour et ombre compris), les points centrés dedans : passer de l'écriture à la parole ne
 * fait plus glisser le /me. Éteinte, elle reste <b>réservée</b> (invisible) 1,5 s tant qu'un élément est posé au-dessus
 * (/me, nametag staff) : le /me ne descend plus à chaque pause de la voix (Plasmo coupe la source entre deux phrases), et
 * la bulle revient tout de suite. Tailles en « unités menu », comme le nametag staff : la même taille à l'écran à toutes
 * les échelles d'interface (la taille validée à l'échelle 2 en 1080p).
 *
 * <p><b>Portée</b>, mesurée depuis le <b>joueur local</b> (elle l'était depuis la caméra : en vue épaule, une cible
 * devant n'avait de bulle qu'à 1,25 bloc) : <b>voix de proximité</b> jusqu'à 64 blocs, la coupure de l'ancienne icône
 * de Plasmo — la bulle est le seul signe de qui parle, et une source de proximité activée est forcément audible (Plasmo
 * n'envoie la voix qu'aux auditeurs à portée de l'orateur) ; pleine jusqu'à 8 blocs, puis 60 % à 16 blocs, jamais moins,
 * fondu sur les 4 derniers blocs. Frappe, et voix d'une autre ligne (radio) : 8 blocs, fondu sur le dernier. Fondu
 * entrant 120 ms, sortant 220 ms ; masquée si la tête n'est pas dégagée ({@link CalqueTete#teteDegagee} : mur, ou corps
 * d'un joueur plus proche — l'auteur caché derrière quelqu'un ne le fait pas parler). Les sources (Plasmo Voice,
 * frappe) sont relues toutes les 50 ms dans des tableaux réutilisés : rien n'est alloué par image. Qui parle vient du
 * seul {@link VoiceState} (relu à chaque tick), bancs compris ({@code VoiceState.DEBUG_PARLE}, {@code DEBUG_SOI_PARLE}).
 */
public final class SpeechBubbles {

    private SpeechBubbles() {}

    // ── réglages ────────────────────────────────────────────────
    /** Frappe et voix hors proximité : portée (blocs), de l'œil du joueur local au centre de la tête ; fondu sur le dernier bloc. */
    private static final double PORTEE = 8.0, FONDU_PORTEE = 1.0;
    /** Voix de proximité : 64 blocs (l'ancienne icône de Plasmo), fondu sur les 4 derniers. */
    private static final double PORTEE_VOIX = 64.0, FONDU_VOIX = 4.0;
    /** Voix de proximité : pleine jusqu'à 8 blocs, 60 % à 16 blocs et au-delà (un orateur lointain reste repéré). */
    private static final float ATTENUATION_DEBUT = 8f, ATTENUATION_FIN = 16f, ATTENUATION_MIN = 0.6f;
    private static final float FONDU_ENTREE_MS = 120f, FONDU_SORTIE_MS = 220f;
    /** Après l'extinction, la couche reste réservée 1,5 s tant qu'un élément est posé au-dessus. */
    private static final long GARDE_MS = 1500L;
    private static final int MOTIF = 0xFFEDEAE4;
    /**
     * Ombre portée (celle du texte du /me, {@link MeBulles}) : 1 px écran en bas à droite (2 en unités menu ≥ 3), encre
     * à 85 % × le fondu ; elle ne suit pas l'éclat des points (lisible au repos) et n'existe jamais sans le motif.
     */
    private static final int OMBRE = 0x0A0608;
    private static final float OMBRE_ALPHA = 0.85f;
    /** Contour sombre très discret sur les 4 côtés (sans les coins), 1 px écran (2 en 4K), encre à 40 % × le fondu. */
    private static final float CONTOUR_ALPHA = 0.40f;
    /** Barres de voix (unités menu) : hauteur de repos et amplitude, périodes des trois barres (ms). */
    private static final float BARRE_MIN = 1.2f, BARRE_AMPLITUDE = 3.6f;
    /** Barre du milieu au plus haut (elle est 10 % plus haute) : la hauteur de la couche, frappe comprise. */
    private static final float BARRE_MAX = (BARRE_MIN + BARRE_AMPLITUDE) * 1.1f;
    private static final float[] BARRE_PERIODE = {460f, 380f, 540f};
    /** Points de frappe : période de la vague (ms), décalage entre deux points (fraction), levée (unités menu). */
    private static final float VAGUE_MS = 1000f, VAGUE_DECALAGE = 0.16f, VAGUE_LEVEE = 1.5f;
    /** Éclat d'un point au repos (la vague, portée par la levée, le pousse à 1) : 55 % ne se voyait plus sans fond. */
    private static final float POINT_REPOS = 0.85f;
    /** Les sources sont relues au plus toutes les 50 ms (un tick), pas à chaque image. */
    private static final long RAFRAICHIR_MS = 50L;

    /** Une bulle affichée (ou en fondu, ou réservée) au-dessus d'une entité. */
    private static final class Bulle {
        final int id;
        /** Progression du fondu (0 à 1, affichée en smoothstep : un retournement ne saute pas). */
        float p;
        /** Dernière nature affichée (gardée pendant le fondu sortant) et sa portée : voix de proximité ou non. */
        boolean voix, proche;
        /** Heure (horloge de la pile) jusqu'à laquelle la couche reste réservée après l'extinction. */
        long gardeJusqua;

        Bulle(int id) { this.id = id; }
    }

    private static final ArrayList<Bulle> BULLES = new ArrayList<>();
    private static int[] parle = new int[8], ecrit = new int[8];
    /** Pour chaque id de {@link #parle} : voix de la ligne de proximité ({@link VoiceState#voixDeProximite}). */
    private static boolean[] parleProche = new boolean[8];
    private static int nParle, nEcrit;
    private static boolean soiParle;
    private static long rafraichiMs, dernierMs;
    private static final Vector3d C = new Vector3d();
    private static final Vector3f A = new Vector3f();

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options == null || mc.gui.hud.isHidden()) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) return;
        long reel = System.currentTimeMillis();
        if (reel - rafraichiMs >= RAFRAICHIR_MS || reel < rafraichiMs) rafraichir(mc, reel);
        long now = CalqueTete.maintenant();
        float pas = Mth.clamp(now - dernierMs, 0L, 100L);
        dernierMs = now;
        if (BULLES.isEmpty()) return;

        LocalPlayer moi = mc.player;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double ox = Mth.lerp(pt, moi.xo, moi.getX()), oy = Mth.lerp(pt, moi.yo, moi.getY()) + moi.getEyeHeight(),
            oz = Mth.lerp(pt, moi.zo, moi.getZ());
        boolean detachee = cam.isDetached();
        int gs = Math.max(1, mc.getWindow().getGuiScale()), km = DaKit.echelleMenu();
        for (int i = BULLES.size() - 1; i >= 0; i--) {
            Bulle b = BULLES.get(i);
            int k = indice(parle, nParle, b.id);
            boolean soi = soiParle && b.id == moi.getId();
            boolean voix = k >= 0 || soi;
            boolean frappe = contient(ecrit, nEcrit, b.id);
            if (voix || frappe) {
                b.voix = voix;
                b.proche = soi || (k >= 0 && parleProche[k]);
            }
            boolean loin = b.voix && b.proche;   // voix de proximité : 64 blocs
            double portee = loin ? PORTEE_VOIX : PORTEE;
            Entity e = mc.level.getEntity(b.id);
            double d = Double.MAX_VALUE;
            boolean on = (voix || frappe) && e != null && !e.isInvisible() && (e != moi || detachee);
            if (e != null) {
                TeteAncre.centre(e, C);
                d = Math.sqrt((C.x - ox) * (C.x - ox) + (C.y - oy) * (C.y - oy) + (C.z - oz) * (C.z - oz));
                on &= d <= portee && CalqueTete.teteDegagee(e);
            }
            b.p = on ? Math.min(1f, b.p + pas / FONDU_ENTREE_MS) : Math.max(0f, b.p - pas / FONDU_SORTIE_MS);
            if (on) b.gardeJusqua = now + GARDE_MS;
            if (b.p <= 0f && !on) {
                // éteinte : la couche reste réservée, sans rien dessiner, tant qu'un élément est posé au-dessus (1,5 s
                // au plus) — le /me ne redescend pas à chaque pause ; allumée, elle prend sa place dès la première image
                if (e != null && now < b.gardeJusqua && CalqueTete.occupeeAuDessus(e, CalqueTete.BULLE)) {
                    reserver(e, b, gs, km);
                } else if (!voix && !frappe) {
                    BULLES.remove(i);
                }
                continue;
            }
            if (e == null) continue;
            float a = b.p * b.p * (3f - 2f * b.p)
                * (float) Mth.clamp((portee - d) / (loin ? FONDU_VOIX : FONDU_PORTEE), 0.0, 1.0);
            if (loin) a *= attenuation(d);
            dessiner(ctx, e, b, a, now, gs, km);
        }
    }

    /** Oublie les bulles (déconnexion). */
    public static void vider() {
        BULLES.clear();
        nParle = nEcrit = 0;
        soiParle = false;
    }

    /** Relit qui parle (et sur quelle ligne) et qui écrit (toutes les 50 ms) ; une bulle naît pour chaque nouvelle source. */
    private static void rafraichir(Minecraft mc, long reel) {
        rafraichiMs = reel;
        nParle = 0;
        nEcrit = 0;
        // VoiceState : vide et « ne parle pas » sans Plasmo Voice, banc compris (DEBUG_PARLE, DEBUG_SOI_PARLE)
        for (Integer id : VoiceState.speakingEntityIds()) {
            if (nParle == parle.length) {
                parle = Arrays.copyOf(parle, nParle * 2);
                parleProche = Arrays.copyOf(parleProche, nParle * 2);
            }
            parle[nParle] = id;
            parleProche[nParle] = VoiceState.voixDeProximite(id);
            nParle++;
        }
        for (Integer id : TypingState.typingEntityIds()) ecrit = ajouter(ecrit, nEcrit++, id);
        soiParle = VoiceState.selfSpeaking();
        if (soiParle && mc.player != null) naitre(mc.player.getId());
        for (int i = 0; i < nParle; i++) naitre(parle[i]);
        for (int i = 0; i < nEcrit; i++) naitre(ecrit[i]);
    }

    private static int[] ajouter(int[] t, int i, int v) {
        if (i >= t.length) t = Arrays.copyOf(t, t.length * 2);
        t[i] = v;
        return t;
    }

    private static int indice(int[] t, int n, int v) {
        for (int i = 0; i < n; i++) if (t[i] == v) return i;
        return -1;
    }

    private static boolean contient(int[] t, int n, int v) { return indice(t, n, v) >= 0; }

    private static void naitre(int id) {
        for (int i = 0, n = BULLES.size(); i < n; i++) if (BULLES.get(i).id == id) return;
        BULLES.add(new Bulle(id));
    }

    /** Voix de proximité : pleine jusqu'à 8 blocs, puis vers 60 % à 16 blocs, jamais moins. */
    private static float attenuation(double d) {
        float f = (float) Mth.clamp((d - ATTENUATION_DEBUT) / (ATTENUATION_FIN - ATTENUATION_DEBUT), 0.0, 1.0);
        return 1f - (1f - ATTENUATION_MIN) * f;
    }

    // ── dessin ──────────────────────────────────────────────────

    /** Couche réservée sans dessin (bulle éteinte, un /me au-dessus) : même hauteur que la bulle affichée. */
    private static void reserver(Entity e, Bulle b, int gs, int km) {
        int o = ombrePx(km), c = contourPx(km);
        CalqueTete.occuper(e, CalqueTete.BULLE, hauteurPx(km, o, c) / (float) gs, largeurPx(b.voix, km, o, c) / (2f * gs));
    }

    private static void dessiner(GuiGraphicsExtractor ctx, Entity e, Bulle b, float alpha, long now, int gs, int km) {
        if (!CalqueTete.ancre(e, A)) return;
        int o = ombrePx(km), c = contourPx(km), cote = cotePoint(km);
        // couche de hauteur fixe (px écran) : les barres au plus haut, contour et ombre compris ; la frappe y est centrée
        int haut = hauteurPx(km, o, c);
        float demi = largeurPx(b.voix, km, o, c) / (2f * gs);
        CalqueTete.occuper(e, CalqueTete.BULLE, haut / (float) gs, demi);
        float a = alpha * CalqueTete.place(e, CalqueTete.BULLE);
        float base = CalqueTete.base(e, CalqueTete.BULLE);
        // centre dégagé (lot 2) : à 25 % si elle touche le texte au centre élargi de 12 px
        float yb = A.y - CalqueTete.calePx(base);
        a *= RecitHud.attenuation(A.x - demi, yb - haut / (float) gs, A.x + demi, yb);
        if (a <= 0.004f) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(A.x, A.y - CalqueTete.calePx(base));   // bas de la couche sur le bas de sa place dans la pile
        // motif au pixel physique (animation douce même à l'échelle 2), centré sur l'ancre
        float phase = (b.id * 0.6180339f) % 1f;   // chaque bulle a son propre rythme
        DaKit.debutPx(ctx);
        if (b.voix) barres(ctx, km, o, c, now, phase, a);
        else points(ctx, km, o, c, cote, haut, now, a);
        DaKit.finPx(ctx);
        ctx.pose().popMatrix();
    }

    /** Hauteur fixe de la couche (px écran) : contour au-dessus, barre au plus haut, ombre au-dessous. */
    private static int hauteurPx(int km, int o, int c) { return c + Math.round(BARRE_MAX * km) + o; }

    /** Largeur du motif (px écran), contour des deux côtés et ombre à droite compris. */
    private static int largeurPx(boolean voix, int km, int o, int c) {
        return c + (voix ? 5 * km : 3 * cotePoint(km) + 2 * km) + Math.max(o, c);
    }

    /** Décalage de l'ombre portée (px écran) : 1, 2 à partir de 3 px par unité menu (comme le texte posé sur le jeu). */
    private static int ombrePx(int km) { return Math.max(1, Math.round(km / 2f)); }

    /** Épaisseur du contour (px écran) : 1, 2 à partir de 4 px par unité menu (4K). */
    private static int contourPx(int km) { return km >= 4 ? 2 : 1; }

    /** Côté d'un point de frappe (px écran). */
    private static int cotePoint(int km) { return Math.max(2, Math.round(1.5f * km)); }

    /** Contour sombre sur les 4 côtés (sans les coins) d'un rectangle, épaisseur {@code c} px. */
    private static void contour(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int c, int col) {
        ctx.fill(x0 - c, y0, x0, y1, col);
        ctx.fill(x1, y0, x1 + c, y1, col);
        ctx.fill(x0, y0 - c, x1, y0, col);
        ctx.fill(x0, y1, x1, y1 + c, col);
    }

    /**
     * Égaliseur doux : trois barres d'1 unité, espacées d'1 unité, qui s'étirent autour du milieu ; la barre du milieu au
     * plus haut, contour et ombre compris, remplit la couche (de −haut à 0). Contours et ombres d'abord, puis les barres.
     */
    private static void barres(GuiGraphicsExtractor ctx, int km, int o, int c, long now, float phase, float a) {
        int col = DaKit.alpha(MOTIF, a), ombre = DaKit.argb(OMBRE, OMBRE_ALPHA * a),
            trait = DaKit.argb(OMBRE, CONTOUR_ALPHA * a);
        float enveloppe = 0.8f + 0.2f * Mth.sin((float) (now % 1300L) / 1300f * Mth.TWO_PI + phase * Mth.TWO_PI);
        int max = Math.round(BARRE_MAX * km);
        int cy = max / 2 - max - o;
        for (int passe = 0; passe < 2; passe++) {
            int x = -(5 * km) / 2;
            for (int i = 0; i < 3; i++) {
                float t = (float) (now % (long) BARRE_PERIODE[i]) / BARRE_PERIODE[i];
                float v = 0.5f + 0.5f * Mth.sin((t + phase + i * 0.33f) * Mth.TWO_PI);
                float h = (BARRE_MIN + BARRE_AMPLITUDE * v * enveloppe) * (i == 1 ? 1.1f : 1f);
                int hp = Math.max(km, Math.round(h * km));
                int top = cy - hp / 2;
                if (passe == 0) {
                    contour(ctx, x, top, x + km, top + hp, c, trait);
                    ctx.fill(x + o, top + o, x + km + o, top + hp + o, ombre);
                } else {
                    ctx.fill(x, top, x + km, top + hp, col);
                }
                x += 2 * km;
            }
        }
    }

    /**
     * Trois points en vague : chacun se lève à son tour et s'éclaire. Le bloc (contour du point levé → bas de l'ombre au
     * repos) est centré verticalement dans la couche de hauteur fixe. Contour et ombre ne suivent pas l'éclat.
     */
    private static void points(GuiGraphicsExtractor ctx, int km, int o, int c, int cote, int haut, long now, float a) {
        int ombre = DaKit.argb(OMBRE, OMBRE_ALPHA * a), trait = DaKit.argb(OMBRE, CONTOUR_ALPHA * a);
        int ecart = cote + km;
        int bloc = c + Math.round(VAGUE_LEVEE * km) + cote + o;
        int repos = -(haut - bloc) / 2 - o - cote;   // haut d'un point au repos
        float t = (float) (now % (long) VAGUE_MS) / VAGUE_MS;
        for (int passe = 0; passe < 2; passe++) {
            int x = -(3 * cote + 2 * km) / 2;
            for (int i = 0; i < 3; i++) {
                float f = t - i * VAGUE_DECALAGE;
                f -= (float) Math.floor(f);
                float v = f < 0.5f ? 0.5f - 0.5f * Mth.cos(f * 2f * Mth.TWO_PI) : 0f;   // bosse sur la première moitié
                int top = repos - Math.round(VAGUE_LEVEE * km * v);
                if (passe == 0) {
                    contour(ctx, x, top, x + cote, top + cote, c, trait);
                    ctx.fill(x + o, top + o, x + cote + o, top + cote + o, ombre);
                } else {
                    ctx.fill(x, top, x + cote, top + cote, DaKit.alpha(MOTIF, a * (POINT_REPOS + (1f - POINT_REPOS) * v)));
                }
                x += ecart;
            }
        }
    }
}
