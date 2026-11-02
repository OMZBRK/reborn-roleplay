package fr.reborn.hud.voice;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.douane.DouaneClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.menu.tablist.TabEntry;
import fr.reborn.hud.menu.tablist.TablistData;
import fr.reborn.hud.nametag.Nametags;
import fr.reborn.hud.notif.BasDroite;
import fr.reborn.hud.runtime.VitalsHud;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.TexteCache;
import fr.reborn.hud.ui.TexteRaster;
import fr.reborn.hud.ui.TexteRaster.Masque;
import fr.reborn.hud.ui.TexteRaster.Police;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Qui parle</b>, en haut de la colonne bas-droite (lot 2 « Reprises Ketto », Game Plan n° 17 ; maquettes c1 / c2,
 * LISEZMOI v2, brief §4.2) — élément {@link HudElement#QUI_PARLE} de l'éditeur de HUD. Au moteur du lot
 * ({@link TexteRaster} / {@link TexteCache}) : chaque nom et chaque forme calculés une fois en couche, posés en un blit
 * à 1:1 sur des pixels entiers.
 *
 * <p><b>Contenu</b> : la seule ligne de <b>proximité</b> de Plasmo Voice ({@link VoiceState}, la radio reste dans
 * l'élément Escouade) ; 3 lignes au plus, <b>soi en bas</b> quand on émet, les autres au-dessus par ordre d'arrivée
 * (les plus récents gardés quand ils sont plus de trois). Nom tel que le nametag l'affiche ({@code Nametags} : douane,
 * puis banc, puis nom RP du tablist), en capitales avec accents ({@code toUpperCase(Locale.ROOT)} : « HYŪGA »), jamais
 * le pseudo Minecraft ; masqué (relation INCONNU) ou absent du tablist : « INCONNU » #A8A0A0. Un orateur dont l'entité
 * n'est pas chargée garde sa ligne (au-delà de la portée de suivi, la voix porte encore).
 *
 * <p><b>Gabarit</b> (px 1080p × k, k = kHud / 2 × l'échelle de l'éditeur) : centres des lignes à 244 / 271 / 298 px du
 * bas (836 / 809 / 782), fin du vumètre à 80 px du bord droit (1840) ; nom dans la police du récit (Alegreya Sans Bold
 * Italic), capitales 12 px, interlettre +1 px, #F3F0EA, préréglage HUD ; puce 4×4 à 6 px avant le nom (blanche, grise
 * pour INCONNU, couleur de la portée pour soi — {@link VitalsHud#couleurPortee}) ; vumètre de 5 barres de 1 px au pas de
 * 4 (2 à 6 px, posées 3 px sous le centre), à 85 %, 6 px après le nom ; puce et barres au contour rond des formes.
 *
 * <p><b>Temps</b> : apparition en 150 ms ; 1,5 s après la fin de la parole, effacement en 400 ms ; 100 % jusqu'à
 * 8 blocs, 60 % à 16 blocs et au-delà, ou quand l'entité n'est pas chargée. Vumètre <b>réel</b> ({@link VoixNiveaux} :
 * crête de la voix entendue, montée en 60 ms, retombée en 220 ms) ; niveau inconnu (banc, mannequin) : l'égaliseur doux
 * de la bulle. Remonte avec les notifications au-dessus des sous-titres vanilla ({@link BasDroite}). Rien n'est alloué
 * par image (nom et largeur recalculés quand le nom change, masques quand k change).
 */
public final class QuiParleHud {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("reborn-hud", "qui-parle");

    /* ---------------------------------------------------------------- gabarit 1080p (px, × k) */
    /** Boîte de l'éditeur : bas à 231 px du bas (13 px sous la ligne la plus basse), bord droit à 76 px (le vumètre
     *  finit 4 px plus à gauche), 3 lignes (80 px), 240 px de large. */
    private static final int BOITE_BAS = 231, BOITE_DROITE = 76, BOITE_H = 80, BOITE_L = 240;
    /** Ligne la plus basse 13 px au-dessus du bas de la boîte, pas de 27 ; vumètre fini 4 px avant le bord de la boîte. */
    private static final int MARGE_BAS = 13, PAS = 27, MARGE_DROITE = 4;
    private static final int CAP = 12, INTERLETTRE = 1, ECART = 6, PUCE = 4;
    private static final int VU_N = 5, VU_L = 1, VU_PAS = 4, VU_MIN = 2, VU_MAX = 6, VU_SOUS = 3;
    private static final int LIGNES = 3;

    /* ---------------------------------------------------------------- couleurs, opacités */
    private static final int BLANC_HUD = 0xF3F0EA, GRIS_INCONNU = 0xA8A0A0;
    private static final float BARRES = 0.85f, LOIN = 0.60f, PROCHE_BLOCS = 8f, LOIN_BLOCS = 16f;
    private static final String INCONNU = "INCONNU";

    /* ---------------------------------------------------------------- temps */
    private static final long ENTREE_MS = 150, GARDE_MS = 1500, SORTIE_MS = 400;
    private static final float MONTEE_MS = 60f, RETOMBEE_MS = 220f, GLISSE_MS = 90f;
    /** Pulsations (rad/s) des cinq barres : à niveau constant, le vumètre reste vivant. */
    private static final double[] VU_W = {9.1, 11.7, 7.3, 13.9, 10.3};

    /** Banc : vumètre imposé (hauteurs 1080p des cinq barres, 2 à 6) d'un orateur, ou de soi (son UUID). */
    public static final Map<UUID, int[]> DEBUG_VU = new ConcurrentHashMap<>();

    /* ---------------------------------------------------------------- orateurs suivis */
    private static final class Orateur {
        UUID uuid;
        boolean soi, actif, vu, inconnu, nomPose;
        int entite = -1;
        /** Arrivée (ordre), apparition (fondu d'entrée), fin de la parole (0 tant qu'il parle). */
        long depuis, neA, finA;
        /** Niveau visé (-1 inconnu) et niveau lissé du vumètre. */
        float cible = -1f, niveau;
        float phase;
        /** Position verticale lissée (px depuis le bas de la boîte), NaN avant le premier dessin. */
        double y = Double.NaN;
        /** Nom source (comparé par equals : le tablist reconstruit ses noms toutes les 2 s), nom affiché, largeur. */
        String source, affiche = INCONNU;
        double largeur = -1, kLargeur;
        final int[] barres = new int[VU_N];
        final TexteCache.Texte nom = new TexteCache.Texte();
    }

    private static final int SUIVIS = 8;
    private static final Orateur[] SUIVI = new Orateur[SUIVIS];
    private static final Orateur SOI = new Orateur();
    private static final Orateur[] CANDIDATS = new Orateur[SUIVIS], AFFICHES = new Orateur[LIGNES];
    private static int nAffiches;

    static {
        for (int i = 0; i < SUIVIS; i++) SUIVI[i] = new Orateur();
        SOI.soi = true;
    }

    /*
     * Poignées des formes : une par couleur, par hauteur et par opacité cuite (100 %, et 60 % pour un orateur loin : comme
     * la maquette, l'opacité est cuite dans la couche — le contour reste sous le remplissage — ; entre 8 et 16 blocs,
     * l'atténuation passe par l'alpha des sommets).
     */
    private static final TexteCache.Texte PUCE_BLANCHE = new TexteCache.Texte(), PUCE_GRISE = new TexteCache.Texte();
    private static final TexteCache.Texte PUCE_BLANCHE_LOIN = new TexteCache.Texte(), PUCE_GRISE_LOIN = new TexteCache.Texte();
    private static final TexteCache.Texte[] PUCE_SOI = poignees(3);
    private static final TexteCache.Texte[] BARRE = poignees(VU_MAX - VU_MIN + 1), BARRE_GRISE = poignees(VU_MAX - VU_MIN + 1);
    private static final TexteCache.Texte[] BARRE_LOIN = poignees(VU_MAX - VU_MIN + 1),
        BARRE_GRISE_LOIN = poignees(VU_MAX - VU_MIN + 1);
    /** Éditeur de HUD : trois noms d'exemple (maquette c1). */
    private static final TexteCache.Texte[] APERCU_NOMS = poignees(LIGNES);
    private static final String[] APERCU = {"AKI UCHIHA", "REN HYŪGA", "KOZUE NARA"};
    private static final int[][] APERCU_VU = {{4, 6, 5, 3, 2}, {3, 5, 6, 4, 3}, {2, 4, 3, 2, 2}};
    private static final double[] APERCU_L = {-1, -1, -1};
    private static double apercuK = -1;

    private static final Formes FORMES = new Formes();
    private static final double[] P = new double[2];
    private static long derniereImage;

    private QuiParleHud() {}

    private static TexteCache.Texte[] poignees(int n) {
        TexteCache.Texte[] t = new TexteCache.Texte[n];
        for (int i = 0; i < n; i++) t[i] = new TexteCache.Texte();
        return t;
    }

    private static int arr(double v) { return TexteRaster.arrondi(v); }

    /** Couche HUD (après les notifications, avant {@code staff-effets} : l'écran noir du staff la couvre). */
    public static void init() {
        HudElementRegistry.addLast(ID, (g, dt) -> render(g));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> oublier());
    }

    private static void oublier() {
        for (Orateur o : SUIVI) liberer(o);
        liberer(SOI);
        nAffiches = 0;
    }

    private static void liberer(Orateur o) {
        if (!o.soi) o.uuid = null;
        o.actif = false;
        o.finA = 0;
        o.y = Double.NaN;
        o.nom.liberer();
        o.source = null;
        o.nomPose = false;
        o.largeur = -1;
    }

    /* ================================================================ suivi */

    /** Vivant : il parle, ou s'est tu depuis moins de 1,5 s + 400 ms. */
    private static boolean vivant(Orateur o, long now) {
        return o.actif || (o.finA > 0 && now - o.finA < GARDE_MS + SORTIE_MS);
    }

    /** Relit les orateurs de {@link VoiceState} (relus une fois par tick) : arrivées, fins, niveaux. */
    private static void synchroniser(Minecraft mc, long now) {
        for (Orateur o : SUIVI) o.vu = false;
        int n = VoiceState.nombreOrateurs();
        for (int i = 0; i < n; i++) {
            UUID u = VoiceState.orateur(i);
            Orateur o = trouver(u);
            if (o == null) o = prendre(u, now);
            if (o == null) continue;   // plus de 8 orateurs à la fois : les autres attendent une place
            o.vu = true;
            if (!o.actif) {
                if (!vivant(o, now)) o.neA = now;   // une reprise pendant l'effacement ne refait pas l'entrée
                o.actif = true;
            }
            o.finA = 0;
            o.depuis = VoiceState.orateurDepuis(i);
            o.entite = VoiceState.orateurEntite(i);
            o.cible = VoiceState.orateurNiveau(i);
        }
        for (Orateur o : SUIVI) {
            if (o.uuid == null) continue;
            if (!o.vu && o.actif) {
                o.actif = false;
                o.finA = now;
            } else if (!o.actif && !vivant(o, now)) {
                liberer(o);
            }
        }
        // soi : quand on émet (voix de proximité ; la radio, B tenue, n'est pas la proximité)
        boolean parle = VoiceState.selfSpeaking() && mc.player != null;
        if (parle) {
            if (SOI.uuid == null || !SOI.uuid.equals(mc.player.getUUID())) {
                liberer(SOI);
                SOI.uuid = mc.player.getUUID();
                SOI.phase = phase(SOI.uuid);
            }
            if (!SOI.actif) {
                if (!vivant(SOI, now)) SOI.neA = now;
                SOI.actif = true;
                SOI.depuis = now;
            }
            SOI.finA = 0;
            SOI.entite = mc.player.getId();
            SOI.cible = VoiceState.niveauSoi();
        } else if (SOI.actif) {
            SOI.actif = false;
            SOI.finA = now;
        }
    }

    private static Orateur trouver(UUID u) {
        for (Orateur o : SUIVI) if (o.uuid != null && o.uuid.equals(u)) return o;
        return null;
    }

    /** Une place libre, sinon celle de l'orateur tu depuis le plus longtemps ; null si tous parlent. */
    private static Orateur prendre(UUID u, long now) {
        Orateur libre = null;
        for (Orateur o : SUIVI) {
            if (o.uuid == null) { libre = o; break; }
            if (!o.actif && (libre == null || o.finA < libre.finA)) libre = o;
        }
        if (libre == null) return null;
        liberer(libre);
        libre.uuid = u;
        libre.neA = now;
        libre.niveau = 0f;
        libre.phase = phase(u);
        return libre;
    }

    private static float phase(UUID u) { return (u.hashCode() & 0xFFFF) / 65535f * 6.2832f; }

    /** Soi en bas s'il est vivant ; au-dessus, les autres vivants les plus récents, rangés par ordre d'arrivée. */
    private static void choisir(long now) {
        nAffiches = 0;
        if (SOI.uuid != null && vivant(SOI, now)) AFFICHES[nAffiches++] = SOI;
        int n = 0;
        for (Orateur o : SUIVI) {
            if (o.uuid == null || !vivant(o, now)) continue;
            int j = n++;
            while (j > 0 && plusRecent(CANDIDATS[j - 1], o)) { CANDIDATS[j] = CANDIDATS[j - 1]; j--; }
            CANDIDATS[j] = o;
        }
        int place = LIGNES - nAffiches;
        for (int i = Math.max(0, n - place); i < n; i++) AFFICHES[nAffiches++] = CANDIDATS[i];
        for (int i = 0; i < n; i++) CANDIDATS[i] = null;
    }

    private static boolean plusRecent(Orateur a, Orateur b) {
        return a.depuis > b.depuis || (a.depuis == b.depuis && a.uuid.compareTo(b.uuid) > 0);
    }

    /* ================================================================ rendu */

    private static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float dt = Math.max(0f, Math.min(100f, now - derniereImage));
        derniereImage = now;
        if (mc.player == null || mc.options == null || mc.gui.hud.isHidden()) return;
        // un écran ouvert la couvre (sauf le tchat : on voit qui parle en écrivant)
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) return;
        HudElementState st;
        try { st = RebornHudClient.config().stateOf(HudElement.QUI_PARLE); } catch (RuntimeException e) { return; }
        if (!st.visible()) return;
        synchroniser(mc, now);
        choisir(now);
        if (nAffiches == 0 || !Police.RECIT.chargee()) return;

        var win = mc.getWindow();
        int W = win.getWidth(), H = win.getHeight(), gs = Math.max(1, win.getGuiScale());
        double k0 = BasDroite.k0(), k = k0 * st.scale();
        placer(st, W, H, gs, k0);
        // remonte avec les notifications au-dessus des sous-titres vanilla, tant qu'il est dans la colonne
        int dy = P[0] > W / 2.0 && P[1] > H / 2.0 ? BasDroite.remontee(mc) : 0;
        Formes f = FORMES.pour(k);
        int droite = (int) Math.round(P[0]) - arr(MARGE_DROITE * k), bas = (int) Math.round(P[1]) + dy;

        DaKit.debutPx(ctx);
        for (int r = 0; r < nAffiches; r++) {
            Orateur o = AFFICHES[r];
            nom(o, mc);
            if (o.largeur < 0 || o.kLargeur != k) {
                double w = TexteRaster.largeur(o.affiche, Police.RECIT, CAP * k, INTERLETTRE * k, false);
                if (Double.isNaN(w)) continue;
                o.largeur = w;
                o.kLargeur = k;
            }
            double cible = arr((MARGE_BAS + PAS * r) * k);
            if (Double.isNaN(o.y)) o.y = cible;
            else o.y += (cible - o.y) * Math.min(1.0, dt / GLISSE_MS);
            if (Math.abs(cible - o.y) < 0.5) o.y = cible;
            vumetre(o, now, dt);
            ligne(ctx, f, o, o.nom, o.affiche, o.largeur, o.barres, couleurPuce(o), droite, bas - (int) Math.round(o.y),
                k, fondu(o, now), o.soi ? 1f : distance(o, mc));
            if (!o.nom.pret()) o.neA = now;   // le fondu d'entrée part quand le nom est prêt (rendu sur le fil du texte)
        }
        DaKit.finPx(ctx);
    }

    /**
     * Une ligne (déjà dans {@link DaKit#debutPx}) : nom, puce, vumètre fini en {@code droite}, centrée sur {@code yc}.
     * {@code fondu} = fondus d'entrée et de sortie, {@code dist} = atténuation de la distance (1, 60 %, ou entre les deux) :
     * à 60 % tout rond, l'opacité est cuite dans la puce et les barres (comme la maquette c2), sinon portée par les sommets.
     */
    private static void ligne(GuiGraphicsExtractor ctx, Formes f, Orateur o, TexteCache.Texte poignee, String texte,
                              double largeur, int[] barres, int puce, int droite, int yc, double k, float fondu, float dist) {
        float a = fondu * dist;
        boolean inconnu = o != null && o.inconnu, loin = dist == LOIN;
        float cuite = loin ? LOIN : 1f, sommets = loin ? fondu : a;
        int vb = f.vb, vp = f.vp;
        int xv = droite - (vp * (VU_N - 1) + vb);
        double xn = xv - arr(ECART * k) - largeur;
        // le nom est demandé même invisible : le fondu d'entrée attend qu'il soit prêt
        TexteCache.Texte nom = poignee.ligne(texte, inconnu ? TexteCache.Style.NOM_INCONNU : TexteCache.Style.NOM, CAP * k,
            k, INTERLETTRE * k, xn, yc);
        if (a <= 0.004f) return;
        nom.dessinerPx(ctx, a);
        int n = f.puce.w();
        int xp = (int) Math.floor(xn - arr(ECART * k) - n + 0.5);
        poigneePuce(puce, loin).forme(f.puce, puce, k, cuite, xp, yc - n / 2).dessinerPx(ctx, sommets);
        TexteCache.Texte[] jeu = inconnu ? (loin ? BARRE_GRISE_LOIN : BARRE_GRISE) : loin ? BARRE_LOIN : BARRE;
        int rgb = inconnu ? GRIS_INCONNU : BLANC_HUD, sous = yc + arr(VU_SOUS * k);
        for (int j = 0; j < VU_N; j++) {
            int h = Math.max(VU_MIN, Math.min(VU_MAX, barres[j]));
            Masque m = f.barres[h - VU_MIN];
            jeu[h - VU_MIN].forme(m, rgb, k, BARRES * cuite, xv + j * vp, sous - m.h()).dessinerPx(ctx, sommets);
        }
    }

    private static TexteCache.Texte poigneePuce(int rgb, boolean loin) {
        if (rgb == BLANC_HUD) return loin ? PUCE_BLANCHE_LOIN : PUCE_BLANCHE;
        if (rgb == GRIS_INCONNU) return loin ? PUCE_GRISE_LOIN : PUCE_GRISE;
        if (rgb == (VitalsHud.MIC_VERT & 0xFFFFFF)) return PUCE_SOI[0];
        if (rgb == (VitalsHud.MIC_ROUGE & 0xFFFFFF)) return PUCE_SOI[2];
        return PUCE_SOI[1];
    }

    private static int couleurPuce(Orateur o) {
        if (o.inconnu) return GRIS_INCONNU;
        if (o.soi) return VitalsHud.couleurPortee(VoiceState.portee()) & 0xFFFFFF;
        return BLANC_HUD;
    }

    /** Fondus : entrée en 150 ms, puis, la parole finie, 1,5 s de garde et sortie en 400 ms. */
    private static float fondu(Orateur o, long now) {
        float a = Math.min(1f, Math.max(0f, (now - o.neA) / (float) ENTREE_MS));
        if (!o.actif && o.finA > 0) {
            long t = now - o.finA;
            if (t > GARDE_MS) a *= Math.max(0f, 1f - (t - GARDE_MS) / (float) SORTIE_MS);
        }
        return a;
    }

    /** Atténuation de la distance : 100 % jusqu'à 8 blocs, 60 % à 16 et au-delà, ou quand l'entité n'est pas chargée. */
    private static float distance(Orateur o, Minecraft mc) {
        float d;
        Float banc = VoiceState.DEBUG_ORATEURS.get(o.uuid);
        if (banc != null) d = banc;
        else {
            Entity e = o.entite >= 0 && mc.level != null ? mc.level.getEntity(o.entite) : null;
            d = e == null ? -1f : (float) Math.sqrt(e.distanceToSqr(mc.player));
        }
        if (d < 0f || d >= LOIN_BLOCS) return LOIN;
        return d <= PROCHE_BLOCS ? 1f : 1f - (1f - LOIN) * (d - PROCHE_BLOCS) / (LOIN_BLOCS - PROCHE_BLOCS);
    }

    /**
     * Nom à afficher, selon les règles du nametag ; recomposé seulement quand il change. Une ligne qui s'efface garde
     * le nom qu'elle avait (un orateur qui quitte le tablist pendant la garde ne devient pas « INCONNU »).
     */
    private static void nom(Orateur o, Minecraft mc) {
        if (!o.actif && o.nomPose) return;
        o.nomPose = true;
        Entity e = o.soi ? mc.player : o.entite >= 0 && mc.level != null ? mc.level.getEntity(o.entite) : null;
        String src = e != null ? DouaneClient.nomNametag(e) : null;   // douane : « Examinateur » ou le pseudo du profil
        if (src == null) {
            TablistData.RpName rp = Nametags.DEBUG_NOMS.get(o.uuid);
            if (rp == null) rp = TablistData.rpNameFor(o.uuid);
            boolean connu = rp != null && rp.relation() != TabEntry.Relation.INCONNU && rp.name() != null
                && !rp.name().isBlank();
            if (connu) src = rp.name();
        }
        if (src == null) {
            if (!o.inconnu) {
                o.inconnu = true;
                o.source = null;
                o.affiche = INCONNU;
                o.largeur = -1;
            }
            return;
        }
        if (o.inconnu || !src.equals(o.source)) {
            o.inconnu = false;
            o.source = src;
            o.affiche = src.toUpperCase(Locale.ROOT);   // pas DaKit.etiq ni sansAccents : « HYŪGA », pas « HYUGA »
            o.largeur = -1;
        }
    }

    /** Hauteurs des barres (px 1080p) : niveau lissé × une ondulation propre à chaque barre ; banc : imposées. */
    private static void vumetre(Orateur o, long now, float dt) {
        int[] impose = DEBUG_VU.get(o.uuid);
        if (impose != null) {
            for (int j = 0; j < VU_N; j++) o.barres[j] = j < impose.length ? impose[j] : VU_MIN;
            return;
        }
        float cible = !o.actif ? 0f : o.cible >= 0f ? o.cible : factice(o, now);
        o.niveau += (cible - o.niveau) * Math.min(1f, dt / (cible > o.niveau ? MONTEE_MS : RETOMBEE_MS));
        double s = now / 1000.0;
        for (int j = 0; j < VU_N; j++) {
            // ondulation 0,35 à 1,35 : une voix posée (−20 dB, niveau ≈ 0,6) remplit la moitié, les crêtes touchent 6 px
            // (les quatre images de la maquette c2)
            double m = 0.85 + 0.5 * Math.sin(s * VU_W[j] + o.phase + j * 1.7);
            o.barres[j] = VU_MIN + (int) Math.round((VU_MAX - VU_MIN) * Math.max(0.0, Math.min(1.0, o.niveau * m)));
        }
    }

    /** Égaliseur doux de la bulle (périodes de 460 et 170 ms, phase propre à l'orateur), quand le niveau est inconnu. */
    private static float factice(Orateur o, long now) {
        double t = now * (2 * Math.PI);
        return (float) (0.6 + 0.25 * Math.sin(t / 460.0 + o.phase) + 0.15 * Math.sin(t / 170.0 + 2 * o.phase));
    }

    /* ================================================================ placement, éditeur */

    /** Coin bas droit de la boîte placée (px écran) dans {@link #P}. */
    private static void placer(HudElementState st, int W, int H, int gs, double k0) {
        int droite = W - arr(BOITE_DROITE * k0), bas = H - arr(BOITE_BAS * k0);
        int l = arr(BOITE_L * k0), h = arr(BOITE_H * k0);
        BasDroite.placer(HudElement.QUI_PARLE, st, gs, droite - l, bas - h, l, h, P);
    }

    /** Boîte de l'élément à l'échelle 1, en GUI (éditeur de HUD) : trois lignes au-dessus des notifications. */
    public static HudElementBounds boite(int largeurGui, int hauteurGui) {
        var win = Minecraft.getInstance().getWindow();
        int gs = Math.max(1, win.getGuiScale());
        double k0 = BasDroite.k0();
        int droite = win.getWidth() - arr(BOITE_DROITE * k0), bas = win.getHeight() - arr(BOITE_BAS * k0);
        int x = Math.floorDiv(droite - arr(BOITE_L * k0), gs), y = Math.floorDiv(bas - arr(BOITE_H * k0), gs);
        return new HudElementBounds(x, y, Math.ceilDiv(droite, gs) - x, Math.ceilDiv(bas, gs) - y);
    }

    /** Éditeur de HUD : trois lignes d'exemple (maquette c1) — soi en bas (puce de la portée), deux orateurs au-dessus. */
    public static void apercu(GuiGraphicsExtractor ctx, HudElementState st) {
        if (!Police.RECIT.chargee()) return;
        var win = Minecraft.getInstance().getWindow();
        int W = win.getWidth(), H = win.getHeight(), gs = Math.max(1, win.getGuiScale());
        double k0 = BasDroite.k0(), k = k0 * st.scale();
        placer(st, W, H, gs, k0);
        Formes f = FORMES.pour(k);
        if (apercuK != k) {
            for (int i = 0; i < LIGNES; i++) {
                APERCU_L[i] = TexteRaster.largeur(APERCU[i], Police.RECIT, CAP * k, INTERLETTRE * k, false);
            }
            apercuK = k;
        }
        int droite = (int) Math.round(P[0]) - arr(MARGE_DROITE * k), bas = (int) Math.round(P[1]);
        DaKit.debutPx(ctx);
        for (int r = 0; r < LIGNES; r++) {
            if (Double.isNaN(APERCU_L[r])) continue;
            int puce = r == 0 ? VitalsHud.couleurPortee(VoiceState.portee()) & 0xFFFFFF : BLANC_HUD;
            ligne(ctx, f, null, APERCU_NOMS[r], APERCU[r], APERCU_L[r], APERCU_VU[r], puce, droite,
                bas - arr((MARGE_BAS + PAS * r) * k), k, 1f, 1f);
        }
        DaKit.finPx(ctx);
    }

    /** Masques qui ne dépendent que de k : la puce, une barre par hauteur. Un objet par état (TexteCache). */
    private static final class Formes {
        private double k = -1;
        Masque puce;
        final Masque[] barres = new Masque[VU_MAX - VU_MIN + 1];
        int vb, vp;

        Formes pour(double kk) {
            if (kk == k) return this;
            k = kk;
            vb = Math.max(1, arr(VU_L * kk));
            vp = arr(VU_PAS * kk);
            puce = Masque.puce(arr(PUCE * kk));
            for (int h = VU_MIN; h <= VU_MAX; h++) barres[h - VU_MIN] = Masque.barres(new int[]{Math.max(1, arr(h * kk))}, vb, 1);
            return this;
        }
    }

    /* ================================================================ banc */

    /** Banc : oublie tout de suite les orateurs suivis (sans attendre la garde de 1,5 s), entre deux états capturés. */
    public static void debugOublier() { oublier(); }

    /** Lignes affichées à la dernière image, pour le journal du banc. */
    public static String bilan() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < nAffiches; i++) {
            Orateur o = AFFICHES[i];
            if (i > 0) s.append(" · ");
            s.append(o.affiche).append(o.soi ? " (soi)" : "").append(o.actif ? "" : " (se tait)");
        }
        return nAffiches == 0 ? "personne" : s.toString();
    }
}
