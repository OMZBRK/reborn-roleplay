package fr.reborn.hud.menu.connect;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static fr.reborn.hud.menu.connect.ChargementArt.*;

/**
 * Scène de chargement « nuit de pleine lune », du clic sur Rejoindre jusqu'à l'arrivée en jeu : écran de connexion
 * ({@code ConnectScreenMixin}), téléchargement et application du pack serveur ({@code LoadingOverlayMixin}, dont le
 * logo, le fond et la barre Mojang ne sont plus dessinés ; état suivi par {@code DownloadedPackSourceMixin}),
 * chargement du monde ({@code LevelLoadingScreenMixin}). Une seule horloge pour tous ces écrans : l'animation continue
 * sans saut quand l'écran change. Fondu d'entrée depuis la nuit, fondu de sortie vers le jeu (la scène s'éteint dans la
 * nuit, puis la nuit s'efface sur le jeu, élément HUD « chargement_fondu »). Annuler la connexion : Échap.
 *
 * <p>Léger (DA §6) : 7 textures fixes (2,8 Mo de VRAM en tout), une vingtaine de blits et d'aplats par image, aucune
 * allocation par image dans ce code (états et textes mis en cache, pas de {@code Random} ni de lot).
 */
public final class ChargementScene {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/chargement");

    private ChargementScene() {}

    /* ================================================================ horloge */

    private static final long T0 = System.nanoTime();
    /** Temps imposé par le banc (s), ou NaN. */
    static double tempsBanc = Double.NaN;

    /** Temps de l'animation (s) : commun à tous les écrans. */
    static double temps() {
        return Double.isNaN(tempsBanc) ? (System.nanoTime() - T0) / 1e9 : tempsBanc;
    }

    private static long ms() { return System.nanoTime() / 1_000_000L; }

    /* ================================================================ session (trajet vers le serveur) */

    static final long ENTREE_MS = 450, SORTIE_MS = 380, FONDU_JEU_MS = 650;
    /** Au-delà, on lâche l'écran du monde même si un pack n'a pas fini (garde-fou). */
    static final long ATTENTE_PACK_MAX_MS = 120_000;

    private static boolean session;
    private static long debut, sortie = -1, fonduJeu = -1, attenteDepuis = -1;

    /** Ouverture de l'écran de connexion : la scène commence (ou continue, si on y revient). */
    public static void demarrer() {
        if (!session) {
            session = true;
            debut = ms();
            sortie = -1;
            fonduJeu = -1;
            attenteDepuis = -1;
            libelle = null;
            LOG.info("[chargement] début du trajet vers le serveur");
        }
    }

    public static boolean actif() { return session; }

    /** L'écran de chargement du monde prend-il la scène ? Pendant le trajet, et sur un serveur distant (changement de
     *  dimension, réapparition) : jamais l'écran vanilla en multijoueur. */
    public static boolean pourMonde() {
        if (session) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc.getCurrentServer() != null && !mc.isLocalServer();
    }

    static void terminer(String pourquoi) {
        if (session) LOG.info("[chargement] fin ({})", pourquoi);
        session = false;
        sortie = -1;
        fonduJeu = -1;
        attenteDepuis = -1;
        liberer = true;
    }

    /** Textures de la scène à rendre au GPU dès qu'aucun écran ne les dessine plus (2,4 Mo de VRAM). */
    private static boolean liberer;
    private static final Identifier[] TEXTURES = {CIEL, LUNE, NUAGES_VOILE, VILLAGE, NUAGES_BAS, AVANT, CORBEAU, FEUILLE};

    private static void libererSiPossible(Minecraft mc, Screen s, Overlay o) {
        if (!liberer || session || o != null || packEnAttente() || s instanceof LevelLoadingScreen
            || s instanceof ChargementDebug.Banc) return;
        liberer = false;
        for (Identifier id : TEXTURES) mc.getTextureManager().release(id);
        LOG.info("[chargement] textures de la scène libérées");
    }

