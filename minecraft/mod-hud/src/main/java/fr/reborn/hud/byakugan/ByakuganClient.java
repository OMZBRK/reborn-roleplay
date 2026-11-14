package fr.reborn.hud.byakugan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.keybind.HudKeybinds;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.LongSupplier;

/**
 * <b>Byakugan</b>, côté mod (lot 5 « Mobilité », étape B ; refait le 09/11, encre de Ketto refaite le 10/11). Vision du porteur seulement, rien n'est montré aux autres sauf, de près, des yeux pâles ({@link ByakuganSigneLayer}).
 *
 * <ul>
 *   <li><b>Touche O</b> ({@link HudKeybinds#BYAKUGAN}), en bascule. Sans droit (clan, yeux : le serveur le dit à la
 *       connexion par {@code regles}), elle ne fait rien. Le serveur ({@code reborn:dojutsu}, ShinobiCore) accepte, paie
 *       (0,1 % de la réserve par seconde), coupe (KO, mort, cinématique, capacité {@code TECHNIQUE} à 0, chakra épuisé…).</li>
 *   <li><b>Filtre</b> {@code reborn-hud:byakugan} (un seul post-effet, posé par {@link fr.reborn.hud.effets.FiltresEcran},
 *       priorité KO &gt; Byakugan &gt; concentration &gt; météo) : monde pâle, gris et froid, feuillages sombres, et
 *       <b>l'encre de Ketto</b> (5e tour, 10/11 : « les blocs et les joueurs contourés ») : trait blanc net de ~2 px sur
 *       les arêtes des blocs, silhouettes et plis lus dans la profondeur seule, et (7e tour, 11/11) la grille des blocs sur
 *       chaque face, le pixel replacé dans le monde par la projection de l'image ({@link #vue}) ; jamais sur les textures,
 *       ni dans le ciel, ni sur les nuages ; affiné au loin. Le filtre est compilé dès que le serveur donne le droit
 *       ({@link #prechauffer}). Pour que la profondeur ne soit que de
 *       la géométrie, les feuilles sont pleines et les plantes basses cachées pendant la vision ({@link FeuillagePlein},
 *       l'option du joueur n'est jamais touchée). Le brouillard du jeu recule pendant la vision
 *       ({@code FogRendererByakuganMixin}). La profondeur sert aussi à la brume du lointain le jour et à la lueur du proche
 *       la nuit et sous terre, où il ne reste presque que les traits et le cyan. Fondu : {@code 1 − exp(−t / 0,16 s)} à l'entrée (fini à 0,6 s), linéaire en
 *       0,27 s à la sortie, sans flash ; les traits arrivent avant la couleur et partent avant elle. Gain adaptatif :
 *       l'obscurité lue à la position du joueur, lissée sur environ une demi-seconde.</li>
 *   <li><b>Personnes</b> à portée : corps et réseau de chakra dessinés dans des couleurs-clés que le filtre rend cyan
 *       (corps cyan pâle, réseau vif, tenketsu presque blancs) ; derrière un mur, seulement un liseré cyan (le contour
 *       vanilla, relu tous les 4 ticks par deux rayons ; pas sur le porteur qui est devant). Ni nom, ni chiffre, ni lecture
 *       de chakra.</li>
 *   <li><b>Rappel</b> « Vision du Byakugan [O] » pendant l'action seulement (décision du 05/11), aucune annonce.</li>
 * </ul>
 * Hors vision : aucun rayon, aucun filtre, texture jamais réécrite, aucune allocation.
 */
public final class ByakuganClient {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/byakugan");

    public static final Identifier FILTRE = Identifier.fromNamespaceAndPath("reborn-hud", "byakugan");
    /** Lue par le filtre (entrée « texture » reborn-hud:byakugan_params → textures/effect/byakugan_params.png). */
    public static final Identifier PARAMS = Identifier.fromNamespaceAndPath("reborn-hud", "textures/effect/byakugan_params.png");

