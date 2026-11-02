package fr.reborn.hud.ui;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.ui.TexteRaster.Ancre;
import fr.reborn.hud.ui.TexteRaster.Demande;
import fr.reborn.hud.ui.TexteRaster.Equilibrage;
import fr.reborn.hud.ui.TexteRaster.Masque;
import fr.reborn.hud.ui.TexteRaster.Mise;
import fr.reborn.hud.ui.TexteRaster.Police;
import fr.reborn.hud.ui.TexteRaster.Preset;
import fr.reborn.hud.ui.TexteRaster.Rendu;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Fil, textures et poignées de {@link TexteRaster} (lot 2, brief §1.4 et §7.1).
 *
 * <ul>
 *   <li><b>Un seul fil</b>, {@code Reborn-Texte} (pas {@code Util.backgroundExecutor()}, qui est un pool) : FreeType
 *       n'est appelé que là, avec la bibliothèque propre au mod.</li>
 *   <li><b>Textures</b> {@code reborn:texte/<n>} : {@link NativeImage} remplie sur le fil du texte, puis
 *       {@link DynamicTexture} créée et enregistrée sur le fil de rendu, libérée par {@code TextureManager.release}
 *       (comme {@code screenshot/ScreenshotTextures}). Lecture au plus proche, blit à 1:1 sur des pixels entiers
 *       ({@link DaKit#debutPx}) ; le fondu passe par l'alpha des sommets.</li>
 *   <li><b>Poignées</b> {@link Texte} : une par texte affiché, gardée par l'élément qui le dessine. À chaque image,
 *       {@code ligne(…)} / {@code bloc(…)} / {@code forme(…)} comparent la demande à la précédente <b>sans
 *       allouer</b>, puis {@code dessiner} fait un seul blit. Un changement de contenu part tout de suite ; un
 *       changement d'échelle (taille de la fenêtre, échelle de l'élément dans l'éditeur) part après
 *       {@value #ATTENTE_ECHELLE_MS} ms sans nouveau changement, et l'ancienne texture reste affichée en attendant.
 *       Déplacer l'ancre d'un nombre entier de pixels ne recalcule rien.</li>
 *   <li><b>LRU</b> de {@value #LRU_PETITES} petites textures (lignes et formes du HUD) ; les blocs (narration,
 *       plaques) sont hors LRU, libérés par {@link Texte#liberer()} ou remplacés.</li>
 * </ul>
 *
 * <p>Exemple (un libellé du bas-droite, k = kHud / 2) :
 * <pre>{@code
 * static final TexteCache.Texte PORTEE = new TexteCache.Texte();
 * ...
 * PORTEE.ligne("Portée de la voix", TexteCache.Style.LIBELLE, 12 * k, k, 0, xLibelle, yCentre).dessiner(ctx, alpha);
 * }</pre>
 */
public final class TexteCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/texte");

    /** Délai sans changement d'échelle avant de recalculer (brief §7.1). */
    public static final long ATTENTE_ECHELLE_MS = 150;
    /**
     * Petites textures du HUD gardées au plus (brief §1.4 : 32, porté à 64 à l'étape 2b, à 96 à la revue du 02/11) : la
     * colonne bas-droite au complet (trois orateurs et leurs barres, quatre notifications, quatre rappels) et le lecteur
     * OST en fondu croisé en dessinent jusqu'à ≈ 55 à la même image ; à 32, la LRU tournait à chaque image (rendus
     * FreeType en boucle), à 64 la marge n'était que de quelques textures.
     */
    public static final int LRU_PETITES = 96;

    private TexteCache() {}

    /* ================================================================ fil Reborn-Texte */

    private static volatile Thread fil;
    /**
     * Un seul fil, file à <b>priorité</b> : les petites demandes (lignes et formes du HUD, travaux du banc) passent avant
     * les blocs (narration, plaque, aperçu du Poste de garde : jusqu'à quelques centaines de ms chacun) ; dans une même
     * priorité, l'ordre d'arrivée. Un bloc déjà commencé n'est pas interrompu.
     */
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new PriorityBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "Reborn-Texte");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            fil = t;
            return t;
        });
    private static final AtomicLong ORDRE = new AtomicLong();
    private static final int PRIO_PETITE = 0, PRIO_BLOC = 1;

    /** Travail en file : priorité, puis ordre d'arrivée. */
    private record Tache(int prio, long ordre, Runnable r) implements Runnable, Comparable<Tache> {
        @Override public void run() { r.run(); }

        @Override public int compareTo(Tache o) {
            return prio != o.prio ? Integer.compare(prio, o.prio) : Long.compare(ordre, o.ordre);
        }
    }

    private static void soumettre(int prio, Runnable r) { POOL.execute(new Tache(prio, ORDRE.incrementAndGet(), r)); }

    /** Exécuteur à la priorité des petites demandes (banc, mesures, polices). */
    private static final Executor EXEC = r -> soumettre(PRIO_PETITE, r);

    /** Vrai sur le fil {@code Reborn-Texte}. */
    public static boolean surFil() { return Thread.currentThread() == fil; }

    /** Exécute un travail sur le fil du texte (rendu, mesure, banc). */
    public static <T> CompletableFuture<T> executer(Supplier<T> travail) {
        return CompletableFuture.supplyAsync(travail, EXEC);
    }

    /** Ouvre les polices à l'avance, sur le fil du texte (le premier rendu n'a plus à les lire). */
    public static void prechauffer() {
        EXEC.execute(() -> {
            try {
                for (Police p : Police.values()) TexteRaster.face(p);
            } catch (RuntimeException e) {
                LOGGER.error("TexteRaster : polices illisibles", e);
            }
        });
    }

    /**
     * Démarrage du client : polices ouvertes à l'avance sur le fil du texte (le lecteur OST, premier consommateur, n'a
     * pas à attendre la lecture des TTF) ; fermeture : libère les textures (fil de rendu), puis FreeType sur son fil.
     */
    public static void init() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> prechauffer());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> fermer());
    }

    static void fermer() {
        for (Tex t : new ArrayList<>(VIVANTES)) relacher(t);
        if (fil == null) return;   // jamais servi
        try {
            executer(() -> {
                TexteRaster.fermer();
                return null;
            }).get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("TexteRaster : fermeture de FreeType incomplète ({})", e.toString());
        }
        POOL.shutdown();
    }

    /* ================================================================ compteurs (banc) */

    /** Textures créées / libérées, demandes envoyées, rendus sautés (dépassés), résultats jetés, évictions LRU. */
    public static volatile int texturesCreees, texturesLiberees, demandes, rendusSautes, resultatsJetes, evictions,
        erreurs;
    /** Durée des rendus (fil du texte, ms) : lignes et formes / blocs — somme, nombre, maximum. */
    public static volatile long petitsMs, petitsN, petitsMaxMs, blocsMs, blocsN, blocsMaxMs;

    public static int texturesVivantes() { return VIVANTES.size(); }

    public static int petitesVivantes() { return PETITES.size(); }

    public static String bilan() {
        return "textures " + texturesCreees + " créées / " + texturesLiberees + " libérées (" + VIVANTES.size()
            + " vivantes, " + PETITES.size() + " petites) · demandes " + demandes + " · sautées " + rendusSautes
            + " · résultats jetés " + resultatsJetes + " · évictions " + evictions + " · erreurs " + erreurs
            + " · rendus : lignes " + petitsN + " (moy. " + (petitsN == 0 ? 0 : petitsMs / petitsN) + " ms, max "
            + petitsMaxMs + " ms), blocs " + blocsN + " (moy. " + (blocsN == 0 ? 0 : blocsMs / blocsN) + " ms, max "
            + blocsMaxMs + " ms)";
    }

    /* ================================================================ textures */

    /** Texture posée : position de la couche relative à la partie entière de l'ancre du rendu, mise en page. */
    static final class Tex {
        final Identifier id;
        final int w, h, dx, dy;
        final Mise mise;
        final boolean petite;
        final Texte proprietaire;
        long dernierDessin;
        boolean libere, dessinee;

        Tex(Identifier id, int w, int h, int dx, int dy, Mise mise, boolean petite, Texte proprietaire) {
            this.id = id;
            this.w = w;
            this.h = h;
            this.dx = dx;
            this.dy = dy;
            this.mise = mise;
            this.petite = petite;
            this.proprietaire = proprietaire;
            this.dernierDessin = Util.getMillis();
        }
    }

    private static final AtomicInteger NUMERO = new AtomicInteger();
    private static final ArrayList<Tex> VIVANTES = new ArrayList<>();
    private static final ArrayList<Tex> PETITES = new ArrayList<>();
    private static boolean averti;

    /** Couche → NativeImage (fil du texte : rien que de la mémoire, pas de GL). À fermer si elle n'est pas envoyée. */
    public static NativeImage image(Rendu r) {
        NativeImage img = new NativeImage(r.w, r.h, false);
        for (int y = 0; y < r.h; y++) {
            int l = y * r.w;
            for (int x = 0; x < r.w; x++) img.setPixel(x, y, r.argb[l + x]);
        }
        return img;
    }

    /** Envoi GL (fil de rendu) ; l'image appartient ensuite à la texture. */
    private static Tex televerser(NativeImage img, int dx, int dy, Mise mise, boolean petite, Texte proprietaire) {
        Identifier id = Identifier.fromNamespaceAndPath("reborn", "texte/" + NUMERO.incrementAndGet());
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "reborn-texte", img));
        Tex t = new Tex(id, img.getWidth(), img.getHeight(), dx, dy, mise, petite, proprietaire);
        texturesCreees++;
        VIVANTES.add(t);
        if (petite) {
            PETITES.add(t);
            while (PETITES.size() > LRU_PETITES) evincer(t);
        }
        return t;
    }

    /** Libère la petite texture la moins récemment dessinée (sauf {@code garder}) ; sa poignée la redemandera. */
    private static void evincer(Tex garder) {
        Tex vieille = null;
        for (Tex t : PETITES) if (t != garder && (vieille == null || t.dernierDessin < vieille.dernierDessin)) vieille = t;
        if (vieille == null) return;
        if (!averti && vieille.dessinee && Util.getMillis() - vieille.dernierDessin < 1000) {
            averti = true;
            LOGGER.warn("TexteCache : plus de {} petites textures dessinées à la fois, la LRU tourne", LRU_PETITES);
        }
        evictions++;
        Texte p = vieille.proprietaire;
        if (p != null && p.tex == vieille) {
            p.tex = null;
            p.aEnvoyer = p.voulue != null || p.masque != null;
            p.contenuChange = true;
        }
        relacher(vieille);
    }

    private static void relacher(Tex t) {
        if (t.libere) return;
        t.libere = true;
        Minecraft.getInstance().getTextureManager().release(t.id);
        VIVANTES.remove(t);
        PETITES.remove(t);
        texturesLiberees++;
    }

    private static double frac(double v) { return v - Math.floor(v); }

    /* ================================================================ styles du lot (maquettes v2) */

    /**
     * Style constant d'une ligne de HUD (police, préréglage, couleur, ancre, typographie, centrage sur l'encre) : gardé
     * dans une constante, il ne s'alloue pas à chaque image.
     */
    public record Style(Police police, Preset preset, String couleur, Ancre ancre, boolean typo, boolean centrageEncre) {
        /** Titre de l'OST : Inter Bold, capitales 12·k, #F3F0EA. */
        public static final Style TITRE_OST = new Style(Police.INTER_GRAS, Preset.HUD, "hud", Ancre.GAUCHE, true, false);
        /** Libellé de rappel, fini à droite : Inter Bold, capitales 12·k. */
        public static final Style LIBELLE = new Style(Police.INTER_GRAS, Preset.HUD, "hud", Ancre.DROITE, true, false);
        /** Touche dans sa case : Inter Bold, capitales 10·k, centrée, sans typographie. */
        public static final Style TOUCHE = new Style(Police.INTER_GRAS, Preset.HUD, "hud", Ancre.CENTRE, false, false);
        /** Touche de ponctuation (« . », « ; ») centrée sur son encre. */
        public static final Style TOUCHE_PONCT = new Style(Police.INTER_GRAS, Preset.HUD, "hud", Ancre.CENTRE, false, true);
        /** Nom de qui parle : police du récit, capitales 12·k, interlettre 1·k, sans typographie. */
        public static final Style NOM = new Style(Police.RECIT, Preset.HUD, "hud", Ancre.GAUCHE, false, false);
        /** « INCONNU » (#A8A0A0). */
        public static final Style NOM_INCONNU = new Style(Police.RECIT, Preset.HUD, "inconnu", Ancre.GAUCHE, false, false);
        /** Notification, texte principal (Inter Bold, capitales 10·k), fini à droite. */
        public static final Style NOTIF = new Style(Police.INTER_GRAS, Preset.HUD, "hud", Ancre.DROITE, true, false);
        /** Notification, détail (Inter Medium, #C2B59A). */
        public static final Style NOTIF_DETAIL = new Style(Police.INTER_MEDIUM, Preset.HUD, "doux", Ancre.DROITE, true, false);
    }

    /* ================================================================ poignée */

    /**
     * Un texte (ou une forme) affiché : demande courante, texture affichée, rendu en cours. Fil de rendu seulement
     * (sauf le numéro de la dernière demande, lu par le fil du texte pour sauter un rendu déjà dépassé).
     */
    public static final class Texte {

        private static final int LIGNE = 0, BLOC = 1, FORME = 2;

        private int genre = -1;
        Demande voulue;
        Masque masque;
        private int rgbForme;
        private double fForme;
        private float alphaForme = 1f;
        boolean aEnvoyer, contenuChange;
        private long changeA;
        private double ancreX, ancreY;
        private long seq, texSeq;
        private volatile long seqVoulue = -1;
        Tex tex;

        /**
         * Ligne de HUD posée sur x selon l'ancre du style, centrée verticalement sur yCentre (milieu des capitales, ou
         * de l'encre), en pixels écran. {@code cap} et {@code interlettre} en pixels écran, {@code f} = facteur du
         * contour (k = kHud / 2 pour le HUD). Rien n'est alloué si rien n'a changé.
         */
        public Texte ligne(String texte, Style s, double cap, double f, double interlettre, double x, double yCentre) {
            if (texte == null) texte = "";
            Demande v = voulue;
            boolean contenu = genre != LIGNE || v == null || !v.texte().equals(texte) || v.police() != s.police()
                || v.preset() != s.preset() || !v.couleurDefaut().equals(s.couleur()) || v.ancre() != s.ancre()
                || v.typo() != s.typo() || v.centrageEncre() != s.centrageEncre();
            boolean echelle = !contenu && (v.cap() != cap || v.f() != f || v.interlettre() != interlettre
                || frac(v.cx()) != frac(x) || frac(v.cy()) != frac(yCentre));
            ancreX = x;
            ancreY = yCentre;
            if (contenu || echelle) {
                genre = LIGNE;
                voulue = new Demande(texte, s.police(), s.preset(), cap, TexteRaster.SANS_LIMITE, 0, f, x, yCentre,
                    s.ancre(), interlettre, s.couleur(), s.typo(), true, s.centrageEncre(), Equilibrage.JAMAIS, null);
                changer(contenu);
            } else if (aEnvoyer) maj();   // envoi en attente (délai d'échelle) sur une poignée qu'on ne dessine pas
            return this;
        }

        /**
         * Bloc centré sur (cx, cy) en pixels écran (narration, plaque) : capitales {@code cap}, largeur maximale,
         * pas de ligne, facteur f (voir {@link TexteRaster#capRecit} et voisines).
         */
        public Texte bloc(String texte, Police police, Preset preset, double cap, double largeurMax, int pas, double f,
                          double cx, double cy) {
            return bloc(texte, police, preset, cap, largeurMax, pas, f, cx, cy, 0.0, Equilibrage.AUTO, null);
        }

        /**
         * Bloc, tous réglages ({@link Demande#bloc(String, Police, Preset, double, double, int, double, double, double,
         * double, Equilibrage, TexteRaster.Ombre)}) : approche, équilibrage, ombre douce (null = ombre dure ; comparée par
         * valeur, garder une instance par échelle pour ne rien allouer à chaque image).
         */
        public Texte bloc(String texte, Police police, Preset preset, double cap, double largeurMax, int pas, double f,
                          double cx, double cy, double interlettre, Equilibrage equilibrage, TexteRaster.Ombre ombre) {
            if (texte == null) texte = "";
            Demande v = voulue;
            boolean contenu = genre != BLOC || v == null || !v.texte().equals(texte) || v.police() != police
                || v.preset() != preset || v.equilibrage() != equilibrage;
            boolean echelle = !contenu && (v.cap() != cap || v.largeurMax() != largeurMax || v.pas() != pas
                || v.f() != f || v.interlettre() != interlettre || !java.util.Objects.equals(v.ombre(), ombre)
                || frac(v.cx()) != frac(cx) || frac(v.cy()) != frac(cy));
            ancreX = cx;
            ancreY = cy;
            if (contenu || echelle) {
                genre = BLOC;
                voulue = Demande.bloc(texte, police, preset, cap, largeurMax, pas, f, cx, cy, interlettre, equilibrage, ombre);
                changer(contenu);
            } else if (aEnvoyer) maj();   // page préparée d'avance : jamais dessinée, son recalcul part quand même
            return this;
        }

        /**
         * Forme du HUD (préréglage {@link Preset#FORME}, facteur f) posée avec le coin haut gauche du masque en (x, y)
         * entiers. Le masque est comparé par référence : garder un masque par état (une barre par hauteur…).
         */
        public Texte forme(Masque m, int rgb, double f, int x, int y) {
            return forme(m, rgb, f, 1f, x, y);
        }

        /**
         * Comme {@link #forme(Masque, int, double, int, int)}, avec une opacité <b>cuite dans la couche</b> (ombre,
         * contour et remplissage chacun × {@code alpha}, comme {@code poser_forme(alpha=…)} des maquettes) : sur un fond
         * clair, une forme cuite à 70 % garde son contour sous le remplissage, ce que l'alpha des sommets ne rend pas.
         * Une poignée par opacité (lecteur OST : filet à 100 / 70 / 55 %, barres à 85 %) ; le fondu d'apparition reste
         * à l'alpha des sommets ({@link #dessiner}).
         */
        public Texte forme(Masque m, int rgb, double f, float alpha, int x, int y) {
            // un nouveau masque avec un nouveau f (échelle de l'élément tirée dans l'éditeur, fenêtre) est un changement
            // d'échelle : il attend 150 ms et l'ancienne texture reste (pas un rendu et un envoi GPU à chaque image)
            boolean contenu = genre != FORME || rgbForme != rgb || alphaForme != alpha || masque != m && fForme == f;
            boolean echelle = !contenu && (fForme != f || masque != m);
            ancreX = x;
            ancreY = y;
            if (contenu || echelle) {
                genre = FORME;
                voulue = null;
                masque = m;
                rgbForme = rgb;
                fForme = f;
                alphaForme = alpha;
                changer(contenu);
            } else if (aEnvoyer) maj();
            return this;
        }

        private void changer(boolean contenu) {
            aEnvoyer = true;
            contenuChange |= contenu;
            changeA = Util.getMillis();
            maj();
        }

        private void maj() {
            if (!aEnvoyer) return;
            if (tex != null && !contenuChange && Util.getMillis() - changeA < ATTENTE_ECHELLE_MS) return;
            aEnvoyer = false;
            contenuChange = false;
            if (genre == FORME) {
                installerForme();
                return;
            }
            final Demande d = voulue;
            if (d == null) return;
            final long n = ++seq;
            seqVoulue = n;
            demandes++;
            final boolean petite = genre != BLOC;
            final Texte moi = this;
            soumettre(petite ? PRIO_PETITE : PRIO_BLOC, () -> {
                if (moi.seqVoulue != n) {
                    rendusSautes++;
                    return;
                }
                Rendu r;
                NativeImage img;
                long t0 = System.nanoTime();
                try {
                    r = TexteRaster.rendre(d);
                    img = image(r);
                } catch (Throwable e) {
                    erreurs++;
                    LOGGER.error("TexteRaster : rendu impossible de « {} »", d.texte(), e);
                    return;
                }
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                if (petite) {
                    petitsMs += ms;
                    petitsN++;
                    petitsMaxMs = Math.max(petitsMaxMs, ms);
                } else {
                    blocsMs += ms;
                    blocsN++;
                    blocsMaxMs = Math.max(blocsMaxMs, ms);
                }
                int dx = r.x - (int) Math.floor(d.cx()), dy = r.y - (int) Math.floor(d.cy());
                Minecraft.getInstance().execute(() -> moi.installer(n, img, dx, dy, r.mise, petite));
            });
        }

        private void installer(long n, NativeImage img, int dx, int dy, Mise mise, boolean petite) {
            if (n <= texSeq) {
                img.close();
                resultatsJetes++;
                return;
            }
            Tex t = televerser(img, dx, dy, mise, petite, this);
            Tex ancien = tex;
            tex = t;
            texSeq = n;
            if (ancien != null) relacher(ancien);
        }

        private void installerForme() {
            Rendu r = TexteRaster.forme(masque, rgbForme, fForme, Math.max(1, TexteRaster.arrondi(fForme)),
                Preset.FORME.contour, Preset.FORME.ombre, alphaForme);
            NativeImage img = image(r);
            long n = ++seq;
            installer(n, img, r.x, r.y, null, true);
        }

        /** Dessine la texture courante (ou la précédente, pendant un recalcul) : un blit à 1:1 en pixels écran. */
        public void dessiner(GuiGraphicsExtractor ctx, float alpha) {
            if (tex == null && !aEnvoyer) return;
            DaKit.debutPx(ctx);
            dessinerPx(ctx, alpha);
            DaKit.finPx(ctx);
        }

        /** Comme {@link #dessiner}, quand on est déjà en pixels écran ({@link DaKit#debutPx}). */
        public void dessinerPx(GuiGraphicsExtractor ctx, float alpha) {
            maj();
            Tex t = tex;
            if (t == null) return;
            t.dernierDessin = Util.getMillis();
            t.dessinee = true;
            int col = DaKit.blanc(alpha);
            if ((col >>> 24) == 0) return;
            ctx.blit(RenderPipelines.GUI_TEXTURED, t.id, x(), y(), 0f, 0f, t.w, t.h, t.w, t.h, col);
        }

        /** Une texture est prête (celle de la demande courante ou la précédente). */
        public boolean pret() { return tex != null; }

        /** Le rendu affiché correspond à la dernière demande (aucun calcul en attente ni en cours). */
        public boolean ajour() { return tex != null && !aEnvoyer && texSeq == seq; }

        /** Mise en page du texte affiché (lignes, largeurs, em…), ou null. */
        public Mise mise() { return tex != null ? tex.mise : null; }

        /** Largeur d'avance de la plus longue ligne (px), ou -1 tant que rien n'est prêt. */
        public double largeur() { return tex != null && tex.mise != null ? tex.mise.largeurBloc : -1; }

        /** Rectangle de la couche à l'ancre courante, en pixels écran (contour et ombre compris). */
        public int x() { return tex == null ? 0 : (int) Math.floor(ancreX) + tex.dx; }

        public int y() { return tex == null ? 0 : (int) Math.floor(ancreY) + tex.dy; }

        public int w() { return tex == null ? 0 : tex.w; }

        public int h() { return tex == null ? 0 : tex.h; }

        /** Libère la texture et oublie la demande ; un rendu encore en route sera jeté. La poignée resservira. */
        public void liberer() {
            if (tex != null) relacher(tex);
            tex = null;
            voulue = null;
            masque = null;
            genre = -1;
            aEnvoyer = contenuChange = false;
            texSeq = seq;
            seqVoulue = -1;
        }
    }
}