    /**
     * L'écran de chargement du monde veut se fermer (monde prêt). On le garde tant qu'un pack serveur est en route
     * (Nexo l'envoie à l'arrivée) ou qu'un rechargement des ressources est à l'écran, puis la scène s'éteint dans la nuit
     * ({@value #SORTIE_MS} ms) avant de le laisser fermer. Hors trajet : fermeture normale.
     */
    public static boolean laisserFermerMonde() {
        if (!session) return true;
        long now = ms();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.overlay() != null) return false;
        if (packEnAttente()) {
            if (attenteDepuis < 0) {
                attenteDepuis = now;
                LOG.info("[chargement] monde prêt, on attend le pack serveur");
            }
            if (now - attenteDepuis < ATTENTE_PACK_MAX_MS) return false;
            LOG.warn("[chargement] pack serveur toujours en attente après {} s : on entre quand même",
                ATTENTE_PACK_MAX_MS / 1000);
        }
        if (sortie < 0) {
            sortie = now;
            return false;
        }
        if (now - sortie < SORTIE_MS) return false;
        fonduJeu = now;
        LOG.info("[chargement] arrivée en jeu ({} ms depuis le clic)", now - debut);
        return true;
    }

    /** Tick client : fin du trajet si on est arrivé ailleurs (déconnexion, refus, retour au menu) ; oubli des packs. */
    static void tick(Minecraft mc) {
        if (mc.gui == null) return;
        Screen s = mc.gui.screen();
        Overlay o = mc.gui.overlay();
        if (!(s instanceof ConnectScreen) && mc.getConnection() == null && o == null) viderPacks();
        libererSiPossible(mc, s, o);
        if (!session) return;
        if (s instanceof ConnectScreen || s instanceof LevelLoadingScreen || o != null) return;
        if (s == null) {
            if (mc.level != null && fonduJeu < 0) fonduJeu = ms();   // arrivée par un chemin inattendu : fondu quand même
            return;
        }
        terminer("écran " + s.getClass().getSimpleName());
    }

    /* ================================================================ packs serveur */

    private static final Set<UUID> PACKS = ConcurrentHashMap.newKeySet();
    private static volatile boolean dlEnCours, dlFini;
    private static volatile long dlTotal = -1, dlRecu;

    /** Pack serveur accepté (silencieusement) : il est en route jusqu'à son résultat final. */
    public static void packPousse(UUID id) {
        PACKS.add(id);
        dlFini = false;
        LOG.info("[chargement] pack serveur {} accepté automatiquement", id);
    }

    public static void packFini(UUID id, Object resultat) {
        if (PACKS.remove(id)) LOG.info("[chargement] pack serveur {} : {}", id, resultat);
    }

    static void viderPacks() {
        if (!PACKS.isEmpty()) PACKS.clear();
        dlEnCours = false;
    }

    public static boolean packEnAttente() { return !PACKS.isEmpty(); }

    public static void dlDebut() {
        dlEnCours = true;
        dlFini = false;
        dlRecu = 0;
        dlTotal = -1;
    }

    public static void dlTaille(long total) { dlTotal = total; }

    public static void dlRecu(long octets) { dlRecu = octets; }

    public static void dlFin(boolean ok) {
        dlEnCours = false;
        dlFini = true;
    }

    /** La scène remplace-t-elle l'écran de rechargement des ressources ? (trajet en cours ou pack serveur en route) */
    public static boolean pourRechargement() { return session || packEnAttente(); }

    /* ================================================================ état affiché (sans allocation) */

    enum Source { CONNEXION, RECHARGEMENT, MONDE }

    /** État imposé (banc) : libellé, points animés, progression 0..1 ou -1. */
    record Etat(String texte, boolean points, float progres) {}

    static Etat etatBanc;

    private static final String CONNEXION = "Connexion au serveur", MONDE = "Chargement du monde",
        APPLICATION = "Application des ressources", PREPARATION = "Préparation des ressources",
        TELECHARGEMENT = "Téléchargement des ressources", TRANSFERT = "Changement de serveur";
    private static final Component[] POURCENTS = new Component[101];

    /** Résultat du calcul de l'état (champs réutilisés à chaque image) : libellé, points animés, progression 0..1
     *  (-1 : inconnue, barre indéterminée et pas de pourcentage). */
    private static String eTexte = CONNEXION;
    private static boolean ePoints = true;
    private static float eProgres = -1;

    private static void etat(Source src, Component statut, float progres) {
        if (etatBanc != null) {
            eTexte = etatBanc.texte();
            ePoints = etatBanc.points() && etatBanc.progres() < 0;
            eProgres = etatBanc.progres();
            return;
        }
        ePoints = true;
        eProgres = -1;
        if (dlEnCours) {
            long tot = dlTotal, rec = dlRecu;
            eTexte = TELECHARGEMENT;
            if (tot > 0) {
                eProgres = Math.max(0, Math.min(1f, rec / (float) tot));
                ePoints = false;
            }
            return;
        }
        if (src == Source.RECHARGEMENT) {
            eTexte = APPLICATION;
            eProgres = progres;
        } else if (packEnAttente()) {
            eTexte = dlFini ? APPLICATION : PREPARATION;
        } else if (src == Source.MONDE) {
            eTexte = MONDE;
            eProgres = progres;
        } else {
            eTexte = libelleConnexion(statut);
        }
        if (eProgres >= 0) ePoints = false;
    }

    private static String libelleConnexion(Component statut) {
        if (statut != null && statut.getContents() instanceof TranslatableContents tc) {
            String k = tc.getKey();
            if (k.contains("joining") || k.contains("configur")) return MONDE;
            if (k.contains("transferring")) return TRANSFERT;
        }
        return CONNEXION;
    }

    /* ================================================================ points d'entrée de rendu */

    /** Écran de connexion (statut vanilla). */
    public static void dessinerConnexion(GuiGraphicsExtractor ctx, Component statut) {
        demarrer();
        etat(Source.CONNEXION, statut, -1);
        dessiner(ctx);
    }

    /** Banc : la scène avec l'état imposé ({@link #etatBanc}). */
    static void dessinerBanc(GuiGraphicsExtractor ctx) {
        etat(Source.CONNEXION, null, -1);
        dessiner(ctx);
    }

    /** Écran de chargement du monde (progression lissée du serveur, ou -1). */
    public static void dessinerMonde(GuiGraphicsExtractor ctx, float progres) {
        etat(Source.MONDE, null, progres);
        dessiner(ctx);
    }

    private static Object overlayCourant;
    private static long overlayDebut;

    /**
     * Rechargement des ressources du pack serveur (le fond, le logo et la barre Mojang ne sont pas dessinés). Si l'écran
     * dessous dessine déjà la scène (connexion, chargement du monde), elle est opaque du début à la fin et rien ne
     * saute ; sinon (pack reçu en jeu), fondu du jeu vers la nuit puis vers la scène, et l'inverse à la fin.
     */
    public static void dessinerRechargement(GuiGraphicsExtractor ctx, Object overlay, float progres, long fadeOutStart) {
        long now = ms();
        derniereImage = now;
        if (overlay != overlayCourant) {
            overlayCourant = overlay;
            overlayDebut = now;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.gui == null ? null : mc.gui.screen();
        boolean sceneDessous = s instanceof ConnectScreen || (s instanceof LevelLoadingScreen && pourMonde());
        etat(Source.RECHARGEMENT, null, progres);
        if (sceneDessous) {
            dessiner(ctx);
            return;
        }
        // fondus propres (aplat de nuit) : jamais la scène en transparence
        float nuitAvant = 0.25f;   // part du fondu de sortie (1000 ms vanilla) passée à éteindre la scène
        if (fadeOutStart > 0) {
            float f = (now - fadeOutStart) / 1000f;
            if (f < nuitAvant) {
                dessiner(ctx);
                voile(ctx, f / nuitAvant);
            } else {
                voile(ctx, 1f - Math.min(1f, (f - nuitAvant) / (1f - nuitAvant)));
            }
            return;
        }
        long d = now - overlayDebut;
        if (d < 250) {
            voile(ctx, d / 250f);
        } else {
            dessiner(ctx);
            if (d < 500) voile(ctx, 1f - (d - 250) / 250f);
        }
    }

    /** HUD : la nuit s'efface sur le jeu après l'arrivée. */
    static void dessinerFonduJeu(GuiGraphicsExtractor ctx) {
        if (!session || fonduJeu < 0) return;
        long d = ms() - fonduJeu;
        if (d >= FONDU_JEU_MS) {
            terminer("arrivée en jeu");
            return;
        }
        float a = 1f - d / (float) FONDU_JEU_MS;
        voile(ctx, a * a * (3 - 2 * a));
    }

    private static void voile(GuiGraphicsExtractor ctx, float a) {
        if (a <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        ctx.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
            DaKit.argb(NUIT, Math.min(1f, a)));
    }

    /* ================================================================ la scène */

    /** Pixels écran par unité, origine de la scène et partie visible (unités), fixés à chaque image. */
    static int k = 4, ox, oy, visL, visT, visR, visB, pw, ph;

    private static long derniereImage = -10_000, derniereScene = -10_000;

    /** Les toasts vanilla sont-ils à masquer ? (trajet en cours, ou scène à l'écran) */
    public static boolean masquerToasts() { return session || ms() - derniereImage < 300; }

    /** Banc : durée cumulée de {@link #dessiner} (extraction côté CPU) et nombre d'images. */
    static long coutNs, images;

    private static void geometrie() {
        var w = Minecraft.getInstance().getWindow();
        pw = w.getWidth();
        ph = w.getHeight();
        k = Math.max(1, Math.min(pw / SAFE_W, ph / SAFE_H));
        int lw = -Math.floorDiv(-pw, k), lh = -Math.floorDiv(-ph, k);
        ox = Math.floorDiv(lw - VW, 2);
        oy = Math.floorDiv(lh - VH, 2);
        visL = -ox;
        visT = -oy;
        visR = lw - ox;
        visB = lh - oy;
    }

    static void dessiner(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        liberer = true;
        long t0 = System.nanoTime();
        geometrie();
        double t = temps();
        long now = ms();
        if (now - derniereScene > 500) {       // la scène réapparaît : pas de transition depuis un ancien libellé
            libelle = null;
            progresLisse = -1;
        }
        derniereScene = now;
        derniereImage = now;

        DaKit.unites(ctx, k);
        ctx.pose().translate(ox, oy);
        if (visL < -EXT_X || visR > VW + EXT_X || visT < -EXT_Y) ctx.fill(visL, visT, visR, visB, NUIT);
        blit(ctx, CIEL, -EXT_X, -EXT_Y, 0, CIEL_W, CIEL_H, CIEL_W, CIEL_H);
        etoiles(ctx, t);
        blit(ctx, LUNE, LUNE_X, LUNE_Y, 0, LUNE_S, LUNE_S, LUNE_S, LUNE_S);
        couche(ctx, NUAGES_VOILE, VOILE_Y, VOILE_H, V_VOILE, t, 300);
        corbeaux(ctx, t);
        blit(ctx, VILLAGE, -EXT_X, VILLAGE_Y, 0, CIEL_W, VILLAGE_H, CIEL_W, VILLAGE_H);
        couche(ctx, NUAGES_BAS, BAS_Y, BAS_H, V_BAS, t, 120);
        feuilles(ctx, t);
        blit(ctx, AVANT, -EXT_X, AVANT_Y, 0, CIEL_W, AVANT_H, CIEL_W, AVANT_H);
        if (visB > VH) ctx.fill(visL, VH, visR, visB, SOL);
        if (visR > VW + EXT_X) ctx.fill(VW + EXT_X, AVANT_Y, visR, visB, SOL);
        if (visL < -EXT_X) ctx.fill(visL, AVANT_Y, -EXT_X, visB, SOL);
        texte(ctx, mc.font, now, t);
        DaKit.finUnites(ctx);

        if (session) {
            long d = now - debut;
            if (d < ENTREE_MS && Double.isNaN(tempsBanc)) voile(ctx, 1f - d / (float) ENTREE_MS);
            if (sortie > 0) voile(ctx, Math.min(1f, (now - sortie) / (float) SORTIE_MS));
        }
        coutNs += System.nanoTime() - t0;
        images++;
    }

    private static void blit(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int u, int w, int h, int tw, int th) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, 0f, w, h, tw, th);
    }

    /** Couche de nuages répétée en x, qui dérive vers la droite ; décalage calé sur le pixel écran (2 blits). */
    private static void couche(GuiGraphicsExtractor ctx, Identifier id, int y, int h, float vitesse, double t, int dephasage) {
        double o = (vitesse * t + dephasage) % NUAGES_W;
        o = Math.floor(o * k) / k;
        int ent = (int) Math.floor(o);
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) (o - ent), 0);
        int x = ent - NUAGES_W;
        while (x + NUAGES_W <= visL - 1) x += NUAGES_W;
        for (; x < visR; x += NUAGES_W) blit(ctx, id, x, y, 0, NUAGES_W, h, NUAGES_W, h);
        ctx.pose().popMatrix();
    }

    /** Hachage déterministe 0..1 (remplace un {@code Random} : aucune allocation). */
    private static double h01(long a, int b) {
        long z = a * 0x9E3779B97F4A7C15L + b * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    /* ---- étoiles qui scintillent ---- */

    private static final int[][] ETOILES = new int[14][];

    static {
        int n = 0, i = 0;
        while (n < ETOILES.length) {
            int x = -80 + (int) (h01(1987, i++) * (VW + 160)), y = 4 + (int) (h01(1987, i++) * 96);
            if (Math.hypot(x - 240, y - 118) < 175) continue;
            ETOILES[n++] = new int[] {x, y, (int) (h01(1987, i++) * 1000)};
        }
    }

    private static void etoiles(GuiGraphicsExtractor ctx, double t) {
        for (int[] e : ETOILES) {
            double s = Math.sin(t * (0.9 + (e[2] % 7) * 0.17) + e[2]);
            if (s < 0.2) continue;
            ctx.fill(e[0], e[1], e[0] + 1, e[1] + 1, DaKit.argb(ETOILE, (float) ((s - 0.2) / 0.8)));
        }
    }

    /* ---- corbeaux : une volée toutes les 13 s, qui traverse la lune ---- */

    private static final double PERIODE_VOLEE = 13.0;
    private static final int[] BATTEMENT = {0, 1, 2, 3, 2, 1};

    private static void corbeaux(GuiGraphicsExtractor ctx, double t) {
        double tt = t + 5.0;
        long n = (long) Math.floor(tt / PERIODE_VOLEE);
        for (long v = n - 1; v <= n; v++) {
            double lt = tt - v * PERIODE_VOLEE;
            int nb = 3 + (int) (h01(v, 0) * 3);
            double vit = 24 + h01(v, 1) * 8;
            double yb = 34 + h01(v, 2) * 70;
            double x0 = visL - 30 + vit * lt;
            for (int i = 0; i < nb; i++) {
                double dx = -i * (9 + h01(v, 10 + i) * 8), dy = (i % 2 == 0 ? 1 : -1) * (i * 3.5 + h01(v, 20 + i) * 3);
                double x = x0 + dx, y = yb + dy + 2.2 * Math.sin(lt * 1.6 + i * 1.3);
                if (x < visL - CB_W || x > visR) continue;
                int fr = Math.sin(lt * 0.8 + i) > 0.55 ? 2 : BATTEMENT[(int) Math.floor(lt * 9.0 + i * 1.7) % BATTEMENT.length];
                blit(ctx, CORBEAU, (int) Math.round(x), (int) Math.round(y), fr * CB_W, CB_W, CB_H, CB_W * CB_N, CB_H);
            }
        }
    }

    /* ---- feuilles emportées par le vent ---- */

    private static void feuilles(GuiGraphicsExtractor ctx, double t) {
        int largeur = visR - visL + 40;
        for (int i = 0; i < 4; i++) {
            double per = 7.5 + i * 1.9;
            double u = ((t + i * 3.1) % per) / per;
            double x = visL - 20 + largeur * u;
            double y = 128 + i * 15 + 34 * u + 5 * Math.sin(t * 1.3 + i * 2.1);
            int fr = (int) Math.floor(t * 7 + i) % FE_N;
            blit(ctx, FEUILLE, (int) Math.round(x), (int) Math.round(y), fr * FE_W, FE_W, FE_H, FE_W * FE_N, FE_H);
        }
    }

    /* ---- barre de chargement pixel art, libellé et pourcentage (ArcadePix, unités de la scène) ---- */

    private static String libelle;
    private static long libelleDepuis;
    private static float progresLisse = -1;
    /** Libellés ArcadePix mis en cache (composant et largeur), recalculés seulement quand le libellé change. */
    private static Component cComp, cCompPrec;
    private static int cLarg;
    private static final Component[] POINTS = new Component[4];

    /** Taille ArcadePix : un pixel de fonte = n pixels écran (2 en 1080p et 1366×768, 4 en 4K). */
    private static int pixelFonte() { return Math.max(1, Math.round(k / 2f)); }

    private static void texte(GuiGraphicsExtractor ctx, Font font, long now, double t) {
        if (libelle == null || !eTexte.equals(libelle)) {
            cCompPrec = libelle == null ? null : cComp;
            libelleDepuis = libelle == null ? now - 1000 : now;
            libelle = eTexte;
            cComp = DaKit.arcade(eTexte.toUpperCase(java.util.Locale.ROOT));
            cLarg = font.width(cComp);
        }
        // barre : 40 % de la largeur visible, centrée sur la scène
        int bw = Math.max(80, (int) ((visR - visL) * 0.40f)) & ~1;
        int bx = VW / 2 - bw / 2, by = BARRE_Y, bh = BARRE_H;
        float sc = pixelFonte() / (float) k;               // unités par pixel de fonte
        float tr = Double.isNaN(tempsBanc) ? Math.min(1f, (now - libelleDepuis) / 320f) : 1f;
        float yTxt = by - 4 - 7 * sc;                       // capitales ArcadePix : 7 pixels de fonte
        if (tr < 1f && cCompPrec != null) arcade(ctx, font, cCompPrec, bx, yTxt - tr * 2, sc, 1f - tr);
        arcade(ctx, font, cComp, bx, yTxt + (1f - tr) * 2, sc, tr);
        if (ePoints) {
            int n = (int) Math.floor(t * 2.5) % 4;
            if (n > 0) {
                if (POINTS[n] == null) POINTS[n] = DaKit.arcade(".".repeat(n));
                arcade(ctx, font, POINTS[n], bx + cLarg * sc, yTxt + (1f - tr) * 2, sc, tr);
            }
        }

        // cadre fin (coins coupés), ombre intérieure, rail
        ctx.fill(bx - 1, by, bx + bw + 1, by + bh, CADRE);
        ctx.fill(bx, by - 1, bx + bw, by + bh + 1, CADRE);
        ctx.fill(bx, by, bx + bw, by + bh, RAIL);
        ctx.fill(bx, by, bx + bw, by + 1, RAIL_HAUT);
        int ix = bx, iy = by + 1, iw = bw;                  // intérieur : 5 rangées sous l'ombre
        float p = eProgres;
        if (p >= 0) {
            progresLisse = progresLisse < 0 || p < progresLisse - 0.2f ? p : progresLisse + (p - progresLisse) * 0.25f;
            int fw = Math.round(iw * Math.min(1f, progresLisse));
            remplir(ctx, ix, iy, fw, 1f);
            if (fw > 1 && fw < iw) ctx.fill(ix + fw - 1, iy, ix + fw, iy + BLEUS.length, TETE);
            int pct = Math.round(Math.min(1f, p) * 100);
            if (POURCENTS[pct] == null) POURCENTS[pct] = DaKit.arcade(pct + "%");
            arcade(ctx, font, POURCENTS[pct], bx + bw + 5, by + (bh - 7 * sc) / 2f, sc, 1f);
        } else {
            // progression inconnue : un éclat bleu glisse dans la barre, avec sa traîne
            progresLisse = -1;
            int seg = Math.max(12, iw / 5);
            double u = (t * 0.45) % 1.0;
            int x0 = (int) Math.round(-seg + u * (iw + seg));
            remplirClip(ctx, ix, iy, iw, x0 - seg / 2, seg / 2, 0.35f);
            remplirClip(ctx, ix, iy, iw, x0, seg, 1f);
        }
    }

    /** Remplissage bleu (5 rangées : reflet en haut, plus sombre en bas) sur w unités. */
    private static void remplir(GuiGraphicsExtractor ctx, int x, int y, int w, float a) {
        if (w <= 0) return;
        for (int i = 0; i < BLEUS.length; i++) ctx.fill(x, y + i, x + w, y + i + 1, DaKit.alpha(BLEUS[i], a));
    }

    private static void remplirClip(GuiGraphicsExtractor ctx, int ix, int iy, int iw, int x0, int w, float a) {
        int g = Math.max(0, x0), d = Math.min(iw, x0 + w);
        if (d > g) remplir(ctx, ix + g, iy, d - g, a);
    }

    /** ArcadePix au pixel écran (ombre d'un pixel de fonte), coin haut gauche en (x, y) unités. */
    private static void arcade(GuiGraphicsExtractor ctx, Font font, Component c, float x, float y, float sc, float a) {
        if (a <= 0.01f || c == null) return;
        float xs = Math.round(x * k) / (float) k, ys = Math.round(y * k) / (float) k;
        ctx.pose().pushMatrix();
        ctx.pose().translate(xs, ys);
        ctx.pose().scale(sc, sc);
        ctx.text(font, c, 1, 1, DaKit.alpha(OMBRE, a), false);
        ctx.text(font, c, 0, 0, DaKit.alpha(TEXTE, a), false);
        ctx.pose().popMatrix();
    }

    /* ================================================================ branchements */

    /** Appelé une fois par {@code RebornHudClient} : fondu HUD, tick, banc. */
    public static void init() {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("reborn-hud", "chargement_fondu"),
            (ctx, tickCounter) -> dessinerFonduJeu(ctx));
        // fin du trajet (déconnexion, refus, retour au menu) et oubli des packs : constatés au tick, sur le fil
        // principal (l'évènement DISCONNECT d'une ancienne connexion peut arriver après le clic suivant)
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(ChargementScene::tick);
        ChargementDebug.init();
    }
}