    /**
     * Couleurs-clés reconnues par le filtre (vert nul, le bleu dit la classe) : corps et contour {@code FF00FF}, réseau
     * {@code FF0066}, tenketsu {@code FF00A6}. Ni la lave ni le feu ni les vêtements rouges n'y entrent (contrairement à
     * l'orange d'avant) ; seul un magenta franc du décor peut prendre une teinte cyan pâle.
     */
    public static final int CLE_CONTOUR = 0xFF00FF;
    public static final Identifier SILHOUETTE = Identifier.fromNamespaceAndPath("reborn-hud", "textures/entity/byakugan/silhouette.png");
    /** Silhouette des joueurs et mannequins : couche de base du skin seule (la couche extérieure laisse voir le réseau). */
    public static final Identifier SILHOUETTE_JOUEUR = Identifier.fromNamespaceAndPath("reborn-hud", "textures/entity/byakugan/silhouette_joueur.png");
    /** Yeux pâles du porteur, vus par les autres de près (gabarit de skin 64 × 64, face de la tête seulement). */
    public static final Identifier SIGNE = Identifier.fromNamespaceAndPath("reborn-hud", "textures/entity/byakugan/signe.png");

    /** Fondu d'entrée : constante de temps et fin forcée ; sortie linéaire. */
    public static final long ENTREE_TAU_MS = 160, ENTREE_FIN_MS = 600, SORTIE_MS = 270;
    /** Les personnes passent en couleurs-clés à partir de ce niveau du fondu (le monde est déjà à un quart). */
    public static final float SEUIL_PERSONNES = 0.25f;
    /** Une demande sans réponse du serveur est oubliée après ce délai (la touche redevient libre). */
    public static final long ATTENTE_MAX_MS = 2000;
    /** Boucles d'encre (grands anneaux au pinceau fixés à l'écran, 8e tour) ; le banc peut les couper. */
    public static volatile boolean anneaux = true;
    /** Un tour de la phase des boucles d'encre. */
    private static final long BOUCLES_MS = 64000L;
    /** Banc : le filtre montre des bandes de distance au lieu du rendu (vérifie la lecture de la profondeur). */
    public static volatile boolean debugProfondeur = false;
    /** Banc : le filtre montre les traits seuls, blanc sur noir (part de pixels de traits, fourmillement). */
    public static volatile boolean debugTraits = false;
    /** Banc : le filtre écrit la distance brute (blocs × 4096 sur 24 bits, ciel au maximum) pour l'étude hors du jeu. */
    public static volatile boolean debugBrut = false;

    private static final int IMAGES = 4;
    private static final Identifier[] RESEAU = new Identifier[IMAGES];

    static {
        for (int i = 0; i < IMAGES; i++) {
            RESEAU[i] = Identifier.fromNamespaceAndPath("reborn-hud", "textures/entity/byakugan/reseau_" + i + ".png");
        }
    }

    /** Horloge du fondu (le banc la fige). */
    public static volatile LongSupplier horloge = Util::getMillis;

    private static boolean actif;
    private static float portee = 50f;
    /** Le serveur parle {@code reborn:dojutsu} (règles reçues) : l'ancien {@code reborn:byakugan} est ignoré. */
    private static boolean serveurConnu;
    /** Le personnage a le Byakugan (règles du serveur). */
    private static boolean droit;
    private static long demandeA = Long.MIN_VALUE / 4;
    private static boolean attendReponse;
    private static String derniereRaison;
    /** Fondu : avancement au dernier changement, son instant. */
    private static float p0;
    private static long changeA = Long.MIN_VALUE / 4;
    /** Niveau calculé à l'image courante (lu par le rendu des entités, sans relire l'horloge). */
    private static float niveauImage;
    private static boolean filtreAfficheImage;
    /** Obscurité au joueur (0 jour, 1 grotte noire), lissée ; part « sous terre » (aucune lumière du ciel), lissée. */
    private static float sombre, sombreCible, sousTerre;
    private static int ticks;

    /** Entités vues derrière un mur (liseré seulement), relues tous les 4 ticks. */
    private static final IntOpenHashSet CACHEES = new IntOpenHashSet();
    /** Porteurs proches dont on peint les yeux pâles (envoyés par le serveur). */
    private static final IntOpenHashSet SIGNES = new IntOpenHashSet();

