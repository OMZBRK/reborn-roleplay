package fr.reborn.hud.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.anchor.TeteAncre;
import fr.reborn.hud.nametag.CalqueTete;
import fr.reborn.hud.recit.RecitHud;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>/me au-dessus de la tête</b> (31/10, canal {@code reborn:me}, {@link fr.reborn.hud.chat.MePayload}) : l'action
 * s'écrit au-dessus de son auteur, sobre et propre (référence Ketto) — texte blanc doux en Inter, <b>sans fond</b>
 * (décision du 31/10 au soir), posé directement au-dessus de la tête comme le nom ; une ombre portée d'1 px écran et un
 * contour sombre très discret sur les 4 côtés (35 %) le gardent lisible sur la neige, le sable ou le ciel clair, sans
 * jamais faire un pavé ; pas de nom (il est déjà sur le nametag). Remplace côté client la bulle {@code TextDisplay} du
 * serveur (téléportée 20 fois par seconde, vue à travers les murs, posée sur la hitbox).
 *
 * <ul>
 *   <li><b>Apparition</b> : montée de 6 px et fondu de 220 ms (ease-out), lettres révélées une à une (16 ms par
 *       lettre, 450 ms au plus pour tout le texte) ; <b>disparition</b> : fondu de 400 ms et montée de 4 px. L'ombre et
 *       le contour suivent le carré du fondu et les lettres déjà révélées : jamais d'ombre ni de contour seuls.</li>
 *   <li><b>Mise en page</b> : 200 unités de large au plus (× facteur distance), 3 lignes au plus puis « … ». Au plus 2
 *       /me par auteur : le plus récent près de la tête, l'ancien monte (un 3e fait partir le plus ancien). Tailles en
 *       « unités menu », comme le nametag staff : la taille validée à l'échelle 2 en 1080p, quelle que soit l'échelle
 *       d'interface (à l'échelle 3 ou en « Auto », le /me grossissait de 1,5 à 2 fois au-dessus d'un nom resté petit).</li>
 *   <li><b>Distance</b> : échelle = clamp(1 − (d − 4) / 24 ; 0,72 ; 1), fondu sur les 4 derniers blocs avant la
 *       portée {@code r} du message ; {@code d} = de l'œil du joueur local au centre de la tête de l'auteur.</li>
 *   <li><b>Placement</b> : ancre de la tête rendue ({@link TeteAncre}, à terre et allongé compris), couche
 *       {@link CalqueTete#ME} de la pile (au-dessus du nametag et de la bulle de voix) ; deux auteurs qui se
 *       recouvrent à l'écran sont écartés (poussée verticale gloutonne, lissée ; le plus proche reste en place et est
 *       dessiné en dernier), et un /me ne se pose pas non plus sur le nom ou la bulle d'un joueur plus proche.</li>
 *   <li><b>Visibilité</b> : masqué si la tête n'est pas dégagée ({@link CalqueTete#teteDegagee} : mur, ou corps d'un
 *       joueur plus proche — le /me d'un auteur caché derrière quelqu'un ne se pose pas sur le visage de celui-ci ; la
 *       ligne de tchat reste), si l'auteur est invisible ou hors de portée ; sur soi, en 3e personne seulement. Un
 *       auteur naît invisible : rien ne s'affiche s'il est caché quand le /me arrive. Reste visible à terre (pas dans
 *       {@code KoMasqueHud}).</li>
 * </ul>
 * La mise en page (lignes, préfixes de la révélation, largeurs) est faite une fois à la réception : rien n'est alloué
 * par image. Messages reçus sur le thread client ; rendu sur le thread de rendu (le même).
 */
public final class MeBulles {

    private MeBulles() {}

    // ── réglages ────────────────────────────────────────────────
    /** Texte : Inter 8 (Inter 9 × 8/9), blanc doux. */
    private static final float ECHELLE_TEXTE = DaKit.S8;
    private static final int TEXTE = 0xFFF3F0EA;
    /**
     * Ombre portée : 1 px écran en bas à droite (2 à partir de 3 px par unité menu), encre à 85 % (comme le texte posé
     * sur le jeu) ; son alpha suit le carré du fondu, elle s'efface avant le texte.
     */
    private static final int OMBRE = 0x0A0608;
    private static final float OMBRE_ALPHA = 0.85f;
    /**
     * Contour sombre très discret sur les 4 côtés, en plus de l'ombre : 1 px écran (2 en 4K), même encre à 35 % × le
     * carré du fondu, sous les seules lettres révélées. Sur la neige pure, le blanc ne se lisait plus que par son ombre
     * (lettres grises fines) ; sur fond sombre il disparaît ; à 35 % il ne fait pas un « texte gras » sur fond moyen.
     */
    private static final float CONTOUR_ALPHA = 0.35f;
    /**
     * Mise en page (unités menu, à l'échelle 1) : largeur maximale, lignes, interligne, corps visible d'une ligne (du
     * haut des hampes au bas des jambages ; l'ombre et le contour s'y ajoutent).
     */
    private static final float LARGEUR_MAX = 200f;
    private static final int LIGNES_MAX = 3;
    private static final float INTERLIGNE = 9f, CORPS = 8f;
    /** Entre deux /me d'un même auteur, et entre deux auteurs écartés (px GUI). */
    private static final float ECART = CalqueTete.ECART;
    private static final float APPARITION_MS = 220f, DISPARITION_MS = 400f;
    private static final float MONTEE_APPARITION = 6f, MONTEE_DISPARITION = 4f;
    /** Révélation : 16 ms par lettre, 450 ms au plus pour tout le texte. */
    private static final float LETTRE_MS = 16f, REVELATION_MAX_MS = 450f;
    private static final int PAR_AUTEUR = 2;
    /** Facteur distance : échelle = clamp(1 − (d − 4) / 24 ; 0,72 ; 1). */
    private static final float ECHELLE_DEBUT = 4f, ECHELLE_PENTE = 24f, ECHELLE_MIN = 0.72f;
    /** Fondu sur les 4 derniers blocs avant la portée du message. */
    private static final float FONDU_PORTEE = 4f;
    private static final int PORTEE_DEFAUT = 16;
    /** Durée d'affichage par défaut (formule de {@code CharacterEmote}) et bornes (ms). */
    private static final int DUREE_MIN = 1500, DUREE_MAX = 20_000;
    /** Fondu quand la tête se cache (mur, hors champ) ou reparaît (ms). */
    private static final float VISIBILITE_MS = 150f;
    private static final int MAX_AUTEURS = 24;

    /** Une action /me mise en page. */
    private static final class Me {
        final long t0, fin;
        final int r;
        /** [ligne][k] : les k + 1 premiers caractères de la ligne (révélation sans allocation). */
        final FormattedCharSequence[][] prefixes;
        final float[] largeurs;
        final int[] debut;
        final int total;
        /** Texte visible à l'échelle 1, sans l'ombre (px GUI). */
        final float w, h;
        long finDebut = -1;
        /** Hauteur lissée au-dessus du bas de la pile de l'auteur. */
        float decalage;
        boolean place;

        Me(long t0, int ms, int r, FormattedCharSequence[][] prefixes, float[] largeurs, int[] debut, int total, float w,
           float h) {
            this.t0 = t0;
            this.fin = t0 + ms;
            this.r = r;
            this.prefixes = prefixes;
            this.largeurs = largeurs;
            this.debut = debut;
            this.total = total;
            this.w = w;
            this.h = h;
        }

        void partir(long now) { if (finDebut < 0) finDebut = now; }
    }

    /** Les /me d'un auteur (index 0 = le plus récent) et sa place à l'écran pour l'image courante. */
    private static final class Auteur {
        int id;
        UUID uuid;
        long dernierN = Long.MIN_VALUE;
        final ArrayList<Me> mes = new ArrayList<>(4);
        float vis, poussee;
        // image courante : entité, ancre, bas de la couche, place après écartement, boîte, échelle, alpha, distance caméra
        Entity e;
        float x, yBas, yPlace, larg, haut, s, a, z;
    }

    private static final ArrayList<Auteur> AUTEURS = new ArrayList<>();
    private static final Auteur[] ORDRE = new Auteur[MAX_AUTEURS];
    private static final Vector3d C = new Vector3d();
    private static final Vector3f A = new Vector3f();
    private static long dernierMs;

    // ── réception ───────────────────────────────────────────────