    private static DynamicTexture params;
    /** Largeur de la texture de paramètres (pixels décrits dans {@code shaders/post/byakugan.vsh}). */
    private static final int LARGEUR = 21;
    /** Pixels de la texture de paramètres : à écrire, déjà écrits. */
    private static final int[] VALEURS = new int[LARGEUR], ECRIT = new int[LARGEUR];
    private static final Matrix4f MAT = new Matrix4f();
    private static final Vector3f VEC = new Vector3f();
    private static final Vector4f COIN = new Vector4f();
    /** Fin du brouillard de la distance de rendu (blocs), relevée par {@code FogRendererByakuganMixin} ; 0 tant qu'inconnue. */
    private static float finBrouillard;
    /** Couleur du brouillard (l'horizon), 0xFFRRGGBB, relevée avec sa fin. */
    private static int couleurBrouillard = 0xFF000000;
    /** Hauteur des nuages (attribut d'environnement), relue au tick. */
    private static float hauteurNuages = Float.NaN;

    private ByakuganClient() {}

    public static void init() {
        DojutsuCanal.init();
        ClientTickEvents.END_CLIENT_TICK.register(ByakuganClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(ByakuganClient::clear));
    }

    /* ================================================================ API */

    /** La vision est-elle ouverte (le serveur l'a acceptée) ? Fondu de sortie non compris. */
    public static boolean actif() { return actif; }

    /** Ancien nom, gardé pour les appelants : {@link #actif()}. */
    public static boolean active() { return actif; }

    /** Le personnage a-t-il le Byakugan (règles du serveur ; faux tant qu'elles ne sont pas reçues, donc hors serveur Reborn) ? */
    public static boolean droit() { return droit; }

    public static boolean serveurConnu() { return serveurConnu; }

    public static float portee() { return portee; }

    /** Banc : dernière raison d'un refus ou d'une fin imposée. */
    public static String derniereRaison() { return derniereRaison; }

    /** Niveau du fondu à l'image courante (0 à 1). */
    public static float niveau() { return niveauImage; }

    /** Le filtre doit-il être posé (vision ouverte, ou fondu de sortie pas fini) ? Lu par {@code FiltresEcran}. */
    public static boolean filtreVoulu() {
        return actif || avancement(horloge.getAsLong()) > 0.001f;
    }

    /** Le filtre du Byakugan est-il réellement à l'écran (sinon le KO l'a pris) ? */
    public static boolean filtreAffiche() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameRenderer != null && FILTRE.equals(mc.gameRenderer.currentPostEffect());
    }

    /** Image d'animation du flux de chakra (impulsions qui partent du cœur), 5 images / s. */
    public static Identifier reseau() {
        return RESEAU[(int) ((horloge.getAsLong() / 200L) % IMAGES)];
    }

    /**
     * L'entité est-elle vue au Byakugan ? Vivante, pas le joueur local, ni porte-armure ni spectateur, à portée, et le
     * filtre réellement posé au-delà du quart du fondu (piège 8 du cadrage : jamais de couleurs-clés à l'écran sans le
     * filtre qui les traduit, KO compris).
     */
    public static boolean isTarget(LivingEntity e) {
        if (!filtreAfficheImage || niveauImage < SEUIL_PERSONNES) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e == mc.player || e instanceof ArmorStand || e.isSpectator() || !e.isAlive()) return false;
        float r = portee;
        return e.distanceToSqr(mc.player) <= (double) r * r;
    }

    /**
     * Identifiant de l'entité, 0 si elle n'en a pas encore : {@code getId()} lève en 26.2 pour l'entité d'affichage d'une
     * cage à monstres, recréée à chaque image (les identifiants réels partent de 1).
     */
    private static int id(Entity e) {
        return ((fr.reborn.hud.mixin.EntityIdByakuganAccessor) e).reborn$idBrut();
    }

    /** La cible est-elle derrière un mur (seulement le liseré cyan, pas le corps) ? */
    public static boolean cachee(Entity e) {
        if (CACHEES.isEmpty()) return false;
        int i = id(e);
        return i != 0 && CACHEES.contains(i);
    }

    /** Faut-il la faire « briller » pour le jeu (passe de contour vanilla, et un mod d'élagage qui garde ce qui brille) ? */
    public static boolean contour(Entity e) {
        return filtreAfficheImage && e instanceof LivingEntity l && cachee(e) && isTarget(l);
    }

    /** Ce porteur montre-t-il ses yeux pâles (vu par un autre joueur, de près) ? */
    public static boolean signe(Entity e) {
        if (SIGNES.isEmpty()) return false;
        int i = id(e);
        return i != 0 && SIGNES.contains(i);
    }

    /* ================================================================ réseau */

    /** Message du canal {@code reborn:dojutsu} (fil client). */
    static void recevoir(JsonObject o) {
        String t = o.has("t") ? o.get("t").getAsString() : "";
        switch (t) {
            case "regles" -> {
                serveurConnu = true;
                droit = o.has("byakugan") && o.get("byakugan").getAsBoolean();
                if (o.has("portee")) portee = Math.max(0f, o.get("portee").getAsFloat());
                if (!droit && actif) fermer(false);
                if (droit) prechauffer(Minecraft.getInstance());
            }
            case "byakugan" -> {
                serveurConnu = true;
                attendReponse = false;
                boolean on = o.has("on") && o.get("on").getAsBoolean();
                derniereRaison = o.has("raison") ? o.get("raison").getAsString() : null;
                if (o.has("portee")) portee = Math.max(0f, o.get("portee").getAsFloat());
                if (on) ouvrir();
                else fermer(isKoOuMort());
            }
            case "signes" -> {
                SIGNES.clear();
                JsonElement ids = o.get("ids");
                if (ids instanceof JsonArray a) for (JsonElement e : a) SIGNES.add(e.getAsInt());
            }
            default -> { }
        }
    }

    /**
     * Ancien canal {@code reborn:byakugan} (ShinobiSense, {@code /sense dojutsu}) : suivi seulement tant que ShinobiCore
     * n'a pas parlé {@code reborn:dojutsu} (serveur d'avant le lot 5). Sinon ignoré : ShinobiCore reprend la commande.
     */
    public static void ancienCanal(boolean on, float r) {
        if (serveurConnu) {
            LOG.debug("reborn:byakugan ignoré (ShinobiCore parle reborn:dojutsu)");
            return;
        }
        update(on, r);
    }

    /** Ouvre ou ferme la vision tout de suite (ancien canal, banc). */
    public static void update(boolean on, float r) {
        if (r > 0) portee = r;
        if (on) ouvrir();
        else fermer(false);
    }

    public static void clear() {
        actif = false;
        attendReponse = false;
        serveurConnu = false;
        droit = false;
        p0 = 0f;
        changeA = Long.MIN_VALUE / 4;
        niveauImage = 0f;
        CACHEES.clear();
        SIGNES.clear();
    }

    private static void ouvrir() {
        if (actif) return;
        long now = horloge.getAsLong();
        p0 = avancement(now);
        changeA = now;
        actif = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) lumiere(mc, mc.player, true);
        preparerParams(mc);
        son(mc, true);
        fr.reborn.hud.effets.FiltresEcran.tick();
    }

    /** Ferme la vision : fondu de 0,27 s, ou coupure nette (KO, mort). */
    private static void fermer(boolean net) {
        long now = horloge.getAsLong();
        boolean etait = actif;
        p0 = net ? 0f : avancement(now);
        changeA = now;
        actif = false;
        CACHEES.clear();
        if (etait && !net) son(Minecraft.getInstance(), false);
        fr.reborn.hud.effets.FiltresEcran.tick();
    }

    private static void son(Minecraft mc, boolean entree) {
        if (mc.getSoundManager() == null) return;
        // sons à nous (tools/ui-art/gen_byakugan_sfx.py) : un tintement de cristal tenu puis une longue résonance à
        // l'ouverture, une retombée sourde et brève à la fermeture ; locaux, non placés dans le monde
        fr.reborn.hud.menu.RebornSounds.playReborn(entree ? "byakugan.ouverture" : "byakugan.fermeture", 1f, entree ? 0.8f : 0.7f);
    }

    /** Touche O : bascule. Rien sans droit, à terre, sans technique, ni pendant une demande en cours. */
    public static void basculer() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        long now = horloge.getAsLong();
        if (actif) {
            fermer(false);
            DojutsuCanal.envoyer(false);
            return;
        }
        if (attendReponse && now - demandeA < ATTENTE_MAX_MS) return;
        if (!droit || isKoOuMort() || fr.reborn.hud.etat.EtatClient.refuse("tec")) return;
        attendReponse = true;
        demandeA = now;
        DojutsuCanal.envoyer(true);
    }

    private static boolean isKoOuMort() {
        Minecraft mc = Minecraft.getInstance();
        return fr.reborn.hud.ko.KoClient.actif() || mc.player == null || mc.player.isDeadOrDying();
    }

    /* ================================================================ fondu, paramètres */

    private static float avancement(long now) {
        long d = Math.max(0L, now - changeA);
        if (actif) {
            if (d >= ENTREE_FIN_MS) return 1f;
            return 1f - (1f - p0) * (float) Math.exp(-d / (double) ENTREE_TAU_MS);
        }
        return Math.max(0f, p0 - d / (float) SORTIE_MS);
    }

    /** À chaque image, avant le rendu : niveau du fondu (lu par le brouillard et le rendu des entités). */
    public static void frame() {
        if (params == null && !actif) { niveauImage = 0f; filtreAfficheImage = false; return; }
        niveauImage = avancement(horloge.getAsLong());
        filtreAfficheImage = filtreAffiche();
    }

    /**
     * Pendant le rendu du monde, une fois la projection de l'image connue ({@code GameRendererByakuganDepthMixin}) :
     * texture du filtre, réécrite seulement si elle change. {@code projection} est celle du monde (balancement de la marche
     * et nausée compris), {@code cam} l'état de la caméra de cette image : le filtre replace chaque pixel dans le monde
     * exactement (distance, brume, bord du monde), sans image de retard.
     */
    public static void vue(Matrix4fc projection, CameraRenderState cam) {
        if (niveauImage <= 0f && !actif) return;
        long now = horloge.getAsLong();
        Minecraft mc = Minecraft.getInstance();
        int[] v = VALEURS;
        // phase des boucles d'encre sur 16 bits (octet fort au pixel 0, faible au pixel 2) : elles dérivent sans à-coup
        int b = anneaux ? (int) (Math.floorMod(now, BOUCLES_MS) * 65535L / BOUCLES_MS) : 0;
        v[0] = 0xFF000000 | Math.round(niveauImage * 255f) << 16 | Math.round(sombre * 255f) << 8 | b >> 8;
        v[1] = (anneaux ? 0xFFFF0000 : 0xFF000000) | (debugProfondeur ? 0xFF00 : debugTraits ? 0x8000 : debugBrut ? 0x2000 : 0)
            | Math.round(sousTerre * 255f);
        // pixel 2 : R = distance de rendu en chunks (au-delà, la profondeur n'est que nuages) ; G = sens du fondu (l'encre
        // arrive avant la couleur et part avant elle)
        v[2] = 0xFF000000 | Math.min(255, Math.max(0, mc.options.getEffectiveRenderDistance())) << 16 | (actif ? 0xFF00 : 0)
            | (b & 0xFF);
        // pixel 3 : hauteur des nuages au-dessus de l'œil (ils écrivent la profondeur : le filtre en fait du ciel)
        boolean nuages = mc.options.getCloudStatus() != CloudStatus.OFF && !Float.isNaN(hauteurNuages);
        v[3] = 0xFF000000 | (nuages ? u16(((float) (hauteurNuages - cam.pos.y) + 2048f) / 4096f) << 8 | 0xFF : 0);
        // pixel 4 : distance le long du regard de l'arrière du porteur (troisième personne : son centre plus sa
        // demi-diagonale et 0,3 ; première personne : 1 bloc, la main). Ni grille ni contour vanilla plus près
        float porteur = 1f;
        LocalPlayer moi = mc.player;
        Camera camera = mc.gameRenderer.mainCamera();
        // pixels 19 et 20 : rectangle de l'écran (0 à 1) qui contient le porteur ; toute l'image en première personne (la main)
        float x0 = 0f, y0 = 0f, x1 = 1f, y1 = 1f;
        if (moi != null && camera.isDetached()) {
            var fw = camera.forwardVector();
            double w = moi.getBbWidth(), h = moi.getBbHeight();
            porteur = (float) ((moi.getX() - cam.pos.x) * fw.x() + (moi.getY() + h * 0.5 - cam.pos.y) * fw.y()
                + (moi.getZ() - cam.pos.z) * fw.z() + 0.5 * Math.sqrt(2.0 * w * w + h * h) + 0.3);
            // coins de sa boîte élargie de 0,3 bloc (bras, objet tenu), projetés ; un coin derrière l'œil : toute l'image
            Matrix4f pv = MAT.set(projection).mul(cam.viewRotationMatrix);
            var bb = moi.getBoundingBox();
            x0 = y0 = 1f;
            x1 = y1 = 0f;
            for (int c = 0; c < 8; c++) {
                pv.transform(COIN.set((float) ((c & 1) == 0 ? bb.minX - 0.3 - cam.pos.x : bb.maxX + 0.3 - cam.pos.x),
                    (float) ((c & 2) == 0 ? bb.minY - 0.3 - cam.pos.y : bb.maxY + 0.3 - cam.pos.y),
                    (float) ((c & 4) == 0 ? bb.minZ - 0.3 - cam.pos.z : bb.maxZ + 0.3 - cam.pos.z), 1f));
                if (COIN.w <= 1.0e-4f) { x0 = y0 = 0f; x1 = y1 = 1f; break; }
                float sx = COIN.x / COIN.w * 0.5f + 0.5f, sy = COIN.y / COIN.w * 0.5f + 0.5f;
                x0 = Math.min(x0, sx); x1 = Math.max(x1, sx);
                y0 = Math.min(y0, sy); y1 = Math.max(y1, sy);
            }
        }
        v[4] = 0xFF000000 | u16(Math.max(0f, porteur) / 256f) << 8 | 0xFF;
        v[19] = Integer.rotateRight(u16(x0) << 16 | u16(y0), 8);
        v[20] = Integer.rotateRight(u16(x1) << 16 | u16(y1), 8);
        // pixel 5 : fin du brouillard de la distance de rendu (blocs / 4096), le bord du monde ; pixel 18 : couleur du
        // brouillard (l'horizon), où le terrain du bord se fond, l'encre avec lui
        v[5] = 0xFF000000 | u16(finBrouillard / 4096f) << 8;
        v[18] = couleurBrouillard;
        // pixels 6 à 14 : le rayon de chaque pixel. Projection du monde = projection pure × A (balancement, nausée) ; le
        // point vu à la profondeur NEAR / d est à z × (regard + x · pasX + y · pasY) de l'œil. (Pixels 15 à 17 libres
        // depuis le 8e tour : l'œil dans son bloc ne servait qu'à la grille des blocs.)
        Matrix4f m = MAT.set(cam.projectionMatrix);
        float sx = 1f / m.m00(), sy = 1f / m.m11();
        m.invert().mul(projection).mul(cam.viewRotationMatrix).invert();
        int i = vecteur(v, 6, m.transformDirection(VEC.set(0f, 0f, -1f)));
        i = vecteur(v, i, m.transformDirection(VEC.set(sx, 0f, 0f)));
        vecteur(v, i, m.transformDirection(VEC.set(0f, sy, 0f)));
        if (java.util.Arrays.equals(v, ECRIT)) return;
        preparerParams(mc);
        if (params == null) return;
        NativeImage px = params.getPixels();
        if (px == null) return;
        for (int k = 0; k < LARGEUR; k++) px.setPixel(k, 0, v[k]);
        params.upload();
        System.arraycopy(v, 0, ECRIT, 0, LARGEUR);
    }

    /** Trois flottants, un par pixel : bits IEEE, octet fort en rouge, octet faible en alpha. */
    private static int vecteur(int[] v, int i, Vector3f x) {
        v[i] = Integer.rotateRight(Float.floatToRawIntBits(x.x), 8);
        v[i + 1] = Integer.rotateRight(Float.floatToRawIntBits(x.y), 8);
        v[i + 2] = Integer.rotateRight(Float.floatToRawIntBits(x.z), 8);
        return i + 3;
    }

    /** Fin du brouillard de la distance de rendu, en blocs (relevée sous le filtre ; 0 tant qu'inconnue). */
    public static float finBrouillard() { return finBrouillard; }

    /** Relevé par {@code FogRendererByakuganMixin} à chaque image où le filtre est posé : fin et couleur du brouillard. */
    public static void brouillard(float fin, float r, float g, float b) {
        finBrouillard = fin;
        couleurBrouillard = 0xFF000000 | octet(r) << 16 | octet(g) << 8 | octet(b);
    }

    private static int octet(float x) {
        return Math.round(Math.max(0f, Math.min(1f, x)) * 255f);
    }

    /** Valeur de 0 à 1 sur 16 bits, rouge (8 bits forts) puis vert, à décaler de 8 dans un pixel 0xAARRGGBB. */
    private static int u16(float x) {
        return Math.round(Math.max(0f, Math.min(1f, x)) * 65535f);
    }

    private static void preparerParams(Minecraft mc) {
        if (params != null || mc.getTextureManager() == null) return;
        params = new DynamicTexture(() -> "reborn-hud byakugan params", LARGEUR, 1, false);
        mc.getTextureManager().register(PARAMS, params);
        java.util.Arrays.fill(ECRIT, -1);
    }

    /**
     * Compile le filtre d'avance, dès que le serveur dit que le personnage a le Byakugan : la première ouverture ne paie plus
     * la compilation (~130 ms à froid sur Iris Xe). La texture de paramètres est enregistrée avant (le filtre la lit à son
     * chargement).
     */
    private static void prechauffer(Minecraft mc) {
        if (mc.getShaderManager() == null) return;
        preparerParams(mc);
        mc.getShaderManager().getPostChain(FILTRE, LevelTargetBundle.MAIN_TARGETS);
    }

    /* ================================================================ tick */

    private static void tick(Minecraft mc) {
        if (HudKeybinds.BYAKUGAN != null) {
            while (HudKeybinds.BYAKUGAN.consumeClick()) {
                if (mc.gui.screen() == null) basculer();
            }
        }
        // feuilles pleines et plantes cachées tant que le filtre du Byakugan est voulu (fondu de sortie compris)
        FeuillagePlein.suivre(mc, filtreVoulu());
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (attendReponse && horloge.getAsLong() - demandeA >= ATTENTE_MAX_MS) attendReponse = false;
        if (actif && isKoOuMort()) {
            // KO, mort : coupé net (le serveur le confirme de son côté)
            fermer(true);
            DojutsuCanal.envoyer(false);
            return;
        }
        if (!actif) {
            if (!CACHEES.isEmpty()) CACHEES.clear();
            return;
        }
        lumiere(mc, p, false);
        hauteurNuages = mc.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.CLOUD_HEIGHT, 1f);
        if (++ticks % 4 == 0) visibilites(mc, p);
    }

    /**
     * Gain adaptatif, lu à la position du joueur : obscurité (0 en plein jour, lumière ≥ 12 ; 1 dans le noir, ≤ 3) et part
     * « sous terre » (lumière du ciel brute, sans la nuit : 8 et moins). Lissés sur ~0,6 s, posés d'un coup à l'ouverture.
     */
    private static void lumiere(Minecraft mc, LocalPlayer p, boolean net) {
        int l = mc.level.getRawBrightness(p.blockPosition(), mc.level.getSkyDarken());
        Float force = sombreForce;
        sombreCible = force != null ? force : Math.max(0f, Math.min(1f, (12 - l) / 9f));
        int ciel = mc.level.getBrightness(LightLayer.SKY, p.blockPosition());
        float sousCible = Math.max(0f, Math.min(1f, (8 - ciel) / 8f));
        if (net) {
            sombre = sombreCible;
            sousTerre = sousCible;
            return;
        }
        sombre += (sombreCible - sombre) * 0.08f;
        if (Math.abs(sombreCible - sombre) < 0.002f) sombre = sombreCible;
        sousTerre += (sousCible - sousTerre) * 0.08f;
        if (Math.abs(sousCible - sousTerre) < 0.002f) sousTerre = sousCible;
    }

    /** Qui est derrière un mur : deux rayons (yeux et milieu du corps) par cible à portée, tous les 4 ticks. */
    private static void visibilites(Minecraft mc, LocalPlayer p) {
        CACHEES.clear();
        Vec3 oeil = p.getEyePosition();
        double r2 = (double) portee * portee;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity l) || e == p || e instanceof ArmorStand || e.isSpectator() || !l.isAlive()) continue;
            if (e.distanceToSqr(p) > r2) continue;
            if (vu(mc, p, oeil, e.getEyePosition()) || vu(mc, p, oeil, e.position().add(0, e.getBbHeight() * 0.5, 0))) continue;
            CACHEES.add(e.getId());
        }
    }

    private static boolean vu(Minecraft mc, LocalPlayer p, Vec3 de, Vec3 a) {
        return mc.level.clip(new ClipContext(de, a, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p)).getType()
            == HitResult.Type.MISS;
    }

    /** Banc : obscurité imposée (null = lue dans le monde). */
    public static volatile Float sombreForce;

    /** Banc : obscurité posée tout de suite, sans lissage. */
    public static void debugSombre(float s) {
        sombre = sombreCible = Math.max(0f, Math.min(1f, s));
    }

    public static float sombre() { return sombre; }

    public static float sousTerre() { return sousTerre; }
}