    /** Message {@code reborn:me} (thread client). */
    public static void recevoir(String json) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException ex) {
            return;
        }
        String t = str(o, "t");
        boolean fin = "me_fin".equals(t);
        if (!fin && !"me".equals(t)) return;
        int id = num(o, "id", -1);
        UUID u = uuid(str(o, "u"));
        if (id <= 0 && u == null) return;
        long now = CalqueTete.maintenant();
        Auteur au = trouver(id, u);
        if (fin) {
            if (au != null) for (int i = 0; i < au.mes.size(); i++) au.mes.get(i).partir(now);
            return;
        }
        String texte = nettoyer(str(o, "texte"));
        Minecraft mc = Minecraft.getInstance();
        if (texte.isEmpty() || mc.font == null) return;
        long n = o.has("n") ? lng(o, "n") : Long.MIN_VALUE;
        if (au != null && n != Long.MIN_VALUE && n == au.dernierN) return;   // doublon
        if (au == null) {
            if (AUTEURS.size() >= 64) return;
            au = new Auteur();
            // Un auteur naît invisible : la montée de vis (150 ms) se fond dans l'apparition (220 ms). Né visible, un
            // auteur caché quand le /me arrive (mur, potion, soi en 1re personne) montrait un fragment pendant 150 ms.
            au.vis = 0f;
            AUTEURS.add(au);
        }
        if (id > 0) au.id = id;
        if (u != null) au.uuid = u;
        au.dernierN = n;
        int ms = o.has("ms") ? Mth.clamp(num(o, "ms", 0), DUREE_MIN, DUREE_MAX) : Math.min(12_000, 2000 + 150 * texte.length());
        int r = o.has("r") ? Mth.clamp(num(o, "r", PORTEE_DEFAUT), 4, 64) : PORTEE_DEFAUT;
        au.mes.add(0, composer(mc.font, texte, now, ms, r));
        int restants = 0;
        for (int i = 0; i < au.mes.size(); i++) {
            Me m = au.mes.get(i);
            if (m.finDebut < 0 && ++restants > PAR_AUTEUR) m.partir(now);
        }
        while (au.mes.size() > PAR_AUTEUR + 2) au.mes.remove(au.mes.size() - 1);   // rafale : les fondus en trop partent
    }

    /** Banc : même chemin qu'un message du serveur. */
    public static void debugRecevoir(String json) { recevoir(json); }

    /** Oublie tout (déconnexion). */
    public static void vider() { AUTEURS.clear(); }

    private static Auteur trouver(int id, UUID u) {
        for (int i = 0, n = AUTEURS.size(); i < n; i++) {
            Auteur a = AUTEURS.get(i);
            if (u != null && u.equals(a.uuid)) return a;
            if (id > 0 && a.id == id && (u == null || a.uuid == null)) return a;
        }
        return null;
    }

    /** Codes § retirés, blancs ramenés à un espace, 256 caractères au plus. */
    private static String nettoyer(String s) {
        if (s == null) return "";
        String t = s.replaceAll("§.?", "").replaceAll("\\s+", " ").trim();
        if (t.length() > 256) t = t.substring(0, Character.isHighSurrogate(t.charAt(255)) ? 255 : 256).trim();
        return t;
    }

    /** Mise en page une fois pour toutes : lignes, préfixes de révélation, largeurs, emprise du texte. */
    private static Me composer(Font f, String texte, long t0, int ms, int r) {
        float max = LARGEUR_MAX / ECHELLE_TEXTE;   // en unités de police (Inter 9)
        List<String> lignes = couper(f, texte, max);
        int nl = lignes.size(), total = 0;
        FormattedCharSequence[][] pref = new FormattedCharSequence[nl][];
        float[] larg = new float[nl];
        int[] debut = new int[nl];
        float lmax = 0f;
        for (int i = 0; i < nl; i++) {
            String l = lignes.get(i);
            int cp = l.codePointCount(0, l.length());
            pref[i] = new FormattedCharSequence[cp];
            int off = 0;
            for (int k = 0; k < cp; k++) {
                off = l.offsetByCodePoints(off, 1);
                pref[i][k] = DaKit.inter(l.substring(0, off)).getVisualOrderText();
            }
            larg[i] = cp > 0 ? f.width(pref[i][cp - 1]) : 0f;
            debut[i] = total;
            total += cp;
            lmax = Math.max(lmax, larg[i]);
        }
        float w = lmax * ECHELLE_TEXTE;
        float h = (nl - 1) * INTERLIGNE + CORPS;
        return new Me(t0, ms, r, pref, larg, debut, total, w, h);
    }

    /** Coupe aux espaces (un mot trop long au caractère), 3 lignes au plus, la dernière finie par « … ». */
    private static List<String> couper(Font f, String texte, float max) {
        List<String> lignes = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String mot : texte.split(" ")) {
            if (mot.isEmpty()) continue;
            String cand = cur.isEmpty() ? mot : cur + " " + mot;
            if (largeur(f, cand) <= max) {
                cur.setLength(0);
                cur.append(cand);
                continue;
            }
            if (!cur.isEmpty()) {
                lignes.add(cur.toString());
                cur.setLength(0);
            }
            String reste = mot;
            while (largeur(f, reste) > max) {
                int off = reste.offsetByCodePoints(0, 1);
                while (off < reste.length()) {
                    int suite = reste.offsetByCodePoints(off, 1);
                    if (largeur(f, reste.substring(0, suite)) > max) break;
                    off = suite;
                }
                lignes.add(reste.substring(0, off));
                reste = reste.substring(off);
            }
            cur.append(reste);
        }
        if (!cur.isEmpty()) lignes.add(cur.toString());
        if (lignes.size() > LIGNES_MAX) {
            String der = String.join(" ", lignes.subList(LIGNES_MAX - 1, lignes.size()));
            List<String> k = new ArrayList<>(lignes.subList(0, LIGNES_MAX - 1));
            k.add(DaKit.ellipse(f, der, (int) max));
            lignes = k;
        }
        if (lignes.isEmpty()) lignes.add("");
        return lignes;
    }

    private static int largeur(Font f, String s) { return f.width(DaKit.inter(s)); }

    // ── rendu ───────────────────────────────────────────────────

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        long now = CalqueTete.maintenant();
        float pas = Math.max(0L, Math.min(100L, now - dernierMs));
        dernierMs = now;
        if (AUTEURS.isEmpty()) return;
        if (mc.level == null || mc.player == null || mc.options == null) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) return;
        boolean cache = mc.gui.hud.isHidden();
        LocalPlayer moi = mc.player;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double ox = Mth.lerp(pt, moi.xo, moi.getX()), oy = Mth.lerp(pt, moi.yo, moi.getY()) + moi.getEyeHeight(),
            oz = Mth.lerp(pt, moi.zo, moi.getZ());
        boolean detachee = cam.isDetached();
        float k = 1f - (float) Math.exp(-pas / CalqueTete.LISSAGE_MS);
        int gs = Math.max(1, mc.getWindow().getGuiScale()), km = DaKit.echelleMenu();
        float unite = km / (float) gs;   // une unité menu en px GUI : la même taille à l'écran à toutes les échelles
        int o = ombrePx(km), c = contourPx(km);
        // ombre (dessous) et contour (dessus, et de chaque côté) comptent dans la pile : contenu visible, px GUI
        float marges = (o + c) / (float) gs, largeurs = (c + Math.max(o, c)) / (float) gs;

        int n = 0;
        for (int i = AUTEURS.size() - 1; i >= 0; i--) {
            Auteur au = AUTEURS.get(i);
            vieillir(au, now);
            if (au.mes.isEmpty()) {
                AUTEURS.remove(i);
                continue;
            }
            Entity e = resoudre(mc, au);
            boolean ok = !cache && e != null && !e.isInvisible() && (e != moi || detachee);
            double d = 0.0;
            int portee = 0;
            for (int j = 0; j < au.mes.size(); j++) portee = Math.max(portee, au.mes.get(j).r);
            if (e != null) {
                TeteAncre.centre(e, C);
                d = Math.sqrt((C.x - ox) * (C.x - ox) + (C.y - oy) * (C.y - oy) + (C.z - oz) * (C.z - oz));
                ok &= d <= portee && CalqueTete.teteDegagee(e);
            }
            au.vis = ok ? Math.min(1f, au.vis + pas / VISIBILITE_MS) : Math.max(0f, au.vis - pas / VISIBILITE_MS);
            if (au.vis <= 0f || e == null || cache || !CalqueTete.ancre(e, A)) continue;

            // facteur distance × unité menu : s convertit les mesures « à l'échelle 1 » en px GUI
            float s = Mth.clamp(1f - ((float) d - ECHELLE_DEBUT) / ECHELLE_PENTE, ECHELLE_MIN, 1f) * unite;
            // pile de l'auteur : le plus récent en bas, les plus anciens au-dessus (positions lissées)
            float haut = 0f, larg = 0f;
            for (int j = 0, nm = au.mes.size(); j < nm; j++) {
                Me m = au.mes.get(j);
                if (!m.place) {
                    m.decalage = haut;
                    m.place = true;
                } else {
                    m.decalage += (haut - m.decalage) * k;
                    if (Math.abs(haut - m.decalage) < 0.02f) m.decalage = haut;
                }
                haut += m.h * s + marges + (j < nm - 1 ? ECART : 0f);
                larg = Math.max(larg, m.w * s + largeurs);
            }
            CalqueTete.occuper(e, CalqueTete.ME, haut, larg / 2f);
            float place = CalqueTete.place(e, CalqueTete.ME);
            float base = CalqueTete.base(e, CalqueTete.ME);
            au.x = A.x;
            au.yBas = A.y - CalqueTete.calePx(base);
            au.z = A.z;
            au.s = s;
            au.larg = larg;
            au.haut = haut;
            au.a = au.vis * place * Mth.clamp((portee - (float) d) / FONDU_PORTEE, 0f, 1f);
            if (au.a > 0.004f && n < MAX_AUTEURS) {
                au.e = e;   // le temps de l'image (relâché après le dessin)
                ORDRE[n++] = au;
            }
        }
        if (n == 0) return;

        // du plus proche au plus loin
        for (int i = 1; i < n; i++) {
            Auteur a = ORDRE[i];
            int j = i - 1;
            while (j >= 0 && ORDRE[j].z > a.z) {
                ORDRE[j + 1] = ORDRE[j];
                j--;
            }
            ORDRE[j + 1] = a;
        }
        // anti-chevauchement : le plus proche garde sa place, les suivants montent juste au-dessus de ce qu'ils couvrent
        // (les /me des auteurs plus proches, et le nom ou la bulle de tout joueur plus proche)
        for (int i = 0; i < n; i++) {
            Auteur ai = ORDRE[i];
            float yb = ai.yBas;
            for (int passe = 0; passe < 8; passe++) {
                boolean bouge = false;
                float y2 = CalqueTete.auDessusDesAutres(ai.e, ai.x, yb, ai.haut, ai.larg / 2f, ai.z);
                if (y2 < yb - 0.01f) {
                    yb = y2;
                    bouge = true;
                }
                for (int j = 0; j < i; j++) {
                    Auteur aj = ORDRE[j];
                    if (Math.abs(ai.x - aj.x) * 2f >= ai.larg + aj.larg + 2f * ECART) continue;
                    float hautJ = aj.yPlace - aj.haut;
                    if (yb - ai.haut >= aj.yPlace + ECART || yb + ECART <= hautJ) continue;
                    yb = hautJ - ECART;
                    bouge = true;
                }
                if (!bouge) break;
            }
            ai.yPlace = yb;
            float cible = ai.yBas - yb;
            ai.poussee += (cible - ai.poussee) * k;
            if (Math.abs(cible - ai.poussee) < 0.02f) ai.poussee = cible;
        }
        // du plus loin au plus proche : le plus proche par-dessus
        Font f = mc.font;
        for (int i = n - 1; i >= 0; i--) {
            Auteur au = ORDRE[i];
            float yb = au.yBas - CalqueTete.calePx(au.poussee);
            // centre dégagé (lot 2) : à 25 % si la pile touche le texte au centre élargi de 12 px
            au.a *= RecitHud.attenuation(au.x - au.larg / 2f, yb - au.haut, au.x + au.larg / 2f, yb);
            dessinerAuteur(ctx, f, au, yb, now, gs, o, c, marges);
            au.e = null;
            ORDRE[i] = null;
        }
    }

    /** Lance les disparitions dues, retire les /me effacés. */
    private static void vieillir(Auteur au, long now) {
        for (int j = au.mes.size() - 1; j >= 0; j--) {
            Me m = au.mes.get(j);
            if (m.finDebut < 0 && now >= m.fin - (long) DISPARITION_MS) m.finDebut = Math.max(m.t0, m.fin - (long) DISPARITION_MS);
            if (m.finDebut >= 0 && now - m.finDebut >= DISPARITION_MS) au.mes.remove(j);
        }
    }

    /** Entité de l'auteur : par identifiant réseau, puis par UUID (reconnexion, changement de monde). */
    private static Entity resoudre(Minecraft mc, Auteur au) {
        Entity e = au.id > 0 ? mc.level.getEntity(au.id) : null;
        if (e != null && (au.uuid == null || au.uuid.equals(e.getUUID()))) return e;
        if (au.uuid == null) return null;
        e = mc.level.getEntity(au.uuid);
        if (e != null) au.id = e.getId();
        return e;
    }

    private static void dessinerAuteur(GuiGraphicsExtractor ctx, Font f, Auteur au, float yBas, long now, int gs,
                                       int o, int c, float marges) {
        float s = au.s;
        for (int j = 0, nm = au.mes.size(); j < nm; j++) {
            Me m = au.mes.get(j);
            float ein = easeOut(Mth.clamp((now - m.t0) / APPARITION_MS, 0f, 1f));
            float a = au.a * ein;
            float y = yBas - m.decalage + MONTEE_APPARITION * s * (1f - ein);
            if (m.finDebut >= 0) {
                float q = Mth.clamp((now - m.finDebut) / DISPARITION_MS, 0f, 1f);
                a *= 1f - q * q;
                y -= MONTEE_DISPARITION * s * easeOut(q);
            }
            if (j + 1 < nm) {
                // l'ancien n'a pas encore fini de monter : le nouveau reste estompé d'autant (aucun chevauchement)
                Me ancien = au.mes.get(j + 1);
                a *= Mth.clamp((ancien.decalage - m.decalage - ECART) / (m.h * s + marges), 0f, 1f);
            }
            if (a > 0.004f) dessinerMe(ctx, f, m, au.x, y, s, a, now, gs, o, c);
        }
    }

    /**
     * Texte révélé lettre à lettre, chaque ligne centrée à sa place définitive, sans fond : le bas de l'ombre de la
     * dernière ligne sur {@code yBas}, au pixel physique. Contour (4 côtés, {@code c} px) puis ombre ({@code o} px) puis
     * texte, sous les mêmes lettres : jamais de contour ni d'ombre sans leur lettre.
     */
    private static void dessinerMe(GuiGraphicsExtractor ctx, Font f, Me m, float cx, float yBas, float s, float a,
                                   long now, int gs, int o, int c) {
        float ts = ECHELLE_TEXTE * s;
        float lettre = Math.min(LETTRE_MS, REVELATION_MAX_MS / Math.max(1, m.total));
        int vus = (int) Math.min(m.total, Math.max(0L, now - m.t0) / lettre);
        if (vus <= 0) return;
        float px = 1f / gs;
        int haut = Math.round(yBas * gs) - Math.round(m.h * s * gs) - o;   // haut des hampes de la 1re ligne (px écran)
        int col = DaKit.alpha(TEXTE, a), ombre = DaKit.argb(OMBRE, OMBRE_ALPHA * a * a),
            trait = DaKit.argb(OMBRE, CONTOUR_ALPHA * a * a);
        float dc = c * px, dO = o * px;
        for (int i = 0; i < m.prefixes.length; i++) {
            int nb = Math.min(m.prefixes[i].length, vus - m.debut[i]);
            if (nb <= 0) break;
            FormattedCharSequence seq = m.prefixes[i][nb - 1];
            float lx = Math.round((cx - m.largeurs[i] * ts / 2f) * gs) * px;
            float ly = (haut + Math.round(i * INTERLIGNE * s * gs)) * px;
            texte(ctx, f, seq, lx - dc, ly, ts, trait);
            texte(ctx, f, seq, lx + dc, ly, ts, trait);
            texte(ctx, f, seq, lx, ly - dc, ts, trait);
            texte(ctx, f, seq, lx, ly + dc, ts, trait);
            texte(ctx, f, seq, lx + dO, ly + dO, ts, ombre);
            texte(ctx, f, seq, lx, ly, ts, col);
        }
    }

    /** Décalage de l'ombre portée (px écran) : 1, 2 à partir de 3 px par unité menu (comme le texte posé sur le jeu). */
    private static int ombrePx(int km) { return Math.max(1, Math.round(km / 2f)); }

    /** Épaisseur du contour (px écran) : 1, 2 à partir de 4 px par unité menu (4K). */
    private static int contourPx(int km) { return km >= 4 ? 2 : 1; }

    private static void texte(GuiGraphicsExtractor ctx, Font f, FormattedCharSequence seq, float x, float y, float s, int col) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(s, s);
        ctx.text(f, seq, 0, 0, col, false);
        ctx.pose().popMatrix();
    }

    private static float easeOut(float t) {
        float q = 1f - t;
        return 1f - q * q * q;
    }

    // ── JSON ────────────────────────────────────────────────────

    private static String str(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; } catch (RuntimeException e) { return ""; }
    }

    private static int num(JsonObject o, String k, int def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def; } catch (RuntimeException e) { return def; }
    }

    private static long lng(JsonObject o, String k) {
        try { return o.get(k).getAsLong(); } catch (RuntimeException e) { return Long.MIN_VALUE; }
    }

    private static UUID uuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
